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
            try await client.send("TAKEOWNERSHIP")
            _ = try? await client.send("SETCONF __OwningControllerProcess=\(ProcessInfo.processInfo.processIdentifier)")
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

    func waitForBootstrap(timeout: Duration) async throws {
        let started = ContinuousClock.now
        var lastPoll = started
        while !bootstrap.isDone {
            try Task.checkCancellation()
            if let exitStatus {
                throw TorEngineError.processExited(exitStatus, lastWarning: lastWarning)
            }
            if ContinuousClock.now - started > timeout {
                throw TorEngineError.bootstrapTimeout(lastWarning: lastWarning)
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
            if await !waitForExit(process, timeout: .seconds(3)) {
                process.terminate()
            }
            if await !waitForExit(process, timeout: .seconds(3)) {
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

    func setExitCountry(_ code: String?) async throws {
        guard let controller else { throw TorEngineError.notRunning }
        if let code = code?.lowercased(), TorConfiguration.isValidCountryCode(code) {
            try await controller.setConf(["ExitNodes": "{\(code)}", "StrictNodes": "1"])
        } else {
            try await controller.resetConf(["ExitNodes", "StrictNodes"])
        }
        try await controller.signal("NEWNYM")
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

    func check(socksPort: UInt16) async throws -> TorCheckResult {
        try await TorCheck.run(socksPort: socksPort)
    }

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
        case .snowflake: "Snowflake"
        case .obfs4: "obfs4 bridges"
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
