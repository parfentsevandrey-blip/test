import Foundation

/// When a lane is replaced, and the brake that stops replacement becoming a treadmill.
enum LanePolicy {
    struct Limits: Equatable, Sendable {
        var failuresBeforeRetire = 3
        var minRetireGap: TimeInterval = 90
        var slowRatio = 2.5
        /// Retiring a lane that is 3× the best but only 400 ms is pure churn.
        var slowFloor: TimeInterval = 1.2
        var minimumScoredLanes = 3
    }

    /// Precedence, at most one lane per pass:
    /// 1. a broken lane, out of band — it bypasses the gap;
    /// 2. nothing else within `minRetireGap` of the last retirement;
    /// 3. a lane past its lifetime;
    /// 4. the slowest lane, when it is both a multiple of the best *and* slow in absolute terms.
    static func retirementCandidate(rows: [LaneRow], deadlines: [Int: Date], now: Date,
                                    lastRetirement: Date?, slowFrozenUntil: Date?,
                                    limits: Limits = Limits()) -> (lane: Int, reason: LaneRetireReason)? {
        if let broken = rows.filter({ $0.consecutiveFailures >= limits.failuresBeforeRetire })
            .sorted(by: { $0.id < $1.id }).first {
            return (broken.id, .failures)
        }
        if let lastRetirement, now.timeIntervalSince(lastRetirement) < limits.minRetireGap { return nil }
        if let expired = rows.filter({ row in
            guard let deadline = deadlines[row.id] else { return false }
            return now >= deadline
        }).sorted(by: { $0.id < $1.id }).first {
            return (expired.id, .expired)
        }
        if let slowFrozenUntil, now < slowFrozenUntil { return nil }
        let scored = rows.filter { $0.isReady && !$0.scope.isSite && $0.p50 != nil }
        guard scored.count >= limits.minimumScoredLanes,
              let best = scored.compactMap(\.p50).min(),
              let worst = scored.max(by: { ($0.p50 ?? 0) < ($1.p50 ?? 0) }),
              let worstP50 = worst.p50,
              worstP50 >= best * limits.slowRatio, worstP50 >= limits.slowFloor else { return nil }
        return (worst.id, .slow)
    }

    /// On a bad uplink everything looks slow; without this the pool fans traffic across a dozen
    /// exits, which is the opposite of what is wanted.
    static func shouldFreezeSlowRetirement(recentSlowRetirements: Int, perWindow: Int) -> Bool {
        recentSlowRetirements > perWindow
    }
}
