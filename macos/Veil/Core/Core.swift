import Foundation
#if canImport(Veiltun)
import Veiltun
#endif

/// Everything that crosses into the Go core, in one file.
///
/// gomobile generates Objective-C names by prefixing the package: `veiltun`
/// becomes `VeiltunStartTunnel`, `VeiltunNewTransports` and so on. Those names
/// are not quite stable across gomobile versions, and an app that calls them
/// from twenty places has twenty things to fix when one changes. Here there is
/// one, and the rest of the app talks to `Core` in its own vocabulary.
///
/// The `canImport` guard is so the project still compiles before anyone has
/// run `scripts/build-core.sh`: without the framework the shim answers
/// honestly that the core is missing, instead of the whole target failing to
/// build with errors that point at the wrong thing.
enum Core {

    static var isAvailable: Bool {
        #if canImport(Veiltun)
        return true
        #else
        return false
        #endif
    }

    // MARK: - The transports

    /// Starts every pluggable transport and reports the loopback port each
    /// listens on, keyed by the name tor uses in a `ClientTransportPlugin`.
    ///
    /// All of them are started together on purpose: each is only a local SOCKS
    /// listener until a bridge line names it, so an unused one costs nothing,
    /// and having them all up front is what lets tor be configured once and
    /// switched between routes over the control port instead of restarted.
    static func startTransports(stateDirectory: String, log: @escaping (String) -> Void) throws -> [String: Int] {
        #if canImport(Veiltun)
        let events = TransportEventBridge(log: log)
        guard let transports = VeiltunNewTransports(stateDirectory, events) else {
            throw CoreFailure.unavailable("the transport controller would not start")
        }
        Self.transports = transports
        var ports: [String: Int] = [:]
        for name in ["obfs4", "webtunnel", "meek_lite", "conjure", "snowflake"] {
            do {
                try transports.start(name)
                let port = transports.port(name)
                if port > 0 {
                    ports[name] = Int(port)
                    log("\(name) listening on 127.0.0.1:\(port)")
                }
            } catch {
                log("\(name) would not start: \(error.localizedDescription)")
            }
        }
        guard !ports.isEmpty else { throw CoreFailure.unavailable("no transport could be started") }
        return ports
        #else
        throw CoreFailure.unavailable("the Go core is not built; run scripts/build-core.sh")
        #endif
    }

    static func stopTransports() {
        #if canImport(Veiltun)
        transports?.stopAll()
        transports = nil
        #endif
    }

    /// Rendezvous settings for Snowflake that only fill gaps: anything a
    /// bridge line carries wins.
    static func configureSnowflake(iceServers: [String]) {
        #if canImport(Veiltun)
        transports?.configureSnowflake(
            iceServers.joined(separator: ","),
            brokerURL: defaultBroker,
            frontDomains: defaultFronts,
            ampCacheURL: "",
            sqsURL: "",
            sqsCreds: "",
            maxPeers: 3
        )
        #endif
    }

    #if canImport(Veiltun)
    private nonisolated(unsafe) static var transports: VeiltunTransports?
    #endif

    private static let defaultBroker = "https://1098762253.rsc.cdn77.org/"
    private static let defaultFronts = "app.datapacket.com,www.datapacket.com"

    // MARK: - The datapath, which runs in the extension

    /// Tells the tunnel the path underneath is being rebuilt, or is back.
    ///
    /// While it is set, an application's connection whose upstream dial fails
    /// is held and retried rather than refused. That is the difference between
    /// a messenger saying "connecting" for the ten seconds a re-dial takes and
    /// saying it for two minutes: applications answer repeated errors by
    /// waiting longer between attempts, and nothing tells them the path came
    /// back.
    static func setRebuilding(_ on: Bool) {
        #if canImport(Veiltun)
        VeiltunSetRebuilding(on)
        #endif
    }

    static func stopTunnel() {
        #if canImport(Veiltun)
        VeiltunStop()
        #endif
    }

    static func snapshot() -> TunnelStats {
        #if canImport(Veiltun)
        guard let s = VeiltunSnapshot() else { return TunnelStats() }
        return TunnelStats(
            rxBytes: s.rxBytes, txBytes: s.txBytes, tcpOpen: s.tcpOpen,
            dnsQueries: s.dnsQueries, dnsBlocked: s.dnsBlocked, blockedUDP: s.blocked
        )
        #else
        return TunnelStats()
        #endif
    }

    static func resetStats() {
        #if canImport(Veiltun)
        VeiltunResetStats()
        #endif
    }

    // MARK: - The HTTP proxy, for the mode without a tunnel

    /// Starts the loopback HTTP proxy that stands in front of tor's SOCKS for
    /// the system's HTTP and HTTPS proxy settings, and returns its port.
    ///
    /// tor's own HTTPTunnelPort answers everything but CONNECT with 405, so a
    /// browser's plain-HTTP fetches would fail through it. This one forwards
    /// them, dials by name so nothing is resolved locally, and applies the ad
    /// blocker on the way.
    static func startHTTPProxy(socksNetwork: String, socksAddress: String, blocklist: String?) throws -> Int {
        #if canImport(Veiltun)
        var port: Int = 0
        var failure: NSError?
        VeiltunStartHTTPProxy(socksNetwork, socksAddress, blocklist ?? "", &port, &failure)
        if let failure { throw failure }
        return port
        #else
        throw CoreFailure.unavailable("the Go core is not built")
        #endif
    }

    static func stopHTTPProxy() {
        #if canImport(Veiltun)
        VeiltunStopHTTPProxy()
        #endif
    }

    /// Unpacks an xz asset. Used for the directory seed and the ad blocker's
    /// name list, both of which ship compressed inside the app.
    static func extractXz(from source: String, to destination: String) throws {
        #if canImport(Veiltun)
        // As above: an NSError out parameter, not a throwing function.
        var failure: NSError?
        VeiltunExtractXz(source, destination, &failure)
        if let failure { throw failure }
        #else
        throw CoreFailure.unavailable("the Go core is not built")
        #endif
    }

    enum CoreFailure: Error, LocalizedError {
        case unavailable(String)
        var errorDescription: String? {
            switch self { case .unavailable(let why): return why }
        }
    }
}

#if canImport(Veiltun)
/// Carries the transports' own events back as log lines.
private final class TransportEventBridge: NSObject, VeiltunTransportEventsProtocol {
    private let log: (String) -> Void
    init(log: @escaping (String) -> Void) { self.log = log }

    func connected(_ name: String?) { log("\(name ?? "?") connected") }
    func failed(_ name: String?, message: String?) { log("\(name ?? "?"): \(message ?? "")") }
    func stopped(_ name: String?, message: String?) {
        let text = message ?? ""
        log(text.isEmpty ? "\(name ?? "?") stopped" : "\(name ?? "?") stopped: \(text)")
    }

    /// Where a Snowflake attempt spends its time, step by step. These are the
    /// numbers that decide what to optimise; before they were logged, every
    /// judgement about a slow Snowflake was a guess.
    func phase(_ name: String?, phase: String?, detail: String?) {
        let n = name ?? "?", d = detail ?? ""
        switch phase {
        case "offer": log("\(n): offer ready, ICE gathering took \(d)ms")
        case "rendezvous": log("\(n): broker answered at +\(d)ms")
        case "connected": log("\(n): data channel open at +\(d)ms")
        default: log("\(n): \(phase ?? "") \(d)")
        }
    }
}
#endif
