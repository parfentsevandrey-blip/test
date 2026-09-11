import SwiftUI

extension AppSettings.Transport {
    var title: LocalizedStringKey {
        switch self {
        case .auto: "Automatic (recommended)"
        case .snowflake: "Snowflake"
        case .obfs4: "obfs4 bridges"
        case .meek: "meek (domain fronting)"
        case .custom: "Custom bridges"
        case .direct: "Direct (no bridges)"
        }
    }

    var details: LocalizedStringKey {
        switch self {
        case .auto: "Checks whether Tor is reachable directly, then tries the last transport that worked, your custom bridges, Snowflake, obfs4 and meek in turn until one bootstraps."
        case .meek: "Tunnels through a large CDN with domain fronting. Slow but very hard to block; the built-in bridge comes from Tor Browser."
        case .snowflake: "Uses volunteer WebRTC proxies and domain fronting to reach Tor. Works in most censored networks."
        case .obfs4: "Built-in obfs4 bridges from Tor Browser. Fast, but the addresses are public and may already be blocked."
        case .custom: "Paste bridge lines from bridges.torproject.org, the Telegram bot @GetBridgesBot, or email bridges@torproject.org. obfs4, webtunnel, snowflake, meek_lite and conjure are supported."
        case .direct: "Connect to public Tor relays without any bridge. Fastest, but it only works where Tor is not blocked."
        }
    }

    var symbol: String {
        switch self {
        case .auto: "wand.and.stars"
        case .snowflake: "snowflake"
        case .obfs4: "shuffle"
        case .meek: "cloud.fill"
        case .custom: "doc.text"
        case .direct: "bolt.fill"
        }
    }
}

extension PaddingLevel {
    var title: LocalizedStringKey {
        switch self {
        case .light: "Light"
        case .balanced: "Balanced"
        case .strong: "Strong"
        }
    }

    var details: LocalizedStringKey {
        switch self {
        case .light: "Sporadic background noise only. About 1–2 KB/s of extra traffic."
        case .balanced: "Background noise plus a front-loaded burst of dummy traffic whenever real activity starts, so the shape of page loads is hidden (FRONT defence). Roughly 5–30 KB/s while you browse."
        case .strong: "Everything in Balanced, and both directions are topped up to a constant rate (≈10 KB/s up, ≈24 KB/s down) so traffic volume reveals nothing. Uses the most bandwidth."
        }
    }
}

extension RouteMode {
    var title: LocalizedStringKey {
        switch self {
        case .tor: "Through Tor"
        case .directAntiThrottle: "Direct, anti-throttling"
        case .direct: "Direct"
        }
    }

    var details: LocalizedStringKey {
        switch self {
        case .tor: "YouTube goes through the Tor circuit like everything else. Anonymous, but slow for video and YouTube often demands a sign-in to prove you are not a bot."
        case .directAntiThrottle: "YouTube connects directly at full speed, but Veil fragments the TLS handshake so throttling DPI cannot see the host name. YouTube sees your real IP address."
        case .direct: "YouTube connects directly with no tricks. Use this where YouTube is not throttled."
        }
    }
}

extension DPIStrategy {
    var title: LocalizedStringKey {
        switch self {
        case .recordAndSegmentAtSNI: "TLS record + TCP split at SNI (recommended)"
        case .segmentAtSNI: "TCP split at SNI"
        case .recordAtSNI: "TLS record split at SNI"
        case .firstByte: "TCP split after the first byte"
        }
    }
}

struct DemoBanner: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "sparkles")
                .foregroundStyle(.yellow)
            VStack(alignment: .leading, spacing: 2) {
                Text("Demo mode")
                    .font(.subheadline.weight(.semibold))
                Text("No tor binaries were found next to the app, so the connection is simulated. Build the DMG with `make dmg` or run `make tor` to bundle Tor.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .glassEffect(.regular.tint(.yellow.opacity(0.15)), in: .rect(cornerRadius: 16))
    }
}
