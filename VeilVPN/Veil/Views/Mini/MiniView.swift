import SwiftUI

/// The compact window: power button, status and the essentials.
struct MiniView: View {
    @Environment(AppState.self) private var app
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        ZStack {
            AuroraBackground(state: app.connection)
                .ignoresSafeArea()
            VStack(spacing: 18) {
                GlassEffectContainer(spacing: 24) {
                    VStack(spacing: 16) {
                        PowerButton(state: app.connection, progress: app.bootstrap) {
                            app.toggleConnection()
                        }
                        .scaleEffect(0.8)
                        .frame(height: 190)
                        Text(app.connection.title)
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                        if app.connection == .connecting {
                            Text(app.transportAttemptMessage.map { Text(verbatim: $0) } ?? Text(app.bootstrap.phaseTitle))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                        } else if let exit = app.exitHop, app.connection.isConnected {
                            Text("Exit relay: \(exit.flag) \(exit.countryName ?? exit.nickname)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if app.connection.isConnected {
                    HStack(spacing: 10) {
                        MiniStat(symbol: "arrow.down", value: ByteFormat.rate(app.traffic.downloadRate), tint: .cyan)
                        MiniStat(symbol: "arrow.up", value: ByteFormat.rate(app.traffic.uploadRate), tint: .orange)
                        MiniStat(symbol: "timer", value: app.routeLatency.map { "\(Int($0 * 1000)) ms" } ?? "—", tint: .purple)
                    }
                }

                if app.killSwitchEngaged {
                    Label("Kill switch engaged", systemImage: "hand.raised.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.red)
                }

                Spacer(minLength: 0)

                HStack {
                    Button {
                        app.requestNewIdentity()
                    } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                    }
                    .buttonStyle(.glass)
                    .disabled(!app.connection.isConnected)
                    .help("New Identity")
                    Spacer()
                    Button {
                        openWindow(id: "main")
                    } label: {
                        Label("Full window", systemImage: "macwindow")
                    }
                    .buttonStyle(.glass)
                }
            }
            .padding(22)
        }
        .frame(width: 360, height: 560)
        .animation(.smooth(duration: 0.5), value: app.connection)
    }
}

private struct MiniStat: View {
    let symbol: String
    let value: String
    let tint: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .foregroundStyle(tint)
            Text(verbatim: value)
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .font(.caption.weight(.semibold))
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .glassEffect(.regular, in: .capsule)
    }
}
