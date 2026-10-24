import Foundation
import Observation
import Virtualization

/// Запущенная (или готовая к запуску) машина. Всё на главном акторе: очередь VZVirtualMachine — главная.
@Observable
final class VMSession: NSObject, VZVirtualMachineDelegate {
    enum Status: Equatable {
        case stopped, starting, running, paused, saving, restoring, stopping
        case failed(String)

        var title: String {
            switch self {
            case .stopped: "Выключена"
            case .starting: "Запускается…"
            case .running: "Работает"
            case .paused: "На паузе"
            case .saving: "Сохраняю состояние…"
            case .restoring: "Возвращаю состояние…"
            case .stopping: "Выключается…"
            case .failed: "Ошибка"
            }
        }

        var isActive: Bool { self == .running || self == .paused || self == .starting || self == .restoring }
    }

    private(set) var machine: Machine
    private(set) var vm: VZVirtualMachine?
    private(set) var status: Status = .stopped
    /// Короткое сообщение поверх экрана машины («Установка завершена — запускаю систему»).
    var notice: String?
    @ObservationIgnored var onChange: ((Machine) -> Void)?
    @ObservationIgnored private var stateObservation: NSKeyValueObservation?
    @ObservationIgnored private var config: VZVirtualMachineConfiguration?
    @ObservationIgnored var serialLog: URL?

    init(machine: Machine) {
        self.machine = machine
    }

    func update(_ machine: Machine) {
        guard vm == nil else { return }
        self.machine = machine
    }

    // MARK: Запуск и остановка

    func start() async {
        guard !status.isActive else { return }
        guard VZVirtualMachine.isSupported else { status = .failed(VMBuildError.unsupported.localizedDescription); return }
        status = .starting
        do {
            try MachineStore.createDisk(machine)
            let config = try VMBuilder.configuration(for: machine, serialLog: serialLog)
            self.config = config
            let vm = VZVirtualMachine(configuration: config)
            vm.delegate = self
            self.vm = vm
            observe(vm)
            if machine.hasSavedState {
                status = .restoring
                do {
                    try await vm.restoreMachineStateFrom(url: machine.stateURL)
                    try? FileManager.default.removeItem(at: machine.stateURL)
                    try await vm.resume()
                    status = .running
                    touch()
                    return
                } catch {
                    // Состояние не подошло (другая версия macOS или настроек) — обычная загрузка.
                    try? FileManager.default.removeItem(at: machine.stateURL)
                    notice = "Сохранённое состояние не подошло — система загружается заново."
                }
            }
            try await vm.start()
            status = .running
            touch()
        } catch {
            vm = nil
            status = .failed(error.localizedDescription)
        }
    }

    /// Мягкое выключение: системе приходит нажатие кнопки питания.
    func requestStop() {
        guard let vm, vm.canRequestStop else { return }
        do {
            try vm.requestStop()
            status = .stopping
        } catch {
            notice = "Система не ответила на кнопку питания: \(error.localizedDescription)"
        }
    }

    /// Выключить сразу, как выдернуть шнур.
    func forceStop() async {
        guard let vm, vm.canStop else { return }
        status = .stopping
        try? await vm.stop()
        finished()
    }

    func pause() async {
        guard let vm, vm.canPause else { return }
        try? await vm.pause()
        status = .paused
    }

    func resume() async {
        guard let vm, vm.canResume else { return }
        try? await vm.resume()
        status = .running
    }

    /// Приостановить и сохранить память на диск: в следующий раз машина продолжит с того же места.
    func suspend() async {
        guard let vm else { return }
        do {
            if vm.state == .running { try await vm.pause() }
            status = .saving
            try config?.validateSaveRestoreSupport()
            try await vm.saveMachineStateTo(url: machine.stateURL)
            try await vm.stop()
            finished()
        } catch {
            try? FileManager.default.removeItem(at: machine.stateURL)
            notice = "Не удалось сохранить состояние: \(error.localizedDescription)"
            if vm.canResume { try? await vm.resume(); status = .running }
        }
    }

    /// Установочный диск больше не нужен — со следующего запуска машина грузится только со своего диска.
    func ejectInstaller() {
        machine.phase = .installed
        machine.isoName = nil
        persist()
        notice = vm == nil ? nil : "Установочный диск извлечён — со следующего запуска система загрузится с диска машины."
    }

    // MARK: VZVirtualMachineDelegate

    func guestDidStop(_ virtualMachine: VZVirtualMachine) {
        let wasInstalling = machine.phase == .installing
        finished()
        // VZ не перезагружает машину сама: перезагрузка гостя приходит сюда как остановка.
        // Если при этом на диск уже записана система — установка закончилась, извлекаем образ и запускаем.
        if wasInstalling && machine.diskUsed > 1_500_000_000 {
            machine.phase = .installed
            machine.isoName = nil
            persist()
            notice = "Установка завершена — запускаю установленную систему."
            Task { await start() }
        }
    }

    func virtualMachine(_ virtualMachine: VZVirtualMachine, didStopWithError error: any Error) {
        finished()
        status = .failed(error.localizedDescription)
    }

    func virtualMachine(_ virtualMachine: VZVirtualMachine, networkDevice: VZNetworkDevice,
                        attachmentWasDisconnectedWithError error: any Error) {
        notice = "Сеть машины отключилась: \(error.localizedDescription)"
    }

    // MARK: Внутреннее

    private func observe(_ vm: VZVirtualMachine) {
        stateObservation = vm.observe(\.state, options: [.new]) { [weak self] vm, _ in
            let state = vm.state
            Task { @MainActor in self?.sync(state) }
        }
    }

    private func sync(_ state: VZVirtualMachine.State) {
        switch state {
        case .running: if status != .saving { status = .running }
        case .paused: if status != .saving { status = .paused }
        case .stopped: if vm != nil && status != .saving { finished() }
        case .error: status = .failed("Машина остановилась с ошибкой.")
        default: break
        }
    }

    private func finished() {
        stateObservation = nil
        vm = nil
        if case .failed = status {} else { status = .stopped }
    }

    private func touch() {
        machine.lastRun = Date()
        persist()
    }

    private func persist() {
        try? MachineStore.save(machine)
        onChange?(machine)
    }
}
