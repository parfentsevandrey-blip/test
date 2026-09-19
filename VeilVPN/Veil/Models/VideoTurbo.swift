import Foundation

/// Turbo 4K: everything that makes YouTube fast through Tor, in one switch. Each piece exists as
/// a setting of its own — YouTube through Tor, the widest measured exit, the widest entry guard,
/// the tunnel in tone, conflux in throughput mode, no padding, no multihop, no relay pinning —
/// and each is a trade-off a user can make alone. This makes them together, remembers what it
/// changed, and puts every one of them back when switched off. Pure functions over
/// `AppSettings`; what tor is told live is the app state's job.
enum VideoTurbo {
    /// The user's own values of what Turbo changes, kept while it is on so that switching it off
    /// restores choices rather than defaults.
    struct Snapshot: Codable, Equatable, Sendable {
        var youtubeMode: RouteMode
        var youtubeModeChosen: Bool
        var videoExitEnabled: Bool
        var videoGuardPinning: Bool
        var videoKeepWarm: Bool
        var videoKeepWarmAlways: Bool
        var videoKeepWarmKilobytes: Int
        var confluxLatency: Bool
        var paddingEnabled: Bool
        var multihopEnabled: Bool
        var latencyTuning: Bool
        var isolatePerSite: Bool
        var snowflakePeers: Int
    }

    /// The least the tonus runs at under Turbo: 4 Mbit/s keeps every hop's window open and is a
    /// small share of the 22 Mbit/s that 4K takes. A higher rate the user set stands.
    static let minimumTonusKilobytes = 512
    /// How long a circuit keeps taking new streams (`MaxCircuitDirtiness`) while Turbo is on:
    /// half an hour instead of tor's ten minutes. A video is one long session, and every move to
    /// a fresh circuit is a cold start — a new build, then slow start on every hop again.
    static let circuitLifetimeSeconds = 1800
    /// The lane pool's lifetime under Turbo, below the circuit lifetime so that measured
    /// replacement still fires first.
    static let laneLifetime: TimeInterval = 1500
    /// Snowflake proxies at once under Turbo: the most the client takes. Applies at the next
    /// connection, and only behind Snowflake.
    static let snowflakePeers = 4

    static func snapshot(of settings: AppSettings) -> Snapshot {
        Snapshot(youtubeMode: settings.youtubeMode, youtubeModeChosen: settings.youtubeModeChosen,
                 videoExitEnabled: settings.videoExitEnabled, videoGuardPinning: settings.videoGuardPinning,
                 videoKeepWarm: settings.videoKeepWarm, videoKeepWarmAlways: settings.videoKeepWarmAlways,
                 videoKeepWarmKilobytes: settings.videoKeepWarmKilobytes, confluxLatency: settings.confluxLatency,
                 paddingEnabled: settings.paddingEnabled, multihopEnabled: settings.multihopEnabled,
                 latencyTuning: settings.latencyTuning, isolatePerSite: settings.isolatePerSite,
                 snowflakePeers: settings.snowflakePeers)
    }

    /// `settings` with Turbo on. A snapshot already held is kept: the first one is the user's own.
    static func applied(to base: AppSettings) -> AppSettings {
        var settings = base
        settings.videoTurbo = true
        settings.videoTurboRestore = base.videoTurboRestore ?? snapshot(of: base)
        settings.youtubeMode = .tor
        settings.youtubeModeChosen = true
        settings.videoExitEnabled = true
        settings.videoGuardPinning = true
        settings.videoKeepWarm = true
        settings.videoKeepWarmAlways = true
        settings.videoKeepWarmKilobytes = max(minimumTonusKilobytes, base.videoKeepWarmKilobytes)
        settings.confluxLatency = false
        settings.paddingEnabled = false
        settings.multihopEnabled = false
        settings.latencyTuning = false
        // Per-site isolation gives YouTube one circuit tor picked at random; the pool's lanes are
        // what the video lane is raced across.
        settings.isolatePerSite = false
        settings.snowflakePeers = snowflakePeers
        return settings
    }

    /// `settings` with Turbo off and what it changed put back. A value that is no longer the one
    /// Turbo set was changed by hand meanwhile — that change is what switched Turbo off, and it
    /// stands; everything still at Turbo's value goes back to the user's own.
    static func restored(_ base: AppSettings) -> AppSettings {
        var settings = base
        settings.videoTurbo = false
        settings.videoTurboRestore = nil
        guard let saved = base.videoTurboRestore else { return settings }
        if base.youtubeMode == .tor {
            settings.youtubeMode = saved.youtubeMode
            settings.youtubeModeChosen = saved.youtubeModeChosen
        }
        if base.videoExitEnabled { settings.videoExitEnabled = saved.videoExitEnabled }
        if base.videoGuardPinning { settings.videoGuardPinning = saved.videoGuardPinning }
        if base.videoKeepWarm { settings.videoKeepWarm = saved.videoKeepWarm }
        if base.videoKeepWarmAlways { settings.videoKeepWarmAlways = saved.videoKeepWarmAlways }
        if base.videoKeepWarmKilobytes >= minimumTonusKilobytes { settings.videoKeepWarmKilobytes = saved.videoKeepWarmKilobytes }
        if !base.confluxLatency { settings.confluxLatency = saved.confluxLatency }
        if !base.paddingEnabled { settings.paddingEnabled = saved.paddingEnabled }
        if !base.multihopEnabled { settings.multihopEnabled = saved.multihopEnabled }
        if !base.latencyTuning { settings.latencyTuning = saved.latencyTuning }
        if !base.isolatePerSite { settings.isolatePerSite = saved.isolatePerSite }
        if base.snowflakePeers == snowflakePeers { settings.snowflakePeers = saved.snowflakePeers }
        return settings
    }

    /// Whether every value Turbo sets is still in place.
    static func holds(in settings: AppSettings) -> Bool {
        settings.youtubeMode == .tor && settings.videoExitEnabled && settings.videoGuardPinning
            && settings.videoKeepWarm && settings.videoKeepWarmAlways
            && settings.videoKeepWarmKilobytes >= minimumTonusKilobytes
            && !settings.confluxLatency && !settings.paddingEnabled && !settings.multihopEnabled
            && !settings.latencyTuning && !settings.isolatePerSite && settings.snowflakePeers == snowflakePeers
    }

    /// Whether a security preset can land on top of Turbo without undoing it: true when the
    /// preset sets nothing Turbo manages the other way.
    static func tolerates(_ preset: SecurityPreset, over settings: AppSettings) -> Bool {
        holds(in: preset.applied(to: applied(to: settings)))
    }
}
