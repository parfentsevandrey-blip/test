import AppKit
import Observation
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

struct SpeedSample: Identifiable, Equatable {
    let id: Int
    let date: Date
    let down: Double
    let up: Double
}

enum TorrentFilter: String, CaseIterable, Identifiable {
    case all, downloading, seeding, done, paused

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "Все"
        case .downloading: "Загрузка"
        case .seeding: "Раздача"
        case .done: "Готово"
        case .paused: "Пауза"
        }
    }

    func matches(_ t: TorrentSnapshot) -> Bool {
        switch self {
        case .all: true
        case .downloading: t.status == .downloading || t.status == .metadata || t.status == .checking
        case .seeding: t.status == .seeding
        case .done: t.progress >= 1
        case .paused: t.status == .paused || t.status == .finished || t.status == .error
        }
    }
}

/// То, что пользователь собирается добавить (показывается в листе подтверждения).
struct PendingAdd: Identifiable {
    enum Source {
        case torrent(Metainfo)
        case magnet(MagnetLink)
    }

    let id = UUID()
    let source: Source

    var name: String {
        switch source {
        case .torrent(let m): m.name
        case .magnet(let m): m.name ?? "Magnet \(m.infoHash.hex.prefix(8))…"
        }
    }
}

@Observable
final class AppModel {
    let engine: Engine
    private(set) var torrents: [TorrentSnapshot] = []
    var selectedID: String?
    var filter: TorrentFilter = .all
    var search = ""
    var pendingAdd: PendingAdd?
    var magnetText = ""
    private(set) var history: [SpeedSample] = []
    private(set) var torrentHistory: [String: [SpeedSample]] = [:]
    private(set) var toast: String?
    private(set) var lastError: String?
    var settings: EngineSettings {
        didSet {
            persistSettings()
            let s = settings
            Task { await engine.update(settings: s) }
        }
    }

    @ObservationIgnored private var pollTask: Task<Void, Never>?
    @ObservationIgnored private var sampleCounter = 0
    @ObservationIgnored private var toastTask: Task<Void, Never>?
    @ObservationIgnored private let dock = DockTileController()

    static let supportDirectory: URL = {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Swarm", isDirectory: true)
    }()

    init() {
        let defaults = UserDefaults.standard
        let stored = defaults.data(forKey: "engineSettings").flatMap { try? JSONDecoder().decode(EngineSettings.self, from: $0) }
        let settings = stored ?? EngineSettings()
        self.settings = settings
        var port = defaults.integer(forKey: "listenPort")
        if port == 0 {
            port = Int.random(in: 49152...65000)
            defaults.set(port, forKey: "listenPort")
        }
        engine = Engine(supportDirectory: Self.supportDirectory, port: UInt16(port), settings: settings)
    }

    var downloadFolder: URL {
        get {
            UserDefaults.standard.string(forKey: "downloadFolder").map { URL(fileURLWithPath: $0) }
                ?? FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]
        }
        set { UserDefaults.standard.set(newValue.path, forKey: "downloadFolder") }
    }

    func start() {
        guard pollTask == nil else { return }
        let engine = self.engine
        pollTask = Task { [weak self] in
            await engine.start()
            while !Task.isCancelled {
                await self?.poll()
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
        Task { [weak self] in
            for await name in engine.finishedNames {
                self?.finished(name)
            }
        }
        Task {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
        }
    }

    private func poll() async {
        let list = await engine.snapshots(detailed: selectedID)
        torrents = list
        sampleCounter += 1
        if sampleCounter % 2 == 0 {
            let down = list.reduce(0) { $0 + $1.downRate }
            let up = list.reduce(0) { $0 + $1.upRate }
            let now = Date()
            history.append(SpeedSample(id: sampleCounter, date: now, down: down, up: up))
            if history.count > 120 { history.removeFirst(history.count - 120) }
            for t in list where t.status.isActive {
                var h = torrentHistory[t.id] ?? []
                h.append(SpeedSample(id: sampleCounter, date: now, down: t.downRate, up: t.upRate))
                if h.count > 120 { h.removeFirst(h.count - 120) }
                torrentHistory[t.id] = h
            }
        }
        let downloading = list.filter { $0.status == .downloading || $0.status == .metadata }
        let wanted = downloading.reduce(Int64(0)) { $0 + $1.wantedSize }
        let done = downloading.reduce(Int64(0)) { $0 + $1.doneSize }
        dock.update(down: list.reduce(0) { $0 + $1.downRate },
                    progress: wanted > 0 ? Double(done) / Double(wanted) : nil,
                    active: downloading.count)
    }

    // MARK: - Сводка

    var totalDown: Double { torrents.reduce(0) { $0 + $1.downRate } }
    var totalUp: Double { torrents.reduce(0) { $0 + $1.upRate } }

    /// 0…1 — насколько «живой» сейчас рой (для фонового сияния).
    var activity: Double {
        let rate = totalDown + totalUp
        guard rate > 0 else { return 0 }
        return min(1, log10(1 + rate / 50_000) / 3)
    }

    var visible: [TorrentSnapshot] {
        torrents.filter { filter.matches($0) && (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search)) }
    }

    var selected: TorrentSnapshot? { torrents.first { $0.id == selectedID } }

    func count(_ f: TorrentFilter) -> Int { torrents.filter(f.matches).count }

    // MARK: - Добавление

    func open(_ urls: [URL]) {
        for url in urls {
            if url.scheme?.lowercased() == "magnet" {
                addMagnet(url.absoluteString)
            } else if url.isFileURL {
                openTorrentFile(url)
            }
        }
    }

    func openTorrentFile(_ url: URL) {
        do {
            let meta = try Metainfo(torrentData: Data(contentsOf: url))
            pendingAdd = PendingAdd(source: .torrent(meta))
        } catch {
            showError("Не удалось прочитать «\(url.lastPathComponent)»: \(error.localizedDescription)")
        }
    }

    func addMagnet(_ text: String) {
        guard let magnet = MagnetLink(text) else {
            showError("Это не magnet-ссылка")
            return
        }
        pendingAdd = PendingAdd(source: .magnet(magnet))
        magnetText = ""
    }

    func confirm(_ add: PendingAdd, folder: URL, priorities: [FilePriority]?, sequential: Bool, startNow: Bool) {
        pendingAdd = nil
        let engine = self.engine
        Task {
            let id: InfoHash
            switch add.source {
            case .torrent(let meta):
                id = await engine.add(meta: meta, savePath: folder, priorities: priorities, sequential: sequential, paused: !startNow)
            case .magnet(let magnet):
                id = await engine.add(magnet: magnet, savePath: folder)
                if sequential { await engine.setSequential(id.hex, true) }
                if !startNow { await engine.pause(id.hex) }
            }
            withAnimation(.smooth) { self.selectedID = id.hex }
        }
    }

    func presentOpenPanel() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.allowedContentTypes = [UTType(filenameExtension: "torrent") ?? .data]
        panel.prompt = "Открыть"
        panel.begin { [weak self] response in
            guard response == .OK else { return }
            let urls = panel.urls
            MainActor.assumeIsolated { self?.open(urls) }
        }
    }

    /// «Создать торрент»: выбрать файл или папку, сохранить .torrent и сразу начать раздачу.
    func presentCreatePanel() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = true
        panel.prompt = "Создать торрент"
        panel.message = "Выберите файл или папку для раздачи"
        panel.begin { [weak self] response in
            guard response == .OK, let source = panel.url else { return }
            MainActor.assumeIsolated { self?.create(from: source) }
        }
    }

    private func create(from source: URL) {
        showToast("Считаю хеши «\(source.lastPathComponent)»…")
        let engine = self.engine
        Task.detached(priority: .userInitiated) {
            do {
                let meta = try TorrentCreator.create(from: source)
                await engine.add(meta: meta, savePath: source.deletingLastPathComponent())
                await MainActor.run { self.saveCreated(meta) }
            } catch {
                await MainActor.run { self.showError(error.localizedDescription) }
            }
        }
    }

    private func saveCreated(_ meta: Metainfo) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "\(meta.name).torrent"
        panel.allowedContentTypes = [UTType(filenameExtension: "torrent") ?? .data]
        panel.message = "Раздача уже началась. Куда сохранить .torrent-файл?"
        panel.begin { response in
            guard response == .OK, let url = panel.url else { return }
            try? meta.torrentFileData().write(to: url)
        }
        selectedID = meta.infoHash.hex
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(MagnetLink.make(hash: meta.infoHash, name: meta.name, trackers: []), forType: .string)
        showToast("Раздача создана — magnet-ссылка в буфере обмена")
    }

    // MARK: - Действия

    func toggle(_ t: TorrentSnapshot) {
        let engine = self.engine
        let id = t.id
        let paused = t.status == .paused || t.status == .finished || t.status == .error
        Task {
            if paused { await engine.resume(id) } else { await engine.pause(id) }
        }
    }

    func remove(_ t: TorrentSnapshot, deleteFiles: Bool) {
        let engine = self.engine
        let id = t.id
        if selectedID == id { selectedID = nil }
        withAnimation(.smooth) { torrents.removeAll { $0.id == id } }
        Task { await engine.remove(id, deleteFiles: deleteFiles) }
    }

    func setPriority(_ t: TorrentSnapshot, files: [Int], _ priority: FilePriority) {
        let engine = self.engine
        let id = t.id
        Task { await engine.setPriority(id, files: files, priority: priority) }
    }

    func setSequential(_ t: TorrentSnapshot, _ on: Bool) {
        let engine = self.engine
        let id = t.id
        Task { await engine.setSequential(id, on) }
    }

    func pauseAll() { let e = engine; Task { await e.pauseAll() } }
    func resumeAll() { let e = engine; Task { await e.resumeAll() } }

    func reveal(_ t: TorrentSnapshot) {
        let url = t.contentURL ?? t.savePath
        if FileManager.default.fileExists(atPath: url.path) {
            NSWorkspace.shared.activateFileViewerSelecting([url])
        } else {
            NSWorkspace.shared.open(t.savePath)
        }
    }

    func copyMagnet(_ t: TorrentSnapshot) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(t.magnet, forType: .string)
        showToast("Magnet-ссылка скопирована")
    }

    /// Видео открываем в Lumen, если он установлен, иначе — в приложении по умолчанию.
    func open(file: FileSnapshot) {
        let url = file.localURL
        let video: Set<String> = ["mkv", "mp4", "m4v", "mov", "webm", "avi", "ts"]
        if video.contains(url.pathExtension.lowercased()),
           let lumen = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "app.lumen.player") {
            NSWorkspace.shared.open([url], withApplicationAt: lumen, configuration: NSWorkspace.OpenConfiguration())
        } else {
            NSWorkspace.shared.open(url)
        }
    }

    func makeDefaultMagnetHandler() {
        Task {
            do {
                try await NSWorkspace.shared.setDefaultApplication(at: Bundle.main.bundleURL, toOpenURLsWithScheme: "magnet")
                showToast("Swarm открывает magnet-ссылки по умолчанию")
            } catch {
                showError(error.localizedDescription)
            }
        }
    }

    // MARK: - Уведомления

    private func finished(_ name: String) {
        showToast("Загружено: \(name)")
        let content = UNMutableNotificationContent()
        content.title = "Загрузка завершена"
        content.body = name
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        Task { try? await UNUserNotificationCenter.current().add(request) }
        NSApp.requestUserAttention(.informationalRequest)
    }

    func showToast(_ text: String) {
        withAnimation(.bouncy) { toast = text }
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3.5))
            guard !Task.isCancelled else { return }
            withAnimation(.smooth) { self?.toast = nil }
        }
    }

    func showError(_ text: String) {
        lastError = text
        showToast(text)
    }

    private func persistSettings() {
        if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "engineSettings") }
    }

    func saveBeforeQuit() async {
        await engine.saveAll()
    }
}
