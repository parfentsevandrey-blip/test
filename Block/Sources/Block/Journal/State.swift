import Foundation

/// Прежнее значение ключа настроек (nil — ключа не было).
nonisolated struct PrefBefore: Codable, Equatable, Sendable {
    var key: PrefKey
    var value: PlistValue?
}

/// Что было до правила. Возврат берёт именно это, а не «значение по умолчанию».
nonisolated enum Snapshot: Codable, Equatable, Sendable {
    case prefs([PrefBefore])
    case system(path: String, values: [String: PlistValue?])
    /// «kind:value» → путь программы, которая открывала (пусто — не было).
    case handlers([String: String])
    case agent(Launchd.Override, loaded: Bool)
    case dock(Data?)
    /// Правила, состояние которых живёт в самом Block (сторож, сеть через Veil, ручные шаги).
    case flag

    var summary: String {
        switch self {
        case .prefs(let list): list.map { "\($0.key.domain) \($0.key.key) = \($0.value?.description ?? "нет")" }.joined(separator: "; ")
        case .system(let path, let values): "\(path): " + values.map { "\($0.key) = \($0.value?.description ?? "нет")" }.sorted().joined(separator: "; ")
        case .handlers(let map): map.map { "\($0.key) → \($0.value.isEmpty ? "нет" : URL(fileURLWithPath: $0.value).deletingPathExtension().lastPathComponent)" }.sorted().joined(separator: "; ")
        case .agent(let o, let loaded): "запись: \(o.rawValue), работала: \(loaded ? "да" : "нет")"
        case .dock(let data): data == nil ? "значков не было" : "значки и их места сохранены"
        case .flag: "—"
        }
    }
}

nonisolated struct JournalEntry: Codable, Identifiable, Sendable {
    enum Kind: String, Codable, Sendable { case applied, reverted, failed, drift, launch }
    var id = UUID()
    var date = Date()
    var kind: Kind
    var ruleID: String
    var title: String
    var note: String
}

/// Всё, что Block помнит. Хранится в ~/Library/Application Support/Block/state.json.
nonisolated struct BlockState: Codable, Sendable {
    /// Правило → снимок до первого применения. Есть запись — правило применено Block.
    var baseline: [String: Snapshot] = [:]
    var manualDone: Set<String> = []
    /// Группы адресов, отправленные в Veil.
    var veilGroups: Set<String> = []
    var journal: [JournalEntry] = []
    var answers = Answers()
    var profile: Profile = .gentle
    var onboarded = false
    var networkMethod: NetworkMethod = .hosts
    /// После обновления macOS применять заново всё, что Apple вернула.
    var autoReapply = true
    var lastOS = ""
    /// Сколько раз сторож не дал открыться программе.
    var stoppedLaunches: [String: Int] = [:]
    /// Правила, которые человек выбрал вручную (профиль «Своё»).
    var customSelection: Set<String> = []

    static var directory: URL {
        if let custom = ProcessInfo.processInfo.environment["BLOCK_HOME"] { return URL(fileURLWithPath: custom) }
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Block")
    }

    static var file: URL { directory.appendingPathComponent("state.json") }

    static func load() -> BlockState {
        guard let data = try? Data(contentsOf: file), let state = try? JSONDecoder().decode(BlockState.self, from: data) else { return BlockState() }
        return state
    }

    func save() {
        try? FileManager.default.createDirectory(at: Self.directory, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(self) else { return }
        try? data.write(to: Self.file, options: .atomic)
    }

    mutating func log(_ kind: JournalEntry.Kind, _ rule: Rule, _ note: String) {
        journal.insert(JournalEntry(kind: kind, ruleID: rule.id, title: rule.title, note: note), at: 0)
        if journal.count > 2000 { journal.removeLast(journal.count - 2000) }
    }

    static var osVersion: String {
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "\(v.majorVersion).\(v.minorVersion).\(v.patchVersion)"
    }
}
