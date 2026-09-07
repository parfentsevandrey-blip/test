import SwiftUI

/// Home: the power button, the live route diagram and a grid of interactive tiles.
struct DashboardView: View {
    @Environment(AppState.self) private var app

    var openActivity: @MainActor () -> Void
    var openLocations: @MainActor () -> Void

    private let statColumns = [GridItem(.adaptive(minimum: 186, maximum: 320), spacing: 14)]

    private var showsLiveTiles: Bool {
        app.connection.isActive || app.killSwitchEngaged
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if app.isDemo {
                    DemoBanner()
                }
                if app.killSwitchEngaged {
                    KillSwitchBanner()
                }
                if let update = app.availableUpdate {
                    UpdateBanner(update: update)
                }

                HeroSection(openLocations: openLocations)

                switch app.connection {
                case .connecting:
                    BootstrapPanel()
                        .transition(.opacity)
                case .failed:
                    if let error = app.lastError {
                        ErrorCard(error: error, openActivity: openActivity)
                            .transition(.opacity)
                    }
                default:
                    EmptyView()
                }

                if app.turboActive, !app.connection.isActive {
                    TurboActiveCard()
                        .transition(.opacity)
                }

                if showsLiveTiles {
                    LazyVGrid(columns: statColumns, spacing: 14) {
                        SpeedTile(action: openActivity)
                            .frame(height: 134)
                        SessionTile()
                            .frame(height: 134)
                        LatencyTile()
                            .frame(height: 134)
                        ShieldTile()
                            .frame(height: 134)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    HStack(alignment: .top, spacing: 14) {
                        PaddingTile()
                            .frame(height: 138)
                        RoutingTile()
                            .frame(height: 138)
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                HStack(alignment: .top, spacing: 14) {
                    YouTubeTile()
                        .frame(height: 152)
                    AppsTile()
                        .frame(height: 152)
                    NetworkTile()
                        .frame(height: 152)
                }

                if app.connection == .disconnected, !app.turboActive {
                    TurboCard()
                        .transition(.opacity)
                    IdleHint()
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: 900)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 32)
            .padding(.vertical, 30)
        }
        .animation(.smooth(duration: 0.55), value: app.connection)
        .animation(.smooth(duration: 0.45), value: app.turboActive)
        .animation(.smooth(duration: 0.45), value: app.killSwitchEngaged)
    }
}

// MARK: - Hero: power button, status, chips and the live route

struct HeroSection: View {
    @Environment(AppState.self) private var app
    @Environment(\.openSettings) private var openSettings
    @Namespace private var glassNamespace

    var openLocations: @MainActor () -> Void

    var body: some View {
        GlassEffectContainer(spacing: 30) {
            VStack(spacing: 14) {
                HStack(alignment: .center, spacing: 26) {
                    PowerButton(state: app.connection, progress: app.bootstrap, size: 170) {
                        app.toggleConnection()
                    }
                    .glassEffectID("power", in: glassNamespace)

                    VStack(alignment: .leading, spacing: 8) {
                        Text(app.connection.title)
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .contentTransition(.opacity)
                        subtitle
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: 520, alignment: .leading)
                        HStack(spacing: 10) {
                            TransportChip(transport: app.settings.transport, active: app.connection.isActive ? app.activeTransport : nil)
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
                    Spacer(minLength: 0)
                }

                TunnelFlowView(
                    nodes: flowNodes,
                    mode: flowMode,
                    downloadRate: app.traffic.downloadRate,
                    uploadRate: app.traffic.uploadRate,
                    paddingRate: app.padding.status.isActive ? app.padding.rate : 0,
                    fastLane: fastLaneNode,
                    onSelect: { node in select(node) }
                )
                .frame(height: 178)
            }
        }
        .padding(.top, 6)
    }

    @ViewBuilder
    private var subtitle: some View {
        switch app.connection {
        case .disconnected:
            if app.turboActive {
                Text("YouTube Turbo is on: YouTube goes direct with anti-throttling, Tor is off.")
            } else {
                Text("Your traffic is not going through Tor. Press the button to connect.")
            }
        case .connecting:
            if let message = app.transportAttemptMessage {
                Text(verbatim: message)
            } else {
                Text(app.bootstrap.phaseTitle) + Text(verbatim: " · \(app.bootstrap.percent)%")
            }
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

    // MARK: Diagram model

    private var flowMode: FlowMode {
        if app.killSwitchEngaged { return .blocked }
        switch app.connection {
        case .connected: return .connected
        case .connecting: return .connecting(progress: Double(app.bootstrap.percent) / 100)
        case .failed: return .failed
        case .disconnected, .disconnecting: return app.turboActive ? .turbo : .idle
        }
    }

    private var flowNodes: [FlowNode] {
        var nodes: [FlowNode] = []
        nodes.append(FlowNode(
            id: "mac", kind: .mac, title: String(localized: "This Mac"), subtitle: macSubtitle,
            flag: nil, symbol: "laptopcomputer", accent: .indigo, details: macDetails
        ))
        let transport = app.activeTransport ?? app.settings.transport
        let usesBridge = transport != .direct
        if usesBridge {
            nodes.append(FlowNode(
                id: "bridge", kind: .bridge, title: String(localized: "Bridge"), subtitle: AppState.name(of: transport),
                flag: nil, symbol: transport.symbol, accent: .cyan,
                details: [
                    String(localized: "Hides the fact that you are using Tor from the network you are on."),
                    String(localized: "Click to change the transport in Settings → Bridges."),
                ]
            ))
        }
        let relays = app.circuit.filter { $0.role != .bridge }
        if relays.isEmpty {
            if !usesBridge {
                nodes.append(placeholder(id: "guard", role: .entry, location: nil))
            }
            nodes.append(placeholder(id: "middle", role: .middle, location: app.selectedMiddle))
            nodes.append(placeholder(id: "exit", role: .exit, location: app.selectedExit))
        } else {
            for hop in relays {
                var details = [hop.nickname]
                if let address = hop.address { details.append(address) }
                details.append(String(hop.fingerprint.prefix(16)) + "…")
                details.append(String(localized: "Click to choose countries on the Route screen."))
                nodes.append(FlowNode(
                    id: hop.fingerprint, kind: .relay(hop.role), title: Self.roleTitle(hop.role),
                    subtitle: hop.countryName ?? hop.nickname, flag: hop.flag.isEmpty ? nil : hop.flag,
                    symbol: "server.rack", accent: hop.role == .exit ? .mint : .blue, details: details
                ))
            }
        }
        nodes.append(FlowNode(
            id: "internet", kind: .internet, title: String(localized: "Internet"), subtitle: internetSubtitle,
            flag: nil, symbol: "globe", accent: .green, details: internetDetails
        ))
        return nodes
    }

    private func placeholder(id: String, role: CircuitHop.Role, location: ExitLocation?) -> FlowNode {
        FlowNode(
            id: id, kind: .relay(role), title: Self.roleTitle(role),
            subtitle: location?.name ?? String(localized: "Automatic"), flag: location?.flag,
            symbol: "server.rack", accent: role == .exit ? .mint : .blue,
            details: [
                String(localized: "Chosen by Tor when the circuit is built."),
                String(localized: "Click to choose countries on the Route screen."),
            ]
        )
    }

    private var fastLaneNode: FlowNode? {
        let bypass = app.turboActive || (app.connection.isConnected && app.settings.youtubeMode != .tor)
        guard bypass else { return nil }
        let subtitle: String
        if app.turboActive {
            subtitle = String(localized: "Turbo")
        } else if app.settings.youtubeMode == .directAntiThrottle {
            subtitle = String(localized: "anti-throttling")
        } else {
            subtitle = String(localized: "direct")
        }
        return FlowNode(
            id: "youtube", kind: .youtube, title: "YouTube", subtitle: subtitle, flag: nil,
            symbol: "play.rectangle.fill", accent: .red,
            details: [
                String(localized: "YouTube bypasses Tor and connects directly; with anti-throttling the TLS handshake is fragmented so DPI cannot read the host name."),
                String(localized: "Click to change in Settings → YouTube."),
            ]
        )
    }

    private var macSubtitle: String {
        switch app.proxyStatus {
        case .configured(let services): String(localized: "Proxy: \(services.joined(separator: ", "))")
        case .manual: String(localized: "Manual proxy")
        case .failed: String(localized: "Proxy not set")
        case .off: app.connection.isActive ? String(localized: "Proxy pending") : String(localized: "Proxy off")
        }
    }

    private var macDetails: [String] {
        let socks = app.socksPortForApps
        let http = app.ports?.http ?? UInt16(clamping: app.settings.httpPort)
        return [
            String(localized: "SOCKS5 127.0.0.1:\(String(socks)) · HTTP 127.0.0.1:\(String(http))"),
            String(localized: "Click to open Settings."),
        ]
    }

    private var internetSubtitle: String {
        if let check = app.torCheck { return check.ip }
        if app.connection.isConnected { return app.isCheckingTor ? String(localized: "Verifying…") : String(localized: "Not verified") }
        if let report = app.connectivity { return report.isUsable ? String(localized: "Reachable") : String(localized: "Not responding") }
        return "—"
    }

    private var internetDetails: [String] {
        var lines: [String] = []
        if let check = app.torCheck {
            lines.append(check.isTor ? String(localized: "check.torproject.org confirms Tor") : String(localized: "check.torproject.org: NOT Tor"))
            lines.append(String(localized: "Exit IP \(check.ip)"))
        }
        if let latency = app.routeLatency {
            lines.append(String(localized: "Round trip \(String(Int((latency * 1000).rounded()))) ms"))
        }
        if let report = app.connectivity {
            lines.append(report.summary)
        }
        lines.append(app.connection.isConnected ? String(localized: "Click to verify the exit.") : String(localized: "Click to check the Internet."))
        return lines
    }

    private static func roleTitle(_ role: CircuitHop.Role) -> String {
        switch role {
        case .bridge: String(localized: "Bridge")
        case .entry: String(localized: "Guard")
        case .middle: String(localized: "Middle")
        case .exit: String(localized: "Exit")
        }
    }

    private func select(_ node: FlowNode) {
        Feedback.tap(haptic: app.settings.hapticFeedback)
        switch node.kind {
        case .mac, .bridge, .youtube:
            openSettings()
        case .relay:
            openLocations()
        case .internet:
            if app.connection.isConnected {
                app.runTorCheck()
            } else {
                app.probeInternet()
            }
        }
    }
}

// MARK: - Cards

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
                Label {
                    Text("youtube.com reachable in \(result.milliseconds) ms (\(result.viaTor ? "via Tor" : "direct"))")
                    + Text(verbatim: result.kilobytesPerSecond.map { String(format: " · %.0f KB/s", $0) } ?? "")
                } icon: {
                    Image(systemName: "checkmark.seal.fill")
                }
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
            if app.networkRepair != .idle {
                NetworkRepairLabel(status: app.networkRepair, report: app.connectivity)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack {
                Button {
                    app.toggleConnection()
                } label: {
                    Label("Try Again", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.glassProminent)
                Button {
                    app.resetNetwork()
                } label: {
                    Label("Reset network & retry", systemImage: "wifi.exclamationmark")
                }
                .buttonStyle(.glass)
                .disabled(app.isResettingNetwork)
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
