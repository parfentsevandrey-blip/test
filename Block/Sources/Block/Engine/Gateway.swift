import Darwin
import Foundation

/// Шлюз Block: локальный прокси (HTTP CONNECT, HTTP и SOCKS5) на 127.0.0.1. Через него — и только через него —
/// программы под замком выходят в сеть. Шлюз узнаёт программу по сокету клиента, спрашивает решение и
/// соединяет. Свои исходящие соединения он открывает с портов 30000–39999: фильтр пакетов пропускает
/// только их, поэтому всё, что идёт мимо шлюза, остаётся внутри Mac.
///
/// Сокеты POSIX и по потоку на соединение: так понятно, что происходит, и легко привязать исходящий порт.
nonisolated final class Gateway: @unchecked Sendable {
    static let sourcePorts: ClosedRange<UInt16> = 30000...39999
    static let defaultPort: UInt16 = 41080

    struct Request: Sendable {
        var app: NetApp
        var host: String
        var port: Int
        /// HTTPS, HTTP или SOCKS.
        var kind: String
    }

    enum Verdict: Sendable { case allow, deny }

    struct Event: Sendable {
        var date: Date
        var app: NetApp
        var host: String
        var port: Int
        var verdict: Verdict
        var bytes: Int64
        var failure: String?
    }

    let port: UInt16
    /// Решение по запросу (может спросить человека — ждём до 45 с).
    var decide: @Sendable (Request) async -> Verdict
    /// Соединение закрылось: сколько передано.
    var onEvent: (@Sendable (Event) -> Void)?
    /// Следующий шаг (SOCKS5 на этом Mac, например Veil): разрешённое уходит через него.
    var upstream: (host: String, port: UInt16)? {
        get { lock.withLock { _upstream } }
        set { lock.withLock { _upstream = newValue } }
    }

    private let lock = NSLock()
    private var _upstream: (host: String, port: UInt16)?
    private var listenFD: Int32 = -1
    private var running = false
    private var nextSource: UInt16

    init(port: UInt16 = Gateway.defaultPort, decide: @escaping @Sendable (Request) async -> Verdict) {
        self.port = port
        self.decide = decide
        nextSource = Gateway.sourcePorts.lowerBound + UInt16.random(in: 0...999)
    }

    var isRunning: Bool { lock.withLock { running } }

    // MARK: Приём

    func start() throws {
        guard !isRunning else { return }
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { throw GatewayError.socket(errno) }
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        addr.sin_addr.s_addr = inet_addr("127.0.0.1")
        let bound = withUnsafePointer(to: &addr) { $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) } }
        guard bound == 0, listen(fd, 256) == 0 else {
            let code = errno
            close(fd)
            throw GatewayError.bind(port, code)
        }
        lock.withLock { listenFD = fd; running = true }
        let thread = Thread { [weak self] in self?.acceptLoop(fd) }
        thread.name = "Block.Gateway.accept"
        thread.start()
    }

    func stop() {
        let fd = lock.withLock { () -> Int32 in
            running = false
            let fd = listenFD
            listenFD = -1
            return fd
        }
        if fd >= 0 { shutdown(fd, SHUT_RDWR); close(fd) }
    }

    private func acceptLoop(_ fd: Int32) {
        while isRunning {
            var peer = sockaddr_in()
            var length = socklen_t(MemoryLayout<sockaddr_in>.size)
            let client = withUnsafeMutablePointer(to: &peer) { $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { accept(fd, $0, &length) } }
            if client < 0 {
                if errno == EINTR { continue }
                break
            }
            let clientPort = UInt16(bigEndian: peer.sin_port)
            let thread = Thread { [weak self] in
                guard let self else { close(client); return }
                self.serve(client, clientPort: clientPort)
            }
            thread.stackSize = 512 * 1024
            thread.start()
        }
    }

    // MARK: Одно соединение

    private func serve(_ client: Int32, clientPort: UInt16) {
        defer { close(client) }
        configure(client)
        setTimeout(client, seconds: 20)
        var buffer = [UInt8]()
        guard read(client, into: &buffer, atLeast: 1) else { return }
        // Кто это: пока соединение клиента живо, его сокет виден в таблице процессов.
        let app = ProcessFinder.identify(clientPort: clientPort, serverPort: port)
        if buffer[0] == 5 {
            socks(client, buffer: &buffer, app: app)
        } else {
            http(client, buffer: &buffer, app: app)
        }
    }

    private func socks(_ client: Int32, buffer: inout [UInt8], app: NetApp) {
        // Приветствие: 05, число способов, способы. Отвечаем «без пароля».
        guard read(client, into: &buffer, atLeast: 2), read(client, into: &buffer, atLeast: 2 + Int(buffer[1])) else { return }
        buffer.removeFirst(2 + Int(buffer[1]))
        guard send(client, [5, 0]) else { return }
        guard read(client, into: &buffer, atLeast: 5) else { return }
        guard let (host, port, length) = SOCKSParser.request(buffer) ?? waitForRequest(client, &buffer) else {
            _ = send(client, [5, 1, 0, 1, 0, 0, 0, 0, 0, 0])
            return
        }
        guard buffer[1] == 1 else { _ = send(client, [5, 7, 0, 1, 0, 0, 0, 0, 0, 0]); return }   // только CONNECT
        buffer.removeFirst(length)
        let request = Request(app: app, host: host, port: port, kind: "SOCKS")
        guard verdict(request) == .allow else {
            _ = send(client, [5, 2, 0, 1, 0, 0, 0, 0, 0, 0])   // «запрещено правилами»
            report(request, .deny, 0, nil)
            return
        }
        switch connectOut(host: host, port: port) {
        case .success(let remote):
            defer { close(remote) }
            guard send(client, [5, 0, 0, 1, 0, 0, 0, 0, 0, 0]) else { return }
            if !buffer.isEmpty { _ = send(remote, buffer) }
            report(request, .allow, pump(client, remote), nil)
        case .failure(let error):
            _ = send(client, [5, 5, 0, 1, 0, 0, 0, 0, 0, 0])
            report(request, .allow, 0, error.description)
        }
    }

    private func waitForRequest(_ client: Int32, _ buffer: inout [UInt8]) -> (String, Int, Int)? {
        for _ in 0..<8 {
            guard read(client, into: &buffer, atLeast: buffer.count + 1) else { return nil }
            if let parsed = SOCKSParser.request(buffer) { return parsed }
        }
        return nil
    }

    private func http(_ client: Int32, buffer: inout [UInt8], app: NetApp) {
        // Заголовок целиком: до пустой строки.
        while HTTPParser.headerEnd(buffer) == nil {
            guard buffer.count < 65_536, read(client, into: &buffer, atLeast: buffer.count + 1) else { return }
        }
        guard let head = HTTPParser.parse(buffer) else {
            _ = send(client, Array("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".utf8))
            return
        }
        let request = Request(app: app, host: head.host, port: head.port, kind: head.isConnect ? "HTTPS" : "HTTP")
        guard verdict(request) == .allow else {
            let text = "Block: программе «\(app.name)» выход в сеть не разрешён."
            let body = Array(text.utf8)
            _ = send(client, Array("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8) + body)
            report(request, .deny, 0, nil)
            return
        }
        switch connectOut(host: head.host, port: head.port) {
        case .success(let remote):
            defer { close(remote) }
            if head.isConnect {
                guard send(client, Array("HTTP/1.1 200 Connection Established\r\n\r\n".utf8)) else { return }
                let rest = Array(buffer[head.length...])
                if !rest.isEmpty { _ = send(remote, rest) }
            } else {
                // Обычный HTTP: запрос в форме для сервера, соединение закрывается после ответа
                // (следующий запрос клиента может быть к другому сайту).
                guard send(remote, head.forwarded + Array(buffer[head.length...])) else { return }
            }
            report(request, .allow, pump(client, remote), nil)
        case .failure(let error):
            let body = Array("Block: не удалось соединиться с \(head.host): \(error.description)".utf8)
            _ = send(client, Array("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8) + body)
            report(request, .allow, 0, error.description)
        }
    }

    // MARK: Решение

    private final class Box: @unchecked Sendable { var verdict: Verdict = .deny }

    private func verdict(_ request: Request) -> Verdict {
        let box = Box()
        let done = DispatchSemaphore(value: 0)
        let decide = self.decide
        Task {
            box.verdict = await decide(request)
            done.signal()
        }
        // Человек не ответил — соединение не проходит (решение не запоминается).
        return done.wait(timeout: .now() + 45) == .success ? box.verdict : .deny
    }

    private func report(_ request: Request, _ verdict: Verdict, _ bytes: Int64, _ failure: String?) {
        onEvent?(Event(date: Date(), app: request.app, host: request.host, port: request.port, verdict: verdict, bytes: bytes, failure: failure))
    }

    // MARK: Исходящее соединение

    enum ConnectError: Error, CustomStringConvertible {
        case resolve(String), connect(Int32), upstream(String)
        var description: String {
            switch self {
            case .resolve(let host): "адрес \(host) не найден"
            case .connect(let code): String(cString: strerror(code))
            case .upstream(let why): "следующий шаг (SOCKS): \(why)"
            }
        }
    }

    private func connectOut(host: String, port: Int) -> Result<Int32, ConnectError> {
        if let up = upstream {
            switch connectDirect(host: up.host, port: Int(up.port), bindSource: false) {
            case .success(let fd):
                if let why = SOCKSParser.handshake(fd, host: host, port: port) { close(fd); return .failure(.upstream(why)) }
                return .success(fd)
            case .failure(let error):
                return .failure(.upstream(error.description))
            }
        }
        return connectDirect(host: host, port: port, bindSource: true)
    }

    /// Соединение с адресом; исходящий порт — из диапазона шлюза (его пропускает фильтр пакетов).
    func connectDirect(host: String, port: Int, bindSource: Bool) -> Result<Int32, ConnectError> {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var list: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, String(port), &hints, &list) == 0, let first = list else { return .failure(.resolve(host)) }
        defer { freeaddrinfo(first) }
        var lastError: Int32 = ECONNREFUSED
        var node: UnsafeMutablePointer<addrinfo>? = first
        var tried = 0
        while let n = node, tried < 4 {
            tried += 1
            let fd = socket(n.pointee.ai_family, n.pointee.ai_socktype, n.pointee.ai_protocol)
            node = n.pointee.ai_next
            guard fd >= 0 else { lastError = errno; continue }
            configure(fd)
            if bindSource, !bindToSourcePort(fd, family: n.pointee.ai_family) { lastError = errno; close(fd); continue }
            if let code = connectWithTimeout(fd, n.pointee.ai_addr, n.pointee.ai_addrlen, seconds: 12) {
                lastError = code
                close(fd)
                continue
            }
            setTimeout(fd, seconds: 0)
            return .success(fd)
        }
        return .failure(.connect(lastError))
    }

    private func bindToSourcePort(_ fd: Int32, family: Int32) -> Bool {
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        let range = Gateway.sourcePorts
        for _ in 0..<64 {
            let candidate = lock.withLock { () -> UInt16 in
                let p = nextSource
                nextSource = p >= range.upperBound ? range.lowerBound : p + 1
                return p
            }
            let result: Int32
            if family == AF_INET6 {
                var a = sockaddr_in6()
                a.sin6_len = UInt8(MemoryLayout<sockaddr_in6>.size)
                a.sin6_family = sa_family_t(AF_INET6)
                a.sin6_port = candidate.bigEndian
                a.sin6_addr = in6_addr()
                result = withUnsafePointer(to: &a) { $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in6>.size)) } }
            } else {
                var a = sockaddr_in()
                a.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
                a.sin_family = sa_family_t(AF_INET)
                a.sin_port = candidate.bigEndian
                a.sin_addr.s_addr = 0
                result = withUnsafePointer(to: &a) { $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) } }
            }
            if result == 0 { return true }
            if errno != EADDRINUSE { return false }
        }
        return false
    }

    /// nil — соединились; иначе код ошибки.
    private func connectWithTimeout(_ fd: Int32, _ addr: UnsafeMutablePointer<sockaddr>, _ length: socklen_t, seconds: Int32) -> Int32? {
        let flags = fcntl(fd, F_GETFL, 0)
        _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)
        defer { _ = fcntl(fd, F_SETFL, flags) }
        if Darwin.connect(fd, addr, length) == 0 { return nil }
        guard errno == EINPROGRESS else { return errno }
        var p = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        guard poll(&p, 1, seconds * 1000) == 1 else { return ETIMEDOUT }
        var code: Int32 = 0
        var size = socklen_t(MemoryLayout<Int32>.size)
        getsockopt(fd, SOL_SOCKET, SO_ERROR, &code, &size)
        return code == 0 ? nil : code
    }

    // MARK: Перекачка

    /// Байты в обе стороны, пока одна из сторон не закроет соединение.
    private func pump(_ a: Int32, _ b: Int32) -> Int64 {
        setTimeout(a, seconds: 0)
        var fds = [pollfd(fd: a, events: Int16(POLLIN), revents: 0), pollfd(fd: b, events: Int16(POLLIN), revents: 0)]
        var buffer = [UInt8](repeating: 0, count: 65_536)
        var total: Int64 = 0
        while true {
            let ready = poll(&fds, 2, 300_000)
            if ready <= 0 { break }
            var finished = false
            for i in 0..<2 where fds[i].revents != 0 {
                let from = fds[i].fd, to = fds[1 - i].fd
                let n = buffer.withUnsafeMutableBytes { recv(from, $0.baseAddress, $0.count, 0) }
                if n <= 0 { finished = true; break }
                total += Int64(n)
                if !buffer.withUnsafeBytes({ sendAll(to, $0.baseAddress!, n) }) { finished = true; break }
            }
            if finished { break }
        }
        return total
    }

    // MARK: Мелочи сокетов

    private func configure(_ fd: Int32) {
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &yes, socklen_t(MemoryLayout<Int32>.size))
    }

    private func setTimeout(_ fd: Int32, seconds: Int) {
        var tv = timeval(tv_sec: seconds, tv_usec: 0)
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
    }

    /// Дочитать до `atLeast` байт.
    private func read(_ fd: Int32, into buffer: inout [UInt8], atLeast: Int) -> Bool {
        var chunk = [UInt8](repeating: 0, count: 16_384)
        while buffer.count < atLeast {
            let n = chunk.withUnsafeMutableBytes { recv(fd, $0.baseAddress, $0.count, 0) }
            if n <= 0 { return false }
            buffer += chunk[0..<n]
        }
        return true
    }

    private func send(_ fd: Int32, _ bytes: [UInt8]) -> Bool {
        bytes.withUnsafeBytes { sendAll(fd, $0.baseAddress!, bytes.count) }
    }

    private func sendAll(_ fd: Int32, _ base: UnsafeRawPointer, _ count: Int) -> Bool {
        var sent = 0
        while sent < count {
            let n = Darwin.send(fd, base + sent, count - sent, 0)
            if n <= 0 { return false }
            sent += n
        }
        return true
    }
}

nonisolated enum GatewayError: LocalizedError {
    case socket(Int32), bind(UInt16, Int32)

    var errorDescription: String? {
        switch self {
        case .socket(let code): "Шлюз не открыл сокет: \(String(cString: strerror(code)))"
        case .bind(let port, let code): "Порт \(port) занят другой программой (\(String(cString: strerror(code))))"
        }
    }
}

/// Разбор заголовка HTTP-запроса к прокси.
nonisolated enum HTTPParser {
    struct Head {
        var isConnect: Bool
        var host: String
        var port: Int
        /// Длина заголовка вместе с пустой строкой.
        var length: Int
        /// Для обычного HTTP — запрос в форме, которую ждёт сервер.
        var forwarded: [UInt8]
    }

    static func headerEnd(_ bytes: [UInt8]) -> Int? {
        guard bytes.count >= 4 else { return nil }
        for i in 0...(bytes.count - 4) where bytes[i] == 13 && bytes[i + 1] == 10 && bytes[i + 2] == 13 && bytes[i + 3] == 10 {
            return i + 4
        }
        return nil
    }

    static func parse(_ bytes: [UInt8]) -> Head? {
        guard let end = headerEnd(bytes) else { return nil }
        let text = String(decoding: bytes[0..<end], as: UTF8.self)
        var lines = text.components(separatedBy: "\r\n")
        let first = lines.removeFirst().split(separator: " ", omittingEmptySubsequences: true)
        guard first.count == 3 else { return nil }
        let method = String(first[0]), target = String(first[1]), version = String(first[2])
        if method.uppercased() == "CONNECT" {
            guard let (host, port) = hostPort(target, defaultPort: 443) else { return nil }
            return Head(isConnect: true, host: host, port: port, length: end, forwarded: [])
        }
        guard let url = URL(string: target), let host = url.host, url.scheme?.lowercased() == "http" else { return nil }
        let port = url.port ?? 80
        var path = url.path.isEmpty ? "/" : url.path
        if let q = url.query { path += "?" + q }
        var out = "\(method) \(path) \(version)\r\n"
        var hasHost = false
        for line in lines where !line.isEmpty {
            let lower = line.lowercased()
            if lower.hasPrefix("proxy-connection:") || lower.hasPrefix("proxy-authorization:") || lower.hasPrefix("connection:") || lower.hasPrefix("keep-alive:") { continue }
            if lower.hasPrefix("host:") { hasHost = true }
            out += line + "\r\n"
        }
        if !hasHost { out += "Host: \(host)\(port == 80 ? "" : ":\(port)")\r\n" }
        out += "Connection: close\r\n\r\n"
        return Head(isConnect: false, host: host, port: port, length: end, forwarded: Array(out.utf8))
    }

    /// «host:port», «[v6]:port».
    static func hostPort(_ s: String, defaultPort: Int) -> (String, Int)? {
        if s.hasPrefix("["), let close = s.firstIndex(of: "]") {
            let host = String(s[s.index(after: s.startIndex)..<close])
            let rest = s[s.index(after: close)...]
            let port = rest.hasPrefix(":") ? Int(rest.dropFirst()) : defaultPort
            return port.map { (host, $0) }
        }
        let parts = s.split(separator: ":")
        guard let host = parts.first, !host.isEmpty else { return nil }
        if parts.count == 2 { return Int(parts[1]).map { (String(host), $0) } }
        return parts.count == 1 ? (String(host), defaultPort) : nil
    }
}

/// SOCKS5: разбор запроса клиента и рукопожатие со следующим шагом.
nonisolated enum SOCKSParser {
    /// (адрес, порт, длина запроса) — или nil, если пришло не всё.
    static func request(_ b: [UInt8]) -> (String, Int, Int)? {
        guard b.count >= 5, b[0] == 5 else { return nil }
        switch b[3] {
        case 1:
            guard b.count >= 10 else { return nil }
            return ("\(b[4]).\(b[5]).\(b[6]).\(b[7])", Int(b[8]) << 8 | Int(b[9]), 10)
        case 3:
            let n = Int(b[4])
            guard b.count >= 7 + n else { return nil }
            return (String(decoding: b[5..<(5 + n)], as: UTF8.self), Int(b[5 + n]) << 8 | Int(b[6 + n]), 7 + n)
        case 4:
            guard b.count >= 22 else { return nil }
            var groups: [String] = []
            for i in stride(from: 4, to: 20, by: 2) { groups.append(String(Int(b[i]) << 8 | Int(b[i + 1]), radix: 16)) }
            return (groups.joined(separator: ":"), Int(b[20]) << 8 | Int(b[21]), 22)
        default:
            return nil
        }
    }

    /// Рукопожатие с SOCKS5-сервером (без пароля) и CONNECT по имени. nil — успех, иначе причина.
    static func handshake(_ fd: Int32, host: String, port: Int) -> String? {
        func sendBytes(_ bytes: [UInt8]) -> Bool { bytes.withUnsafeBytes { Darwin.send(fd, $0.baseAddress, bytes.count, 0) == bytes.count } }
        func recvBytes(_ n: Int) -> [UInt8]? {
            var out = [UInt8](), chunk = [UInt8](repeating: 0, count: n)
            while out.count < n {
                let got = chunk.withUnsafeMutableBytes { recv(fd, $0.baseAddress, n - out.count, 0) }
                if got <= 0 { return nil }
                out += chunk[0..<got]
            }
            return out
        }
        guard sendBytes([5, 1, 0]), let hello = recvBytes(2), hello == [5, 0] else { return "не отвечает" }
        let name = Array(host.utf8.prefix(255))
        guard sendBytes([5, 1, 0, 3, UInt8(name.count)] + name + [UInt8(port >> 8), UInt8(port & 0xFF)]),
              let head = recvBytes(4), head[1] == 0 else { return "отказал в соединении" }
        let rest: Int = switch head[3] {
        case 1: 6
        case 4: 18
        case 3: Int(recvBytes(1)?.first ?? 0) + 2
        default: 0
        }
        return rest > 0 && recvBytes(rest) == nil ? "оборвал соединение" : nil
    }
}
