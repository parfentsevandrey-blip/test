import Foundation
import Network

/// A tiny local HTTP proxy (CONNECT tunnels + absolute-URI requests) that forwards everything to
/// Tor's SOCKS5 port. It gives the system "Web Proxy (HTTP)" / "Secure Web Proxy (HTTPS)" settings
/// something to point at, so apps that only understand HTTP proxies still go through Tor.
/// Hostnames are handed to Tor unresolved, so there are no local DNS leaks.
final class HTTPProxyBridge: @unchecked Sendable {
    private let socksPort: UInt16
    private let queue = DispatchQueue(label: "app.veilvpn.httpbridge")
    private let lock = NSLock()
    private var listener: NWListener?
    private var sessions: [ObjectIdentifier: ProxySession] = [:]
    private(set) var port: UInt16 = 0

    init(socksPort: UInt16) {
        self.socksPort = socksPort
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
        let session = ProxySession(client: connection, socksPort: socksPort) { [weak self] finished in
            guard let self else { return }
            self.lock.lock()
            self.sessions[ObjectIdentifier(finished)] = nil
            self.lock.unlock()
        }
        lock.lock()
        sessions[ObjectIdentifier(session)] = session
        lock.unlock()
        session.start()
    }
}

/// One client connection of the HTTP bridge.
final class ProxySession: @unchecked Sendable {
    private let client: NWConnection
    private let socksPort: UInt16
    private let queue = DispatchQueue(label: "app.veilvpn.httpbridge.session")
    private let onClose: (ProxySession) -> Void
    private var upstream: NWConnection?
    private var head = Data()
    private var closed = false
    private var finishedDirections = 0

    init(client: NWConnection, socksPort: UInt16, onClose: @escaping (ProxySession) -> Void) {
        self.client = client
        self.socksPort = socksPort
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
            openUpstream(host: destination.host, port: destination.port) { [weak self] in
                guard let self else { return }
                let established = Data("HTTP/1.1 200 Connection Established\r\nProxy-Agent: Veil\r\n\r\n".utf8)
                self.client.send(content: established, completion: .contentProcessed { [weak self] error in
                    guard let self else { return }
                    if error != nil {
                        self.close()
                        return
                    }
                    if !remainder.isEmpty {
                        self.upstream?.send(content: remainder, completion: .contentProcessed { _ in })
                    }
                    self.startPiping()
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

        openUpstream(host: host, port: port) { [weak self] in
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
            // [ipv6]:port
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

    // MARK: Upstream through SOCKS5

    private func openUpstream(host: String, port: UInt16, onReady: @escaping () -> Void) {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            respond(400, "Bad Request")
            return
        }
        let parameters = NWParameters.tcp
        let socks = ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050))
        parameters.proxyConfigurations = [socks]
        let upstream = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: parameters)
        self.upstream = upstream
        var signalled = false
        upstream.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                if !signalled {
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
