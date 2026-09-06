import Foundation

/// How aggressively dummy traffic is injected.
enum PaddingLevel: String, Codable, CaseIterable, Identifiable, Sendable {
    /// Sporadic background noise only.
    case light
    /// Background noise plus front-loaded dummy bursts whenever real activity starts (FRONT-like).
    case balanced
    /// Additionally fills both directions up to a constant rate so volume leaks nothing.
    case strong

    var id: String { rawValue }
}

/// One dummy exchange: the client sends `upstreamBytes` of noise to the sink, which answers with
/// `downstreamBytes` of noise after `replyDelayMilliseconds`.
struct PaddingFrame: Equatable, Sendable {
    var upstreamBytes: Int
    var downstreamBytes: Int
    var replyDelayMilliseconds: Int

    static let headerSize = 12
    static let maximumBytes = 65_536
}

/// Padding-only traffic-analysis defence in the spirit of Mullvad's DAITA and the Maybenot
/// framework it is built on. It decides *when* and *how much* dummy traffic to send, based on what
/// the real traffic is doing, so that an observer between the Mac and the Snowflake proxy sees a
/// far noisier pattern:
///
/// - **background noise** — sporadic dummy exchanges with Poisson-distributed gaps;
/// - **burst defence** — when real activity starts, a front-loaded volley of dummy frames whose
///   timing follows a Rayleigh distribution (the FRONT defence), hiding the shape of page loads;
/// - **constant rate** — each direction is topped up to a target rate every tick.
///
/// Unlike DAITA it never delays real packets: it only adds traffic.
struct PaddingMachine {
    struct Parameters: Sendable {
        var backgroundMeanInterval: TimeInterval
        var backgroundUpstream: ClosedRange<Int>
        var backgroundDownstream: ClosedRange<Int>
        var burstFrames: ClosedRange<Int>?
        var burstWindow: ClosedRange<TimeInterval>
        var burstUpstream: ClosedRange<Int>
        var burstDownstream: ClosedRange<Int>
        var activityThreshold: Int
        var burstCooldown: TimeInterval
        var constantUpstreamRate: Int?
        var constantDownstreamRate: Int?

        static func forLevel(_ level: PaddingLevel) -> Parameters {
            switch level {
            case .light:
                return Parameters(
                    backgroundMeanInterval: 3.0,
                    backgroundUpstream: 300...1_200, backgroundDownstream: 800...5_000,
                    burstFrames: nil, burstWindow: 0...0, burstUpstream: 0...0, burstDownstream: 0...0,
                    activityThreshold: Int.max, burstCooldown: 0,
                    constantUpstreamRate: nil, constantDownstreamRate: nil
                )
            case .balanced:
                return Parameters(
                    backgroundMeanInterval: 1.5,
                    backgroundUpstream: 300...1_500, backgroundDownstream: 1_000...6_000,
                    burstFrames: 8...24, burstWindow: 2.0...6.0,
                    burstUpstream: 400...4_000, burstDownstream: 1_000...12_000,
                    activityThreshold: 3_000, burstCooldown: 4.0,
                    constantUpstreamRate: nil, constantDownstreamRate: nil
                )
            case .strong:
                return Parameters(
                    backgroundMeanInterval: 1.5,
                    backgroundUpstream: 300...1_500, backgroundDownstream: 1_000...6_000,
                    burstFrames: 8...24, burstWindow: 2.0...6.0,
                    burstUpstream: 400...4_000, burstDownstream: 1_000...12_000,
                    activityThreshold: 3_000, burstCooldown: 4.0,
                    constantUpstreamRate: 10_000, constantDownstreamRate: 24_000
                )
            }
        }
    }

    let level: PaddingLevel
    let parameters: Parameters
    private var nextBackgroundAt: TimeInterval
    private var burstQueue: [(at: TimeInterval, frame: PaddingFrame)] = []
    private var burstEndsAt: TimeInterval = -1_000

    init(level: PaddingLevel, now: TimeInterval) {
        let parameters = Parameters.forLevel(level)
        self.level = level
        self.parameters = parameters
        self.nextBackgroundAt = now + Self.exponential(mean: parameters.backgroundMeanInterval)
    }

    /// Called every tick with what happened during the tick (real and padding bytes per direction).
    mutating func tick(now: TimeInterval, elapsed: TimeInterval,
                       realUpstream: Int, realDownstream: Int,
                       paddingUpstream: Int, paddingDownstream: Int) -> [PaddingFrame] {
        var frames: [PaddingFrame] = []
        let p = parameters

        // 1. Background noise.
        if now >= nextBackgroundAt {
            frames.append(PaddingFrame(
                upstreamBytes: Int.random(in: p.backgroundUpstream),
                downstreamBytes: Int.random(in: p.backgroundDownstream),
                replyDelayMilliseconds: Int.random(in: 20...400)
            ))
            nextBackgroundAt = now + Self.exponential(mean: p.backgroundMeanInterval)
        }

        // 2. Front-loaded burst when real activity starts.
        let realTotal = realUpstream + realDownstream
        if let burstRange = p.burstFrames,
           realTotal >= p.activityThreshold,
           burstQueue.isEmpty,
           now >= burstEndsAt + p.burstCooldown {
            let count = Int.random(in: burstRange)
            let window = Double.random(in: p.burstWindow)
            let sigma = window / 2.448 // 95th percentile of the Rayleigh distribution lands at `window`
            for _ in 0..<count {
                let offset = min(window, Self.rayleigh(sigma: sigma))
                burstQueue.append((at: now + offset, frame: PaddingFrame(
                    upstreamBytes: Int.random(in: p.burstUpstream),
                    downstreamBytes: Int.random(in: p.burstDownstream),
                    replyDelayMilliseconds: Int.random(in: 0...250)
                )))
            }
            burstQueue.sort { $0.at < $1.at }
            burstEndsAt = now + window
        }
        while let first = burstQueue.first, first.at <= now {
            frames.append(first.frame)
            burstQueue.removeFirst()
        }

        // 3. Constant-rate fill.
        if let upRate = p.constantUpstreamRate, let downRate = p.constantDownstreamRate, elapsed > 0 {
            let scheduledUp = frames.reduce(0) { $0 + $1.upstreamBytes }
            let scheduledDown = frames.reduce(0) { $0 + $1.downstreamBytes }
            let deficitUp = Int(Double(upRate) * elapsed) - realUpstream - paddingUpstream - scheduledUp
            let deficitDown = Int(Double(downRate) * elapsed) - realDownstream - paddingDownstream - scheduledDown
            if deficitUp > 200 || deficitDown > 200 {
                frames.append(PaddingFrame(
                    upstreamBytes: Self.jitter(max(0, deficitUp)),
                    downstreamBytes: Self.jitter(max(0, deficitDown)),
                    replyDelayMilliseconds: Int.random(in: 0...120)
                ))
            }
        }

        return frames.map { frame in
            PaddingFrame(
                upstreamBytes: min(PaddingFrame.maximumBytes, max(0, frame.upstreamBytes)),
                downstreamBytes: min(PaddingFrame.maximumBytes, max(0, frame.downstreamBytes)),
                replyDelayMilliseconds: max(0, frame.replyDelayMilliseconds)
            )
        }
    }

    private static func exponential(mean: TimeInterval) -> TimeInterval {
        -mean * log(1 - Double.random(in: 0..<1))
    }

    private static func rayleigh(sigma: Double) -> Double {
        sigma * sqrt(-2 * log(1 - Double.random(in: 0..<1)))
    }

    private static func jitter(_ value: Int) -> Int {
        guard value > 0 else { return 0 }
        return Int(Double(value) * Double.random(in: 0.7...1.3))
    }
}
