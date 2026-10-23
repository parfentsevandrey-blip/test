import AVFoundation
import AudioToolbox
import CoreMedia

/// Звук дорожки Matroska → сэмплы для AVSampleBufferAudioRenderer.
///
/// - AAC, AC-3, E-AC-3, MP3 отдаются рендереру как есть: он декодирует их сам (у E-AC-3 так сохраняется Atmos
///   и пространственный звук в AirPods).
/// - DTS и PCM декодирует Lumen, FLAC, Opus и ALAC — системный декодер AudioToolbox; рендерер получает PCM.
///
/// Время пакетов считается от якоря по числу отсчётов: метки Matroska округлены до миллисекунды, и без этого
/// между пакетами появлялись бы щелчки.
nonisolated protocol AudioPacketSource: AnyObject {
    /// Пакеты одного блока → готовые сэмплы. `time` — время блока, нс.
    func samples(for packets: [[UInt8]], time: Int64) throws -> [CMSampleBuffer]
    /// После перемотки: сбросить историю декодера и якорь времени.
    func reset()
}

nonisolated enum AudioSources {
    /// Как воспроизводить дорожку: nil — macOS такой звук не умеет.
    static func make(for track: MKVTrack, firstPacket: [UInt8]?, preferDecoding: Bool = false) -> AudioPacketSource? {
        let id = track.codecID
        if id.hasPrefix("A_DTS") { return DTSAudioSource() }
        if id.hasPrefix("A_PCM") { return PCMAudioSource(track: track) }
        if !preferDecoding, let format = FormatFactory.audio(for: track, firstFrame: firstPacket.map { Data($0) }) {
            return PassthroughAudioSource(format: format.format, framesPerPacket: format.framesPerPacket, sampleRate: format.sampleRate)
        }
        if let format = FormatFactory.audio(for: track, firstFrame: firstPacket.map { Data($0) }) ?? FormatFactory.decodable(track) {
            return ConvertedAudioSource(input: format.format, framesPerPacket: format.framesPerPacket)
        }
        return nil
    }

    /// Звук, который можно хотя бы попробовать воспроизвести (для списка дорожек).
    static func isPlayable(_ track: MKVTrack) -> Bool {
        let id = track.codecID
        if id.hasPrefix("A_DTS") || id.hasPrefix("A_PCM") || id.hasPrefix("A_AAC") { return true }
        return ["A_AC3", "A_EAC3", "A_MPEG/L3", "A_MPEG/L2", "A_FLAC", "A_OPUS", "A_ALAC"].contains(id)
    }

    // MARK: PCM-сэмплы

    /// Метки громкоговорителей в порядке WAVE (так же хранится PCM в Matroska).
    static func waveLabels(_ channels: Int) -> [AudioChannelLabel] {
        switch channels {
        case 1: [kAudioChannelLabel_Mono]
        case 2: [kAudioChannelLabel_Left, kAudioChannelLabel_Right]
        case 3: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_Center]
        case 4: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_LeftSurround, kAudioChannelLabel_RightSurround]
        case 5: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_Center, kAudioChannelLabel_LeftSurround, kAudioChannelLabel_RightSurround]
        case 6: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_Center, kAudioChannelLabel_LFEScreen,
                 kAudioChannelLabel_LeftSurround, kAudioChannelLabel_RightSurround]
        case 7: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_Center, kAudioChannelLabel_LFEScreen,
                 kAudioChannelLabel_CenterSurround, kAudioChannelLabel_LeftSurround, kAudioChannelLabel_RightSurround]
        default: [kAudioChannelLabel_Left, kAudioChannelLabel_Right, kAudioChannelLabel_Center, kAudioChannelLabel_LFEScreen,
                  kAudioChannelLabel_RearSurroundLeft, kAudioChannelLabel_RearSurroundRight,
                  kAudioChannelLabel_LeftSurround, kAudioChannelLabel_RightSurround]
        }
    }

    static func pcmFormat(rate: Double, labels: [AudioChannelLabel]) -> AVAudioFormat? {
        let count = labels.count
        let size = MemoryLayout<AudioChannelLayout>.size + max(0, count - 1) * MemoryLayout<AudioChannelDescription>.size
        let raw = UnsafeMutableRawPointer.allocate(byteCount: size, alignment: MemoryLayout<AudioChannelLayout>.alignment)
        defer { raw.deallocate() }
        raw.initializeMemory(as: UInt8.self, repeating: 0, count: size)
        let layout = raw.assumingMemoryBound(to: AudioChannelLayout.self)
        layout.pointee.mChannelLayoutTag = kAudioChannelLayoutTag_UseChannelDescriptions
        layout.pointee.mNumberChannelDescriptions = UInt32(count)
        withUnsafeMutablePointer(to: &layout.pointee.mChannelDescriptions) { first in
            let descriptions = UnsafeMutableRawPointer(first).assumingMemoryBound(to: AudioChannelDescription.self)
            for (i, label) in labels.enumerated() { descriptions[i].mChannelLabel = label }
        }
        let channelLayout = AVAudioChannelLayout(layout: layout)
        return AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, interleaved: true, channelLayout: channelLayout)
    }

    /// PCM float32 с чередованием каналов → сэмпл для рендерера.
    static func pcmSample(_ samples: UnsafeBufferPointer<Float>, frames: Int, format: AVAudioFormat, pts: CMTime) throws -> CMSampleBuffer {
        let bytes = frames * Int(format.channelCount) * 4
        var block: CMBlockBuffer?
        var status = CMBlockBufferCreateWithMemoryBlock(
            allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: bytes, blockAllocator: kCFAllocatorDefault,
            customBlockSource: nil, offsetToData: 0, dataLength: bytes, flags: kCMBlockBufferAssureMemoryNowFlag, blockBufferOut: &block)
        guard status == noErr, let block, let base = samples.baseAddress else { throw MatroskaError("Нет памяти под звук") }
        status = CMBlockBufferReplaceDataBytes(with: base, blockBuffer: block, offsetIntoDestination: 0, dataLength: bytes)
        guard status == noErr else { throw MatroskaError("Не удалось скопировать звук") }
        var sample: CMSampleBuffer?
        status = CMAudioSampleBufferCreateReadyWithPacketDescriptions(
            allocator: kCFAllocatorDefault, dataBuffer: block, formatDescription: format.formatDescription,
            sampleCount: frames, presentationTimeStamp: pts, packetDescriptions: nil, sampleBufferOut: &sample)
        guard status == noErr, let sample else { throw MatroskaError("PCM-сэмпл (\(status))") }
        return sample
    }
}

/// Якорь времени: пакеты идут подряд, пока метка блока не разойдётся с расчётной больше чем на 150 мс.
nonisolated struct AudioClock {
    private var anchor: Int64 = 0
    private var frames: Int64 = 0
    private var started = false

    mutating func time(blockTime: Int64, rate: Double) -> CMTime {
        let expected = anchor + Int64(Double(frames) / rate * 1e9)
        if !started || abs(expected - blockTime) > 150_000_000 {
            anchor = blockTime
            frames = 0
            started = true
        }
        return CMTime(value: anchor + Int64(Double(frames) / rate * 1e9), timescale: 1_000_000_000)
    }

    mutating func advance(_ count: Int) { frames += Int64(count) }
    mutating func reset() { started = false }
}

// MARK: - Сжатый звук как есть

nonisolated final class PassthroughAudioSource: AudioPacketSource {
    let format: CMAudioFormatDescription
    let framesPerPacket: Int
    let sampleRate: Double
    private var clock = AudioClock()

    init(format: CMAudioFormatDescription, framesPerPacket: Int, sampleRate: Double) {
        self.format = format
        self.framesPerPacket = framesPerPacket
        self.sampleRate = sampleRate
    }

    func samples(for packets: [[UInt8]], time: Int64) throws -> [CMSampleBuffer] {
        var out: [CMSampleBuffer] = []
        for packet in packets where !packet.isEmpty {
            let pts = clock.time(blockTime: time, rate: sampleRate)
            var block: CMBlockBuffer?
            var status = CMBlockBufferCreateWithMemoryBlock(
                allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: packet.count, blockAllocator: kCFAllocatorDefault,
                customBlockSource: nil, offsetToData: 0, dataLength: packet.count, flags: kCMBlockBufferAssureMemoryNowFlag, blockBufferOut: &block)
            guard status == noErr, let block else { continue }
            status = packet.withUnsafeBytes { CMBlockBufferReplaceDataBytes(with: $0.baseAddress!, blockBuffer: block, offsetIntoDestination: 0, dataLength: packet.count) }
            out.append(try SampleFactory.audioSample(block, format: format, pts: pts, size: packet.count))
            clock.advance(framesPerPacket)
        }
        return out
    }

    func reset() { clock.reset() }
}

// MARK: - Системный декодер (FLAC, Opus, ALAC; запасной путь для остальных)

nonisolated final class ConvertedAudioSource: AudioPacketSource {
    private let inputFormat: AVAudioFormat
    private let outputFormat: AVAudioFormat
    private var converter: AVAudioConverter
    private let maxFrames: AVAudioFrameCount
    private var clock = AudioClock()

    init?(input: CMAudioFormatDescription, framesPerPacket: Int) {
        let source = AVAudioFormat(cmAudioFormatDescription: input)
        let channels = Int(source.channelCount)
        guard let output = AudioSources.pcmFormat(rate: source.sampleRate, labels: AudioSources.waveLabels(channels)),
              let converter = AVAudioConverter(from: source, to: output) else { return nil }
        inputFormat = source
        outputFormat = output
        self.converter = converter
        maxFrames = AVAudioFrameCount(max(framesPerPacket, 4096) * 2)
    }

    func samples(for packets: [[UInt8]], time: Int64) throws -> [CMSampleBuffer] {
        var out: [CMSampleBuffer] = []
        for packet in packets where !packet.isEmpty {
            let compressed = AVAudioCompressedBuffer(format: inputFormat, packetCapacity: 1, maximumPacketSize: packet.count)
            packet.withUnsafeBytes { compressed.data.copyMemory(from: $0.baseAddress!, byteCount: packet.count) }
            compressed.packetDescriptions?.pointee = AudioStreamPacketDescription(mStartOffset: 0, mVariableFramesInPacket: 0, mDataByteSize: UInt32(packet.count))
            compressed.packetCount = 1
            compressed.byteLength = UInt32(packet.count)
            guard let pcm = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: maxFrames) else { continue }
            let feed = PacketFeed(compressed)
            var error: NSError?
            let status = converter.convert(to: pcm, error: &error) { _, outStatus in
                guard let buffer = feed.take() else {
                    outStatus.pointee = .noDataNow
                    return nil
                }
                outStatus.pointee = .haveData
                return buffer
            }
            if status == .error { throw error ?? MatroskaError("Не удалось декодировать звук") }
            guard pcm.frameLength > 0, let data = pcm.floatChannelData?[0] else { continue }
            let frames = Int(pcm.frameLength)
            let pts = clock.time(blockTime: time, rate: outputFormat.sampleRate)
            let buffer = UnsafeBufferPointer(start: data, count: frames * Int(outputFormat.channelCount))
            out.append(try AudioSources.pcmSample(buffer, frames: frames, format: outputFormat, pts: pts))
            clock.advance(frames)
        }
        return out
    }

    func reset() {
        converter.reset()
        clock.reset()
    }
}

/// Отдаёт пакет конвертеру ровно один раз.
nonisolated private final class PacketFeed: @unchecked Sendable {
    private var buffer: AVAudioCompressedBuffer?
    init(_ buffer: AVAudioCompressedBuffer) { self.buffer = buffer }
    func take() -> AVAudioCompressedBuffer? {
        defer { buffer = nil }
        return buffer
    }
}

// MARK: - DTS

nonisolated final class DTSAudioSource: AudioPacketSource {
    private let decoder = DTSDecoder()
    private var clock = AudioClock()
    private var format: AVAudioFormat?
    private var formatKey: [DTSDecoder.Speaker] = []
    private var rate = 0

    func samples(for packets: [[UInt8]], time: Int64) throws -> [CMSampleBuffer] {
        var out: [CMSampleBuffer] = []
        for packet in packets {
            // В блоке может лежать несколько кадров подряд, а у DTS-HD за ядром идёт поток расширений —
            // берём каждое ядро (синхрослово 7FFE8001) и пропускаем остальное.
            var offset = 0
            while let start = Self.nextCore(packet, from: offset) {
                let size = Self.coreSize(packet, at: start)
                guard size > 0 else { break }
                let frame = Array(packet[start..<min(packet.count, start + size)])
                offset = start + size
                guard let decoded = try? decoder.decode(frame) else { continue }
                if decoded.speakers != formatKey || decoded.sampleRate != rate || format == nil {
                    format = AudioSources.pcmFormat(rate: Double(decoded.sampleRate), labels: decoded.speakers.map(Self.label))
                    formatKey = decoded.speakers
                    rate = decoded.sampleRate
                }
                guard let format else { continue }
                let pts = clock.time(blockTime: time, rate: Double(decoded.sampleRate))
                try decoded.samples.withUnsafeBufferPointer {
                    out.append(try AudioSources.pcmSample($0, frames: decoded.frames, format: format, pts: pts))
                }
                clock.advance(decoded.frames)
            }
        }
        return out
    }

    func reset() {
        decoder.reset()
        clock.reset()
    }

    private static func nextCore(_ p: [UInt8], from: Int) -> Int? {
        var i = from
        while i + 10 <= p.count {
            if p[i] == 0x7F, p[i + 1] == 0xFE, p[i + 2] == 0x80, p[i + 3] == 0x01 { return i }
            i += 1
        }
        return nil
    }

    private static func coreSize(_ p: [UInt8], at i: Int) -> Int {
        guard i + 8 <= p.count else { return 0 }
        let fsize = ((Int(p[i + 5]) & 0x03) << 12) | (Int(p[i + 6]) << 4) | (Int(p[i + 7]) >> 4)
        return fsize + 1
    }

    private static func label(_ s: DTSDecoder.Speaker) -> AudioChannelLabel {
        switch s {
        case .left: kAudioChannelLabel_Left
        case .right: kAudioChannelLabel_Right
        case .center: kAudioChannelLabel_Center
        case .lfe: kAudioChannelLabel_LFEScreen
        case .leftSurround: kAudioChannelLabel_LeftSurround
        case .rightSurround: kAudioChannelLabel_RightSurround
        case .centerSurround: kAudioChannelLabel_CenterSurround
        }
    }
}

// MARK: - PCM из Matroska

nonisolated final class PCMAudioSource: AudioPacketSource {
    private let channels: Int
    private let bits: Int
    private let float: Bool
    private let bigEndian: Bool
    private let format: AVAudioFormat?
    private var clock = AudioClock()
    private let rate: Double

    init(track: MKVTrack) {
        channels = max(1, track.channels)
        bits = track.bitDepth ?? 16
        float = track.codecID == "A_PCM/FLOAT/IEEE"
        bigEndian = track.codecID == "A_PCM/INT/BIG"
        rate = track.samplingFrequency > 0 ? track.samplingFrequency : 48000
        format = AudioSources.pcmFormat(rate: rate, labels: AudioSources.waveLabels(channels))
    }

    func samples(for packets: [[UInt8]], time: Int64) throws -> [CMSampleBuffer] {
        guard let format else { return [] }
        let bytesPerSample = max(1, bits / 8)
        var out: [CMSampleBuffer] = []
        for packet in packets {
            let count = packet.count / bytesPerSample
            let frames = count / channels
            guard frames > 0 else { continue }
            var samples = [Float](repeating: 0, count: frames * channels)
            for i in 0..<(frames * channels) {
                let o = i * bytesPerSample
                if float && bytesPerSample == 4 {
                    let raw = UInt32(packet[o]) | UInt32(packet[o + 1]) << 8 | UInt32(packet[o + 2]) << 16 | UInt32(packet[o + 3]) << 24
                    samples[i] = Float(bitPattern: raw)
                    continue
                }
                var value: Int32 = 0
                for b in 0..<bytesPerSample {
                    let byte = Int32(packet[o + (bigEndian ? b : bytesPerSample - 1 - b)])
                    value = value << 8 | byte
                }
                if bytesPerSample == 1 {
                    samples[i] = (Float(value) - 128) / 128
                } else {
                    let shift = 32 - bytesPerSample * 8
                    samples[i] = Float((value << shift) >> shift) / Float(1 << (bytesPerSample * 8 - 1))
                }
            }
            let pts = clock.time(blockTime: time, rate: rate)
            try samples.withUnsafeBufferPointer { out.append(try AudioSources.pcmSample($0, frames: frames, format: format, pts: pts)) }
            clock.advance(frames)
        }
        return out
    }

    func reset() { clock.reset() }
}
