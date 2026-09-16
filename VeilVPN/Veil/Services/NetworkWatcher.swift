import AppKit
import Foundation
import Network

/// Reports network path changes and wake-from-sleep so the app can reconnect.
@MainActor
final class NetworkWatcher {
    enum Event: Sendable {
        case pathRestored
        case pathLost
        /// Still satisfied, but over different interfaces or gateways: Wi-Fi to Ethernet, one
        /// network to another, a VPN coming or going. The guard's TCP connection rarely
        /// survives it, and macOS reports no loss in between.
        case pathChanged
        case didWake
        case willSleep
    }

    var onEvent: (@MainActor (Event) -> Void)?
    /// Whether macOS currently reports a usable path at all.
    private(set) var isSatisfied = true

    private let monitor = NWPathMonitor()
    private var wasSatisfied = true
    private var lastSignature: String?
    private var observers: [NSObjectProtocol] = []

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            let signature = Self.signature(of: path)
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    self?.handle(satisfied: satisfied, signature: signature)
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "app.veilvpn.network-watcher"))
        let center = NSWorkspace.shared.notificationCenter
        observers.append(center.addObserver(forName: NSWorkspace.willSleepNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.onEvent?(.willSleep)
            }
        })
        observers.append(center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.onEvent?(.didWake)
            }
        })
    }

    func stop() {
        monitor.cancel()
        observers.forEach { NSWorkspace.shared.notificationCenter.removeObserver($0) }
        observers = []
    }

    private func handle(satisfied: Bool, signature: String) {
        let event = Self.event(previousSatisfied: wasSatisfied, previousSignature: lastSignature,
                               satisfied: satisfied, signature: signature)
        wasSatisfied = satisfied
        isSatisfied = satisfied
        if satisfied { lastSignature = signature }
        if let event { onEvent?(event) }
    }

    /// The interfaces and gateways a path runs over; the same path gives the same string.
    nonisolated static func signature(of path: NWPath) -> String {
        let interfaces = path.availableInterfaces.map { "\($0.name):\($0.type)" }.sorted()
        let gateways = path.gateways.map { "\($0)" }.sorted()
        return (interfaces + gateways).joined(separator: ",")
    }

    /// Loss and recovery outrank a change; a change is only reported between two satisfied
    /// paths that differ, and never for the first path seen.
    nonisolated static func event(previousSatisfied: Bool, previousSignature: String?,
                                  satisfied: Bool, signature: String) -> Event? {
        if satisfied != previousSatisfied { return satisfied ? .pathRestored : .pathLost }
        guard satisfied, let previousSignature, previousSignature != signature else { return nil }
        return .pathChanged
    }
}

/// How long auto-reconnect waits after each consecutive failure: a live network is not kept
/// waiting, a dead one is not hammered. Reset by a success and by the path coming back.
enum ReconnectBackoff {
    static func delay(afterFailures failures: Int) -> Duration {
        .seconds(min(300, 15 * (1 << min(max(failures, 0), 5))))
    }
}
