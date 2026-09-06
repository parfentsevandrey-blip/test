import SwiftUI

struct LogConsole: View {
    let entries: [LogEntry]
    var onClear: @MainActor () -> Void
    var onCopy: @MainActor () -> Void

    @State private var warningsOnly = false

    private static let timeFormat = Date.FormatStyle(date: .omitted, time: .standard)

    private var visible: [LogEntry] {
        warningsOnly ? entries.filter { $0.level >= .warn } : entries
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Tor log", systemImage: "text.alignleft")
                    .font(.headline)
                Spacer()
                Toggle("Warnings only", isOn: $warningsOnly)
                    .toggleStyle(.checkbox)
                Button {
                    onCopy()
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                .buttonStyle(.glass)
                Button {
                    onClear()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
                .buttonStyle(.glass)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 3) {
                        ForEach(visible) { entry in
                            LogLine(entry: entry, timeFormat: Self.timeFormat)
                                .id(entry.id)
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: entries.count) { _, _ in
                    if let last = visible.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
                .onAppear {
                    if let last = visible.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
            .background(Color.black.opacity(0.22), in: .rect(cornerRadius: 16))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}

private struct LogLine: View {
    let entry: LogEntry
    let timeFormat: Date.FormatStyle

    private var color: Color {
        switch entry.level {
        case .error: .red
        case .warn: .orange
        case .notice: entry.source == .veil ? .mint : .primary
        case .info, .debug: .secondary
        }
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(entry.date, format: timeFormat)
                .foregroundStyle(.tertiary)
            Text(verbatim: entry.source == .veil ? "veil" : entry.level.label)
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .leading)
            Text(verbatim: entry.message)
                .foregroundStyle(color)
                .textSelection(.enabled)
        }
        .font(.system(.caption, design: .monospaced))
    }
}
