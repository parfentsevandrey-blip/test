import Foundation

/// The one place AppState is turned into the ledger's input. Computed on demand rather than stored:
/// with `@Observable`, a stored aggregate would make every reader re-render on every unrelated
/// change, and twelve rows of string formatting once a second is nothing.
extension AppState {
    func diagnostics(at now: Date = .now) -> DiagnosticSnapshot {
        var snapshot = DiagnosticSnapshot()
        snapshot.now = now
        snapshot.connection = connection
        snapshot.connectStartedAt = connectStartedAt
        snapshot.connectedAt = connectedAt
        snapshot.transport = settings.transport
        snapshot.activeTransport = activeTransport
        snapshot.attemptIndex = attemptIndex
        snapshot.attemptTotal = plannedQueue.count
        snapshot.bootstrap = bootstrap
        snapshot.stage = currentStage
        snapshot.stageEnteredAt = stageEnteredAt
        snapshot.stageBudget = stageBudget
        snapshot.lastFailure = lastAttemptFailure
        snapshot.warmth = warmth
        snapshot.circuit = circuit
        snapshot.circuitUpdatedAt = circuitUpdatedAt
        snapshot.torCheck = torCheck
        snapshot.torCheckSeconds = torCheckSeconds
        snapshot.isCheckingTor = isCheckingTor
        snapshot.proxyStatus = proxyStatus
        snapshot.killSwitchEngaged = killSwitchEngaged
        snapshot.configureSystemProxy = settings.configureSystemProxy
        snapshot.bridge = bridgeStats
        snapshot.bridgeInFlight = bridgeInFlight
        snapshot.bridgeUpdatedAt = bridgeUpdatedAt
        snapshot.download = traffic.downloadRate
        snapshot.upload = traffic.uploadRate
        snapshot.trafficUpdatedAt = trafficUpdatedAt
        snapshot.paddingRate = padding.status.isActive ? padding.rate : 0
        snapshot.latency = latency.summary
        snapshot.lanes = lanes
        snapshot.connectivity = connectivity
        snapshot.reachability = reachability
        snapshot.networkRepair = networkRepair
        snapshot.primaryNetwork = primaryNetwork
        snapshot.turboActive = turboActive
        snapshot.youtubeMode = settings.youtubeMode
        snapshot.bypassClasses = SecurityPostureEvaluator.bypassClasses(securityInput)
        snapshot.isolatePerSite = settings.isolatePerSite
        snapshot.pinnedExitCount = tuner.pinnedExits.count
        return snapshot
    }

    /// Replaces every measured circuit at once. New connections take fresh ones; anything already
    /// open keeps the circuit it was given.
    func replaceMeasuredCircuits() {
        httpBridgeLanePool?.retireAll(reason: .resized)
    }
}
