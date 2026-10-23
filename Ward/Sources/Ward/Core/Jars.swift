import CryptoKit
import Foundation

/// «Банки» — отдельные хранилища, в которых живут сайты. Ключ банки — строка:
/// - `sberbank.ru` — изолятор сайта (см. `Isolation`);
/// - `corp:google` — корпоративная банка: все сервисы одной корпорации вместе (вход работает везде),
///   но никого больше;
/// - `mask:example.com` — «Маска»: у каждого сайта своя вымышленная личность.
nonisolated enum JarKind: Sendable { case isolator, corporation, mask }

nonisolated enum Jars {
    static func kind(of key: String) -> JarKind {
        if key.hasPrefix("corp:") { return .corporation }
        if key.hasPrefix("mask:") { return .mask }
        return .isolator
    }

    /// Сайт банки (для изолятора и маски) или id корпорации.
    static func subject(of key: String) -> String {
        if let i = key.firstIndex(of: ":"), kind(of: key) != .isolator { return String(key[key.index(after: i)...]) }
        return key
    }

    static func uuid(_ text: String) -> UUID {
        let digest = Array(SHA256.hash(data: Data(text.utf8)))
        var b = Array(digest.prefix(16))
        b[6] = (b[6] & 0x0F) | 0x50
        b[8] = (b[8] & 0x3F) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }

    /// Человеческое название банки для вкладки и щита.
    static func title(of key: String) -> String {
        switch kind(of: key) {
        case .isolator: "Изолятор"
        case .corporation: corporation(id: subject(of: key))?.name ?? "Корпорация"
        case .mask: "Маска"
        }
    }

    // MARK: Корпорации

    nonisolated struct Corporation: Sendable, Identifiable, Hashable {
        let id: String
        let name: String
        /// Домены корпорации (сайты целиком: поддомены входят).
        let domains: [String]
        /// Национальные зоны: «google» → google.ru, google.de, google.co.uk…
        var ccTLDNames: [String] = []
    }

    /// Крупные технологические корпорации и их домены. Только то, чем владеет сама компания:
    /// облака для чужих сайтов (amazonaws.com, azurewebsites.net, firebaseapp.com) сюда не входят —
    /// иначе чужие сайты попали бы в корпоративную банку.
    static let corporations: [Corporation] = [
        Corporation(id: "google", name: "Google", domains: [
            "google.com", "youtube.com", "youtu.be", "ytimg.com", "googlevideo.com", "youtube-nocookie.com", "gstatic.com",
            "googleapis.com", "googleusercontent.com", "gmail.com", "blogger.com", "blogspot.com", "doubleclick.net",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com", "googlesyndication.com",
            "googleadservices.com", "gvt1.com", "gvt2.com", "ggpht.com", "android.com", "withgoogle.com", "recaptcha.net",
            "app-measurement.com", "2mdn.net", "googleblog.com", "goo.gl", "g.co", "chrome.com", "chromium.org", "waze.com",
        ], ccTLDNames: ["google"]),
        Corporation(id: "yandex", name: "Яндекс", domains: [
            "yandex.ru", "yandex.com", "yandex.net", "ya.ru", "yastatic.net", "yandex.by", "yandex.kz", "yandex.uz",
            "yandex.com.tr", "kinopoisk.ru", "auto.ru", "webvisor.org", "webvisor.com", "adfox.ru", "yandexadexchange.net",
            "yandex-team.ru", "turbopages.org", "yandexcloud.net", "ya.cc", "edadeal.ru",
        ]),
        Corporation(id: "vk", name: "VK", domains: [
            "vk.com", "vk.ru", "vkontakte.ru", "userapi.com", "vk-cdn.net", "vkuseraudio.net", "vkuservideo.net", "mail.ru",
            "ok.ru", "odnoklassniki.ru", "mycdn.me", "imgsmail.ru", "mradx.net", "dzen.ru", "vkvideo.ru", "my.com",
            "tamtam.chat", "max.ru", "vkplay.ru", "youla.ru", "delivery-club.ru", "rustore.ru",
        ]),
        Corporation(id: "microsoft", name: "Microsoft", domains: [
            "microsoft.com", "live.com", "outlook.com", "office.com", "office.net", "microsoftonline.com", "bing.com", "bing.net",
            "msn.com", "linkedin.com", "licdn.com", "skype.com", "xbox.com", "azure.com", "msauth.net", "msftauth.net",
            "clarity.ms", "adnxs.com", "microsoftstore.com", "windows.com", "sharepoint.com", "onedrive.com", "live.net",
            "github.com", "githubassets.com", "githubusercontent.com", "copilot.microsoft.com", "aka.ms", "s-microsoft.com",
        ]),
        Corporation(id: "meta", name: "Meta", domains: [
            "facebook.com", "facebook.net", "fbcdn.net", "fbsbx.com", "instagram.com", "cdninstagram.com", "whatsapp.com",
            "whatsapp.net", "messenger.com", "meta.com", "threads.net", "threads.com", "oculus.com", "fb.com", "fb.me", "wa.me",
        ]),
        Corporation(id: "amazon", name: "Amazon", domains: [
            "amazon.com", "amazon-adsystem.com", "media-amazon.com", "ssl-images-amazon.com", "images-amazon.com",
            "twitch.tv", "ttvnw.net", "jtvnw.net", "imdb.com", "primevideo.com", "audible.com", "amazontrust.com", "a2z.com",
        ], ccTLDNames: ["amazon"]),
        Corporation(id: "apple", name: "Apple", domains: [
            "apple.com", "icloud.com", "mzstatic.com", "cdn-apple.com", "apple-cloudkit.com", "icloud-content.com", "me.com",
        ]),
        Corporation(id: "bytedance", name: "ByteDance (TikTok)", domains: [
            "tiktok.com", "tiktokcdn.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com", "ibyteimg.com", "capcut.com",
            "tiktokcdn-us.com", "byteimg.com", "bytedance.com",
        ]),
        Corporation(id: "x", name: "X (Twitter)", domains: ["x.com", "twitter.com", "twimg.com", "t.co", "ads-twitter.com", "x.ai", "grok.com"]),
        Corporation(id: "openai", name: "OpenAI", domains: ["openai.com", "chatgpt.com", "oaiusercontent.com", "oaistatic.com", "sora.com"]),
        Corporation(id: "anthropic", name: "Anthropic", domains: ["claude.ai", "anthropic.com", "claude.com", "claudeusercontent.com"]),
        Corporation(id: "sber", name: "Сбер", domains: [
            "sber.ru", "sberbank.ru", "sberbank.com", "sberdevices.ru", "giga.chat", "okko.tv", "2gis.ru", "2gis.com", "samokat.ru",
            "zvuk.com", "megamarket.ru", "kuper.ru", "rambler.ru", "lenta.ru", "top100.ru", "sbermarket.ru", "sberhealth.ru",
        ]),
    ]

    /// Домен → корпорация.
    static let domainIndex: [String: String] = {
        var map: [String: String] = [:]
        for c in corporations { for d in c.domains { map[d] = c.id } }
        return map
    }()

    static func corporation(id: String) -> Corporation? { corporations.first { $0.id == id } }

    static func corporation(of host: String) -> Corporation? {
        let h = host.lowercased()
        for candidate in Hosts.chain(h) {
            if let id = domainIndex[candidate] { return corporation(id: id) }
        }
        // google.ru, google.co.uk, amazon.de…: второй уровень — имя корпорации, дальше — национальная зона.
        let labels = h.split(separator: ".").map(String.init)
        for c in corporations where !c.ccTLDNames.isEmpty {
            for name in c.ccTLDNames {
                guard let i = labels.lastIndex(of: name), i >= labels.count - 3, i < labels.count - 1 else { continue }
                let rest = labels[(i + 1)...]
                if rest.count == 1 || (rest.count == 2 && ["co", "com"].contains(rest.first!)) { return c }
            }
        }
        return nil
    }

    /// Страницы входа («Войти через Google», Яндекс ID, VK ID, Apple, Microsoft, Госуслуги). Если на них
    /// уходят из изолятора, они остаются в его банке: у изолированного сайта своя, отдельная учётная запись
    /// входа, которую не видят ни корпорация в своей банке, ни другие сайты.
    static let identityHosts: Set<String> = [
        "accounts.google.com", "accounts.youtube.com", "id.yandex.ru", "passport.yandex.ru", "oauth.yandex.ru",
        "id.vk.com", "id.vk.ru", "oauth.vk.com", "login.vk.com", "appleid.apple.com", "idmsa.apple.com",
        "login.microsoftonline.com", "login.live.com", "account.live.com", "esia.gosuslugi.ru", "id.sber.ru",
        "login.yahoo.com", "auth.mail.ru", "account.mail.ru",
    ]

    /// Чужие корпорации, к которым можно обращаться из корпоративной банки: шрифты и капча — без них
    /// не открываются формы входа на сайтах всех корпораций, а данных о вас в них нет.
    static let sharedInfrastructure = [
        "fonts.googleapis.com", "fonts.gstatic.com", "www.google.com/recaptcha/", "www.gstatic.com/recaptcha/",
        "recaptcha.net", "ajax.googleapis.com",
    ]

    /// Правила для корпоративной банки: запросы к другим корпорациям не уходят вообще.
    static func ruleList(blockingAllBut corp: String) -> String {
        var rules: [String] = []
        for c in corporations where c.id != corp {
            for d in c.domains {
                rules.append(#"{"trigger":{"url-filter":"^[^:]+:(//)?([^/]+\\.)?\#(escape(d))[/:]","load-type":["third-party"]},"action":{"type":"block"}}"#)
            }
            for name in c.ccTLDNames {
                rules.append(#"{"trigger":{"url-filter":"^[^:]+:(//)?([^/]+\\.)?\#(name)\\.(co\\.)?[a-z][a-z][/:]","load-type":["third-party"]},"action":{"type":"block"}}"#)
            }
        }
        for allowed in sharedInfrastructure {
            rules.append(#"{"trigger":{"url-filter":"^[^:]+:(//)?\#(escape(allowed))"},"action":{"type":"ignore-previous-rules"}}"#)
        }
        return "[" + rules.joined(separator: ",") + "]"
    }

    /// Точка в домене — буквально; в JSON обратная косая удваивается.
    private static func escape(_ domain: String) -> String {
        domain.replacingOccurrences(of: ".", with: #"\\."#)
    }

    /// Версия правил: меняется вместе со списком корпораций, чтобы правила пересобрались.
    static let rulesVersion: String = {
        let all = corporations.map { $0.id + ":" + $0.domains.joined(separator: ",") + $0.ccTLDNames.joined() }.joined(separator: ";")
        return SHA256.hash(data: Data((all + sharedInfrastructure.joined()).utf8)).prefix(6).map { String(format: "%02x", $0) }.joined()
    }()
}
