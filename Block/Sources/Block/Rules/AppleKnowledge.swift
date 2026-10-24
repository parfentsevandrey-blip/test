import Foundation

/// Что делает соединение — с точки зрения человека, а не сети.
nonisolated enum Verdict: String, Codable, CaseIterable, Sendable, Comparable {
    /// Без этого Mac не обновляется, не проверяет подписи и сертификаты, не знает времени. Не закрывается никогда.
    case essential
    /// Сервис Apple, которым можно пользоваться (iCloud, App Store, «Музыка»).
    case service
    /// Не опознано — показывается, решает человек.
    case unknown
    /// Подсказки и Siri: набранное и сказанное уходит на серверы Apple.
    case suggestions
    /// Аналитика, отчёты, счётчики.
    case telemetry
    /// Реклама и атрибуция.
    case ads

    var suspicious: Bool { self == .telemetry || self == .ads || self == .suggestions }

    /// Чем «опаснее», тем выше в списке.
    private var rank: Int {
        switch self {
        case .ads: 0
        case .telemetry: 1
        case .suggestions: 2
        case .unknown: 3
        case .service: 4
        case .essential: 5
        }
    }

    static func < (a: Verdict, b: Verdict) -> Bool { a.rank < b.rank }

    var title: String {
        switch self {
        case .essential: "нужно системе"
        case .service: "сервис Apple"
        case .unknown: "не опознано"
        case .suggestions: "подсказки и Siri"
        case .telemetry: "телеметрия"
        case .ads: "реклама"
        }
    }

    var symbol: String {
        switch self {
        case .essential: "checkmark.shield.fill"
        case .service: "icloud.fill"
        case .unknown: "questionmark.circle.fill"
        case .suggestions: "waveform.badge.magnifyingglass"
        case .telemetry: "chart.bar.xaxis"
        case .ads: "megaphone.fill"
        }
    }
}

/// Процесс Apple: что это и как его остановить.
nonisolated struct AppleProcess: Sendable {
    let name: String
    let title: String
    let verdict: Verdict
    /// Агент пользователя, который можно выключить без пароля.
    var agent: String?
}

/// База знаний: процессы Apple и их адреса. Вшита в приложение, Block никуда за ней не ходит.
nonisolated enum AppleKnowledge {
    static let processes: [AppleProcess] = [
        // Нужно системе — никогда не закрывается.
        AppleProcess(name: "trustd", title: "Проверка сертификатов", verdict: .essential),
        AppleProcess(name: "softwareupdated", title: "Обновления macOS", verdict: .essential),
        AppleProcess(name: "SoftwareUpdateNotificationManager", title: "Уведомления об обновлениях", verdict: .essential),
        AppleProcess(name: "mobileassetd", title: "Системные ресурсы и обновления безопасности", verdict: .essential),
        AppleProcess(name: "timed", title: "Точное время", verdict: .essential),
        AppleProcess(name: "mDNSResponder", title: "Имена сайтов (DNS)", verdict: .essential),
        AppleProcess(name: "syspolicyd", title: "Gatekeeper: проверка программ", verdict: .essential),
        AppleProcess(name: "XprotectService", title: "XProtect: защита от вредоносных программ", verdict: .essential),
        AppleProcess(name: "captiveagent", title: "Вход в Wi‑Fi кафе и гостиниц", verdict: .essential),
        AppleProcess(name: "configd", title: "Настройки сети", verdict: .essential),
        // Сервисы.
        AppleProcess(name: "apsd", title: "Push-уведомления Apple", verdict: .service),
        AppleProcess(name: "cloudd", title: "CloudKit (iCloud)", verdict: .service, agent: "com.apple.cloudd"),
        AppleProcess(name: "bird", title: "iCloud Drive", verdict: .service, agent: "com.apple.bird"),
        AppleProcess(name: "identityservicesd", title: "iMessage и FaceTime", verdict: .service, agent: "com.apple.identityservicesd"),
        AppleProcess(name: "akd", title: "Apple Account", verdict: .service),
        AppleProcess(name: "findmydeviced", title: "«Найти Mac»", verdict: .service),
        AppleProcess(name: "locationd", title: "Геолокация", verdict: .service),
        AppleProcess(name: "geod", title: "Карты и геоданные", verdict: .service, agent: "com.apple.geod"),
        AppleProcess(name: "sharingd", title: "AirDrop и Handoff", verdict: .service),
        AppleProcess(name: "rapportd", title: "Связь с устройствами Apple", verdict: .service),
        AppleProcess(name: "networkserviceproxy", title: "Частный узел iCloud", verdict: .service),
        AppleProcess(name: "nsurlsessiond", title: "Фоновые загрузки системы", verdict: .unknown),
        AppleProcess(name: "App Store", title: "App Store", verdict: .service),
        AppleProcess(name: "appstoreagent", title: "Служба App Store", verdict: .service, agent: "com.apple.appstoreagent"),
        AppleProcess(name: "storedownloadd", title: "Загрузки App Store", verdict: .service),
        AppleProcess(name: "storeassetd", title: "Покупки App Store", verdict: .service),
        AppleProcess(name: "commerce", title: "Покупки Apple", verdict: .service),
        AppleProcess(name: "itunescloudd", title: "Медиатека iCloud", verdict: .service, agent: "com.apple.itunescloudd"),
        AppleProcess(name: "AMPLibraryAgent", title: "Медиатека «Музыки»", verdict: .service, agent: "com.apple.AMPLibraryAgent"),
        AppleProcess(name: "Music", title: "«Музыка»", verdict: .service),
        AppleProcess(name: "TV", title: "TV", verdict: .service),
        AppleProcess(name: "Podcasts", title: "«Подкасты»", verdict: .service),
        AppleProcess(name: "newsd", title: "Служба News", verdict: .service, agent: "com.apple.newsd"),
        AppleProcess(name: "gamed", title: "Game Center", verdict: .service, agent: "com.apple.gamed"),
        AppleProcess(name: "homed", title: "«Дом»", verdict: .service, agent: "com.apple.homed"),
        AppleProcess(name: "financed", title: "Apple Card и Wallet", verdict: .service, agent: "com.apple.financed"),
        AppleProcess(name: "passd", title: "Wallet", verdict: .service),
        AppleProcess(name: "cloudphotod", title: "Фото iCloud", verdict: .service, agent: "com.apple.cloudphotod"),
        AppleProcess(name: "tipsd", title: "«Советы»", verdict: .service, agent: "com.apple.tipsd"),
        // Телеметрия.
        AppleProcess(name: "analyticsd", title: "Аналитика Apple", verdict: .telemetry),
        AppleProcess(name: "SubmitDiagInfo", title: "Отправка диагностики в Apple", verdict: .telemetry),
        AppleProcess(name: "awdd", title: "Диагностика беспроводных сетей для Apple", verdict: .telemetry),
        AppleProcess(name: "feedbackd", title: "Отзывы для Apple", verdict: .telemetry),
        AppleProcess(name: "diagnosticextensionsd", title: "Сбор диагностики", verdict: .telemetry),
        AppleProcess(name: "triald", title: "Эксперименты Apple (A/B-тесты функций)", verdict: .telemetry, agent: "com.apple.triald"),
        // Реклама.
        AppleProcess(name: "adprivacyd", title: "Рекламная атрибуция", verdict: .ads, agent: "com.apple.ap.adprivacyd"),
        AppleProcess(name: "promotedcontentd", title: "Продвигаемый контент (реклама Apple)", verdict: .ads, agent: "com.apple.ap.promotedcontentd"),
        AppleProcess(name: "adservicesd", title: "Рекламные службы Apple", verdict: .ads),
        AppleProcess(name: "amsengagementd", title: "Маркетинг App Store и Apple Music (предложения, баннеры)", verdict: .ads, agent: "com.apple.amsengagementd"),
        AppleProcess(name: "amsaccountsd", title: "Учётная запись App Store и Apple Music", verdict: .service, agent: "com.apple.amsaccountsd"),
        AppleProcess(name: "amsondevicestoraged", title: "Данные Apple Media Services на Mac", verdict: .telemetry),
        AppleProcess(name: "biomesyncd", title: "Синхронизация данных об использовании (Biome)", verdict: .telemetry, agent: "com.apple.biomesyncd"),
        AppleProcess(name: "duetexpertd", title: "Подсказки на основе ваших привычек", verdict: .suggestions, agent: "com.apple.duetexpertd"),
        AppleProcess(name: "routined", title: "Значимые места (история перемещений)", verdict: .suggestions),
        // Подсказки и Siri.
        AppleProcess(name: "parsecd", title: "Подсказки Siri в поиске", verdict: .suggestions, agent: "com.apple.parsecd"),
        AppleProcess(name: "assistantd", title: "Siri", verdict: .suggestions, agent: "com.apple.assistantd"),
        AppleProcess(name: "siriknowledged", title: "Знания Siri", verdict: .suggestions),
        AppleProcess(name: "siriactionsd", title: "Действия Siri", verdict: .suggestions),
        AppleProcess(name: "suggestd", title: "Подсказки Siri из писем и сообщений", verdict: .suggestions, agent: "com.apple.suggestd"),
        AppleProcess(name: "intelligenceplatformd", title: "Apple Intelligence", verdict: .suggestions),
        AppleProcess(name: "privatecloudcomputed", title: "Private Cloud Compute (Apple Intelligence)", verdict: .suggestions),
        AppleProcess(name: "photoanalysisd", title: "Анализ фото", verdict: .service, agent: "com.apple.photoanalysisd"),
    ]

    /// Процесс по имени. nettop обрезает имена до 16 символов, поэтому сравниваются и начала имён.
    static func process(_ name: String) -> AppleProcess? {
        if let exact = processes.first(where: { $0.name == name }) { return exact }
        guard name.count >= 15 else { return nil }
        return processes.first { $0.name.hasPrefix(name) || name.hasPrefix($0.name) }
    }

    /// Процесс принадлежит Apple: системный путь или известное имя.
    static func isApple(path: String, name: String) -> Bool {
        // /usr/bin не в списке: там curl, ssh, git — их запускаете вы, а не Apple.
        let systemRoots = ["/System/", "/usr/libexec/", "/usr/sbin/", "/sbin/", "/Library/Apple/", "/System/Volumes/Preboot/Cryptexes/"]
        if systemRoots.contains(where: path.hasPrefix) { return true }
        if path.hasPrefix("/Applications/"), let bundle = Bundle(path: appBundle(of: path)), bundle.bundleIdentifier?.hasPrefix("com.apple.") == true { return true }
        return process(name) != nil
    }

    /// «/Applications/Safari.app/Contents/MacOS/Safari» → «/Applications/Safari.app».
    static func appBundle(of path: String) -> String {
        guard let range = path.range(of: ".app/") else { return path }
        return String(path[..<range.lowerBound]) + ".app"
    }

    // MARK: Адреса

    /// Адрес по имени: из групп Block, неприкосновенного списка и признаков в самом имени.
    static func verdict(host: String) -> Verdict? {
        let h = host.lowercased()
        if AppleNetwork.isUntouchable(h) { return .essential }
        for group in AppleNetwork.groups where group.hosts.contains(h) || group.suffixes.contains(where: { h == $0 || h.hasSuffix("." + $0) }) {
            switch group.id {
            case "telemetry": return .telemetry
            case "ads": return .ads
            case "siri": return .suggestions
            default: return .service
            }
        }
        // Признаки в имени: счётчики, аналитика, события, реклама.
        let telemetryMarks = ["metrics.", "analytics", "telemetry", "-events.", "diagnostics.", "feedbackws.", "radarsubmissions."]
        let adMarks = ["iadsdk.", "adservices", "advertising.", "ads.apple", "promotedcontent"]
        if adMarks.contains(where: h.contains) { return .ads }
        if telemetryMarks.contains(where: h.contains) { return .telemetry }
        return nil
    }

    /// Имя принадлежит Apple (по домену).
    static func isAppleHost(_ host: String) -> Bool {
        let roots = ["apple.com", "icloud.com", "icloud-content.com", "mzstatic.com", "apple-cloudkit.com", "aaplimg.com", "cdn-apple.com", "apple.news", "itunes.com", "me.com"]
        let h = host.lowercased()
        return roots.contains { h == $0 || h.hasSuffix("." + $0) }
    }

    /// Сеть 17.0.0.0/8 целиком принадлежит Apple.
    static func isAppleAddress(_ ip: String) -> Bool { ip.hasPrefix("17.") }

    /// Итог: имя важнее процесса, если по имени понятно больше (телеметрия на сервисном процессе — телеметрия);
    /// неприкосновенное имя — всегда «нужно системе».
    static func verdict(process: String, host: String?) -> Verdict {
        let byProcess = self.process(process)?.verdict
        if let host, let byHost = verdict(host: host) {
            if byHost == .essential || byProcess == .essential { return .essential }
            return min(byHost, byProcess ?? byHost)
        }
        return byProcess ?? .unknown
    }

    /// Имена, которые Block заранее разрешает в адреса, чтобы узнать, куда идёт соединение
    /// (nettop показывает только адреса).
    static var knownHosts: [String] {
        var all = AppleNetwork.groups.flatMap(\.hosts) + AppleNetwork.untouchable.map(\.host)
        all += ["www.apple.com", "apple.com", "gateway.icloud.com", "p30-ckdatabase.icloud.com", "configuration.apple.com",
                "init.itunes.apple.com", "play.itunes.apple.com", "bag.itunes.apple.com", "gsa.apple.com", "gsas.apple.com",
                "smp-device-content.apple.com", "api.apple-cloudkit.com", "courier.push.apple.com", "init.push.apple.com",
                "weather-data.apple.com", "gspe1-ssl.ls.apple.com", "gsp-ssl.ls.apple.com", "configuration.ls.apple.com",
                "token.safebrowsing.apple", "safebrowsing.apple", "doh.dns.apple.com", "mask.icloud.com", "mask-h2.icloud.com",
                "mesu.apple.com", "xp.apple.com", "cl2.apple.com", "cl4.apple.com", "is1-ssl.mzstatic.com"]
        return Array(Set(all)).sorted()
    }
}
