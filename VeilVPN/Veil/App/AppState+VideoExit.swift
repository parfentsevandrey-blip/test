import Foundation

/// Where YouTube leaves Tor, and how wide the path is. Video through Tor is bounded by one
/// circuit's share of one guard, one middle and one exit, and a random relay is a random share.
/// This measures the widest exits the consensus knows — by pulling bytes through each, not by
/// trusting the advertised weight — and sends every YouTube host through the fastest: the page
/// and the video CDN alike, because YouTube binds its media URLs to the address that fetched the
/// page, while everything else keeps Tor's own exit choice. `MapAddress … .exit` is the
/// mechanism: per destination, no global `ExitNodes`. The 8K mode adds the other end of the
/// path — the entry guard, restricted to the widest guards, for every site, which is why it is a
/// choice — and under Turbo measures those too. The path that worked is remembered, so the next
/// connection starts on it and the race is paid once.
extension AppState {
    enum VideoExitState: Equatable {
        case off
        case choosing
        /// Pinned, with the rate measured through it where the transport allowed a measurement.
        case active(RelayCandidate, megabits: Double?)
        /// Measured, and Tor's own exits were as fast: nothing pinned, the number stands.
        case unpinned(megabits: Double)
        case failed(String)

        var isActive: Bool {
            if case .active = self { return true }
            return false
        }

        var relay: RelayCandidate? {
            if case .active(let relay, _) = self { return relay }
            return nil
        }
    }

    static let videoExitProbe = LatencyProbe.Target(name: "www.youtube.com", host: "www.youtube.com", port: 443)

    var videoExitWanted: Bool {
        settings.videoExitEnabled && settings.youtubeMode == .tor && !settings.latencyTuning
            && !engine.isSimulated && connection == .connected
    }

    /// Behind Snowflake or meek the first hop caps everything; measuring exits through it is moot.
    private var firstHopCapped: Bool {
        let transport = activeTransport ?? settings.transport
        return transport == .snowflake || transport == .meek || transport == .auto
    }

    /// Under Turbo a measurement runs longer and further — five seconds and up to 25 MB — so a
    /// wide path is timed past its slow start; otherwise four seconds and 4 MB.
    var measurementWindow: (duration: Duration, byteCap: Int) {
        settings.videoTurbo ? (.seconds(5), 25_000_000) : (.seconds(4), 4_000_000)
    }

    /// Starts (or restarts) the selection after `delay`, then keeps the chosen exit verified —
    /// and under Turbo re-measured while nothing plays, with a path that has fallen to half of
    /// what it carried raced again: the lanes first, then the exits.
    func startVideoExitSelection(after delay: Duration) {
        videoExitTask?.cancel()
        guard videoExitWanted else { return }
        videoExitTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            await pinVideoGuardIfWanted()
            await selectVideoExit()
            await raceVideoLanes()
            var lowStreak = 0
            while !Task.isCancelled, videoExit.isActive {
                do { try await Task.sleep(for: .seconds(settings.videoTurbo ? 300 : 900)) } catch { return }
                guard videoExitWanted, let current = videoExit.relay else { return }
                if settings.videoTurbo, (httpBridge?.youtubeSessionsInFlight ?? 0) == 0,
                   case .active(_, let adopted?) = videoExit {
                    guard let now = await measureCurrentPath() else { continue }
                    lowStreak = now < adopted * 0.5 ? lowStreak + 1 : 0
                    guard lowStreak >= 2 else { continue }
                    lowStreak = 0
                    append(.veil(.notice, "Video path fell to \(Self.megabits(now)) Mbit/s from \(Self.megabits(adopted)); racing the lanes again"))
                    await raceVideoLanes()
                    if (videoLane?.megabits ?? now) < adopted * 0.5 {
                        append(.veil(.notice, "Video exit \(current.nickname) no longer carries what it did; choosing another"))
                        await selectVideoExit(ignoringMemory: true)
                        await raceVideoLanes()
                    }
                } else if await verifyVideoExit(current.fingerprint) == nil {
                    append(.veil(.notice, "Video exit \(current.nickname) stopped answering; choosing another"))
                    await selectVideoExit(ignoringMemory: true)
                    await raceVideoLanes()
                }
            }
        }
    }

    /// Back to Tor's own exits for YouTube and its own guard. Safe to call when nothing was set.
    func clearVideoExit() async {
        videoExitTask?.cancel()
        videoExitTask = nil
        let wasActive = videoExit.isActive
        videoExit = .off
        videoLane = nil
        httpBridge?.videoFanExit = ""
        if engine.isLive || engine.isWarm {
            await engine.clearAddressMappings()
        }
        if wasActive { append(.veil(.info, "Video exit released; YouTube uses Tor's usual exits")) }
        await unpinVideoGuard()
    }

    func setVideoExitEnabled(_ enabled: Bool) {
        guard settings.videoExitEnabled != enabled else { return }
        settings.videoExitEnabled = enabled
        if enabled {
            startVideoExitSelection(after: .seconds(1))
        } else {
            Task { [weak self] in await self?.clearVideoExit() }
        }
    }

    func setVideoGuardPinning(_ enabled: Bool) {
        guard settings.videoGuardPinning != enabled else { return }
        settings.videoGuardPinning = enabled
        guard connection == .connected else { return }
        if enabled {
            // The exits are measured through the guard they will actually use, so the guard
            // comes first and the selection runs again behind it.
            startVideoExitSelection(after: .seconds(1))
        } else {
            Task { [weak self] in await self?.unpinVideoGuard() }
        }
    }

    // MARK: Guard (8K mode)

    /// Restricts Tor's entry guard to the widest guards in the consensus and proves a circuit
    /// through one of them before keeping the restriction. Direct connections only: behind a
    /// bridge the bridge is the first hop, and `EntryNodes` means nothing. Under Turbo the guards
    /// are measured, one at a time through one wide exit, and the winner is pinned alone; the
    /// guard that carried the video last time skips the race.
    private func pinVideoGuardIfWanted() async {
        guard settings.videoGuardPinning, connection == .connected, !engine.isSimulated else { return }
        let transport = activeTransport ?? settings.transport
        guard transport == .direct else {
            append(.veil(.notice, "8K mode: the first hop is \(Self.name(of: transport)), which caps the rate on its own; the entry guard is left alone"))
            return
        }
        let relays = await engine.consensusExits()
        guard !Task.isCancelled, !relays.isEmpty else { return }
        let excluded = Set(settings.route.excludedCountries.map { $0.lowercased() })
        // Two legs and congestion control are what a video path needs from its guard as well;
        // only when no such guard is listed does the plain ranking stand in.
        var ranked = ExitCatalog.rankGuards(relays, count: 10, modernOnly: true)
        if ranked.isEmpty { ranked = ExitCatalog.rankGuards(relays, count: 10) }
        var chosen: [(relay: ExitRelay, country: String?)] = []
        for relay in ranked where chosen.count < 3 {
            guard !Task.isCancelled else { return }
            let country = await engine.relayCountry(relay.fingerprint)
            if let country, excluded.contains(country) { continue }
            chosen.append((relay, country))
        }
        guard !chosen.isEmpty else { return }
        if let current = videoGuard, chosen.contains(where: { $0.relay.fingerprint == current.fingerprint }) {
            return // already on one of them
        }
        if settings.videoTurbo {
            let memory = settings.videoPathMemory
            let remembered = memory?.isFresh(transport: transport) == true ? memory?.guardFingerprint : nil
            if let remembered, let index = chosen.firstIndex(where: { $0.relay.fingerprint == remembered }) {
                if await proveGuard([chosen[index]]) { return }
                settings.videoPathMemory?.guardFingerprint = nil
            } else if let winner = await raceGuards(chosen, relays: relays) {
                if await proveGuard([winner]) { return }
            }
            guard !Task.isCancelled else { return }
            // Alone and not proven: the three together, as outside Turbo.
        }
        if await proveGuard(chosen) { return }
        await engine.clearEntryGuards()
        videoGuard = nil
        append(.veil(.warn, "8K mode: no circuit came up through the widest guards within 25 s; the entry guard is back to Tor's own choice"))
    }

    /// `EntryNodes` set to `pins`, then one fresh circuit whose first hop must be one of them: a
    /// guard this network cannot reach would otherwise leave Tor with no usable guard at all.
    private func proveGuard(_ pins: [(relay: ExitRelay, country: String?)]) async -> Bool {
        do {
            try await engine.setEntryGuards(pins.map(\.relay.fingerprint))
        } catch {
            append(.veil(.warn, "8K mode: tor refused the guard restriction: \(error.localizedDescription)"))
            return false
        }
        guard let id = try? await engine.launchCircuit(),
              let info = await engine.awaitCircuit(id, timeout: .seconds(25)), info.status == .built,
              let first = info.path.first,
              let match = pins.first(where: { $0.relay.fingerprint == Self.normalized(first.fingerprint) }) else {
            return false
        }
        let millis = Int(((info.buildTime ?? 0) * 1000).rounded())
        videoGuard = RelayCandidate(fingerprint: match.relay.fingerprint, nickname: match.relay.nickname,
                                    countryCode: match.country, buildMilliseconds: millis,
                                    bandwidth: match.relay.bandwidth)
        // Lane circuits were built through the old guard; new keys move the next connections
        // onto the new one while open streams finish.
        httpBridge?.lanePool.retireAll(reason: .routeChanged)
        append(.veil(.notice, "8K mode: entry guard \(match.relay.nickname) (\(match.relay.bandwidth / 1000) MB/s consensus weight\(pins.count == 1 ? ", pinned alone" : "")); circuit built in \(millis) ms"))
        return true
    }

    /// Each guard alone for a few seconds: `EntryNodes` set to it, one stream on credentials of
    /// its own so tor builds a fresh circuit through it, timed to one wide exit that is the same
    /// for every guard, and proof from the circuit list that the stream went that way.
    private func raceGuards(_ candidates: [(relay: ExitRelay, country: String?)],
                            relays: [ExitRelay]) async -> (relay: ExitRelay, country: String?)? {
        guard let ports else { return nil }
        let avoided = Set(settings.route.avoidedRelays).union(refusedVideoExits)
        let remembered = settings.videoPathMemory.flatMap { memory in
            relays.first { $0.fingerprint == memory.exitFingerprint && $0.isUsableExit && $0.allows80 }
        }
        guard let farEnd = remembered?.fingerprint
                ?? ExitCatalog.rank(relays, count: 1, excluding: avoided, modernOnly: true).first?.fingerprint else {
            return nil
        }
        let current = videoExit.relay.map {
            ExitCatalog.mapAddressPairs(exit: $0.fingerprint, domains: RoutingPolicy.youtubeDomains)
        } ?? []
        do {
            try await engine.setAddressMappings(current + ExitCatalog.mapAddressPairs(exit: farEnd, domains: ThroughputProbe.measurementHosts))
        } catch {
            return nil
        }
        var results: [(candidate: (relay: ExitRelay, country: String?), megabits: Double)] = []
        for (index, candidate) in candidates.enumerated() {
            guard !Task.isCancelled else { break }
            do { try await engine.setEntryGuards([candidate.relay.fingerprint]) } catch { continue }
            let credentials = SOCKS5.Credentials(username: "veil-guard\(index)", password: Self.randomPassword())
            guard let megabits = await measurePath(socksPort: ports.socks, credentials: credentials) else {
                append(.veil(.debug, "8K mode: nothing came through guard \(candidate.relay.nickname)"))
                continue
            }
            let paths = await engine.builtCircuitPaths()
            guard paths.contains(where: { $0.first == candidate.relay.fingerprint && $0.last == farEnd }) else {
                append(.veil(.debug, "8K mode: the stream did not go through guard \(candidate.relay.nickname)"))
                continue
            }
            append(.veil(.info, "8K mode: guard \(candidate.relay.nickname) carries \(Self.megabits(megabits)) Mbit/s"))
            results.append((candidate, megabits))
        }
        await restoreMapping(current)
        return results.max { $0.megabits < $1.megabits }?.candidate
    }

    func unpinVideoGuard() async {
        guard videoGuard != nil else { return }
        if engine.isLive || engine.isWarm {
            await engine.clearEntryGuards()
        }
        videoGuard = nil
        httpBridge?.lanePool.retireAll(reason: .routeChanged)
        append(.veil(.info, "Entry guard back to Tor's own choice"))
    }

    private static func normalized(_ fingerprint: String) -> String {
        var value = fingerprint.uppercased()
        if value.hasPrefix("$") { value.removeFirst() }
        return value
    }

    static func randomPassword() -> String {
        String(format: "%016lx%016lx", UInt64.random(in: 0...UInt64.max), UInt64.random(in: 0...UInt64.max))
    }

    // MARK: Exit

    private enum RememberedOutcome { case adopted, held, gone }

    /// Exits the video CDN turned away within the week: never candidates, never remembered.
    var refusedVideoExits: Set<String> {
        let now = Date.now
        return Set(settings.videoExitsRefused.filter { now.timeIntervalSince($0.value) < VideoPathMemory.lifetime }.keys)
    }

    /// `forcePin` adopts the widest candidate that carries a stream whether or not it beats
    /// Tor's own exit: for when Tor's own exit is the one the video CDN turns away.
    func selectVideoExit(ignoringMemory: Bool = false, forcePin: Bool = false) async {
        guard videoExitWanted, let ports else { return }
        let previous = videoExit
        videoExit = .choosing
        videoPathBusy = true
        defer { videoPathBusy = false }
        let transport = activeTransport ?? settings.transport
        let capped = firstHopCapped
        let route = settings.route
        let avoided = Set(route.avoidedRelays).union(refusedVideoExits)

        // The mapping YouTube already has stays in place while candidates are measured, so a
        // video playing meanwhile is not moved back and forth.
        var current = previous.relay.map {
            ExitCatalog.mapAddressPairs(exit: $0.fingerprint, domains: RoutingPolicy.youtubeDomains)
        } ?? []
        var heldFromMemory = false

        // The exit that carried video last time goes first: pinned now, so the browser is on a
        // wide exit within seconds of connecting, and kept when it still carries most of what it
        // did. When it does not, it stays mapped while the race runs, and races as a candidate.
        if !ignoringMemory, previous.relay == nil, let memory = settings.videoPathMemory,
           memory.isFresh(transport: transport), !avoided.contains(memory.exitFingerprint),
           countryAllowed(memory.exitCountry) {
            switch await adoptRemembered(memory, capped: capped, ports: ports) {
            case .adopted:
                return
            case .held:
                current = ExitCatalog.mapAddressPairs(exit: memory.exitFingerprint, domains: RoutingPolicy.youtubeDomains)
                heldFromMemory = true
            case .gone:
                settings.videoPathMemory = nil
            }
            guard !Task.isCancelled, videoExitWanted else { return }
        }

        let relays = await engine.consensusExits()
        guard !Task.isCancelled else { return }
        guard !relays.isEmpty else {
            videoExit = .failed("Tor gave no consensus")
            append(.veil(.debug, "Video exit: tor answered ns/all with nothing"))
            return
        }
        // Two legs and congestion control are what a video path needs from its exit; only when
        // no such exit is listed does the plain ranking stand in.
        var ranked = ExitCatalog.rank(relays, count: settings.videoTurbo ? 24 : 12, excluding: avoided, modernOnly: true)
        if ranked.isEmpty { ranked = ExitCatalog.rank(relays, count: 12, excluding: avoided) }
        if heldFromMemory, let memory = settings.videoPathMemory,
           let held = relays.first(where: { $0.fingerprint == memory.exitFingerprint }) {
            ranked.removeAll { $0.fingerprint == held.fingerprint }
            ranked.insert(held, at: 0)
        }
        let excludedCountries = Set(route.excludedCountries.map { $0.lowercased() })
        let wanted = route.exitCountry?.lowercased()
        let limit = capped ? 2 : (settings.videoTurbo ? 5 : 3)

        // Country rules are the user's, so they hold here too: the widest exit in the chosen
        // country, never one in an excluded country.
        var candidates: [(relay: ExitRelay, country: String?)] = []
        for relay in ranked where candidates.count < limit {
            guard !Task.isCancelled else { return }
            let country = await engine.relayCountry(relay.fingerprint)
            if let wanted, country != wanted { continue }
            if let country, excludedCountries.contains(country) { continue }
            candidates.append((relay, country))
        }
        guard !candidates.isEmpty else {
            videoExit = .failed("no exit matches the route")
            append(.veil(.notice, "Video exit: none of the widest exits fits the route's country rules; YouTube uses Tor's usual exits"))
            return
        }

        if capped {
            // Nothing to measure behind a bridge: the first candidate that carries a stream wins.
            for candidate in candidates {
                guard !Task.isCancelled, videoExitWanted else { return }
                guard await map(youtubeTo: candidate.relay.fingerprint) else { return }
                if let seconds = await verifyVideoExit(candidate.relay.fingerprint) {
                    adopt(Self.candidate(candidate, connectMillis: Int((seconds * 1000).rounded())), megabits: nil)
                    return
                }
                append(.veil(.debug, "Video exit: \(candidate.relay.nickname) did not carry a stream to YouTube; trying the next"))
            }
            await engine.clearAddressMappings()
            if heldFromMemory { settings.videoPathMemory = nil }
            videoExit = .failed("no candidate carried a stream")
            append(.veil(.notice, "Video exit: no wide exit could be verified; YouTube uses Tor's usual exits"))
            return
        }

        // Measured. Tor's own exit goes first, as the bar a candidate has to clear.
        await restoreMapping(current)
        let baseline = await measurePath(socksPort: ports.socks)
        if let baseline {
            append(.veil(.info, "Video path: Tor's own exit carries \(Self.megabits(baseline)) Mbit/s"))
        }
        var best: (candidate: (relay: ExitRelay, country: String?), megabits: Double)?
        for candidate in candidates {
            guard !Task.isCancelled, videoExitWanted else { return }
            let pairs = current + ExitCatalog.mapAddressPairs(exit: candidate.relay.fingerprint,
                                                              domains: ThroughputProbe.measurementHosts)
            do {
                try await engine.setAddressMappings(pairs)
            } catch {
                videoExit = .failed(error.localizedDescription)
                append(.veil(.warn, "Video exit: tor refused the address mapping: \(error.localizedDescription)"))
                return
            }
            guard let sample = await measurePath(socksPort: ports.socks) else {
                append(.veil(.debug, "Video exit: \(candidate.relay.nickname) carried nothing; trying the next"))
                continue
            }
            let exits = await engine.builtCircuitExits()
            guard exits.contains(candidate.relay.fingerprint) else {
                // The stream left elsewhere: this tor does not honour the mapping, and no
                // candidate will fare differently.
                append(.veil(.notice, "Video exit: the measurement did not leave through \(candidate.relay.nickname); this tor ignores the exit mapping"))
                break
            }
            append(.veil(.info, "Video exit: \(candidate.relay.nickname) carries \(Self.megabits(sample)) Mbit/s"))
            if sample > (best?.megabits ?? -1) { best = (candidate, sample) }
        }
        guard !Task.isCancelled, videoExitWanted else { return }
        let bar = forcePin ? 0 : (baseline ?? 0) * 0.8
        if let best, best.megabits >= bar {
            guard await map(youtubeTo: best.candidate.relay.fingerprint) else { return }
            videoPathMegabits = best.megabits
            videoPathMeasuredAt = .now
            adopt(Self.candidate(best.candidate, connectMillis: 0), megabits: best.megabits)
        } else if let baseline {
            // Tor's own exit was as fast as the widest: nothing pinned — and an exit held from
            // memory that fell under the bar is let go, with the memory.
            if heldFromMemory {
                await engine.clearAddressMappings()
                settings.videoPathMemory = nil
                videoExit = .unpinned(megabits: baseline)
            } else {
                await restoreMapping(current)
                videoExit = current.isEmpty ? .unpinned(megabits: baseline) : previous
            }
            videoPathMegabits = baseline
            videoPathMeasuredAt = .now
            append(.veil(.notice, "Video exit: Tor's own exit (\(Self.megabits(baseline)) Mbit/s) was as fast as the widest; nothing pinned"))
        } else {
            await engine.clearAddressMappings()
            if heldFromMemory { settings.videoPathMemory = nil }
            videoExit = .failed("no candidate carried a stream")
            append(.veil(.notice, "Video exit: no wide exit could be verified; YouTube uses Tor's usual exits"))
        }
    }

    /// The remembered exit: mapped at once, proven with one stream, and — where the transport
    /// allows a measurement — kept only if it still carries most of what it did.
    private func adoptRemembered(_ memory: VideoPathMemory, capped: Bool, ports: ActivePorts) async -> RememberedOutcome {
        guard await map(youtubeTo: memory.exitFingerprint) else { return .gone }
        guard let seconds = await verifyVideoExit(memory.exitFingerprint) else {
            append(.veil(.notice, "Video exit: \(memory.exitNickname), remembered, did not carry a stream; choosing afresh"))
            await engine.clearAddressMappings()
            return .gone
        }
        let chosen = RelayCandidate(fingerprint: memory.exitFingerprint, nickname: memory.exitNickname,
                                    countryCode: memory.exitCountry, buildMilliseconds: Int((seconds * 1000).rounded()),
                                    bandwidth: memory.exitBandwidth)
        if capped {
            adopt(chosen, megabits: nil)
            append(.veil(.info, "Video exit remembered: \(memory.exitNickname) carries a stream again"))
            return .adopted
        }
        let youtube = ExitCatalog.mapAddressPairs(exit: memory.exitFingerprint, domains: RoutingPolicy.youtubeDomains)
        try? await engine.setAddressMappings(youtube + ExitCatalog.mapAddressPairs(exit: memory.exitFingerprint,
                                                                                  domains: ThroughputProbe.measurementHosts))
        let measured = await measurePath(socksPort: ports.socks)
        await restoreMapping(youtube)
        guard !Task.isCancelled else { return .held }
        guard let measured, measured >= memory.megabits * VideoPathMemory.keepShare else {
            append(.veil(.notice, "Video exit: \(memory.exitNickname), remembered, carries \(measured.map { Self.megabits($0) } ?? "nothing") against \(Self.megabits(memory.megabits)) Mbit/s last time; racing again"))
            return .held
        }
        videoPathMegabits = measured
        videoPathMeasuredAt = .now
        adopt(chosen, megabits: measured)
        append(.veil(.info, "Video exit remembered: \(memory.exitNickname) carries \(Self.megabits(measured)) Mbit/s, as before"))
        return .adopted
    }

    private static func candidate(_ pair: (relay: ExitRelay, country: String?), connectMillis: Int) -> RelayCandidate {
        RelayCandidate(fingerprint: pair.relay.fingerprint, nickname: pair.relay.nickname, countryCode: pair.country,
                       buildMilliseconds: connectMillis, bandwidth: pair.relay.bandwidth)
    }

    private func adopt(_ chosen: RelayCandidate, megabits: Double?) {
        videoExit = .active(chosen, megabits: megabits)
        // Lanes already carrying YouTube were built to other exits; new keys send the next
        // connection through the chosen one while open streams finish.
        httpBridge?.lanePool.retireAll(reason: .routeChanged)
        settings.videoPathMemory = VideoPathMemory(exitFingerprint: chosen.fingerprint, exitNickname: chosen.nickname,
                                                   exitCountry: chosen.countryCode, exitBandwidth: chosen.bandwidth ?? 0,
                                                   guardFingerprint: videoGuard?.fingerprint, guardNickname: videoGuard?.nickname,
                                                   megabits: megabits ?? 0, measuredAt: .now,
                                                   transport: activeTransport ?? settings.transport)
        let rate = megabits.map { " · \(Self.megabits($0)) Mbit/s, enough for \(ThroughputProbe.quality(forMegabits: $0))" } ?? ""
        append(.veil(.notice, "Video exit: \(chosen.nickname) \(chosen.flag) — \((chosen.bandwidth ?? 0) / 1000) MB/s consensus weight\(rate)"))
        updateVideoFan()
    }

    private func countryAllowed(_ country: String?) -> Bool {
        let route = settings.route
        if let wanted = route.exitCountry?.lowercased(), country != wanted { return false }
        if let country, route.excludedCountries.map({ $0.lowercased() }).contains(country) { return false }
        return true
    }

    /// Maps every YouTube host to `fingerprint`; false when tor refused, with the state set.
    private func map(youtubeTo fingerprint: String) async -> Bool {
        do {
            try await engine.setAddressMappings(
                ExitCatalog.mapAddressPairs(exit: fingerprint, domains: RoutingPolicy.youtubeDomains))
            return true
        } catch {
            videoExit = .failed(error.localizedDescription)
            append(.veil(.warn, "Video exit: tor refused the address mapping: \(error.localizedDescription)"))
            return false
        }
    }

    func restoreMapping(_ pairs: [(key: String, value: String?)]) async {
        if pairs.isEmpty {
            await engine.clearAddressMappings()
        } else {
            try? await engine.setAddressMappings(pairs)
        }
    }

    /// Megabits a second through `socksPort`, on `credentials` when the stream must have a
    /// circuit of its own; nil when nothing came through either measurement host.
    func measurePath(socksPort: UInt16, credentials: SOCKS5.Credentials? = nil) async -> Double? {
        let window = measurementWindow
        for target in ThroughputProbe.targets {
            if let sample = await ThroughputProbe.measure(socksPort: socksPort, target: target, duration: window.duration,
                                                          byteCap: window.byteCap, credentials: credentials) {
                return sample.megabitsPerSecond
            }
        }
        return nil
    }

    /// The rate through the pinned exit right now, with the YouTube mapping left in place.
    private func measureCurrentPath() async -> Double? {
        guard let ports, !videoPathBusy else { return nil }
        videoPathBusy = true
        defer { videoPathBusy = false }
        let current = videoExit.relay.map {
            ExitCatalog.mapAddressPairs(exit: $0.fingerprint, domains: RoutingPolicy.youtubeDomains)
        } ?? []
        if let relay = videoExit.relay {
            try? await engine.setAddressMappings(
                current + ExitCatalog.mapAddressPairs(exit: relay.fingerprint, domains: ThroughputProbe.measurementHosts))
        }
        let megabits = await measurePath(socksPort: ports.socks)
        await restoreMapping(current)
        return megabits
    }

    /// The button: the rate YouTube gets right now, through the pinned exit if there is one.
    func measureVideoPath() {
        guard connection == .connected, ports != nil, !videoPathMeasuring, !videoPathBusy, videoExit != .choosing else { return }
        videoPathMeasuring = true
        Task { [weak self] in
            guard let self else { return }
            defer { videoPathMeasuring = false }
            let megabits = await measureCurrentPath()
            videoPathMegabits = megabits
            videoPathMeasuredAt = .now
            if let megabits {
                append(.veil(.notice, "Video path: \(Self.megabits(megabits)) Mbit/s, enough for \(ThroughputProbe.quality(forMegabits: megabits))"))
            } else {
                append(.veil(.warn, "Video path: the measurement stream did not open"))
            }
        }
    }

    static func megabits(_ value: Double) -> String {
        String(format: value >= 10 ? "%.0f" : "%.1f", value)
    }

    /// One real stream to YouTube through the mapping, then proof that it left through `fingerprint`
    /// — a tor that ignores `.exit` would still open the stream, just elsewhere.
    private func verifyVideoExit(_ fingerprint: String) async -> TimeInterval? {
        guard let ports else { return nil }
        guard let seconds = await LatencyProbe.sample(socksPort: ports.socks, target: Self.videoExitProbe,
                                                      timeout: .seconds(15)) else { return nil }
        let exits = await engine.builtCircuitExits()
        guard exits.contains(fingerprint) else {
            append(.veil(.debug, "Video exit: the stream to YouTube did not leave through \(fingerprint.prefix(8))"))
            return nil
        }
        return seconds
    }
}
