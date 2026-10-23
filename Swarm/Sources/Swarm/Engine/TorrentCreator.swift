import Foundation

/// Создание .torrent из файла или папки: хеши кусков считаются потоково,
/// поэтому память не зависит от размера раздачи. Форматы: v1, v2 (BEP 52) и гибрид.
nonisolated enum TorrentCreator {
    struct Options: Sendable {
        var trackers: [URL] = []
        var isPrivate = false
        var comment: String?
        /// 0 — подобрать автоматически (~1500 кусков).
        var pieceLength = 0
        var version: MetaVersion = .hybrid
        var webSeeds: [URL] = []
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
            // Сравниваем компоненты уже разрешённых путей: /var и /tmp на macOS — симлинки на /private/…
            let base = source.resolvingSymlinksInPath().pathComponents.count
            let all = (fm.enumerator(at: source, includingPropertiesForKeys: keys, options: [.skipsHiddenFiles])?.allObjects as? [URL]) ?? []
            for url in all {
                let values = try url.resourceValues(forKeys: Set(keys))
                guard values.isRegularFile == true else { continue }
                let rel = Array(url.resolvingSymlinksInPath().pathComponents.dropFirst(base))
                entries.append((url, rel, Int64(values.fileSize ?? 0)))
            }
            // Порядок — побайтовый по компонентам пути, как ключи словаря в дереве файлов v2.
            entries.sort { $0.path.map { Array($0.utf8) }.lexicographicallyPrecedes($1.path.map { Array($0.utf8) }) { $0.lexicographicallyPrecedes($1) } }
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
        let v1 = options.version != .v2
        let v2 = options.version != .v1
        if v2 {
            guard pieceLength >= Merkle.blockSize, pieceLength & (pieceLength - 1) == 0 else {
                throw BencodeError("Для v2 размер куска — степень двойки от 16 КБ")
            }
        }
        let blocksPerPiece = pieceLength / Merkle.blockSize

        var v1Hashes = Data()
        var v1Buffer = Data(capacity: pieceLength)
        var v1Files: [BValue] = []
        var tree: [String: BValue] = [:]
        var layers: [String: BValue] = [:]
        var processed: Int64 = 0

        func addToTree(_ path: [String], _ leaf: BValue) {
            func insert(_ node: [String: BValue], _ rest: ArraySlice<String>) -> [String: BValue] {
                var node = node
                let key = rest.first!
                if rest.count == 1 {
                    node[key] = .dict(["": leaf])
                } else {
                    node[key] = .dict(insert(node[key]?.dict ?? [:], rest.dropFirst()))
                }
                return node
            }
            tree = insert(tree, path[...])
        }

        for (n, entry) in entries.enumerated() {
            let filePath = entry.path.isEmpty ? [name] : entry.path
            // Гибрид: перед каждым непустым файлом (кроме первого) — заполнитель до границы куска.
            if v1 && v2, entry.size > 0, !v1Buffer.isEmpty {
                let pad = pieceLength - v1Buffer.count
                v1Buffer.append(Data(count: pad))
                v1Hashes.append(Hashing.sha1(v1Buffer))
                v1Buffer.removeAll(keepingCapacity: true)
                v1Files.append(.dict(["attr": .str("p"), "length": .int(Int64(pad)), "path": .list([.str(".pad"), .str("\(pad)")])]))
            }
            var leaves: [Data] = []
            var pieceNodes: [Data] = []
            var blockBuffer = Data(capacity: Merkle.blockSize)
            func closeBlock() {
                leaves.append(Hashing.sha256(blockBuffer))
                blockBuffer.removeAll(keepingCapacity: true)
                if leaves.count == blocksPerPiece {
                    pieceNodes.append(Merkle.root(leaves, width: blocksPerPiece))
                    leaves.removeAll()
                }
            }
            let handle = try FileHandle(forReadingFrom: entry.url)
            defer { try? handle.close() }
            while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty {
                if v1 {
                    var rest = chunk[...]
                    while !rest.isEmpty {
                        let take = min(pieceLength - v1Buffer.count, rest.count)
                        v1Buffer.append(rest.prefix(take))
                        rest = rest.dropFirst(take)
                        if v1Buffer.count == pieceLength {
                            v1Hashes.append(Hashing.sha1(v1Buffer))
                            v1Buffer.removeAll(keepingCapacity: true)
                        }
                    }
                }
                if v2 {
                    var rest = chunk[...]
                    while !rest.isEmpty {
                        let take = min(Merkle.blockSize - blockBuffer.count, rest.count)
                        blockBuffer.append(rest.prefix(take))
                        rest = rest.dropFirst(take)
                        if blockBuffer.count == Merkle.blockSize { closeBlock() }
                    }
                }
                processed += Int64(chunk.count)
                progress?(Double(processed) / Double(total))
            }
            if v2 {
                if !blockBuffer.isEmpty { closeBlock() }
                var leaf: [String: BValue] = ["length": .int(entry.size)]
                if entry.size > 0 {
                    let root: Data
                    if pieceNodes.isEmpty {
                        // Файл меньше куска: дерево дополняется до ближайшей степени двойки.
                        root = Merkle.root(leaves, width: Merkle.nextPowerOfTwo(leaves.count))
                    } else {
                        if !leaves.isEmpty { pieceNodes.append(Merkle.root(leaves, width: blocksPerPiece)) }
                        root = Merkle.root(pieceNodes, width: Merkle.nextPowerOfTwo(pieceNodes.count),
                                           pad: Merkle.padHash(levels: Merkle.log2(blocksPerPiece)))
                        if pieceNodes.count > 1 || entry.size > Int64(pieceLength) {
                            layers[Bencode.key(ArraySlice([UInt8](root)))] = .bytes(pieceNodes.reduce(Data(), +))
                        }
                    }
                    leaf["pieces root"] = .bytes(root)
                }
                addToTree(filePath, .dict(leaf))
            }
            if v1, isDir.boolValue {
                v1Files.append(.dict(["length": .int(entry.size), "path": .list(entry.path.map { .str($0) })]))
            }
            _ = n
        }
        if v1, !v1Buffer.isEmpty { v1Hashes.append(Hashing.sha1(v1Buffer)) }

        var info: [String: BValue] = [
            "name": .str(name),
            "piece length": .int(Int64(pieceLength)),
        ]
        if v1 {
            info["pieces"] = .bytes(v1Hashes)
            if isDir.boolValue { info["files"] = .list(v1Files) } else { info["length"] = .int(total) }
        }
        if v2 {
            info["meta version"] = .int(2)
            info["file tree"] = .dict(tree)
        }
        if options.isPrivate { info["private"] = .int(1) }
        let infoData = BValue.dict(info).encoded
        var pieceLayers: [Data: Data] = [:]
        for (k, v) in layers { if let d = v.data { pieceLayers[Data(Bencode.keyBytes(k))] = d } }
        return try Metainfo(infoDictionary: infoData, trackers: options.trackers.map { [$0] },
                            comment: options.comment, createdBy: "Swarm", creationDate: Date(),
                            pieceLayers: pieceLayers, webSeeds: options.webSeeds)
    }
}
