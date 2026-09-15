import Foundation

/// An exit relay as the consensus describes it: what `GETINFO ns/all` says about one router.
struct ExitRelay: Equatable, Sendable {
    /// Uppercase hex, forty characters.
    var fingerprint: String
    var nickname: String
    var address: String
    /// Consensus weight (kB/s): the bandwidth authorities' measurement, not the relay's claim.
    var bandwidth: Int
    var flags: Set<String>
    /// From the policy summary when the entry carries one; the Exit flag alone already means
    /// the relay lets two of ports 80, 443 and 6667 out, so an absent summary is taken as yes.
    var allows443: Bool

    /// Fit to carry video: a real, measured, stable exit that is not flagged bad.
    var isUsableExit: Bool {
        flags.contains("Exit") && flags.contains("Running") && flags.contains("Valid")
            && flags.contains("Fast") && !flags.contains("BadExit") && allows443
    }
}

/// Reads the consensus for exits and chooses where video should leave Tor: the widest pipe the
/// bandwidth authorities know of. Pure functions; the control-port side lives in the engine.
enum ExitCatalog {
    /// Parses the `r` / `s` / `w` / `p` entries of `GETINFO ns/all`.
    static func parse(_ text: String) -> [ExitRelay] {
        var relays: [ExitRelay] = []
        var current: ExitRelay?
        func flush() {
            if let relay = current { relays.append(relay) }
            current = nil
        }
        for rawLine in text.split(separator: "\n", omittingEmptySubsequences: true) {
            let line = rawLine.hasSuffix("\r") ? rawLine.dropLast() : rawLine[...]
            let parts = line.split(separator: " ")
            guard let kind = parts.first else { continue }
            switch kind {
            case "r":
                flush()
                // r nickname identity digest date time address orport dirport — the digest is
                // absent in a microdescriptor consensus, so the address is parsed from the end.
                guard parts.count >= 7, let fingerprint = hexFingerprint(fromBase64: String(parts[2])) else { continue }
                current = ExitRelay(fingerprint: fingerprint, nickname: String(parts[1]),
                                    address: String(parts[parts.count - 3]), bandwidth: 0,
                                    flags: [], allows443: true)
            case "s":
                current?.flags = Set(parts.dropFirst().map(String.init))
            case "w":
                for token in parts.dropFirst() where token.hasPrefix("Bandwidth=") {
                    current?.bandwidth = Int(token.dropFirst("Bandwidth=".count)) ?? 0
                }
            case "p":
                current?.allows443 = policyAllows(String(line.dropFirst(2)), port: 443)
            default:
                break
            }
        }
        flush()
        return relays
    }

    /// The consensus writes identities as unpadded base64 of the twenty-byte digest; the
    /// control port wants them as hex.
    static func hexFingerprint(fromBase64 digest: String) -> String? {
        var padded = digest
        while padded.count % 4 != 0 { padded.append("=") }
        guard let data = Data(base64Encoded: padded), data.count == 20 else { return nil }
        return data.map { String(format: "%02X", $0) }.joined()
    }

    /// `accept 80,443` / `reject 1-79,81-442,444-65535`: whether `port` gets through.
    static func policyAllows(_ summary: String, port: Int) -> Bool {
        let parts = summary.split(separator: " ", maxSplits: 1)
        guard parts.count == 2 else { return true }
        let listed = parts[1].split(separator: ",").contains { range in
            let bounds = range.split(separator: "-").compactMap { Int($0) }
            switch bounds.count {
            case 1: return bounds[0] == port
            case 2: return bounds[0] <= port && port <= bounds[1]
            default: return false
            }
        }
        return parts[0] == "accept" ? listed : !listed
    }

    /// The widest usable exits, capacity first. Stable relays outrank unstable ones of equal
    /// weight, because a video stream that lasts an hour needs an exit that lasts an hour.
    static func rank(_ relays: [ExitRelay], count: Int = 12, excluding: Set<String> = []) -> [ExitRelay] {
        relays
            .filter { $0.isUsableExit && !excluding.contains($0.fingerprint) }
            .sorted { left, right in
                let leftStable = left.flags.contains("Stable"), rightStable = right.flags.contains("Stable")
                if leftStable != rightStable { return leftStable }
                if left.bandwidth != right.bandwidth { return left.bandwidth > right.bandwidth }
                return left.fingerprint < right.fingerprint
            }
            .prefix(count)
            .map { $0 }
    }

    /// `MapAddress *.youtube.com *.youtube.com.$FP.exit` for every domain: tor keeps the host and
    /// takes the suffix as the exit to use. The `.exit` notation is refused on a SOCKS request
    /// but honoured from MapAddress, which is exactly why it goes in through the configuration.
    static func mapAddressPairs(exit fingerprint: String, domains: [String]) -> [(key: String, value: String?)] {
        var pairs: [(key: String, value: String?)] = []
        for domain in domains {
            pairs.append(("MapAddress", "\(domain) \(domain).\(fingerprint).exit"))
            pairs.append(("MapAddress", "*.\(domain) *.\(domain).\(fingerprint).exit"))
        }
        return pairs
    }
}
