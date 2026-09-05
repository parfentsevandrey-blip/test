import Foundation

/// A SOCKS5 client, used for one thing: proving the tunnel carries traffic.
///
/// This is the whole basis of the app's honesty about being connected. A
/// bootstrap percentage says tor believes it has a circuit; it does not
/// survive contact with a parked process, because the number never goes down
/// and a tor whose network was switched off and on again still reports a
/// hundred. The only claim that cannot be wrong is a stream that opened to
/// somewhere on the internet and came back.
enum Socks5 {

    enum Failure: Error, LocalizedError {
        case handshakeRefused
        case unreachable(UInt8)

        var errorDescription: String? {
            switch self {
            case .handshakeRefused: return "the proxy refused the SOCKS handshake"
            case .unreachable(let code): return "the proxy could not reach the host (code \(code))"
            }
        }
    }

    /// Opens a connection through the proxy and hands back the live socket.
    ///
    /// `timeout` is the one number that matters here, and it is a parameter
    /// because the right value differs by an order of magnitude between the
    /// two callers. Checking a tunnel believed to be up should be patient. A
    /// probe inside a retry loop, waiting for a link that is still coming
    /// back, must not be: there the timeout is not patience but the interval
    /// at which the question gets asked, and a long one means a link that
    /// returned seconds ago goes unnoticed until the attempt in flight gives
    /// up. That mistake cost the Android build most of every reconnect.
    static func connect(
        socketPath: String,
        host: String,
        port: UInt16,
        timeout: TimeInterval
    ) throws -> UnixSocket {
        let socket = try UnixSocket(path: socketPath, timeout: timeout)
        do {
            // Greeting: version 5, one method, "no authentication".
            try socket.write(Data([0x05, 0x01, 0x00]))
            let choice = try socket.readExactly(2)
            guard choice.count == 2, choice[0] == 0x05, choice[1] == 0x00 else {
                throw Failure.handshakeRefused
            }

            // CONNECT to a name, so tor resolves it at the exit rather than
            // us resolving it here and leaking the lookup.
            var request = Data([0x05, 0x01, 0x00, 0x03])
            let name = Array(host.utf8)
            request.append(UInt8(name.count))
            request.append(contentsOf: name)
            request.append(UInt8(port >> 8))
            request.append(UInt8(port & 0xFF))
            try socket.write(request)

            let head = try socket.readExactly(4)
            guard head.count == 4, head[1] == 0x00 else {
                throw Failure.unreachable(head.count > 1 ? head[1] : 0xFF)
            }
            // Drain the bound address so the socket is left at the payload.
            switch head[3] {
            case 0x01: _ = try socket.readExactly(4 + 2)
            case 0x04: _ = try socket.readExactly(16 + 2)
            case 0x03:
                let length = try socket.readExactly(1)
                _ = try socket.readExactly(Int(length[0]) + 2)
            default: throw Failure.handshakeRefused
            }
            return socket
        } catch {
            socket.close()
            throw error
        }
    }

    /// One round trip through the tunnel, timed: connect, ask, first byte back.
    ///
    /// Used by the pulse. Plain HTTP on purpose — the target answers with a
    /// 204 and no body, and what is being measured is the path, not a
    /// server's willingness to send a page. Nothing identifying is in the
    /// request, and it is carried inside Tor like everything else.
    static func timedRequest(
        socketPath: String,
        host: String,
        path: String,
        timeout: TimeInterval
    ) throws -> (connectMillis: Int, ttfbMillis: Int) {
        let began = Date()
        let socket = try connect(socketPath: socketPath, host: host, port: 80, timeout: timeout)
        defer { socket.close() }
        let connected = Date()

        try socket.write(
            "GET \(path) HTTP/1.1\r\nHost: \(host)\r\nConnection: close\r\n" +
            "User-Agent: Mozilla/5.0\r\n\r\n"
        )
        // The first byte is the measurement. Reading the whole response would
        // measure the body's size as well, which is a different question and
        // is what the throughput sample below is for.
        _ = try socket.read(upTo: 1)
        let firstByte = Date()

        return (
            connectMillis: Int(connected.timeIntervalSince(began) * 1000),
            ttfbMillis: Int(firstByte.timeIntervalSince(connected) * 1000)
        )
    }

    /// Pulls a body of known size through the tunnel and reports the rate.
    ///
    /// Small on purpose: this runs every few minutes while the tunnel is up,
    /// and a speed sample that costs a megabyte is a speed sample the user
    /// pays for. Sixty-four kilobytes is enough to distinguish a path that is
    /// working from one that is technically open and useless.
    static func throughput(
        socketPath: String,
        host: String,
        path: String,
        timeout: TimeInterval
    ) throws -> Int {
        let socket = try connect(socketPath: socketPath, host: host, port: 80, timeout: timeout)
        defer { socket.close() }
        try socket.write(
            "GET \(path) HTTP/1.1\r\nHost: \(host)\r\nConnection: close\r\n\r\n"
        )
        let began = Date()
        var total = 0
        while let chunk = try? socket.read(upTo: 16 * 1024), !chunk.isEmpty {
            total += chunk.count
            if total > 512 * 1024 { break }
        }
        let seconds = max(0.001, Date().timeIntervalSince(began))
        return Int(Double(total) / seconds / 1024)
    }
}
