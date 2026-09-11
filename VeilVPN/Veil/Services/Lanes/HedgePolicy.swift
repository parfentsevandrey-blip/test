import Foundation

/// Every deadline the bridge applies to a connection through Tor, and when a slow connect is
/// re-tried on a second circuit.
enum HedgePolicy {
    /// The floor is the whole point: anything answering under 1.2 s — the median case — is never
    /// touched, so hedging cannot regress the median by cancelling a connect about to complete.
    /// Everything above it was already in the tail.
    static let floorSeconds: TimeInterval = 1.2
    static let ceilingSeconds: TimeInterval = 3.0
    static let defaultDeadline: TimeInterval = 10
    static let onionDeadline: TimeInterval = 30

    static func hedgeDelay(bestP50: TimeInterval?, isOnion: Bool, enabled: Bool, readyLanes: Int,
                           transport: AppSettings.Transport, secondsSinceConnect: TimeInterval) -> TimeInterval? {
        guard enabled, !isOnion, readyLanes >= 2, let bestP50 else { return nil }
        // A young snowflake or meek session is still finding proxies; a second circuit costs more
        // than it saves.
        let slowTransport = transport == .snowflake || transport == .meek || transport == .auto
        if slowTransport, secondsSinceConnect < 60 { return nil }
        var value = min(ceilingSeconds, max(floorSeconds, 1.5 * bestP50 + 0.4))
        if slowTransport { value += 1.0 }
        return value
    }

    static func deadline(attempt: Int, isOnion: Bool, hedgeDelay: TimeInterval?) -> TimeInterval {
        if isOnion { return onionDeadline }
        if attempt == 0 { return hedgeDelay ?? defaultDeadline }
        return defaultDeadline
    }
}
