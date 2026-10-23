import Foundation

/// Поиск пиров в локальной сети (BEP 14, Local Service Discovery): анонсы раздач по мультикасту
/// 239.192.152.143:6771. Так два компьютера в одной сети находят друг друга без трекеров и DHT
/// и обмениваются данными на скорости локальной сети.
nonisolated final class LocalDiscovery: @unchecked Sendable {
    static let group: [UInt8] = [239, 192, 152, 143]
    static let port: UInt16 = 6771

    let queue = DispatchQueue(label: "swarm.lsd")
    let cookie = String(format: "%08x", UInt32.random(in: 0...UInt32.max))
    private let fd: Int32
    private var source: DispatchSourceRead?
    /// Найден пир: (info-хеш в hex, адрес).
    var onPeer: (@Sendable (String, PeerAddress) -> Void)?
    private(set) var received = 0

    /// `interface` — адрес интерфейса для мультикаста (127.0.0.1 в самопроверке), иначе системный по умолчанию.
    init(interface: [UInt8]? = nil) throws {
        let s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard s >= 0 else { throw PeerError("LSD: сокет не создан") }
        var yes: Int32 = 1
        setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(s, SOL_SOCKET, SO_REUSEPORT, &yes, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = LocalDiscovery.port.bigEndian
        addr.sin_addr.s_addr = INADDR_ANY
        let bound = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard bound == 0 else { close(s); throw PeerError("LSD: порт 6771 занят") }

        var membership = ip_mreq()
        withUnsafeMutableBytes(of: &membership.imr_multiaddr) { $0.copyBytes(from: LocalDiscovery.group) }
        if let interface {
            withUnsafeMutableBytes(of: &membership.imr_interface) { $0.copyBytes(from: interface) }
            var ifaddr = in_addr()
            withUnsafeMutableBytes(of: &ifaddr) { $0.copyBytes(from: interface) }
            setsockopt(s, IPPROTO_IP, IP_MULTICAST_IF, &ifaddr, socklen_t(MemoryLayout<in_addr>.size))
        } else {
            membership.imr_interface.s_addr = INADDR_ANY
        }
        guard setsockopt(s, IPPROTO_IP, IP_ADD_MEMBERSHIP, &membership, socklen_t(MemoryLayout<ip_mreq>.size)) == 0 else {
            close(s)
            throw PeerError("LSD: мультикаст недоступен")
        }
        var ttl: UInt8 = 3
        setsockopt(s, IPPROTO_IP, IP_MULTICAST_TTL, &ttl, socklen_t(1))
        var loop: UInt8 = 1
        setsockopt(s, IPPROTO_IP, IP_MULTICAST_LOOP, &loop, socklen_t(1))
        _ = fcntl(s, F_SETFL, fcntl(s, F_GETFL) | O_NONBLOCK)
        fd = s
    }

    func start() {
        let src = DispatchSource.makeReadSource(fileDescriptor: fd, queue: queue)
        src.setEventHandler { [weak self] in self?.drain() }
        src.setCancelHandler { [fd] in close(fd) }
        src.resume()
        source = src
    }

    func stop() {
        queue.async {
            self.source?.cancel()
            self.source = nil
        }
    }

    /// Анонс раздач (несколько Infohash в одном пакете, не длиннее 1400 байт).
    func announce(_ hashes: [String], port: UInt16) {
        var batch: [String] = []
        func flush() {
            guard !batch.isEmpty else { return }
            send(LocalDiscovery.message(hashes: batch, port: port, cookie: cookie))
            batch.removeAll()
        }
        for h in hashes {
            batch.append(h)
            if LocalDiscovery.message(hashes: batch + ["0000000000000000000000000000000000000000"], port: port, cookie: cookie).count > 1400 { flush() }
        }
        flush()
    }

    static func message(hashes: [String], port: UInt16, cookie: String) -> Data {
        var text = "BT-SEARCH * HTTP/1.1\r\nHost: 239.192.152.143:6771\r\nPort: \(port)\r\n"
        for h in hashes { text += "Infohash: \(h)\r\n" }
        text += "cookie: \(cookie)\r\n\r\n\r\n"
        return Data(text.utf8)
    }

    /// Разбор анонса: порт, хеши, cookie.
    static func parse(_ data: Data) -> (port: UInt16, hashes: [String], cookie: String?)? {
        let text = String(decoding: data, as: UTF8.self)
        let lines = text.components(separatedBy: "\r\n")
        guard lines.first?.hasPrefix("BT-SEARCH * HTTP/1.") == true else { return nil }
        var port: UInt16?
        var hashes: [String] = []
        var cookie: String?
        for line in lines.dropFirst() {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            switch key {
            case "port": port = UInt16(value)
            case "infohash" where value.count == 40: hashes.append(value.lowercased())
            case "cookie": cookie = value
            default: break
            }
        }
        guard let port, port > 0, !hashes.isEmpty else { return nil }
        return (port, hashes, cookie)
    }

    private func send(_ data: Data) {
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = LocalDiscovery.port.bigEndian
        withUnsafeMutableBytes(of: &addr.sin_addr) { $0.copyBytes(from: LocalDiscovery.group) }
        _ = data.withUnsafeBytes { b in
            withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { sendto(fd, b.baseAddress, b.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
            }
        }
    }

    private func drain() {
        var buffer = [UInt8](repeating: 0, count: 2048)
        for _ in 0..<64 {
            var from = sockaddr_in()
            var len = socklen_t(MemoryLayout<sockaddr_in>.size)
            let n = buffer.withUnsafeMutableBytes { b in
                withUnsafeMutablePointer(to: &from) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { recvfrom(fd, b.baseAddress, b.count, 0, $0, &len) }
                }
            }
            if n <= 0 { break }
            guard let msg = LocalDiscovery.parse(Data(buffer[0..<n])), msg.cookie != cookie else { continue }
            received += 1
            let host = withUnsafeBytes(of: from.sin_addr) { Array($0) }.map(String.init).joined(separator: ".")
            for h in msg.hashes { onPeer?(h, PeerAddress(host: host, port: msg.port)) }
        }
    }
}
