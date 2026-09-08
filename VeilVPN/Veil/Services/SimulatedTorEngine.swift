import Foundation

/// Drives the UI without a real Tor process (no bundled binaries, `VEIL_SIMULATE=1`, previews).
@MainActor
final class SimulatedTorEngine: TorEngine {
    let isSimulated = true
    let versionDescription: String? = "simulated"

    var onLog: (@MainActor (LogEntry) -> Void)?
    var onBootstrap: (@MainActor (BootstrapProgress) -> Void)?
    var onExit: (@MainActor (Int32) -> Void)?

    private var bootstrap = BootstrapProgress()
    private var running = false
    private var task: Task<Void, Never>?
    private var counters = TrafficCounters(read: 0, written: 0)
    private var exitCountry: String?
    private var middleCountry: String?
    private var excluded: [String] = []
    private var circuitSeed = 0
    private var launched: [String: (info: CircuitInfo, ready: Date)] = [:]
    private var nextCircuitID = 100
    private var pinnedExits: [String] = []

    private static let demoExits: [(fingerprint: String, nickname: String, country: String)] = [
        (String(repeating: "B", count: 40), "niftyexit", "se"),
        (String(repeating: "C", count: 40), "swiftrelay", "se"),
        (String(repeating: "D", count: 40), "slowpoke", "se"),
        (String(repeating: "E", count: 40), "northwind", "se"),
    ]

    private static let phases: [(Int, String, String)] = [
        (0, "starting", "Starting"),
        (5, "conn_pt", "Connecting to pluggable transport"),
        (10, "conn_done_pt", "Connected to pluggable transport"),
        (14, "handshake", "Handshaking with a relay"),
        (15, "handshake_done", "Handshake with a relay done"),
        (20, "onehop_create", "Establishing an encrypted directory connection"),
        (25, "requesting_status", "Asking for networkstatus consensus"),
        (30, "loading_status", "Loading networkstatus consensus"),
        (40, "loading_keys", "Loading authority key certs"),
        (45, "requesting_descriptors", "Asking for relay descriptors"),
        (75, "enough_dirinfo", "Loaded enough directory info to build circuits"),
        (95, "circuit_create", "Establishing a Tor circuit"),
        (100, "done", "Done"),
    ]

    func start(settings: AppSettings, ports: ActivePorts) async throws {
        task?.cancel()
        running = true
        bootstrap = BootstrapProgress()
        counters = TrafficCounters(read: 0, written: 0)
        let route = settings.route
        exitCountry = route.exitCountry
        middleCountry = route.middleCountry
        excluded = route.excludedCountries
        onLog?(.veil(.notice, "Demo mode: simulating a Snowflake connection (no tor binary bundled)."))
        task = Task { [weak self] in
            for (percent, tag, summary) in Self.phases {
                try? await Task.sleep(for: .milliseconds(Int.random(in: 250...650)))
                guard let self, !Task.isCancelled, self.running else { return }
                let progress = BootstrapProgress(percent: percent, tag: tag, summary: summary)
                self.bootstrap = progress
                self.onLog?(LogEntry(level: .notice, source: .tor, message: "Bootstrapped \(percent)% (\(tag)): \(summary)"))
                self.onBootstrap?(progress)
            }
        }
    }

    func isCircuitEstablished() async -> Bool {
        running && bootstrap.isDone
    }

    func waitForBootstrap(timeout: Duration, stallTimeout: Duration) async throws {
        let started = ContinuousClock.now
        while !bootstrap.isDone {
            try Task.checkCancellation()
            if ContinuousClock.now - started > timeout {
                throw TorEngineError.bootstrapTimeout(lastWarning: nil)
            }
            try await Task.sleep(for: .milliseconds(200))
        }
    }

    func stop() async {
        task?.cancel()
        task = nil
        running = false
        bootstrap = BootstrapProgress()
    }

    func newIdentity() async throws {
        circuitSeed += 1
        onLog?(LogEntry(level: .notice, source: .tor, message: "Received reload signal (NEWNYM); switching to new circuits."))
    }

    func applyRoute(_ route: TorRoute, dropConnections: Bool) async throws {
        exitCountry = route.exitCountry
        middleCountry = route.middleCountry
        excluded = route.excludedCountries
        pinnedExits = route.pinnedExits
        if dropConnections { circuitSeed += 1 }
        onLog?(LogEntry(level: .notice, source: .tor, message: "Demo: route updated (\(route.torrcLines.joined(separator: ", ")))\(dropConnections ? ", circuits rebuilt" : ", existing connections kept")"))
    }

    func launchCircuit() async throws -> String {
        guard running else { throw TorEngineError.notRunning }
        nextCircuitID += 1
        let id = String(nextCircuitID)
        let exit = Self.demoExits[nextCircuitID % Self.demoExits.count]
        let build = Double(nextCircuitID % Self.demoExits.count + 1) * 0.35 + Double.random(in: 0...0.3)
        let created = Date.now
        let info = CircuitInfo(
            id: id, status: .launched,
            path: [
                RelayRef(fingerprint: "2B280B23E1107BB62ABFC40DDCC8824814F80A72", nickname: "flakey"),
                RelayRef(fingerprint: String(repeating: "A", count: 40), nickname: "Quetzalcoatl"),
                RelayRef(fingerprint: exit.fingerprint, nickname: exit.nickname),
            ],
            purpose: "GENERAL", created: created
        )
        launched[id] = (info, created.addingTimeInterval(build))
        return id
    }

    func awaitCircuit(_ id: String, timeout: Duration) async -> CircuitInfo? {
        guard let entry = launched[id] else { return nil }
        let wait = max(0, entry.ready.timeIntervalSinceNow)
        try? await Task.sleep(for: .milliseconds(Int(wait * 1000)))
        var info = entry.info
        info.status = .built
        info.builtAt = entry.ready
        launched[id] = (info, entry.ready)
        return info
    }

    func closeCircuit(_ id: String) async {
        launched[id] = nil
    }

    func relayBandwidth(_ fingerprint: String) async -> Int? {
        Int.random(in: 2_000...80_000)
    }

    func relayCountry(_ fingerprint: String) async -> String? {
        if let exit = Self.demoExits.first(where: { $0.fingerprint == fingerprint }) { return exitCountry ?? exit.country }
        if fingerprint.hasPrefix("A") { return middleCountry ?? "de" }
        return nil
    }

    func circuit() async throws -> [CircuitHop] {
        guard running, bootstrap.isDone else { return [] }
        let middleCountries = ["de", "nl", "fr", "se", "ch", "at"].filter { !excluded.contains($0) }
        let exitCountries = ["us", "de", "nl", "fi", "gb", "fr"].filter { !excluded.contains($0) }
        let middle = middleCountry ?? middleCountries[circuitSeed % max(1, middleCountries.count)]
        let exit = exitCountry ?? exitCountries[(circuitSeed * 7 + 3) % max(1, exitCountries.count)]
        let exitRelay = Self.demoExits.first { pinnedExits.contains($0.fingerprint) } ?? Self.demoExits[0]
        return [
            CircuitHop(fingerprint: "2B280B23E1107BB62ABFC40DDCC8824814F80A72", nickname: "flakey", address: nil, countryCode: nil, role: .bridge),
            CircuitHop(fingerprint: String(repeating: "A", count: 40), nickname: "Quetzalcoatl", address: "185.220.101.\(10 + circuitSeed % 200)", countryCode: middle, role: .middle),
            CircuitHop(fingerprint: exitRelay.fingerprint, nickname: exitRelay.nickname, address: "199.249.230.\(80 + circuitSeed % 100)", countryCode: exit, role: .exit),
        ]
    }

    func trafficCounters() async throws -> TrafficCounters {
        guard running, bootstrap.isDone else { return counters }
        counters.read += UInt64(Int.random(in: 8_000...420_000))
        counters.written += UInt64(Int.random(in: 2_000...90_000))
        return counters
    }

    func check(httpPort: UInt16) async throws -> TorCheckResult {
        try await Task.sleep(for: .milliseconds(900))
        return TorCheckResult(isTor: true, ip: "199.249.230.\(80 + circuitSeed % 100)")
    }

    func createOnionService(targetPort: UInt16) async throws -> String {
        try await Task.sleep(for: .milliseconds(400))
        return "demoonionservice"
    }

    func waitForOnionServicePublication(serviceID: String, timeout: Duration) async -> Bool {
        try? await Task.sleep(for: .seconds(1))
        return true
    }

    func removeOnionService(_ serviceID: String) async {}

    func setTorPadding(enabled: Bool) async {
        onLog?(.veil(.info, enabled ? "Demo: Tor circuit/connection padding forced on" : "Demo: Tor padding back to defaults"))
    }
}
