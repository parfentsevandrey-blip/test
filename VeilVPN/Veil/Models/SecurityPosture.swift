import Foundation

/// Who the protection is against. The weights sum to 100 and the ceilings say what a userspace
/// proxy can structurally reach — which is why the score can never be 100.
enum Adversary: String, CaseIterable, Codable, Sendable {
    case localNetwork, ispDPI, exitRelay, trafficAnalysis, localSoftware, physicalAccess

    var weight: Int {
        switch self {
        case .localNetwork: 25
        case .ispDPI: 15
        case .exitRelay: 25
        case .trafficAnalysis: 10
        case .localSoftware: 15
        case .physicalAccess: 10
        }
    }

    /// What Veil can reach at best. Nothing here is aspirational: an app that configures the system
    /// proxy cannot stop software that ignores it, and padding that never delays a real packet
    /// cannot defeat a global observer.
    var ceiling: Int {
        switch self {
        case .localNetwork: 90
        case .ispDPI: 95
        case .exitRelay: 100
        case .trafficAnalysis: 70
        case .localSoftware: 75
        case .physicalAccess: 80
        }
    }

    var title: String {
        switch self {
        case .localNetwork: "Someone on your network"
        case .ispDPI: "Your provider"
        case .exitRelay: "The exit relay"
        case .trafficAnalysis: "Traffic analysis"
        case .localSoftware: "Software on this Mac"
        case .physicalAccess: "Someone with this Mac"
        }
    }

    var symbol: String {
        switch self {
        case .localNetwork: "wifi"
        case .ispDPI: "antenna.radiowaves.left.and.right"
        case .exitRelay: "arrow.up.forward.square"
        case .trafficAnalysis: "waveform.path.ecg"
        case .localSoftware: "app.badge"
        case .physicalAccess: "externaldrive"
        }
    }
}

struct SecurityFinding: Identifiable, Equatable, Sendable {
    enum Severity: Int, Comparable, Sendable {
        case info = 0, notice = 1, warning = 2, critical = 3
        static func < (lhs: Severity, rhs: Severity) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    /// What tapping the row does. `.none` means the finding is a statement of fact, not a defect.
    enum Fix: Equatable, Sendable {
        case none
        case apply(SecurityPreset)
        case enableKillSwitch, enableSystemProxy, enableIsolation, disableRelayPinning
        case enablePadding, clearBypasses, enableHTTPSOnly
        case clearExclusions, deferUpdateCheck, redactDiagnostics, disableVerboseLogs
        case setForgetPolicy(AppSettings.ForgetPolicy)
        case reconnect, stopTurbo, runSelfTest
    }

    let id: String
    let adversary: Adversary
    let severity: Severity
    /// Subtracted from that adversary's ceiling. Zero for rows that only state a fact.
    let penalty: Int
    var detail: String?
    var fix: Fix = .none
}

/// A limit of the design, not a defect: always shown, never scored.
struct SecurityLimit: Identifiable, Equatable, Sendable {
    let id: String
    let adversary: Adversary
}

struct SecurityPosture: Equatable, Sendable {
    enum Grade: String, Sendable { case unknown, exposed, basic, solid, hardened }

    /// What is true right now, as opposed to how things are configured.
    enum Live: Equatable, Sendable {
        case unavailable
        case exposed
        case blocked
        case turbo
        case connecting(percent: Int)
        case tunnelled(bypassClasses: Int, verified: Bool)
    }

    var coverage: [Adversary: Int] = [:]
    var findings: [SecurityFinding] = []
    var limits: [SecurityLimit] = []
    var score: Int?
    var grade: Grade = .unknown
    var live: Live = .unavailable
    var bypassClasses = 0

    /// 25×90 + 15×95 + 25×100 + 10×70 + 15×75 + 10×80, over 100. Not a magic number: it falls out
    /// of the ceilings, and `coverage[a] <= a.ceiling` by construction makes it a theorem.
    static let absoluteMaximum = 88
    /// Reaching 88 needs `forgetPolicy == .everything`, which throws away Tor's entry guard. Veil
    /// never recommends that, so the best it will ever suggest is 87.
    static let recommendedMaximum = 87

    static func grade(for score: Int) -> Grade {
        switch score {
        case ..<40: .exposed
        case ..<65: .basic
        case ..<82: .solid
        default: .hardened
        }
    }

    var gradeTitle: String {
        switch grade {
        case .unknown: "Unknown"
        case .exposed: "Exposed"
        case .basic: "Basic"
        case .solid: "Solid"
        case .hardened: "Hardened"
        }
    }

    var criticalFindings: [SecurityFinding] { findings.filter { $0.severity == .critical } }
}
