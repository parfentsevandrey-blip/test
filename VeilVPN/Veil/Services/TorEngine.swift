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
    /// Applies country restrictions for the hops live (SETCONF) and rebuilds circuits.
    func applyRoute(_ route: TorRoute) async throws
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
        }
    }
}
