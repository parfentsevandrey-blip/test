import Foundation
import Network

/// How many megabits a second a path through Tor actually carries, measured by pulling a public
/// file through it for a few seconds. Consensus weight is what the bandwidth authorities saw from
/// where they sit; this is what this Mac gets, right now, through this guard and this exit — the
/// only number that says which video quality the path can sustain.
enum ThroughputProbe {
    struct Sample: Equatable, Sendable {
        var bytes: Int
        var seconds: TimeInterval
        var megabitsPerSecond: Double { seconds > 0 ? Double(bytes) * 8 / seconds / 1_000_000 : 0 }
    }

    struct Target: Equatable, Sendable {
        let host: String
        let path: String
    }

    /// Large, public, served by a CDN that does not challenge Tor exits, and plain HTTP on
    /// purpose: the bytes are public and only their rate matters, and a plain request travels
    /// through Tor's SOCKS port with no TLS stack in the way. The host name goes to Tor
    /// unresolved, which is what lets a `MapAddress` steer the measurement through one exit.
    static let targets: [Target] = [
        Target(host: "deb.debian.org", path: "/debian/ls-lR.gz"),
        Target(host: "ftp.debian.org", path: "/debian/ls-lR.gz"),
    ]

    static var measurementHosts: [String] { targets.map(\.host) }

    /// The YouTube quality a rate sustains: YouTube's own recommendations with some headroom for
    /// the bursts a player fetches in.
    static func quality(forMegabits megabits: Double) -> String {
        switch megabits {
        case 50...: "8K"
        case 22...: "4K"
        case 10...: "1440p"
        case 5...: "1080p"
        case 2.5...: "720p"
        default: "480p"
        }
    }

    /// The status line and where the body starts, once the whole header has arrived.
    static func parseHead(_ data: Data) -> (status: Int, bodyStart: Int)? {
        guard let range = data.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let head = String(decoding: data[data.startIndex..<range.lowerBound], as: UTF8.self)
        let parts = head.split(separator: " ", maxSplits: 2)
        guard parts.count >= 2, parts[0].hasPrefix("HTTP/"), let status = Int(parts[1]) else { return nil }
        return (status, range.upperBound - data.startIndex)
    }

    /// Pulls `target` through Tor's SOCKS port for `duration` after the first body byte, or until
    /// `byteCap`, whichever comes first. Nil when the stream never opened or never delivered.
    static func measure(socksPort: UInt16, target: Target, duration: Duration = .seconds(4),
                        byteCap: Int = 4_000_000, credentials: SOCKS5.Credentials? = nil) async -> Sample? {
        let queue = DispatchQueue(label: "app.veilvpn.throughput")
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = 10
        let parameters = NWParameters(tls: nil, tcp: tcp)
        parameters.preferNoProxies = true // Tor's SOCKS port is local; never loop back through Veil
        let connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050, using: parameters)
        let window = Double(duration.components.seconds) + Double(duration.components.attoseconds) / 1e18
        return await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<Sample?, Never>) in
                var resumed = false
                var buffer = Data()
                var inBody = false
                var bodyBytes = 0
                var firstBodyAt: DispatchTime?
                let finish: (Sample?) -> Void = { result in
                    guard !resumed else { return }
                    resumed = true
                    connection.cancel()
                    continuation.resume(returning: result)
                }
                func settle() {
                    guard let firstBodyAt else {
                        finish(nil)
                        return
                    }
                    let seconds = Double(DispatchTime.now().uptimeNanoseconds &- firstBodyAt.uptimeNanoseconds) / 1e9
                    finish(bodyBytes > 0 && seconds > 0.2 ? Sample(bytes: bodyBytes, seconds: seconds) : nil)
                }
                // The ceiling over everything: SOCKS, the connect at the exit, the first byte and
                // the window itself.
                queue.asyncAfter(deadline: .now() + window + 15) { settle() }
                func receive() {
                    connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { data, _, isComplete, error in
                        if let data, !data.isEmpty {
                            if inBody {
                                bodyBytes += data.count
                            } else {
                                buffer.append(data)
                                if let head = Self.parseHead(buffer) {
                                    guard (200..<300).contains(head.status) else {
                                        finish(nil)
                                        return
                                    }
                                    inBody = true
                                    bodyBytes = buffer.count - head.bodyStart
                                    buffer = Data()
                                    firstBodyAt = .now()
                                    queue.asyncAfter(deadline: .now() + window) { settle() }
                                } else if buffer.count > 65536 {
                                    finish(nil)
                                    return
                                }
                            }
                            if bodyBytes >= byteCap {
                                settle()
                                return
                            }
                        }
                        if isComplete || error != nil {
                            settle()
                            return
                        }
                        receive()
                    }
                }
                connection.stateUpdateHandler = { state in
                    switch state {
                    case .ready:
                        SOCKS5.connect(on: connection, host: target.host, port: 80, credentials: credentials) { error, _ in
                            guard error == nil else {
                                finish(nil)
                                return
                            }
                            let request = "GET \(target.path) HTTP/1.1\r\nHost: \(target.host)\r\nUser-Agent: Veil\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
                            connection.send(content: Data(request.utf8), completion: .contentProcessed { error in
                                if error != nil {
                                    finish(nil)
                                } else {
                                    receive()
                                }
                            })
                        }
                    case .failed, .cancelled:
                        finish(nil)
                    default:
                        break
                    }
                }
                connection.start(queue: queue)
            }
        } onCancel: {
            connection.cancel()
        }
    }
}
