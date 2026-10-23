import Foundation
import WebKit

/// Домашняя сеть: роутер, принтер, NAS, камеры и сервисы на самом Mac. Сайт из интернета может
/// незаметно стучаться к ним — сканировать порты, менять настройки роутера, узнавать, что у вас дома.
nonisolated enum LocalNetwork {
    /// Адреса домашней сети и этого Mac (регулярные выражения WebKit: без «или», поэтому по одному).
    static let patterns: [String] = [
        "^[^:]+://localhost[:/]", "^[^:]+://[^/]*\\.localhost[:/]", "^[^:]+://[^/]*\\.local[:/]",
        "^[^:]+://127\\.", "^[^:]+://10\\.", "^[^:]+://192\\.168\\.", "^[^:]+://169\\.254\\.",
        "^[^:]+://172\\.1[6-9]\\.", "^[^:]+://172\\.2[0-9]\\.", "^[^:]+://172\\.3[01]\\.", "^[^:]+://0\\.0\\.0\\.0[:/]",
        "^[^:]+://\\[::1\\]", "^[^:]+://\\[f[cd]", "^[^:]+://\\[fe80",
    ]

    /// Правила WebKit: запросы к домашней сети блокируются, если сама страница — из интернета.
    /// Страницы, открытые из домашней сети (роутер, NAS), работают с ней как обычно.
    static var ruleList: String {
        let rules: [[String: Any]] = patterns.map { pattern in
            ["trigger": ["url-filter": pattern, "unless-top-url": patterns], "action": ["type": "block"]]
        }
        guard let data = try? JSONSerialization.data(withJSONObject: rules) else { return "[]" }
        return String(decoding: data, as: UTF8.self)
    }

    static let rulesVersion = "1"

    static func isLocal(_ host: String) -> Bool {
        let h = host.lowercased()
        if Hosts.isLocal(h) || h == "0.0.0.0" { return true }
        let bare = h.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        return bare == "::1" || bare.hasPrefix("fc") && bare.contains(":") || bare.hasPrefix("fd") && bare.contains(":") || bare.hasPrefix("fe80:")
    }
}

/// Ответ странице, которая всерьёз снимает отпечаток: дальше она ничего не отправляет на сторонние адреса
/// (запросы скриптов, маяки, пиксели) — собранное не уходит к сборщику. Сама страница работает.
nonisolated enum FingerprintResponse {
    static let rulesVersion = "1"
    static var ruleList: String {
        let rules: [[String: Any]] = [
            ["trigger": ["url-filter": ".*", "load-type": ["third-party"], "resource-type": ["raw", "ping", "image"]], "action": ["type": "block"]],
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: rules) else { return "[]" }
        return String(decoding: data, as: UTF8.self)
    }
}

/// Когда вы что-то делали на сайтах, когда через них «отскакивали» и когда Ward впервые увидел их данные.
/// Хранится только на этом Mac, зашифрованным, как история.
nonisolated struct SiteActivity: Codable, Sendable {
    /// Сайт → когда вы последний раз нажимали или печатали на нём.
    var activation: [String: Date] = [:]
    /// Сайт-прокладка → когда через него последний раз перебрасывали.
    var bounces: [String: Date] = [:]
    /// Сайт → когда Ward впервые увидел его хранилище (для срока жизни данных).
    var firstSeen: [String: Date] = [:]

    static let file = "activity.json"

    mutating func activated(_ site: String, at date: Date = Date()) {
        guard !site.isEmpty else { return }
        activation[site] = date
        trim()
    }

    /// Последнее, что известно о сайте: ваше действие или (если его не было) первая встреча.
    func lastUse(_ site: String) -> Date? { activation[site] ?? firstSeen[site] }

    func interacted(_ site: String, within days: Int, now: Date = Date()) -> Bool {
        guard let date = activation[site] else { return false }
        return now.timeIntervalSince(date) < Double(days) * 86_400
    }

    private mutating func trim() {
        for map in [\SiteActivity.activation, \SiteActivity.bounces, \SiteActivity.firstSeen] where self[keyPath: map].count > 3000 {
            let keep = self[keyPath: map].sorted { $0.value > $1.value }.prefix(2000)
            self[keyPath: map] = Dictionary(uniqueKeysWithValues: keep.map { ($0.key, $0.value) })
        }
    }
}

/// Цепочка перехода: сайты, через которые вас провели без вашего участия, — от вашего щелчка до страницы,
/// на которой вы остались. Всё, что между ними, — сайты-прокладки («отскоки»): они на миг становятся
/// «первой стороной», читают и пишут свои cookie и отдают вас дальше с меткой в адресе.
nonisolated struct NavigationChain: Sendable, Equatable {
    /// Сайт, с которого вы ушли (на нём был щелчок или ввод адреса).
    var origin = ""
    /// Сайты по порядку, начиная с первого после `origin`.
    var sites: [String] = []

    /// Новый переход по вашему действию.
    mutating func start(from origin: String, to site: String) {
        self.origin = origin
        sites = site.isEmpty ? [] : [site]
    }

    /// Переход без вашего участия (переадресация сервера или скрипта).
    mutating func hop(to site: String) {
        guard !site.isEmpty, sites.last != site else { return }
        sites.append(site)
        if sites.count > 12 { sites.removeFirst(sites.count - 12) }
    }

    /// Тот же переход, но адрес поправил сам Ward (очистка ссылки, HTTPS): последний сайт заменяется.
    mutating func replaceLast(with site: String) {
        guard !site.isEmpty else { return }
        if sites.isEmpty { sites = [site] } else { sites[sites.count - 1] = site }
    }

    /// Сайты-прокладки: все, кроме конечного, и не совпадающие с исходным и конечным.
    var bounces: [String] {
        guard let final = sites.last, sites.count > 1 else { return [] }
        var seen: Set<String> = []
        return sites.dropLast().filter { $0 != final && $0 != origin && seen.insert($0).inserted }
    }
}

/// «Скопировать чистую ссылку»: без меток слежки, переходников и фрагмента «перейти к тексту».
nonisolated enum CleanLink {
    static func clean(_ url: URL, cleaner: LinkCleaner) -> URL {
        var result = cleaner.clean(url)?.url ?? url
        if let fragment = result.fragment, fragment.hasPrefix(":~:"), var comps = URLComponents(url: result, resolvingAgainstBaseURL: false) {
            comps.fragment = nil
            result = comps.url ?? result
        }
        return result
    }
}

/// Защита страницы от эксплойтов без режима блокировки: WebKit без JIT-компиляции JavaScript
/// и с усиленной проверкой памяти (macOS 26.4 и новее). Сайты работают как обычно, тяжёлые — медленнее.
enum Hardening {
    /// `WKSecurityRestrictionMode.maximizeCompatibility`.
    static let maximizeCompatibility = 1
    private static let setter = NSSelectorFromString("setSecurityRestrictionMode:")

    static var available: Bool { WKWebpagePreferences().responds(to: setter) }

    /// Включить режим без JIT для этой навигации. false — эта версия macOS его не умеет.
    @discardableResult
    static func apply(_ preferences: WKWebpagePreferences) -> Bool {
        guard preferences.responds(to: setter) else { return false }
        preferences.setValue(maximizeCompatibility, forKey: "securityRestrictionMode")
        return true
    }

    private static let gpcSetter = NSSelectorFromString("setGlobalPrivacyControlEnabled:")

    /// Настоящий GPC в WebKit (заголовок Sec-GPC для всех запросов страницы) — macOS 27 и новее.
    static func enableGPC(_ preferences: WKWebpagePreferences) -> Bool {
        guard preferences.responds(to: gpcSetter) else { return false }
        preferences.setValue(true, forKey: "globalPrivacyControlEnabled")
        return true
    }
}
