import Foundation
import Security

/// What the app's own signature says it may do.
///
/// Read from the signature rather than assumed from the build, because the
/// same source ships two ways: signed by a team, with the app group and the
/// packet-tunnel entitlement, and ad-hoc, with neither. The difference
/// decides real things. macOS 15 and later treat an app that touches a group
/// container it is not entitled to as an app reading another app's data — a
/// prompt at best, a refused directory at worst — so where the state lives
/// has to be decided before anything is touched. And a tunnel the signature
/// cannot carry is not worth thirty seconds of waiting to find out.
enum Entitlements {

    /// The entitlements dictionary in the running code's signature; empty
    /// when there is no signature, or an ad-hoc one that carries none.
    static let own: [String: Any] = {
        var code: SecCode?
        guard SecCodeCopySelf(SecCSFlags(), &code) == errSecSuccess, let code else { return [:] }
        var staticCode: SecStaticCode?
        guard SecCodeCopyStaticCode(code, SecCSFlags(), &staticCode) == errSecSuccess,
              let staticCode else { return [:] }
        var information: CFDictionary?
        let flags = SecCSFlags(
            rawValue: UInt32(kSecCSSigningInformation) | UInt32(kSecCSRequirementInformation)
        )
        guard SecCodeCopySigningInformation(staticCode, flags, &information) == errSecSuccess,
              let dictionary = information as? [String: Any],
              let entitlements = dictionary[kSecCodeInfoEntitlementsDict as String] as? [String: Any]
        else { return [:] }
        return entitlements
    }()

    /// The app groups this signature is a member of.
    static var appGroups: [String] {
        own["com.apple.security.application-groups"] as? [String] ?? []
    }

    /// Whether a packet tunnel provider may be started at all.
    static var hasPacketTunnel: Bool {
        let kinds = own["com.apple.developer.networking.networkextension"] as? [String] ?? []
        return kinds.contains("packet-tunnel-provider")
    }
}
