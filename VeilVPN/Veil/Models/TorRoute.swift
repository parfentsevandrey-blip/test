import Foundation

/// Where the hops of a circuit may live. Tor always builds three-hop circuits (bridge → middle →
/// exit); "multihop" in Veil means pinning the countries of the middle and exit hops, excluding
/// countries entirely, and rotating the route on a timer.
struct TorRoute: Equatable, Sendable {
    /// Lowercase ISO code for the middle relay, or nil for automatic.
    var middleCountry: String?
    /// Lowercase ISO code for the exit relay, or nil for automatic.
    var exitCountry: String?
    /// Lowercase ISO codes that must never appear in a circuit.
    var excludedCountries: [String]

    static let fiveEyes = ["us", "gb", "ca", "au", "nz"]

    var isRestricted: Bool {
        middleCountry != nil || exitCountry != nil || !excludedCountries.isEmpty
    }

    /// `Key value` pairs for torrc / SETCONF, and keys that must be reset.
    var configuration: (set: [(key: String, value: String)], reset: [String]) {
        var set: [(key: String, value: String)] = []
        var reset: [String] = []
        if let middle = middleCountry {
            set.append(("MiddleNodes", "{\(middle)}"))
        } else {
            reset.append("MiddleNodes")
        }
        if let exit = exitCountry {
            set.append(("ExitNodes", "{\(exit)}"))
        } else {
            reset.append("ExitNodes")
        }
        // Never exclude a country we explicitly asked for; Tor would have no relays left.
        let excluded = excludedCountries.filter { $0 != middleCountry && $0 != exitCountry }
        if !excluded.isEmpty {
            set.append(("ExcludeNodes", excluded.map { "{\($0)}" }.joined(separator: ",")))
        } else {
            reset.append("ExcludeNodes")
        }
        if set.isEmpty {
            reset.append("StrictNodes")
        } else {
            set.append(("StrictNodes", "1"))
        }
        return (set, reset)
    }

    var torrcLines: [String] {
        configuration.set.map { "\($0.key) \($0.value)" }
    }
}

extension AppSettings {
    /// The route Tor should use given the current settings.
    var route: TorRoute {
        var excluded: [String] = []
        if multihopEnabled {
            excluded = excludedCountries
            if avoidFiveEyes {
                excluded.append(contentsOf: TorRoute.fiveEyes)
            }
        }
        let normalizedExit = exitCountry.flatMap { TorConfiguration.isValidCountryCode($0) ? $0.lowercased() : nil }
        let normalizedMiddle = multihopEnabled
            ? middleCountry.flatMap { TorConfiguration.isValidCountryCode($0) ? $0.lowercased() : nil }
            : nil
        var seen: Set<String> = []
        let uniqueExcluded = excluded.map { $0.lowercased() }.filter { TorConfiguration.isValidCountryCode($0) && seen.insert($0).inserted }
        return TorRoute(middleCountry: normalizedMiddle, exitCountry: normalizedExit, excludedCountries: uniqueExcluded)
    }
}
