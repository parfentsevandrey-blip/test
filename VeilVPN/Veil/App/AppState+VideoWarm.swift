import Foundation

/// Drives the circuit warmer: on while a YouTube connection is open through Tor and the setting
/// asks for it, off otherwise, and re-leased every minute so it follows the lane the browser is
/// actually on after a retirement.
extension AppState {
    private static let warmSite = "youtube.com"

    var videoWarmWanted: Bool {
        connection == .connected && settings.videoKeepWarm && settings.youtubeMode == .tor
            && !engine.isSimulated && (httpBridge?.youtubeSessionsInFlight ?? 0) > 0
    }

    func startVideoWarmSupervision() {
        videoWarmTask?.cancel()
        videoWarmTask = Task { [weak self] in
            var leasedAt: Date?
            while !Task.isCancelled {
                guard let self else { return }
                let wanted = videoWarmWanted
                if wanted, !videoWarmer.isRunning {
                    leasedAt = leaseAndWarm() ? .now : nil
                } else if !wanted, videoWarmer.isRunning || videoWarmLease != nil {
                    releaseWarm()
                    leasedAt = nil
                } else if wanted, let since = leasedAt, Date.now.timeIntervalSince(since) > 60 {
                    // The browser may have moved to a new lane since; follow it.
                    releaseWarm()
                    leasedAt = leaseAndWarm() ? .now : nil
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

    func setVideoKeepWarmRate(_ kilobytes: Int) {
        let clamped = VideoWarmer.rates.min { abs($0 - kilobytes) < abs($1 - kilobytes) } ?? 128
        guard settings.videoKeepWarmKilobytes != clamped else { return }
        settings.videoKeepWarmKilobytes = clamped
        if videoWarmer.isRunning {
            releaseWarm()
            _ = leaseAndWarm()
        }
    }

    /// The lane the browser uses for YouTube is the one with the site's affinity; leasing the same
    /// site returns it — or binds the site to a lane now, which the browser then follows too.
    private func leaseAndWarm() -> Bool {
        guard let bridge = httpBridge, let poolPort = bridge.poolPort,
              let lease = bridge.lanePool.lease(site: Self.warmSite, avoiding: nil) else {
            return false
        }
        videoWarmLease = lease
        videoWarmer.start(poolPort: poolPort, credentials: lease.credentials,
                          kilobytesPerSecond: settings.videoKeepWarmKilobytes)
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
