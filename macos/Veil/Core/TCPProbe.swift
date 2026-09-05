import Foundation

/// One plain TCP connection to an address on the internet, made the ordinary
/// way with no proxy in the request: the test of whether the *machine's own*
/// traffic is going through the tunnel.
///
/// The SOCKS probe proves tor works. This proves the tunnel carries what the
/// system routes into it, which is a different question with a different
/// failure: an extension that loaded, took the default route, and forwards
/// nothing looks connected from every angle but this one.
enum TCPProbe {

    /// Whether a TCP handshake with `address:port` completes within `timeout`.
    static func reaches(_ address: String, port: UInt16, timeout: TimeInterval) -> Bool {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        defer { close(fd) }

        var target = sockaddr_in()
        target.sin_family = sa_family_t(AF_INET)
        target.sin_port = port.bigEndian
        guard inet_pton(AF_INET, address, &target.sin_addr) == 1 else { return false }

        // Non-blocking, so the wait is bounded by poll rather than by the
        // kernel's own connect timeout, which is over a minute.
        let flags = fcntl(fd, F_GETFL, 0)
        _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)

        let started = withUnsafePointer(to: &target) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if started == 0 { return true }
        guard errno == EINPROGRESS else { return false }

        var waiting = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        guard poll(&waiting, 1, Int32(timeout * 1000)) > 0 else { return false }

        var failure: Int32 = 0
        var size = socklen_t(MemoryLayout<Int32>.size)
        guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &failure, &size) == 0 else { return false }
        return failure == 0
    }
}
