import AppIntents

struct ConnectIntent: AppIntent {
    static var title: LocalizedStringResource = "Connect through Tor"
    static var description = IntentDescription("Starts Tor and routes the Mac through it.")
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        AppState.shared?.connect()
        return .result()
    }
}

struct DisconnectIntent: AppIntent {
    static var title: LocalizedStringResource = "Disconnect from Tor"
    static var description = IntentDescription("Stops Tor and restores the system proxy.")
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        AppState.shared?.disconnect()
        return .result()
    }
}

struct NewIdentityIntent: AppIntent {
    static var title: LocalizedStringResource = "New Tor identity"
    static var description = IntentDescription("Rebuilds the Tor circuits.")
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        AppState.shared?.requestNewIdentity()
        return .result()
    }
}

struct ToggleTurboIntent: AppIntent {
    static var title: LocalizedStringResource = "Toggle YouTube Turbo"
    static var description = IntentDescription("Switches the YouTube anti-throttling proxy (without Tor) on or off.")
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        AppState.shared?.toggleTurbo()
        return .result()
    }
}

struct VeilShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: ConnectIntent(), phrases: ["Connect \(.applicationName)"], shortTitle: "Connect", systemImageName: "power")
        AppShortcut(intent: DisconnectIntent(), phrases: ["Disconnect \(.applicationName)"], shortTitle: "Disconnect", systemImageName: "stop.fill")
        AppShortcut(intent: NewIdentityIntent(), phrases: ["New identity in \(.applicationName)"], shortTitle: "New Identity", systemImageName: "arrow.triangle.2.circlepath")
        AppShortcut(intent: ToggleTurboIntent(), phrases: ["Toggle YouTube Turbo in \(.applicationName)"], shortTitle: "YouTube Turbo", systemImageName: "bolt.fill")
    }
}
