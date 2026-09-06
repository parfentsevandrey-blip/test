import SwiftUI

/// The big glass power button. Shows Tor's bootstrap progress as a ring while connecting.
struct PowerButton: View {
    let state: ConnectionState
    let progress: BootstrapProgress
    let action: @MainActor () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var tint: Color { state.tint }

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .stroke(.primary.opacity(0.10), lineWidth: 8)

                if state == .connecting {
                    Circle()
                        .trim(from: 0, to: max(0.02, Double(progress.percent) / 100))
                        .stroke(
                            AngularGradient(colors: [tint.opacity(0.25), tint], center: .center),
                            style: StrokeStyle(lineWidth: 8, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .animation(.smooth(duration: 0.4), value: progress.percent)
                    if !reduceMotion {
                        SpinnerArc(tint: tint)
                    }
                } else if state == .connected {
                    Circle()
                        .stroke(tint.opacity(0.9), lineWidth: 8)
                        .shadow(color: tint.opacity(0.7), radius: 18)
                } else if state == .disconnecting {
                    Circle()
                        .stroke(tint.opacity(0.5), style: StrokeStyle(lineWidth: 8, dash: [6, 10]))
                }

                Image(systemName: "power")
                    .font(.system(size: 64, weight: .semibold, design: .rounded))
                    .foregroundStyle(state.isConnected ? AnyShapeStyle(tint) : AnyShapeStyle(.primary))
                    .symbolEffect(.pulse, isActive: state.isBusy)
                    .shadow(color: state.isConnected ? tint.opacity(0.6) : .clear, radius: 12)
            }
            .padding(18)
            .frame(width: 220, height: 220)
            .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(tint.opacity(state.isConnected ? 0.45 : 0.25)).interactive(), in: .circle)
        .disabled(state == .disconnecting)
        .accessibilityLabel(state.isActive ? Text("Disconnect") : Text("Connect"))
        .accessibilityValue(Text(state.title))
    }
}

private struct SpinnerArc: View {
    let tint: Color

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30)) { context in
            let angle = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6 * 360
            Circle()
                .trim(from: 0.78, to: 0.98)
                .stroke(tint.opacity(0.85), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .padding(-7)
                .rotationEffect(.degrees(angle))
        }
    }
}
