import Foundation

/// Как часто включать «Режим блокировки» (Lockdown Mode) WebKit.
nonisolated enum StrictMode: String, Codable, CaseIterable, Sendable {
    case off, unfamiliar, all
}

/// Через что ходят окна: напрямую или через локальный прокси (например, Veil).
nonisolated enum ProxyMode: String, Codable, CaseIterable, Sendable {
    case off, all, privateOnly
}

/// Настройки Ward. Новые поля читаются с запасными значениями — обновление не сбрасывает настройки.
nonisolated struct Prefs: Codable, Sendable, Equatable {
    var search: SearchEngine = .duckduckgo
    var enabledLists: Set<String> = Set(FilterList.all.filter(\.enabledByDefault).map(\.id))
    /// Списки, о которых Ward уже знал: новые списки с обновлением включаются сами, выключенные вами — нет.
    var knownLists: Set<String> = Set(FilterList.all.map(\.id))
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
    // Новое во второй версии.
    var consentReject = true
    var cleanClosedSites = true
    var strictMode: StrictMode = .off
    var strictSites: Set<String> = []
    var relaxedSites: Set<String> = []
    var phishingWarnings = true
    var trustedLookalikes: Set<String> = []
    var sponsorBlock = true
    var sponsorCategories: Set<String> = ["sponsor", "selfpromo", "interaction", "exclusive_access"]
    var proxyMode: ProxyMode = .off
    var proxyHost = "127.0.0.1"
    var proxyPort = 10808
    var cnameDetection = true
    var spoofTimezone = false
    var spoofLanguage = false
    var protectHistory = false
    /// Камера и микрофон: сайт → разрешено навсегда (true) или запрещено (false).
    var mediaDecisions: [String: Bool] = [:]
    var denyMediaByDefault = false
    /// Сайт → контейнер, в котором он всегда открывается.
    var siteContainers: [String: UUID] = [:]

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Prefs()
        func v<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T { (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback }
        search = v(.search, d.search)
        enabledLists = v(.enabledLists, d.enabledLists)
        // До второй версии Ward знал только первые 12 списков.
        knownLists = v(.knownLists, ["easylist", "easyprivacy", "ubo-filters", "ubo-privacy", "ubo-badware", "urlhaus", "peterlowe",
                                     "ruadlist", "cookies", "ubo-unbreak", "adguard-url", "annoyances"])
        for list in FilterList.all where !knownLists.contains(list.id) {
            knownLists.insert(list.id)
            if list.enabledByDefault { enabledLists.insert(list.id) }
        }
        fingerprint = v(.fingerprint, d.fingerprint)
        cleanLinks = v(.cleanLinks, d.cleanLinks)
        httpsOnly = v(.httpsOnly, d.httpsOnly)
        blockWebRTC = v(.blockWebRTC, d.blockWebRTC)
        fraudWarnings = v(.fraudWarnings, d.fraudWarnings)
        clearOnQuit = v(.clearOnQuit, d.clearOnQuit)
        keepHistory = v(.keepHistory, d.keepHistory)
        clearHistoryOnQuit = v(.clearHistoryOnQuit, d.clearHistoryOnQuit)
        restoreTabs = v(.restoreTabs, d.restoreTabs)
        blockAutoplay = v(.blockAutoplay, d.blockAutoplay)
        autoUpdateLists = v(.autoUpdateLists, d.autoUpdateLists)
        developerTools = v(.developerTools, d.developerTools)
        rememberedSites = v(.rememberedSites, d.rememberedSites)
        unprotectedSites = v(.unprotectedSites, d.unprotectedSites)
        noScriptSites = v(.noScriptSites, d.noScriptSites)
        listsChecked = try? c.decodeIfPresent(Date.self, forKey: .listsChecked)
        consentReject = v(.consentReject, d.consentReject)
        cleanClosedSites = v(.cleanClosedSites, d.cleanClosedSites)
        strictMode = v(.strictMode, d.strictMode)
        strictSites = v(.strictSites, d.strictSites)
        relaxedSites = v(.relaxedSites, d.relaxedSites)
        phishingWarnings = v(.phishingWarnings, d.phishingWarnings)
        trustedLookalikes = v(.trustedLookalikes, d.trustedLookalikes)
        sponsorBlock = v(.sponsorBlock, d.sponsorBlock)
        sponsorCategories = v(.sponsorCategories, d.sponsorCategories)
        proxyMode = v(.proxyMode, d.proxyMode)
        proxyHost = v(.proxyHost, d.proxyHost)
        proxyPort = v(.proxyPort, d.proxyPort)
        cnameDetection = v(.cnameDetection, d.cnameDetection)
        spoofTimezone = v(.spoofTimezone, d.spoofTimezone)
        spoofLanguage = v(.spoofLanguage, d.spoofLanguage)
        protectHistory = v(.protectHistory, d.protectHistory)
        mediaDecisions = v(.mediaDecisions, d.mediaDecisions)
        denyMediaByDefault = v(.denyMediaByDefault, d.denyMediaByDefault)
        siteContainers = v(.siteContainers, d.siteContainers)
    }

    /// Включён ли строгий режим для сайта. `familiar` — сайт есть в истории или избранном.
    func strict(site: String, familiar: Bool) -> Bool {
        if relaxedSites.contains(site) { return false }
        if strictSites.contains(site) { return true }
        switch strictMode {
        case .off: return false
        case .unfamiliar: return !familiar
        case .all: return true
        }
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
    var consentsRejected = 0
    var sponsorSeconds = 0.0
    var phishingBlocked = 0
    var since = Date()

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func v<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T { (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback }
        total = v(.total, 0)
        byKind = v(.byKind, [:])
        byCompany = v(.byCompany, [:])
        days = v(.days, [:])
        cleanedLinks = v(.cleanedLinks, 0)
        upgraded = v(.upgraded, 0)
        consentsRejected = v(.consentsRejected, 0)
        sponsorSeconds = v(.sponsorSeconds, 0)
        phishingBlocked = v(.phishingBlocked, 0)
        since = v(.since, Date())
    }

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

/// Контейнер: отдельное хранилище cookie и данных сайтов. Сайты из разных контейнеров не видят друг друга.
nonisolated struct Container: Codable, Sendable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var color: Int

    static let defaults: [Container] = [
        Container(name: "Личное", color: 0), Container(name: "Работа", color: 1), Container(name: "Покупки", color: 2),
        Container(name: "Банк", color: 3), Container(name: "Соцсети", color: 4),
    ]
}

/// Сохранённое состояние: окна и вкладки прошлого сеанса.
nonisolated struct SavedSession: Codable, Sendable {
    nonisolated struct SavedTab: Codable, Sendable, Equatable {
        var url: URL
        var container: UUID?
    }

    var windows: [[SavedTab]]

    init(windows: [[SavedTab]]) { self.windows = windows }

    enum CodingKeys: String, CodingKey { case windows }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let tabs = try? c.decode([[SavedTab]].self, forKey: .windows) {
            windows = tabs
        } else {
            // Первая версия хранила только адреса.
            windows = try c.decode([[URL]].self, forKey: .windows).map { $0.map { SavedTab(url: $0, container: nil) } }
        }
    }
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

    mutating func remove(_ url: URL) { entries.removeAll { $0.url == url } }

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
