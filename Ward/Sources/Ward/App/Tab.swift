import AppKit
import WebKit

/// Экран вместо страницы: опасный сайт, сайт без HTTPS, заблокированная страница, ошибка.
enum Interstitial: Equatable {
    case danger(URL)
    case insecure(URL)
    case blocked(URL)
    case failed(URL, String)
}

/// Вкладка: страница WebKit и всё, что Ward о ней знает.
@Observable
final class Tab: NSObject, Identifiable {
    let id = UUID()
    @ObservationIgnored weak var browser: BrowserState?

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
    /// Что убрано из адреса этой страницы.
    var cleanedParams: [String] = []
    var unwrappedRedirect = false
    var upgradedToHTTPS = false
    var protectedHere = true
    var javaScriptOn = true
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
    @ObservationIgnored private var appliedSite: String?
    @ObservationIgnored private var appliedProtection: Bool?
    @ObservationIgnored private var allowOnce: URL?
    @ObservationIgnored private var popupConfiguration: WKWebViewConfiguration?
    @ObservationIgnored private var mediaAllowed: Set<String> = []

    init(browser: BrowserState, url: URL? = nil, popup configuration: WKWebViewConfiguration? = nil) {
        self.browser = browser
        popupConfiguration = configuration
        super.init()
        if configuration != nil { _ = webView }
        if let url { load(url) }
    }

    var isStartPage: Bool { url == nil && pendingURL == nil && interstitial == nil && !isLoading }

    var displayTitle: String {
        if !title.isEmpty { return title }
        if let host = (url ?? pendingURL)?.siteHost, !host.isEmpty { return host }
        return "Новая вкладка"
    }

    var host: String { (url ?? pendingURL)?.host?.lowercased() ?? "" }
    var site: String { Hosts.site(host) }

    var restorableURL: URL? {
        guard let u = url ?? pendingURL, let scheme = u.scheme, scheme == "http" || scheme == "https" else { return nil }
        return u
    }

    var blockedCount: Int { trackers.count }

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
        if let store = browser?.dataStore { configuration.websiteDataStore = store }
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
        view.configuration.userContentController.add(proxy, contentWorld: .defaultClient, name: "wardHosts")
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
            if let u = view.url { AppModel.shared.retitle(u, title: newTitle, isPrivate: browser?.isPrivate ?? false) }
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
        let site = Hosts.site(host)
        let on = !model.isUnprotected(host)
        guard force || site != appliedSite || on != appliedProtection else { return }
        let controller = view.configuration.userContentController
        model.blocker.apply(to: controller, enabled: on)
        controller.removeAllUserScripts()
        for script in model.userScripts(secret: browser?.secret ?? model.sessionSecret, site: host) { controller.addUserScript(script) }
        appliedSite = site
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
            switch interstitial {
            case .failed(let u, _), .blocked(let u), .insecure(let u), .danger(let u): load(u)
            }
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
    }

    // MARK: - Решения экранов-предупреждений

    /// «Всё равно открыть» опасный сайт.
    func proceedDanger(_ target: URL) {
        browser?.dangerAllowed.insert(target.host?.lowercased() ?? "")
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

    // MARK: - Трекеры

    fileprivate func received(_ message: WKScriptMessage) {
        guard message.name == "wardHosts", let body = message.body as? [String: Any], let hosts = body["hosts"] as? [String] else { return }
        let page = url?.host?.lowercased() ?? ""
        guard !page.isEmpty, protectedHere else { return }
        let index = AppModel.shared.blocker.index
        for host in hosts.prefix(500) {
            let h = host.lowercased()
            guard trackers[h] == nil, let kind = index.classify(h, page: page) else { continue }
            trackers[h] = kind
            if !(browser?.isPrivate ?? false) { AppModel.shared.record(kind, host: h) }
        }
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

        // ⌘-клик и средняя кнопка — в новую вкладку.
        if action.navigationType == .linkActivated, action.modifierFlags.contains(.command) || action.buttonNumber == 2 {
            browser?.newTab(url: target, select: action.modifierFlags.contains(.shift), after: self)
            return (.cancel, preferences)
        }

        let model = AppModel.shared
        let host = target.host?.lowercased() ?? ""
        let bypass = allowOnce == target
        if bypass { allowOnce = nil }
        let web = scheme == "http" || scheme == "https"
        let protected = !model.isUnprotected(host)

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
            // 3. Только HTTPS: сначала пробуем защищённое соединение.
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
        let jsOn = !model.prefs.noScriptSites.contains(Hosts.site(host))
        preferences.allowsContentJavaScript = jsOn
        javaScriptOn = jsOn
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
        AppModel.shared.downloads.add(download)
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        AppModel.shared.downloads.add(download)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        interstitial = nil
        sync()
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        trackers = [:]
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
        sync()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loadsFinished += 1
        sync()
        if let u = webView.url { AppModel.shared.visited(u, title: webView.title ?? "", isPrivate: browser?.isPrivate ?? false) }
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
        interstitial = .failed(failing, Self.explain(ns))
    }

    static func explain(_ error: NSError) -> String {
        guard error.domain == NSURLErrorDomain else { return error.localizedDescription }
        switch error.code {
        case NSURLErrorNotConnectedToInternet: return "Нет подключения к интернету."
        case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed: return "Такого сайта нет — или его адрес не находится. Проверьте написание."
        case NSURLErrorTimedOut: return "Сайт слишком долго не отвечает."
        case NSURLErrorCannotConnectToHost: return "Сайт не принимает соединения."
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
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType) async -> WKPermissionDecision {
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
        alert.informativeText = "Разрешение действует, пока открыта эта вкладка."
        alert.addButton(withTitle: "Разрешить")
        alert.addButton(withTitle: "Запретить")
        guard await sheet(alert) == .alertFirstButtonReturn else { return .deny }
        mediaAllowed.insert(key)
        return .grant
    }
}

/// Посредник для сообщений из скрипта-наблюдателя: WebKit держит обработчик сильной ссылкой.
private final class ScriptProxy: NSObject, WKScriptMessageHandler {
    weak var tab: Tab?

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        tab?.received(message)
    }
}
