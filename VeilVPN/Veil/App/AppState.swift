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
    /// Round-trip time of the last successful Tor check — a practical measure of route latency.
    private(set) var routeLatency: TimeInterval?
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
    /// Sidebar selection, so menu commands can navigate.
    var sidebarSelection: SidebarItem = .home
    let traffic = TrafficMonitor()
    let padding = PaddingLoop()
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
        traffic.paddingRateProvider = { [weak self] in self?.padding.rate ?? 0 }
        networkWatcher.onEvent = { [weak self] event in self?.handleNetworkEvent(event) }
        networkWatcher.start()
        if isDemo {
            append(.veil(.notice, "Demo mode: tor binaries were not found next to the app, connections are simulated."))
        }
        restoreStaleSystemProxyIfNeeded()
        scheduleUpdateCheck()
        if settings.connectOnLaunch {
            connect()
        }
    }

    // MARK: Derived

    var selectedExit: ExitLocation? { ExitLocation.named(settings.exitCountry) }
    var selectedMiddle: ExitLocation? { settings.multihopEnabled ? ExitLocation.named(settings.middleCountry) : nil }
    var exitHop: CircuitHop? { circuit.last { $0.role == .exit } }
    var middleHop: CircuitHop? { circuit.first { $0.role == .middle } }
    var needsTeardown: Bool { connection != .disconnected || proxyApplied || turboActive || killSwitchEngaged }
    /// True while Veil's local HTTP bridge is serving (Tor connected or YouTube Turbo).
    var bridgeRunning: Bool { connection.isConnected || turboActive }

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

    /// Transports to try, in order. Automatic mode remembers the last one that worked.
    private func transportCandidates() -> [AppSettings.Transport] {
        guard settings.transport == .auto else { return [settings.transport] }
        var list: [AppSettings.Transport] = []
        if reachability?.directLooksPossible == true {
            list.append(.direct)
        }
        if let last = settings.lastWorkingTransport, last.isConcrete, !list.contains(last) {
            list.append(last)
        }
        if !TorConfiguration.parseBridgeLines(settings.customBridges).isEmpty, !list.contains(.custom) {
            list.append(.custom)
        }
        for transport in [AppSettings.Transport.snowflake, .obfs4, .meek] where !list.contains(transport) {
            list.append(transport)
        }
        return list
    }

    func connect() {
        guard connection == .disconnected || connection == .failed, !isTearingDown else { return }
        generation += 1
        let attempt = generation
        userRequestedDisconnect = false
        reconnectPending = false
        lastError = nil
        torCheck = nil
        routeLatency = nil
        circuit = []
        bootstrap = BootstrapProgress()
        transportAttemptMessage = nil
        connection = .connecting
        let settings = self.settings

        connectTask = Task { [weak self] in
            guard let self else { return }
            do {
                if turboActive {
                    await teardown()
                    turboActive = false
                }
                await NotificationManager.shared.requestAuthorizationIfNeeded()

                // Reuse the ports and the (blocking) bridge left behind by the kill switch, so the
                // network is never opened up between a failure and the reconnection.
                let ports: ActivePorts
                if let existing = self.ports, httpBridge != nil {
                    ports = existing
                } else {
                    ports = try PortAllocator.allocate(preferredSocks: settings.socksPort, preferredHTTP: settings.httpPort)
                    self.ports = ports
                    if Int(ports.socks) != settings.socksPort || Int(ports.http) != settings.httpPort {
                        append(.veil(.warn, "Preferred ports are busy; using SOCKS \(ports.socks) and HTTP \(ports.http) instead."))
                    }
                }

                // Kill switch: fail closed from the very first second.
                if settings.killSwitch, settings.configureSystemProxy {
                    if httpBridge == nil {
                        let bridge = HTTPProxyBridge(socksPort: nil, policy: settings.routingPolicy)
                        bridge.blockAll = true
                        try bridge.start(port: ports.http)
                        httpBridge = bridge
                    }
                    if !proxyApplied {
                        await enableSystemProxy(socks: ports.socks, http: ports.http)
                    }
                    append(.veil(.info, "Kill switch armed: proxied apps are blocked until Tor is up"))
                }

                if settings.transport == .auto {
                    let report = await ReachabilityProbe.probeDirectTor()
                    reachability = report
                    append(.veil(.info, "Direct Tor reachability: \(report.reachable)/\(report.total) directory authorities answered"))
                }
                try Task.checkCancellation()
                guard attempt == generation else { return }

                let candidates = transportCandidates()
                var connectedTransport: AppSettings.Transport?
                var lastFailure: Error?
                for (index, transport) in candidates.enumerated() {
                    try Task.checkCancellation()
                    guard attempt == generation else { return }
                    activeTransport = transport
                    bootstrap = BootstrapProgress()
                    if candidates.count > 1 {
                        transportAttemptMessage = index == 0
                            ? String(localized: "Trying \(Self.name(of: transport))…")
                            : String(localized: "\(Self.name(of: candidates[index - 1])) did not respond, trying \(Self.name(of: transport))…")
                        append(.veil(.notice, "Automatic transport: trying \(transport.rawValue) (\(index + 1)/\(candidates.count))"))
                    }
                    do {
                        try await engine.start(settings: settings.resolving(transport: transport), ports: ports)
                        torVersion = engine.versionDescription
                        let isLast = index == candidates.count - 1 || candidates.count == 1
                        try await engine.waitForBootstrap(
                            timeout: isLast ? .seconds(240) : .seconds(120),
                            stallTimeout: isLast ? .seconds(150) : .seconds(45)
                        )
                        connectedTransport = transport
                        break
                    } catch is CancellationError {
                        throw CancellationError()
                    } catch {
                        lastFailure = error
                        append(.veil(.warn, "\(transport.rawValue): \(error.localizedDescription)"))
                        await engine.stop()
                    }
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

                // Bridge: reuse the blocking one from the kill switch, or start a fresh one.
                if let bridge = httpBridge {
                    bridge.policy = self.settings.routingPolicy
                    bridge.socksPort = ports.socks
                    bridge.blockAll = false
                } else {
                    let bridge = HTTPProxyBridge(socksPort: ports.socks, policy: self.settings.routingPolicy)
                    try bridge.start(port: ports.http)
                    httpBridge = bridge
                }
                append(.veil(.info, "HTTP proxy bridge listening on 127.0.0.1:\(ports.http) → SOCKS5 127.0.0.1:\(ports.socks)"))

                if settings.configureSystemProxy {
                    if !proxyApplied {
                        await enableSystemProxy(socks: ports.socks, http: ports.http)
                    }
                } else {
                    proxyStatus = .manual
                }
                guard attempt == generation else { return }

                killSwitchEngaged = false
                connectedAt = .now
                connection = .connected
                traffic.start(engine: engine)
                startCircuitUpdates()
                startStatsUpdates()
                if self.settings.paddingEnabled {
                    padding.start(engine: engine, socksPort: ports.socks, level: self.settings.paddingLevel)
                }
                restartRouteRotation()
                Feedback.connected(sound: self.settings.soundEffects, haptic: self.settings.hapticFeedback)
                notify(id: "connected", title: String(localized: "Connected through Tor"),
                       body: String(localized: "Transport: \(Self.name(of: connectedTransport)). Your traffic now goes through the Tor network."))
                if self.settings.checkAfterConnect {
                    runTorCheck()
                }
            } catch is CancellationError {
                await engine.stop()
            } catch {
                guard attempt == generation else { return }
                append(.veil(.error, error.localizedDescription))
                lastError = AppError(title: String(localized: "Could not connect"), message: error.localizedDescription)
                await failClosedOrTeardown(reason: error.localizedDescription)
                if attempt == generation {
                    connection = .failed
                    Feedback.failed(sound: self.settings.soundEffects, haptic: self.settings.hapticFeedback)
                    scheduleReconnectIfWanted()
                }
            }
        }
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
        Task { [weak self] in
            guard let self else { return }
            await teardown()
            killSwitchEngaged = false
            connection = .disconnected
            bootstrap = BootstrapProgress()
            activeTransport = nil
            transportAttemptMessage = nil
            Feedback.disconnected(sound: settings.soundEffects, haptic: settings.hapticFeedback)
        }
    }

    /// Stops Tor and connects again without opening the network in between.
    func reconnect() {
        guard connection == .connected || connection == .failed else { return }
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        Task { [weak self] in
            guard let self else { return }
            await stopEngineSide()
            if settings.killSwitch, let bridge = httpBridge {
                bridge.blockAll = true
                bridge.socksPort = nil
            }
            connection = .disconnected
            connect()
        }
    }

    /// Called from the app delegate before the process exits.
    func prepareForTermination() async {
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        reconnectTask?.cancel()
        await teardown()
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

    /// Everything on the Tor side, leaving the bridge and the system proxy alone.
    private func stopEngineSide() async {
        circuitTask?.cancel()
        circuitTask = nil
        rotationTask?.cancel()
        rotationTask = nil
        traffic.stop()
        await padding.stop()
        await engine.stop()
        connectedAt = nil
        circuit = []
        torCheck = nil
        routeLatency = nil
    }

    /// On failure: keep blocking when the kill switch is on, otherwise restore the network.
    private func failClosedOrTeardown(reason: String) async {
        if settings.killSwitch, proxyApplied, let bridge = httpBridge {
            await stopEngineSide()
            bridge.blockAll = true
            bridge.socksPort = nil
            killSwitchEngaged = true
            append(.veil(.warn, "Kill switch engaged: proxied traffic stays blocked until you reconnect or disconnect"))
            notify(id: "killswitch", title: String(localized: "Kill switch engaged"),
                   body: String(localized: "Tor is down; apps using the system proxy are blocked until Veil reconnects."))
        } else {
            await teardown()
        }
    }

    private func teardown() async {
        guard !isTearingDown else {
            while isTearingDown { try? await Task.sleep(for: .milliseconds(100)) }
            return
        }
        isTearingDown = true
        defer { isTearingDown = false }

        statsTask?.cancel()
        statsTask = nil
        await stopEngineSide()
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
        case .pathLost:
            guard connection == .connected || connection == .connecting else { return }
            append(.veil(.warn, "Network path lost"))
            if settings.autoReconnect { reconnectPending = true }
        case .pathRestored:
            append(.veil(.info, "Network path restored"))
            if settings.autoReconnect, reconnectPending || connection == .failed {
                scheduleReconnect(after: .seconds(3))
            } else if connection == .connected {
                verifyCircuitAfterDelay(.seconds(8))
            }
        case .didWake:
            append(.veil(.info, "Mac woke from sleep"))
            if connection == .connected {
                verifyCircuitAfterDelay(.seconds(6))
            } else if connection == .failed, settings.autoReconnect {
                scheduleReconnect(after: .seconds(5))
            }
        }
    }

    private func verifyCircuitAfterDelay(_ delay: Duration) {
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: delay) } catch { return }
            guard connection == .connected else { return }
            if await engine.isCircuitEstablished() { return }
            do { try await Task.sleep(for: .seconds(20)) } catch { return }
            guard connection == .connected else { return }
            if await engine.isCircuitEstablished() { return }
            guard settings.autoReconnect else { return }
            append(.veil(.warn, "Tor lost its circuits after the network change; reconnecting"))
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
                await teardown()
                killSwitchEngaged = false
                if connection == .failed { connection = .disconnected }
            }
        }
    }

    func setIsolatePerSite(_ enabled: Bool) {
        settings.isolatePerSite = enabled
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
        if mode == .tor {
            settings.serviceRoutes[serviceID] = nil
        } else {
            settings.serviceRoutes[serviceID] = mode
        }
        pushRoutingPolicy()
    }

    func serviceRoute(_ serviceID: String) -> RouteMode {
        settings.serviceRoutes[serviceID] ?? .tor
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
        httpBridge?.policy = settings.routingPolicy
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
        let route = settings.route
        Task { [weak self] in
            guard let self else { return }
            do {
                try await engine.applyRoute(route)
                let description = route.torrcLines.isEmpty ? "automatic" : route.torrcLines.joined(separator: ", ")
                append(.veil(.notice, "Route updated: \(description)"))
                torCheck = nil
                routeLatency = nil
                try? await Task.sleep(for: .seconds(2))
                refreshCircuit()
                if settings.checkAfterConnect { runTorCheck() }
            } catch {
                append(.veil(.warn, "Could not change the route: \(error.localizedDescription)"))
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
                requestNewIdentity()
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
            do {
                try await engine.newIdentity()
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
                routeLatency = Date.now.timeIntervalSince(started)
                torCheck = result
                append(.veil(.info, "check.torproject.org: \(result.isTor ? "Tor confirmed" : "NOT using Tor"), exit IP \(result.ip), round trip \(Int((routeLatency ?? 0) * 1000)) ms"))
            } catch {
                append(.veil(.warn, "Tor check failed: \(error.localizedDescription)"))
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
