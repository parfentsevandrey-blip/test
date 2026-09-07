import Foundation
import Network

/// Measures how fast YouTube answers through Veil's own proxy, for the technique auto-selection
/// and the "Test YouTube" button.
enum YouTubeBenchmark {
    struct Measurement: Equatable, Sendable {
        let milliseconds: Int
        let bytes: Int
        let kilobytesPerSecond: Double
        let status: Int
    }

    struct StrategyResult: Identifiable, Equatable, Sendable {
        let strategy: DPIStrategy
        let measurement: Measurement?
        let error: String?
        var id: String { strategy.rawValue }
    }

    static let homepage = URL(string: "https://www.youtube.com/")!
    static let probe = URL(string: "https://www.youtube.com/generate_204")!

    static func measure(url: URL, httpPort: UInt16, timeout: TimeInterval = 20) async throws -> Measurement {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.proxyConfigurations = [
            ProxyConfiguration(httpCONNECTProxy: .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: httpPort) ?? 8118)),
        ]
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout + 10
        configuration.httpAdditionalHeaders = ["User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 26_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let started = Date.now
        let (data, response) = try await session.data(from: url)
        let elapsed = Date.now.timeIntervalSince(started)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let kbps = elapsed > 0 ? Double(data.count) / 1024 / elapsed : 0
        return Measurement(milliseconds: Int((elapsed * 1000).rounded()), bytes: data.count, kilobytesPerSecond: kbps, status: status)
    }
}
