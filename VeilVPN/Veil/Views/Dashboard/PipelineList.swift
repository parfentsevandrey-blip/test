import SwiftUI

/// The ledger. Exactly one row is marked at a time — the bottleneck — and that is the whole
/// encoding: a three-point bar, a semibold title and one extra line. Nothing moves unless a
/// measurement moved.
struct PipelineList: View {
    let rows: [StageRow]
    let bottleneck: PipelineStage?
    @Binding var selection: PipelineStage?

    var body: some View {
        List(rows, selection: $selection) { row in
            LabeledContent {
                ValueText(value: row.value, ageSeconds: row.ageSeconds)
            } label: {
                HStack(spacing: 8) {
                    Rectangle()
                        .fill(row.state.tint)
                        .frame(width: 3)
                        // Always present, so marking a row never shifts the layout.
                        .opacity(row.stage == bottleneck ? 1 : 0)
                    StageGlyph(state: row.state)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.stage.title)
                            .fontWeight(row.stage == bottleneck ? .semibold : .regular)
                        if row.stage == bottleneck, let detail = row.detail {
                            Text(verbatim: detail)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                    }
                }
            }
            .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 12))
            .frame(minHeight: 30)
        }
        .listStyle(.inset)
        .alternatingRowBackgrounds()
        .scrollContentBackground(.hidden)
        .animation(.easeInOut(duration: 0.2), value: bottleneck)
    }
}
