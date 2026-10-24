import Foundation
import Virtualization

enum VMBuildError: LocalizedError {
    case unsupported
    case missingImage(String)
    case rosetta(String)

    var errorDescription: String? {
        switch self {
        case .unsupported: "Этот Mac не поддерживает виртуализацию (нужен Apple Silicon, а внутри другой виртуальной машины — M3 или новее)."
        case .missingImage(let name): "Установочный образ \(name) не найден — скачайте его снова или извлеките диск."
        case .rosetta(let why): "Rosetta: \(why)"
        }
    }
}

/// Сборка конфигурации Virtualization.framework из настроек машины.
/// Linux на arm64: загрузчик EFI, диск virtio, установочный образ — как USB-флешка.
enum VMBuilder {
    static let sharedTag = "vessel"
    static let rosettaTag = "rosetta"

    static func configuration(for machine: Machine, serialLog: URL? = nil) throws -> VZVirtualMachineConfiguration {
        let fm = FileManager.default
        let config = VZVirtualMachineConfiguration()

        let platform = VZGenericPlatformConfiguration()
        if let data = try? Data(contentsOf: machine.identifierURL), let id = VZGenericMachineIdentifier(dataRepresentation: data) {
            platform.machineIdentifier = id
        } else {
            let id = VZGenericMachineIdentifier()
            try id.dataRepresentation.write(to: machine.identifierURL)
            platform.machineIdentifier = id
        }
        config.platform = platform

        let boot = VZEFIBootLoader()
        if fm.fileExists(atPath: machine.efiURL.path) {
            boot.variableStore = VZEFIVariableStore(url: machine.efiURL)
        } else {
            boot.variableStore = try VZEFIVariableStore(creatingVariableStoreAt: machine.efiURL)
        }
        config.bootLoader = boot

        config.cpuCount = min(max(machine.cpu, VZVirtualMachineConfiguration.minimumAllowedCPUCount),
                              VZVirtualMachineConfiguration.maximumAllowedCPUCount)
        let memory = UInt64(machine.memoryGB) * 1_073_741_824
        config.memorySize = min(max(memory, VZVirtualMachineConfiguration.minimumAllowedMemorySize),
                                VZVirtualMachineConfiguration.maximumAllowedMemorySize)

        // Диск машины первым: после установки EFI загружается с него, даже если образ ещё подключён.
        var storage: [VZStorageDeviceConfiguration] = []
        let disk = try VZDiskImageStorageDeviceAttachment(url: machine.diskURL, readOnly: false,
                                                          cachingMode: .automatic, synchronizationMode: .full)
        storage.append(VZVirtioBlockDeviceConfiguration(attachment: disk))
        if machine.phase == .installing, let iso = machine.isoURL {
            guard fm.fileExists(atPath: iso.path) else { throw VMBuildError.missingImage(iso.lastPathComponent) }
            let image = try VZDiskImageStorageDeviceAttachment(url: iso, readOnly: true)
            storage.append(VZUSBMassStorageDeviceConfiguration(attachment: image))
        }
        config.storageDevices = storage

        let network = VZVirtioNetworkDeviceConfiguration()
        network.attachment = VZNATNetworkDeviceAttachment()
        if let mac = VZMACAddress(string: machine.macAddress) { network.macAddress = mac }
        config.networkDevices = [network]

        let graphics = VZVirtioGraphicsDeviceConfiguration()
        graphics.scanouts = [VZVirtioGraphicsScanoutConfiguration(widthInPixels: machine.displayWidth, heightInPixels: machine.displayHeight)]
        config.graphicsDevices = [graphics]
        config.keyboards = [VZUSBKeyboardConfiguration()]
        config.pointingDevices = [VZUSBScreenCoordinatePointingDeviceConfiguration()]

        if machine.sound || machine.microphone {
            let sound = VZVirtioSoundDeviceConfiguration()
            var streams: [VZVirtioSoundDeviceStreamConfiguration] = []
            if machine.sound {
                let output = VZVirtioSoundDeviceOutputStreamConfiguration()
                output.sink = VZHostAudioOutputStreamSink()
                streams.append(output)
            }
            if machine.microphone {
                let input = VZVirtioSoundDeviceInputStreamConfiguration()
                input.source = VZHostAudioInputStreamSource()
                streams.append(input)
            }
            sound.streams = streams
            config.audioDevices = [sound]
        }

        config.entropyDevices = [VZVirtioEntropyDeviceConfiguration()]
        config.memoryBalloonDevices = [VZVirtioTraditionalMemoryBalloonDeviceConfiguration()]

        var shares: [VZDirectorySharingDeviceConfiguration] = []
        if let folder = machine.sharedFolder {
            let url = URL(fileURLWithPath: folder, isDirectory: true)
            try? fm.createDirectory(at: url, withIntermediateDirectories: true)
            let share = VZVirtioFileSystemDeviceConfiguration(tag: sharedTag)
            share.share = VZSingleDirectoryShare(directory: VZSharedDirectory(url: url, readOnly: false))
            shares.append(share)
        }
        if machine.rosetta && VZLinuxRosettaDirectoryShare.availability == .installed {
            let share = VZVirtioFileSystemDeviceConfiguration(tag: rosettaTag)
            do { share.share = try VZLinuxRosettaDirectoryShare() } catch { throw VMBuildError.rosetta(error.localizedDescription) }
            shares.append(share)
        }
        config.directorySharingDevices = shares

        // Буфер обмена: агент SPICE в гостевой системе (spice-vdagent) общается через этот порт.
        if machine.clipboard {
            let console = VZVirtioConsoleDeviceConfiguration()
            let port = VZVirtioConsolePortConfiguration()
            port.name = VZSpiceAgentPortAttachment.spiceAgentPortName
            let spice = VZSpiceAgentPortAttachment()
            spice.sharesClipboard = true
            port.attachment = spice
            console.ports[0] = port
            config.consoleDevices = [console]
        }

        // Последовательная консоль в файл: по ней самопроверка видит, что система загрузилась.
        if let serialLog {
            fm.createFile(atPath: serialLog.path, contents: nil)
            let serial = VZVirtioConsoleDeviceSerialPortConfiguration()
            serial.attachment = try VZFileSerialPortAttachment(url: serialLog, append: false)
            config.serialPorts = [serial]
        }

        try config.validate()
        return config
    }
}
