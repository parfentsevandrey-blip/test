import SwiftUI

struct MenuBarLabel: View {
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: app.connection.symbolName)
                .symbolRenderingMode(.hierarchical)
            if app.connection.isConnected, app.traffic.samples.count > 2 {
                MenuBarSparkline(samples: app.traffic.samples.suffix(30).map { $0.download + $0.upload })
                    .frame(width: 26, height: 12)
            }
        }
    }
}

/// A tiny throughput sparkline rendered into the status item.
struct MenuBarSparkline: View {
    let samples: [Double]

    var body: some View {
        Canvas { context, size in
            guard samples.count > 1 else { return }
            let peak = max(samples.max() ?? 1, 1)
            var path = Path()
            for (index, value) in samples.enumerated() {
                let x = size.width * CGFloat(index) / CGFloat(samples.count - 1)
                let y = size.height - size.height * CGFloat(value / peak)
                if index == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            context.stroke(path, with: .color(.primary), lineWidth: 1.2)
        }
    }
}

struct MenuBarView: View {
    @Environment(AppState.self) private var app
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                StatusDot(state: app.connection)
                VStack(alignment: .leading, spacing: 2) {
                    Text(app.connection.title)
                        .font(.headline)
                    subtitle
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                Toggle("Tor", isOn: Binding(
                    get: { app.connection.isActive },
                    set: { _ in app.toggleConnection() }
                ))
                .toggleStyle(.switch)
                .labelsHidden()
                .disabled(app.connection == .disconnecting)
            }

            if app.connection == .connecting {
                ProgressView(value: Double(app.bootstrap.percent), total: 100)
                    .tint(.orange)
                Text(app.bootstrap.phaseTitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Divider()

            Picker("Exit country", selection: Binding(
                get: { app.settings.exitCountry ?? "" },
                set: { app.setExitCountry($0.isEmpty ? nil : $0) }
            )) {
                Text("Automatic").tag("")
                ForEach(ExitLocation.popular) { location in
                    Text(verbatim: "\(location.flag) \(location.name)").tag(location.code)
                }
            }
            .pickerStyle(.menu)

            Button {
                app.requestNewIdentity()
            } label: {
                Label("New Identity", systemImage: "arrow.triangle.2.circlepath")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.glass)
            .disabled(!app.connection.isConnected || app.isChangingIdentity)

            Toggle(isOn: Binding(
                get: { app.settings.paddingEnabled },
                set: { app.setPaddingEnabled($0) }
            )) {
                Label("Traffic padding", systemImage: "waveform.badge.plus")
            }
            .toggleStyle(.switch)
            .controlSize(.small)

            Toggle(isOn: Binding(
                get: { app.turboActive },
                set: { _ in app.toggleTurbo() }
            )) {
                Label("YouTube Turbo (no Tor)", systemImage: "bolt.fill")
            }
            .toggleStyle(.switch)
            .controlSize(.small)
            .disabled(app.connection != .disconnected && app.connection != .failed)

            if let update = app.availableUpdate {
                Button {
                    app.openAvailableUpdate()
                } label: {
                    Label("Download Veil \(update.version)", systemImage: "arrow.down.circle.fill")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.glass)
                .tint(.blue)
            }

            Divider()

            HStack {
                Button("Open Veil") {
                    openWindow(id: "main")
                    NSApp.activate()
                }
                Spacer()
                SettingsLink {
                    Text("Settings…")
                }
                Button("Quit") {
                    NSApp.terminate(nil)
                }
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .font(.callout)
        }
        .padding(14)
        .frame(width: 320)
    }

    @ViewBuilder
    private var subtitle: some View {
        switch app.connection {
        case .connected:
            if let exit = app.exitHop {
                Text("Exit: \(exit.flag) \(exit.countryName ?? exit.nickname)")
            } else {
                Text("Routed through Tor")
            }
        case .connecting:
            Text(verbatim: "\(app.bootstrap.percent)%")
        case .failed:
            Text("Open Veil to see the error")
        case .disconnected:
            if app.turboActive {
                Text("YouTube Turbo is active, Tor is off")
            } else {
                Text("Click to connect through Tor")
            }
        case .disconnecting:
            Text("Restoring network settings…")
        }
    }
}
