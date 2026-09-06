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
                            ExitChip(
                                location: app.selectedExit,
                                exitHop: app.exitHop,
                                multihop: app.settings.multihopEnabled,
                                middle: app.selectedMiddle,
                                middleHop: app.middleHop,
                                action: openLocations
                            )
                            .glassEffectID("exit", in: glassNamespace)
                            if app.settings.paddingEnabled {
                                PaddingChip(level: app.settings.paddingLevel, status: app.padding.status)
                                    .glassEffectID("padding", in: glassNamespace)
                            }
                            if app.turboActive || app.settings.youtubeMode != .tor {
                                YouTubeChip(mode: app.settings.youtubeMode, turbo: app.turboActive)
                                    .glassEffectID("youtube", in: glassNamespace)
                            }
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
                    if app.turboActive {
                        TurboActiveCard()
                            .transition(.opacity)
                    } else {
                        TurboCard()
                            .transition(.opacity)
                        IdleHint()
                            .transition(.opacity)
                    }
                }
            }
            .frame(maxWidth: 780)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 32)
            .padding(.vertical, 36)
        }
        .animation(.smooth(duration: 0.55), value: app.connection)
        .animation(.smooth(duration: 0.45), value: app.turboActive)
    }
}

/// Offer to speed up YouTube without Tor while disconnected.
struct TurboCard: View {
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "play.rectangle.fill")
                .font(.system(size: 30))
                .foregroundStyle(.red)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 6) {
                Text("YouTube Turbo")
                    .font(.headline)
                Text("Speeds up throttled YouTube without Tor. Only YouTube traffic goes through Veil’s anti-throttling proxy, which fragments the TLS handshake so DPI throttling cannot recognise it; everything else is untouched and your IP address stays visible.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            Button {
                app.startTurbo()
            } label: {
                Label("Enable", systemImage: "bolt.fill")
            }
            .buttonStyle(.glassProminent)
            .tint(.red)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}

struct TurboActiveCard: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "bolt.fill")
                    .font(.title2)
                    .foregroundStyle(.red)
                    .symbolEffect(.pulse, isActive: true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("YouTube Turbo is active")
                        .font(.headline)
                    Text("YouTube is fetched directly with a fragmented TLS handshake; Tor is off and other sites are untouched.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                Button {
                    app.stopTurbo()
                } label: {
                    Label("Stop", systemImage: "stop.fill")
                }
                .buttonStyle(.glass)
            }
            HStack(spacing: 12) {
                Button {
                    app.testYouTube()
                } label: {
                    Label("Test YouTube", systemImage: "checkmark.circle")
                }
                .buttonStyle(.glass)
                .disabled(app.isTestingYouTube)
                YouTubeTestLabel(result: app.youtubeTest, inProgress: app.isTestingYouTube)
                Spacer()
                ProxyStatusLabel(status: app.proxyStatus)
            }
            .font(.caption)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular.tint(.red.opacity(0.14)), in: .rect(cornerRadius: 22))
    }
}

struct YouTubeTestLabel: View {
    let result: YouTubeTestResult?
    let inProgress: Bool

    var body: some View {
        if inProgress {
            HStack(spacing: 6) {
                ProgressView()
                    .controlSize(.small)
                Text("Checking youtube.com…")
            }
            .foregroundStyle(.secondary)
        } else if let result {
            if result.success {
                Label("youtube.com reachable in \(result.milliseconds) ms (\(result.viaTor ? "via Tor" : "direct"))", systemImage: "checkmark.seal.fill")
                    .foregroundStyle(.mint)
            } else {
                Label("youtube.com failed: \(result.detail)", systemImage: "xmark.seal.fill")
                    .foregroundStyle(.red)
                    .lineLimit(2)
            }
        } else {
            Text("Not checked yet")
                .foregroundStyle(.secondary)
        }
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
