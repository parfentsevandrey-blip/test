import SwiftUI

/// The path a request takes, from this Mac outward. The order is chain order and never changes, so
/// a row's position always means the same thing.
enum PipelineStage: String, CaseIterable, Identifiable, Sendable {
    case network, reachability, link, bootstrap, circuit, lanes
    case verify, systemProxy, localProxy, throughput, latency, bypass

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .network: "Network"
        case .reachability: "Tor reachable"
        case .link: "Bridge / guard link"
        case .bootstrap: "Bootstrap"
        case .circuit: "Circuit"
        case .lanes: "Measured circuits"
        case .verify: "Exit verified"
        case .systemProxy: "System proxy"
        case .localProxy: "Local proxy"
        case .throughput: "Throughput"
        case .latency: "Route latency"
        case .bypass: "Direct routing"
        }
    }
}

/// How a stage is doing. Green is deliberately unused: if healthy were green, green would stop
/// meaning anything. Healthy is quiet.
enum StageState: Equatable, Sendable {
    case ok
    case notApplicable
    case unknown
    /// A determinate fraction, or nil for an indeterminate spinner.
    case working(Double?)
    case degraded
    case stuck
    case blocked

    var severity: Int {
        switch self {
        case .blocked: 4
        case .stuck: 3
        case .degraded: 2
        case .working: 1
        case .ok, .notApplicable, .unknown: 0
        }
    }

    /// Nil for `.working`, where the caller renders a ProgressView instead.
    var symbolName: String? {
        switch self {
        case .ok: "circle.fill"
        case .notApplicable: nil
        case .unknown: "minus"
        case .working: nil
        case .degraded: "exclamationmark.triangle.fill"
        case .stuck: "clock.badge.exclamationmark.fill"
        case .blocked: "exclamationmark.octagon.fill"
        }
    }

    var tint: Color {
        switch self {
        case .blocked: .red
        case .stuck, .degraded: .orange
        case .ok: .secondary
        case .working: .secondary
        case .unknown, .notApplicable: Color.secondary.opacity(0.55)
        }
    }
}

/// One row of the ledger.
struct StageRow: Identifiable, Equatable, Sendable {
    let stage: PipelineStage
    let state: StageState
    /// The right-hand column.
    var value: String = "—"
    /// Rendered only when this row is the bottleneck, so eleven quiet rows stay quiet.
    var detail: String?
    /// Non-nil means the underlying fact is older than this stage's own cadence.
    var ageSeconds: TimeInterval?

    var id: PipelineStage { stage }
}
