import AppKit
import SwiftUI
import WebKit

/// Окно браузера: панель с адресной строкой, вкладки, страница.
struct BrowserWindow: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings
    @State private var browser: BrowserState
    @State private var address = AddressState()

    init(isPrivate: Bool) {
        _browser = State(initialValue: BrowserState(isPrivate: isPrivate))
    }

    var body: some View {
        VStack(spacing: 0) {
            // Полоса вкладок видна всегда: «+» для новой вкладки — сразу за последней, как в Chrome и Safari.
            TabStrip(browser: browser)
            Divider()
            if model.prefs.showBookmarksBar {
                BookmarksBar(browser: browser)
                Divider()
            }
            ZStack(alignment: .top) {
                if let tab = browser.selected {
                    PageArea(tab: tab, browser: browser)
                        .id(tab.id)
                }
                if address.editing, !address.suggestions.isEmpty {
                    SuggestionList(address: address) { input in
                        go(input)
                    }
                    .padding(.top, 6)
                }
            }
            if browser.findVisible, let tab = browser.selected {
                Divider()
                FindBar(browser: browser, tab: tab)
            }
        }
        .toolbar { toolbar }
        .navigationTitle(browser.selected?.displayTitle ?? "Ward")
        .background(WindowAccessor { window in browser.window = window })
        // Частное окно — тёмное целиком, чтобы его нельзя было спутать с обычным.
        .preferredColorScheme(browser.isPrivate ? .dark : nil)
        .focusedSceneValue(\.browser, browser)
        .onAppear {
            model.register(browser)
            model.activeBrowser = browser
            model.openWindow = { id in openWindow(id: id) }
        }
        .onDisappear { browser.closedWindow() }
        .onChange(of: model.settingsRequest) { openSettings() }
        .onReceive(NotificationCenter.default.publisher(for: NSWindow.didBecomeKeyNotification)) { note in
            if let window = note.object as? NSWindow, window === browser.window { model.activeBrowser = browser }
        }
    }

    private func go(_ input: String) {
        guard let tab = browser.selected else { return }
        browser.go(input, in: tab)
        address.editing = false
        address.focusPage += 1
        if let window = browser.window, let view = tab.webViewIfLoaded { window.makeFirstResponder(view) }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .navigation) {
            let tab = browser.selected
            Button { tab?.goBack() } label: { Image(systemName: "chevron.backward") }
                .help("Назад")
                .disabled(!(tab?.canGoBack ?? false) && tab?.interstitial == nil)
            Button { tab?.goForward() } label: { Image(systemName: "chevron.forward") }
                .help("Вперёд")
                .disabled(!(tab?.canGoForward ?? false))
            ReloadButton(tab: tab)
        }
        ToolbarItem(placement: .principal) {
            if let tab = browser.selected {
                AddressBar(tab: tab, browser: browser, address: address, go: go)
                    .frame(minWidth: 340, idealWidth: 540, maxWidth: 860)
            }
        }
        ToolbarItemGroup(placement: .primaryAction) {
            SettingsLink {
                Image(systemName: "gearshape")
            }
            .help("Настройки Ward (⌘,)")
            if let tab = browser.selected {
                ShieldButton(tab: tab)
            }
            if !model.downloads.items.isEmpty {
                DownloadsButton()
            }
            DashboardButton()
        }
    }
}

/// Обновить страницу; пока она грузится — остановить. ⌥-щелчок — обновить без кэша.
struct ReloadButton: View {
    let tab: Tab?

    var body: some View {
        let loading = tab?.isLoading ?? false
        Button {
            guard let tab else { return }
            if loading { tab.stop() }
            else if NSEvent.modifierFlags.contains(.option) { tab.reloadFromOrigin() }
            else { tab.reload() }
        } label: {
            Image(systemName: loading ? "xmark" : "arrow.clockwise")
                .contentTransition(.symbolEffect(.replace))
        }
        .help(loading ? "Остановить загрузку (⌘.)" : "Обновить страницу (⌘R, с ⌥ — без кэша)")
        .disabled(tab == nil || (tab?.isStartPage ?? true))
    }
}

/// Панель безопасности: вся статистика защиты. Значок вздрагивает, когда Ward что-то останавливает.
struct DashboardButton: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Button { openWindow(id: "dashboard") } label: {
            Image(systemName: "chart.line.uptrend.xyaxis")
                .symbolEffect(.bounce, value: model.stats.total / 10)
        }
        .help("Панель безопасности: что Ward остановил (⇧⌘S)")
    }
}

/// «+» сразу за вкладками: щелчок — новая вкладка; правый щелчок — в контейнере или одноразовая.
struct NewTabButton: View {
    @Environment(AppModel.self) private var model
    let browser: BrowserState
    @State private var hover = false

    var body: some View {
        Button { browser.newTab() } label: {
            Image(systemName: "plus")
                .font(.system(size: 13, weight: .semibold))
                .frame(width: 28, height: 28)
                .background(Circle().fill(Color.primary.opacity(hover ? 0.1 : 0)))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(.secondary)
        .onHover { hover = $0 }
        .help("Новая вкладка (⌘T). Правый щелчок — одноразовая или в контейнере")
        .contextMenu {
            Button("Новая вкладка") { browser.newTab() }
            if !browser.isPrivate {
                Button("Одноразовая вкладка") { browser.newTab(ephemeral: true) }
                Divider()
                ForEach(model.containers) { c in
                    Button("В контейнере «\(c.name)»") { browser.newTab(container: c) }
                }
            }
        }
    }
}

/// Страница вкладки и всё, что поверх неё.
struct PageArea: View {
    let tab: Tab
    let browser: BrowserState

    var body: some View {
        ZStack(alignment: .top) {
            if tab.isStartPage {
                StartPage(browser: browser)
            } else {
                WebViewHost(tab: tab)
            }
            if let interstitial = tab.interstitial {
                InterstitialView(tab: tab, kind: interstitial)
                    .transition(.opacity)
            }
            if tab.isLoading, tab.progress < 1 {
                ProgressLine(progress: tab.progress)
                LoadingStatus(tab: tab)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    .padding(10)
                    .transition(.opacity)
            }
            if tab.passwordWarning, tab.interstitial == nil {
                PasswordBanner(tab: tab)
                    .padding(.top, 10)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
    }
}

/// Полоса загрузки над страницей: заметная, со свечением и бегущим бликом, пока сайт думает.
struct ProgressLine: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            let width = max(36, geo.size.width * progress)
            ZStack(alignment: .leading) {
                Rectangle().fill(Color.accentColor.opacity(0.15))
                Capsule()
                    .fill(LinearGradient(colors: [.accentColor.opacity(0.7), .accentColor], startPoint: .leading, endPoint: .trailing))
                    .frame(width: width)
                    .shadow(color: .accentColor.opacity(0.7), radius: 4)
                    .animation(.easeOut(duration: 0.3), value: progress)
                TimelineView(.animation) { context in
                    let t = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.4) / 1.4
                    Capsule()
                        .fill(LinearGradient(colors: [.clear, .white.opacity(0.75), .clear], startPoint: .leading, endPoint: .trailing))
                        .frame(width: 80)
                        .offset(x: (width + 80) * t - 80)
                }
                .frame(width: width, alignment: .leading)
                .clipped()
            }
        }
        .frame(height: 3)
        .allowsHitTesting(false)
    }
}

/// Строка состояния в углу страницы, пока она грузится: какой сайт и сколько готово.
struct LoadingStatus: View {
    let tab: Tab

    var body: some View {
        HStack(spacing: 7) {
            ProgressView().controlSize(.small)
            Text(text)
                .font(.callout)
                .monospacedDigit()
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .glassEffect(.regular, in: Capsule())
        .allowsHitTesting(false)
    }

    private var text: String {
        let host = (tab.url ?? tab.pendingURL)?.host() ?? ""
        let percent = Int((tab.progress * 100).rounded())
        if percent < 10 { return host.isEmpty ? "Соединение…" : "Соединение с \(host)…" }
        return host.isEmpty ? "Загрузка… \(percent) %" : "Загрузка \(host)… \(percent) %"
    }
}

/// Страница WebKit внутри SwiftUI. Сама страница живёт во вкладке: при переключении вкладок
/// она не перезагружается.
struct WebViewHost: NSViewRepresentable {
    let tab: Tab

    func makeNSView(context: Context) -> NSView {
        let container = NSView()
        attach(to: container)
        return container
    }

    func updateNSView(_ container: NSView, context: Context) {
        attach(to: container)
    }

    private func attach(to container: NSView) {
        let web = tab.webView
        guard web.superview !== container else { return }
        container.subviews.forEach { $0.removeFromSuperview() }
        web.frame = container.bounds
        web.autoresizingMask = [.width, .height]
        container.addSubview(web)
    }
}

/// Доступ к NSWindow окна SwiftUI.
struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    /// Узнаёт окно в момент, когда попадает в него, — не раньше и не позже.
    final class Probe: NSView {
        var onWindow: ((NSWindow) -> Void)?

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { onWindow?(window) }
        }
    }

    func makeNSView(context: Context) -> Probe {
        let view = Probe()
        view.onWindow = onWindow
        return view
    }

    func updateNSView(_ view: Probe, context: Context) {
        view.onWindow = onWindow
        if let window = view.window { onWindow(window) }
    }
}

/// Поле пароля на странице без шифрования.
struct PasswordBanner: View {
    let tab: Tab

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.open.trianglebadge.exclamationmark.fill").foregroundStyle(.orange).font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text("Не вводите здесь пароль").font(.headline)
                Text("Страница открыта без шифрования: пароль увидит любой на пути — провайдер, владелец Wi-Fi, злоумышленник.")
                    .font(.callout).foregroundStyle(.secondary)
            }
            Button { tab.passwordWarning = false } label: { Image(systemName: "xmark") }
                .buttonStyle(.borderless)
        }
        .padding(14)
        .frame(maxWidth: 560)
        .glassEffect(.regular.tint(.orange.opacity(0.15)), in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Поиск на странице (⌘F).
struct FindBar: View {
    @Bindable var browser: BrowserState
    let tab: Tab
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Найти на странице", text: $browser.findText)
                .textFieldStyle(.plain)
                .focused($focused)
                .onSubmit { tab.find(browser.findText) }
                .onChange(of: browser.findText) { tab.find(browser.findText) }
                .frame(maxWidth: 320)
            if let matched = tab.findMatched, !browser.findText.isEmpty {
                Text(matched ? "Найдено" : "Не найдено")
                    .font(.callout)
                    .foregroundStyle(matched ? Color.secondary : Color.red)
            }
            Spacer()
            Button { tab.find(browser.findText, backwards: true) } label: { Image(systemName: "chevron.up") }
                .buttonStyle(.borderless)
            Button { tab.find(browser.findText) } label: { Image(systemName: "chevron.down") }
                .buttonStyle(.borderless)
            Button("Готово") { browser.findVisible = false }
                .keyboardShortcut(.cancelAction)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.bar)
        .onAppear { focused = true }
    }
}
