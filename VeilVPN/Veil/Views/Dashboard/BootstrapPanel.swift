import SwiftUI

struct BootstrapPanel: View {
    @Environment(AppState.self) private var app

    private var recentLines: [LogEntry] {
        Array(app.logs.suffix(4))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(app.bootstrap.phaseTitle, systemImage: app.settings.transport.symbol)
                    .font(.headline)
                Spacer()
                Text(verbatim: "\(app.bootstrap.percent)%")
                    .font(.headline)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .contentTransition(.numericText())
            }
            ProgressView(value: Double(app.bootstrap.percent), total: 100)
                .tint(.orange)
                .animation(.smooth, value: app.bootstrap.percent)
            if !app.bootstrap.summary.isEmpty {
                Text(verbatim: app.bootstrap.summary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if app.settings.transport == .snowflake {
                Text("Snowflake first has to find a volunteer proxy; in censored networks this can take 10–60 seconds.")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            VStack(alignment: .leading, spacing: 2) {
                ForEach(recentLines) { entry in
                    Text(verbatim: entry.message)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(entry.level >= .warn ? AnyShapeStyle(.orange) : AnyShapeStyle(.secondary))
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack {
                Spacer()
                Button("Cancel") {
                    app.disconnect()
                }
                .buttonStyle(.glass)
            }
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}
