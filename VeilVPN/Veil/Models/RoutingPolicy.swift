import Foundation

/// How YouTube traffic leaves the Mac.
enum YouTubeMode: String, Codable, CaseIterable, Identifiable, Sendable {
    /// Through Tor like everything else: anonymous, but slow and often met with "confirm you're not a bot".
    case tor
    /// Directly, with the TLS ClientHello fragmented so throttling DPI cannot see the host name. Fast; not anonymous.
    case directAntiThrottle
    /// Directly, untouched.
    case direct

    var id: String { rawValue }
}

/// User-space DPI evasion technique applied to the first TLS record of a direct connection.
enum DPIStrategy: String, Codable, CaseIterable, Identifiable, Sendable {
    /// Split the ClientHello into two TLS records *and* two TCP segments in the middle of the SNI host name.
    case recordAndSegmentAtSNI
    /// Two TCP segments split in the middle of the SNI host name.
    case segmentAtSNI
    /// Two TLS records split in the middle of the SNI host name, sent together.
    case recordAtSNI
    /// First TCP segment carries a single byte.
    case firstByte

    var id: String { rawValue }
}

enum RouteDecision: Equatable, Sendable {
    case tor
    case direct(antiThrottle: Bool)
}

/// Which hosts bypass Tor, and how. Evaluated by the HTTP bridge for every CONNECT.
struct RoutingPolicy: Equatable, Sendable {
    var youtubeMode: YouTubeMode = .tor
    var customDirectDomains: [String] = []
    var customDirectAntiThrottle: Bool = true
    var strategy: DPIStrategy = .recordAndSegmentAtSNI

    /// Everything the YouTube web player and apps talk to.
    static let youtubeDomains: [String] = [
        "youtube.com", "youtu.be", "youtube-nocookie.com", "youtubekids.com", "youtubeeducation.com",
        "googlevideo.com", "ytimg.com", "ggpht.com", "gvt1.com", "gvt2.com",
        "jnn-pa.googleapis.com", "youtubei.googleapis.com", "yt3.googleusercontent.com", "lh3.googleusercontent.com",
    ]

    static func isYouTube(_ host: String) -> Bool {
        matches(host, domains: youtubeDomains)
    }

    static func matches(_ host: String, domains: [String]) -> Bool {
        let lowered = host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        for domain in domains {
            let candidate = domain.lowercased()
            if lowered == candidate || lowered.hasSuffix("." + candidate) {
                return true
            }
        }
        return false
    }

    /// Where a connection to `host` should go. `torAvailable` is false in YouTube Turbo mode (no Tor running).
    func decision(for host: String, torAvailable: Bool) -> RouteDecision {
        if Self.isYouTube(host) {
            switch youtubeMode {
            case .tor:
                return torAvailable ? .tor : .direct(antiThrottle: true)
            case .directAntiThrottle:
                return .direct(antiThrottle: true)
            case .direct:
                return .direct(antiThrottle: false)
            }
        }
        if !customDirectDomains.isEmpty, Self.matches(host, domains: customDirectDomains) {
            return .direct(antiThrottle: customDirectAntiThrottle)
        }
        return torAvailable ? .tor : .direct(antiThrottle: false)
    }

    /// Parses a user-entered list: one domain per line or comma-separated, comments with `#`.
    static func parseDomains(_ text: String) -> [String] {
        text.components(separatedBy: CharacterSet(charactersIn: "\n,; "))
            .map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            .map { $0.hasPrefix("*.") ? String($0.dropFirst(2)) : $0 }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") && $0.contains(".") }
    }
}

extension AppSettings {
    var routingPolicy: RoutingPolicy {
        RoutingPolicy(
            youtubeMode: youtubeMode,
            customDirectDomains: RoutingPolicy.parseDomains(customDirectDomains),
            customDirectAntiThrottle: customDirectAntiThrottle,
            strategy: dpiStrategy
        )
    }
}
