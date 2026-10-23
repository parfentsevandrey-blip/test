import AVFoundation
import AVKit
import QuartzCore

/// Глава в независимом от контейнера виде.
nonisolated struct ChapterMark: Codable, Sendable, Hashable {
    var title: String
    var start: Double
}

/// Звуковая дорожка для списка: название, подробности (кодек, каналы) и можно ли её включить.
struct AudioOption: Identifiable, Hashable {
    let id: Int
    let title: String
    let detail: String
    let available: Bool
}

/// Встроенная дорожка субтитров файла.
struct EmbeddedSubtitle: Identifiable, Hashable {
    enum Kind: Hashable { case legible, text, image }
    let id: Int
    let title: String
    let kind: Kind
}

/// Воспроизведение одного файла. Две реализации: AVPlayer для того, что macOS открывает сама
/// (MP4, MOV, M4V, аудио), и собственный потоковый движок для Matroska/WebM.
@MainActor
protocol PlaybackCore: AnyObject {
    /// Слой с картинкой (AVPlayerLayer или AVSampleBufferDisplayLayer).
    var videoLayer: CALayer { get }
    var info: MediaInfo { get }
    var chapters: [ChapterMark] { get }
    var audioOptions: [AudioOption] { get }
    var selectedAudio: Int { get }
    var subtitles: [EmbeddedSubtitle] { get }

    var currentTime: Double { get }
    var isPlaying: Bool { get }
    var hasEnded: Bool { get }
    /// Ошибка воспроизведения, обнаруженная уже после открытия.
    var failure: String? { get }

    func play(rate: Float)
    func pause()
    func setRate(_ rate: Float)
    func seek(to seconds: Double, precise: Bool, completion: @escaping @MainActor () -> Void)
    func step(_ frames: Int)
    func setVolume(_ volume: Float, muted: Bool)
    func selectAudio(_ index: Int)
    /// Включить встроенные субтитры (nil — выключить). Текст и картинки придут через `subtitleText`/`subtitleImage`.
    func selectSubtitle(_ id: Int?)
    func subtitleText(at time: Double) -> String?
    func subtitleImage(at time: Double) -> PGSImage?
    /// Небольшой кадр BGRA для живой подсветки.
    func ambientFrame() -> CVPixelBuffer?
    /// Кадр на экране в полном разрешении (снимок).
    func snapshot() async -> CGImage?
    func makePictureInPicture() -> AVPictureInPictureController?
    func setGravity(_ gravity: AVLayerVideoGravity)
    /// Проверка конца файла по таймеру движка: true — только что доиграли.
    func pollEnd() -> Bool
    func teardown()
}

// MARK: - Субтитры Blu-ray

/// Субтитры PGS одной дорожки: блоки хранятся сжатыми (как в файле), картинка собирается при показе.
/// Так даже субтитры целого фильма занимают единицы мегабайт, а не сотни.
final class PGSStore {
    private struct Entry {
        var time: Double
        var data: [UInt8]
        var resets: Bool
    }

    private var entries: [Entry] = []
    private var cache: [Int: PGSImage] = [:]

    func add(time: Double, data: [UInt8]) {
        // Повторное чтение после перемотки назад даёт те же блоки — их не дублируем.
        let index = insertionIndex(time)
        if index > 0, abs(entries[index - 1].time - time) < 0.0005 { return }
        if index < entries.count, abs(entries[index].time - time) < 0.0005 { return }
        // Начало эпохи (0x80) или точка входа (0x40) в первом сегменте PCS: с них можно декодировать заново.
        let state = data.count > 10 && data[0] == 0x16 ? data[10] : 0
        entries.insert(Entry(time: time, data: data, resets: state & 0xC0 != 0), at: index)
        cache.removeAll()
    }

    func image(at time: Double) -> PGSImage? {
        let i = insertionIndex(time + 0.0005) - 1
        guard i >= 0 else { return nil }
        if let cached = cache[i] { return cached.isEmpty ? nil : cached }
        var start = i
        while start > 0, !entries[start].resets, i - start < 64 { start -= 1 }
        let decoder = PGSDecoder()
        var last: PGSImage?
        for k in start...i {
            if let image = decoder.decode(entries[k].data, time: entries[k].time).last {
                last = image
                cache[k] = image
            }
        }
        if cache.count > 48 { cache = cache.filter { abs($0.key - i) < 8 } }
        guard let last, !last.isEmpty else { return nil }
        return last
    }

    private func insertionIndex(_ time: Double) -> Int {
        var lo = 0, hi = entries.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if entries[mid].time < time { lo = mid + 1 } else { hi = mid }
        }
        return lo
    }
}

extension SubtitleTrackData {
    /// Реплика из блока Matroska: текстовые субтитры приходят по мере чтения файла.
    mutating func insert(_ cue: SubtitleCue) {
        var lo = 0, hi = cues.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if cues[mid].start < cue.start { lo = mid + 1 } else { hi = mid }
        }
        if lo < cues.count, abs(cues[lo].start - cue.start) < 0.0005 { return }
        // У предыдущей реплики без длительности конец — начало этой.
        // Реплика без длительности показывается до следующей (но не дольше 5 с).
        if lo > 0, cues[lo - 1].end > cue.start, cues[lo - 1].end - cues[lo - 1].start >= 4.99 { cues[lo - 1].end = cue.start }
        cues.insert(cue, at: lo)
    }
}
