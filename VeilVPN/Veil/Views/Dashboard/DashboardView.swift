import SwiftUI

struct DashboardView: View {
    @Environment(AppState.self) private var app
    @Namespace private var glassNamespace

    var openActivity: @MainActor () -> Void
    var openLocations: @MainActor () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                if app.isDemo {
                    DemoBanner()
                }

                GlassEffectContainer(spacing: 40) {
                    VStack(spacing: 22) {
                        PowerButton(state: app.connection, progress: app.bootstrap) {
                            app.toggleConnection()
                        }
                        .glassEffectID("power", in: glassNamespace)

                        StatusHeadline()

                        HStack(spacing: 12) {
                            TransportChip(transport: app.settings.transport)
                                .glassEffectID("transport", in: glassNamespace)
                            ExitChip(location: app.selectedExit, exitHop: app.exitHop, action: openLocations)
                                .glassEffectID("exit", in: glassNamespace)
                        }
                    }
                    .padding(.top, 12)
                }

                switch app.connection {
                case .connected:
                    ConnectedPanel(openActivity: openActivity)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                case .connecting:
                    BootstrapPanel()
                        .transition(.opacity)
                case .failed:
                    if let error = app.lastError {
                        ErrorCard(error: error, openActivity: openActivity)
                            .transition(.opacity)
                    }
                case .disconnected, .disconnecting:
                    IdleHint()
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: 780)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 32)
            .padding(.vertical, 36)
        }
        .animation(.smooth(duration: 0.55), value: app.connection)
    }
}

struct StatusHeadline: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(spacing: 6) {
            Text(app.connection.title)
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .contentTransition(.opacity)
            subtitle
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)
        }
    }

    @ViewBuilder
    private var subtitle: some View {
        switch app.connection {
        case .disconnected:
            Text("Your traffic is not going through Tor.")
        case .connecting:
            Text(app.bootstrap.phaseTitle) + Text(verbatim: " · \(app.bootstrap.percent)%")
        case .connected:
            if let exit = app.exitHop {
                Text("Exit relay: \(exit.flag) \(exit.countryName ?? exit.nickname)")
            } else {
                Text("System traffic is routed through the Tor network.")
            }
        case .disconnecting:
            Text("Restoring network settings…")
        case .failed:
            Text("See the details below or open Activity for the full log.")
        }
    }
}

struct IdleHint: View {
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            HintColumn(symbol: "snowflake", title: "Snowflake bridge", text: "Reaches Tor through volunteer proxies, even where Tor is blocked.")
            HintColumn(symbol: "point.3.connected.trianglepath.dotted", title: "Three-hop circuit", text: "Traffic is encrypted and relayed through three Tor relays.")
            HintColumn(symbol: "network", title: "System proxy", text: "Safari, browsers and most apps are switched to Tor automatically.")
        }
    }
}

private struct HintColumn: View {
    let symbol: String
    let title: LocalizedStringKey
    let text: LocalizedStringKey

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(.tint)
            Text(title)
                .font(.subheadline.weight(.semibold))
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }
}

struct ErrorCard: View {
    @Environment(AppState.self) private var app
    let error: AppError
    var openActivity: @MainActor () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(error.title, systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
                .foregroundStyle(.red)
            Text(error.message)
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
            HStack {
                Button {
                    app.toggleConnection()
                } label: {
                    Label("Try Again", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.glassProminent)
                Button {
                    openActivity()
                } label: {
                    Label("Open Log", systemImage: "text.alignleft")
                }
                .buttonStyle(.glass)
                Spacer()
                Button("Dismiss") {
                    app.dismissError()
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular.tint(.red.opacity(0.18)), in: .rect(cornerRadius: 22))
    }
}
