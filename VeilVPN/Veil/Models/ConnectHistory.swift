import Foundation

/// How one attempt ended, for the history.
enum AttemptResultKind: Equatable, Sendable {
    case success(BootstrapOutcome)
    case failure(AttemptFailure, stage: BootstrapStage, percent: Int)
}

struct AttemptResult: Sendable {
    var transport: AppSettings.Transport
    var kind: AttemptResultKind
}

/// What this Mac has learned about one transport on one network.
struct TransportRecord: Codable, Equatable, Sendable {
    var attempts = 0
    var successes = 0
    /// +1 per consecutive success, -1 per consecutive failure; resets to ±1 on a sign change.
    var streak = 0
    var lastSuccess: Date?
    var lastAttempt: Date?
    /// Newest first, capped at 8.
    var bootstrapMillis: [Int] = []
    var stageMillis: [String: [Int]] = [:]
    var failures: [String: Int] = [:]

    static let reservoir = 8
    static let decayThreshold = 60

    /// Laplace-smoothed, so one bad evening never zeroes a transport that usually works.
    var successRate: Double { Double(successes + 1) / Double(attempts + 2) }

    var bootstrapP50: Double? {
        guard !bootstrapMillis.isEmpty else { return nil }
        let sorted = bootstrapMillis.map { Double($0) / 1000 }.sorted()
        return LatencySummary.percentile(sorted, 0.5)
    }

    /// Nil under three samples: two numbers do not describe a distribution.
    func stageP80(_ stage: BootstrapStage) -> TimeInterval? {
        guard let values = stageMillis[stage.rawValue], values.count >= 3 else { return nil }
        return LatencySummary.percentile(values.map { Double($0) / 1000 }.sorted(), 0.8)
    }

    var dominantFailure: AttemptFailure? {
        guard let raw = failures.max(by: { $0.value < $1.value })?.key else { return nil }
        return AttemptFailure(rawValue: raw)
    }

    static func insert(_ value: Int, into reservoir: inout [Int]) {
        reservoir.insert(value, at: 0)
        if reservoir.count > Self.reservoir { reservoir.removeLast(reservoir.count - Self.reservoir) }
    }

    mutating func record(_ kind: AttemptResultKind, at date: Date) {
        attempts += 1
        lastAttempt = date
        switch kind {
        case .success(let outcome):
            successes += 1
            streak = streak > 0 ? streak + 1 : 1
            lastSuccess = date
            Self.insert(outcome.totalMillis, into: &bootstrapMillis)
            for (stage, millis) in outcome.stageMillis {
                var values = stageMillis[stage.rawValue] ?? []
                Self.insert(millis, into: &values)
                stageMillis[stage.rawValue] = values
            }
        case .failure(let failure, _, _):
            streak = streak < 0 ? streak - 1 : -1
            failures[failure.rawValue, default: 0] += 1
        }
        decayIfNeeded()
    }

    /// A network that changes behaviour should be believed again within ~20 attempts.
    private mutating func decayIfNeeded() {
        guard attempts > Self.decayThreshold else { return }
        attempts /= 2
        successes /= 2
        for (key, value) in failures {
            let halved = value / 2
            if halved == 0 { failures[key] = nil } else { failures[key] = halved }
        }
    }
}

struct NetworkHistory: Codable, Equatable, Sendable {
    var kind: String = NetworkFingerprint.Kind.other.rawValue
    var firstSeen: Date = .now
    var lastSeen: Date = .now
    var attempts = 0
    var transports: [String: TransportRecord] = [:]

    func record(_ transport: AppSettings.Transport) -> TransportRecord? {
        transports[transport.rawValue]
    }
}

/// Every network this Mac has tried to reach Tor from. No SSID, no router address, no DNS server:
/// the key is a salted 64-bit hash, and the file is written 0600.
struct ConnectHistoryFile: Codable, Equatable, Sendable {
    var version = 1
    var updated: Date = .now
    var networks: [String: NetworkHistory] = [:]

    static let currentVersion = 1
    static let maxNetworks = 24
    static let maxAgeDays: TimeInterval = 90

    enum CodingKeys: String, CodingKey { case version, updated, networks }

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        version = try container.decodeIfPresent(Int.self, forKey: .version) ?? 1
        updated = try container.decodeIfPresent(Date.self, forKey: .updated) ?? .now
        networks = try container.decodeIfPresent([String: NetworkHistory].self, forKey: .networks) ?? [:]
    }

    mutating func prune(now: Date = .now) {
        networks = networks.filter { now.timeIntervalSince($0.value.lastSeen) < Self.maxAgeDays * 24 * 3600 }
        guard networks.count > Self.maxNetworks else { return }
        let ordered = networks.sorted { $0.value.lastSeen > $1.value.lastSeen }.prefix(Self.maxNetworks)
        networks = Dictionary(uniqueKeysWithValues: ordered.map { ($0.key, $0.value) })
    }

    mutating func record(_ result: AttemptResult, for fingerprint: NetworkFingerprint, at date: Date = .now) {
        if case .failure(let failure, _, _) = result.kind, !failure.countsAgainstTransport { return }
        var history = networks[fingerprint.id] ?? NetworkHistory(kind: fingerprint.kind.rawValue,
                                                                 firstSeen: date, lastSeen: date)
        history.kind = fingerprint.kind.rawValue
        history.lastSeen = date
        history.attempts += 1
        var record = history.transports[result.transport.rawValue] ?? TransportRecord()
        record.record(result.kind, at: date)
        history.transports[result.transport.rawValue] = record
        networks[fingerprint.id] = history
        updated = date
        prune(now: date)
    }
}
