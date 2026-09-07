import SwiftUI

@main
struct VeilApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.openWindow) private var openWindow
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup(id: "main") {
            RootView()
                .environment(appState)
        }
        .defaultSize(width: 1120, height: 760)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("View") {
                Button("Home") { appState.sidebarSelection = .home }
                    .keyboardShortcut("1", modifiers: .command)
                Button("Route") { appState.sidebarSelection = .locations }
                    .keyboardShortcut("2", modifiers: .command)
                Button("Activity") { appState.sidebarSelection = .activity }
                    .keyboardShortcut("3", modifiers: .command)
                Divider()
                Button("Mini Window") { openWindow(id: "mini") }
                    .keyboardShortcut("m", modifiers: [.command, .shift])
            }
            CommandMenu("Connection") {
                Button("Connect") { appState.toggleConnection() }
                    .keyboardShortcut("k", modifiers: .command)
                    .disabled(appState.connection.isActive || appState.connection == .disconnecting)
                Button("Disconnect") { appState.disconnect() }
                    .keyboardShortcut("k", modifiers: [.command, .shift])
                    .disabled(!appState.connection.isActive)
                Divider()
                Button("New Identity") { appState.requestNewIdentity() }
                    .keyboardShortcut("n", modifiers: [.command, .shift])
                    .disabled(!appState.connection.isConnected)
                Button("Check Tor Connection") { appState.runTorCheck() }
                    .keyboardShortcut("t", modifiers: [.command, .shift])
                    .disabled(!appState.connection.isConnected)
                Button("Reset Network") { appState.resetNetwork() }
                    .keyboardShortcut("r", modifiers: [.command, .shift])
                    .disabled(appState.isResettingNetwork)
                Divider()
                Button("YouTube Turbo") { appState.toggleTurbo() }
                    .keyboardShortcut("y", modifiers: [.command, .shift])
                    .disabled(appState.connection.isActive)
                Divider()
                Button("Check for Updates…") { appState.checkForUpdates(manual: true) }
                Button("Save Diagnostics Report…") { appState.saveDiagnostics() }
            }
        }

        Window("Veil Mini", id: "mini") {
            MiniView()
                .environment(appState)
        }
        .windowResizability(.contentSize)
        .defaultSize(width: 360, height: 560)

        MenuBarExtra(isInserted: $appState.settings.showInMenuBar) {
            MenuBarView()
                .environment(appState)
        } label: {
            MenuBarLabel()
                .environment(appState)
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView()
                .environment(appState)
        }
    }
}
