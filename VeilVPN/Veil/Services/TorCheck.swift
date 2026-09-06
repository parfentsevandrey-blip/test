import Foundation
import Network

/// Asks check.torproject.org, through Tor's SOCKS port, whether we really exit via Tor.
enum TorCheck {
    enum CheckError: LocalizedError {
        case badResponse
        var errorDescription: String? { String(localized: "check.torproject.org returned an unexpected response.") }
    }

    static func run(socksPort: UInt16) async throws -> TorCheckResult {
        let configuration = URLSessionConfiguration.ephemeral
        let proxy = ProxyConfiguration(socksv5Proxy: .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: socksPort) ?? 9050))
        configuration.proxyConfigurations = [proxy]
        configuration.timeoutIntervalForRequest = 45
        configuration.timeoutIntervalForResource = 60
        configuration.httpAdditionalHeaders = ["User-Agent": "Veil/1.0 (macOS)"]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }

        let url = URL(string: "https://check.torproject.org/api/ip")!
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CheckError.badResponse
        }
        return try JSONDecoder().decode(TorCheckResult.self, from: data)
    }
}
