import CryptoKit
import Foundation

/// A stable, SSID-free identity for "which network am I on", used only as a local lookup key for
/// connect history. It stores no router address, no DNS server and no network name: the material
/// is hashed with a random per-installation salt and truncated to 64 bits.
///
/// No SSID on purpose. `CWInterface.ssid()` needs Location Services on current macOS: it would
/// either return nil — collapsing every network onto one key — or prompt for location permission
/// in a privacy app.
struct NetworkFingerprint: Codable, Hashable, Sendable {
    enum Kind: String, Codable, Sendable { case wifi, ethernet, tunnel, other, none }

    let id: String
    let kind: Kind

    static let saltKey = "app.veilvpn.networkSalt"

    static func material(kind: Kind, router: String?, dns0: String?, interface: String?) -> String {
        [kind.rawValue, router ?? "-", dns0 ?? "-", interface ?? "-"].joined(separator: "|")
    }

    static func id(material: String, salt: Data) -> String {
        var hasher = SHA256()
        hasher.update(data: salt)
        hasher.update(data: Data(material.utf8))
        return hasher.finalize().prefix(8).map { String(format: "%02x", $0) }.joined()
    }

    static func salt(from defaults: UserDefaults = .standard) -> Data {
        if let stored = defaults.data(forKey: saltKey), stored.count == 16 { return stored }
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let fresh = Data(bytes)
        defaults.set(fresh, forKey: saltKey)
        return fresh
    }

    static func kind(for primary: NetworkReset.Primary?) -> Kind {
        guard let primary else { return .none }
        if primary.isTunnel { return .tunnel }
        if primary.isWiFi { return .wifi }
        if primary.interface.hasPrefix("en") { return .ethernet }
        return .other
    }

    static func current(primary: NetworkReset.Primary? = NetworkReset.primary(),
                        defaults: UserDefaults = .standard) -> NetworkFingerprint {
        // Every call is qualified: these statics share their names with the instance properties,
        // and an unqualified call next to a local of the same name is how this repo has been
        // bitten before.
        let resolved = Self.kind(for: primary)
        let text = Self.material(kind: resolved, router: primary?.router, dns0: primary?.firstDNS,
                                 interface: primary?.interface)
        return NetworkFingerprint(id: Self.id(material: text, salt: Self.salt(from: defaults)),
                                  kind: resolved)
    }
}
