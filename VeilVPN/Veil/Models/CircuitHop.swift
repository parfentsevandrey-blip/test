import Foundation

/// One relay of the currently used Tor circuit.
struct CircuitHop: Identifiable, Equatable, Sendable {
    enum Role: Equatable, Sendable {
        case bridge
        case entry
        case middle
        case exit

        var title: String {
            switch self {
            case .bridge: String(localized: "Bridge")
            case .entry: String(localized: "Guard")
            case .middle: String(localized: "Middle")
            case .exit: String(localized: "Exit")
            }
        }
    }

    let fingerprint: String
    let nickname: String
    let address: String?
    let countryCode: String?
    let role: Role

    var id: String { fingerprint }

    var flag: String {
        guard let countryCode, countryCode.count == 2, countryCode != "??" else { return "" }
        return countryCode.flagEmoji
    }

    var countryName: String? {
        guard let countryCode, countryCode.count == 2, countryCode != "??" else { return nil }
        return Locale.current.localizedString(forRegionCode: countryCode.uppercased())
    }
}

/// Result of https://check.torproject.org/api/ip
struct TorCheckResult: Decodable, Equatable, Sendable {
    let isTor: Bool
    let ip: String

    enum CodingKeys: String, CodingKey {
        case isTor = "IsTor"
        case ip = "IP"
    }

    init(isTor: Bool, ip: String) {
        self.isTor = isTor
        self.ip = ip
    }
}
