import Foundation

/// Загрузка и разбор подписок.
///
/// Тело: base64 со списком ссылок, ссылки построчно или JSON-конфигурации Xray (так отдают Remnawave
/// и Marzban). Сведения о тарифе — в заголовках (`subscription-userinfo`, `profile-title`, `announce`…)
/// или строками `#имя: значение` в начале тела, как у Happ.
nonisolated enum SubscriptionLoader {
    struct Loaded: Sendable {
        var servers: [Server]
        var info: SubscriptionInfo
        var failures: [String]
    }

    enum Failure: Error, CustomStringConvertible {
        case http(Int)
        case empty
        case clash
        case encrypted

        var description: String {
            switch self {
            case .http(let code): code == 403 || code == 401 ? "Провайдер отказал в доступе (\(code)). Проверьте ссылку или лимит устройств." : "Сервер подписки ответил ошибкой \(code)"
            case .empty: "В подписке не нашлось ни одного сервера"
            case .clash: "Подписка отдала формат Clash. Попросите у провайдера ссылку для v2rayN/Happ или смените User-Agent в настройках."
            case .encrypted: "Это зашифрованная ссылка Happ (happ://crypt…). Её может открыть только Happ — попросите у провайдера обычную ссылку подписки."
            }
        }
    }

    /// Ссылка подписки из текста: https://…, happ://add/https://…, veil://add/…
    static func url(from text: String) -> String? {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        for prefix in ["happ://add/", "veil://add/", "v2rayn://install-config?url=", "streisand://import/", "hiddify://import/"] where t.lowercased().hasPrefix(prefix) {
            t = Links.decode(String(t.dropFirst(prefix.count)))
        }
        guard let u = URL(string: t), let scheme = u.scheme?.lowercased(), scheme == "https" || scheme == "http", u.host != nil else { return nil }
        return t
    }

    static func isEncryptedHapp(_ text: String) -> Bool {
        text.lowercased().hasPrefix("happ://crypt")
    }

    static func fetch(_ url: String, userAgent: String, hwid: String?) async throws -> Loaded {
        guard let u = URL(string: url) else { throw Failure.empty }
        var request = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        if let hwid {
            // Как у Happ: провайдеры ограничивают число устройств по этим заголовкам.
            request.setValue(hwid, forHTTPHeaderField: "x-hwid")
            request.setValue("macOS", forHTTPHeaderField: "x-device-os")
            request.setValue(ProcessInfo.processInfo.operatingSystemVersionString, forHTTPHeaderField: "x-ver-os")
            request.setValue("Mac", forHTTPHeaderField: "x-device-model")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let code = http?.statusCode, !(200..<300).contains(code) { throw Failure.http(code) }
        var headers: [String: String] = [:]
        for (k, v) in http?.allHeaderFields ?? [:] {
            if let k = k as? String, let v = v as? String { headers[k.lowercased()] = v }
        }
        var loaded = try parse(body: data, headers: headers)
        if loaded.info.title.isEmpty, let disposition = headers["content-disposition"],
           let range = disposition.range(of: "filename=") {
            loaded.info.title = disposition[range.upperBound...].trimmingCharacters(in: CharacterSet(charactersIn: "\"' "))
        }
        return loaded
    }

    static func parse(body: Data, headers: [String: String]) throws -> Loaded {
        var info = SubscriptionInfo()
        apply(headers, to: &info)
        var text = String(decoding: body, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)

        // JSON-конфигурации Xray.
        if text.hasPrefix("[") || text.hasPrefix("{"), let servers = jsonConfigs(body), !servers.isEmpty {
            return Loaded(servers: servers, info: info, failures: [])
        }
        if text.hasPrefix("proxies:") || text.contains("\nproxies:") { throw Failure.clash }
        // base64 целиком.
        if !text.contains("://"), let decoded = Links.base64String(text) { text = decoded }

        // Строки «#имя: значение» — параметры подписки в теле (Happ).
        var meta: [String: String] = [:]
        for line in text.split(whereSeparator: \.isNewline) where line.hasPrefix("#") {
            let body = line.dropFirst().trimmingCharacters(in: .whitespaces)
            guard let colon = body.firstIndex(of: ":") else { continue }
            let key = body[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            meta[key] = body[body.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        apply(meta, to: &info, overwrite: false)
        // Профиль маршрутизации может прийти и строкой happ://routing/… в теле.
        for line in text.split(whereSeparator: \.isNewline) where line.lowercased().hasPrefix("happ://routing/") {
            info.routing = String(line)
        }

        let parsed = Links.parseAll(text)
        guard !parsed.servers.isEmpty else { throw Failure.empty }
        return Loaded(servers: parsed.servers, info: info, failures: parsed.failures)
    }

    static func apply(_ h: [String: String], to info: inout SubscriptionInfo, overwrite: Bool = true) {
        func set(_ key: String, _ apply: (String) -> Void) {
            if let v = h[key], !v.isEmpty { apply(v) }
        }
        set("profile-title") { v in if overwrite || info.title.isEmpty { info.title = decodeValue(v) } }
        set("subscription-userinfo") { v in
            for part in v.split(separator: ";") {
                let kv = part.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
                guard kv.count == 2, let n = Int64(kv[1]) ?? Double(kv[1]).map({ Int64($0) }) else { continue }
                switch kv[0].lowercased() {
                case "upload": info.upload = n
                case "download": info.download = n
                case "total": info.total = n
                case "expire": info.expire = n > 0 ? Date(timeIntervalSince1970: TimeInterval(n)) : nil
                default: break
                }
            }
        }
        set("profile-update-interval") { v in info.updateHours = Int(v) ?? info.updateHours }
        set("support-url") { v in info.supportURL = v }
        set("profile-web-page-url") { v in info.webPageURL = v }
        set("announce") { v in info.announce = decodeValue(v) }
        set("routing") { v in info.routing = v }
    }

    /// «base64:…» или просто текст.
    static func decodeValue(_ v: String) -> String {
        if v.lowercased().hasPrefix("base64:") { return Links.base64String(String(v.dropFirst(7))) ?? v }
        return v
    }

    /// Массив (или одна) полная конфигурация Xray → серверы из их первого исходящего прокси.
    static func jsonConfigs(_ data: Data) -> [Server]? {
        guard let obj = try? JSONSerialization.jsonObject(with: data) else { return nil }
        let configs: [[String: Any]] = (obj as? [[String: Any]]) ?? ((obj as? [String: Any]).map { [$0] } ?? [])
        let proxies: Set<String> = ["vless", "vmess", "trojan", "shadowsocks", "socks", "hysteria", "wireguard"]
        var servers: [Server] = []
        for (i, config) in configs.enumerated() {
            guard let outbounds = config["outbounds"] as? [[String: Any]],
                  var out = outbounds.first(where: { proxies.contains(($0["protocol"] as? String ?? "").lowercased()) }) else { continue }
            out.removeValue(forKey: "tag")
            // Цепочки через другие исходящие (dialerProxy) отдельно не поддерживаем.
            if var stream = out["streamSettings"] as? [String: Any], var sockopt = stream["sockopt"] as? [String: Any] {
                sockopt.removeValue(forKey: "dialerProxy")
                stream["sockopt"] = sockopt
                out["streamSettings"] = stream
            }
            guard let data = try? JSONSerialization.data(withJSONObject: out, options: [.sortedKeys]) else { continue }
            let raw = String(decoding: data, as: UTF8.self)
            var e = Endpoint(proto: .json)
            e.rawOutbound = raw
            let settings = out["settings"] as? [String: Any]
            let target = (settings?["vnext"] as? [[String: Any]])?.first ?? (settings?["servers"] as? [[String: Any]])?.first ?? settings
            e.address = target?["address"] as? String ?? ""
            e.port = (target?["port"] as? NSNumber)?.intValue ?? 0
            let name = config["remarks"] as? String ?? "Сервер \(i + 1)"
            servers.append(Server(id: Server.stableID(raw), name: name, endpoint: e, link: ""))
        }
        return servers
    }
}

/// Базы сайтов и адресов для маршрутизации (runetfreedom: заблокированное в России и российские сервисы).
nonisolated enum GeoFiles {
    static let sources = [
        "geoip.dat": "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat",
        "geosite.dat": "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat",
    ]

    static var present: Bool {
        sources.keys.allSatisfy { FileManager.default.fileExists(atPath: AppPaths.geo.appendingPathComponent($0).path) }
    }

    static var updated: Date? {
        (try? FileManager.default.attributesOfItem(atPath: AppPaths.geo.appendingPathComponent("geosite.dat").path))?[.modificationDate] as? Date
    }

    /// Раз в неделю — базы обновляются у источника каждые несколько часов, чаще не нужно.
    static var stale: Bool { (updated.map { Date().timeIntervalSince($0) > 7 * 86400 }) ?? true }

    static func categories() -> GeoCategories {
        GeoCategories(sites: GeoCategories.read(AppPaths.geo.appendingPathComponent("geosite.dat")),
                      ips: GeoCategories.read(AppPaths.geo.appendingPathComponent("geoip.dat")))
    }

    /// Скачать обе базы (атомарная замена: ядро никогда не увидит половину файла).
    static func download(sources custom: [String: String]? = nil) async throws {
        AppPaths.ensure(AppPaths.geo)
        for (name, url) in custom ?? sources {
            guard let u = URL(string: url) else { continue }
            var request = URLRequest(url: u, timeoutInterval: 120)
            request.setValue("Veil/1.0 (macOS)", forHTTPHeaderField: "User-Agent")
            let (tmp, response) = try await URLSession.shared.download(for: request)
            if let code = (response as? HTTPURLResponse)?.statusCode, !(200..<300).contains(code) {
                throw SubscriptionLoader.Failure.http(code)
            }
            let size = (try? FileManager.default.attributesOfItem(atPath: tmp.path)[.size] as? Int64) ?? 0
            guard size > 1000 else { throw SubscriptionLoader.Failure.empty }
            let target = AppPaths.geo.appendingPathComponent(name)
            _ = try? FileManager.default.replaceItemAt(target, withItemAt: tmp)
            if !FileManager.default.fileExists(atPath: target.path) { try FileManager.default.moveItem(at: tmp, to: target) }
        }
    }
}
