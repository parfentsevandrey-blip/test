import Foundation
import SwiftUI

enum SidebarItem: Hashable {
    case overview
    case roms
    case install
}

/// Корневая модель: следит за подключёнными устройствами, собирает их
/// характеристики, ищет прошивки и хранит активную установку.
@Observable
@MainActor
final class AppModel {
    let tools = PlatformTools()

    var selection: SidebarItem? = .overview
    private(set) var devices: [ConnectedDevice] = []
    var selectedSerial: String?
    private(set) var specs: [String: DeviceSpecs] = [:]
    private(set) var availabilityBySerial: [String: [String: ROMAvailability]] = [:]
    var installSession: InstallSession?

    private var monitorTask: Task<Void, Never>?
    private var loadingSpecs: Set<String> = []

    var selectedDevice: ConnectedDevice? {
        devices.first { $0.serial == selectedSerial } ?? devices.first
    }

    var selectedSpecs: DeviceSpecs? {
        selectedDevice.flatMap { specs[$0.serial] }
    }

    func availability(for project: ROMProject) -> ROMAvailability {
        guard let serial = selectedSpecs?.serial else { return .checking }
        return availabilityBySerial[serial]?[project.id] ?? .checking
    }

    var adb: ADB? { tools.adb.map(ADB.init) }
    var fastboot: Fastboot? { tools.fastboot.map(Fastboot.init) }

    func start() {
        guard monitorTask == nil else { return }
        monitorTask = Task { [weak self] in
            do {
                try await self?.tools.prepare()
            } catch {
                return
            }
            while !Task.isCancelled {
                await self?.refreshDevices()
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    func retryTools() {
        monitorTask?.cancel()
        monitorTask = nil
        start()
    }

    func refreshDevices() async {
        guard let adb, let fastboot else { return }
        async let adbDevices = adb.devices()
        async let fastbootDevices = fastboot.devices()
        var merged = await adbDevices
        for device in await fastbootDevices where !merged.contains(where: { $0.serial == device.serial }) {
            merged.append(device)
        }
        // Сохраняем модель, известную до перехода в fastboot/recovery.
        merged = merged.map { device in
            var device = device
            if device.model == nil, let known = specs[device.serial] { device.model = known.displayName }
            return device
        }
        if merged != devices { devices = merged }
        if selectedSerial == nil || !devices.contains(where: { $0.serial == selectedSerial }) {
            if installSession?.isActive != true { selectedSerial = devices.first?.serial }
        }

        for device in devices where device.mode == .android && specs[device.serial] == nil {
            loadSpecs(for: device.serial)
        }
    }

    func reloadSelected() {
        guard let serial = selectedDevice?.serial else { return }
        specs[serial] = nil
        availabilityBySerial[serial] = nil
        loadSpecs(for: serial)
    }

    private func loadSpecs(for serial: String) {
        guard let adb, !loadingSpecs.contains(serial) else { return }
        loadingSpecs.insert(serial)
        Task {
            let result = await adb.specs(for: serial)
            specs[serial] = result
            loadingSpecs.remove(serial)
            await findROMs(for: result)
        }
    }

    func findROMs(for specs: DeviceSpecs) async {
        availabilityBySerial[specs.serial] = [:]
        await withTaskGroup(of: (String, ROMAvailability).self) { group in
            for project in ROMCatalog.all {
                group.addTask { (project.id, await ROMFinder.availability(of: project, for: specs)) }
            }
            for await (id, result) in group {
                availabilityBySerial[specs.serial, default: [:]][id] = result
            }
        }
    }

    func beginInstall(project: ROMProject, build: ROMBuild?, localPackage: URL? = nil, localRecovery: URL? = nil) {
        guard let specs = selectedSpecs else { return }
        let session = InstallSession(
            model: self,
            project: project,
            build: build,
            specs: specs,
            localPackage: localPackage,
            localRecovery: localRecovery
        )
        installSession = session
        selection = .install
        session.start()
    }

    /// Ищет устройство по серийному номеру в любом режиме.
    func device(serial: String) -> ConnectedDevice? {
        devices.first { $0.serial == serial }
    }
}
