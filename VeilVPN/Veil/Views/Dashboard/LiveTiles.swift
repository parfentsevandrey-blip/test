import Charts
import SwiftUI

// MARK: - Tile chrome

/// A quiet glass tile with a hairline edge that lifts a little on hover; optionally the whole tile acts as a button.
struct Tile<Content: View>: View {
    private let tint: Color?
    private let action: (@MainActor () -> Void)?
    private let content: Content
    @State private var hovering = false

    init(tint: Color? = nil, action: (@MainActor () -> Void)? = nil, @ViewBuilder content: () -> Content) {
        self.tint = tint
        self.action = action
        self.content = content()
    }

    private var lifts: Bool { hovering && action != nil }

    var body: some View {
        content
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(.rect(cornerRadius: 24))
            .glassEffect(.regular.tint(tint?.opacity(0.07)).interactive(action != nil), in: .rect(cornerRadius: 24))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Color.primary.opacity(lifts ? 0.10 : 0.06), lineWidth: 0.6)
            }
            .scaleEffect(lifts ? 1.01 : 1)
            .shadow(color: .black.opacity(lifts ? 0.10 : 0), radius: 18, y: 8)
            .onHover { hovering = $0 }
            .animation(.snappy(duration: 0.25), value: hovering)
            .onTapGesture {
                action?()
            }
    }
}

/// Small-caps style caption with a coloured symbol.
struct TileHeader: View {
    let title: LocalizedStringKey
    let symbol: String
    var tint: Color = .secondary

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.caption.weight(.semibold))
                .foregroundStyle(tint)
                .symbolRenderingMode(.hierarchical)
            Text(title)
                .font(.caption2.weight(.semibold))
                .textCase(.uppercase)
                .kerning(0.7)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

/// The big number of a tile.
private struct Figure: View {
    let text: String
    var tint: Color = .primary

    var body: some View {
        Text(verbatim: text)
            .font(.system(.title3, design: .rounded, weight: .semibold))
            .monospacedDigit()
            .contentTransition(.numericText())
            .foregroundStyle(tint)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
    }
}

// MARK: - Throughput

struct SpeedTile: View {
    @Environment(AppState.self) private var app
    let action: @MainActor () -> Void

    var body: some View {
        let traffic = app.traffic
        Tile(tint: .cyan, action: action) {
            VStack(alignment: .leading, spacing: 8) {
                TileHeader(title: "Throughput", symbol: "waveform.path.ecg", tint: .cyan)
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    RateReadout(symbol: "arrow.down", value: traffic.downloadRate, tint: .cyan)
                    RateReadout(symbol: "arrow.up", value: traffic.uploadRate, tint: .orange)
                    Spacer(minLength: 0)
                }
                ThroughputSparkline(samples: Array(traffic.samples.suffix(60)))
                    .frame(maxWidth: .infinity)
                    .frame(height: 30)
                Text("Total \(ByteFormat.total(traffic.totalDownload)) down · \(ByteFormat.total(traffic.totalUpload)) up")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }
}

private struct RateReadout: View {
    let symbol: String
    let value: Double
    let tint: Color

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Image(systemName: symbol)
                .font(.caption2.weight(.bold))
                .foregroundStyle(tint)
            Text(verbatim: ByteFormat.rate(value))
                .font(.system(.body, design: .rounded, weight: .semibold))
                .monospacedDigit()
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .animation(.snappy(duration: 0.3), value: value)
    }
}

/// Download and upload as two hairline traces with a faint fill.
struct ThroughputSparkline: View {
    let samples: [TrafficSample]

    var body: some View {
        Canvas { context, size in
            guard samples.count > 1 else { return }
            let peak = max(samples.map { max($0.download, $0.upload) }.max() ?? 1, 1)
            func trace(_ key: KeyPath<TrafficSample, Double>) -> Path {
                var path = Path()
                for (index, sample) in samples.enumerated() {
                    let x = size.width * CGFloat(index) / CGFloat(samples.count - 1)
                    let y = size.height - 2 - (size.height - 4) * CGFloat(sample[keyPath: key] / peak)
                    if index == 0 {
                        path.move(to: CGPoint(x: x, y: y))
                    } else {
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                }
                return path
            }
            let series: [(KeyPath<TrafficSample, Double>, Color)] = [(\TrafficSample.download, .cyan), (\TrafficSample.upload, .orange)]
            for (key, color) in series {
                let line = trace(key)
                var area = line
                area.addLine(to: CGPoint(x: size.width, y: size.height))
                area.addLine(to: CGPoint(x: 0, y: size.height))
                area.closeSubpath()
                context.fill(
                    area,
                    with: .linearGradient(Gradient(colors: [color.opacity(0.22), color.opacity(0)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height))
                )
                context.stroke(line, with: .color(color.opacity(0.9)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
            }
        }
    }
}

// MARK: - Session

struct SessionTile: View {
    @Environment(AppState.self) private var app

    var body: some View {
        let since = app.connectedAt
        Tile(tint: .purple, action: { app.requestNewIdentity() }) {
            VStack(alignment: .leading, spacing: 8) {
                TileHeader(title: "Session", symbol: "clock", tint: .purple)
                TimelineView(.periodic(from: since ?? .now, by: 1)) { context in
                    let seconds = Int(max(0, context.date.timeIntervalSince(since ?? context.date)))
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .stroke(Color.purple.opacity(0.14), lineWidth: 3)
                            Circle()
                                .trim(from: 0, to: Double(seconds % 60) / 60)
                                .stroke(Color.purple.opacity(0.8), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .animation(.linear(duration: 1), value: seconds)
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.purple)
                                .symbolEffect(.pulse, isActive: app.isChangingIdentity)
                        }
                        .frame(width: 42, height: 42)
                        VStack(alignment: .leading, spacing: 3) {
                            Figure(text: Duration.seconds(seconds).formatted(.time(pattern: .hourMinuteSecond)))
                            Text("\(app.circuit.count) hops · click for a new identity")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Latency

struct LatencyTile: View {
    @Environment(AppState.self) private var app

    private var fraction: Double { min(1, (app.routeLatency ?? 0) / 2) }

    private var gaugeColor: Color {
        guard let latency = app.routeLatency else { return .secondary }
        if latency < 0.6 { return .mint }
        if latency < 1.2 { return .orange }
        return .red
    }

    var body: some View {
        Tile(tint: .mint, action: { app.runTorCheck() }) {
            VStack(alignment: .leading, spacing: 8) {
                TileHeader(title: "Route latency", symbol: "timer", tint: .mint)
                HStack(spacing: 14) {
                    ArcGauge(fraction: fraction, tint: gaugeColor)
                        .frame(width: 64, height: 36)
                    VStack(alignment: .leading, spacing: 3) {
                        if app.isCheckingTor {
                            Figure(text: String(localized: "Checking…"))
                        } else if let latency = app.routeLatency {
                            Figure(text: "\(Int((latency * 1000).rounded())) ms")
                        } else {
                            Figure(text: String(localized: "Not measured"))
                        }
                        Text(verbatim: app.torCheck?.ip ?? "—")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                            .lineLimit(1)
                    }
                }
                latencyFootnote
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
    }

    private static func milliseconds(_ seconds: TimeInterval) -> String {
        "\(Int((seconds * 1000).rounded())) ms"
    }

    @ViewBuilder
    private var latencyFootnote: some View {
        if app.tuner.status.isRacing {
            HStack(spacing: 6) {
                ProgressView().controlSize(.mini)
                Text("Racing circuits to find the fastest relays…")
            }
        } else if app.tuner.isPinned, let before = app.routeLatencyBeforeTuning, let after = app.routeLatency {
            Text("Tuned: \(Self.milliseconds(before)) → \(Self.milliseconds(after)) · \(app.tuner.pinnedExits.count) fast exits pinned")
        } else if app.tuner.isPinned {
            Text("Fastest relays pinned · click to verify the exit")
        } else {
            Text("Click to verify the exit with check.torproject.org")
        }
    }
}

/// A half-ring scale with a small knob marking the value.
struct ArcGauge: View {
    let fraction: Double
    let tint: Color

    var body: some View {
        GeometryReader { geometry in
            let center = CGPoint(x: geometry.size.width / 2, y: geometry.size.height - 3)
            let radius = min(geometry.size.width / 2, geometry.size.height) - 5
            let angle = (180 + 180 * min(1, max(0, fraction))) * Double.pi / 180
            ZStack {
                GaugeArc(fraction: 1)
                    .stroke(
                        AngularGradient(colors: [.mint, .yellow, .orange, .red], center: .bottom, startAngle: .degrees(180), endAngle: .degrees(360)),
                        style: StrokeStyle(lineWidth: 3.5, lineCap: .round)
                    )
                    .opacity(0.32)
                GaugeArc(fraction: max(0.01, fraction))
                    .stroke(tint.opacity(0.9), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                Circle()
                    .fill(tint)
                    .frame(width: 8, height: 8)
                    .overlay { Circle().strokeBorder(.white.opacity(0.9), lineWidth: 1.5) }
                    .shadow(color: tint.opacity(0.5), radius: 4)
                    .position(x: center.x + radius * CGFloat(cos(angle)), y: center.y + radius * CGFloat(sin(angle)))
            }
            .animation(.spring(duration: 0.8), value: fraction)
        }
    }
}

struct GaugeArc: Shape {
    var fraction: Double

    var animatableData: Double {
        get { fraction }
        set { fraction = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.maxY - 3)
        let radius = min(rect.width / 2, rect.height) - 5
        path.addArc(center: center, radius: radius, startAngle: .degrees(180), endAngle: .degrees(180 + 180 * fraction), clockwise: false)
        return path
    }
}

// MARK: - Protection

struct ShieldTile: View {
    @Environment(AppState.self) private var app

    private var proxyServices: [String] {
        if case .configured(let services) = app.proxyStatus { return services }
        return []
    }

    private var proxyText: String {
        switch app.proxyStatus {
        case .off: String(localized: "off")
        case .configured: String(localized: "on")
        case .manual: String(localized: "manual")
        case .failed: String(localized: "not set")
        }
    }

    var body: some View {
        let engaged = app.killSwitchEngaged
        let tint: Color = engaged ? .red : .indigo
        Tile(tint: tint) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TileHeader(title: "Protection", symbol: engaged ? "hand.raised.fill" : "shield.lefthalf.filled", tint: tint)
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { app.settings.killSwitch },
                        set: { app.setKillSwitch($0) }
                    ))
                    .labelsHidden()
                    .toggleStyle(.switch)
                    .controlSize(.mini)
                }
                statusRow("Kill switch", value: engaged ? String(localized: "engaged") : (app.settings.killSwitch ? String(localized: "armed") : String(localized: "off")), ok: app.settings.killSwitch, alert: engaged)
                statusRow("System proxy", value: proxyText, ok: !proxyServices.isEmpty, alert: false)
                    .help(Text(verbatim: proxyServices.joined(separator: ", ")))
                statusRow("Circuit per site", value: app.settings.isolatePerSite ? String(localized: "on") : String(localized: "off"), ok: app.settings.isolatePerSite, alert: false)
            }
        }
    }

    private func statusRow(_ title: LocalizedStringKey, value: String, ok: Bool, alert: Bool) -> some View {
        HStack(spacing: 7) {
            Circle()
                .fill(alert ? Color.red : (ok ? Color.mint : Color.secondary.opacity(0.35)))
                .frame(width: 5, height: 5)
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Spacer(minLength: 6)
            Text(verbatim: value)
                .font(.caption2.weight(.medium))
                .lineLimit(1)
        }
    }
}

// MARK: - Padding

struct PaddingTile: View {
    @Environment(AppState.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let enabled = app.settings.paddingEnabled
        let active = app.padding.status.isActive
        let activity: Double = active ? min(1, 0.25 + app.padding.rate / 24_000) : (enabled ? 0.1 : 0.04)
        Tile(tint: .purple, action: { app.setPaddingEnabled(!enabled) }) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TileHeader(title: "Traffic padding", symbol: "waveform", tint: .purple)
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { app.settings.paddingEnabled },
                        set: { app.setPaddingEnabled($0) }
                    ))
                    .labelsHidden()
                    .toggleStyle(.switch)
                    .controlSize(.mini)
                }
                NoiseWave(activity: activity, tint: .purple, paused: reduceMotion || !active)
                    .frame(height: 40)
                HStack {
                    statusText
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    if active {
                        Text(verbatim: ByteFormat.rate(app.padding.rate))
                            .font(.caption2.weight(.semibold))
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var statusText: some View {
        switch app.padding.status {
        case .off:
            if app.settings.paddingEnabled {
                Text("Starts with the connection")
            } else {
                Text("Off · click to enable")
            }
        case .preparing:
            Text("Creating the onion loop…")
        case .publishing:
            Text("Publishing the onion service…")
        case .connecting(let attempt):
            Text("Connecting to the loop (attempt \(attempt))…")
        case .active:
            Text(app.settings.paddingLevel.title)
        case .failed(let message):
            Text(verbatim: message)
        }
    }
}

/// A slow, layered wave whose amplitude follows the padding rate.
struct NoiseWave: View {
    let activity: Double
    let tint: Color
    let paused: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 24.0, paused: paused)) { context in
            Canvas { graphics, size in
                let t = context.date.timeIntervalSinceReferenceDate
                let midY = size.height * 0.55
                let amplitude = size.height * 0.4 * CGFloat(0.12 + 0.88 * min(1, max(0, activity)))
                let steps = max(24, Int(size.width / 2))
                var line = Path()
                for step in 0...steps {
                    let x = size.width * CGFloat(step) / CGFloat(steps)
                    let u = Double(x / size.width)
                    let wave = 0.55 * sin(u * 9.4 - t * 1.1) + 0.3 * sin(u * 17.3 + t * 0.7) + 0.15 * sin(u * 31 - t * 1.9)
                    let y = midY - amplitude * CGFloat(wave)
                    if step == 0 {
                        line.move(to: CGPoint(x: x, y: y))
                    } else {
                        line.addLine(to: CGPoint(x: x, y: y))
                    }
                }
                var area = line
                area.addLine(to: CGPoint(x: size.width, y: size.height))
                area.addLine(to: CGPoint(x: 0, y: size.height))
                area.closeSubpath()
                graphics.fill(area, with: .linearGradient(Gradient(colors: [tint.opacity(0.16), tint.opacity(0)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
                graphics.stroke(line, with: .color(tint.opacity(0.7)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
            }
        }
    }
}

// MARK: - Routing

struct RoutingSlice: Identifiable {
    let id: String
    let title: LocalizedStringKey
    let count: Int
    let color: Color
}

struct RoutingTile: View {
    @Environment(AppState.self) private var app
    @Environment(\.openSettings) private var openSettings

    private var slices: [RoutingSlice] {
        let stats = app.bridgeStats
        return [
            RoutingSlice(id: "tor", title: "Via Tor", count: stats.tor, color: .mint),
            RoutingSlice(id: "direct", title: "Direct", count: stats.direct, color: .orange),
            RoutingSlice(id: "anti", title: "Anti-throttling", count: stats.antiThrottle, color: .red),
            RoutingSlice(id: "blocked", title: "Blocked", count: stats.blocked, color: .gray),
        ]
    }

    var body: some View {
        let slices = self.slices
        let total = slices.reduce(0) { $0 + $1.count }
        let chartData = total == 0
            ? [RoutingSlice(id: "none", title: "None", count: 1, color: Color.secondary.opacity(0.2))]
            : slices.filter { $0.count > 0 }
        Tile(tint: .orange, action: { openSettings() }) {
            VStack(alignment: .leading, spacing: 8) {
                TileHeader(title: "Routing", symbol: "arrow.triangle.branch", tint: .orange)
                HStack(spacing: 16) {
                    Chart(chartData) { slice in
                        SectorMark(angle: .value("Connections", slice.count), innerRadius: .ratio(0.74), angularInset: 1.2)
                            .foregroundStyle(slice.color.opacity(0.85))
                            .cornerRadius(2)
                    }
                    .chartLegend(.hidden)
                    .frame(width: 62, height: 62)
                    .overlay {
                        Text(verbatim: "\(total)")
                            .font(.caption.weight(.semibold))
                            .monospacedDigit()
                            .contentTransition(.numericText())
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(slices) { slice in
                            HStack(spacing: 7) {
                                Circle()
                                    .fill(slice.color.opacity(0.85))
                                    .frame(width: 5, height: 5)
                                Text(slice.title)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                Spacer(minLength: 6)
                                Text(verbatim: "\(slice.count)")
                                    .font(.caption2.weight(.medium))
                                    .monospacedDigit()
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - YouTube

struct YouTubeTile: View {
    @Environment(AppState.self) private var app

    private var fraction: Double {
        min(1, (app.youtubeTest?.kilobytesPerSecond ?? 0) / 3000)
    }

    var body: some View {
        Tile(tint: .red, action: { app.testYouTube() }) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TileHeader(title: "YouTube", symbol: "play.rectangle.fill", tint: .red)
                    Spacer()
                    Group {
                        if app.turboActive {
                            Text("Turbo")
                        } else {
                            Text(app.settings.youtubeMode.title)
                        }
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                }
                SpeedBar(fraction: fraction, tint: .red)
                    .frame(height: 6)
                HStack(spacing: 6) {
                    resultText
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                    if app.isTestingYouTube {
                        ProgressView().controlSize(.mini)
                    }
                }
                if !app.bridgeRunning {
                    Button {
                        app.startTurbo()
                    } label: {
                        Label("Turbo without Tor", systemImage: "bolt.fill")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
            }
        }
    }

    @ViewBuilder
    private var resultText: some View {
        if let result = app.youtubeTest {
            if result.success {
                let speed = result.kilobytesPerSecond.map { String(format: " · %.0f KB/s", $0) } ?? ""
                Text(verbatim: "\(result.milliseconds) ms\(speed)\(result.viaTor ? " · Tor" : " · direct")")
            } else {
                Text(verbatim: result.detail)
            }
        } else if app.bridgeRunning {
            Text("Click to test youtube.com through Veil")
        } else {
            Text("Connect, or enable Turbo, to test")
        }
    }
}

struct SpeedBar: View {
    let fraction: Double
    let tint: Color

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.primary.opacity(0.07))
                Capsule()
                    .fill(LinearGradient(colors: [tint.opacity(0.5), tint.opacity(0.9)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(6, geometry.size.width * CGFloat(fraction)))
                    .animation(.spring(duration: 0.8), value: fraction)
            }
        }
    }
}

// MARK: - Apps

struct AppsTile: View {
    @Environment(AppState.self) private var app
    @State private var copied = false

    var body: some View {
        Tile(tint: .blue) {
            VStack(alignment: .leading, spacing: 9) {
                TileHeader(title: "Apps", symbol: "square.grid.2x2", tint: .blue)
                appRow(symbol: "paperplane.fill", title: "Telegram", detail: "Ignores the system proxy") {
                    Button {
                        app.addProxyToTelegram()
                    } label: {
                        Label("Add proxy", systemImage: "plus.circle")
                    }
                }
                appRow(symbol: "terminal.fill", title: "Terminal", detail: "curl, git, Homebrew") {
                    Button {
                        app.copyTerminalSnippet()
                        copied = true
                        Task {
                            try? await Task.sleep(for: .seconds(2))
                            copied = false
                        }
                    } label: {
                        Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                }
                appRow(symbol: "apple.logo", title: "App Store & iCloud", detail: app.serviceRoute("apple") == .tor ? "Through Tor" : "Direct, so updates keep working") {
                    EmptyView()
                }
            }
        }
    }

    private func appRow<Trailing: View>(symbol: String, title: LocalizedStringKey, detail: LocalizedStringKey, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: 9) {
            Image(systemName: symbol)
                .font(.caption)
                .foregroundStyle(.blue)
                .frame(width: 16)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.caption.weight(.semibold))
                Text(detail)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            trailing()
                .buttonStyle(.glass)
                .controlSize(.small)
        }
    }
}

// MARK: - Network

struct NetworkTile: View {
    @Environment(AppState.self) private var app

    private var tint: Color {
        switch app.networkRepair {
        case .failed, .skipped: return .orange
        case .recovered: return .mint
        case .resetting, .waitingForNetwork, .probing: return .yellow
        case .idle: return (app.connectivity?.isUsable ?? true) ? .teal : .orange
        }
    }

    var body: some View {
        let report = app.connectivity
        Tile(tint: tint) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TileHeader(title: "Network", symbol: app.primaryNetwork?.isWiFi == true ? "wifi" : "network", tint: tint)
                    Spacer()
                    if app.networkRepair.isBusy {
                        ProgressView().controlSize(.mini)
                    }
                }
                HStack(spacing: 12) {
                    StatusPill(title: "Internet", ok: report?.internetReachable)
                    StatusPill(title: "DNS", ok: report?.nameResolution)
                    if let primary = app.primaryNetwork {
                        Text(verbatim: primary.displayName)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                NetworkRepairLabel(status: app.networkRepair, report: report)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                HStack(spacing: 8) {
                    Button {
                        app.resetNetwork()
                    } label: {
                        Label("Reset network", systemImage: "wifi.exclamationmark")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    .disabled(app.isResettingNetwork)
                    Button {
                        app.probeInternet()
                    } label: {
                        Label("Check", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    .disabled(app.networkRepair.isBusy)
                }
            }
        }
    }
}

struct StatusPill: View {
    let title: LocalizedStringKey
    let ok: Bool?

    private var color: Color {
        switch ok {
        case .some(true): .mint
        case .some(false): .red
        case .none: Color.secondary.opacity(0.35)
        }
    }

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(color)
                .frame(width: 6, height: 6)
            Text(title)
                .font(.caption2.weight(.medium))
        }
    }
}

/// One line describing the network check / reset state.
struct NetworkRepairLabel: View {
    let status: NetworkRepairStatus
    var report: ConnectivityProbe.Report? = nil

    var body: some View {
        switch status {
        case .idle:
            if let report {
                Text(verbatim: report.summary)
            } else {
                Text("Not checked yet")
            }
        case .probing:
            Text("Checking the Internet…")
        case .resetting(let name):
            Text("Network not responding — resetting \(name), like Wi-Fi off and on…")
        case .waitingForNetwork:
            Text("Waiting for the network to come back…")
        case .recovered(let method):
            Text("Network recovered after the \(method)")
        case .skipped(let reason):
            Text(verbatim: reason)
        case .failed(let reason):
            Text(verbatim: reason)
        }
    }
}
