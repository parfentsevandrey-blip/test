import AppKit
import Foundation
import Observation

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
    private(set) var isCheckingTor = false
    private(set) var isChangingIdentity = false
    private(set) var logs: [LogEntry] = []
    private(set) var lastError: AppError?
    private(set) var connectedAt: Date?
    private(set) var proxyStatus: ProxyStatus = .off
    private(set) var ports: ActivePorts?
    private(set) var torVersion: String?
    let traffic = TrafficMonitor()
    let isDemo: Bool

    @ObservationIgnored private var engine: any TorEngine
    @ObservationIgnored private var httpBridge: HTTPProxyBridge?
    @ObservationIgnored private var connectTask: Task<Void, Never>?
    @ObservationIgnored private var circuitTask: Task<Void, Never>?
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
    var exitHop: CircuitHop? { circuit.last { $0.role == .exit } }
    var needsTeardown: Bool { connection != .disconnected || proxyApplied }

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

                let bridge = HTTPProxyBridge(socksPort: ports.socks)
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
        connection = .disconnected
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
        traffic.stop()
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

    func setExitCountry(_ code: String?) {
        let normalized = code?.lowercased()
        guard settings.exitCountry != normalized || connection == .connected else { return }
        settings.exitCountry = normalized
        guard connection == .connected else { return }
        Task { [weak self] in
            guard let self else { return }
            do {
                try await engine.setExitCountry(normalized)
                if let normalized {
                    append(.veil(.notice, "Exit relays restricted to \(normalized.uppercased())"))
                } else {
                    append(.veil(.notice, "Exit relay selection is automatic again"))
                }
                torCheck = nil
                try? await Task.sleep(for: .seconds(2))
                refreshCircuit()
                if settings.checkAfterConnect { runTorCheck() }
            } catch {
                append(.veil(.warn, "Could not change exit country: \(error.localizedDescription)"))
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
                let result = try await engine.check(socksPort: ports.socks)
                torCheck = result
                append(.veil(.info, "check.torproject.org: \(result.isTor ? "Tor confirmed" : "NOT using Tor"), exit IP \(result.ip)"))
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
