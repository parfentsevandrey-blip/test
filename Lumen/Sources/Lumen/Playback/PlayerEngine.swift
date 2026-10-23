import AVFoundation
import AVKit
import AppKit
import Combine
import Observation
import SwiftUI

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
    var detail = ""
    var id: SubtitleChoice { choice }
}

/// Субтитры-картинки (Blu-ray), готовые к показу поверх кадра.
struct SubtitleBitmap: Equatable {
    struct Piece {
        var rect: CGRect
        var image: CGImage
    }

    let start: Double
    let canvas: CGSize
    let pieces: [Piece]

    static func == (a: SubtitleBitmap, b: SubtitleBitmap) -> Bool { a.start == b.start && a.canvas == b.canvas }

    init?(_ pgs: PGSImage) {
        start = pgs.start
        canvas = CGSize(width: pgs.canvasWidth, height: pgs.canvasHeight)
        pieces = pgs.objects.compactMap { object in
            guard object.width > 0, object.height > 0, object.rgba.count >= object.width * object.height * 4,
                  let provider = CGDataProvider(data: Data(object.rgba) as CFData),
                  let image = CGImage(width: object.width, height: object.height, bitsPerComponent: 8, bitsPerPixel: 32,
                                      bytesPerRow: object.width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                                      provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
            else { return nil }
            return Piece(rect: CGRect(x: object.x, y: object.y, width: object.width, height: object.height), image: image)
        }
        guard canvas.width > 0, canvas.height > 0, !pieces.isEmpty else { return nil }
    }
}

/// Состояние плеера. Само воспроизведение — в `PlaybackCore`: AVPlayer для того, что macOS открывает сама,
/// и собственный потоковый движок для Matroska. Здесь — время, дорожки, субтитры и всё, что видит интерфейс.
@Observable
final class PlayerEngine {
    enum Phase: Equatable {
        case idle
        case opening
        case ready
        case failed(String)
    }

    static let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]

    let ambient = AmbientSampler()

    private(set) var phase: Phase = .idle
    private(set) var core: PlaybackCore?
    private(set) var url: URL?
    private(set) var info = MediaInfo()
    private(set) var currentTime: Double = 0
    private(set) var duration: Double = 0
    private(set) var isPlaying = false
    private(set) var rate: Float = 1
    private(set) var chapters: [ChapterMark] = []

    var volume: Float = 1 {
        didSet {
            core?.setVolume(volume, muted: isMuted)
            UserDefaults.standard.set(Double(volume), forKey: Prefs.volume)
        }
    }
    var isMuted = false { didSet { core?.setVolume(volume, muted: isMuted) } }

    // Дорожки
    private(set) var audioOptions: [AudioOption] = []
    private(set) var selectedAudio = 0
    private(set) var subtitleOptions: [SubtitleOption] = []
    private(set) var subtitleChoice: SubtitleChoice = .off
    private(set) var subtitleText: String?
    private(set) var subtitleImage: SubtitleBitmap?
    var subtitleDelay: Double = 0

    // Картинка
    var gravity: VideoGravity = .fit { didSet { core?.setGravity(gravity.avGravity) } }
    var zoom: CGFloat = 1
    var rotation = 0
    var mirrored = false

    // Несколько брошенных файлов играют по порядку.
    private(set) var playlist: [URL] = []
    private(set) var playlistIndex = 0

    // Уведомления интерфейса
    private(set) var hud: HUDMessage?
    private(set) var resumedFrom: Double?
    private(set) var capture: CapturedFrame?
    private(set) var isPictureInPictureActive = false
    var isScrubbing = false

    /// Вызывается, когда известен размер кадра (для подгонки окна).
    @ObservationIgnored var onVideoReady: ((CGSize) -> Void)?

    @ObservationIgnored private let nowPlaying = NowPlayingBridge()
    @ObservationIgnored private var externalSubtitles: [SubtitleTrackData] = []
    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var tickTask: Task<Void, Never>?
    @ObservationIgnored private var hudTask: Task<Void, Never>?
    @ObservationIgnored private var pip: AVPictureInPictureController?
    @ObservationIgnored private var pipObserver: AnyCancellable?
    @ObservationIgnored private var seekTarget: Double?
    @ObservationIgnored private var seekPrecise = true
    @ObservationIgnored private var seekInFlight = false
    @ObservationIgnored private var lastPersist: Double = 0

    init() {
        let stored = UserDefaults.standard.double(forKey: Prefs.volume)
        volume = Float(stored == 0 ? 1 : stored)
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

    var hasMedia: Bool { url != nil }
    var hasPlaylist: Bool { playlist.count > 1 }
    var hasNext: Bool { playlistIndex + 1 < playlist.count }
    var hasPrevious: Bool { playlistIndex > 0 }
    var displayTitle: String { url?.deletingPathExtension().lastPathComponent ?? "Lumen" }

    /// Точное время прямо из движка — для шкалы, которая перерисовывается в такт экрану.
    var liveTime: Double {
        if isScrubbing || seekTarget != nil { return currentTime }
        return core?.currentTime ?? currentTime
    }

    func open(_ urls: [URL]) {
        guard let first = urls.first else { return }
        playlist = urls
        playlistIndex = 0
        load(first)
    }

    func next() {
        guard hasNext else { return }
        playlistIndex += 1
        load(playlist[playlistIndex])
    }

    func previous() {
        if currentTime > 3 || !hasPrevious {
            seek(to: 0)
        } else {
            playlistIndex -= 1
            load(playlist[playlistIndex])
        }
    }

    func close() {
        persistPosition()
        loadTask?.cancel()
        teardown()
        url = nil
        playlist = []
        phase = .idle
        nowPlaying.clear()
    }

    func retry() {
        if let url { load(url) }
    }

    private func load(_ url: URL) {
        persistPosition()
        loadTask?.cancel()
        teardown()
        self.url = url
        currentTime = 0
        duration = 0
        info = MediaInfo()
        chapters = []
        resumedFrom = nil
        subtitleDelay = 0
        zoom = 1
        rotation = 0
        mirrored = false
        phase = .opening
        loadTask = Task { [weak self] in await self?.prepare(url) }
    }

    private func prepare(_ url: URL) async {
        do {
            let core: PlaybackCore
            if MediaResolver.isMatroska(url) {
                core = try await MatroskaCore.open(url)
            } else {
                core = try await AVPlayerCore.open(url)
            }
            let sidecars = SubtitleParser.sidecars(for: url)
            let loaded = await Task.detached(priority: .userInitiated) {
                sidecars.compactMap { try? SubtitleParser.load($0) }.filter { !$0.cues.isEmpty }
            }.value
            guard !Task.isCancelled, self.url == url else {
                core.teardown()
                return
            }
            start(core, url: url, sidecars: loaded)
        } catch is CancellationError {
        } catch {
            guard self.url == url else { return }
            phase = .failed(error.localizedDescription)
        }
    }

    private func start(_ core: PlaybackCore, url: URL, sidecars: [SubtitleTrackData]) {
        self.core = core
        info = core.info
        duration = core.info.duration
        chapters = core.chapters
        audioOptions = core.audioOptions
        selectedAudio = core.selectedAudio
        externalSubtitles = sidecars
        rebuildSubtitleOptions()
        core.setVolume(volume, muted: isMuted)
        core.setGravity(gravity.avGravity)

        pip = core.makePictureInPicture()
        pipObserver = pip?.publisher(for: \.isPictureInPictureActive)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] active in self?.isPictureInPictureActive = active }

        if UserDefaults.standard.bool(forKey: Prefs.autoSubtitles),
           let first = subtitleOptions.first(where: { $0.choice != .off }) {
            selectSubtitle(first.choice, announce: false)
        }

        // Продолжение просмотра
        var startAt = 0.0
        let saved = ResumeStore.position(for: url)
        if UserDefaults.standard.bool(forKey: Prefs.resumePlayback), saved > 15, duration > 0, saved < duration * 0.95 {
            startAt = saved
            currentTime = saved
            resumedFrom = saved
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(6))
                self?.resumedFrom = nil
            }
        }

        ambient.source = { [weak core] in core?.ambientFrame() }
        if info.hasVideo { onVideoReady?(info.displaySize) }
        seekInFlight = true
        core.seek(to: startAt, precise: true) { [weak self, weak core] in
            guard let self, let core, self.core === core else { return }
            self.seekInFlight = false
            self.phase = .ready
            core.play(rate: self.rate)
            self.isPlaying = true
            self.ambient.start()
            self.updateNowPlaying()
        }
        startTicking()
    }

    private func teardown() {
        tickTask?.cancel()
        tickTask = nil
        core?.teardown()
        core = nil
        pip = nil
        pipObserver = nil
        isPictureInPictureActive = false
        externalSubtitles = []
        subtitleOptions = []
        subtitleChoice = .off
        subtitleText = nil
        subtitleImage = nil
        audioOptions = []
        selectedAudio = 0
        seekTarget = nil
        seekInFlight = false
        isPlaying = false
        ambient.stop()
        ambient.source = nil
        ambient.reset()
    }

    // MARK: - Такт

    private func startTicking() {
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                self?.tick()
                try? await Task.sleep(for: .milliseconds(50))
            }
        }
    }

    private func tick() {
        guard let core, phase == .ready || phase == .opening else { return }
        if let failure = core.failure {
            phase = .failed(failure)
            return
        }
        if core.pollEnd() {
            itemDidEnd()
            return
        }
        let playing = core.isPlaying
        if playing != isPlaying, !seekInFlight {
            isPlaying = playing
            playing ? ambient.start() : ambient.stop()
            updateNowPlaying()
            if !playing { persistPosition() }
        }
        guard !isScrubbing, seekTarget == nil, !seekInFlight else { return }
        let time = core.currentTime
        // Подписи времени обновляются четыре раза в секунду — чаще им незачем, а интерфейс не пересчитывается зря.
        if (time * 4).rounded(.down) != (currentTime * 4).rounded(.down) { currentTime = time }
        updateSubtitles(at: time)
        if isPlaying, abs(time - lastPersist) > 5 { persistPosition() }
    }

    private func itemDidEnd() {
        guard let url else { return }
        ResumeStore.forget(url)
        isPlaying = false
        currentTime = duration
        ambient.stop()
        if UserDefaults.standard.bool(forKey: Prefs.autoplayNext), hasNext {
            next()
        }
    }

    // MARK: - Управление

    func play() {
        guard let core, phase == .ready else { return }
        if duration > 0, currentTime >= duration - 0.3 { seek(to: 0) }
        core.play(rate: rate)
        isPlaying = true
        ambient.start()
        updateNowPlaying()
    }

    func pause() {
        core?.pause()
        isPlaying = false
        ambient.stop()
        persistPosition()
        updateNowPlaying()
    }

    func togglePlay() {
        guard phase == .ready else { return }
        let willPlay = !isPlaying
        willPlay ? play() : pause()
        flash(willPlay ? "play.fill" : "pause.fill", willPlay ? "Воспроизведение" : "Пауза")
    }

    func seek(to seconds: Double, precise: Bool = true) {
        guard core != nil, phase == .ready else { return }
        let clamped = max(0, min(seconds, max(duration - 0.05, 0)))
        currentTime = clamped
        seekTarget = clamped
        seekPrecise = precise
        if !seekInFlight { performSeek() }
        updateSubtitles(at: clamped)
    }

    /// «Догоняющая» перемотка: при скраббинге не копим сотню запросов,
    /// а после завершения текущего сразу переходим к последней цели.
    private func performSeek() {
        guard let core, let target = seekTarget else { return }
        seekInFlight = true
        seekTarget = nil
        core.seek(to: target, precise: seekPrecise) { [weak self, weak core] in
            guard let self, let core, self.core === core else { return }
            self.seekInFlight = false
            if self.seekTarget != nil {
                self.performSeek()
            } else {
                if !self.isPlaying { self.ambient.refresh() }
                self.updateNowPlaying()
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
        guard let core, phase == .ready else { return }
        isPlaying = false
        core.step(count)
        Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(80))
            guard let self, let core = self.core else { return }
            self.currentTime = core.currentTime
            self.updateSubtitles(at: self.currentTime)
            self.ambient.refresh()
        }
        flash(count > 0 ? "forward.frame.fill" : "backward.frame.fill", count > 0 ? "Кадр вперёд" : "Кадр назад")
    }

    func setRate(_ value: Float) {
        rate = value
        core?.setRate(value)
        flash("gauge.with.dots.needle.67percent", Self.rateLabel(value))
        updateNowPlaying()
    }

    func changeRate(by step: Int) {
        let index = Self.speeds.firstIndex(of: rate) ?? Self.speeds.firstIndex(of: 1)!
        let next = min(max(index + step, 0), Self.speeds.count - 1)
        setRate(Self.speeds[next])
    }

    static func rateLabel(_ r: Float) -> String {
        r.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(r))×" : "\(String(format: "%g", r))×"
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

    // MARK: - Дорожки

    func selectAudio(_ index: Int) {
        guard let core, audioOptions.indices.contains(index), audioOptions[index].available else { return }
        core.selectAudio(index)
        selectedAudio = core.selectedAudio
        info = core.info
        flash("waveform", audioOptions[index].title)
    }

    func cycleAudio() {
        let playable = audioOptions.filter(\.available).map(\.id)
        guard playable.count > 1 else { return }
        let position = playable.firstIndex(of: selectedAudio) ?? 0
        selectAudio(playable[(position + 1) % playable.count])
    }

    private func rebuildSubtitleOptions() {
        var options = [SubtitleOption(choice: .off, title: "Выключены")]
        for subtitle in core?.subtitles ?? [] {
            options.append(SubtitleOption(choice: .embedded(subtitle.id), title: subtitle.title,
                                          detail: subtitle.kind == .image ? "Blu-ray" : ""))
        }
        for (i, track) in externalSubtitles.enumerated() {
            options.append(SubtitleOption(choice: .external(i), title: track.name, detail: "Файл"))
        }
        subtitleOptions = options
    }

    func selectSubtitle(_ choice: SubtitleChoice) {
        selectSubtitle(choice, announce: true)
    }

    private func selectSubtitle(_ choice: SubtitleChoice, announce: Bool) {
        subtitleChoice = choice
        subtitleText = nil
        subtitleImage = nil
        if case .embedded(let id) = choice { core?.selectSubtitle(id) } else { core?.selectSubtitle(nil) }
        updateSubtitles(at: currentTime)
        guard announce else { return }
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
        updateSubtitles(at: currentTime)
        flash("timer", "Субтитры \(subtitleDelay >= 0 ? "+" : "")\(String(format: "%.1f", subtitleDelay)) с")
    }

    private func updateSubtitles(at time: Double) {
        let t = time - subtitleDelay
        var text: String?
        var image: PGSImage?
        switch subtitleChoice {
        case .off: break
        case .embedded:
            text = core?.subtitleText(at: t)
            image = core?.subtitleImage(at: t)
        case .external(let i):
            if externalSubtitles.indices.contains(i) { text = externalSubtitles[i].text(at: t) }
        }
        if text != subtitleText { subtitleText = text }
        if let image {
            if subtitleImage?.start != image.start { subtitleImage = SubtitleBitmap(image) }
        } else if subtitleImage != nil {
            subtitleImage = nil
        }
    }

    // MARK: - Картинка

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

    func togglePictureInPicture() {
        guard let pip else {
            flash("pip", "Картинка в картинке недоступна")
            return
        }
        if pip.isPictureInPictureActive { pip.stopPictureInPicture() } else { pip.startPictureInPicture() }
    }

    // MARK: - Снимок кадра

    func captureFrame() {
        guard let core, info.hasVideo else { return }
        let stamp = TimeFormat.string(currentTime).replacingOccurrences(of: ":", with: ".")
        let folder = FileManager.default.urls(for: .picturesDirectory, in: .userDomainMask)[0].appendingPathComponent("Lumen", isDirectory: true)
        let file = folder.appendingPathComponent("\(displayTitle) — \(stamp).png")
        Task { [weak self] in
            guard let image = await core.snapshot() else { return }
            let saved = await Task.detached(priority: .userInitiated) { Thumbnailer.writePNG(image, to: file) }.value
            guard let self, saved else { return }
            let picture = NSImage(cgImage: image, size: .zero)
            NSPasteboard.general.clearContents()
            NSPasteboard.general.writeObjects([picture])
            withAnimation(.bouncy) { self.capture = CapturedFrame(image: picture, url: file) }
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
        guard let url, let core, duration > 0, phase == .ready else { return }
        let time = core.currentTime
        lastPersist = time
        ResumeStore.save(time, duration: duration, for: url)
    }

    private func updateNowPlaying() {
        guard url != nil else { return }
        nowPlaying.update(title: displayTitle, duration: duration, elapsed: liveTime,
                          rate: isPlaying ? rate : 0, artwork: nil)
    }
}

/// Где остановились в каждом файле. Только позиции — без библиотеки и истории.
nonisolated enum ResumeStore {
    private static let key = "resumePositions"
    private static let limit = 300

    static func position(for url: URL) -> Double {
        let all = UserDefaults.standard.dictionary(forKey: key) as? [String: [Double]] ?? [:]
        return all[url.standardizedFileURL.path]?.first ?? 0
    }

    static func save(_ time: Double, duration: Double, for url: URL) {
        guard time.isFinite else { return }
        var all = UserDefaults.standard.dictionary(forKey: key) as? [String: [Double]] ?? [:]
        let path = url.standardizedFileURL.path
        if time < 15 || time > duration * 0.95 {
            all[path] = nil
        } else {
            all[path] = [time, Date().timeIntervalSince1970]
        }
        if all.count > limit {
            let oldest = all.sorted { ($0.value.last ?? 0) < ($1.value.last ?? 0) }.prefix(all.count - limit)
            for (k, _) in oldest { all[k] = nil }
        }
        UserDefaults.standard.set(all, forKey: key)
    }

    static func forget(_ url: URL) {
        var all = UserDefaults.standard.dictionary(forKey: key) as? [String: [Double]] ?? [:]
        all[url.standardizedFileURL.path] = nil
        UserDefaults.standard.set(all, forKey: key)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
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
