import AVFoundation
import AudioToolbox
import CoreMedia
import os

/// Глава в независимом от контейнера виде.
nonisolated struct ChapterMark: Codable, Sendable, Hashable {
    var title: String
    var start: Double
}

/// Результат подготовки файла, который AVFoundation не умеет открыть сам.
/// Хранится рядом с перепакованным файлом в кэше.
nonisolated struct ConversionManifest: Codable, Sendable {
    static let currentVersion = 2
    var version = ConversionManifest.currentVersion
    var mediaFile: String
    var audioNames: [String]
    var subtitles: [SubtitleTrackData]
    var chapters: [ChapterMark]
    var notes: [String]
    var sourceVideoCodec: String?
    var sourceAudioCodecs: [String]
    var transcodedVideo: Bool
}

/// Флаг отмены, который можно дёргать из любого потока.
nonisolated final class CancelFlag: Sendable {
    private let state = OSAllocatedUnfairLock(initialState: false)
    var isCancelled: Bool { state.withLock { $0 } }
    func cancel() { state.withLock { $0 = true } }
}

/// Перепаковка Matroska/WebM в MP4/MOV без перекодирования (passthrough):
/// собственный демультиплексор → CMSampleBuffer → AVAssetWriter.
/// Видео VP9 перекодируется аппаратным HEVC-кодером (см. VP9Transcoder).
nonisolated enum MatroskaRemuxer {
    static let fileExtensions: Set<String> = ["mkv", "mk3d", "mka", "webm"]

    typealias Progress = @Sendable (Double, String) -> Void

    /// Запускает работу на отдельной очереди, чтобы не занимать пул Swift Concurrency
    /// долгими синхронными циклами, и корректно отменяется вместе с задачей.
    static func remux(input: URL, outputDirectory: URL, progress: @escaping Progress) async throws -> ConversionManifest {
        let flag = CancelFlag()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<ConversionManifest, any Error>) in
                DispatchQueue.global(qos: .userInitiated).async {
                    do {
                        let job = try RemuxJob(input: input, outputDirectory: outputDirectory, cancel: flag, progress: progress)
                        continuation.resume(returning: try job.run())
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }
        } onCancel: {
            flag.cancel()
        }
    }
}

// MARK: - Задание перепаковки

nonisolated private final class RemuxJob {
    struct Output {
        let track: MKVTrack
        let format: CMFormatDescription
        var input: AVAssetWriterInput!
        var adaptor: AVAssetWriterInputPixelBufferAdaptor?
        /// Аудио: отсчётов в пакете и частота.
        var framesPerPacket = 0
        var sampleRate: Double = 0
        var transcoder: VP9Transcoder?
        var queue: [CMSampleBuffer] = []
        var head = 0
        var exhausted = false
        var finished = false
        var lastBlock = -1
        // Хронометраж аудио
        var anchorNs: Double = 0
        var packetsSinceAnchor = 0
        var started = false

        var pending: Bool { head < queue.count }
    }

    let input: URL
    let outputDirectory: URL
    let cancel: CancelFlag
    let progress: MatroskaRemuxer.Progress
    let mkv: MatroskaFile

    var outputs: [Output] = []
    var outputByTrack: [UInt32: Int] = [:]
    var subtitleTracks: [UInt32: Int] = [:]
    var subtitles: [SubtitleTrackData] = []
    var notes: [String] = []

    var videoDTS: [Int: Int64] = [:]
    var videoFrameDuration: Int64 = 0
    var timeShift: Int64 = 0

    init(input: URL, outputDirectory: URL, cancel: CancelFlag, progress: @escaping MatroskaRemuxer.Progress) throws {
        self.input = input
        self.outputDirectory = outputDirectory
        self.cancel = cancel
        self.progress = progress
        progress(0, "Читаю структуру файла…")
        mkv = try MatroskaFile(url: input) { fraction in
            progress(fraction * 0.12, "Индексирую кадры…")
        }
    }

    func run() throws -> ConversionManifest {
        try planTracks()
        guard outputs.contains(where: { $0.track.kind == .video || $0.track.kind == .audio }) else {
            throw MatroskaError(notes.first ?? "В файле нет дорожек, которые можно воспроизвести")
        }
        computeVideoTiming()

        let hasVP9 = outputs.contains { $0.transcoder != nil }
        let types: [AVFileType] = [.mp4, .mov]
        var writer: AVAssetWriter?
        var url = outputDirectory
        for type in types {
            url = outputDirectory.appendingPathComponent(type == .mp4 ? "media.mp4" : "media.mov")
            try? FileManager.default.removeItem(at: url)
            if let w = try makeWriter(url: url, type: type, strict: type != types.last) {
                writer = w
                break
            }
        }
        guard let writer else { throw MatroskaError("Системный модуль записи отклонил дорожки файла") }
        // Дорожки, которые не принял даже MOV, выбрасываем.
        outputs.removeAll { $0.input == nil }
        outputByTrack = Dictionary(uniqueKeysWithValues: outputs.enumerated().map { (UInt32($0.element.track.number), $0.offset) })

        for (index, block) in mkv.blocks.enumerated() {
            if let o = outputByTrack[block.track] { outputs[o].lastBlock = index }
        }
        for i in outputs.indices where outputs[i].lastBlock < 0 { outputs[i].exhausted = true }

        guard writer.startWriting() else {
            throw MatroskaError(writer.error?.localizedDescription ?? "Не удалось начать запись")
        }
        writer.startSession(atSourceTime: .zero)

        let total = max(1, mkv.blocks.count)
        let message = hasVP9 ? "Перекодирую VP9 аппаратным кодером Apple…" : "Перепаковываю без потери качества…"
        var videoOrdinal = 0

        for (index, block) in mkv.blocks.enumerated() {
            if index & 1023 == 0 {
                if cancel.isCancelled { writer.cancelWriting(); throw CancellationError() }
                progress(0.12 + 0.86 * Double(index) / Double(total), message)
            }
            if let s = subtitleTracks[block.track] {
                appendSubtitle(block, to: s)
                continue
            }
            guard let o = outputByTrack[block.track] else { continue }
            switch outputs[o].track.kind {
            case .video:
                try enqueueVideo(block, ordinal: videoOrdinal, output: o)
                videoOrdinal += 1
            case .audio:
                try enqueueAudio(block, output: o)
            default: break
            }
            if index == outputs[o].lastBlock {
                if let t = outputs[o].transcoder { outputs[o].queue += try t.flush() }
                outputs[o].exhausted = true
            }
            try pump(writer, untilStarving: true)
        }
        for i in outputs.indices where !outputs[i].exhausted {
            if let t = outputs[i].transcoder { outputs[i].queue += try t.flush() }
            outputs[i].exhausted = true
        }
        try pump(writer, untilStarving: false)

        let done = DispatchSemaphore(value: 0)
        writer.finishWriting { done.signal() }
        done.wait()
        guard writer.status == .completed else {
            throw MatroskaError(writer.error?.localizedDescription ?? "Запись файла не завершилась")
        }
        progress(1, "Готово")

        finalizeSubtitles()
        let shift = Double(timeShift) / 1e9
        let audio = outputs.filter { $0.track.kind == .audio }
        return ConversionManifest(
            mediaFile: url.lastPathComponent,
            audioNames: audio.map(\.track.label),
            subtitles: subtitles,
            chapters: mkv.chapters.map { ChapterMark(title: $0.title, start: $0.start + shift) },
            notes: notes,
            sourceVideoCodec: outputs.first { $0.track.kind == .video }.map { Codecs.name(forMatroska: $0.track.codecID) },
            sourceAudioCodecs: audio.map { Codecs.name(forMatroska: $0.track.codecID) },
            transcodedVideo: hasVP9
        )
    }

    // MARK: Дорожки

    private func planTracks() throws {
        var hasVideo = false
        for track in mkv.tracks where track.isEnabled {
            let codec = Codecs.name(forMatroska: track.codecID)
            switch track.kind {
            case .video:
                guard !hasVideo else { continue }
                if let fd = FormatFactory.video(for: track) {
                    outputs.append(Output(track: track, format: fd))
                    hasVideo = true
                } else if track.codecID == "V_VP9", let t = try? VP9Transcoder(track: track, firstFrame: firstFrame(of: track)) {
                    var out = Output(track: track, format: t.outputHint)
                    out.transcoder = t
                    outputs.append(out)
                    hasVideo = true
                } else {
                    notes.append("Видео \(codec) не поддерживается — показываю только звук")
                }
            case .audio:
                if let a = FormatFactory.audio(for: track) {
                    var out = Output(track: track, format: a.format)
                    out.framesPerPacket = a.framesPerPacket
                    out.sampleRate = a.sampleRate
                    outputs.append(out)
                } else {
                    notes.append("Звук «\(track.label)» (\(codec)) не поддерживается macOS — дорожка пропущена")
                }
            case .subtitle:
                if Codecs.isTextSubtitle(track.codecID) {
                    subtitleTracks[UInt32(track.number)] = subtitles.count
                    var name = track.label
                    if track.isForced { name += " (форсированные)" }
                    subtitles.append(SubtitleTrackData(name: name, language: track.languageBCP47 ?? track.language, cues: []))
                } else {
                    notes.append("Субтитры «\(track.label)» (\(codec)) — графический формат, пропущены")
                }
            case .other:
                break
            }
        }
    }

    private func firstFrame(of track: MKVTrack) -> Data? {
        guard let block = mkv.blocks.first(where: { UInt64($0.track) == track.number }),
              let range = mkv.frames(of: block).first else { return nil }
        var data = track.strippedHeader ?? Data()
        data.append(mkv.file.data(range.lowerBound, range.count))
        return data
    }

    /// DTS для видео с B-кадрами: в Matroska хранится только время показа.
    /// Сортируем PTS и сдвигаем так, чтобы DTS ≤ PTS и шёл монотонно.
    private func computeVideoTiming() {
        guard let video = outputs.first(where: { $0.track.kind == .video }) else { return }
        let number = UInt32(video.track.number)
        let pts = mkv.blocks.lazy.filter { $0.track == number }.map(\.time)
        let list = Array(pts)
        guard !list.isEmpty else { return }
        let sorted = list.sorted()
        var shift: Int64 = 0
        for i in list.indices { shift = max(shift, sorted[i] - list[i]) }
        for i in list.indices { videoDTS[i] = sorted[i] - shift }
        timeShift = max(0, -(sorted[0] - shift))

        if let d = video.track.defaultDuration, d > 0 {
            videoFrameDuration = Int64(d)
        } else if sorted.count > 2 {
            var diffs = zip(sorted.dropFirst(), sorted).map { $0 - $1 }.filter { $0 > 0 }
            diffs.sort()
            videoFrameDuration = diffs.isEmpty ? 41_708_333 : diffs[diffs.count / 2]
        } else {
            videoFrameDuration = 41_708_333
        }
    }

    // MARK: Запись

    private func makeWriter(url: URL, type: AVFileType, strict: Bool) throws -> AVAssetWriter? {
        let writer = try AVAssetWriter(outputURL: url, fileType: type)
        writer.shouldOptimizeForNetworkUse = false
        var accepted: [Int] = []
        for i in outputs.indices {
            let out = outputs[i]
            let mediaType: AVMediaType = out.track.kind == .video ? .video : .audio
            let input: AVAssetWriterInput
            if let transcoder = out.transcoder {
                input = AVAssetWriterInput(mediaType: .video, outputSettings: transcoder.encoderSettings)
            } else {
                input = AVAssetWriterInput(mediaType: mediaType, outputSettings: nil, sourceFormatHint: out.format)
            }
            input.expectsMediaDataInRealTime = false
            if out.track.kind == .audio || out.track.kind == .video {
                if let code = Codecs.iso639T(out.track.language) { input.languageCode = code }
                if let tag = out.track.languageBCP47 { input.extendedLanguageTag = tag }
            }
            if out.track.kind == .video, let size = Codecs.displaySize(of: out.track), out.transcoder == nil {
                input.naturalSize = size
            }
            if writer.canAdd(input) {
                writer.add(input)
                outputs[i].input = input
                if out.transcoder != nil {
                    outputs[i].adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: nil)
                }
                accepted.append(i)
            } else {
                if strict { return nil }
                outputs[i].input = nil
                notes.append("Дорожка «\(out.track.label)» не принята системой и пропущена")
            }
        }
        let audioInputs = accepted.filter { outputs[$0].track.kind == .audio }
        if audioInputs.count > 1 {
            let inputs = audioInputs.map { outputs[$0].input! }
            let def = audioInputs.first { outputs[$0].track.isDefault }.map { outputs[$0].input! } ?? inputs[0]
            let group = AVAssetWriterInputGroup(inputs: inputs, defaultInput: def)
            if writer.canAdd(group) { writer.add(group) }
        }
        return writer
    }

    /// Отдаёт накопленные сэмплы писателю. Если `untilStarving`, возвращается,
    /// как только какой-то дорожке нужны новые данные из файла.
    private func pump(_ writer: AVAssetWriter, untilStarving: Bool) throws {
        while true {
            var appended = false
            for i in outputs.indices {
                guard !outputs[i].finished, let input = outputs[i].input else { continue }
                while outputs[i].pending, input.isReadyForMoreMediaData {
                    let sample = outputs[i].queue[outputs[i].head]
                    let ok: Bool
                    if let adaptor = outputs[i].adaptor, let pixels = CMSampleBufferGetImageBuffer(sample) {
                        ok = adaptor.append(pixels, withPresentationTime: CMSampleBufferGetPresentationTimeStamp(sample))
                    } else {
                        ok = input.append(sample)
                    }
                    guard ok else {
                        throw MatroskaError(writer.error?.localizedDescription ?? "Ошибка записи кадра")
                    }
                    outputs[i].head += 1
                    appended = true
                }
                if outputs[i].head > 512 {
                    outputs[i].queue.removeFirst(outputs[i].head)
                    outputs[i].head = 0
                }
                if outputs[i].exhausted, !outputs[i].pending {
                    input.markAsFinished()
                    outputs[i].finished = true
                }
            }
            if outputs.allSatisfy(\.finished) { return }
            if untilStarving, outputs.contains(where: { !$0.exhausted && !$0.pending }) { return }
            if writer.status == .failed {
                throw MatroskaError(writer.error?.localizedDescription ?? "Ошибка записи")
            }
            if cancel.isCancelled { writer.cancelWriting(); throw CancellationError() }
            if !appended { usleep(400) }
        }
    }

    private func enqueueVideo(_ block: MKVBlockRef, ordinal: Int, output o: Int) throws {
        let track = outputs[o].track
        let pts = block.time + timeShift
        let dts = (videoDTS[ordinal] ?? block.time) + timeShift
        let ns = Int32(1_000_000_000)
        var timing = CMSampleTimingInfo(
            duration: CMTime(value: videoFrameDuration, timescale: ns),
            presentationTimeStamp: CMTime(value: pts, timescale: ns),
            decodeTimeStamp: CMTime(value: dts, timescale: ns)
        )
        for range in mkv.frames(of: block) {
            let buffer = try SampleFactory.blockBuffer(mkv.file, range, prefix: track.strippedHeader)
            if let transcoder = outputs[o].transcoder {
                outputs[o].queue += try transcoder.decode(buffer, timing: timing, isKey: block.isKey)
            } else {
                let sample = try SampleFactory.sample(buffer, format: outputs[o].format, timing: &timing)
                if !block.isKey { SampleFactory.markNotSync(sample) }
                outputs[o].queue.append(sample)
            }
        }
    }

    private func enqueueAudio(_ block: MKVBlockRef, output o: Int) throws {
        let out = outputs[o]
        let packetNs = Double(out.framesPerPacket) / out.sampleRate * 1e9
        let blockNs = Double(block.time + timeShift)
        // Хронометраж аудио считаем по количеству пакетов: миллисекундные метки
        // Matroska иначе дают дрожание, на которое AVAssetWriter отвечает правками монтажа.
        let expected = out.anchorNs + Double(out.packetsSinceAnchor) * packetNs
        if !out.started || abs(expected - blockNs) > 150_000_000 {
            outputs[o].anchorNs = max(0, blockNs)
            outputs[o].packetsSinceAnchor = 0
            outputs[o].started = true
        }
        for range in mkv.frames(of: block) where !range.isEmpty {
            let buffer = try SampleFactory.blockBuffer(mkv.file, range, prefix: out.track.strippedHeader)
            let ptsNs = outputs[o].anchorNs + Double(outputs[o].packetsSinceAnchor) * packetNs
            let pts = CMTime(value: Int64(ptsNs.rounded()), timescale: 1_000_000_000)
            let sample = try SampleFactory.audioSample(buffer, format: out.format, pts: pts, size: range.count + (out.track.strippedHeader?.count ?? 0))
            outputs[o].queue.append(sample)
            outputs[o].packetsSinceAnchor += 1
        }
    }

    // MARK: Субтитры

    private func appendSubtitle(_ block: MKVBlockRef, to index: Int) {
        guard let track = mkv.track(number: UInt64(block.track)) else { return }
        for range in mkv.frames(of: block) {
            var data = mkv.file.data(range.lowerBound, range.count)
            if track.zlibCompressed, data.count > 2,
               let inflated = try? (data.dropFirst(2) as NSData).decompressed(using: .zlib) as Data {
                data = inflated
            }
            if let prefix = track.strippedHeader { data = prefix + data }
            var text = SubtitleParser.decode(data)
            if track.codecID == "S_TEXT/ASS" || track.codecID == "S_TEXT/SSA" {
                let fields = text.split(separator: ",", maxSplits: 8, omittingEmptySubsequences: false)
                text = fields.count == 9 ? SubtitleParser.cleanASS(String(fields[8])) : SubtitleParser.cleanASS(text)
            } else {
                text = SubtitleParser.stripTags(text).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            guard !text.isEmpty else { continue }
            let start = Double(block.time + timeShift) / 1e9
            let end = block.duration.map { start + Double($0) / 1e9 } ?? -1
            subtitles[index].cues.append(SubtitleCue(start: start, end: end, text: text))
        }
    }

    private func finalizeSubtitles() {
        for i in subtitles.indices {
            var cues = subtitles[i].cues.sorted { $0.start < $1.start }
            for j in cues.indices where cues[j].end < cues[j].start {
                let next = j + 1 < cues.count ? cues[j + 1].start : cues[j].start + 4
                cues[j].end = min(next, cues[j].start + 6)
            }
            subtitles[i].cues = cues
        }
        subtitles.removeAll { $0.cues.isEmpty }
    }
}

// MARK: - Сэмплы

nonisolated enum SampleFactory {
    static func blockBuffer(_ file: MappedFile, _ range: Range<Int>, prefix: Data?) throws -> CMBlockBuffer {
        let prefixCount = prefix?.count ?? 0
        let length = range.count + prefixCount
        var buffer: CMBlockBuffer?
        var status = CMBlockBufferCreateWithMemoryBlock(
            allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: length,
            blockAllocator: kCFAllocatorDefault, customBlockSource: nil,
            offsetToData: 0, dataLength: length, flags: kCMBlockBufferAssureMemoryNowFlag,
            blockBufferOut: &buffer)
        guard status == noErr, let buffer else { throw MatroskaError("Нет памяти под кадр") }
        if let prefix, prefixCount > 0 {
            status = prefix.withUnsafeBytes {
                CMBlockBufferReplaceDataBytes(with: $0.baseAddress!, blockBuffer: buffer, offsetIntoDestination: 0, dataLength: prefixCount)
            }
        }
        if range.count > 0 {
            status = CMBlockBufferReplaceDataBytes(with: file.base + range.lowerBound, blockBuffer: buffer,
                                                   offsetIntoDestination: prefixCount, dataLength: range.count)
        }
        guard status == noErr else { throw MatroskaError("Не удалось скопировать кадр") }
        return buffer
    }

    static func sample(_ buffer: CMBlockBuffer, format: CMFormatDescription, timing: inout CMSampleTimingInfo) throws -> CMSampleBuffer {
        var size = CMBlockBufferGetDataLength(buffer)
        var sample: CMSampleBuffer?
        let status = CMSampleBufferCreateReady(
            allocator: kCFAllocatorDefault, dataBuffer: buffer, formatDescription: format,
            sampleCount: 1, sampleTimingEntryCount: 1, sampleTimingArray: &timing,
            sampleSizeEntryCount: 1, sampleSizeArray: &size, sampleBufferOut: &sample)
        guard status == noErr, let sample else { throw MatroskaError("Не удалось собрать видеокадр (\(status))") }
        return sample
    }

    static func audioSample(_ buffer: CMBlockBuffer, format: CMFormatDescription, pts: CMTime, size: Int) throws -> CMSampleBuffer {
        var packet = AudioStreamPacketDescription(mStartOffset: 0, mVariableFramesInPacket: 0, mDataByteSize: UInt32(size))
        var sample: CMSampleBuffer?
        let status = CMAudioSampleBufferCreateReadyWithPacketDescriptions(
            allocator: kCFAllocatorDefault, dataBuffer: buffer, formatDescription: format,
            sampleCount: 1, presentationTimeStamp: pts, packetDescriptions: &packet, sampleBufferOut: &sample)
        guard status == noErr, let sample else { throw MatroskaError("Не удалось собрать аудиопакет (\(status))") }
        return sample
    }

    static func markNotSync(_ sample: CMSampleBuffer) {
        guard let attachments = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: true),
              CFArrayGetCount(attachments) > 0 else { return }
        let dict = unsafeBitCast(CFArrayGetValueAtIndex(attachments, 0), to: CFMutableDictionary.self)
        CFDictionarySetValue(dict,
                             Unmanaged.passUnretained(kCMSampleAttachmentKey_NotSync).toOpaque(),
                             Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
    }
}
