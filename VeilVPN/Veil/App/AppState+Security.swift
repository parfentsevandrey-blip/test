import AppKit
import Foundation

/// AppState's side of the security seam: one snapshot out, one bulk write in, and the handful of
/// imperative verbs the Security section needs. The engine owns intent; AppState owns machinery.
extension AppState {
    var securityInput: SecurityInput {
        var input = SecurityInput()
        input.isDemo = isDemo
        input.settings = settings
        input.connection = connection
        input.turboActive = turboActive
        input.killSwitchEngaged = killSwitchEngaged
        input.proxyStatus = proxyStatus
        input.bootstrapPercent = bootstrap.percent
        input.activeTransport = activeTransport
        input.torCheck = torCheck
        input.reachability = reachability
        input.observedExitCountry = exitHop?.countryCode
        input.pinnedExitCount = tuner.pinnedExits.count
        input.paddingActive = padding.status.isActive
        input.paddingFailed = padding.status.isFailed
        input.lanePoolActive = (lanes?.readyLanes ?? 0) > 0
        input.lanePoolSuspended = lanes?.suspended
        input.transportDirectoryIsPrivate = transportDirectoryIsPrivate
        input.selfTest = selfTestReport
        return input
    }

    var securityPosture: SecurityPosture {
        SecurityPostureEvaluator.evaluate(securityInput)
    }

    /// What the score would become if a preset were applied — shown before anything is changed.
    func projectedScore(for preset: SecurityPreset) -> Int? {
        var input = securityInput
        input.settings = preset.applied(to: settings)
        if input.settings.latencyTuning == false { input.pinnedExitCount = 0 }
        return SecurityPostureEvaluator.evaluate(input).score
    }

    func applyPreset(_ preset: SecurityPreset) {
        let updated = preset.applied(to: settings)
        let wasPadding = settings.paddingEnabled
        settings = updated
        pushSecuritySideEffects(paddingChanged: wasPadding != updated.paddingEnabled)
        applyRouteIfConnected()
        append(.veil(.notice, "Security preset applied: \(preset.title)"))
    }

    /// Applies a finding's one-tap remedy. Every branch is a setting the user could also reach by
    /// hand; nothing here does anything the Security section does not already name.
    func applyFix(_ fix: SecurityFinding.Fix) {
        switch fix {
        case .none:
            return
        case .apply(let preset):
            applyPreset(preset)
        case .enableKillSwitch:
            setKillSwitch(true)
        case .enableSystemProxy:
            settings.configureSystemProxy = true
            append(.veil(.notice, "System proxy configuration switched on; reconnect to apply it"))
        case .enableIsolation:
            setIsolatePerSite(true)
        case .disableRelayPinning:
            setLatencyTuning(false)
        case .enablePadding:
            setPaddingEnabled(false)
            setPaddingEnabled(true)
        case .clearBypasses:
            // Emptying the dictionary would put Apple straight back to direct, because the policy
            // merges over the catalogue's own defaults — so write an explicit `.tor` for each.
            settings.serviceRoutes = SecurityPreset.allServicesThroughTor
            settings.youtubeMode = .tor
            settings.customDirectDomains = ""
            httpBridgePolicyChanged()
            append(.veil(.notice, "Every bypass removed; all traffic goes through Tor"))
        case .enableHTTPSOnly:
            setHTTPSOnly(true)
        case .clearExclusions:
            settings.excludedCountries = []
            settings.avoidFiveEyes = false
            applyRouteIfConnected()
        case .deferUpdateCheck:
            settings.updateCheckAfterConnect = true
        case .redactDiagnostics:
            settings.redactDiagnostics = true
        case .disableVerboseLogs:
            settings.verboseLogs = false
        case .setForgetPolicy(let policy):
            settings.forgetPolicy = policy
        case .reconnect:
            reconnect()
        case .stopTurbo:
            stopTurbo()
        case .runSelfTest:
            runSelfTest()
        }
    }

    func setHTTPSOnly(_ enabled: Bool) {
        guard settings.httpsOnly != enabled else { return }
        settings.httpsOnly = enabled
        httpBridgePolicyChanged()
    }

    func setCloseSessionsOnKillSwitch(_ enabled: Bool) {
        settings.closeSessionsOnKillSwitch = enabled
    }

    func setRedactDiagnostics(_ enabled: Bool) {
        settings.redactDiagnostics = enabled
    }

    func setUpdateCheckAfterConnect(_ enabled: Bool) {
        settings.updateCheckAfterConnect = enabled
    }

    func setForgetPolicy(_ policy: AppSettings.ForgetPolicy) {
        settings.forgetPolicy = policy
    }

    /// Clears everything Veil has learned about the networks it has been on.
    func forgetConnectHistory() {
        Task {
            await ConnectHistoryStore.shared.forgetEverything()
            await MainActor.run { self.append(.veil(.notice, "Connection history cleared")) }
        }
    }

    func runSelfTest() {
        guard !isSelfTesting else { return }
        isSelfTesting = true
        Task { [weak self] in
            guard let self else { return }
            defer { isSelfTesting = false }
            var results = SelfTest.localChecks(settings: settings, ports: ports, proxyStatus: proxyStatus,
                                               transportDirectory: transportDirectory)
            results.append(await SelfTest.isolationCheck(poolPort: lanes?.poolPort))
            results.append(await SelfTest.dnsCheck(socksPort: connection == .connected ? ports?.socks : nil))
            selfTestReport = SelfTestReport.reduce(results, at: .now)
            append(.veil(.info, "Self-test: \(results.filter { $0.verdict == .pass }.count)/\(results.count) passed"))
        }
    }
}
