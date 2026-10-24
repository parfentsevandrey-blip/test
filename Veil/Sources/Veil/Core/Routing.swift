import Foundation

/// Что идёт через VPN, а что — напрямую.
nonisolated enum RouteMode: String, Codable, Sendable, CaseIterable, Identifiable {
    /// Российские сайты и сервисы — напрямую, остальное — через VPN.
    case smart
    /// Только заблокированное в России и сервисы, которые закрыли доступ из России, — через VPN.
    case blocked
    /// Всё через VPN.
    case all
    /// Профиль маршрутизации Happ (из подписки или по ссылке happ://routing/…).
    case profile

    var id: String { rawValue }

    var title: String {
        switch self {
        case .smart: "Умный"
        case .blocked: "Только заблокированное"
        case .all: "Всё через VPN"
        case .profile: "Профиль провайдера"
        }
    }

    var subtitle: String {
        switch self {
        case .smart: "Российские сайты, банки и Госуслуги — напрямую, остальное — через VPN"
        case .blocked: "Через VPN — только заблокированные сайты и сервисы, закрытые для России; остальное — напрямую, на полной скорости"
        case .all: "Весь трафик через сервер, кроме локальной сети"
        case .profile: "Правила, которые прислал провайдер подписки"
        }
    }

    var symbol: String {
        switch self {
        case .smart: "sparkles"
        case .blocked: "lock.open.trianglebadge.exclamationmark"
        case .all: "globe"
        case .profile: "list.bullet.rectangle"
        }
    }
}

nonisolated struct RoutingSettings: Codable, Hashable, Sendable {
    var mode: RouteMode = .smart
    var blockAds = false
    /// Свои правила: домены или IP/подсети, по одному на строку.
    var direct: [String] = []
    var proxy: [String] = []
    var block: [String] = []
    var profile: HappRoutingProfile?
    /// Списки блокировки от других программ каталога (Block): программа → домены. Приходят ссылкой
    /// veil://block-list и заменяются целиком; пустой список убирает правила программы.
    var external: [String: [String]]?
}

/// veil://block-list?owner=block&domains=<base64url(JSON-массив доменов)>
nonisolated enum ExternalBlockList {
    static func parse(_ text: String) -> (owner: String, domains: [String])? {
        guard let comps = URLComponents(string: text), comps.scheme == "veil", comps.host == "block-list" else { return nil }
        let items = comps.queryItems ?? []
        guard let owner = items.first(where: { $0.name == "owner" })?.value?.lowercased(),
              !owner.isEmpty, owner.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "-" }),
              var encoded = items.first(where: { $0.name == "domains" })?.value else { return nil }
        encoded = encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while encoded.count % 4 != 0 { encoded += "=" }
        guard let data = Data(base64Encoded: encoded), let list = try? JSONDecoder().decode([String].self, from: data) else { return nil }
        // Только имена доменов: ни geosite, ни IP, ни регулярных выражений — чужая программа не перенаправит трафик.
        let domains = list.map { $0.lowercased().trimmingCharacters(in: .whitespaces) }.filter { d in
            !d.isEmpty && d.count < 254 && d.contains(".") && d.allSatisfy { $0.isLetter || $0.isNumber || $0 == "." || $0 == "-" }
        }
        return (owner, Array(Set(domains)).sorted())
    }
}

/// Профиль маршрутизации Happ: happ://routing/add/{base64(JSON)}.
nonisolated struct HappRoutingProfile: Codable, Hashable, Sendable {
    var name = ""
    var globalProxy = true
    var remoteDNSType = "DoH"
    var remoteDNSDomain = "https://cloudflare-dns.com/dns-query"
    var remoteDNSIP = "1.1.1.1"
    var domesticDNSType = "DoH"
    var domesticDNSDomain = "https://dns.google/dns-query"
    var domesticDNSIP = "8.8.8.8"
    var geoipURL = ""
    var geositeURL = ""
    var lastUpdated = ""
    var dnsHosts: [String: String] = [:]
    var directSites: [String] = []
    var directIP: [String] = []
    var proxySites: [String] = []
    var proxyIP: [String] = []
    var blockSites: [String] = []
    var blockIP: [String] = []
    var domainStrategy = "IPIfNonMatch"

    /// Из ссылки happ://routing/add/…, happ://routing/onadd/… или голого base64/JSON.
    static func parse(_ text: String) -> (profile: HappRoutingProfile, activate: Bool)? {
        var payload = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var activate = false
        for prefix in ["happ://routing/onadd/", "happ://routing/add/"] where payload.lowercased().hasPrefix(prefix) {
            activate = prefix.contains("onadd")
            payload = String(payload.dropFirst(prefix.count))
        }
        let data = payload.hasPrefix("{") ? Data(payload.utf8) : Links.base64(Links.decode(payload))
        guard let data, let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        func s(_ k: String) -> String? {
            if let v = obj[k] as? String { return v }
            if let v = obj[k] as? Bool { return v ? "true" : "false" }
            if let v = obj[k] as? NSNumber { return v.stringValue }
            return nil
        }
        func list(_ k: String) -> [String] { (obj[k] as? [Any])?.compactMap { $0 as? String } ?? [] }
        var p = HappRoutingProfile()
        p.name = s("Name") ?? "Профиль"
        p.globalProxy = (s("GlobalProxy") ?? "true").lowercased() != "false"
        p.remoteDNSType = s("RemoteDNSType") ?? p.remoteDNSType
        p.remoteDNSDomain = s("RemoteDNSDomain") ?? p.remoteDNSDomain
        p.remoteDNSIP = s("RemoteDNSIP") ?? p.remoteDNSIP
        p.domesticDNSType = s("DomesticDNSType") ?? p.domesticDNSType
        p.domesticDNSDomain = s("DomesticDNSDomain") ?? p.domesticDNSDomain
        p.domesticDNSIP = s("DomesticDNSIP") ?? p.domesticDNSIP
        p.geoipURL = s("Geoipurl") ?? ""
        p.geositeURL = s("Geositeurl") ?? ""
        p.lastUpdated = s("LastUpdated") ?? ""
        p.dnsHosts = (obj["DnsHosts"] as? [String: String]) ?? [:]
        p.directSites = list("DirectSites")
        p.directIP = list("DirectIp")
        p.proxySites = list("ProxySites")
        p.proxyIP = list("ProxyIp")
        p.blockSites = list("BlockSites")
        p.blockIP = list("BlockIp")
        p.domainStrategy = s("DomainStrategy") ?? p.domainStrategy
        return (p, activate)
    }
}

/// Правила маршрутизации для ядра.
nonisolated enum RoutingRules {
    struct Rule {
        var domains: [String] = []
        var ips: [String] = []
        var outbound: String
    }

    /// `available` — категории, которые реально есть в скачанных geosite/geoip (иначе ядро не запустится).
    static func rules(_ settings: RoutingSettings, available: GeoCategories) -> (rules: [Rule], final: String, domainStrategy: String) {
        var rules: [Rule] = []
        func add(_ domains: [String], _ ips: [String], _ outbound: String) {
            let d = domains.compactMap { available.filterDomain($0) }
            let i = ips.compactMap { available.filterIP($0) }
            if !d.isEmpty { rules.append(Rule(domains: d, outbound: outbound)) }
            if !i.isEmpty { rules.append(Rule(ips: i, outbound: outbound)) }
        }
        // Локальная сеть — всегда напрямую.
        add(["geosite:private"], ["geoip:private"], "direct")
        // Свои правила важнее режима.
        let user = { (list: [String]) -> ([String], [String]) in
            let entries = list.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty && !$0.hasPrefix("#") }
            return (entries.filter { !isIP($0) }.map(domainRule), entries.filter(isIP))
        }
        let blocked = user(settings.block), direct = user(settings.direct), proxied = user(settings.proxy)
        add(blocked.0, blocked.1, "block")
        add((settings.external ?? [:]).keys.sorted().flatMap { settings.external?[$0] ?? [] }.map(domainRule), [], "block")
        add(direct.0, direct.1, "direct")
        add(proxied.0, proxied.1, "proxy")
        if settings.blockAds { add(["geosite:category-ads-all"], [], "block") }

        let ruDomains = ["geosite:category-ru", "geosite:ru-available-only-inside", "domain:ru", "domain:su", "domain:xn--p1ai"]
        let closedForRussia = ["geosite:ru-blocked", "geosite:category-ai-chat-!cn", "geosite:youtube"]
        switch settings.mode {
        case .all:
            return (rules, "proxy", "IPIfNonMatch")
        case .smart:
            add(closedForRussia, ["geoip:ru-blocked"], "proxy")
            add(ruDomains, ["geoip:ru"], "direct")
            return (rules, "proxy", "IPIfNonMatch")
        case .blocked:
            add(closedForRussia, ["geoip:ru-blocked"], "proxy")
            return (rules, "direct", "IPIfNonMatch")
        case .profile:
            guard let p = settings.profile else { return (rules, "proxy", "IPIfNonMatch") }
            add(p.blockSites, p.blockIP, "block")
            add(p.proxySites, p.proxyIP, "proxy")
            add(p.directSites, p.directIP, "direct")
            return (rules, p.globalProxy ? "proxy" : "direct", p.domainStrategy)
        }
    }

    static func isIP(_ s: String) -> Bool {
        if s.hasPrefix("geoip:") { return true }
        let host = s.split(separator: "/").first.map(String.init) ?? s
        var v4 = in_addr(), v6 = in6_addr()
        return inet_pton(AF_INET, host, &v4) == 1 || inet_pton(AF_INET6, host, &v6) == 1
    }

    /// «example.com» → «domain:example.com» (сам домен и поддомены); готовые префиксы — как есть.
    static func domainRule(_ s: String) -> String {
        let prefixes = ["domain:", "full:", "regexp:", "keyword:", "geosite:"]
        if prefixes.contains(where: s.hasPrefix) { return s }
        var d = s.lowercased()
        for scheme in ["https://", "http://"] where d.hasPrefix(scheme) { d.removeFirst(scheme.count) }
        d = d.split(separator: "/").first.map(String.init) ?? d
        if d.hasPrefix("*.") { d.removeFirst(2) }
        return "domain:" + d
    }
}

/// Категории в файлах geosite.dat и geoip.dat — читаются прямо из protobuf, без ядра.
nonisolated struct GeoCategories: Sendable {
    var sites: Set<String> = []
    var ips: Set<String> = []
    /// Файлов нет вовсе — правила с geosite/geoip пропускаются.
    var loaded: Bool { !sites.isEmpty || !ips.isEmpty }

    func filterDomain(_ d: String) -> String? {
        guard d.lowercased().hasPrefix("geosite:") else { return d }
        let name = String(d.dropFirst(8)).split(separator: "@").first.map(String.init) ?? ""
        return sites.contains(name.lowercased()) ? d : nil
    }

    func filterIP(_ i: String) -> String? {
        guard i.lowercased().hasPrefix("geoip:") else { return i }
        var name = String(i.dropFirst(6)).lowercased()
        if name.hasPrefix("!") { name.removeFirst() }
        return ips.contains(name) ? i : nil
    }

    static func read(_ url: URL) -> Set<String> {
        guard let data = try? Data(contentsOf: url, options: .alwaysMapped) else { return [] }
        var result = Set<String>()
        data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            let b = raw.bindMemory(to: UInt8.self)
            var i = 0
            func varint() -> Int? {
                var r = 0, shift = 0
                while i < b.count, shift < 64 {
                    let c = Int(b[i]); i += 1
                    r |= (c & 0x7F) << shift
                    if c < 0x80 { return r }
                    shift += 7
                }
                return nil
            }
            // GeoSiteList / GeoIPList: repeated entry = 1; в каждой записи поле 1 — название категории.
            while i < b.count {
                guard let key = varint(), key == 0x0A, let len = varint(), len >= 0, i + len <= b.count else { break }
                let end = i + len
                if let k = varint(), k == 0x0A, let l = varint(), i + l <= end {
                    result.insert(String(decoding: UnsafeBufferPointer(rebasing: b[i..<(i + l)]), as: UTF8.self).lowercased())
                }
                i = end
            }
        }
        return result
    }
}
