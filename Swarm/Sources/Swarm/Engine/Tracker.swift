import Foundation

nonisolated enum AnnounceEvent: String, Sendable {
    case none = "", started, completed, stopped

    var udpCode: UInt32 {
        switch self {
        case .none: 0
        case .completed: 1
        case .started: 2
        case .stopped: 3
        }
    }
}

nonisolated struct AnnounceRequest: Sendable {
    var infoHash: InfoHash
    var peerID: Data
    var port: UInt16
    var uploaded: Int64
    var downloaded: Int64
    var left: Int64
    var event: AnnounceEvent
    var numWant = 80
    var key: UInt32
    /// Анонимный режим: не называем программу трекеру.
    var anonymous = false
}

nonisolated struct AnnounceResponse: Sendable {
    var interval: Int
    var peers: [PeerAddress]
    var seeders: Int?
    var leechers: Int?
}

/// Клиент трекеров: HTTP(S) (BEP 3, компактный ответ BEP 23) и UDP (BEP 15).
nonisolated enum TrackerClient {
    static func announce(_ url: URL, _ request: AnnounceRequest) async throws -> AnnounceResponse {
        switch url.scheme?.lowercased() {
        case "http", "https": return try await http(url, request)
        case "udp": return try await udp(url, request)
        default: throw PeerError("Неподдерживаемый трекер")
        }
    }

    private static func percentEncode(_ data: Data) -> String {
        let unreserved = Set(Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~".utf8))
        return data.map { unreserved.contains($0) ? String(UnicodeScalar($0)) : String(format: "%%%02X", $0) }.joined()
    }

    private static func http(_ url: URL, _ r: AnnounceRequest) async throws -> AnnounceResponse {
        var params = [
            "info_hash=\(percentEncode(r.infoHash.bytes))",
            "peer_id=\(percentEncode(r.peerID))",
            "port=\(r.port)",
            "uploaded=\(r.uploaded)",
            "downloaded=\(r.downloaded)",
            "left=\(r.left)",
            "compact=1",
            "numwant=\(r.numWant)",
            "key=\(String(r.key, radix: 16))",
        ]
        if r.event != .none { params.append("event=\(r.event.rawValue)") }
        let base = url.absoluteString
        guard let full = URL(string: base + (base.contains("?") ? "&" : "?") + params.joined(separator: "&")) else {
            throw PeerError("Неверный адрес трекера")
        }
        var request = URLRequest(url: full, timeoutInterval: 20)
        request.setValue(r.anonymous ? "Mozilla/5.0" : PeerIdentity.clientName, forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw PeerError("Трекер ответил \(http.statusCode)")
        }
        let root = try Bencode.decode(data)
        if let failure = root["failure reason"]?.string { throw PeerError(failure) }
        var peers: [PeerAddress] = []
        if let compact = root["peers"]?.data {
            peers = PeerAddress.compact(compact)
        } else if let list = root["peers"]?.list {
            peers = list.compactMap { p in
                guard let ip = p["ip"]?.string, let port = p["port"]?.int, let p16 = UInt16(exactly: port) else { return nil }
                return PeerAddress(host: ip, port: p16)
            }
        }
        if let six = root["peers6"]?.data { peers += PeerAddress.compact(six, ipv6: true) }
        return AnnounceResponse(interval: Int(root["interval"]?.int ?? 1800), peers: peers,
                                seeders: root["complete"]?.int.map(Int.init), leechers: root["incomplete"]?.int.map(Int.init))
    }

    private static func udp(_ url: URL, _ r: AnnounceRequest) async throws -> AnnounceResponse {
        guard let host = url.host, let port = url.port, let p16 = UInt16(exactly: port) else { throw PeerError("Неверный UDP-трекер") }
        let session = UDPSession(host: host, port: p16)
        defer { session.close() }
        try await session.start(timeout: 10)

        // connect
        let tid1 = UInt32.random(in: 0...UInt32.max)
        var connect = Data()
        connect.appendBE(UInt64(0x41727101980))
        connect.appendBE(UInt32(0))
        connect.appendBE(tid1)
        let reply = try await session.exchange(connect, timeout: 8) { d in d.count >= 16 && d.readBE32(4) == tid1 }
        guard reply.readBE32(0) == 0 else { throw PeerError("UDP-трекер отказал") }
        let connectionID = reply.readBE64(8)

        // announce
        let tid2 = UInt32.random(in: 0...UInt32.max)
        var announce = Data()
        announce.appendBE(connectionID)
        announce.appendBE(UInt32(1))
        announce.appendBE(tid2)
        announce.append(r.infoHash.bytes)
        announce.append(r.peerID)
        announce.appendBE(UInt64(max(0, r.downloaded)))
        announce.appendBE(UInt64(max(0, r.left)))
        announce.appendBE(UInt64(max(0, r.uploaded)))
        announce.appendBE(r.event.udpCode)
        announce.appendBE(UInt32(0))
        announce.appendBE(r.key)
        announce.appendBE(UInt32(bitPattern: Int32(r.numWant)))
        announce.appendBE(r.port)
        let res = try await session.exchange(announce, timeout: 10) { d in d.count >= 8 && d.readBE32(4) == tid2 }
        let action = res.readBE32(0)
        if action == 3 {
            throw PeerError(String(decoding: res.dropFirst(8), as: UTF8.self))
        }
        guard action == 1, res.count >= 20 else { throw PeerError("Неверный ответ UDP-трекера") }
        let interval = Int(res.readBE32(8))
        let leechers = Int(res.readBE32(12))
        let seeders = Int(res.readBE32(16))
        let peers = PeerAddress.compact(Data(res.dropFirst(20)))
        return AnnounceResponse(interval: max(60, interval), peers: peers, seeders: seeders, leechers: leechers)
    }
}
