import Foundation
import Observation

/// One relay measured during a circuit race.
struct RelayCandidate: Identifiable, Equatable, Sendable {
    let fingerprint: String
    let nickname: String
    var countryCode: String?
    /// How long the fastest circuit through this relay took to build.
    var buildMilliseconds: Int
    /// Consensus weight (kB/s), when known.
    var bandwidth: Int?

    var id: String { fingerprint }

    var flag: String {
        guard let countryCode, countryCode.count == 2 else { return "" }
        return countryCode.flagEmoji
    }
}

/// Finds the quickest relays for a route by racing circuits: Tor builds several circuits under
/// the country constraints, Veil times how long each takes to come up and pins the exits (and,
/// with a chosen middle country, the middle) of the winners. A circuit that builds fast runs
/// through relays that answer fast, and pinning them keeps every later circuit on them.
@MainActor
@Observable
final class RouteTuner {
    enum Status: Equatable, Sendable {
        case idle
        case racing(launched: Int, built: Int)
        case pinned
        case failed(String)

        var isRacing: Bool {
            if case .racing = self { return true }
            return false
        }
    }

    private(set) var status: Status = .idle
    private(set) var pinnedExits: [RelayCandidate] = []
    private(set) var pinnedMiddle: RelayCandidate?
    /// Every exit measured in the last race, fastest first.
    private(set) var candidates: [RelayCandidate] = []
    private(set) var lastRun: Date?
    /// The country-level route the pins belong to.
    private(set) var baseRoute: TorRoute?

    @ObservationIgnored var onLog: (@MainActor (LogEntry) -> Void)?

    var isPinned: Bool { !pinnedExits.isEmpty || pinnedMiddle != nil }

    /// `base` with the measured pins applied — only when they were measured for that same route.
    func route(for base: TorRoute) -> TorRoute {
        guard isPinned, let baseRoute, baseRoute == base.base else { return base }
        var tuned = base
        tuned.pinnedExits = pinnedExits.map(\.fingerprint)
        tuned.pinnedMiddles = pinnedMiddle.map { [$0.fingerprint] } ?? []
        return tuned
    }

    func reset() {
        status = .idle
        pinnedExits = []
        pinnedMiddle = nil
        candidates = []
        baseRoute = nil
    }

    /// Races `count` circuits for `route` and pins the winners. Returns the tuned route, or nil
    /// when nothing could be measured. Tor must already be on `route` (without pins).
    func tune(engine: any TorEngine, route: TorRoute, circuits count: Int = 4, pinMiddle: Bool, timeout: Duration = .seconds(25)) async -> TorRoute? {
        status = .racing(launched: 0, built: 0)
        var ids: [String] = []
        for attempt in 0..<count {
            do {
                let id = try await engine.launchCircuit()
                ids.append(id)
                status = .racing(launched: ids.count, built: 0)
            } catch {
                onLog?(.veil(.debug, "Circuit race: launch \(attempt + 1) refused: \(error.localizedDescription)"))
            }
            if Task.isCancelled {
                status = .idle
                return nil
            }
            try? await Task.sleep(for: .milliseconds(180))
        }
        guard !ids.isEmpty else {
            status = .failed(String(localized: "Tor refused to build test circuits."))
            return nil
        }

        var built: [CircuitInfo] = []
        let deadline = ContinuousClock.now + timeout
        for id in ids {
            let remaining = max(.seconds(1), deadline - ContinuousClock.now)
            if let info = await engine.awaitCircuit(id, timeout: remaining), info.status == .built {
                built.append(info)
                status = .racing(launched: ids.count, built: built.count)
            }
            if Task.isCancelled {
                status = .idle
                return nil
            }
        }

        let ranking = Self.rank(built)
        guard !ranking.exits.isEmpty else {
            status = .failed(String(localized: "No test circuit was built in time."))
            onLog?(.veil(.warn, "Circuit race: none of \(ids.count) circuits was built within \(Int(timeout.components.seconds)) s"))
            return nil
        }

        var exits = Array(ranking.exits.prefix(2))
        for index in exits.indices {
            exits[index].bandwidth = await engine.relayBandwidth(exits[index].fingerprint)
            exits[index].countryCode = await engine.relayCountry(exits[index].fingerprint)
        }
        exits = Self.preferBandwidth(exits)

        var middle: RelayCandidate?
        if pinMiddle, var best = ranking.middles.first {
            best.bandwidth = await engine.relayBandwidth(best.fingerprint)
            best.countryCode = await engine.relayCountry(best.fingerprint)
            middle = best
        }
        if Task.isCancelled {
            status = .idle
            return nil
        }

        // The losers served their purpose; close them so they do not linger as spare circuits.
        let winners = Set(exits.map(\.fingerprint))
        for info in built where !(info.exit.map { winners.contains($0.fingerprint) } ?? false) {
            await engine.closeCircuit(info.id)
        }

        pinnedExits = exits
        pinnedMiddle = middle
        candidates = ranking.exits
        lastRun = .now
        baseRoute = route.base
        status = .pinned
        let summary = exits.map { "\($0.nickname) \($0.buildMilliseconds) ms" }.joined(separator: ", ")
        onLog?(.veil(.notice, "Circuit race: \(built.count)/\(ids.count) built; pinned exits \(summary)\(middle.map { "; middle \($0.nickname) \($0.buildMilliseconds) ms" } ?? "")"))
        return route(for: route)
    }

    /// Fastest exits and middles among built circuits, deduplicated by relay, with laggards
    /// (slower than 2.5× the best, and at least 400 ms behind) dropped.
    static func rank(_ circuits: [CircuitInfo]) -> (exits: [RelayCandidate], middles: [RelayCandidate]) {
        var exits: [String: RelayCandidate] = [:]
        var middles: [String: RelayCandidate] = [:]
        for info in circuits where info.status == .built {
            guard let exit = info.exit, let seconds = info.buildTime else { continue }
            let milliseconds = Int((seconds * 1000).rounded())
            if exits[exit.fingerprint].map({ $0.buildMilliseconds > milliseconds }) ?? true {
                exits[exit.fingerprint] = RelayCandidate(fingerprint: exit.fingerprint, nickname: exit.nickname, countryCode: nil, buildMilliseconds: milliseconds, bandwidth: nil)
            }
            if let middle = info.middle, middles[middle.fingerprint].map({ $0.buildMilliseconds > milliseconds }) ?? true {
                middles[middle.fingerprint] = RelayCandidate(fingerprint: middle.fingerprint, nickname: middle.nickname, countryCode: nil, buildMilliseconds: milliseconds, bandwidth: nil)
            }
        }
        func trimmed(_ table: [String: RelayCandidate]) -> [RelayCandidate] {
            let sorted = table.values.sorted { $0.buildMilliseconds < $1.buildMilliseconds }
            guard let best = sorted.first else { return [] }
            let cutoff = max(best.buildMilliseconds * 5 / 2, best.buildMilliseconds + 400)
            return sorted.filter { $0.buildMilliseconds <= cutoff }
        }
        return (trimmed(exits), trimmed(middles))
    }

    /// When two exits build almost equally fast, put the one with far more capacity first.
    static func preferBandwidth(_ exits: [RelayCandidate]) -> [RelayCandidate] {
        guard exits.count == 2, let first = exits[0].bandwidth, let second = exits[1].bandwidth else { return exits }
        let closeEnough = Double(exits[1].buildMilliseconds) <= Double(exits[0].buildMilliseconds) * 1.15
        if closeEnough, second >= first * 3 {
            return [exits[1], exits[0]]
        }
        return exits
    }
}
