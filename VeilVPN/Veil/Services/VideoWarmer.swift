import Foundation
import Network
import Observation

/// Keeps the tunnel in tone. A player fetches a chunk, waits while the buffer drains, then
/// fetches again; on every hop the TCP connection idles in between, and Linux relays reset their
/// congestion window after an idle period longer than one round trip (`tcp_slow_start_after_idle`).
/// A Snowflake proxy drops a link that goes quiet. Each burst then ramps from a small window
/// again — the sawtooth on the throughput graph, and the stall when a burst ramps too slowly to
/// beat the buffer. A steady stream — small requests a few times a second, at a rate the user
/// sets — keeps every hop's window open and the first link busy, so the next burst starts at
/// full speed. On the YouTube lane's own credentials it rides the video's circuit and exit; on
/// the plain SOCKS port it rides the main route, which shares the guard or bridge link with
/// everything.
@MainActor
@Observable
final class VideoWarmer {
    private(set) var isRunning = false
    /// Smoothed bytes a second the stream actually achieved.
    private(set) var bytesPerSecond: Double = 0
    private(set) var requests = 0
    private(set) var failures = 0
    /// Several requests in a row have failed: the path under the stream is not carrying it.
    private(set) var stalled = false
    private(set) var targetKilobytes = 0

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
    static let rates = [128, 256, 512, 1024]

    /// How long to wait after a response of `bytes` so the average lands on `kilobytesPerSecond`,
    /// never more than a second (a longer gap is exactly the idle this exists to prevent) and
    /// never under 50 ms.
    nonisolated static func pause(afterBytes bytes: Int, took seconds: TimeInterval, kilobytesPerSecond rate: Int) -> TimeInterval {
        let budget = Double(bytes) / Double(max(1, rate) * 1024)
        return min(1.0, max(0.05, budget - seconds))
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
        let configuration = URLSessionConfiguration.ephemeral
        var proxy = ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1",
                                                                port: NWEndpoint.Port(rawValue: socksPort) ?? 9050))
        if let credentials {
            proxy.applyCredential(username: credentials.username, password: credentials.password)
        }
        configuration.proxyConfigurations = [proxy]
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.httpMaximumConnectionsPerHost = 1
        configuration.timeoutIntervalForRequest = 12
        configuration.httpAdditionalHeaders = ["User-Agent": "Veil"]
        let session = URLSession(configuration: configuration)
        let url = URL(string: "https://\(Self.host)\(Self.assetPath(forKilobytes: rate))")!
        onLog?(.veil(.info, "Tunnel tonus: \(rate) KB/s \(credentials == nil ? "on the main route" : "on the YouTube lane")"))
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
                        stalled = false
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
                    let firstStall = streak == 4
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        failures += 1
                        if firstStall {
                            stalled = true
                            bytesPerSecond = 0
                            onLog?(.veil(.warn, "Tunnel tonus: four requests in a row failed (\(error.localizedDescription)); the path is not carrying it — kept trying"))
                        }
                    }
                    // Never gives up while wanted: the path coming back is exactly the moment
                    // the stream must be there.
                    do { try await Task.sleep(for: .seconds(min(10, 2 * Double(streak)))) } catch { return }
                }
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        if isRunning {
            isRunning = false
            onLog?(.veil(.info, "Tunnel tonus off"))
        }
        bytesPerSecond = 0
        stalled = false
        targetKilobytes = 0
    }
}
