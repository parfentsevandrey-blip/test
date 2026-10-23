import Foundation

/// Потоковое чтение блоков Matroska с любого кластера: кластер за кластером, блок за блоком.
/// Читается только то, что сейчас нужно плееру, — через отображённый в память файл.
nonisolated struct MKVBlockReader {
    let mkv: MatroskaFile
    private var offset: Int
    /// Текущий кластер: время и граница; `inCluster == false` — между кластерами.
    private var clusterTime: Int64 = 0
    private var clusterEnd = 0
    private var clusterUnknownSize = false
    private var inCluster = false
    private(set) var atEnd = false

    init(mkv: MatroskaFile, startingAt offset: Int) {
        self.mkv = mkv
        self.offset = offset
    }

    /// Позиция для продолжения (например, чтобы запомнить, где остановились).
    var position: Int { offset }

    /// Следующий блок или nil в конце файла.
    mutating func next() -> MKVBlockRef? {
        let file = mkv.file
        let scale = Int64(mkv.timecodeScale)
        while !atEnd {
            if !inCluster {
                // Ищем следующий кластер среди элементов верхнего уровня сегмента.
                var c = EBMLCursor(file: file, offset: offset, limit: mkv.segmentEnd)
                guard let e = c.nextElement() else { atEnd = true; return nil }
                if e.id == MKV.cluster {
                    inCluster = true
                    clusterUnknownSize = e.unknownSize
                    clusterEnd = e.dataEnd
                    clusterTime = 0
                    offset = e.dataStart
                    continue
                }
                if e.unknownSize { atEnd = true; return nil }
                // Что-то другое (Cues, Tags…) — перепрыгиваем; мусор между элементами — побайтно ищем кластер.
                offset = e.dataEnd > offset ? e.dataEnd : offset + 1
                continue
            }
            var c = EBMLCursor(file: file, offset: offset, limit: clusterEnd)
            let before = offset
            guard let e = c.nextElement() else {
                // Кластер кончился: следующий элемент верхнего уровня начинается ровно на его границе.
                inCluster = false
                offset = clusterEnd
                if clusterUnknownSize || before >= mkv.segmentEnd { atEnd = true }
                continue
            }
            if clusterUnknownSize, MKV.topLevel.contains(e.id) {
                // Кластер без размера закончился там, где начался следующий элемент верхнего уровня.
                inCluster = false
                offset = before
                continue
            }
            offset = e.dataEnd
            switch e.id {
            case MKV.clusterTimecode:
                clusterTime = Int64(c.uint(e))
            case MKV.simpleBlock:
                if var ref = mkv.blockRef(start: e.dataStart, end: e.dataEnd, clusterTime: clusterTime, scale: scale) {
                    ref.isKey = ref.flags & 0x80 != 0
                    return ref
                }
            case MKV.blockGroup:
                var g = c.children(of: e)
                var ref: MKVBlockRef?
                var hasReference = false
                var duration: Int64?
                while let x = g.nextElement() {
                    switch x.id {
                    case MKV.block: ref = mkv.blockRef(start: x.dataStart, end: x.dataEnd, clusterTime: clusterTime, scale: scale)
                    case MKV.referenceBlock: hasReference = true
                    case MKV.blockDuration: duration = Int64(g.uint(x)) * scale
                    default: break
                    }
                    g.offset = x.dataEnd
                }
                if var ref {
                    ref.isKey = !hasReference
                    ref.duration = duration
                    return ref
                }
            default:
                if e.unknownSize { atEnd = true; return nil }
            }
        }
        return nil
    }
}

/// Индекс кластеров для файлов без Cues: быстрый проход по заголовкам кластеров (не по кадрам).
nonisolated enum MKVClusterIndex {
    struct Entry: Sendable {
        var offset: Int
        var time: Int64
    }

    /// Для кластеров известной длины читаем только их начало; кластеры без длины (запись трансляции)
    /// приходится проходить по блокам — это всё равно без чтения самих кадров.
    static func build(_ mkv: MatroskaFile, cancelled: () -> Bool = { false }) -> [Entry] {
        guard let first = mkv.firstCluster else { return [] }
        var out: [Entry] = []
        var offset = first
        let scale = Int64(mkv.timecodeScale)
        while offset < mkv.segmentEnd, !cancelled() {
            var c = EBMLCursor(file: mkv.file, offset: offset, limit: mkv.segmentEnd)
            guard let e = c.nextElement() else { break }
            if e.id == MKV.cluster {
                var inner = c.children(of: e)
                var time: Int64 = 0
                if let t = inner.nextElement(), t.id == MKV.clusterTimecode { time = Int64(inner.uint(t)) * scale }
                out.append(Entry(offset: e.start, time: time))
                if e.unknownSize {
                    // Ищем конец: следующий элемент верхнего уровня.
                    var scan = EBMLCursor(file: mkv.file, offset: e.dataStart, limit: mkv.segmentEnd)
                    var end = mkv.segmentEnd
                    while let x = scan.nextElement() {
                        if MKV.topLevel.contains(x.id) { end = x.start; break }
                        if x.unknownSize { break }
                        scan.offset = x.dataEnd
                    }
                    offset = end
                    continue
                }
            } else if e.unknownSize {
                break
            }
            offset = max(e.dataEnd, offset + 1)
        }
        return out
    }
}
