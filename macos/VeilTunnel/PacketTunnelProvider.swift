import NetworkExtension
import os
#if canImport(Veiltun)
import Veiltun
#endif

/// The datapath, and nothing else.
///
/// Deliberately thin. tor, the transports and every decision about which route
/// to take live in the app; this extension gets the tunnel's descriptor, hands
/// it to the Go stack with the address of tor's socket, and moves packets. The
/// split means the connection logic cannot wedge the datapath, and a crash in
/// either does not take the other down.
///
/// The descriptor is the interesting part. `NEPacketTunnelFlow` offers
/// `readPackets`/`writePackets`, which is the supported API and the wrong one
/// for a whole machine's traffic: every packet would cross the
/// Swift/Objective-C/Go boundary individually. The flow is backed by a real
/// utun socket, and taking its descriptor — the technique the WireGuard client
/// uses — lets the Go stack read and write it directly, exactly as the Android
/// build reads the descriptor its VpnService hands over.
final class PacketTunnelProvider: NEPacketTunnelProvider {

    private let log = Logger(subsystem: "app.veil.mac.tunnel", category: "tunnel")

    override func startTunnel(options: [String: NSObject]?) async throws {
        guard let proto = protocolConfiguration as? NETunnelProviderProtocol,
              let configuration = proto.providerConfiguration,
              let socksSocket = configuration["socksSocket"] as? String else {
            throw TunnelFailure.noConfiguration
        }

        // The addresses are ours and private; the routes are everything, so
        // the machine's traffic arrives here rather than at the real
        // interface. IPv6 is claimed too: without a default route for it, an
        // IPv6-capable network would simply bypass the tunnel.
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = 1500

        let v4 = NEIPv4Settings(addresses: ["10.55.0.1"], subnetMasks: ["255.255.255.0"])
        v4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = v4

        let v6 = NEIPv6Settings(addresses: ["fd00:5645:494c::1"], networkPrefixLengths: [64])
        v6.includedRoutes = [NEIPv6Route.default()]
        settings.ipv6Settings = v6

        // Names are resolved through the tunnel. tor answers with a virtual
        // address it remembers, so the hostname survives to the exit; a
        // resolver on the local network would see every name instead.
        let dns = NEDNSSettings(servers: ["10.55.0.2"])
        dns.matchDomains = [""]
        settings.dnsSettings = dns

        try await setTunnelNetworkSettings(settings)

        guard let fd = tunnelDescriptor else {
            throw TunnelFailure.noDescriptor
        }

        #if canImport(Veiltun)
        let config = VeiltunNewTunnelConfig(Int(fd), 1500, socksSocket)
        config?.blockUDP = (configuration["blockUDP"] as? Bool) ?? true
        if let list = configuration["blocklist"] as? String, !list.isEmpty,
           (configuration["blockAds"] as? Bool) ?? false {
            config?.blockAds = true
            config?.blocklistPath = list
        }
        VeiltunSetLogger(LogBridge(log: log))
        // A gomobile free function reports failure through an NSError out
        // parameter rather than by throwing: Swift imports `BOOL f(x, NSError**)`
        // as a throwing call only for methods, not for C functions.
        var failure: NSError?
        VeiltunStartTunnel(config, &failure)
        if let failure { throw failure }
        log.info("tunnel up on fd \(fd), socks \(socksSocket, privacy: .public)")
        #else
        throw TunnelFailure.coreMissing
        #endif
    }

    override func stopTunnel(with reason: NEProviderStopReason) async {
        log.info("stopping: \(String(describing: reason), privacy: .public)")
        #if canImport(Veiltun)
        VeiltunStop()
        #endif
    }

    /// Handles the app asking the datapath something while it runs.
    ///
    /// One message so far: hold applications' connections while the path is
    /// rebuilt, rather than refusing them. Refusing is what teaches a
    /// messenger to wait a minute before trying again.
    override func handleAppMessage(_ messageData: Data) async -> Data? {
        guard let text = String(data: messageData, encoding: .utf8) else { return nil }
        #if canImport(Veiltun)
        switch text {
        case "rebuilding:on": VeiltunSetRebuilding(true)
        case "rebuilding:off": VeiltunSetRebuilding(false)
        case "stats":
            if let s = VeiltunSnapshot() {
                return try? JSONSerialization.data(withJSONObject: [
                    "rx": s.rxBytes, "tx": s.txBytes, "tcp": s.tcpOpen,
                    "dnsBlocked": s.dnsBlocked,
                ])
            }
        default: break
        }
        #endif
        return nil
    }

    /// The utun descriptor behind the packet flow.
    ///
    /// There is no public accessor for it. The flow is a wrapper over a utun
    /// socket and exposes it through this key path, which is what every VPN
    /// that needs line-rate throughput on Apple platforms uses. If a future
    /// macOS removes it, the fallback is `readPackets`/`writePackets` at a
    /// real cost in throughput — so this fails loudly rather than silently.
    private var tunnelDescriptor: Int32? {
        if let value = packetFlow.value(forKeyPath: "socket.fileDescriptor") as? Int32,
           value > 0 {
            return value
        }
        log.error("the packet flow did not expose a descriptor")
        return nil
    }

    enum TunnelFailure: Error, LocalizedError {
        case noConfiguration, noDescriptor, coreMissing
        var errorDescription: String? {
            switch self {
            case .noConfiguration: return "the tunnel was started without a configuration"
            case .noDescriptor: return "the packet flow did not expose a utun descriptor"
            case .coreMissing: return "the Go core is not linked into the extension"
            }
        }
    }
}

#if canImport(Veiltun)
/// Sends the Go stack's log lines to the unified log.
private final class LogBridge: NSObject, VeiltunLoggerProtocol {
    private let log: Logger
    init(log: Logger) { self.log = log }
    func log(_ level: String?, msg: String?) {
        let text = msg ?? ""
        switch level {
        case "error": log.error("\(text, privacy: .public)")
        case "warn": log.warning("\(text, privacy: .public)")
        default: log.debug("\(text, privacy: .public)")
        }
    }
}
#endif
