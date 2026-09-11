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

    /// How much of the connection is paid for before the button is pressed.
    enum WarmStart: String, Codable, CaseIterable, Identifiable, Sendable {
        /// Tor is spawned only when you connect. Nothing runs in the background.
        case off
        /// One tor process is kept loaded but offline (`DisableNetwork 1`): no listener, no
        /// packets, observationally identical to tor not running. Connecting is then one command.
        case standby
        /// Tor also builds circuits at launch. Faster still, but it puts Tor traffic on the wire
        /// before you asked for it.
        case preBootstrap

        var id: String { rawValue }
    }

    /// What is removed from Tor's data directory when Veil quits.
    enum ForgetPolicy: String, Codable, CaseIterable, Identifiable, Sendable {
        case off
        /// Cached directory data. Keeps `state`, so the entry guard survives.
        case caches
        /// The whole data directory. Discards the entry guard as well, which is a real
        /// anonymity trade-off, not just a cleanup.
        case everything

        var id: String { rawValue }
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
    /// Race circuits after every route change and pin the fastest relays. Off by default since
    /// the lane pool arrived: pinning collapses every lane onto the same exit.
    var latencyTuning: Bool = false
    /// Route changes keep existing connections; only new ones take the new route.
    var seamlessRouteSwitch: Bool = true
    /// Tor Conflux with the lowest-latency leg preferred (applies on the next connection).
    var confluxLatency: Bool = true
    /// Concurrent Snowflake proxies (`max=`), 1–4.
    var snowflakePeers: Int = 3
    /// Keep one tor process loaded but offline, so connecting is a single command.
    var warmStart: WarmStart = .standby
    /// Hold several measured circuits open and send each new connection down a fast one.
    var lanePoolEnabled: Bool = true
    /// Parallel circuits kept alive (2–6).
    var lanePoolSize: Int = 4
    /// Re-try a slow connect on a second circuit.
    var lanePoolHedging: Bool = true
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
    /// Close connections that are already open when the kill switch engages, not just refuse new ones.
    var closeSessionsOnKillSwitch: Bool = true
    /// Refuse plain `http://` through Tor, so an exit relay can never read or rewrite a page.
    var httpsOnly: Bool = false
    /// Leave IP addresses, ports and host names out of exported diagnostics.
    var redactDiagnostics: Bool = true
    /// Check for updates only once Tor is up, so the request does not go out in the clear.
    var updateCheckAfterConnect: Bool = true
    var forgetPolicy: ForgetPolicy = .off
    /// Which security preset was last applied, for the UI only.
    var securityPreset: String? = nil
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
        case warmStart, lanePoolEnabled, lanePoolSize, lanePoolHedging
        case closeSessionsOnKillSwitch, httpsOnly, redactDiagnostics, updateCheckAfterConnect
        case forgetPolicy, securityPreset
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
        warmStart = try c.decodeIfPresent(WarmStart.self, forKey: .warmStart) ?? d.warmStart
        lanePoolSize = min(6, max(2, try c.decodeIfPresent(Int.self, forKey: .lanePoolSize) ?? d.lanePoolSize))
        lanePoolHedging = try c.decodeIfPresent(Bool.self, forKey: .lanePoolHedging) ?? d.lanePoolHedging
        // The lane pool and the tuner's exit pinning are mutually exclusive: with ExitNodes pinned
        // to one or two fingerprints every lane ends at the same exit, so the pool degenerates into
        // N redundant circuits with all of the cost and none of the spread — and a measured,
        // stable pair of exits is itself a cross-session pseudonym.
        if let stored = try c.decodeIfPresent(Bool.self, forKey: .lanePoolEnabled) {
            lanePoolEnabled = stored
            if stored { latencyTuning = false }
        } else {
            lanePoolEnabled = d.lanePoolEnabled
            if d.lanePoolEnabled { latencyTuning = false }
        }
        closeSessionsOnKillSwitch = try c.decodeIfPresent(Bool.self, forKey: .closeSessionsOnKillSwitch) ?? d.closeSessionsOnKillSwitch
        httpsOnly = try c.decodeIfPresent(Bool.self, forKey: .httpsOnly) ?? d.httpsOnly
        redactDiagnostics = try c.decodeIfPresent(Bool.self, forKey: .redactDiagnostics) ?? d.redactDiagnostics
        updateCheckAfterConnect = try c.decodeIfPresent(Bool.self, forKey: .updateCheckAfterConnect) ?? d.updateCheckAfterConnect
        forgetPolicy = try c.decodeIfPresent(ForgetPolicy.self, forKey: .forgetPolicy) ?? d.forgetPolicy
        securityPreset = try c.decodeIfPresent(String.self, forKey: .securityPreset)
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
