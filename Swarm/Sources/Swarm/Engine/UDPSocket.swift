import Foundation

/// Общий UDP-порт Swarm (BSD-сокет, IPv4 и IPv6): по нему ходят DHT и uTP.
/// Чтение — источником событий Dispatch на собственной очереди; пакеты отдаются пачкой,
/// после пачки вызывается `onBatchEnd` (uTP отправляет накопленные подтверждения).
nonisolated final class UDPSocket: @unchecked Sendable {
    let queue = DispatchQueue(label: "swarm.udp", qos: .userInitiated)
    let port: UInt16
    private let fd: Int32
    private let isV6: Bool
    private var source: DispatchSourceRead?
    var onPacket: (([UInt8], UDPAddress) -> Void)?
    var onBatchEnd: (() -> Void)?

    init(port: UInt16, loopbackOnly: Bool = false) throws {
        var s = socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP)
        var v6 = s >= 0
        if v6 {
            var off: Int32 = 0
            setsockopt(s, IPPROTO_IPV6, IPV6_V6ONLY, &off, socklen_t(MemoryLayout<Int32>.size))
            var addr = sockaddr_in6()
            addr.sin6_len = UInt8(MemoryLayout<sockaddr_in6>.size)
            addr.sin6_family = sa_family_t(AF_INET6)
            addr.sin6_port = port.bigEndian
            addr.sin6_addr = loopbackOnly ? UDPSocket.mappedLoopback : in6addr_any
            let ok = withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in6>.size)) }
            }
            if ok != 0 {
                Darwin.close(s)
                v6 = false
                s = -1
            }
        }
        if !v6 {
            s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            guard s >= 0 else { throw PeerError("UDP-сокет не создан") }
            var addr = sockaddr_in()
            addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = port.bigEndian
            addr.sin_addr.s_addr = loopbackOnly ? UInt32(0x7F00_0001).bigEndian : INADDR_ANY
            let ok = withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
            }
            guard ok == 0 else {
                Darwin.close(s)
                throw PeerError("UDP-порт \(port) занят")
            }
        }
        var buf: Int32 = 4 << 20
        setsockopt(s, SOL_SOCKET, SO_RCVBUF, &buf, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(s, SOL_SOCKET, SO_SNDBUF, &buf, socklen_t(MemoryLayout<Int32>.size))
        _ = fcntl(s, F_SETFL, fcntl(s, F_GETFL) | O_NONBLOCK)
        fd = s
        isV6 = v6
        self.port = port
    }

    private static let mappedLoopback: in6_addr = {
        var a = in6_addr()
        withUnsafeMutableBytes(of: &a) { b in
            b[10] = 0xFF; b[11] = 0xFF; b[12] = 127; b[15] = 1
        }
        return a
    }()

    func start() {
        let src = DispatchSource.makeReadSource(fileDescriptor: fd, queue: queue)
        src.setEventHandler { [weak self] in self?.drain() }
        src.setCancelHandler { [fd] in Darwin.close(fd) }
        src.resume()
        source = src
    }

    func close() {
        queue.async {
            self.source?.cancel()
            self.source = nil
        }
    }

    private func drain() {
        var buffer = [UInt8](repeating: 0, count: 2048)
        var storage = sockaddr_storage()
        for _ in 0..<256 {
            var len = socklen_t(MemoryLayout<sockaddr_storage>.size)
            let n = buffer.withUnsafeMutableBytes { b in
                withUnsafeMutablePointer(to: &storage) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { recvfrom(fd, b.baseAddress, b.count, 0, $0, &len) }
                }
            }
            if n <= 0 { break }
            guard let from = UDPSocket.address(storage) else { continue }
            onPacket?(Array(buffer[0..<n]), from)
        }
        onBatchEnd?()
    }

    private static func address(_ s: sockaddr_storage) -> UDPAddress? {
        var s = s
        switch Int32(s.ss_family) {
        case AF_INET6:
            return withUnsafePointer(to: &s) {
                $0.withMemoryRebound(to: sockaddr_in6.self, capacity: 1) { sin in
                    UDPAddress(ip: withUnsafeBytes(of: sin.pointee.sin6_addr) { Array($0) }, port: UInt16(bigEndian: sin.pointee.sin6_port))
                }
            }
        case AF_INET:
            return withUnsafePointer(to: &s) {
                $0.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { sin in
                    UDPAddress(ip: withUnsafeBytes(of: sin.pointee.sin_addr) { Array($0) }, port: UInt16(bigEndian: sin.pointee.sin_port))
                }
            }
        default:
            return nil
        }
    }

    /// Можно вызывать с любого потока: sendto потокобезопасен.
    func send(_ bytes: [UInt8], to address: UDPAddress) {
        if isV6 {
            var a = sockaddr_in6()
            a.sin6_len = UInt8(MemoryLayout<sockaddr_in6>.size)
            a.sin6_family = sa_family_t(AF_INET6)
            a.sin6_port = address.port.bigEndian
            withUnsafeMutableBytes(of: &a.sin6_addr) { $0.copyBytes(from: address.ip) }
            _ = bytes.withUnsafeBytes { b in
                withUnsafePointer(to: &a) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        sendto(fd, b.baseAddress, b.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in6>.size))
                    }
                }
            }
        } else {
            guard let v4 = address.v4 else { return }
            var a = sockaddr_in()
            a.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            a.sin_family = sa_family_t(AF_INET)
            a.sin_port = address.port.bigEndian
            withUnsafeMutableBytes(of: &a.sin_addr) { $0.copyBytes(from: v4) }
            _ = bytes.withUnsafeBytes { b in
                withUnsafePointer(to: &a) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        sendto(fd, b.baseAddress, b.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
                    }
                }
            }
        }
    }
}
