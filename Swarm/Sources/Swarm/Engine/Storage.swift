import CryptoKit
import Foundation

/// Файлы раздачи на диске. Куски отображаются на файлы по смещениям;
/// запись и чтение идут через pwrite/pread без промежуточных буферов.
actor Storage {
    let meta: Metainfo
    let root: URL
    private var descriptors: [Int: Int32] = [:]

    init(meta: Metainfo, root: URL) {
        self.meta = meta
        self.root = root
    }

    nonisolated func url(of file: TorrentFile) -> URL {
        file.path.reduce(root) { $0.appendingPathComponent($1) }
    }

    nonisolated func isPadding(_ file: TorrentFile) -> Bool { file.path.contains(".pad") }

    func write(piece: Int, begin: Int, data: Data) throws {
        var offset = Int64(piece) * Int64(meta.pieceLength) + Int64(begin)
        var cursor = data.startIndex
        for (index, file) in segments(from: offset, length: data.count) {
            let inFile = offset - file.offset
            let count = Int(min(Int64(data.endIndex - cursor), file.length - inFile))
            if !isPadding(file) {
                let fd = try descriptor(index, create: true)
                let written = data[cursor..<(cursor + count)].withUnsafeBytes { pwrite(fd, $0.baseAddress, count, off_t(inFile)) }
                guard written == count else { throw PeerError("Не удалось записать на диск: \(String(cString: strerror(errno)))") }
            }
            cursor += count
            offset += Int64(count)
        }
    }

    func read(piece: Int, begin: Int, length: Int) throws -> Data {
        var out = Data(count: length)
        var offset = Int64(piece) * Int64(meta.pieceLength) + Int64(begin)
        var cursor = 0
        for (index, file) in segments(from: offset, length: length) {
            let inFile = offset - file.offset
            let count = Int(min(Int64(length - cursor), file.length - inFile))
            if !isPadding(file) {
                guard let fd = try? descriptor(index, create: false) else { throw PeerError("Файл отсутствует") }
                let read = out.withUnsafeMutableBytes { pread(fd, $0.baseAddress! + cursor, count, off_t(inFile)) }
                guard read == count else { throw PeerError("Файл короче ожидаемого") }
            }
            cursor += count
            offset += Int64(count)
        }
        return out
    }

    func verify(piece: Int) -> Bool {
        guard let data = try? read(piece: piece, begin: 0, length: meta.pieceSize(piece)) else { return false }
        return Data(Insecure.SHA1.hash(data: data)) == meta.pieceHash(piece)
    }

    /// Проверка уже скачанного: хешируются только куски, для которых файлы на месте.
    func check(progress: @Sendable (Double) -> Void) -> Bitfield {
        var have = Bitfield(count: meta.pieceCount)
        let fm = FileManager.default
        let present = meta.files.map { file -> Bool in
            if isPadding(file) { return true }
            let attributes = try? fm.attributesOfItem(atPath: url(of: file).path)
            return ((attributes?[.size] as? NSNumber)?.int64Value ?? 0) > 0
        }
        guard present.contains(true) else { return have }
        for piece in 0..<meta.pieceCount {
            let start = Int64(piece) * Int64(meta.pieceLength)
            let filesOK = segments(from: start, length: meta.pieceSize(piece)).allSatisfy { present[$0.0] }
            if filesOK && verify(piece: piece) { have[piece] = true }
            if piece % 64 == 0 { progress(Double(piece) / Double(meta.pieceCount)) }
        }
        return have
    }

    func closeAll() {
        for fd in descriptors.values { close(fd) }
        descriptors.removeAll()
    }

    func deleteFiles() {
        closeAll()
        let fm = FileManager.default
        for file in meta.files { try? fm.removeItem(at: url(of: file)) }
        if meta.isMultiFile { try? fm.removeItem(at: root.appendingPathComponent(meta.name)) }
    }

    /// Пары (номер файла, файл), которые пересекает диапазон.
    private func segments(from offset: Int64, length: Int) -> [(Int, TorrentFile)] {
        let end = offset + Int64(length)
        var lo = 0, hi = meta.files.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if meta.files[mid].offset <= offset { lo = mid } else { hi = mid - 1 }
        }
        var result: [(Int, TorrentFile)] = []
        var i = lo
        while i < meta.files.count, meta.files[i].offset < end {
            let f = meta.files[i]
            if f.length > 0, f.offset + f.length > offset { result.append((i, f)) }
            i += 1
        }
        return result
    }

    private func descriptor(_ index: Int, create: Bool) throws -> Int32 {
        if let fd = descriptors[index] { return fd }
        let fileURL = url(of: meta.files[index])
        if create {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        }
        let fd = open(fileURL.path, create ? (O_RDWR | O_CREAT) : O_RDWR, 0o644)
        guard fd >= 0 else { throw PeerError("Нет доступа к файлу \(fileURL.lastPathComponent)") }
        descriptors[index] = fd
        return fd
    }
}
