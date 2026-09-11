import Foundation

/// Everything the ledger observes, in one value, plus the three pure functions that turn it into
/// the screen. Nothing here touches SwiftUI or the main actor, so the whole screen is testable.
struct DiagnosticSnapshot: Sendable {
    var now: Date = .now
    var connection: ConnectionState = .disconnected
    var connectStartedAt: Date?
    var connectedAt: Date?
    var transport: AppSettings.Transport = .auto
    var activeTransport: AppSettings.Transport?
    var attemptIndex = 0
    var attemptTotal = 0
    var bootstrap = BootstrapProgress()
    var stage: BootstrapStage = .launch
    var stageEnteredAt: Date?
    var stageBudget: Duration?
    var lastFailure: AttemptFailure?
    var warmth: WarmthProfile?
    var circuit: [CircuitHop] = []
    var circuitUpdatedAt: Date?
    var torCheck: TorCheckResult?
    var torCheckSeconds: TimeInterval?
    var isCheckingTor = false
    var proxyStatus: ProxyStatus = .off
    var killSwitchEngaged = false
    var configureSystemProxy = true
    var bridge = HTTPProxyBridge.Stats()
    var bridgeInFlight = 0
    var bridgeUpdatedAt: Date?
    var download: Double = 0
    var upload: Double = 0
    var trafficUpdatedAt: Date?
    var paddingRate: Double = 0
    var latency: LatencySummary?
    var lanes: LanePoolSnapshot?
    var connectivity: ConnectivityProbe.Report?
    var reachability: ReachabilityProbe.Report?
    var networkRepair: NetworkRepairStatus = .idle
    var primaryNetwork: NetworkReset.Primary?
    var turboActive = false
    var youtubeMode: RouteMode = .tor
    var bypassClasses = 0
    var isolatePerSite = false
    var pinnedExitCount = 0

    var isActive: Bool { connection.isActive || turboActive || killSwitchEngaged }

    // MARK: Rows

    /// Every row is always emitted, including the ones that do not apply: a diagnostic must never
    /// hide the row you came to look at.
    static func rows(from snapshot: DiagnosticSnapshot) -> [StageRow] {
        PipelineStage.allCases.map { row(for: $0, snapshot: snapshot) }
    }

    static func row(for stage: PipelineStage, snapshot s: DiagnosticSnapshot) -> StageRow {
        switch stage {
        case .network: return networkRow(s)
        case .reachability: return reachabilityRow(s)
        case .link: return linkRow(s)
        case .bootstrap: return bootstrapRow(s)
        case .circuit: return circuitRow(s)
        case .lanes: return lanesRow(s)
        case .verify: return verifyRow(s)
        case .systemProxy: return systemProxyRow(s)
        case .localProxy: return localProxyRow(s)
        case .throughput: return throughputRow(s)
        case .latency: return latencyRow(s)
        case .bypass: return bypassRow(s)
        }
    }

    private static func networkRow(_ s: DiagnosticSnapshot) -> StageRow {
        let state: StageState
        if s.networkRepair.isBusy {
            state = .working(nil)
        } else if let report = s.connectivity {
            if !report.internetReachable { state = .blocked }
            else if !report.nameResolution { state = .degraded }
            else { state = .ok }
        } else {
            state = .unknown
        }
        return StageRow(stage: .network, state: state,
                        value: s.primaryNetwork?.displayName ?? "—",
                        detail: s.connectivity?.summary,
                        ageSeconds: StageThresholds.age(of: s.connectivity?.date, stage: .network, now: s.now))
    }

    private static func reachabilityRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.transport == .auto else {
            return StageRow(stage: .reachability, state: .notApplicable,
                            value: AppState.name(of: s.transport))
        }
        guard let report = s.reachability else {
            return StageRow(stage: .reachability, state: .unknown, value: "—")
        }
        let state: StageState
        if report.reachable == 0 { state = .blocked }
        else if report.reachable == 1 { state = .degraded }     // directLooksPossible needs two
        else { state = .ok }
        return StageRow(stage: .reachability, state: state,
                        value: "\(report.reachable)/\(report.total)",
                        detail: report.directLooksPossible
                            ? "Tor is reachable without a bridge on this network."
                            : "Directory authorities did not answer; this network appears to block Tor.")
    }

    private static func linkRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.connection.isActive else {
            return StageRow(stage: .link, state: .notApplicable, value: "—")
        }
        let name = AppState.name(of: s.activeTransport ?? s.transport)
        if s.connection == .connected {
            return StageRow(stage: .link, state: .ok, value: name)
        }
        var detail: String?
        if s.attemptTotal > 1 {
            detail = "attempt \(s.attemptIndex + 1) of \(s.attemptTotal)"
        }
        if let failure = s.lastFailure {
            detail = [detail, failure.summary].compactMap { $0 }.joined(separator: " · ")
        }
        let state: StageState = (s.lastFailure?.isFirstHop ?? false) ? .degraded : .working(nil)
        return StageRow(stage: .link, state: state, value: name, detail: detail)
    }

    private static func bootstrapRow(_ s: DiagnosticSnapshot) -> StageRow {
        if s.connection == .connected {
            return StageRow(stage: .bootstrap, state: .ok, value: "100%",
                            detail: s.warmth?.summary)
        }
        guard s.connection == .connecting else {
            return StageRow(stage: .bootstrap, state: .notApplicable, value: "—", detail: s.warmth?.summary)
        }
        let inStage = s.stageEnteredAt.map { s.now.timeIntervalSince($0) } ?? 0
        let budget = s.stageBudget.map { BootstrapWatchdog.seconds($0) } ?? 0
        // Fires before Tor gives up, not after.
        let stuck = budget > 0 && inStage > budget * StageThresholds.stallShareOfBudget
        var detail = "\(s.stage.title) · \(Int(inStage)) s"
        if budget > 0 { detail += " of a \(Int(budget)) s budget" }
        return StageRow(stage: .bootstrap,
                        state: stuck ? .stuck : .working(Double(s.bootstrap.percent) / 100),
                        value: "\(s.bootstrap.percent)%",
                        detail: detail)
    }

    private static func circuitRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.connection == .connected else {
            return StageRow(stage: .circuit, state: s.circuit.isEmpty ? .unknown : .ok,
                            value: s.circuit.isEmpty ? "—" : "\(s.circuit.count) hops")
        }
        guard !s.circuit.isEmpty else {
            return StageRow(stage: .circuit, state: .working(nil), value: "building…")
        }
        let stale = s.circuitUpdatedAt.map { s.now.timeIntervalSince($0) > StageThresholds.circuitStaleSeconds } ?? false
        let path = s.circuit.map { hop in hop.countryCode?.uppercased() ?? hop.nickname }.joined(separator: " → ")
        return StageRow(stage: .circuit, state: stale ? .degraded : .ok,
                        value: "\(s.circuit.count) hops",
                        detail: path,
                        ageSeconds: StageThresholds.age(of: s.circuitUpdatedAt, stage: .circuit, now: s.now))
    }

    private static func lanesRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard let lanes = s.lanes, lanes.enabled else {
            return StageRow(stage: .lanes, state: .notApplicable, value: "off",
                            detail: "One circuit carries every connection.")
        }
        if let reason = lanes.suspended {
            return StageRow(stage: .lanes, state: .degraded, value: "suspended", detail: reason)
        }
        let ready = lanes.readyLanes
        let state: StageState
        if ready == 0 { state = .working(nil) }
        else if ready == 1 { state = .degraded }
        else { state = .ok }
        var detail = "\(ready) of \(lanes.rows.count) measured"
        if let best = lanes.bestP50 {
            detail += " · best \(Int((best * 1000).rounded())) ms"
        }
        if let worst = lanes.rows.compactMap(\.p50).max(), let best = lanes.bestP50,
           worst >= best * StageThresholds.laneSlowRatio, worst >= StageThresholds.laneSlowFloorSeconds {
            detail += " · slowest \(Int((worst * 1000).rounded())) ms"
        }
        return StageRow(stage: .lanes, state: state, value: "\(ready)", detail: detail)
    }

    private static func verifyRow(_ s: DiagnosticSnapshot) -> StageRow {
        if s.isCheckingTor {
            return StageRow(stage: .verify, state: .working(nil), value: "checking…")
        }
        guard let check = s.torCheck else {
            return StageRow(stage: .verify, state: s.connection == .connected ? .unknown : .notApplicable,
                            value: "—")
        }
        return StageRow(stage: .verify, state: check.isTor ? .ok : .blocked,
                        value: check.ip,
                        detail: check.isTor
                            ? "Exit used for this check, not for your browsing."
                            : "check.torproject.org says this traffic is NOT going through Tor.")
    }

    private static func systemProxyRow(_ s: DiagnosticSnapshot) -> StageRow {
        if s.killSwitchEngaged {
            return StageRow(stage: .systemProxy, state: .blocked, value: "blocking",
                            detail: "Kill switch engaged: proxied apps are blocked until Tor is back.")
        }
        switch s.proxyStatus {
        case .configured(let services):
            return StageRow(stage: .systemProxy, state: .ok, value: services.joined(separator: ", "))
        case .manual:
            return StageRow(stage: .systemProxy, state: .notApplicable, value: "manual")
        case .failed(let detail):
            return StageRow(stage: .systemProxy, state: .degraded, value: "failed", detail: detail)
        case .off:
            let expected = s.configureSystemProxy && s.connection.isActive
            return StageRow(stage: .systemProxy, state: expected ? .degraded : .notApplicable, value: "off")
        }
    }

    private static func localProxyRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.isActive else {
            return StageRow(stage: .localProxy, state: .notApplicable, value: "—")
        }
        let failed = s.bridge.torFailed
        let opened = s.bridge.tor + s.bridge.direct
        let state: StageState
        if s.bridgeInFlight >= StageThresholds.localProxyWedgeInFlight, opened == 0 {
            state = .stuck
        } else if failed * StageThresholds.localProxyFailureRatio > max(1, opened),
                  opened + failed >= StageThresholds.localProxyMinimumSamples {
            state = .degraded
        } else {
            state = .ok
        }
        var detail = "\(s.bridge.tor) through Tor, \(s.bridge.direct) direct, \(s.bridge.blocked) refused"
        if s.bridge.torRetried > 0 || s.bridge.torHedged > 0 {
            detail += " · \(s.bridge.torHedged) re-raced, \(s.bridge.torRetried) retried"
        }
        return StageRow(stage: .localProxy, state: state, value: "\(s.bridgeInFlight) open", detail: detail,
                        ageSeconds: StageThresholds.age(of: s.bridgeUpdatedAt, stage: .localProxy, now: s.now))
    }

    private static func throughputRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.isActive else {
            return StageRow(stage: .throughput, state: .notApplicable, value: "—")
        }
        // Idle is not a fault. The old animation glided a comet at 0 B/s; this states the difference.
        let idle = s.bridgeInFlight == 0
        let stalled = !idle && s.download == 0 && s.upload == 0
        let value = "↓ \(Self.rate(s.download))  ↑ \(Self.rate(s.upload))"
        return StageRow(stage: .throughput, state: stalled ? .degraded : .ok, value: value,
                        detail: idle ? "No connections open — nothing to carry."
                                     : (stalled ? "Connections are open but nothing has moved." : nil),
                        ageSeconds: StageThresholds.age(of: s.trafficUpdatedAt, stage: .throughput, now: s.now))
    }

    private static func latencyRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.connection == .connected else {
            return StageRow(stage: .latency, state: .notApplicable, value: "—")
        }
        if let best = s.lanes?.bestP50 {
            return StageRow(stage: .latency, state: .ok, value: "\(Int((best * 1000).rounded())) ms",
                            detail: "Median of the fastest measured circuit — what your next connection gets.")
        }
        guard let summary = s.latency else {
            return StageRow(stage: .latency, state: .unknown, value: "—")
        }
        return StageRow(stage: .latency, state: summary.isStable ? .ok : .degraded,
                        value: "\(Int((summary.median * 1000).rounded())) ms",
                        detail: "jitter \(Int((summary.jitter * 1000).rounded())) ms over \(summary.samples) samples")
    }

    private static func bypassRow(_ s: DiagnosticSnapshot) -> StageRow {
        guard s.bypassClasses > 0 else {
            return StageRow(stage: .bypass, state: .notApplicable, value: "none")
        }
        // Never `.ok`: a bypass is an exposure, and the row says so.
        return StageRow(stage: .bypass, state: .degraded, value: "\(s.bypassClasses)",
                        detail: "These destinations see your real IP address.")
    }

    static func rate(_ bytesPerSecond: Double) -> String {
        let units = ["B/s", "KB/s", "MB/s", "GB/s"]
        var value = bytesPerSecond
        var index = 0
        while value >= 1024, index < units.count - 1 {
            value /= 1024
            index += 1
        }
        return index == 0 ? "\(Int(value)) \(units[index])" : String(format: "%.1f %@", value, units[index])
    }

    // MARK: Bottleneck and verdict

    /// Highest severity first, ties broken by chain order — a downstream number is meaningless when
    /// an upstream stage is broken — with one override: a failed exit verification outranks every
    /// latency figure, because "this is not going through Tor" is not a slowdown.
    static func bottleneck(in rows: [StageRow]) -> StageRow? {
        if let verify = rows.first(where: { $0.stage == .verify }), verify.state == .blocked {
            return verify
        }
        let ranked = rows.filter { $0.state.severity > 0 }
        guard let worst = ranked.map(\.state.severity).max(), worst > 0 else { return nil }
        return ranked.first { $0.state.severity == worst }
    }

    /// The single most valuable string on the screen.
    static func headline(rows: [StageRow], snapshot s: DiagnosticSnapshot) -> String {
        guard let bottleneck = bottleneck(in: rows) else {
            if s.turboActive {
                return String(localized: "YouTube Turbo is on. Tor is not running and every site sees your real IP address.")
            }
            guard s.connection == .connected else {
                return String(localized: "Not connected. Traffic is not going through Tor.")
            }
            let exit = s.torCheck?.ip ?? s.circuit.last?.nickname ?? "—"
            let milliseconds = s.lanes?.bestP50.map { Int(($0 * 1000).rounded()) }
                ?? s.latency.map { Int(($0.median * 1000).rounded()) } ?? 0
            let open = s.bridgeInFlight
            return String(localized: "No problems. Exit \(exit) · \(milliseconds) ms · \(open) open connections.")
        }
        let detail = bottleneck.detail ?? bottleneck.value
        switch bottleneck.stage {
        case .verify:
            return String(localized: "check.torproject.org says this traffic is NOT going through Tor (\(bottleneck.value)).")
        case .network:
            let summary = s.connectivity?.summary ?? bottleneck.value
            return String(localized: "The Internet is not answering on \(bottleneck.value) — \(summary).")
        case .reachability:
            return String(localized: "This network appears to block Tor: \(bottleneck.value) directory authorities answered.")
        case .link:
            return String(localized: "\(bottleneck.value) has not reached a relay yet — \(detail).")
        case .bootstrap:
            let percent = s.bootstrap.percent
            return String(localized: "Stuck at \(percent) per cent — \(detail).")
        case .circuit:
            return String(localized: "The circuit has not been refreshed: \(detail).")
        case .lanes:
            return String(localized: "Measured circuits: \(detail).")
        case .systemProxy:
            return s.killSwitchEngaged
                ? String(localized: "Kill switch engaged: proxied apps are blocked until Tor is back.")
                : String(localized: "The system proxy is not configured, so apps are not going through Veil.")
        case .localProxy:
            return String(localized: "Connections through Veil are failing — \(detail).")
        case .throughput:
            let open = s.bridgeInFlight
            return String(localized: "Connected, but nothing has moved with \(open) connections open.")
        case .latency:
            return String(localized: "The route is unsteady: \(detail).")
        case .bypass:
            let classes = s.bypassClasses
            return String(localized: "Connected. \(classes) destination group(s) bypass Tor and see your real IP.")
        }
    }
}
