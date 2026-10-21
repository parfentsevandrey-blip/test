import Foundation

/// Сообщения протокола обмена с пирами (BEP 3) и расширений (BEP 10).
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
    case extended(id: UInt8, payload: Data)
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
        case .extended(let id, let payload): header(2 + payload.count, 20); out.append(id); out.append(payload)
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
        case 20:
            guard body.count >= 2 else { return nil }
            return .extended(id: body[1], payload: Data(body[2...]))
        default:
            return .unknown(id)
        }
    }
}

nonisolated struct Handshake: Sendable {
    let infoHash: Data
    let peerID: Data
    let reserved: Data

    var supportsExtensions: Bool { reserved.count == 8 && reserved[reserved.startIndex + 5] & 0x10 != 0 }

    static let protocolName = Data("BitTorrent protocol".utf8)

    static func make(infoHash: Data, peerID: Data) -> Data {
        var out = Data([19])
        out.append(protocolName)
        out.append(Data([0, 0, 0, 0, 0, 0x10, 0, 0])) // расширения BEP 10
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
