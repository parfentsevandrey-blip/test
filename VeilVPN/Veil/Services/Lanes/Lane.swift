import Foundation

enum LaneState: String, Equatable, Sendable { case warming, ready, suspended }

enum LaneScope: Equatable, Hashable, Sendable {
    case pool(Int)
    case site(Int)

    var index: Int {
        switch self {
        case .pool(let index), .site(let index): index
        }
    }

    var isSite: Bool { if case .site = self { true } else { false } }
}

enum AssignmentReason: String, Sendable { case affinity, best, fallback }

enum LaneRetireReason: String, Sendable {
    case expired, slow, failures, routeChanged, newIdentity, checkFailed, resized, suspended
}

/// What a `ProxySession` holds. Immutable on purpose: retirement writes a *new* value into the
/// lane table and never reaches into an open session. That is the whole seamlessness invariant —
/// an open upstream is a TCP socket whose isolation key was consumed at handshake time.
struct LaneLease: Equatable, Sendable {
    let lane: Int
    let generation: UInt32
    let credentials: SOCKS5.Credentials
    let port: UInt16
    let reason: AssignmentReason
}

/// An immutable view of one lane; the only thing the scheduler, the policy and the UI ever see.
struct LaneRow: Equatable, Sendable, Identifiable {
    let id: Int
    let scope: LaneScope
    let generation: UInt32
    let state: LaneState
    /// From probe samples only. This is the one quantity selection ranks on.
    let p50: TimeInterval?
    let p90: TimeInterval?
    /// From real connections. Display only: different lanes serve different destinations.
    let liveP50: TimeInterval?
    let probeSamples: Int
    let consecutiveFailures: Int
    let inFlight: Int
    let assignedSites: Int
    let openedAt: Date
    let lastActiveSampleAt: Date?
    let recentPassiveSuccesses: Int

    var p50Milliseconds: Int? { p50.map { Int(($0 * 1000).rounded()) } }
    var p90Milliseconds: Int? { p90.map { Int(($0 * 1000).rounded()) } }
    var isReady: Bool { state == .ready }
}

struct LanePoolSnapshot: Equatable, Sendable {
    var enabled = false
    /// Non-nil means the bridge quietly went back to the single-circuit path, and why.
    var suspended: String?
    var poolPort: UInt16?
    var siteMode = false
    var rows: [LaneRow] = []
    var bestP50: TimeInterval?
    var assignmentsByAffinity = 0
    var assignmentsByScore = 0
    var assignmentsFallback = 0
    var assignmentsLegacy = 0
    var hedgesStarted = 0
    var hedgesWon = 0
    var retriesAttempted = 0
    var retriesSucceeded = 0
    var deadlineExpiries = 0
    var lastRetirement: String?

    var readyLanes: Int { rows.filter(\.isReady).count }
    var openStreams: Int { rows.reduce(0) { $0 + $1.inFlight } }
}

/// What a finished connection says about the lane it used.
enum LaneOutcome: Sendable {
    case connected(LaneLease, seconds: TimeInterval, isOnion: Bool)
    case failed(LaneLease, code: UInt8?)
    case timedOut(LaneLease)
    /// We cancelled it ourselves to hedge: neither a success nor a failure.
    case abandoned(LaneLease)
}
