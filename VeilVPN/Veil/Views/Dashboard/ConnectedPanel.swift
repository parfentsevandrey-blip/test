import SwiftUI

struct ConnectedPanel: View {
    @Environment(AppState.self) private var app
    var openActivity: @MainActor () -> Void

    var body: some View {
        VStack(spacing: 18) {
            GlassEffectContainer(spacing: 16) {
                HStack(spacing: 14) {
                    StatTile(title: "Download", symbol: "arrow.down.circle.fill", value: ByteFormat.rate(app.traffic.downloadRate), tint: .cyan)
                    StatTile(title: "Upload", symbol: "arrow.up.circle.fill", value: ByteFormat.rate(app.traffic.uploadRate), tint: .orange)
                    DurationTile(since: app.connectedAt)
                    StatTile(title: "Exit IP", symbol: "network", value: app.torCheck?.ip ?? "—", tint: .mint)
                }
            }

            CircuitStrip(hops: app.circuit)

            if app.settings.paddingEnabled {
                PaddingStatusRow(loop: app.padding)
            }

            HStack(spacing: 12) {
                Button {
                    app.requestNewIdentity()
                } label: {
                    Label("New Identity", systemImage: "arrow.triangle.2.circlepath")
                }
                .buttonStyle(.glass)
                .disabled(app.isChangingIdentity)

                Button {
                    app.runTorCheck()
                } label: {
                    Label("Check Tor", systemImage: "checkmark.shield")
                }
                .buttonStyle(.glass)
                .disabled(app.isCheckingTor)

                Button {
                    openActivity()
                } label: {
                    Label("Activity", systemImage: "waveform.path.ecg")
                }
                .buttonStyle(.glass)

                Spacer()
                ProxyStatusLabel(status: app.proxyStatus)
            }

            TorCheckBadge(result: app.torCheck, inProgress: app.isCheckingTor)
        }
    }
}

struct StatTile: View {
    let title: LocalizedStringKey
    let symbol: String
    let value: String
    var tint: Color = .accentColor

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: symbol)
                .font(.caption)
                .foregroundStyle(.secondary)
                .symbolRenderingMode(.hierarchical)
            Text(value)
                .font(.system(.title3, design: .rounded, weight: .semibold))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .contentTransition(.numericText())
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }
}

struct DurationTile: View {
    let since: Date?

    var body: some View {
        TimelineView(.periodic(from: since ?? .now, by: 1)) { context in
            let seconds = Int(max(0, context.date.timeIntervalSince(since ?? context.date)))
            StatTile(
                title: "Duration",
                symbol: "clock.fill",
                value: Duration.seconds(seconds).formatted(.time(pattern: .hourMinuteSecond)),
                tint: .purple
            )
        }
    }
}

struct CircuitStrip: View {
    let hops: [CircuitHop]

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "laptopcomputer")
                    .foregroundStyle(.secondary)
                    .padding(.trailing, 4)
                if hops.isEmpty {
                    Label("Building a circuit…", systemImage: "point.3.connected.trianglepath.dotted")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .glassEffect(.regular, in: .capsule)
                } else {
                    ForEach(Array(hops.enumerated()), id: \.element.id) { index, hop in
                        if index > 0 {
                            Image(systemName: "chevron.right")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.tertiary)
                        }
                        HopChip(hop: hop)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .animation(.smooth, value: hops)
    }
}

struct HopChip: View {
    let hop: CircuitHop

    private var roleTitle: LocalizedStringKey {
        switch hop.role {
        case .bridge: "Snowflake bridge"
        case .entry: "Guard"
        case .middle: "Middle"
        case .exit: "Exit"
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            if hop.role == .bridge {
                Image(systemName: "snowflake")
                    .foregroundStyle(.cyan)
            } else if !hop.flag.isEmpty {
                Text(hop.flag)
            } else {
                Image(systemName: "server.rack")
                    .foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(roleTitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(verbatim: hop.role == .bridge ? "snowflake" : (hop.countryName ?? hop.nickname))
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .glassEffect(.regular.tint(hop.role == .exit ? Color.mint.opacity(0.25) : nil), in: .capsule)
        .help(Text(verbatim: "\(hop.nickname) \(hop.address ?? "") \(hop.fingerprint)"))
    }
}

struct PaddingStatusRow: View {
    let loop: PaddingLoop

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "waveform.badge.plus")
                .foregroundStyle(loop.status.isActive ? AnyShapeStyle(.mint) : AnyShapeStyle(.secondary))
                .symbolEffect(.variableColor.iterative, isActive: loop.status.isActive)
            VStack(alignment: .leading, spacing: 2) {
                Text("Traffic padding")
                    .font(.subheadline.weight(.semibold))
                statusText
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if loop.status.isActive {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(verbatim: ByteFormat.rate(loop.rate))
                        .font(.subheadline.weight(.semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Text("\(Int((loop.overheadShare * 100).rounded()))% of traffic")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .glassEffect(.regular.tint(loop.status.isActive ? Color.mint.opacity(0.18) : nil), in: .rect(cornerRadius: 18))
    }

    @ViewBuilder
    private var statusText: some View {
        switch loop.status {
        case .off:
            Text("Off")
        case .preparing:
            Text("Creating a private onion service…")
        case .publishing:
            Text("Publishing the onion service through Tor — usually 1–3 minutes via Snowflake…")
        case .connecting(let attempt):
            Text("Connecting to the loop through Tor (attempt \(attempt))…")
        case .active:
            Text(loop.level.title) + Text(verbatim: " · ") + Text("dummy traffic bounces through Tor back to this Mac")
        case .failed(let message):
            Text(verbatim: message)
        }
    }
}

struct ProxyStatusLabel: View {
    let status: ProxyStatus

    var body: some View {
        switch status {
        case .off:
            Label("System proxy off", systemImage: "circle.dashed")
                .foregroundStyle(.secondary)
        case .configured(let services):
            Label("System proxy: \(services.joined(separator: ", "))", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.mint)
        case .manual:
            Label("Manual proxy mode", systemImage: "hand.raised.fill")
                .foregroundStyle(.secondary)
        case .failed(let message):
            Label("System proxy not set", systemImage: "exclamationmark.circle.fill")
                .foregroundStyle(.orange)
                .help(Text(verbatim: message))
        }
    }
}

struct TorCheckBadge: View {
    let result: TorCheckResult?
    let inProgress: Bool

    var body: some View {
        HStack(spacing: 8) {
            if inProgress {
                ProgressView()
                    .controlSize(.small)
                Text("Verifying with check.torproject.org…")
            } else if let result {
                if result.isTor {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(.mint)
                    Text("check.torproject.org confirms Tor · exit IP \(result.ip)")
                } else {
                    Image(systemName: "xmark.seal.fill").foregroundStyle(.red)
                    Text("check.torproject.org says this is NOT Tor · IP \(result.ip)")
                }
            } else {
                Image(systemName: "questionmark.circle").foregroundStyle(.secondary)
                Text("Tor exit not verified yet")
            }
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
