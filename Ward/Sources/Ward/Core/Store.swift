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
    var showBookmarksBar = true
    /// Прятать плашки «Подпишитесь», «Отключите блокировщик», рассылки и возвращать прокрутку.
    var blockAnnoyances = true
    /// Сайты, которые всегда открываются в изоляторе (см. `Isolation`).
    var isolatedSites: Set<String> = []
    /// Сайты, для которых вы отказались от предложения изолятора.
    var isolationDeclined: Set<String> = []
    /// Предлагать изолятор банкам, госуслугам, маркетплейсам и соцсетям.
    var suggestIsolation = true
    /// Постоянный секрет «легенды» устройства в изоляторе (создаётся при первом запуске).
    var isolationSecret = ""
    /// Корпоративные банки: каждая крупная корпорация (Google, Яндекс, VK, Microsoft…) — в своём хранилище,
    /// без связи с другими корпорациями.
    var corporateJars = true
    /// «Маска»: у каждого сайта своя вымышленная личность и своё хранилище.
    var maskMode = false
    /// Номер маски: растёт при смене маски.
    var maskEpoch = 0
    /// Менять маску раз в N дней (0 — только вручную).
    var maskRotation = 0
    var maskRotatedAt: Date?
    /// Хранилища масок текущего номера — чтобы удалить их при смене.
    var maskStores: [String] = []
    /// Отдельная цепочка прокси (Tor) для каждой банки.
    var separateCircuits = false
    /// Журнал безопасности: все события защиты в файлах на этом Mac.
    var securityLog = true
    var logRetentionDays = 30
    /// Сигнал «не продавать мои данные» (Global Privacy Control): заголовок Sec-GPC и navigator.globalPrivacyControl.
    var gpc = true
    /// Другому сайту — только адрес сайта, с которого вы пришли, без страницы и параметров.
    var trimReferrer = true
    /// Срок жизни cookie, которые ставят скрипты страницы (0 — без ограничения).
    var cookieLifetimeDays = 7
    /// Хранилище скриптов (localStorage, IndexedDB, Service Worker) сайта, где вы ничего не делали N дней, удаляется.
    var storageLifetimeDays = 7
    /// Сайты из интернета не достучатся до устройств домашней сети и сервисов на этом Mac.
    var blockLocalNetwork = true
    /// Сайты, которым разрешена домашняя сеть.
    var lanAllowedSites: Set<String> = []
    /// Защита от «отскоков»: данные сайтов-прокладок, через которые вас перебрасывали, удаляются.
    var bounceProtection = true
    /// Незнакомые сайты и изолятор — без JIT-компиляции JavaScript (macOS 26.4+).
    var noJIT = true
    /// Страница, которая снимает отпечаток всерьёз, больше ничего не отправляет на сторонние адреса.
    var fingerprintResponse = false

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
        showBookmarksBar = v(.showBookmarksBar, d.showBookmarksBar)
        blockAnnoyances = v(.blockAnnoyances, d.blockAnnoyances)
        isolatedSites = v(.isolatedSites, d.isolatedSites)
        isolationDeclined = v(.isolationDeclined, d.isolationDeclined)
        suggestIsolation = v(.suggestIsolation, d.suggestIsolation)
        isolationSecret = v(.isolationSecret, d.isolationSecret)
        corporateJars = v(.corporateJars, d.corporateJars)
        maskMode = v(.maskMode, d.maskMode)
        maskEpoch = v(.maskEpoch, d.maskEpoch)
        maskRotation = v(.maskRotation, d.maskRotation)
        maskRotatedAt = try? c.decodeIfPresent(Date.self, forKey: .maskRotatedAt)
        maskStores = v(.maskStores, d.maskStores)
        separateCircuits = v(.separateCircuits, d.separateCircuits)
        securityLog = v(.securityLog, d.securityLog)
        logRetentionDays = v(.logRetentionDays, d.logRetentionDays)
        gpc = v(.gpc, d.gpc)
        trimReferrer = v(.trimReferrer, d.trimReferrer)
        cookieLifetimeDays = v(.cookieLifetimeDays, d.cookieLifetimeDays)
        storageLifetimeDays = v(.storageLifetimeDays, d.storageLifetimeDays)
        blockLocalNetwork = v(.blockLocalNetwork, d.blockLocalNetwork)
        lanAllowedSites = v(.lanAllowedSites, d.lanAllowedSites)
        bounceProtection = v(.bounceProtection, d.bounceProtection)
        noJIT = v(.noJIT, d.noJIT)
        fingerprintResponse = v(.fingerprintResponse, d.fingerprintResponse)
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
    // Новое в третьей версии — для панели безопасности.
    /// Страницы, которые пытались снять отпечаток браузера, сайты-рекордсмены и способы.
    var fingerprintPages = 0
    var fingerprintSites: [String: Int] = [:]
    var fingerprintTechniques: [String: Int] = [:]
    /// Скрытые рекламные блоки, баннеры и кнопки «поделиться».
    var hiddenElements = 0
    /// Трекеры под видом поддомена сайта (CNAME).
    var cloaked = 0
    /// Опасные сайты из списков вредоносного, остановленные до первого запроса.
    var dangerBlocked = 0
    /// Сайты, чьи cookie и данные убраны после закрытия вкладки.
    var sitesCleaned = 0
    /// Страницы, открытые под защитой.
    var pages = 0
    /// Плашки и окна поверх статьи: «Подпишитесь», «Отключите блокировщик», рассылки.
    var annoyancesHidden = 0
    /// Изолятор: сайт → что он пытался получить → сколько раз.
    var isolation: [String: [String: Int]] = [:]
    /// Корпоративные банки: корпорация → сколько её обращений к другим корпорациям не ушло.
    var corporationBlocks: [String: Int] = [:]
    /// Сколько вымышленных личностей выдано «Маской».
    var masksIssued = 0
    /// «Отскоки»: сколько раз вас перебрасывали через сайт-прокладку, через какие сайты, сколько их данных удалено.
    var bounces = 0
    var bounceSites: [String: Int] = [:]
    var bouncePurged = 0
    /// Обращения сайтов из интернета к устройствам домашней сети: всего и с каких сайтов.
    var lanBlocked = 0
    var lanSites: [String: Int] = [:]
    /// Урезанные следы: адрес, откуда пришли, и cookie скриптов со сроком «навсегда».
    var referrersTrimmed = 0
    var cookiesCapped = 0
    /// Сайты, чьё хранилище скриптов удалено по сроку (вы ничего не делали на них неделю).
    var storagePurged = 0
    /// Страницы, открытые без JIT.
    var noJITPages = 0
    /// Скрипты, которые снимали отпечаток (адрес скрипта → на скольких страницах).
    var fingerprintScripts: [String: Int] = [:]
    /// Какие метки вырезаны из ссылок и сколько раз.
    var paramsRemoved: [String: Int] = [:]

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
        fingerprintPages = v(.fingerprintPages, 0)
        fingerprintSites = v(.fingerprintSites, [:])
        fingerprintTechniques = v(.fingerprintTechniques, [:])
        hiddenElements = v(.hiddenElements, 0)
        cloaked = v(.cloaked, 0)
        dangerBlocked = v(.dangerBlocked, 0)
        sitesCleaned = v(.sitesCleaned, 0)
        pages = v(.pages, 0)
        annoyancesHidden = v(.annoyancesHidden, 0)
        isolation = v(.isolation, [:])
        corporationBlocks = v(.corporationBlocks, [:])
        masksIssued = v(.masksIssued, 0)
        bounces = v(.bounces, 0)
        bounceSites = v(.bounceSites, [:])
        bouncePurged = v(.bouncePurged, 0)
        lanBlocked = v(.lanBlocked, 0)
        lanSites = v(.lanSites, [:])
        referrersTrimmed = v(.referrersTrimmed, 0)
        cookiesCapped = v(.cookiesCapped, 0)
        storagePurged = v(.storagePurged, 0)
        noJITPages = v(.noJITPages, 0)
        fingerprintScripts = v(.fingerprintScripts, [:])
        paramsRemoved = v(.paramsRemoved, [:])
    }

    /// Добавить к счётчику по ключу, не давая словарю разрастись.
    static func bump(_ map: inout [String: Int], _ key: String, by n: Int = 1, limit: Int = 300) {
        guard !key.isEmpty else { return }
        map[key, default: 0] += n
        if map.count > limit {
            for (k, _) in map.sorted(by: { $0.value < $1.value }).prefix(map.count - limit * 2 / 3) { map[k] = nil }
        }
    }

    static func dayKey(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    var today: Int { days[Self.dayKey()] ?? 0 }

    nonisolated struct Day: Sendable, Identifiable {
        var date: Date
        var count: Int
        var id: Date { date }
    }

    /// Последние `count` дней по порядку, включая сегодняшний (дни без трекеров — нули).
    func lastDays(_ count: Int, now: Date = Date()) -> [Day] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        return (0..<count).reversed().compactMap { back in
            guard let day = calendar.date(byAdding: .day, value: -back, to: today) else { return nil }
            return Day(date: day, count: days[Self.dayKey(day)] ?? 0)
        }
    }

    /// Попытка снять отпечаток: `first` — первая на этой странице (тогда считается страница и сайт).
    mutating func recordFingerprint(site: String, technique: String, first: Bool) {
        fingerprintTechniques[technique, default: 0] += 1
        guard first else { return }
        fingerprintPages += 1
        fingerprintSites[site, default: 0] += 1
        if fingerprintSites.count > 300 {
            for (key, _) in fingerprintSites.sorted(by: { $0.value < $1.value }).prefix(fingerprintSites.count - 200) { fingerprintSites[key] = nil }
        }
    }

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
