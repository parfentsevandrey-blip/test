import Foundation

/// What the app looks like from the engine's point of view. One snapshot, pulled on demand, so the
/// engine holds no Tor engine, no bridge, no proxy handle and no mirror of the settings.
struct SecurityInput: Equatable, Sendable {
    var isDemo = false
    var settings = AppSettings()
    var connection: ConnectionState = .disconnected
    var turboActive = false
    var killSwitchEngaged = false
    var proxyStatus: ProxyStatus = .off
    var bootstrapPercent = 0
    var activeTransport: AppSettings.Transport?
    var torCheck: TorCheckResult?
    var reachability: ReachabilityProbe.Report?
    var observedExitCountry: String?
    var pinnedExitCount = 0
    var paddingActive = false
    var paddingFailed = false
    var lanePoolActive = false
    var lanePoolSuspended: String?
    var transportDirectoryIsPrivate: Bool?
    var diagnosticsOnPasteboard = false
    var selfTest: SelfTestReport?
    var recentDirectHosts: [String] = []
}

/// Everything that is deliberately out of reach, stated rather than scored.
enum SecurityLimits {
    static let all: [SecurityLimit] = [
        SecurityLimit(id: "limit-apps-ignore-proxy", adversary: .localNetwork),
        SecurityLimit(id: "limit-udp", adversary: .localNetwork),
        SecurityLimit(id: "limit-bridge-unauthenticated", adversary: .localSoftware),
        SecurityLimit(id: "limit-global-observer", adversary: .trafficAnalysis),
        SecurityLimit(id: "limit-guard-persistence", adversary: .physicalAccess),
    ]
}

/// Derives a posture from an input. Pure, integer-only arithmetic, so the numbers are exact on
/// every machine and testable without a network.
enum SecurityPostureEvaluator {
    static func evaluate(_ input: SecurityInput) -> SecurityPosture {
        var posture = SecurityPosture()
        posture.limits = SecurityLimits.all
        guard !input.isDemo else {
            posture.findings = [SecurityFinding(id: "demo-mode", adversary: .localNetwork, severity: .info,
                                                penalty: 0)]
            posture.live = .unavailable
            return posture
        }
        let findings = buildFindings(input)
        posture.findings = findings.sorted { left, right in
            if left.severity != right.severity { return left.severity > right.severity }
            if left.penalty != right.penalty { return left.penalty > right.penalty }
            return left.id < right.id
        }
        var total = 0
        for adversary in Adversary.allCases {
            let lost = findings.filter { $0.adversary == adversary }.reduce(0) { $0 + $1.penalty }
            let coverage = max(0, adversary.ceiling - lost)
            posture.coverage[adversary] = coverage
            total += adversary.weight * coverage
        }
        let score = (total + 50) / 100
        posture.score = score
        posture.grade = SecurityPosture.grade(for: score)
        posture.bypassClasses = bypassClasses(input)
        posture.live = liveState(input, bypassClasses: posture.bypassClasses)
        return posture
    }

    /// With stock settings this is 1: the catalogue ships Apple's services direct, because App
    /// Store downloads and iCloud stall through exits.
    static func bypassClasses(_ input: SecurityInput) -> Int {
        let policy = input.settings.routingPolicy
        var classes = input.settings.youtubeMode == .tor ? 0 : 1
        // Iterated through the ordered catalogue, never through the dictionary: a dictionary's
        // order varies per process and would make findings and their text non-deterministic.
        for preset in ServiceCatalog.all {
            if let mode = policy.serviceModes[preset.id], mode != .tor { classes += 1 }
        }
        if !policy.customDirectDomains.isEmpty { classes += 1 }
        return classes
    }

    static func liveState(_ input: SecurityInput, bypassClasses: Int) -> SecurityPosture.Live {
        if input.turboActive { return .turbo }
        if input.killSwitchEngaged { return .blocked }
        switch input.connection {
        case .connected:
            return .tunnelled(bypassClasses: bypassClasses, verified: input.torCheck?.isTor == true)
        case .connecting:
            return .connecting(percent: input.bootstrapPercent)
        default:
            return .exposed
        }
    }

    /// Walked in source order, never by iterating a dictionary, so the order and the detail strings
    /// are the same on every run.
    static func buildFindings(_ input: SecurityInput) -> [SecurityFinding] {
        var findings: [SecurityFinding] = []
        let settings = input.settings
        let live = input.connection == .connected || input.turboActive
        let classes = bypassClasses(input)

        // A1 · someone on your network
        if input.turboActive {
            findings.append(SecurityFinding(id: "turbo-active", adversary: .localNetwork, severity: .critical,
                                            penalty: 90, detail: nil, fix: .stopTurbo))
            findings.append(SecurityFinding(id: "turbo-active-isp", adversary: .ispDPI, severity: .critical,
                                            penalty: 95, detail: nil, fix: .stopTurbo))
            findings.append(SecurityFinding(id: "turbo-active-exit", adversary: .exitRelay, severity: .critical,
                                            penalty: 100, detail: nil, fix: .stopTurbo))
        }
        if live, input.torCheck?.isTor == false {
            findings.append(SecurityFinding(id: "tor-check-failed", adversary: .localNetwork, severity: .critical,
                                            penalty: 60, detail: input.torCheck?.ip, fix: .reconnect))
            findings.append(SecurityFinding(id: "tor-check-failed-exit", adversary: .exitRelay, severity: .critical,
                                            penalty: 60, detail: input.torCheck?.ip, fix: .reconnect))
        }
        if !settings.configureSystemProxy {
            findings.append(SecurityFinding(id: "system-proxy-off", adversary: .localNetwork, severity: .critical,
                                            penalty: 60, detail: nil, fix: .enableSystemProxy))
        }
        if case .failed(let detail) = input.proxyStatus {
            findings.append(SecurityFinding(id: "proxy-failed", adversary: .localNetwork, severity: .critical,
                                            penalty: 40, detail: detail, fix: .reconnect))
        }
        if settings.killSwitch, !settings.configureSystemProxy {
            findings.append(SecurityFinding(id: "kill-switch-inert", adversary: .localNetwork, severity: .critical,
                                            penalty: 25, detail: nil, fix: .enableSystemProxy))
        }
        if !settings.killSwitch, settings.configureSystemProxy {
            findings.append(SecurityFinding(id: "kill-switch-off", adversary: .localNetwork, severity: .warning,
                                            penalty: 20, detail: nil, fix: .enableKillSwitch))
        }
        if classes > 0 {
            findings.append(SecurityFinding(id: "direct-routes-active", adversary: .localNetwork, severity: .warning,
                                            penalty: min(30, 8 * classes), detail: exposureDetail(input),
                                            fix: .clearBypasses))
            findings.append(SecurityFinding(id: "direct-routes-active-isp", adversary: .ispDPI, severity: .warning,
                                            penalty: min(20, 6 * classes), detail: exposureDetail(input),
                                            fix: .clearBypasses))
        }
        if !settings.closeSessionsOnKillSwitch {
            findings.append(SecurityFinding(id: "sessions-survive-killswitch", adversary: .localNetwork,
                                            severity: .notice, penalty: 5))
        }
        if !live, !input.killSwitchEngaged {
            findings.append(SecurityFinding(id: "tunnel-off", adversary: .localNetwork, severity: .info, penalty: 0))
        }

        // A2 · your provider
        if input.activeTransport == .direct {
            if input.reachability?.directLooksPossible == false {
                findings.append(SecurityFinding(id: "transport-direct-blocked", adversary: .ispDPI,
                                                severity: .warning, penalty: 30))
            } else {
                findings.append(SecurityFinding(id: "transport-fingerprintable", adversary: .ispDPI,
                                                severity: .notice, penalty: 15))
            }
        }
        if settings.checkForUpdates, !settings.updateCheckAfterConnect {
            findings.append(SecurityFinding(id: "update-check-direct", adversary: .ispDPI, severity: .notice,
                                            penalty: 8, detail: nil, fix: .deferUpdateCheck))
        }

        // A3 · the exit relay
        if settings.latencyTuning, input.pinnedExitCount > 0 {
            // Isolation gives separate circuits, but every one of them still ends at the same one
            // or two pinned exits, so the exit sees the whole session either way.
            findings.append(SecurityFinding(id: "shared-exit", adversary: .exitRelay,
                                            severity: settings.isolatePerSite ? .warning : .critical,
                                            penalty: settings.isolatePerSite ? 35 : 55,
                                            detail: "\(input.pinnedExitCount) pinned exit(s)",
                                            fix: .disableRelayPinning))
        } else if !settings.isolatePerSite, !input.lanePoolActive {
            findings.append(SecurityFinding(id: "no-isolation", adversary: .exitRelay, severity: .warning,
                                            penalty: 25, detail: nil, fix: .enableIsolation))
        } else if !settings.isolatePerSite, input.lanePoolActive {
            // Several measured circuits spread a session across exits without pinning any of them.
            findings.append(SecurityFinding(id: "partial-isolation", adversary: .exitRelay, severity: .notice,
                                            penalty: 10, detail: nil, fix: .enableIsolation))
        }
        if !settings.httpsOnly {
            findings.append(SecurityFinding(id: "plain-http-allowed", adversary: .exitRelay, severity: .warning,
                                            penalty: 15, detail: nil, fix: .enableHTTPSOnly))
        }
        if live, let wanted = settings.exitCountry, let observed = input.observedExitCountry,
           wanted.lowercased() != observed.lowercased() {
            findings.append(SecurityFinding(id: "exit-country-mismatch", adversary: .exitRelay, severity: .warning,
                                            penalty: 10, detail: "\(wanted) → \(observed)"))
        }
        if settings.route.excludedCountries.count >= 8 {
            findings.append(SecurityFinding(id: "over-restricted", adversary: .exitRelay, severity: .notice,
                                            penalty: 5, detail: "\(settings.route.excludedCountries.count) countries",
                                            fix: .clearExclusions))
        }

        // A4 · traffic analysis
        if !settings.paddingEnabled {
            findings.append(SecurityFinding(id: "padding-off", adversary: .trafficAnalysis, severity: .notice,
                                            penalty: 40, detail: nil, fix: .enablePadding))
        } else if input.paddingFailed || (live && !input.paddingActive) {
            findings.append(SecurityFinding(id: "padding-stalled", adversary: .trafficAnalysis, severity: .warning,
                                            penalty: 30, detail: nil, fix: .enablePadding))
        }

        // A5 · software on this Mac
        if input.transportDirectoryIsPrivate == false {
            findings.append(SecurityFinding(id: "pt-dir-world-readable", adversary: .localSoftware,
                                            severity: .warning, penalty: 15))
        }
        if input.diagnosticsOnPasteboard {
            findings.append(SecurityFinding(id: "diagnostics-on-pasteboard", adversary: .localSoftware,
                                            severity: .notice, penalty: 10))
        }
        if !settings.redactDiagnostics {
            findings.append(SecurityFinding(id: "diagnostics-unredacted", adversary: .localSoftware,
                                            severity: .notice, penalty: 5, detail: nil, fix: .redactDiagnostics))
        }

        // A6 · someone with this Mac
        if !settings.customBridges.isEmpty {
            findings.append(SecurityFinding(id: "bridges-stored-plaintext", adversary: .physicalAccess,
                                            severity: .notice, penalty: 20))
        }
        if settings.forgetPolicy == .off {
            findings.append(SecurityFinding(id: "caches-persist", adversary: .physicalAccess, severity: .info,
                                            penalty: 10, detail: nil, fix: .setForgetPolicy(.caches)))
        }
        if settings.forgetPolicy != .everything {
            // Deliberately offers no button: clearing this discards Tor's entry guard, and guards
            // that rotate every run expose a user to more of them over time.
            findings.append(SecurityFinding(id: "tor-state-persists", adversary: .physicalAccess,
                                            severity: .info, penalty: 10))
        }
        if settings.verboseLogs {
            findings.append(SecurityFinding(id: "verbose-logs", adversary: .physicalAccess, severity: .notice,
                                            penalty: 5, detail: nil, fix: .disableVerboseLogs))
        }
        return findings
    }

    static func exposureDetail(_ input: SecurityInput) -> String? {
        let policy = input.settings.routingPolicy
        var names: [String] = []
        if input.settings.youtubeMode != .tor { names.append("YouTube") }
        for preset in ServiceCatalog.all {
            if let mode = policy.serviceModes[preset.id], mode != .tor { names.append(preset.name) }
        }
        if !policy.customDirectDomains.isEmpty {
            names.append("\(policy.customDirectDomains.count) domain(s)")
        }
        return names.isEmpty ? nil : names.joined(separator: ", ")
    }
}
