import SwiftUI

/// Route screen: the multihop card with a live diagram, its options, and the exit-country grid.
struct RouteView: View {
    @Environment(AppState.self) private var app

    private let columns = [GridItem(.adaptive(minimum: 172, maximum: 240), spacing: 14)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                MultihopCard()

                if app.settings.multihopEnabled {
                    MultihopOptions()
                        .transition(.move(edge: .top).combined(with: .opacity))
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Exit relay country")
                        .font(.title3.weight(.bold))
                    Text("Pins the last relay of the circuit to one country. Automatic selection is the most private choice; changes apply immediately.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                LazyVGrid(columns: columns, spacing: 14) {
                    LocationCard(
                        flag: "🌐",
                        title: Text("Automatic"),
                        detail: Text("Recommended"),
                        isSelected: app.settings.exitCountry == nil
                    ) {
                        app.setExitCountry(nil)
                    }
                    ForEach(ExitLocation.popular) { location in
                        LocationCard(
                            flag: location.flag,
                            title: Text(verbatim: location.name),
                            detail: Text(verbatim: location.code.uppercased()),
                            isSelected: app.settings.exitCountry == location.code
                        ) {
                            app.setExitCountry(location.code)
                        }
                    }
                }
            }
            .frame(maxWidth: 880)
            .frame(maxWidth: .infinity)
            .padding(32)
        }
        .animation(.smooth(duration: 0.4), value: app.settings.multihopEnabled)
    }
}

// MARK: - Multihop card

struct MultihopCard: View {
    @Environment(AppState.self) private var app

    private var middleDisplay: HopDisplay {
        if let hop = app.middleHop, app.connection.isConnected, !hop.flag.isEmpty {
            return HopDisplay(flag: hop.flag, name: hop.countryName ?? hop.nickname)
        }
        if let chosen = app.selectedMiddle {
            return HopDisplay(flag: chosen.flag, name: chosen.name)
        }
        return HopDisplay(flag: nil, name: nil)
    }

    private var exitDisplay: HopDisplay {
        if let hop = app.exitHop, app.connection.isConnected, !hop.flag.isEmpty {
            return HopDisplay(flag: hop.flag, name: hop.countryName ?? hop.nickname)
        }
        if let chosen = app.selectedExit {
            return HopDisplay(flag: chosen.flag, name: chosen.name)
        }
        return HopDisplay(flag: nil, name: nil)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .firstTextBaseline) {
                Text("Multihop")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                Spacer()
                if let latency = app.routeLatency {
                    Label("Route latency ≈ \(Int((latency * 1000).rounded())) ms", systemImage: "timer")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }

            RouteDiagram(
                usesBridge: (app.activeTransport ?? app.settings.transport) != .direct,
                middle: middleDisplay,
                exit: exitDisplay,
                active: app.connection.isConnected
            )
            .frame(height: 150)

            RouteMapView(hops: app.circuit)
                .frame(height: 220)
                .clipShape(.rect(cornerRadius: 20))

            RouteTuningRow()

            Text("Multihop routes your traffic through relays in the countries you choose — bridge, middle and exit hops in different jurisdictions — which makes tracking much harder. It can add latency, but improves anonymity. The route can also be excluded from chosen countries and rotated on a timer.")
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Toggle(isOn: Binding(
                get: { app.settings.multihopEnabled },
                set: { app.setMultihopEnabled($0) }
            )) {
                Text("Enable")
                    .font(.headline)
            }
            .toggleStyle(.switch)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(.primary.opacity(0.06), in: .rect(cornerRadius: 18))
        }
        .padding(24)
        .glassEffect(.regular, in: .rect(cornerRadius: 26))
    }
}

struct HopDisplay: Equatable {
    var flag: String?
    var name: String?
}

/// laptop → (Snowflake bridge) → middle relay → exit relay → internet, with traffic dots when connected.
struct RouteDiagram: View {
    let usesBridge: Bool
    let middle: HopDisplay
    let exit: HopDisplay
    let active: Bool

    var body: some View {
        HStack(spacing: 0) {
            RouteNodeView(symbol: "laptopcomputer", flag: nil, title: "This Mac", subtitle: nil)
            RouteConnector(active: active, phase: 0)
            if usesBridge {
                RouteNodeView(symbol: "snowflake", flag: nil, title: "Bridge", subtitle: "Snowflake")
                RouteConnector(active: active, phase: 0.25)
            }
            RouteNodeView(symbol: "server.rack", flag: middle.flag, title: "Middle", subtitle: middle.name)
            RouteConnector(active: active, phase: 0.5)
            RouteNodeView(symbol: "server.rack", flag: exit.flag, title: "Exit", subtitle: exit.name)
            RouteConnector(active: active, phase: 0.75)
            RouteNodeView(symbol: "globe", flag: nil, title: "Internet", subtitle: nil)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 16)
        .background {
            RouteBackdrop()
        }
        .clipShape(.rect(cornerRadius: 20))
    }
}

private struct RouteNodeView: View {
    let symbol: String
    let flag: String?
    let title: LocalizedStringKey
    let subtitle: String?

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(.primary.opacity(0.08))
                    .frame(width: 52, height: 52)
                if let flag, !flag.isEmpty {
                    Text(flag)
                        .font(.system(size: 28))
                } else {
                    Image(systemName: symbol)
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(.primary)
                }
            }
            Text(title)
                .font(.caption.weight(.semibold))
            Text(verbatim: subtitle ?? " ")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(width: 92)
    }
}

private struct RouteConnector: View {
    let active: Bool
    let phase: Double

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: !active || reduceMotion)) { context in
            let progress = (context.date.timeIntervalSinceReferenceDate / 2.4 + phase).truncatingRemainder(dividingBy: 1)
            GeometryReader { geometry in
                let width = geometry.size.width
                let midY = geometry.size.height / 2
                Path { path in
                    path.move(to: CGPoint(x: 0, y: midY))
                    path.addLine(to: CGPoint(x: width, y: midY))
                }
                .stroke(.primary.opacity(active ? 0.35 : 0.18), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [3, 7]))
                if active {
                    Circle()
                        .fill(.mint)
                        .frame(width: 8, height: 8)
                        .shadow(color: .mint.opacity(0.9), radius: 6)
                        .position(x: max(4, min(width - 4, width * progress)), y: midY)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 52)
        .offset(y: -12)
    }
}

/// A faint dotted "map" behind the diagram.
private struct RouteBackdrop: View {
    var body: some View {
        Canvas { context, size in
            let spacing: CGFloat = 14
            var y: CGFloat = 8
            var row = 0
            while y < size.height {
                var x: CGFloat = row.isMultiple(of: 2) ? 8 : 15
                while x < size.width {
                    // Deterministic pseudo-random "land" pattern.
                    let seed = sin(Double(x) * 0.13 + Double(y) * 0.29) * cos(Double(y) * 0.07 - Double(x) * 0.05)
                    if seed > 0.15 {
                        let rect = CGRect(x: x, y: y, width: 3, height: 3)
                        context.fill(Path(ellipseIn: rect), with: .color(.primary.opacity(seed > 0.55 ? 0.18 : 0.09)))
                    }
                    x += spacing
                }
                y += spacing
                row += 1
            }
        }
        .background(.primary.opacity(0.04))
    }
}

// MARK: - Multihop options

struct MultihopOptions: View {
    @Environment(AppState.self) private var app

    private let chipColumns = [GridItem(.adaptive(minimum: 132, maximum: 180), spacing: 8)]

    var body: some View {
        @Bindable var app = app
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Middle hop")
                        .font(.subheadline.weight(.semibold))
                    Picker("Middle hop", selection: Binding(
                        get: { app.settings.middleCountry ?? "" },
                        set: { app.setMiddleCountry($0.isEmpty ? nil : $0) }
                    )) {
                        Text("Automatic").tag("")
                        ForEach(ExitLocation.popular) { location in
                            Text(verbatim: "\(location.flag) \(location.name)").tag(location.code)
                        }
                    }
                    .labelsHidden()
                    .frame(width: 220)
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Rotate route")
                        .font(.subheadline.weight(.semibold))
                    Picker("Rotate route", selection: Binding(
                        get: { app.settings.rotateRouteMinutes },
                        set: { app.setRotateRouteMinutes($0) }
                    )) {
                        Text("Off").tag(0)
                        Text("Every 5 minutes").tag(5)
                        Text("Every 10 minutes").tag(10)
                        Text("Every 30 minutes").tag(30)
                    }
                    .labelsHidden()
                    .frame(width: 180)
                }
                Spacer(minLength: 0)
            }

            Toggle(isOn: Binding(
                get: { app.settings.avoidFiveEyes },
                set: { app.setAvoidFiveEyes($0) }
            )) {
                Text("Avoid Five Eyes countries (US, UK, Canada, Australia, New Zealand)")
            }

            Toggle(isOn: $app.settings.seamlessRouteSwitch) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Switch routes seamlessly")
                    Text("Open connections stay on the old route until they finish; new ones take the new route as soon as its first circuit is ready.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Never route through")
                    .font(.subheadline.weight(.semibold))
                LazyVGrid(columns: chipColumns, spacing: 8) {
                    ForEach(ExitLocation.popular) { location in
                        ExcludeChip(
                            location: location,
                            isExcluded: app.settings.excludedCountries.contains(location.code)
                        ) {
                            app.toggleExcludedCountry(location.code)
                        }
                    }
                }
            }

            Text("Every restriction shrinks the pool of relays Tor can pick from and makes your circuits more distinctive. Choose a middle-hop country different from the exit and keep exclusions modest.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}

private struct ExcludeChip: View {
    let location: ExitLocation
    let isExcluded: Bool
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(location.flag)
                Text(verbatim: location.name)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: isExcluded ? "nosign" : "circle")
                    .font(.caption)
                    .foregroundStyle(isExcluded ? AnyShapeStyle(.red) : AnyShapeStyle(.tertiary))
            }
            .font(.caption)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(isExcluded ? Color.red.opacity(0.22) : Color.primary.opacity(0.06), in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .animation(.smooth(duration: 0.2), value: isExcluded)
    }
}

// MARK: - Route tuning

/// Circuit races: Tor builds a few circuits for the chosen countries, Veil times them and pins
/// the relays of the quickest ones.
struct RouteTuningRow: View {
    @Environment(AppState.self) private var app

    private static func milliseconds(_ seconds: TimeInterval) -> String {
        "\(Int((seconds * 1000).rounded())) ms"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Label("Fast route", systemImage: "hare")
                    .font(.headline)
                Spacer()
                if app.tuner.status.isRacing {
                    ProgressView().controlSize(.small)
                }
                Button {
                    app.retuneRoute()
                } label: {
                    Label("Measure again", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.glass)
                .controlSize(.small)
                .disabled(!app.connection.isConnected || app.tuner.status.isRacing || !app.settings.latencyTuning)
                Toggle("", isOn: Binding(
                    get: { app.settings.latencyTuning },
                    set: { app.setLatencyTuning($0) }
                ))
                .labelsHidden()
                .toggleStyle(.switch)
                .controlSize(.small)
            }
            Text("Veil races several circuits to your chosen countries, times how fast each one comes up and pins the quickest exit relays — and the middle relay when you chose its country. Relays are measured again after every route change and every half hour.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            statusLine
                .font(.caption)
                .foregroundStyle(.secondary)
            if !app.tuner.pinnedExits.isEmpty || app.tuner.pinnedMiddle != nil {
                HStack(spacing: 8) {
                    ForEach(app.tuner.pinnedExits) { relay in
                        RelayChip(relay: relay, role: "Exit")
                    }
                    if let middle = app.tuner.pinnedMiddle {
                        RelayChip(relay: middle, role: "Middle")
                    }
                }
            }
        }
        .padding(16)
        .background(.primary.opacity(0.06), in: .rect(cornerRadius: 18))
        .animation(.smooth(duration: 0.3), value: app.tuner.status)
    }

    @ViewBuilder
    private var statusLine: some View {
        if !app.settings.latencyTuning {
            Text("Off — Tor picks relays within the chosen countries on its own.")
        } else if !app.connection.isConnected {
            Text("Runs after connecting.")
        } else {
            switch app.tuner.status {
            case .idle:
                Text("Waiting for the first measurement…")
            case .racing(let launched, let built):
                Text("Racing \(launched) circuits, \(built) built…")
            case .pinned:
                if let before = app.routeLatencyBeforeTuning, let after = app.routeLatency {
                    Text("Route latency \(Self.milliseconds(before)) → \(Self.milliseconds(after))")
                } else if let after = app.routeLatency {
                    Text("Route latency now \(Self.milliseconds(after))")
                } else {
                    Text("Fastest relays pinned; verifying the exit…")
                }
            case .failed(let message):
                Text(verbatim: message)
            }
        }
    }
}

private struct RelayChip: View {
    let relay: RelayCandidate
    let role: LocalizedStringKey

    var body: some View {
        HStack(spacing: 6) {
            if !relay.flag.isEmpty {
                Text(verbatim: relay.flag)
            } else {
                Image(systemName: "server.rack")
                    .foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(role)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(verbatim: "\(relay.nickname) · \(relay.buildMilliseconds) ms")
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.primary.opacity(0.06), in: .capsule)
        .help(Text(verbatim: relay.bandwidth.map { "\(relay.fingerprint) · \($0) kB/s" } ?? relay.fingerprint))
    }
}

// MARK: - Exit grid card

struct LocationCard: View {
    let flag: String
    let title: Text
    let detail: Text
    let isSelected: Bool
    let action: @MainActor () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(flag)
                    .font(.system(size: 30))
                VStack(alignment: .leading, spacing: 2) {
                    title
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    detail
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
                    .imageScale(.large)
            }
            .padding(14)
            .contentShape(.rect(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(isSelected ? Color.accentColor.opacity(0.35) : nil).interactive(), in: .rect(cornerRadius: 18))
        .animation(.smooth(duration: 0.25), value: isSelected)
    }
}
