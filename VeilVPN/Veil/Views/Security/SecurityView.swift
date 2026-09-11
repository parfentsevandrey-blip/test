import SwiftUI

/// One place that answers "how protected am I right now?" — and says plainly what Veil cannot do.
struct SecurityView: View {
    @Environment(AppState.self) private var app
    @State private var pendingPreset: SecurityPreset?

    private var posture: SecurityPosture { app.securityPosture }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PostureHeader(posture: posture)
                PresetRow(posture: posture, pending: $pendingPreset)
                if !posture.findings.filter({ $0.penalty > 0 || $0.severity >= .warning }).isEmpty {
                    SecuritySection(title: "What is costing you") {
                        VStack(spacing: 0) {
                            ForEach(posture.findings.filter { $0.penalty > 0 || $0.severity >= .warning }) { finding in
                                SecurityFindingRow(finding: finding) { app.applyFix(finding.fix) }
                                if finding.id != posture.findings.last?.id { Divider() }
                            }
                        }
                    }
                }
                ThreatMatrixView(posture: posture)
                ExposurePanel(posture: posture)
                SelfTestPanel()
                SecurityControls()
                SecurityLimitsPanel(limits: posture.limits)
            }
            .padding(20)
            .frame(maxWidth: 900, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
        .confirmationDialog("Apply this preset?", isPresented: presetDialog, presenting: pendingPreset) { preset in
            Button(preset.title) {
                app.applyPreset(preset)
                pendingPreset = nil
            }
            Button("Cancel", role: .cancel) { pendingPreset = nil }
        } message: { preset in
            Text(preset.warning ?? preset.summary)
        }
    }

    private var presetDialog: Binding<Bool> {
        Binding(get: { pendingPreset != nil }, set: { if !$0 { pendingPreset = nil } })
    }
}

/// A plain titled container. Deliberately not glass: this screen is read, not admired.
struct SecuritySection<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            content
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}

struct PostureHeader: View {
    let posture: SecurityPosture
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                if let score = posture.score {
                    Text(verbatim: "\(score)")
                        .font(.system(size: 44, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                    Text(verbatim: "/ \(SecurityPosture.absoluteMaximum)")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                } else {
                    Text(verbatim: "—")
                        .font(.system(size: 44, weight: .semibold, design: .rounded))
                }
                Spacer(minLength: 0)
                Text(verbatim: posture.gradeTitle)
                    .font(.headline)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(.regularMaterial, in: Capsule())
            }
            if let score = posture.score {
                ProgressView(value: Double(score), total: Double(SecurityPosture.absoluteMaximum))
                    .progressViewStyle(.linear)
            }
            Text(liveSentence)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
            Text("Veil configures the system proxy, not a network extension, so it cannot reach a perfect score. Reaching the ceiling would also mean throwing away Tor’s entry guard, which is why Veil never recommends it.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var liveSentence: String {
        switch posture.live {
        case .unavailable:
            return String(localized: "Demo mode: nothing is actually routed.")
        case .turbo:
            return String(localized: "YouTube Turbo is on. Tor is not running and every site sees your real IP address.")
        case .blocked:
            return String(localized: "Kill switch engaged: proxied apps are blocked until Veil reconnects.")
        case .exposed:
            return String(localized: "Not connected. Traffic is not going through Tor.")
        case .connecting(let percent):
            return String(localized: "Connecting — \(percent) per cent bootstrapped. Nothing is tunnelled yet.")
        case .tunnelled(let classes, let verified):
            let base = verified
                ? String(localized: "Traffic goes through Tor, confirmed by check.torproject.org.")
                : String(localized: "Traffic goes through Tor. The exit has not been verified this session.")
            guard classes > 0 else { return base }
            return base + " " + String(localized: "\(classes) destination group(s) bypass Tor and see your real IP.")

        }
    }
}

struct PresetRow: View {
    let posture: SecurityPosture
    @Binding var pending: SecurityPreset?
    @Environment(AppState.self) private var app

    var body: some View {
        SecuritySection(title: "Presets") {
            HStack(alignment: .top, spacing: 10) {
                ForEach(SecurityPreset.allCases) { preset in
                    Button {
                        pending = preset
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(verbatim: preset.title).font(.subheadline.weight(.semibold))
                                Spacer(minLength: 0)
                                if app.settings.securityPreset == preset.rawValue {
                                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.secondary)
                                }
                            }
                            if let current = posture.score, let projected = app.projectedScore(for: preset) {
                                Text(verbatim: "\(current) → \(projected)")
                                    .font(.title3.monospacedDigit())
                            }
                            Text(verbatim: preset.summary)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }
}

/// The honesty core: who is being defended against, how far that can go, and where it stands.
struct ThreatMatrixView: View {
    let posture: SecurityPosture

    var body: some View {
        SecuritySection(title: "What you are protected from") {
            VStack(spacing: 10) {
                ForEach(Adversary.allCases, id: \.rawValue) { adversary in
                    let coverage = posture.coverage[adversary] ?? 0
                    HStack(spacing: 10) {
                        Image(systemName: adversary.symbol)
                            .frame(width: 20)
                            .foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(verbatim: adversary.title).font(.subheadline)
                                Spacer(minLength: 0)
                                Text(verbatim: "\(coverage) / \(adversary.ceiling)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                            CoverageBar(value: coverage, ceiling: adversary.ceiling)
                        }
                    }
                }
            }
        }
    }
}

/// Two bars: what is reachable at all, and how much of it is reached. Green is unused on purpose —
/// if healthy were green, green would stop meaning anything.
struct CoverageBar: View {
    let value: Int
    let ceiling: Int

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Color.secondary.opacity(0.12))
                Capsule().fill(Color.secondary.opacity(0.22))
                    .frame(width: width * Double(ceiling) / 100)
                Capsule().fill(tint)
                    .frame(width: width * Double(value) / 100)
            }
        }
        .frame(height: 6)
    }

    private var tint: Color {
        let share = ceiling == 0 ? 0 : Double(value) / Double(ceiling)
        if share < 0.4 { return .red }
        if share < 0.75 { return .orange }
        return .secondary
    }
}

struct SecurityFindingRow: View {
    let finding: SecurityFinding
    let fix: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol)
                .foregroundStyle(tint)
                .frame(width: 18)
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: SecurityCopy.title(finding.id))
                    .font(.subheadline)
                Text(verbatim: SecurityCopy.detail(finding.id))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let detail = finding.detail {
                    Text(verbatim: detail)
                        .font(.caption.monospaced())
                        .foregroundStyle(.tertiary)
                }
            }
            Spacer(minLength: 8)
            if finding.penalty > 0 {
                Text(verbatim: "−\(finding.penalty)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            if finding.fix != .none {
                Button("Fix", action: fix)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
            }
        }
        .padding(.vertical, 8)
    }

    private var symbol: String {
        switch finding.severity {
        case .critical: "exclamationmark.octagon.fill"
        case .warning: "exclamationmark.triangle.fill"
        case .notice: "info.circle"
        case .info: "minus.circle"
        }
    }

    private var tint: Color {
        switch finding.severity {
        case .critical: .red
        case .warning: .orange
        case .notice, .info: .secondary
        }
    }
}

struct SecurityLimitsPanel: View {
    let limits: [SecurityLimit]

    var body: some View {
        SecuritySection(title: "What Veil cannot do") {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(limits) { limit in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "minus")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .frame(width: 14)
                        Text(verbatim: SecurityCopy.detail(limit.id))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }
}
