import AppKit
import Observation
import SwiftUI
import Virtualization

enum Screen: Hashable {
    case hot, popular, catalog, family(Family), machines, downloads

    var title: String {
        switch self {
        case .hot: "Горячие"
        case .popular: "Популярные"
        case .catalog: "Все системы"
        case .family(let f): f.title
        case .machines: "Мои машины"
        case .downloads: "Загрузки"
        }
    }

    var symbol: String {
        switch self {
        case .hot: "flame.fill"
        case .popular: "star.fill"
        case .catalog: "square.grid.2x2.fill"
        case .family(let f): f.symbol
        case .machines: "macwindow.on.rectangle"
        case .downloads: "arrow.down.circle.fill"
        }
    }
}

/// Что выбрал человек в мастере установки.
struct InstallPlan {
    var distro: Distro
    var edition: Edition
    var name: String
    var resources: Resources
    var shareFolder = true
    var rosetta = false
    var clipboard = true
    var sound = true
    var microphone = false
    var startAfter = true
}

/// Загрузка образа и создание машины после неё.
@Observable
final class DownloadJob: Identifiable {
    enum State: Equatable {
        case resolving, downloading, verifying, done, paused
        case failed(String)
    }

    let id = UUID()
    let plan: InstallPlan
    var state: State = .resolving
    var image: ResolvedImage?
    var received: Int64 = 0
    var total: Int64 = 0
    var speed: Double = 0
    @ObservationIgnored var task: Task<Void, Never>?
    @ObservationIgnored var lastSample: (Date, Int64) = (Date(), 0)

    init(plan: InstallPlan) { self.plan = plan }

    var fraction: Double { total > 0 ? min(1, Double(received) / Double(total)) : 0 }
    var eta: Double { speed > 0 && total > received ? Double(total - received) / speed : .infinity }

    var stateTitle: String {
        switch state {
        case .resolving: "Ищу последний выпуск…"
        case .downloading: "Скачиваю"
        case .verifying: "Проверяю контрольную сумму…"
        case .done: "Готово"
        case .paused: "На паузе"
        case .failed(let why): why
        }
    }
}

@Observable
final class AppModel {
    static let shared = AppModel()

    var screen: Screen = .hot {
        didSet { if oldValue != screen { path = [] } }
    }
    var path: [String] = []
    var search = ""
    var tags: Set<Tag> = []
    var onlyRunnable = false
    var machines: [Machine] = []
    var jobs: [DownloadJob] = []
    var sessions: [UUID: VMSession] = [:]
    /// Машина, окно которой нужно открыть (RootView вызывает openWindow).
    var openRequest: UUID?
    var installTarget: Distro?
    var installEdition: String?
    var alert: String?

    var virtualizationSupported: Bool { DemoUI.isOn || VZVirtualMachine.isSupported }

    func start() {
        guard !DemoUI.isOn else { return }
        machines = MachineStore.load()
        try? FileManager.default.createDirectory(at: Paths.images, withIntermediateDirectories: true)
    }

    // MARK: Каталог

    func filtered(_ list: [Distro]) -> [Distro] {
        list.filter { d in
            (tags.isEmpty || !tags.isDisjoint(with: d.tags)) &&
            (!onlyRunnable || d.runsHere) &&
            (search.isEmpty || [d.name, d.tagline, d.summary, d.family.title, d.facts.desktop]
                .contains { $0.localizedCaseInsensitiveContains(search) })
        }
    }

    // MARK: Установка

    func install(_ plan: InstallPlan) {
        let job = DownloadJob(plan: plan)
        jobs.insert(job, at: 0)
        run(job)
        if screen != .machines { screen = .machines }
    }

    func run(_ job: DownloadJob) {
        job.task?.cancel()
        job.task = Task { [weak self] in
            guard let self else { return }
            do {
                if job.image == nil {
                    job.state = .resolving
                    job.image = try await Resolver.resolve(job.plan.edition.source)
                }
                guard let image = job.image, let hash = image.hash, let digest = image.digest else { return }
                job.state = .downloading
                job.total = image.size ?? 0
                let destination = Paths.images.appendingPathComponent(image.fileName)
                let fetcher = FileFetcher(url: image.url, destination: destination, expectedSize: image.size, hash: hash, digest: digest) { got, total in
                    Task { @MainActor in
                        let now = Date()
                        let dt = now.timeIntervalSince(job.lastSample.0)
                        if dt > 1 {
                            let instant = Double(got - job.lastSample.1) / dt
                            job.speed = job.speed == 0 ? instant : job.speed * 0.7 + instant * 0.3
                            job.lastSample = (now, got)
                        }
                        job.received = got
                        if total > 0 { job.total = total }
                        if job.total > 0 && got >= job.total { job.state = .verifying }
                    }
                }
                try await fetcher.run()
                if Task.isCancelled { return }
                job.state = .done
                try await createMachine(from: job.plan, image: image)
            } catch is CancellationError {
                job.state = .paused
            } catch let error as URLError where error.code == .cancelled {
                job.state = .paused
            } catch {
                job.state = .failed(error.localizedDescription)
            }
        }
    }

    func pause(_ job: DownloadJob) {
        job.task?.cancel()
        job.state = .paused
    }

    func remove(_ job: DownloadJob) {
        job.task?.cancel()
        jobs.removeAll { $0.id == job.id }
    }

    func createMachine(from plan: InstallPlan, image: ResolvedImage) async throws {
        var shared: String?
        if plan.shareFolder {
            let folder = Paths.shared.appendingPathComponent(plan.name, isDirectory: true)
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            shared = folder.path
        }
        if plan.rosetta && VZLinuxRosettaDirectoryShare.availability == .notInstalled {
            try? await VZLinuxRosettaDirectoryShare.installRosetta()
        }
        let machine = Machine(name: plan.name, distroID: plan.distro.id, editionID: plan.edition.id, resources: plan.resources,
                              isoName: image.fileName, macAddress: VZMACAddress.randomLocallyAdministered().string,
                              sharedFolder: shared, rosetta: plan.rosetta, clipboard: plan.clipboard,
                              sound: plan.sound, microphone: plan.microphone, version: image.version)
        try MachineStore.save(machine)
        try MachineStore.createDisk(machine)
        machines.insert(machine, at: 0)
        if plan.startAfter { open(machine, start: true) }
    }

    // MARK: Машины

    func session(_ machine: Machine) -> VMSession {
        if let s = sessions[machine.id] { return s }
        let s = VMSession(machine: machine)
        s.onChange = { [weak self] m in self?.replace(m) }
        sessions[machine.id] = s
        return s
    }

    func machine(_ id: UUID) -> Machine? { machines.first { $0.id == id } }

    func open(_ machine: Machine, start: Bool) {
        let s = session(machine)
        openRequest = machine.id
        if start { Task { await s.start() } }
    }

    func replace(_ machine: Machine) {
        if let i = machines.firstIndex(where: { $0.id == machine.id }) { machines[i] = machine }
    }

    func save(_ machine: Machine) {
        do {
            if let old = self.machine(machine.id), machine.diskGB > old.diskGB { try MachineStore.growDisk(machine, toGB: machine.diskGB) }
            try MachineStore.save(machine)
            replace(machine)
            sessions[machine.id]?.update(machine)
        } catch {
            alert = error.localizedDescription
        }
    }

    func delete(_ machine: Machine) {
        Task {
            if let s = sessions[machine.id], s.status.isActive { await s.forceStop() }
            sessions[machine.id] = nil
            do { try MachineStore.delete(machine) } catch { alert = error.localizedDescription }
            machines.removeAll { $0.id == machine.id }
        }
    }

    // MARK: Образы на диске

    struct StoredImage: Identifiable {
        var url: URL
        var size: Int64
        var id: String { url.lastPathComponent }
        var inUse: Bool
    }

    func storedImages() -> [StoredImage] {
        let fm = FileManager.default
        let used = Set(machines.compactMap(\.isoName))
        let files = (try? fm.contentsOfDirectory(at: Paths.images, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.filter { $0.pathExtension == "iso" }.map {
            StoredImage(url: $0, size: Int64((try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0), inUse: used.contains($0.lastPathComponent))
        }.sorted { $0.size > $1.size }
    }

    func deleteImage(_ image: StoredImage) {
        try? FileManager.default.trashItem(at: image.url, resultingItemURL: nil)
    }

    /// Имя новой машины: «Ubuntu», «Ubuntu 2»…
    func suggestedName(_ distro: Distro) -> String {
        let names = Set(machines.map(\.name))
        if !names.contains(distro.name) { return distro.name }
        var i = 2
        while names.contains("\(distro.name) \(i)") { i += 1 }
        return "\(distro.name) \(i)"
    }
}
