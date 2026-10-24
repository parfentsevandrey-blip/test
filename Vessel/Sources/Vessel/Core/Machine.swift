import Foundation

/// Виртуальная машина на диске: папка «Имя.vessel» с настройками, диском, памятью EFI,
/// идентификатором машины и (если машину приостановили) сохранённым состоянием.
nonisolated struct Machine: Codable, Identifiable, Hashable, Sendable {
    enum Phase: String, Codable, Sendable {
        /// Установочный диск подключён, система ставится.
        case installing
        /// Система установлена, загрузка с диска машины.
        case installed
    }

    var id: UUID
    var name: String
    var distroID: String
    var editionID: String
    var cpu: Int
    var memoryGB: Int
    var diskGB: Int
    var phase: Phase
    /// Имя файла образа в Images (пока он подключён).
    var isoName: String?
    var macAddress: String
    var sharedFolder: String?
    var rosetta: Bool
    var clipboard: Bool
    var sound: Bool
    var microphone: Bool
    var displayWidth: Int
    var displayHeight: Int
    var created: Date
    var lastRun: Date?
    var version: String?

    init(id: UUID = UUID(), name: String, distroID: String, editionID: String, resources: Resources,
         isoName: String?, macAddress: String, sharedFolder: String?, rosetta: Bool, clipboard: Bool,
         sound: Bool, microphone: Bool, version: String?) {
        self.id = id
        self.name = name
        self.distroID = distroID
        self.editionID = editionID
        cpu = resources.cpu
        memoryGB = resources.memoryGB
        diskGB = resources.diskGB
        phase = .installing
        self.isoName = isoName
        self.macAddress = macAddress
        self.sharedFolder = sharedFolder
        self.rosetta = rosetta
        self.clipboard = clipboard
        self.sound = sound
        self.microphone = microphone
        displayWidth = 1920
        displayHeight = 1200
        created = Date()
        self.version = version
    }

    // Новые поля не должны ломать старые машины: всё необязательное читается с умолчаниями.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        distroID = try c.decode(String.self, forKey: .distroID)
        editionID = try c.decodeIfPresent(String.self, forKey: .editionID) ?? ""
        cpu = try c.decodeIfPresent(Int.self, forKey: .cpu) ?? 2
        memoryGB = try c.decodeIfPresent(Int.self, forKey: .memoryGB) ?? 4
        diskGB = try c.decodeIfPresent(Int.self, forKey: .diskGB) ?? 32
        phase = try c.decodeIfPresent(Phase.self, forKey: .phase) ?? .installed
        isoName = try c.decodeIfPresent(String.self, forKey: .isoName)
        macAddress = try c.decodeIfPresent(String.self, forKey: .macAddress) ?? ""
        sharedFolder = try c.decodeIfPresent(String.self, forKey: .sharedFolder)
        rosetta = try c.decodeIfPresent(Bool.self, forKey: .rosetta) ?? false
        clipboard = try c.decodeIfPresent(Bool.self, forKey: .clipboard) ?? true
        sound = try c.decodeIfPresent(Bool.self, forKey: .sound) ?? true
        microphone = try c.decodeIfPresent(Bool.self, forKey: .microphone) ?? false
        displayWidth = try c.decodeIfPresent(Int.self, forKey: .displayWidth) ?? 1920
        displayHeight = try c.decodeIfPresent(Int.self, forKey: .displayHeight) ?? 1200
        created = try c.decodeIfPresent(Date.self, forKey: .created) ?? Date()
        lastRun = try c.decodeIfPresent(Date.self, forKey: .lastRun)
        version = try c.decodeIfPresent(String.self, forKey: .version)
    }

    var distro: Distro? { Catalog.distro(distroID) }
    var edition: Edition? { distro?.editions.first { $0.id == editionID } }

    var bundle: URL { Paths.machines.appendingPathComponent("\(id.uuidString).vessel", isDirectory: true) }
    var configURL: URL { bundle.appendingPathComponent("machine.json") }
    var diskURL: URL { bundle.appendingPathComponent("disk.img") }
    var efiURL: URL { bundle.appendingPathComponent("efi-vars.fd") }
    var identifierURL: URL { bundle.appendingPathComponent("machine-id.bin") }
    var stateURL: URL { bundle.appendingPathComponent("suspended.vzvmsave") }
    var consoleURL: URL { bundle.appendingPathComponent("console.log") }
    var isoURL: URL? { isoName.map { Paths.images.appendingPathComponent($0) } }

    var hasSavedState: Bool { FileManager.default.fileExists(atPath: stateURL.path) }

    /// Сколько диск машины реально занимает на Mac (он разреженный: растёт по мере записи).
    var diskUsed: Int64 {
        if DemoUI.isOn { return Int64(diskGB) * 1_073_741_824 / 3 }
        let values = try? diskURL.resourceValues(forKeys: [.totalFileAllocatedSizeKey])
        return Int64(values?.totalFileAllocatedSize ?? 0)
    }
}

/// Хранилище машин: каждая — своя папка, список собирается из них (без общего файла, который можно испортить).
nonisolated enum MachineStore {
    static func load() -> [Machine] {
        let fm = FileManager.default
        guard let items = try? fm.contentsOfDirectory(at: Paths.machines, includingPropertiesForKeys: nil) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return items.filter { $0.pathExtension == "vessel" }.compactMap { folder in
            guard let data = try? Data(contentsOf: folder.appendingPathComponent("machine.json")) else { return nil }
            return try? decoder.decode(Machine.self, from: data)
        }.sorted { ($0.lastRun ?? $0.created) > ($1.lastRun ?? $1.created) }
    }

    static func save(_ machine: Machine) throws {
        try FileManager.default.createDirectory(at: machine.bundle, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        try encoder.encode(machine).write(to: machine.configURL, options: .atomic)
    }

    /// Разреженный диск: файл нужного размера, который занимает место только по мере записи (APFS).
    static func createDisk(_ machine: Machine) throws {
        let fm = FileManager.default
        guard !fm.fileExists(atPath: machine.diskURL.path) else { return }
        fm.createFile(atPath: machine.diskURL.path, contents: nil)
        let handle = try FileHandle(forWritingTo: machine.diskURL)
        defer { try? handle.close() }
        try handle.truncate(atOffset: UInt64(machine.diskGB) * 1_073_741_824)
    }

    /// Увеличить диск (уменьшать нельзя — внутри уже есть разделы).
    static func growDisk(_ machine: Machine, toGB size: Int) throws {
        let handle = try FileHandle(forWritingTo: machine.diskURL)
        defer { try? handle.close() }
        let current = try handle.seekToEnd()
        let wanted = UInt64(size) * 1_073_741_824
        if wanted > current { try handle.truncate(atOffset: wanted) }
    }

    static func delete(_ machine: Machine) throws {
        try FileManager.default.trashItem(at: machine.bundle, resultingItemURL: nil)
    }
}
