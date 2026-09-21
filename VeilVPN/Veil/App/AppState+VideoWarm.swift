import Foundation

/// Drives the tonus stream: the whole time the tunnel is up (or only while a video plays, by
/// choice), on a circuit of its own. The link it warms is the one every circuit shares — the TCP
/// connection to the guard or the bridge, the one whose window collapses after an idle — and
/// that link warms from any circuit through it; riding the video's own circuit, as it once did,
/// only took its rate out of the video's share. When the stream itself collapses, the path under
/// it has: the lanes are retired so the next connections get fresh circuits, and the video exit
/// is asked to prove itself again.
extension AppState {
    /// The rate the tonus actually runs at. Behind Snowflake or meek the whole tunnel is one
    /// volunteer proxy or one CDN front, often only a few hundred KB/s in total: 512 KB/s of
    /// tonus would take all of it and leave the video nothing — which is the stream failing four
    /// times in a row in the log while the browser waits. It still runs, at the lowest rate that
    /// keeps the link from idling back to slow start.
    var effectiveTonusKilobytes: Int {
        var rate = settings.videoKeepWarmKilobytes
        let transport = activeTransport ?? settings.transport
        if transport == .snowflake || transport == .meek {
            rate = min(rate, VideoWarmer.rates.first ?? 128)
        }
        // Never more than a quarter of what the path was actually measured to carry. Holding a
        // congestion window open takes a trickle; on a path that carries 1 Mbit/s in total — a
        // Snowflake proxy, say — a 128 KB/s tonus *is* the whole path, and what is left for the
        // video is nothing. This is the difference between keeping the pipe warm and owning it.
        if let megabits = videoLane?.megabits ?? videoPathMegabits, megabits > 0 {
            let kilobytesPerSecond = megabits * 1000 / 8
            rate = min(rate, max(VideoWarmer.minimumKilobytes, Int(kilobytesPerSecond / 4)))
        }
        return max(VideoWarmer.minimumKilobytes, rate)
    }

    var tunnelTonusWanted: Bool {
        guard connection == .connected, settings.videoKeepWarm, !engine.isSimulated else { return false }
        return settings.videoKeepWarmAlways || (httpBridge?.youtubeSessionsInFlight ?? 0) > 0
    }

    func startVideoWarmSupervision() {
        videoWarmTask?.cancel()
        videoWarmTask = Task { [weak self] in
            var reachedTarget = false
            var lowSince: Date?
            var lastAction: Date?
            while !Task.isCancelled {
                guard let self else { return }
                if !tunnelTonusWanted {
                    if videoWarmer.isRunning { videoWarmer.stop() }
                    reachedTarget = false
                    lowSince = nil
                } else if !videoWarmer.isRunning || tonusRateDrifted {
                    // A measurement that changes what the path can carry changes the tonus with
                    // it: the stream is restarted at the new rate rather than holding the old one.
                    videoWarmer.stop()
                    warm()
                    reachedTarget = false
                    lowSince = nil
                } else {
                    // The stream is the gauge. Once it has reached half the target, a quarter of
                    // the target held for half a minute (or a stall) means the path under it
                    // dropped: retire the lanes so new connections get fresh circuits, and have
                    // the video exit prove itself again. At most once every three minutes.
                    let target = Double(effectiveTonusKilobytes * 1024)
                    let rate = videoWarmer.bytesPerSecond
                    if rate >= target * 0.5 { reachedTarget = true; lowSince = nil }
                    let low = videoWarmer.stalled || (reachedTarget && rate < target * 0.25)
                    if low, lowSince == nil { lowSince = .now }
                    if !low { lowSince = nil }
                    if let since = lowSince, Date.now.timeIntervalSince(since) >= 30,
                       lastAction.map({ Date.now.timeIntervalSince($0) > 180 }) ?? true {
                        lastAction = .now
                        lowSince = nil
                        reachedTarget = false
                        append(.veil(.warn, "Tunnel tonus fell to \(ByteFormat.rate(rate)) against \(effectiveTonusKilobytes) KB/s: retiring the circuits and re-checking the video exit"))
                        httpBridge?.lanePool.retireAll(reason: .routeChanged)
                        latency.measureNow()
                        if videoExit.isActive { startVideoExitSelection(after: .seconds(1)) }
                        videoWarmer.stop()
                        warm()
                    }
                }
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
            }
        }
    }

    func stopVideoWarmSupervision() {
        videoWarmTask?.cancel()
        videoWarmTask = nil
        videoWarmer.stop()
    }

    func setVideoKeepWarm(_ enabled: Bool) {
        guard settings.videoKeepWarm != enabled else { return }
        settings.videoKeepWarm = enabled
        if !enabled { videoWarmer.stop() }
    }

    func setVideoKeepWarmAlways(_ always: Bool) {
        guard settings.videoKeepWarmAlways != always else { return }
        settings.videoKeepWarmAlways = always
    }

    func setVideoKeepWarmRate(_ kilobytes: Int) {
        let clamped = VideoWarmer.rates.min { abs($0 - kilobytes) < abs($1 - kilobytes) } ?? 512
        guard settings.videoKeepWarmKilobytes != clamped else { return }
        settings.videoKeepWarmKilobytes = clamped
        if videoWarmer.isRunning { videoWarmer.stop() } // the supervisor restarts it at the new rate
    }

    /// The running stream is more than a quarter off the rate the path now justifies.
    private var tonusRateDrifted: Bool {
        let running = videoWarmer.targetKilobytes
        guard running > 0 else { return false }
        return abs(running - effectiveTonusKilobytes) * 4 > running
    }

    /// Credentials of its own on the plain port, so tor isolates the stream on a circuit of its
    /// own: through the same guard as everything, which is the link it is there to warm, and on
    /// no circuit that carries anything else — the video's, or a measurement's.
    private func warm() {
        guard let ports else { return }
        let credentials = SOCKS5.Credentials(username: "veil-tonus", password: Self.randomPassword())
        videoWarmer.start(socksPort: ports.socks, credentials: credentials,
                          kilobytesPerSecond: effectiveTonusKilobytes)
    }
}
