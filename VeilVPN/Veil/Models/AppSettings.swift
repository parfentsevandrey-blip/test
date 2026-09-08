import Foundation

/// User preferences. Persisted as JSON in `UserDefaults`; every field has a default so that
/// settings written by older versions keep decoding.
struct AppSettings: Codable, Equatable, Sendable {
    enum Transport: String, Codable, CaseIterable, Identifiable, Sendable {
        /// Try the transports in order until one bootstraps; remembers the winner.
        case auto
        /// Snowflake pluggable transport with the built-in broker configuration.
        case snowflake
        /// Built-in obfs4 bridges shipped with Tor Browser.
        case obfs4
        /// Built-in meek (domain-fronted) bridge shipped with Tor Browser.
        case meek
        /// Bridge lines pasted by the user (obfs4, webtunnel, snowflake, meek_lite, conjure).
        case custom
        /// No bridges: connect to public Tor relays directly.
        case direct

        var id: String { rawValue }
        var isConcrete: Bool { self != .auto }
    }

    var transport: Transport = .auto
    var lastWorkingTransport: Transport? = nil
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
    /// DAITA-style traffic padding through Veil's private onion loop.
    var paddingEnabled: Bool = false
    var paddingLevel: PaddingLevel = .balanced
    /// Multihop: pin the middle hop's country, exclude countries, rotate the route on a timer.
    var multihopEnabled: Bool = false
    var middleCountry: String? = nil
    var excludedCountries: [String] = []
    var avoidFiveEyes: Bool = false
    /// Minutes between automatic route rotations (NEWNYM); 0 disables rotation.
    var rotateRouteMinutes: Int = 0
    /// Race circuits after every route change and pin the fastest relays.
    var latencyTuning: Bool = true
    /// Route changes keep existing connections; only new ones take the new route.
    var seamlessRouteSwitch: Bool = true
    /// Tor Conflux with the lowest-latency leg preferred (applies on the next connection).
    var confluxLatency: Bool = true
    /// Concurrent Snowflake proxies (`max=`), 1–4.
    var snowflakePeers: Int = 2
    /// YouTube: through Tor, or directly with/without anti-throttling.
    var youtubeMode: RouteMode = .tor
    var dpiStrategy: DPIStrategy = .recordAndSegmentAtSNI
    /// Extra domains that bypass Tor (one per line).
    var customDirectDomains: String = ""
    var customDirectAntiThrottle: Bool = true
    /// Per-service routing for the presets in `ServiceCatalog` (absent = through Tor).
    var serviceRoutes: [String: RouteMode] = [:]
    /// Fail closed: keep the system proxy pointed at Veil when Tor dies unexpectedly.
    var killSwitch: Bool = true
    var autoReconnect: Bool = true
    /// Switch Wi-Fi off and on (or restart the network service) when the network stops responding.
    var autoResetNetwork: Bool = true
    /// `IsolateDestAddr`: a separate circuit per destination site.
    var isolatePerSite: Bool = false
    var notificationsEnabled: Bool = true
    var soundEffects: Bool = false
    var hapticFeedback: Bool = true
    var checkForUpdates: Bool = true
    var skippedUpdateVersion: String? = nil
    var onboardingCompleted: Bool = false

    init() {}

    private enum CodingKeys: String, CodingKey {
        case transport, lastWorkingTransport, customBridges, exitCountry, socksPort, httpPort, configureSystemProxy
        case showInMenuBar, connectOnLaunch, checkAfterConnect, verboseLogs
        case paddingEnabled, paddingLevel
        case multihopEnabled, middleCountry, excludedCountries, avoidFiveEyes, rotateRouteMinutes
        case latencyTuning, seamlessRouteSwitch, confluxLatency, snowflakePeers
        case youtubeMode, dpiStrategy, customDirectDomains, customDirectAntiThrottle, serviceRoutes
        case killSwitch, autoReconnect, autoResetNetwork, isolatePerSite, notificationsEnabled, soundEffects, hapticFeedback
        case checkForUpdates, skippedUpdateVersion, onboardingCompleted
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        transport = try c.decodeIfPresent(Transport.self, forKey: .transport) ?? d.transport
        lastWorkingTransport = try c.decodeIfPresent(Transport.self, forKey: .lastWorkingTransport)
        customBridges = try c.decodeIfPresent(String.self, forKey: .customBridges) ?? d.customBridges
        exitCountry = try c.decodeIfPresent(String.self, forKey: .exitCountry)
        socksPort = try c.decodeIfPresent(Int.self, forKey: .socksPort) ?? d.socksPort
        httpPort = try c.decodeIfPresent(Int.self, forKey: .httpPort) ?? d.httpPort
        configureSystemProxy = try c.decodeIfPresent(Bool.self, forKey: .configureSystemProxy) ?? d.configureSystemProxy
        showInMenuBar = try c.decodeIfPresent(Bool.self, forKey: .showInMenuBar) ?? d.showInMenuBar
        connectOnLaunch = try c.decodeIfPresent(Bool.self, forKey: .connectOnLaunch) ?? d.connectOnLaunch
        checkAfterConnect = try c.decodeIfPresent(Bool.self, forKey: .checkAfterConnect) ?? d.checkAfterConnect
        verboseLogs = try c.decodeIfPresent(Bool.self, forKey: .verboseLogs) ?? d.verboseLogs
        paddingEnabled = try c.decodeIfPresent(Bool.self, forKey: .paddingEnabled) ?? d.paddingEnabled
        paddingLevel = try c.decodeIfPresent(PaddingLevel.self, forKey: .paddingLevel) ?? d.paddingLevel
        multihopEnabled = try c.decodeIfPresent(Bool.self, forKey: .multihopEnabled) ?? d.multihopEnabled
        middleCountry = try c.decodeIfPresent(String.self, forKey: .middleCountry)
        excludedCountries = try c.decodeIfPresent([String].self, forKey: .excludedCountries) ?? d.excludedCountries
        avoidFiveEyes = try c.decodeIfPresent(Bool.self, forKey: .avoidFiveEyes) ?? d.avoidFiveEyes
        rotateRouteMinutes = try c.decodeIfPresent(Int.self, forKey: .rotateRouteMinutes) ?? d.rotateRouteMinutes
        latencyTuning = try c.decodeIfPresent(Bool.self, forKey: .latencyTuning) ?? d.latencyTuning
        seamlessRouteSwitch = try c.decodeIfPresent(Bool.self, forKey: .seamlessRouteSwitch) ?? d.seamlessRouteSwitch
        confluxLatency = try c.decodeIfPresent(Bool.self, forKey: .confluxLatency) ?? d.confluxLatency
        snowflakePeers = min(4, max(1, try c.decodeIfPresent(Int.self, forKey: .snowflakePeers) ?? d.snowflakePeers))
        youtubeMode = try c.decodeIfPresent(RouteMode.self, forKey: .youtubeMode) ?? d.youtubeMode
        dpiStrategy = try c.decodeIfPresent(DPIStrategy.self, forKey: .dpiStrategy) ?? d.dpiStrategy
        customDirectDomains = try c.decodeIfPresent(String.self, forKey: .customDirectDomains) ?? d.customDirectDomains
        customDirectAntiThrottle = try c.decodeIfPresent(Bool.self, forKey: .customDirectAntiThrottle) ?? d.customDirectAntiThrottle
        serviceRoutes = try c.decodeIfPresent([String: RouteMode].self, forKey: .serviceRoutes) ?? d.serviceRoutes
        killSwitch = try c.decodeIfPresent(Bool.self, forKey: .killSwitch) ?? d.killSwitch
        autoReconnect = try c.decodeIfPresent(Bool.self, forKey: .autoReconnect) ?? d.autoReconnect
        autoResetNetwork = try c.decodeIfPresent(Bool.self, forKey: .autoResetNetwork) ?? d.autoResetNetwork
        isolatePerSite = try c.decodeIfPresent(Bool.self, forKey: .isolatePerSite) ?? d.isolatePerSite
        notificationsEnabled = try c.decodeIfPresent(Bool.self, forKey: .notificationsEnabled) ?? d.notificationsEnabled
        soundEffects = try c.decodeIfPresent(Bool.self, forKey: .soundEffects) ?? d.soundEffects
        hapticFeedback = try c.decodeIfPresent(Bool.self, forKey: .hapticFeedback) ?? d.hapticFeedback
        checkForUpdates = try c.decodeIfPresent(Bool.self, forKey: .checkForUpdates) ?? d.checkForUpdates
        skippedUpdateVersion = try c.decodeIfPresent(String.self, forKey: .skippedUpdateVersion)
        onboardingCompleted = try c.decodeIfPresent(Bool.self, forKey: .onboardingCompleted) ?? d.onboardingCompleted
    }

    /// The same settings with a concrete transport substituted (used by automatic selection).
    func resolving(transport concrete: Transport) -> AppSettings {
        var copy = self
        copy.transport = concrete
        return copy
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
