import SwiftUI

/// Results of the checks that make the claims falsifiable. A check that could not run says
/// "skipped" rather than quietly passing.
struct SelfTestPanel: View {
    @Environment(AppState.self) private var app

    var body: some View {
        SecuritySection(title: "Self-test") {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Button {
                        app.runSelfTest()
                    } label: {
                        if app.isSelfTesting {
                            ProgressView().controlSize(.small)
                        } else {
                            Text("Run the self-test")
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(app.isSelfTesting)
                    Spacer(minLength: 0)
                    if let report = app.selfTestReport {
                        Text(report.date, format: .dateTime.hour().minute().second())
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
                if app.connection != .connected {
                    Text("Network checks need a live connection; they will be skipped.")
                        .settingsCaption()
                }
                if let report = app.selfTestReport {
                    ForEach(report.results) { result in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: symbol(result.verdict))
                                .foregroundStyle(tint(result.verdict))
                                .frame(width: 16)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(verbatim: SelfTestCopy.title(result.id))
                                    .font(.subheadline)
                                Text(verbatim: SelfTestCopy.detail(result.id))
                                    .settingsCaption()
                            }
                            Spacer(minLength: 8)
                            if let milliseconds = result.milliseconds {
                                Text(verbatim: "\(milliseconds) ms")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                            if let detail = result.detail {
                                Text(verbatim: detail)
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }

    private func symbol(_ verdict: SelfTestResult.Verdict) -> String {
        switch verdict {
        case .pass: "circle.fill"
        case .warn: "exclamationmark.triangle.fill"
        case .fail: "exclamationmark.octagon.fill"
        case .skipped: "minus"
        }
    }

    private func tint(_ verdict: SelfTestResult.Verdict) -> Color {
        switch verdict {
        case .pass: .secondary
        case .warn: .orange
        case .fail: .red
        case .skipped: Color.secondary.opacity(0.55)
        }
    }
}

enum SelfTestCopy {
    static func title(_ id: String) -> String {
        switch id {
        case "selftest-system-proxy": String(localized: "The Mac points at Veil")
        case "selftest-pt-directory": String(localized: "The transport binaries are private")
        case "selftest-bypasses": String(localized: "Nothing bypasses Tor")
        case "selftest-ports": String(localized: "Local ports are assigned")
        case "selftest-isolation": String(localized: "Two circuits are really separate")
        case "selftest-dns": String(localized: "Host names are resolved by Tor")
        default: id
        }
    }

    static func detail(_ id: String) -> String {
        switch id {
        case "selftest-system-proxy": String(localized: "Which network services are configured to send traffic to Veil.")
        case "selftest-pt-directory": String(localized: "Only this user may replace the pluggable transport binaries.")
        case "selftest-bypasses": String(localized: "Whether any destination is deliberately routed outside Tor.")
        case "selftest-ports": String(localized: "Veil holds its local SOCKS, HTTP and control ports.")
        case "selftest-isolation": String(localized: "Two connections with different isolation keys each get their own circuit.")
        case "selftest-dns": String(localized: "A name is handed to Tor rather than looked up on this Mac, so nothing leaks to your DNS server.")
        default: ""
        }
    }
}
