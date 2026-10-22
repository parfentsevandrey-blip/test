import Foundation

/// Как трафик Mac попадает в ядро.
nonisolated enum ConnectionMode: String, Codable, Sendable, CaseIterable, Identifiable {
    /// Системный прокси: браузеры, мессенджеры и большинство программ.
    case proxy
    /// TUN: весь трафик всех программ, включая игры и терминал.
    case tun
    /// Только локальные порты SOCKS/HTTP — для программ, настроенных вручную.
    case local

    var id: String { rawValue }

    var title: String {
        switch self {
        case .proxy: "Системный прокси"
        case .tun: "Весь трафик (TUN)"
        case .local: "Только локальный порт"
        }
    }

    var subtitle: String {
        switch self {
        case .proxy: "Браузеры, мессенджеры и почти все программы. Быстро и экономно."
        case .tun: "Каждая программа на Mac, включая игры и терминал, — как настоящий VPN."
        case .local: "Ничего в системе не меняется: SOCKS5 и HTTP на этом Mac для тех, кто настроит их сам."
        }
    }

    var symbol: String {
        switch self {
        case .proxy: "network"
        case .tun: "shield.lefthalf.filled"
        case .local: "point.3.connected.trianglepath.dotted"
        }
    }
}

/// Фрагментация TLS ClientHello — против DPI, который ищет имя сайта в первом пакете.
nonisolated struct FragmentSettings: Codable, Hashable, Sendable {
    var enabled = false
    var packets = "tlshello"
    var length = "100-200"
    var interval = "10-20"
}

nonisolated struct CoreOptions: Sendable {
    var mode: ConnectionMode = .proxy
    var socksPort = 10808
    var httpPort = 10809
    var metricsPort = 10813
    /// Служебный HTTP-порт, который всегда ведёт через сервер: проверка связи и адреса выхода.
    var checkPort = 10812
    /// Открыть порты для других устройств в локальной сети.
    var lanShare = false
    var routing = RoutingSettings()
    var fragment = FragmentSettings()
    var remoteDNS = "https://1.1.1.1/dns-query"
    var domesticDNS = "https+local://77.88.8.8/dns-query"
    var tunName = "utun233"
    var logLevel = "warning"
}

nonisolated enum XrayConfig {
    enum Failure: Error, CustomStringConvertible {
        case unsupported(String)
        case badJSON

        var description: String {
            switch self {
            case .unsupported(let why): why
            case .badJSON: "конфигурация сервера — не JSON"
            }
        }
    }

    static let sniffing: [String: Any] = ["enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": true]

    /// Полная конфигурация для подключения.
    static func build(_ server: Server, _ o: CoreOptions, geo: GeoCategories) throws -> [String: Any] {
        if let issue = server.issue { throw Failure.unsupported(issue) }
        var proxy = try outbound(server.endpoint, global: o.fragment)
        proxy["tag"] = "proxy"

        let listen = o.lanShare ? "0.0.0.0" : "127.0.0.1"
        var inbounds: [[String: Any]] = [
            ["tag": "socks-in", "listen": listen, "port": o.socksPort, "protocol": "socks",
             "settings": ["udp": true] as [String: Any], "sniffing": sniffing],
            ["tag": "http-in", "listen": listen, "port": o.httpPort, "protocol": "http", "sniffing": sniffing],
            ["tag": "check-in", "listen": "127.0.0.1", "port": o.checkPort, "protocol": "http"],
        ]
        if o.mode == .tun {
            inbounds.append([
                "tag": "tun-in", "port": 0, "protocol": "tun", "sniffing": sniffing,
                "settings": ["name": o.tunName, "mtu": 1500, "autoSystemRoutingTable": ["0.0.0.0/0", "::/0"]] as [String: Any],
            ])
        }

        let plan = RoutingRules.rules(o.routing, available: geo)
        var rules: [[String: Any]] = []
        if o.mode == .tun {
            // Системные DNS-запросы, попавшие в туннель, отвечает DNS ядра.
            rules.append(["type": "field", "inboundTag": ["tun-in"], "port": "53", "outboundTag": "dns-out"])
        }
        // Удалённый DNS и проверки связи — всегда через сервер, при любых правилах.
        rules.append(["type": "field", "inboundTag": ["dns-internal", "check-in"], "outboundTag": "proxy"])
        for r in plan.rules {
            if !r.domains.isEmpty { rules.append(["type": "field", "domain": r.domains, "outboundTag": r.outbound]) }
            if !r.ips.isEmpty { rules.append(["type": "field", "ip": r.ips, "outboundTag": r.outbound]) }
        }
        rules.append(["type": "field", "network": "tcp,udp", "outboundTag": plan.final])

        var config: [String: Any] = [
            "log": ["loglevel": o.logLevel],
            "dns": dns(o, geo: geo),
            "inbounds": inbounds,
            "outbounds": [
                proxy,
                ["tag": "direct", "protocol": "freedom"],
                ["tag": "block", "protocol": "blackhole"],
                ["tag": "dns-out", "protocol": "dns"],
            ],
            "routing": ["domainStrategy": plan.domainStrategy, "rules": rules] as [String: Any],
            "stats": [String: Any](),
            "policy": ["system": ["statsOutboundUplink": true, "statsOutboundDownlink": true]],
            "metrics": ["tag": "metrics", "listen": "127.0.0.1:\(o.metricsPort)"],
        ]
        if case .profile = o.routing.mode, let hosts = o.routing.profile?.dnsHosts, !hosts.isEmpty {
            var dns = config["dns"] as? [String: Any] ?? [:]
            dns["hosts"] = hosts
            config["dns"] = dns
        }
        return config
    }

    static func dns(_ o: CoreOptions, geo: GeoCategories) -> [String: Any] {
        var remote = o.remoteDNS
        var domestic = o.domesticDNS
        if o.routing.mode == .profile, let p = o.routing.profile {
            remote = dnsAddress(type: p.remoteDNSType, domain: p.remoteDNSDomain, ip: p.remoteDNSIP, local: false)
            domestic = dnsAddress(type: p.domesticDNSType, domain: p.domesticDNSDomain, ip: p.domesticDNSIP, local: true)
        }
        var servers: [Any] = []
        if o.routing.mode != .all {
            let domains = ["geosite:category-ru", "domain:ru", "domain:su", "domain:xn--p1ai"].compactMap(geo.filterDomain)
            servers.append(["address": domestic, "domains": domains, "skipFallback": true] as [String: Any])
        }
        servers.append(remote)
        return ["tag": "dns-internal", "queryStrategy": "UseIPv4", "servers": servers]
    }

    /// Адрес DNS для ядра: DoH по IP (не нужно разрешать имя самого DNS), «+local» — мимо VPN.
    static func dnsAddress(type: String, domain: String, ip: String, local: Bool) -> String {
        if type.lowercased() == "doh", let url = URL(string: domain), let host = url.host {
            let path = url.path.isEmpty ? "/dns-query" : url.path
            let target = ip.isEmpty ? host : ip
            return (local ? "https+local://" : "https://") + target + path
        }
        return local ? "localhost" : (ip.isEmpty ? "1.1.1.1" : ip)
    }

    // MARK: - Outbound сервера

    static func outbound(_ e: Endpoint, global: FragmentSettings = FragmentSettings()) throws -> [String: Any] {
        if e.proto == .json {
            guard let raw = e.rawOutbound, let data = raw.data(using: .utf8),
                  let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure.badJSON }
            return obj
        }
        var out: [String: Any] = [:]
        switch e.proto {
        case .vless:
            var user: [String: Any] = ["id": e.id, "encryption": e.encryption.isEmpty ? "none" : e.encryption]
            if !e.flow.isEmpty { user["flow"] = e.flow }
            out = ["protocol": "vless", "settings": ["vnext": [["address": e.address, "port": e.port, "users": [user]] as [String: Any]]]]
        case .vmess:
            out = ["protocol": "vmess", "settings": ["vnext": [["address": e.address, "port": e.port,
                                                                "users": [["id": e.id, "security": e.method.isEmpty ? "auto" : e.method]]] as [String: Any]]]]
        case .trojan:
            out = ["protocol": "trojan", "settings": ["servers": [["address": e.address, "port": e.port, "password": e.password] as [String: Any]]]]
        case .shadowsocks:
            out = ["protocol": "shadowsocks", "settings": ["servers": [["address": e.address, "port": e.port, "method": e.method,
                                                                        "password": e.password] as [String: Any]]]]
        case .socks:
            var server: [String: Any] = ["address": e.address, "port": e.port]
            if !e.user.isEmpty || !e.password.isEmpty { server["users"] = [["user": e.user, "pass": e.password]] }
            out = ["protocol": "socks", "settings": ["servers": [server]]]
        case .hysteria2:
            out = ["protocol": "hysteria", "settings": ["version": 2, "address": e.address, "port": e.port] as [String: Any]]
        case .json:
            break
        }
        out["streamSettings"] = try stream(e, global: global)
        return out
    }

    static func stream(_ e: Endpoint, global: FragmentSettings) throws -> [String: Any] {
        var s: [String: Any] = ["network": e.network]
        switch e.network {
        case "raw":
            if e.headerType == "http" {
                var request: [String: Any] = ["path": [e.path.isEmpty ? "/" : e.path]]
                if !e.host.isEmpty { request["headers"] = ["Host": e.host.split(separator: ",").map(String.init)] }
                s["rawSettings"] = ["header": ["type": "http", "request": request] as [String: Any]]
            }
        case "ws":
            var ws: [String: Any] = ["path": e.path.isEmpty ? "/" : e.path]
            if !e.host.isEmpty { ws["host"] = e.host }
            s["wsSettings"] = ws
        case "httpupgrade":
            var hu: [String: Any] = ["path": e.path.isEmpty ? "/" : e.path]
            if !e.host.isEmpty { hu["host"] = e.host }
            s["httpupgradeSettings"] = hu
        case "grpc":
            var g: [String: Any] = ["serviceName": e.serviceName, "multiMode": e.mode == "multi"]
            if !e.authority.isEmpty { g["authority"] = e.authority }
            s["grpcSettings"] = g
        case "xhttp":
            var x: [String: Any] = ["path": e.path.isEmpty ? "/" : e.path, "mode": e.mode.isEmpty ? "auto" : e.mode]
            if !e.host.isEmpty { x["host"] = e.host }
            if !e.extra.isEmpty, let data = e.extra.data(using: .utf8), let extra = try? JSONSerialization.jsonObject(with: data) {
                x["extra"] = extra
            }
            s["xhttpSettings"] = x
        case "kcp":
            var k: [String: Any] = [:]
            if !e.headerType.isEmpty && e.headerType != "none" { k["header"] = ["type": e.headerType] }
            if !e.seed.isEmpty { k["seed"] = e.seed }
            s["kcpSettings"] = k
        case "hysteria":
            s["hysteriaSettings"] = ["version": 2, "auth": e.password] as [String: Any]
        default:
            throw Failure.unsupported("Транспорт «\(e.network)» ядро не поддерживает")
        }

        s["security"] = e.security
        let fp = e.fingerprint.isEmpty ? "chrome" : e.fingerprint
        switch e.security {
        case "tls":
            var tls: [String: Any] = [:]
            let sni = e.sni.isEmpty ? (e.host.isEmpty ? e.address : e.host) : e.sni
            tls["serverName"] = sni
            if !e.alpn.isEmpty { tls["alpn"] = e.alpn } else if e.proto == .hysteria2 { tls["alpn"] = ["h3"] }
            if e.proto != .hysteria2 { tls["fingerprint"] = fp }
            if !e.pinnedCert.isEmpty { tls["pinnedPeerCertSha256"] = e.pinnedCert }
            if !e.verifyName.isEmpty { tls["verifyPeerCertByName"] = e.verifyName }
            if !e.echConfig.isEmpty { tls["echConfigList"] = e.echConfig }
            s["tlsSettings"] = tls
        case "reality":
            var r: [String: Any] = ["serverName": e.sni.isEmpty ? e.host : e.sni, "fingerprint": fp, "publicKey": e.publicKey, "shortId": e.shortId]
            if !e.spiderX.isEmpty { r["spiderX"] = e.spiderX }
            if !e.mldsa65Verify.isEmpty { r["mldsa65Verify"] = e.mldsa65Verify }
            s["realitySettings"] = r
        case "none":
            break
        default:
            throw Failure.unsupported("Защита «\(e.security)» ядро не поддерживает")
        }

        // Маскировка: обфускация Hysteria, смена портов, фрагментация первого пакета TLS.
        var tcpMasks: [[String: Any]] = []
        var udpMasks: [[String: Any]] = []
        if e.proto == .hysteria2 {
            if e.obfs == "salamander" { udpMasks.append(["type": "salamander", "settings": ["password": e.obfsPassword]]) }
            if !e.hopPorts.isEmpty {
                var hop: [String: Any] = ["remotePorts": e.hopPorts]
                if !e.hopInterval.isEmpty { hop["interval"] = e.hopInterval }
                udpMasks.append(["type": "udphop", "settings": hop])
            }
        } else if e.security != "none" {
            if let f = fragmentMask(e.fragment) ?? (global.enabled ? ["packets": global.packets, "length": global.length, "delay": global.interval] : nil) {
                tcpMasks.append(["type": "fragment", "settings": f])
            }
        }
        if !tcpMasks.isEmpty || !udpMasks.isEmpty {
            var mask: [String: Any] = [:]
            if !tcpMasks.isEmpty { mask["tcp"] = tcpMasks }
            if !udpMasks.isEmpty { mask["udp"] = udpMasks }
            s["finalmask"] = mask
        }
        return s
    }

    /// Формат Happ: «длина,интервал,пакеты[,maxSplit]», например «1-10,5-20,tlshello».
    static func fragmentMask(_ text: String) -> [String: Any]? {
        let parts = text.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }
        guard parts.count >= 3 else { return nil }
        var m: [String: Any] = ["length": parts[0], "delay": parts[1], "packets": parts[2]]
        if parts.count > 3 { m["maxSplit"] = parts[3] }
        return m
    }

    // MARK: - Проверка задержки

    /// Конфигурация для замера: у каждого сервера свой локальный порт SOCKS.
    static func tester(_ servers: [(Server, Int)]) -> [String: Any] {
        var inbounds: [[String: Any]] = []
        var outbounds: [[String: Any]] = []
        var rules: [[String: Any]] = []
        for (i, (server, port)) in servers.enumerated() {
            guard server.issue == nil, var out = try? outbound(server.endpoint) else { continue }
            out["tag"] = "o\(i)"
            outbounds.append(out)
            inbounds.append(["tag": "i\(i)", "listen": "127.0.0.1", "port": port, "protocol": "http"])
            rules.append(["type": "field", "inboundTag": ["i\(i)"], "outboundTag": "o\(i)"])
        }
        outbounds.append(["tag": "block", "protocol": "blackhole"])
        return [
            "log": ["loglevel": "none"],
            "inbounds": inbounds,
            "outbounds": outbounds,
            "routing": ["rules": rules],
        ]
    }

    static func json(_ object: [String: Any]) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])) ?? Data()
        return String(decoding: data, as: UTF8.self)
    }
}
