import Foundation

/// Сведения из списков, которые нужны самому Ward (а не блокировщику WebKit): кто трекер, какие адреса
/// опасны, какие метки убирать из ссылок. Сохраняются рядом с правилами, чтобы не разбирать списки
/// при каждом запуске.
nonisolated struct ProtectionIndex: Codable, Sendable {
    var trackers: [String: TrackerKind] = [:]
    var allowed: Set<String> = []
    var danger: [String: [String]] = [:]
    var removeParams: Set<String> = []
    var siteRemoveParams: [String: Set<String>] = [:]
    var redirectors: [String: [RedirectRule]] = [:]

    init() {}

    init(_ set: FilterSet) {
        trackers = set.trackers
        allowed = set.allowedHosts
        danger = set.danger
        removeParams = set.removeParams
        siteRemoveParams = set.siteRemoveParams
        redirectors = set.redirectors
    }

    var cleaner: LinkCleaner {
        LinkCleaner(params: removeParams, siteParams: siteRemoveParams, redirectors: redirectors)
    }

    /// Сторонний адрес на странице: реклама/трекер/опасное или nil.
    func classify(_ host: String, page: String) -> TrackerKind? {
        let h = host.lowercased()
        guard !h.isEmpty, Hosts.site(h) != Hosts.site(page) else { return nil }
        let chain = Hosts.chain(h)
        if chain.contains(where: { allowed.contains($0) }) { return nil }
        for c in chain { if let kind = trackers[c] { return kind } }
        return nil
    }

    /// Опасный адрес из списков вредоносного ПО и мошенничества.
    func isDangerous(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        let path = url.path
        for c in Hosts.chain(host) + (Hosts.chain(host).isEmpty ? [host] : []) {
            guard let paths = danger[c] else { continue }
            if paths.contains(where: { $0.isEmpty || $0 == "/" || path.hasPrefix($0) }) { return true }
        }
        return false
    }
}

/// Кто стоит за доменом: чтобы в щите было «Google, Яндекс, Meta», а не сотня адресов.
nonisolated enum Companies {
    static let owners: [String: String] = [
        "google-analytics.com": "Google", "googletagmanager.com": "Google", "doubleclick.net": "Google",
        "googlesyndication.com": "Google", "googleadservices.com": "Google", "google.com": "Google",
        "googletagservices.com": "Google", "app-measurement.com": "Google", "2mdn.net": "Google", "adservice.google.com": "Google",
        "facebook.net": "Meta", "facebook.com": "Meta", "instagram.com": "Meta", "fbsbx.com": "Meta",
        "yandex.ru": "Яндекс", "yandex.net": "Яндекс", "yandex.com": "Яндекс", "adfox.ru": "Яндекс", "yandexadexchange.net": "Яндекс", "yastatic.net": "Яндекс",
        "vk.com": "VK", "vk.ru": "VK", "mail.ru": "VK", "userapi.com": "VK", "mycdn.me": "VK", "ok.ru": "VK", "imgsmail.ru": "VK",
        "ads-twitter.com": "X (Twitter)", "twitter.com": "X (Twitter)", "x.com": "X (Twitter)", "t.co": "X (Twitter)",
        "bing.com": "Microsoft", "clarity.ms": "Microsoft", "adnxs.com": "Microsoft", "msn.com": "Microsoft", "linkedin.com": "Microsoft", "licdn.com": "Microsoft",
        "amazon-adsystem.com": "Amazon", "criteo.com": "Criteo", "criteo.net": "Criteo",
        "tiktok.com": "TikTok", "byteoversea.com": "TikTok", "ibytedtos.com": "TikTok",
        "hotjar.com": "Hotjar", "hotjar.io": "Hotjar", "taboola.com": "Taboola", "outbrain.com": "Outbrain",
        "adriver.ru": "AdRiver", "top100.ru": "Rambler", "rambler.ru": "Rambler", "liveinternet.ru": "LiveInternet",
        "scorecardresearch.com": "comScore", "quantserve.com": "Quantcast", "rubiconproject.com": "Magnite",
        "pubmatic.com": "PubMatic", "openx.net": "OpenX", "segment.io": "Segment", "segment.com": "Segment",
        "mixpanel.com": "Mixpanel", "amplitude.com": "Amplitude", "newrelic.com": "New Relic", "nr-data.net": "New Relic",
        "pinimg.com": "Pinterest", "pinterest.com": "Pinterest", "sc-static.net": "Snap", "snapchat.com": "Snap",
        "tns-counter.ru": "Mediascope", "mediascope.net": "Mediascope", "adsrvr.org": "The Trade Desk",
        "casalemedia.com": "Index Exchange", "smartadserver.com": "Equativ", "moatads.com": "Oracle", "bluekai.com": "Oracle",
        "addthis.com": "Oracle", "demdex.net": "Adobe", "omtrdc.net": "Adobe", "everesttech.net": "Adobe",
        "yahoo.com": "Yahoo", "advertising.com": "Yahoo", "onetrust.com": "OneTrust", "cookielaw.org": "OneTrust",
        "sentry.io": "Sentry", "intercom.io": "Intercom", "zendesk.com": "Zendesk", "hubspot.com": "HubSpot", "hs-analytics.net": "HubSpot",
        "mgid.com": "MGID", "relap.io": "Relap", "smi2.ru": "СМИ2", "24smi.org": "24СМИ", "sape.ru": "Sape", "buzzoola.com": "Buzzoola",
    ]

    static func owner(of host: String) -> String {
        for c in Hosts.chain(host) { if let name = owners[c] { return name } }
        return Hosts.site(host)
    }
}
