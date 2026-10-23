import AppKit
import SwiftUI
import WebKit

/// Одно окно: вкладки, выбранная вкладка, хранилище данных.
///
/// Частное окно хранит cookie и кэш только в памяти: закрыли окно — всё исчезло. Защита от отпечатка
/// у него со своим секретом, так что сайт не свяжет частное окно с обычным.
@Observable
final class BrowserState {
    let id = UUID()
    let isPrivate: Bool
    let dataStore: WKWebsiteDataStore
    let secret: String
    private(set) var tabs: [Tab] = []
    var selectedID: UUID?
    var findVisible = false
    var findText = ""
    /// Сайты без HTTPS, которые вы разрешили открыть в этом окне.
    var httpAllowed: Set<String> = []
    /// Опасные сайты, которые вы решили открыть.
    var dangerAllowed: Set<String> = []
    /// Просьба поставить курсор в адресную строку (новая вкладка, ⌘L). Счётчик окна, а не вкладки:
    /// у новой вкладки свой счётчик начинается с того же числа, и строка адреса не заметила бы просьбы.
    var addressFocus = 0
    @ObservationIgnored weak var window: NSWindow?

    init(isPrivate: Bool) {
        self.isPrivate = isPrivate
        dataStore = isPrivate ? .nonPersistent() : .default()
        secret = isPrivate ? AppModel.randomSecret() : AppModel.shared.sessionSecret
        NetworkPrivacy.apply(to: dataStore, isPrivate: isPrivate, prefs: AppModel.shared.prefs)
        newTab()
    }

    var selected: Tab? { tabs.first { $0.id == selectedID } ?? tabs.first }

    /// Идёт ли это окно через прокси.
    var usesProxy: Bool { NetworkPrivacy.usesProxy(AppModel.shared.prefs, isPrivate: isPrivate) }

    @discardableResult
    func newTab(url: URL? = nil, select: Bool = true, after: Tab? = nil, container: Container? = nil, ephemeral: Bool = false, jar: String? = nil) -> Tab {
        let tab = Tab(browser: self, url: url, container: container, ephemeral: ephemeral, jar: jar ?? self.jar(for: url, ephemeral: ephemeral))
        insert(tab, after: after)
        if select || tabs.count == 1 { self.select(tab) }
        if url == nil { focusAddressSoon() }
        return tab
    }

    /// Новая пустая вкладка: курсор сразу в поле адреса — можно печатать без щелчка.
    func focusAddressSoon() {
        addressFocus += 1
        Task { @MainActor [weak self] in
            for delay in [30, 120, 300, 600, 1000] {
                try? await Task.sleep(for: .milliseconds(delay))
                guard let window = self?.window else { continue }
                if AddressFocus.focus(in: window) { return }
            }
        }
    }

    /// Окно, которое открыла страница (window.open, «Открыть в новом окне»): то же хранилище, что у неё.
    func popup(configuration: WKWebViewConfiguration, opener: Tab) -> Tab {
        let tab = Tab(browser: self, popup: configuration, container: opener.container, ephemeral: opener.ephemeral, jar: opener.jar)
        insert(tab, after: opener)
        select(tab)
        return tab
    }

    private func insert(_ tab: Tab, after: Tab?) {
        if let after, let i = tabs.firstIndex(where: { $0 === after }) {
            // Новые вкладки от одной страницы идут подряд за ней.
            tabs.insert(tab, at: min(tabs.count, i + 1))
        } else {
            tabs.append(tab)
        }
    }

    func restore(_ saved: [SavedSession.SavedTab]) {
        let fresh = tabs.filter { $0.isStartPage }
        tabs.removeAll { t in fresh.contains { $0 === t } }
        fresh.forEach { $0.close() }
        for item in saved {
            let container = item.container.flatMap { id in AppModel.shared.containers.first { $0.id == id } }
            let tab = Tab(browser: self, container: container, jar: jar(for: item.url, ephemeral: false))
            tab.pendingURL = item.url
            tabs.append(tab)
        }
        if tabs.isEmpty { newTab() } else { select(tabs[0]) }
    }

    var savedTabs: [SavedSession.SavedTab] {
        tabs.compactMap { tab in tab.restorableURL.map { SavedSession.SavedTab(url: $0, container: tab.container?.id) } }
    }

    func select(_ tab: Tab) {
        selectedID = tab.id
        tab.wake()
        findVisible = false
    }

    func select(index: Int) {
        guard !tabs.isEmpty else { return }
        select(tabs[max(0, min(tabs.count - 1, index))])
    }

    func selectLast() { if let last = tabs.last { select(last) } }

    func selectNeighbour(_ offset: Int) {
        guard let current = selected, let i = tabs.firstIndex(where: { $0 === current }), !tabs.isEmpty else { return }
        select(tabs[(i + offset + tabs.count) % tabs.count])
    }

    /// Закрыть вкладку. Последняя вкладка закрывает окно.
    func close(_ tab: Tab) {
        guard let i = tabs.firstIndex(where: { $0 === tab }) else { return }
        if !isPrivate, let u = tab.restorableURL {
            AppModel.shared.closedTabs.append(SavedSession.SavedTab(url: u, container: tab.container?.id))
            if AppModel.shared.closedTabs.count > 30 { AppModel.shared.closedTabs.removeFirst() }
        }
        let wasSelected = selected === tab
        tabs.remove(at: i)
        tab.close()
        AppModel.shared.scheduleSiteCleanup()
        if tabs.isEmpty {
            window?.close()
            return
        }
        if wasSelected { select(tabs[min(i, tabs.count - 1)]) }
    }

    func closeSelected() { if let tab = selected { close(tab) } }

    func closeOthers(_ keep: Tab) {
        for tab in tabs where tab !== keep { tab.close() }
        tabs = [keep]
        select(keep)
        AppModel.shared.scheduleSiteCleanup()
    }

    func reopenClosed() {
        guard let saved = AppModel.shared.closedTabs.popLast() else { return }
        let container = saved.container.flatMap { id in AppModel.shared.containers.first { $0.id == id } }
        newTab(url: saved.url, container: container)
    }

    /// Банка, в которую попадает адрес (изолятор, корпорация, маска).
    func jar(for url: URL?, ephemeral: Bool) -> String? {
        guard let host = url?.host?.lowercased() else { return nil }
        return AppModel.shared.jar(forHost: host, privateWindow: isPrivate, ephemeral: ephemeral)
    }

    /// Перенести страницу вкладки в её изолятор — на месте.
    func isolate(_ tab: Tab) {
        guard !isPrivate, let u = tab.url ?? tab.pendingURL else { return }
        let site = Hosts.site(u.host?.lowercased() ?? "")
        guard !site.isEmpty else { return }
        if tab.webViewIfLoaded == nil, tab.pendingURL != nil { tab.wake(); return }
        tab.rehome(to: site, url: u, remember: false)
    }

    /// Открыть ту же страницу в другом контейнере (или одноразовой вкладке).
    func reopen(_ tab: Tab, container: Container?, ephemeral: Bool = false) {
        guard let u = tab.url ?? tab.pendingURL else { newTab(container: container, ephemeral: ephemeral); return }
        newTab(url: u, after: tab, container: container, ephemeral: ephemeral)
    }

    func move(_ tab: Tab, to index: Int) {
        guard let from = tabs.firstIndex(where: { $0 === tab }) else { return }
        tabs.remove(at: from)
        tabs.insert(tab, at: max(0, min(tabs.count, index)))
    }

    /// Ввод из адресной строки.
    func go(_ input: String, in tab: Tab? = nil) {
        guard let target = Omnibox.url(from: input, search: AppModel.shared.prefs.search) else { return }
        // Банку выбирает сама вкладка: адрес из другой банки — переезд на месте.
        (tab ?? selected ?? newTab()).load(target)
    }

    func showFind() {
        findVisible = true
    }

    /// Окно закрыто: остановить все страницы.
    func closedWindow() {
        for tab in tabs { tab.close() }
        AppModel.shared.unregister(self)
        AppModel.shared.scheduleSiteCleanup()
    }

    /// Закрыть все вкладки без сохранения (кнопка «Стереть всё»).
    func wipe() {
        for tab in tabs { tab.close() }
        tabs = []
        window?.close()
    }
}

extension FocusedValues {
    @Entry var browser: BrowserState?
}
