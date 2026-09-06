import Foundation

/// User preferences. Persisted as JSON in `UserDefaults`.
struct AppSettings: Codable, Equatable, Sendable {
    enum Transport: String, Codable, CaseIterable, Identifiable, Sendable {
        /// Snowflake pluggable transport with the built-in broker configuration (default).
        case snowflake
        /// Built-in obfs4 bridges shipped with Tor Browser.
        case obfs4
        /// Bridge lines pasted by the user (obfs4, webtunnel, snowflake, meek_lite, conjure).
        case custom
        /// No bridges: connect to public Tor relays directly.
        case direct

        var id: String { rawValue }
    }

    var transport: Transport = .snowflake
    var customBridges: String = ""
    /// ISO 3166-1 alpha-2 country code (lowercase) for `ExitNodes`, or nil for automatic.
    var exitCountry: String? = nil
    var socksPort: Int = 9050
    var httpPort: Int = 8118
    var configureSystemProxy: Bool = true
    var showInMenuBar: Bool = true
    var connectOnLaunch: Bool = false
    var checkAfterConnect: Bool = true
    var verboseLogs: Bool = false

    init() {}

    // Tolerant decoding so that settings written by older versions keep working.
    private enum CodingKeys: String, CodingKey {
        case transport, customBridges, exitCountry, socksPort, httpPort, configureSystemProxy
        case showInMenuBar, connectOnLaunch, checkAfterConnect, verboseLogs
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        transport = try c.decodeIfPresent(Transport.self, forKey: .transport) ?? .snowflake
        customBridges = try c.decodeIfPresent(String.self, forKey: .customBridges) ?? ""
        exitCountry = try c.decodeIfPresent(String.self, forKey: .exitCountry)
        socksPort = try c.decodeIfPresent(Int.self, forKey: .socksPort) ?? 9050
        httpPort = try c.decodeIfPresent(Int.self, forKey: .httpPort) ?? 8118
        configureSystemProxy = try c.decodeIfPresent(Bool.self, forKey: .configureSystemProxy) ?? true
        showInMenuBar = try c.decodeIfPresent(Bool.self, forKey: .showInMenuBar) ?? true
        connectOnLaunch = try c.decodeIfPresent(Bool.self, forKey: .connectOnLaunch) ?? false
        checkAfterConnect = try c.decodeIfPresent(Bool.self, forKey: .checkAfterConnect) ?? true
        verboseLogs = try c.decodeIfPresent(Bool.self, forKey: .verboseLogs) ?? false
    }

    static let storageKey = "app.veilvpn.settings.v1"

    static func load(from defaults: UserDefaults = .standard) -> AppSettings {
        guard let data = defaults.data(forKey: storageKey),
              let settings = try? JSONDecoder().decode(AppSettings.self, from: data) else {
            return AppSettings()
        }
        return settings
    }

    func save(to defaults: UserDefaults = .standard) {
        if let data = try? JSONEncoder().encode(self) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }
}
