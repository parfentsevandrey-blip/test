import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        setvbuf(stdout, nil, _IOLBF, 0)
        SelfTest.runIfRequested()
        VeilApp.main()
    }
}

struct VeilApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        Window("Veil", id: "main") {
            MainView().environment(AppModel.shared)
        }
        .defaultSize(width: 1080, height: 720)
        .windowToolbarStyle(.unified(showsTitle: false))

        Window("Журнал Veil", id: "log") {
            LogView().environment(AppModel.shared)
        }
        .defaultSize(width: 760, height: 460)

        Settings {
            SettingsView().environment(AppModel.shared)
        }

        MenuBarExtra {
            MenuBarContent().environment(AppModel.shared)
        } label: {
            MenuBarIcon().environment(AppModel.shared)
        }
    }
}

/// Щит в строке меню: пустой — отключено, заполненный — подключено.
struct MenuBarIcon: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        switch model.status {
        case .connected: Image(systemName: "shield.lefthalf.filled")
        case .connecting, .reconnecting: Image(systemName: "shield.lefthalf.filled.badge.checkmark")
        case .failed: Image(systemName: "exclamationmark.shield")
        case .disconnected: Image(systemName: "shield")
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        let model = AppModel.shared
        model.start()
        model.applyDockPolicy()
        if DemoUI.isOn { Task { await DemoUI.run(model) } }
    }

    /// Ссылки vless://, vmess://, trojan://, ss://, hy2://, happ://, veil:// открываются в Veil.
    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls { AppModel.shared.open(url) }
        NSApp.activate()
    }

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        AppModel.shared.shutdown()
        return .terminateNow
    }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { true }
}
