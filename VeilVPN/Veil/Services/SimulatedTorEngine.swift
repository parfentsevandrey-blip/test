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

    func applyRoute(_ route: TorRoute) async throws {
        exitCountry = route.exitCountry
        middleCountry = route.middleCountry
        excluded = route.excludedCountries
        circuitSeed += 1
        onLog?(LogEntry(level: .notice, source: .tor, message: "Demo: route updated (\(route.torrcLines.joined(separator: ", ")))"))
    }

    func circuit() async throws -> [CircuitHop] {
        guard running, bootstrap.isDone else { return [] }
        let middleCountries = ["de", "nl", "fr", "se", "ch", "at"].filter { !excluded.contains($0) }
        let exitCountries = ["us", "de", "nl", "fi", "gb", "fr"].filter { !excluded.contains($0) }
        let middle = middleCountry ?? middleCountries[circuitSeed % max(1, middleCountries.count)]
        let exit = exitCountry ?? exitCountries[(circuitSeed * 7 + 3) % max(1, exitCountries.count)]
        return [
            CircuitHop(fingerprint: "2B280B23E1107BB62ABFC40DDCC8824814F80A72", nickname: "flakey", address: nil, countryCode: nil, role: .bridge),
            CircuitHop(fingerprint: String(repeating: "A", count: 40), nickname: "Quetzalcoatl", address: "185.220.101.\(10 + circuitSeed % 200)", countryCode: middle, role: .middle),
            CircuitHop(fingerprint: String(repeating: "B", count: 40), nickname: "niftyexit", address: "199.249.230.\(80 + circuitSeed % 100)", countryCode: exit, role: .exit),
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
