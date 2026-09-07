import AppKit
import Foundation
import Network
import Observation

struct YouTubeTestResult: Equatable, Sendable {
    let success: Bool
    let milliseconds: Int
    let detail: String
    let viaTor: Bool
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
    let traffic = TrafficMonitor()
    let padding = PaddingLoop()
    let isDemo: Bool

    @ObservationIgnored private var engine: any TorEngine
    @ObservationIgnored private var httpBridge: HTTPProxyBridge?
    @ObservationIgnored private var connectTask: Task<Void, Never>?
    @ObservationIgnored private var circuitTask: Task<Void, Never>?
    @ObservationIgnored private var rotationTask: Task<Void, Never>?
    @ObservationIgnored private var proxyApplied = false
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var isTearingDown = false

    private static let maxLogEntries = 2000
    /// Remembers that the system proxy points at Veil, so a crash or force-quit can be repaired on the next launch.
    private static let proxyAppliedKey = "app.veilvpn.systemProxyApplied"

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
        if isDemo {
            append(.veil(.notice, "Demo mode: tor binaries were not found next to the app, connections are simulated."))
        }
        restoreStaleSystemProxyIfNeeded()
        if settings.connectOnLaunch {
            connect()
        }
    }

    // MARK: Derived

    var selectedExit: ExitLocation? { ExitLocation.named(settings.exitCountry) }
    var selectedMiddle: ExitLocation? { settings.multihopEnabled ? ExitLocation.named(settings.middleCountry) : nil }
    var middleHop: CircuitHop? { circuit.first { $0.role == .middle } }
    var exitHop: CircuitHop? { circuit.last { $0.role == .exit } }
    var needsTeardown: Bool { connection != .disconnected || proxyApplied || turboActive }
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

    func connect() {
        guard connection == .disconnected || connection == .failed, !isTearingDown else { return }
        generation += 1
        let attempt = generation
        lastError = nil
        torCheck = nil
        circuit = []
        bootstrap = BootstrapProgress()
        connection = .connecting
        let settings = self.settings

        connectTask = Task { [weak self] in
            guard let self else { return }
            do {
                if turboActive {
                    await teardown()
                    turboActive = false
                }
                let ports = try PortAllocator.allocate(preferredSocks: settings.socksPort, preferredHTTP: settings.httpPort)
                self.ports = ports
                if Int(ports.socks) != settings.socksPort || Int(ports.http) != settings.httpPort {
                    append(.veil(.warn, "Preferred ports are busy; using SOCKS \(ports.socks) and HTTP \(ports.http) instead."))
                }

                try await engine.start(settings: settings, ports: ports)
                torVersion = engine.versionDescription
                try await engine.waitForBootstrap(timeout: .seconds(180))
                try Task.checkCancellation()
                guard attempt == generation else { return }

                let bridge = HTTPProxyBridge(socksPort: ports.socks, policy: settings.routingPolicy)
                try bridge.start(port: ports.http)
                httpBridge = bridge
                append(.veil(.info, "HTTP proxy bridge listening on 127.0.0.1:\(ports.http) → SOCKS5 127.0.0.1:\(ports.socks)"))

                if settings.configureSystemProxy {
                    do {
                        let services = try await SystemProxy.enable(socksPort: ports.socks, httpPort: ports.http)
                        proxyApplied = true
                        UserDefaults.standard.set(true, forKey: Self.proxyAppliedKey)
                        proxyStatus = .configured(services)
                        append(.veil(.notice, "System proxy enabled for \(services.joined(separator: ", "))"))
                    } catch {
                        proxyStatus = .failed(error.localizedDescription)
                        append(.veil(.warn, "System proxy was not configured: \(error.localizedDescription)"))
                    }
                } else {
                    proxyStatus = .manual
                }
                guard attempt == generation else { return }

                connectedAt = .now
                connection = .connected
                traffic.start(engine: engine)
                startCircuitUpdates()
                if settings.paddingEnabled {
                    padding.start(engine: engine, socksPort: ports.socks, level: settings.paddingLevel)
                }
                restartRouteRotation()
                if settings.checkAfterConnect {
                    runTorCheck()
                }
            } catch is CancellationError {
                await engine.stop()
            } catch {
                guard attempt == generation else { return }
                append(.veil(.error, error.localizedDescription))
                lastError = AppError(title: String(localized: "Could not connect"), message: error.localizedDescription)
                await teardown()
                if attempt == generation {
                    connection = .failed
                }
            }
        }
    }

    func disconnect() {
        guard connection == .connecting || connection == .connected else { return }
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        connection = .disconnecting
        Task { [weak self] in
            guard let self else { return }
            await teardown()
            connection = .disconnected
            bootstrap = BootstrapProgress()
        }
    }

    /// Called from the app delegate before the process exits.
    func prepareForTermination() async {
        generation += 1
        connectTask?.cancel()
        connectTask = nil
        await teardown()
        turboActive = false
        connection = .disconnected
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
                let allocated = try PortAllocator.allocate(preferredSocks: settings.socksPort, preferredHTTP: settings.httpPort)
                let bridge = HTTPProxyBridge(socksPort: nil, policy: settings.routingPolicy)
                try bridge.start(port: allocated.http)
                httpBridge = bridge
                ports = allocated
                append(.veil(.notice, "YouTube Turbo: anti-throttling proxy on 127.0.0.1:\(allocated.http); Tor is not used"))
                if settings.configureSystemProxy {
                    do {
                        let services = try await SystemProxy.enable(socksPort: nil, httpPort: allocated.http)
                        proxyApplied = true
                        UserDefaults.standard.set(true, forKey: Self.proxyAppliedKey)
                        proxyStatus = .configured(services)
                    } catch {
                        proxyStatus = .failed(error.localizedDescription)
                        append(.veil(.warn, "System proxy was not configured: \(error.localizedDescription)"))
                    }
                } else {
                    proxyStatus = .manual
                }
                guard attempt == generation else { return }
                turboActive = true
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

    // MARK: YouTube routing

    func setYouTubeMode(_ mode: YouTubeMode) {
        guard settings.youtubeMode != mode else { return }
        settings.youtubeMode = mode
        youtubeTest = nil
        pushRoutingPolicy()
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

    /// Opens https://www.youtube.com/generate_204 through Veil's own proxy, exactly like a browser would.
    func testYouTube() {
        guard let bridge = httpBridge, !isTestingYouTube else { return }
        let port = bridge.port
        let viaTor = settings.routingPolicy.decision(for: "www.youtube.com", torAvailable: bridge.torAvailable) == .tor
        isTestingYouTube = true
        Task { [weak self] in
            guard let self else { return }
            defer { isTestingYouTube = false }
            let started = Date.now
            let configuration = URLSessionConfiguration.ephemeral
            configuration.proxyConfigurations = [
                ProxyConfiguration(httpCONNECTProxy: .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port) ?? 8118)),
            ]
            configuration.timeoutIntervalForRequest = 30
            configuration.timeoutIntervalForResource = 40
            let session = URLSession(configuration: configuration)
            defer { session.invalidateAndCancel() }
            do {
                let (_, response) = try await session.data(from: URL(string: "https://www.youtube.com/generate_204")!)
                let elapsed = Int((Date.now.timeIntervalSince(started) * 1000).rounded())
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                let success = status == 204 || status == 200
                youtubeTest = YouTubeTestResult(success: success, milliseconds: elapsed, detail: "HTTP \(status)", viaTor: viaTor)
                append(.veil(success ? .notice : .warn, "YouTube check: HTTP \(status) in \(elapsed) ms (\(viaTor ? "through Tor" : "direct"))"))
            } catch {
                let elapsed = Int((Date.now.timeIntervalSince(started) * 1000).rounded())
                youtubeTest = YouTubeTestResult(success: false, milliseconds: elapsed, detail: error.localizedDescription, viaTor: viaTor)
                append(.veil(.warn, "YouTube check failed after \(elapsed) ms: \(error.localizedDescription)"))
            }
        }
    }

    private func teardown() async {
        guard !isTearingDown else {
            while isTearingDown { try? await Task.sleep(for: .milliseconds(100)) }
            return
        }
        isTearingDown = true
        defer { isTearingDown = false }

        circuitTask?.cancel()
        circuitTask = nil
        rotationTask?.cancel()
        rotationTask = nil
        traffic.stop()
        await padding.stop()
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
        await engine.stop()
        connectedAt = nil
        circuit = []
        torCheck = nil
        routeLatency = nil
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
            await teardown()
            connection = .failed
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

    // MARK: Circuit, identity, checks

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
        if connection == .failed {
            connection = .disconnected
        }
    }
}
