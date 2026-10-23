import AVFoundation
import AVKit
import AppKit
import CoreImage
import CoreMedia
import QuartzCore
import VideoToolbox

/// Matroska и WebM без перепаковки: файл читается потоком прямо во время просмотра, кадры уходят в
/// системный слой видео (он же декодирует их аппаратно), звук — в системный рендерер, часы у них общие.
/// Открытие мгновенное при любом размере файла; перемотка — по индексу Cues к ближайшему ключевому кадру.
@MainActor
final class MatroskaCore: PlaybackCore {
    let videoLayer: CALayer
    private(set) var info = MediaInfo()
    private(set) var chapters: [ChapterMark] = []
    private(set) var audioOptions: [AudioOption] = []
    private(set) var selectedAudio = 0
    private(set) var subtitles: [EmbeddedSubtitle] = []
    private(set) var isPlaying = false
    private(set) var hasEnded = false
    var failure: String? { nil }

    private let pipeline: MKVPipeline
    private let display: AVSampleBufferDisplayLayer
    private var rate: Float = 1
    private var audioTracks: [MKVTrack] = []
    private var subtitleTracks: [(track: MKVTrack, kind: EmbeddedSubtitle.Kind)] = []
    private var textStores: [UInt64: SubtitleTrackData] = [:]
    private var imageStores: [UInt64: PGSStore] = [:]
    private var selectedSubtitle: Int?
    private var pipDelegate: PiPDelegate?
    private var startTask: Task<Void, Never>?
    private let ciContext = CIContext(options: [.cacheIntermediates: false])
    private var ambientBuffer: CVPixelBuffer?

    /// Разбор заголовков файла уходит с главного потока; сам он ленивый и занимает миллисекунды.
    static func open(_ url: URL) async throws -> MatroskaCore {
        let mkv = try await Task.detached(priority: .userInitiated) { try MatroskaFile(url: url) }.value
        return try MatroskaCore(mkv: mkv, url: url)
    }

    private init(mkv: MatroskaFile, url: URL) throws {
        let display = AVSampleBufferDisplayLayer()
        display.videoGravity = .resizeAspect
        display.backgroundColor = NSColor.black.cgColor
        display.preventsDisplaySleepDuringVideoPlayback = true
        self.display = display
        videoLayer = display
        pipeline = try MKVPipeline(mkv: mkv, video: display.sampleBufferRenderer)

        info = MKVInspector.info(mkv, url: url, video: pipeline.videoTrack)
        chapters = mkv.chapters.map { ChapterMark(title: $0.title, start: $0.start) }

        audioTracks = mkv.tracks.filter { $0.kind == .audio && $0.isEnabled }
        audioOptions = audioTracks.enumerated().map { i, t in
            AudioOption(id: i, title: MKVInspector.title(t), detail: MKVInspector.audioDetail(t), available: AudioSources.isPlayable(t))
        }
        // Звук по умолчанию: дорожка с флагом «по умолчанию», которую можно воспроизвести, иначе первая воспроизводимая.
        let preferred = audioTracks.indices.first { audioTracks[$0].isDefault && audioOptions[$0].available }
            ?? audioTracks.indices.first { audioOptions[$0].available }
        selectedAudio = preferred ?? 0
        if let preferred { info.audioCodec = Codecs.name(forMatroska: audioTracks[preferred].codecID) }

        for t in mkv.tracks where t.kind == .subtitle {
            if Codecs.isTextSubtitle(t.codecID) {
                subtitleTracks.append((t, .text))
                textStores[t.number] = SubtitleTrackData(name: MKVInspector.title(t), language: t.languageBCP47 ?? t.language, cues: [])
            } else if t.codecID == "S_HDMV/PGS" {
                subtitleTracks.append((t, .image))
                imageStores[t.number] = PGSStore()
            }
            // Прочие графические форматы (VobSub) не показываем и не жалуемся на них.
        }
        subtitles = subtitleTracks.enumerated().map { i, s in
            EmbeddedSubtitle(id: i, title: MKVInspector.title(s.track) + (s.track.isForced ? " (форсированные)" : ""), kind: s.kind)
        }

        pipeline.onSubtitle = { [weak self] number, start, duration, data in
            Task { @MainActor in self?.received(subtitle: number, start: start, duration: duration, data: data) }
        }
        pipeline.onAudioRejected = { [weak self] in
            Task { @MainActor in self?.audioRejected() }
        }
        if let preferred { pipeline.selectAudio(audioTracks[preferred]) }
    }

    // MARK: - Время

    var currentTime: Double {
        let t = pipeline.synchronizer.currentTime().seconds
        return t.isFinite ? max(0, t) : 0
    }

    func play(rate: Float) {
        self.rate = rate
        if hasEnded || (info.duration > 0 && currentTime >= info.duration - 0.2) {
            restart(at: 0, precise: true, resume: true, completion: {})
            return
        }
        isPlaying = true
        pipeline.synchronizer.setRate(rate, time: pipeline.synchronizer.currentTime())
    }

    func pause() {
        isPlaying = false
        pipeline.synchronizer.setRate(0, time: pipeline.synchronizer.currentTime())
    }

    func setRate(_ rate: Float) {
        self.rate = rate
        if isPlaying { pipeline.synchronizer.rate = rate }
    }

    func seek(to seconds: Double, precise: Bool, completion: @escaping @MainActor () -> Void) {
        restart(at: seconds, precise: precise, resume: isPlaying, completion: completion)
    }

    /// Перемотка: сбросить рендереры, читать файл с кластера перед целью, кадры до цели декодировать, но не показывать.
    private func restart(at seconds: Double, precise: Bool, resume: Bool, completion: @escaping @MainActor () -> Void) {
        let target = max(0, info.duration > 0 ? min(seconds, info.duration - 0.05) : seconds)
        hasEnded = false
        startTask?.cancel()
        let time = CMTime(seconds: target, preferredTimescale: 90000)
        pipeline.synchronizer.setRate(0, time: time)
        pipeline.restart(at: target, precise: precise)
        startTask = Task { [weak self] in
            // Ждём, пока у звука накопится запас (или до 1,5 с), — тогда старт без рывка.
            for _ in 0..<75 {
                guard let self, !Task.isCancelled else { return }
                if self.pipeline.readyToStart { break }
                try? await Task.sleep(for: .milliseconds(20))
            }
            guard let self, !Task.isCancelled else { return }
            if resume {
                self.isPlaying = true
                self.pipeline.synchronizer.setRate(self.rate, time: time)
            }
            completion()
        }
    }

    func step(_ frames: Int) {
        pause()
        let duration = pipeline.frameDuration.seconds
        let target = currentTime + Double(frames) * (duration > 0 ? duration : 1.0 / 24)
        if frames > 0 {
            // Вперёд: кадры уже в очереди слоя — достаточно передвинуть часы.
            pipeline.synchronizer.setRate(0, time: CMTime(seconds: target, preferredTimescale: 90000))
        } else {
            restart(at: target, precise: true, resume: false, completion: {})
        }
    }

    /// Вызывается движком по таймеру: конец файла.
    func pollEnd() -> Bool {
        if !hasEnded, isPlaying, pipeline.finished, info.duration <= 0 || currentTime >= info.duration - 0.25 {
            hasEnded = true
            isPlaying = false
            pipeline.synchronizer.setRate(0, time: pipeline.synchronizer.currentTime())
            return true
        }
        return false
    }

    func setVolume(_ volume: Float, muted: Bool) {
        pipeline.audio.volume = volume
        pipeline.audio.isMuted = muted
    }

    // MARK: - Дорожки

    func selectAudio(_ index: Int) {
        guard audioTracks.indices.contains(index), audioOptions[index].available else { return }
        selectedAudio = index
        info.audioCodec = Codecs.name(forMatroska: audioTracks[index].codecID)
        info.audioChannels = audioTracks[index].channels
        info.sampleRate = audioTracks[index].samplingFrequency
        pipeline.selectAudio(audioTracks[index])
        // Новая дорожка звучит с того же места: перечитываем файл с текущей позиции.
        restart(at: currentTime, precise: true, resume: isPlaying, completion: {})
    }

    private func audioRejected() {
        // Рендерер не принял сжатый звук — декодируем его сами и продолжаем с того же места.
        guard audioTracks.indices.contains(selectedAudio) else { return }
        pipeline.selectAudio(audioTracks[selectedAudio], decode: true)
        restart(at: currentTime, precise: true, resume: isPlaying, completion: {})
    }

    func selectSubtitle(_ id: Int?) {
        selectedSubtitle = id
    }

    private func received(subtitle number: UInt64, start: Double, duration: Double?, data: [UInt8]) {
        if imageStores[number] != nil {
            imageStores[number]?.add(time: start, data: data)
            return
        }
        guard var store = textStores[number], let track = subtitleTracks.first(where: { $0.track.number == number })?.track else { return }
        var text = SubtitleParser.decode(Data(data))
        if track.codecID.hasSuffix("ASS") || track.codecID.hasSuffix("SSA") {
            let fields = text.split(separator: ",", maxSplits: 8, omittingEmptySubsequences: false)
            text = fields.count == 9 ? SubtitleParser.cleanASS(String(fields[8])) : SubtitleParser.cleanASS(text)
        } else {
            text = SubtitleParser.stripTags(text).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard !text.isEmpty else { return }
        store.insert(SubtitleCue(start: start, end: start + (duration ?? 5), text: text))
        textStores[number] = store
    }

    func subtitleText(at time: Double) -> String? {
        guard let id = selectedSubtitle, subtitleTracks.indices.contains(id), subtitleTracks[id].kind == .text else { return nil }
        return textStores[subtitleTracks[id].track.number]?.text(at: time)
    }

    func subtitleImage(at time: Double) -> PGSImage? {
        guard let id = selectedSubtitle, subtitleTracks.indices.contains(id), subtitleTracks[id].kind == .image else { return nil }
        return imageStores[subtitleTracks[id].track.number]?.image(at: time)
    }

    // MARK: - Картинка

    func ambientFrame() -> CVPixelBuffer? {
        guard let frame = display.sampleBufferRenderer.copyDisplayedPixelBuffer() else { return nil }
        if ambientBuffer == nil {
            CVPixelBufferCreate(kCFAllocatorDefault, 64, 36, kCVPixelFormatType_32BGRA,
                                [kCVPixelBufferIOSurfacePropertiesKey: [:]] as CFDictionary, &ambientBuffer)
        }
        guard let target = ambientBuffer else { return nil }
        let image = CIImage(cvPixelBuffer: frame)
        let scaled = image.transformed(by: CGAffineTransform(scaleX: 64 / image.extent.width, y: 36 / image.extent.height))
        ciContext.render(scaled, to: target)
        return target
    }

    func snapshot() async -> CGImage? {
        guard let frame = display.sampleBufferRenderer.copyDisplayedPixelBuffer() else { return nil }
        let image = CIImage(cvPixelBuffer: frame)
        return ciContext.createCGImage(image, from: image.extent)
    }

    func makePictureInPicture() -> AVPictureInPictureController? {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return nil }
        let delegate = PiPDelegate(core: self)
        pipDelegate = delegate
        let source = AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer: display, playbackDelegate: delegate)
        return AVPictureInPictureController(contentSource: source)
    }

    func setGravity(_ gravity: AVLayerVideoGravity) {
        display.videoGravity = gravity
    }

    func teardown() {
        startTask?.cancel()
        pipeline.stop()
    }
}

// MARK: - Конвейер чтения файла

/// Всё изменяемое состояние живёт на собственной очереди: и подкачка кадров, и их разбор.
nonisolated final class MKVPipeline: @unchecked Sendable {
    let mkv: MatroskaFile
    let queue = DispatchQueue(label: "app.lumen.matroska", qos: .userInteractive)
    let audio = AVSampleBufferAudioRenderer()
    let synchronizer = AVSampleBufferRenderSynchronizer()
    let videoTrack: MKVTrack?
    let frameDuration: CMTime

    var onSubtitle: (@Sendable (UInt64, Double, Double?, [UInt8]) -> Void)?
    var onAudioRejected: (@Sendable () -> Void)?

    private let video: AVSampleBufferVideoRenderer
    private let videoFormat: CMVideoFormatDescription?
    private let vp9: VP9Decoder?
    private let subtitleTracks: Set<UInt64>
    private let lock = NSLock()

    // Только на queue:
    private var reader: MKVBlockReader
    private var videoQueue = SampleQueue()
    private var audioQueue = SampleQueue()
    private var audioTrack: MKVTrack?
    private var audioSource: AudioPacketSource?
    private var passthrough = false
    private var hideBefore: Int64 = .min
    private var waitingForKey = true
    private var generation = 0
    private var clusterIndex: [MKVClusterIndex.Entry]?
    private var rejectionReported = false
    // Читаются и с главного потока — под замком.
    private var _eof = false
    private var _audioBuffered = 0
    private var _videoBuffered = 0

    var finished: Bool { lock.withLock { _eof } }

    /// Звука (или видео, если звука нет) накопилось достаточно, чтобы стартовать без рывка.
    var readyToStart: Bool {
        if audioTrackSelected { return audio.hasSufficientMediaDataForReliablePlaybackStart || lock.withLock { _eof } }
        return lock.withLock { _videoBuffered > 6 || _eof }
    }

    private var audioTrackSelected: Bool { lock.withLock { _audioSelected } }
    private var _audioSelected = false

    init(mkv: MatroskaFile, video renderer: AVSampleBufferVideoRenderer) throws {
        self.mkv = mkv
        self.video = renderer
        guard let start = mkv.firstCluster else { throw MatroskaError("В файле нет кадров") }
        reader = MKVBlockReader(mkv: mkv, startingAt: start)

        var video: MKVTrack?
        var format: CMVideoFormatDescription?
        var vp9: VP9Decoder?
        for t in mkv.tracks where t.kind == .video && t.isEnabled {
            if let fd = FormatFactory.video(for: t) {
                video = t; format = fd; break
            }
            if t.codecID == "V_VP9" {
                let first = Self.firstFrame(of: t, in: mkv)
                if let decoder = try? VP9Decoder(track: t, firstFrame: first) {
                    video = t; format = decoder.format; vp9 = decoder; break
                }
            }
        }
        videoTrack = video
        videoFormat = format
        self.vp9 = vp9
        if let d = video?.defaultDuration, d > 0 {
            frameDuration = CMTime(value: Int64(d), timescale: 1_000_000_000)
        } else {
            frameDuration = CMTime(value: 1001, timescale: 24000)
        }
        subtitleTracks = Set(mkv.tracks.filter { $0.kind == .subtitle && (Codecs.isTextSubtitle($0.codecID) || $0.codecID == "S_HDMV/PGS") }.map(\.number))
        if mkv.tracks.contains(where: { $0.kind == .video }) && video == nil && mkv.tracks.allSatisfy({ $0.kind != .audio }) {
            throw MatroskaError("Видео \(Codecs.name(forMatroska: mkv.tracks.first { $0.kind == .video }?.codecID ?? "")) не поддерживается")
        }

        audio.audioTimePitchAlgorithm = .spectral
        if video != nil { synchronizer.addRenderer(self.video) }
        synchronizer.addRenderer(audio)
        synchronizer.delaysRateChangeUntilHasSufficientMediaData = false
    }

    // MARK: Управление (вызывается с главного потока)

    func selectAudio(_ track: MKVTrack, decode: Bool = false) {
        queue.sync {
            audioTrack = track
            let first = Self.firstFrame(of: track, in: mkv).map { [UInt8]($0) }
            audioSource = AudioSources.make(for: track, firstPacket: first, preferDecoding: decode)
            passthrough = audioSource is PassthroughAudioSource
            rejectionReported = false
            lock.withLock { _audioSelected = audioSource != nil }
        }
    }

    func restart(at seconds: Double, precise: Bool) {
        queue.sync {
            generation += 1
            let gen = generation
            self.video.stopRequestingMediaData()
            audio.stopRequestingMediaData()
            self.video.flush(removingDisplayedImage: false, completionHandler: nil)
            audio.flush()
            videoQueue.clear()
            audioQueue.clear()
            vp9?.reset()
            audioSource?.reset()
            lock.withLock { _eof = false; _videoBuffered = 0 }
            let target = Int64(seconds * 1e9)
            hideBefore = precise ? target : .min
            waitingForKey = videoTrack != nil
            reader = MKVBlockReader(mkv: mkv, startingAt: startOffset(for: target))
            if videoTrack != nil {
                self.video.requestMediaDataWhenReady(on: queue) { [weak self] in self?.feedVideo(gen) }
            }
            if audioSource != nil {
                audio.requestMediaDataWhenReady(on: queue) { [weak self] in self?.feedAudio(gen) }
            }
        }
    }

    func stop() {
        queue.sync {
            generation += 1
            self.video.stopRequestingMediaData()
            audio.stopRequestingMediaData()
            synchronizer.setRate(0, time: synchronizer.currentTime())
            audio.flush()
            videoQueue.clear()
            audioQueue.clear()
        }
    }

    // MARK: Чтение (на queue)

    private func startOffset(for target: Int64) -> Int {
        let fallback = mkv.firstCluster ?? 0
        guard target > 0 else { return fallback }
        let anchor = videoTrack?.number ?? audioTrack?.number
        if !mkv.cues.isEmpty, let offset = mkv.clusterOffset(before: target, track: anchor) { return offset }
        // Без индекса: один быстрый проход по заголовкам кластеров, запас 3 с назад до ключевого кадра.
        if clusterIndex == nil { clusterIndex = MKVClusterIndex.build(mkv) }
        guard let index = clusterIndex, !index.isEmpty else { return fallback }
        let goal = target - 3_000_000_000
        var best = index[0].offset
        for entry in index { if entry.time <= goal { best = entry.offset } else { break } }
        return best
    }

    private func feedVideo(_ gen: Int) {
        let renderer = self.video
        while gen == generation, renderer.isReadyForMoreMediaData {
            if let sample = videoQueue.pop() {
                renderer.enqueue(sample)
                lock.withLock { _videoBuffered += 1 }
                continue
            }
            if !pump(until: { !$0.videoQueue.isEmpty }) {
                renderer.stopRequestingMediaData()
                return
            }
        }
    }

    private func feedAudio(_ gen: Int) {
        if audio.status == .failed, passthrough {
            if !rejectionReported {
                rejectionReported = true
                audio.stopRequestingMediaData()
                onAudioRejected?()
            }
            return
        }
        while gen == generation, audio.isReadyForMoreMediaData {
            if let sample = audioQueue.pop() {
                audio.enqueue(sample)
                continue
            }
            if !pump(until: { !$0.audioQueue.isEmpty }) {
                audio.stopRequestingMediaData()
                return
            }
        }
    }

    /// Читает блоки, пока нужная очередь не пополнится. false — файл кончился.
    private func pump(until ready: (MKVPipeline) -> Bool) -> Bool {
        var budget = 3000
        while !ready(self) {
            guard let block = reader.next() else {
                lock.withLock { _eof = true }
                return false
            }
            handle(block)
            budget -= 1
            // Очередь другой дорожки разрослась (дорожки сильно разнесены в файле) — отдаём управление.
            if budget == 0 || videoQueue.count > 900 || audioQueue.count > 4000 { return true }
        }
        return true
    }

    private func handle(_ block: MKVBlockRef) {
        let number = UInt64(block.track)
        if let video = videoTrack, number == video.number {
            handleVideo(block, track: video)
        } else if let track = audioTrack, number == track.number {
            handleAudio(block, track: track)
        } else if subtitleTracks.contains(number) {
            handleSubtitle(block, number: number)
        }
    }

    private func handleVideo(_ block: MKVBlockRef, track: MKVTrack) {
        if waitingForKey {
            guard block.isKey else { return }
            waitingForKey = false
        }
        guard let format = videoFormat else { return }
        for range in mkv.frames(of: block) where !range.isEmpty {
            guard let buffer = try? SampleFactory.blockBuffer(mkv.file, range, prefix: track.strippedHeader) else { continue }
            var timing = CMSampleTimingInfo(duration: frameDuration,
                                            presentationTimeStamp: CMTime(value: block.time, timescale: 1_000_000_000),
                                            decodeTimeStamp: .invalid)
            guard let sample = try? SampleFactory.sample(buffer, format: format, timing: &timing) else { continue }
            if !block.isKey { SampleFactory.markNotSync(sample) }
            let hidden = block.time < hideBefore
            if let vp9 {
                for decoded in vp9.decode(sample) {
                    if hidden { SampleFactory.markDoNotDisplay(decoded) }
                    videoQueue.push(decoded)
                }
            } else {
                if hidden { SampleFactory.markDoNotDisplay(sample) }
                videoQueue.push(sample)
            }
        }
    }

    private func handleAudio(_ block: MKVBlockRef, track: MKVTrack) {
        guard let source = audioSource else { return }
        var packets: [[UInt8]] = []
        for range in mkv.frames(of: block) where !range.isEmpty {
            var packet = track.strippedHeader.map { [UInt8]($0) } ?? []
            packet.append(contentsOf: UnsafeBufferPointer(start: mkv.file.base.advanced(by: range.lowerBound).assumingMemoryBound(to: UInt8.self), count: range.count))
            packets.append(packet)
        }
        guard let samples = try? source.samples(for: packets, time: block.time) else { return }
        for sample in samples {
            let end = CMTimeAdd(CMSampleBufferGetPresentationTimeStamp(sample), CMSampleBufferGetDuration(sample))
            if hideBefore != .min, end.isValid, Int64(end.seconds * 1e9) < hideBefore { continue }
            audioQueue.push(sample)
        }
    }

    private func handleSubtitle(_ block: MKVBlockRef, number: UInt64) {
        guard let track = mkv.track(number: number), let deliver = onSubtitle else { return }
        for range in mkv.frames(of: block) where !range.isEmpty {
            var data = mkv.file.data(range.lowerBound, range.count)
            if track.zlibCompressed, data.count > 2,
               let inflated = try? (data.dropFirst(2) as NSData).decompressed(using: .zlib) as Data {
                data = inflated
            }
            if let prefix = track.strippedHeader { data = prefix + data }
            deliver(number, Double(block.time) / 1e9, block.duration.map { Double($0) / 1e9 }, [UInt8](data))
        }
    }

    static func firstFrame(of track: MKVTrack, in mkv: MatroskaFile) -> Data? {
        guard let start = mkv.firstCluster else { return nil }
        var reader = MKVBlockReader(mkv: mkv, startingAt: start)
        var budget = 20000
        while budget > 0, let block = reader.next() {
            budget -= 1
            guard UInt64(block.track) == track.number, let range = mkv.frames(of: block).first else { continue }
            var data = track.strippedHeader ?? Data()
            data.append(mkv.file.data(range.lowerBound, range.count))
            return data
        }
        return nil
    }
}

/// Очередь сэмплов без копирования при извлечении.
nonisolated struct SampleQueue {
    private var items: [CMSampleBuffer] = []
    private var head = 0

    var isEmpty: Bool { head >= items.count }
    var count: Int { items.count - head }

    mutating func push(_ s: CMSampleBuffer) { items.append(s) }

    mutating func pop() -> CMSampleBuffer? {
        guard head < items.count else { return nil }
        let s = items[head]
        head += 1
        if head > 256, head * 2 > items.count {
            items.removeFirst(head)
            head = 0
        }
        return s
    }

    mutating func clear() {
        items.removeAll(keepingCapacity: true)
        head = 0
    }
}

// MARK: - Картинка в картинке

/// Управление воспроизведением из окна «Картинка в картинке».
final class PiPDelegate: NSObject, AVPictureInPictureSampleBufferPlaybackDelegate {
    weak var core: MatroskaCore?

    init(core: MatroskaCore) { self.core = core }

    nonisolated func pictureInPictureController(_ controller: AVPictureInPictureController, setPlaying playing: Bool) {
        MainActor.assumeIsolated {
            guard let core else { return }
            playing ? core.play(rate: 1) : core.pause()
        }
    }

    nonisolated func pictureInPictureControllerTimeRangeForPlayback(_ controller: AVPictureInPictureController) -> CMTimeRange {
        MainActor.assumeIsolated {
            let duration = core?.info.duration ?? 0
            return CMTimeRange(start: .zero, duration: CMTime(seconds: max(duration, 0.1), preferredTimescale: 600))
        }
    }

    nonisolated func pictureInPictureControllerIsPlaybackPaused(_ controller: AVPictureInPictureController) -> Bool {
        MainActor.assumeIsolated { !(core?.isPlaying ?? false) }
    }

    nonisolated func pictureInPictureController(_ controller: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {}

    nonisolated func pictureInPictureController(_ controller: AVPictureInPictureController, skipByInterval skipInterval: CMTime, completion: @escaping () -> Void) {
        nonisolated(unsafe) let done = completion
        MainActor.assumeIsolated {
            guard let core else { done(); return }
            core.seek(to: core.currentTime + skipInterval.seconds, precise: false) { done() }
        }
    }
}

// MARK: - Сведения о файле

enum MKVInspector {
    static func info(_ mkv: MatroskaFile, url: URL, video: MKVTrack?) -> MediaInfo {
        var info = MediaInfo()
        info.duration = mkv.durationSeconds
        info.container = mkv.docType == "webm" ? "WebM" : "Matroska"
        info.fileSize = Int64(mkv.file.size)
        info.chapters = mkv.chapters.map { ChapterMark(title: $0.title, start: $0.start) }
        if let v = video {
            info.hasVideo = true
            let size = Codecs.displaySize(of: v) ?? CGSize(width: v.pixelWidth, height: v.pixelHeight)
            info.displaySize = size
            info.videoCodec = Codecs.name(forMatroska: v.codecID)
            if let d = v.defaultDuration, d > 0 { info.frameRate = 1e9 / Double(d) }
            switch v.colour?.transfer {
            case 16: info.dynamicRange = "HDR10"
            case 18: info.dynamicRange = "HLG"
            default: break
            }
        }
        if let a = mkv.tracks.first(where: { $0.kind == .audio && AudioSources.isPlayable($0) }) ?? mkv.tracks.first(where: { $0.kind == .audio }) {
            info.audioCodec = Codecs.name(forMatroska: a.codecID)
            info.audioChannels = a.channels
            info.sampleRate = a.outputSamplingFrequency ?? a.samplingFrequency
        }
        return info
    }

    /// «Русский — Дубляж», «Английский».
    static func title(_ t: MKVTrack) -> String {
        let language = t.displayLanguage
        if let name = t.name?.trimmingCharacters(in: .whitespaces), !name.isEmpty, name.lowercased() != language.lowercased() {
            return "\(language) — \(name)"
        }
        return language
    }

    static func audioDetail(_ t: MKVTrack) -> String {
        var parts = [Codecs.name(forMatroska: t.codecID)]
        parts.append(Units.channels(t.channels))
        if !AudioSources.isPlayable(t) { parts.append("не поддерживается") }
        return parts.joined(separator: " · ")
    }
}
