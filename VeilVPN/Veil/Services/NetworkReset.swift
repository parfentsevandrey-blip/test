import CoreWLAN
import Foundation
import SystemConfiguration

/// What people do by hand when the network is "up" but dead — Wi-Fi off and on again — done from
/// inside the app. Tried in order: a CoreWLAN power toggle, `networksetup`, and finally disabling
/// and re-enabling the primary network service through SystemConfiguration, which also covers
/// Ethernet and reuses the administrator right already granted for the system proxy.
enum NetworkReset {
    struct Primary: Equatable, Sendable {
        /// BSD name, e.g. "en0".
        let interface: String
        let serviceID: String?
        let serviceName: String?
        let isWiFi: Bool

        /// VPN tunnels and dial-ups: resetting them would fight another VPN rather than fix Wi-Fi.
        var isTunnel: Bool { Self.isTunnelInterface(interface) }

        static func isTunnelInterface(_ name: String) -> Bool {
            ["utun", "ipsec", "ppp", "tun", "tap", "wg", "gpd"].contains { name.hasPrefix($0) }
        }

        var displayName: String {
            if let serviceName, !serviceName.isEmpty { return "\(serviceName) (\(interface))" }
            return interface
        }
    }

    enum Method: Equatable, Sendable {
        case wifiPower
        case networksetup
        case serviceCycle

        var title: String {
            switch self {
            case .wifiPower: "Wi-Fi power toggle"
            case .networksetup: "networksetup power toggle"
            case .serviceCycle: "network service restart"
            }
        }
    }

    enum ResetError: LocalizedError {
        case noPrimaryInterface
        case serviceNotFound(String)
        case failed(String)

        var errorDescription: String? {
            switch self {
            case .noPrimaryInterface: String(localized: "No active network interface was found.")
            case .serviceNotFound(let name): String(localized: "The network service for \(name) was not found.")
            case .failed(let detail): String(localized: "Every reset method failed: \(detail)")
            }
        }
    }

    private static let disabledServiceKey = "app.veilvpn.disabledNetworkService"

    // MARK: Discovery

    /// The interface carrying the default route right now.
    static func primary() -> Primary? {
        guard let store = SCDynamicStoreCreate(nil, "Veil" as CFString, nil, nil),
              let global = SCDynamicStoreCopyValue(store, "State:/Network/Global/IPv4" as CFString) as? [String: Any],
              let interface = global["PrimaryInterface"] as? String else {
            return nil
        }
        let serviceID = global["PrimaryService"] as? String
        var serviceName: String?
        if let serviceID,
           let setup = SCDynamicStoreCopyValue(store, "Setup:/Network/Service/\(serviceID)" as CFString) as? [String: Any] {
            serviceName = setup["UserDefinedName"] as? String
        }
        let wifiNames = CWWiFiClient.interfaceNames() ?? []
        return Primary(interface: interface, serviceID: serviceID, serviceName: serviceName, isWiFi: wifiNames.contains(interface))
    }

    /// True while some interface carries an IPv4 default route.
    static func hasIPv4() -> Bool {
        guard let store = SCDynamicStoreCreate(nil, "Veil" as CFString, nil, nil),
              let global = SCDynamicStoreCopyValue(store, "State:/Network/Global/IPv4" as CFString) as? [String: Any] else {
            return false
        }
        return global["PrimaryInterface"] as? String != nil
    }

    static var hasPendingRestore: Bool {
        UserDefaults.standard.string(forKey: disabledServiceKey) != nil
    }

    // MARK: Reset

    /// Switches the interface off and on. Returns the method that worked; the interface is always
    /// switched back on, even when the surrounding task is cancelled.
    static func perform(_ primary: Primary) async throws -> Method {
        var failures: [String] = []
        if primary.isWiFi {
            do {
                try await toggleWiFiPower(primary.interface)
                return .wifiPower
            } catch {
                failures.append("CoreWLAN: \(error.localizedDescription)")
            }
            do {
                try await toggleAirportPower(primary.interface)
                return .networksetup
            } catch {
                failures.append("networksetup: \(error.localizedDescription)")
            }
        }
        do {
            try await cycleService(primary)
            return .serviceCycle
        } catch {
            failures.append("SystemConfiguration: \(error.localizedDescription)")
        }
        throw ResetError.failed(failures.joined(separator: "; "))
    }

    /// Waits until an IPv4 default route is back, then a moment longer for DNS to settle.
    static func waitForNetwork(timeout: Duration) async -> Bool {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if hasIPv4() {
                try? await Task.sleep(for: .seconds(1))
                return true
            }
            do {
                try await Task.sleep(for: .milliseconds(500))
            } catch {
                return hasIPv4()
            }
        }
        return hasIPv4()
    }

    private static func toggleWiFiPower(_ name: String) async throws {
        guard let interface = CWWiFiClient.shared().interface(withName: name) else {
            throw ResetError.failed("no Wi-Fi interface named \(name)")
        }
        try interface.setPower(false)
        try? await Task.sleep(for: .seconds(2))
        try interface.setPower(true)
    }

    private static func toggleAirportPower(_ name: String) async throws {
        for state in ["off", "on"] {
            let result = try await ProcessRunner.run("/usr/sbin/networksetup", ["-setairportpower", name, state])
            let output = (result.stderr + result.stdout).trimmingCharacters(in: .whitespacesAndNewlines)
            guard result.status == 0, !output.localizedCaseInsensitiveContains("error") else {
                throw ResetError.failed(output.isEmpty ? "exit status \(result.status)" : output)
            }
            if state == "off" {
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    /// Marks the service inactive and active again, which drops and re-acquires its DHCP lease,
    /// routes and DNS configuration — the same thing System Settings does for "Make Inactive".
    private static func cycleService(_ primary: Primary) async throws {
        let authorization = try SystemConfigurationProxyWriter.authorization()
        guard let preferences = SCPreferencesCreateWithAuthorization(nil, "Veil" as CFString, nil, authorization) else {
            throw SystemProxyError.preferencesUnavailable
        }
        guard let service = findService(primary, in: preferences) else {
            throw ResetError.serviceNotFound(primary.displayName)
        }
        let identifier = (SCNetworkServiceGetServiceID(service) as String?) ?? primary.interface
        UserDefaults.standard.set(identifier, forKey: disabledServiceKey)
        try setEnabled(service, false, preferences: preferences)
        try? await Task.sleep(for: .seconds(2))
        try setEnabled(service, true, preferences: preferences)
        UserDefaults.standard.removeObject(forKey: disabledServiceKey)
    }

    private static func findService(_ primary: Primary, in preferences: SCPreferences) -> SCNetworkService? {
        if let serviceID = primary.serviceID, let service = SCNetworkServiceCopy(preferences, serviceID as CFString) {
            return service
        }
        guard let set = SCNetworkSetCopyCurrent(preferences),
              let services = SCNetworkSetCopyServices(set) as? [SCNetworkService] else {
            return nil
        }
        return services.first { service in
            guard let interface = SCNetworkServiceGetInterface(service),
                  let name = SCNetworkInterfaceGetBSDName(interface) as String? else {
                return false
            }
            return name == primary.interface
        }
    }

    private static func setEnabled(_ service: SCNetworkService, _ enabled: Bool, preferences: SCPreferences) throws {
        guard SCPreferencesLock(preferences, true) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        defer { _ = SCPreferencesUnlock(preferences) }
        guard SCNetworkServiceSetEnabled(service, enabled) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        guard SCPreferencesCommitChanges(preferences) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        guard SCPreferencesApplyChanges(preferences) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
    }

    /// A crash between "disable" and "enable" would leave the service inactive; repair it on launch.
    static func restoreDisabledServiceIfNeeded() throws -> String? {
        guard let identifier = UserDefaults.standard.string(forKey: disabledServiceKey) else { return nil }
        let authorization = try SystemConfigurationProxyWriter.authorization()
        guard let preferences = SCPreferencesCreateWithAuthorization(nil, "Veil" as CFString, nil, authorization),
              let service = SCNetworkServiceCopy(preferences, identifier as CFString) else {
            UserDefaults.standard.removeObject(forKey: disabledServiceKey)
            return nil
        }
        try setEnabled(service, true, preferences: preferences)
        UserDefaults.standard.removeObject(forKey: disabledServiceKey)
        return (SCNetworkServiceGetName(service) as String?) ?? identifier
    }
}
