import AppKit
import LocalAuthentication
import SwiftUI
import WebKit

/// Общее состояние Ward: настройки, блокировщик, история, закладки, контейнеры, загрузки, счётчики, окна.
@Observable
final class AppModel {
    static let shared = AppModel()

    var prefs: Prefs {
        didSet {
            guard prefs != oldValue else { return }
            Storage.save(prefs, "prefs.json")
            if prefs.enabledLists != oldValue.enabledLists { blocker.prepare(lists: prefs.enabledFilterLists) }
            if prefs.proxyMode != oldValue.proxyMode || prefs.proxyHost != oldValue.proxyHost || prefs.proxyPort != oldValue.proxyPort
                || prefs.separateCircuits != oldValue.separateCircuits {
                applyProxyEverywhere()
                refreshAllTabs()
            }
            if prefs.spoofLanguage != oldValue.spoofLanguage { Self.applyLanguage(prefs.spoofLanguage) }
            if prefs.fingerprint != oldValue.fingerprint || prefs.blockWebRTC != oldValue.blockWebRTC
                || prefs.developerTools != oldValue.developerTools || prefs.blockAutoplay != oldValue.blockAutoplay
                || prefs.unprotectedSites != oldValue.unprotectedSites || prefs.noScriptSites != oldValue.noScriptSites
                || prefs.consentReject != oldValue.consentReject || prefs.sponsorBlock != oldValue.sponsorBlock
                || prefs.blockAnnoyances != oldValue.blockAnnoyances
                || prefs.maskMode != oldValue.maskMode || prefs.corporateJars != oldValue.corporateJars
                || prefs.spoofTimezone != oldValue.spoofTimezone || prefs.spoofLanguage != oldValue.spoofLanguage
                || prefs.gpc != oldValue.gpc || prefs.trimReferrer != oldValue.trimReferrer || prefs.cookieLifetimeDays != oldValue.cookieLifetimeDays
                || prefs.blockLocalNetwork != oldValue.blockLocalNetwork || prefs.lanAllowedSites != oldValue.lanAllowedSites {
                refreshAllTabs()
            }
        }
    }
    let blocker = ContentBlocker()
    let cname = CNAMEGuard()
    private(set) var history = History()
    private(set) var bookmarks = BookmarkTree()
    private(set) var containers: [Container] = []
    private(set) var stats = Stats()
    /// Последние события защиты — живая лента панели безопасности (только в памяти).
    private(set) var recent: [SecurityEvent] = []
    /// Закладку только что добавили (⌘D, звезда) — показать её редактор в адресной строке.
    var bookmarkEditRequest: (id: UUID, count: Int)?
    let downloads = Downloads()
    private(set) var listsUpdating = false
    private(set) var listsMessage: String?

    /// Секрет защиты от отпечатка: новый при каждом запуске, свой у частных окон.
    let sessionSecret = AppModel.randomSecret()
    /// Имя сигнала «сайт снимает отпечаток» (см. `Scripts.fingerprintRelay`): новое при каждом запуске.
    let signalToken = "w" + AppModel.randomSecret()

    /// Открытые окна и последнее активное.
    private(set) var browsers: [BrowserState] = []
    weak var activeBrowser: BrowserState?
    /// Адреса, которые нужно открыть, когда появится окно (ссылки из других приложений до запуска окна).
    var pendingURLs: [URL] = []
    /// Закрытые вкладки — для ⇧⌘T.
    var closedTabs: [SavedSession.SavedTab] = []
    /// Открыть окно (действие из SwiftUI, сохраняется первым окном).
    var openWindow: ((String) -> Void)?
    /// Открыть окно настроек (демо для скриншотов).
    var settingsRequest = 0
    /// История открыта после Touch ID (до ухода Ward в фон).
    var historyUnlocked = false
    private var restored = false
    private var saveTimer: Timer?
    private var cleanupTask: Task<Void, Never>?
    private var stores: [UUID: WKWebsiteDataStore] = [:]
    /// Хранилище банки → её ключ (для прокси и смены маски).
    private var jarIDs: [UUID: String] = [:]
    private var corpRules: [String: WKContentRuleList] = [:]
    private var corpRulesCompiling: Set<String> = []
    private var historySites: Set<String> = []
    /// Идёт «Стереть всё»: окна закрываются без сохранения вкладок.
    private(set) var wiping = false
    /// Когда вы что-то делали на сайтах и через какие сайты вас «отскакивали» (зашифровано, только на этом Mac).
    private(set) var activity = SiteActivity()
    /// Собранные правила WebKit по имени: домашняя сеть, ответ сборщику отпечатка.
    private var namedRules: [String: WKContentRuleList] = [:]
    private var namedRulesCompiling: Set<String> = []
    private var maintenanceTimer: Timer?
    /// Последняя проверка правдоподобия вымышленного устройства (для панели безопасности).
    var coherence: CoherenceReport?

    init() {
        prefs = Storage.load(Prefs.self, "prefs.json") ?? Prefs()
        history = History(SecureStore.load([HistoryEntry].self, "history.json") ?? [])
        // Избранное прошлых версий становится панелью закладок.
        bookmarks = Storage.load(BookmarkTree.self, "bookmarks.json")
            ?? Storage.load([Favorite].self, "favorites.json").map(BookmarkTree.init(favorites:))
            ?? .defaults
        containers = Storage.load([Container].self, "containers.json") ?? Container.defaults
        stats = Storage.load(Stats.self, "stats.json") ?? Stats()
        if prefs.isolationSecret.isEmpty { prefs.isolationSecret = Self.randomSecret() + Self.randomSecret() }
        historySites = Set(history.entries.map { Hosts.site($0.url.host ?? "") })
        if !SelfTest.isRunning { activity = SecureStore.load(SiteActivity.self, SiteActivity.file) ?? SiteActivity() }
    }

    static func randomSecret() -> String {
        (0..<4).map { _ in String(UInt32.random(in: .min ... .max), radix: 36) }.joined()
    }

    func start() {
        blocker.onChange = { [weak self] in self?.refreshAllTabs() }
        cname.onChange = { [weak self] in self?.refreshAllTabs() }
        blocker.prepare(lists: prefs.enabledFilterLists)
        // Правила домашней сети и ответа сборщику отпечатка — собрать заранее, к первой странице.
        _ = localNetworkRules
        _ = fingerprintResponseRules
        SecurityLog.shared.prune(keepingDays: prefs.logRetentionDays)
        rotateMaskIfDue()
        cname.scheduleCompile(store: blocker.store)
        saveTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.saveState() }
        }
        // Раз в полчаса: данные сайтов-прокладок и просроченное хранилище скриптов.
        maintenanceTimer = Timer.scheduledTimer(withTimeInterval: 1800, repeats: true) { _ in
            Task { @MainActor in await AppModel.shared.maintain() }
        }
        Task {
            try? await Task.sleep(for: .seconds(120))
            await maintain()
        }
        NotificationCenter.default.addObserver(forName: NSApplication.didResignActiveNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { AppModel.shared.historyUnlocked = false }
        }
        // Свежие списки — раз в сутки, через минуту после запуска, чтобы не мешать открытию страниц.
        Task {
            try? await Task.sleep(for: .seconds(60))
            if prefs.autoUpdateLists, Date().timeIntervalSince(prefs.listsChecked ?? .distantPast) > 86_400 {
                await updateLists()
            }
        }
    }

    // MARK: - Окна

    func register(_ browser: BrowserState) {
        browsers.removeAll { $0 === browser }
        browsers.append(browser)
        if activeBrowser == nil { activeBrowser = browser }
        // Первое обычное окно получает вкладки прошлого сеанса.
        if !restored, !browser.isPrivate {
            restored = true
            if prefs.restoreTabs, !DemoUI.isOn, let session = SecureStore.load(SavedSession.self, "session.json") {
                let tabs = session.windows.flatMap { $0 }
                if !tabs.isEmpty { browser.restore(tabs) }
            }
        }
        if !pendingURLs.isEmpty {
            let urls = pendingURLs
            pendingURLs.removeAll()
            for url in urls { browser.newTab(url: url) }
        }
    }

    func unregister(_ browser: BrowserState) {
        // Закрыли последнее обычное окно — его вкладки и есть прошлый сеанс.
        let lastNormal = !browser.isPrivate && browsers.filter { !$0.isPrivate }.count == 1
        if lastNormal { saveSession() }
        browsers.removeAll { $0 === browser }
        if activeBrowser === browser { activeBrowser = browsers.last }
        if !browser.isPrivate && !lastNormal { saveSession() }
        if browser.isPrivate, !browsers.contains(where: \.isPrivate) { downloads.forgetPrivate() }
    }

    /// Ссылка из другого приложения или с Dock.
    func open(_ url: URL) {
        // Ссылки извне — в обычное окно: частное окно открывают для своего, а не для чужих ссылок.
        let normal: BrowserState? = {
            if let active = activeBrowser, !active.isPrivate { return active }
            return browsers.last(where: { b in !b.isPrivate })
        }()
        if let browser = normal {
            browser.newTab(url: url)
            browser.window?.makeKeyAndOrderFront(nil)
        } else {
            pendingURLs.append(url)
            openWindow?("browser")
        }
    }

    func refreshAllTabs() {
        for browser in browsers { for tab in browser.tabs { tab.protectionsChanged() } }
    }

    var allTabs: [Tab] { browsers.flatMap(\.tabs) }

    // MARK: - Скрипты страниц

    /// Скрипты для страницы хоста и сколько в них скриптлетов из списков.
    /// `proxied` — окно идёт через прокси: тогда WebRTC выключается сам, иначе он раскрыл бы настоящий IP в обход прокси.
    func pageScripts(host: String, protected: Bool, secret: String, proxied: Bool = false, jar: String? = nil, wrapWorkers: Bool = true) -> (list: [WKUserScript], scriptlets: Int) {
        var list: [WKUserScript] = []
        var scriptlets = 0
        if let jar {
            // Банка: вымышленное устройство, постоянное для банки. Изолятор вдобавок отказывает во всех
            // разрешениях — даже если на этом сайте защита выключена.
            let kind = Jars.kind(of: jar)
            let legend = jarLegend(jar)
            // WebRTC прячет настоящий IP только тогда, когда сайты ходят через прокси: без прокси сайт и так видит
            // ваш адрес, а исчезнувший WebRTC выдаёт «подделанный браузер» защите от ботов (YouTube, банки).
            let webRTCOff = prefs.blockWebRTC || proxied
            list.append(WKUserScript(source: Scripts.fingerprint(secret: legend.noiseSecret, blockWebRTC: webRTCOff, event: signalToken, wrapWorkers: wrapWorkers),
                                     injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
            list.append(WKUserScript(source: Isolation.script(legend, event: signalToken, deny: kind == .isolator), injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
            list.append(WKUserScript(source: Isolation.relay(token: signalToken), injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        } else if protected && (prefs.fingerprint || proxied) {
            list.append(WKUserScript(source: Scripts.fingerprint(secret: secret, blockWebRTC: prefs.blockWebRTC || proxied,
                                                                 spoofTimezone: prefs.spoofTimezone, spoofLanguage: prefs.spoofLanguage, event: signalToken, wrapWorkers: wrapWorkers),
                                     injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
        }
        if protected || jar != nil {
            // Следы: GPC, адрес, откуда пришли, срок cookie из скриптов, обращения к домашней сети.
            let lan = prefs.blockLocalNetwork && !prefs.lanAllowedSites.contains(Hosts.site(host))
            list.append(WKUserScript(source: Scripts.privacy(gpc: prefs.gpc, trimReferrer: prefs.trimReferrer, cookieDays: prefs.cookieLifetimeDays, lan: lan, event: signalToken),
                                     injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
            list.append(WKUserScript(source: Scripts.fingerprintRelay(token: signalToken), injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        }
        if protected, !host.isEmpty {
            let calls = blocker.index.scriptletCalls(for: host)
            if let source = Scripts.scriptlets(calls) {
                scriptlets = calls.count
                list.append(WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true, in: .page))
            }
            if let source = Scripts.procedural(blocker.index.proceduralSelectors(for: host)) {
                list.append(WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true, in: .defaultClient))
            }
        }
        list.append(WKUserScript(source: Scripts.observer, injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        if protected && prefs.consentReject {
            list.append(WKUserScript(source: Scripts.consent, injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        }
        if protected && prefs.blockAnnoyances {
            list.append(WKUserScript(source: Scripts.annoyances, injectionTime: .atDocumentEnd, forMainFrameOnly: true, in: .defaultClient))
        }
        if prefs.sponsorBlock {
            list.append(WKUserScript(source: Scripts.sponsorBlock, injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        }
        return (list, scriptlets)
    }

    func isUnprotected(_ host: String) -> Bool {
        prefs.unprotectedSites.contains(Hosts.site(host))
    }

    /// Сайт знаком: вы на нём бывали, он в избранном или вы помните на нём вход.
    func isFamiliar(_ site: String) -> Bool {
        historySites.contains(site) || prefs.rememberedSites.contains(site) || bookmarks.allLinks.contains { Hosts.site($0.url?.host ?? "") == site }
    }

    func setProtection(_ on: Bool, site: String) {
        if on { prefs.unprotectedSites.remove(site) } else { prefs.unprotectedSites.insert(site) }
    }

    func setJavaScript(_ on: Bool, site: String) {
        if on { prefs.noScriptSites.remove(site) } else { prefs.noScriptSites.insert(site) }
    }

    func setRemembered(_ on: Bool, site: String) {
        if on { prefs.rememberedSites.insert(site) } else { prefs.rememberedSites.remove(site) }
    }

    /// Строгий режим на сайте: включить, выключить (вопреки общей настройке) или вернуть общее правило.
    func setStrict(_ on: Bool?, site: String) {
        prefs.strictSites.remove(site)
        prefs.relaxedSites.remove(site)
        switch on {
        case true?: prefs.strictSites.insert(site)
        case false?: prefs.relaxedSites.insert(site)
        case nil: break
        }
    }

    /// Строка браузера как у Safari этой версии macOS: Ward не выделяется среди миллионов Safari.
    static var applicationNameForUserAgent: String {
        let major = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
        return "Version/\(major).0 Safari/605.1.15"
    }

    /// Язык для сайтов: английский вместо системного. WebKit берёт его из языков приложения при запуске.
    nonisolated static func applyLanguage(_ english: Bool) {
        let defaults = UserDefaults.standard
        if english {
            defaults.set(["en-US"], forKey: "AppleLanguages")
            defaults.set(true, forKey: "WardSetLanguage")
        } else if defaults.bool(forKey: "WardSetLanguage") {
            defaults.removeObject(forKey: "AppleLanguages")
            defaults.removeObject(forKey: "WardSetLanguage")
        }
    }

    // MARK: - Контейнеры и хранилища

    func store(for container: Container) -> WKWebsiteDataStore {
        if let store = stores[container.id] { return store }
        let store = WKWebsiteDataStore(forIdentifier: container.id)
        NetworkPrivacy.apply(to: store, isPrivate: false, prefs: prefs)
        stores[container.id] = store
        return store
    }

    func container(forSite site: String) -> Container? {
        guard let id = prefs.siteContainers[site] else { return nil }
        return containers.first { $0.id == id }
    }

    func setContainer(_ container: Container?, forSite site: String) {
        prefs.siteContainers[site] = container?.id
    }

    @discardableResult
    func addContainer(name: String) -> Container {
        let c = Container(name: name, color: containers.count % 8)
        containers.append(c)
        Storage.save(containers, "containers.json")
        return c
    }

    func renameContainer(_ c: Container, to name: String) {
        guard let i = containers.firstIndex(of: c) else { return }
        containers[i].name = name
        Storage.save(containers, "containers.json")
    }

    /// Удалить контейнер вместе со всеми его cookie и данными.
    func deleteContainer(_ c: Container) async {
        for tab in allTabs where tab.container?.id == c.id { tab.browser?.close(tab) }
        containers.removeAll { $0.id == c.id }
        prefs.siteContainers = prefs.siteContainers.filter { $0.value != c.id }
        Storage.save(containers, "containers.json")
        stores[c.id] = nil
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            WKWebsiteDataStore.remove(forIdentifier: c.id) { _ in cont.resume() }
        }
    }

    /// Все постоянные хранилища: общее и контейнеры.
    var persistentStores: [(id: UUID?, store: WKWebsiteDataStore)] {
        var list: [(id: UUID?, store: WKWebsiteDataStore)] = [(id: nil, store: .default())]
        for c in containers { list.append((id: c.id, store: store(for: c))) }
        return list
    }

    private func applyProxyEverywhere() {
        NetworkPrivacy.apply(to: .default(), isPrivate: false, prefs: prefs)
        for (id, store) in stores {
            if containers.contains(where: { $0.id == id }) { NetworkPrivacy.apply(to: store, isPrivate: false, prefs: prefs) }
            if let jar = jarIDs[id] { NetworkPrivacy.apply(to: store, isPrivate: false, prefs: prefs, circuit: circuitKey(jar)) }
        }
        for browser in browsers where browser.isPrivate { NetworkPrivacy.apply(to: browser.dataStore, isPrivate: true, prefs: prefs) }
        for tab in allTabs where tab.ephemeral { NetworkPrivacy.apply(to: tab.dataStore, isPrivate: true, prefs: prefs) }
    }

    // MARK: - История, закладки, счётчики

    func visited(_ url: URL, title: String, isPrivate: Bool) {
        guard prefs.keepHistory, !isPrivate else { return }
        history.visit(url, title: title)
        historySites.insert(Hosts.site(url.host ?? ""))
    }

    func retitle(_ url: URL, title: String, isPrivate: Bool) {
        guard prefs.keepHistory, !isPrivate else { return }
        history.retitle(url, title: title)
    }

    func clearHistory() {
        history.clear()
        historySites = []
        SecureStore.remove("history.json")
    }

    func removeHistory(_ entry: HistoryEntry) {
        history.remove(entry.url)
        SecureStore.save(history.entries, "history.json")
    }

    /// Открыть историю: при включённой защите — после Touch ID или пароля Mac.
    func unlockHistory() async -> Bool {
        if !prefs.protectHistory || historyUnlocked || DemoUI.isOn { historyUnlocked = true; return true }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { return false }
        let ok = (try? await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "открыть историю Ward")) ?? false
        historyUnlocked = ok
        return ok
    }

    // MARK: Закладки

    /// Закладки на панели (без папок) — плитки новой вкладки.
    var favorites: [Bookmark] { bookmarks.bar.filter { !$0.isFolder } }

    func isBookmarked(_ url: URL?) -> Bool {
        guard let url else { return false }
        return bookmarks.contains(url)
    }

    func bookmark(for url: URL?) -> Bookmark? { url.flatMap { bookmarks.bookmark(for: $0) } }

    private func changeBookmarks(_ change: (inout BookmarkTree) -> Void) {
        change(&bookmarks)
        Storage.save(bookmarks, "bookmarks.json")
    }

    /// ⌘D и звезда: страница уже в закладках — открыть её редактор, нет — добавить на панель и открыть редактор.
    func bookmarkPage(_ url: URL, title: String) {
        let id: UUID
        if let existing = bookmarks.bookmark(for: url) {
            id = existing.id
        } else {
            let b = Bookmark.link(url, title: title)
            changeBookmarks { $0.insert(b, into: .bar) }
            id = b.id
        }
        bookmarkEditRequest = (id, (bookmarkEditRequest?.count ?? 0) + 1)
    }

    @discardableResult
    func addBookmark(_ url: URL, title: String, to place: BookmarkPlace = .bar, at index: Int? = nil) -> Bookmark {
        let b = Bookmark.link(url, title: title)
        changeBookmarks { $0.insert(b, into: place, at: index) }
        return b
    }

    @discardableResult
    func addFolder(_ title: String, to place: BookmarkPlace = .bar, at index: Int? = nil) -> Bookmark {
        let f = Bookmark.folder(title.isEmpty ? "Новая папка" : title)
        changeBookmarks { $0.insert(f, into: place, at: index) }
        return f
    }

    func updateBookmark(_ id: UUID, title: String, url: URL?) {
        changeBookmarks { tree in
            tree.update(id) { b in
                b.title = title
                if !b.isFolder, let url { b.url = url }
            }
        }
    }

    func moveBookmark(_ id: UUID, to place: BookmarkPlace, at index: Int? = nil) {
        changeBookmarks { tree in tree.move(id, to: place, at: index ?? tree.items(in: place).count) }
    }

    func removeBookmark(_ id: UUID) {
        changeBookmarks { _ = $0.remove(id) }
    }

    func importBookmarks(_ result: BookmarkImport.Result, source: String) -> Int {
        var added = 0
        changeBookmarks { added = $0.merge(result, source: source) }
        return added
    }

    // MARK: Счётчики и лента событий

    private func feed(_ kind: SecurityEvent.Kind, _ title: String, _ detail: String = "") {
        let event = SecurityEvent(kind: kind, title: title, detail: detail)
        recent.insert(event, at: 0)
        if recent.count > 60 { recent.removeLast(recent.count - 60) }
        if prefs.securityLog { SecurityLog.shared.append(LogEntry(date: event.date, kind: kind.rawValue, title: title, detail: detail)) }
    }

    func record(_ kind: TrackerKind, host: String, page: String = "") {
        let company = Companies.owner(of: host)
        stats.record(kind, company: company)
        let what: SecurityEvent.Kind = kind == .malware ? .danger : kind == .tracking ? .tracker : .ad
        feed(what, "\(kind.title): \(company)", page.isEmpty ? host : "\(host) на \(page)")
    }

    func recordCloaked(_ host: String, target: String, page: String) {
        stats.cloaked += 1
        stats.record(.tracking, company: Companies.owner(of: target))
        feed(.cloaked, "Скрытый трекер \(Companies.owner(of: target))", "\(host) → \(target)")
    }

    func recordCleaned(_ params: [String] = [], host: String = "") {
        stats.cleanedLinks += 1
        for p in params { Stats.bump(&stats.paramsRemoved, p.lowercased(), limit: 200) }
        feed(.link, "Ссылка очищена", ([host] + params.prefix(4)).filter { !$0.isEmpty }.joined(separator: " · "))
    }

    func recordUpgraded(_ host: String = "") {
        stats.upgraded += 1
        feed(.https, "Защищённое соединение", host)
    }

    func recordConsent(_ cmp: String = "", site: String = "") {
        stats.consentsRejected += 1
        feed(.consent, "Отказ от cookie", [site, cmp].filter { !$0.isEmpty }.joined(separator: " · "))
    }

    // MARK: - Следы: отскоки, домашняя сеть, Referer, cookie, отпечаток

    /// Правила WebKit, собранные один раз по имени (ключ включает версию правил).
    func namedRuleList(_ key: String, _ json: @autoclosure () -> String) -> WKContentRuleList? {
        if let list = namedRules[key] { return list }
        guard !namedRulesCompiling.contains(key) else { return nil }
        namedRulesCompiling.insert(key)
        let source = json()
        Task {
            switch await blocker.compile("ward-\(key)", source) {
            case .success(let list):
                namedRules[key] = list
                refreshAllTabs()
            case .failure(let error):
                print("Ward: правила \(key): \(error)")
            }
            namedRulesCompiling.remove(key)
        }
        return nil
    }

    var localNetworkRules: WKContentRuleList? { namedRuleList("lan-\(LocalNetwork.rulesVersion)", LocalNetwork.ruleList) }
    var fingerprintResponseRules: WKContentRuleList? { namedRuleList("fp-response-\(FingerprintResponse.rulesVersion)", FingerprintResponse.ruleList) }

    /// Вы что-то нажали или напечатали на сайте.
    func recordActivation(_ site: String) {
        guard !site.isEmpty else { return }
        activity.activated(site)
    }

    /// Хранилище сайта впервые замечено — от этой даты считается срок, если вы на сайте ничего не делали.
    func noteSeen(_ site: String, at date: Date = Date()) {
        if activity.firstSeen[site] == nil { activity.firstSeen[site] = date }
    }

    /// Вас перебросили через сайты-прокладки по пути с `origin` на `final`. Известные трекеры (по спискам)
    /// и сайты, где вы ничего не делали 45 дней, забываются: их данные удаляются сразу — во всех хранилищах
    /// и в хранилище этой вкладки (`store`).
    func recordBounce(_ sites: [String], from origin: String, to final: String, store: WKWebsiteDataStore?, noTrace: Bool) {
        guard prefs.bounceProtection, !sites.isEmpty else { return }
        let now = Date()
        for site in sites {
            stats.bounces += 1
            if !noTrace { Stats.bump(&stats.bounceSites, site) }
            activity.bounces[site] = now
            feed(.bounce, "Отскок через \(noTrace ? "сайт-прокладку" : site)", noTrace ? "" : [origin, final].filter { !$0.isEmpty }.joined(separator: " → "))
        }
        Task { await purgeBounceTrackers(only: Set(sites), extra: store) }
    }

    /// Сайт-прокладку можно забыть: вы на нём ничего не делали 45 дней, он не открыт и не в «Помнить вход».
    func bouncePurgeable(_ site: String, now: Date = Date()) -> Bool {
        guard !site.isEmpty, !prefs.rememberedSites.contains(site), !isIsolated(site) else { return false }
        if activity.interacted(site, within: 45, now: now) { return false }
        return !allTabs.contains { $0.site == site }
    }

    /// Удалить данные сайтов-прокладок. `only` — только эти сайты (сразу после отскока), иначе все известные.
    @discardableResult
    func purgeBounceTrackers(only: Set<String>? = nil, extra: WKWebsiteDataStore? = nil, now: Date = Date()) async -> Int {
        guard prefs.bounceProtection else { return 0 }
        let candidates = (only ?? Set(activity.bounces.keys)).filter { bouncePurgeable($0, now: now) }
        guard !candidates.isEmpty else { return 0 }
        var removed = 0
        var stores = allPersistentStores.map(\.store)
        if let extra, !stores.contains(where: { $0 === extra }) { stores.append(extra) }
        for store in stores {
            let doomed = await Self.records(store).filter { candidates.contains(Hosts.site($0.displayName)) }
            removed += doomed.count
            await Self.remove(store, doomed)
        }
        for site in candidates { activity.bounces[site] = nil }
        if removed > 0 {
            stats.bouncePurged += removed
            feed(.bounce, "Данные сайтов-прокладок удалены", "\(removed) \(Format.plural(removed, "запись", "записи", "записей"))")
        }
        return removed
    }

    /// Хранилище скриптов (localStorage, IndexedDB, Service Worker, кэш) сайтов, где вы не нажимали и не печатали
    /// `prefs.storageLifetimeDays` дней, удаляется. Cookie (и вход) не трогаются. Открытые сайты — тоже.
    @discardableResult
    func enforceStorageLifetime(now: Date = Date()) async -> Int {
        let days = prefs.storageLifetimeDays
        guard days > 0 else { return 0 }
        let types: Set<String> = [WKWebsiteDataTypeLocalStorage, WKWebsiteDataTypeIndexedDBDatabases, WKWebsiteDataTypeServiceWorkerRegistrations,
                                  WKWebsiteDataTypeFetchCache, WKWebsiteDataTypeWebSQLDatabases]
        let open = Set(allTabs.map(\.site))
        var purged = Set<String>()
        for (_, store) in allPersistentStores {
            let records: [WKWebsiteDataRecord] = await withCheckedContinuation { (c: CheckedContinuation<RecordsBox, Never>) in
                store.fetchDataRecords(ofTypes: types) { list in
                    let box = RecordsBox()
                    box.records = list
                    c.resume(returning: box)
                }
            }.records
            var doomed: [WKWebsiteDataRecord] = []
            for record in records {
                let site = Hosts.site(record.displayName)
                guard !site.isEmpty, !open.contains(site) else { continue }
                noteSeen(site, at: now)
                guard let last = activity.lastUse(site), now.timeIntervalSince(last) > Double(days) * 86_400 else { continue }
                doomed.append(record)
                purged.insert(site)
            }
            if !doomed.isEmpty {
                await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                    store.removeData(ofTypes: types, for: doomed) { c.resume() }
                }
            }
        }
        if !purged.isEmpty {
            stats.storagePurged += purged.count
            feed(.storage, "Просроченное хранилище удалено", "\(purged.count) \(Format.plural(purged.count, "сайт", "сайта", "сайтов")) без ваших действий \(days) \(Format.plural(days, "день", "дня", "дней"))")
        }
        return purged.count
    }

    /// Плановая уборка следов.
    func maintain() async {
        await purgeBounceTrackers()
        await enforceStorageLifetime()
    }

    /// Хранилища на диске: общее, контейнеры и открытые за этот запуск банки.
    var allPersistentStores: [(id: UUID?, store: WKWebsiteDataStore)] {
        var list = persistentStores
        for (id, store) in stores where jarIDs[id] != nil { list.append((id: id, store: store)) }
        return list
    }

    /// Сайт из интернета пытался обратиться к устройству домашней сети.
    func recordLAN(_ address: String, site: String, noTrace: Bool) {
        stats.lanBlocked += 1
        if !noTrace { Stats.bump(&stats.lanSites, site) }
        feed(.lan, "Домашняя сеть: обращение остановлено", noTrace ? address : "\(site) → \(address)")
    }

    /// Урезанный след: адрес, откуда пришли, или cookie «навсегда».
    func recordTrace(_ kind: TraceSignal) {
        switch kind {
        case .referrer: stats.referrersTrimmed += 1
        case .cookie: stats.cookiesCapped += 1
        case .lan: break
        }
    }

    /// Скрипт со стороннего адреса снимал отпечаток (`first` — первый раз на этой странице).
    func recordFingerprintScript(_ script: String) {
        Stats.bump(&stats.fingerprintScripts, script, limit: 200)
    }

    /// Страница всерьёз снимает отпечаток (набрала порог детектора).
    func recordFingerprintAlert(site: String, scripts: [String], techniques: Int, blocked: Bool) {
        let who = scripts.isEmpty ? site : scripts.prefix(3).joined(separator: ", ")
        feed(.fingerprint, "Сборщик отпечатка: \(techniques) \(Format.plural(techniques, "способ", "способа", "способов"))\(blocked ? " — отправка остановлена" : "")", who)
    }

    func recordNoJIT() { stats.noJITPages += 1 }

    /// «Скопировать чистую ссылку»: без меток слежки и переходников.
    func copyCleanLink(_ url: URL) {
        let clean = CleanLink.clean(url, cleaner: blocker.cleaner)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(clean.absoluteString, forType: .string)
        if clean != url { recordCleaned([], host: "") }
    }

    // MARK: - Банки: изолятор, корпорации, маска

    func isIsolated(_ site: String) -> Bool { !site.isEmpty && prefs.isolatedSites.contains(site) }

    /// В какой банке открывать адрес: изолятор сайта, банка корпорации, маска — или nil (обычное хранилище).
    func jar(forHost host: String, privateWindow: Bool, ephemeral: Bool) -> String? {
        guard !privateWindow, !ephemeral, !host.isEmpty, !Hosts.isLocal(host) || isIsolated(Hosts.site(host)) else { return nil }
        let site = Hosts.site(host)
        if isIsolated(site) { return site }
        if prefs.corporateJars, let corp = Jars.corporation(of: host) { return "corp:" + corp.id }
        if prefs.maskMode, !site.isEmpty, !Hosts.isLocal(host) { return "mask:" + site }
        return nil
    }

    /// Идентификатор хранилища банки. Маски меняются при «смене маски», кроме сайтов «Помнить вход».
    func jarStoreID(_ jar: String) -> UUID {
        switch Jars.kind(of: jar) {
        case .isolator: return Isolation.storeID(for: jar)
        case .corporation: return Jars.uuid("ward-corp|" + Jars.subject(of: jar))
        case .mask:
            let site = Jars.subject(of: jar)
            return prefs.rememberedSites.contains(site) ? Jars.uuid("ward-mask|" + site) : Jars.uuid("ward-mask|\(prefs.maskEpoch)|" + site)
        }
    }

    /// Вымышленное устройство банки: постоянное для изолятора и корпорации, своё для каждой маски.
    func jarLegend(_ jar: String) -> Isolation.Legend {
        switch Jars.kind(of: jar) {
        case .isolator: return Isolation.legend(site: jar, secret: prefs.isolationSecret)
        case .corporation: return Isolation.legend(site: jar, secret: prefs.isolationSecret)
        case .mask:
            let site = Jars.subject(of: jar)
            let epoch = prefs.rememberedSites.contains(site) ? 0 : prefs.maskEpoch
            return Isolation.legend(site: site, secret: prefs.isolationSecret + "|mask|\(epoch)")
        }
    }

    /// Хранилище банки: постоянное и ни с кем не общее.
    func jarStore(for jar: String) -> WKWebsiteDataStore {
        let id = jarStoreID(jar)
        if let store = stores[id] { return store }
        let store = WKWebsiteDataStore(forIdentifier: id)
        NetworkPrivacy.apply(to: store, isPrivate: false, prefs: prefs, circuit: circuitKey(jar))
        stores[id] = store
        jarIDs[id] = jar
        if Jars.kind(of: jar) == .mask, !prefs.maskStores.contains(id.uuidString) {
            prefs.maskStores.append(id.uuidString)
            stats.masksIssued += 1
        }
        return store
    }

    /// Имя «цепочки» для прокси: свой выход в сеть у каждой банки (Tor различает цепочки по имени SOCKS).
    func circuitKey(_ jar: String) -> String? {
        guard prefs.separateCircuits else { return nil }
        return "ward-" + jarStoreID(jar).uuidString.prefix(12).lowercased()
    }

    /// Правила корпоративной банки: запросы к другим корпорациям не уходят. Собираются один раз и хранятся.
    func corporationRules(_ corp: String) -> WKContentRuleList? {
        if let list = corpRules[corp] { return list }
        guard !corpRulesCompiling.contains(corp) else { return nil }
        corpRulesCompiling.insert(corp)
        let identifier = "ward-corp-\(corp)-\(Jars.rulesVersion)"
        let json = Jars.ruleList(blockingAllBut: corp)
        Task {
            switch await blocker.compile(identifier, json) {
            case .success(let list):
                corpRules[corp] = list
                refreshAllTabs()
            case .failure(let error):
                print("Ward: правила корпорации \(corp): \(error)")
            }
            corpRulesCompiling.remove(corp)
        }
        return nil
    }

    /// Корпорация из своей банки пыталась обратиться к другой корпорации — запрос не ушёл.
    func recordCorporationBlock(from corp: String, to other: String, host: String) {
        stats.corporationBlocks[corp, default: 0] += 1
        feed(.corporation, "\(Jars.corporation(id: corp)?.name ?? corp) → \(other): запрос не ушёл", host)
    }

    /// Сменить маску: у всех сайтов — новые вымышленные личности и чистые хранилища (кроме «Помнить вход»).
    func rotateMask() async {
        let old = prefs.maskStores
        prefs.maskEpoch += 1
        prefs.maskStores = []
        prefs.maskRotatedAt = Date()
        for browser in browsers {
            for tab in browser.tabs {
                guard let jar = tab.jar, Jars.kind(of: jar) == .mask, let u = tab.url else { continue }
                tab.rehome(to: jar, url: u, remember: false)
            }
        }
        for raw in old {
            guard let id = UUID(uuidString: raw), jarIDs[id].map({ !prefs.rememberedSites.contains(Jars.subject(of: $0)) }) ?? true else { continue }
            stores[id] = nil
            jarIDs[id] = nil
            await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
                WKWebsiteDataStore.remove(forIdentifier: id) { _ in cont.resume() }
            }
        }
        feed(.mask, "Маска сменена", "у всех сайтов новые личности")
    }

    /// Смена маски по расписанию — при запуске.
    func rotateMaskIfDue() {
        guard prefs.maskMode, prefs.maskRotation > 0 else { return }
        let last = prefs.maskRotatedAt ?? .distantPast
        if Date().timeIntervalSince(last) > Double(prefs.maskRotation) * 86_400 { Task { await rotateMask() } }
    }

    /// Поместить сайт в изолятор (открытые вкладки сайта переезжают в банку) или выпустить из него.
    /// Выпущенный сайт теряет всё, что накопил в банке: вход, cookie, хранилище.
    func setIsolated(_ on: Bool, site: String) async {
        guard !site.isEmpty else { return }
        if on {
            prefs.isolatedSites.insert(site)
            prefs.isolationDeclined.remove(site)
            for browser in browsers where !browser.isPrivate {
                for tab in browser.tabs where tab.site == site && tab.jar != site && !tab.ephemeral { browser.isolate(tab) }
            }
        } else {
            prefs.isolatedSites.remove(site)
            for browser in browsers {
                for tab in browser.tabs where tab.jar == site {
                    if let u = tab.url { tab.rehome(to: jar(forHost: u.host?.lowercased() ?? "", privateWindow: false, ephemeral: false), url: u, remember: false) }
                }
            }
            await removeIsolationStore(site)
        }
    }

    private func removeIsolationStore(_ site: String) async {
        let id = Isolation.storeID(for: site)
        stores[id] = nil
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            WKWebsiteDataStore.remove(forIdentifier: id) { _ in cont.resume() }
        }
    }

    func declineIsolation(_ site: String) { prefs.isolationDeclined.insert(site) }

    func recordIsolation(_ what: String, site: String) {
        guard Isolation.Attempt(rawValue: what) != nil, !site.isEmpty else { return }
        stats.isolation[site, default: [:]][what, default: 0] += 1
        let title = Isolation.Attempt(rawValue: what)?.title ?? what
        if what != Isolation.Attempt.trackers.rawValue { feed(.isolation, "Изолятор: \(title.lowercased()) — отказано", site) }
    }

    var isolationAttemptsTotal: Int { stats.isolation.values.reduce(0) { $0 + $1.values.reduce(0, +) } }

    func recordAnnoyance(_ kind: String, site: String = "") {
        stats.annoyancesHidden += 1
        let what = switch kind {
        case "modal": "Окно «подпишитесь»"
        case "backdrop": "Затемнение поверх статьи"
        case "bottom-sheet", "top-bar": "Плашка поверх статьи"
        case "gate": "Окно «создайте аккаунт или войдите» вместо статьи"
        default: "Всплывающее окно"
        }
        feed(.annoyance, what + " убрано", site)
    }

    func recordSponsor(seconds: Double, category: String = "") {
        stats.sponsorSeconds += seconds
        feed(.sponsor, "Вставка в ролике пропущена", "\(Int(seconds.rounded())) с")
    }

    func recordPhishing(_ host: String = "") {
        stats.phishingBlocked += 1
        feed(.phishing, "Подделка под известный сайт", host)
    }

    func recordDanger(_ host: String) {
        stats.dangerBlocked += 1
        feed(.danger, "Опасный сайт остановлен", host)
    }

    func recordFingerprint(_ technique: String, site: String, first: Bool) {
        stats.recordFingerprint(site: site, technique: technique, first: first)
        let title = FingerprintTechnique(rawValue: technique)?.title ?? technique
        feed(.fingerprint, "Попытка узнать вас: \(title.lowercased())", site)
    }

    func recordHidden(_ count: Int, site: String) {
        guard count > 0 else { return }
        stats.hiddenElements += count
        feed(.hidden, "Скрыто \(count) \(Format.plural(count, "элемент", "элемента", "элементов"))", site)
    }

    func recordPage() { stats.pages += 1 }

    func resetStats() {
        stats = Stats()
        recent = []
        Storage.save(stats, "stats.json")
    }

    func saveState() {
        guard !wiping else { return }
        Storage.save(stats, "stats.json")
        if prefs.keepHistory { SecureStore.save(history.entries, "history.json") }
        if !SelfTest.isRunning { SecureStore.save(activity, SiteActivity.file) }
        saveSession()
    }

    func saveSession() {
        guard !wiping else { return }
        let normal = browsers.filter { !$0.isPrivate }
        guard !normal.isEmpty else { return }
        let windows = normal.map(\.savedTabs).filter { !$0.isEmpty }
        SecureStore.save(SavedSession(windows: windows), "session.json")
    }

    // MARK: - Списки фильтров

    /// Скачать свежие списки; если что-то изменилось — пересобрать правила.
    func updateLists() async {
        guard !listsUpdating else { return }
        listsUpdating = true
        listsMessage = "Обновляю списки…"
        var changed = 0
        var failed: [String] = []
        for list in prefs.enabledFilterLists {
            do {
                if try await FilterStorage.download(list) { changed += 1 }
            } catch {
                failed.append(list.title)
            }
        }
        prefs.listsChecked = Date()
        listsUpdating = false
        if changed > 0 { blocker.prepare(lists: prefs.enabledFilterLists) }
        listsMessage = failed.isEmpty
            ? (changed == 0 ? "Списки уже свежие" : "Обновлено: \(changed) \(Format.plural(changed, "список", "списка", "списков"))")
            : "Не скачались: \(failed.joined(separator: ", "))"
    }

    // MARK: - Данные сайтов

    /// Удалить данные одного сайта во всех хранилищах: cookie, хранилище, кэш.
    func forget(site: String) async {
        for (_, store) in persistentStores {
            let records = await Self.records(store)
            await Self.remove(store, records.filter { Hosts.site($0.displayName) == site })
        }
        history.forget(site: site)
        historySites.remove(site)
    }

    /// Удалить данные всех сайтов, кроме перечисленных. Возвращает, сколько сайтов удалено.
    func clearSiteData(keeping: Set<String>) async -> Int {
        var count = 0
        for (_, store) in persistentStores {
            let records = await Self.records(store)
            let doomed = records.filter { !keeping.contains(Hosts.site($0.displayName)) }
            count += doomed.count
            await Self.remove(store, doomed)
        }
        return count
    }

    func siteDataCount() async -> Int {
        var n = 0
        for (_, store) in persistentStores { n += await Self.records(store).count }
        return n
    }

    /// Уборка за закрытыми сайтами: через 15 секунд после закрытия вкладки удаляются данные всех сайтов,
    /// которые не открыты ни в одной вкладке своего хранилища (и не в списке «Помнить вход»). Заодно уходят
    /// cookie трекеров, которые сайт успел поставить.
    func scheduleSiteCleanup() {
        guard prefs.cleanClosedSites else { return }
        cleanupTask?.cancel()
        cleanupTask = Task {
            try? await Task.sleep(for: .seconds(15))
            if Task.isCancelled { return }
            _ = await cleanupClosedSites()
        }
    }

    @discardableResult
    func cleanupClosedSites() async -> Int {
        var removed = 0
        for (id, store) in persistentStores {
            let open = Set(allTabs.filter { !$0.isPrivate && !$0.ephemeral && $0.container?.id == id }.map { $0.site }.filter { !$0.isEmpty })
            let keep = open.union(prefs.rememberedSites)
            let records = await Self.records(store)
            let doomed = records.filter { !keep.contains(Hosts.site($0.displayName)) }
            removed += doomed.count
            await Self.remove(store, doomed)
        }
        if removed > 0 {
            stats.sitesCleaned += removed
            feed(.cleanup, "Данные закрытых сайтов убраны", "\(removed) \(Format.plural(removed, "сайт", "сайта", "сайтов"))")
        }
        return removed
    }

    nonisolated private final class RecordsBox: @unchecked Sendable { var records: [WKWebsiteDataRecord] = [] }

    static func records(_ store: WKWebsiteDataStore) async -> [WKWebsiteDataRecord] {
        let box: RecordsBox = await withCheckedContinuation { (c: CheckedContinuation<RecordsBox, Never>) in
            store.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
                let b = RecordsBox()
                b.records = records
                c.resume(returning: b)
            }
        }
        return box.records
    }

    static func remove(_ store: WKWebsiteDataStore, _ records: [WKWebsiteDataRecord]) async {
        guard !records.isEmpty else { return }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records) { c.resume() }
        }
    }

    /// Выход: сохранить, что нужно, и стереть, что попросили.
    func shutdown() async {
        saveState()
        if prefs.clearHistoryOnQuit { clearHistory() }
        if prefs.clearOnQuit { _ = await clearSiteData(keeping: prefs.rememberedSites) }
    }

    /// «Стереть всё»: закрыть все окна (и частные), удалить данные всех сайтов во всех контейнерах,
    /// историю, вкладки прошлого сеанса и найденные скрытые трекеры. Настройки и избранное остаются.
    func wipeEverything() async {
        wiping = true
        for browser in browsers { browser.wipe() }
        closedTabs.removeAll()
        clearHistory()
        activity = SiteActivity()
        SecureStore.remove(SiteActivity.file)
        SecureStore.remove("session.json")
        downloads.clearFinished()
        _ = await clearSiteData(keeping: [])
        // Банки изолятора тоже: «стереть всё» значит всё.
        for site in prefs.isolatedSites { await removeIsolationStore(site) }
        cname.forgetAll()
        wiping = false
        openWindow?("browser")
    }

    // MARK: - Браузер по умолчанию

    var isDefaultBrowser: Bool {
        guard let url = URL(string: "https://example.com"), let app = NSWorkspace.shared.urlForApplication(toOpen: url) else { return false }
        return app.standardizedFileURL.path == Bundle.main.bundleURL.standardizedFileURL.path
    }

    func makeDefaultBrowser() {
        let app = Bundle.main.bundleURL
        NSWorkspace.shared.setDefaultApplication(at: app, toOpenURLsWithScheme: "http") { _ in }
        NSWorkspace.shared.setDefaultApplication(at: app, toOpenURLsWithScheme: "https") { _ in }
    }
}

/// Событие защиты для живой ленты панели безопасности.
struct SecurityEvent: Identifiable, Equatable {
    enum Kind: String, CaseIterable {
        case tracker, ad, danger, cloaked, fingerprint, hidden, link, https, consent, sponsor, phishing, cleanup, annoyance, isolation, corporation, mask,
             bounce, lan, storage

        /// Название вида для журнала и фильтров.
        var title: String {
            switch self {
            case .tracker: "Слежка"
            case .ad: "Реклама"
            case .danger: "Опасный сайт"
            case .cloaked: "Скрытый трекер"
            case .fingerprint: "Отпечаток"
            case .hidden: "Скрытые элементы"
            case .link: "Очистка ссылок"
            case .https: "HTTPS"
            case .consent: "Отказ от cookie"
            case .sponsor: "SponsorBlock"
            case .phishing: "Подделка сайта"
            case .cleanup: "Уборка данных"
            case .annoyance: "Плашки"
            case .isolation: "Изолятор"
            case .corporation: "Корпорации"
            case .mask: "Маска"
            case .bounce: "Отскоки"
            case .lan: "Домашняя сеть"
            case .storage: "Срок данных"
            }
        }

        var icon: String {
            switch self {
            case .tracker: "eye.slash.fill"
            case .ad: "rectangle.slash.fill"
            case .danger: "exclamationmark.octagon.fill"
            case .cloaked: "theatermasks.fill"
            case .fingerprint: "touchid"
            case .hidden: "eye.trianglebadge.exclamationmark"
            case .link: "scissors"
            case .https: "lock.fill"
            case .consent: "hand.raised.fill"
            case .sponsor: "forward.end.fill"
            case .phishing: "exclamationmark.shield.fill"
            case .cleanup: "trash.fill"
            case .annoyance: "rectangle.on.rectangle.slash.fill"
            case .isolation: "cube.transparent.fill"
            case .corporation: "building.2.crop.circle"
            case .mask: "theatermasks.circle.fill"
            case .bounce: "arrow.triangle.turn.up.right.diamond.fill"
            case .lan: "wifi.router.fill"
            case .storage: "hourglass"
            }
        }

        var color: Color {
            switch self {
            case .tracker, .cloaked: .purple
            case .ad, .hidden: .blue
            case .danger, .phishing: .red
            case .fingerprint: .pink
            case .link: .indigo
            case .https: .green
            case .consent: .orange
            case .sponsor: .teal
            case .cleanup: .gray
            case .annoyance: .mint
            case .isolation: .teal
            case .corporation: .orange
            case .mask: .purple
            case .bounce: .indigo
            case .lan: .red
            case .storage: .brown
            }
        }
    }

    let id = UUID()
    let date = Date()
    let kind: Kind
    let title: String
    let detail: String
}
