import Foundation

/// Where YouTube leaves Tor, and how wide the path is. Video through Tor is bounded by one
/// circuit's share of one guard and one exit, and a random exit is a random share. This measures
/// the widest exits the consensus knows — by pulling bytes through each, not by trusting its
/// advertised weight — and sends every YouTube host through the fastest: the page and the video
/// CDN alike, because YouTube binds its media URLs to the address that fetched the page, while
/// everything else keeps Tor's own exit choice. `MapAddress … .exit` is the mechanism: per
/// destination, no global `ExitNodes`. The 8K mode adds the other end of the path: the entry
/// guard, restricted to the widest guards, for every site — which is why it is a choice.
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

    /// Starts (or restarts) the selection after `delay`, then keeps the chosen exit verified.
    func startVideoExitSelection(after delay: Duration) {
        videoExitTask?.cancel()
        guard videoExitWanted else { return }
        videoExitTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            await pinVideoGuardIfWanted()
            await selectVideoExit()
            while !Task.isCancelled, videoExit.isActive {
                do { try await Task.sleep(for: .seconds(900)) } catch { return }
                guard videoExitWanted, let current = videoExit.relay else { return }
                if await verifyVideoExit(current.fingerprint) == nil {
                    append(.veil(.notice, "Video exit \(current.nickname) stopped answering; choosing another"))
                    await selectVideoExit()
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
    /// bridge the bridge is the first hop, and `EntryNodes` means nothing.
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
        var chosen: [(relay: ExitRelay, country: String?)] = []
        for relay in ExitCatalog.rankGuards(relays, count: 10) where chosen.count < 3 {
            guard !Task.isCancelled else { return }
            let country = await engine.relayCountry(relay.fingerprint)
            if let country, excluded.contains(country) { continue }
            chosen.append((relay, country))
        }
        guard !chosen.isEmpty else { return }
        if let current = videoGuard, chosen.contains(where: { $0.relay.fingerprint == current.fingerprint }) {
            return // already on one of them
        }
        do {
            try await engine.setEntryGuards(chosen.map(\.relay.fingerprint))
        } catch {
            append(.veil(.warn, "8K mode: tor refused the guard restriction: \(error.localizedDescription)"))
            return
        }
        // Proof before trust: one fresh circuit, and its first hop must be one of them. A guard
        // this network cannot reach would otherwise leave Tor with no usable guard at all.
        if let id = try? await engine.launchCircuit(),
           let info = await engine.awaitCircuit(id, timeout: .seconds(25)), info.status == .built,
           let first = info.path.first,
           let match = chosen.first(where: { $0.relay.fingerprint == Self.normalized(first.fingerprint) }) {
            videoGuard = RelayCandidate(fingerprint: match.relay.fingerprint, nickname: match.relay.nickname,
                                        countryCode: match.country,
                                        buildMilliseconds: Int(((info.buildTime ?? 0) * 1000).rounded()),
                                        bandwidth: match.relay.bandwidth)
            // Lane circuits were built through the old guard; new keys move the next
            // connections onto the new one while open streams finish.
            httpBridge?.lanePool.retireAll(reason: .routeChanged)
            append(.veil(.notice, "8K mode: entry guard \(match.relay.nickname) (\(match.relay.bandwidth / 1000) MB/s consensus weight); circuit built in \(Int(((info.buildTime ?? 0) * 1000).rounded())) ms"))
        } else {
            await engine.clearEntryGuards()
            videoGuard = nil
            append(.veil(.warn, "8K mode: no circuit came up through the widest guards within 25 s; the entry guard is back to Tor's own choice"))
        }
    }

    private func unpinVideoGuard() async {
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

    // MARK: Exit

    private func selectVideoExit() async {
        guard videoExitWanted, let ports else { return }
        let previous = videoExit
        videoExit = .choosing
        let relays = await engine.consensusExits()
        guard !Task.isCancelled else { return }
        guard !relays.isEmpty else {
            videoExit = .failed("Tor gave no consensus")
            append(.veil(.debug, "Video exit: tor answered ns/all with nothing"))
            return
        }
        let route = settings.route
        let ranked = ExitCatalog.rank(relays, count: 12, excluding: Set(route.avoidedRelays))
        let excludedCountries = Set(route.excludedCountries.map { $0.lowercased() })
        let wanted = route.exitCountry?.lowercased()
        let capped = firstHopCapped

        // Country rules are the user's, so they hold here too: the widest exit in the chosen
        // country, never one in an excluded country.
        var candidates: [(relay: ExitRelay, country: String?)] = []
        for relay in ranked where candidates.count < (capped ? 2 : 3) {
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

        // The mapping YouTube already has stays in place while candidates are measured, so a
        // video playing meanwhile is not moved back and forth.
        let current = videoExit.relay.map {
            ExitCatalog.mapAddressPairs(exit: $0.fingerprint, domains: RoutingPolicy.youtubeDomains)
        } ?? []

        if capped {
            // Nothing to measure behind a bridge: the first candidate that carries a stream wins.
            for candidate in candidates {
                guard !Task.isCancelled, videoExitWanted else { return }
                guard await map(youtubeTo: candidate.relay.fingerprint) else { return }
                if let seconds = await verifyVideoExit(candidate.relay.fingerprint) {
                    adopt(candidate, megabits: nil, connectMillis: Int((seconds * 1000).rounded()))
                    return
                }
                append(.veil(.debug, "Video exit: \(candidate.relay.nickname) did not carry a stream to YouTube; trying the next"))
            }
            await engine.clearAddressMappings()
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
        let bar = (baseline ?? 0) * 0.8
        if let best, best.megabits >= bar {
            guard await map(youtubeTo: best.candidate.relay.fingerprint) else { return }
            videoPathMegabits = best.megabits
            videoPathMeasuredAt = .now
            adopt(best.candidate, megabits: best.megabits, connectMillis: 0)
        } else if let baseline {
            await restoreMapping(current)
            videoExit = current.isEmpty ? .unpinned(megabits: baseline) : previous
            videoPathMegabits = baseline
            videoPathMeasuredAt = .now
            append(.veil(.notice, "Video exit: Tor's own exit (\(Self.megabits(baseline)) Mbit/s) was as fast as the widest; nothing pinned"))
        } else {
            await engine.clearAddressMappings()
            videoExit = .failed("no candidate carried a stream")
            append(.veil(.notice, "Video exit: no wide exit could be verified; YouTube uses Tor's usual exits"))
        }
    }

    private func adopt(_ candidate: (relay: ExitRelay, country: String?), megabits: Double?, connectMillis: Int) {
        let chosen = RelayCandidate(fingerprint: candidate.relay.fingerprint, nickname: candidate.relay.nickname,
                                    countryCode: candidate.country, buildMilliseconds: connectMillis,
                                    bandwidth: candidate.relay.bandwidth)
        videoExit = .active(chosen, megabits: megabits)
        // Lanes already carrying YouTube were built to other exits; new keys send the next
        // connection through the chosen one while open streams finish.
        httpBridge?.lanePool.retireAll(reason: .routeChanged)
        let rate = megabits.map { " · \(Self.megabits($0)) Mbit/s, enough for \(ThroughputProbe.quality(forMegabits: $0))" } ?? ""
        append(.veil(.notice, "Video exit: \(chosen.nickname) \(chosen.flag) — \(candidate.relay.bandwidth / 1000) MB/s consensus weight\(rate)"))
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

    private func restoreMapping(_ pairs: [(key: String, value: String?)]) async {
        if pairs.isEmpty {
            await engine.clearAddressMappings()
        } else {
            try? await engine.setAddressMappings(pairs)
        }
    }

    private func measurePath(socksPort: UInt16) async -> Double? {
        for target in ThroughputProbe.targets {
            if let sample = await ThroughputProbe.measure(socksPort: socksPort, target: target) {
                return sample.megabitsPerSecond
            }
        }
        return nil
    }

    /// The button: the rate YouTube gets right now, through the pinned exit if there is one.
    func measureVideoPath() {
        guard connection == .connected, let ports, !videoPathMeasuring, videoExit != .choosing else { return }
        videoPathMeasuring = true
        Task { [weak self] in
            guard let self else { return }
            defer { videoPathMeasuring = false }
            let current = videoExit.relay.map {
                ExitCatalog.mapAddressPairs(exit: $0.fingerprint, domains: RoutingPolicy.youtubeDomains)
            } ?? []
            if let relay = videoExit.relay {
                try? await engine.setAddressMappings(
                    current + ExitCatalog.mapAddressPairs(exit: relay.fingerprint, domains: ThroughputProbe.measurementHosts))
            }
            let megabits = await measurePath(socksPort: ports.socks)
            await restoreMapping(current)
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
