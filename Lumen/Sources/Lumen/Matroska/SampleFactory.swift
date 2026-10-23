import CoreMedia
import Foundation

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

    /// Кадр нужен декодеру (на него ссылаются следующие), но показывать его не надо — так работает точная перемотка.
    static func markDoNotDisplay(_ sample: CMSampleBuffer) {
        guard let attachments = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: true),
              CFArrayGetCount(attachments) > 0 else { return }
        let dict = unsafeBitCast(CFArrayGetValueAtIndex(attachments, 0), to: CFMutableDictionary.self)
        CFDictionarySetValue(dict,
                             Unmanaged.passUnretained(kCMSampleAttachmentKey_DoNotDisplay).toOpaque(),
                             Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
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
