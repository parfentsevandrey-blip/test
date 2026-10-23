import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        setvbuf(stdout, nil, _IOLBF, 0)
        // Язык для сайтов нужно выставить до запуска WebKit.
        AppModel.applyLanguage((Storage.load(Prefs.self, "prefs.json") ?? Prefs()).spoofLanguage)
        SelfTest.runIfRequested()
        WardApp.main()
    }
}

struct WardApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup("Ward", id: "browser") {
            BrowserWindow(isPrivate: DemoUI.isOn && DemoUI.screen == "private").environment(AppModel.shared)
        }
        .defaultSize(width: 1280, height: 820)
        .windowToolbarStyle(.unified(showsTitle: false))
        .handlesExternalEvents(matching: [])
        .commands { BrowserCommands() }

        WindowGroup("Частное окно", id: "private") {
            BrowserWindow(isPrivate: true).environment(AppModel.shared)
        }
        .defaultSize(width: 1280, height: 820)
        .windowToolbarStyle(.unified(showsTitle: false))
        .handlesExternalEvents(matching: [])
        // Частное окно после перезапуска не должно появляться никогда.
        .restorationBehavior(.disabled)

        Window("История", id: "history") {
            HistoryView().environment(AppModel.shared)
        }
        .defaultSize(width: 760, height: 620)
        .restorationBehavior(.disabled)

        Settings {
            SettingsView().environment(AppModel.shared)
        }
    }
}

struct BrowserCommands: Commands {
    @FocusedValue(\.browser) private var browser
    @Environment(\.openWindow) private var openWindow

    private var tab: Tab? { browser?.selected }

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("Новая вкладка") {
                if let browser { browser.newTab() } else { openWindow(id: "browser") }
            }
            .keyboardShortcut("t")
            Button("Новое окно") { openWindow(id: "browser") }.keyboardShortcut("n")
            Button("Новое частное окно") { openWindow(id: "private") }.keyboardShortcut("n", modifiers: [.command, .shift])
            Button("Одноразовая вкладка") { browser?.newTab(ephemeral: true) }
                .keyboardShortcut("t", modifiers: [.command, .option])
                .disabled(browser == nil || browser?.isPrivate == true)
            Divider()
            Button("Открыть адрес…") { tab?.focusRequest += 1 }.keyboardShortcut("l").disabled(browser == nil)
            Button("Вернуть закрытую вкладку") { browser?.reopenClosed() }
                .keyboardShortcut("t", modifiers: [.command, .shift])
                .disabled(browser == nil || AppModel.shared.closedTabs.isEmpty)
            Button("Закрыть вкладку") { browser?.closeSelected() }.keyboardShortcut("w").disabled(browser == nil)
        }
        CommandGroup(replacing: .printItem) {
            Button("Напечатать…") { tab?.printPage() }.keyboardShortcut("p").disabled(tab?.url == nil)
        }
        CommandGroup(after: .textEditing) {
            Button("Найти на странице…") { browser?.showFind() }.keyboardShortcut("f").disabled(tab == nil)
            Button("Найти далее") { if let b = browser { tab?.find(b.findText) } }.keyboardShortcut("g").disabled(tab == nil)
            Button("Найти ранее") { if let b = browser { tab?.find(b.findText, backwards: true) } }
                .keyboardShortcut("g", modifiers: [.command, .shift]).disabled(tab == nil)
        }
        CommandGroup(after: .toolbar) {
            Button("Обновить страницу") { tab?.reload() }.keyboardShortcut("r").disabled(tab == nil)
            Button("Остановить") { tab?.stop() }.keyboardShortcut(".").disabled(!(tab?.isLoading ?? false))
            Divider()
            Button("Увеличить") { if let t = tab { t.zoom = min(3, t.zoom + 0.1) } }.keyboardShortcut("+").disabled(tab == nil)
            Button("Уменьшить") { if let t = tab { t.zoom = max(0.5, t.zoom - 0.1) } }.keyboardShortcut("-").disabled(tab == nil)
            Button("Реальный размер") { tab?.zoom = 1 }.keyboardShortcut("0").disabled(tab == nil)
            Divider()
        }
        CommandMenu("История") {
            Button("Назад") { tab?.goBack() }.keyboardShortcut("[").disabled(!(tab?.canGoBack ?? false) && tab?.interstitial == nil)
            Button("Вперёд") { tab?.goForward() }.keyboardShortcut("]").disabled(!(tab?.canGoForward ?? false))
            Divider()
            Button("Показать всю историю") { openWindow(id: "history") }.keyboardShortcut("y")
            Divider()
            Button("Добавить в избранное") {
                if let t = tab, let u = t.url { AppModel.shared.toggleFavorite(u, title: t.title) }
            }
            .keyboardShortcut("d")
            .disabled(tab?.url == nil)
        }
        CommandMenu("Вкладки") {
            Button("Следующая вкладка") { browser?.selectNeighbour(1) }.keyboardShortcut("]", modifiers: [.command, .shift])
            Button("Предыдущая вкладка") { browser?.selectNeighbour(-1) }.keyboardShortcut("[", modifiers: [.command, .shift])
            Divider()
            ForEach(1..<9) { i in
                Button("Вкладка \(i)") { browser?.select(index: i - 1) }.keyboardShortcut(KeyEquivalent(Character("\(i)")))
            }
            Button("Последняя вкладка") { browser?.selectLast() }.keyboardShortcut("9")
        }
        CommandGroup(after: .appSettings) {
            Button("Сделать браузером по умолчанию") { AppModel.shared.makeDefaultBrowser() }
            Button("Стереть всё…") { WipeConfirmation.ask() }.keyboardShortcut(.delete, modifiers: [.command, .shift])
        }
    }
}

/// «Стереть всё» из меню: подтверждение, потом закрыть всё и удалить все данные.
enum WipeConfirmation {
    static func ask() {
        let alert = NSAlert()
        alert.alertStyle = .critical
        alert.messageText = "Стереть всё?"
        alert.informativeText = "Закроются все окна, удалятся cookie и данные всех сайтов во всех контейнерах (и запомненных тоже), история и вкладки прошлого сеанса. Настройки и избранное останутся."
        alert.addButton(withTitle: "Стереть всё")
        alert.addButton(withTitle: "Отмена")
        guard alert.runModal() == .alertFirstButtonReturn else { return }
        Task { await AppModel.shared.wipeEverything() }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var quitting = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        AppModel.shared.start()
        if DemoUI.isOn { Task { await DemoUI.run() } }
    }

    /// Ссылки из других приложений — во вкладку Ward.
    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls { AppModel.shared.open(url) }
        NSApp.activate()
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag { AppModel.shared.openWindow?("browser") }
        return true
    }

    /// Перед выходом — стереть данные сайтов (если так настроено). Это занимает доли секунды.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard !quitting else { return .terminateNow }
        quitting = true
        Task {
            await AppModel.shared.shutdown()
            sender.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }
}
