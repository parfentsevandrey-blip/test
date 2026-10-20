import AVFoundation
import CoreMedia

/// Техническая информация о файле для панели «Инфо» и бейджей HDR.
nonisolated struct MediaInfo: Sendable, Equatable {
    var duration: Double = 0
    var hasVideo = false
    var displaySize: CGSize = .zero
    var frameRate: Double = 0
    var videoCodec: String?
    var dynamicRange: String?
    var videoBitrate: Double = 0
    var audioCodec: String?
    var audioChannels: Int = 0
    var sampleRate: Double = 0
    var fileSize: Int64 = 0
    var container: String = ""
    var chapters: [ChapterMark] = []
    var convertedFrom: String?

    var isHDR: Bool { dynamicRange != nil }

    var resolutionLabel: String? {
        guard hasVideo, displaySize.height > 0 else { return nil }
        let h = min(displaySize.width, displaySize.height)
        switch h {
        case 2000...: return "4K"
        case 1400...: return "1440p"
        case 1000...: return "1080p"
        case 700...: return "720p"
        default: return "\(Int(h))p"
        }
    }
}

nonisolated enum AssetInspector {
    @concurrent static func inspect(_ asset: AVURLAsset, original: URL) async throws -> MediaInfo {
        var info = MediaInfo()
        let (duration, playable) = try await asset.load(.duration, .isPlayable)
        guard playable else {
            throw MatroskaError("macOS не умеет воспроизводить этот файл (.\(original.pathExtension))")
        }
        info.duration = duration.seconds.isFinite ? duration.seconds : 0
        info.container = original.pathExtension.uppercased()
        info.fileSize = Int64((try? original.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)

        if let video = try await asset.loadTracks(withMediaType: .video).first {
            let (size, transform, fps, formats, rate) = try await video.load(
                .naturalSize, .preferredTransform, .nominalFrameRate, .formatDescriptions, .estimatedDataRate)
            let rect = CGRect(origin: .zero, size: size).applying(transform)
            info.hasVideo = true
            info.displaySize = CGSize(width: abs(rect.width), height: abs(rect.height))
            info.frameRate = Double(fps)
            info.videoBitrate = Double(rate)
            if let fd = formats.first {
                info.videoCodec = codecName(CMFormatDescriptionGetMediaSubType(fd))
                info.dynamicRange = dynamicRange(fd)
                // Анаморфное видео: учитываем соотношение сторон пикселя.
                if let par = CMFormatDescriptionGetExtension(fd, extensionKey: kCMFormatDescriptionExtension_PixelAspectRatio) as? [CFString: Any],
                   let h = par[kCMFormatDescriptionKey_PixelAspectRatioHorizontalSpacing] as? Double,
                   let v = par[kCMFormatDescriptionKey_PixelAspectRatioVerticalSpacing] as? Double, h > 0, v > 0, h != v {
                    info.displaySize.width *= h / v
                }
            }
        }
        if let audio = try await asset.loadTracks(withMediaType: .audio).first,
           let fd = try await audio.load(.formatDescriptions).first {
            info.audioCodec = codecName(CMFormatDescriptionGetMediaSubType(fd))
            if let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(fd)?.pointee {
                info.audioChannels = Int(asbd.mChannelsPerFrame)
                info.sampleRate = asbd.mSampleRate
            }
        }
        let groups = (try? await asset.loadChapterMetadataGroups(bestMatchingPreferredLanguages: Locale.preferredLanguages)) ?? []
        for (index, group) in groups.enumerated() {
            var title = "Глава \(index + 1)"
            if let item = AVMetadataItem.metadataItems(from: group.items, filteredByIdentifier: .commonIdentifierTitle).first,
               let value = try? await item.load(.stringValue), !value.isEmpty {
                title = value
            }
            info.chapters.append(ChapterMark(title: title, start: group.timeRange.start.seconds))
        }
        return info
    }

    static func codecName(_ code: FourCharCode) -> String {
        switch code {
        case kCMVideoCodecType_H264: "H.264"
        case kCMVideoCodecType_HEVC, kCMVideoCodecType_HEVCWithAlpha: "HEVC"
        case kCMVideoCodecType_AV1: "AV1"
        case kCMVideoCodecType_VP9: "VP9"
        case kCMVideoCodecType_MPEG4Video: "MPEG-4"
        case kCMVideoCodecType_AppleProRes422, kCMVideoCodecType_AppleProRes422HQ, kCMVideoCodecType_AppleProRes422LT,
             kCMVideoCodecType_AppleProRes422Proxy, kCMVideoCodecType_AppleProRes4444, kCMVideoCodecType_AppleProRes4444XQ: "ProRes"
        case 0x64766831, 0x64766865: "HEVC Dolby Vision" // dvh1, dvhe
        case kAudioFormatMPEG4AAC, kAudioFormatMPEG4AAC_HE, kAudioFormatMPEG4AAC_HE_V2: "AAC"
        case kAudioFormatAC3: "AC-3"
        case kAudioFormatEnhancedAC3: "E-AC-3 / Dolby Digital Plus"
        case kAudioFormatMPEGLayer3: "MP3"
        case kAudioFormatAppleLossless: "Apple Lossless"
        case kAudioFormatFLAC: "FLAC"
        case kAudioFormatOpus: "Opus"
        case kAudioFormatLinearPCM: "PCM"
        default: fourCC(code)
        }
    }

    static func fourCC(_ code: FourCharCode) -> String {
        let bytes = [24, 16, 8, 0].map { UInt8((code >> $0) & 0xFF) }
        return String(decoding: bytes, as: UTF8.self).trimmingCharacters(in: .whitespaces)
    }

    static func dynamicRange(_ fd: CMFormatDescription) -> String? {
        let sub = CMFormatDescriptionGetMediaSubType(fd)
        if sub == 0x64766831 || sub == 0x64766865 { return "Dolby Vision" } // dvh1, dvhe
        guard let transfer = CMFormatDescriptionGetExtension(fd, extensionKey: kCMFormatDescriptionExtension_TransferFunction) as? String else { return nil }
        if transfer == (kCMFormatDescriptionTransferFunction_SMPTE_ST_2084_PQ as String) { return "HDR10" }
        if transfer == (kCMFormatDescriptionTransferFunction_ITU_R_2100_HLG as String) { return "HLG" }
        return nil
    }
}
