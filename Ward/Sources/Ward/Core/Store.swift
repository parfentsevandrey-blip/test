import Foundation

/// Настройки Ward. Новые поля читаются с запасными значениями — обновление не сбрасывает настройки.
nonisolated struct Prefs: Codable, Sendable, Equatable {
    var search: SearchEngine = .duckduckgo
    var enabledLists: Set<String> = Set(FilterList.all.filter(\.enabledByDefault).map(\.id))
    var fingerprint = true
    var cleanLinks = true
    var httpsOnly = true
    var blockWebRTC = false
    var fraudWarnings = true
    var clearOnQuit = true
    var keepHistory = true
    var clearHistoryOnQuit = false
    var restoreTabs = true
    var blockAutoplay = true
    var autoUpdateLists = true
    var developerTools = false
    /// Сайты, где Ward помнит вход (не удаляет cookie при выходе).
    var rememberedSites: Set<String> = []
    /// Сайты, где защита выключена.
    var unprotectedSites: Set<String> = []
    /// Сайты, где выключен JavaScript.
    var noScriptSites: Set<String> = []
    var listsChecked: Date?

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Prefs()
        search = (try? c.decodeIfPresent(SearchEngine.self, forKey: .search)) ?? d.search
        enabledLists = (try? c.decodeIfPresent(Set<String>.self, forKey: .enabledLists)) ?? d.enabledLists
        fingerprint = (try? c.decodeIfPresent(Bool.self, forKey: .fingerprint)) ?? d.fingerprint
        cleanLinks = (try? c.decodeIfPresent(Bool.self, forKey: .cleanLinks)) ?? d.cleanLinks
        httpsOnly = (try? c.decodeIfPresent(Bool.self, forKey: .httpsOnly)) ?? d.httpsOnly
        blockWebRTC = (try? c.decodeIfPresent(Bool.self, forKey: .blockWebRTC)) ?? d.blockWebRTC
        fraudWarnings = (try? c.decodeIfPresent(Bool.self, forKey: .fraudWarnings)) ?? d.fraudWarnings
        clearOnQuit = (try? c.decodeIfPresent(Bool.self, forKey: .clearOnQuit)) ?? d.clearOnQuit
        keepHistory = (try? c.decodeIfPresent(Bool.self, forKey: .keepHistory)) ?? d.keepHistory
        clearHistoryOnQuit = (try? c.decodeIfPresent(Bool.self, forKey: .clearHistoryOnQuit)) ?? d.clearHistoryOnQuit
        restoreTabs = (try? c.decodeIfPresent(Bool.self, forKey: .restoreTabs)) ?? d.restoreTabs
        blockAutoplay = (try? c.decodeIfPresent(Bool.self, forKey: .blockAutoplay)) ?? d.blockAutoplay
        autoUpdateLists = (try? c.decodeIfPresent(Bool.self, forKey: .autoUpdateLists)) ?? d.autoUpdateLists
        developerTools = (try? c.decodeIfPresent(Bool.self, forKey: .developerTools)) ?? d.developerTools
        rememberedSites = (try? c.decodeIfPresent(Set<String>.self, forKey: .rememberedSites)) ?? d.rememberedSites
        unprotectedSites = (try? c.decodeIfPresent(Set<String>.self, forKey: .unprotectedSites)) ?? d.unprotectedSites
        noScriptSites = (try? c.decodeIfPresent(Set<String>.self, forKey: .noScriptSites)) ?? d.noScriptSites
        listsChecked = try? c.decodeIfPresent(Date.self, forKey: .listsChecked)
    }

    var enabledFilterLists: [FilterList] { FilterList.all.filter { enabledLists.contains($0.id) } }
}

nonisolated struct HistoryEntry: Codable, Sendable, Identifiable, Hashable {
    var url: URL
    var title: String
    var visited: Date
    var visits: Int
    var id: URL { url }
}

nonisolated struct Favorite: Codable, Sendable, Identifiable, Hashable {
    var id = UUID()
    var url: URL
    var title: String
}

/// Счётчики защиты: сколько трекеров и рекламы не дошло до вас.
nonisolated struct Stats: Codable, Sendable, Equatable {
    var total = 0
    var byKind: [String: Int] = [:]
    var byCompany: [String: Int] = [:]
    var days: [String: Int] = [:]
    var cleanedLinks = 0
    var upgraded = 0
    var since = Date()

    static func dayKey(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    var today: Int { days[Self.dayKey()] ?? 0 }

    mutating func record(_ kind: TrackerKind, company: String) {
        total += 1
        byKind[kind.rawValue, default: 0] += 1
        byCompany[company, default: 0] += 1
        days[Self.dayKey(), default: 0] += 1
        if days.count > 60 { for key in days.keys.sorted().prefix(days.count - 60) { days[key] = nil } }
        if byCompany.count > 400 {
            for (key, _) in byCompany.sorted(by: { $0.value < $1.value }).prefix(byCompany.count - 300) { byCompany[key] = nil }
        }
    }
}

/// Сохранённое состояние: окна и вкладки прошлого сеанса.
nonisolated struct SavedSession: Codable, Sendable {
    var windows: [[URL]]
}

/// Файлы JSON в папке поддержки.
nonisolated enum Storage {
    static func url(_ name: String) -> URL { AppPaths.support.appendingPathComponent(name) }

    static func load<T: Decodable>(_ type: T.Type, _ name: String) -> T? {
        guard let data = try? Data(contentsOf: url(name)) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(T.self, from: data)
    }

    static func save<T: Encodable>(_ value: T, _ name: String) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(value) else { return }
        try? data.write(to: url(name), options: .atomic)
    }

    static func remove(_ name: String) { try? FileManager.default.removeItem(at: url(name)) }
}

/// История посещений: для подсказок в адресной строке. Хранится только на этом Mac.
nonisolated struct History: Sendable {
    private(set) var entries: [HistoryEntry] = []
    static let limit = 5000

    init(_ entries: [HistoryEntry] = []) { self.entries = entries }

    mutating func visit(_ url: URL, title: String) {
        guard let scheme = url.scheme, scheme == "http" || scheme == "https" else { return }
        if let i = entries.firstIndex(where: { $0.url == url }) {
            var e = entries.remove(at: i)
            e.visits += 1
            e.visited = Date()
            if !title.isEmpty { e.title = title }
            entries.insert(e, at: 0)
        } else {
            entries.insert(HistoryEntry(url: url, title: title, visited: Date(), visits: 1), at: 0)
            if entries.count > Self.limit { entries.removeLast(entries.count - Self.limit) }
        }
    }

    mutating func retitle(_ url: URL, title: String) {
        guard !title.isEmpty, let i = entries.firstIndex(where: { $0.url == url }) else { return }
        entries[i].title = title
    }

    mutating func clear() { entries.removeAll() }

    mutating func forget(site: String) { entries.removeAll { Hosts.site($0.url.siteHost) == site } }

    /// Подсказки: сначала адреса, которые начинаются с введённого, потом совпадения в заголовке.
    func suggestions(_ text: String, limit: Int = 6) -> [HistoryEntry] {
        let q = text.lowercased().trimmingCharacters(in: .whitespaces)
        guard q.count >= 1 else { return [] }
        func stripped(_ url: URL) -> String {
            var s = url.absoluteString.lowercased()
            for p in ["https://", "http://", "www."] where s.hasPrefix(p) { s.removeFirst(p.count) }
            return s
        }
        var scored: [(HistoryEntry, Int)] = []
        for e in entries {
            let s = stripped(e.url)
            var score = 0
            if s.hasPrefix(q) { score = 1000 - min(s.count, 900) / 10 }
            else if e.title.lowercased().contains(q) || s.contains(q) { score = 200 }
            if score > 0 { scored.append((e, score + min(e.visits, 50) * 4)) }
            if scored.count > 300 { break }
        }
        return scored.sorted { $0.1 > $1.1 }.prefix(limit).map(\.0)
    }
}
