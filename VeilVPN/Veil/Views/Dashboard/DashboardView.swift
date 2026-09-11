import SwiftUI

/// Home is a ledger, not a diagram. Twelve named stages of the path a request takes, each judged
/// against numbers that live in one file, with exactly one row marked at a time — the bottleneck.
/// Nothing moves unless a measurement moved.
struct DashboardView: View {
    @Environment(AppState.self) private var app
    @State private var selection: PipelineStage?

    var openActivity: @MainActor () -> Void
    var openLocations: @MainActor () -> Void

    var body: some View {
        // One periodic tick for the whole screen, so ages stay honest. This is a data refresh,
        // not an animation, and it is the only clock the screen has.
        TimelineView(.periodic(from: .now, by: app.connection.isActive ? 1 : 5)) { context in
            let snapshot = app.diagnostics(at: context.date)
            let rows = DiagnosticSnapshot.rows(from: snapshot)
            let bottleneck = DiagnosticSnapshot.bottleneck(in: rows)
            VStack(spacing: 0) {
                if app.isDemo { DemoBanner().padding(.horizontal, 20).padding(.top, 12) }
                if app.killSwitchEngaged { KillSwitchBanner().padding(.horizontal, 20).padding(.top, 12) }
                if let update = app.availableUpdate {
                    UpdateBanner(update: update).padding(.horizontal, 20).padding(.top, 12)
                }
                StatusHeader(headline: DiagnosticSnapshot.headline(rows: rows, snapshot: snapshot),
                             hasBottleneck: bottleneck != nil)
                Divider()
                if app.connection == .failed, let error = app.lastError {
                    ErrorCard(error: error, openActivity: openActivity)
                        .padding(20)
                }
                HStack(spacing: 0) {
                    PipelineList(rows: rows, bottleneck: bottleneck?.stage, selection: $selection)
                        .frame(width: 360)
                        .background(.regularMaterial)
                    Divider()
                    StageDetail(row: selected(in: rows, bottleneck: bottleneck),
                                snapshot: snapshot,
                                openActivity: openActivity,
                                openLocations: openLocations)
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    /// With nothing selected the detail pane follows the bottleneck, so the screen answers the
    /// question you came with before you click anything.
    private func selected(in rows: [StageRow], bottleneck: StageRow?) -> StageRow {
        if let selection, let row = rows.first(where: { $0.stage == selection }) { return row }
        return bottleneck ?? rows[0]
    }
}

// MARK: - Cards

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
