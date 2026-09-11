import Foundation

/// Abstraction over "something that runs Tor for us". The real implementation spawns the
/// bundled `tor` binary; the simulated one drives the UI when no binary is available
/// (e.g. `swift run` straight from the package, or SwiftUI previews).
@MainActor
protocol TorEngine: AnyObject {
    var isSimulated: Bool { get }
    var versionDescription: String? { get }

    var onLog: (@MainActor (LogEntry) -> Void)? { get set }
    var onBootstrap: (@MainActor (BootstrapProgress) -> Void)? { get set }
    var onExit: (@MainActor (Int32) -> Void)? { get set }

    /// Launches Tor and authenticates on the control port. Returns before bootstrap completes.
    func start(settings: AppSettings, ports: ActivePorts) async throws
    /// Suspends until Tor reports `Bootstrapped 100%`, the process dies, the timeout elapses, or
    /// bootstrap makes no progress for `stallTimeout`.
    func waitForBootstrap(timeout: Duration, stallTimeout: Duration) async throws
    /// True when Tor currently has a working circuit (`status/circuit-established`).
    func isCircuitEstablished() async -> Bool
    func stop() async
    func newIdentity() async throws
    /// Applies the route live (SETCONF). With `dropConnections` Tor also gets NEWNYM, which
    /// tears down every circuit; without it existing streams stay on their circuits and only
    /// new connections use the new route.
    func applyRoute(_ route: TorRoute, dropConnections: Bool) async throws
    /// Asks Tor to build one more circuit for the current route; returns its circuit id.
    func launchCircuit() async throws -> String
    /// Waits until the circuit is BUILT, FAILED or CLOSED. Nil when unknown or on timeout.
    func awaitCircuit(_ id: String, timeout: Duration) async -> CircuitInfo?
    func closeCircuit(_ id: String) async
    /// Consensus bandwidth weight of a relay (kB/s), when the directory knows it.
    func relayBandwidth(_ fingerprint: String) async -> Int?
    /// Lowercase country code of a relay from Tor's GeoIP database.
    func relayCountry(_ fingerprint: String) async -> String?
    func circuit() async throws -> [CircuitHop]
    func trafficCounters() async throws -> TrafficCounters
    /// Verifies the exit through Veil's local HTTP proxy (host names are passed to Tor unresolved).
    func check(httpPort: UInt16) async throws -> TorCheckResult

    /// Creates an ephemeral v3 onion service that forwards port 80 to `targetPort` on this Mac.
    /// Returns the service id (the part before `.onion`).
    func createOnionService(targetPort: UInt16) async throws -> String
    /// Waits until Tor reports the service descriptor uploaded to the directory (HS_DESC events).
    func waitForOnionServicePublication(serviceID: String, timeout: Duration) async -> Bool
    func removeOnionService(_ serviceID: String) async
    /// Forces Tor's own circuit/connection padding on (or back to defaults) without a restart.
    func setTorPadding(enabled: Bool) async

    // MARK: Warm lifecycle

    /// What Tor already has on disk, and whether the process is loaded.
    var warmth: WarmthProfile? { get }
    /// Process up, network held off (`DisableNetwork 1`).
    var isWarm: Bool { get }
    /// Process up, network live.
    var isLive: Bool { get }
    /// The ports Tor is actually listening on, once activated.
    var activePorts: ActivePorts? { get }
    var bootstrapPercent: Int { get }
    /// Spawns tor with `SocksPort 0` and `DisableNetwork 1` and lets it load the consensus,
    /// descriptors and guards. Nothing reaches the network.
    func warmUp(settings: AppSettings, controlPort: UInt16, mode: AppSettings.WarmStart,
                budget: Duration) async throws -> WarmthProfile
    /// One ordered SETCONF that opens the listeners and lifts `DisableNetwork`. Returns the
    /// bootstrap percentage Tor was already at, since its counter is monotone across attempts.
    func activate(settings: AppSettings, ports: ActivePorts) async throws -> Int
    /// Closes every OR connection, circuit and listener in about two milliseconds, keeping the
    /// consensus, descriptors, guards and the pluggable transports in memory.
    func holdNetwork() async
    /// `DisableNetwork` off and on again: new circuits, same process, same guards.
    func refreshNetwork() async throws
    /// Supervises one bootstrap attempt from the events Tor pushes.
    func runBootstrap(_ config: BootstrapWatchdog.Config,
                      onStage: (@MainActor (BootstrapStage, Int) -> Void)?) async throws -> BootstrapOutcome
    /// Ends the current attempt from outside (the connectivity sentinel, and nothing else).
    func abortBootstrap(_ failure: AttemptFailure)
    func stop(grace: Duration) async
    /// The SOCKS listeners Tor actually opened, as "127.0.0.1:9050" strings. Empty when unknown.
    func socksListeners() async -> [String]
}

/// Defaults for every warm-lifecycle member, so an engine that does not implement them still
/// behaves exactly as it did before. No requirement here is a stored-property-shaped closure: a
/// protocol extension cannot store one, which is why bootstrap progress travels as a parameter.
extension TorEngine {
    /// The historical behaviour: apply and rebuild everything.
    func applyRoute(_ route: TorRoute) async throws {
        try await applyRoute(route, dropConnections: true)
    }

    var warmth: WarmthProfile? { nil }
    var isWarm: Bool { false }
    var isLive: Bool { false }
    var activePorts: ActivePorts? { nil }
    var bootstrapPercent: Int { 0 }

    func warmUp(settings: AppSettings, controlPort: UInt16, mode: AppSettings.WarmStart,
                budget: Duration) async throws -> WarmthProfile {
        WarmthProfile()
    }

    func activate(settings: AppSettings, ports: ActivePorts) async throws -> Int {
        try await start(settings: settings, ports: ports)
        return 0
    }

    func holdNetwork() async { await stop() }
    func refreshNetwork() async throws {}

    func runBootstrap(_ config: BootstrapWatchdog.Config,
                      onStage: (@MainActor (BootstrapStage, Int) -> Void)?) async throws -> BootstrapOutcome {
        let started = ContinuousClock.now
        try await waitForBootstrap(timeout: config.hardTimeout, stallTimeout: config.budget(for: .directory))
        return BootstrapOutcome(totalMillis: BootstrapWatchdog.millis(started.duration(to: .now)),
                                firstHopMillis: nil, stageMillis: [:], peakPercent: 100, warm: false)
    }

    func abortBootstrap(_ failure: AttemptFailure) {}
    func stop(grace: Duration) async { await stop() }
    func socksListeners() async -> [String] { [] }
}

struct TrafficCounters: Equatable, Sendable {
    var read: UInt64
    var written: UInt64
}

enum TorEngineError: LocalizedError {
    case noBridges
    case transportUnavailable(String)
    case controlPortUnavailable(String?)
    case cookieUnavailable
    case processExited(Int32, lastWarning: String?)
    case bootstrapTimeout(lastWarning: String?)
    case bootstrapStalled(percent: Int, lastWarning: String?)
    case notRunning
    case onionServiceFailed
    case circuitLaunchFailed(String)
    case portInUse(UInt16)
    case dataDirectoryLocked(String)

    var errorDescription: String? {
        switch self {
        case .noBridges:
            return String(localized: "No bridge lines configured. Paste bridges in Settings → Bridges or choose another transport.")
        case .transportUnavailable(let name):
            return String(localized: "The pluggable transport ‘\(name)’ is not bundled with this build.")
        case .controlPortUnavailable(let detail):
            let base = String(localized: "Tor did not open its control port in time.")
            return detail.map { "\(base) \($0)" } ?? base
        case .cookieUnavailable:
            return String(localized: "Could not read Tor’s control authentication cookie.")
        case .processExited(let code, let warning):
            let base = String(localized: "Tor exited unexpectedly (code \(String(code))).")
            return warning.map { "\(base) \($0)" } ?? base
        case .bootstrapTimeout(let warning):
            let base = String(localized: "Tor could not bootstrap in time. Snowflake proxies may be scarce right now — try again or switch bridges.")
            return warning.map { "\(base) Last warning: \($0)" } ?? base
        case .bootstrapStalled(let percent, let warning):
            let base = String(localized: "Tor stopped making progress at \(percent)%.")
            return warning.map { "\(base) \($0)" } ?? base
        case .notRunning:
            return String(localized: "Tor is not running.")
        case .onionServiceFailed:
            return String(localized: "Tor did not create the private onion service needed for traffic padding.")
        case .circuitLaunchFailed(let detail):
            return String(localized: "Tor could not start a circuit: \(detail)")
        case .portInUse(let port):
            return String(localized: "Local port \(String(port)) is already in use.")
        case .dataDirectoryLocked(let detail):
            return String(localized: "Another Tor is using Veil’s data directory. \(detail)")
        }
    }
}
