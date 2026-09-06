import Foundation

/// A country the user can pin Tor exit relays to (`ExitNodes {xx}`).
struct ExitLocation: Identifiable, Hashable, Sendable {
    /// Lowercase ISO 3166-1 alpha-2 code, as Tor expects it.
    let code: String

    var id: String { code }
    var flag: String { code.flagEmoji }
    var name: String {
        Locale.current.localizedString(forRegionCode: code.uppercased()) ?? code.uppercased()
    }

    /// Countries with a healthy number of exit relays.
    static let popular: [ExitLocation] = [
        "us", "de", "nl", "fr", "gb", "se", "ch", "fi", "at", "ca", "no", "dk", "pl", "cz", "ro",
        "es", "it", "jp", "sg", "au", "ua", "lu", "is", "ee", "lv", "md", "bg", "hk", "kr", "br",
    ].map { ExitLocation(code: $0) }

    static func named(_ code: String?) -> ExitLocation? {
        guard let code, !code.isEmpty else { return nil }
        return ExitLocation(code: code.lowercased())
    }
}

extension String {
    /// Regional-indicator flag for an ISO country code ("de" → 🇩🇪). Non-letters are ignored.
    var flagEmoji: String {
        var result = ""
        for scalar in uppercased().unicodeScalars where scalar.value >= 65 && scalar.value <= 90 {
            if let flagScalar = UnicodeScalar(0x1F1E6 + scalar.value - 65) {
                result.unicodeScalars.append(flagScalar)
            }
        }
        return result
    }
}
