import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        VesselApp.main()
    }
}

struct VesselApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel.shared

    var body: some Scene {
        Window("Vessel", id: "main") {
            RootView()
                .environment(model)
                .frame(minWidth: 980, minHeight: 640)
                .background(WindowAccessor { window in
                    if DemoUI.isOn, let screen = window.screen ?? NSScreen.main {
                        let f = screen.visibleFrame
                        window.setFrame(NSRect(x: f.minX, y: f.minY, width: min(1360, f.width), height: min(860, f.height)), display: true)
                        NSApp.activate(ignoringOtherApps: true)
                        window.makeKeyAndOrderFront(nil)
                    }
                })
        }
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1280, height: 820)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Новая машина…") { model.screen = .hot }.keyboardShortcut("n")
            }
            CommandMenu("Каталог") {
                Button("Горячие") { model.screen = .hot }.keyboardShortcut("1")
                Button("Популярные") { model.screen = .popular }.keyboardShortcut("2")
                Button("Все системы") { model.screen = .catalog }.keyboardShortcut("3")
                Button("Мои машины") { model.screen = .machines }.keyboardShortcut("4")
                Button("Загрузки") { model.screen = .downloads }.keyboardShortcut("5")
            }
        }

        WindowGroup("Машина", id: "machine", for: UUID.self) { $id in
            if let id {
                MachineWindow(id: id).environment(model)
            }
        }
        .windowToolbarStyle(.unifiedCompact)
        .defaultSize(width: 1280, height: 840)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        AppModel.shared.start()
        if DemoUI.isOn {
            NSApp.activate()
            Task { await DemoUI.prepare(AppModel.shared) }
        }
    }

    /// Окно каталога закрыто — Vessel продолжает работать, пока открыты машины.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        !AppModel.shared.sessions.values.contains { $0.status.isActive }
    }

    /// При выходе работающие машины не выключаются, а засыпают: память сохраняется на диск,
    /// и в следующий раз система продолжит ровно с того же места.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        let active = AppModel.shared.sessions.values.filter { $0.status.isActive }
        guard !active.isEmpty else { return .terminateNow }
        Task {
            for session in active { await session.suspend() }
            NSApp.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }
}

/// Доступ к NSWindow из SwiftUI.
struct WindowAccessor: NSViewRepresentable {
    let configure: (NSWindow) -> Void

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async { if let w = view.window { configure(w) } }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}
