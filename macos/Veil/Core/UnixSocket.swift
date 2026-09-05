import Foundation

/// A blocking unix-domain socket.
///
/// Everything this app talks to locally is a unix socket rather than a
/// loopback port: tor's control connection and tor's SOCKS proxy both. That
/// is the one place macOS lets this app be stricter than the Android build,
/// where both had to be TCP on 127.0.0.1 and therefore reachable by every
/// other process on the device. A socket inside the App Group container is
/// reachable by this app and its extension, and by nothing else.
///
/// Deliberately POSIX rather than Network.framework. The control protocol is
/// a synchronous request/response with an asynchronous event channel laid over
/// it, read from a dedicated thread; a callback-driven connection would mean
/// reassembling that framing out of arbitrary chunks for no gain.
final class UnixSocket {

    enum Failure: Error, LocalizedError {
        case pathTooLong
        case cannotOpen(Int32)
        case cannotConnect(Int32)
        case closed
        case timedOut

        var errorDescription: String? {
            switch self {
            case .pathTooLong: return "the socket path does not fit in sockaddr_un"
            case .cannotOpen(let e): return "socket() failed: \(String(cString: strerror(e)))"
            case .cannotConnect(let e): return "connect() failed: \(String(cString: strerror(e)))"
            case .closed: return "the socket closed"
            case .timedOut: return "the socket timed out"
            }
        }
    }

    private var fd: Int32 = -1
    private var pending = Data()
    private let lock = NSLock()

    var isOpen: Bool { fd >= 0 }

    init(path: String, timeout: TimeInterval = 15) throws {
        var address = sockaddr_un()
        address.sun_family = sa_family_t(AF_UNIX)
        let bytes = Array(path.utf8)
        // sun_path is a fixed C array; anything longer simply cannot be named.
        let capacity = MemoryLayout.size(ofValue: address.sun_path)
        guard bytes.count < capacity else { throw Failure.pathTooLong }
        withUnsafeMutablePointer(to: &address.sun_path) { raw in
            raw.withMemoryRebound(to: CChar.self, capacity: capacity) { dst in
                for (i, b) in bytes.enumerated() { dst[i] = CChar(bitPattern: b) }
                dst[bytes.count] = 0
            }
        }

        fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { throw Failure.cannotOpen(errno) }

        var tv = timeval(
            tv_sec: Int(timeout),
            tv_usec: Int32((timeout - floor(timeout)) * 1_000_000)
        )
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        // A write to a socket the far end has closed must come back as an
        // error, not as SIGPIPE taking the whole process down with it.
        var on: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &on, socklen_t(MemoryLayout<Int32>.size))

        let size = socklen_t(MemoryLayout<sockaddr_un>.size)
        let result = withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { generic in
                Darwin.connect(fd, generic, size)
            }
        }
        guard result == 0 else {
            let code = errno
            close()
            throw Failure.cannotConnect(code)
        }
    }

    deinit { close() }

    func close() {
        lock.lock()
        defer { lock.unlock() }
        if fd >= 0 {
            Darwin.close(fd)
            fd = -1
        }
    }

    func write(_ data: Data) throws {
        guard fd >= 0 else { throw Failure.closed }
        try data.withUnsafeBytes { buffer in
            var sent = 0
            while sent < buffer.count {
                let n = Darwin.write(fd, buffer.baseAddress!.advanced(by: sent), buffer.count - sent)
                if n > 0 {
                    sent += n
                } else if n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK) {
                    throw Failure.timedOut
                } else {
                    throw Failure.closed
                }
            }
        }
    }

    func write(_ text: String) throws { try write(Data(text.utf8)) }

    /// Reads up to `count` bytes, blocking until at least one arrives.
    func read(upTo count: Int) throws -> Data {
        guard fd >= 0 else { throw Failure.closed }
        var buffer = [UInt8](repeating: 0, count: count)
        let n = buffer.withUnsafeMutableBytes { Darwin.read(fd, $0.baseAddress, count) }
        if n > 0 { return Data(buffer[0..<n]) }
        if n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK) { throw Failure.timedOut }
        throw Failure.closed
    }

    /// Reads exactly `count` bytes or throws.
    func readExactly(_ count: Int) throws -> Data {
        var out = Data()
        while out.count < count {
            out.append(try read(upTo: count - out.count))
        }
        return out
    }

    /// One CRLF-terminated line, without the terminator.
    ///
    /// Buffered, because the control protocol is line-oriented and a socket
    /// read returns whatever happens to have arrived.
    func readLine() throws -> String {
        while true {
            if let index = pending.firstIndex(of: 0x0A) {
                let line = pending[pending.startIndex..<index]
                pending.removeSubrange(pending.startIndex...index)
                var text = String(decoding: line, as: UTF8.self)
                if text.hasSuffix("\r") { text.removeLast() }
                return text
            }
            pending.append(try read(upTo: 4096))
        }
    }
}
