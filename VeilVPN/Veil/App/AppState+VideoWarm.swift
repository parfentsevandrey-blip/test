import Foundation

/// Drives the tonus stream: the whole time the tunnel is up (or only while a video plays, by
/// choice), on the YouTube lane while a YouTube connection is open — so it shares the video's
/// circuit — and on the main route otherwise. Re-leased every minute so it follows the lane the
/// browser is actually on after a retirement. When the stream itself collapses, the path under
/// it has: the lanes are retired so the next connections get fresh circuits, and the video exit
/// is asked to prove itself again.
extension AppState {
    private static let warmSite = "youtube.com"

    private enum WarmMode: Equatable { case lane, route }

    var tunnelTonusWanted: Bool {
        guard connection == .connected, settings.videoKeepWarm, !engine.isSimulated else { return false }
        return settings.videoKeepWarmAlways || (httpBridge?.youtubeSessionsInFlight ?? 0) > 0
    }

    private var preferredWarmMode: WarmMode {
        settings.youtubeMode == .tor && (httpBridge?.youtubeSessionsInFlight ?? 0) > 0 && httpBridge?.poolPort != nil
            ? .lane : .route
    }

    func startVideoWarmSupervision() {
        videoWarmTask?.cancel()
        videoWarmTask = Task { [weak self] in
            var mode: WarmMode?
            var leasedAt: Date?
            var reachedTarget = false
            var lowSince: Date?
            var lastAction: Date?
            while !Task.isCancelled {
                guard let self else { return }
                let wanted = tunnelTonusWanted
                let preferred = preferredWarmMode
                if !wanted {
                    if videoWarmer.isRunning || videoWarmLease != nil { releaseWarm() }
                    mode = nil
                    leasedAt = nil
                    reachedTarget = false
                    lowSince = nil
                } else if !videoWarmer.isRunning || mode != preferred
                            || (preferred == .lane && leasedAt.map { Date.now.timeIntervalSince($0) > 60 } ?? false) {
                    releaseWarm()
                    mode = warm(preferred) ? preferred : nil
                    leasedAt = mode == nil ? nil : .now
                    reachedTarget = false
                    lowSince = nil
                } else {
                    // The stream is the gauge. Once it has reached half the target, a quarter of
                    // the target held for half a minute (or a stall) means the path under it
                    // dropped: retire the lanes so new connections get fresh circuits, and have
                    // the video exit prove itself again. At most once every three minutes.
                    let target = Double(settings.videoKeepWarmKilobytes * 1024)
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
                        append(.veil(.warn, "Tunnel tonus fell to \(ByteFormat.rate(rate)) against \(settings.videoKeepWarmKilobytes) KB/s: retiring the circuits and re-checking the video exit"))
                        httpBridge?.lanePool.retireAll(reason: .routeChanged)
                        latency.measureNow()
                        if videoExit.isActive { startVideoExitSelection(after: .seconds(1)) }
                        releaseWarm()
                        mode = warm(preferred) ? preferred : nil
                        leasedAt = mode == nil ? nil : .now
                    }
                }
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
            }
        }
    }

    func stopVideoWarmSupervision() {
        videoWarmTask?.cancel()
        videoWarmTask = nil
        releaseWarm()
    }

    func setVideoKeepWarm(_ enabled: Bool) {
        guard settings.videoKeepWarm != enabled else { return }
        settings.videoKeepWarm = enabled
        if !enabled { releaseWarm() }
    }

    func setVideoKeepWarmAlways(_ always: Bool) {
        guard settings.videoKeepWarmAlways != always else { return }
        settings.videoKeepWarmAlways = always
    }

    func setVideoKeepWarmRate(_ kilobytes: Int) {
        let clamped = VideoWarmer.rates.min { abs($0 - kilobytes) < abs($1 - kilobytes) } ?? 512
        guard settings.videoKeepWarmKilobytes != clamped else { return }
        settings.videoKeepWarmKilobytes = clamped
        if videoWarmer.isRunning { releaseWarm() } // the supervisor restarts it at the new rate
    }

    /// The lane the browser uses for YouTube is the one with the site's affinity; leasing the same
    /// site returns it — or binds the site to a lane now, which the browser then follows too.
    private func warm(_ mode: WarmMode) -> Bool {
        guard let ports else { return false }
        switch mode {
        case .lane:
            guard let bridge = httpBridge, let poolPort = bridge.poolPort,
                  let lease = bridge.lanePool.lease(site: Self.warmSite, avoiding: nil) else {
                return warm(.route)
            }
            videoWarmLease = lease
            videoWarmer.start(socksPort: poolPort, credentials: lease.credentials,
                              kilobytesPerSecond: settings.videoKeepWarmKilobytes)
        case .route:
            videoWarmer.start(socksPort: ports.socks, credentials: nil,
                              kilobytesPerSecond: settings.videoKeepWarmKilobytes)
        }
        return true
    }

    private func releaseWarm() {
        videoWarmer.stop()
        if let lease = videoWarmLease {
            videoWarmLease = nil
            httpBridge?.lanePool.release(lease)
        }
    }
}
