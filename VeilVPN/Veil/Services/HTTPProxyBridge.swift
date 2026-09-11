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
        /// Outcomes, as opposed to the counters above, which record which route was chosen.
        var torFailed = 0
        var torRetried = 0
        var torHedged = 0
    }

    private let queue = DispatchQueue(label: "app.veilvpn.httpbridge")
    private let lock = NSLock()
    private var listener: NWListener?
    private var sessions: [ObjectIdentifier: ProxySession] = [:]
    private var storedPolicy: RoutingPolicy
    private var storedSocksPort: UInt16?
    private var storedBlockAll = false
    private var storedStats = Stats()
    private var storedPoolPort: UInt16?
    private var storedHTTPSOnly = false
    private var storedTransport: AppSettings.Transport = .auto
    private var storedConnectedAt: Date?
    private(set) var port: UInt16 = 0

    /// Measured circuits for traffic through Tor. Created here, started and stopped by AppState.
    let lanePool = LanePool()

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

    /// Tor's isolation-flagged SOCKS listener, or nil when the lane pool is off.
    var poolPort: UInt16? {
        get { lock.lock(); defer { lock.unlock() }; return storedPoolPort }
        set { lock.lock(); storedPoolPort = newValue; lock.unlock() }
    }

    /// Refuse plain `http://` through Tor. An exit relay can read and rewrite unencrypted traffic,
    /// and it is the one part of the path the user did not choose.
    var httpsOnly: Bool {
        get { lock.lock(); defer { lock.unlock() }; return storedHTTPSOnly }
        set { lock.lock(); storedHTTPSOnly = newValue; lock.unlock() }
    }

    /// Context the hedge threshold needs: a young snowflake session is still finding proxies.
    func setConnectionContext(transport: AppSettings.Transport, connectedAt: Date?) {
        lock.lock()
        storedTransport = transport
        storedConnectedAt = connectedAt
        lock.unlock()
    }

    var inFlight: Int {
        lock.lock()
        defer { lock.unlock() }
        return sessions.count
    }

    /// Cuts every open connection. The kill switch refuses new ones on its own; this is what makes
    /// it apply to the download that was already running.
    func closeAllSessions() {
        lock.lock()
        let active = Array(sessions.values)
        lock.unlock()
        active.forEach { $0.close() }
    }

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
        let pool = storedPoolPort
        let transport = storedTransport
        let connectedAt = storedConnectedAt
        let httpsOnly = storedHTTPSOnly
        lock.unlock()
        let session = ProxySession(client: connection, socksPort: socks, policy: policy, blocked: blocked,
                                   pool: pool == nil ? nil : lanePool, poolPort: pool,
                                   transport: transport, connectedAt: connectedAt, httpsOnly: httpsOnly,
                                   onDecision: { [weak self] decision in
            self?.record(decision)
        }, onOutcome: { [weak self] outcome in
            self?.recordOutcome(outcome)
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

    enum SessionOutcome: Sendable { case failed, retried, hedged }

    private func recordOutcome(_ outcome: SessionOutcome) {
        lock.lock()
        switch outcome {
        case .failed: storedStats.torFailed += 1
        case .retried: storedStats.torRetried += 1
        case .hedged: storedStats.torHedged += 1
        }
        lock.unlock()
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
    private let onOutcome: @Sendable (HTTPProxyBridge.SessionOutcome) -> Void
    private let onClose: (ProxySession) -> Void
    private let pool: LanePool?
    private let poolPort: UInt16?
    private let transport: AppSettings.Transport
    private let connectedAt: Date?
    private let httpsOnly: Bool
    private var upstream: NWConnection?
    private var head = Data()
    private var helloBuffer = Data()
    private var closed = false
    private var finishedDirections = 0
    private var lease: LaneLease?
    /// Replaces the captured local that used to live inside the state handler: a local cannot
    /// survive a re-attempt, and a doubled `respond` is the likeliest bug in this file.
    private var upstreamSettled = false
    private var attempt = 0
    private var deadline: DispatchWorkItem?
    private var upstreamStartedAt: DispatchTime?
    private var pendingHost = ""
    private var pendingPort: UInt16 = 0
    private var pendingIsOnion = false
    private var hedgeDelay: TimeInterval?

    init(client: NWConnection, socksPort: UInt16?, policy: RoutingPolicy, blocked: Bool,
         pool: LanePool?, poolPort: UInt16?, transport: AppSettings.Transport, connectedAt: Date?,
         httpsOnly: Bool,
         onDecision: @escaping @Sendable (RouteDecision?) -> Void,
         onOutcome: @escaping @Sendable (HTTPProxyBridge.SessionOutcome) -> Void,
         onClose: @escaping (ProxySession) -> Void) {
        self.client = client
        self.socksPort = socksPort
        self.policy = policy
        self.blocked = blocked
        self.pool = pool
        self.poolPort = poolPort
        self.transport = transport
        self.connectedAt = connectedAt
        self.httpsOnly = httpsOnly
        self.onDecision = onDecision
        self.onOutcome = onOutcome
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
            self.deadline?.cancel()
            self.deadline = nil
            self.client.cancel()
            self.upstream?.cancel()
            if let lease = self.lease {
                self.lease = nil
                self.pool?.release(lease)
            }
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
        // An exit relay can read and rewrite anything that is not encrypted, and it is the one hop
        // on the path the user did not choose.
        if httpsOnly, socksPort != nil, policy.decision(for: host, torAvailable: true) == .tor {
            onDecision(nil)
            respond(403, "Veil: plain HTTP is blocked through Tor")
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
        guard NWEndpoint.Port(rawValue: port) != nil else {
            respond(400, "Bad Request")
            return
        }
        switch route {
        case .tor:
            pendingHost = host
            pendingPort = port
            pendingIsOnion = host.lowercased().hasSuffix(".onion")
            let site = HostKey.site(host)
            lease = pool?.lease(site: site, avoiding: nil)
            let snapshot = pool?.snapshot()
            hedgeDelay = HedgePolicy.hedgeDelay(
                bestP50: snapshot?.bestP50,
                isOnion: pendingIsOnion,
                enabled: pool?.hedgingEnabled ?? false,
                readyLanes: snapshot?.readyLanes ?? 0,
                transport: transport,
                secondsSinceConnect: connectedAt.map { Date.now.timeIntervalSince($0) } ?? 0
            )
            openTorUpstream(onReady: onReady)
        case .direct(let antiThrottle):
            openDirectUpstream(host: host, port: port, antiThrottle: antiThrottle, onReady: onReady)
        }
    }

    /// A lease is never required: with no pool, none ready, or a suspended one, this is the plain
    /// no-auth path against `socksPort`, byte-identical to the behaviour before lanes existed.
    private func openTorUpstream(onReady: @escaping () -> Void) {
        let target = lease?.port ?? socksPort
        guard let target, let socksEndpoint = NWEndpoint.Port(rawValue: target) else {
            respond(502, "Bad Gateway")
            return
        }
        upstreamSettled = false
        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.connectionTimeout = 5 // loopback: 20 s was meaningless
        let parameters = NWParameters(tls: nil, tcp: tcpOptions)
        parameters.preferNoProxies = true
        let upstream = NWConnection(host: "127.0.0.1", port: socksEndpoint, using: parameters)
        self.upstream = upstream
        upstreamStartedAt = .now()

        let seconds = HedgePolicy.deadline(attempt: attempt, isOnion: pendingIsOnion, hedgeDelay: hedgeDelay)
        let work = DispatchWorkItem { [weak self] in self?.deadlineExpired(onReady: onReady) }
        deadline?.cancel()
        deadline = work
        queue.asyncAfter(deadline: .now() + seconds, execute: work)

        let host = pendingHost
        let port = pendingPort
        let credentials = lease?.credentials
        upstream.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                guard !self.upstreamSettled else { return }
                SOCKS5.connect(on: upstream, host: host, port: port, credentials: credentials) { [weak self] error, outcome in
                    self?.queue.async {
                        self?.handleTorHandshake(error: error, outcome: outcome, onReady: onReady)
                    }
                }
            case .waiting, .failed:
                self.queue.async { self.handleTorTransportFailure(onReady: onReady) }
            case .cancelled:
                if self.upstreamSettled { self.close() }
            default:
                break
            }
        }
        upstream.start(queue: queue)
    }

    private func openDirectUpstream(host: String, port: UInt16, antiThrottle: Bool, onReady: @escaping () -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            respond(400, "Bad Request")
            return
        }
        upstreamSettled = false
        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.noDelay = antiThrottle // each write must leave as its own segment
        tcpOptions.connectionTimeout = 20
        let parameters = NWParameters(tls: nil, tcp: tcpOptions)
        parameters.preferNoProxies = true // the system proxy is Veil itself
        let upstream = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: parameters)
        self.upstream = upstream
        upstream.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                self.queue.async {
                    guard !self.upstreamSettled else { return }
                    self.upstreamSettled = true
                    onReady()
                }
            case .waiting, .failed:
                self.queue.async {
                    if !self.upstreamSettled {
                        self.upstreamSettled = true
                        self.respond(502, "Bad Gateway")
                    } else {
                        self.close()
                    }
                }
            case .cancelled:
                self.close()
            default:
                break
            }
        }
        upstream.start(queue: queue)
    }

    private func handleTorHandshake(error: Error?, outcome: SOCKS5.Outcome, onReady: @escaping () -> Void) {
        guard !closed, !upstreamSettled else { return }
        deadline?.cancel()
        deadline = nil
        if error == nil {
            upstreamSettled = true
            if let lease {
                let seconds = upstreamStartedAt.map {
                    Double(DispatchTime.now().uptimeNanoseconds &- $0.uptimeNanoseconds) / 1e9
                } ?? 0
                pool?.report(.connected(lease, seconds: seconds, isOnion: pendingIsOnion))
                if attempt > 0 { pool?.noteHedgeWon() }
            }
            onReady()
            return
        }
        // Isolation silently gone: Tor chose no-auth, so the pool is not measuring what it thinks.
        if lease != nil, !outcome.isolationApplied, outcome.replyCode == nil {
            pool?.suspend(reason: "Tor did not accept SOCKS authentication")
        }
        var code = outcome.replyCode
        if code == nil, let failure = error as? SOCKS5.Failure, case .rejected(let value) = failure {
            code = value
        }
        if let lease { pool?.report(.failed(lease, code: code)) }
        // Only a reply code that blames the circuit is worth a second one.
        if let code, !SOCKS5.laneAttributable(code) {
            onOutcome(.failed)
            respond(502, "Bad Gateway (\(SOCKS5.Failure.describe(code)))")
            return
        }
        retryOnAnotherLane(hedged: false, onReady: onReady)
    }

    private func handleTorTransportFailure(onReady: @escaping () -> Void) {
        guard !closed, !upstreamSettled else { return }
        deadline?.cancel()
        deadline = nil
        if lease != nil, attempt == 0 {
            // Tor's lane listener is gone; the pool steps aside and this connection retries plain.
            pool?.suspend(reason: "the lane port stopped answering")
            releaseLease()
            attempt = 1
            onOutcome(.retried)
            openTorUpstream(onReady: onReady)
            return
        }
        upstreamSettled = true
        onOutcome(.failed)
        respond(502, "Bad Gateway")
    }

    /// Nothing has been written upstream at this point — no byte has reached the client, the
    /// `200 Connection Established` has not been sent and the buffered request is still buffered —
    /// so a second attempt is safe for any HTTP method.
    private func retryOnAnotherLane(hedged: Bool, onReady: @escaping () -> Void) {
        guard !closed, !upstreamSettled, attempt == 0 else {
            if !upstreamSettled {
                upstreamSettled = true
                onOutcome(.failed)
                respond(hedged ? 504 : 502, hedged ? "Gateway Timeout" : "Bad Gateway")
            }
            return
        }
        let previous = lease
        // Detach the handler first: a cancelled connection still reports state, and that report
        // must not be mistaken for the new attempt failing.
        upstream?.stateUpdateHandler = nil
        upstream?.cancel()
        upstream = nil
        if let previous {
            pool?.report(hedged ? .abandoned(previous) : .timedOut(previous))
            pool?.release(previous)
        }
        lease = nil
        guard let next = pool?.bestLane(avoiding: previous?.lane) else {
            upstreamSettled = true
            onOutcome(.failed)
            respond(hedged ? 504 : 502, hedged ? "Gateway Timeout" : "Bad Gateway")
            return
        }
        lease = next
        attempt = 1
        onOutcome(hedged ? .hedged : .retried)
        openTorUpstream(onReady: onReady)
    }

    private func deadlineExpired(onReady: @escaping () -> Void) {
        guard !closed, !upstreamSettled else { return }
        deadline = nil
        if attempt == 0, hedgeDelay != nil, pool != nil {
            retryOnAnotherLane(hedged: true, onReady: onReady)
            return
        }
        if let lease { pool?.report(.timedOut(lease)) }
        upstreamSettled = true
        onOutcome(.failed)
        respond(504, "Gateway Timeout")
    }

    private func releaseLease() {
        guard let lease else { return }
        self.lease = nil
        pool?.release(lease)
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
