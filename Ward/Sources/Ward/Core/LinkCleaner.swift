import Foundation

/// Очистка ссылок: метки слежки в адресе (utm_source, fbclid, yclid…) и ссылки-переходники
/// (google.com/url?q=…, vk.com/away.php?to=…), через которые сайты узнают, куда вы ушли.
nonisolated struct LinkCleaner: Sendable {
    /// Метки, известные всем: их убираем, даже если списки ещё не загружены.
    static let builtinParams: Set<String> = [
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "ysclid", "ymclid",
        "_openstat", "mc_eid", "mc_cid", "igshid", "igsh", "twclid", "ttclid", "li_fat_id", "epik",
        "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "vero_conv", "rb_clickid", "s_cid", "wickedid", "srsltid",
        "_ga", "_gl", "zanpid", "irclickid", "gad_source", "gad_campaignid",
    ]
    /// Метки отдельных сайтов: на других сайтах параметр с таким именем может быть нужным.
    static let builtinSiteParams: [String: Set<String>] = [
        "youtube.com": ["si", "feature", "pp"], "youtu.be": ["si", "feature"], "music.youtube.com": ["si"],
        "open.spotify.com": ["si", "context"], "twitter.com": ["s", "t", "ref_src", "ref_url"], "x.com": ["s", "t", "ref_src", "ref_url"],
        "instagram.com": ["igsh", "img_index"], "tiktok.com": ["_t", "_r", "is_from_webapp", "sender_device"],
        "aliexpress.com": ["spm", "scm", "pvid", "algo_pvid", "algo_exp_id"], "aliexpress.ru": ["spm", "scm", "sku_id_s"],
        "ozon.ru": ["asb", "asb2", "avtc", "avte", "avts"], "wildberries.ru": ["targetUrl"], "amazon.com": ["ref", "ref_", "pf_rd_r", "pf_rd_p", "pd_rd_r", "pd_rd_w", "pd_rd_wg", "content-id"],
        "reddit.com": ["share_id", "rdt"], "linkedin.com": ["trackingId", "refId", "trk", "lipi"],
    ]
    /// Метки с этими началами: utm_source, utm_medium, pk_campaign, mtm_…
    static let builtinPrefixes = ["utm_", "pk_", "mtm_", "hsa_", "_hs", "vgo_", "itm_"]

    /// Переходники: хост → [(начало пути, параметр с настоящим адресом)].
    static let builtinRedirectors: [String: [RedirectRule]] = [
        "google.com": [RedirectRule(pathPrefix: "/url", parameter: "q"), RedirectRule(pathPrefix: "/url", parameter: "url")],
        "l.facebook.com": [RedirectRule(pathPrefix: "/l.php", parameter: "u")],
        "lm.facebook.com": [RedirectRule(pathPrefix: "/l.php", parameter: "u")],
        "l.instagram.com": [RedirectRule(pathPrefix: "/", parameter: "u")],
        "l.messenger.com": [RedirectRule(pathPrefix: "/l.php", parameter: "u")],
        "vk.com": [RedirectRule(pathPrefix: "/away.php", parameter: "to")],
        "m.vk.com": [RedirectRule(pathPrefix: "/away.php", parameter: "to")],
        "vk.ru": [RedirectRule(pathPrefix: "/away.php", parameter: "to")],
        "youtube.com": [RedirectRule(pathPrefix: "/redirect", parameter: "q")],
        "t.umblr.com": [RedirectRule(pathPrefix: "/redirect", parameter: "z")],
        "steamcommunity.com": [RedirectRule(pathPrefix: "/linkfilter/", parameter: "url"), RedirectRule(pathPrefix: "/linkfilter/", parameter: "u")],
        "away.vk.com": [RedirectRule(pathPrefix: "/away.php", parameter: "to")],
        "out.reddit.com": [RedirectRule(pathPrefix: "/", parameter: "url")],
        "slack-redir.net": [RedirectRule(pathPrefix: "/link", parameter: "url")],
        "duckduckgo.com": [RedirectRule(pathPrefix: "/l/", parameter: "uddg")],
        "exit.sc": [RedirectRule(pathPrefix: "/", parameter: "url")],
        "deviantart.com": [RedirectRule(pathPrefix: "/users/outgoing", parameter: "")],
    ]

    var params: Set<String> = []
    var siteParams: [String: Set<String>] = [:]
    var redirectors: [String: [RedirectRule]] = [:]

    init(params: Set<String> = [], siteParams: [String: Set<String>] = [:], redirectors: [String: [RedirectRule]] = [:]) {
        self.params = params.union(Self.builtinParams)
        self.siteParams = siteParams.merging(Self.builtinSiteParams) { $0.union($1) }
        self.redirectors = Self.builtinRedirectors.merging(redirectors) { $0 + $1 }
    }

    init(_ set: FilterSet) {
        self.init(params: set.removeParams, siteParams: set.siteRemoveParams, redirectors: set.redirectors)
    }

    /// Итог очистки: чистый адрес и что было убрано.
    nonisolated struct Result: Equatable, Sendable {
        var url: URL
        var removed: [String]
        var unwrapped: Bool
    }

    /// nil — ссылка уже чистая.
    func clean(_ url: URL) -> Result? {
        var current = url
        var unwrapped = false
        // Переходник внутри переходника тоже бывает: разворачиваем до трёх раз.
        for _ in 0..<3 {
            guard let target = unwrap(current) else { break }
            current = target
            unwrapped = true
        }
        var removed: [String] = []
        if let stripped = strip(current, removed: &removed) { current = stripped }
        guard unwrapped || !removed.isEmpty else { return nil }
        return Result(url: current, removed: removed, unwrapped: unwrapped)
    }

    /// Настоящий адрес из ссылки-переходника.
    func unwrap(_ url: URL) -> URL? {
        guard let host = url.host?.lowercased(), let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let path = url.path.isEmpty ? "/" : url.path
        for h in Hosts.chain(host) {
            // google.com, google.ru, google.de… — у всех /url?q=
            let key = h.hasPrefix("google.") ? "google.com" : h
            for rule in redirectors[key] ?? [] where path.hasPrefix(rule.pathPrefix) {
                let value: String?
                if rule.parameter.isEmpty {
                    value = comps.percentEncodedQuery.flatMap { $0.removingPercentEncoding }
                } else {
                    value = comps.queryItems?.first { $0.name == rule.parameter }?.value
                }
                if let value, let target = URL(string: value), let scheme = target.scheme?.lowercased(),
                   scheme == "http" || scheme == "https", target.host?.isEmpty == false {
                    return target
                }
            }
        }
        return nil
    }

    /// Адрес без меток слежки; nil — убирать нечего.
    func strip(_ url: URL, removed: inout [String]) -> URL? {
        guard var comps = URLComponents(url: url, resolvingAgainstBaseURL: false), let items = comps.queryItems, !items.isEmpty else { return nil }
        let host = url.host?.lowercased() ?? ""
        var local: Set<String> = []
        for h in Hosts.chain(host) { local.formUnion((siteParams[h] ?? []).map { $0.lowercased() }) }
        let kept = items.filter { item in
            let name = item.name.lowercased()
            let drop = params.contains(name) || local.contains(name) || Self.builtinPrefixes.contains { name.hasPrefix($0) }
            if drop { removed.append(item.name) }
            return !drop
        }
        guard kept.count != items.count else { return nil }
        comps.queryItems = kept.isEmpty ? nil : kept
        return comps.url
    }
}

/// Ввод в адресной строке: адрес или поисковый запрос.
nonisolated enum Omnibox {
    static func url(from input: String, search: SearchEngine) -> URL? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if let direct = address(text) { return direct }
        return search.url(for: text)
    }

    /// Похоже ли на адрес: схема, домен с зоной, localhost, IP, порт.
    static func address(_ text: String) -> URL? {
        if text.contains(" ") { return nil }
        let lower = text.lowercased()
        for scheme in ["http://", "https://", "file://", "about:", "data:"] where lower.hasPrefix(scheme) {
            return URL(string: text)
        }
        if lower.hasPrefix("view-source:") || lower.hasPrefix("javascript:") { return nil }
        let hostPart = String(lower.split(whereSeparator: { "/?#".contains($0) }).first ?? "")
        let host = String(hostPart.split(separator: ":").first ?? "")
        let isIP = host.split(separator: ".").count == 4 && host.split(separator: ".").allSatisfy { Int($0).map { (0...255).contains($0) } ?? false }
        let hasZone: Bool = {
            let parts = host.split(separator: ".")
            guard parts.count >= 2, let zone = parts.last, zone.count >= 2 else { return false }
            return zone.allSatisfy { $0.isLetter } || zone.hasPrefix("xn--")
        }()
        guard host == "localhost" || isIP || hasZone else { return nil }
        let scheme = Hosts.isLocal(host) ? "http://" : "https://"
        return URL(string: scheme + text)
    }
}

/// Поисковики. По умолчанию — DuckDuckGo: не строит профиль и не связывает запросы с вами.
nonisolated enum SearchEngine: String, Codable, CaseIterable, Identifiable, Sendable {
    case duckduckgo, startpage, brave, ecosia, mojeek, google, yandex

    var id: String { rawValue }

    var title: String {
        switch self {
        case .duckduckgo: "DuckDuckGo"
        case .startpage: "Startpage"
        case .brave: "Brave Search"
        case .ecosia: "Ecosia"
        case .mojeek: "Mojeek"
        case .google: "Google"
        case .yandex: "Яндекс"
        }
    }

    var note: String {
        switch self {
        case .duckduckgo: "Не хранит запросы и не строит профиль. Рекомендуется."
        case .startpage: "Результаты Google без слежки Google: запросы идут от имени Startpage."
        case .brave: "Свой независимый индекс, без профиля и рекламы по интересам."
        case .ecosia: "Сажает деревья на доход от рекламы; запросы не продаёт."
        case .mojeek: "Небольшой независимый поисковик со своим индексом, без отслеживания."
        case .google: "Лучшие результаты, но запросы связываются с вашим профилем Google."
        case .yandex: "Хорош для русскоязычного поиска, но собирает данные о запросах."
        }
    }

    var isPrivate: Bool { self != .google && self != .yandex }

    func url(for query: String) -> URL? {
        var comps: URLComponents
        switch self {
        case .duckduckgo: comps = URLComponents(string: "https://duckduckgo.com/")!; comps.queryItems = [.init(name: "q", value: query)]
        case .startpage: comps = URLComponents(string: "https://www.startpage.com/sp/search")!; comps.queryItems = [.init(name: "query", value: query)]
        case .brave: comps = URLComponents(string: "https://search.brave.com/search")!; comps.queryItems = [.init(name: "q", value: query)]
        case .ecosia: comps = URLComponents(string: "https://www.ecosia.org/search")!; comps.queryItems = [.init(name: "q", value: query)]
        case .mojeek: comps = URLComponents(string: "https://www.mojeek.com/search")!; comps.queryItems = [.init(name: "q", value: query)]
        case .google: comps = URLComponents(string: "https://www.google.com/search")!; comps.queryItems = [.init(name: "q", value: query)]
        case .yandex: comps = URLComponents(string: "https://yandex.ru/search/")!; comps.queryItems = [.init(name: "text", value: query)]
        }
        return comps.url
    }

    /// Это страница результатов этого поисковика — показать в адресной строке сам запрос.
    func query(in url: URL) -> String? {
        guard let comps = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let template = self.url(for: "x"), url.host == template.host, url.path == template.path else { return nil }
        let name = self == .yandex ? "text" : self == .startpage ? "query" : "q"
        return comps.queryItems?.first { $0.name == name }?.value
    }
}
