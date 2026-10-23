import AppKit
import WebKit

/// Экран вместо страницы: опасный сайт, подделка, сайт без HTTPS, заблокированная страница, ошибка.
enum Interstitial: Equatable {
    case danger(URL)
    case lookalike(URL, String)
    case insecure(URL)
    case blocked(URL)
    case failed(URL, String)

    var url: URL {
        switch self {
        case .danger(let u), .lookalike(let u, _), .insecure(let u), .blocked(let u), .failed(let u, _): u
        }
    }
}

/// Вкладка: страница WebKit и всё, что Ward о ней знает.
@Observable
final class Tab: NSObject, Identifiable {
    let id = UUID()
    @ObservationIgnored weak var browser: BrowserState?
    /// Контейнер вкладки (nil — общее хранилище окна).
    let container: Container?
    /// Одноразовая вкладка: своё хранилище в памяти, исчезает вместе с вкладкой.
    let ephemeral: Bool

    var title = ""
    var url: URL?
    var progress = 0.0
    var isLoading = false
    var canGoBack = false
    var canGoForward = false
    var hasOnlySecureContent = false
    var interstitial: Interstitial?
    /// Сторонние адреса этой страницы, которые есть в списках: хост → вид.
    var trackers: [String: TrackerKind] = [:]
    /// Скрытые трекеры (CNAME): поддомен сайта → настоящее имя трекера.
    var cloaked: [String: String] = [:]
    /// Что убрано из адреса этой страницы.
    var cleanedParams: [String] = []
    var unwrappedRedirect = false
    var upgradedToHTTPS = false
    var protectedHere = true
    var javaScriptOn = true
    var strictHere = false
    var scriptletCount = 0
    /// Окно согласия на cookie, в котором Ward нажал «Отклонить».
    var consentRejected: String?
    var sponsorSkipped = 0
    var passwordWarning = false
    var certificate: CertificateInfo?
    var zoom = 1.0 { didSet { webViewIfLoaded?.pageZoom = zoom } }
    /// Вкладка из прошлого сеанса: страница загрузится, когда вкладку откроют.
    var pendingURL: URL?
    var findMatched: Bool?
    /// Счётчик для фокуса адресной строки (новая вкладка, ⌘L).
    var focusRequest = 0

    @ObservationIgnored private(set) var webViewIfLoaded: WKWebView?
    /// Сколько загрузок закончилось (успехом или ошибкой) — для самопроверок.
    @ObservationIgnored private(set) var loadsFinished = 0
    @ObservationIgnored private var observations: [NSKeyValueObservation] = []
    @ObservationIgnored private var proxy: ScriptProxy?
    @ObservationIgnored private var upgradedFrom: URL?
    @ObservationIgnored private var pendingClean: LinkCleaner.Result?
    @ObservationIgnored private var cleanGuard: (url: URL, time: Date)?
    @ObservationIgnored private var appliedHost: String?
    @ObservationIgnored private var appliedProtection: Bool?
    @ObservationIgnored private var allowOnce: URL?
    @ObservationIgnored private var popupConfiguration: WKWebViewConfiguration?
    @ObservationIgnored private var mediaAllowed: Set<String> = []
    @ObservationIgnored private var ephemeralStore: WKWebsiteDataStore?
    @ObservationIgnored private var cnameChecks = 0

    init(browser: BrowserState, url: URL? = nil, popup configuration: WKWebViewConfiguration? = nil, container: Container? = nil, ephemeral: Bool = false) {
        self.browser = browser
        self.container = browser.isPrivate ? nil : container
        self.ephemeral = ephemeral && !browser.isPrivate
        popupConfiguration = configuration
        super.init()
        if configuration != nil { _ = webView }
        if let url { load(url) }
    }

    var isStartPage: Bool { url == nil && pendingURL == nil && interstitial == nil && !isLoading }

    var displayTitle: String {
        if !title.isEmpty { return title }
        if let host = (url ?? pendingURL)?.siteHost, !host.isEmpty { return Punycode.display(host).text }
        return ephemeral ? "Одноразовая вкладка" : "Новая вкладка"
    }

    var host: String { (url ?? pendingURL)?.host?.lowercased() ?? "" }
    var site: String { Hosts.site(host) }
    var isPrivate: Bool { browser?.isPrivate ?? false }

    var restorableURL: URL? {
        guard !ephemeral, let u = url ?? pendingURL, let scheme = u.scheme, scheme == "http" || scheme == "https" else { return nil }
        return u
    }

    var blockedCount: Int { trackers.count }

    /// Хранилище cookie и данных этой вкладки.
    var dataStore: WKWebsiteDataStore {
        if ephemeral {
            if let ephemeralStore { return ephemeralStore }
            let store = WKWebsiteDataStore.nonPersistent()
            NetworkPrivacy.apply(to: store, isPrivate: true, prefs: AppModel.shared.prefs)
            ephemeralStore = store
            return store
        }
        if let container { return AppModel.shared.store(for: container) }
        return browser?.dataStore ?? .default()
    }

    // MARK: - Страница

    var webView: WKWebView {
        if let existing = webViewIfLoaded { return existing }
        let configuration = popupConfiguration ?? makeConfiguration()
        popupConfiguration = nil
        // У каждой вкладки свои правила и скрипты: защиту можно выключить на одном сайте, не трогая другие.
        configuration.userContentController = WKUserContentController()
        let view = WKWebView(frame: .zero, configuration: configuration)
        setUp(view)
        webViewIfLoaded = view
        return view
    }

    private func makeConfiguration() -> WKWebViewConfiguration {
        let model = AppModel.shared
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = dataStore
        configuration.applicationNameForUserAgent = AppModel.applicationNameForUserAgent
        configuration.upgradeKnownHostsToHTTPS = true
        configuration.preferences.isFraudulentWebsiteWarningEnabled = model.prefs.fraudWarnings
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.preferences.isElementFullscreenEnabled = true
        configuration.mediaTypesRequiringUserActionForPlayback = model.prefs.blockAutoplay ? .all : []
        configuration.defaultWebpagePreferences.preferredContentMode = .desktop
        return configuration
    }

    private func setUp(_ view: WKWebView) {
        view.navigationDelegate = self
        view.uiDelegate = self
        view.allowsBackForwardNavigationGestures = true
        view.allowsMagnification = true
        view.isInspectable = AppModel.shared.prefs.developerTools
        view.pageZoom = zoom
        let proxy = ScriptProxy()
        proxy.tab = self
        self.proxy = proxy
        let controller = view.configuration.userContentController
        controller.add(proxy, contentWorld: .defaultClient, name: "wardHosts")
        controller.add(proxy, contentWorld: .defaultClient, name: "wardEvent")
        let sponsor = SponsorProxy()
        sponsor.tab = self
        controller.addScriptMessageHandler(sponsor, contentWorld: .defaultClient, name: "wardSponsor")
        configureProtections(for: url, force: true, in: view)

        func watch<V>(_ path: KeyPath<WKWebView, V>) -> NSKeyValueObservation {
            view.observe(path, options: []) { @Sendable [weak self] _, _ in
                DispatchQueue.main.async { MainActor.assumeIsolated { self?.sync() } }
            }
        }
        observations = [
            watch(\.title), watch(\.url), watch(\.estimatedProgress), watch(\.isLoading),
            watch(\.canGoBack), watch(\.canGoForward), watch(\.hasOnlySecureContent),
        ]
    }

    private func sync() {
        guard let view = webViewIfLoaded else { return }
        let newTitle = view.title ?? ""
        if newTitle != title {
            title = newTitle
            if let u = view.url { AppModel.shared.retitle(u, title: newTitle, isPrivate: isPrivate || ephemeral) }
        }
        if let u = view.url, u.absoluteString != "about:blank", interstitial == nil { url = u }
        progress = view.estimatedProgress
        isLoading = view.isLoading
        canGoBack = view.canGoBack
        canGoForward = view.canGoForward
        hasOnlySecureContent = view.hasOnlySecureContent
    }

    /// Правила блокировки и скрипты защиты — для сайта, который сейчас откроется.
    private func configureProtections(for url: URL?, force: Bool = false, in view: WKWebView? = nil) {
        guard let view = view ?? webViewIfLoaded else { return }
        let model = AppModel.shared
        let host = url?.host?.lowercased() ?? ""
        let on = !model.isUnprotected(host)
        guard force || host != appliedHost || on != appliedProtection else { return }
        let controller = view.configuration.userContentController
        model.blocker.apply(to: controller, enabled: on)
        if on, let cname = model.cname.ruleList { controller.add(cname) }
        controller.removeAllUserScripts()
        let scripts = model.pageScripts(host: host, protected: on, secret: browser?.secret ?? model.sessionSecret)
        for script in scripts.list { controller.addUserScript(script) }
        scriptletCount = scripts.scriptlets
        appliedHost = host
        appliedProtection = on
        protectedHere = on
    }

    /// Изменились правила или настройки защиты.
    func protectionsChanged() {
        guard let view = webViewIfLoaded else { return }
        view.isInspectable = AppModel.shared.prefs.developerTools
        configureProtections(for: url, force: true)
    }

    func load(_ target: URL) {
        interstitial = nil
        pendingURL = nil
        url = target
        Task {
            await AppModel.shared.blocker.waitReady(timeout: 30)
            configureProtections(for: target)
            webView.load(URLRequest(url: target))
        }
    }

    /// Открыть вкладку из прошлого сеанса.
    func wake() {
        if let pending = pendingURL, webViewIfLoaded == nil { load(pending) }
    }

    func reload() {
        if let interstitial {
            load(interstitial.url)
            return
        }
        guard let view = webViewIfLoaded else { if let pendingURL { load(pendingURL) }; return }
        configureProtections(for: url, force: true)
        view.reload()
    }

    func stop() { webViewIfLoaded?.stopLoading() }
    func goBack() {
        if interstitial != nil, webViewIfLoaded?.url != nil { interstitial = nil; sync(); return }
        webViewIfLoaded?.goBack()
    }
    func goForward() { webViewIfLoaded?.goForward() }

    func printPage() {
        guard let view = webViewIfLoaded, let window = browser?.window else { return }
        let operation = view.printOperation(with: NSPrintInfo.shared)
        operation.view?.frame = view.bounds
        operation.runModal(for: window, delegate: nil, didRun: nil, contextInfo: nil)
    }

    /// Перед закрытием: остановить страницу и разорвать связи.
    func close() {
        guard let view = webViewIfLoaded else { return }
        view.stopLoading()
        observations.removeAll()
        view.configuration.userContentController.removeAllScriptMessageHandlers()
        view.navigationDelegate = nil
        view.uiDelegate = nil
        view.removeFromSuperview()
        webViewIfLoaded = nil
        ephemeralStore = nil
    }

    // MARK: - Решения экранов-предупреждений

    /// «Всё равно открыть» опасный сайт.
    func proceedDanger(_ target: URL) {
        browser?.dangerAllowed.insert(target.host?.lowercased() ?? "")
        allowOnce = target
        interstitial = nil
        webView.load(URLRequest(url: target))
    }

    /// «Это не подделка» — сайт больше не проверяется на сходство.
    func proceedLookalike(_ target: URL) {
        AppModel.shared.prefs.trustedLookalikes.insert(Hosts.site(target.host?.lowercased() ?? ""))
        allowOnce = target
        interstitial = nil
        webView.load(URLRequest(url: target))
    }

    /// Сайт не работает по HTTPS — открыть без шифрования (до конца сеанса для этого сайта).
    func proceedInsecure(_ target: URL) {
        browser?.httpAllowed.insert(target.host?.lowercased() ?? "")
        allowOnce = target
        interstitial = nil
        webView.load(URLRequest(url: target))
    }

    /// Страница целиком в списках блокировки — открыть один раз без защиты.
    func proceedBlocked(_ target: URL) {
        allowOnce = target
        interstitial = nil
        let view = webView
        AppModel.shared.blocker.apply(to: view.configuration.userContentController, enabled: false)
        appliedProtection = false
        protectedHere = false
        view.load(URLRequest(url: target))
    }

    // MARK: - Поиск на странице

    func find(_ text: String, backwards: Bool = false) {
        guard let view = webViewIfLoaded, !text.isEmpty else { findMatched = nil; return }
        let configuration = WKFindConfiguration()
        configuration.backwards = backwards
        configuration.wraps = true
        configuration.caseSensitive = false
        view.find(text, configuration: configuration) { [weak self] result in
            let found = result.matchFound
            Task { @MainActor in self?.findMatched = found }
        }
    }

    // MARK: - Сообщения от скриптов Ward

    fileprivate func received(_ message: WKScriptMessage) {
        guard let body = message.body as? [String: Any] else { return }
        if message.name == "wardEvent" { event(body); return }
        guard message.name == "wardHosts" else { return }
        let hosts = (body["hosts"] as? [String]) ?? []
        let failed = Set((body["failed"] as? [String]) ?? [])
        let page = url?.host?.lowercased() ?? ""
        guard !page.isEmpty, protectedHere else { return }
        let model = AppModel.shared
        let index = model.blocker.index
        let pageSite = Hosts.site(page)
        for host in (hosts + failed).prefix(800) {
            let h = host.lowercased()
            if Hosts.site(h) == pageSite {
                // Поддомен самого сайта: проверить, не псевдоним ли это трекера.
                if h != page, model.prefs.cnameDetection, cnameChecks < 40, !NetworkPrivacy.usesProxy(model.prefs, isPrivate: isPrivate || ephemeral) {
                    cnameChecks += 1
                    model.cname.inspect(h, page: page, index: index) { [weak self] target, kind in
                        guard let self, self.trackers[h] == nil else { return }
                        self.trackers[h] = kind
                        self.cloaked[h] = target
                        if !(self.isPrivate || self.ephemeral) { model.record(kind, host: target) }
                    }
                }
                continue
            }
            guard trackers[h] == nil, let kind = index.classify(h, page: page, failed: failed.contains(host)) else { continue }
            trackers[h] = kind
            if !(isPrivate || ephemeral) { model.record(kind, host: h) }
        }
    }

    private func event(_ body: [String: Any]) {
        let model = AppModel.shared
        switch body["type"] as? String {
        case "consent":
            consentRejected = (body["cmp"] as? String) ?? "окно согласия"
            model.recordConsent()
        case "sponsor":
            sponsorSkipped += 1
            model.recordSponsor(seconds: (body["seconds"] as? Double) ?? 0)
        case "password-http":
            if url?.scheme == "http", !Hosts.isLocal(host) { passwordWarning = true }
        default:
            break
        }
    }

    fileprivate func sponsorRequest(_ message: WKScriptMessage) async -> Any? {
        let model = AppModel.shared
        guard model.prefs.sponsorBlock, let body = message.body as? [String: Any], let id = body["videoID"] as? String else { return [] }
        return await SponsorBlockClient.shared.segments(videoID: id, prefs: model.prefs)
    }

    // MARK: - Внешние ссылки

    private func openExternally(_ target: URL) {
        guard let window = browser?.window else { return }
        let alert = NSAlert()
        alert.messageText = "Открыть в другом приложении?"
        alert.informativeText = "Страница хочет открыть ссылку «\(target.scheme ?? "")»: \(target.absoluteString.prefix(120))"
        alert.addButton(withTitle: "Открыть")
        alert.addButton(withTitle: "Отмена")
        alert.beginSheetModal(for: window) { response in
            if response == .alertFirstButtonReturn { NSWorkspace.shared.open(target) }
        }
    }
}

// MARK: - Навигация

extension Tab: WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, preferences: WKWebpagePreferences) async -> (WKNavigationActionPolicy, WKWebpagePreferences) {
        if action.shouldPerformDownload { return (.download, preferences) }
        guard let target = action.request.url else { return (.cancel, preferences) }
        let scheme = target.scheme?.lowercased() ?? ""
        if !["http", "https", "about", "data", "blob", "file"].contains(scheme) {
            // mailto:, tel:, ссылки на приложения — только по нажатию и после вопроса.
            if action.navigationType == .linkActivated { openExternally(target) }
            return (.cancel, preferences)
        }
        guard action.targetFrame?.isMainFrame ?? true else { return (.allow, preferences) }

        // ⌘-клик и средняя кнопка — в новую вкладку (того же контейнера).
        if action.navigationType == .linkActivated, action.modifierFlags.contains(.command) || action.buttonNumber == 2 {
            browser?.newTab(url: target, select: action.modifierFlags.contains(.shift), after: self, container: container)
            return (.cancel, preferences)
        }

        let model = AppModel.shared
        let host = target.host?.lowercased() ?? ""
        let site = Hosts.site(host)
        let bypass = allowOnce == target
        if bypass { allowOnce = nil }
        let web = scheme == "http" || scheme == "https"
        let protected = !model.isUnprotected(host)

        // Сайт закреплён за контейнером — открыть его там.
        if web, !isPrivate, !ephemeral, let wanted = model.container(forSite: site), wanted.id != container?.id {
            browser?.newTab(url: target, select: true, after: self, container: wanted)
            if url == nil && !canGoBack { browser?.close(self) }
            return (.cancel, preferences)
        }

        if web && !bypass && protected {
            let isGET = (action.request.httpMethod ?? "GET").uppercased() == "GET"
            // 1. Метки слежки и переходники.
            if model.prefs.cleanLinks, isGET, action.navigationType != .backForward, action.navigationType != .reload,
               let result = model.blocker.cleaner.clean(target) {
                let looping = cleanGuard.map { $0.url == result.url && Date().timeIntervalSince($0.time) < 5 } ?? false
                if !looping {
                    cleanGuard = (result.url, Date())
                    pendingClean = result
                    model.recordCleaned()
                    navigate(result.url)
                    return (.cancel, preferences)
                }
            }
            // 2. Опасные адреса — до того, как браузер к ним обратится.
            if model.blocker.index.isDangerous(target), !(browser?.dangerAllowed.contains(host) ?? false) {
                url = target
                interstitial = .danger(target)
                return (.cancel, preferences)
            }
            // 3. Подделка под известный сайт.
            if model.prefs.phishingWarnings, !model.prefs.trustedLookalikes.contains(site), !model.isFamiliar(site),
               let original = Lookalike.imitated(host: host) {
                url = target
                interstitial = .lookalike(target, original)
                model.recordPhishing()
                return (.cancel, preferences)
            }
            // 4. Только HTTPS: сначала пробуем защищённое соединение.
            if scheme == "http", model.prefs.httpsOnly, isGET, !Hosts.isLocal(host), !(browser?.httpAllowed.contains(host) ?? false) {
                if let from = upgradedFrom, from.host?.lowercased() == host, from == target {
                    // Сайт сам вернул нас на http — не спорим по кругу, но и не скрываем.
                    browser?.httpAllowed.insert(host)
                } else if var comps = URLComponents(url: target, resolvingAgainstBaseURL: false) {
                    comps.scheme = "https"
                    if comps.port == 80 { comps.port = nil }
                    if let secure = comps.url {
                        upgradedFrom = target
                        model.recordUpgraded()
                        navigate(secure)
                        return (.cancel, preferences)
                    }
                }
            }
        }

        configureProtections(for: target)
        let jsOn = !model.prefs.noScriptSites.contains(site)
        preferences.allowsContentJavaScript = jsOn
        javaScriptOn = jsOn
        // Строгий режим: WebKit без JIT, WebGL, сложных шрифтов и других путей для эксплойтов.
        let strict = web && model.prefs.strict(site: site, familiar: model.isFamiliar(site))
        preferences.isLockdownModeEnabled = strict
        strictHere = strict
        return (.allow, preferences)
    }

    private func navigate(_ target: URL) {
        Task { @MainActor in
            self.url = target
            self.webView.load(URLRequest(url: target))
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        if !response.canShowMIMEType { return .download }
        if response.isForMainFrame, let http = response.response as? HTTPURLResponse,
           let disposition = http.value(forHTTPHeaderField: "Content-Disposition"), disposition.lowercased().hasPrefix("attachment") {
            return .download
        }
        return .allow
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        AppModel.shared.downloads.add(download, window: browser?.window)
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        AppModel.shared.downloads.add(download, window: browser?.window)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        interstitial = nil
        sync()
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        trackers = [:]
        cloaked = [:]
        consentRejected = nil
        passwordWarning = false
        sponsorSkipped = 0
        cnameChecks = 0
        let current = webView.url
        if let clean = pendingClean, clean.url.host == current?.host {
            cleanedParams = clean.removed
            unwrappedRedirect = clean.unwrapped
        } else {
            cleanedParams = []
            unwrappedRedirect = false
        }
        pendingClean = nil
        upgradedToHTTPS = upgradedFrom != nil && current?.scheme == "https" && current?.host == upgradedFrom?.host
        upgradedFrom = nil
        findMatched = nil
        certificate = webView.serverTrust.flatMap(CertificateInfo.from)
        sync()
        AppModel.shared.scheduleSiteCleanup()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loadsFinished += 1
        sync()
        if certificate == nil { certificate = webView.serverTrust.flatMap(CertificateInfo.from) }
        if let u = webView.url { AppModel.shared.visited(u, title: webView.title ?? "", isPrivate: isPrivate || ephemeral) }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let ns = error as NSError
        if ns.domain == "WebKitErrorDomain", ns.code == 104 { failed(error) }
        sync()
    }

    private func failed(_ error: Error) {
        loadsFinished += 1
        sync()
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain && ns.code == NSURLErrorCancelled { return }
        // 102 — загрузка прервана решением (отмена, переход к загрузке файла).
        if ns.domain == "WebKitErrorDomain" && ns.code == 102 { return }
        let failing = (ns.userInfo[NSURLErrorFailingURLErrorKey] as? URL) ?? url
        guard let failing else { return }
        url = failing
        if ns.domain == "WebKitErrorDomain" && ns.code == 104 {
            interstitial = .blocked(failing)
            return
        }
        if let from = upgradedFrom, failing.scheme == "https", failing.host == from.host,
           ns.code != NSURLErrorCannotFindHost, ns.code != NSURLErrorNotConnectedToInternet {
            upgradedFrom = nil
            url = from
            interstitial = .insecure(from)
            return
        }
        upgradedFrom = nil
        interstitial = .failed(failing, Self.explain(ns, proxy: NetworkPrivacy.usesProxy(AppModel.shared.prefs, isPrivate: isPrivate || ephemeral)))
    }

    static func explain(_ error: NSError, proxy: Bool = false) -> String {
        guard error.domain == NSURLErrorDomain else { return error.localizedDescription }
        switch error.code {
        case NSURLErrorNotConnectedToInternet: return "Нет подключения к интернету."
        case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed: return "Такого сайта нет — или его адрес не находится. Проверьте написание."
        case NSURLErrorTimedOut: return proxy ? "Сайт не отвечает через прокси. Проверьте, что Veil (или другой прокси) подключён." : "Сайт слишком долго не отвечает."
        case NSURLErrorCannotConnectToHost:
            return proxy ? "Прокси не принимает соединения. Окно настроено ходить через прокси — Ward не стал открывать сайт напрямую." : "Сайт не принимает соединения."
        case NSURLErrorServerCertificateUntrusted, NSURLErrorServerCertificateHasBadDate, NSURLErrorServerCertificateHasUnknownRoot,
             NSURLErrorServerCertificateNotYetValid, NSURLErrorSecureConnectionFailed:
            return "Сертификат сайта недействителен: кто-то может подменять страницу. Ward не стал её открывать."
        case NSURLErrorNetworkConnectionLost: return "Соединение оборвалось."
        default: return error.localizedDescription
        }
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }
}

// MARK: - Окна, диалоги, разрешения

extension Tab: WKUIDelegate {
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        guard let browser else { return nil }
        let tab = browser.popup(configuration: configuration, opener: self)
        return tab.webView
    }

    func webViewDidClose(_ webView: WKWebView) {
        browser?.close(self)
    }

    private func sheet(_ alert: NSAlert) async -> NSApplication.ModalResponse {
        if let window = browser?.window { return await alert.beginSheetModal(for: window) }
        return alert.runModal()
    }

    private func origin(_ frame: WKFrameInfo) -> String {
        frame.securityOrigin.host.isEmpty ? "Страница" : frame.securityOrigin.host
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async {
        let alert = NSAlert()
        alert.messageText = "\(origin(frame)) сообщает"
        alert.informativeText = String(message.prefix(2000))
        alert.addButton(withTitle: "OK")
        _ = await sheet(alert)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async -> Bool {
        let alert = NSAlert()
        alert.messageText = "\(origin(frame)) спрашивает"
        alert.informativeText = String(message.prefix(2000))
        alert.addButton(withTitle: "OK")
        alert.addButton(withTitle: "Отмена")
        return await sheet(alert) == .alertFirstButtonReturn
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo) async -> String? {
        let alert = NSAlert()
        alert.messageText = "\(origin(frame)) спрашивает"
        alert.informativeText = String(prompt.prefix(2000))
        let field = NSTextField(frame: NSRect(x: 0, y: 0, width: 280, height: 24))
        field.stringValue = defaultText ?? ""
        alert.accessoryView = field
        alert.addButton(withTitle: "OK")
        alert.addButton(withTitle: "Отмена")
        return await sheet(alert) == .alertFirstButtonReturn ? field.stringValue : nil
    }

    func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo) async -> [URL]? {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = parameters.allowsMultipleSelection
        panel.canChooseDirectories = parameters.allowsDirectories
        panel.canChooseFiles = true
        let response: NSApplication.ModalResponse
        if let window = browser?.window { response = await panel.beginSheetModal(for: window) } else { response = panel.runModal() }
        return response == .OK ? panel.urls : nil
    }

    /// Камера и микрофон — только с вашего согласия, отдельно для каждого сайта.
    func webView(_ webView: WKWebView, decideMediaCapturePermissionsFor origin: WKSecurityOrigin, initiatedBy frame: WKFrameInfo, type: WKMediaCaptureType) async -> WKPermissionDecision {
        let model = AppModel.shared
        let site = Hosts.site(origin.host)
        if let remembered = model.prefs.mediaDecisions[site] { return remembered ? .grant : .deny }
        if model.prefs.denyMediaByDefault { return .deny }
        let key = "\(origin.host)|\(type.rawValue)"
        if mediaAllowed.contains(key) { return .grant }
        let what: String
        switch type {
        case .camera: what = "камере"
        case .microphone: what = "микрофону"
        default: what = "камере и микрофону"
        }
        let alert = NSAlert()
        alert.messageText = "\(origin.host) просит доступ к \(what)"
        alert.informativeText = "Без запоминания разрешение действует, пока открыта эта вкладка."
        alert.addButton(withTitle: "Разрешить")
        alert.addButton(withTitle: "Запретить")
        alert.showsSuppressionButton = true
        alert.suppressionButton?.title = "Запомнить для \(site)"
        let allowed = await sheet(alert) == .alertFirstButtonReturn
        if alert.suppressionButton?.state == .on { model.prefs.mediaDecisions[site] = allowed }
        guard allowed else { return .deny }
        mediaAllowed.insert(key)
        return .grant
    }
}

/// Посредник для сообщений из скриптов Ward: WebKit держит обработчики сильной ссылкой.
private final class ScriptProxy: NSObject, WKScriptMessageHandler {
    weak var tab: Tab?

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        tab?.received(message)
    }
}

/// Запросы SponsorBlock из страницы YouTube — с ответом (отрезки ролика).
private final class SponsorProxy: NSObject, WKScriptMessageHandlerWithReply {
    weak var tab: Tab?

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) async -> (Any?, String?) {
        guard let tab else { return ([Any](), nil) }
        return (await tab.sponsorRequest(message), nil)
    }
}
