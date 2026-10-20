import AudioToolbox
import CoreMedia
import Foundation

/// Справочник кодеков и языков.
nonisolated enum Codecs {
    static func name(forMatroska id: String) -> String {
        switch id {
        case "V_MPEG4/ISO/AVC": "H.264"
        case "V_MPEGH/ISO/HEVC": "HEVC"
        case "V_AV1": "AV1"
        case "V_VP9": "VP9"
        case "V_VP8": "VP8"
        case "V_MPEG2": "MPEG-2"
        case "V_MPEG4/ISO/ASP", "V_MPEG4/ISO/SP": "MPEG-4"
        case "V_MS/VFW/FOURCC": "VfW"
        case "V_THEORA": "Theora"
        case "A_AC3": "AC-3"
        case "A_EAC3": "E-AC-3"
        case "A_MPEG/L3": "MP3"
        case "A_MPEG/L2": "MP2"
        case "A_OPUS": "Opus"
        case "A_VORBIS": "Vorbis"
        case "A_FLAC": "FLAC"
        case "A_TRUEHD": "Dolby TrueHD"
        case "A_ALAC": "ALAC"
        case "S_TEXT/UTF8": "SRT"
        case "S_TEXT/ASS", "S_TEXT/SSA": "ASS"
        case "S_TEXT/WEBVTT": "WebVTT"
        case "S_HDMV/PGS": "PGS"
        case "S_VOBSUB": "VobSub"
        default:
            if id.hasPrefix("A_AAC") { "AAC" }
            else if id.hasPrefix("A_DTS") { "DTS" }
            else if id.hasPrefix("A_PCM") { "PCM" }
            else { id }
        }
    }

    static func isTextSubtitle(_ id: String) -> Bool {
        ["S_TEXT/UTF8", "S_TEXT/ASCII", "S_TEXT/ASS", "S_TEXT/SSA", "S_TEXT/WEBVTT", "S_ASS", "S_SSA"].contains(id)
    }

    /// ISO 639-2/B (так пишет Matroska) → 639-2/T (так ждёт QuickTime).
    static func iso639T(_ code: String) -> String? {
        let map = ["fre": "fra", "ger": "deu", "chi": "zho", "cze": "ces", "dut": "nld", "gre": "ell",
                   "per": "fas", "rum": "ron", "slo": "slk", "alb": "sqi", "arm": "hye", "baq": "eus",
                   "bur": "mya", "geo": "kat", "ice": "isl", "mac": "mkd", "mao": "mri", "may": "msa",
                   "tib": "bod", "wel": "cym"]
        let c = code.lowercased()
        guard c.count == 3, c != "und" else { return nil }
        return map[c] ?? c
    }

    static func displaySize(of t: MKVTrack) -> CGSize? {
        guard let dw = t.displayWidth, let dh = t.displayHeight, t.displayUnit == 0, dw > 0, dh > 0,
              t.pixelWidth > 0, t.pixelHeight > 0 else { return nil }
        // Ширину считаем от высоты кадра — так анаморфное видео растягивается, а не сжимается.
        let width = (Double(t.pixelHeight) * Double(dw) / Double(dh)).rounded()
        guard abs(width - Double(t.pixelWidth)) > 2 else { return nil }
        return CGSize(width: width, height: Double(t.pixelHeight))
    }
}

/// Описания форматов CoreMedia из данных дорожки Matroska.
nonisolated enum FormatFactory {
    // MARK: Видео

    static func video(for t: MKVTrack) -> CMVideoFormatDescription? {
        let codec: CMVideoCodecType
        let atom: String
        switch t.codecID {
        case "V_MPEG4/ISO/AVC": codec = kCMVideoCodecType_H264; atom = "avcC"
        case "V_MPEGH/ISO/HEVC": codec = kCMVideoCodecType_HEVC; atom = "hvcC"
        case "V_AV1": codec = kCMVideoCodecType_AV1; atom = "av1C"
        default: return nil
        }
        guard let config = t.codecPrivate, !config.isEmpty, t.pixelWidth > 0, t.pixelHeight > 0 else { return nil }
        var ext = colorExtensions(t.colour)
        ext[kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms] = [atom: config] as CFDictionary
        if let par = pixelAspect(t) { ext[kCMFormatDescriptionExtension_PixelAspectRatio] = par }
        var fd: CMVideoFormatDescription?
        let status = CMVideoFormatDescriptionCreate(
            allocator: kCFAllocatorDefault, codecType: codec,
            width: Int32(t.pixelWidth), height: Int32(t.pixelHeight),
            extensions: ext as CFDictionary, formatDescriptionOut: &fd)
        return status == noErr ? fd : nil
    }

    static func colorExtensions(_ colour: MKVColour?) -> [CFString: Any] {
        guard let colour else { return [:] }
        var ext: [CFString: Any] = [:]
        switch colour.primaries {
        case 1: ext[kCMFormatDescriptionExtension_ColorPrimaries] = kCMFormatDescriptionColorPrimaries_ITU_R_709_2
        case 9: ext[kCMFormatDescriptionExtension_ColorPrimaries] = kCMFormatDescriptionColorPrimaries_ITU_R_2020
        case 5: ext[kCMFormatDescriptionExtension_ColorPrimaries] = kCMFormatDescriptionColorPrimaries_EBU_3213
        case 6, 7: ext[kCMFormatDescriptionExtension_ColorPrimaries] = kCMFormatDescriptionColorPrimaries_SMPTE_C
        case 12: ext[kCMFormatDescriptionExtension_ColorPrimaries] = kCMFormatDescriptionColorPrimaries_P3_D65
        default: break
        }
        switch colour.transfer {
        case 1, 6, 14, 15: ext[kCMFormatDescriptionExtension_TransferFunction] = kCMFormatDescriptionTransferFunction_ITU_R_709_2
        case 16: ext[kCMFormatDescriptionExtension_TransferFunction] = kCMFormatDescriptionTransferFunction_SMPTE_ST_2084_PQ
        case 18: ext[kCMFormatDescriptionExtension_TransferFunction] = kCMFormatDescriptionTransferFunction_ITU_R_2100_HLG
        case 13: ext[kCMFormatDescriptionExtension_TransferFunction] = kCMFormatDescriptionTransferFunction_sRGB
        default: break
        }
        switch colour.matrix {
        case 1: ext[kCMFormatDescriptionExtension_YCbCrMatrix] = kCMFormatDescriptionYCbCrMatrix_ITU_R_709_2
        case 9, 10: ext[kCMFormatDescriptionExtension_YCbCrMatrix] = kCMFormatDescriptionYCbCrMatrix_ITU_R_2020
        case 5, 6: ext[kCMFormatDescriptionExtension_YCbCrMatrix] = kCMFormatDescriptionYCbCrMatrix_ITU_R_601_4
        default: break
        }
        if colour.range == 2 { ext[kCMFormatDescriptionExtension_FullRangeVideo] = true }
        return ext
    }

    static func pixelAspect(_ t: MKVTrack) -> CFDictionary? {
        guard let dw = t.displayWidth, let dh = t.displayHeight, t.displayUnit == 0, dw > 0, dh > 0 else { return nil }
        var h = dw * t.pixelHeight
        var v = dh * t.pixelWidth
        guard h > 0, v > 0, h != v else { return nil }
        let g = gcd(h, v)
        h /= g; v /= g
        return [kCMFormatDescriptionKey_PixelAspectRatioHorizontalSpacing: h,
                kCMFormatDescriptionKey_PixelAspectRatioVerticalSpacing: v] as CFDictionary
    }

    private static func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? a : gcd(b, a % b) }

    // MARK: Аудио

    struct Audio {
        let format: CMAudioFormatDescription
        let framesPerPacket: Int
        let sampleRate: Double
    }

    static func audio(for t: MKVTrack, firstFrame: Data? = nil) -> Audio? {
        let id = t.codecID
        let rate = t.samplingFrequency > 0 ? t.samplingFrequency : 48000
        if id.hasPrefix("A_AAC") { return aac(t) }
        switch id {
        case "A_AC3": return simple(kAudioFormatAC3, rate: rate, channels: t.channels, frames: 1536)
        case "A_EAC3": return eac3(t, firstFrame: firstFrame)
        case "A_MPEG/L3": return simple(kAudioFormatMPEGLayer3, rate: rate, channels: t.channels, frames: rate <= 24000 ? 576 : 1152)
        case "A_MPEG/L2": return simple(kAudioFormatMPEGLayer2, rate: rate, channels: t.channels, frames: 1152)
        default: return nil
        }
    }

    private static func eac3(_ t: MKVTrack, firstFrame: Data?) -> Audio? {
        guard let frame = firstFrame, let h = EAC3Config.header(frame) else {
            return simple(kAudioFormatEnhancedAC3, rate: t.samplingFrequency, channels: t.channels, frames: 1536)
        }
        let frames = h.blocks * 256
        var asbd = AudioStreamBasicDescription(
            mSampleRate: h.sampleRate, mFormatID: kAudioFormatEnhancedAC3, mFormatFlags: 0, mBytesPerPacket: 0,
            mFramesPerPacket: UInt32(frames), mBytesPerFrame: 0, mChannelsPerFrame: UInt32(t.channels),
            mBitsPerChannel: 0, mReserved: 0)
        let ext = [kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms: ["dec3": EAC3Config.dec3(h)]] as CFDictionary
        var fd: CMAudioFormatDescription?
        let status = CMAudioFormatDescriptionCreate(
            allocator: kCFAllocatorDefault, asbd: &asbd, layoutSize: 0, layout: nil,
            magicCookieSize: 0, magicCookie: nil, extensions: ext, formatDescriptionOut: &fd)
        guard status == noErr, let fd else { return nil }
        return Audio(format: fd, framesPerPacket: frames, sampleRate: h.sampleRate)
    }

    private static func simple(_ id: AudioFormatID, rate: Double, channels: Int, frames: Int) -> Audio? {
        var asbd = AudioStreamBasicDescription(
            mSampleRate: rate, mFormatID: id, mFormatFlags: 0, mBytesPerPacket: 0,
            mFramesPerPacket: UInt32(frames), mBytesPerFrame: 0, mChannelsPerFrame: UInt32(channels),
            mBitsPerChannel: 0, mReserved: 0)
        return make(&asbd, cookie: nil).map {
            Audio(format: $0, framesPerPacket: frames, sampleRate: rate)
        }
    }

    private static func aac(_ t: MKVTrack) -> Audio? {
        let asc = (t.codecPrivate?.isEmpty == false ? t.codecPrivate : nil) ?? legacyASC(t)
        guard let asc else { return nil }
        let esds = esdsDescriptor(asc: asc)
        var asbd = AudioStreamBasicDescription()
        var size = UInt32(MemoryLayout<AudioStreamBasicDescription>.size)
        let status = esds.withUnsafeBytes {
            AudioFormatGetProperty(kAudioFormatProperty_ASBDFromESDS, UInt32(esds.count), $0.baseAddress, &size, &asbd)
        }
        if status != noErr || asbd.mSampleRate == 0 {
            asbd = AudioStreamBasicDescription(
                mSampleRate: t.samplingFrequency, mFormatID: kAudioFormatMPEG4AAC, mFormatFlags: 0,
                mBytesPerPacket: 0, mFramesPerPacket: 1024, mBytesPerFrame: 0,
                mChannelsPerFrame: UInt32(t.channels), mBitsPerChannel: 0, mReserved: 0)
        }
        if asbd.mFramesPerPacket == 0 { asbd.mFramesPerPacket = 1024 }
        if asbd.mChannelsPerFrame == 0 { asbd.mChannelsPerFrame = UInt32(t.channels) }
        let frames = Int(asbd.mFramesPerPacket)
        let rate = asbd.mSampleRate
        return make(&asbd, cookie: esds).map { Audio(format: $0, framesPerPacket: frames, sampleRate: rate) }
    }

    private static func make(_ asbd: inout AudioStreamBasicDescription, cookie: Data?) -> CMAudioFormatDescription? {
        var fd: CMAudioFormatDescription?
        var description = asbd
        let status: OSStatus
        if let cookie, !cookie.isEmpty {
            status = cookie.withUnsafeBytes { raw in
                CMAudioFormatDescriptionCreate(
                    allocator: kCFAllocatorDefault, asbd: &description, layoutSize: 0, layout: nil,
                    magicCookieSize: cookie.count, magicCookie: raw.baseAddress,
                    extensions: nil, formatDescriptionOut: &fd)
            }
        } else {
            status = CMAudioFormatDescriptionCreate(
                allocator: kCFAllocatorDefault, asbd: &description, layoutSize: 0, layout: nil,
                magicCookieSize: 0, magicCookie: nil, extensions: nil, formatDescriptionOut: &fd)
        }
        return status == noErr ? fd : nil
    }

    static let aacRates: [Double] = [96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350]

    /// Старые файлы с «A_AAC/MPEG4/LC» не содержат AudioSpecificConfig — собираем его сами.
    static func legacyASC(_ t: MKVTrack) -> Data? {
        let profile: UInt16
        if t.codecID.hasSuffix("/MAIN") { profile = 1 }
        else if t.codecID.hasSuffix("/SSR") { profile = 3 }
        else { profile = 2 }
        guard let index = aacRates.firstIndex(of: t.samplingFrequency) else { return nil }
        let channels = UInt16(min(t.channels, 7))
        let value: UInt16 = (profile << 11) | (UInt16(index) << 7) | (channels << 3)
        return Data([UInt8(value >> 8), UInt8(value & 0xFF)])
    }

    /// Дескриптор ES (содержимое атома esds) — в таком виде CoreAudio ждёт «magic cookie» AAC.
    static func esdsDescriptor(asc: Data) -> Data {
        func descriptor(_ tag: UInt8, _ payload: Data) -> Data {
            let n = payload.count
            var d = Data([tag,
                          UInt8(0x80 | ((n >> 21) & 0x7F)), UInt8(0x80 | ((n >> 14) & 0x7F)),
                          UInt8(0x80 | ((n >> 7) & 0x7F)), UInt8(n & 0x7F)])
            d.append(payload)
            return d
        }
        var decoderConfig = Data([0x40, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])
        decoderConfig.append(descriptor(0x05, asc))
        var es = Data([0x00, 0x00, 0x00])
        es.append(descriptor(0x04, decoderConfig))
        es.append(descriptor(0x06, Data([0x02])))
        return descriptor(0x03, es)
    }
}
