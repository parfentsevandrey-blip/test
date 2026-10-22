import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        SynapseApp.main()
    }
}

struct SynapseApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        Window("Synapse", id: "main") {
            RootView()
                .environment(model)
                .frame(minWidth: 900, minHeight: 600)
                .background(WindowAccessor { window in
                    if DemoUI.isOn, let screen = window.screen ?? NSScreen.main {
                        let f = screen.visibleFrame
                        window.setFrame(NSRect(x: f.minX, y: f.minY, width: min(1280, f.width), height: min(820, f.height)), display: true)
                    }
                })
                .onAppear {
                    DemoUI.prepareBeforeStart(model)
                    model.start()
                    Task { await DemoUI.run(model) }
                }
        }
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1240, height: 800)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Новый чат") { model.section = .chat; model.newChat() }.keyboardShortcut("n")
            }
            CommandMenu("Модели") {
                Button("Чат") { model.section = .chat }.keyboardShortcut("1")
                Button("Для этого Mac") { model.section = .forYou }.keyboardShortcut("2")
                Button("Каталог") { model.section = .catalog }.keyboardShortcut("3")
                Button("Лента") { model.section = .feed }.keyboardShortcut("4")
                Button("Мои модели") { model.section = .library }.keyboardShortcut("5")
                Divider()
                Button("Остановить ответ") { model.stopGenerating() }.keyboardShortcut(".").disabled(!model.isGenerating)
            }
        }

        Settings {
            SettingsView().environment(model)
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    func applicationWillTerminate(_ notification: Notification) {
        LlamaEngine.shared.shutdown()
    }
}
