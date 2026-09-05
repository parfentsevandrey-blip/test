import Foundation
import NetworkExtension

/// Installs and drives the packet tunnel.
///
/// macOS keeps VPN configurations in system preferences rather than in the
/// app, so the first connect asks the user to allow one to be added. That
/// prompt is the system asking whether this app may route the machine's
/// traffic, and it is the right question — it is answered once.
///
/// The tunnel is started only after a stream has already gone through tor.
/// That ordering is deliberate and is the macOS shape of the same honesty the
/// Android build arrived at: rather than raising an interface and holding
/// traffic while the route is worked out, nothing is captured until there is
/// somewhere working to send it. The machine's traffic goes where it always
/// went until the moment the tunnel is real.
actor VPNManager {

    private var manager: NETunnelProviderManager?

    enum Failure: Error, LocalizedError {
        case notPermitted
        case neverCameUp(String)
        case timedOut

        var errorDescription: String? {
            switch self {
            case .notPermitted:
                return "macOS did not allow the VPN configuration to be added"
            case .neverCameUp(let status):
                return "the tunnel did not come up (status: \(status))"
            case .timedOut:
                return "the tunnel did not come up in time"
            }
        }
    }

    /// Finds this app's configuration, or makes one.
    private func loadManager() async throws -> NETunnelProviderManager {
        if let manager { return manager }
        let existing = try await NETunnelProviderManager.loadAllFromPreferences()
        let found = existing.first ?? NETunnelProviderManager()
        manager = found
        return found
    }

    func start(
        socksSocket: String,
        blockAds: Bool,
        blocklist: String?,
        blockUDP: Bool,
        killSwitch: Bool
    ) async throws {
        let manager = try await loadManager()

        let proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "app.veil.mac.tunnel"
        // Required to be non-empty even though nothing is dialled: the tunnel
        // goes to a socket, not to a server. There is no server.
        proto.serverAddress = "Tor"
        proto.providerConfiguration = [
            "socksSocket": socksSocket,
            "blockAds": blockAds,
            "blocklist": blocklist ?? "",
            "blockUDP": blockUDP,
        ]
        // Everything keeps going through the tunnel if the extension stops
        // unexpectedly, rather than silently falling back to the open network.
        proto.disconnectOnSleep = false

        manager.protocolConfiguration = proto
        manager.localizedDescription = "Veil"
        manager.isEnabled = true
        // On-demand is what makes the kill switch a kill switch: without it,
        // a tunnel that drops simply stops, and the machine carries on in the
        // clear without saying so.
        if killSwitch {
            let rule = NEOnDemandRuleConnect()
            rule.interfaceTypeMatch = .any
            manager.onDemandRules = [rule]
            manager.isOnDemandEnabled = true
        } else {
            manager.onDemandRules = []
            manager.isOnDemandEnabled = false
        }

        try await manager.saveToPreferences()
        // A save invalidates the in-memory object; reload before starting or
        // the session refers to a configuration that no longer exists.
        try await manager.loadFromPreferences()

        try manager.connection.startVPNTunnel()
    }

    /// Waits for the session to report connected.
    ///
    /// `startVPNTunnel` returning is not the tunnel being up; it means the
    /// request was accepted. The extension may then fail to load — which is
    /// what happens on a build without the entitlement, whenever the
    /// configuration was allowed to be saved at all — or load and fail, and
    /// the session's status is the only thing that says which. Calling the
    /// tunnel attached on the strength of a request being accepted would be
    /// the fake "connected" this app exists not to show.
    func awaitConnected(within timeout: TimeInterval) async throws {
        let manager = try await loadManager()
        let began = Date()
        var seenTrying = false
        while Date().timeIntervalSince(began) < timeout {
            let status = manager.connection.status
            switch status {
            case .connected:
                return
            case .connecting, .reasserting:
                seenTrying = true
            case .disconnected, .invalid, .disconnecting:
                // Straight after the start request the status can still read
                // "disconnected" for a moment. Only once it has been seen
                // trying, or after a grace period, is that a verdict.
                if seenTrying || Date().timeIntervalSince(began) > 3 {
                    throw Failure.neverCameUp(Self.describe(status))
                }
            @unknown default:
                break
            }
            try await Task.sleep(for: .milliseconds(250))
        }
        throw Failure.timedOut
    }

    func stop() async {
        guard let manager = try? await loadManager() else { return }
        manager.isOnDemandEnabled = false
        try? await manager.saveToPreferences()
        manager.connection.stopVPNTunnel()
    }

    var status: NEVPNStatus {
        get async { (try? await loadManager())?.connection.status ?? .invalid }
    }

    private static func describe(_ status: NEVPNStatus) -> String {
        switch status {
        case .invalid: return "invalid"
        case .disconnected: return "disconnected"
        case .connecting: return "connecting"
        case .connected: return "connected"
        case .reasserting: return "reasserting"
        case .disconnecting: return "disconnecting"
        @unknown default: return "unknown"
        }
    }
}
