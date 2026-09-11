import Foundation

/// One tap that moves every protective setting at once, with the score it would produce shown
/// before it is applied.
enum SecurityPreset: String, CaseIterable, Codable, Identifiable, Sendable {
    case privacyFirst, balanced, speedFirst

    var id: String { rawValue }

    var title: String {
        switch self {
        case .privacyFirst: "Privacy first"
        case .balanced: "Balanced"
        case .speedFirst: "Speed first"
        }
    }

    var summary: String {
        switch self {
        case .privacyFirst: "Everything through Tor, a circuit per site, padding on. App Store downloads and iCloud will stall."
        case .balanced: "Everything through Tor except Apple's own services, which stall badly through exits."
        case .speedFirst: "YouTube and Apple bypass Tor, padding off, measured relays pinned. Fast, and least private."
        }
    }

    /// Every bypass off means writing an explicit `.tor` for each service: clearing the dictionary
    /// would put Apple straight back to direct, because the policy merges over the catalogue's
    /// own defaults.
    static var allServicesThroughTor: [String: RouteMode] {
        Dictionary(uniqueKeysWithValues: ServiceCatalog.all.map { ($0.id, RouteMode.tor) })
    }

    func applied(to base: AppSettings) -> AppSettings {
        var settings = base
        settings.securityPreset = rawValue
        switch self {
        case .privacyFirst:
            settings.killSwitch = true
            settings.configureSystemProxy = true
            settings.closeSessionsOnKillSwitch = true
            settings.isolatePerSite = true
            settings.latencyTuning = false
            settings.lanePoolEnabled = true
            settings.paddingEnabled = true
            settings.paddingLevel = .balanced
            settings.httpsOnly = true
            settings.youtubeMode = .tor
            settings.serviceRoutes = Self.allServicesThroughTor
            settings.customDirectDomains = ""
            settings.updateCheckAfterConnect = true
            settings.redactDiagnostics = true
            settings.verboseLogs = false
            settings.forgetPolicy = .caches
        case .balanced:
            settings.killSwitch = true
            settings.configureSystemProxy = true
            settings.closeSessionsOnKillSwitch = true
            settings.isolatePerSite = true
            settings.latencyTuning = false
            settings.lanePoolEnabled = true
            settings.httpsOnly = true
            settings.youtubeMode = .tor
            settings.serviceRoutes = [:]
            settings.updateCheckAfterConnect = true
            settings.redactDiagnostics = true
            settings.forgetPolicy = .off
        case .speedFirst:
            settings.killSwitch = true
            settings.configureSystemProxy = true
            settings.closeSessionsOnKillSwitch = false
            settings.isolatePerSite = false
            settings.lanePoolEnabled = true
            settings.latencyTuning = false
            settings.paddingEnabled = false
            settings.httpsOnly = false
            settings.youtubeMode = .directAntiThrottle
            settings.serviceRoutes = ["apple": .direct]
            settings.forgetPolicy = .off
        }
        return settings
    }

    /// Said plainly before anything is changed, so a preset is never a surprise.
    var warning: String? {
        switch self {
        case .privacyFirst: "App Store downloads, updates and iCloud go through Tor exits, where they usually stall."
        case .balanced: nil
        case .speedFirst: "YouTube and Apple services will see your real IP address."
        }
    }
}
