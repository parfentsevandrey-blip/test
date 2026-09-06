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
        var lines: [String] = [
            "# Generated by Veil. This file is rewritten on every connection attempt.",
            "DataDirectory \(dataDirectory.path)",
            "SocksPort 127.0.0.1:\(ports.socks)",
            "ControlPort 127.0.0.1:\(ports.control)",
            "CookieAuthentication 1",
            "ClientOnly 1",
            "AvoidDiskWrites 1",
            "DormantCanceledByStartup 1",
            "Log \(settings.verboseLogs ? "info" : "notice") stdout",
        ]
        if settings.paddingEnabled {
            for (key, value) in TorProcessEngine.torPaddingOptions.sorted(by: { $0.key < $1.key }) {
                lines.append("\(key) \(value)")
            }
        }
        if let geoip = bundle.geoip { lines.append("GeoIPFile \(geoip.path)") }
        if let geoip6 = bundle.geoip6 { lines.append("GeoIPv6File \(geoip6.path)") }
        lines.append(contentsOf: settings.route.torrcLines)

        let bridgeLines: [String]
        switch settings.transport {
        case .direct: bridgeLines = []
        case .snowflake: bridgeLines = defaults.bridges["snowflake"] ?? []
        case .obfs4: bridgeLines = defaults.bridges["obfs4"] ?? []
        case .custom: bridgeLines = Self.parseBridgeLines(settings.customBridges)
        }

        if settings.transport == .direct {
            lines.append("UseBridges 0")
        } else {
            guard !bridgeLines.isEmpty else { throw TorEngineError.noBridges }
            lines.append("UseBridges 1")
            let transports = Set(bridgeLines.compactMap { Self.transportName(of: $0) })
            try lines.append(contentsOf: defaults.pluginLines(for: transports, pluggableTransportDirectory: pluggableTransportDirectory, bundle: bundle))
            for bridge in bridgeLines { lines.append("Bridge \(bridge)") }
        }
        return lines.joined(separator: "\n") + "\n"
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
