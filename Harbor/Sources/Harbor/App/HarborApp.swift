import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        // Подставное приложение для самопроверки установки (копия Harbor с другим идентификатором).
        if Dummy.isDummy { Dummy.run() }
        SelfTest.runIfRequested()
        HarborApp.main()
    }
}

struct HarborApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var store = Store()

    var body: some Scene {
        Window("Harbor", id: "main") {
            RootView()
                .environment(store)
                .frame(minWidth: 900, minHeight: 600)
                .background(WindowSetup())
                .onAppear {
                    store.start()
                    delegate.store = store
                }
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1180, height: 760)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("Каталог") {
                Button("Проверить обновления") { Task { await store.check() } }
                    .keyboardShortcut("r")
                Button("Обновить всё") { Task { await store.updateAll() } }
                    .keyboardShortcut("u", modifiers: [.command, .shift])
                    .disabled(store.updates.isEmpty)
            }
        }

        Settings {
            SettingsView().environment(store)
        }
    }
}

/// Окно в демо-режиме — фиксированного размера для скриншотов.
private struct WindowSetup: View {
    var body: some View {
        WindowAccessor { window in
            window.titlebarAppearsTransparent = true
            guard DemoUI.isOn, let screen = window.screen ?? NSScreen.main else { return }
            let width = min(1280, screen.visibleFrame.width), height = min(820, screen.visibleFrame.height)
            window.setFrame(NSRect(x: screen.visibleFrame.minX, y: screen.visibleFrame.maxY - height, width: width, height: height), display: true)
        }
    }
}

struct WindowAccessor: NSViewRepresentable {
    let configure: (NSWindow) -> Void

    func makeNSView(context: Context) -> Probe { Probe(configure: configure) }
    func updateNSView(_ view: Probe, context: Context) {}

    final class Probe: NSView {
        let configure: (NSWindow) -> Void
        init(configure: @escaping (NSWindow) -> Void) {
            self.configure = configure
            super.init(frame: .zero)
        }
        required init?(coder: NSCoder) { fatalError() }
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { configure(window) }
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    var store: Store?

    /// Закрытие окна не завершает Harbor: он продолжает следить за обновлениями из строки меню.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }

    /// В демо-режиме окно должно быть активным, иначе на скриншотах кнопки выглядят погасшими.
    func applicationDidFinishLaunching(_ notification: Notification) {
        if DemoUI.isOn { NSApplication.shared.activate() }
    }
}
