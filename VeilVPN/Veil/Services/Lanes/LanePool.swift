import Foundation

/// Holds several measured Tor circuits open by holding several SOCKS credential pairs, and issues
/// each new connection a lease on the one that is currently fast. Tor's `IsolateSOCKSAuth`
/// guarantees two streams with different pairs never share a circuit, so N pairs held open *are*
/// N parallel circuits — with no control-port involvement at all.
///
/// Retiring a lane is a string write under a lock. It cannot reach an already-open upstream socket,
/// whose isolation key was consumed at handshake time. That is the whole seamlessness argument.
final class LanePool: @unchecked Sendable {
    struct Configuration: Equatable, Sendable {
        var laneCount = 4
        var siteMode = false
        var siteKeyCapacity = 12
        var maxStreamsPerLane = 12
        var probeRound: TimeInterval = 20
        var probeStagger: TimeInterval = 0.25
        var probeTimeout: TimeInterval = 8
        var warmupTimeout: TimeInterval = 15
        var scoreWindow = 6
        var liveWindow = 16
        /// Always below `MaxCircuitDirtiness 600`, so measured replacement always fires first.
        var laneLifetime: TimeInterval = 480
        var lifetimeJitter = 0.15
        var evaluateInterval: TimeInterval = 30
        var slowRetirementsPerWindow = 4
        var churnWindow: TimeInterval = 600
        var churnFreeze: TimeInterval = 1800
        var fastSetFactor = 1.25
        var affinityTTL: TimeInterval = 300
        var affinityCapacity = 512
        /// Skip a lane's probe when real traffic has already proved it recently.
        var passiveSkipCount = 3
        var passiveSkipWindow: TimeInterval = 30
        var activeSampleMaxAge: TimeInterval = 60
        var hedgingEnabled = true
        var limits = LanePolicy.Limits()
    }

    private struct Entry {
        var scope: LaneScope
        var generation: UInt32
        var state: LaneState
        var credentials: SOCKS5.Credentials
        var probeRing: [TimeInterval] = []
        var liveRing: [TimeInterval] = []
        var consecutiveFailures = 0
        var inFlight = 0
        var openedAt = Date.now
        var deadline = Date.now
        var lastActiveSampleAt: Date?
        var passiveSuccesses: [Date] = []
        var probing = false
        var warmupAttempts = 0
    }

    private struct Affinity {
        var lane: Int
        var generation: UInt32
        var lastUsed: Date
    }

    private let poolQueue = DispatchQueue(label: "app.veilvpn.lanepool")
    private let lock = NSLock()

    private var entries: [Int: Entry] = [:]
    private var affinity: [String: Affinity] = [:]
    private var configuration = Configuration()
    private var poolPort: UInt16?
    private var running = false
    private var suspendedReason: String?
    private var round = 0
    private var counters = LanePoolSnapshot()
    private var lastRetirement: Date?
    private var slowFrozenUntil: Date?
    private var slowRetirements: [Date] = []
    private var timer: DispatchSourceTimer?
    private var evaluator: DispatchSourceTimer?
    private var nextSiteSlot = 0

    /// Fired after the lock is released, on the pool's own queue. `LanePool` never calls into the
    /// bridge, so a lock-order inversion between the two is structurally impossible.
    var onLog: (@Sendable (LogEntry) -> Void)?

    // MARK: Lifecycle

    func start(poolPort: UInt16, configuration: Configuration) {
        var messages: [LogEntry] = []
        lock.lock()
        self.poolPort = poolPort
        self.configuration = configuration
        running = true
        suspendedReason = nil
        counters = LanePoolSnapshot(enabled: true, poolPort: poolPort, siteMode: configuration.siteMode)
        entries = [:]
        affinity = [:]
        round = 0
        lastRetirement = nil
        slowFrozenUntil = nil
        slowRetirements = []
        nextSiteSlot = 0
        let count = min(6, max(2, configuration.laneCount))
        for index in 0..<count {
            entries[index] = makeEntry(scope: .pool(index), index: index, generation: 1, laneCount: count)
        }
        let warmups = entries.keys.sorted()
        let stagger = configuration.probeStagger
        lock.unlock()
        messages.append(.veil(.info, "Lane pool: \(count) measured circuits on 127.0.0.1:\(poolPort)"))
        messages.forEach { emit($0) }
        for (offset, index) in warmups.enumerated() {
            scheduleWarmup(lane: index, after: Double(offset) * stagger)
        }
        startTimers()
    }

    func stop() {
        lock.lock()
        running = false
        entries = [:]
        affinity = [:]
        poolPort = nil
        counters = LanePoolSnapshot()
        lock.unlock()
        timer?.cancel()
        timer = nil
        evaluator?.cancel()
        evaluator = nil
    }

    /// The bridge quietly reverts to the single-circuit path. Never persisted: a bad session must
    /// not silently turn the feature off for good.
    func suspend(reason: String) {
        lock.lock()
        guard running, suspendedReason == nil else {
            lock.unlock()
            return
        }
        suspendedReason = reason
        for key in entries.keys { entries[key]?.state = .suspended }
        lock.unlock()
        emit(.veil(.warn, "Lane pool suspended (\(reason)); using a single circuit"))
    }

    func setSiteMode(_ on: Bool) {
        lock.lock()
        guard running, configuration.siteMode != on else {
            lock.unlock()
            return
        }
        configuration.siteMode = on
        counters.siteMode = on
        lock.unlock()
        retireAll(reason: .resized)
    }

    func setLaneCount(_ count: Int) {
        let clamped = min(6, max(2, count))
        lock.lock()
        guard running, configuration.laneCount != clamped else {
            lock.unlock()
            return
        }
        configuration.laneCount = clamped
        let existing = entries.filter { !$0.value.scope.isSite }
        var warmups: [Int] = []
        if existing.count > clamped {
            for index in existing.keys.sorted().dropFirst(clamped) { entries[index] = nil }
        } else {
            for index in existing.count..<clamped where entries[index] == nil {
                entries[index] = makeEntry(scope: .pool(index), index: index, generation: 1, laneCount: clamped)
                warmups.append(index)
            }
        }
        let stagger = configuration.probeStagger
        lock.unlock()
        for (offset, index) in warmups.enumerated() {
            scheduleWarmup(lane: index, after: Double(offset) * stagger)
        }
    }

    func setHedging(_ on: Bool) {
        lock.lock()
        configuration.hedgingEnabled = on
        lock.unlock()
    }

    var hedgingEnabled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return configuration.hedgingEnabled
    }

    func retireAll(reason: LaneRetireReason) {
        lock.lock()
        guard running else {
            lock.unlock()
            return
        }
        let keys = entries.keys.sorted()
        for key in keys { _retire(lane: key, reason: reason) }
        affinity = [:]
        let stagger = configuration.probeStagger
        lock.unlock()
        emit(.veil(.info, "Lane pool: every circuit replaced (\(reason.rawValue))"))
        for (offset, index) in keys.enumerated() {
            scheduleWarmup(lane: index, after: Double(offset) * stagger)
        }
    }

    // MARK: Leasing

    /// Nil is never an error: the caller falls back to the legacy no-auth path, byte-identically to
    /// the behaviour before the pool existed. User traffic never waits on it.
    func lease(site: String, avoiding: Int?) -> LaneLease? {
        lock.lock()
        guard running, suspendedReason == nil, let poolPort else {
            counters.assignmentsLegacy += 1
            lock.unlock()
            return nil
        }
        if configuration.siteMode, !site.isEmpty, let lease = _siteLease(site: site, port: poolPort) {
            counters.assignmentsByAffinity += 1
            lock.unlock()
            return lease
        }
        var bound: Int?
        if let stored = affinity[site], stored.generation == entries[stored.lane]?.generation {
            bound = stored.lane
        }
        let choice = LaneScheduler.pick(rows: _rows(), affinity: bound, avoiding: avoiding,
                                        maxStreamsPerLane: configuration.maxStreamsPerLane,
                                        fastSetFactor: configuration.fastSetFactor,
                                        randomValue: Double.random(in: 0..<1))
        guard let choice, var entry = entries[choice.lane] else {
            counters.assignmentsLegacy += 1
            lock.unlock()
            return nil
        }
        entry.inFlight += 1
        entries[choice.lane] = entry
        if !site.isEmpty {
            affinity[site] = Affinity(lane: choice.lane, generation: entry.generation, lastUsed: .now)
            _trimAffinity()
        }
        switch choice.reason {
        case .affinity: counters.assignmentsByAffinity += 1
        case .best: counters.assignmentsByScore += 1
        case .fallback: counters.assignmentsFallback += 1
        }
        let lease = LaneLease(lane: choice.lane, generation: entry.generation,
                              credentials: entry.credentials, port: poolPort, reason: choice.reason)
        lock.unlock()
        return lease
    }

    /// The hedge target: strictly the lowest measured p50 that is not the lane we are abandoning.
    func bestLane(avoiding: Int?) -> LaneLease? {
        lock.lock()
        guard running, suspendedReason == nil, let poolPort,
              let lane = LaneScheduler.best(rows: _rows(), avoiding: avoiding),
              var entry = entries[lane] else {
            lock.unlock()
            return nil
        }
        entry.inFlight += 1
        entries[lane] = entry
        let lease = LaneLease(lane: lane, generation: entry.generation, credentials: entry.credentials,
                              port: poolPort, reason: .best)
        lock.unlock()
        return lease
    }

    func release(_ lease: LaneLease) {
        lock.lock()
        // Counted per lane index, not per generation, so a session opened before a retirement still
        // decrements the right counter.
        if var entry = entries[lease.lane] {
            entry.inFlight = max(0, entry.inFlight - 1)
            entries[lease.lane] = entry
        }
        lock.unlock()
    }

    func report(_ outcome: LaneOutcome) {
        lock.lock()
        switch outcome {
        case .connected(let lease, let seconds, let isOnion):
            if var entry = entries[lease.lane], entry.generation == lease.generation {
                entry.consecutiveFailures = 0
                // A six-hop rendezvous latency says nothing about the lane.
                if !isOnion {
                    entry.liveRing.insert(seconds, at: 0)
                    if entry.liveRing.count > configuration.liveWindow { entry.liveRing.removeLast() }
                    entry.passiveSuccesses.insert(.now, at: 0)
                    if entry.passiveSuccesses.count > 16 { entry.passiveSuccesses.removeLast() }
                }
                entries[lease.lane] = entry
            }
        case .failed(let lease, let code):
            if let code, !SOCKS5.laneAttributable(code) { break }
            if var entry = entries[lease.lane], entry.generation == lease.generation {
                entry.consecutiveFailures += 1
                entries[lease.lane] = entry
            }
            counters.retriesAttempted += 1
        case .timedOut(let lease):
            if var entry = entries[lease.lane], entry.generation == lease.generation {
                entry.consecutiveFailures += 1
                entries[lease.lane] = entry
            }
            counters.deadlineExpiries += 1
        case .abandoned:
            counters.hedgesStarted += 1
        }
        lock.unlock()
    }

    func noteHedgeWon() {
        lock.lock()
        counters.hedgesWon += 1
        counters.retriesSucceeded += 1
        lock.unlock()
    }

    func snapshot() -> LanePoolSnapshot {
        lock.lock()
        defer { lock.unlock() }
        var result = counters
        result.enabled = running
        result.suspended = suspendedReason
        result.poolPort = poolPort
        result.siteMode = configuration.siteMode
        result.rows = _rows()
        result.bestP50 = result.rows.filter { $0.isReady && !$0.scope.isSite }.compactMap(\.p50).min()
        return result
    }

    // MARK: Credentials

    /// The username is stable across generations so logs group by lane, and never contains a
    /// hostname: Tor stores the pair verbatim and echoes it in STREAM events.
    static func makeCredentials(scope: LaneScope, generation: UInt32,
                                random: () -> UInt64 = { UInt64.random(in: 0...UInt64.max) }) -> SOCKS5.Credentials {
        let prefix = scope.isSite ? "veil-s" : "veil-l"
        let password = String(format: "%016lx%016lx", random(), random())
        return SOCKS5.Credentials(username: "\(prefix)\(scope.index)", password: password)
    }

    // MARK: Private, lock held

    private func makeEntry(scope: LaneScope, index: Int, generation: UInt32, laneCount: Int) -> Entry {
        let jitter = configuration.lifetimeJitter
        let spread = configuration.laneLifetime * (1 - jitter + 2 * jitter * Double.random(in: 0..<1))
        let stagger = configuration.laneLifetime * Double(index) / Double(max(1, laneCount))
        return Entry(scope: scope,
                     generation: generation,
                     state: .warming,
                     credentials: Self.makeCredentials(scope: scope, generation: generation),
                     openedAt: .now,
                     deadline: Date.now.addingTimeInterval(spread + stagger))
    }

    private func _rows() -> [LaneRow] {
        entries.keys.sorted().compactMap { key in
            guard let entry = entries[key] else { return nil }
            let probes = entry.probeRing.sorted()
            let live = entry.liveRing.sorted()
            let cutoff = Date.now.addingTimeInterval(-configuration.passiveSkipWindow)
            return LaneRow(
                id: key,
                scope: entry.scope,
                generation: entry.generation,
                state: entry.state,
                p50: probes.count >= 1 ? LatencySummary.percentile(probes, 0.5) : nil,
                p90: probes.count >= 2 ? LatencySummary.percentile(probes, 0.9) : nil,
                liveP50: live.isEmpty ? nil : LatencySummary.percentile(live, 0.5),
                probeSamples: entry.probeRing.count,
                consecutiveFailures: entry.consecutiveFailures,
                inFlight: entry.inFlight,
                assignedSites: affinity.values.filter { $0.lane == key }.count,
                openedAt: entry.openedAt,
                lastActiveSampleAt: entry.lastActiveSampleAt,
                recentPassiveSuccesses: entry.passiveSuccesses.filter { $0 > cutoff }.count
            )
        }
    }

    /// Site mode gives exactly `IsolateDestAddr`'s isolation for bridge traffic, applied live.
    private func _siteLease(site: String, port: UInt16) -> LaneLease? {
        if let stored = affinity[site], let entry = entries[stored.lane], entry.scope.isSite,
           entry.generation == stored.generation, entry.state != .suspended {
            var updated = entry
            updated.inFlight += 1
            entries[stored.lane] = updated
            affinity[site] = Affinity(lane: stored.lane, generation: entry.generation, lastUsed: .now)
            return LaneLease(lane: stored.lane, generation: entry.generation,
                             credentials: entry.credentials, port: port, reason: .affinity)
        }
        let siteLanes = entries.filter { $0.value.scope.isSite }
        var slot: Int?
        if siteLanes.count < configuration.siteKeyCapacity {
            slot = 1000 + nextSiteSlot
            nextSiteSlot += 1
        } else {
            // Evict the least recently used site key; its future connections get a fresh circuit.
            let bound = Set(affinity.values.map(\.lane))
            slot = siteLanes.keys.first { !bound.contains($0) }
                ?? affinity.min { $0.value.lastUsed < $1.value.lastUsed }?.value.lane
        }
        guard let slot else { return nil }
        let index = slot - 1000
        var entry = entries[slot] ?? makeEntry(scope: .site(max(0, index)), index: 0, generation: 1, laneCount: 1)
        if entries[slot] != nil {
            entry.generation &+= 1
            entry.credentials = Self.makeCredentials(scope: entry.scope, generation: entry.generation)
            entry.probeRing = []
            entry.liveRing = []
            entry.consecutiveFailures = 0
        }
        // A site key is never probed: a brand-new circuit has nothing to be compared against.
        entry.state = .ready
        entry.inFlight += 1
        entries[slot] = entry
        affinity[site] = Affinity(lane: slot, generation: entry.generation, lastUsed: .now)
        _trimAffinity()
        return LaneLease(lane: slot, generation: entry.generation, credentials: entry.credentials,
                         port: port, reason: .affinity)
    }

    private func _trimAffinity() {
        let cutoff = Date.now.addingTimeInterval(-configuration.affinityTTL)
        affinity = affinity.filter { $0.value.lastUsed > cutoff }
        guard affinity.count > configuration.affinityCapacity else { return }
        let keep = affinity.sorted { $0.value.lastUsed > $1.value.lastUsed }.prefix(configuration.affinityCapacity)
        affinity = Dictionary(uniqueKeysWithValues: keep.map { ($0.key, $0.value) })
    }

    /// A key bump, nothing more: no CLOSECIRCUIT, no NEWNYM, no drain. Tor keeps a circuit while
    /// streams are attached, and `KeepAliveIsolateSOCKSAuth` stops its dirtiness timer even
    /// starting until it is idle, so an open upstream is untouched.
    private func _retire(lane: Int, reason: LaneRetireReason) {
        guard var entry = entries[lane] else { return }
        affinity = affinity.filter { $0.value.lane != lane }
        entry.generation &+= 1
        entry.credentials = Self.makeCredentials(scope: entry.scope, generation: entry.generation)
        entry.state = .warming
        entry.probeRing = []
        entry.liveRing = []
        entry.passiveSuccesses = []
        entry.consecutiveFailures = 0
        entry.lastActiveSampleAt = nil
        entry.probing = false
        entry.warmupAttempts = 0
        entry.openedAt = .now
        let jitter = configuration.lifetimeJitter
        entry.deadline = Date.now.addingTimeInterval(configuration.laneLifetime * (1 - jitter + 2 * jitter * Double.random(in: 0..<1)))
        entries[lane] = entry
        counters.lastRetirement = "lane \(lane): \(reason.rawValue)"
        lastRetirement = .now
        if reason == .slow {
            slowRetirements.insert(.now, at: 0)
            let cutoff = Date.now.addingTimeInterval(-configuration.churnWindow)
            slowRetirements = slowRetirements.filter { $0 > cutoff }
            if LanePolicy.shouldFreezeSlowRetirement(recentSlowRetirements: slowRetirements.count,
                                                     perWindow: configuration.slowRetirementsPerWindow) {
                slowFrozenUntil = Date.now.addingTimeInterval(configuration.churnFreeze)
            }
        }
    }

    // MARK: Timers

    private func startTimers() {
        timer?.cancel()
        let probeTimer = DispatchSource.makeTimerSource(queue: poolQueue)
        // The first real round comes early: the warm-up sample is discarded (it measures circuit
        // construction, not a round trip), so without this the pool has no ranking — and the
        // dashboard no latency figure — for a full round after connecting.
        probeTimer.schedule(deadline: .now() + min(5, configuration.probeRound),
                            repeating: configuration.probeRound)
        probeTimer.setEventHandler { [weak self] in self?.probeRound() }
        probeTimer.resume()
        timer = probeTimer

        evaluator?.cancel()
        let evaluateTimer = DispatchSource.makeTimerSource(queue: poolQueue)
        evaluateTimer.schedule(deadline: .now() + configuration.evaluateInterval, repeating: configuration.evaluateInterval)
        evaluateTimer.setEventHandler { [weak self] in self?.evaluate() }
        evaluateTimer.resume()
        evaluator = evaluateTimer
    }

    private func scheduleWarmup(lane: Int, after delay: TimeInterval) {
        poolQueue.asyncAfter(deadline: .now() + delay) { [weak self] in
            self?.probe(lane: lane, warmup: true)
        }
    }

    /// Every lane in a round probes the same target, which is what makes the numbers comparable;
    /// rotating targets between rounds stops one busy anycast site colouring the ranking.
    private func probeRound() {
        lock.lock()
        guard running, suspendedReason == nil else {
            lock.unlock()
            return
        }
        let now = Date.now
        var due: [Int] = []
        for key in entries.keys.sorted() {
            guard let entry = entries[key], !entry.probing, !entry.scope.isSite else { continue }
            let cutoff = now.addingTimeInterval(-configuration.passiveSkipWindow)
            let passive = entry.passiveSuccesses.filter { $0 > cutoff }.count
            let fresh = entry.lastActiveSampleAt.map { now.timeIntervalSince($0) < configuration.activeSampleMaxAge } ?? false
            if passive >= configuration.passiveSkipCount, fresh { continue }
            due.append(key)
        }
        round += 1
        let stagger = configuration.probeStagger
        lock.unlock()
        for (offset, lane) in due.enumerated() {
            poolQueue.asyncAfter(deadline: .now() + Double(offset) * stagger) { [weak self] in
                self?.probe(lane: lane, warmup: false)
            }
        }
    }

    /// The warm-up probe *is* the pre-build: its CONNECT is the first stream carrying that isolation
    /// key, so Tor attaches it to one of its preemptive clean circuits. Its own sample is discarded,
    /// because it includes construction rather than a round trip.
    private func probe(lane: Int, warmup: Bool) {
        lock.lock()
        guard running, suspendedReason == nil, let port = poolPort,
              var entry = entries[lane], !entry.probing else {
            lock.unlock()
            return
        }
        entry.probing = true
        entries[lane] = entry
        let credentials = entry.credentials
        let generation = entry.generation
        let target = LatencyProbe.target(at: round)
        let timeout = warmup ? configuration.warmupTimeout : configuration.probeTimeout
        lock.unlock()

        Task.detached { [weak self] in
            let seconds = await LatencyProbe.sample(socksPort: port, target: target,
                                                    credentials: credentials, timeout: .seconds(Int(timeout)))
            self?.poolQueue.async {
                self?.finishProbe(lane: lane, generation: generation, seconds: seconds, warmup: warmup)
            }
        }
    }

    private func finishProbe(lane: Int, generation: UInt32, seconds: TimeInterval?, warmup: Bool) {
        var retryDelay: TimeInterval?
        var suspendReason: String?
        lock.lock()
        if var entry = entries[lane], entry.generation == generation {
            entry.probing = false
            if let seconds {
                entry.state = .ready
                entry.lastActiveSampleAt = .now
                entry.consecutiveFailures = 0
                entry.warmupAttempts = 0
                if !warmup {
                    entry.probeRing.insert(seconds, at: 0)
                    if entry.probeRing.count > configuration.scoreWindow { entry.probeRing.removeLast() }
                }
            } else if warmup {
                entry.warmupAttempts += 1
                if entry.warmupAttempts <= 3 {
                    retryDelay = pow(2.0, Double(entry.warmupAttempts))
                } else if entries.values.allSatisfy({ $0.state != .ready }) {
                    // Nothing warmed at all: the listener or the auth method is not what we think.
                    suspendReason = "no circuit answered on the lane port"
                }
            } else {
                entry.consecutiveFailures += 1
            }
            entries[lane] = entry
        }
        lock.unlock()
        if let suspendReason { suspend(reason: suspendReason) }
        if let retryDelay { scheduleWarmup(lane: lane, after: retryDelay) }
    }

    private func evaluate() {
        lock.lock()
        guard running, suspendedReason == nil else {
            lock.unlock()
            return
        }
        let rows = _rows()
        var deadlines: [Int: Date] = [:]
        for (key, entry) in entries { deadlines[key] = entry.deadline }
        let candidate = LanePolicy.retirementCandidate(rows: rows, deadlines: deadlines, now: .now,
                                                       lastRetirement: lastRetirement,
                                                       slowFrozenUntil: slowFrozenUntil,
                                                       limits: configuration.limits)
        let frozenBefore = slowFrozenUntil
        var retired: (lane: Int, reason: LaneRetireReason)?
        if let candidate {
            _retire(lane: candidate.lane, reason: candidate.reason)
            retired = candidate
        }
        let froze = slowFrozenUntil != frozenBefore
        lock.unlock()
        if froze {
            emit(.veil(.warn, "Replacing circuits is not helping; the network is slow, not the relays"))
        }
        if let retired {
            emit(.veil(.debug, "Lane \(retired.lane) replaced (\(retired.reason.rawValue))"))
            scheduleWarmup(lane: retired.lane, after: 0)
        }
    }

    private func emit(_ entry: LogEntry) {
        onLog?(entry)
    }
}
