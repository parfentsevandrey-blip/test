import Foundation
import Network

/// A tiny local HTTP proxy (CONNECT tunnels + absolute-URI requests). By default everything is
/// forwarded to Tor's SOCKS5 port with a hand-made SOCKS5 CONNECT, so host names are resolved by
/// Tor (no DNS leaks, `.onion` works). A `RoutingPolicy` can send chosen hosts (YouTube, services,
/// custom domains) directly instead, optionally fragmenting the TLS ClientHello so throttling DPI
/// cannot read the SNI. With `socksPort == nil` nothing goes through Tor (YouTube Turbo), and with
/// `blockAll` every request is refused (kill switch: fail closed while Tor is down).
final class HTTPProxyBridge: @unchecked Sendable {
    struct Stats: Equatable, Sendable {
        var tor = 0
        var direct = 0
        var antiThrottle = 0
        var blocked = 0
    }

    private let queue = DispatchQueue(label: "app.veilvpn.httpbridge")
    private let lock = NSLock()
    private var listener: NWListener?
    private var sessions: [ObjectIdentifier: ProxySession] = [:]
    private var storedPolicy: RoutingPolicy
    private var storedSocksPort: UInt16?
    private var storedBlockAll = false
    private var storedStats = Stats()
    private(set) var port: UInt16 = 0

    init(socksPort: UInt16?, policy: RoutingPolicy) {
        storedSocksPort = socksPort
        storedPolicy = policy
    }

    /// Tor's SOCKS port, or nil when Tor is not available. Applies to new connections.
    var socksPort: UInt16? {
        get { lock.lock(); defer { lock.unlock() }; return storedSocksPort }
        set { lock.lock(); storedSocksPort = newValue; lock.unlock() }
    }

    var torAvailable: Bool { socksPort != nil }

    /// Refuse every request (kill switch). Applies to new connections.
    var blockAll: Bool {
        get { lock.lock(); defer { lock.unlock() }; return storedBlockAll }
        set { lock.lock(); storedBlockAll = newValue; lock.unlock() }
    }

    /// Applies to connections accepted after the change.
    var policy: RoutingPolicy {
        get { lock.lock(); defer { lock.unlock() }; return storedPolicy }
        set { lock.lock(); storedPolicy = newValue; lock.unlock() }
    }

    var stats: Stats {
        lock.lock()
        defer { lock.unlock() }
        return storedStats
    }

    func resetStats() {
        lock.lock()
        storedStats = Stats()
        lock.unlock()
    }

    func start(port: UInt16) throws {
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port) ?? 8118)
        let listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        listener.start(queue: queue)
        self.listener = listener
        self.port = port
    }

    func stop() {
        listener?.cancel()
        listener = nil
        lock.lock()
        let active = Array(sessions.values)
        sessions = [:]
        lock.unlock()
        active.forEach { $0.close() }
    }

    private func accept(_ connection: NWConnection) {
        lock.lock()
        let socks = storedSocksPort
        let policy = storedPolicy
        let blocked = storedBlockAll
        lock.unlock()
        let session = ProxySession(client: connection, socksPort: socks, policy: policy, blocked: blocked, onDecision: { [weak self] decision in
            self?.record(decision)
        }, onClose: { [weak self] finished in
            guard let self else { return }
            self.lock.lock()
            self.sessions[ObjectIdentifier(finished)] = nil
            self.lock.unlock()
        })
        lock.lock()
        sessions[ObjectIdentifier(session)] = session
        lock.unlock()
        session.start()
    }

    private func record(_ decision: RouteDecision?) {
        lock.lock()
        switch decision {
        case .none: storedStats.blocked += 1
        case .some(.tor): storedStats.tor += 1
        case .some(.direct(let antiThrottle)):
            storedStats.direct += 1
            if antiThrottle { storedStats.antiThrottle += 1 }
        }
        lock.unlock()
    }
}

/// One client connection of the HTTP bridge.
final class ProxySession: @unchecked Sendable {
    private let client: NWConnection
    private let socksPort: UInt16?
    private let policy: RoutingPolicy
    private let blocked: Bool
    private let queue = DispatchQueue(label: "app.veilvpn.httpbridge.session")
    private let onDecision: @Sendable (RouteDecision?) -> Void
    private let onClose: (ProxySession) -> Void
    private var upstream: NWConnection?
    private var head = Data()
    private var helloBuffer = Data()
    private var closed = false
    private var finishedDirections = 0

    init(client: NWConnection, socksPort: UInt16?, policy: RoutingPolicy, blocked: Bool,
         onDecision: @escaping @Sendable (RouteDecision?) -> Void, onClose: @escaping (ProxySession) -> Void) {
        self.client = client
        self.socksPort = socksPort
        self.policy = policy
        self.blocked = blocked
        self.onDecision = onDecision
        self.onClose = onClose
    }

    func start() {
        client.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled:
                self?.close()
            default:
                break
            }
        }
        client.start(queue: queue)
        readHead()
    }

    func close() {
        queue.async {
            guard !self.closed else { return }
            self.closed = true
            self.client.cancel()
            self.upstream?.cancel()
            self.onClose(self)
        }
    }

    // MARK: Request parsing

    private func readHead() {
        client.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, isComplete, error in
            guard let self, !self.closed else { return }
            if let data {
                self.head.append(data)
            }
            if let range = self.head.range(of: Data("\r\n\r\n".utf8)) {
                self.handleRequest(headerEnd: range)
            } else if isComplete || error != nil || self.head.count > 65536 {
                self.close()
            } else {
                self.readHead()
            }
        }
    }

    private func handleRequest(headerEnd: Range<Data.Index>) {
        let headerData = head.subdata(in: head.startIndex..<headerEnd.lowerBound)
        let remainder = head.subdata(in: headerEnd.upperBound..<head.endIndex)
        head = Data()

        if blocked {
            onDecision(nil)
            respond(503, "Veil kill switch: Tor is not running")
            return
        }

        guard let headerText = String(data: headerData, encoding: .utf8) else {
            respond(400, "Bad Request")
            return
        }
        var lines = headerText.components(separatedBy: "\r\n")
        let requestLine = lines.removeFirst()
        let parts = requestLine.split(separator: " ", omittingEmptySubsequences: true)
        guard parts.count >= 2 else {
            respond(400, "Bad Request")
            return
        }
        let method = String(parts[0]).uppercased()
        let target = String(parts[1])

        if method == "CONNECT" {
            guard let destination = Self.hostPort(from: target, defaultPort: 443) else {
                respond(400, "Bad Request")
                return
            }
            let route = policy.decision(for: destination.host, torAvailable: socksPort != nil)
            onDecision(route)
            openUpstream(host: destination.host, port: destination.port, route: route) { [weak self] in
                guard let self else { return }
                let established = Data("HTTP/1.1 200 Connection Established\r\nProxy-Agent: Veil\r\n\r\n".utf8)
                self.client.send(content: established, completion: .contentProcessed { [weak self] error in
                    guard let self else { return }
                    if error != nil {
                        self.close()
                        return
                    }
                    self.helloBuffer = remainder
                    if case .direct(antiThrottle: true) = route {
                        self.processClientHelloBuffer()
                    } else {
                        self.forwardBufferedAndPipe()
                    }
                })
            }
            return
        }

        guard let url = URL(string: target), let scheme = url.scheme?.lowercased(), let host = url.host() else {
            respond(400, "Bad Request")
            return
        }
        guard scheme == "http" else {
            respond(501, "Not Implemented")
            return
        }
        let port = UInt16(clamping: url.port ?? 80)
        var path = url.path(percentEncoded: true)
        if path.isEmpty { path = "/" }
        if let query = url.query(percentEncoded: true) { path += "?" + query }

        var forwarded = ["\(method) \(path) HTTP/1.1"]
        var sawHost = false
        for line in lines where !line.isEmpty {
            let lower = line.lowercased()
            if lower.hasPrefix("proxy-connection:") || lower.hasPrefix("proxy-authorization:")
                || lower.hasPrefix("connection:") || lower.hasPrefix("keep-alive:") {
                continue
            }
            if lower.hasPrefix("host:") { sawHost = true }
            forwarded.append(line)
        }
        if !sawHost {
            forwarded.append("Host: \(host)\(url.port.map { ":\($0)" } ?? "")")
        }
        forwarded.append("Connection: close")
        let requestHead = Data((forwarded.joined(separator: "\r\n") + "\r\n\r\n").utf8)

        let route = policy.decision(for: host, torAvailable: socksPort != nil)
        onDecision(route)
        openUpstream(host: host, port: port, route: route) { [weak self] in
            guard let self, let upstream = self.upstream else { return }
            upstream.send(content: requestHead + remainder, completion: .contentProcessed { [weak self] error in
                guard let self else { return }
                if error != nil {
                    self.close()
                } else {
                    self.startPiping()
                }
            })
        }
    }

    private static func hostPort(from target: String, defaultPort: UInt16) -> (host: String, port: UInt16)? {
        var host = target
        var port = defaultPort
        if target.hasPrefix("[") {
            guard let close = target.firstIndex(of: "]") else { return nil }
            host = String(target[target.index(after: target.startIndex)..<close])
            let rest = target[target.index(after: close)...]
            if rest.hasPrefix(":"), let value = UInt16(rest.dropFirst()) { port = value }
        } else if let colon = target.lastIndex(of: ":") {
            host = String(target[..<colon])
            guard let value = UInt16(target[target.index(after: colon)...]) else { return nil }
            port = value
        }
        return host.isEmpty ? nil : (host, port)
    }

    // MARK: Upstream: through Tor's SOCKS5 port or directly

    private func openUpstream(host: String, port: UInt16, route: RouteDecision, onReady: @escaping () -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            respond(400, "Bad Request")
            return
        }
        let upstream: NWConnection
        var socksTarget: (host: String, port: UInt16)?
        switch route {
        case .tor:
            guard let socksPort else {
                respond(502, "Bad Gateway")
                return
            }
            let tcpOptions = NWProtocolTCP.Options()
            tcpOptions.connectionTimeout = 20
            upstream = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050, using: NWParameters(tls: nil, tcp: tcpOptions))
            socksTarget = (host, port)
        case .direct(let antiThrottle):
            let tcpOptions = NWProtocolTCP.Options()
            tcpOptions.noDelay = antiThrottle // each write must leave as its own segment
            tcpOptions.connectionTimeout = 20
            upstream = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: NWParameters(tls: nil, tcp: tcpOptions))
        }
        self.upstream = upstream
        var signalled = false
        upstream.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                guard !signalled else { return }
                if let socksTarget {
                    SOCKS5.connect(on: upstream, host: socksTarget.host, port: socksTarget.port) { [weak self] error in
                        guard !signalled else { return }
                        signalled = true
                        if error != nil {
                            self?.respond(502, "Bad Gateway")
                        } else {
                            onReady()
                        }
                    }
                } else {
                    signalled = true
                    onReady()
                }
            case .waiting, .failed:
                if !signalled {
                    signalled = true
                    self?.respond(502, "Bad Gateway")
                } else {
                    self?.close()
                }
            case .cancelled:
                self?.close()
            default:
                break
            }
        }
        upstream.start(queue: queue)
    }

    private func respond(_ status: Int, _ reason: String) {
        let body = "\(status) \(reason)\n"
        let response = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: text/plain\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
        client.send(content: Data(response.utf8), completion: .contentProcessed { [weak self] _ in
            self?.close()
        })
    }

    // MARK: Anti-throttling: fragment the first TLS record

    private func processClientHelloBuffer() {
        guard upstream != nil, !closed else { return }
        if helloBuffer.count >= 6, !ClientHelloSplitter.isClientHello(helloBuffer) {
            forwardBufferedAndPipe()
            return
        }
        guard let recordLength = ClientHelloSplitter.firstRecordLength(helloBuffer), helloBuffer.count >= 6 else {
            receiveMoreClientHello()
            return
        }
        guard helloBuffer.count >= recordLength else {
            if helloBuffer.count > 65_536 {
                forwardBufferedAndPipe()
            } else {
                receiveMoreClientHello()
            }
            return
        }
        let record = Data(helloBuffer.prefix(recordLength))
        let rest = Data(helloBuffer.dropFirst(recordLength))
        helloBuffer = Data()
        var chunks = ClientHelloSplitter.chunks(for: record, strategy: policy.strategy)
        if !rest.isEmpty {
            chunks.append(rest)
        }
        sendSequentially(chunks, index: 0) { [weak self] error in
            guard let self else { return }
            if error != nil {
                self.close()
            } else {
                self.startPiping()
            }
        }
    }

    private func receiveMoreClientHello() {
        client.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, isComplete, error in
            guard let self, !self.closed else { return }
            if let data {
                self.helloBuffer.append(data)
            }
            if isComplete || error != nil {
                self.forwardBufferedAndPipe()
                return
            }
            self.processClientHelloBuffer()
        }
    }

    private func forwardBufferedAndPipe() {
        guard let upstream else {
            close()
            return
        }
        let pending = helloBuffer
        helloBuffer = Data()
        if pending.isEmpty {
            startPiping()
            return
        }
        upstream.send(content: pending, completion: .contentProcessed { [weak self] error in
            guard let self else { return }
            if error != nil {
                self.close()
            } else {
                self.startPiping()
            }
        })
    }

    /// Writes chunks one after another, with a short pause so each one travels in its own segment.
    private func sendSequentially(_ chunks: [Data], index: Int, completion: @escaping (Error?) -> Void) {
        guard let upstream, !closed else {
            completion(PaddingError.cancelled)
            return
        }
        guard index < chunks.count else {
            completion(nil)
            return
        }
        upstream.send(content: chunks[index], completion: .contentProcessed { [weak self] error in
            guard let self else { return }
            if let error {
                completion(error)
                return
            }
            let isLast = index == chunks.count - 1
            self.queue.asyncAfter(deadline: .now() + .milliseconds(isLast ? 0 : 25)) {
                self.sendSequentially(chunks, index: index + 1, completion: completion)
            }
        })
    }

    // MARK: Bidirectional piping

    private func startPiping() {
        guard let upstream else {
            close()
            return
        }
        pipe(from: client, to: upstream)
        pipe(from: upstream, to: client)
    }

    private func pipe(from source: NWConnection, to destination: NWConnection) {
        source.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            guard let self, !self.closed else { return }
            if let data, !data.isEmpty {
                destination.send(content: data, completion: .contentProcessed { [weak self] sendError in
                    guard let self, !self.closed else { return }
                    if sendError != nil {
                        self.close()
                    } else if isComplete || error != nil {
                        self.halfClose(destination)
                    } else {
                        self.pipe(from: source, to: destination)
                    }
                })
            } else if isComplete || error != nil {
                self.halfClose(destination)
            } else {
                self.pipe(from: source, to: destination)
            }
        }
    }

    private func halfClose(_ destination: NWConnection) {
        destination.send(content: nil, contentContext: .finalMessage, isComplete: true, completion: .idempotent)
        finishedDirections += 1
        if finishedDirections >= 2 {
            close()
        }
    }
}
