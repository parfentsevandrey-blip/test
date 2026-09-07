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
    /// Result of the last Internet probe (TCP to public resolvers plus one name lookup).
    private(set) var connectivity: ConnectivityProbe.Report?
    private(set) var networkRepair: NetworkRepairStatus = .idle
    private(set) var isResettingNetwork = false
    private(set) var lastNetworkReset: Date?
    /// The interface carrying the default route (Wi-Fi, Ethernet, or another VPN's tunnel).
    private(set) var primaryNetwork: NetworkReset.Primary?
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
    @ObservationIgnored private var sleptAt: Date?
    @ObservationIgnored private var suppressPathEvents = false
    @ObservationIgnored private var networkRepairedThisConnect = false

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
        restoreDisabledNetworkServiceIfNeeded()
        primaryNetwork = NetworkReset.primary()
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

                // The network often looks fine after sleep or after another VPN quits while nothing
                // gets through. Find out now, and fix it the way people do by hand (Wi-Fi off/on).
                networkRepairedThisConnect = false
                let repair = await repairNetworkIfNeeded(trigger: "connect", allowReset: settings.autoResetNetwork)
                if repair == .recovered { networkRepairedThisConnect = true }
                try Task.checkCancellation()
                guard attempt == generation else { return }

                if settings.transport == .auto {
                    let report = await ReachabilityProbe.probeDirectTor()
                    reachability = report
                    append(.veil(.info, "Direct Tor reachability: \(report.reachable)/\(report.total) directory authorities answered"))
                }
                try Task.checkCancellation()
                guard attempt == generation else { return }

                var queue = transportCandidates()
                let planned = queue.count
                var connectedTransport: AppSettings.Transport?
                var lastFailure: Error?
                var previous: AppSettings.Transport?
                var index = 0
                while index < queue.count {
                    let transport = queue[index]
                    try Task.checkCancellation()
                    guard attempt == generation else { return }
                    activeTransport = transport
                    bootstrap = BootstrapProgress()
                    if planned > 1 || index > 0 {
                        if let previous {
                            transportAttemptMessage = String(localized: "\(Self.name(of: previous)) did not respond, trying \(Self.name(of: transport))…")
                        } else {
                            transportAttemptMessage = String(localized: "Trying \(Self.name(of: transport))…")
                        }
                        append(.veil(.notice, "Automatic transport: trying \(transport.rawValue) (\(index + 1)/\(queue.count))"))
                    }
                    do {
                        try await engine.start(settings: settings.resolving(transport: transport), ports: ports)
                        torVersion = engine.versionDescription
                        let isLast = index == queue.count - 1
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
                        // A stall on a network that claims to work is the classic stale-Wi-Fi symptom:
                        // reset once, and if that brought the Internet back, retry the same transport.
                        if Self.isStall(error), !networkRepairedThisConnect, self.settings.autoResetNetwork {
                            networkRepairedThisConnect = true
                            let outcome = await repairNetworkIfNeeded(trigger: "stall at \(bootstrap.percent)%", allowReset: true)
                            if outcome == .recovered {
                                append(.veil(.notice, "Network recovered; retrying \(transport.rawValue)"))
                                queue.insert(transport, at: index + 1)
                            }
                        }
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
        guard connection == .connected || connection == .failed || connection == .connecting else { return }
        generation += 1
        let previous = connectTask
        previous?.cancel()
        connectTask = nil
        Task { [weak self] in
            guard let self else { return }
            // Let a cancelled attempt finish stopping its Tor before a new one is started.
            await previous?.value
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

    /// After sleep the path may look satisfied while nothing works, and Tor's own connections are
    /// dead anyway. Wait for Wi-Fi to come back, reset it when it does not, then restart Tor when
    /// the sleep was long enough for its circuits and the Snowflake proxy to be gone.
    private func scheduleWakeRecovery(sleptFor: TimeInterval) {
        reconnectTask?.cancel()
        reconnectPending = false
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            do { try await Task.sleep(for: .seconds(4)) } catch { return }
            let outcome = await repairNetworkIfNeeded(trigger: "wake", allowReset: settings.autoResetNetwork, patience: .seconds(20))
            guard !Task.isCancelled else { return }
            switch connection {
            case .connected:
                let longSleep = sleptFor > 120
                if outcome == .recovered || longSleep {
                    guard settings.autoReconnect else { return }
                    append(.veil(.notice, longSleep ? "Restarting Tor after a long sleep" : "Restarting Tor after the network reset"))
                    reconnect()
                    return
                }
                if await engine.isCircuitEstablished() { return }
                do { try await Task.sleep(for: .seconds(15)) } catch { return }
                guard connection == .connected else { return }
                if await engine.isCircuitEstablished() { return }
                guard settings.autoReconnect else { return }
                append(.veil(.warn, "Tor lost its circuits after sleep; reconnecting"))
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

    /// Probes the Internet; when nothing answers, waits `patience` for Wi-Fi to settle, then resets
    /// the network (if allowed) and probes again.
    private func repairNetworkIfNeeded(trigger: String, allowReset: Bool, patience: Duration = .seconds(3)) async -> NetworkRepairOutcome {
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
