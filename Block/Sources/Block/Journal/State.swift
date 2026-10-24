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
    /// Наблюдение: следить за соединениями процессов Apple.
    var monitorOn = true
    /// Закрывать подозрительные адреса сразу, как только увидены (через Veil — без пароля).
    var monitorAuto = false
    /// Адреса, закрытые по итогам наблюдения (группа «Найдено наблюдением»).
    var observedHosts: Set<String> = []
    /// Службы, найденные наблюдением: метка launchd → процесс.
    var observedAgents: [String: String] = [:]
    /// Соединения, которые человек разрешил («процесс|адрес»).
    var allowedObservations: Set<String> = []

    init() {}

    /// Чтение с умолчаниями: новые поля не ломают состояние, сохранённое прежней версией Block.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = BlockState()
        baseline = try c.decodeIfPresent([String: Snapshot].self, forKey: .baseline) ?? d.baseline
        manualDone = try c.decodeIfPresent(Set<String>.self, forKey: .manualDone) ?? d.manualDone
        veilGroups = try c.decodeIfPresent(Set<String>.self, forKey: .veilGroups) ?? d.veilGroups
        journal = try c.decodeIfPresent([JournalEntry].self, forKey: .journal) ?? d.journal
        answers = try c.decodeIfPresent(Answers.self, forKey: .answers) ?? d.answers
        profile = try c.decodeIfPresent(Profile.self, forKey: .profile) ?? d.profile
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? d.onboarded
        networkMethod = try c.decodeIfPresent(NetworkMethod.self, forKey: .networkMethod) ?? d.networkMethod
        autoReapply = try c.decodeIfPresent(Bool.self, forKey: .autoReapply) ?? d.autoReapply
        lastOS = try c.decodeIfPresent(String.self, forKey: .lastOS) ?? d.lastOS
        stoppedLaunches = try c.decodeIfPresent([String: Int].self, forKey: .stoppedLaunches) ?? d.stoppedLaunches
        customSelection = try c.decodeIfPresent(Set<String>.self, forKey: .customSelection) ?? d.customSelection
        monitorOn = try c.decodeIfPresent(Bool.self, forKey: .monitorOn) ?? d.monitorOn
        monitorAuto = try c.decodeIfPresent(Bool.self, forKey: .monitorAuto) ?? d.monitorAuto
        observedHosts = try c.decodeIfPresent(Set<String>.self, forKey: .observedHosts) ?? d.observedHosts
        observedAgents = try c.decodeIfPresent([String: String].self, forKey: .observedAgents) ?? d.observedAgents
        allowedObservations = try c.decodeIfPresent(Set<String>.self, forKey: .allowedObservations) ?? d.allowedObservations
    }

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
