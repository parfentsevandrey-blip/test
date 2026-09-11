import SwiftUI

/// One section per stage: the facts behind the row, and the actions that address it.
struct StageDetail: View {
    @Environment(AppState.self) private var app
    let row: StageRow
    let snapshot: DiagnosticSnapshot
    var openActivity: @MainActor () -> Void
    var openLocations: @MainActor () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(row.stage.title)
                        .font(.title3.weight(.semibold))
                    if let detail = row.detail {
                        Text(verbatim: detail)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                GroupBox {
                    VStack(spacing: 8) {
                        ForEach(Array(facts.enumerated()), id: \.offset) { index, fact in
                            DetailRow(label: fact.label, value: fact.value)
                            if index < facts.count - 1 { Divider() }
                        }
                    }
                    .padding(4)
                }
                if !actions.isEmpty {
                    HStack(spacing: 8) {
                        ForEach(Array(actions.enumerated()), id: \.offset) { _, action in
                            Button(action.title) { action.run() }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                        }
                        Spacer(minLength: 0)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollContentBackground(.hidden)
    }

    private struct Action {
        let title: LocalizedStringKey
        let run: @MainActor () -> Void
    }

    private var facts: [(label: LocalizedStringKey, value: String)] {
        switch row.stage {
        case .network:
            var rows: [(LocalizedStringKey, String)] = [("Interface", snapshot.primaryNetwork?.displayName ?? "—")]
            if let report = snapshot.connectivity {
                rows.append(("Addresses answered", "\(report.reachedByAddress)/\(report.addressTargets)"))
                rows.append(("Name resolution", report.nameResolution ? "working" : "failed"))
                rows.append(("Measured", "\(Int(report.elapsed * 1000)) ms"))
            }
            return rows
        case .reachability:
            guard let report = snapshot.reachability else { return [("Probed", "not yet")] }
            return [("Directory authorities", "\(report.reachable)/\(report.total)"),
                    ("Direct Tor", report.directLooksPossible ? "looks possible" : "looks blocked")]
        case .link:
            var rows: [(LocalizedStringKey, String)] = [
                ("Transport", AppState.name(of: snapshot.activeTransport ?? snapshot.transport)),
            ]
            if snapshot.attemptTotal > 1 {
                rows.append(("Attempt", "\(snapshot.attemptIndex + 1) of \(snapshot.attemptTotal)"))
            }
            if let failure = snapshot.lastFailure { rows.append(("Last failure", failure.summary)) }
            return rows
        case .bootstrap:
            var rows: [(LocalizedStringKey, String)] = [
                ("Progress", "\(snapshot.bootstrap.percent)%"),
                ("Phase", snapshot.stage.title),
            ]
            if let budget = snapshot.stageBudget {
                rows.append(("Stage budget", "\(Int(BootstrapWatchdog.seconds(budget))) s"))
            }
            if let warmth = snapshot.warmth {
                rows.append(("Tor state", warmth.summary))
            }
            return rows
        case .circuit:
            guard !snapshot.circuit.isEmpty else { return [("Hops", "none yet")] }
            return snapshot.circuit.map { hop in
                (LocalizedStringKey(stringLiteral: hop.role.title), "\(hop.flag) \(hop.nickname)")
            }
        case .lanes:
            guard let lanes = snapshot.lanes, lanes.enabled else {
                return [("Measured circuits", "off")]
            }
            var rows: [(LocalizedStringKey, String)] = lanes.rows.map { lane in
                let latency = lane.p50Milliseconds.map { "\($0) ms" } ?? "measuring…"
                return (LocalizedStringKey(stringLiteral: "#\(lane.id)"),
                        "\(latency) · \(lane.inFlight) open · \(lane.state.rawValue)")
            }
            rows.append(("Assignments", "\(lanes.assignmentsByScore) by speed, \(lanes.assignmentsByAffinity) by site, \(lanes.assignmentsLegacy) unmeasured"))
            rows.append(("Re-raced", "\(lanes.hedgesStarted) started, \(lanes.hedgesWon) won"))
            if let last = lanes.lastRetirement { rows.append(("Last replacement", last)) }
            return rows
        case .verify:
            guard let check = snapshot.torCheck else { return [("Checked", "not yet")] }
            var rows: [(LocalizedStringKey, String)] = [
                ("Through Tor", check.isTor ? "yes" : "NO"),
                ("Address seen", check.ip),
            ]
            if let seconds = snapshot.torCheckSeconds {
                rows.append(("Took", "\(Int(seconds * 1000)) ms"))
            }
            return rows
        case .systemProxy:
            return [("Status", row.value),
                    ("Configured by Veil", snapshot.configureSystemProxy ? "yes" : "no"),
                    ("Kill switch", snapshot.killSwitchEngaged ? "engaged" : "armed")]
        case .localProxy:
            return [("Open now", "\(snapshot.bridgeInFlight)"),
                    ("Through Tor", "\(snapshot.bridge.tor)"),
                    ("Direct", "\(snapshot.bridge.direct)"),
                    ("Refused", "\(snapshot.bridge.blocked)"),
                    ("Failed", "\(snapshot.bridge.torFailed)")]
        case .throughput:
            return [("Download", DiagnosticSnapshot.rate(snapshot.download)),
                    ("Upload", DiagnosticSnapshot.rate(snapshot.upload)),
                    ("Padding", DiagnosticSnapshot.rate(snapshot.paddingRate))]
        case .latency:
            guard let summary = snapshot.latency else {
                if let best = snapshot.lanes?.bestP50 {
                    return [("Fastest circuit", "\(Int(best * 1000)) ms")]
                }
                return [("Measured", "not yet")]
            }
            return [("Median", "\(Int(summary.median * 1000)) ms"),
                    ("Best", "\(Int(summary.best * 1000)) ms"),
                    ("Jitter", "\(Int(summary.jitter * 1000)) ms"),
                    ("Samples", "\(summary.samples), \(summary.failures) failed")]
        case .bypass:
            return [("Destination groups outside Tor", "\(snapshot.bypassClasses)"),
                    ("YouTube", snapshot.youtubeMode == .tor ? "through Tor" : "direct")]
        }
    }

    private var actions: [Action] {
        switch row.stage {
        case .network:
            return [Action(title: "Check again") { app.probeInternet() },
                    Action(title: "Reset the network") { app.resetNetwork() }]
        case .reachability:
            return [Action(title: "Probe again") { app.probeReachability() }]
        case .link, .bootstrap:
            return [Action(title: "Reconnect") { app.reconnect() },
                    Action(title: "Open Activity") { openActivity() }]
        case .circuit:
            return [Action(title: "New identity") { app.requestNewIdentity() },
                    Action(title: "Route settings") { openLocations() }]
        case .lanes:
            return [Action(title: "Replace every circuit") { app.replaceMeasuredCircuits() }]
        case .verify:
            return [Action(title: "Check the exit") { app.runTorCheck() }]
        case .systemProxy:
            return [Action(title: "Reconnect") { app.reconnect() }]
        case .localProxy, .throughput:
            return [Action(title: "Open Activity") { openActivity() }]
        case .latency:
            return [Action(title: "Measure now") { app.latency.measureNow() }]
        case .bypass:
            var actions = [Action(title: "Send everything through Tor") { app.applyFix(.clearBypasses) }]
            if snapshot.turboActive {
                actions.append(Action(title: "Stop YouTube Turbo") { app.stopTurbo() })
            } else if snapshot.connection == .disconnected {
                actions.append(Action(title: "YouTube Turbo") { app.startTurbo() })
            }
            return actions
        }
    }
}
