import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        SwarmApp.main()
    }
}

struct SwarmApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        Window("Swarm", id: "main") {
            RootView()
                .environment(model)
                .background(WindowAccessor { window in
                    window.titlebarAppearsTransparent = true
                    window.isMovableByWindowBackground = true
                    if DemoUI.isOn, let screen = window.screen ?? NSScreen.main {
                        let width = min(DemoUI.width ?? 1280, screen.visibleFrame.width)
                        let height = min(820, screen.visibleFrame.height)
                        window.setFrame(NSRect(x: screen.visibleFrame.minX, y: screen.visibleFrame.maxY - height,
                                               width: width, height: height), display: true)
                    }
                })
                .onAppear {
                    model.start()
                    delegate.attach(model)
                }
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unifiedCompact)
        .defaultSize(width: 1180, height: 760)
        .commands { SwarmCommands(model: model) }

        WindowGroup("Рой", id: "swarm", for: String.self) { $id in
            SwarmWindowView(torrentID: id)
                .environment(model)
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 1180, height: 800)

        MenuBarExtra {
            MenuBarView().environment(model)
        } label: {
            // Скорость прямо в строке меню, пока идёт загрузка.
            if model.totalDown >= 1000 {
                Label(Format.rate(model.totalDown), systemImage: "arrow.down.circle.fill")
                    .labelStyle(.titleAndIcon)
                    .monospacedDigit()
            } else {
                Image(systemName: "point.3.connected.trianglepath.dotted")
            }
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView().environment(model)
        }
    }
}

struct SwarmCommands: Commands {
    let model: AppModel

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("Открыть .torrent…") { model.presentOpenPanel() }
                .keyboardShortcut("o")
            Button("Создать торрент…") { model.presentCreatePanel() }
                .keyboardShortcut("n")
        }
        CommandMenu("Раздачи") {
            Button("Пауза для всех") { model.pauseAll() }
                .keyboardShortcut("p", modifiers: [.command, .option])
            Button("Продолжить все") { model.resumeAll() }
                .keyboardShortcut("r", modifiers: [.command, .option])
            Divider()
            Button("Скопировать magnet-ссылку") { if let t = model.selected { model.copyMagnet(t) } }
                .keyboardShortcut("c", modifiers: [.command, .shift])
                .disabled(model.selected == nil)
            Button("Показать в Finder") { if let t = model.selected { model.reveal(t) } }
                .keyboardShortcut("r", modifiers: [.command, .shift])
                .disabled(model.selected == nil)
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var model: AppModel?
    private var pending: [URL] = []
    private var savedBeforeQuit = false

    func attach(_ model: AppModel) {
        guard self.model == nil else { return }
        self.model = model
        if !pending.isEmpty {
            model.open(pending)
            pending = []
        }
    }

    /// magnet:-ссылки и .torrent-файлы, открытые из браузера или Finder.
    func application(_ application: NSApplication, open urls: [URL]) {
        if let model { model.open(urls) } else { pending += urls }
        NSApp.activate()
    }

    /// Закрытие окна не останавливает загрузки: Swarm продолжает работать из строки меню.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }

    /// Перед выходом сохраняем прогресс всех раздач, чтобы не перепроверять файлы при запуске.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard let model, !savedBeforeQuit else { return .terminateNow }
        savedBeforeQuit = true
        Task {
            await model.saveBeforeQuit()
            sender.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }
}
