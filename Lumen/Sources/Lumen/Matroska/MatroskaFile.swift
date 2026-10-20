import Foundation

nonisolated struct MKVColour: Sendable {
    var matrix: UInt64?
    var transfer: UInt64?
    var primaries: UInt64?
    var range: UInt64?
}

nonisolated struct MKVTrack: Sendable {
    enum Kind: Sendable { case video, audio, subtitle, other }

    var number: UInt64 = 0
    var type: UInt64 = 0
    var codecID = ""
    var codecPrivate: Data?
    var name: String?
    var language = "eng"
    var languageBCP47: String?
    var isEnabled = true
    var isDefault = true
    var isForced = false
    var defaultDuration: UInt64?
    var codecDelay: UInt64 = 0

    var pixelWidth: Int = 0
    var pixelHeight: Int = 0
    var displayWidth: Int?
    var displayHeight: Int?
    var displayUnit: UInt64 = 0
    var colour: MKVColour?

    var samplingFrequency: Double = 8000
    var outputSamplingFrequency: Double?
    var channels: Int = 1
    var bitDepth: Int?

    /// Сжатие «header stripping»: эти байты нужно вернуть в начало каждого кадра.
    var strippedHeader: Data?
    /// Блоки сжаты zlib (встречается у субтитров).
    var zlibCompressed = false

    var kind: Kind {
        switch type {
        case 1: .video
        case 2: .audio
        case 17: .subtitle
        default: .other
        }
    }

    var displayLanguage: String {
        let code = languageBCP47 ?? language
        return Locale.current.localizedString(forLanguageCode: code)?.capitalized ?? code
    }

    var label: String {
        if let name, !name.isEmpty { return "\(name) · \(displayLanguage)" }
        return displayLanguage
    }
}

nonisolated struct MKVChapter: Sendable {
    var title: String
    var start: Double
    var end: Double?
}

/// Ссылка на блок внутри файла. Сами кадры не копируются до момента записи.
nonisolated struct MKVBlockRef {
    var track: UInt32
    var flags: UInt8
    var isKey: Bool
    /// Время блока в наносекундах.
    var time: Int64
    /// Длительность из BlockGroup (нс), если указана.
    var duration: Int64?
    /// Смещение полезной нагрузки (после заголовка блока) и её длина.
    var payloadOffset: Int
    var payloadSize: Int

    var lacing: UInt8 { (flags >> 1) & 0x03 }
}

/// Разобранный файл Matroska / WebM: дорожки, главы и полный индекс блоков.
nonisolated final class MatroskaFile: @unchecked Sendable {
    let file: MappedFile
    private(set) var docType = "matroska"
    private(set) var timecodeScale: UInt64 = 1_000_000
    private(set) var durationSeconds: Double = 0
    private(set) var title: String?
    private(set) var tracks: [MKVTrack] = []
    private(set) var chapters: [MKVChapter] = []
    private(set) var blocks: [MKVBlockRef] = []

    /// - Parameter progress: доля прочитанного файла при индексации (0…1).
    init(url: URL, progress: ((Double) -> Void)? = nil) throws {
        file = try MappedFile(url: url)
        try parse(progress: progress)
    }

    func track(number: UInt64) -> MKVTrack? { tracks.first { $0.number == number } }

    // MARK: - Разбор

    private func parse(progress: ((Double) -> Void)?) throws {
        var cursor = EBMLCursor(file: file)
        guard let header = cursor.nextElement(), header.id == MKV.ebmlHeader else {
            throw MatroskaError("Это не файл Matroska/WebM")
        }
        var h = cursor.children(of: header)
        while let e = h.nextElement() {
            if e.id == MKV.docType { docType = h.string(e) }
            h.offset = e.dataEnd
        }
        cursor.offset = header.dataEnd

        // Пропускаем возможный мусор до сегмента.
        var segment: EBMLElement?
        while let e = cursor.nextElement() {
            if e.id == MKV.segment { segment = e; break }
            if e.unknownSize { break }
            cursor.offset = e.dataEnd
        }
        guard let segment else { throw MatroskaError("В файле нет сегмента Matroska") }

        var seg = cursor.children(of: segment)
        var lastReport = 0
        while let e = seg.nextElement() {
            switch e.id {
            case MKV.info: parseInfo(e)
            case MKV.tracks: parseTracks(e)
            case MKV.chapters: parseChapters(e)
            case MKV.cluster:
                let end = indexCluster(e)
                seg.offset = end
                if let progress, end - lastReport > 32 << 20 {
                    lastReport = end
                    progress(Double(end) / Double(file.size))
                }
                continue
            default:
                if e.unknownSize { return } // неизвестный элемент без размера — дальше не пройти
            }
            seg.offset = e.dataEnd
        }
        if tracks.isEmpty { throw MatroskaError("В файле нет дорожек") }
    }

    private func parseInfo(_ element: EBMLElement) {
        var c = EBMLCursor(file: file).children(of: element)
        var rawDuration: Double = 0
        while let e = c.nextElement() {
            switch e.id {
            case MKV.timecodeScale: timecodeScale = max(1, c.uint(e))
            case MKV.duration: rawDuration = c.float(e)
            case MKV.title: title = c.string(e)
            default: break
            }
            c.offset = e.dataEnd
        }
        durationSeconds = rawDuration * Double(timecodeScale) / 1e9
    }

    private func parseTracks(_ element: EBMLElement) {
        var c = EBMLCursor(file: file).children(of: element)
        while let e = c.nextElement() {
            if e.id == MKV.trackEntry { tracks.append(parseTrack(e)) }
            c.offset = e.dataEnd
        }
    }

    private func parseTrack(_ element: EBMLElement) -> MKVTrack {
        var t = MKVTrack()
        var c = EBMLCursor(file: file).children(of: element)
        while let e = c.nextElement() {
            switch e.id {
            case MKV.trackNumber: t.number = c.uint(e)
            case MKV.trackType: t.type = c.uint(e)
            case MKV.flagEnabled: t.isEnabled = c.uint(e) != 0
            case MKV.flagDefault: t.isDefault = c.uint(e) != 0
            case MKV.flagForced: t.isForced = c.uint(e) != 0
            case MKV.name: t.name = c.string(e)
            case MKV.language: t.language = c.string(e)
            case MKV.languageBCP47: t.languageBCP47 = c.string(e)
            case MKV.codecID: t.codecID = c.string(e)
            case MKV.codecPrivate: t.codecPrivate = c.binary(e)
            case MKV.defaultDuration: t.defaultDuration = c.uint(e)
            case MKV.codecDelay: t.codecDelay = c.uint(e)
            case MKV.video: parseVideo(e, into: &t)
            case MKV.audio: parseAudio(e, into: &t)
            case MKV.contentEncodings: parseEncodings(e, into: &t)
            default: break
            }
            c.offset = e.dataEnd
        }
        return t
    }

    private func parseVideo(_ element: EBMLElement, into t: inout MKVTrack) {
        var c = EBMLCursor(file: file).children(of: element)
        while let e = c.nextElement() {
            switch e.id {
            case MKV.pixelWidth: t.pixelWidth = Int(c.uint(e))
            case MKV.pixelHeight: t.pixelHeight = Int(c.uint(e))
            case MKV.displayWidth: t.displayWidth = Int(c.uint(e))
            case MKV.displayHeight: t.displayHeight = Int(c.uint(e))
            case MKV.displayUnit: t.displayUnit = c.uint(e)
            case MKV.colour:
                var colour = MKVColour()
                var cc = c.children(of: e)
                while let ce = cc.nextElement() {
                    switch ce.id {
                    case MKV.matrixCoefficients: colour.matrix = cc.uint(ce)
                    case MKV.transferCharacteristics: colour.transfer = cc.uint(ce)
                    case MKV.primaries: colour.primaries = cc.uint(ce)
                    case MKV.range: colour.range = cc.uint(ce)
                    default: break
                    }
                    cc.offset = ce.dataEnd
                }
                t.colour = colour
            default: break
            }
            c.offset = e.dataEnd
        }
    }

    private func parseAudio(_ element: EBMLElement, into t: inout MKVTrack) {
        var c = EBMLCursor(file: file).children(of: element)
        while let e = c.nextElement() {
            switch e.id {
            case MKV.samplingFrequency: t.samplingFrequency = c.float(e)
            case MKV.outputSamplingFrequency: t.outputSamplingFrequency = c.float(e)
            case MKV.channels: t.channels = max(1, Int(c.uint(e)))
            case MKV.bitDepth: t.bitDepth = Int(c.uint(e))
            default: break
            }
            c.offset = e.dataEnd
        }
    }

    private func parseEncodings(_ element: EBMLElement, into t: inout MKVTrack) {
        var c = EBMLCursor(file: file).children(of: element)
        while let enc = c.nextElement() {
            if enc.id == MKV.contentEncoding {
                var ec = c.children(of: enc)
                while let comp = ec.nextElement() {
                    if comp.id == MKV.contentCompression {
                        var algo: UInt64 = 0
                        var settings: Data?
                        var cc = ec.children(of: comp)
                        while let x = cc.nextElement() {
                            if x.id == MKV.contentCompAlgo { algo = cc.uint(x) }
                            if x.id == MKV.contentCompSettings { settings = cc.binary(x) }
                            cc.offset = x.dataEnd
                        }
                        if algo == 3 { t.strippedHeader = settings }
                        if algo == 0 { t.zlibCompressed = true }
                    }
                    ec.offset = comp.dataEnd
                }
            }
            c.offset = enc.dataEnd
        }
    }

    private func parseChapters(_ element: EBMLElement) {
        guard chapters.isEmpty else { return }
        var c = EBMLCursor(file: file).children(of: element)
        while let edition = c.nextElement() {
            defer { c.offset = edition.dataEnd }
            guard edition.id == MKV.editionEntry, chapters.isEmpty else { continue }
            var ec = c.children(of: edition)
            while let atom = ec.nextElement() {
                defer { ec.offset = atom.dataEnd }
                guard atom.id == MKV.chapterAtom else { continue }
                var start: UInt64 = 0
                var end: UInt64?
                var hidden = false
                var title = ""
                var ac = ec.children(of: atom)
                while let x = ac.nextElement() {
                    switch x.id {
                    case MKV.chapterTimeStart: start = ac.uint(x)
                    case MKV.chapterTimeEnd: end = ac.uint(x)
                    case MKV.chapterFlagHidden: hidden = ac.uint(x) != 0
                    case MKV.chapterDisplay where title.isEmpty:
                        var dc = ac.children(of: x)
                        while let d = dc.nextElement() {
                            if d.id == MKV.chapString { title = dc.string(d) }
                            dc.offset = d.dataEnd
                        }
                    default: break
                    }
                    ac.offset = x.dataEnd
                }
                if !hidden {
                    chapters.append(MKVChapter(title: title, start: Double(start) / 1e9, end: end.map { Double($0) / 1e9 }))
                }
            }
        }
        chapters.sort { $0.start < $1.start }
    }

    /// Индексирует блоки кластера и возвращает смещение его конца.
    private func indexCluster(_ cluster: EBMLElement) -> Int {
        var c = EBMLCursor(file: file).children(of: cluster)
        var clusterTime: Int64 = 0
        let scale = Int64(timecodeScale)
        while true {
            let before = c.offset
            guard let e = c.nextElement() else { return c.limit }
            if cluster.unknownSize, MKV.topLevel.contains(e.id) { return before }
            switch e.id {
            case MKV.clusterTimecode:
                clusterTime = Int64(c.uint(e))
            case MKV.simpleBlock:
                if var ref = blockRef(start: e.dataStart, end: e.dataEnd, clusterTime: clusterTime, scale: scale) {
                    ref.isKey = ref.flags & 0x80 != 0
                    blocks.append(ref)
                }
            case MKV.blockGroup:
                var g = c.children(of: e)
                var ref: MKVBlockRef?
                var hasReference = false
                var duration: Int64?
                while let x = g.nextElement() {
                    switch x.id {
                    case MKV.block: ref = blockRef(start: x.dataStart, end: x.dataEnd, clusterTime: clusterTime, scale: scale)
                    case MKV.referenceBlock: hasReference = true
                    case MKV.blockDuration: duration = Int64(g.uint(x)) * scale
                    default: break
                    }
                    g.offset = x.dataEnd
                }
                if var ref {
                    ref.isKey = !hasReference
                    ref.duration = duration
                    blocks.append(ref)
                }
            default:
                if e.unknownSize { return c.limit }
            }
            c.offset = e.dataEnd
        }
    }

    private func blockRef(start: Int, end: Int, clusterTime: Int64, scale: Int64) -> MKVBlockRef? {
        var c = EBMLCursor(file: file, offset: start, limit: end)
        guard let track = c.readVInt(), c.offset + 3 <= end else { return nil }
        let rel = Int16(bitPattern: UInt16(file.byte(c.offset)) << 8 | UInt16(file.byte(c.offset + 1)))
        let flags = file.byte(c.offset + 2)
        let payload = c.offset + 3
        return MKVBlockRef(track: UInt32(truncatingIfNeeded: track.value),
                           flags: flags,
                           isKey: false,
                           time: (clusterTime + Int64(rel)) * scale,
                           duration: nil,
                           payloadOffset: payload,
                           payloadSize: end - payload)
    }

    // MARK: - Кадры

    /// Разбивает блок на кадры с учётом лейсинга (Xiph, фиксированный, EBML).
    func frames(of block: MKVBlockRef) -> [Range<Int>] {
        let start = block.payloadOffset
        let end = start + block.payloadSize
        guard block.lacing != 0, block.payloadSize > 0 else { return [start..<end] }

        let count = Int(file.byte(start)) + 1
        var offset = start + 1
        var sizes: [Int] = []
        sizes.reserveCapacity(count)

        switch block.lacing {
        case 1: // Xiph
            for _ in 0..<(count - 1) {
                var size = 0
                while offset < end {
                    let b = Int(file.byte(offset)); offset += 1
                    size += b
                    if b != 255 { break }
                }
                sizes.append(size)
            }
        case 3: // EBML
            var c = EBMLCursor(file: file, offset: offset, limit: end)
            guard let first = c.readVInt() else { return [start..<end] }
            var size = Int(first.value)
            sizes.append(size)
            for _ in 1..<max(1, count - 1) where count > 2 {
                guard let v = c.readVInt() else { break }
                let bias = (Int64(1) << (7 * Int64(v.length) - 1)) - 1
                size += Int(Int64(v.value) - bias)
                sizes.append(size)
            }
            offset = c.offset
        default: // фиксированный размер
            let total = end - offset
            let each = total / count
            return (0..<count).map { (offset + $0 * each)..<(offset + ($0 + 1) * each) }
        }

        var ranges: [Range<Int>] = []
        ranges.reserveCapacity(count)
        for size in sizes {
            let r = offset..<min(end, offset + max(0, size))
            ranges.append(r)
            offset = r.upperBound
        }
        if offset <= end { ranges.append(offset..<end) }
        return ranges
    }
}
