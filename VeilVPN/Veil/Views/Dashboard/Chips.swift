import SwiftUI

extension AppSettings.Transport {
    var title: LocalizedStringKey {
        switch self {
        case .snowflake: "Snowflake"
        case .obfs4: "obfs4 bridges"
        case .custom: "Custom bridges"
        case .direct: "Direct (no bridges)"
        }
    }

    var details: LocalizedStringKey {
        switch self {
        case .snowflake: "Uses volunteer WebRTC proxies and domain fronting to reach Tor. Works in most censored networks and is the recommended default."
        case .obfs4: "Built-in obfs4 bridges from Tor Browser. Fast, but the addresses are public and may already be blocked."
        case .custom: "Paste bridge lines from bridges.torproject.org, the Telegram bot @GetBridgesBot, or email bridges@torproject.org. obfs4, webtunnel, snowflake, meek_lite and conjure are supported."
        case .direct: "Connect to public Tor relays without any bridge. Fastest, but it only works where Tor is not blocked."
        }
    }

    var symbol: String {
        switch self {
        case .snowflake: "snowflake"
        case .obfs4: "shuffle"
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

extension YouTubeMode {
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

struct YouTubeChip: View {
    let mode: YouTubeMode
    let turbo: Bool

    var body: some View {
        SettingsLink {
            HStack(spacing: 8) {
                Image(systemName: "play.rectangle.fill")
                    .foregroundStyle(.red)
                Text("YouTube")
                if turbo {
                    Text("Turbo")
                        .foregroundStyle(.secondary)
                } else {
                    Text(mode.title)
                        .foregroundStyle(.secondary)
                }
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
        .help("YouTube routing — configure it in Settings → YouTube")
    }
}

struct PaddingChip: View {
    let level: PaddingLevel
    let status: PaddingLoop.Status

    private var dotColor: Color {
        switch status {
        case .active: .mint
        case .preparing, .connecting: .orange
        case .failed: .red
        case .off: .secondary
        }
    }

    var body: some View {
        SettingsLink {
            HStack(spacing: 8) {
                Image(systemName: "waveform.badge.plus")
                Text("Padding")
                Text(level.title)
                    .foregroundStyle(.secondary)
                Circle()
                    .fill(dotColor)
                    .frame(width: 7, height: 7)
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
        .help("Traffic padding is on — configure it in Settings → Privacy")
    }
}

struct TransportChip: View {
    let transport: AppSettings.Transport

    var body: some View {
        SettingsLink {
            Label(transport.title, systemImage: transport.symbol)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
        .help("Change the transport in Settings → Bridges")
    }
}

struct ExitChip: View {
    let location: ExitLocation?
    let exitHop: CircuitHop?
    var multihop: Bool = false
    var middle: ExitLocation? = nil
    var middleHop: CircuitHop? = nil
    let action: @MainActor () -> Void

    private var middleFlag: String? {
        if let middleHop, !middleHop.flag.isEmpty { return middleHop.flag }
        return middle?.flag
    }

    private var exitFlag: String? {
        if let location { return location.flag }
        if let exitHop, !exitHop.flag.isEmpty { return exitHop.flag }
        return nil
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if multihop {
                    Image(systemName: "point.3.connected.trianglepath.dotted")
                    Text("Multihop")
                    Text(verbatim: "\(middleFlag ?? "🌐") → \(exitFlag ?? "🌐")")
                } else if let location {
                    Text(location.flag)
                    Text(verbatim: location.name)
                } else if let exitHop, !exitHop.flag.isEmpty {
                    Text(exitHop.flag)
                    Text("Auto · \(exitHop.countryName ?? exitHop.nickname)")
                } else {
                    Image(systemName: "globe")
                    Text("Automatic exit")
                }
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
        .help("Choose the exit country")
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
