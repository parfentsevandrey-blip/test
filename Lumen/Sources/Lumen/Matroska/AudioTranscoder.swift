import AVFoundation
import AudioToolbox
import CoreMedia

/// Звук, который нельзя положить в MP4 как есть (Opus из WebM): декодируем
/// системным AVAudioConverter в PCM, а AVAssetWriter сам кодирует его в AAC.
nonisolated final class AudioTranscoder {
    let outputSettings: [String: Any]
    let pcmFormat: AVAudioFormat
    private let inputFormat: AVAudioFormat
    private let converter: AVAudioConverter
    private var samplesWritten: Int64 = 0
    private var anchor: CMTime?

    init?(track: MKVTrack) {
        guard track.codecID == "A_OPUS" else { return nil }
        let channels = AVAudioChannelCount(max(1, min(track.channels, 8)))
        var asbd = AudioStreamBasicDescription(
            mSampleRate: 48000, mFormatID: kAudioFormatOpus, mFormatFlags: 0, mBytesPerPacket: 0,
            mFramesPerPacket: 960, mBytesPerFrame: 0, mChannelsPerFrame: channels, mBitsPerChannel: 0, mReserved: 0)
        guard let input = AVAudioFormat(streamDescription: &asbd),
              let output = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 48000, channels: channels, interleaved: true)
        else { return nil }
        // Для многоканального Opus декодеру нужна таблица каналов из заголовка OpusHead.
        if channels > 2, let head = track.codecPrivate, !head.isEmpty { input.magicCookie = head }
        guard let converter = AVAudioConverter(from: input, to: output) else { return nil }
        inputFormat = input
        pcmFormat = output
        self.converter = converter
        outputSettings = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: 48000,
            AVNumberOfChannelsKey: Int(channels),
            AVEncoderBitRateKey: channels > 2 ? 384_000 : 192_000,
        ]
    }

    /// Декодирует один пакет и возвращает PCM-сэмпл (или nil, если декодер ещё копит данные).
    func decode(_ packet: Data, at time: CMTime) throws -> CMSampleBuffer? {
        if anchor == nil { anchor = time }
        let compressed = AVAudioCompressedBuffer(format: inputFormat, packetCapacity: 1, maximumPacketSize: packet.count)
        packet.withUnsafeBytes { raw in
            compressed.data.copyMemory(from: raw.baseAddress!, byteCount: packet.count)
        }
        compressed.packetDescriptions?.pointee = AudioStreamPacketDescription(
            mStartOffset: 0, mVariableFramesInPacket: 0, mDataByteSize: UInt32(packet.count))
        compressed.packetCount = 1
        compressed.byteLength = UInt32(packet.count)

        guard let pcm = AVAudioPCMBuffer(pcmFormat: pcmFormat, frameCapacity: 5760) else { return nil }
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
        guard pcm.frameLength > 0 else { return nil }
        return try sample(from: pcm)
    }

    private func sample(from pcm: AVAudioPCMBuffer) throws -> CMSampleBuffer {
        let base = anchor ?? .zero
        let pts = CMTimeAdd(base, CMTime(value: samplesWritten, timescale: 48000))
        samplesWritten += Int64(pcm.frameLength)
        var timing = CMSampleTimingInfo(duration: CMTime(value: 1, timescale: 48000), presentationTimeStamp: pts, decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        var status = CMSampleBufferCreate(
            allocator: kCFAllocatorDefault, dataBuffer: nil, dataReady: false,
            makeDataReadyCallback: nil, refcon: nil, formatDescription: pcmFormat.formatDescription,
            sampleCount: CMItemCount(pcm.frameLength), sampleTimingEntryCount: 1, sampleTimingArray: &timing,
            sampleSizeEntryCount: 0, sampleSizeArray: nil, sampleBufferOut: &sample)
        guard status == noErr, let sample else { throw MatroskaError("PCM-буфер (\(status))") }
        status = CMSampleBufferSetDataBufferFromAudioBufferList(
            sample, blockBufferAllocator: kCFAllocatorDefault, blockBufferMemoryAllocator: kCFAllocatorDefault,
            flags: 0, bufferList: pcm.audioBufferList)
        guard status == noErr else { throw MatroskaError("PCM-данные (\(status))") }
        return sample
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

/// Бокс dec3 (ETSI TS 102 366, приложение F) из заголовка первого кадра E-AC-3.
/// Без него QuickTime/MP4 не видит дорожку Dolby Digital Plus.
nonisolated enum EAC3Config {
    struct Header {
        var fscod: UInt32
        var bsid: UInt32
        var acmod: UInt32
        var lfeon: UInt32
        var frameBytes: Int
        var blocks: Int
        var sampleRate: Double
    }

    static func header(_ frame: Data) -> Header? {
        guard frame.count >= 6, frame[frame.startIndex] == 0x0B, frame[frame.startIndex + 1] == 0x77 else { return nil }
        var r = BitReader(data: frame)
        _ = r.read(16)            // syncword
        _ = r.read(2)             // strmtyp
        _ = r.read(3)             // substreamid
        let frmsiz = r.read(11)
        let fscod = r.read(2)
        var blocks = 6
        var rate: Double
        if fscod == 3 {
            let fscod2 = r.read(2)
            rate = [24000, 22050, 16000, 0][Int(fscod2)]
        } else {
            blocks = [1, 2, 3, 6][Int(r.read(2))]
            rate = [48000, 44100, 32000, 0][Int(fscod)]
        }
        let acmod = r.read(3)
        let lfeon = r.read(1)
        let bsid = r.read(5)
        guard rate > 0 else { return nil }
        return Header(fscod: fscod == 3 ? 3 : fscod, bsid: bsid, acmod: acmod, lfeon: lfeon,
                      frameBytes: (Int(frmsiz) + 1) * 2, blocks: blocks, sampleRate: rate)
    }

    static func dec3(_ h: Header) -> Data {
        let kbps = UInt32(Double(h.frameBytes * 8) * h.sampleRate / Double(h.blocks * 256) / 1000)
        var w = BitWriter()
        w.write(min(kbps, 8191), 13)  // data_rate
        w.write(0, 3)                 // num_ind_sub (= 1 поток)
        w.write(h.fscod, 2)
        w.write(h.bsid, 5)
        w.write(0, 1)                 // reserved
        w.write(0, 1)                 // asvc
        w.write(0, 3)                 // bsmod
        w.write(h.acmod, 3)
        w.write(h.lfeon, 1)
        w.write(0, 3)                 // reserved
        w.write(0, 4)                 // num_dep_sub
        w.write(0, 1)                 // reserved
        return w.data
    }
}

nonisolated struct BitWriter {
    private(set) var data = Data()
    private var current: UInt8 = 0
    private var used = 0

    mutating func write(_ value: UInt32, _ bits: Int) {
        for i in stride(from: bits - 1, through: 0, by: -1) {
            current = (current << 1) | UInt8((value >> UInt32(i)) & 1)
            used += 1
            if used == 8 {
                data.append(current)
                current = 0
                used = 0
            }
        }
    }
}
