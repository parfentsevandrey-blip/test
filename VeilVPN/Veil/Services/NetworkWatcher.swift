import AppKit
import Foundation
import Network

/// Reports network path changes and wake-from-sleep so the app can reconnect.
@MainActor
final class NetworkWatcher {
    enum Event: Sendable {
        case pathRestored
        case pathLost
        case didWake
    }

    var onEvent: (@MainActor (Event) -> Void)?

    private let monitor = NWPathMonitor()
    private var wasSatisfied = true
    private var observers: [NSObjectProtocol] = []

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    self?.handle(satisfied: satisfied)
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "app.veilvpn.network-watcher"))
        let center = NSWorkspace.shared.notificationCenter
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

    private func handle(satisfied: Bool) {
        guard satisfied != wasSatisfied else { return }
        wasSatisfied = satisfied
        onEvent?(satisfied ? .pathRestored : .pathLost)
    }
}
