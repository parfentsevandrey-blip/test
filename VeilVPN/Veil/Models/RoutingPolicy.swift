import Foundation
import SwiftUI

/// How traffic for a site leaves the Mac.
enum RouteMode: String, Codable, CaseIterable, Identifiable, Sendable {
    /// Through Tor like everything else: anonymous, but slow and sometimes met with bot checks.
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

/// A well-known service with the hosts it talks to.
struct ServicePreset: Identifiable, Sendable {
    let id: String
    let name: String
    let symbol: String
    let domains: [String]
    /// Shown under the picker: caveats such as "the desktop app ignores proxies".
    let note: LocalizedStringKey?
}

enum ServiceCatalog {
    static let all: [ServicePreset] = [
        ServicePreset(id: "discord", name: "Discord", symbol: "bubble.left.and.bubble.right.fill",
                      domains: ["discord.com", "discordapp.com", "discord.gg", "discordapp.net", "discord.media", "discordcdn.com"],
                      note: "Web and the desktop app follow the system proxy; voice (UDP) never goes through a proxy."),
        ServicePreset(id: "telegram", name: "Telegram", symbol: "paperplane.fill",
                      domains: ["telegram.org", "t.me", "telegram.me", "telesco.pe", "tdesktop.com", "telegram.dog"],
                      note: "Only Telegram Web. The desktop app needs its own proxy setting: SOCKS5 127.0.0.1 with Veil's SOCKS port."),
        ServicePreset(id: "twitch", name: "Twitch", symbol: "gamecontroller.fill",
                      domains: ["twitch.tv", "ttvnw.net", "jtvnw.net", "twitchcdn.net", "twitchsvc.net"],
                      note: nil),
        ServicePreset(id: "instagram", name: "Instagram", symbol: "camera.fill",
                      domains: ["instagram.com", "cdninstagram.com", "ig.me"],
                      note: nil),
        ServicePreset(id: "facebook", name: "Facebook", symbol: "person.2.fill",
                      domains: ["facebook.com", "fb.com", "fbcdn.net", "fbsbx.com", "messenger.com", "facebook.net"],
                      note: nil),
        ServicePreset(id: "x", name: "X (Twitter)", symbol: "xmark.circle.fill",
                      domains: ["x.com", "twitter.com", "twimg.com", "t.co", "twitter.co"],
                      note: nil),
        ServicePreset(id: "signal", name: "Signal", symbol: "lock.fill",
                      domains: ["signal.org", "whispersystems.org", "signal.art"],
                      note: "The desktop app uses the system proxy for HTTPS; calls use UDP."),
        ServicePreset(id: "rutube", name: "RuTube", symbol: "play.circle.fill",
                      domains: ["rutube.ru"],
                      note: nil),
    ]

    static func preset(_ id: String) -> ServicePreset? {
        all.first { $0.id == id }
    }
}

/// Which hosts bypass Tor, and how. Evaluated by the HTTP bridge for every CONNECT.
struct RoutingPolicy: Equatable, Sendable {
    var youtubeMode: RouteMode = .tor
    var serviceModes: [String: RouteMode] = [:]
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

    /// The service preset a host belongs to, if any.
    static func service(for host: String) -> ServicePreset? {
        ServiceCatalog.all.first { matches(host, domains: $0.domains) }
    }

    private func decision(for mode: RouteMode, torAvailable: Bool) -> RouteDecision {
        switch mode {
        case .tor: return torAvailable ? .tor : .direct(antiThrottle: true)
        case .directAntiThrottle: return .direct(antiThrottle: true)
        case .direct: return .direct(antiThrottle: false)
        }
    }

    /// Where a connection to `host` should go. `torAvailable` is false in YouTube Turbo mode (no Tor running).
    func decision(for host: String, torAvailable: Bool) -> RouteDecision {
        if Self.isYouTube(host) {
            return decision(for: youtubeMode, torAvailable: torAvailable)
        }
        if let service = Self.service(for: host), let mode = serviceModes[service.id] {
            return decision(for: mode, torAvailable: torAvailable)
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
            serviceModes: serviceRoutes,
            customDirectDomains: RoutingPolicy.parseDomains(customDirectDomains),
            customDirectAntiThrottle: customDirectAntiThrottle,
            strategy: dpiStrategy
        )
    }
}
