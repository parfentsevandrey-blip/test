import SwiftUI

/// What actually happened, in the app's own words.
///
/// Kept in memory and never written to disk: a log file recording which
/// bridges were used is evidence sitting on the machine. It is here because
/// diagnosing a connection on a censored network is most of the support
/// burden, and the lines that matter — where a slow connect spent its time,
/// which Snowflake phase was slow — are only useful if they can be read.
struct LogView: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    @State private var follow = true

    var body: some View {
        VStack(spacing: Metrics.snug) {
            HStack {
                Toggle("Следить за концом", isOn: $follow)
                    .toggleStyle(.switch)
                    .font(.system(size: 12))
                Spacer()
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(tunnel.lines.joined(separator: "\n"), forType: .string)
                } label: {
                    Label("Скопировать", systemImage: "doc.on.doc")
                }
                .buttonStyle(.borderless)
                .font(.system(size: 12))
            }

            GlassPanel(padding: Metrics.snug) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 2) {
                            ForEach(Array(tunnel.lines.enumerated()), id: \.offset) { index, line in
                                Text(line)
                                    .font(.veilMono)
                                    .foregroundStyle(tone(for: line))
                                    .textSelection(.enabled)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .id(index)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onChange(of: tunnel.lines.count) { _, count in
                        guard follow, count > 0 else { return }
                        withAnimation(.easeOut(duration: 0.15)) {
                            proxy.scrollTo(count - 1, anchor: .bottom)
                        }
                    }
                }
            }
        }
    }

    /// The lines worth spotting without reading: a failure, and the timeline
    /// entries that say where a connect went.
    private func tone(for line: String) -> Color {
        let lowered = line.lowercased()
        if lowered.contains("failed") || lowered.contains("no answer") || lowered.contains("stalled") {
            return .orange
        }
        if lowered.contains("timeline:") || lowered.contains("reached the internet") {
            return .green
        }
        return .primary.opacity(0.85)
    }
}
