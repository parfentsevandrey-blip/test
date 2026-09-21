import Foundation
import Network
import Observation

/// Keeps the tunnel in tone: one continuous stream, read at exactly the rate the user set, the
/// whole time it is wanted. A player fetches a chunk, waits while the buffer drains, then
/// fetches again; on every hop the TCP connection idles in between, Linux relays reset their
/// congestion window after an idle longer than one round trip, and a Snowflake proxy drops a link
/// that goes quiet. Each burst then ramps from a small window — the sawtooth on the throughput
/// graph, and the stall when a burst ramps too slowly to beat the buffer.
///
/// This pulls a large public file through the tunnel and reads it in fixed slices, ten times a
/// second: the receive window does the pacing, so the sender delivers a steady stream at the set
/// rate with no request/response gaps at all. When the file ends the next one starts at once;
/// when a source fails the next source is tried; it never gives up while wanted. With credentials
/// of its own it rides a circuit of its own through the same guard — the link it is there to
/// warm — and competes with nothing else on any circuit.
@MainActor
@Observable
final class VideoWarmer {
    private(set) var isRunning = false
    /// Smoothed bytes a second the stream actually achieved.
    private(set) var bytesPerSecond: Double = 0
    /// Streams opened so far.
    private(set) var requests = 0
    private(set) var failures = 0
    /// Several attempts in a row have failed: the path under the stream is not carrying it.
    private(set) var stalled = false
    private(set) var targetKilobytes = 0

    @ObservationIgnored var onLog: (@MainActor (LogEntry) -> Void)?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var connection: NWConnection?

    struct Source: Equatable, Sendable {
        let host: String
        let path: String
    }

    /// Large public files on hosts that do not turn Tor exits away, biggest first so a stream
    /// runs for the better part of an hour before it has to be reopened. All over HTTPS: the
    /// bytes are public and meaningless, and the host name goes to Tor unresolved.
    static let sources: [Source] = [
        Source(host: "proof.ovh.net", path: "/files/1Gb.dat"),
        Source(host: "speedtest.tele2.net", path: "/1GB.zip"),
        Source(host: "speed.cloudflare.com", path: "/__down?bytes=1000000000"),
        Source(host: "deb.debian.org", path: "/debian/ls-lR.gz"),
    ]

    static let rates = [128, 256, 512, 1024]
    /// The floor the tonus is never taken below: enough to keep a congestion window from
    /// collapsing, small enough to be invisible next to a video.
    static let minimumKilobytes = 32
    /// Ten slices a second: far under one round trip through Tor, so the pipe never drains.
    static let tick: TimeInterval = 0.1

    /// The slice read each tick to land on `rate`: the receive window then holds the sender at it.
    nonisolated static func slice(forKilobytes rate: Int, tick: TimeInterval = VideoWarmer.tick) -> Int {
        max(4096, Int(Double(max(1, rate) * 1024) * tick))
    }

    /// `Content-Length`, when the header carries one.
    nonisolated static func contentLength(in head: String) -> Int? {
        for line in head.split(separator: "\r\n") {
            let parts = line.split(separator: ":", maxSplits: 1)
            guard parts.count == 2, parts[0].trimmingCharacters(in: .whitespaces).lowercased() == "content-length" else { continue }
            return Int(parts[1].trimmingCharacters(in: .whitespaces))
        }
        return nil
    }

    /// `credentials` puts the stream on that lane's circuit; nil rides the plain port's.
    func start(socksPort: UInt16, credentials: SOCKS5.Credentials?, kilobytesPerSecond rate: Int) {
        stop()
        isRunning = true
        stalled = false
        bytesPerSecond = 0
        requests = 0
        failures = 0
        targetKilobytes = rate
        onLog?(.veil(.info, "Tunnel tonus: \(rate) KB/s \(credentials == nil ? "on the main route" : "on a circuit of its own"), continuous"))
        task = Task { [weak self] in
            await self?.run(socksPort: socksPort, credentials: credentials, rate: rate)
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        connection?.cancel()
        connection = nil
        if isRunning {
            isRunning = false
            onLog?(.veil(.info, "Tunnel tonus off"))
        }
        bytesPerSecond = 0
        stalled = false
        targetKilobytes = 0
    }

    private enum Outcome { case finished, failed, cancelled }

    private func run(socksPort: UInt16, credentials: SOCKS5.Credentials?, rate: Int) async {
        var index = 0
        var streak = 0
        while !Task.isCancelled {
            let source = Self.sources[index % Self.sources.count]
            switch await stream(source, socksPort: socksPort, credentials: credentials, rate: rate) {
            case .cancelled:
                return
            case .finished:
                // The file ran out: the same source again, at once. No idle in between.
                streak = 0
                stalled = false
            case .failed:
                streak += 1
                failures += 1
                index += 1
                if streak == 4 {
                    stalled = true
                    bytesPerSecond = 0
                    onLog?(.veil(.warn, "Tunnel tonus: four streams in a row failed; the path is not carrying it — kept trying"))
                }
                // Never gives up while wanted: the path coming back is exactly the moment the
                // stream must be there.
                do { try await Task.sleep(for: .seconds(min(10, 2 * Double(streak)))) } catch { return }
            }
        }
    }

    private func stream(_ source: Source, socksPort: UInt16, credentials: SOCKS5.Credentials?, rate: Int) async -> Outcome {
        let queue = DispatchQueue(label: "app.veilvpn.tonus")
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = 15
        let parameters = NWParameters(tls: NWProtocolTLS.Options(), tcp: tcp)
        var proxy = ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1",
                                                                port: NWEndpoint.Port(rawValue: socksPort) ?? 9050))
        if let credentials {
            proxy.applyCredential(username: credentials.username, password: credentials.password)
        }
        // Proxies live on the privacy context in Network.framework, not on the parameters.
        let privacy = NWParameters.PrivacyContext(description: "app.veilvpn.tonus")
        privacy.proxyConfigurations = [proxy]
        parameters.setPrivacyContext(privacy)
        let connection = NWConnection(host: NWEndpoint.Host(source.host), port: 443, using: parameters)
        self.connection = connection
        defer {
            connection.cancel()
            if self.connection === connection { self.connection = nil }
        }
        guard await Self.open(connection, on: queue, timeout: 20) else { return Task.isCancelled ? .cancelled : .failed }
        let request = "GET \(source.path) HTTP/1.1\r\nHost: \(source.host)\r\nUser-Agent: Veil\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
        guard await Self.send(connection, Data(request.utf8)) else { return Task.isCancelled ? .cancelled : .failed }

        // The header, then the body in paced slices.
        var buffer = Data()
        var head: (status: Int, bodyStart: Int)?
        while head == nil {
            if Task.isCancelled { return .cancelled }
            guard let chunk = await Self.receive(connection, minimum: 1, maximum: 16384, on: queue, timeout: 20),
                  let data = chunk.data else { return .failed }
            buffer.append(data)
            head = ThroughputProbe.parseHead(buffer)
            if head == nil, chunk.complete || buffer.count > 65536 { return .failed }
        }
        guard let head, (200..<300).contains(head.status) else { return .failed }
        let length = Self.contentLength(in: String(decoding: buffer.prefix(head.bodyStart), as: UTF8.self))
        var received = buffer.count - head.bodyStart
        requests += 1
        stalled = false

        let slice = Self.slice(forKilobytes: rate)
        var windowBytes = received
        var windowStart = Date.now
        while true {
            if Task.isCancelled { return .cancelled }
            if let length, received >= length { return .finished }
            let tickStart = Date.now
            guard let chunk = await Self.receive(connection, minimum: slice, maximum: slice, on: queue, timeout: 15) else {
                return .failed
            }
            if let data = chunk.data {
                received += data.count
                windowBytes += data.count
            }
            let span = Date.now.timeIntervalSince(windowStart)
            if span >= 2 {
                bytesPerSecond = Double(windowBytes) / span
                windowBytes = 0
                windowStart = .now
            }
            if chunk.complete { return received > 0 ? .finished : .failed }
            let elapsed = Date.now.timeIntervalSince(tickStart)
            if elapsed < Self.tick {
                do { try await Task.sleep(for: .milliseconds(Int((Self.tick - elapsed) * 1000))) } catch { return .cancelled }
            }
        }
    }

    // MARK: Network.framework, awaited

    private nonisolated static func open(_ connection: NWConnection, on queue: DispatchQueue, timeout: TimeInterval) async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            var resumed = false
            let finish: (Bool) -> Void = { result in
                guard !resumed else { return }
                resumed = true
                continuation.resume(returning: result)
            }
            queue.asyncAfter(deadline: .now() + timeout) { finish(false) }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: finish(true)
                case .failed, .cancelled, .waiting: finish(false)
                default: break
                }
            }
            connection.start(queue: queue)
        }
    }

    private nonisolated static func send(_ connection: NWConnection, _ data: Data) async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            connection.send(content: data, completion: .contentProcessed { error in
                continuation.resume(returning: error == nil)
            })
        }
    }

    struct Chunk: Sendable {
        let data: Data?
        let complete: Bool
    }

    /// Nil on error or timeout; otherwise the bytes (nil at end of stream) and whether that was
    /// the end.
    private nonisolated static func receive(_ connection: NWConnection, minimum: Int, maximum: Int,
                                            on queue: DispatchQueue, timeout: TimeInterval) async -> Chunk? {
        await withCheckedContinuation { (continuation: CheckedContinuation<Chunk?, Never>) in
            var resumed = false
            let finish: (Chunk?) -> Void = { result in
                guard !resumed else { return }
                resumed = true
                continuation.resume(returning: result)
            }
            queue.asyncAfter(deadline: .now() + timeout) { finish(nil) }
            connection.receive(minimumIncompleteLength: minimum, maximumLength: maximum) { data, _, isComplete, error in
                if error != nil {
                    finish(nil)
                } else {
                    finish(Chunk(data: data, complete: isComplete))
                }
            }
        }
    }
}
