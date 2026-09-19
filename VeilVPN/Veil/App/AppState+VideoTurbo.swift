import Foundation

/// Turbo 4K, live. The settings side is `VideoTurbo`; this applies each change through the
/// setter that already knows its live consequence — the padding loop, the route, the video
/// exit, the guard, the tonus — tells tor the longer circuit lifetime, lengthens the lanes, and
/// has the video path prove itself again at once. A hand change to anything Turbo manages
/// switches it off and puts the rest back, so what the switch says is always what runs.
extension AppState {
    enum VideoTurboStatus: Equatable {
        case off
        /// On, and waiting for a connection to apply to.
        case idle
        /// On, behind a bridge that is the ceiling on its own.
        case capped(AppSettings.Transport)
        case choosing
        /// On, with the last measured rate of the video path when there is one.
        case running(megabits: Double?)
    }

    var videoTurboStatus: VideoTurboStatus {
        guard settings.videoTurbo else { return .off }
        guard connection == .connected else { return .idle }
        let transport = activeTransport ?? settings.transport
        if transport == .snowflake || transport == .meek { return .capped(transport) }
        switch videoExit {
        case .choosing: return .choosing
        case .active(_, let megabits): return .running(megabits: megabits ?? videoPathMegabits)
        case .unpinned(let megabits): return .running(megabits: megabits)
        case .off, .failed: return .running(megabits: videoPathMegabits)
        }
    }

    func toggleVideoTurbo() {
        setVideoTurbo(!settings.videoTurbo)
    }

    func setVideoTurbo(_ on: Bool) {
        guard settings.videoTurbo != on else { return }
        if on {
            // The values first, the switch last: the settings observer treats a value that is
            // not Turbo's as a hand change only once the switch is on.
            let target = VideoTurbo.applied(to: settings)
            applyVideoTurboValues(target)
            settings.videoTurboRestore = target.videoTurboRestore
            settings.videoTurbo = true
            pushVideoTurboLiveOptions()
            append(.veil(.notice, "Turbo 4K on: YouTube through Tor by the widest measured exit, guard and lane, the tunnel in tone at \(settings.videoKeepWarmKilobytes) KB/s the whole time on a circuit of its own, conflux in throughput mode, no padding, no multihop, no relay pinning, no per-site isolation; circuits take new streams for \(VideoTurbo.circuitLifetimeSeconds / 60) minutes"))
            if connection == .connected { startVideoExitSelection(after: .seconds(1)) }
        } else {
            settings.videoTurbo = false
            let target = VideoTurbo.restored(settings)
            applyVideoTurboValues(target)
            settings.videoTurboRestore = nil
            pushVideoTurboLiveOptions()
            append(.veil(.notice, "Turbo 4K off; everything it managed is back as it was"))
        }
    }

    /// From the settings observer: a value Turbo manages is no longer the one it set. That
    /// change is the user's and stands; Turbo goes off and the rest goes back.
    func videoTurboBroken() {
        guard settings.videoTurbo else { return }
        settings.videoTurbo = false
        let target = VideoTurbo.restored(settings)
        applyVideoTurboValues(target)
        settings.videoTurboRestore = nil
        pushVideoTurboLiveOptions()
        append(.veil(.notice, "Turbo 4K off: a setting it manages was changed by hand; the rest are back as they were"))
    }

    /// Each field through its own setter, so the live side follows. Relay pinning first (it and
    /// the video exit contradict each other), the route next, the YouTube pieces last so that
    /// their selection runs against the final route.
    private func applyVideoTurboValues(_ target: AppSettings) {
        setLatencyTuning(target.latencyTuning)
        setPaddingEnabled(target.paddingEnabled)
        setMultihopEnabled(target.multihopEnabled)
        setConfluxLatency(target.confluxLatency)
        if settings.isolatePerSite != target.isolatePerSite { setIsolatePerSite(target.isolatePerSite) }
        if settings.snowflakePeers != target.snowflakePeers { settings.snowflakePeers = target.snowflakePeers }
        setYouTubeMode(target.youtubeMode)
        settings.youtubeModeChosen = target.youtubeModeChosen
        setVideoExitEnabled(target.videoExitEnabled)
        setVideoGuardPinning(target.videoGuardPinning)
        setVideoKeepWarm(target.videoKeepWarm)
        setVideoKeepWarmAlways(target.videoKeepWarmAlways)
        setVideoKeepWarmRate(target.videoKeepWarmKilobytes)
    }

    /// The circuit lifetime to tor and the lane lifetime to the pool, for the current switch.
    private func pushVideoTurboLiveOptions() {
        httpBridge?.lanePool.setLaneLifetime(settings.videoTurbo ? VideoTurbo.laneLifetime
                                                                 : LanePool.Configuration().laneLifetime)
        guard connection == .connected else { return }
        let current = settings
        Task { [weak self] in await self?.engine.applyPerformanceOptions(settings: current) }
    }

    /// Conflux's leg preference, live: new conflux sets read it; the ones open keep theirs.
    func setConfluxLatency(_ latency: Bool) {
        guard settings.confluxLatency != latency else { return }
        settings.confluxLatency = latency
        guard connection == .connected else { return }
        let current = settings
        Task { [weak self] in await self?.engine.applyPerformanceOptions(settings: current) }
    }
}
