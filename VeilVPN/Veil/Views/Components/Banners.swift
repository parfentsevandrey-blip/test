import SwiftUI

struct KillSwitchBanner: View {
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "hand.raised.fill")
                .font(.title2)
                .foregroundStyle(.red)
            VStack(alignment: .leading, spacing: 4) {
                Text("Kill switch engaged")
                    .font(.headline)
                Text("Tor is down, so apps that use the system proxy are blocked instead of leaking. Reconnect to resume, or disconnect to restore normal networking.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                if app.reconnectPending {
                    Label("Reconnecting automatically…", systemImage: "arrow.clockwise")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            VStack(spacing: 8) {
                Button {
                    app.connect()
                } label: {
                    Label("Reconnect", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.glassProminent)
                Button {
                    app.disconnect()
                } label: {
                    Label("Restore network", systemImage: "network")
                }
                .buttonStyle(.glass)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular.tint(.red.opacity(0.2)), in: .rect(cornerRadius: 22))
    }
}

struct UpdateBanner: View {
    @Environment(AppState.self) private var app
    let update: UpdateInfo

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.title2)
                .foregroundStyle(.blue)
            VStack(alignment: .leading, spacing: 2) {
                Text("Veil \(update.version) is available")
                    .font(.headline)
                Text("Download the new DMG, replace Veil in Applications and relaunch.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button {
                app.openAvailableUpdate()
            } label: {
                Label("Download", systemImage: "arrow.down.to.line")
            }
            .buttonStyle(.glassProminent)
            Button("Skip") {
                app.skipAvailableUpdate()
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
        .padding(16)
        .glassEffect(.regular.tint(.blue.opacity(0.16)), in: .rect(cornerRadius: 20))
    }
}

/// Four small tiles with the bridge's routing counters.
struct RoutingStatsRow: View {
    let stats: HTTPProxyBridge.Stats

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                StatTile(title: "Via Tor", symbol: "point.3.connected.trianglepath.dotted", value: "\(stats.tor)", tint: .mint)
                StatTile(title: "Direct", symbol: "arrow.right.circle.fill", value: "\(stats.direct)", tint: .orange)
                StatTile(title: "Anti-throttling", symbol: "bolt.fill", value: "\(stats.antiThrottle)", tint: .red)
                StatTile(title: "Blocked", symbol: "hand.raised.fill", value: "\(stats.blocked)", tint: .secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("Connections: \(stats.tor) via Tor, \(stats.direct) direct, \(stats.antiThrottle) with anti-throttling, \(stats.blocked) blocked"))
    }
}
