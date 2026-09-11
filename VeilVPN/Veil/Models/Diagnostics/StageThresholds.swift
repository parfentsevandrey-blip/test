import Foundation

/// Every number the ledger judges by, in one file, so a threshold is changed in one place and a
/// reader can see what "degraded" actually means.
enum StageThresholds {
    /// Fires before Tor gives up rather than after: 0.6 × the stage budget.
    static let stallShareOfBudget = 0.6
    /// A circuit older than this has not been refreshed and its hops may be stale.
    static let circuitStaleSeconds: TimeInterval = 60
    /// Under this many samples, the exit-stream figures are not a distribution and can never be a fault.
    static let minimumStreamSamples = 10
    static let streamFailureDegraded = 0.10
    static let streamFailureBlocked = 0.25
    /// Local proxy: this many open with nothing completing is a wedge, not a busy moment.
    static let localProxyWedgeInFlight = 32
    static let localProxyFailureRatio = 5
    static let localProxyMinimumSamples = 5
    /// A lane this much slower than the best one, and slow in absolute terms, is worth naming.
    static let laneSlowRatio = 2.5
    static let laneSlowFloorSeconds: TimeInterval = 1.2
    /// A hop is called slow only when it is both a multiple of the others and slow outright: on a
    /// three-hop path, the "median of the other hops" is a median of two samples.
    static let hopSlowFloorMilliseconds = 600
    static let hopSlowRatio = 2.5

    /// How often each stage's underlying fact is refreshed. A row is dimmed past 2.5× its cadence.
    static func cadence(_ stage: PipelineStage) -> TimeInterval? {
        switch stage {
        case .throughput: 1
        case .localProxy, .lanes: 2
        case .circuit: 20
        case .latency: 25
        case .network: 20
        case .link, .bootstrap: 5
        case .reachability, .verify, .systemProxy, .bypass: nil
        }
    }

    static func age(of date: Date?, stage: PipelineStage, now: Date) -> TimeInterval? {
        guard let date, let cadence = cadence(stage) else { return nil }
        let age = now.timeIntervalSince(date)
        return age > cadence * 2.5 ? age : nil
    }
}
