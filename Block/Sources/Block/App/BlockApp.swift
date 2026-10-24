import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        BlockApp.main()
    }
}

struct BlockApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel.shared

    var body: some Scene {
        Window("Block", id: "main") {
            RootView()
                .environment(model)
                .frame(minWidth: 960, minHeight: 640)
                .background(WindowAccessor { window in
                    // Запуск при входе в систему: работает только сторож в строке меню, окно не нужно.
                    if AppDelegate.launchedAtLogin && !DemoUI.isOn { window.close() }
                    if DemoUI.isOn, let screen = window.screen ?? NSScreen.main {
                        let f = screen.visibleFrame
                        window.setFrame(NSRect(x: f.minX, y: f.minY, width: min(1280, f.width), height: min(820, f.height)), display: true)
                    }
                })
        }
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1180, height: 780)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("Block") {
                ForEach(Array(Screen.allCases.enumerated()), id: \.element) { i, screen in
                    Button(screen.title) { model.screen = screen }.keyboardShortcut(KeyEquivalent(Character(String(i + 1))))
                }
                Divider()
                Button("Проверить, не вернула ли Apple своё") { model.checkDrift(reason: "проверка вручную") }.keyboardShortcut("r")
            }
        }

        MenuBarExtra {
            MenuBarView().environment(model)
        } label: {
            Image(systemName: model.score >= 0.999 ? "lock.shield.fill" : "lock.shield")
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    nonisolated(unsafe) static var launchedAtLogin = false

    func applicationWillFinishLaunching(_ notification: Notification) {
        let event = NSAppleEventManager.shared().currentAppleEvent
        // keyAEPropData ('prdt') = keyAELaunchedAsLogInItem ('lgit').
        AppDelegate.launchedAtLogin = event?.paramDescriptor(forKeyword: 0x7072_6474)?.enumCodeValue == 0x6C67_6974
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        AppModel.shared.start()
        if DemoUI.isOn { Task { await DemoUI.prepare(AppModel.shared) } }
    }

    /// Окно закрыто — сторож продолжает работать из строки меню.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
}

struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> Probe {
        let v = Probe()
        v.onWindow = onWindow
        return v
    }

    func updateNSView(_ nsView: Probe, context: Context) {}

    final class Probe: NSView {
        var onWindow: ((NSWindow) -> Void)?
        private var done = false
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window, !done { done = true; onWindow?(window) }
        }
    }
}
