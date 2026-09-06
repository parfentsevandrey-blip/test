import SwiftUI

@main
struct VeilApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup(id: "main") {
            RootView()
                .environment(appState)
        }
        .defaultSize(width: 1120, height: 760)
        .commands {
            CommandGroup(replacing: .newItem) {}
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
            }
        }

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
