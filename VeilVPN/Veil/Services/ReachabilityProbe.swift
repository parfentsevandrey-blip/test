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

    static func probeDirectTor(timeout: Duration = .seconds(4)) async -> Report {
        let results = await withTaskGroup(of: Bool.self, returning: [Bool].self) { group in
            for target in directoryAuthorities {
                group.addTask {
                    await canConnect(host: target.host, port: target.port, timeout: timeout)
                }
            }
            var collected: [Bool] = []
            for await result in group {
                collected.append(result)
            }
            return collected
        }
        return Report(reachable: results.filter { $0 }.count, total: directoryAuthorities.count)
    }

    /// Plain TCP connect with a deadline; true when the handshake completes.
    static func canConnect(host: String, port: UInt16, timeout: Duration) async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            let queue = DispatchQueue(label: "app.veilvpn.probe")
            let tcp = NWProtocolTCP.Options()
            tcp.connectionTimeout = Int(max(1, timeout.components.seconds))
            let connection = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port) ?? 443, using: NWParameters(tls: nil, tcp: tcp))
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
                case .failed, .waiting: finish(false)
                case .cancelled: finish(false)
                default: break
                }
            }
            queue.asyncAfter(deadline: .now() + .seconds(Int(max(1, timeout.components.seconds)))) {
                finish(false)
            }
            connection.start(queue: queue)
        }
    }
}
