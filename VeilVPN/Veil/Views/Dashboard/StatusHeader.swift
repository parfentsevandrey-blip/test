import SwiftUI

/// State, the verdict sentence, and — when there is one — where the time actually goes.
struct StatusHeader: View {
    @Environment(AppState.self) private var app
    let headline: String
    let hasBottleneck: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                PowerButton(state: app.connection, progress: app.bootstrap, size: 34) {
                    app.toggleConnection()
                }
                Text(app.connection.title)
                    .font(.headline)
                if let elapsed {
                    Text(verbatim: elapsed)
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                if let attempt = attemptLabel {
                    Text(verbatim: attempt)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Text(verbatim: headline)
                .font(.subheadline)
                .foregroundStyle(hasBottleneck ? .primary : .secondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
            if app.connection == .connected {
                BudgetBar()
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
    }

    private var elapsed: String? {
        let reference = app.connection == .connected ? app.connectedAt : app.connectStartedAt
        guard let reference else { return nil }
        let seconds = Int(Date.now.timeIntervalSince(reference))
        guard seconds >= 0 else { return nil }
        return seconds < 60 ? "\(seconds) s" : String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private var attemptLabel: String? {
        guard app.connection == .connecting, app.plannedQueue.count > 1 else { return nil }
        let name = AppState.name(of: app.activeTransport ?? app.settings.transport)
        return "\(name) · attempt \(app.attemptIndex + 1) of \(app.plannedQueue.count)"
    }
}

/// Where the latency of one connection through Veil actually goes. Bootstrap and throughput are
/// deliberately absent: neither is on a request's critical path.
struct BudgetBar: View {
    @Environment(AppState.self) private var app

    private var segments: [(label: String, seconds: Double)] {
        guard let lanes = app.lanes, let best = lanes.bestP50 else {
            guard let median = app.latency.summary?.median else { return [] }
            return [(String(localized: "route"), median)]
        }
        let worst = lanes.rows.compactMap(\.p50).max() ?? best
        return [(String(localized: "fastest circuit"), best),
                (String(localized: "spread"), max(0, worst - best))]
    }

    var body: some View {
        let total = segments.reduce(0) { $0 + $1.seconds }
        if total > 0 {
            VStack(alignment: .leading, spacing: 4) {
                GeometryReader { proxy in
                    HStack(spacing: 1) {
                        ForEach(Array(segments.enumerated()), id: \.offset) { index, segment in
                            Rectangle()
                                .fill(index == 0 ? Color.secondary.opacity(0.45) : Color.secondary.opacity(0.2))
                                .frame(width: max(1, proxy.size.width * segment.seconds / total))
                        }
                    }
                }
                .frame(height: 12)
                .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
                HStack(spacing: 10) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        Text(verbatim: "\(segment.label): \(Int(segment.seconds * 1000)) ms")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .animation(.easeInOut(duration: 0.2), value: total)
        }
    }
}
