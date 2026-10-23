import AppKit
import SwiftUI

/// Точка входа: обычный запуск или самопроверка воспроизведения в CI (`--selftest-playback файл`).
@main
enum Entry {
    static func main() {
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "--selftest-playback"), args.count > i + 1 {
            PlaybackSelfTest.run(URL(fileURLWithPath: args[i + 1]))
        }
        LumenApp.main()
    }
}

struct LumenApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        Window("Lumen", id: "main") {
            RootView()
                .environment(model)
                .onAppear { delegate.attach(model) }
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentMinSize)
        .defaultSize(width: 1280, height: 800)
        .commands { LumenCommands(model: model) }

        Settings {
            SettingsView()
                .environment(model)
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var model: AppModel?
    private var pending: [URL] = []

    func attach(_ model: AppModel) {
        guard self.model == nil else { return }
        self.model = model
        DemoUI.run(model)
        if !pending.isEmpty {
            model.open(pending)
            pending = []
        }
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        if let model { model.open(urls) } else { pending += urls }
    }

    func applicationWillFinishLaunching(_ notification: Notification) {
        DemoUI.prepare()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        Task.detached(priority: .background) { MediaResolver.removeLegacyCache() }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    func applicationWillTerminate(_ notification: Notification) {
        model?.engine.persistPosition()
    }
}
