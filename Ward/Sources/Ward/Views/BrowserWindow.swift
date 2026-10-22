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
            if browser.tabs.count > 1 || browser.isPrivate {
                TabStrip(browser: browser)
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
        .background(WindowAccessor { window in
            browser.window = window
            if browser.isPrivate { window.appearance = NSAppearance(named: .darkAqua) }
        })
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
        }
        ToolbarItem(placement: .principal) {
            if let tab = browser.selected {
                AddressBar(tab: tab, browser: browser, address: address, go: go)
                    .frame(minWidth: 380, idealWidth: 680, maxWidth: 860)
            }
        }
        ToolbarItemGroup(placement: .primaryAction) {
            if let tab = browser.selected {
                ShieldButton(tab: tab)
            }
            if !model.downloads.items.isEmpty {
                DownloadsButton()
            }
            Button { browser.newTab() } label: { Image(systemName: "plus") }
                .help("Новая вкладка (⌘T)")
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
            }
        }
    }
}

struct ProgressLine: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            Rectangle()
                .fill(Color.accentColor)
                .frame(width: max(24, geo.size.width * progress), height: 2)
                .animation(.easeOut(duration: 0.25), value: progress)
        }
        .frame(height: 2)
        .allowsHitTesting(false)
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
