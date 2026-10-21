import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        SweepApp.main()
    }
}

struct SweepApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        Window("Sweep", id: "main") {
            RootView()
                .environment(model)
                .frame(minWidth: 1040, minHeight: 680)
                .background(WindowAccessor { window in
                    if DemoUI.isOn, let screen = window.screen ?? NSScreen.main {
                        let f = screen.visibleFrame
                        window.setFrame(NSRect(x: f.minX, y: f.minY, width: min(1280, f.width), height: min(800, f.height)), display: true)
                    }
                })
                .onAppear { model.start() }
        }
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1220, height: 800)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("Проверка") {
                Button("Умная проверка") { model.module = .smart; model.runSmartScan() }.keyboardShortcut("r")
                Button("Очистка") { model.module = .cleanup }.keyboardShortcut("1")
                Button("Программы") { model.module = .uninstaller }.keyboardShortcut("2")
                Button("Крупные файлы") { model.module = .clutter }.keyboardShortcut("3")
                Button("Карта диска") { model.module = .space }.keyboardShortcut("4")
                Divider()
                Button("Полный доступ к диску…") { Permissions.openFullDiskAccessSettings() }
            }
        }

        MenuBarExtra {
            MenuBarView().environment(model)
        } label: {
            Image(systemName: model.disk.usedFraction > 0.9 ? "externaldrive.fill.badge.exclamationmark" : "sparkles")
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
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
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { onWindow?(window) }
        }
    }
}
