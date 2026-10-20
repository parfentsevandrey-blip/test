import Foundation

/// Ошибка разбора контейнера.
nonisolated struct MatroskaError: LocalizedError, Sendable {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/// Файл, отображённый в память только для чтения. Даже 50-гигабайтный ремукс
/// не копирует файл целиком: ядро подкачивает страницы по мере чтения.
nonisolated final class MappedFile: @unchecked Sendable {
    let base: UnsafeRawPointer
    let size: Int

    init(url: URL) throws {
        let fd = open(url.path, O_RDONLY)
        guard fd >= 0 else { throw MatroskaError("Не удалось открыть файл") }
        defer { close(fd) }
        var st = stat()
        guard fstat(fd, &st) == 0, st.st_size > 0 else { throw MatroskaError("Пустой файл") }
        size = Int(st.st_size)
        guard let p = mmap(nil, size, PROT_READ, MAP_PRIVATE, fd, 0), p != MAP_FAILED else {
            throw MatroskaError("Не удалось отобразить файл в память")
        }
        // Последовательное чтение: подсказываем ядру читать вперёд.
        madvise(p, size, MADV_SEQUENTIAL)
        base = UnsafeRawPointer(p)
    }

    deinit { munmap(UnsafeMutableRawPointer(mutating: base), size) }

    @inline(__always) func byte(_ offset: Int) -> UInt8 {
        base.load(fromByteOffset: offset, as: UInt8.self)
    }

    func data(_ offset: Int, _ count: Int) -> Data {
        Data(bytes: base + offset, count: count)
    }
}

/// Элемент EBML: идентификатор и границы полезной нагрузки.
nonisolated struct EBMLElement {
    let id: UInt32
    let start: Int
    let dataStart: Int
    let dataEnd: Int
    let unknownSize: Bool
}

/// Последовательный читатель EBML поверх отображённого файла.
nonisolated struct EBMLCursor {
    let file: MappedFile
    var offset: Int
    var limit: Int

    init(file: MappedFile, offset: Int = 0, limit: Int? = nil) {
        self.file = file
        self.offset = offset
        self.limit = min(limit ?? file.size, file.size)
    }

    var atEnd: Bool { offset >= limit }

    /// Идентификатор элемента (маркерные биты сохраняются, как в спецификации).
    mutating func readID() -> UInt32? {
        guard offset < limit else { return nil }
        let first = file.byte(offset)
        let length: Int
        if first & 0x80 != 0 { length = 1 }
        else if first & 0x40 != 0 { length = 2 }
        else if first & 0x20 != 0 { length = 3 }
        else if first & 0x10 != 0 { length = 4 }
        else { return nil }
        guard offset + length <= limit else { return nil }
        var value: UInt32 = 0
        for i in 0..<length { value = (value << 8) | UInt32(file.byte(offset + i)) }
        offset += length
        return value
    }

    /// Целое переменной длины (размер элемента или номер дорожки).
    mutating func readVInt() -> (value: UInt64, length: Int, allOnes: Bool)? {
        guard offset < limit else { return nil }
        let first = file.byte(offset)
        guard first != 0 else { return nil }
        let length = first.leadingZeroBitCount + 1
        guard offset + length <= limit else { return nil }
        let mask = UInt8(0xFF) >> length
        var value = UInt64(first & mask)
        var allOnes = (first & mask) == mask
        for i in 1..<length {
            let b = file.byte(offset + i)
            value = (value << 8) | UInt64(b)
            if b != 0xFF { allOnes = false }
        }
        offset += length
        return (value, length, allOnes)
    }

    /// Следующий элемент в пределах `limit`. Неизвестный размер растягивается до `limit`.
    mutating func nextElement() -> EBMLElement? {
        let start = offset
        guard let id = readID(), let size = readVInt() else { return nil }
        let dataStart = offset
        if size.allOnes {
            return EBMLElement(id: id, start: start, dataStart: dataStart, dataEnd: limit, unknownSize: true)
        }
        let end = dataStart + Int(clamping: size.value)
        guard end <= limit, end >= dataStart else {
            // Обрезанный файл: отдаём то, что есть.
            return EBMLElement(id: id, start: start, dataStart: dataStart, dataEnd: limit, unknownSize: false)
        }
        return EBMLElement(id: id, start: start, dataStart: dataStart, dataEnd: end, unknownSize: false)
    }

    func children(of element: EBMLElement) -> EBMLCursor {
        EBMLCursor(file: file, offset: element.dataStart, limit: element.dataEnd)
    }

    // MARK: Значения

    func uint(_ e: EBMLElement) -> UInt64 {
        var v: UInt64 = 0
        for i in e.dataStart..<min(e.dataEnd, e.dataStart + 8) { v = (v << 8) | UInt64(file.byte(i)) }
        return v
    }

    func int(_ e: EBMLElement) -> Int64 {
        let count = e.dataEnd - e.dataStart
        guard count > 0 else { return 0 }
        let raw = uint(e)
        let bits = min(count, 8) * 8
        if bits == 64 { return Int64(bitPattern: raw) }
        let sign = UInt64(1) << (bits - 1)
        return raw & sign != 0 ? Int64(bitPattern: raw | ~((sign << 1) &- 1)) : Int64(raw)
    }

    func float(_ e: EBMLElement) -> Double {
        switch e.dataEnd - e.dataStart {
        case 4: return Double(Float(bitPattern: UInt32(truncatingIfNeeded: uint(e))))
        case 8: return Double(bitPattern: uint(e))
        default: return 0
        }
    }

    func string(_ e: EBMLElement) -> String {
        let d = file.data(e.dataStart, e.dataEnd - e.dataStart)
        let trimmed = d.prefix { $0 != 0 }
        return String(decoding: trimmed, as: UTF8.self)
    }

    func binary(_ e: EBMLElement) -> Data {
        file.data(e.dataStart, e.dataEnd - e.dataStart)
    }
}

/// Идентификаторы элементов Matroska, которые мы понимаем.
nonisolated enum MKV {
    static let ebmlHeader: UInt32 = 0x1A45DFA3
    static let docType: UInt32 = 0x4282
    static let segment: UInt32 = 0x18538067

    static let seekHead: UInt32 = 0x114D9B74
    static let info: UInt32 = 0x1549A966
    static let timecodeScale: UInt32 = 0x2AD7B1
    static let duration: UInt32 = 0x4489
    static let title: UInt32 = 0x7BA9

    static let tracks: UInt32 = 0x1654AE6B
    static let trackEntry: UInt32 = 0xAE
    static let trackNumber: UInt32 = 0xD7
    static let trackType: UInt32 = 0x83
    static let flagEnabled: UInt32 = 0xB9
    static let flagDefault: UInt32 = 0x88
    static let flagForced: UInt32 = 0x55AA
    static let name: UInt32 = 0x536E
    static let language: UInt32 = 0x22B59C
    static let languageBCP47: UInt32 = 0x22B59D
    static let codecID: UInt32 = 0x86
    static let codecPrivate: UInt32 = 0x63A2
    static let defaultDuration: UInt32 = 0x23E383
    static let codecDelay: UInt32 = 0x56AA

    static let video: UInt32 = 0xE0
    static let pixelWidth: UInt32 = 0xB0
    static let pixelHeight: UInt32 = 0xBA
    static let displayWidth: UInt32 = 0x54B0
    static let displayHeight: UInt32 = 0x54BA
    static let displayUnit: UInt32 = 0x54B2
    static let colour: UInt32 = 0x55B0
    static let matrixCoefficients: UInt32 = 0x55B1
    static let range: UInt32 = 0x55B9
    static let transferCharacteristics: UInt32 = 0x55BA
    static let primaries: UInt32 = 0x55BB

    static let audio: UInt32 = 0xE1
    static let samplingFrequency: UInt32 = 0xB5
    static let outputSamplingFrequency: UInt32 = 0x78B5
    static let channels: UInt32 = 0x9F
    static let bitDepth: UInt32 = 0x6264

    static let contentEncodings: UInt32 = 0x6D80
    static let contentEncoding: UInt32 = 0x6240
    static let contentCompression: UInt32 = 0x5034
    static let contentCompAlgo: UInt32 = 0x4254
    static let contentCompSettings: UInt32 = 0x4255

    static let cluster: UInt32 = 0x1F43B675
    static let clusterTimecode: UInt32 = 0xE7
    static let simpleBlock: UInt32 = 0xA3
    static let blockGroup: UInt32 = 0xA0
    static let block: UInt32 = 0xA1
    static let blockDuration: UInt32 = 0x9B
    static let referenceBlock: UInt32 = 0xFB

    static let cues: UInt32 = 0x1C53BB6B
    static let chapters: UInt32 = 0x1043A770
    static let editionEntry: UInt32 = 0x45B9
    static let chapterAtom: UInt32 = 0xB6
    static let chapterTimeStart: UInt32 = 0x91
    static let chapterTimeEnd: UInt32 = 0x92
    static let chapterFlagHidden: UInt32 = 0x98
    static let chapterDisplay: UInt32 = 0x80
    static let chapString: UInt32 = 0x85
    static let tags: UInt32 = 0x1254C367
    static let attachments: UInt32 = 0x1941A469

    /// Элементы верхнего уровня сегмента: на них заканчивается кластер неизвестной длины.
    static let topLevel: Set<UInt32> = [seekHead, info, tracks, cluster, cues, chapters, tags, attachments, segment, ebmlHeader]
}
