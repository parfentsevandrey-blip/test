import AVFoundation
import AppKit
import MediaPlayer

/// Пункт «Исполняется» в Пункте управления, медиаклавиши и AirPods.
final class NowPlayingBridge {
    struct Handlers: Sendable {
        var play: @MainActor @Sendable () -> Void
        var pause: @MainActor @Sendable () -> Void
        var toggle: @MainActor @Sendable () -> Void
        var next: @MainActor @Sendable () -> Void
        var previous: @MainActor @Sendable () -> Void
        var seek: @MainActor @Sendable (Double) -> Void
        var skip: @MainActor @Sendable (Double) -> Void
    }

    private var configured = false

    func configure(_ h: Handlers) {
        guard !configured else { return }
        configured = true
        Self.register(h)
    }

    func update(title: String, duration: Double, elapsed: Double, rate: Float, artwork: CGImage?) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: elapsed,
            MPNowPlayingInfoPropertyPlaybackRate: Double(rate),
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.video.rawValue,
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = Self.artwork(artwork) }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = rate > 0 ? .playing : .paused
    }

    // MediaPlayer вызывает эти замыкания на своих очередях. Они создаются в nonisolated-
    // контексте, иначе Swift 6 привяжет их к главному актору и уронит процесс проверкой изоляции.

    nonisolated private static func artwork(_ image: CGImage) -> MPMediaItemArtwork {
        let size = CGSize(width: image.width, height: image.height)
        return MPMediaItemArtwork(boundsSize: size) { _ in NSImage(cgImage: image, size: size) }
    }

    nonisolated private static func register(_ h: Handlers) {
        let c = MPRemoteCommandCenter.shared()
        func on(_ command: MPRemoteCommand, _ action: @escaping @MainActor @Sendable () -> Void) {
            command.addTarget { _ in
                Task { @MainActor in action() }
                return .success
            }
        }
        on(c.playCommand) { h.play() }
        on(c.pauseCommand) { h.pause() }
        on(c.togglePlayPauseCommand) { h.toggle() }
        on(c.nextTrackCommand) { h.next() }
        on(c.previousTrackCommand) { h.previous() }
        c.skipForwardCommand.preferredIntervals = [10]
        c.skipBackwardCommand.preferredIntervals = [10]
        on(c.skipForwardCommand) { h.skip(10) }
        on(c.skipBackwardCommand) { h.skip(-10) }
        c.changePlaybackPositionCommand.addTarget { event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let position = e.positionTime
            Task { @MainActor in h.seek(position) }
            return .success
        }
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
