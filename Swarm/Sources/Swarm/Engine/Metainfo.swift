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
}

/// Разобранный .torrent (BEP 3). Гибридные торренты v1+v2 читаются по v1-части.
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

    var pieceCount: Int { pieceHashes.count / 20 }
    var isMultiFile: Bool { files.count > 1 || files.first?.path.count ?? 0 > 1 }

    func pieceHash(_ index: Int) -> Data {
        pieceHashes.subdata(in: (index * 20)..<(index * 20 + 20))
    }

    func pieceSize(_ index: Int) -> Int {
        if index == pieceCount - 1 {
            let rest = Int(totalLength - Int64(index) * Int64(pieceLength))
            return rest
        }
        return pieceLength
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
        try self.init(infoDictionary: info, trackers: tiers,
                      comment: root["comment"]?.string,
                      createdBy: root["created by"]?.string,
                      creationDate: root["creation date"]?.int.map { Date(timeIntervalSince1970: TimeInterval($0)) })
    }

    init(infoDictionary: Data, trackers: [[URL]], comment: String? = nil, createdBy: String? = nil, creationDate: Date? = nil) throws {
        let info = try Bencode.decode(infoDictionary)
        guard let hash = InfoHash(Data(Insecure.SHA1.hash(data: infoDictionary))) else { throw BencodeError("info-хеш") }
        guard let pieceLength = info["piece length"]?.int, pieceLength > 0,
              let pieces = info["pieces"]?.data, pieces.count % 20 == 0, !pieces.isEmpty else {
            if info["meta version"]?.int == 2 { throw BencodeError("Торренты только версии 2 пока не поддерживаются") }
            throw BencodeError("Повреждённый словарь info")
        }
        let name = Metainfo.sanitize(info["name.utf-8"]?.string ?? info["name"]?.string ?? hash.hex)
        var files: [TorrentFile] = []
        var offset: Int64 = 0
        if let list = info["files"]?.list {
            for entry in list {
                guard let length = entry["length"]?.int, length >= 0 else { continue }
                // Файлы-заполнители BEP 47 выравнивают куски; на диск их не пишем по имени, но смещения учитываем.
                let attr = entry["attr"]?.string ?? ""
                let rawPath = (entry["path.utf-8"]?.list ?? entry["path"]?.list ?? []).compactMap(\.string)
                var path = [name] + rawPath.map(Metainfo.sanitize)
                if attr.contains("p") { path = [name, ".pad", "\(offset)"] }
                files.append(TorrentFile(path: path, length: length, offset: offset))
                offset += length
            }
        } else if let length = info["length"]?.int {
            files = [TorrentFile(path: [name], length: length, offset: 0)]
            offset = length
        }
        guard !files.isEmpty, offset > 0 else { throw BencodeError("В торренте нет файлов") }
        let expected = (offset + pieceLength - 1) / pieceLength
        guard expected == pieces.count / 20 else { throw BencodeError("Число кусков не совпадает с размером") }

        self.infoHash = hash
        self.name = name
        self.pieceLength = Int(pieceLength)
        self.pieceHashes = pieces
        self.files = files
        self.totalLength = offset
        self.trackers = trackers
        self.isPrivate = info["private"]?.int == 1
        self.infoDictionary = infoDictionary
        self.comment = comment
        self.createdBy = createdBy
        self.creationDate = creationDate
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

/// magnet:?xt=urn:btih:…&dn=…&tr=…&x.pe=… (BEP 9).
nonisolated struct MagnetLink: Sendable {
    let infoHash: InfoHash
    let name: String?
    let trackers: [URL]
    let peers: [PeerAddress]

    init?(_ string: String) {
        guard let comps = URLComponents(string: string.trimmingCharacters(in: .whitespacesAndNewlines)),
              comps.scheme?.lowercased() == "magnet" else { return nil }
        let items = comps.queryItems ?? []
        var hash: InfoHash?
        for item in items where item.name == "xt" {
            guard let value = item.value, value.lowercased().hasPrefix("urn:btih:") else { continue }
            let code = String(value.dropFirst(9))
            if code.count == 40 { hash = InfoHash(hex: code) }
            else if code.count == 32, let raw = MagnetLink.base32(code) { hash = InfoHash(raw) }
        }
        guard let hash else { return nil }
        infoHash = hash
        name = items.first { $0.name == "dn" }?.value
        trackers = items.filter { $0.name == "tr" }.compactMap { $0.value.flatMap(URL.init(string:)) }
        peers = items.filter { $0.name == "x.pe" }.compactMap { $0.value.flatMap(PeerAddress.init(hostPort:)) }
    }

    static func make(hash: InfoHash, name: String?, trackers: [URL]) -> String {
        var comps = URLComponents()
        comps.scheme = "magnet"
        var items = [URLQueryItem(name: "xt", value: "urn:btih:\(hash.hex)")]
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
