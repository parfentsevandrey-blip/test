import AVFoundation
import AVKit
import AppKit
import Combine
import Observation
import SwiftUI

nonisolated enum RepeatMode: String, CaseIterable, Sendable {
    case off, one, all

    var symbol: String {
        switch self {
        case .off: "repeat"
        case .one: "repeat.1"
        case .all: "repeat"
        }
    }
}

nonisolated enum VideoGravity: String, CaseIterable, Sendable {
    case fit, fill, stretch

    var title: String {
        switch self {
        case .fit: "Вписать"
        case .fill: "Заполнить"
        case .stretch: "Растянуть"
        }
    }

    var symbol: String {
        switch self {
        case .fit: "rectangle.arrowtriangle.2.inward"
        case .fill: "rectangle.arrowtriangle.2.outward"
        case .stretch: "arrow.left.and.right.square"
        }
    }

    var avGravity: AVLayerVideoGravity {
        switch self {
        case .fit: .resizeAspect
        case .fill: .resizeAspectFill
        case .stretch: .resize
        }
    }
}

struct HUDMessage: Equatable, Identifiable {
    let id = UUID()
    var symbol: String
    var title: String
    var level: Double?
}

struct CapturedFrame: Identifiable {
    let id = UUID()
    let image: NSImage
    let url: URL
}

enum SubtitleChoice: Hashable {
    case off
    case embedded(Int)
    case external(Int)
}

struct SubtitleOption: Identifiable, Hashable {
    let choice: SubtitleChoice
    let title: String
    var id: SubtitleChoice { choice }
}

/// Всё состояние воспроизведения. Живёт на главном акторе; тяжёлая работа
/// (перепаковка, кадры, анализ) уходит в фоновые задачи.
@Observable
final class PlayerEngine {
    enum Phase: Equatable {
        case idle
        case preparing(progress: Double?, message: String)
        case ready
        case failed(String)
    }

    static let speeds: [Float] = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3]

    let player = AVPlayer()
    let ambient = AmbientSampler()
    let previewer = ScrubPreviewer()

    private(set) var phase: Phase = .idle
    private(set) var current: MediaItem?
    private(set) var info = MediaInfo()
    private(set) var notes: [String] = []
    private(set) var currentTime: Double = 0
    private(set) var duration: Double = 0
    private(set) var isPlaying = false
    private(set) var isBuffering = false
    private(set) var rate: Float = 1
    private(set) var chapters: [ChapterMark] = []

    var volume: Float = 1 {
        didSet {
            player.volume = volume
            UserDefaults.standard.set(Double(volume), forKey: Prefs.volume)
        }
    }
    var isMuted = false { didSet { player.isMuted = isMuted } }

    // Дорожки
    private(set) var audioOptions: [String] = []
    private(set) var selectedAudio = 0
    private(set) var subtitleOptions: [SubtitleOption] = []
    private(set) var subtitleChoice: SubtitleChoice = .off
    private(set) var subtitleText: String?
    var subtitleDelay: Double = 0 { didSet { updateExternalSubtitle() } }

    // Картинка
    var adjustments = VideoAdjustments() { didSet { adjustmentsChanged() } }
    var gravity: VideoGravity = .fit
    var zoom: CGFloat = 1
    var rotation = 0
    var mirrored = false

    // Очередь
    private(set) var queue: [MediaItem] = []
    private(set) var queueIndex = 0
    var repeatMode: RepeatMode = .off
    var shuffle = false

    // A-B повтор
    private(set) var loopA: Double?
    private(set) var loopB: Double?

    // Уведомления интерфейса
    private(set) var hud: HUDMessage?
    private(set) var resumedFrom: Double?
    private(set) var capture: CapturedFrame?
    private(set) var isPictureInPictureActive = false
    var isScrubbing = false

    /// Вызывается, когда известен размер кадра (для подгонки окна).
    @ObservationIgnored var onVideoReady: ((CGSize) -> Void)?

    @ObservationIgnored private weak var library: MediaLibrary?
    @ObservationIgnored private let adjustmentBox = AdjustmentBox()
    @ObservationIgnored private let nowPlaying = NowPlayingBridge()
    @ObservationIgnored private let legibleBridge = LegibleBridge()
    @ObservationIgnored private var legibleOutput: AVPlayerItemLegibleOutput?
    @ObservationIgnored private var audioGroup: AVMediaSelectionGroup?
    @ObservationIgnored private var legibleGroup: AVMediaSelectionGroup?
    @ObservationIgnored private var externalSubtitles: [SubtitleTrackData] = []
    @ObservationIgnored private var source: PlaybackSource?
    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var hudTask: Task<Void, Never>?
    @ObservationIgnored private var itemObservers: Set<AnyCancellable> = []
    @ObservationIgnored private var playerObservers: Set<AnyCancellable> = []
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var pip: AVPictureInPictureController?
    @ObservationIgnored private var compositionActive = false
    @ObservationIgnored private var seekTarget: CMTime?
    @ObservationIgnored private var seekInFlight = false
    @ObservationIgnored private var lastPersist: Double = 0
    @ObservationIgnored private var artwork: CGImage?

    init(library: MediaLibrary) {
        self.library = library
        let stored = UserDefaults.standard.double(forKey: Prefs.volume)
        volume = Float(stored == 0 ? 1 : stored)
        player.volume = volume
        player.preventsDisplaySleepDuringVideoPlayback = true
        player.appliesMediaSelectionCriteriaAutomatically = false
        legibleBridge.onText = { [weak self] text in
            guard let self, case .embedded = self.subtitleChoice else { return }
            self.subtitleText = text
        }
        observePlayer()
        nowPlaying.configure(.init(
            play: { [weak self] in self?.play() },
            pause: { [weak self] in self?.pause() },
            toggle: { [weak self] in self?.togglePlay() },
            next: { [weak self] in self?.next() },
            previous: { [weak self] in self?.previous() },
            seek: { [weak self] in self?.seek(to: $0) },
            skip: { [weak self] in self?.skip(by: $0) }
        ))
    }

    // MARK: - Открытие

    var hasMedia: Bool { current != nil }
    var hasNext: Bool { queueIndex + 1 < queue.count || repeatMode == .all }
    var hasPrevious: Bool { queueIndex > 0 || currentTime > 3 }
    var displayTitle: String { current?.title ?? "Lumen" }

    func open(_ items: [MediaItem], startAt index: Int = 0) {
        guard items.indices.contains(index) else { return }
        queue = items
        queueIndex = index
        load(items[index])
    }

    func enqueue(_ items: [MediaItem]) {
        let known = Set(queue.map(\.id))
        queue += items.filter { !known.contains($0.id) }
    }

    func playQueueItem(at index: Int) {
        guard queue.indices.contains(index) else { return }
        queueIndex = index
        load(queue[index])
    }

    func removeFromQueue(at offsets: IndexSet) {
        let currentID = current?.id
        queue.remove(atOffsets: offsets)
        queueIndex = queue.firstIndex { $0.id == currentID } ?? 0
    }

    func moveInQueue(from source: IndexSet, to destination: Int) {
        let currentID = current?.id
        queue.move(fromOffsets: source, toOffset: destination)
        queueIndex = queue.firstIndex { $0.id == currentID } ?? 0
    }

    func next() {
        guard !queue.isEmpty else { return }
        if shuffle, queue.count > 1 {
            var index = queueIndex
            while index == queueIndex { index = Int.random(in: 0..<queue.count) }
            playQueueItem(at: index)
        } else if queueIndex + 1 < queue.count {
            playQueueItem(at: queueIndex + 1)
        } else if repeatMode == .all {
            playQueueItem(at: 0)
        }
    }

    func previous() {
        if currentTime > 3 || queueIndex == 0 {
            seek(to: 0)
        } else {
            playQueueItem(at: queueIndex - 1)
        }
    }

    func stop() {
        persistPosition()
        loadTask?.cancel()
        teardownItem()
        player.replaceCurrentItem(with: nil)
        current = nil
        phase = .idle
        nowPlaying.clear()
    }

    func retry() {
        if let current { load(current) }
    }

    private func load(_ item: MediaItem) {
        persistPosition()
        loadTask?.cancel()
        teardownItem()
        current = item
        currentTime = 0
        duration = item.duration
        info = MediaInfo()
        notes = []
        chapters = []
        loopA = nil
        loopB = nil
        resumedFrom = nil
        adjustmentsReset()
        phase = .preparing(progress: nil, message: "Открываю…")
        loadTask = Task { [weak self] in await self?.prepare(item) }
    }

    private func prepare(_ item: MediaItem) async {
        do {
            let source = try await MediaResolver.resolve(item.url) { [weak self] fraction, message in
                Task { @MainActor in
                    guard let self, self.current?.id == item.id, case .preparing = self.phase else { return }
                    self.phase = .preparing(progress: fraction, message: message)
                }
            }
            try Task.checkCancellation()
            let asset = AVURLAsset(url: source.url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
            var info = try await AssetInspector.inspect(asset, original: item.url)
            try Task.checkCancellation()
            if let manifest = source.manifest {
                info.convertedFrom = [manifest.sourceVideoCodec, manifest.sourceAudioCodecs.first].compactMap { $0 }.joined(separator: " + ")
                if info.chapters.isEmpty { info.chapters = manifest.chapters }
            }
            await start(item: item, asset: asset, source: source, info: info)
        } catch is CancellationError {
        } catch {
            guard current?.id == item.id else { return }
            phase = .failed(error.localizedDescription)
        }
    }

    private func start(item: MediaItem, asset: AVURLAsset, source: PlaybackSource, info: MediaInfo) async {
        self.source = source
        self.info = info
        duration = info.duration
        chapters = info.chapters
        notes = source.manifest?.notes ?? []

        let playerItem = AVPlayerItem(asset: asset)
        playerItem.audioTimePitchAlgorithm = .spectral
        playerItem.preferredForwardBufferDuration = 8

        // Дорожки звука и встроенные субтитры
        audioGroup = try? await asset.loadMediaSelectionGroup(for: .audible)
        legibleGroup = try? await asset.loadMediaSelectionGroup(for: .legible)
        guard current?.id == item.id, !Task.isCancelled else { return }

        let names = source.manifest?.audioNames ?? []
        let audible = audioGroup?.options ?? []
        audioOptions = audible.enumerated().map { index, option in
            names.count == audible.count ? names[index] : option.displayName
        }
        if let group = audioGroup, let selected = group.defaultOption ?? group.options.first {
            playerItem.select(selected, in: group)
            selectedAudio = group.options.firstIndex(of: selected) ?? 0
        }

        if let legibleGroup, !legibleGroup.options.isEmpty {
            let output = AVPlayerItemLegibleOutput()
            output.suppressesPlayerRendering = true
            output.setDelegate(legibleBridge, queue: .main)
            playerItem.add(output)
            legibleOutput = output
            if legibleGroup.allowsEmptySelection { playerItem.select(nil, in: legibleGroup) }
        }

        // Внешние субтитры: из перепаковки и файлы рядом с видео.
        var external = source.manifest?.subtitles ?? []
        let sidecars = SubtitleParser.sidecars(for: item.url)
        let loaded = await Task.detached(priority: .userInitiated) {
            sidecars.compactMap { try? SubtitleParser.load($0) }.filter { !$0.cues.isEmpty }
        }.value
        external += loaded
        externalSubtitles = external
        rebuildSubtitleOptions()

        ambient.attach(to: playerItem)
        player.replaceCurrentItem(with: playerItem)
        observe(playerItem)
        previewer.reset(url: source.url, duration: info.duration)
        if !adjustments.isIdentity { rebuildComposition() }

        // Продолжение просмотра
        let saved = library?.item(id: item.id)?.position ?? 0
        if UserDefaults.standard.bool(forKey: Prefs.resumePlayback), saved > 15, info.duration > 0, saved < info.duration * 0.95 {
            player.seek(to: CMTime(seconds: saved, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) { _ in }
            currentTime = saved
            resumedFrom = saved
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(7))
                self?.resumedFrom = nil
            }
        }

        if UserDefaults.standard.bool(forKey: Prefs.autoSubtitles),
           let first = subtitleOptions.first(where: { $0.choice != .off }) {
            selectSubtitle(first.choice)
        }

        library?.update(item.id) {
            $0.lastPlayedAt = Date()
            $0.duration = info.duration
        }
        phase = .ready
        if info.hasVideo { onVideoReady?(info.displaySize) }
        player.playImmediately(atRate: rate)
        ambient.start()
        captureArtwork(for: item, url: source.url)
    }

    private func teardownItem() {
        itemObservers.removeAll()
        if let output = legibleOutput, let item = player.currentItem { item.remove(output) }
        legibleOutput = nil
        audioGroup = nil
        legibleGroup = nil
        externalSubtitles = []
        subtitleOptions = []
        subtitleChoice = .off
        subtitleText = nil
        audioOptions = []
        selectedAudio = 0
        compositionActive = false
        seekTarget = nil
        seekInFlight = false
        ambient.stop()
        ambient.detach()
        player.pause()
    }

    // MARK: - Наблюдение

    private func observePlayer() {
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 10), queue: .main) { [weak self] time in
            MainActor.assumeIsolated { self?.tick(time.seconds) }
        }
        player.publisher(for: \.timeControlStatus)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self else { return }
                let playing = status != .paused
                if playing != self.isPlaying {
                    self.isPlaying = playing
                    playing ? self.ambient.start() : self.ambient.stop()
                    self.updateNowPlaying()
                    if !playing { self.persistPosition() }
                }
                self.isBuffering = status == .waitingToPlayAtSpecifiedRate
            }
            .store(in: &playerObservers)
    }

    private func observe(_ item: AVPlayerItem) {
        item.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self, status == .failed else { return }
                self.phase = .failed(self.player.currentItem?.error?.localizedDescription ?? "Не удалось воспроизвести файл")
            }
            .store(in: &itemObservers)
        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification, object: item)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.itemDidEnd() }
            .store(in: &itemObservers)
    }

    private func tick(_ seconds: Double) {
        guard seconds.isFinite, !isScrubbing, seekTarget == nil else { return }
        currentTime = seconds
        if let a = loopA, let b = loopB, seconds >= b {
            seek(to: a)
        }
        updateExternalSubtitle()
        if isPlaying, abs(seconds - lastPersist) > 5 { persistPosition() }
    }

    private func itemDidEnd() {
        guard let current else { return }
        library?.update(current.id) { $0.position = $0.duration }
        if repeatMode == .one {
            seek(to: 0)
            play()
        } else if UserDefaults.standard.bool(forKey: Prefs.autoplayNext), hasNext {
            next()
        } else {
            isPlaying = false
        }
    }

    // MARK: - Управление

    func play() {
        guard player.currentItem != nil else { return }
        if duration > 0, currentTime >= duration - 0.3 { seek(to: 0) }
        player.playImmediately(atRate: rate)
    }

    func pause() { player.pause() }

    func togglePlay() {
        let willPlay = !isPlaying
        willPlay ? play() : pause()
        flash(willPlay ? "play.fill" : "pause.fill", willPlay ? "Воспроизведение" : "Пауза")
    }

    func seek(to seconds: Double, precise: Bool = true) {
        guard player.currentItem != nil else { return }
        let clamped = max(0, min(seconds, max(duration - 0.05, 0)))
        currentTime = clamped
        seekTarget = CMTime(seconds: clamped, preferredTimescale: 600)
        if !seekInFlight { performSeek(precise: precise) }
        updateExternalSubtitle()
    }

    /// «Догоняющая» перемотка: не ставим в очередь сотню запросов при скраббинге,
    /// а всегда переходим к последней цели после завершения текущей.
    private func performSeek(precise: Bool) {
        guard let target = seekTarget else { return }
        seekInFlight = true
        let tolerance = precise ? CMTime.zero : CMTime(seconds: 0.4, preferredTimescale: 600)
        player.seek(to: target, toleranceBefore: tolerance, toleranceAfter: tolerance) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.seekTarget == target {
                    self.seekTarget = nil
                    self.seekInFlight = false
                    if !self.isPlaying { self.ambient.refresh() }
                    self.updateNowPlaying()
                } else {
                    self.performSeek(precise: precise)
                }
            }
        }
    }

    func skip(by delta: Double) {
        seek(to: currentTime + delta)
        flash(delta > 0 ? "goforward" : "gobackward", "\(delta > 0 ? "+" : "−")\(Int(abs(delta))) с")
    }

    func seek(fraction: Double) {
        seek(to: duration * min(max(fraction, 0), 1))
    }

    func stepFrame(_ count: Int) {
        pause()
        player.currentItem?.step(byCount: count)
        Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(60))
            guard let self else { return }
            self.currentTime = self.player.currentTime().seconds
            self.ambient.refresh()
        }
        flash(count > 0 ? "forward.frame.fill" : "backward.frame.fill", count > 0 ? "Кадр вперёд" : "Кадр назад")
    }

    func setRate(_ value: Float) {
        rate = value
        player.defaultRate = value
        if isPlaying { player.rate = value }
        flash("gauge.with.dots.needle.67percent", Self.rateLabel(value))
        updateNowPlaying()
    }

    func changeRate(by step: Int) {
        let index = Self.speeds.firstIndex(of: rate) ?? Self.speeds.firstIndex(of: 1)!
        let next = min(max(index + step, 0), Self.speeds.count - 1)
        setRate(Self.speeds[next])
    }

    static func rateLabel(_ r: Float) -> String {
        r == 1 ? "1×" : (r.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(r))×" : "\(String(format: "%g", r))×")
    }

    func changeVolume(by delta: Float) {
        isMuted = false
        volume = min(max(volume + delta, 0), 1)
        flash(volumeSymbol, "Громкость", level: Double(volume))
    }

    func toggleMute() {
        isMuted.toggle()
        flash(volumeSymbol, isMuted ? "Без звука" : "Звук включён", level: isMuted ? 0 : Double(volume))
    }

    var volumeSymbol: String {
        if isMuted || volume == 0 { return "speaker.slash.fill" }
        if volume < 0.34 { return "speaker.wave.1.fill" }
        if volume < 0.67 { return "speaker.wave.2.fill" }
        return "speaker.wave.3.fill"
    }

    // MARK: - A-B

    func markLoop() {
        if loopA == nil || loopB != nil {
            loopA = currentTime
            loopB = nil
            flash("a.circle.fill", "Точка A")
        } else if let a = loopA, currentTime > a + 0.3 {
            loopB = currentTime
            flash("repeat.circle.fill", "Повтор A–B")
            seek(to: a)
        }
    }

    func clearLoop() {
        loopA = nil
        loopB = nil
        flash("xmark.circle", "Повтор выключен")
    }

    func cycleRepeat() {
        let all = RepeatMode.allCases
        repeatMode = all[(all.firstIndex(of: repeatMode)! + 1) % all.count]
        flash(repeatMode.symbol, ["off": "Без повтора", "one": "Повтор файла", "all": "Повтор очереди"][repeatMode.rawValue] ?? "")
    }

    // MARK: - Дорожки

    func selectAudio(_ index: Int) {
        guard let group = audioGroup, group.options.indices.contains(index), let item = player.currentItem else { return }
        item.select(group.options[index], in: group)
        selectedAudio = index
        flash("waveform", audioOptions[safe: index] ?? "Дорожка \(index + 1)")
    }

    func cycleAudio() {
        guard audioOptions.count > 1 else { return }
        selectAudio((selectedAudio + 1) % audioOptions.count)
    }

    private func rebuildSubtitleOptions() {
        var options = [SubtitleOption(choice: .off, title: "Выключены")]
        for (i, option) in (legibleGroup?.options ?? []).enumerated() {
            options.append(SubtitleOption(choice: .embedded(i), title: option.displayName))
        }
        for (i, track) in externalSubtitles.enumerated() {
            options.append(SubtitleOption(choice: .external(i), title: track.name))
        }
        subtitleOptions = options
    }

    func selectSubtitle(_ choice: SubtitleChoice) {
        subtitleChoice = choice
        subtitleText = nil
        if let group = legibleGroup, let item = player.currentItem {
            if case .embedded(let i) = choice, group.options.indices.contains(i) {
                item.select(group.options[i], in: group)
            } else if group.allowsEmptySelection {
                item.select(nil, in: group)
            }
        }
        updateExternalSubtitle()
        let title = subtitleOptions.first { $0.choice == choice }?.title ?? ""
        flash(choice == .off ? "captions.bubble" : "captions.bubble.fill", choice == .off ? "Субтитры выключены" : title)
    }

    func cycleSubtitles() {
        guard subtitleOptions.count > 1 else { return }
        let index = subtitleOptions.firstIndex { $0.choice == subtitleChoice } ?? 0
        selectSubtitle(subtitleOptions[(index + 1) % subtitleOptions.count].choice)
    }

    func addSubtitleFile(_ url: URL) {
        do {
            let track = try SubtitleParser.load(url)
            externalSubtitles.append(track)
            rebuildSubtitleOptions()
            selectSubtitle(.external(externalSubtitles.count - 1))
        } catch {
            flash("exclamationmark.triangle", "Не удалось прочитать субтитры")
        }
    }

    func shiftSubtitles(by delta: Double) {
        subtitleDelay = (subtitleDelay + delta * 10).rounded() / 10
        flash("timer", "Субтитры \(subtitleDelay >= 0 ? "+" : "")\(String(format: "%.1f", subtitleDelay)) с")
    }

    private func updateExternalSubtitle() {
        guard case .external(let i) = subtitleChoice, externalSubtitles.indices.contains(i) else { return }
        let text = externalSubtitles[i].text(at: currentTime - subtitleDelay)
        if text != subtitleText { subtitleText = text }
    }

    // MARK: - Картинка

    private func adjustmentsReset() {
        compositionActive = false
    }

    private func adjustmentsChanged() {
        adjustmentBox.value = adjustments
        let needsComposition = !adjustments.isIdentity
        if needsComposition != compositionActive {
            rebuildComposition()
        } else if needsComposition, !isPlaying {
            refreshFrame()
        }
    }

    private func rebuildComposition() {
        guard let item = player.currentItem, info.hasVideo else { return }
        guard !adjustments.isIdentity else {
            item.videoComposition = nil
            compositionActive = false
            refreshFrame()
            return
        }
        let box = adjustmentBox
        let composition = AVMutableVideoComposition(asset: item.asset) { request in
            let source = request.sourceImage
            let output = box.apply(to: source.clampedToExtent()).cropped(to: source.extent)
            request.finish(with: output, context: nil)
        }
        item.videoComposition = composition
        compositionActive = true
        refreshFrame()
    }

    private func refreshFrame() {
        guard !isPlaying, player.currentItem != nil else { return }
        player.seek(to: player.currentTime(), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func applyPreset(_ preset: VideoAdjustments.Preset) {
        withAnimation(.smooth) { adjustments = preset.value }
        flash(preset.symbol, preset.name)
    }

    func cycleGravity() {
        let all = VideoGravity.allCases
        gravity = all[(all.firstIndex(of: gravity)! + 1) % all.count]
        zoom = 1
        flash(gravity.symbol, gravity.title)
    }

    func rotate() {
        rotation = (rotation + 90) % 360
        flash("rotate.right", "Поворот \(rotation)°")
    }

    func resetTransform() {
        zoom = 1
        rotation = 0
        mirrored = false
        gravity = .fit
    }

    // MARK: - Картинка в картинке

    func attachPictureInPicture(to layer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        pip = AVPictureInPictureController(playerLayer: layer)
        pip?.publisher(for: \.isPictureInPictureActive)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] active in self?.isPictureInPictureActive = active }
            .store(in: &playerObservers)
    }

    func togglePictureInPicture() {
        guard let pip else {
            flash("pip", "Картинка в картинке недоступна")
            return
        }
        if pip.isPictureInPictureActive { pip.stopPictureInPicture() } else { pip.startPictureInPicture() }
    }

    // MARK: - Снимок кадра

    func captureFrame() {
        guard let source, let current, info.hasVideo else { return }
        let time = player.currentTime()
        let box = adjustments.isIdentity ? nil : adjustmentBox
        let stamp = TimeFormat.string(time.seconds).replacingOccurrences(of: ":", with: ".")
        let folder = FileManager.default.urls(for: .picturesDirectory, in: .userDomainMask)[0].appendingPathComponent("Lumen", isDirectory: true)
        let url = folder.appendingPathComponent("\(current.title) — \(stamp).png")
        let mediaURL = source.url
        Task { [weak self] in
            guard let image = await Thumbnailer.exactFrame(url: mediaURL, at: time, adjustments: box) else { return }
            let saved = await Task.detached(priority: .userInitiated) { Thumbnailer.writePNG(image, to: url) }.value
            guard let self, saved else { return }
            NSPasteboard.general.clearContents()
            NSPasteboard.general.writeObjects([NSImage(cgImage: image, size: .zero)])
            withAnimation(.bouncy) { self.capture = CapturedFrame(image: NSImage(cgImage: image, size: .zero), url: url) }
            try? await Task.sleep(for: .seconds(4))
            withAnimation(.smooth) { self.capture = nil }
        }
    }

    // MARK: - Служебное

    func flash(_ symbol: String, _ title: String, level: Double? = nil) {
        hud = HUDMessage(symbol: symbol, title: title, level: level)
        hudTask?.cancel()
        hudTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1.1))
            guard !Task.isCancelled else { return }
            withAnimation(.smooth(duration: 0.35)) { self?.hud = nil }
        }
    }

    func dismissResume() { resumedFrom = nil }

    func persistPosition() {
        guard let current, duration > 0, phase == .ready else { return }
        let time = player.currentTime().seconds
        guard time.isFinite else { return }
        lastPersist = time
        library?.update(current.id) {
            $0.position = time
            $0.duration = self.duration
        }
    }

    private func updateNowPlaying() {
        guard let current else { return }
        nowPlaying.update(title: current.title, duration: duration, elapsed: currentTime,
                          rate: isPlaying ? rate : 0, artwork: artwork)
    }

    private func captureArtwork(for item: MediaItem, url: URL) {
        let grabber = FrameGrabber(url: url, maxSize: CGSize(width: 720, height: 405), tolerance: 3)
        let at = info.duration > 0 ? min(info.duration * 0.12, 600) : 1
        let needsThumbnail = library?.thumbnails[item.id] == nil
        Task { [weak self] in
            guard let image = await grabber.image(at: at), let self else { return }
            self.artwork = image
            if needsThumbnail { self.library?.setThumbnail(image, for: item.id) }
            self.updateNowPlaying()
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}

nonisolated enum TimeFormat {
    static func string(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds.rounded(.down))
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "—" }
        let total = Int(seconds)
        let h = total / 3600, m = (total % 3600) / 60
        return h > 0 ? "\(h) ч \(m) мин" : "\(max(m, 1)) мин"
    }
}
