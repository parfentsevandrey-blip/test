import Foundation
import Network

/// Answers "does the Internet actually work right now?". macOS happily reports a satisfied network
/// path after a long sleep, or after another VPN has disconnected, while nothing gets through until
/// Wi-Fi is switched off and on. A few TCP handshakes to well-known addresses plus one name lookup
/// settle the question in well under a second on a healthy network, and in `timeout` on a dead one.
enum ConnectivityProbe {
    struct Report: Equatable, Sendable {
        /// Endpoints reached by IP address, so DNS plays no part.
        var reachedByAddress: Int
        var addressTargets: Int
        /// At least one host name resolved and answered.
        var nameResolution: Bool
        var elapsed: TimeInterval
        var date: Date

        var internetReachable: Bool { reachedByAddress > 0 }
        /// Good enough to start Tor: an address answered and DNS works.
        var isUsable: Bool { internetReachable && nameResolution }

        var summary: String {
            let dns = nameResolution ? "DNS ok" : "DNS failed"
            return "\(reachedByAddress)/\(addressTargets) addresses answered, \(dns), \(Int((elapsed * 1000).rounded())) ms"
        }
    }

    /// Public resolvers on port 443, spread across operators so that one blocked network is never mistaken for a dead one.
    static let addressTargets: [ReachabilityProbe.Target] = [
        .init(name: "Cloudflare", host: "1.1.1.1", port: 443),
        .init(name: "Google", host: "8.8.8.8", port: 443),
        .init(name: "Quad9", host: "9.9.9.9", port: 443),
        .init(name: "Yandex", host: "77.88.8.8", port: 443),
        .init(name: "OpenDNS", host: "208.67.222.222", port: 443),
        .init(name: "AdGuard", host: "94.140.14.14", port: 443),
    ]

    /// Host names that resolve everywhere: connecting to one proves DNS works.
    static let nameTargets: [ReachabilityProbe.Target] = [
        .init(name: "captive.apple.com", host: "captive.apple.com", port: 80),
        .init(name: "www.gstatic.com", host: "www.gstatic.com", port: 80),
        .init(name: "ya.ru", host: "ya.ru", port: 443),
    ]

    static func check(timeout: Duration = .seconds(4)) async -> Report {
        let started = Date.now
        let outcome = await withTaskGroup(of: (byName: Bool, success: Bool).self, returning: (addresses: Int, names: Bool).self) { group in
            for target in addressTargets {
                group.addTask { (false, await ReachabilityProbe.canConnect(host: target.host, port: target.port, timeout: timeout)) }
            }
            for target in nameTargets {
                group.addTask { (true, await ReachabilityProbe.canConnect(host: target.host, port: target.port, timeout: timeout)) }
            }
            var addresses = 0
            var names = false
            for await result in group {
                guard result.success else { continue }
                if result.byName { names = true } else { addresses += 1 }
                // Enough evidence: stop waiting for the slow or blocked ones.
                if names, addresses >= 2 {
                    group.cancelAll()
                    break
                }
            }
            return (addresses, names)
        }
        return Report(
            reachedByAddress: outcome.addresses,
            addressTargets: addressTargets.count,
            nameResolution: outcome.names,
            elapsed: Date.now.timeIntervalSince(started),
            date: .now
        )
    }
}
