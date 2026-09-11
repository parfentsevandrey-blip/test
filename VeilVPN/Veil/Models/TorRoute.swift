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
    /// Fingerprints of the exit relays the route tuner measured to be fastest; empty = any exit in `exitCountry`.
    var pinnedExits: [String] = []
    /// Fingerprint(s) of the pinned middle relay; empty = any middle in `middleCountry`.
    var pinnedMiddles: [String] = []
    /// Relays to keep out of the next circuits (the previous winners, so a rotation really moves).
    var avoidedRelays: [String] = []

    static let fiveEyes = ["us", "gb", "ca", "au", "nz"]

    init(middleCountry: String? = nil, exitCountry: String? = nil, excludedCountries: [String] = [],
         pinnedExits: [String] = [], pinnedMiddles: [String] = [], avoidedRelays: [String] = []) {
        self.middleCountry = middleCountry
        self.exitCountry = exitCountry
        self.excludedCountries = excludedCountries
        self.pinnedExits = pinnedExits
        self.pinnedMiddles = pinnedMiddles
        self.avoidedRelays = avoidedRelays
    }

    var isRestricted: Bool {
        middleCountry != nil || exitCountry != nil || !excludedCountries.isEmpty || isPinned
    }

    var isPinned: Bool {
        !pinnedExits.isEmpty || !pinnedMiddles.isEmpty
    }

    /// The same route without any measured pins or temporary exclusions.
    var base: TorRoute {
        TorRoute(middleCountry: middleCountry, exitCountry: exitCountry, excludedCountries: excludedCountries)
    }

    private static func fingerprintList(_ fingerprints: [String]) -> String {
        fingerprints.map { "$" + $0.uppercased() }.joined(separator: ",")
    }

    /// `Key value` pairs for torrc / SETCONF, and keys that must be reset.
    var configuration: (set: [(key: String, value: String)], reset: [String]) {
        var set: [(key: String, value: String)] = []
        var reset: [String] = []
        if !pinnedMiddles.isEmpty {
            set.append(("MiddleNodes", Self.fingerprintList(pinnedMiddles)))
        } else if let middle = middleCountry {
            set.append(("MiddleNodes", "{\(middle)}"))
        } else {
            reset.append("MiddleNodes")
        }
        if !pinnedExits.isEmpty {
            set.append(("ExitNodes", Self.fingerprintList(pinnedExits)))
        } else if let exit = exitCountry {
            set.append(("ExitNodes", "{\(exit)}"))
        } else {
            reset.append("ExitNodes")
        }
        // Never exclude a country we explicitly asked for; Tor would have no relays left.
        let excluded = excludedCountries.filter { $0 != middleCountry && $0 != exitCountry }
        var excludeList = excluded.map { "{\($0)}" }
        let pinned = Set((pinnedExits + pinnedMiddles).map { $0.uppercased() })
        excludeList += avoidedRelays.map { $0.uppercased() }.filter { !pinned.contains($0) }.map { "$" + $0 }
        if !excludeList.isEmpty {
            set.append(("ExcludeNodes", excludeList.joined(separator: ",")))
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
        // Exclusions are no longer gated on multihop: "never route through these countries" is a
        // protective choice in its own right, and gating it silently ignored what the user asked.
        var excluded: [String] = excludedCountries
        if avoidFiveEyes {
            excluded.append(contentsOf: TorRoute.fiveEyes)
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
