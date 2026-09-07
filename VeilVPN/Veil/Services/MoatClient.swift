import Foundation
import Network

/// Fetches bridges from the Tor Project's Moat / rdsys "circumvention settings" API — the same
/// service Tor Browser's Connection Assist uses. Tries bridges.torproject.org directly, then a
/// domain-fronted reflector on a CDN (TLS to the front host, HTTP `Host:` of the reflector).
enum MoatClient {
    struct BridgeSet: Identifiable, Equatable, Sendable {
        let transport: String
        let source: String
        let bridges: [String]
        var id: String { transport + "/" + source }
    }

    enum MoatError: LocalizedError {
        case badResponse(Int)
        case noBridges
        case unreachable(String)

        var errorDescription: String? {
            switch self {
            case .badResponse(let status): return String(localized: "The bridge service answered with HTTP \(status).")
            case .noBridges: return String(localized: "The bridge service has no bridges for this location.")
            case .unreachable(let detail): return String(localized: "Could not reach the bridge service: \(detail)")
            }
        }
    }

    static let directBase = "https://bridges.torproject.org"
    /// Domain-fronting parameters used by Tor Browser for Moat (reflector on CDN77, front on phpMyAdmin's site).
    static let reflectorHost = "1723181537.rsc.cdn77.org"
    static let frontHost = "www.phpmyadmin.net"
    static let transports = ["obfs4", "snowflake", "webtunnel"]

    /// Recommended bridges for the current (or given) country.
    static func fetchCircumventionSettings(country: String?) async throws -> [BridgeSet] {
        var payload: [String: Any] = ["transports": transports]
        if let country, !country.isEmpty {
            payload["country"] = country.lowercased()
        }
        let body = try JSONSerialization.data(withJSONObject: payload)
        let data = try await post(path: "/moat/circumvention/settings", body: body)
        return try parseSettings(data)
    }

    /// The current built-in bridge lines, keyed by transport.
    static func fetchBuiltin() async throws -> [String: [String]] {
        let data = try await post(path: "/moat/circumvention/builtin", body: Data("{}".utf8))
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw MoatError.noBridges }
        var result: [String: [String]] = [:]
        for (key, value) in json {
            if let lines = value as? [String] { result[key] = lines }
        }
        return result
    }

    static func parseSettings(_ data: Data) throws -> [BridgeSet] {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw MoatError.noBridges }
        if let errors = json["errors"] as? [[String: Any]], let first = errors.first {
            let detail = (first["detail"] as? String) ?? (first["code"] as? String) ?? "error"
            throw MoatError.unreachable(detail)
        }
        var sets: [BridgeSet] = []
        for entry in json["settings"] as? [[String: Any]] ?? [] {
            guard let bridges = entry["bridges"] as? [String: Any],
                  let type = bridges["type"] as? String,
                  let lines = bridges["bridge_strings"] as? [String], !lines.isEmpty else { continue }
            let source = (bridges["source"] as? String) ?? "moat"
            sets.append(BridgeSet(transport: type, source: source, bridges: lines))
        }
        guard !sets.isEmpty else { throw MoatError.noBridges }
        return sets
    }

    private static func post(path: String, body: Data) async throws -> Data {
        do {
            return try await postDirect(path: path, body: body)
        } catch {
            let directFailure = error.localizedDescription
            do {
                let response = try await FrontedHTTPClient.post(front: frontHost, host: reflectorHost, path: path, body: body, timeout: .seconds(25))
                guard (200..<300).contains(response.status) else { throw MoatError.badResponse(response.status) }
                return response.body
            } catch {
                throw MoatError.unreachable("\(directFailure); fronted: \(error.localizedDescription)")
            }
        }
    }

    private static func postDirect(path: String, body: Data) async throws -> Data {
        var request = URLRequest(url: URL(string: directBase + path)!)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("application/vnd.api+json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/vnd.api+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 15
        let configuration = URLSessionConfiguration.ephemeral
        configuration.proxyConfigurations = [] // never through the system proxy: this runs before Tor exists
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw MoatError.badResponse(status) }
        return data
    }
}

/// A tiny HTTPS/1.1 client on Network.framework that lets the TLS server name and the HTTP `Host`
/// header differ (domain fronting).
enum FrontedHTTPClient {
    struct Response: Sendable {
        let status: Int
        let headers: [String: String]
        let body: Data
    }

    enum ClientError: LocalizedError {
        case timeout
        case malformed
        var errorDescription: String? {
            switch self {
            case .timeout: return "timed out"
            case .malformed: return "malformed HTTP response"
            }
        }
    }

    static func post(front: String, host: String, path: String, body: Data, timeout: Duration) async throws -> Response {
        let head = "POST \(path) HTTP/1.1\r\nHost: \(host)\r\nUser-Agent: Mozilla/5.0\r\nContent-Type: application/vnd.api+json\r\nAccept: application/vnd.api+json\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n"
        var request = Data(head.utf8)
        request.append(body)
        let raw = try await exchange(front: front, request: request, timeout: timeout)
        return try parse(raw)
    }

    private static func exchange(front: String, request: Data, timeout: Duration) async throws -> Data {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            let queue = DispatchQueue(label: "app.veilvpn.fronted")
            let tls = NWProtocolTLS.Options()
            let tcp = NWProtocolTCP.Options()
            tcp.connectionTimeout = 15
            let connection = NWConnection(host: NWEndpoint.Host(front), port: 443, using: NWParameters(tls: tls, tcp: tcp))
            var buffer = Data()
            var finished = false
            let finish: (Result<Data, Error>) -> Void = { result in
                guard !finished else { return }
                finished = true
                connection.cancel()
                continuation.resume(with: result)
            }
            func receive() {
                connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { data, _, isComplete, error in
                    if let data { buffer.append(data) }
                    if let error {
                        if buffer.isEmpty { finish(.failure(error)) } else { finish(.success(buffer)) }
                        return
                    }
                    if isComplete || buffer.count > 4_000_000 {
                        finish(.success(buffer))
                        return
                    }
                    receive()
                }
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.send(content: request, completion: .contentProcessed { error in
                        if let error { finish(.failure(error)) } else { receive() }
                    })
                case .failed(let error), .waiting(let error):
                    finish(.failure(error))
                case .cancelled:
                    finish(.failure(ClientError.timeout))
                default:
                    break
                }
            }
            queue.asyncAfter(deadline: .now() + .seconds(Int(max(1, timeout.components.seconds)))) {
                finish(.failure(ClientError.timeout))
            }
            connection.start(queue: queue)
        }
    }

    static func parse(_ raw: Data) throws -> Response {
        guard let separator = raw.range(of: Data("\r\n\r\n".utf8)) else { throw ClientError.malformed }
        let headText = String(decoding: raw[raw.startIndex..<separator.lowerBound], as: UTF8.self)
        var lines = headText.components(separatedBy: "\r\n")
        let statusLine = lines.removeFirst()
        let parts = statusLine.split(separator: " ")
        guard parts.count >= 2, let status = Int(parts[1]) else { throw ClientError.malformed }
        var headers: [String: String] = [:]
        for line in lines {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            headers[key] = value
        }
        var body = Data(raw[separator.upperBound...])
        if headers["transfer-encoding"]?.lowercased().contains("chunked") == true {
            body = decodeChunked(body)
        }
        return Response(status: status, headers: headers, body: body)
    }

    static func decodeChunked(_ data: Data) -> Data {
        var result = Data()
        var cursor = data.startIndex
        while cursor < data.endIndex {
            guard let lineEnd = data[cursor...].range(of: Data("\r\n".utf8)) else { break }
            let sizeText = String(decoding: data[cursor..<lineEnd.lowerBound], as: UTF8.self)
            guard let size = Int(sizeText.split(separator: ";").first ?? "", radix: 16) else { break }
            if size == 0 { break }
            let chunkStart = lineEnd.upperBound
            let chunkEnd = min(data.endIndex, chunkStart + size)
            result.append(data[chunkStart..<chunkEnd])
            cursor = min(data.endIndex, chunkEnd + 2)
        }
        return result
    }
}
