import Foundation

/// Ссылки на серверы: vless://, vmess://, trojan://, ss://, socks://, hy2:// — в форматах, которые выдают
/// панели (3x-ui, Marzban, Remnawave) и понимают Happ, v2rayN, Streisand, Hiddify.
nonisolated enum Links {
    enum Failure: Error, CustomStringConvertible, Equatable {
        case unknownScheme(String)
        case malformed(String)

        var description: String {
            switch self {
            case .unknownScheme(let s): "неизвестный тип ссылки «\(s)»"
            case .malformed(let why): "ссылка повреждена: \(why)"
            }
        }
    }

    static let schemes = ["vless", "vmess", "trojan", "ss", "socks", "socks5", "hy2", "hysteria2"]

    /// Все серверы из текста: по одной ссылке на строку (или через пробел).
    static func parseAll(_ text: String) -> (servers: [Server], failures: [String]) {
        var servers: [Server] = []
        var failures: [String] = []
        for token in text.split(whereSeparator: { $0 == "\n" || $0 == "\r" || $0 == " " || $0 == "\t" }) {
            let line = String(token).trimmingCharacters(in: .whitespaces)
            guard let colon = line.range(of: "://") else { continue }
            let scheme = line[..<colon.lowerBound].lowercased()
            guard schemes.contains(scheme) else { continue }
            switch parse(line) {
            case .success(let server): servers.append(server)
            case .failure(let error): failures.append(error.description)
            }
        }
        return (servers, failures)
    }

    static func parse(_ link: String) -> Result<Server, Failure> {
        let link = link.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let range = link.range(of: "://") else { return .failure(.malformed("нет «://»")) }
        let scheme = link[..<range.lowerBound].lowercased()
        let body = String(link[range.upperBound...])
        do {
            var server: Server
            switch scheme {
            case "vless": server = try uriStyle(.vless, body)
            case "trojan": server = try uriStyle(.trojan, body)
            case "vmess": server = try vmess(body)
            case "ss": server = try shadowsocks(body)
            case "socks", "socks5": server = try socks(body)
            case "hy2", "hysteria2": server = try hysteria2(body)
            default: return .failure(.unknownScheme(scheme))
            }
            server.link = link
            server.id = Server.stableID(link)
            check(&server)
            return .success(server)
        } catch let failure as Failure {
            return .failure(failure)
        } catch {
            return .failure(.malformed(error.localizedDescription))
        }
    }

    // MARK: - Разбор URI

    /// userinfo@host:port/path?query#fragment — вручную: в ссылках бывают эмодзи, пробелы
    /// и неэкранированные символы, на которых `URLComponents` спотыкается.
    struct Parts {
        var user = ""
        var host = ""
        var port = ""
        var path = ""
        var query: [String: String] = [:]
        var fragment = ""
    }

    static func split(_ body: String) throws -> Parts {
        var parts = Parts()
        var rest = Substring(body)
        if let hash = rest.firstIndex(of: "#") {
            parts.fragment = decode(String(rest[rest.index(after: hash)...]))
            rest = rest[..<hash]
        }
        if let q = rest.firstIndex(of: "?") {
            parts.query = query(String(rest[rest.index(after: q)...]))
            rest = rest[..<q]
        }
        if let slash = rest.firstIndex(of: "/") {
            parts.path = String(rest[slash...])
            rest = rest[..<slash]
        }
        if let at = rest.lastIndex(of: "@") {
            parts.user = decode(String(rest[..<at]))
            rest = rest[rest.index(after: at)...]
        }
        if rest.hasPrefix("[") {
            guard let close = rest.firstIndex(of: "]") else { throw Failure.malformed("адрес IPv6 без «]»") }
            parts.host = String(rest[rest.index(after: rest.startIndex)..<close])
            let after = rest[rest.index(after: close)...]
            if after.hasPrefix(":") { parts.port = String(after.dropFirst()) }
        } else if let colon = rest.lastIndex(of: ":") {
            parts.host = String(rest[..<colon])
            parts.port = String(rest[rest.index(after: colon)...])
        } else {
            parts.host = String(rest)
        }
        return parts
    }

    static func query(_ text: String) -> [String: String] {
        var result: [String: String] = [:]
        for pair in text.split(separator: "&") {
            let kv = pair.split(separator: "=", maxSplits: 1)
            guard let key = kv.first, !key.isEmpty else { continue }
            result[decode(String(key))] = kv.count > 1 ? decode(String(kv[1])) : ""
        }
        return result
    }

    static func decode(_ text: String) -> String { text.removingPercentEncoding ?? text }

    /// Base64 в любом виде: обычный и URL-safe, с выравниванием и без, с переносами строк.
    static func base64(_ text: String) -> Data? {
        var s = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: "").replacingOccurrences(of: "\r", with: "")
            .replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        guard !s.isEmpty, s.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "+" || $0 == "/" || $0 == "=") }) else { return nil }
        s = s.trimmingCharacters(in: CharacterSet(charactersIn: "="))
        while s.count % 4 != 0 { s += "=" }
        return Data(base64Encoded: s)
    }

    static func base64String(_ text: String) -> String? {
        base64(text).flatMap { String(data: $0, encoding: .utf8) }
    }

    static func port(_ text: String) throws -> Int {
        guard let p = Int(text.trimmingCharacters(in: .whitespaces)), (1...65535).contains(p) else {
            throw Failure.malformed("порт «\(text)»")
        }
        return p
    }

    /// Название и параметры Happ после «#»: «Название?serverDescription=…&fragment=…».
    static func titleAndMeta(_ fragment: String, into server: inout Server) {
        let known = ["serverDescription", "fragment", "noises", "resolve-address", "host", "insecure"]
        if let q = fragment.firstIndex(of: "?") {
            let params = query(String(fragment[fragment.index(after: q)...]))
            if params.keys.contains(where: known.contains) {
                server.name = String(fragment[..<q])
                if let d = params["serverDescription"] { server.note = base64String(d) ?? d }
                if let f = params["fragment"] { server.endpoint.fragment = f }
                return
            }
        }
        server.name = fragment
    }

    // MARK: - VLESS и Trojan

    static func uriStyle(_ proto: Proto, _ body: String) throws -> Server {
        let p = try split(body)
        var e = Endpoint(proto: proto)
        guard !p.host.isEmpty else { throw Failure.malformed("нет адреса сервера") }
        e.address = p.host
        e.port = try port(p.port)
        if proto == .vless {
            guard !p.user.isEmpty else { throw Failure.malformed("нет UUID") }
            e.id = p.user
            e.encryption = p.query["encryption"].flatMap { $0.isEmpty ? nil : $0 } ?? "none"
            e.flow = p.query["flow"] ?? ""
        } else {
            guard !p.user.isEmpty else { throw Failure.malformed("нет пароля") }
            e.password = p.user
        }
        applyTransport(p.query, to: &e, defaultSecurity: proto == .trojan ? "tls" : "none")
        var server = Server(id: "", name: "", endpoint: e, link: "")
        titleAndMeta(p.fragment, into: &server)
        if server.name.isEmpty { server.name = "\(e.address):\(e.port)" }
        return server
    }

    /// Общие параметры транспорта и защиты (одинаковы у VLESS, Trojan и VMess в URI-форме).
    static func applyTransport(_ q: [String: String], to e: inout Endpoint, defaultSecurity: String) {
        var network = (q["type"] ?? q["net"] ?? "tcp").lowercased()
        switch network {
        case "tcp", "raw", "": network = "raw"
        case "splithttp": network = "xhttp"
        case "mkcp": network = "kcp"
        case "websocket": network = "ws"
        default: break
        }
        e.network = network
        e.headerType = q["headerType"] ?? ""
        e.host = q["host"] ?? ""
        e.path = q["path"] ?? ""
        e.serviceName = q["serviceName"] ?? ""
        e.authority = q["authority"] ?? ""
        e.mode = q["mode"] ?? ""
        e.extra = q["extra"] ?? ""
        e.seed = q["seed"] ?? ""
        if network == "grpc" && e.serviceName.isEmpty && !e.path.isEmpty { e.serviceName = e.path }

        var security = (q["security"] ?? defaultSecurity).lowercased()
        if security.isEmpty || security == "xtls" { security = security.isEmpty ? defaultSecurity : "tls" }
        e.security = security
        e.sni = q["sni"] ?? q["peer"] ?? ""
        e.alpn = (q["alpn"] ?? "").split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        e.fingerprint = q["fp"] ?? ""
        e.publicKey = q["pbk"] ?? ""
        e.shortId = q["sid"] ?? ""
        e.spiderX = q["spx"] ?? ""
        e.mldsa65Verify = q["pqv"] ?? ""
        e.pinnedCert = q["pcs"] ?? ""
        e.verifyName = q["vcn"] ?? ""
        e.echConfig = q["ech"] ?? ""
        e.insecure = ["1", "true"].contains((q["allowInsecure"] ?? q["insecure"] ?? "").lowercased())
        if let f = q["fragment"] { e.fragment = f }
    }

    // MARK: - VMess

    /// vmess://base64(JSON v2rayN) или vmess://uuid@host:port?… (новый формат).
    static func vmess(_ body: String) throws -> Server {
        let main = body.split(separator: "#", maxSplits: 1).first.map(String.init) ?? body
        if let json = base64(main), let obj = try? JSONSerialization.jsonObject(with: json) as? [String: Any] {
            func str(_ k: String) -> String {
                if let s = obj[k] as? String { return s }
                if let n = obj[k] as? NSNumber { return n.stringValue }
                return ""
            }
            var e = Endpoint(proto: .vmess)
            e.address = str("add")
            e.port = try port(str("port"))
            e.id = str("id")
            guard !e.address.isEmpty, !e.id.isEmpty else { throw Failure.malformed("нет адреса или UUID") }
            e.method = str("scy").isEmpty ? "auto" : str("scy")
            var q: [String: String] = [
                "type": str("net"), "headerType": str("type"), "host": str("host"), "path": str("path"),
                "security": str("tls"), "sni": str("sni"), "alpn": str("alpn"), "fp": str("fp"),
            ]
            if str("net") == "grpc" { q["serviceName"] = str("path"); q["mode"] = str("type") == "multi" ? "multi" : "" ; q["headerType"] = "" }
            if str("net") == "kcp" { q["seed"] = str("path"); q["path"] = "" }
            applyTransport(q, to: &e, defaultSecurity: "none")
            if e.security == "tls" && e.sni.isEmpty && !e.host.isEmpty && e.network != "raw" { e.sni = e.host }
            if let aid = Int(str("aid")), aid > 0 { throw Failure.malformed("VMess с alterId > 0 больше не поддерживается ядром") }
            let name = str("ps")
            return Server(id: "", name: name.isEmpty ? "\(e.address):\(e.port)" : name, endpoint: e, link: "")
        }
        // Новый формат: как у VLESS.
        let p = try split(body)
        var e = Endpoint(proto: .vmess)
        e.address = p.host
        e.port = try port(p.port)
        e.id = p.user
        guard !e.address.isEmpty, !e.id.isEmpty else { throw Failure.malformed("нет адреса или UUID") }
        e.method = p.query["encryption"] ?? "auto"
        applyTransport(p.query, to: &e, defaultSecurity: "none")
        var server = Server(id: "", name: "", endpoint: e, link: "")
        titleAndMeta(p.fragment, into: &server)
        if server.name.isEmpty { server.name = "\(e.address):\(e.port)" }
        return server
    }

    // MARK: - Shadowsocks

    static let ssMethods: Set<String> = [
        "aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305", "xchacha20-poly1305",
        "xchacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none", "plain",
    ]

    /// SIP002: ss://base64(метод:пароль)@host:port#имя (у 2022 — без base64), и старый
    /// ss://base64(метод:пароль@host:port)#имя.
    static func shadowsocks(_ body: String) throws -> Server {
        var p = try split(body)
        var userinfo = p.user
        if p.user.isEmpty {
            // Старый формат: всё до «#» — в base64.
            let main = body.split(separator: "#", maxSplits: 1).first.map(String.init) ?? body
            let encoded = main.split(separator: "?", maxSplits: 1).first.map(String.init) ?? main
            guard let decoded = base64String(encoded.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) else {
                throw Failure.malformed("непонятный формат ss://")
            }
            let fragment = p.fragment
            p = try split(decoded)
            p.fragment = fragment
            userinfo = p.user
        } else if !userinfo.contains(":"), let decoded = base64String(userinfo) {
            userinfo = decoded
        }
        guard let colon = userinfo.firstIndex(of: ":") else { throw Failure.malformed("нет метода шифрования") }
        var e = Endpoint(proto: .shadowsocks)
        e.method = String(userinfo[..<colon]).lowercased()
        e.password = String(userinfo[userinfo.index(after: colon)...])
        e.address = p.host
        e.port = try port(p.port)
        guard !e.address.isEmpty else { throw Failure.malformed("нет адреса сервера") }
        var server = Server(id: "", name: "", endpoint: e, link: "")
        titleAndMeta(p.fragment, into: &server)
        if server.name.isEmpty { server.name = "\(e.address):\(e.port)" }
        if let plugin = p.query["plugin"], !plugin.isEmpty {
            server.issue = "Плагин Shadowsocks «\(plugin.split(separator: ";").first ?? "")» ядро Xray не поддерживает"
        }
        return server
    }

    // MARK: - SOCKS

    static func socks(_ body: String) throws -> Server {
        var p = try split(body)
        if p.host.isEmpty || p.port.isEmpty || Int(p.port) == nil {
            // Целиком в base64.
            let main = body.split(separator: "#", maxSplits: 1).first.map(String.init) ?? body
            guard let decoded = base64String(main) else { throw Failure.malformed("непонятный формат socks://") }
            let fragment = p.fragment
            p = try split(decoded)
            p.fragment = fragment
        }
        var e = Endpoint(proto: .socks)
        e.address = p.host
        e.port = try port(p.port)
        var userinfo = p.user
        if !userinfo.isEmpty, !userinfo.contains(":"), let decoded = base64String(userinfo) { userinfo = decoded }
        if let colon = userinfo.firstIndex(of: ":") {
            e.user = String(userinfo[..<colon])
            e.password = String(userinfo[userinfo.index(after: colon)...])
        } else {
            e.user = userinfo
        }
        var server = Server(id: "", name: "", endpoint: e, link: "")
        titleAndMeta(p.fragment, into: &server)
        if server.name.isEmpty { server.name = "\(e.address):\(e.port)" }
        return server
    }

    // MARK: - Hysteria 2

    static func hysteria2(_ body: String) throws -> Server {
        let p = try split(body)
        var e = Endpoint(proto: .hysteria2)
        e.address = p.host
        e.password = p.user
        guard !e.address.isEmpty else { throw Failure.malformed("нет адреса сервера") }
        // Порт может быть списком для смены портов: «443,5000-6000».
        let ports = p.port.split(separator: ",").map(String.init)
        let first = ports.first.map { $0.split(separator: "-").first.map(String.init) ?? $0 } ?? "443"
        e.port = try port(first.isEmpty ? "443" : first)
        if ports.count > 1 || p.port.contains("-") { e.hopPorts = p.port }
        if let mport = p.query["mport"], !mport.isEmpty { e.hopPorts = mport }
        e.hopInterval = p.query["mportHopInt"] ?? p.query["hop-interval"] ?? ""
        e.network = "hysteria"
        e.security = "tls"
        e.sni = p.query["sni"] ?? p.query["peer"] ?? ""
        e.alpn = (p.query["alpn"] ?? "").split(separator: ",").map(String.init)
        e.obfs = p.query["obfs"] ?? ""
        e.obfsPassword = p.query["obfs-password"] ?? ""
        e.pinnedCert = (p.query["pinSHA256"] ?? p.query["pcs"] ?? "").replacingOccurrences(of: ":", with: "")
        e.insecure = ["1", "true"].contains((p.query["insecure"] ?? "").lowercased())
        e.fingerprint = p.query["fp"] ?? ""
        var server = Server(id: "", name: "", endpoint: e, link: "")
        titleAndMeta(p.fragment, into: &server)
        if server.name.isEmpty { server.name = "\(e.address):\(e.port)" }
        return server
    }

    // MARK: - Проверка совместимости

    /// Заранее понятные проблемы — чтобы не узнавать о них по ошибке подключения.
    static func check(_ server: inout Server) {
        let e = server.endpoint
        if ["h2", "http", "h3", "quic"].contains(e.network) {
            server.issue = "Транспорт «\(e.network)» удалён из ядра Xray. Попросите у провайдера ссылку с XHTTP."
        }
        if e.proto == .shadowsocks && !ssMethods.contains(e.method) {
            server.issue = "Шифр Shadowsocks «\(e.method)» ядро не поддерживает"
        }
        if e.security == "reality" && e.publicKey.isEmpty {
            server.issue = "В ссылке Reality нет открытого ключа (pbk)"
        }
        if e.insecure && e.pinnedCert.isEmpty && e.security == "tls" {
            server.warning = "Сервер просит не проверять сертификат. Ядро Xray так больше не умеет: если сертификат самоподписанный, подключение не пройдёт."
        }
    }

    // MARK: - Ссылка из сервера

    /// Ссылка для «Поделиться»: исходная, если она есть, иначе собранная заново.
    static func link(for server: Server) -> String {
        if !server.link.isEmpty { return server.link }
        let e = server.endpoint
        let name = server.name.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? ""
        let host = e.address.contains(":") ? "[\(e.address)]" : e.address
        func q(_ items: [(String, String)]) -> String {
            items.filter { !$0.1.isEmpty }.map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? $0.1)" }.joined(separator: "&")
        }
        let common: [(String, String)] = [
            ("type", e.network == "raw" ? "tcp" : e.network), ("security", e.security), ("sni", e.sni), ("fp", e.fingerprint),
            ("alpn", e.alpn.joined(separator: ",")), ("pbk", e.publicKey), ("sid", e.shortId), ("spx", e.spiderX),
            ("host", e.host), ("path", e.path), ("serviceName", e.serviceName), ("mode", e.mode), ("headerType", e.headerType),
            ("pcs", e.pinnedCert),
        ]
        switch e.proto {
        case .vless:
            return "vless://\(e.id)@\(host):\(e.port)?\(q([("encryption", e.encryption), ("flow", e.flow)] + common))#\(name)"
        case .trojan:
            return "trojan://\(e.password.addingPercentEncoding(withAllowedCharacters: .urlUserAllowed) ?? "")@\(host):\(e.port)?\(q(common))#\(name)"
        case .vmess:
            return "vmess://\(e.id)@\(host):\(e.port)?\(q([("encryption", e.method)] + common))#\(name)"
        case .shadowsocks:
            let user = Data("\(e.method):\(e.password)".utf8).base64EncodedString()
            return "ss://\(user)@\(host):\(e.port)#\(name)"
        case .socks:
            let user = Data("\(e.user):\(e.password)".utf8).base64EncodedString()
            return "socks://\(user)@\(host):\(e.port)#\(name)"
        case .hysteria2:
            return "hy2://\(e.password.addingPercentEncoding(withAllowedCharacters: .urlUserAllowed) ?? "")@\(host):\(e.port)/?\(q([("sni", e.sni), ("obfs", e.obfs), ("obfs-password", e.obfsPassword), ("pinSHA256", e.pinnedCert), ("mport", e.hopPorts)]))#\(name)"
        case .json:
            return ""
        }
    }
}

nonisolated extension CharacterSet {
    static let urlQueryValueAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&=+#")
        return set
    }()
}
