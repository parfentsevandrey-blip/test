import SwiftUI

/// The small shared pieces the diagnostic screens are built from. Two materials only — `.bar`
/// behind the header, `.regularMaterial` behind panels — and no tinted glass on a row, a bar or a
/// dot: `glassEffect` on many small elements is expensive and says nothing.
struct StageGlyph: View {
    let state: StageState

    var body: some View {
        Group {
            switch state {
            case .working(let fraction):
                if let fraction {
                    ProgressView(value: fraction, total: 1)
                        .progressViewStyle(.circular)
                        .controlSize(.small)
                } else {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .controlSize(.small)
                }
            case .notApplicable:
                Color.clear
            default:
                if let symbol = state.symbolName {
                    Image(systemName: symbol)
                        .font(state == .ok ? .caption2 : .caption)
                        .symbolRenderingMode(.monochrome)
                        .foregroundStyle(state.tint)
                }
            }
        }
        .frame(width: 16, height: 16)
    }
}

/// One uniform staleness treatment, never a per-widget invention: the value, and how old it is.
struct ValueText: View {
    let value: String
    var ageSeconds: TimeInterval?

    var body: some View {
        if let ageSeconds {
            Text(verbatim: "\(value) (\(Int(ageSeconds)) s ago)")
                .font(.callout.monospacedDigit())
                .foregroundStyle(.tertiary)
        } else {
            Text(verbatim: value)
                .font(.callout.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }
}

/// A labelled value inside a detail panel.
struct DetailRow: View {
    let label: LocalizedStringKey
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.subheadline)
            Spacer(minLength: 12)
            Text(verbatim: value)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
        }
    }
}

struct NetworkRepairLabel: View {
    let status: NetworkRepairStatus
    var report: ConnectivityProbe.Report? = nil

    var body: some View {
        switch status {
        case .idle:
            if let report {
                Text(verbatim: report.summary)
            } else {
                Text("Not checked yet")
            }
        case .probing:
            Text("Checking the Internet…")
        case .resetting(let name):
            Text("Network not responding — resetting \(name), like Wi-Fi off and on…")
        case .waitingForNetwork:
            Text("Waiting for the network to come back…")
        case .recovered(let method):
            Text("Network recovered after the \(method)")
        case .skipped(let reason):
            Text(verbatim: reason)
        case .failed(let reason):
            Text(verbatim: reason)
        }
    }
}

/// One counter, for the Activity screen's routing row.
struct StatTile: View {
    let title: LocalizedStringKey
    let symbol: String
    let value: String
    var tint: Color = .accentColor

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: symbol)
                .font(.caption)
                .foregroundStyle(.secondary)
                .symbolRenderingMode(.hierarchical)
            Text(value)
                .font(.system(.title3, design: .rounded, weight: .semibold))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .contentTransition(.numericText())
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }
}
