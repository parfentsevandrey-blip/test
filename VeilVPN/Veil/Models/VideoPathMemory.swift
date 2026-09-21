import Foundation

/// The video path that worked last time — the exit YouTube was pinned to, the guard 8K mode
/// chose, and what they carried — so the next connection starts on it instead of measuring from
/// scratch: pinned within seconds of connecting, verified by one measurement, and raced again
/// only when it no longer carries most of what it did.
struct VideoPathMemory: Codable, Equatable, Sendable {
    var exitFingerprint: String
    var exitNickname: String
    var exitCountry: String?
    var exitBandwidth: Int
    var guardFingerprint: String?
    var guardNickname: String?
    /// What the exit carried when it was chosen; 0 behind a bridge, where nothing is measured.
    var megabits: Double
    var measuredAt: Date
    var transport: AppSettings.Transport

    /// A week: relays come and go, and a path that old is worth measuring from scratch.
    static let lifetime: TimeInterval = 7 * 24 * 3600
    /// How long an exit the video CDN turned away stays out of the running. Hours, not the week
    /// the path memory keeps: a country may hold only a handful of exits, and a week-long ban on
    /// each mistake would empty it.
    static let refusalLifetime: TimeInterval = 6 * 3600
    /// Below this the path is too slow for the CDN's verdict to mean anything: a connection that
    /// closes with little in it is what a slow path looks like, not what a refusal looks like.
    static let refusalFloorMegabits = 3.0
    /// The share of the remembered rate a re-measurement has to reach for the memory to stand.
    static let keepShare = 0.6

    /// The same transport, and not too old. A different transport is a different first hop, and
    /// the numbers say nothing about it.
    func isFresh(now: Date = .now, transport: AppSettings.Transport) -> Bool {
        self.transport == transport && now.timeIntervalSince(measuredAt) < Self.lifetime
    }
}
