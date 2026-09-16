import Foundation
import Network
import Observation

/// Keeps the video circuit warm between the player's bursts. A player fetches a chunk, waits
/// while the buffer drains, then fetches again; on every hop of the circuit the TCP connection
/// idles in between, and Linux relays reset their congestion window after an idle period longer
/// than one round trip (`tcp_slow_start_after_idle`). Each burst then ramps from a small window
/// again — the sawtooth on the throughput graph, and the stall when a burst ramps too slowly to
/// beat the buffer. A steady trickle on the same circuit, small requests a few times a second,
/// keeps every hop's window open, so the next burst starts at full speed. The trickle rides the
/// YouTube lane's own credentials, which is what puts it on the same circuit; it goes to a
/// YouTube host, so it also leaves through the same exit.
@MainActor
@Observable
final class VideoWarmer {
    private(set) var isRunning = false
    /// Smoothed bytes a second the trickle actually achieved.
    private(set) var bytesPerSecond: Double = 0
    private(set) var requests = 0
    private(set) var failures = 0

    @ObservationIgnored var onLog: (@MainActor (LogEntry) -> Void)?
    @ObservationIgnored private var task: Task<Void, Never>?

    /// Thumbnails of one long-lived public video: a few tens of kilobytes to a few hundred, on the
    /// YouTube image CDN, cache-neutral and meaningless. The asset grows with the rate so the
    /// request interval stays a few hundred milliseconds — under one round trip — either way.
    nonisolated static func assetPath(forKilobytes rate: Int) -> String {
        switch rate {
        case ..<200: "/vi/dQw4w9WgXcQ/hqdefault.jpg"
        case ..<400: "/vi/dQw4w9WgXcQ/sddefault.jpg"
        default: "/vi/dQw4w9WgXcQ/maxresdefault.jpg"
        }
    }

    static let host = "i.ytimg.com"
    static let rates = [64, 128, 256, 512]

    /// How long to wait after a response of `bytes` so the average lands on `kilobytesPerSecond`,
    /// never more than a second (a longer gap is exactly the idle this exists to prevent) and
    /// never under 100 ms.
    nonisolated static func pause(afterBytes bytes: Int, took seconds: TimeInterval, kilobytesPerSecond rate: Int) -> TimeInterval {
        let budget = Double(bytes) / Double(max(1, rate) * 1024)
        return min(1.0, max(0.1, budget - seconds))
    }

    func start(poolPort: UInt16, credentials: SOCKS5.Credentials, kilobytesPerSecond rate: Int) {
        stop()
        isRunning = true
        bytesPerSecond = 0
        requests = 0
        failures = 0
        let configuration = URLSessionConfiguration.ephemeral
        var proxy = ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1",
                                                                port: NWEndpoint.Port(rawValue: poolPort) ?? 9250))
        proxy.applyCredential(username: credentials.username, password: credentials.password)
        configuration.proxyConfigurations = [proxy]
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.httpMaximumConnectionsPerHost = 1
        configuration.timeoutIntervalForRequest = 12
        configuration.httpAdditionalHeaders = ["User-Agent": "Veil"]
        let session = URLSession(configuration: configuration)
        let url = URL(string: "https://\(Self.host)\(Self.assetPath(forKilobytes: rate))")!
        onLog?(.veil(.info, "Video circuit warmer: \(rate) KB/s on the YouTube lane"))
        task = Task { [weak self] in
            defer { session.invalidateAndCancel() }
            var streak = 0
            var windowBytes = 0
            var windowStart = Date.now
            while !Task.isCancelled {
                let started = Date.now
                do {
                    var request = URLRequest(url: url)
                    request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
                    let (data, response) = try await session.data(for: request)
                    guard let http = response as? HTTPURLResponse, (200..<400).contains(http.statusCode) else {
                        throw URLError(.badServerResponse)
                    }
                    streak = 0
                    windowBytes += data.count
                    let elapsed = Date.now.timeIntervalSince(started)
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        requests += 1
                        let span = Date.now.timeIntervalSince(windowStart)
                        if span >= 2 {
                            bytesPerSecond = Double(windowBytes) / span
                            windowBytes = 0
                            windowStart = .now
                        }
                    }
                    let pause = Self.pause(afterBytes: data.count, took: elapsed, kilobytesPerSecond: rate)
                    try await Task.sleep(for: .milliseconds(Int(pause * 1000)))
                } catch is CancellationError {
                    return
                } catch {
                    streak += 1
                    await MainActor.run { [weak self] in self?.failures += 1 }
                    if streak >= 4 {
                        await MainActor.run { [weak self] in
                            guard let self else { return }
                            onLog?(.veil(.warn, "Video circuit warmer stopped: four requests in a row failed (\(error.localizedDescription))"))
                            isRunning = false
                        }
                        return
                    }
                    do { try await Task.sleep(for: .seconds(2)) } catch { return }
                }
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        if isRunning {
            isRunning = false
            onLog?(.veil(.info, "Video circuit warmer off"))
        }
        bytesPerSecond = 0
    }
}
