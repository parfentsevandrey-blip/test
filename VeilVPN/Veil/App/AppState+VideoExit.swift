import Foundation

/// Where YouTube leaves Tor. Video through Tor is bounded by one circuit's share of one exit,
/// and a random exit is a random share. This picks one of the widest exits the consensus knows
/// and sends every YouTube host through it — the page and the video CDN alike, because YouTube
/// binds its media URLs to the address that fetched the page — while everything else keeps
/// Tor's own exit choice. `MapAddress … .exit` is the mechanism: per destination, no global
/// `ExitNodes`, no effect on any other site.
extension AppState {
    enum VideoExitState: Equatable {
        case off
        case choosing
        case active(RelayCandidate)
        case failed(String)

        var isActive: Bool {
            if case .active = self { return true }
            return false
        }
    }

    static let videoExitProbe = LatencyProbe.Target(name: "www.youtube.com", host: "www.youtube.com", port: 443)

    var videoExitWanted: Bool {
        settings.videoExitEnabled && settings.youtubeMode == .tor && !settings.latencyTuning
            && !engine.isSimulated && connection == .connected
    }

    /// Starts (or restarts) the selection after `delay`, then keeps the chosen exit verified.
    func startVideoExitSelection(after delay: Duration) {
        videoExitTask?.cancel()
        guard videoExitWanted else { return }
        videoExitTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            await selectVideoExit()
            while !Task.isCancelled, videoExit.isActive {
                do { try await Task.sleep(for: .seconds(900)) } catch { return }
                guard videoExitWanted, case .active(let current) = videoExit else { return }
                if await verifyVideoExit(current.fingerprint) == nil {
                    append(.veil(.notice, "Video exit \(current.nickname) stopped answering; choosing another"))
                    await selectVideoExit()
                }
            }
        }
    }

    /// Back to Tor's own exits for YouTube. Safe to call when nothing was mapped.
    func clearVideoExit() async {
        videoExitTask?.cancel()
        videoExitTask = nil
        let wasActive = videoExit.isActive
        videoExit = .off
        if engine.isLive || engine.isWarm {
            await engine.clearAddressMappings()
        }
        if wasActive { append(.veil(.info, "Video exit released; YouTube uses Tor's usual exits")) }
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

    private func selectVideoExit() async {
        guard videoExitWanted, let ports else { return }
        videoExit = .choosing
        let relays = await engine.consensusExits()
        guard !Task.isCancelled else { return }
        guard !relays.isEmpty else {
            videoExit = .failed("Tor gave no consensus")
            append(.veil(.debug, "Video exit: tor answered ns/all with nothing"))
            return
        }
        let route = settings.route
        let avoided = Set(route.avoidedRelays)
        let ranked = ExitCatalog.rank(relays, count: 12, excluding: avoided)
        let excludedCountries = Set(route.excludedCountries.map { $0.lowercased() })
        let wanted = route.exitCountry?.lowercased()

        // Country rules are the user's, so they hold here too: the widest exit in the chosen
        // country, never one in an excluded country.
        var candidates: [(relay: ExitRelay, country: String?)] = []
        for relay in ranked where candidates.count < 4 {
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

        for candidate in candidates {
            guard !Task.isCancelled, videoExitWanted else { return }
            let relay = candidate.relay
            do {
                try await engine.setAddressMappings(
                    ExitCatalog.mapAddressPairs(exit: relay.fingerprint, domains: RoutingPolicy.youtubeDomains))
            } catch {
                videoExit = .failed(error.localizedDescription)
                append(.veil(.warn, "Video exit: tor refused the address mapping: \(error.localizedDescription)"))
                return
            }
            if let seconds = await verifyVideoExit(relay.fingerprint) {
                let chosen = RelayCandidate(fingerprint: relay.fingerprint, nickname: relay.nickname,
                                            countryCode: candidate.country,
                                            buildMilliseconds: Int((seconds * 1000).rounded()),
                                            bandwidth: relay.bandwidth)
                videoExit = .active(chosen)
                // Lanes already carrying YouTube were built to other exits; new keys send the
                // next connection through the chosen one while open streams finish.
                httpBridge?.lanePool.retireAll(reason: .routeChanged)
                append(.veil(.notice, "Video exit: \(relay.nickname) \(chosen.flag) — \(relay.bandwidth / 1000) MB/s consensus weight, first stream in \(Int(seconds * 1000)) ms"))
                return
            }
            append(.veil(.debug, "Video exit: \(relay.nickname) did not carry a stream to YouTube; trying the next"))
        }
        // Nothing verified: leave YouTube on Tor's own exits rather than on a mapping that fails.
        await engine.clearAddressMappings()
        videoExit = .failed("no candidate carried a stream")
        append(.veil(.notice, "Video exit: no wide exit could be verified; YouTube uses Tor's usual exits"))
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
