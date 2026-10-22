import AppKit
import SwiftUI
import WebKit

/// Общее состояние Ward: настройки, блокировщик, история, закладки, загрузки, счётчики, окна.
@Observable
final class AppModel {
    static let shared = AppModel()

    var prefs: Prefs {
        didSet {
            guard prefs != oldValue else { return }
            Storage.save(prefs, "prefs.json")
            if prefs.enabledLists != oldValue.enabledLists { blocker.prepare(lists: prefs.enabledFilterLists) }
            if prefs.fingerprint != oldValue.fingerprint || prefs.blockWebRTC != oldValue.blockWebRTC
                || prefs.developerTools != oldValue.developerTools || prefs.blockAutoplay != oldValue.blockAutoplay
                || prefs.unprotectedSites != oldValue.unprotectedSites || prefs.noScriptSites != oldValue.noScriptSites {
                refreshAllTabs()
            }
        }
    }
    let blocker = ContentBlocker()
    private(set) var history = History()
    private(set) var favorites: [Favorite] = []
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
    var closedTabs: [URL] = []
    /// Открыть окно (действие из SwiftUI, сохраняется первым окном).
    var openWindow: ((String) -> Void)?
    /// Открыть окно настроек (демо для скриншотов).
    var settingsRequest = 0
    private var restored = false
    private var saveTimer: Timer?

    init() {
        prefs = Storage.load(Prefs.self, "prefs.json") ?? Prefs()
        history = History(Storage.load([HistoryEntry].self, "history.json") ?? [])
        favorites = Storage.load([Favorite].self, "favorites.json") ?? Self.defaultFavorites
        stats = Storage.load(Stats.self, "stats.json") ?? Stats()
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
        blocker.prepare(lists: prefs.enabledFilterLists)
        saveTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.saveState() }
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
            if prefs.restoreTabs, let session = Storage.load(SavedSession.self, "session.json") {
                let urls = session.windows.flatMap { $0 }
                if !urls.isEmpty { browser.restore(urls) }
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

    // MARK: - Конфигурация страниц

    func userScripts(secret: String, site: String?) -> [WKUserScript] {
        var scripts: [WKUserScript] = []
        let protected = site.map { !isUnprotected($0) } ?? true
        if prefs.fingerprint && protected {
            scripts.append(WKUserScript(source: Scripts.fingerprint(secret: secret, blockWebRTC: prefs.blockWebRTC),
                                        injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .page))
        }
        scripts.append(WKUserScript(source: Scripts.observer, injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        return scripts
    }

    func isUnprotected(_ host: String) -> Bool {
        let site = Hosts.site(host)
        return prefs.unprotectedSites.contains(site)
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

    /// Строка браузера как у Safari этой версии macOS: Ward не выделяется среди миллионов Safari.
    static var applicationNameForUserAgent: String {
        let major = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
        return "Version/\(major).0 Safari/605.1.15"
    }

    // MARK: - История, закладки, счётчики

    func visited(_ url: URL, title: String, isPrivate: Bool) {
        guard prefs.keepHistory, !isPrivate else { return }
        history.visit(url, title: title)
    }

    func retitle(_ url: URL, title: String, isPrivate: Bool) {
        guard prefs.keepHistory, !isPrivate else { return }
        history.retitle(url, title: title)
    }

    func clearHistory() {
        history.clear()
        Storage.remove("history.json")
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

    func resetStats() {
        stats = Stats()
        Storage.save(stats, "stats.json")
    }

    func saveState() {
        Storage.save(stats, "stats.json")
        if prefs.keepHistory { Storage.save(history.entries, "history.json") }
        saveSession()
    }

    func saveSession() {
        let normal = browsers.filter { !$0.isPrivate }
        guard !normal.isEmpty else { return }
        let windows = normal.map { $0.tabs.compactMap(\.restorableURL) }.filter { !$0.isEmpty }
        Storage.save(SavedSession(windows: windows), "session.json")
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

    /// Удалить данные одного сайта: cookie, хранилище, кэш.
    func forget(site: String) async {
        let store = WKWebsiteDataStore.default()
        let records = await Self.records(store)
        let doomed = records.filter { Hosts.site($0.displayName) == site }
        await Self.remove(store, doomed)
        history.forget(site: site)
    }

    /// При выходе: всё, кроме сайтов, где вы попросили помнить вход.
    func clearSiteData(keeping: Set<String>) async -> Int {
        let store = WKWebsiteDataStore.default()
        let records = await Self.records(store)
        let doomed = records.filter { !keeping.contains(Hosts.site($0.displayName)) }
        await Self.remove(store, doomed)
        return doomed.count
    }

    func siteDataCount() async -> Int { await Self.records(WKWebsiteDataStore.default()).count }

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
