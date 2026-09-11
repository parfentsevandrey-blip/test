import Foundation

/// Which part of the bootstrap Tor is in. Derived from the percentage alone, so it works on every
/// Tor version: 0 starting, 1–4 conn_pt/proxy, 5 conn, 10 conn_done, 14 handshake, 15 done,
/// 20 onehop_create, 25–50 directory, 75 enough_dirinfo, 80–95 ap_*/circuit_create, 100 done.
enum BootstrapStage: String, Codable, Sendable, CaseIterable {
    case launch, firstHop, handshake, directory, circuits

    static func of(percent: Int) -> BootstrapStage {
        switch percent {
        case ..<5: .launch
        case ..<14: .firstHop
        case ..<25: .handshake
        case ..<75: .directory
        default: .circuits
        }
    }

    var title: String {
        switch self {
        case .launch: "starting"
        case .firstHop: "reaching the first relay"
        case .handshake: "TLS handshake"
        case .directory: "loading relay descriptors"
        case .circuits: "building circuits"
        }
    }
}

/// Why one attempt was abandoned. Only failures that say something about the transport are
/// recorded in the connect history; a busy port or a cancelled task say nothing about it.
enum AttemptFailure: String, Codable, Equatable, Sendable {
    case noInternet, ptLaunchFailed, firstHopRefused, firstHopTimeout
    case handshakeStalled, directoryStalled, circuitStalled
    case processExited, controlUnavailable, dataDirectoryLocked, portInUse, cancelled, deadlineExceeded

    var isFirstHop: Bool { self == .ptLaunchFailed || self == .firstHopRefused || self == .firstHopTimeout }

    var countsAgainstTransport: Bool {
        switch self {
        case .portInUse, .dataDirectoryLocked, .cancelled, .controlUnavailable, .noInternet: false
        default: true
        }
    }

    var summary: String {
        switch self {
        case .noInternet: "no Internet"
        case .ptLaunchFailed: "the pluggable transport did not start"
        case .firstHopRefused: "the first relay refused the connection"
        case .firstHopTimeout: "the first relay did not answer"
        case .handshakeStalled: "the TLS handshake stalled"
        case .directoryStalled: "loading relay descriptors stalled"
        case .circuitStalled: "circuit building stalled"
        case .processExited: "tor exited"
        case .controlUnavailable: "the control connection went quiet"
        case .dataDirectoryLocked: "another tor owns the data directory"
        case .portInUse: "a port was taken"
        case .cancelled: "cancelled"
        case .deadlineExceeded: "the attempt ran out of time"
        }
    }
}

struct BootstrapOutcome: Equatable, Sendable {
    var totalMillis: Int
    var firstHopMillis: Int?
    var stageMillis: [BootstrapStage: Int]
    var peakPercent: Int
    var warm: Bool
}

struct TorAttemptError: Error {
    let failure: AttemptFailure
    let stage: BootstrapStage
    let percent: Int
    let lastWarning: String?
    let lastReason: String?

    var localizedDescription: String {
        var text = "\(failure.summary) at \(stage.title) (\(percent)%)"
        if let lastReason { text += " — \(lastReason)" }
        else if let lastWarning { text += " — \(lastWarning)" }
        return text
    }
}

/// Supervises one bootstrap attempt from the events Tor pushes. A doomed attempt dies in seconds
/// instead of burning a flat stall budget; a slow but working one is protected by four escapes —
/// bytes read, a circuit event, an OR connection reaching CONNECTED, and a floor under every stall.
struct BootstrapWatchdog {
    struct Config: Equatable, Sendable {
        var stall: [BootstrapStage: Duration]
        var hardTimeout: Duration
        var firstHopDeadline: Duration
        var bytesProgressThreshold: UInt64 = 16_384
        var bytesWindow: Duration = .seconds(5)
        var distinctFailuresToAbort: Int = 3
        var warnCountToAbort: Int = 3
        /// BW ticks once a second whether or not traffic flows, so a gap means a dead connection.
        var controlSilenceLimit: Duration = .seconds(6)

        func budget(for stage: BootstrapStage) -> Duration { stall[stage] ?? .seconds(20) }
    }

    enum Signal: Sendable {
        case bootstrap(percent: Int, tag: String, warning: String?, reason: String?,
                       count: Int?, recommendation: String?, hostAddress: String?)
        case orConn(target: String, status: String, reason: String?)
        case circuitEvent
        case bytes(read: UInt64, written: UInt64)
        case transportLaunched(String)
        case circuitEstablished
        case managedProxyFailed(String)
        case processExited(Int32)
        case controlLost
        case externalAbort(AttemptFailure)
        case tick
    }

    enum Verdict: Equatable, Sendable {
        case keepWaiting
        case succeeded
        case abort(AttemptFailure)
    }

    let config: Config
    private let startedAt: ContinuousClock.Instant

    private(set) var stage: BootstrapStage = .launch
    private(set) var percent = 0
    private(set) var peakPercent = 0
    private(set) var stageEnteredAt: ContinuousClock.Instant
    private(set) var stageMillis: [BootstrapStage: Int] = [:]
    private(set) var firstHopMillis: Int?
    private(set) var lastWarning: String?
    private(set) var lastReason: String?
    private(set) var bytesEscapes = 0
    private(set) var circuitEscapes = 0
    private(set) var orConnected = 0

    private var lastProgressAt: ContinuousClock.Instant
    private var lastBandwidthAt: ContinuousClock.Instant?
    private var windowStartedAt: ContinuousClock.Instant
    private var windowStartRead: UInt64 = 0
    private var totalRead: UInt64 = 0
    private var sawLaunch = false
    private var firstHopReached = false
    private var hardFailures: Set<String> = []
    private var softFailures: Set<String> = []
    private var unclassifiedFailures = 0
    private var warnStreak = 0
    private var warnPercent = -1
    private var lastWarnCount = 0

    init(config: Config, startedAt: ContinuousClock.Instant) {
        self.config = config
        self.startedAt = startedAt
        stageEnteredAt = startedAt
        lastProgressAt = startedAt
        windowStartedAt = startedAt
    }

    var elapsedMillis: Int { Self.millis(startedAt.duration(to: .now)) }

    func secondsInStage(now: ContinuousClock.Instant) -> TimeInterval {
        Self.seconds(stageEnteredAt.duration(to: now))
    }

    func outcome(warm: Bool, now: ContinuousClock.Instant) -> BootstrapOutcome {
        var closed = stageMillis
        closed[stage] = (closed[stage] ?? 0) + Self.millis(stageEnteredAt.duration(to: now))
        return BootstrapOutcome(
            totalMillis: Self.millis(startedAt.duration(to: now)),
            firstHopMillis: firstHopMillis,
            stageMillis: closed,
            peakPercent: peakPercent,
            warm: warm
        )
    }

    /// Escape counters, logged with every abort so a false positive is visible in the field.
    var escapeSummary: String {
        "bytes-escapes \(bytesEscapes), circ-escapes \(circuitEscapes), orconn-connected \(orConnected)"
    }

    @discardableResult
    mutating func handle(_ signal: Signal, at now: ContinuousClock.Instant) -> Verdict {
        switch signal {
        case .processExited:
            return .abort(.processExited)
        case .controlLost:
            return .abort(.controlUnavailable)
        case .managedProxyFailed(let detail):
            lastReason = detail
            return .abort(.ptLaunchFailed)
        case .externalAbort(let failure):
            return .abort(failure)
        case .circuitEstablished:
            // Version-independent, and true even when the percentage never moves: Tor's bootstrap
            // counter is monotone and does not reset when the Bridge lines change under it.
            return .succeeded
        case .transportLaunched:
            sawLaunch = true
            return .keepWaiting
        case .circuitEvent:
            circuitEscapes += 1
            lastProgressAt = now
            return .keepWaiting
        case .bytes(let read, _):
            totalRead &+= read
            lastBandwidthAt = now
            if totalRead >= windowStartRead &+ config.bytesProgressThreshold {
                bytesEscapes += 1
                lastProgressAt = now
                windowStartRead = totalRead
                windowStartedAt = now
            } else if windowStartedAt.duration(to: now) > config.bytesWindow {
                windowStartRead = totalRead
                windowStartedAt = now
            }
            return .keepWaiting
        case .bootstrap(let value, _, let warning, let reason, let count, let recommendation, _):
            return handleBootstrap(value, warning: warning, reason: reason, count: count,
                                   recommendation: recommendation, at: now)
        case .orConn(let target, let status, let reason):
            return handleORConn(target: target, status: status, reason: reason)
        case .tick:
            return handleTick(now)
        }
    }

    private mutating func handleBootstrap(_ value: Int, warning: String?, reason: String?, count: Int?,
                                          recommendation: String?, at now: ContinuousClock.Instant) -> Verdict {
        if let warning { lastWarning = warning }
        if let reason { lastReason = reason }
        if value >= 100 {
            percent = 100
            peakPercent = 100
            return .succeeded
        }
        if value > percent {
            let previous = stage
            percent = value
            peakPercent = max(peakPercent, value)
            let next = BootstrapStage.of(percent: value)
            if next != previous {
                stageMillis[previous] = (stageMillis[previous] ?? 0) + Self.millis(stageEnteredAt.duration(to: now))
                stage = next
                stageEnteredAt = now
            }
            if firstHopMillis == nil, value >= 14 {
                firstHopMillis = Self.millis(startedAt.duration(to: now))
            }
            lastProgressAt = now
            warnStreak = 0
            warnPercent = -1
            lastWarnCount = 0
            return .keepWaiting
        }
        if recommendation == "warn" {
            if warnPercent != percent {
                warnPercent = percent
                warnStreak = 0
                lastWarnCount = 0
            }
            let step = max(1, (count ?? 0) - lastWarnCount)
            lastWarnCount = max(lastWarnCount, count ?? 0)
            warnStreak += step
            if warnStreak >= config.warnCountToAbort {
                return .abort(Self.failure(for: stage))
            }
        }
        return .keepWaiting
    }

    private mutating func handleORConn(target: String, status: String, reason: String?) -> Verdict {
        let relay = TorControlEvents.normalizeORTarget(target)
        switch status {
        case "LAUNCHED":
            sawLaunch = true
        case "CONNECTED":
            firstHopReached = true
            orConnected += 1
            hardFailures.removeAll()
            softFailures.removeAll()
            unclassifiedFailures = 0
        case "FAILED", "CLOSED":
            guard status == "FAILED", !firstHopReached, !relay.isEmpty else { break }
            switch reason {
            case "CONNECTREFUSED", "NOROUTE", "IDENTITY", "PT_MISSING":
                hardFailures.insert(relay)
                if hardFailures.count >= config.distinctFailuresToAbort { return .abort(.firstHopRefused) }
            case "TIMEOUT", "IOERROR", "CONNECTRESET", "RESOURCELIMIT":
                softFailures.insert(relay)
                if softFailures.count >= config.distinctFailuresToAbort { return .abort(.firstHopTimeout) }
            default:
                // An unknown or absent reason never triggers an abort on its own.
                unclassifiedFailures += 1
            }
        default:
            break
        }
        return .keepWaiting
    }

    private mutating func handleTick(_ now: ContinuousClock.Instant) -> Verdict {
        if !sawLaunch, startedAt.duration(to: now) > config.firstHopDeadline {
            return .abort(.ptLaunchFailed)
        }
        if let lastBandwidthAt, lastBandwidthAt.duration(to: now) > config.controlSilenceLimit {
            return .abort(.controlUnavailable)
        }
        if lastProgressAt.duration(to: now) > config.budget(for: stage) {
            return .abort(Self.failure(for: stage))
        }
        if startedAt.duration(to: now) > config.hardTimeout {
            return .abort(Self.failure(for: stage))
        }
        return .keepWaiting
    }

    static func failure(for stage: BootstrapStage) -> AttemptFailure {
        switch stage {
        case .launch, .firstHop: .firstHopTimeout
        case .handshake: .handshakeStalled
        case .directory: .directoryStalled
        case .circuits: .circuitStalled
        }
    }

    static func seconds(_ duration: Duration) -> TimeInterval {
        Double(duration.components.seconds) + Double(duration.components.attoseconds) / 1e18
    }

    static func millis(_ duration: Duration) -> Int {
        Int((seconds(duration) * 1000).rounded())
    }
}

/// The numbers each transport is judged by, and how they scale with what is already on disk.
enum BootstrapDefaults {
    static let stallSeconds: [AppSettings.Transport: [BootstrapStage: Double]] = [
        .direct: [.launch: 5, .firstHop: 10, .handshake: 8, .directory: 14, .circuits: 12],
        .obfs4: [.launch: 6, .firstHop: 12, .handshake: 10, .directory: 16, .circuits: 14],
        .custom: [.launch: 8, .firstHop: 14, .handshake: 12, .directory: 18, .circuits: 16],
        .meek: [.launch: 8, .firstHop: 16, .handshake: 12, .directory: 22, .circuits: 16],
        .snowflake: [.launch: 10, .firstHop: 28, .handshake: 16, .directory: 26, .circuits: 26],
        .auto: [.launch: 10, .firstHop: 28, .handshake: 16, .directory: 26, .circuits: 26],
    ]

    static let firstHopDeadlineSeconds: [AppSettings.Transport: Double] = [
        .direct: 6, .obfs4: 9, .custom: 12, .meek: 11, .snowflake: 22, .auto: 22,
    ]

    static let priorSuccess: [AppSettings.Transport: Double] = [
        .direct: 0.55, .obfs4: 0.45, .snowflake: 0.55, .meek: 0.35, .custom: 0.50, .auto: 0.50,
    ]

    static let priorBootstrapSeconds: [AppSettings.Transport: Double] = [
        .direct: 8, .obfs4: 14, .custom: 16, .meek: 25, .snowflake: 30, .auto: 30,
    ]

    /// A cold consensus means real directory work; a fresh one means almost none.
    static func consensusMultiplier(_ freshness: ConsensusInfo.Freshness) -> Double {
        switch freshness {
        case .fresh: 0.6
        case .live: 0.8
        case .stale: 1.2
        case .expired: 2.5
        case .missing: 3.0
        }
    }

    static func stall(transport: AppSettings.Transport, stage: BootstrapStage,
                      consensus: ConsensusInfo.Freshness, warmEngine: Bool,
                      marginalLink: Bool, learnedP80: TimeInterval?) -> Duration {
        let base = stallSeconds[transport]?[stage] ?? 20
        var multiplier = 1.0
        if stage == .directory { multiplier *= consensusMultiplier(consensus) }
        if warmEngine, stage == .launch || stage == .handshake { multiplier *= 0.7 }
        if marginalLink { multiplier *= 1.25 }
        let scaled = base * multiplier
        // A learned p80 widens the budget for a link that is simply slow; it never narrows it
        // below 60 % of the static value, so one lucky fast run cannot make the next one brittle.
        let value = learnedP80.map { max(scaled * 0.6, $0 * 1.8 + 2.0) } ?? scaled
        return .seconds(min(max(value, 4.0), scaled * 2.0))
    }

    static func hardTimeout(expectedSeconds: Double, consensus: ConsensusInfo.Freshness,
                            isLast: Bool, remaining: TimeInterval) -> Duration {
        if isLast { return .seconds(max(45, remaining)) }
        let value = max(30, expectedSeconds * 2.2 * consensusMultiplier(consensus))
        return .seconds(min(max(value, 30), 110))
    }
}
