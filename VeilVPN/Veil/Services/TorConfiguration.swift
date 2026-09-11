import Foundation

/// Bridge lines and `ClientTransportPlugin` templates. Loaded from the `pt_config.json`
/// shipped with the Tor Expert Bundle so that a Tor update also refreshes the defaults;
/// the built-in copy mirrors Tor Browser 15.0.21.
struct PluggableTransportDefaults: Sendable {
    /// Plugin key ("snowflake", "lyrebird", "conjure") → `ClientTransportPlugin` line with `${pt_path}`.
    var plugins: [String: String]
    /// Transport family ("snowflake", "obfs4", "meek") → bridge lines.
    var bridges: [String: [String]]

    static let builtin = PluggableTransportDefaults(
        plugins: [
            "lyrebird": "ClientTransportPlugin meek_lite,obfs2,obfs3,obfs4,scramblesuit,webtunnel exec ${pt_path}lyrebird",
            "snowflake": "ClientTransportPlugin snowflake exec ${pt_path}lyrebird",
            "conjure": "ClientTransportPlugin conjure exec ${pt_path}conjure-client -registerURL https://registration.refraction.network/api",
        ],
        bridges: [
            "snowflake": [
                "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn",
                "snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn",
            ],
            "obfs4": [
                "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0",
                "obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 cert=ssH+9rP8dG2NLDN2XuFw63hIO/9MNNinLmxQDpVa+7kTOa9/m+tGWT1SmSYpQ9uTBGa6Hw iat-mode=0",
                "obfs4 146.57.248.225:22 10A6CD36A537FCE513A322361547444B393989F0 cert=K1gDtDAIcUfeLqbstggjIw2rtgIKqdIhUlHp82XRqNSq/mtAjp1BIC9vHKJ2FAEpGssTPw iat-mode=0",
                "obfs4 45.145.95.6:27015 C5B7CD6946FF10C5B3E89691A7D3F2C122D2117C cert=TD7PbUO0/0k6xYHMPW3vJxICfkMZNdkRrb63Zhl5j9dW3iRGiCx0A7mPhe5T2EDzQ35+Zw iat-mode=0",
                "obfs4 51.222.13.177:80 5EDAC3B810E12B01F6FD8050D2FD3E277B289A08 cert=2uplIpLQ0q9+0qMFrK5pkaYRDOe460LL9WHBvatgkuRr/SL31wBOEupaMMJ6koRE6Ld0ew iat-mode=0",
                "obfs4 212.83.43.95:443 BFE712113A72899AD685764B211FACD30FF52C31 cert=ayq0XzCwhpdysn5o0EyDUbmSOx3X/oTEbzDMvczHOdBJKlvIdHHLJGkZARtT4dcBFArPPg iat-mode=1",
                "obfs4 212.83.43.74:443 39562501228A4D5E27FCA4C0C81A01EE23AE3EE4 cert=PBwr+S8JTVZo6MPdHnkTwXJPILWADLqfMGoVvhZClMq/Urndyd42BwX9YFJHZnBB3H0XCw iat-mode=1",
            ],
            "meek": [
                "meek_lite 192.0.2.20:80 url=https://1603026938.rsc.cdn77.org front=www.phpmyadmin.net utls=HelloRandomizedALPN",
            ],
        ]
    )

    static func load(from url: URL?) -> PluggableTransportDefaults {
        guard let url,
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let plugins = json["pluggableTransports"] as? [String: String],
              let bridges = json["bridges"] as? [String: [String]],
              !plugins.isEmpty else {
            return builtin
        }
        return PluggableTransportDefaults(plugins: plugins, bridges: bridges)
    }

    /// Which plugin serves a transport name found at the start of a bridge line.
    static func pluginKey(forTransport transport: String) -> String? {
        switch transport {
        case "snowflake": return "snowflake"
        case "conjure": return "conjure"
        case "obfs4", "obfs3", "obfs2", "scramblesuit", "meek_lite", "webtunnel": return "lyrebird"
        default: return nil
        }
    }

    /// `ClientTransportPlugin` lines needed for the given transports, with `${pt_path}` resolved.
    func pluginLines(for transports: Set<String>, pluggableTransportDirectory: URL, bundle: TorBundle) throws -> [String] {
        var keys: [String] = []
        for transport in transports.sorted() {
            guard let key = Self.pluginKey(forTransport: transport) else { continue }
            if !keys.contains(key) { keys.append(key) }
        }
        var lines: [String] = []
        let ptPath = pluggableTransportDirectory.path.hasSuffix("/") ? pluggableTransportDirectory.path : pluggableTransportDirectory.path + "/"
        for key in keys {
            let binaryAvailable = key == "conjure" ? bundle.conjure != nil : bundle.lyrebird != nil
            guard binaryAvailable, let template = plugins[key] else {
                throw TorEngineError.transportUnavailable(key)
            }
            lines.append(template.replacingOccurrences(of: "${pt_path}", with: ptPath))
        }
        return lines
    }
}

/// Renders the `torrc` for a connection attempt.
struct TorConfiguration {
    var settings: AppSettings
    var ports: ActivePorts
    var bundle: TorBundle
    var dataDirectory: URL
    var pluggableTransportDirectory: URL
    var defaults: PluggableTransportDefaults

    func render() throws -> String {
        // AvoidDiskWrites is deliberately absent: it pushes tor's next `state` write an hour out,
        // and `state` carries the guard selection and the circuit-build-time histogram — the two
        // things a fast next connect depends on.
        var lines: [String] = [
            "# Generated by Veil. This file is rewritten on every connection attempt.",
            "DataDirectory \(dataDirectory.path)",
            "ControlPort 127.0.0.1:\(ports.control)",
            "CookieAuthentication 1",
            "ClientOnly 1",
            "DormantCanceledByStartup 1",
            "DormantTimeoutEnabled 0",
            "DormantOnFirstStartup 0",
            "LearnCircuitBuildTimeout 1",
            "Log \(settings.verboseLogs ? "info" : "notice") stdout",
        ]
        lines.append(contentsOf: Self.socksPortLines(settings: settings, ports: ports))
        if settings.paddingEnabled {
            for (key, value) in TorProcessEngine.torPaddingOptions.sorted(by: { $0.key < $1.key }) {
                lines.append("\(key) \(value)")
            }
        }
        lines.append(contentsOf: Self.performanceLines(for: settings))
        if let geoip = bundle.geoip { lines.append("GeoIPFile \(geoip.path)") }
        if let geoip6 = bundle.geoip6 { lines.append("GeoIPv6File \(geoip6.path)") }
        lines.append(contentsOf: settings.route.torrcLines)

        let bridgeLines = Self.bridgeLines(for: settings.transport, settings: settings, defaults: defaults)

        if settings.transport == .direct {
            lines.append("UseBridges 0")
        } else {
            guard !bridgeLines.isEmpty else { throw TorEngineError.noBridges }
            lines.append("UseBridges 1")
            // Every lyrebird transport gets its plugin line, not just the one in use, so a live
            // transport switch never needs a torrc rewrite.
            var transports = Set(bridgeLines.compactMap { Self.transportName(of: $0) })
            if transports.contains(where: { PluggableTransportDefaults.pluginKey(forTransport: $0) == "lyrebird" })
                || transports.contains("snowflake") {
                transports.formUnion(["obfs4", "snowflake"])
            }
            try lines.append(contentsOf: defaults.pluginLines(for: transports, pluggableTransportDirectory: pluggableTransportDirectory, bundle: bundle))
            for bridge in bridgeLines { lines.append("Bridge \(bridge)") }
        }
        return lines.joined(separator: "\n") + "\n"
    }

    /// Latency-oriented Tor options: Conflux sends on the lowest-latency leg, and there is room
    /// for the lane pool's circuits next to the ones Tor pre-builds on its own.
    static func performanceLines(for settings: AppSettings) -> [String] {
        var lines = ["MaxClientCircuitsPending 48"]
        if settings.confluxLatency {
            lines.append("ConfluxEnabled 1")
            lines.append("ConfluxClientUX latency")
        }
        if settings.lanePoolEnabled {
            // Pinned to tor's own default on purpose: raising it would lengthen the window in
            // which one circuit links a user's activity. The pool's lane lifetime stays below it,
            // so measured, app-controlled replacement always fires first.
            lines.append("MaxCircuitDirtiness 600")
            // Keep the stock of clean pre-built circuits alive through idle spells, so the first
            // connection after a coffee break still lands on one instead of waiting for a build.
            lines.append("CircuitsAvailableTimeout 3600")
            // How often tor reconsiders building predicted circuits (default 30). Halving it
            // refills the clean pool faster after warm-ups draw from it — not a rotation interval.
            lines.append("NewCircuitPeriod 15")
        }
        return lines
    }

    /// The user-facing SOCKS listener, plus the lane pool's own listener when it is on.
    static func socksPortLines(settings: AppSettings, ports: ActivePorts) -> [String] {
        var lines = ["SocksPort 127.0.0.1:\(ports.socks)\(settings.isolatePerSite ? " IsolateDestAddr" : "")"]
        if settings.lanePoolEnabled, ports.pool != 0 {
            lines.append(poolSocksPortLine(port: ports.pool))
        }
        return lines
    }

    /// The bridge's own port. `IsolateDestAddr` must NEVER appear here: isolation keys compose, so
    /// it would fan every lane out into one circuit per destination and the lane↔circuit mapping
    /// this design rests on would evaporate silently.
    ///
    /// `IsolateSOCKSAuth` is tor's default, written out so a later edit cannot drop it.
    /// `KeepAliveIsolateSOCKSAuth` — the flag Tor Browser ships — withholds the dirtiness timer
    /// until a circuit has no attached streams, so a long download is never cut mid-transfer and a
    /// probed lane keeps one circuit for its whole life, which is what makes its numbers mean
    /// something. `ExtendedErrors` yields the 0xF0–0xF7 onion reply codes; it is on this port only,
    /// because third-party apps on the legacy port may not tolerate unknown codes.
    static func poolSocksPortLine(port: UInt16) -> String {
        "SocksPort 127.0.0.1:\(port) IsolateSOCKSAuth KeepAliveIsolateSOCKSAuth ExtendedErrors"
    }

    /// The `torrc` tor is warmed with: everything expensive to load, nothing on the wire.
    /// `SocksPort 0` and `DisableNetwork 1` mean no listener and no packets — observationally
    /// identical to tor not running — while the consensus, descriptors and guards load into RAM.
    static func renderStandby(controlPort: UInt16, dataDirectory: URL, bundle: TorBundle,
                              pluggableTransportDirectory: URL, defaults: PluggableTransportDefaults,
                              settings: AppSettings, predicted: AppSettings.Transport,
                              consensus: ConsensusInfo.Freshness, preBootstrap: Bool) -> String {
        var lines: [String] = [
            "# Generated by Veil (standby). Rewritten whenever the standby is (re)started.",
            "DataDirectory \(dataDirectory.path)",
            "ControlPort 127.0.0.1:\(controlPort)",
            "CookieAuthentication 1",
            "ClientOnly 1",
            "SocksPort 0",
            "DisableNetwork \(preBootstrap ? "0" : "1")",
            "DormantCanceledByStartup 1",
            "DormantTimeoutEnabled 0",
            "DormantOnFirstStartup 0",
            "LearnCircuitBuildTimeout 1",
            "MaxClientCircuitsPending 48",
            "Log \(settings.verboseLogs ? "info" : "notice") stdout",
        ]
        if let geoip = bundle.geoip { lines.append("GeoIPFile \(geoip.path)") }
        if let geoip6 = bundle.geoip6 { lines.append("GeoIPv6File \(geoip6.path)") }
        if consensus == .missing || consensus == .expired {
            // Only reached by a client with no usable consensus at all, and only in timing.
            lines.append("ClientBootstrapConsensusAuthorityDownloadInitialDelay 3")
            lines.append("ClientBootstrapConsensusMaxInProgressTries 4")
        }
        // Both lyrebird lines are always present so a live transport switch never needs a new
        // plugin line. Tor matches managed proxies by argv, so these produce one lyrebird process
        // serving both transport sets — the arrangement Tor Browser ships.
        let ptPath = pluggableTransportDirectory.path.hasSuffix("/")
            ? pluggableTransportDirectory.path
            : pluggableTransportDirectory.path + "/"
        if bundle.lyrebird != nil {
            for key in ["lyrebird", "snowflake"] {
                if let template = defaults.plugins[key] {
                    lines.append(template.replacingOccurrences(of: "${pt_path}", with: ptPath))
                }
            }
        }
        if bundle.conjure != nil, let template = defaults.plugins["conjure"],
           parseBridgeLines(settings.customBridges).contains(where: { $0.hasPrefix("conjure ") }) {
            lines.append(template.replacingOccurrences(of: "${pt_path}", with: ptPath))
        }
        let bridges = bridgeLines(for: predicted, settings: settings, defaults: defaults)
        if bridges.isEmpty {
            lines.append("UseBridges 0")
        } else {
            lines.append("UseBridges 1")
            for bridge in bridges.prefix(maxBridgeLines) { lines.append("Bridge \(bridge)") }
        }
        return lines.joined(separator: "\n") + "\n"
    }

    /// At most this many Bridge lines are pushed in one SETCONF (~2.5 KB); only custom bridges can
    /// exceed it, and a snowflake line alone is ~600 characters.
    static let maxBridgeLines = 8

    /// The bridge lines a transport actually configures.
    static func bridgeLines(for transport: AppSettings.Transport, settings: AppSettings,
                            defaults: PluggableTransportDefaults) -> [String] {
        let raw: [String]
        switch transport {
        case .direct: raw = []
        case .snowflake, .auto: raw = defaults.bridges["snowflake"] ?? []
        case .obfs4: raw = defaults.bridges["obfs4"] ?? []
        case .meek: raw = defaults.bridges["meek"] ?? []
        case .custom: raw = parseBridgeLines(settings.customBridges)
        }
        return raw.map { snowflakeLine($0, peers: settings.snowflakePeers) }
    }

    /// One ordered SETCONF that turns a standby tor into a connected one. DisableNetwork is ALWAYS
    /// last, so the SOCKS listener and the network come up inside the same `options_act`: if the
    /// bind fails, tor reverts the whole set and answers with an error rather than going live with
    /// no listener.
    static func activationAssignments(settings: AppSettings, ports: ActivePorts,
                                      defaults: PluggableTransportDefaults) throws -> [(key: String, value: String?)] {
        var pairs: [(key: String, value: String?)] = []
        for line in socksPortLines(settings: settings, ports: ports) {
            pairs.append(("SocksPort", String(line.dropFirst("SocksPort ".count))))
        }
        pairs.append(("Log", settings.verboseLogs ? "info stdout" : "notice stdout"))
        if settings.transport == .direct {
            pairs.append(("Bridge", nil))          // a bare key resets the list
            pairs.append(("UseBridges", "0"))
        } else {
            let bridges = bridgeLines(for: settings.transport, settings: settings, defaults: defaults)
            guard !bridges.isEmpty else { throw TorEngineError.noBridges }
            pairs.append(("UseBridges", "1"))
            for bridge in bridges.prefix(maxBridgeLines) { pairs.append(("Bridge", bridge)) }
        }
        let configuration = settings.route.configuration
        for key in configuration.reset { pairs.append((key, nil)) }
        for assignment in configuration.set { pairs.append((assignment.key, assignment.value)) }
        pairs.append(("ConfluxEnabled", settings.confluxLatency ? "1" : "0"))
        if settings.confluxLatency { pairs.append(("ConfluxClientUX", "latency")) }
        if settings.paddingEnabled {
            for (key, value) in TorProcessEngine.torPaddingOptions.sorted(by: { $0.key < $1.key }) {
                pairs.append((key, value))
            }
        }
        pairs.append(("DisableNetwork", "0"))
        return pairs
    }

    /// Swaps the SOCKS port in a rendered activation set, for the rare case where the port was
    /// taken in the millisecond between allocation and SETCONF.
    static func replacingSocksPort(_ pairs: [(key: String, value: String?)], socks: UInt16,
                                   settings: AppSettings, pool: UInt16) -> [(key: String, value: String?)] {
        var replaced: [(key: String, value: String?)] = []
        var wrote = false
        for pair in pairs {
            guard pair.key == "SocksPort" else {
                replaced.append(pair)
                continue
            }
            if !wrote {
                wrote = true
                let fresh = ActivePorts(socks: socks, http: 0, control: 0, pool: pool)
                for line in socksPortLines(settings: settings, ports: fresh) {
                    replaced.append(("SocksPort", String(line.dropFirst("SocksPort ".count))))
                }
            }
        }
        return replaced
    }

    /// Snowflake can hold several volunteer proxies at once (`max=`): a slow one no longer
    /// drags the whole session down, and the KCP layer picks the quickest path.
    static func snowflakeLine(_ line: String, peers: Int) -> String {
        guard peers > 1, line.hasPrefix("snowflake "), !line.contains(" max=") else { return line }
        return line + " max=\(min(4, peers))"
    }

    static func isValidCountryCode(_ code: String) -> Bool {
        code.count == 2 && code.allSatisfy { $0.isLetter && $0.isASCII }
    }

    /// Normalises user-pasted bridge lines: trims, drops comments/blank lines and a leading "Bridge ".
    static func parseBridgeLines(_ text: String) -> [String] {
        text.components(separatedBy: .newlines).compactMap { raw in
            var line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { return nil }
            if line.lowercased().hasPrefix("bridge ") {
                line = String(line.dropFirst(7)).trimmingCharacters(in: .whitespaces)
            }
            return line.isEmpty ? nil : line
        }
    }

    /// The transport name of a bridge line, or nil for a vanilla `ip:port fingerprint` bridge.
    static func transportName(of bridgeLine: String) -> String? {
        guard let first = bridgeLine.split(separator: " ").first else { return nil }
        let token = String(first)
        if token.contains(":") || token.contains(".") || token.hasPrefix("[") { return nil }
        return token
    }
}
