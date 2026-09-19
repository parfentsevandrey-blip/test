import Foundation

/// The video lane: which of the pool's circuits carries YouTube. Every lane leaves through the
/// pinned exit, so between them the race is about the middle relay — the one hop nothing else
/// chooses — and the circuit a lane's measurement builds is the very circuit YouTube's streams on
/// that lane then ride: same isolation key, same exit. Bound for the lane's whole generation,
/// re-raced when that lane retires and, while nothing plays, every fifteen minutes. The same loop
/// watches the guard Turbo pins alone: one that stops building circuits is let go before the
/// browser notices.
extension AppState {
    struct VideoLaneState: Equatable, Sendable {
        let lane: Int
        let generation: UInt32
        let megabits: Double
        /// Every lane's rate in the race, by lane.
        let field: [Int: Double]
        let racedAt: Date
    }

    static let videoSite = "youtube.com"

    var videoLaneRacingWanted: Bool {
        settings.videoTurbo && connection == .connected && !engine.isSimulated && videoExit.isActive
            && !settings.isolatePerSite && httpBridge?.poolPort != nil
    }

    func startVideoLaneSupervision() {
        videoLaneTask?.cancel()
        videoLaneTask = Task { [weak self] in
            var guardMissingSince: Date?
            while !Task.isCancelled {
                guard let self else { return }
                if videoLaneRacingWanted, let bridge = httpBridge, !videoPathBusy {
                    let bound = bridge.lanePool.binding(for: Self.videoSite)
                    let quiet = bridge.youtubeSessionsInFlight == 0
                    let stale = videoLane.map { Date.now.timeIntervalSince($0.racedAt) > 900 } ?? true
                    // A binding that is gone is replaced at once — the next connection needs a
                    // lane — even while a video plays; a periodic re-race waits for quiet.
                    if bound == nil || bound?.generation != videoLane?.generation || (stale && quiet) {
                        await raceVideoLanes()
                    }
                } else if !videoLaneRacingWanted, videoLane != nil {
                    videoLane = nil
                }
                // The CDN's verdict beats every measurement: an exit it turns away carries no
                // video however wide it is. Dropped for a week, and another chosen at once.
                if videoExitWanted, !videoPathBusy, let bridge = httpBridge, bridge.videoCDNRefusing {
                    bridge.resetVideoCDNSignal()
                    let now = Date.now
                    settings.videoExitsRefused = settings.videoExitsRefused.filter { now.timeIntervalSince($0.value) < VideoPathMemory.lifetime }
                    if let relay = videoExit.relay {
                        settings.videoExitsRefused[relay.fingerprint] = now
                        if settings.videoPathMemory?.exitFingerprint == relay.fingerprint { settings.videoPathMemory = nil }
                        append(.veil(.warn, "The video CDN is turning exit \(relay.nickname) away — connections close with nothing in them; choosing another exit"))
                    } else {
                        append(.veil(.warn, "The video CDN is turning Tor's own exit away — connections close with nothing in them; pinning a wide exit"))
                    }
                    await selectVideoExit(ignoringMemory: true, forcePin: true)
                    await raceVideoLanes()
                }
                if settings.videoTurbo, connection == .connected, let pinned = videoGuard {
                    let paths = await engine.builtCircuitPaths()
                    if paths.contains(where: { $0.first == pinned.fingerprint }) {
                        guardMissingSince = nil
                    } else if let since = guardMissingSince {
                        if Date.now.timeIntervalSince(since) >= 30 {
                            guardMissingSince = nil
                            append(.veil(.warn, "8K mode: no circuit through \(pinned.nickname) for half a minute; back to Tor's own guards"))
                            settings.videoPathMemory?.guardFingerprint = nil
                            await unpinVideoGuard()
                        }
                    } else {
                        guardMissingSince = .now
                    }
                } else {
                    guardMissingSince = nil
                }
                do { try await Task.sleep(for: .seconds(15)) } catch { return }
            }
        }
    }

    func stopVideoLaneSupervision() {
        videoLaneTask?.cancel()
        videoLaneTask = nil
        videoLane = nil
    }

    /// One stream on each ready lane through the pinned exit, three seconds each, and YouTube
    /// bound to the widest.
    func raceVideoLanes() async {
        guard videoLaneRacingWanted, !videoPathBusy, let bridge = httpBridge, let poolPort = bridge.poolPort,
              let relay = videoExit.relay else { return }
        let lanes = bridge.lanePool.readyLanes()
        guard !lanes.isEmpty else { return }
        videoPathBusy = true
        defer { videoPathBusy = false }
        let current = ExitCatalog.mapAddressPairs(exit: relay.fingerprint, domains: RoutingPolicy.youtubeDomains)
        do {
            try await engine.setAddressMappings(
                current + ExitCatalog.mapAddressPairs(exit: relay.fingerprint, domains: ThroughputProbe.measurementHosts))
        } catch {
            append(.veil(.debug, "Video lane: tor refused the measurement mapping: \(error.localizedDescription)"))
            return
        }
        var results: [(handle: LaneHandle, megabits: Double)] = []
        for handle in lanes {
            guard !Task.isCancelled, videoLaneRacingWanted else { break }
            var rate: Double?
            for target in ThroughputProbe.targets {
                if let sample = await ThroughputProbe.measure(socksPort: poolPort, target: target, duration: .seconds(3),
                                                              byteCap: 15_000_000, credentials: handle.credentials) {
                    rate = sample.megabitsPerSecond
                    break
                }
            }
            if let rate {
                results.append((handle, rate))
            } else {
                append(.veil(.debug, "Video lane: nothing came through lane \(handle.lane)"))
            }
        }
        await restoreMapping(current)
        guard !Task.isCancelled, let best = results.max(by: { $0.megabits < $1.megabits }) else { return }
        guard bridge.lanePool.bind(site: Self.videoSite, to: best.handle) else {
            append(.veil(.debug, "Video lane: lane \(best.handle.lane) moved on before it could be bound"))
            return
        }
        videoLane = VideoLaneState(lane: best.handle.lane, generation: best.handle.generation, megabits: best.megabits,
                                   field: Dictionary(uniqueKeysWithValues: results.map { ($0.handle.lane, $0.megabits) }),
                                   racedAt: .now)
        videoPathMegabits = best.megabits
        videoPathMeasuredAt = .now
        let field = results.sorted { $0.handle.lane < $1.handle.lane }
            .map { "\($0.handle.lane): \(Self.megabits($0.megabits))" }.joined(separator: ", ")
        append(.veil(.notice, "Video lane: lane \(best.handle.lane) carries \(Self.megabits(best.megabits)) Mbit/s, enough for \(ThroughputProbe.quality(forMegabits: best.megabits)) (lanes \(field))"))
    }
}
