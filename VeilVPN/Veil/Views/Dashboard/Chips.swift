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
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let location {
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
