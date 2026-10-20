import AVFoundation
import AppKit
import MediaPlayer

/// Пункт «Исполняется» в Пункте управления, медиаклавиши и AirPods.
final class NowPlayingBridge {
    struct Handlers {
        var play: @MainActor () -> Void
        var pause: @MainActor () -> Void
        var toggle: @MainActor () -> Void
        var next: @MainActor () -> Void
        var previous: @MainActor () -> Void
        var seek: @MainActor (Double) -> Void
        var skip: @MainActor (Double) -> Void
    }

    private var configured = false

    func configure(_ h: Handlers) {
        guard !configured else { return }
        configured = true
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { _ in MainActor.assumeIsolated { h.play() }; return .success }
        c.pauseCommand.addTarget { _ in MainActor.assumeIsolated { h.pause() }; return .success }
        c.togglePlayPauseCommand.addTarget { _ in MainActor.assumeIsolated { h.toggle() }; return .success }
        c.nextTrackCommand.addTarget { _ in MainActor.assumeIsolated { h.next() }; return .success }
        c.previousTrackCommand.addTarget { _ in MainActor.assumeIsolated { h.previous() }; return .success }
        c.skipForwardCommand.preferredIntervals = [10]
        c.skipBackwardCommand.preferredIntervals = [10]
        c.skipForwardCommand.addTarget { _ in MainActor.assumeIsolated { h.skip(10) }; return .success }
        c.skipBackwardCommand.addTarget { _ in MainActor.assumeIsolated { h.skip(-10) }; return .success }
        c.changePlaybackPositionCommand.addTarget { event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let position = e.positionTime
            MainActor.assumeIsolated { h.seek(position) }
            return .success
        }
    }

    func update(title: String, duration: Double, elapsed: Double, rate: Float, artwork: CGImage?) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: elapsed,
            MPNowPlayingInfoPropertyPlaybackRate: Double(rate),
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.video.rawValue,
        ]
        if let artwork {
            let size = CGSize(width: artwork.width, height: artwork.height)
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: size) { _ in
                NSImage(cgImage: artwork, size: size)
            }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = rate > 0 ? .playing : .paused
    }

    func clear() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        MPNowPlayingInfoCenter.default().playbackState = .stopped
    }
}

/// Получает текст встроенных субтитров, чтобы рисовать их своим стеклянным оверлеем
/// вместо системного рендера.
final class LegibleBridge: NSObject, AVPlayerItemLegibleOutputPushDelegate {
    var onText: ((String?) -> Void)?

    nonisolated func legibleOutput(_ output: AVPlayerItemLegibleOutput,
                                   didOutputAttributedStrings strings: [NSAttributedString],
                                   nativeSampleBuffers nativeSamples: [Any],
                                   forItemTime itemTime: CMTime) {
        let text = strings.map(\.string).joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        let value: String? = text.isEmpty ? nil : text
        MainActor.assumeIsolated { onText?(value) }
    }
}
