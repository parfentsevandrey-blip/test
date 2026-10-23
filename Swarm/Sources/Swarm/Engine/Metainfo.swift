import CryptoKit
import Foundation

/// 20-байтовый SHA-1 словаря info — идентификатор раздачи.
nonisolated struct InfoHash: Hashable, Sendable, Codable, CustomStringConvertible {
    let bytes: Data

    init?(_ bytes: Data) {
        guard bytes.count == 20 else { return nil }
        self.bytes = bytes
    }

    init?(hex: String) {
        let chars = Array(hex.lowercased())
        guard chars.count == 40 else { return nil }
        var out = Data(capacity: 20)
        for i in stride(from: 0, to: 40, by: 2) {
            guard let b = UInt8(String(chars[i...i + 1]), radix: 16) else { return nil }
            out.append(b)
        }
        bytes = out
    }

    var hex: String { bytes.map { String(format: "%02x", $0) }.joined() }
    var description: String { hex }
}

nonisolated struct TorrentFile: Sendable, Hashable {
    let path: [String]
    let length: Int64
    let offset: Int64

    var name: String { path.last ?? "" }
    var relativePath: String { path.joined(separator: "/") }
    var isPadding: Bool { path.contains(".pad") }
}

/// Файл в формате v2: корень его дерева Меркла и куски, которые он занимает.
nonisolated struct V2File: Sendable, Hashable {
    let fileIndex: Int
    let root: Data
    let length: Int64
    let firstPiece: Int
    let pieceCount: Int
}

nonisolated enum MetaVersion: String, Sendable {
    case v1, v2, hybrid

    var title: String {
        switch self {
        case .v1: "BitTorrent v1"
        case .v2: "BitTorrent v2"
        case .hybrid: "Гибрид v1 + v2"
        }
    }
}

/// Разобранный .torrent: v1 (BEP 3), v2 (BEP 52) или гибрид, где обе части описывают одни и те же файлы.
nonisolated struct Metainfo: Sendable {
    let infoHash: InfoHash
    let name: String
    let pieceLength: Int
    let pieceHashes: Data
    let files: [TorrentFile]
    let totalLength: Int64
    let trackers: [[URL]]
    let isPrivate: Bool
    let infoDictionary: Data
    let comment: String?
    let createdBy: String?
    let creationDate: Date?
    let version: MetaVersion
    /// Полный (32 байта) v2 info-хеш.
    let infoHashV2: Data?
    let v2Files: [V2File]
    /// Слои кусков: корень файла → хеши кусков подряд (для файлов больше куска).
    var pieceLayers: [Data: Data]
    /// Веб-сиды (BEP 19, url-list).
    var webSeeds: [URL]
    let pieceCount: Int

    var isMultiFile: Bool { files.count > 1 || files.first?.path.count ?? 0 > 1 }

    /// Все идентификаторы роя: у гибрида их два (SHA-1 и усечённый SHA-256).
    var swarmHashes: [InfoHash] {
        var out = [infoHash]
        if version == .hybrid, let v2 = infoHashV2, let alt = InfoHash(v2.prefix(20)) { out.append(alt) }
        return out
    }

    /// Номер слоя кусков в дереве (0 — листья по 16 КБ).
    var pieceLayerIndex: Int { Merkle.log2(pieceLength / Merkle.blockSize) }

    /// Файлы v2, для которых ещё нет слоя кусков (бывает после magnet-ссылки).
    var missingLayers: [V2File] {
        v2Files.filter { Int64(pieceLength) < $0.length && pieceLayers[$0.root] == nil }
    }

    func pieceHash(_ index: Int) -> Data {
        pieceHashes.subdata(in: (index * 20)..<(index * 20 + 20))
    }

    /// Файл v2, которому принадлежит кусок.
    func v2File(forPiece index: Int) -> V2File? {
        var lo = 0, hi = v2Files.count - 1, found: V2File?
        while lo <= hi {
            let mid = (lo + hi) / 2
            if v2Files[mid].firstPiece <= index { found = v2Files[mid]; lo = mid + 1 } else { hi = mid - 1 }
        }
        guard let found, index < found.firstPiece + found.pieceCount else { return nil }
        return found
    }

    func pieceSize(_ index: Int) -> Int {
        if version == .v2, let f = v2File(forPiece: index) {
            // В v2 последний кусок каждого файла короче: файлы выровнены по границам кусков.
            return Int(min(Int64(pieceLength), f.length - Int64(index - f.firstPiece) * Int64(pieceLength)))
        }
        if index == pieceCount - 1 {
            return Int(totalLength - Int64(index) * Int64(pieceLength))
        }
        return pieceLength
    }

    /// Проверка куска: SHA-1 для v1, дерево Меркла для v2, у гибрида — оба, если слои известны.
    func verify(piece index: Int, data: Data) -> Bool {
        if version != .v2, Hashing.sha1(data) != pieceHash(index) { return false }
        if version == .v1 { return true }
        guard let f = v2File(forPiece: index) else { return version == .hybrid }
        let inFile = index - f.firstPiece
        let fileBytes = Int(min(Int64(pieceLength), f.length - Int64(inFile) * Int64(pieceLength)))
        let leaves = Merkle.blockHashes(data.prefix(fileBytes))
        if f.length <= Int64(pieceLength) {
            return Merkle.root(leaves, width: Merkle.nextPowerOfTwo(leaves.count)) == f.root
        }
        guard let layer = pieceLayers[f.root], layer.count >= (inFile + 1) * 32 else { return version == .hybrid }
        let expected = layer.subdata(in: (inFile * 32)..<(inFile * 32 + 32))
        return Merkle.root(leaves, width: pieceLength / Merkle.blockSize) == expected
    }

    init(torrentData: Data) throws {
        let (root, info) = try Bencode.decode(torrentData, capturing: "info")
        guard let info else { throw BencodeError("В файле нет словаря info") }
        var tiers: [[URL]] = []
        if let list = root["announce-list"]?.list {
            for tier in list {
                let urls = (tier.list ?? []).compactMap { $0.string.flatMap(URL.init(string:)) }
                if !urls.isEmpty { tiers.append(urls) }
            }
        }
        if tiers.isEmpty, let a = root["announce"]?.string, let url = URL(string: a) { tiers = [[url]] }
        var layers: [Data: Data] = [:]
        for (key, value) in root["piece layers"]?.dict ?? [:] {
            if let v = value.data { layers[Data(Bencode.keyBytes(key))] = v }
        }
        var seeds: [URL] = []
        if let one = root["url-list"]?.string { seeds = [one].compactMap(URL.init(string:)) }
        if let many = root["url-list"]?.list { seeds = many.compactMap { $0.string.flatMap(URL.init(string:)) } }
        try self.init(infoDictionary: info, trackers: tiers,
                      comment: root["comment"]?.string,
                      createdBy: root["created by"]?.string,
                      creationDate: root["creation date"]?.int.map { Date(timeIntervalSince1970: TimeInterval($0)) },
                      pieceLayers: layers, webSeeds: seeds.filter { ["http", "https"].contains($0.scheme?.lowercased() ?? "") })
        for f in v2Files where Int64(pieceLength) < f.length {
            guard let layer = pieceLayers[f.root], layer.count == f.pieceCount * 32,
                  Merkle.verifyLayer(stride(from: 0, to: layer.count, by: 32).map { layer.subdata(in: $0..<($0 + 32)) },
                                     root: f.root, blocksPerPiece: pieceLength / Merkle.blockSize) else {
                if version == .v2 { throw BencodeError("Слои кусков v2 не сходятся с корнями файлов") }
                pieceLayers[f.root] = nil
                continue
            }
        }
    }

    init(infoDictionary: Data, trackers: [[URL]], comment: String? = nil, createdBy: String? = nil, creationDate: Date? = nil,
         pieceLayers: [Data: Data] = [:], webSeeds: [URL] = []) throws {
        let info = try Bencode.decode(infoDictionary)
        let metaVersion = info["meta version"]?.int
        if let metaVersion, metaVersion > 2 { throw BencodeError("Торрент новой версии (\(metaVersion)) пока не поддерживается") }
        guard let pieceLength = info["piece length"]?.int, pieceLength > 0, pieceLength < 1 << 30 else {
            throw BencodeError("Повреждённый словарь info")
        }
        let pieces = info["pieces"]?.data
        let hasV1 = pieces != nil
        let isV2 = metaVersion == 2 && info["file tree"]?.dict != nil
        guard hasV1 || isV2 else { throw BencodeError("Повреждённый словарь info") }
        let sha1 = InfoHash(Hashing.sha1(infoDictionary))!
        let sha256 = isV2 ? Hashing.sha256(infoDictionary) : nil
        let name = Metainfo.sanitize(info["name.utf-8"]?.string ?? info["name"]?.string ?? sha1.hex)

        var files: [TorrentFile] = []
        var offset: Int64 = 0
        if hasV1 {
            guard let pieces, pieces.count % 20 == 0, !pieces.isEmpty else { throw BencodeError("Повреждённый словарь info") }
            if let list = info["files"]?.list {
                for entry in list {
                    guard let length = entry["length"]?.int, length >= 0 else { continue }
                    // Файлы-заполнители BEP 47 выравнивают куски; на диск их не пишем по имени, но смещения учитываем.
                    let attr = entry["attr"]?.string ?? ""
                    let rawPath = (entry["path.utf-8"]?.list ?? entry["path"]?.list ?? []).compactMap(\.string)
                    var path = [name] + rawPath.map(Metainfo.sanitize)
                    if attr.contains("p") || rawPath.first == ".pad" { path = [name, ".pad", "\(offset)"] }
                    files.append(TorrentFile(path: path, length: length, offset: offset))
                    offset += length
                }
            } else if let length = info["length"]?.int {
                files = [TorrentFile(path: [name], length: length, offset: 0)]
                offset = length
            }
        }

        // Дерево файлов v2.
        var treeFiles: [(path: [String], length: Int64, root: Data?)] = []
        if isV2, let tree = info["file tree"]?.dict {
            func walk(_ node: [String: BValue], _ path: [String], depth: Int) throws {
                guard depth < 64 else { throw BencodeError("Слишком глубокое дерево файлов") }
                let keys = node.keys.sorted { Bencode.keyBytes($0).lexicographicallyPrecedes(Bencode.keyBytes($1)) }
                for key in keys {
                    guard let child = node[key]?.dict else { continue }
                    if let leaf = child[""]?.dict, let length = leaf["length"]?.int {
                        guard length >= 0 else { throw BencodeError("Неверная длина файла") }
                        let root = leaf["pieces root"]?.data
                        if length > 0, root?.count != 32 { throw BencodeError("У файла нет корня дерева Меркла") }
                        treeFiles.append((path + [key], length, root))
                    } else {
                        try walk(child, path + [key], depth: depth + 1)
                    }
                }
            }
            try walk(tree, [], depth: 0)
            guard !treeFiles.isEmpty else { throw BencodeError("В торренте нет файлов") }
        }

        var v2: [V2File] = []
        var version: MetaVersion = .v1
        if isV2 {
            let pl = Int64(pieceLength)
            guard pieceLength >= Merkle.blockSize, pieceLength & (pieceLength - 1) == 0 else {
                throw BencodeError("Размер куска v2 должен быть степенью двойки")
            }
            if hasV1 {
                // Гибрид: v1-часть уже задаёт раскладку; сверяем, что обе части описывают одно и то же.
                let real = files.enumerated().filter { !$0.element.isPadding }
                var consistent = real.count == treeFiles.count
                if consistent {
                    for ((index, file), tf) in zip(real, treeFiles) {
                        guard file.length == tf.length else { consistent = false; break }
                        guard tf.length > 0 else { continue }
                        guard file.offset % pl == 0, let root = tf.root else { consistent = false; break }
                        v2.append(V2File(fileIndex: index, root: root, length: tf.length, firstPiece: Int(file.offset / pl),
                                         pieceCount: Int((tf.length + pl - 1) / pl)))
                    }
                }
                if consistent { version = .hybrid } else { v2 = [] }
            } else {
                // Только v2: раскладка с выравниванием каждого файла по куску (как с файлами-заполнителями).
                version = .v2
                let single = treeFiles.count == 1 && treeFiles[0].path.count == 1
                for tf in treeFiles {
                    if tf.length > 0, offset % pl != 0 {
                        let pad = pl - offset % pl
                        files.append(TorrentFile(path: [name, ".pad", "\(offset)"], length: pad, offset: offset))
                        offset += pad
                    }
                    let path = single ? [name] : [name] + tf.path.map(Metainfo.sanitize)
                    if tf.length > 0, let root = tf.root {
                        v2.append(V2File(fileIndex: files.count, root: root, length: tf.length, firstPiece: Int(offset / pl),
                                         pieceCount: Int((tf.length + pl - 1) / pl)))
                    }
                    files.append(TorrentFile(path: path, length: tf.length, offset: offset))
                    offset += tf.length
                }
            }
        }

        guard !files.isEmpty, offset > 0 else { throw BencodeError("В торренте нет файлов") }
        let expected = Int((offset + pieceLength - 1) / pieceLength)
        if hasV1 {
            guard expected == pieces!.count / 20 else { throw BencodeError("Число кусков не совпадает с размером") }
        }

        self.infoHash = version == .v2 ? InfoHash(sha256!.prefix(20))! : sha1
        self.name = name
        self.pieceLength = Int(pieceLength)
        self.pieceHashes = pieces ?? Data()
        self.files = files
        self.totalLength = offset
        self.trackers = trackers
        self.isPrivate = info["private"]?.int == 1
        self.infoDictionary = infoDictionary
        self.comment = comment
        self.createdBy = createdBy
        self.creationDate = creationDate
        self.version = version
        self.infoHashV2 = version == .v1 ? nil : sha256
        self.v2Files = v2
        self.pieceLayers = pieceLayers
        self.webSeeds = webSeeds
        self.pieceCount = expected
    }

    /// Полноценный .torrent для сохранения (в том числе полученного по magnet).
    func torrentFileData() -> Data {
        var out = Data("d".utf8)
        if let first = trackers.first?.first {
            out.append(BValue.str("announce").encoded)
            out.append(BValue.str(first.absoluteString).encoded)
            out.append(BValue.str("announce-list").encoded)
            out.append(BValue.list(trackers.map { .list($0.map { .str($0.absoluteString) }) }).encoded)
        }
        out.append(BValue.str("info").encoded)
        out.append(infoDictionary)
        let layers = v2Files.compactMap { f in pieceLayers[f.root].map { (f.root, $0) } }
        if !layers.isEmpty {
            out.append(BValue.str("piece layers").encoded)
            var dict: [String: BValue] = [:]
            for (root, layer) in layers { dict[Bencode.key(ArraySlice([UInt8](root)))] = .bytes(layer) }
            out.append(BValue.dict(dict).encoded)
        }
        if !webSeeds.isEmpty {
            out.append(BValue.str("url-list").encoded)
            out.append(BValue.list(webSeeds.map { .str($0.absoluteString) }).encoded)
        }
        out.append(Data("e".utf8))
        return out
    }

    /// Имя файла из торрента не должно выводить за пределы папки загрузки.
    static func sanitize(_ component: String) -> String {
        var s = component.replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: ":", with: "_")
        s = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty || s == "." || s == ".." { s = "_" }
        return String(s.prefix(240))
    }
}

/// magnet:?xt=urn:btih:…&xt=urn:btmh:1220…&dn=…&tr=…&ws=…&x.pe=… (BEP 9, BEP 52).
nonisolated struct MagnetLink: Sendable {
    let infoHash: InfoHash
    /// Полный v2-хеш из urn:btmh (если есть).
    let infoHashV2: Data?
    let name: String?
    let trackers: [URL]
    let peers: [PeerAddress]
    let webSeeds: [URL]

    init?(_ string: String) {
        guard let comps = URLComponents(string: string.trimmingCharacters(in: .whitespacesAndNewlines)),
              comps.scheme?.lowercased() == "magnet" else { return nil }
        let items = comps.queryItems ?? []
        var hash: InfoHash?
        var v2: Data?
        for item in items where item.name == "xt" {
            guard let value = item.value?.lowercased() else { continue }
            if value.hasPrefix("urn:btih:") {
                let code = String(value.dropFirst(9))
                if code.count == 40 { hash = InfoHash(hex: code) }
                else if code.count == 32, let raw = MagnetLink.base32(code) { hash = InfoHash(raw) }
            } else if value.hasPrefix("urn:btmh:1220") {
                let bytes = DiffieHellman.hexBytes(String(value.dropFirst(13)))
                if bytes.count == 32 { v2 = Data(bytes) }
            }
        }
        guard let resolved = hash ?? v2.flatMap({ InfoHash($0.prefix(20)) }) else { return nil }
        infoHash = resolved
        infoHashV2 = v2
        name = items.first { $0.name == "dn" }?.value
        trackers = items.filter { $0.name == "tr" }.compactMap { $0.value.flatMap(URL.init(string:)) }
        peers = items.filter { $0.name == "x.pe" }.compactMap { $0.value.flatMap(PeerAddress.init(hostPort:)) }
        webSeeds = items.filter { $0.name == "ws" }.compactMap { $0.value.flatMap(URL.init(string:)) }
    }

    /// `v2` — полный v2-хеш; `v2Only` — у раздачи нет v1-части (тогда btih не пишем).
    static func make(hash: InfoHash, v2: Data? = nil, v2Only: Bool = false, name: String?, trackers: [URL]) -> String {
        var comps = URLComponents()
        comps.scheme = "magnet"
        var items: [URLQueryItem] = []
        if !(v2Only && v2 != nil) { items.append(URLQueryItem(name: "xt", value: "urn:btih:\(hash.hex)")) }
        if let v2 { items.append(URLQueryItem(name: "xt", value: "urn:btmh:1220\(v2.base16)")) }
        if let name { items.append(URLQueryItem(name: "dn", value: name)) }
        items += trackers.map { URLQueryItem(name: "tr", value: $0.absoluteString) }
        comps.queryItems = items
        return comps.string ?? "magnet:?xt=urn:btih:\(hash.hex)"
    }

    static func base32(_ s: String) -> Data? {
        let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        var buffer: UInt64 = 0
        var bits = 0
        var out = Data()
        for ch in s.uppercased() {
            guard let v = alphabet.firstIndex(of: ch) else { return nil }
            buffer = (buffer << 5) | UInt64(v)
            bits += 5
            if bits >= 8 {
                bits -= 8
                out.append(UInt8((buffer >> UInt64(bits)) & 0xFF))
            }
        }
        return out
    }
}

nonisolated struct PeerAddress: Hashable, Sendable, Codable, CustomStringConvertible {
    let host: String
    let port: UInt16

    init(host: String, port: UInt16) {
        self.host = host
        self.port = port
    }

    init?(hostPort: String) {
        guard let colon = hostPort.lastIndex(of: ":"), let port = UInt16(hostPort[hostPort.index(after: colon)...]) else { return nil }
        var host = String(hostPort[..<colon])
        if host.hasPrefix("["), host.hasSuffix("]") { host = String(host.dropFirst().dropLast()) }
        self.init(host: host, port: port)
    }

    var description: String { host.contains(":") ? "[\(host)]:\(port)" : "\(host):\(port)" }

    /// Компактный формат трекеров и DHT: 4 байта IPv4 + 2 байта порта (или 16 + 2 для IPv6).
    static func compact(_ data: Data, ipv6: Bool = false) -> [PeerAddress] {
        let size = ipv6 ? 18 : 6
        let bytes = [UInt8](data)
        guard bytes.count >= size else { return [] }
        var result: [PeerAddress] = []
        for start in stride(from: 0, through: bytes.count - size, by: size) {
            let port = UInt16(bytes[start + size - 2]) << 8 | UInt16(bytes[start + size - 1])
            guard port > 0 else { continue }
            let host: String
            if ipv6 {
                var groups: [String] = []
                for g in stride(from: 0, to: 16, by: 2) {
                    groups.append(String(UInt16(bytes[start + g]) << 8 | UInt16(bytes[start + g + 1]), radix: 16))
                }
                host = groups.joined(separator: ":")
            } else {
                host = bytes[start..<start + 4].map(String.init).joined(separator: ".")
                if host.hasPrefix("0.") || host == "255.255.255.255" { continue }
            }
            result.append(PeerAddress(host: host, port: port))
        }
        return result
    }
}

/// Битовое поле кусков (старший бит первого байта — кусок 0).
nonisolated struct Bitfield: Sendable, Equatable {
    private(set) var bytes: [UInt8]
    let count: Int
    private(set) var setCount = 0

    init(count: Int) {
        self.count = count
        bytes = [UInt8](repeating: 0, count: (count + 7) / 8)
    }

    init(count: Int, data: Data) {
        self.init(count: count)
        let source = [UInt8](data.prefix(bytes.count))
        for i in source.indices { bytes[i] = source[i] }
        // Лишние биты в конце обнуляем.
        if count % 8 != 0, !bytes.isEmpty { bytes[bytes.count - 1] &= UInt8(truncatingIfNeeded: 0xFF << (8 - count % 8)) }
        setCount = bytes.reduce(0) { $0 + $1.nonzeroBitCount }
    }

    subscript(index: Int) -> Bool {
        get { index >= 0 && index < count && bytes[index >> 3] & (0x80 >> UInt8(index & 7)) != 0 }
        set {
            guard index >= 0, index < count, self[index] != newValue else { return }
            if newValue { bytes[index >> 3] |= (0x80 >> UInt8(index & 7)); setCount += 1 }
            else { bytes[index >> 3] &= ~(0x80 >> UInt8(index & 7)); setCount -= 1 }
        }
    }

    var isComplete: Bool { setCount == count }
    var isEmpty: Bool { setCount == 0 }
    var data: Data { Data(bytes) }

    static func full(_ count: Int) -> Bitfield {
        var b = Bitfield(count: count)
        for i in 0..<count { b[i] = true }
        return b
    }
}
