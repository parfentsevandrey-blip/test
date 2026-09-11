import Foundation

/// Chooses and budgets the transport queue from evidence Tor already wrote to disk plus what this
/// Mac has learned on this network — instead of from a probe that costs four seconds before the
/// first byte of bootstrap. The reachability probe still runs, concurrently, and is consumed only
/// if a first attempt fails.
enum AttemptPlanner {
    struct Plan: Sendable {
        var ordered: [AppSettings.Transport]
        var skipped: [(transport: AppSettings.Transport, reason: String)]
    }

    /// One SETCONF on a warm process, against the ~9 s a stop-and-respawn used to cost.
    static let switchCost: Double = 0.5
    /// Below this, trying is not worth the budget it would take from the transports that can work.
    static let minimumProbability = 0.06

    static func candidates(settings: AppSettings, warmth: WarmthProfile, history: NetworkHistory,
                           reachability: ReachabilityProbe.Report?) -> Plan {
        guard settings.transport == .auto else {
            return Plan(ordered: [settings.transport], skipped: [])
        }
        var pool: [AppSettings.Transport] = [.direct, .snowflake, .obfs4, .meek]
        if !TorConfiguration.parseBridgeLines(settings.customBridges).isEmpty { pool.append(.custom) }

        var scored: [(transport: AppSettings.Transport, probability: Double, expected: Double)] = []
        for transport in pool {
            let record = history.record(transport)
            var probability = record?.successRate ?? BootstrapDefaults.priorSuccess[transport] ?? 0.5
            if let record, record.streak <= -3 { probability *= 0.35 }
            if warmth.evidencedTransports.contains(transport) { probability = max(probability, 0.70) }
            if transport == .direct, warmth.state.confirmedDefaultGuards > 0 {
                probability = max(probability, 0.70)
            }
            // A machine that confirmed a bridge here and never confirmed a default guard is a
            // machine where connecting directly did not work. Trying anyway is not merely a waste:
            // it puts recognisable Tor traffic on the wire of a network that blocks it.
            if transport == .direct, warmth.state.confirmedDefaultGuards == 0,
               warmth.evidencedTransports.contains(where: { $0 != .direct }) {
                probability = 0.02
            }
            if transport == .direct, let reachability {
                if reachability.directLooksPossible {
                    probability = max(probability, 0.70)
                } else if reachability.reachable == 0 {
                    probability = 0.02
                }
            }
            let expected = record?.bootstrapP50 ?? BootstrapDefaults.priorBootstrapSeconds[transport] ?? 30
            let penalty = Self.hardTimeoutSeconds(transport) + switchCost
            let cost = probability * expected + (1 - probability) * penalty
            scored.append((transport, probability, cost))
        }
        scored.sort { $0.expected < $1.expected }

        var ordered: [AppSettings.Transport] = []
        var skipped: [(transport: AppSettings.Transport, reason: String)] = []
        for candidate in scored {
            if candidate.probability < minimumProbability {
                let record = history.record(candidate.transport)
                let detail = record.map { "\($0.successes)/\($0.attempts) here" } ?? "no history"
                skipped.append((candidate.transport, "\(detail), \(candidate.transport.rawValue) is not worth the budget"))
            } else {
                ordered.append(candidate.transport)
            }
        }
        // Never leave nothing to try: the best of a bad set still beats refusing to connect.
        if ordered.isEmpty, let best = scored.max(by: { $0.probability < $1.probability }) {
            ordered = [best.transport]
            skipped.removeAll { $0.transport == best.transport }
        }
        return Plan(ordered: ordered, skipped: skipped)
    }

    static func hardTimeoutSeconds(_ transport: AppSettings.Transport) -> Double {
        (BootstrapDefaults.priorBootstrapSeconds[transport] ?? 30) * 2.2
    }

    /// The whole connect, across every transport. A warm Mac has no business spending three minutes.
    static func overallDeadline(tier: WarmthProfile.Tier) -> TimeInterval {
        switch tier {
        case .hot: 90
        case .warm: 120
        case .cool: 180
        case .cold: 210
        }
    }

    static func config(transport: AppSettings.Transport, index: Int, count: Int,
                       warmth: WarmthProfile, history: NetworkHistory, marginalLink: Bool,
                       warmEngine: Bool, elapsed: TimeInterval, deadline: TimeInterval,
                       bridgeLineCount: Int) -> BootstrapWatchdog.Config {
        let record = history.record(transport)
        var stall: [BootstrapStage: Duration] = [:]
        for stage in BootstrapStage.allCases {
            stall[stage] = BootstrapDefaults.stall(
                transport: transport,
                stage: stage,
                consensus: warmth.consensus,
                warmEngine: warmEngine,
                marginalLink: marginalLink,
                learnedP80: record?.stageP80(stage)
            )
        }
        let expected = record?.bootstrapP50 ?? BootstrapDefaults.priorBootstrapSeconds[transport] ?? 30
        var firstHop = BootstrapDefaults.firstHopDeadlineSeconds[transport] ?? 20
        if warmEngine, warmth.launchedTransports.contains(ptName(of: transport)) { firstHop *= 0.5 }
        return BootstrapWatchdog.Config(
            stall: stall,
            hardTimeout: BootstrapDefaults.hardTimeout(
                expectedSeconds: expected,
                consensus: warmth.consensus,
                isLast: index == count - 1,
                remaining: deadline - elapsed
            ),
            firstHopDeadline: .seconds(firstHop),
            distinctFailuresToAbort: min(6, max(1, bridgeLineCount)),
            controlSilenceLimit: .seconds(6)
        )
    }

    /// The pluggable-transport name Tor reports in `TRANSPORT_LAUNCHED`.
    static func ptName(of transport: AppSettings.Transport) -> String {
        switch transport {
        case .snowflake, .auto: "snowflake"
        case .obfs4: "obfs4"
        case .meek: "meek_lite"
        case .custom: "custom"
        case .direct: "direct"
        }
    }

    /// How many distinct first hops the attempt actually offers Tor; a single-bridge transport must
    /// not need three distinct failures before it is allowed to give up.
    static func distinctFirstHops(transport: AppSettings.Transport, settings: AppSettings,
                                  defaults: PluggableTransportDefaults) -> Int {
        switch transport {
        case .direct, .auto: 3
        case .meek: 1
        case .snowflake: 1
        case .obfs4: max(1, (defaults.bridges["obfs4"] ?? []).count)
        case .custom: max(1, TorConfiguration.parseBridgeLines(settings.customBridges).count)
        }
    }
}
