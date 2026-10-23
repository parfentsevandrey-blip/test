import Foundation

/// Сообщения протокола обмена с пирами (BEP 3), Fast extension (BEP 6), расширений (BEP 10)
/// и запросов хешей BitTorrent v2 (BEP 52).
nonisolated enum PeerMessage: Sendable {
    case keepAlive
    case choke
    case unchoke
    case interested
    case notInterested
    case have(Int)
    case bitfield(Data)
    case request(index: Int, begin: Int, length: Int)
    case piece(index: Int, begin: Int, block: Data)
    case cancel(index: Int, begin: Int, length: Int)
    case port(UInt16)
    case suggest(Int)
    case haveAll
    case haveNone
    case reject(index: Int, begin: Int, length: Int)
    case allowedFast(Int)
    case extended(id: UInt8, payload: Data)
    case hashRequest(HashRequest)
    case hashes(HashRequest, Data)
    case hashReject(HashRequest)
    case unknown(UInt8)

    static let maxBlock = 16 * 1024

    var encoded: Data {
        var out = Data()
        func header(_ length: Int, _ id: UInt8) {
            out.appendBE(UInt32(length))
            out.append(id)
        }
        switch self {
        case .keepAlive: out.appendBE(UInt32(0))
        case .choke: header(1, 0)
        case .unchoke: header(1, 1)
        case .interested: header(1, 2)
        case .notInterested: header(1, 3)
        case .have(let i): header(5, 4); out.appendBE(UInt32(i))
        case .bitfield(let d): header(1 + d.count, 5); out.append(d)
        case .request(let i, let b, let l): header(13, 6); out.appendBE(UInt32(i)); out.appendBE(UInt32(b)); out.appendBE(UInt32(l))
        case .piece(let i, let b, let block): header(9 + block.count, 7); out.appendBE(UInt32(i)); out.appendBE(UInt32(b)); out.append(block)
        case .cancel(let i, let b, let l): header(13, 8); out.appendBE(UInt32(i)); out.appendBE(UInt32(b)); out.appendBE(UInt32(l))
        case .port(let p): header(3, 9); out.appendBE(p)
        case .suggest(let i): header(5, 0x0D); out.appendBE(UInt32(i))
        case .haveAll: header(1, 0x0E)
        case .haveNone: header(1, 0x0F)
        case .reject(let i, let b, let l): header(13, 0x10); out.appendBE(UInt32(i)); out.appendBE(UInt32(b)); out.appendBE(UInt32(l))
        case .allowedFast(let i): header(5, 0x11); out.appendBE(UInt32(i))
        case .extended(let id, let payload): header(2 + payload.count, 20); out.append(id); out.append(payload)
        case .hashRequest(let r): header(1 + 48, 21); out.append(r.encoded)
        case .hashes(let r, let hashes): header(1 + 48 + hashes.count, 22); out.append(r.encoded); out.append(hashes)
        case .hashReject(let r): header(1 + 48, 23); out.append(r.encoded)
        case .unknown: break
        }
        return out
    }

    /// Разбор тела сообщения (без 4-байтового префикса длины).
    static func decode(_ body: [UInt8]) -> PeerMessage? {
        guard let id = body.first else { return .keepAlive }
        func u32(_ at: Int) -> Int? {
            guard body.count >= at + 4 else { return nil }
            return Int(UInt32(body[at]) << 24 | UInt32(body[at + 1]) << 16 | UInt32(body[at + 2]) << 8 | UInt32(body[at + 3]))
        }
        switch id {
        case 0: return .choke
        case 1: return .unchoke
        case 2: return .interested
        case 3: return .notInterested
        case 4: return u32(1).map { .have($0) }
        case 5: return .bitfield(Data(body[1...]))
        case 6:
            guard let i = u32(1), let b = u32(5), let l = u32(9) else { return nil }
            return .request(index: i, begin: b, length: l)
        case 7:
            guard let i = u32(1), let b = u32(5) else { return nil }
            return .piece(index: i, begin: b, block: Data(body[9...]))
        case 8:
            guard let i = u32(1), let b = u32(5), let l = u32(9) else { return nil }
            return .cancel(index: i, begin: b, length: l)
        case 9:
            guard body.count >= 3 else { return nil }
            return .port(UInt16(body[1]) << 8 | UInt16(body[2]))
        case 0x0D: return u32(1).map { .suggest($0) }
        case 0x0E: return .haveAll
        case 0x0F: return .haveNone
        case 0x10:
            guard let i = u32(1), let b = u32(5), let l = u32(9) else { return nil }
            return .reject(index: i, begin: b, length: l)
        case 0x11: return u32(1).map { .allowedFast($0) }
        case 20:
            guard body.count >= 2 else { return nil }
            return .extended(id: body[1], payload: Data(body[2...]))
        case 21, 22, 23:
            guard let r = HashRequest(Array(body.dropFirst())) else { return nil }
            if id == 21 { return .hashRequest(r) }
            if id == 23 { return .hashReject(r) }
            return .hashes(r, Data(body[(1 + 48)...]))
        default:
            return .unknown(id)
        }
    }
}

/// Запрос хешей дерева Меркла v2: корень файла, слой, первый индекс, число хешей, слои доказательства.
nonisolated struct HashRequest: Sendable, Hashable {
    var root: Data
    var baseLayer: Int
    var index: Int
    var length: Int
    var proofLayers: Int

    init(root: Data, baseLayer: Int, index: Int, length: Int, proofLayers: Int) {
        self.root = root
        self.baseLayer = baseLayer
        self.index = index
        self.length = length
        self.proofLayers = proofLayers
    }

    init?(_ b: [UInt8]) {
        guard b.count >= 48 else { return nil }
        func u32(_ at: Int) -> Int { Int(UInt32(b[at]) << 24 | UInt32(b[at + 1]) << 16 | UInt32(b[at + 2]) << 8 | UInt32(b[at + 3])) }
        root = Data(b[0..<32])
        baseLayer = u32(32)
        index = u32(36)
        length = u32(40)
        proofLayers = u32(44)
    }

    var encoded: Data {
        var out = root
        for v in [baseLayer, index, length, proofLayers] { out.appendBE(UInt32(truncatingIfNeeded: v)) }
        return out
    }
}

nonisolated struct Handshake: Sendable {
    let infoHash: Data
    let peerID: Data
    let reserved: Data

    private func bit(_ byte: Int, _ mask: UInt8) -> Bool { reserved.count == 8 && reserved[reserved.startIndex + byte] & mask != 0 }
    var supportsExtensions: Bool { bit(5, 0x10) }
    var supportsFast: Bool { bit(7, 0x04) }
    var supportsDHT: Bool { bit(7, 0x01) }

    static let protocolName = Data("BitTorrent protocol".utf8)
    static let prefix = Data([19]) + protocolName

    /// Зарезервированные биты: расширения BEP 10, Fast extension (BEP 6), DHT (BEP 5).
    static func make(infoHash: Data, peerID: Data, dht: Bool = true) -> Data {
        var out = prefix
        out.append(Data([0, 0, 0, 0, 0, 0x10, 0, dht ? 0x05 : 0x04]))
        out.append(infoHash)
        out.append(peerID)
        return out
    }

    init?(_ data: Data) {
        let b = [UInt8](data)
        guard b.count == 68, b[0] == 19, Data(b[1..<20]) == Handshake.protocolName else { return nil }
        reserved = Data(b[20..<28])
        infoHash = Data(b[28..<48])
        peerID = Data(b[48..<68])
    }
}

nonisolated enum PeerIdentity {
    static let clientName = "Swarm 1.0"

    /// Стиль Azureus: -SW1000- + 12 случайных байт.
    static func makePeerID() -> Data {
        var id = Data("-SW1000-".utf8)
        for _ in 0..<12 { id.append(UInt8.random(in: 0...255)) }
        return id
    }

    /// Анонимный режим: 20 случайных букв и цифр, по которым не узнать программу.
    static func makeAnonymousPeerID() -> Data {
        let alphabet = Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".utf8)
        return Data((0..<20).map { _ in alphabet.randomElement()! })
    }

    /// Название клиента по peer id (для списка пиров).
    static func clientName(for id: Data) -> String {
        let b = [UInt8](id)
        guard b.count == 20 else { return "?" }
        if b[0] == UInt8(ascii: "-"), b[7] == UInt8(ascii: "-") {
            let code = String(decoding: b[1...2], as: UTF8.self)
            let version = String(decoding: b[3...6], as: UTF8.self)
            let names = ["qB": "qBittorrent", "TR": "Transmission", "UT": "µTorrent", "LT": "libtorrent", "DE": "Deluge",
                         "AZ": "Vuze", "BI": "BiglyBT", "lt": "rTorrent", "SW": "Swarm", "BC": "BitComet", "FD": "Free Download Manager",
                         "WW": "WebTorrent", "KT": "KTorrent", "TX": "Tixati", "XL": "Xunlei", "BT": "BitTorrent", "AG": "Ares", "PI": "PicoTorrent"]
            let digits = version.filter(\.isNumber).map(String.init).joined(separator: ".")
            return "\(names[code] ?? code) \(digits)"
        }
        return "Неизвестный"
    }
}

nonisolated extension Data {
    mutating func appendBE(_ v: UInt32) {
        append(contentsOf: [UInt8(v >> 24 & 0xFF), UInt8(v >> 16 & 0xFF), UInt8(v >> 8 & 0xFF), UInt8(v & 0xFF)])
    }

    mutating func appendBE(_ v: UInt16) {
        append(contentsOf: [UInt8(v >> 8), UInt8(v & 0xFF)])
    }

    mutating func appendBE(_ v: UInt64) {
        for shift in stride(from: 56, through: 0, by: -8) { append(UInt8(v >> UInt64(shift) & 0xFF)) }
    }

    func readBE32(_ at: Int) -> UInt32 {
        let b = [UInt8](self[(startIndex + at)..<(startIndex + at + 4)])
        return UInt32(b[0]) << 24 | UInt32(b[1]) << 16 | UInt32(b[2]) << 8 | UInt32(b[3])
    }

    func readBE64(_ at: Int) -> UInt64 {
        (UInt64(readBE32(at)) << 32) | UInt64(readBE32(at + 4))
    }
}

/// Fast extension (BEP 6): канонический набор кусков, которые можно запрашивать, даже когда тебя «душат».
nonisolated enum FastExtension {
    static func allowedSet(ip: [UInt8], infoHash: Data, pieceCount: Int, k: Int = 10) -> [Int] {
        guard ip.count == 4, pieceCount > 0 else { return [] }
        let want = min(k, pieceCount)
        var x = Data([ip[0], ip[1], ip[2], 0]) + infoHash
        var out: [Int] = []
        while out.count < want {
            x = Hashing.sha1(x)
            for i in 0..<5 where out.count < want {
                let index = Int(x.readBE32(i * 4) % UInt32(pieceCount))
                if !out.contains(index) { out.append(index) }
            }
        }
        return out
    }
}
