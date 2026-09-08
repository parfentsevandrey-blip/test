import Foundation

/// Runs the bundled `tor` binary as a child process and talks to it over the control port.
@MainActor
final class TorProcessEngine: TorEngine {
    let isSimulated = false
    private(set) var versionDescription: String?

    var onLog: (@MainActor (LogEntry) -> Void)?
    var onBootstrap: (@MainActor (BootstrapProgress) -> Void)?
    var onExit: (@MainActor (Int32) -> Void)?

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

    private static let eventSubscription = "SETEVENTS CIRC HS_DESC"

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
            if let version = try? await client.getInfo("version") {
                versionDescription = version
                emit(.veil(.info, "Tor \(version) is running"))
            }
        } catch {
            await stop()
            throw error
        }
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
        let process = self.process
        let controller = self.controller
        self.controller = nil
        self.process = nil
        self.processID = nil

        if let controller {
            _ = try? await controller.send("SIGNAL SHUTDOWN")
            controller.close()
        }
        if let process, process.isRunning {
            var exited = await waitForExit(process, timeout: .seconds(3))
            if !exited {
                process.terminate()
                exited = await waitForExit(process, timeout: .seconds(3))
            }
            if !exited {
                kill(process.processIdentifier, SIGKILL)
            }
        }
        bootstrap = BootstrapProgress()
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
        if event.hasPrefix("CIRC ") {
            if let update = Self.parseCircuitEvent(event) {
                record(update)
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

    private func connectControl(port: UInt16) async throws -> TorControlClient {
        let deadline = ContinuousClock.now + .seconds(25)
        var lastError: Error?
        while ContinuousClock.now < deadline {
            try Task.checkCancellation()
            if let exitStatus {
                throw TorEngineError.processExited(exitStatus, lastWarning: lastWarning)
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
                try await Task.sleep(for: .milliseconds(100))
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
        if let progress = LogEntry.parseBootstrap(line) {
            updateBootstrap(progress)
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
