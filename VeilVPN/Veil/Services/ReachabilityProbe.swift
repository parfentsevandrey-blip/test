import Foundation
import Network

/// Quick TCP probes that tell whether the public Tor network is reachable without bridges.
enum ReachabilityProbe {
    struct Target: Sendable {
        let name: String
        let host: String
        let port: UInt16
    }

    /// A few Tor directory authorities (ORPort / DirPort). Censors that block Tor block these first.
    static let directoryAuthorities: [Target] = [
        Target(name: "moria1", host: "128.31.0.34", port: 9101),
        Target(name: "dizum", host: "45.66.35.11", port: 443),
        Target(name: "gabelmoo", host: "131.188.40.189", port: 443),
        Target(name: "maatuska", host: "171.25.193.9", port: 80),
        Target(name: "longclaw", host: "199.58.81.140", port: 443),
    ]

    struct Report: Equatable, Sendable {
        let reachable: Int
        let total: Int
        var directLooksPossible: Bool { reachable >= 2 }
    }

    /// Two answers settle it — `directLooksPossible` needs two — and so do four refusals, so there
    /// is no reason to pay the full timeout. On an open network this returns in a fraction of a
    /// second; a censored one is the only case that waits.
    static func conclusive(reachable: Int, failed: Int, total: Int) -> Bool {
        reachable >= 2 || failed >= total - 1
    }

    static func probeDirectTor(timeout: Duration = .seconds(4)) async -> Report {
        let total = directoryAuthorities.count
        return await withTaskGroup(of: Bool.self, returning: Report.self) { group in
            for target in directoryAuthorities {
                group.addTask {
                    await canConnect(host: target.host, port: target.port, timeout: timeout)
                }
            }
            var reachable = 0
            var failed = 0
            for await result in group {
                if result { reachable += 1 } else { failed += 1 }
                if conclusive(reachable: reachable, failed: failed, total: total) {
                    group.cancelAll()
                    break
                }
            }
            return Report(reachable: reachable, total: total)
        }
    }

    /// Plain TCP connect with a deadline; true when the handshake completes. Never goes through the
    /// system proxy (which may be Veil itself) and gives up early when the task is cancelled.
    static func canConnect(host: String, port: UInt16, timeout: Duration) async -> Bool {
        let seconds = Int(max(1, timeout.components.seconds))
        let queue = DispatchQueue(label: "app.veilvpn.probe")
        let tcp = NWProtocolTCP.Options()
        tcp.connectionTimeout = seconds
        let parameters = NWParameters(tls: nil, tcp: tcp)
        parameters.preferNoProxies = true
        let connection = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port) ?? 443, using: parameters)
        return await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
                var resumed = false
                let finish: (Bool) -> Void = { result in
                    guard !resumed else { return }
                    resumed = true
                    connection.cancel()
                    continuation.resume(returning: result)
                }
                connection.stateUpdateHandler = { state in
                    switch state {
                    case .ready: finish(true)
                    case .failed, .waiting, .cancelled: finish(false)
                    default: break
                    }
                }
                queue.asyncAfter(deadline: .now() + .seconds(seconds)) {
                    finish(false)
                }
                connection.start(queue: queue)
            }
        } onCancel: {
            connection.cancel()
        }
    }
}
