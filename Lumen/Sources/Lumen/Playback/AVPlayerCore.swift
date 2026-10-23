import AVFoundation
import AVKit
import AppKit
import Combine
import QuartzCore

/// MP4, MOV, M4V и аудио: всё это macOS играет сама, через AVPlayer.
@MainActor
final class AVPlayerCore: PlaybackCore {
    let player = AVPlayer()
    private let layer = AVPlayerLayer()
    var videoLayer: CALayer { layer }
    private(set) var info: MediaInfo
    private(set) var chapters: [ChapterMark]
    private(set) var audioOptions: [AudioOption] = []
    private(set) var selectedAudio = 0
    private(set) var subtitles: [EmbeddedSubtitle] = []
    private(set) var isPlaying = false
    private(set) var hasEnded = false

    private let url: URL
    private let item: AVPlayerItem
    private var audioGroup: AVMediaSelectionGroup?
    private var legibleGroup: AVMediaSelectionGroup?
    private var legibleOutput: AVPlayerItemLegibleOutput?
    private let legibleBridge = LegibleBridge()
    private var legibleText: String?
    private var ambientOutput: AVPlayerItemVideoOutput?
    private var observers: Set<AnyCancellable> = []
    private var endedFlag = false

    static func open(_ url: URL) async throws -> AVPlayerCore {
        let asset = AVURLAsset(url: url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
        let info = try await AssetInspector.inspect(asset, original: url)
        let audio = try? await asset.loadMediaSelectionGroup(for: .audible)
        let legible = try? await asset.loadMediaSelectionGroup(for: .legible)
        return AVPlayerCore(url: url, asset: asset, info: info, audio: audio, legible: legible)
    }

    private init(url: URL, asset: AVURLAsset, info: MediaInfo, audio: AVMediaSelectionGroup?, legible: AVMediaSelectionGroup?) {
        self.url = url
        self.info = info
        chapters = info.chapters
        item = AVPlayerItem(asset: asset)
        item.audioTimePitchAlgorithm = .spectral
        item.preferredForwardBufferDuration = 8
        player.preventsDisplaySleepDuringVideoPlayback = true
        player.appliesMediaSelectionCriteriaAutomatically = false
        layer.player = player
        layer.videoGravity = .resizeAspect
        layer.backgroundColor = NSColor.black.cgColor

        audioGroup = audio
        legibleGroup = legible
        let audible = audio?.options ?? []
        audioOptions = audible.enumerated().map { i, option in
            AudioOption(id: i, title: option.displayName, detail: "", available: true)
        }
        if let group = audio, let selected = group.defaultOption ?? group.options.first {
            item.select(selected, in: group)
            selectedAudio = group.options.firstIndex(of: selected) ?? 0
        }
        if let legible, !legible.options.isEmpty {
            let output = AVPlayerItemLegibleOutput()
            output.suppressesPlayerRendering = true
            output.setDelegate(legibleBridge, queue: .main)
            item.add(output)
            legibleOutput = output
            if legible.allowsEmptySelection { item.select(nil, in: legible) }
            subtitles = legible.options.enumerated().map { EmbeddedSubtitle(id: $0.offset, title: $0.element.displayName, kind: .legible) }
        }
        legibleBridge.onText = { [weak self] text in self?.legibleText = text }

        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: 64,
            kCVPixelBufferHeightKey as String: 36,
        ])
        item.add(output)
        ambientOutput = output

        player.replaceCurrentItem(with: item)
        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.endedFlag = true }
            .store(in: &observers)
        player.publisher(for: \.timeControlStatus)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in self?.isPlaying = status != .paused }
            .store(in: &observers)
    }

    var failure: String? {
        item.status == .failed ? (item.error?.localizedDescription ?? "Не удалось воспроизвести файл") : nil
    }

    var currentTime: Double {
        let t = player.currentTime().seconds
        return t.isFinite ? t : 0
    }

    func play(rate: Float) {
        hasEnded = false
        player.playImmediately(atRate: rate)
        isPlaying = true
    }

    func pause() {
        player.pause()
        isPlaying = false
    }

    func setRate(_ rate: Float) {
        player.defaultRate = rate
        if isPlaying { player.rate = rate }
    }

    func seek(to seconds: Double, precise: Bool, completion: @escaping @MainActor () -> Void) {
        hasEnded = false
        let tolerance = precise ? CMTime.zero : CMTime(seconds: 0.4, preferredTimescale: 600)
        PlayerCallbacks.seek(player, to: CMTime(seconds: seconds, preferredTimescale: 600), tolerance: tolerance) { _ in
            Task { @MainActor in completion() }
        }
    }

    func step(_ frames: Int) {
        pause()
        player.currentItem?.step(byCount: frames)
    }

    func setVolume(_ volume: Float, muted: Bool) {
        player.volume = volume
        player.isMuted = muted
    }

    func selectAudio(_ index: Int) {
        guard let group = audioGroup, group.options.indices.contains(index) else { return }
        item.select(group.options[index], in: group)
        selectedAudio = index
    }

    func selectSubtitle(_ id: Int?) {
        legibleText = nil
        guard let group = legibleGroup else { return }
        if let id, group.options.indices.contains(id) {
            item.select(group.options[id], in: group)
        } else if group.allowsEmptySelection {
            item.select(nil, in: group)
        }
    }

    func subtitleText(at time: Double) -> String? { legibleText }
    func subtitleImage(at time: Double) -> PGSImage? { nil }

    func ambientFrame() -> CVPixelBuffer? {
        guard let output = ambientOutput else { return nil }
        let time = output.itemTime(forHostTime: CACurrentMediaTime())
        return output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil)
    }

    func snapshot() async -> CGImage? {
        await Thumbnailer.exactFrame(url: url, at: player.currentTime())
    }

    func makePictureInPicture() -> AVPictureInPictureController? {
        AVPictureInPictureController.isPictureInPictureSupported() ? AVPictureInPictureController(playerLayer: layer) : nil
    }

    func setGravity(_ gravity: AVLayerVideoGravity) {
        layer.videoGravity = gravity
    }

    func pollEnd() -> Bool {
        guard endedFlag else { return false }
        endedFlag = false
        hasEnded = true
        isPlaying = false
        return true
    }

    func teardown() {
        observers.removeAll()
        if let output = legibleOutput { item.remove(output) }
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
}

/// Всё, что система вызывает на своих потоках (завершение перемотки), создаётся вне главного актора —
/// иначе Swift 6 считает замыкания изолированными на нём и останавливает процесс проверкой изоляции.
nonisolated enum PlayerCallbacks {
    static func seek(_ player: AVPlayer, to time: CMTime, tolerance: CMTime, completion: @escaping @Sendable (Bool) -> Void) {
        player.seek(to: time, toleranceBefore: tolerance, toleranceAfter: tolerance, completionHandler: completion)
    }
}
