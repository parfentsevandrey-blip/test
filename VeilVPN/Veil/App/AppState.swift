import AppKit
import Foundation
import Network
import Observation

struct YouTubeTestResult: Equatable, Sendable {
    let success: Bool
    let milliseconds: Int
    let detail: String
    let viaTor: Bool
    /// Homepage download speed, when measured.
    let kilobytesPerSecond: Double?
}

enum MoatStatus: Equatable, Sendable {
    case idle
    case fetching
    case done(bridges: Int, transport: String)
    case failed(String)
}

/// Progress of the "is the network actually working?" check and the Wi-Fi reset it may trigger.
enum NetworkRepairStatus: Equatable, Sendable {
    case idle
    case probing
    case resetting(String)
    case waitingForNetwork
    case recovered(String)
    case skipped(String)
    case failed(String)

    var isBusy: Bool {
        switch self {
        case .probing, .resetting, .waitingForNetwork: true
        default: false
        }
    }
}

enum NetworkRepairOutcome: Equatable, Sendable {
    /// The Internet answered without any intervention.
    case healthy
    /// The network was reset and answers now.
    case recovered
    /// Reset attempted (or not allowed) and still nothing answers.
    case stillDown
    /// Nothing was done: another VPN owns the route, or a reset is already running.
    case skipped
}

/// Orchestrates the Tor engine, the local HTTP bridge and the system proxy; the single source
/// of truth for every view.
@MainActor
@Observable
final class AppState {
    private(set) static weak var shared: AppState?

    var settings: AppSettings {
        didSet { settings.save() }
    }

    private(set) var connection: ConnectionState = .disconnected
    private(set) var bootstrap = BootstrapProgress()
    private(set) var circuit: [CircuitHop] = []
    private(set) var torCheck: TorCheckResult?
    /// Median round trip of the route. With the lane pool on this is the best measured lane — the
    /// latency the *next* connection will actually get — rather than whichever circuit a probe
    /// happened to land on.
    var routeLatency: TimeInterval? { lanes?.bestP50 ?? latency.summary?.median }
    /// How long the last exit verification took end to end (a full HTTPS fetch, not route latency).
    private(set) var torCheckSeconds: TimeInterval?
    private(set) var isCheckingTor = false
    private(set) var isChangingIdentity = false
    private(set) var logs: [LogEntry] = []
    private(set) var lastError: AppError?
    private(set) var connectedAt: Date?
    private(set) var proxyStatus: ProxyStatus = .off
    private(set) var ports: ActivePorts?
    private(set) var torVersion: String?
    /// YouTube Turbo: the anti-throttling proxy runs without Tor.
    private(set) var turboActive = false
    private(set) var youtubeTest: YouTubeTestResult?
    private(set) var isTestingYouTube = false
    /// Tor died (or never bootstrapped) and the proxy is deliberately left pointing at Veil.
    private(set) var killSwitchEngaged = false
    /// The transport currently in use or being tried (automatic mode).
    private(set) var activeTransport: AppSettings.Transport?
    private(set) var transportAttemptMessage: String?
    private(set) var reachability: ReachabilityProbe.Report?
    private(set) var availableUpdate: UpdateInfo?
    private(set) var isCheckingUpdate = false
    private(set) var updateCheckMessage: String?
    private(set) var bridgeStats = HTTPProxyBridge.Stats()
    private(set) var benchmarkResults: [YouTubeBenchmark.StrategyResult] = []
    private(set) var isBenchmarking = false
    private(set) var moatStatus: MoatStatus = .idle
    private(set) var reconnectPending = false
    /// Result of the last Internet probe (TCP to public resolvers plus one name lookup).
    private(set) var connectivity: ConnectivityProbe.Report?
    private(set) var networkRepair: NetworkRepairStatus = .idle
    private(set) var isResettingNetwork = false
    private(set) var lastNetworkReset: Date?
    /// The interface carrying the default route (Wi-Fi, Ethernet, or another VPN's tunnel).
    private(set) var primaryNetwork: NetworkReset.Primary?
    /// Round trip measured before the route tuner pinned relays, for the before → after display.
    private(set) var routeLatencyBeforeTuning: TimeInterval?
    /// A route change is being applied: config pushed, first circuit being built.
    private(set) var routeSwitching = false
    /// When the current connection attempt began, so the UI can show how long it has been going.
    private(set) var connectStartedAt: Date?
    /// What Tor already had on disk when the attempt started.
    private(set) var warmth: WarmthProfile?
    private(set) var standby: StandbyState = .off
    private(set) var currentStage: BootstrapStage = .launch
    private(set) var stageEnteredAt: Date?
    private(set) var stageBudget: Duration?
    private(set) var plannedQueue: [AppSettings.Transport] = []
    private(set) var attemptIndex = 0
    private(set) var lanes: LanePoolSnapshot?
    /// Freshness stamps, so the ledger can dim a value instead of quietly showing a stale one.
    private(set) var circuitUpdatedAt: Date?
    private(set) var bridgeUpdatedAt: Date?
    private(set) var trafficUpdatedAt: Date?
    private(set) var bridgeInFlight = 0
    private(set) var lastAttemptFailure: AttemptFailure?
    var selfTestReport: SelfTestReport?
    var isSelfTesting = false
    /// Sidebar selection, so menu commands can navigate.
    var sidebarSelection: SidebarItem = .home
    let traffic = TrafficMonitor()
    let padding = PaddingLoop()
    let tuner = RouteTuner()
    let latency = LatencyMonitor()
    let isDemo: Bool

    @ObservationIgnored private var engine: any TorEngine
    @ObservationIgnored private var httpBridge: HTTPProxyBridge?
    @ObservationIgnored private var connectTask: Task<Void, Never>?
    @ObservationIgnored private var circuitTask: Task<Void, Never>?
    @ObservationIgnored private var rotationTask: Task<Void, Never>?
    @ObservationIgnored private var statsTask: Task<Void, Never>?
    @ObservationIgnored private var reconnectTask: Task<Void, Never>?
    @ObservationIgnored private var proxyApplied = false
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var isTearingDown = false
    @ObservationIgnored private var userRequestedDisconnect = false
    @ObservationIgnored private let networkWatcher = NetworkWatcher()
    @ObservationIgnored private var sleptAt: Date?
    @ObservationIgnored private var suppressPathEvents = false
    @ObservationIgnored private var networkRepairedThisConnect = false
    @ObservationIgnored private var tuneTask: Task<Void, Never>?
    @ObservationIgnored private var retuneTask: Task<Void, Never>?
    @ObservationIgnored private var routeGeneration = 0
    @ObservationIgnored private var checkFailures = 0
    @ObservationIgnored private var standbyTask: Task<Void, Never>?
    @ObservationIgnored private var standbyFailures = 0
    @ObservationIgnored private var proxyArmTask: Task<Void, Never>?
    @ObservationIgnored private var networkFingerprint: NetworkFingerprint?

    /// Where the warm tor stands. `ready` means loaded but held offline: no listener, no packets.
    enum StandbyState: Equatable, Sendable {
        case off
        case starting
        case ready(WarmthProfile.Tier)
        case live
        case failed(String)

        var isReady: Bool { if case .ready = self { true } else { false } }
    }

    /// `make test` is app-hosted and the real tor binary is present, so a standby that was not
    /// gated would spawn a tor process on every CI run.
    static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    private static let maxLogEntries = 2000
    /// Remembers that the system proxy points at Veil, so a crash or force-quit can be repaired on the next launch.
    private static let proxyAppliedKey = "app.veilvpn.systemProxyApplied"
    private static let lastUpdateCheckKey = "app.veilvpn.lastUpdateCheck"

    init(engine: (any TorEngine)? = nil) {
        settings = AppSettings.load()
        let environment = ProcessInfo.processInfo.environment
        let resolved: any TorEngine
        if let engine {
            resolved = engine
        } else if environment["VEIL_SIMULATE"] == "1" {
            resolved = SimulatedTorEngine()
        } else if let bundle = TorBundle.locate(environment: environment) {
            resolved = TorProcessEngine(bundle: bundle)
        } else {
            resolved = SimulatedTorEngine()
        }
        self.engine = resolved
        isDemo = resolved.isSimulated
        AppState.shared = self
        wireEngine()
        padding.onLog = { [weak self] entry in self?.append(entry) }
        tuner.onLog = { [weak self] entry in self?.append(entry) }
        latency.onLog = { [weak self] entry in self?.append(entry) }
        traffic.paddingRateProvider = { [weak self] in self?.padding.rate ?? 0 }
        networkWatcher.onEvent = { [weak self] event in self?.handleNetworkEvent(event) }
        networkWatcher.start()
        if isDemo {
            append(.veil(.notice, "Demo mode: tor binaries were not found next to the app, connections are simulated."))
        }
        // A resident tor raises the stakes on orphans: sweep before anything else, and only ever
        // signal a process whose executable and data directory are demonstrably ours.
        TorProcessRegistry.sweepOrphans(expectedExecutable: TorBundle.locate(environment: environment)?.tor,
                                        ownDataDirectory: StateCleaner.defaultDataDirectory)
        restoreStaleSystemProxyIfNeeded()
        restoreDisabledNetworkServiceIfNeeded()
        primaryNetwork = NetworkReset.primary()
        scheduleUpdateCheck()
        if settings.connectOnLaunch {
            connect()
        } else {
            scheduleStandby(after: .seconds(8))
        }
    }

    // MARK: Warm standby

    /// Keeps one tor process loaded but offline, so connecting is a single command rather than a
    /// spawn, a handshake and a cache reload. Nothing is on the wire: `DisableNetwork 1` means no
    /// packets and `SocksPort 0` means no listener.
    private var standbyAllowed: Bool {
        !isDemo && !Self.isRunningTests && settings.warmStart != .off
            && !ProcessInfo.processInfo.isLowPowerModeEnabled
            && connection == .disconnected && !turboActive && !killSwitchEngaged
    }

    private func scheduleStandby(after delay: Duration) {
        guard standbyAllowed, !engine.isWarm, !engine.isLive else { return }
        standbyTask?.cancel()
        standbyTask = Task { [weak self] in
            do { try await Task.sleep(for: delay) } catch { return }
            await self?.warmStandby()
        }
    }

    private func warmStandby() async {
        guard standbyAllowed, !engine.isWarm, !engine.isLive else { return }
        let control: UInt16
        if let existing = ports?.control {
            control = existing
        } else if let allocated = try? PortAllocator.allocate(preferredSocks: settings.socksPort,
                                                              preferredHTTP: settings.httpPort) {
            ports = allocated
            control = allocated.control
        } else {
            return
        }
        standby = .starting
        do {
            let profile = try await engine.warmUp(settings: settings, controlPort: control,
                                                  mode: settings.warmStart, budget: .seconds(20))
            warmth = profile
            standby = .ready(profile.tier)
            standbyFailures = 0
        } catch {
            standbyFailures += 1
            standby = .failed(error.localizedDescription)
            append(.veil(.debug, "Standby did not start: \(error.localizedDescription)"))
            let backoff: Duration = standbyFailures == 1 ? .seconds(5) : (standbyFailures == 2 ? .seconds(30) : .seconds(300))
            if standbyFailures <= 3 { scheduleStandby(after: backoff) }
        }
    }

    /// Releases the standby without touching a live connection.
    private func releaseStandby() async {
        standbyTask?.cancel()
        standbyTask = nil
        guard connection == .disconnected, engine.isWarm else { return }
        await engine.stop(grace: .seconds(3))
        standby = .off
        warmth = nil
    }

    func setWarmStart(_ mode: AppSettings.WarmStart) {
        guard settings.warmStart != mode else { return }
        settings.warmStart = mode
        Task { [weak self] in
            guard let self else { return }
            if mode == .off {
                await releaseStandby()
            } else if connection == .disconnected {
                await warmStandby()
            }
        }
    }

    // MARK: Derived

    var selectedExit: ExitLocation? { ExitLocation.named(settings.exitCountry) }
    var selectedMiddle: ExitLocation? { settings.multihopEnabled ? ExitLocation.named(settings.middleCountry) : nil }
    /// The route Tor is on: the chosen countries plus the relays the tuner pinned for them.
    var effectiveRoute: TorRoute { tuner.route(for: settings.route) }
    var exitHop: CircuitHop? { circuit.last { $0.role == .exit } }
    var middleHop: CircuitHop? { circuit.first { $0.role == .middle } }
    var needsTeardown: Bool { connection != .disconnected || proxyApplied || turboActive || killSwitchEngaged }
    /// True while Veil's local HTTP bridge is serving (Tor connected or YouTube Turbo).
    var bridgeRunning: Bool { connection.isConnected || turboActive }

    /// Where the pluggable transports were unpacked, when the bundle is present.
    var transportDirectory: URL? {
        guard let bundle = TorBundle.locate() else { return nil }
        return try? PluggableTransportLocator.spaceFreeDirectory(for: bundle)
    }

    /// Whether that directory is readable only by this user.
    var transportDirectoryIsPrivate: Bool? {
        guard let transportDirectory,
              let attributes = try? FileManager.default.attributesOfItem(atPath: transportDirectory.path),
              let permissions = (attributes[.posixPermissions] as? NSNumber)?.intValue else { return nil }
        return permissions & 0o077 == 0
    }

    /// The measured-circuit pool, when the bridge is up.
    var httpBridgeLanePool: LanePool? { httpBridge?.lanePool }

    /// Re-pushes the routing policy after the Security section changes a protective setting.
    func httpBridgePolicyChanged() {
        httpBridge?.policy = settings.routingPolicy
        httpBridge?.httpsOnly = settings.httpsOnly
    }

    /// Applies the machinery behind a bulk settings write.
    func pushSecuritySideEffects(isolationChanged: Bool, paddingChanged: Bool) {
        httpBridgePolicyChanged()
        if isolationChanged { httpBridge?.lanePool.setSiteMode(settings.isolatePerSite) }
        httpBridge?.lanePool.setHedging(settings.lanePoolHedging)
        httpBridge?.lanePool.setLaneCount(settings.isolatePerSite ? 2 : settings.lanePoolSize)
        guard paddingChanged, connection == .connected, let ports else { return }
        let enabled = settings.paddingEnabled
        Task { [weak self] in
            guard let self else { return }
            await engine.setTorPadding(enabled: enabled)
            if enabled {
                padding.start(engine: engine, socksPort: ports.socks, level: settings.paddingLevel)
            } else {
                await padding.stop()
            }
        }
    }

    /// Shell snippet for tools that ignore the system proxy (curl, git, Homebrew, ...).
    var terminalSnippet: String {
        let socks = ports?.socks ?? UInt16(clamping: settings.socksPort)
        let http = ports?.http ?? UInt16(clamping: settings.httpPort)
        return "export ALL_PROXY=socks5h://127.0.0.1:\(socks) HTTP_PROXY=http://127.0.0.1:\(http) HTTPS_PROXY=http://127.0.0.1:\(http)"
    }

    // MARK: Connection lifecycle

    func toggleConnection() {
        switch connection {
        case .disconnected, .failed:
            connect()
        case .connecting, .connected:
            disconnect()
        case .disconnecting:
            break
        }
    }

    func connect() {
        guard connection == .disconnected || connection == .failed, !isTearingDown else { return }
        generation += 1
        let attempt = generation
        standbyTask?.cancel()
        userRequestedDisconnect = false
        reconnectPending = false
        lastError = nil
        torCheck = nil
        torCheckSeconds = nil
        lastAttemptFailure = nil
        latency.stop()
        latency.reset()
        circuit = []
        lanes = nil
        bootstrap = BootstrapProgress()
        transportAttemptMessage = nil
        connectStartedAt = .now
        currentStage = .launch
        stageEnteredAt = .now
        connection = .connecting
        let settings = self.settings

        connectTask = Task { [weak self] in
            guard let self else { return }
            do {
                if turboActive {
                    await teardown(keepWarm: false)
                    turboActive = false
                }
                // Fire and forget: the answer to a system permission alert must never gate a
                // connection attempt.
                Task { await NotificationManager.shared.requestAuthorizationIfNeeded() }
                let fingerprint = NetworkFingerprint.current()
                networkFingerprint = fingerprint

                // Reuse the ports and the (blocking) bridge left behind by the kill switch, so the
                // network is never opened up between a failure and the reconnection.
                var ports: ActivePorts
                if let existing = self.ports, httpBridge != nil || engine.isWarm {
                    ports = existing
                } else {
                    ports = try PortAllocator.allocate(preferredSocks: settings.socksPort, preferredHTTP: settings.httpPort)
                    if Int(ports.socks) != settings.socksPort || Int(ports.http) != settings.httpPort {
                        append(.veil(.warn, "Preferred ports are busy; using SOCKS \(ports.socks) and HTTP \(ports.http) instead."))
                    }
                }
                // Re-validate right before it is written into the system proxy: the window between
                // "this port is free" and "tor bound it" shrinks from minutes to milliseconds.
                if !PortAllocator.isFree(ports.socks), httpBridge == nil,
                   let fresh = try? PortAllocator.freeSocksPort(preferred: 0, excluding: [ports.http, ports.control, ports.pool]) {
                    ports = ActivePorts(socks: fresh, http: ports.http, control: ports.control, pool: ports.pool)
                }
                self.ports = ports

                // Kill switch: fail closed from the very first second — but never awaited, so the
                // administrator prompt and the SCPreferences write run alongside the attempt.
                if settings.killSwitch, settings.configureSystemProxy {
                    if httpBridge == nil {
                        let bridge = HTTPProxyBridge(socksPort: nil, policy: settings.routingPolicy)
                        bridge.blockAll = true
                        try bridge.start(port: ports.http)
                        httpBridge = bridge
                    }
                    if !proxyApplied {
                        proxyArmTask = Task { [weak self] in
                            await self?.enableSystemProxy(socks: ports.socks, http: ports.http)
                        }
                    }
                    append(.veil(.info, "Kill switch armed: proxied apps are blocked until Tor is up"))
                }

                networkRepairedThisConnect = false
                let history = await ConnectHistoryStore.shared.history(for: fingerprint)
                try Task.checkCancellation()
                guard attempt == generation else { return }

                // Tor is loaded here: a few milliseconds when it is already warm, a spawn when not.
                var warmEngine = engine.isWarm
                var profile: WarmthProfile
                if warmEngine, let existing = engine.warmth {
                    profile = existing
                } else if settings.warmStart != .off, !isDemo {
                    standby = .starting
                    profile = try await engine.warmUp(settings: settings, controlPort: ports.control,
                                                      mode: .standby, budget: .seconds(20))
                    warmEngine = true
                } else {
                    profile = WarmthProfile()
                }
                warmth = profile
                standby = .live
                append(.veil(.info, "Tor state: \(profile.summary)"))

                // Probing runs beside the attempt rather than in front of it. Its only jobs are to
                // abort an attempt that has no Internet under it, and to reorder what is left.
                let probeTask = Task.detached(priority: .utility) { () -> (ConnectivityProbe.Report, ReachabilityProbe.Report) in
                    async let network = ConnectivityProbe.check(timeout: .seconds(3))
                    async let direct = ReachabilityProbe.probeDirectTor()
                    return (await network, await direct)
                }
                let sentinel = Task { [weak self] in
                    let result = await probeTask.value
                    guard let self, attempt == generation, !result.0.isUsable, engine.bootstrapPercent < 10 else { return }
                    engine.abortBootstrap(.noInternet)
                }
                defer { sentinel.cancel() }

                var plan = AttemptPlanner.candidates(settings: settings, warmth: profile,
                                                     history: history, reachability: nil)
                plannedQueue = plan.ordered
                for skip in plan.skipped {
                    append(.veil(.debug, "Skipping \(skip.transport.rawValue): \(skip.reason)"))
                }
                let deadline = AttemptPlanner.overallDeadline(tier: profile.tier)
                let started = Date.now
                let defaults = PluggableTransportDefaults.load(from: nil)

                var queue = plan.ordered
                var connectedTransport: AppSettings.Transport?
                var lastFailure: Error?
                var previous: AppSettings.Transport?
                var index = 0
                while index < queue.count {
                    let transport = queue[index]
                    try Task.checkCancellation()
                    guard attempt == generation else { return }
                    let elapsed = Date.now.timeIntervalSince(started)
                    if elapsed > deadline, index > 0 {
                        append(.veil(.warn, "Giving up after \(Int(elapsed)) s of connection attempts"))
                        break
                    }
                    activeTransport = transport
                    attemptIndex = index
                    bootstrap = BootstrapProgress()
                    if queue.count > 1 || index > 0 {
                        if let previous {
                            transportAttemptMessage = String(localized: "\(Self.name(of: previous)) did not respond, trying \(Self.name(of: transport))…")
                        } else {
                            transportAttemptMessage = String(localized: "Trying \(Self.name(of: transport))…")
                        }
                        append(.veil(.notice, "Automatic transport: trying \(transport.rawValue) (\(index + 1)/\(queue.count))"))
                    }
                    let attemptSettings = settings.resolving(transport: transport)
                    let configuration = AttemptPlanner.config(
                        transport: transport, index: index, count: queue.count, warmth: profile,
                        history: history, marginalLink: (connectivity?.reachedByAddress ?? 2) <= 1,
                        warmEngine: warmEngine, elapsed: elapsed, deadline: deadline,
                        bridgeLineCount: AttemptPlanner.distinctFirstHops(transport: transport,
                                                                          settings: settings, defaults: defaults)
                    )
                    currentStage = .launch
                    stageEnteredAt = .now
                    stageBudget = configuration.budget(for: .launch)
                    do {
                        if warmEngine {
                            _ = try await engine.activate(settings: attemptSettings, ports: ports)
                        } else {
                            try await engine.start(settings: attemptSettings, ports: ports)
                        }
                        torVersion = engine.versionDescription
                        let outcome = try await engine.runBootstrap(configuration) { [weak self] stage, percent in
                            guard let self else { return }
                            currentStage = stage
                            stageEnteredAt = .now
                            stageBudget = configuration.budget(for: stage)
                            if percent > bootstrap.percent {
                                bootstrap = BootstrapProgress(percent: percent, tag: bootstrap.tag, summary: bootstrap.summary)
                            }
                        }
                        connectedTransport = transport
                        await ConnectHistoryStore.shared.record(
                            AttemptResult(transport: transport, kind: .success(outcome)), for: fingerprint)
                        append(.veil(.info, "Bootstrapped in \(outcome.totalMillis) ms"))
                        break
                    } catch is CancellationError {
                        throw CancellationError()
                    } catch {
                        lastFailure = error
                        if let attemptError = error as? TorAttemptError {
                            lastAttemptFailure = attemptError.failure
                            await ConnectHistoryStore.shared.record(
                                AttemptResult(transport: transport,
                                              kind: .failure(attemptError.failure, stage: attemptError.stage,
                                                             percent: attemptError.percent)),
                                for: fingerprint)
                            append(.veil(.warn, "\(transport.rawValue): \(attemptError.localizedDescription)"))
                        } else {
                            append(.veil(.warn, "\(transport.rawValue): \(error.localizedDescription)"))
                        }
                        // ~2 ms, not a teardown: the consensus, descriptors, guards and the
                        // pluggable transports all stay loaded for the next candidate.
                        if warmEngine {
                            await engine.holdNetwork()
                        } else {
                            await engine.stop(grace: .milliseconds(800))
                        }
                        let probe = await probeTask.value
                        connectivity = probe.0
                        reachability = probe.1
                        let failure = (error as? TorAttemptError)?.failure
                        let firstHopOnDeadNetwork = failure.map { $0 == .noInternet || ($0.isFirstHop && !probe.0.isUsable) } ?? false
                        if firstHopOnDeadNetwork, !networkRepairedThisConnect, self.settings.autoResetNetwork {
                            networkRepairedThisConnect = true
                            let outcome = await repairNetworkIfNeeded(trigger: "first hop", allowReset: true)
                            if outcome == .recovered {
                                append(.veil(.notice, "Network recovered; retrying \(transport.rawValue)"))
                                queue.insert(transport, at: index + 1)
                            }
                        }
                        // Reorder only what is left, now that the reachability answer is in hand.
                        let tried = Array(queue.prefix(index + 1))
                        plan = AttemptPlanner.candidates(settings: settings, warmth: profile,
                                                         history: history, reachability: probe.1)
                        queue = tried + plan.ordered.filter { !tried.contains($0) }
                        plannedQueue = queue
                    }
                    previous = transport
                    index += 1
                }
                try Task.checkCancellation()
                guard attempt == generation else { return }
                guard let connectedTransport else {
                    throw lastFailure ?? TorEngineError.notRunning
                }
                if settings.transport == .auto, self.settings.lastWorkingTransport != connectedTransport {
                    self.settings.lastWorkingTransport = connectedTransport
                }
                transportAttemptMessage = nil
                let effective = engine.activePorts ?? ports
                self.ports = effective

                // The proxy write is the only await before the flip, and the three bridge writes
                // below have no suspension point between them: `accept` snapshots port, policy and
                // blocked under one lock, so no session can see "unblocked" with no port.
                await proxyArmTask?.value
                proxyArmTask = nil
                if let bridge = httpBridge {
                    bridge.socksPort = effective.socks
                    bridge.policy = self.settings.routingPolicy
                    bridge.blockAll = false
                } else {
                    let bridge = HTTPProxyBridge(socksPort: effective.socks, policy: self.settings.routingPolicy)
                    try bridge.start(port: effective.http)
                    httpBridge = bridge
                }
                append(.veil(.info, "HTTP proxy bridge listening on 127.0.0.1:\(effective.http) → SOCKS5 127.0.0.1:\(effective.socks)"))

                if settings.configureSystemProxy {
                    if !proxyApplied {
                        await enableSystemProxy(socks: effective.socks, http: effective.http)
                    } else if effective.socks != ports.socks {
                        Task { [weak self] in
                            await self?.enableSystemProxy(socks: effective.socks, http: effective.http)
                        }
                    }
                } else {
                    proxyStatus = .manual
                }
                guard attempt == generation else { return }

                killSwitchEngaged = false
                connectedAt = .now
                connectStartedAt = nil
                connection = .connected
                standby = .live
                await ConnectHistoryStore.shared.flush()
                traffic.start(engine: engine)
                startCircuitUpdates()
                startStatsUpdates()
                if self.settings.paddingEnabled {
                    padding.start(engine: engine, socksPort: effective.socks, level: self.settings.paddingLevel)
                }
                await startLanePool(ports: effective, transport: connectedTransport)
                restartRouteRotation()
                startRouteTuning()
                startRetuneTimer()
                Feedback.connected(sound: self.settings.soundEffects, haptic: self.settings.hapticFeedback)
                notify(id: "connected", title: String(localized: "Connected through Tor"),
                       body: String(localized: "Transport: \(Self.name(of: connectedTransport)). Your traffic now goes through the Tor network."))
                if self.settings.checkAfterConnect {
                    runTorCheck()
                }
                if self.settings.checkForUpdates, self.settings.updateCheckAfterConnect {
                    checkForUpdates(manual: false)
                }
            } catch is CancellationError {
                // Cancelling costs one command, not a teardown, so the UI is free immediately.
                if settings.warmStart != .off {
                    await engine.holdNetwork()
                } else {
                    await engine.stop(grace: .milliseconds(800))
                }
            } catch {
                guard attempt == generation else { return }
                append(.veil(.error, error.localizedDescription))
                lastError = AppError(title: String(localized: "Could not connect"), message: error.localizedDescription)
                await failClosedOrTeardown(reason: error.localizedDescription)
                if attempt == generation {
                    connectStartedAt = nil
                    connection = .failed
                    Feedback.failed(sound: self.settings.soundEffects, haptic: self.settings.hapticFeedback)
                    scheduleReconnectIfWanted()
                }
            }
        }
    }

    /// Starts the measured circuits for traffic through Tor, once Tor is actually up.
    private func startLanePool(ports: ActivePorts, transport: AppSettings.Transport) async {
        guard let bridge = httpBridge else { return }
        bridge.setConnectionContext(transport: transport, connectedAt: connectedAt)
        guard settings.lanePoolEnabled, ports.pool != 0, !engine.isSimulated else {
            latency.start(socksPort: ports.socks)
            return
        }
        let listeners = await engine.socksListeners()
        let endpoint = "127.0.0.1:\(ports.pool)"
        // An empty list means the GETINFO failed, not that the listener is missing: proceed, and
        // the first warm-up will suspend the pool cleanly if it really is not there.
        guard listeners.isEmpty || listeners.contains(endpoint) else {
            append(.veil(.warn, "Tor did not open the lane listener on \(endpoint); using a single circuit"))
            latency.start(socksPort: ports.socks)
            return
        }
        bridge.lanePool.onLog = { [weak self] entry in
            Task { @MainActor in self?.append(entry) }
        }
        var configuration = LanePool.Configuration()
        configuration.laneCount = settings.isolatePerSite ? 2 : settings.lanePoolSize
        configuration.siteMode = settings.isolatePerSite
        configuration.hedgingEnabled = settings.lanePoolHedging
        bridge.poolPort = ports.pool
        bridge.lanePool.start(poolPort: ports.pool, configuration: configuration)
    }

    func disconnect() {
        guard connection == .connecting || connection == .connected || killSwitchEngaged else { return }
        generation += 1
        userRequestedDisconnect = true
        reconnectPending = false
        reconnectTask?.cancel()
        reconnectTask = nil
        connectTask?.cancel()
        connectTask = nil
        connection = .disconnecting
        // No await before the state change: cancelling the attempt reaches its next checkpoint in
        // under 250 ms and holding the network is one command, so "Not connected" arrives at once.
        Task { [weak self] in
            guard let self else { return }
            await teardown(keepWarm: settings.warmStart != .off)
            killSwitchEngaged = false
            connection = .disconnected
            bootstrap = BootstrapProgress()
            activeTransport = nil
            transportAttemptMessage = nil
            Feedback.disconnected(sound: settings.soundEffects, haptic: settings.hapticFeedback)
            if standbyAllowed, engine.isWarm {
                standby = .ready(engine.warmth?.tier ?? .cool)
            }
        }
    }

    /// Reconnects. A live tunnel gets a soft bounce first — `DisableNetwork` off and on, which
    /// keeps the process, the guards and the loaded directory — and only falls back to a full
    /// restart if that does not come up.
    func reconnect() {
        guard connection == .connected || connection == .failed || connection == .connecting else { return }
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        Task { [weak self] in
            guard let self else { return }
            if connection == .connected {
                let bounced = await softReconnect(reason: "reconnect")
                if bounced { return }
            }
            await stopEngineSide(keepWarm: settings.warmStart != .off)
            if settings.killSwitch, let bridge = httpBridge {
                bridge.blockAll = true
                bridge.socksPort = nil
            }
            connection = .disconnected
            connect()
        }
    }

    /// A bounce that keeps everything expensive: same process, same guards, same cached directory.
    /// The bridge fails closed for the whole of it, so nothing leaks through the gap.
    private func softReconnect(reason: String) async -> Bool {
        guard connection == .connected, engine.isLive, let ports, let bridge = httpBridge else { return false }
        let alive = await engine.isCircuitEstablished()
        guard alive else { return false }
        let started = Date.now
        bridge.socksPort = nil
        bridge.blockAll = true
        bridge.lanePool.retireAll(reason: .routeChanged)
        do {
            try await engine.refreshNetwork()
        } catch {
            append(.veil(.debug, "Soft reconnect could not restart the network: \(error.localizedDescription)"))
            return false
        }
        var established = false
        for _ in 0..<50 {
            do { try await Task.sleep(for: .milliseconds(400)) } catch { return false }
            guard connection == .connected else { return false }
            let up = await engine.isCircuitEstablished()
            if up {
                established = true
                break
            }
        }
        guard established else { return false }
        let sample = await LatencyProbe.sample(socksPort: ports.socks, target: LatencyProbe.target(at: 0),
                                               timeout: .seconds(6))
        guard sample != nil else { return false }
        bridge.socksPort = ports.socks
        bridge.blockAll = false
        latency.reset()
        latency.measureNow()
        append(.veil(.notice, "Soft reconnect (\(reason)) in \(Int(Date.now.timeIntervalSince(started) * 1000)) ms"))
        return true
    }

    /// Called from the app delegate before the process exits.
    func prepareForTermination() async {
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        reconnectTask?.cancel()
        standbyTask?.cancel()
        await ConnectHistoryStore.shared.flush()
        await teardown(keepWarm: false)
        StateCleaner.applyForgetPolicy(settings.forgetPolicy, dataDirectory: StateCleaner.defaultDataDirectory)
        turboActive = false
        killSwitchEngaged = false
        connection = .disconnected
    }

    private func enableSystemProxy(socks: UInt16?, http: UInt16) async {
        do {
            let services = try await SystemProxy.enable(socksPort: socks, httpPort: http)
            proxyApplied = true
            UserDefaults.standard.set(true, forKey: Self.proxyAppliedKey)
            proxyStatus = .configured(services)
            append(.veil(.notice, "System proxy enabled for \(services.joined(separator: ", "))"))
        } catch {
            proxyStatus = .failed(error.localizedDescription)
            append(.veil(.warn, "System proxy was not configured: \(error.localizedDescription)"))
        }
    }

    /// Everything on the Tor side, leaving the bridge and the system proxy alone. With `keepWarm`
    /// the process stays loaded and offline instead of being torn down and rebuilt.
    private func stopEngineSide(keepWarm: Bool = true) async {
        circuitTask?.cancel()
        circuitTask = nil
        rotationTask?.cancel()
        rotationTask = nil
        tuneTask?.cancel()
        tuneTask = nil
        retuneTask?.cancel()
        retuneTask = nil
        routeGeneration += 1
        tuner.reset()
        routeLatencyBeforeTuning = nil
        routeSwitching = false
        checkFailures = 0
        traffic.stop()
        latency.stop()
        latency.reset()
        lanes = nil
        httpBridge?.lanePool.stop()
        httpBridge?.poolPort = nil
        await padding.stop()
        if keepWarm, settings.warmStart != .off, engine.isLive || engine.isWarm {
            await engine.holdNetwork()
            standby = .ready(engine.warmth?.tier ?? .cool)
        } else {
            await engine.stop(grace: .seconds(3))
            standby = .off
        }
        connectedAt = nil
        connectStartedAt = nil
        circuit = []
        torCheck = nil
        torCheckSeconds = nil
    }

    /// On failure: keep blocking when the kill switch is on, otherwise restore the network.
    private func failClosedOrTeardown(reason: String) async {
        if settings.killSwitch, proxyApplied, let bridge = httpBridge {
            await stopEngineSide(keepWarm: true)
            bridge.blockAll = true
            bridge.socksPort = nil
            if settings.closeSessionsOnKillSwitch { bridge.closeAllSessions() }
            killSwitchEngaged = true
            append(.veil(.warn, "Kill switch engaged: proxied traffic stays blocked until you reconnect or disconnect"))
            notify(id: "killswitch", title: String(localized: "Kill switch engaged"),
                   body: String(localized: "Tor is down; apps using the system proxy are blocked until Veil reconnects."))
        } else {
            await teardown(keepWarm: settings.warmStart != .off)
        }
    }

    private func teardown(keepWarm: Bool = false) async {
        guard !isTearingDown else {
            while isTearingDown { try? await Task.sleep(for: .milliseconds(100)) }
            return
        }
        isTearingDown = true
        defer { isTearingDown = false }

        statsTask?.cancel()
        statsTask = nil
        await stopEngineSide(keepWarm: keepWarm)
        if proxyApplied {
            do {
                try await SystemProxy.disable()
                UserDefaults.standard.set(false, forKey: Self.proxyAppliedKey)
                append(.veil(.info, "System proxy disabled"))
            } catch {
                append(.veil(.warn, "Could not restore the system proxy: \(error.localizedDescription)"))
            }
            proxyApplied = false
        }
        proxyStatus = .off
        httpBridge?.stop()
        httpBridge = nil
        bridgeStats = HTTPProxyBridge.Stats()
    }

    /// If the previous run ended without restoring the proxy (crash, force quit, power loss),
    /// the Mac is left pointing at a dead local port. Repair it before doing anything else.
    private func restoreStaleSystemProxyIfNeeded() {
        guard UserDefaults.standard.bool(forKey: Self.proxyAppliedKey) else { return }
        append(.veil(.warn, "The system proxy was still pointing at Veil from the previous run; restoring it."))
        Task { [weak self] in
            do {
                try await SystemProxy.disable()
                UserDefaults.standard.set(false, forKey: Self.proxyAppliedKey)
                self?.append(.veil(.info, "System proxy restored"))
            } catch {
                self?.append(.veil(.warn, "Could not restore the system proxy: \(error.localizedDescription)"))
            }
        }
    }

    // MARK: Engine events

    private func wireEngine() {
        engine.onLog = { [weak self] entry in self?.append(entry) }
        engine.onBootstrap = { [weak self] progress in self?.bootstrap = progress }
        engine.onExit = { [weak self] status in self?.handleEngineExit(status) }
    }

    private func handleEngineExit(_ status: Int32) {
        guard connection == .connected else { return } // while connecting, the connect task reports it
        lastError = AppError(
            title: String(localized: "Tor stopped unexpectedly"),
            message: String(localized: "The tor process exited with status \(String(status)). Open Activity for details.")
        )
        generation += 1
        Task { [weak self] in
            guard let self else { return }
            await failClosedOrTeardown(reason: "tor exited")
            connection = .failed
            Feedback.failed(sound: settings.soundEffects, haptic: settings.hapticFeedback)
            scheduleReconnectIfWanted()
        }
    }

    // MARK: Auto-reconnect

    private func handleNetworkEvent(_ event: NetworkWatcher.Event) {
        switch event {
        case .willSleep:
            sleptAt = .now
            append(.veil(.info, "Mac is going to sleep"))
        case .pathLost:
            guard !suppressPathEvents else { return }
            guard connection == .connected || connection == .connecting else { return }
            append(.veil(.warn, "Network path lost"))
            if settings.autoReconnect { reconnectPending = true }
        case .pathRestored:
            primaryNetwork = NetworkReset.primary()
            guard !suppressPathEvents else { return }
            append(.veil(.info, "Network path restored" + (primaryNetwork.map { " via \($0.displayName)" } ?? "")))
            if settings.autoReconnect, reconnectPending || connection == .failed {
                scheduleReconnect(after: .seconds(3))
            } else if connection == .connected {
                verifyCircuitAfterDelay(.seconds(8))
            }
        case .didWake:
            let sleptFor = sleptAt.map { Date.now.timeIntervalSince($0) } ?? 0
            sleptAt = nil
            append(.veil(.info, sleptFor > 0 ? "Mac woke from sleep after \(Int(sleptFor / 60)) min" : "Mac woke from sleep"))
            guard connection == .connected || connection == .connecting || connection == .failed else { return }
            scheduleWakeRecovery(sleptFor: sleptFor)
        }
    }

    /// After sleep the network may look satisfied while nothing gets through. Check that first,
    /// then ask the only question that matters for the tunnel: does traffic actually flow through
    /// it right now? A long sleep is not by itself a reason to tear a working tunnel down — the
    /// old code restarted Tor after every nap and paid a full bootstrap for it.
    private func scheduleWakeRecovery(sleptFor: TimeInterval) {
        reconnectTask?.cancel()
        reconnectPending = false
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: .seconds(1)) } catch { return }
            switch connection {
            case .connected:
                // A live tunnel proves the Internet is up, so the two checks race instead of
                // queueing: on a healthy wake the repair — up to half a minute of probing plus a
                // Wi-Fi reset — is skipped entirely.
                let networkTask = Task.detached(priority: .utility) {
                    await ConnectivityProbe.check(timeout: .seconds(3))
                }
                let healthy = await tunnelIsHealthy(timeout: .seconds(5))
                let report = await networkTask.value
                guard !Task.isCancelled else { return }
                connectivity = report
                if healthy {
                    let nap = sleptFor > 60 ? " (slept \(Int(sleptFor / 60)) min)" : ""
                    append(.veil(.info, "Tunnel still carries traffic after sleep\(nap); no restart needed"))
                    latency.reset()
                    latency.measureNow()
                    httpBridge?.lanePool.retireAll(reason: .routeChanged)
                    return
                }
                if !report.isUsable {
                    let outcome = await repairNetworkIfNeeded(trigger: "wake",
                                                              allowReset: settings.autoResetNetwork,
                                                              patience: .seconds(12))
                    guard !Task.isCancelled else { return }
                    if outcome == .stillDown {
                        append(.veil(.warn, "No Internet after waking; leaving Tor alone until the network is back"))
                        return
                    }
                }
                let bounced = await softReconnect(reason: "wake")
                if bounced { return }
                guard settings.autoReconnect else {
                    append(.veil(.warn, "Tunnel is not passing traffic after sleep; auto-reconnect is off"))
                    return
                }
                append(.veil(.notice, "Tunnel is not passing traffic after sleep; restarting Tor"))
                reconnect()
            case .connecting:
                guard settings.autoReconnect else { return }
                append(.veil(.notice, "Restarting the connection attempt after sleep"))
                reconnect()
            case .failed:
                guard settings.autoReconnect, !userRequestedDisconnect else { return }
                connect()
            default:
                break
            }
        }
    }

    /// Opens a real stream through Tor. `status/circuit-established` can still say yes while the
    /// path is dead — after sleep it usually does — so ask the route itself. Both targets are
    /// raced and the first answer wins, so a healthy tunnel replies in about a second.
    private func tunnelIsHealthy(timeout: Duration) async -> Bool {
        guard let ports, connection == .connected else { return false }
        let socks = ports.socks
        return await withTaskGroup(of: Bool.self, returning: Bool.self) { group in
            for index in 0..<2 {
                group.addTask {
                    await LatencyProbe.sample(socksPort: socks, target: LatencyProbe.target(at: index),
                                              timeout: timeout) != nil
                }
            }
            var healthy = false
            for await result in group where result {
                healthy = true
                group.cancelAll()
                break
            }
            return healthy
        }
    }

    /// Probes the Internet; when nothing answers, waits `patience` for Wi-Fi to settle, then resets
    /// the network (if allowed) and probes again.
    private func repairNetworkIfNeeded(trigger: String, allowReset: Bool, patience: Duration = .seconds(3), maxAge: TimeInterval = 20) async -> NetworkRepairOutcome {
        // Wake recovery and the connection attempt that follows it used to probe the network twice,
        // adding half a minute to every wake for an answer we already had.
        if let cached = connectivity, cached.isUsable, Date.now.timeIntervalSince(cached.date) < maxAge {
            append(.veil(.debug, "Connectivity (\(trigger)): reusing the check from \(Int(Date.now.timeIntervalSince(cached.date))) s ago"))
            networkRepair = .idle
            return .healthy
        }
        networkRepair = .probing
        var report = await ConnectivityProbe.check()
        connectivity = report
        append(.veil(.info, "Connectivity (\(trigger)): \(report.summary)"))
        let deadline = ContinuousClock.now + patience
        while !report.isUsable, ContinuousClock.now < deadline {
            do { try await Task.sleep(for: .seconds(3)) } catch {
                networkRepair = .idle
                return .skipped
            }
            report = await ConnectivityProbe.check()
            connectivity = report
            append(.veil(.info, "Connectivity (\(trigger), retry): \(report.summary)"))
        }
        if report.isUsable {
            networkRepair = .idle
            return .healthy
        }
        guard allowReset else {
            networkRepair = .failed(String(localized: "The network is not responding and automatic reset is off."))
            append(.veil(.warn, "Network is not responding (\(report.summary)); automatic reset is off"))
            return .stillDown
        }
        return await performNetworkReset(reason: report.summary, manual: false)
    }

    /// Switches Wi-Fi off and on (or restarts the network service), waits for the network and
    /// probes again. The reset itself runs detached so cancelling a connection attempt can never
    /// leave Wi-Fi switched off.
    private func performNetworkReset(reason: String, manual: Bool) async -> NetworkRepairOutcome {
        guard !isResettingNetwork else { return .skipped }
        isResettingNetwork = true
        suppressPathEvents = true
        defer {
            isResettingNetwork = false
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(3))
                self?.suppressPathEvents = false
            }
        }
        if !manual, let last = lastNetworkReset, Date.now.timeIntervalSince(last) < 90 {
            networkRepair = .failed(String(localized: "The network was just reset and still is not responding."))
            append(.veil(.warn, "Network was reset \(Int(Date.now.timeIntervalSince(last))) s ago; not resetting again"))
            return .stillDown
        }
        let primary = NetworkReset.primary() ?? primaryNetwork
        primaryNetwork = primary
        guard let primary else {
            networkRepair = .failed(String(localized: "No active network interface."))
            append(.veil(.warn, "Network reset skipped: no primary interface"))
            return .skipped
        }
        if primary.isTunnel, !manual {
            networkRepair = .skipped(String(localized: "Another VPN owns the connection (\(primary.interface)); not touching it."))
            append(.veil(.warn, "Network reset skipped: another VPN owns the default route (\(primary.interface))"))
            return .skipped
        }
        networkRepair = .resetting(primary.displayName)
        append(.veil(.notice, "Network is not responding (\(reason)); resetting \(primary.displayName) — the same as switching Wi-Fi off and on"))
        do {
            let method = try await Task.detached(priority: .userInitiated) {
                try await NetworkReset.perform(primary)
            }.value
            networkRepair = .waitingForNetwork
            let cameBack = await NetworkReset.waitForNetwork(timeout: .seconds(25))
            lastNetworkReset = .now
            primaryNetwork = NetworkReset.primary() ?? primary
            let report = await ConnectivityProbe.check()
            connectivity = report
            if report.isUsable {
                networkRepair = .recovered(method.title)
                append(.veil(.notice, "Network is back after the \(method.title): \(report.summary)"))
                return .recovered
            }
            networkRepair = .failed(cameBack
                ? String(localized: "Still no Internet after the reset.")
                : String(localized: "The network did not come back after the reset."))
            append(.veil(.warn, "Still no connectivity after the \(method.title): \(report.summary)"))
            return .stillDown
        } catch {
            networkRepair = .failed(error.localizedDescription)
            append(.veil(.warn, "Network reset failed: \(error.localizedDescription)"))
            return .stillDown
        }
    }

    /// The button: reset the network now and pick the connection up again afterwards.
    func resetNetwork() {
        guard !isResettingNetwork else { return }
        Task { [weak self] in
            guard let self else { return }
            append(.veil(.notice, "Network reset requested"))
            let outcome = await performNetworkReset(reason: "requested", manual: true)
            guard outcome == .recovered else { return }
            switch connection {
            case .connected, .connecting:
                reconnect()
            case .failed:
                connect()
            default:
                break
            }
        }
    }

    /// Just the probe, for the dashboard.
    func probeInternet() {
        guard !networkRepair.isBusy else { return }
        Task { [weak self] in
            guard let self else { return }
            networkRepair = .probing
            let report = await ConnectivityProbe.check()
            connectivity = report
            primaryNetwork = NetworkReset.primary()
            networkRepair = .idle
            append(.veil(.info, "Connectivity (manual): \(report.summary)"))
        }
    }

    private static func isStall(_ error: Error) -> Bool {
        guard let torError = error as? TorEngineError else { return false }
        switch torError {
        case .bootstrapStalled, .bootstrapTimeout: return true
        default: return false
        }
    }

    /// A crash in the middle of a service restart would leave it inactive; repair it on launch.
    private func restoreDisabledNetworkServiceIfNeeded() {
        guard NetworkReset.hasPendingRestore else { return }
        append(.veil(.warn, "A network service was left disabled by the previous run; re-enabling it."))
        Task.detached { [weak self] in
            do {
                let name = try NetworkReset.restoreDisabledServiceIfNeeded()
                await self?.append(.veil(.info, "Network service \(name ?? "") re-enabled"))
            } catch {
                await self?.append(.veil(.warn, "Could not re-enable the network service: \(error.localizedDescription)"))
            }
        }
    }

    private func verifyCircuitAfterDelay(_ delay: Duration) {
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            guard connection == .connected else { return }
            let first = await tunnelIsHealthy(timeout: .seconds(6))
            if first { return }
            do { try await Task.sleep(for: .seconds(12)) } catch { return }
            guard connection == .connected else { return }
            let second = await tunnelIsHealthy(timeout: .seconds(8))
            if second { return }
            let bounced = await softReconnect(reason: "network change")
            if bounced { return }
            guard settings.autoReconnect else { return }
            append(.veil(.warn, "Tunnel stopped carrying traffic after the network change; reconnecting"))
            reconnect()
        }
    }

    private func scheduleReconnectIfWanted() {
        guard settings.autoReconnect, !userRequestedDisconnect else { return }
        scheduleReconnect(after: .seconds(15))
    }

    private func scheduleReconnect(after delay: Duration) {
        reconnectTask?.cancel()
        reconnectPending = true
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            guard reconnectPending, !userRequestedDisconnect else { return }
            reconnectPending = false
            append(.veil(.notice, "Auto-reconnect"))
            if connection == .connected {
                reconnect()
            } else if connection == .failed || connection == .disconnected {
                connect()
            }
        }
    }

    func setAutoReconnect(_ enabled: Bool) {
        settings.autoReconnect = enabled
        if !enabled {
            reconnectTask?.cancel()
            reconnectPending = false
        }
    }

    func setKillSwitch(_ enabled: Bool) {
        settings.killSwitch = enabled
        if !enabled, killSwitchEngaged {
            Task { [weak self] in
                guard let self else { return }
                await teardown(keepWarm: settings.warmStart != .off)
                killSwitchEngaged = false
                if connection == .failed { connection = .disconnected }
            }
        }
    }

    func setIsolatePerSite(_ enabled: Bool) {
        settings.isolatePerSite = enabled
        // Applies to browser traffic at once; apps on Veil's SOCKS port directly pick it up on
        // their next connection.
        httpBridge?.lanePool.setSiteMode(enabled)
    }

    func setLanePoolEnabled(_ enabled: Bool) {
        guard settings.lanePoolEnabled != enabled else { return }
        settings.lanePoolEnabled = enabled
        if enabled { settings.latencyTuning = false }
        guard connection == .connected, let ports else { return }
        Task { [weak self] in
            guard let self else { return }
            if enabled {
                await startLanePool(ports: ports, transport: activeTransport ?? settings.transport)
            } else {
                httpBridge?.lanePool.stop()
                httpBridge?.poolPort = nil
                lanes = nil
                latency.start(socksPort: ports.socks)
            }
        }
    }

    func setLanePoolSize(_ count: Int) {
        let clamped = min(6, max(2, count))
        guard settings.lanePoolSize != clamped else { return }
        settings.lanePoolSize = clamped
        httpBridge?.lanePool.setLaneCount(clamped)
    }

    func setLanePoolHedging(_ enabled: Bool) {
        guard settings.lanePoolHedging != enabled else { return }
        settings.lanePoolHedging = enabled
        httpBridge?.lanePool.setHedging(enabled)
    }

    // MARK: YouTube Turbo (anti-throttling without Tor)

    func toggleTurbo() {
        if turboActive {
            stopTurbo()
        } else {
            startTurbo()
        }
    }

    func startTurbo() {
        guard !turboActive, connection == .disconnected || connection == .failed, !isTearingDown else { return }
        generation += 1
        let attempt = generation
        lastError = nil
        youtubeTest = nil
        Task { [weak self] in
            guard let self else { return }
            do {
                if killSwitchEngaged || httpBridge != nil {
                    await teardown()
                    killSwitchEngaged = false
                }
                let allocated = try PortAllocator.allocate(preferredSocks: settings.socksPort, preferredHTTP: settings.httpPort)
                let bridge = HTTPProxyBridge(socksPort: nil, policy: settings.routingPolicy)
                try bridge.start(port: allocated.http)
                httpBridge = bridge
                ports = allocated
                append(.veil(.notice, "YouTube Turbo: anti-throttling proxy on 127.0.0.1:\(allocated.http); Tor is not used"))
                if settings.configureSystemProxy {
                    await enableSystemProxy(socks: nil, http: allocated.http)
                } else {
                    proxyStatus = .manual
                }
                guard attempt == generation else { return }
                turboActive = true
                startStatsUpdates()
                if connection == .failed { connection = .disconnected }
            } catch {
                append(.veil(.error, error.localizedDescription))
                lastError = AppError(title: String(localized: "YouTube Turbo could not start"), message: error.localizedDescription)
                await teardown()
            }
        }
    }

    func stopTurbo() {
        guard turboActive else { return }
        generation += 1
        Task { [weak self] in
            guard let self else { return }
            await teardown()
            turboActive = false
            youtubeTest = nil
            append(.veil(.info, "YouTube Turbo stopped"))
        }
    }

    // MARK: Routing (YouTube, services, custom domains)

    func setYouTubeMode(_ mode: RouteMode) {
        guard settings.youtubeMode != mode else { return }
        settings.youtubeMode = mode
        youtubeTest = nil
        pushRoutingPolicy()
    }

    func setServiceRoute(_ serviceID: String, _ mode: RouteMode) {
        let preset = ServiceCatalog.preset(serviceID)?.defaultMode ?? .tor
        if mode == preset {
            settings.serviceRoutes[serviceID] = nil
        } else {
            settings.serviceRoutes[serviceID] = mode
        }
        pushRoutingPolicy()
    }

    func serviceRoute(_ serviceID: String) -> RouteMode {
        settings.serviceRoutes[serviceID] ?? ServiceCatalog.preset(serviceID)?.defaultMode ?? .tor
    }

    // MARK: Apps

    /// The SOCKS5 port apps can be pointed at (the active one, or the configured one before connecting).
    var socksPortForApps: UInt16 {
        ports?.socks ?? UInt16(clamping: settings.socksPort)
    }

    /// Hands Telegram a `tg://socks` link so it adds Veil as its SOCKS5 proxy.
    func addProxyToTelegram() {
        let port = socksPortForApps
        if TelegramIntegration.open(TelegramIntegration.proxyURL(socksPort: port)) {
            append(.veil(.notice, "Asked Telegram to add the SOCKS5 proxy 127.0.0.1:\(port); confirm it inside Telegram"))
        } else {
            lastError = AppError(
                title: String(localized: "Telegram was not found"),
                message: String(localized: "Install Telegram for macOS, then add a SOCKS5 proxy with server 127.0.0.1 and port \(String(port)) in Telegram → Settings → Data and Storage → Proxy.")
            )
        }
    }

    func setDPIStrategy(_ strategy: DPIStrategy) {
        guard settings.dpiStrategy != strategy else { return }
        settings.dpiStrategy = strategy
        youtubeTest = nil
        pushRoutingPolicy()
    }

    func setCustomDirectDomains(_ text: String) {
        guard settings.customDirectDomains != text else { return }
        settings.customDirectDomains = text
        pushRoutingPolicy()
    }

    func setCustomDirectAntiThrottle(_ enabled: Bool) {
        guard settings.customDirectAntiThrottle != enabled else { return }
        settings.customDirectAntiThrottle = enabled
        pushRoutingPolicy()
    }

    private func pushRoutingPolicy() {
        httpBridgePolicyChanged()
    }

    /// Opens youtube.com through Veil's own proxy, exactly like a browser would, and measures the homepage speed.
    func testYouTube() {
        guard let bridge = httpBridge, !isTestingYouTube else { return }
        let port = bridge.port
        let viaTor = settings.routingPolicy.decision(for: "www.youtube.com", torAvailable: bridge.torAvailable) == .tor
        isTestingYouTube = true
        Task { [weak self] in
            guard let self else { return }
            defer { isTestingYouTube = false }
            do {
                let probe = try await YouTubeBenchmark.measure(url: YouTubeBenchmark.probe, httpPort: port, timeout: 30)
                let success = probe.status == 204 || probe.status == 200
                var speed: Double?
                if success, let homepage = try? await YouTubeBenchmark.measure(url: YouTubeBenchmark.homepage, httpPort: port, timeout: 30) {
                    speed = homepage.kilobytesPerSecond
                }
                youtubeTest = YouTubeTestResult(success: success, milliseconds: probe.milliseconds, detail: "HTTP \(probe.status)", viaTor: viaTor, kilobytesPerSecond: speed)
                append(.veil(success ? .notice : .warn, "YouTube check: HTTP \(probe.status) in \(probe.milliseconds) ms (\(viaTor ? "through Tor" : "direct"))\(speed.map { String(format: ", homepage %.0f KB/s", $0) } ?? "")"))
            } catch {
                youtubeTest = YouTubeTestResult(success: false, milliseconds: 0, detail: error.localizedDescription, viaTor: viaTor, kilobytesPerSecond: nil)
                append(.veil(.warn, "YouTube check failed: \(error.localizedDescription)"))
            }
        }
    }

    /// Tries every anti-throttling technique against the YouTube homepage and keeps the fastest.
    func autoSelectYouTubeTechnique() {
        guard let bridge = httpBridge, !isBenchmarking else { return }
        guard settings.routingPolicy.decision(for: "www.youtube.com", torAvailable: bridge.torAvailable) != .tor else {
            append(.veil(.warn, "Switch YouTube to a direct mode (or enable YouTube Turbo) before benchmarking techniques"))
            return
        }
        isBenchmarking = true
        benchmarkResults = []
        let original = bridge.policy
        Task { [weak self] in
            guard let self else { return }
            defer {
                isBenchmarking = false
                pushRoutingPolicy()
            }
            var results: [YouTubeBenchmark.StrategyResult] = []
            for strategy in DPIStrategy.allCases {
                var policy = original
                policy.strategy = strategy
                bridge.policy = policy
                do {
                    let measurement = try await YouTubeBenchmark.measure(url: YouTubeBenchmark.homepage, httpPort: bridge.port, timeout: 20)
                    results.append(YouTubeBenchmark.StrategyResult(strategy: strategy, measurement: measurement, error: nil))
                    append(.veil(.info, "Technique \(strategy.rawValue): \(measurement.milliseconds) ms, \(Int(measurement.kilobytesPerSecond)) KB/s"))
                } catch {
                    results.append(YouTubeBenchmark.StrategyResult(strategy: strategy, measurement: nil, error: error.localizedDescription))
                    append(.veil(.info, "Technique \(strategy.rawValue) failed: \(error.localizedDescription)"))
                }
                benchmarkResults = results
            }
            if let best = results.compactMap({ result -> (DPIStrategy, Double)? in
                guard let measurement = result.measurement, (200..<400).contains(measurement.status) else { return nil }
                return (result.strategy, measurement.kilobytesPerSecond)
            }).max(by: { $0.1 < $1.1 }) {
                settings.dpiStrategy = best.0
                append(.veil(.notice, "Selected anti-throttling technique: \(best.0.rawValue) (\(Int(best.1)) KB/s)"))
            } else {
                append(.veil(.warn, "No technique loaded YouTube; keeping the current one"))
            }
        }
    }

    // MARK: Bridges from the Tor Project (Moat)

    func fetchBridgesFromTorProject() {
        guard moatStatus != .fetching else { return }
        moatStatus = .fetching
        let country = Locale.current.region?.identifier
        Task { [weak self] in
            guard let self else { return }
            do {
                let sets = try await MoatClient.fetchCircumventionSettings(country: country)
                let preferred = sets.first { $0.transport == "obfs4" } ?? sets.first { $0.transport == "webtunnel" } ?? sets[0]
                let lines = preferred.bridges
                settings.customBridges = lines.joined(separator: "\n")
                settings.transport = .custom
                moatStatus = .done(bridges: lines.count, transport: preferred.transport)
                append(.veil(.notice, "Received \(lines.count) \(preferred.transport) bridge(s) from the Tor Project (\(preferred.source))"))
            } catch {
                moatStatus = .failed(error.localizedDescription)
                append(.veil(.warn, "Bridge request failed: \(error.localizedDescription)"))
            }
        }
    }

    // MARK: Traffic padding

    func setPaddingEnabled(_ enabled: Bool) {
        guard settings.paddingEnabled != enabled else { return }
        settings.paddingEnabled = enabled
        guard connection == .connected, let ports else { return }
        Task { [weak self] in
            guard let self else { return }
            await engine.setTorPadding(enabled: enabled)
            if enabled {
                padding.start(engine: engine, socksPort: ports.socks, level: settings.paddingLevel)
            } else {
                await padding.stop()
                append(.veil(.notice, "Traffic padding switched off"))
            }
        }
    }

    func setPaddingLevel(_ level: PaddingLevel) {
        guard settings.paddingLevel != level else { return }
        settings.paddingLevel = level
        guard connection == .connected, settings.paddingEnabled, let ports else { return }
        Task { [weak self] in
            guard let self else { return }
            await padding.stop()
            padding.start(engine: engine, socksPort: ports.socks, level: level)
        }
    }

    // MARK: Route (exit country, multihop)

    func setExitCountry(_ code: String?) {
        let normalized = code?.lowercased()
        guard settings.exitCountry != normalized else { return }
        settings.exitCountry = normalized
        applyRouteIfConnected()
    }

    func setMiddleCountry(_ code: String?) {
        let normalized = code?.lowercased()
        guard settings.middleCountry != normalized else { return }
        settings.middleCountry = normalized
        applyRouteIfConnected()
    }

    func setMultihopEnabled(_ enabled: Bool) {
        guard settings.multihopEnabled != enabled else { return }
        settings.multihopEnabled = enabled
        applyRouteIfConnected()
        restartRouteRotation()
    }

    func setAvoidFiveEyes(_ avoid: Bool) {
        guard settings.avoidFiveEyes != avoid else { return }
        settings.avoidFiveEyes = avoid
        applyRouteIfConnected()
    }

    func toggleExcludedCountry(_ code: String) {
        let normalized = code.lowercased()
        if let index = settings.excludedCountries.firstIndex(of: normalized) {
            settings.excludedCountries.remove(at: index)
        } else {
            settings.excludedCountries.append(normalized)
        }
        applyRouteIfConnected()
    }

    func setRotateRouteMinutes(_ minutes: Int) {
        guard settings.rotateRouteMinutes != minutes else { return }
        settings.rotateRouteMinutes = minutes
        restartRouteRotation()
    }

    private func applyRouteIfConnected() {
        guard connection == .connected else { return }
        let dropConnections = !settings.seamlessRouteSwitch
        Task { [weak self] in
            await self?.switchRoute(reason: "route changed", dropConnections: dropConnections, avoiding: [])
        }
    }

    /// Pushes the route to Tor, pre-builds its first circuit and, when enabled, races circuits to
    /// pin the fastest relays. Without `dropConnections` existing connections keep their old
    /// circuits; new ones take the new route the moment its first circuit is ready.
    private func switchRoute(reason: String, dropConnections: Bool, avoiding: [String]) async {
        guard connection == .connected else { return }
        routeGeneration += 1
        let generation = routeGeneration
        tuneTask?.cancel()
        tuneTask = nil
        tuner.reset()
        routeLatencyBeforeTuning = nil
        var base = settings.route
        base.avoidedRelays = avoiding
        routeSwitching = true
        defer {
            if generation == routeGeneration { routeSwitching = false }
        }
        do {
            try await engine.applyRoute(base, dropConnections: dropConnections)
            // Lane circuits were built under the old route: replacing their keys means new
            // connections take the new one while in-flight sessions keep theirs.
            httpBridge?.lanePool.retireAll(reason: .routeChanged)
            let description = base.torrcLines.isEmpty ? "automatic" : base.torrcLines.joined(separator: ", ")
            append(.veil(.notice, "Route updated (\(reason)): \(description)\(dropConnections ? "; circuits rebuilt" : "; existing connections kept")"))
        } catch {
            append(.veil(.warn, "Could not change the route: \(error.localizedDescription)"))
            return
        }
        torCheck = nil
        // The old samples describe a route that no longer exists.
        latency.reset()
        await prebuildCircuit()
        guard generation == routeGeneration, connection == .connected else { return }
        refreshCircuit()
        if settings.latencyTuning {
            await tune(base: base, generation: generation)
        } else if settings.checkAfterConnect {
            runTorCheck()
        }
    }

    /// Builds the first circuit for the current route now, so the next connection does not wait.
    private func prebuildCircuit() async {
        guard let id = try? await engine.launchCircuit() else { return }
        if let info = await engine.awaitCircuit(id, timeout: .seconds(30)), info.status == .built, let seconds = info.buildTime {
            append(.veil(.info, "First circuit for the route built in \(Int((seconds * 1000).rounded())) ms"))
        }
    }

    // MARK: Route tuning (circuit races)

    private func startRouteTuning() {
        // Pinning collapses every lane onto the same exit, so the two are mutually exclusive.
        guard !settings.lanePoolEnabled, settings.latencyTuning, connection == .connected else { return }
        routeGeneration += 1
        let generation = routeGeneration
        let base = settings.route
        tuneTask?.cancel()
        tuneTask = Task { [weak self] in
            guard let self else { return }
            // Let the first check and Tor's own initial circuits settle first.
            do { try await Task.sleep(for: .seconds(3)) } catch { return }
            await tune(base: base, generation: generation)
        }
    }

    private func tune(base: TorRoute, generation: Int) async {
        guard !settings.lanePoolEnabled, settings.latencyTuning, connection == .connected else { return }
        // Race on the country-level route so the candidates are not limited to old pins.
        try? await engine.applyRoute(base, dropConnections: false)
        guard let tuned = await tuner.tune(engine: engine, route: base, circuits: 4, pinMiddle: base.middleCountry != nil) else { return }
        guard generation == routeGeneration, connection == .connected else { return }
        var applied = tuned
        applied.avoidedRelays = []
        do {
            try await engine.applyRoute(applied, dropConnections: false)
        } catch {
            append(.veil(.warn, "Could not pin the fastest relays: \(error.localizedDescription)"))
            tuner.reset()
            return
        }
        // Measure the route as it stands before the pins take effect, so the comparison is real.
        let before = routeLatency
        await prebuildCircuit()
        guard generation == routeGeneration, connection == .connected else { return }
        routeLatencyBeforeTuning = before
        latency.reset()
        latency.measureNow()
        checkFailures = 0
        refreshCircuit()
        if settings.checkAfterConnect { runTorCheck() }
    }

    /// The button: measure again and re-pin.
    func retuneRoute() {
        guard !settings.lanePoolEnabled, connection == .connected, !tuner.status.isRacing else { return }
        routeGeneration += 1
        let generation = routeGeneration
        let base = settings.route
        tuneTask?.cancel()
        tuneTask = Task { [weak self] in
            await self?.tune(base: base, generation: generation)
        }
    }

    func setLatencyTuning(_ enabled: Bool) {
        guard settings.latencyTuning != enabled else { return }
        settings.latencyTuning = enabled
        guard connection == .connected else { return }
        if enabled {
            retuneRoute()
        } else {
            unpinRoute(reason: "route tuning switched off")
        }
    }

    /// Back to Tor's own relay choice within the chosen countries.
    private func unpinRoute(reason: String) {
        routeGeneration += 1
        tuneTask?.cancel()
        tuneTask = nil
        tuner.reset()
        routeLatencyBeforeTuning = nil
        let base = settings.route
        Task { [weak self] in
            guard let self else { return }
            try? await engine.applyRoute(base, dropConnections: false)
            append(.veil(.notice, "Relays unpinned (\(reason)); Tor chooses them again"))
        }
    }

    /// Relays get busier or quieter over time; measure again every half hour.
    private func startRetuneTimer() {
        retuneTask?.cancel()
        retuneTask = Task { [weak self] in
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(30 * 60)) } catch { return }
                guard let self else { return }
                guard !settings.lanePoolEnabled, connection == .connected, settings.latencyTuning,
                      !tuner.status.isRacing, !routeSwitching else { continue }
                append(.veil(.info, "Periodic route tuning"))
                retuneRoute()
            }
        }
    }

    /// Periodically requests a new identity while multihop rotation is on.
    private func restartRouteRotation() {
        rotationTask?.cancel()
        rotationTask = nil
        guard connection == .connected, settings.multihopEnabled, settings.rotateRouteMinutes > 0 else { return }
        let minutes = settings.rotateRouteMinutes
        rotationTask = Task { [weak self] in
            while !Task.isCancelled {
                do {
                    try await Task.sleep(for: .seconds(minutes * 60))
                } catch {
                    return
                }
                guard let self else { return }
                guard connection == .connected else { return }
                append(.veil(.info, "Multihop: rotating the route"))
                await switchRoute(reason: "rotation", dropConnections: !settings.seamlessRouteSwitch, avoiding: tuner.pinnedExits.map(\.fingerprint))
            }
        }
    }

    // MARK: Circuit, identity, checks, stats

    private func startCircuitUpdates() {
        circuitTask?.cancel()
        circuitTask = Task { [weak self] in
            var delaySeconds = 1.0
            while !Task.isCancelled {
                guard let self else { return }
                if let hops = try? await self.engine.circuit(), !hops.isEmpty {
                    if hops != self.circuit { self.circuit = hops }
                    self.circuitUpdatedAt = .now
                    delaySeconds = 20
                } else {
                    delaySeconds = min(delaySeconds * 2, 10)
                }
                do { try await Task.sleep(for: .seconds(delaySeconds)) } catch { return }
            }
        }
    }

    private func startStatsUpdates() {
        statsTask?.cancel()
        statsTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if let bridge = httpBridge {
                    let stats = bridge.stats
                    if stats != bridgeStats { bridgeStats = stats }
                    bridgeUpdatedAt = .now
                    let open = bridge.inFlight
                    if open != bridgeInFlight { bridgeInFlight = open }
                    if traffic.downloadRate > 0 || traffic.uploadRate > 0 { trafficUpdatedAt = .now }
                    let snapshot = bridge.lanePool.snapshot()
                    if snapshot.enabled || lanes != nil {
                        if snapshot != lanes { lanes = snapshot.enabled ? snapshot : nil }
                    }
                }
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
            }
        }
    }

    func refreshCircuit() {
        guard connection == .connected else { return }
        startCircuitUpdates()
    }

    func requestNewIdentity() {
        guard connection == .connected, !isChangingIdentity else { return }
        isChangingIdentity = true
        Task { [weak self] in
            guard let self else { return }
            defer { isChangingIdentity = false }
            if settings.latencyTuning {
                // Pinned exits would come straight back after NEWNYM: move to other relays and measure again.
                await switchRoute(reason: "new identity", dropConnections: true, avoiding: tuner.pinnedExits.map(\.fingerprint))
                return
            }
            do {
                try await engine.newIdentity()
                httpBridge?.lanePool.retireAll(reason: .newIdentity)
                append(.veil(.notice, "New identity requested — circuits will be rebuilt."))
                torCheck = nil
                try? await Task.sleep(for: .seconds(2))
                refreshCircuit()
                if settings.checkAfterConnect { runTorCheck() }
            } catch {
                append(.veil(.warn, "New identity failed: \(error.localizedDescription)"))
            }
        }
    }

    func runTorCheck() {
        guard connection == .connected, let ports, !isCheckingTor else { return }
        isCheckingTor = true
        Task { [weak self] in
            guard let self else { return }
            defer { isCheckingTor = false }
            do {
                let started = Date.now
                let result = try await engine.check(httpPort: ports.http)
                let elapsed = Date.now.timeIntervalSince(started)
                torCheckSeconds = elapsed
                torCheck = result
                checkFailures = 0
                append(.veil(.info, "check.torproject.org: \(result.isTor ? "Tor confirmed" : "NOT using Tor"), exit IP \(result.ip), fetched in \(Int(elapsed * 1000)) ms"))
            } catch {
                append(.veil(.warn, "Tor check failed: \(error.localizedDescription)"))
                checkFailures += 1
                if checkFailures >= 2, settings.lanePoolEnabled {
                    append(.veil(.warn, "The exit check keeps failing; replacing the measured circuits"))
                    httpBridge?.lanePool.retireAll(reason: .checkFailed)
                    checkFailures = 0
                } else if checkFailures >= 2, tuner.isPinned {
                    append(.veil(.warn, "Pinned relays are not answering; back to Tor's own relay choice"))
                    unpinRoute(reason: "pinned relays unreachable")
                }
            }
        }
    }

    func probeReachability() {
        Task { [weak self] in
            guard let self else { return }
            let report = await ReachabilityProbe.probeDirectTor()
            reachability = report
            append(.veil(.info, "Direct Tor reachability: \(report.reachable)/\(report.total) directory authorities answered"))
        }
    }

    // MARK: Updates

    private func scheduleUpdateCheck() {
        guard settings.checkForUpdates, Bundle.main.bundleIdentifier != nil else { return }
        // Deferred by default: an update check before Tor is up goes out in the clear and tells
        // whoever is watching that this Mac runs Veil.
        guard !settings.updateCheckAfterConnect else { return }
        let last = UserDefaults.standard.object(forKey: Self.lastUpdateCheckKey) as? Date ?? .distantPast
        guard Date.now.timeIntervalSince(last) > 6 * 3600 else { return }
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(8))
            self?.checkForUpdates(manual: false)
        }
    }

    func checkForUpdates(manual: Bool) {
        guard !isCheckingUpdate else { return }
        isCheckingUpdate = true
        if manual { updateCheckMessage = nil }
        Task { [weak self] in
            guard let self else { return }
            defer { isCheckingUpdate = false }
            do {
                let info = try await UpdateChecker.check()
                UserDefaults.standard.set(Date.now, forKey: Self.lastUpdateCheckKey)
                if let info, info.version != settings.skippedUpdateVersion || manual {
                    availableUpdate = info
                    updateCheckMessage = String(localized: "Version \(info.version) is available.")
                    append(.veil(.notice, "Update available: Veil \(info.version)"))
                    if !manual {
                        notify(id: "update", title: String(localized: "Veil \(info.version) is available"), body: String(localized: "Open Veil to download the update."))
                    }
                } else {
                    availableUpdate = nil
                    updateCheckMessage = String(localized: "You are up to date (\(UpdateChecker.currentVersion)).")
                }
            } catch {
                updateCheckMessage = String(localized: "Update check failed: \(error.localizedDescription)")
            }
        }
    }

    func skipAvailableUpdate() {
        settings.skippedUpdateVersion = availableUpdate?.version
        availableUpdate = nil
    }

    func openAvailableUpdate() {
        guard let update = availableUpdate else { return }
        NSWorkspace.shared.open(update.downloadURL)
    }

    // MARK: Settings transfer, diagnostics, onboarding

    func exportSettings() {
        SettingsTransfer.export(settings)
    }

    func importSettings() {
        guard var imported = SettingsTransfer.importSettings() else { return }
        imported.onboardingCompleted = true
        settings = imported
        pushRoutingPolicy()
        append(.veil(.notice, "Settings imported"))
    }

    func saveDiagnostics() {
        DiagnosticsReport.save(state: self)
    }

    func copyDiagnostics() {
        DiagnosticsReport.copy(state: self)
    }

    func completeOnboarding() {
        settings.onboardingCompleted = true
    }

    // MARK: Logs

    func append(_ entry: LogEntry) {
        logs.append(entry)
        if logs.count > Self.maxLogEntries {
            logs.removeFirst(logs.count - Self.maxLogEntries)
        }
    }

    func clearLogs() {
        logs.removeAll()
    }

    func copyLogs() {
        let formatter = Date.FormatStyle(date: .omitted, time: .standard)
        let text = logs.map { entry in
            "\(entry.date.formatted(formatter)) [\(entry.level.label)] \(entry.source == .veil ? "veil: " : "")\(entry.message)"
        }.joined(separator: "\n")
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }

    func copyTerminalSnippet() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(terminalSnippet, forType: .string)
    }

    func dismissError() {
        lastError = nil
        if connection == .failed, !killSwitchEngaged {
            connection = .disconnected
        }
    }

    private func notify(id: String, title: String, body: String) {
        guard settings.notificationsEnabled else { return }
        NotificationManager.shared.post(identifier: id, title: title, body: body)
    }

    /// Stop trying new transports after this long; the attempt is reported instead of dragging on.
    static let connectDeadline: TimeInterval = 240

    /// How long one transport gets. A transport that worked last time deserves a short leash: when
    /// it does not come up quickly something has changed, and moving on beats waiting two minutes.
    static func bootstrapBudget(isLast: Bool, isKnownGood: Bool, elapsed: TimeInterval) -> (timeout: Duration, stall: Duration) {
        if isLast {
            let remaining = max(60, connectDeadline - elapsed)
            return (.seconds(Int(remaining)), .seconds(90))
        }
        if isKnownGood {
            return (.seconds(70), .seconds(28))
        }
        return (.seconds(100), .seconds(40))
    }

    static func name(of transport: AppSettings.Transport) -> String {
        switch transport {
        case .auto: "Automatic"
        case .snowflake: "Snowflake"
        case .obfs4: "obfs4"
        case .meek: "meek"
        case .custom: "custom bridges"
        case .direct: "direct connection"
        }
    }
}
