import Foundation
import Network
import Observation

/// Client side of the padding loop: a connection through Tor to Veil's own private onion service
/// (which points back at `PaddingServer` on this Mac). Dummy frames travel out through the
/// Snowflake bridge to a rendezvous relay and back in, so both directions of the observed link
/// carry noise and no third party ever sees the traffic.
@MainActor
@Observable
final class PaddingLoop {
    enum Status: Equatable, Sendable {
        case off
        case preparing
        /// Tor is uploading the onion descriptor to the directory; can take a few minutes via Snowflake.
        case publishing
        case connecting(attempt: Int)
        case active
        case failed(String)

        var isActive: Bool { self == .active }
    }

    private(set) var status: Status = .off
    private(set) var level: PaddingLevel = .balanced
    private(set) var sentBytes: UInt64 = 0
    private(set) var receivedBytes: UInt64 = 0
    /// Padding bytes per second (both directions), smoothed.
    private(set) var rate: Double = 0
    /// Real (non-padding) bytes per second (both directions), smoothed.
    private(set) var realRate: Double = 0

    var onLog: (@MainActor (LogEntry) -> Void)?

    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var server: PaddingServer?
    @ObservationIgnored private var client: PaddingClient?
    @ObservationIgnored private var onionServiceID: String?
    @ObservationIgnored private weak var engine: (any TorEngine)?

    var overheadShare: Double {
        let total = rate + realRate
        return total > 0 ? rate / total : 0
    }

    func start(engine: any TorEngine, socksPort: UInt16, level: PaddingLevel) {
        task?.cancel()
        self.engine = engine
        self.level = level
        status = .preparing
        sentBytes = 0
        receivedBytes = 0
        rate = 0
        realRate = 0
        task = Task { [weak self] in
            guard let self else { return }
            do {
                try await run(engine: engine, socksPort: socksPort, level: level)
            } catch is CancellationError {
                // stop() handles the state
            } catch {
                status = .failed(error.localizedDescription)
                onLog?(.veil(.warn, "Traffic padding stopped: \(error.localizedDescription)"))
                client?.cancel()
                client = nil
                rate = 0
            }
        }
    }

    func stop() async {
        task?.cancel()
        task = nil
        client?.cancel()
        client = nil
        server?.stop()
        server = nil
        if let onionServiceID, let engine {
            await engine.removeOnionService(onionServiceID)
        }
        onionServiceID = nil
        status = .off
        rate = 0
        realRate = 0
    }

    private func run(engine: any TorEngine, socksPort: UInt16, level: PaddingLevel) async throws {
        let server = PaddingServer()
        let sinkPort = try await server.start()
        self.server = server

        let host: String
        let port: UInt16
        let proxyPort: UInt16?
        if engine.isSimulated {
            host = "127.0.0.1"
            port = sinkPort
            proxyPort = nil
            onLog?(.veil(.info, "Traffic padding (demo): looping dummy traffic locally"))
        } else {
            let serviceID = try await engine.createOnionService(targetPort: sinkPort)
            onionServiceID = serviceID
            host = "\(serviceID).onion"
            port = 80
            proxyPort = socksPort
            onLog?(.veil(.info, "Traffic padding: private onion service \(serviceID.prefix(12))… created; waiting for Tor to publish its descriptor"))
            status = .publishing
            let published = await engine.waitForOnionServicePublication(serviceID: serviceID, timeout: .seconds(300))
            try Task.checkCancellation()
            if published {
                onLog?(.veil(.info, "Traffic padding: descriptor published, connecting to the loop"))
                try await Task.sleep(for: .seconds(3)) // let the directory settle
            } else {
                onLog?(.veil(.warn, "Traffic padding: no descriptor upload reported within 5 minutes; trying to connect anyway"))
            }
        }

        var connected: PaddingClient?
        var lastFailure = ""
        for attempt in 1...30 {
            try Task.checkCancellation()
            status = .connecting(attempt: attempt)
            let candidate = PaddingClient(host: host, port: port, socksPort: proxyPort)
            do {
                try await candidate.connect(timeout: .seconds(90))
                connected = candidate
                break
            } catch {
                candidate.cancel()
                lastFailure = error.localizedDescription
                onLog?(.veil(attempt % 3 == 0 ? .info : .debug, "Traffic padding: loop attempt \(attempt) failed: \(lastFailure)"))
                try await Task.sleep(for: .seconds(attempt < 10 ? 8 : 15))
            }
        }
        guard let client = connected else {
            throw PaddingError.loopUnreachable(lastFailure)
        }
        self.client = client
        status = .active
        onLog?(.veil(.notice, "Traffic padding active (\(level.rawValue)) — dummy traffic is flowing through the tunnel"))

        var machine = PaddingMachine(level: level, now: Date.now.timeIntervalSinceReferenceDate)
        var previousCounters = try await engine.trafficCounters()
        var lastTick = Date.now
        while !Task.isCancelled {
            try await Task.sleep(for: .milliseconds(500))
            try Task.checkCancellation()
            if client.isClosed { throw PaddingError.loopClosed }

            let now = Date.now
            let elapsed = max(0.05, now.timeIntervalSince(lastTick))
            lastTick = now

            let counters = (try? await engine.trafficCounters()) ?? previousCounters
            let deltaRead = counters.read >= previousCounters.read ? Int(counters.read - previousCounters.read) : 0
            let deltaWritten = counters.written >= previousCounters.written ? Int(counters.written - previousCounters.written) : 0
            previousCounters = counters

            let padding = client.takeCounters()
            sentBytes += UInt64(padding.sent)
            receivedBytes += UInt64(padding.received)
            let realUp = max(0, deltaWritten - padding.sent)
            let realDown = max(0, deltaRead - padding.received)

            let paddingPerSecond = Double(padding.sent + padding.received) / elapsed
            let realPerSecond = Double(realUp + realDown) / elapsed
            rate = rate * 0.5 + paddingPerSecond * 0.5
            realRate = realRate * 0.5 + realPerSecond * 0.5

            let frames = machine.tick(
                now: now.timeIntervalSinceReferenceDate, elapsed: elapsed,
                realUpstream: realUp, realDownstream: realDown,
                paddingUpstream: padding.sent, paddingDownstream: padding.received
            )
            for frame in frames {
                client.send(frame)
            }
        }
    }
}

/// The Tor-side end of the loop: a plain TCP connection to Tor's SOCKS port with a hand-made
/// SOCKS5 CONNECT to the onion address (or a direct connection in demo mode).
final class PaddingClient: @unchecked Sendable {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "app.veilvpn.padding.client")
    private let lock = NSLock()
    private let targetHost: String
    private let targetPort: UInt16
    private let usesSOCKS: Bool
    private var sent = 0
    private var received = 0
    private var closed = false

    var isClosed: Bool {
        lock.lock()
        defer { lock.unlock() }
        return closed
    }

    init(host: String, port: UInt16, socksPort: UInt16?) {
        targetHost = host
        targetPort = port
        usesSOCKS = socksPort != nil
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = 30
        let parameters = NWParameters(tls: nil, tcp: tcp)
        if let socksPort {
            connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050, using: parameters)
        } else {
            connection = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port) ?? 80, using: parameters)
        }
    }

    func connect(timeout: Duration) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            queue.async {
                var resumed = false
                let finish: (Error?) -> Void = { error in
                    guard !resumed else { return }
                    resumed = true
                    if let error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume()
                    }
                }
                let seconds = Double(timeout.components.seconds) + Double(timeout.components.attoseconds) / 1e18
                self.queue.asyncAfter(deadline: .now() + seconds) { [weak self] in
                    guard !resumed else { return }
                    self?.markClosed()
                    self?.connection.cancel()
                    finish(PaddingError.timeout)
                }
                self.connection.stateUpdateHandler = { [weak self] state in
                    guard let self else { return }
                    switch state {
                    case .ready:
                        guard !resumed else { return }
                        if self.usesSOCKS {
                            SOCKS5.connect(on: self.connection, host: self.targetHost, port: self.targetPort) { error in
                                if let error {
                                    self.markClosed()
                                    finish(error)
                                } else {
                                    self.receiveLoop()
                                    finish(nil)
                                }
                            }
                        } else {
                            self.receiveLoop()
                            finish(nil)
                        }
                    case .waiting(let error), .failed(let error):
                        self.markClosed()
                        finish(error)
                    case .cancelled:
                        self.markClosed()
                        finish(PaddingError.cancelled)
                    default:
                        break
                    }
                }
                self.connection.start(queue: self.queue)
            }
        }
    }

    func send(_ frame: PaddingFrame) {
        var payload = PaddingServer.header(for: frame)
        payload.append(PaddingServer.noiseBytes(frame.upstreamBytes))
        let count = payload.count
        connection.send(content: payload, completion: .contentProcessed { [weak self] error in
            guard let self else { return }
            if error == nil {
                self.lock.lock()
                self.sent += count
                self.lock.unlock()
            } else {
                self.markClosed()
            }
        })
    }

    /// Bytes exchanged since the previous call.
    func takeCounters() -> (sent: Int, received: Int) {
        lock.lock()
        defer { lock.unlock() }
        let result = (sent, received)
        sent = 0
        received = 0
        return result
    }

    func cancel() {
        markClosed()
        connection.cancel()
    }

    private func markClosed() {
        lock.lock()
        closed = true
        lock.unlock()
    }

    private func receiveLoop() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.lock.lock()
                self.received += data.count
                self.lock.unlock()
            }
            if isComplete || error != nil {
                self.markClosed()
                return
            }
            self.receiveLoop()
        }
    }
}
