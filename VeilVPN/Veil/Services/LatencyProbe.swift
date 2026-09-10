import Foundation
import Network
import Observation

/// One timed round trip through the Tor route.
struct LatencySample: Identifiable, Equatable, Sendable {
    let id = UUID()
    let date: Date
    let seconds: TimeInterval
    let target: String
}

/// What a window of samples says about the route. The headline number is the median, so a single
/// slow moment cannot become "the" latency; how bad the slow moments are is reported separately
/// as jitter instead of being averaged into the figure.
struct LatencySummary: Equatable, Sendable {
    var median: TimeInterval
    var best: TimeInterval
    /// 90th percentile minus the median: how much worse the occasional bad round trip is.
    var jitter: TimeInterval
    var samples: Int
    var failures: Int

    var isStable: Bool { jitter < median * 0.5 || jitter < 0.15 }

    static func percentile(_ sorted: [TimeInterval], _ fraction: Double) -> TimeInterval {
        guard !sorted.isEmpty else { return 0 }
        let position = fraction * Double(sorted.count - 1)
        let lower = Int(position.rounded(.down))
        let upper = min(sorted.count - 1, lower + 1)
        let weight = position - Double(lower)
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }

    /// Nil when nothing was measured; failures alone never produce a summary.
    static func make(from samples: [LatencySample], failures: Int) -> LatencySummary? {
        guard !samples.isEmpty else { return nil }
        let sorted = samples.map(\.seconds).sorted()
        let median = percentile(sorted, 0.5)
        return LatencySummary(
            median: median,
            best: sorted[0],
            jitter: max(0, percentile(sorted, 0.9) - median),
            samples: samples.count,
            failures: failures
        )
    }
}

/// Measures the latency of the route itself: how long a SOCKS5 CONNECT through Tor to a
/// well-connected address takes. That covers Mac → bridge → guard → middle → exit → destination
/// and nothing else — no TLS handshake, no page download, no slow web server — so the number
/// describes the route rather than whichever site happened to be asked.
enum LatencyProbe {
    struct Target: Equatable, Sendable {
        let name: String
        let host: String
        let port: UInt16
    }

    /// Anycast resolvers: every exit has one close by, they answer TCP on 443 from anywhere, and
    /// rotating between them keeps one busy host from colouring the result.
    static let targets: [Target] = [
        Target(name: "1.1.1.1", host: "1.1.1.1", port: 443),
        Target(name: "8.8.8.8", host: "8.8.8.8", port: 443),
        Target(name: "9.9.9.9", host: "9.9.9.9", port: 443),
    ]

    static func target(at index: Int) -> Target {
        targets[((index % targets.count) + targets.count) % targets.count]
    }

    /// One CONNECT, timed from the SOCKS greeting to Tor's reply. Nil when it fails or times out.
    static func sample(socksPort: UInt16, target: Target, timeout: Duration = .seconds(10)) async -> TimeInterval? {
        let queue = DispatchQueue(label: "app.veilvpn.latency")
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = Int(max(1, timeout.components.seconds))
        tcp.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcp)
        parameters.preferNoProxies = true // Tor's SOCKS port is local; never loop back through Veil
        let connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050, using: parameters)
        return await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<TimeInterval?, Never>) in
                var resumed = false
                let finish: (TimeInterval?) -> Void = { result in
                    guard !resumed else { return }
                    resumed = true
                    connection.cancel()
                    continuation.resume(returning: result)
                }
                let seconds = Double(timeout.components.seconds) + Double(timeout.components.attoseconds) / 1e18
                queue.asyncAfter(deadline: .now() + seconds) {
                    finish(nil)
                }
                connection.stateUpdateHandler = { state in
                    switch state {
                    case .ready:
                        guard !resumed else { return }
                        let started = ContinuousClock.now
                        SOCKS5.connect(on: connection, host: target.host, port: target.port) { error in
                            guard error == nil else {
                                finish(nil)
                                return
                            }
                            let elapsed = ContinuousClock.now - started
                            finish(Double(elapsed.components.seconds) + Double(elapsed.components.attoseconds) / 1e18)
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

/// Keeps a rolling window of route latency measurements while connected. Each round takes a few
/// samples against one target, so they share a circuit and can be compared; the target rotates
/// between rounds so the window describes the route rather than one destination.
@MainActor
@Observable
final class LatencyMonitor {
    private(set) var summary: LatencySummary?
    private(set) var recent: [LatencySample] = []
    private(set) var isMeasuring = false
    /// Set while every sample in a round failed — the route is not passing traffic.
    private(set) var lastRoundFailed = false

    @ObservationIgnored var onLog: (@MainActor (LogEntry) -> Void)?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var socksPort: UInt16?
    @ObservationIgnored private var round = 0
    @ObservationIgnored private var failures = 0

    /// How many samples the headline median is taken over.
    static let windowSize = 12
    /// Samples older than this never count, so the figure follows the route rather than history.
    static let windowAge: TimeInterval = 6 * 60

    var isRunning: Bool { task != nil }

    func start(socksPort: UInt16) {
        stop()
        self.socksPort = socksPort
        reset()
        task = Task { [weak self] in
            // The first round runs at once so the figure appears right after connecting.
            while !Task.isCancelled {
                guard let self else { return }
                await measureRound(samples: 3)
                do { try await Task.sleep(for: .seconds(25)) } catch { return }
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        socksPort = nil
        isMeasuring = false
    }

    /// Clears the window: after a route change the old samples describe a route that no longer exists.
    func reset() {
        recent = []
        summary = nil
        failures = 0
        lastRoundFailed = false
    }

    /// An extra round now (after a route change, on demand, or when checking a tunnel).
    func measureNow() {
        guard let socksPort, !isMeasuring else { return }
        Task { [weak self] in
            await self?.measureRound(samples: 3)
        }
    }

    private func measureRound(samples count: Int) async {
        guard let socksPort else { return }
        isMeasuring = true
        defer { isMeasuring = false }
        let target = LatencyProbe.target(at: round)
        round += 1
        var measured: [LatencySample] = []
        for index in 0..<count {
            if Task.isCancelled { return }
            if let seconds = await LatencyProbe.sample(socksPort: socksPort, target: target, timeout: .seconds(10)) {
                measured.append(LatencySample(date: .now, seconds: seconds, target: target.name))
            } else {
                failures += 1
            }
            if index < count - 1 {
                do { try await Task.sleep(for: .milliseconds(250)) } catch { return }
            }
        }
        lastRoundFailed = measured.isEmpty
        guard !measured.isEmpty else {
            onLog?(.veil(.debug, "Latency probe: no answer from \(target.name) through Tor"))
            recompute()
            return
        }
        recent.append(contentsOf: measured)
        if recent.count > Self.windowSize * 4 {
            recent.removeFirst(recent.count - Self.windowSize * 4)
        }
        recompute()
    }

    private func recompute() {
        let cutoff = Date.now.addingTimeInterval(-Self.windowAge)
        let window = Array(recent.filter { $0.date >= cutoff }.suffix(Self.windowSize))
        summary = LatencySummary.make(from: window, failures: failures)
    }
}
