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
            if prefs.proxyMode != oldValue.proxyMode || prefs.proxyHost != oldValue.proxyHost || prefs.proxyPort != oldValue.proxyPort {
                applyProxyEverywhere()
            }
            if prefs.spoofLanguage != oldValue.spoofLanguage { Self.applyLanguage(prefs.spoofLanguage) }
            if prefs.fingerprint != oldValue.fingerprint || prefs.blockWebRTC != oldValue.blockWebRTC
                || prefs.developerTools != oldValue.developerTools || prefs.blockAutoplay != oldValue.blockAutoplay
                || prefs.unprotectedSites != oldValue.unprotectedSites || prefs.noScriptSites != oldValue.noScriptSites
                || prefs.consentReject != oldValue.consentReject || prefs.sponsorBlock != oldValue.sponsorBlock
                || prefs.spoofTimezone != oldValue.spoofTimezone || prefs.spoofLanguage != oldValue.spoofLanguage {
                refreshAllTabs()
            }
        }
    }
    let blocker = ContentBlocker()
    let cname = CNAMEGuard()
    private(set) var history = History()
    private(set) var favorites: [Favorite] = []
    private(set) var containers: [Container] = []
    private(set) var stats = Stats()
    let downloads = Downloads()
    private(set) var listsUpdating = false
    private(set) var listsMessage: String?

    /// Секрет защиты от отпечатка: новый при каждом запуске, свой у частных окон.
    let sessionSecret = AppModel.randomSecret()

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
    private var historySites: Set<String> = []
    /// Идёт «Стереть всё»: окна закрываются без сохранения вкладок.
    private(set) var wiping = false

    init() {
        prefs = Storage.load(Prefs.self, "prefs.json") ?? Prefs()
        history = History(SecureStore.load([HistoryEntry].self, "history.json") ?? [])
        favorites = Storage.load([Favorite].self, "favorites.json") ?? Self.defaultFavorites
        containers = Storage.load([Container].self, "containers.json") ?? Container.defaults
        stats = Storage.load(Stats.self, "stats.json") ?? Stats()
        historySites = Set(history.entries.map { Hosts.site($0.url.host ?? "") })
    }

    static var defaultFavorites: [Favorite] {
        [
            Favorite(url: URL(string: "https://duckduckgo.com")!, title: "DuckDuckGo"),
            Favorite(url: URL(string: "https://ru.wikipedia.org")!, title: "Википедия"),
            Favorite(url: URL(string: "https://github.com")!, title: "GitHub"),
            Favorite(url: URL(string: "https://www.youtube.com")!, title: "YouTube"),
        ]
    }

    static func randomSecret() -> String {
        (0..<4).map { _ in String(UInt32.random(in: .min ... .max), radix: 36) }.joined()
    }

    func start() {
        blocker.onChange = { [weak self] in self?.refreshAllTabs() }
        cname.onChange = { [weak self] in self?.refreshAllTabs() }
        blocker.prepare(lists: prefs.enabledFilterLists)
        cname.scheduleCompile(store: blocker.store)
        saveTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.saveState() }
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
    func pageScripts(host: String, protected: Bool, secret: String) -> (list: [WKUserScript], scriptlets: Int) {
        var list: [WKUserScript] = []
        var scriptlets = 0
        if protected && prefs.fingerprint {
            list.append(WKUserScript(source: Scripts.fingerprint(secret: secret, blockWebRTC: prefs.blockWebRTC,
                                                                 spoofTimezone: prefs.spoofTimezone, spoofLanguage: prefs.spoofLanguage),
                                     injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
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
            list.append(WKUserScript(source: Scripts.consent, injectionTime: .atDocumentEnd, forMainFrameOnly: false, in: .defaultClient))
        }
        if prefs.sponsorBlock {
            list.append(WKUserScript(source: Scripts.sponsorBlock, injectionTime: .atDocumentEnd, forMainFrameOnly: false, in: .defaultClient))
        }
        return (list, scriptlets)
    }

    func isUnprotected(_ host: String) -> Bool {
        prefs.unprotectedSites.contains(Hosts.site(host))
    }

    /// Сайт знаком: вы на нём бывали, он в избранном или вы помните на нём вход.
    func isFamiliar(_ site: String) -> Bool {
        historySites.contains(site) || prefs.rememberedSites.contains(site) || favorites.contains { Hosts.site($0.url.host ?? "") == site }
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
        for (id, store) in stores { if containers.contains(where: { $0.id == id }) { NetworkPrivacy.apply(to: store, isPrivate: false, prefs: prefs) } }
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

    func isFavorite(_ url: URL?) -> Bool {
        guard let url else { return false }
        return favorites.contains { $0.url == url }
    }

    func toggleFavorite(_ url: URL, title: String) {
        if let i = favorites.firstIndex(where: { $0.url == url }) { favorites.remove(at: i) }
        else { favorites.append(Favorite(url: url, title: title.isEmpty ? (url.host ?? url.absoluteString) : title)) }
        Storage.save(favorites, "favorites.json")
    }

    func removeFavorite(_ favorite: Favorite) {
        favorites.removeAll { $0.id == favorite.id }
        Storage.save(favorites, "favorites.json")
    }

    func record(_ kind: TrackerKind, host: String) {
        stats.record(kind, company: Companies.owner(of: host))
    }

    func recordCleaned() { stats.cleanedLinks += 1 }
    func recordUpgraded() { stats.upgraded += 1 }
    func recordConsent() { stats.consentsRejected += 1 }
    func recordSponsor(seconds: Double) { stats.sponsorSeconds += seconds }
    func recordPhishing() { stats.phishingBlocked += 1 }

    func resetStats() {
        stats = Stats()
        Storage.save(stats, "stats.json")
    }

    func saveState() {
        guard !wiping else { return }
        Storage.save(stats, "stats.json")
        if prefs.keepHistory { SecureStore.save(history.entries, "history.json") }
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
        SecureStore.remove("session.json")
        downloads.clearFinished()
        _ = await clearSiteData(keeping: [])
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
