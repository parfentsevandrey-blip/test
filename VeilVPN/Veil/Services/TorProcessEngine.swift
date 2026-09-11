import Foundation

/// Runs the bundled `tor` binary as a child process and talks to it over the control port.
@MainActor
final class TorProcessEngine: TorEngine {
    let isSimulated = false
    private(set) var versionDescription: String?

    var onLog: (@MainActor (LogEntry) -> Void)?
    var onBootstrap: (@MainActor (BootstrapProgress) -> Void)?
    var onExit: (@MainActor (Int32) -> Void)?
    /// Per-second byte deltas from `BW`, which cost nothing: they replace two control round trips
    /// a second on the connection that owns tor.
    var onBandwidth: (@MainActor (UInt64, UInt64) -> Void)?

    private let bundle: TorBundle
    private let supportDirectory: URL
    private var process: Process?
    private var processID: ObjectIdentifier?
    private var controller: TorControlClient?
    private var lineBuffer = ""
    private var bootstrap = BootstrapProgress()
    private var lastWarning: String?
    private var exitStatus: Int32?
    private var usesBridges = true
    private var descriptorUploads: [String: Int] = [:]
    /// Circuits seen through `CIRC` events, by id.
    private var circuits: [String: CircuitInfo] = [:]
    /// Signals waiting for the bootstrap watchdog to consume. MainActor throughout, so no locking.
    private var pendingSignals: [BootstrapWatchdog.Signal] = []
    private var disableNetwork = true
    private var boundPorts: ActivePorts?
    private var warmthProfile: WarmthProfile?
    private var standbyTorrc: String?
    private var launchedTransports: Set<String> = []
    private var bindFailure: UInt16?
    private var lockFailure = false
    private var stopping = false
    private var cachedTransportDirectory: URL?
    private var cachedDefaults: PluggableTransportDefaults?

    /// `BW` is the free liveness heartbeat: tor emits it once a second from its second-elapsed
    /// callback whether or not traffic flows, and it keeps ticking under `DisableNetwork 1`. A gap
    /// means the control connection is dead — which nothing else here can detect.
    private static let eventSubscription = "SETEVENTS CIRC HS_DESC STATUS_CLIENT ORCONN BW"

    var warmth: WarmthProfile? { warmthProfile }
    var isWarm: Bool { process != nil && controller?.isOpen == true && disableNetwork }
    var isLive: Bool { process != nil && controller?.isOpen == true && !disableNetwork }
    var activePorts: ActivePorts? { boundPorts }
    var bootstrapPercent: Int { bootstrap.percent }

    private static let circuitTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
        return formatter
    }()

    init(bundle: TorBundle) {
        self.bundle = bundle
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        supportDirectory = base.appendingPathComponent("Veil", isDirectory: true)
    }

    private var dataDirectory: URL { supportDirectory.appendingPathComponent("tor", isDirectory: true) }
    private var torrcURL: URL { supportDirectory.appendingPathComponent("torrc") }
    private var cookieURL: URL { dataDirectory.appendingPathComponent("control_auth_cookie") }

    // MARK: Lifecycle

    func start(settings: AppSettings, ports: ActivePorts) async throws {
        await stop()
        try Task.checkCancellation()

        exitStatus = nil
        bootstrap = BootstrapProgress()
        lineBuffer = ""
        lastWarning = nil
        bindFailure = nil
        lockFailure = false
        pendingSignals = []
        launchedTransports = []
        standbyTorrc = nil
        usesBridges = settings.transport != .direct

        let fileManager = FileManager.default
        try fileManager.createDirectory(at: dataDirectory, withIntermediateDirectories: true,
                                        attributes: [.posixPermissions: 0o700])
        let pluggableTransportDirectory = try PluggableTransportLocator.spaceFreeDirectory(for: bundle)
        let defaults = PluggableTransportDefaults.load(from: bundle.ptConfig)
        let configuration = TorConfiguration(
            settings: settings,
            ports: ports,
            bundle: bundle,
            dataDirectory: dataDirectory,
            pluggableTransportDirectory: pluggableTransportDirectory,
            defaults: defaults
        )
        let torrc = try configuration.render()
        try torrc.write(to: torrcURL, atomically: true, encoding: .utf8)
        try? fileManager.removeItem(at: cookieURL)

        emit(.veil(.info, "Launching tor (\(Self.describe(settings.transport))) · SOCKS \(ports.socks) · HTTP \(ports.http) · control \(ports.control)"))
        try spawn()

        do {
            let client = try await connectControl(port: ports.control)
            client.setEventHandler { [weak self] event in
                DispatchQueue.main.async {
                    MainActor.assumeIsolated {
                        self?.handleControlEvent(event)
                    }
                }
            }
            try await client.send("TAKEOWNERSHIP")
            _ = try? await client.send("SETCONF __OwningControllerProcess=\(ProcessInfo.processInfo.processIdentifier)")
            _ = try? await client.send(Self.eventSubscription)
            circuits = [:]
            controller = client
            disableNetwork = false
            boundPorts = ports
            warmthProfile = TorStateStore.read(dataDirectory: dataDirectory, processWarm: true)
            // Cosmetic, and a full round trip: never on the critical path.
            Task { [weak self] in
                guard let version = try? await client.getInfo("version") else { return }
                self?.versionDescription = version
                self?.emit(.veil(.info, "Tor \(version) is running"))
            }
        } catch {
            await stop()
            throw error
        }
    }

    // MARK: Warm lifecycle

    /// Spawns tor held offline and lets it load the consensus, descriptors and guards. Nothing
    /// reaches the network: `SocksPort 0` means no listener and `DisableNetwork 1` means no packets,
    /// which is observationally identical to tor not running.
    func warmUp(settings: AppSettings, controlPort: UInt16, mode: AppSettings.WarmStart,
                budget: Duration) async throws -> WarmthProfile {
        let transportDirectory = try cachedTransportDirectory ?? PluggableTransportLocator.spaceFreeDirectory(for: bundle)
        cachedTransportDirectory = transportDirectory
        let defaults = cachedDefaults ?? PluggableTransportDefaults.load(from: bundle.ptConfig)
        cachedDefaults = defaults

        let disk = TorStateStore.read(dataDirectory: dataDirectory)
        let predicted = settings.transport == .auto
            ? (disk.evidencedTransports.first ?? settings.lastWorkingTransport ?? .snowflake)
            : settings.transport
        let torrc = TorConfiguration.renderStandby(
            controlPort: controlPort,
            dataDirectory: dataDirectory,
            bundle: bundle,
            pluggableTransportDirectory: transportDirectory,
            defaults: defaults,
            settings: settings,
            predicted: predicted,
            consensus: disk.consensus,
            preBootstrap: mode == .preBootstrap
        )
        // warmUp is called unconditionally from connect(), so it must be idempotent.
        if isWarm, standbyTorrc == torrc, let warmthProfile { return warmthProfile }
        await stop(grace: .milliseconds(800))

        exitStatus = nil
        bootstrap = BootstrapProgress()
        lineBuffer = ""
        lastWarning = nil
        bindFailure = nil
        lockFailure = false
        pendingSignals = []
        launchedTransports = []
        circuits = [:]
        boundPorts = nil
        disableNetwork = mode != .preBootstrap

        try FileManager.default.createDirectory(at: dataDirectory, withIntermediateDirectories: true,
                                                attributes: [.posixPermissions: 0o700])
        try torrc.write(to: torrcURL, atomically: true, encoding: .utf8)
        try? FileManager.default.removeItem(at: cookieURL)
        standbyTorrc = torrc
        emit(.veil(.info, "Warming Tor on standby (control \(controlPort)) · \(disk.summary)"))
        try spawn()

        let client = try await connectControl(port: controlPort)
        client.setEventHandler { [weak self] event in
            DispatchQueue.main.async {
                MainActor.assumeIsolated { self?.handleControlEvent(event) }
            }
        }
        try await client.send("TAKEOWNERSHIP", timeout: .seconds(10))
        _ = try? await client.send("SETCONF __OwningControllerProcess=\(ProcessInfo.processInfo.processIdentifier)",
                                   timeout: .seconds(10))
        _ = try? await client.send(Self.eventSubscription, timeout: .seconds(10))
        controller = client
        Task { [weak self] in
            guard let version = try? await client.getInfo("version") else { return }
            self?.versionDescription = version
        }

        var profile = await settle(client: client, budget: budget, disk: disk)
        profile.evidencedTransports = TorStateStore.evidencedTransports(
            profile.state, defaults: defaults, customBridges: settings.customBridges)
        profile.launchedTransports = launchedTransports
        warmthProfile = profile
        emit(.veil(.info, "Tor is warm (\(profile.tier)) · \(profile.summary)"))
        return profile
    }

    /// Waits until Tor says it has enough directory information, or falls back to a flat settle on
    /// a build that does not answer that key.
    private func settle(client: TorControlClient, budget: Duration, disk: WarmthProfile) async -> WarmthProfile {
        var profile = disk
        profile.processWarm = true
        let deadline = ContinuousClock.now + budget
        var supported = true
        while ContinuousClock.now < deadline {
            if exitStatus != nil || client.isOpen == false { break }
            if supported {
                do {
                    if try await client.getInfo("status/enough-dir-info", timeout: .seconds(5)) == "1" {
                        profile.tier = WarmthProfile.tier(consensus: profile.consensus,
                                                          microdescsUsable: profile.microdescsUsable,
                                                          hasCerts: profile.hasCerts,
                                                          guardsKnown: profile.guardsKnown,
                                                          processWarm: true)
                        return profile
                    }
                } catch {
                    supported = false
                    emit(.veil(.debug, "This Tor does not answer status/enough-dir-info; settling instead"))
                }
            }
            if !supported {
                try? await Task.sleep(for: .seconds(3))
                break
            }
            do { try await Task.sleep(for: .milliseconds(250)) } catch { break }
        }
        let tier = WarmthProfile.tier(consensus: profile.consensus, microdescsUsable: profile.microdescsUsable,
                                      hasCerts: profile.hasCerts, guardsKnown: profile.guardsKnown,
                                      processWarm: true)
        profile.tier = min(tier, supported ? tier : .warm)
        return profile
    }

    /// The button press: one ordered SETCONF. It is all or nothing, so a bad configuration changes
    /// nothing, and because `DisableNetwork` is last the listener and the network come up together.
    func activate(settings: AppSettings, ports: ActivePorts) async throws -> Int {
        guard let controller, process != nil, controller.isOpen else { throw TorEngineError.notRunning }
        let defaults = cachedDefaults ?? PluggableTransportDefaults.load(from: bundle.ptConfig)
        cachedDefaults = defaults
        let baseline = (try? await controller.getInfo("status/bootstrap-phase", timeout: .seconds(5)))
            .flatMap { Self.parseBootstrapPhase($0)?.percent } ?? 0
        var pairs = try TorConfiguration.activationAssignments(settings: settings, ports: ports, defaults: defaults)
        var effective = ports
        do {
            try await controller.setConfLines(pairs, timeout: .seconds(10))
        } catch let error as TorControlClient.ControlError {
            guard case .reply(let code, _) = error, code == 553 || code == 551 else { throw error }
            // The SOCKS port was taken in the millisecond between allocation and SETCONF.
            let fresh = try PortAllocator.freeSocksPort(preferred: 0, excluding: [ports.http, ports.control, ports.pool])
            pairs = TorConfiguration.replacingSocksPort(pairs, socks: fresh, settings: settings, pool: ports.pool)
            try await controller.setConfLines(pairs, timeout: .seconds(10))
            effective = ActivePorts(socks: fresh, http: ports.http, control: ports.control, pool: ports.pool)
            emit(.veil(.warn, "SOCKS port \(ports.socks) was taken; tor is listening on \(fresh) instead"))
        }
        if let bound = try? await controller.getInfo("net/listeners/socks", timeout: .seconds(5)),
           !bound.isEmpty, !bound.contains(":\(effective.socks)") {
            throw TorEngineError.controlPortUnavailable("the SOCKS listener did not bind")
        }
        boundPorts = effective
        usesBridges = settings.transport != .direct
        disableNetwork = false
        pendingSignals = []
        return baseline
    }

    /// Closes every OR connection, circuit and listener in about two milliseconds while keeping the
    /// consensus, descriptors, guards and the pluggable transports loaded.
    func holdNetwork() async {
        guard let controller, controller.isOpen else { return }
        _ = try? await controller.setConfLines([("DisableNetwork", "1")], timeout: .seconds(5))
        disableNetwork = true
        bootstrap = BootstrapProgress()
    }

    /// New circuits, same process, same guards — instead of a full rebuild after a wake.
    func refreshNetwork() async throws {
        guard let controller, controller.isOpen else { throw TorEngineError.notRunning }
        try await controller.setConfLines([("DisableNetwork", "1")], timeout: .seconds(5))
        try await Task.sleep(for: .milliseconds(300))
        try await controller.setConfLines([("DisableNetwork", "0")], timeout: .seconds(5))
        disableNetwork = false
    }

    func abortBootstrap(_ failure: AttemptFailure) {
        pendingSignals.append(.externalAbort(failure))
    }

    func socksListeners() async -> [String] {
        guard let controller, controller.isOpen,
              let value = try? await controller.getInfo("net/listeners/socks", timeout: .seconds(5)) else { return [] }
        return TorControlEvents.parseListeners(value)
    }

    /// Supervises one attempt from the events Tor pushes, so a doomed attempt dies in seconds
    /// instead of burning a flat stall budget.
    func runBootstrap(_ config: BootstrapWatchdog.Config,
                      onStage: (@MainActor (BootstrapStage, Int) -> Void)?) async throws -> BootstrapOutcome {
        var watchdog = BootstrapWatchdog(config: config, startedAt: .now)
        pendingSignals = []
        var tick = 0
        var lastStage: BootstrapStage?
        while true {
            try Task.checkCancellation()
            if let exitStatus { pendingSignals.append(.processExited(exitStatus)) }
            if controller == nil || controller?.isOpen == false { pendingSignals.append(.controlLost) }
            let batch = pendingSignals
            pendingSignals = []
            for signal in batch {
                let verdict = watchdog.handle(signal, at: .now)
                switch verdict {
                case .succeeded:
                    return finish(watchdog)
                case .abort(let failure):
                    throw attemptError(watchdog, failure: failure)
                case .keepWaiting:
                    continue
                }
            }
            tick += 1
            // A 3 s safety net: the stdout log stays the primary source, this only fills gaps.
            if tick % 12 == 0, watchdog.percent < 100, let controller, controller.isOpen {
                if let phase = try? await controller.getInfo("status/bootstrap-phase", timeout: .seconds(5)),
                   let progress = Self.parseBootstrapPhase(phase) {
                    updateBootstrap(progress)
                    pendingSignals.append(.bootstrap(percent: progress.percent, tag: progress.tag, warning: nil,
                                                     reason: nil, count: nil, recommendation: nil, hostAddress: nil))
                }
            }
            let verdict = watchdog.handle(.tick, at: .now)
            switch verdict {
            case .succeeded:
                return finish(watchdog)
            case .abort(let failure):
                throw attemptError(watchdog, failure: failure)
            case .keepWaiting:
                break
            }
            if watchdog.stage != lastStage {
                lastStage = watchdog.stage
                onStage?(watchdog.stage, watchdog.percent)
            }
            try await Task.sleep(for: .milliseconds(250))
        }
    }

    private func finish(_ watchdog: BootstrapWatchdog) -> BootstrapOutcome {
        updateBootstrap(BootstrapProgress(percent: 100, tag: "done", summary: "Done"))
        return watchdog.outcome(warm: true, now: .now)
    }

    private func attemptError(_ watchdog: BootstrapWatchdog, failure: AttemptFailure) -> TorAttemptError {
        emit(.veil(.warn, "attempt failed: \(failure.rawValue) at \(watchdog.stage.rawValue) \(watchdog.percent)% after \(watchdog.elapsedMillis / 1000) s (\(watchdog.escapeSummary))"))
        return TorAttemptError(failure: failure, stage: watchdog.stage, percent: watchdog.percent,
                               lastWarning: watchdog.lastWarning ?? lastWarning, lastReason: watchdog.lastReason)
    }

    func waitForBootstrap(timeout: Duration, stallTimeout: Duration) async throws {
        let started = ContinuousClock.now
        var lastPoll = started
        var lastProgressAt = started
        var lastPercent = bootstrap.percent
        while !bootstrap.isDone {
            try Task.checkCancellation()
            if let exitStatus {
                throw TorEngineError.processExited(exitStatus, lastWarning: lastWarning)
            }
            if bootstrap.percent != lastPercent {
                lastPercent = bootstrap.percent
                lastProgressAt = ContinuousClock.now
            }
            if ContinuousClock.now - started > timeout {
                throw TorEngineError.bootstrapTimeout(lastWarning: lastWarning)
            }
            if ContinuousClock.now - lastProgressAt > stallTimeout {
                throw TorEngineError.bootstrapStalled(percent: bootstrap.percent, lastWarning: lastWarning)
            }
            // The log is the primary source; poll the control port as a safety net.
            if ContinuousClock.now - lastPoll > .seconds(3), let controller {
                lastPoll = ContinuousClock.now
                if let phase = try? await controller.getInfo("status/bootstrap-phase"),
                   let progress = Self.parseBootstrapPhase(phase) {
                    updateBootstrap(progress)
                }
            }
            try await Task.sleep(for: .milliseconds(250))
        }
    }

    func stop() async {
        await stop(grace: .seconds(3))
    }

    /// Both SIGINT (what `SIGNAL SHUTDOWN` means for a client) and SIGTERM run tor's cleanup, which
    /// saves `state` — the guard selection and the build-time histogram. Only the SIGKILL leg loses
    /// them, and this escalation makes it essentially unreachable: a clean exit takes ~100 ms.
    func stop(grace: Duration) async {
        let process = self.process
        let controller = self.controller
        self.controller = nil
        self.process = nil
        self.processID = nil
        stopping = true
        disableNetwork = true
        boundPorts = nil
        warmthProfile = nil
        standbyTorrc = nil
        pendingSignals = []

        let short = min(grace, .seconds(2))
        if let controller {
            _ = try? await controller.send("SIGNAL SHUTDOWN", timeout: short)
            if let process {
                let exited = await waitForExit(process, timeout: short)
                if exited {
                    controller.close()
                    TorProcessRegistry.unregister(pid: process.processIdentifier)
                    bootstrap = BootstrapProgress()
                    return
                }
            }
            // Under TAKEOWNERSHIP, closing the connection alone makes tor exit cleanly.
            controller.close()
        }
        if let process, process.isRunning {
            var exited = await waitForExit(process, timeout: min(grace, .seconds(1)))
            if !exited {
                process.terminate()
                exited = await waitForExit(process, timeout: grace)
            }
            if !exited {
                kill(process.processIdentifier, SIGKILL)
            }
            TorProcessRegistry.unregister(pid: process.processIdentifier)
        }
        bootstrap = BootstrapProgress()
    }

    /// Runs the bundled `tor` against the torrc already written, wiring stdout and the exit handler.
    private func spawn() throws {
        stopping = false
        let process = Process()
        process.executableURL = bundle.tor
        process.arguments = ["-f", torrcURL.path]
        process.currentDirectoryURL = supportDirectory
        process.standardInput = FileHandle.nullDevice
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        pipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty else {
                handle.readabilityHandler = nil
                return
            }
            let text = String(decoding: data, as: UTF8.self)
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    self?.consume(text)
                }
            }
        }
        let identifier = ObjectIdentifier(process)
        process.terminationHandler = { [weak self] finished in
            let status = finished.terminationStatus
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    self?.processDidExit(identifier, status: status)
                }
            }
        }
        try process.run()
        self.process = process
        self.processID = identifier
        TorProcessRegistry.register(pid: process.processIdentifier, dataDirectory: dataDirectory)
    }

    // MARK: Commands

    func newIdentity() async throws {
        guard let controller else { throw TorEngineError.notRunning }
        try await controller.signal("NEWNYM")
    }

    func isCircuitEstablished() async -> Bool {
        guard let controller, process != nil else { return false }
        return (try? await controller.getInfo("status/circuit-established")) == "1"
    }

    func applyRoute(_ route: TorRoute, dropConnections: Bool) async throws {
        guard let controller else { throw TorEngineError.notRunning }
        let configuration = route.configuration
        if !configuration.reset.isEmpty {
            try await controller.resetConf(configuration.reset)
        }
        if !configuration.set.isEmpty {
            try await controller.setConf(Dictionary(uniqueKeysWithValues: configuration.set.map { ($0.key, $0.value) }))
        }
        // Without NEWNYM Tor abandons only its unused circuits and stops giving new streams to
        // the old ones, so downloads in flight keep going while new connections take the new route.
        if dropConnections {
            try await controller.signal("NEWNYM")
        }
    }

    func launchCircuit() async throws -> String {
        guard let controller else { throw TorEngineError.notRunning }
        let lines = try await controller.send("EXTENDCIRCUIT 0")
        guard let id = Self.parseExtendedReply(lines.map(\.text)) else {
            throw TorEngineError.circuitLaunchFailed(lines.last?.text ?? "")
        }
        if circuits[id] == nil {
            circuits[id] = CircuitInfo(id: id, status: .launched, path: [], purpose: "GENERAL", created: .now)
        }
        return id
    }

    func awaitCircuit(_ id: String, timeout: Duration) async -> CircuitInfo? {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if let info = circuits[id], info.status.isFinal {
                return info
            }
            guard controller != nil else { return circuits[id] }
            do {
                try await Task.sleep(for: .milliseconds(200))
            } catch {
                return circuits[id]
            }
        }
        return circuits[id]
    }

    func closeCircuit(_ id: String) async {
        guard let controller else { return }
        _ = try? await controller.send("CLOSECIRCUIT \(id)")
    }

    func relayBandwidth(_ fingerprint: String) async -> Int? {
        guard let controller, let status = try? await controller.getInfo("ns/id/\(fingerprint)") else { return nil }
        return Self.parseBandwidth(status)
    }

    func relayCountry(_ fingerprint: String) async -> String? {
        guard let controller, let status = try? await controller.getInfo("ns/id/\(fingerprint)"),
              let address = Self.address(fromRouterStatus: status),
              let code = try? await controller.getInfo("ip-to-country/\(address)"), code.count == 2 else {
            return nil
        }
        return code.lowercased()
    }

    func circuit() async throws -> [CircuitHop] {
        guard let controller else { throw TorEngineError.notRunning }
        let status = try await controller.getInfo("circuit-status")
        guard let path = Self.bestCircuitPath(in: status), !path.isEmpty else { return [] }
        var hops: [CircuitHop] = []
        for (index, hop) in path.enumerated() {
            var address: String?
            var country: String?
            if let routerStatus = try? await controller.getInfo("ns/id/\(hop.fingerprint)") {
                address = Self.address(fromRouterStatus: routerStatus)
                if let address,
                   let code = try? await controller.getInfo("ip-to-country/\(address)"),
                   code.count == 2 {
                    country = code.lowercased()
                }
            }
            let role: CircuitHop.Role
            if index == 0 {
                role = usesBridges ? .bridge : .entry
            } else if index == path.count - 1 {
                role = .exit
            } else {
                role = .middle
            }
            hops.append(CircuitHop(fingerprint: hop.fingerprint, nickname: hop.nickname,
                                   address: address, countryCode: country, role: role))
        }
        return hops
    }

    func trafficCounters() async throws -> TrafficCounters {
        guard let controller else { throw TorEngineError.notRunning }
        let read = try await controller.getInfo("traffic/read")
        let written = try await controller.getInfo("traffic/written")
        return TrafficCounters(read: UInt64(read) ?? 0, written: UInt64(written) ?? 0)
    }

    func check(httpPort: UInt16) async throws -> TorCheckResult {
        try await TorCheck.run(httpPort: httpPort)
    }

    func waitForOnionServicePublication(serviceID: String, timeout: Duration) async -> Bool {
        guard let controller else { return false }
        descriptorUploads[serviceID] = 0
        _ = try? await controller.send(Self.eventSubscription)
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if (descriptorUploads[serviceID] ?? 0) >= 2 { return true }
            do {
                try await Task.sleep(for: .seconds(1))
            } catch {
                return false
            }
        }
        return (descriptorUploads[serviceID] ?? 0) > 0
    }

    private func handleControlEvent(_ event: String) {
        if event.hasPrefix("BW ") {
            if let counters = TorControlEvents.parseBW(event) {
                pendingSignals.append(.bytes(read: counters.read, written: counters.written))
                onBandwidth?(counters.read, counters.written)
            }
            return
        }
        if event.hasPrefix("STATUS_CLIENT ") {
            handleClientStatus(event)
            return
        }
        if event.hasPrefix("ORCONN ") {
            if let connection = TorControlEvents.parseORConn(event) {
                pendingSignals.append(.orConn(target: connection.target, status: connection.status,
                                              reason: connection.reason))
            }
            return
        }
        if event.hasPrefix("CIRC ") {
            if let update = Self.parseCircuitEvent(event) {
                record(update)
                if update.status == .extended || update.status == .built {
                    pendingSignals.append(.circuitEvent)
                }
            }
            return
        }
        let parts = event.split(separator: " ")
        guard parts.count >= 3, parts[0] == "HS_DESC" else { return }
        let action = parts[1]
        let service = String(parts[2])
        if action == "UPLOADED" {
            let count = (descriptorUploads[service] ?? 0) + 1
            descriptorUploads[service] = count
            if count == 1 {
                emit(.veil(.info, "Onion descriptor for \(service.prefix(12))… published to the Tor directory"))
            }
        } else if action == "FAILED" {
            emit(.veil(.debug, "Onion descriptor event: \(event)"))
        }
    }

    private func handleClientStatus(_ event: String) {
        guard let status = TorControlEvents.parseStatusClient(event) else { return }
        switch status {
        case .bootstrap(let percent, let tag, let summary, let warning, let reason, let count, let recommendation, let hostAddress):
            updateBootstrap(BootstrapProgress(percent: percent, tag: tag, summary: summary))
            if let warning { lastWarning = warning }
            pendingSignals.append(.bootstrap(percent: percent, tag: tag, warning: warning, reason: reason,
                                             count: count, recommendation: recommendation, hostAddress: hostAddress))
        case .transportLaunched(let name):
            launchedTransports.insert(name)
            pendingSignals.append(.transportLaunched(name))
        case .circuitEstablished:
            pendingSignals.append(.circuitEstablished)
        case .circuitNotEstablished(let reason):
            if let reason { lastWarning = reason }
        case .enoughDirInfo, .notEnoughDirInfo:
            break
        }
    }

    func createOnionService(targetPort: UInt16) async throws -> String {
        guard let controller else { throw TorEngineError.notRunning }
        let lines = try await controller.send("ADD_ONION NEW:ED25519-V3 Flags=DiscardPK Port=80,127.0.0.1:\(targetPort)")
        for line in lines where line.text.hasPrefix("ServiceID=") {
            return String(line.text.dropFirst("ServiceID=".count))
        }
        throw TorEngineError.onionServiceFailed
    }

    func removeOnionService(_ serviceID: String) async {
        guard let controller else { return }
        _ = try? await controller.send("DEL_ONION \(serviceID)")
    }

    func setTorPadding(enabled: Bool) async {
        guard let controller else { return }
        if enabled {
            try? await controller.setConf(Self.torPaddingOptions)
        } else {
            try? await controller.resetConf(Array(Self.torPaddingOptions.keys))
        }
    }

    /// Tor's built-in defences: circuit padding negotiated with the middle relay and link padding
    /// towards the bridge, with the "reduced" (battery-saving) variants switched off.
    static let torPaddingOptions: [String: String] = [
        "CircuitPadding": "1",
        "ReducedCircuitPadding": "0",
        "ConnectionPadding": "1",
        "ReducedConnectionPadding": "0",
    ]

    // MARK: Internals

    /// Tor opens the control listener inside `options_act`, long before it parses the consensus,
    /// so 15 s is generous; the bind and lock checks turn the two common failures into ~200 ms.
    private func connectControl(port: UInt16) async throws -> TorControlClient {
        let deadline = ContinuousClock.now + .seconds(15)
        var lastError: Error?
        while ContinuousClock.now < deadline {
            try Task.checkCancellation()
            if let exitStatus {
                throw TorEngineError.processExited(exitStatus, lastWarning: lastWarning)
            }
            if lockFailure {
                throw TorEngineError.dataDirectoryLocked(lastWarning ?? "")
            }
            if let bindFailure {
                throw TorEngineError.portInUse(bindFailure)
            }
            let client = TorControlClient(port: port)
            do {
                try await client.connect()
                let cookie = try await readCookie()
                try await client.authenticate(cookie: cookie)
                return client
            } catch {
                lastError = error
                client.close()
                try await Task.sleep(for: .milliseconds(300))
            }
        }
        throw TorEngineError.controlPortUnavailable(lastError?.localizedDescription)
    }

    private func readCookie() async throws -> Data {
        for _ in 0..<20 {
            if let data = try? Data(contentsOf: cookieURL), data.count == 32 {
                return data
            }
            try await Task.sleep(for: .milliseconds(150))
        }
        throw TorEngineError.cookieUnavailable
    }

    private func waitForExit(_ process: Process, timeout: Duration) async -> Bool {
        let deadline = ContinuousClock.now + timeout
        while process.isRunning {
            if ContinuousClock.now >= deadline { return false }
            do {
                try await Task.sleep(for: .milliseconds(25))
            } catch {
                return !process.isRunning
            }
        }
        return true
    }

    private func consume(_ text: String) {
        lineBuffer += text
        while let newline = lineBuffer.firstIndex(of: "\n") {
            let line = String(lineBuffer[..<newline]).trimmingCharacters(in: .whitespacesAndNewlines)
            lineBuffer.removeSubrange(...newline)
            if !line.isEmpty {
                handleLine(line)
            }
        }
    }

    private func handleLine(_ line: String) {
        let entry = LogEntry.parseTorLine(line)
        if entry.level >= .warn {
            lastWarning = entry.message
        }
        // A lost port race costs the full control-port deadline otherwise; this makes it ~200 ms.
        if let port = TorControlEvents.parseBindFailure(line) { bindFailure = port }
        if TorControlEvents.parseLockFailure(line) { lockFailure = true }
        if let progress = LogEntry.parseBootstrap(line) {
            updateBootstrap(progress)
            // The log is the version-independent source of truth: a tor that does not emit
            // STATUS_CLIENT still feeds the watchdog through here.
            pendingSignals.append(.bootstrap(percent: progress.percent, tag: progress.tag, warning: nil,
                                             reason: nil, count: nil, recommendation: nil, hostAddress: nil))
        }
        emit(entry)
    }

    private func updateBootstrap(_ progress: BootstrapProgress) {
        guard progress != bootstrap else { return }
        bootstrap = progress
        onBootstrap?(progress)
    }

    private func processDidExit(_ identifier: ObjectIdentifier, status: Int32) {
        guard identifier == processID else { return } // an old process we already replaced or stopped
        exitStatus = status
        process = nil
        processID = nil
        controller?.close()
        controller = nil
        disableNetwork = true
        boundPorts = nil
        warmthProfile = nil
        standbyTorrc = nil
        pendingSignals.append(.processExited(status))
        guard !stopping else { return }
        emit(.veil(status == 0 ? .notice : .error, "tor exited with status \(status)"))
        onExit?(status)
    }

    private func emit(_ entry: LogEntry) {
        onLog?(entry)
    }

    private static func describe(_ transport: AppSettings.Transport) -> String {
        switch transport {
        case .auto, .snowflake: "Snowflake"
        case .obfs4: "obfs4 bridges"
        case .meek: "meek bridge"
        case .custom: "custom bridges"
        case .direct: "no bridges"
        }
    }

    /// Parses `NOTICE BOOTSTRAP PROGRESS=100 TAG=done SUMMARY="Done"`.
    static func parseBootstrapPhase(_ text: String) -> BootstrapProgress? {
        var percent: Int?
        var tag = ""
        var summary = ""
        for token in text.split(separator: " ") {
            if token.hasPrefix("PROGRESS=") {
                percent = Int(token.dropFirst("PROGRESS=".count))
            } else if token.hasPrefix("TAG=") {
                tag = String(token.dropFirst("TAG=".count))
            }
        }
        if let range = text.range(of: "SUMMARY=\"") {
            let rest = text[range.upperBound...]
            if let end = rest.firstIndex(of: "\"") {
                summary = String(rest[..<end])
            }
        }
        guard let percent else { return nil }
        return BootstrapProgress(percent: min(100, max(0, percent)), tag: tag, summary: summary)
    }

    /// Merges a `CIRC` event into the table, keeping the first creation time and stamping BUILT.
    private func record(_ update: CircuitInfo) {
        var info = circuits[update.id] ?? update
        info.status = update.status
        if !update.path.isEmpty { info.path = update.path }
        if let purpose = update.purpose { info.purpose = purpose }
        if info.created == nil { info.created = update.created ?? .now }
        if update.status == .built, info.builtAt == nil { info.builtAt = .now }
        if let reason = update.reason { info.reason = reason }
        circuits[update.id] = info
        if circuits.count > 400 {
            let stale = circuits.values.filter { $0.status == .closed || $0.status == .failed }.sorted { ($0.created ?? .distantPast) < ($1.created ?? .distantPast) }
            for old in stale.prefix(200) { circuits[old.id] = nil }
        }
    }

    /// Parses `CIRC 12 BUILT $A~a,$B~b,$C~c BUILD_FLAGS=… PURPOSE=GENERAL TIME_CREATED=2026-09-08T10:00:00.123456`.
    static func parseCircuitEvent(_ text: String) -> CircuitInfo? {
        let parts = text.split(separator: " ")
        guard parts.count >= 3, parts[0] == "CIRC", let status = CircuitInfo.Status(rawValue: String(parts[2])) else { return nil }
        var info = CircuitInfo(id: String(parts[1]), status: status, path: [])
        for token in parts.dropFirst(3) {
            if token.hasPrefix("$") {
                info.path = RelayRef.parsePath(token)
            } else if token.hasPrefix("PURPOSE=") {
                info.purpose = String(token.dropFirst("PURPOSE=".count))
            } else if token.hasPrefix("TIME_CREATED=") {
                info.created = circuitTimeFormatter.date(from: String(token.dropFirst("TIME_CREATED=".count)))
            } else if token.hasPrefix("REASON=") {
                info.reason = String(token.dropFirst("REASON=".count))
            }
        }
        return info
    }

    /// Parses the `250 EXTENDED 12` reply of EXTENDCIRCUIT.
    static func parseExtendedReply(_ lines: [String]) -> String? {
        for line in lines where line.hasPrefix("EXTENDED ") {
            let id = line.dropFirst("EXTENDED ".count).trimmingCharacters(in: .whitespaces)
            if !id.isEmpty { return id }
        }
        return nil
    }

    /// Extracts the consensus weight from a router status entry (`w Bandwidth=12345`).
    static func parseBandwidth(_ text: String) -> Int? {
        for line in text.split(separator: "\n") where line.hasPrefix("w ") {
            for token in line.split(separator: " ") where token.hasPrefix("Bandwidth=") {
                return Int(token.dropFirst("Bandwidth=".count))
            }
        }
        return nil
    }

    /// Chooses the most recent built general-purpose circuit from `GETINFO circuit-status`.
    static func bestCircuitPath(in status: String) -> [(fingerprint: String, nickname: String)]? {
        var best: (id: Int, path: String, general: Bool)?
        for line in status.split(separator: "\n") {
            let parts = line.split(separator: " ")
            guard parts.count >= 3, parts[1] == "BUILT", let id = Int(parts[0]) else { continue }
            let general = parts.contains { $0 == "PURPOSE=GENERAL" }
            let candidate = (id: id, path: String(parts[2]), general: general)
            if let current = best {
                if (candidate.general && !current.general) || (candidate.general == current.general && candidate.id > current.id) {
                    best = candidate
                }
            } else {
                best = candidate
            }
        }
        guard let best else { return nil }
        return best.path.split(separator: ",").map { hop -> (fingerprint: String, nickname: String) in
            var fingerprint = String(hop)
            var nickname = ""
            if let separator = fingerprint.firstIndex(where: { $0 == "~" || $0 == "=" }) {
                nickname = String(fingerprint[fingerprint.index(after: separator)...])
                fingerprint = String(fingerprint[..<separator])
            }
            if fingerprint.hasPrefix("$") {
                fingerprint.removeFirst()
            }
            return (fingerprint: fingerprint, nickname: nickname)
        }
    }

    /// Extracts the IP address from a router status entry (`r nick id digest date time IP ORPort DirPort`).
    static func address(fromRouterStatus text: String) -> String? {
        for line in text.split(separator: "\n") where line.hasPrefix("r ") {
            let parts = line.split(separator: " ")
            if parts.count >= 7 {
                return String(parts[6])
            }
        }
        return nil
    }
}
