import Foundation

/// Группа адресов Apple, которую можно закрыть. В /etc/hosts — точные имена (масок там нет); в Veil —
/// ещё и суффиксы: «icloud.com» закрывает все его поддомены.
nonisolated struct NetGroup: Sendable, Identifiable, Hashable {
    let id: String
    let title: String
    let detail: String
    let hosts: [String]
    /// Только для Veil: домены вместе с поддоменами.
    var suffixes: [String] = []
}

/// Как Block закрывает адреса.
nonisolated enum NetworkMethod: String, Codable, CaseIterable, Sendable, Identifiable {
    /// Своя секция в /etc/hosts (пароль администратора, работает всегда).
    case hosts
    /// Правила блокировки в Veil (без пароля, с масками, пока Veil включён).
    case veil

    var id: String { rawValue }
    var title: String { self == .hosts ? "Файл hosts" : "Через Veil" }

    /// Выбор хранится в состоянии Block; по умолчанию — hosts.
    nonisolated(unsafe) static var current: NetworkMethod = .hosts
}

nonisolated enum AppleNetwork {
    static let telemetry = NetGroup(
        id: "telemetry", title: "Телеметрия и аналитика",
        detail: "Отчёты об использовании программ Apple, счётчики событий App Store, News, «Погоды» и «Акций».",
        hosts: ["xp.apple.com", "metrics.icloud.com", "metrics.mzstatic.com", "iphonesubmissions.apple.com",
                "stocks-analytics-events.apple.com", "news-events.apple.com", "weather-analytics-events.apple.com",
                "notes-analytics-events.apple.com", "books-analytics-events.apple.com", "tv-analytics-events.apple.com",
                "music-analytics-events.apple.com", "podcasts-analytics-events.apple.com"],
        suffixes: ["metrics.icloud.com", "metrics.mzstatic.com"])

    static let ads = NetGroup(
        id: "ads", title: "Реклама Apple",
        detail: "Рекламная сеть Apple: объявления в App Store и News, атрибуция установок.",
        hosts: ["iadsdk.apple.com", "cf.iadsdk.apple.com", "api-adservices.apple.com", "banners.itunes.apple.com",
                "advertising.apple.com", "ads.apple.com"],
        suffixes: ["iadsdk.apple.com", "advertising.apple.com"])

    static let appStore = NetGroup(
        id: "appstore", title: "App Store",
        detail: "Витрина, покупки и обновления программ из App Store. Системные обновления идут другими адресами.",
        hosts: ["apps.apple.com", "amp-api.apps.apple.com", "amp-api-edge.apps.apple.com", "buy.itunes.apple.com",
                "su.itunes.apple.com", "apps.mzstatic.com", "osxapps.itunes.apple.com", "search.itunes.apple.com"],
        suffixes: ["apps.apple.com"])

    static let iCloud = NetGroup(
        id: "icloud", title: "iCloud",
        detail: "Синхронизация, iCloud Drive, «Найти Mac», связка ключей iCloud.",
        hosts: ["icloud.com", "www.icloud.com", "setup.icloud.com", "gateway.icloud.com", "keyvalueservice.icloud.com",
                "gateway.icloud.com.cn", "p01-ckdatabase.icloud.com", "ckdevice.icloud.com", "fmipmobile.icloud.com"],
        suffixes: ["icloud.com", "icloud-content.com"])

    static let siri = NetGroup(
        id: "siri", title: "Siri",
        detail: "Распознавание запросов Siri и подсказки Siri на серверах Apple.",
        hosts: ["guzzoni.apple.com", "guzzoni-apple-com.v.aaplimg.com", "api-glb-siri.smoot.apple.com", "api.smoot.apple.com"],
        suffixes: ["guzzoni.apple.com", "smoot.apple.com"])

    static let news = NetGroup(
        id: "news", title: "News и «Акции»",
        detail: "Лента News и котировки «Акций».",
        hosts: ["news-edge.apple.com", "news-client.apple.com", "news-assets.apple.com", "news-sports-events.apple.com",
                "stocks-data-service.apple.com", "stocks-edge.apple.com"],
        suffixes: ["news-edge.apple.com"])

    static let groups = [telemetry, ads, appStore, iCloud, siri, news]

    /// Неприкосновенные адреса: без них Mac не обновляется, не проверяет подписи и сертификаты, сбивает время.
    /// Ни одна группа не закрывает их и их поддомены.
    static let untouchable: [(host: String, why: String)] = [
        ("swscan.apple.com", "каталог обновлений macOS"),
        ("swcdn.apple.com", "загрузка обновлений macOS"),
        ("swdist.apple.com", "загрузка обновлений macOS"),
        ("mesu.apple.com", "список версий и обновлений безопасности"),
        ("gdmf.apple.com", "проверка доступных обновлений"),
        ("updates.cdn-apple.com", "быстрые обновления безопасности"),
        ("gs.apple.com", "подпись обновлений системы"),
        ("ocsp.apple.com", "проверка отозванных сертификатов"),
        ("ocsp2.apple.com", "проверка отозванных сертификатов"),
        ("crl.apple.com", "списки отозванных сертификатов"),
        ("valid.apple.com", "проверка сертификатов"),
        ("certs.apple.com", "сертификаты Apple"),
        ("api.apple-cloudkit.com", "нотаризация: Gatekeeper проверяет программы"),
        ("ppq.apple.com", "проверка сертификатов разработчиков"),
        ("time.apple.com", "точное время"),
        ("captive.apple.com", "вход в Wi‑Fi гостиниц и кафе"),
    ]

    static func isUntouchable(_ host: String) -> Bool {
        let h = host.lowercased()
        return untouchable.contains { h == $0.host || h.hasSuffix("." + $0.host) || $0.host.hasSuffix("." + h) }
    }
}
