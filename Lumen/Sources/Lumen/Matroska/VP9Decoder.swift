import AVFoundation
import CoreMedia
import VideoToolbox

/// VP9 (типичный WebM с YouTube) системный слой видео не принимает в сжатом виде. Декодируем его
/// аппаратным декодером VideoToolbox и показываем готовые кадры — без перекодирования и без ожидания.
nonisolated final class VP9Decoder {
    let format: CMVideoFormatDescription
    private let session: VTDecompressionSession
    private let collector = FrameCollector()

    init(track: MKVTrack, firstFrame: Data?) throws {
        VTRegisterSupplementalVideoDecoderIfAvailable(kCMVideoCodecType_VP9)
        let header = firstFrame.flatMap(VP9Header.init(frame:)) ?? VP9Header(profile: 0, bitDepth: 8)
        var ext = FormatFactory.colorExtensions(track.colour)
        ext[kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms] =
            ["vpcC": VP9Decoder.vpcC(header: header, colour: track.colour)] as CFDictionary
        var fd: CMVideoFormatDescription?
        let status = CMVideoFormatDescriptionCreate(
            allocator: kCFAllocatorDefault, codecType: kCMVideoCodecType_VP9,
            width: Int32(track.pixelWidth), height: Int32(track.pixelHeight),
            extensions: ext as CFDictionary, formatDescriptionOut: &fd)
        guard status == noErr, let fd else { throw MatroskaError("VP9: не удалось описать формат") }
        let tenBit = header.bitDepth > 8
        let attributes: [CFString: Any] = [
            kCVPixelBufferPixelFormatTypeKey: tenBit ? kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange : kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
            kCVPixelBufferIOSurfacePropertiesKey: [:] as CFDictionary,
        ]
        var session: VTDecompressionSession?
        let spec: [CFString: Any] = [kVTVideoDecoderSpecification_EnableHardwareAcceleratedVideoDecoder: true]
        let created = VTDecompressionSessionCreate(
            allocator: kCFAllocatorDefault, formatDescription: fd, decoderSpecification: spec as CFDictionary,
            imageBufferAttributes: attributes as CFDictionary, outputCallback: nil, decompressionSessionOut: &session)
        guard created == noErr, let session else { throw MatroskaError("Декодер VP9 недоступен на этом Mac (\(created))") }
        self.session = session
        format = fd
    }

    deinit { VTDecompressionSessionInvalidate(session) }

    /// Декодирует кадр; возвращает готовые к показу сэмплы (невидимые кадры VP9 картинки не дают).
    func decode(_ sample: CMSampleBuffer) -> [CMSampleBuffer] {
        let collector = self.collector
        _ = VTDecompressionSessionDecodeFrame(session, sampleBuffer: sample, flags: [], infoFlagsOut: nil) { status, _, image, pts, duration in
            guard status == noErr, let image else { return }
            collector.add(image: image, pts: pts, duration: duration)
        }
        return collector.drain()
    }

    func reset() {
        VTDecompressionSessionWaitForAsynchronousFrames(session)
        _ = collector.drain()
    }

    /// Конфигурация VP Codec (атом vpcC, версия 1).
    static func vpcC(header: VP9Header, colour: MKVColour?) -> Data {
        var d = Data([1, 0, 0, 0]) // version 1, flags 0
        d.append(header.profile)
        d.append(0) // level: декодеру не важен
        let fullRange: UInt8 = colour?.range == 2 ? 1 : 0
        d.append((header.bitDepth << 4) | (1 << 1) | fullRange) // 4:2:0 colocated
        d.append(UInt8(truncatingIfNeeded: colour?.primaries ?? 1))
        d.append(UInt8(truncatingIfNeeded: colour?.transfer ?? 1))
        d.append(UInt8(truncatingIfNeeded: colour?.matrix ?? 1))
        d.append(contentsOf: [0, 0]) // codecInitializationDataSize
        return d
    }
}

/// Собирает декодированные кадры из колбэка VideoToolbox.
nonisolated private final class FrameCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var frames: [CMSampleBuffer] = []

    func add(image: CVImageBuffer, pts: CMTime, duration: CMTime) {
        var format: CMVideoFormatDescription?
        guard CMVideoFormatDescriptionCreateForImageBuffer(allocator: kCFAllocatorDefault, imageBuffer: image, formatDescriptionOut: &format) == noErr,
              let format else { return }
        var timing = CMSampleTimingInfo(duration: duration, presentationTimeStamp: pts, decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        guard CMSampleBufferCreateReadyWithImageBuffer(allocator: kCFAllocatorDefault, imageBuffer: image,
                                                       formatDescription: format, sampleTiming: &timing,
                                                       sampleBufferOut: &sample) == noErr,
              let sample else { return }
        lock.lock(); frames.append(sample); lock.unlock()
    }

    func drain() -> [CMSampleBuffer] {
        lock.lock(); defer { lock.unlock() }
        let out = frames.sorted { CMTimeCompare(CMSampleBufferGetPresentationTimeStamp($0), CMSampleBufferGetPresentationTimeStamp($1)) < 0 }
        frames.removeAll(keepingCapacity: true)
        return out
    }
}

/// Профиль и разрядность из несжатого заголовка ключевого кадра VP9.
nonisolated struct VP9Header {
    var profile: UInt8
    var bitDepth: UInt8

    init(profile: UInt8, bitDepth: UInt8) {
        self.profile = profile
        self.bitDepth = bitDepth
    }

    init?(frame: Data) {
        guard frame.count > 4 else { return nil }
        var reader = BitReader(data: frame)
        guard reader.read(2) == 2 else { return nil } // frame_marker
        let low = reader.read(1)
        let high = reader.read(1)
        let profile = UInt8(high << 1 | low)
        if profile == 3 { _ = reader.read(1) }
        if reader.read(1) == 1 { return nil } // show_existing_frame
        let frameType = reader.read(1)
        _ = reader.read(1) // show_frame
        _ = reader.read(1) // error_resilient
        var depth: UInt8 = 8
        if frameType == 0 {
            guard reader.read(24) == 0x498342 else { return nil } // sync code
            if profile >= 2 { depth = reader.read(1) == 1 ? 12 : 10 }
        }
        self.profile = profile
        self.bitDepth = depth
    }
}

nonisolated struct BitReader {
    let data: Data
    var bit = 0

    mutating func read(_ count: Int) -> UInt32 {
        var value: UInt32 = 0
        for _ in 0..<count {
            let index = data.startIndex + bit / 8
            guard index < data.endIndex else { return value << 1 }
            let b = (data[index] >> (7 - UInt8(bit % 8))) & 1
            value = (value << 1) | UInt32(b)
            bit += 1
        }
        return value
    }
}
