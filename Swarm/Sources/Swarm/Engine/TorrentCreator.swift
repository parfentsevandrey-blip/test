import CryptoKit
import Foundation

/// Создание .torrent из файла или папки: хеши кусков считаются потоково,
/// поэтому память не зависит от размера раздачи.
nonisolated enum TorrentCreator {
    struct Options: Sendable {
        var trackers: [URL] = []
        var isPrivate = false
        var comment: String?
        /// 0 — подобрать автоматически (~1500 кусков).
        var pieceLength = 0
    }

    static func create(from source: URL, options: Options = Options(),
                       progress: (@Sendable (Double) -> Void)? = nil) throws -> Metainfo {
        let fm = FileManager.default
        var isDir: ObjCBool = false
        guard fm.fileExists(atPath: source.path, isDirectory: &isDir) else { throw BencodeError("Нет такого файла") }
        let name = source.lastPathComponent

        var entries: [(url: URL, path: [String], size: Int64)] = []
        if isDir.boolValue {
            let keys: [URLResourceKey] = [.isRegularFileKey, .fileSizeKey]
            let all = (fm.enumerator(at: source, includingPropertiesForKeys: keys, options: [.skipsHiddenFiles])?.allObjects as? [URL]) ?? []
            for url in all.sorted(by: { $0.path < $1.path }) {
                let values = try url.resourceValues(forKeys: Set(keys))
                guard values.isRegularFile == true else { continue }
                let rel = url.path.dropFirst(source.path.count).split(separator: "/").map(String.init)
                entries.append((url, rel, Int64(values.fileSize ?? 0)))
            }
        } else {
            let size = Int64((try source.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0)
            entries = [(source, [], size)]
        }
        let total = entries.reduce(Int64(0)) { $0 + $1.size }
        guard total > 0 else { throw BencodeError("Пустая раздача") }

        var pieceLength = options.pieceLength
        if pieceLength == 0 {
            pieceLength = 16 * 1024
            while Int64(pieceLength) * 1500 < total && pieceLength < 16 * 1024 * 1024 { pieceLength *= 2 }
        }

        var hashes = Data()
        var buffer = Data(capacity: pieceLength)
        var processed: Int64 = 0
        for entry in entries {
            let handle = try FileHandle(forReadingFrom: entry.url)
            defer { try? handle.close() }
            while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty {
                var rest = chunk[...]
                while !rest.isEmpty {
                    let take = min(pieceLength - buffer.count, rest.count)
                    buffer.append(rest.prefix(take))
                    rest = rest.dropFirst(take)
                    if buffer.count == pieceLength {
                        hashes.append(Data(Insecure.SHA1.hash(data: buffer)))
                        buffer.removeAll(keepingCapacity: true)
                    }
                }
                processed += Int64(chunk.count)
                progress?(Double(processed) / Double(total))
            }
        }
        if !buffer.isEmpty { hashes.append(Data(Insecure.SHA1.hash(data: buffer))) }

        var info: [String: BValue] = [
            "name": .str(name),
            "piece length": .int(Int64(pieceLength)),
            "pieces": .bytes(hashes),
        ]
        if isDir.boolValue {
            info["files"] = .list(entries.map { .dict(["length": .int($0.size), "path": .list($0.path.map { .str($0) })]) })
        } else {
            info["length"] = .int(total)
        }
        if options.isPrivate { info["private"] = .int(1) }
        let infoData = BValue.dict(info).encoded
        return try Metainfo(infoDictionary: infoData, trackers: options.trackers.map { [$0] },
                            comment: options.comment, createdBy: "Swarm", creationDate: Date())
    }
}
