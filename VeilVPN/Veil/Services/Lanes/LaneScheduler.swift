import Foundation

/// Which lane a new connection takes. Pure, with injected randomness so it is deterministic in CI.
enum LaneScheduler {
    /// 1. A site already bound to a ready lane keeps it — the stream cap does not apply, because a
    ///    page's dozen subresource connections *should* share that site's circuit.
    /// 2. Otherwise pick at random from the lanes within `fastSetFactor` of the best measured p50.
    ///    Always-the-best would congest one circuit until it stopped being the best, and would make
    ///    the site→circuit grouping a deterministic function of when you browsed.
    static func pick(rows: [LaneRow], affinity: Int?, avoiding: Int?, maxStreamsPerLane: Int,
                     fastSetFactor: Double, randomValue: Double) -> (lane: Int, reason: AssignmentReason)? {
        if let affinity, let row = rows.first(where: { $0.id == affinity }), row.isReady, row.id != avoiding {
            return (row.id, .affinity)
        }
        var reason = AssignmentReason.best
        var candidates = rows.filter { $0.isReady && $0.id != avoiding && $0.inFlight < maxStreamsPerLane }
        if candidates.isEmpty {
            candidates = rows.filter { $0.isReady && $0.id != avoiding }
            reason = .fallback
        }
        guard !candidates.isEmpty else { return nil }
        let scored = candidates.filter { $0.p50 != nil }
        var fastSet = candidates
        if let best = scored.compactMap(\.p50).min() {
            fastSet = scored.filter { ($0.p50 ?? .infinity) <= best * fastSetFactor }
        }
        guard !fastSet.isEmpty else { return nil }
        let ordered = fastSet.sorted { $0.id < $1.id }
        let clamped = min(max(randomValue, 0), 0.999999)
        let index = min(ordered.count - 1, Int(clamped * Double(ordered.count)))
        return (ordered[index].id, reason)
    }

    /// Strictly the lowest measured p50; the hedge target and the headline number.
    static func best(rows: [LaneRow], avoiding: Int?) -> Int? {
        rows.filter { $0.isReady && $0.id != avoiding && $0.p50 != nil }
            .min { left, right in
                if left.p50 == right.p50 { return left.id < right.id }
                return (left.p50 ?? .infinity) < (right.p50 ?? .infinity)
            }?.id
    }
}
