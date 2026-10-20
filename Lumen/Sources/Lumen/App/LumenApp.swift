import AppKit
import SwiftUI

/// Точка входа: обычный запуск или самопроверка ремуксера в CI (`--selftest-remux`).
@main
enum Entry {
    static func main() {
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "--selftest-remux"), args.count > i + 2 {
            exit(SelfTest.run(input: URL(fileURLWithPath: args[i + 1]), output: URL(fileURLWithPath: args[i + 2])))
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
        if !pending.isEmpty {
            model.open(pending)
            pending = []
        }
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        if let model { model.open(urls) } else { pending += urls }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        Task.detached(priority: .background) { MediaResolver.trimCache() }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    func applicationWillTerminate(_ notification: Notification) {
        model?.engine.persistPosition()
        model?.library.saveNow()
    }
}
