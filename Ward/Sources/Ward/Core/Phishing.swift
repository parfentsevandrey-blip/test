import Foundation

/// Международные домены (punycode, RFC 3492): «xn--e1afmkfd.xn--p1ai» → «пример.рф».
nonisolated enum Punycode {
    private static let base = 36, tMin = 1, tMax = 26, skew = 38, damp = 700, initialBias = 72, initialN = 128

    /// Метка без «xn--» → Unicode. nil — метка испорчена.
    static func decode(_ input: String) -> String? {
        let chars = Array(input.lowercased().unicodeScalars)
        var output: [Unicode.Scalar] = []
        var start = 0
        if let dash = chars.lastIndex(of: "-") {
            guard chars[..<dash].allSatisfy({ $0.isASCII }) else { return nil }
            output = Array(chars[..<dash])
            start = dash + 1
        }
        var n = initialN
        var i = 0
        var bias = initialBias
        var idx = start
        while idx < chars.count {
            let oldI = i
            var w = 1
            var k = base
            while true {
                guard idx < chars.count else { return nil }
                let c = chars[idx].value
                idx += 1
                let digit: Int
                switch c {
                case 0x61...0x7A: digit = Int(c) - 0x61
                case 0x30...0x39: digit = Int(c) - 0x30 + 26
                default: return nil
                }
                let (sum, overflow) = i.addingReportingOverflow(digit * w)
                guard !overflow else { return nil }
                i = sum
                let t = k <= bias ? tMin : (k >= bias + tMax ? tMax : k - bias)
                if digit < t { break }
                w *= base - t
                k += base
                guard w < Int(Int32.max) else { return nil }
            }
            bias = adapt(i - oldI, output.count + 1, oldI == 0)
            n += i / (output.count + 1)
            i %= output.count + 1
            guard let scalar = Unicode.Scalar(n) else { return nil }
            output.insert(scalar, at: i)
            i += 1
        }
        var result = String.UnicodeScalarView()
        result.append(contentsOf: output)
        return String(result)
    }

    private static func adapt(_ delta: Int, _ numPoints: Int, _ first: Bool) -> Int {
        var delta = first ? delta / damp : delta / 2
        delta += delta / numPoints
        var k = 0
        while delta > ((base - tMin) * tMax) / 2 {
            delta /= base - tMin
            k += base
        }
        return k + ((base - tMin + 1) * delta) / (delta + skew)
    }

    enum Script { case latin, cyrillic, greek, other, common }

    static func script(_ s: Unicode.Scalar) -> Script {
        switch s.value {
        case 0x30...0x39, 0x2D: return .common
        case 0x41...0x5A, 0x61...0x7A, 0xC0...0x24F: return .latin
        case 0x400...0x52F: return .cyrillic
        case 0x370...0x3FF: return .greek
        default: return .other
        }
    }

    /// Кириллические и греческие буквы, которые выглядят как латинские.
    static let latinLookalikes: Set<Character> = [
        "а", "в", "е", "к", "м", "н", "о", "р", "с", "т", "у", "х", "і", "ј", "ѕ", "ԁ", "ӏ", "ԛ", "ԝ", "ү", "һ", "ɡ",
        "α", "ο", "ρ", "ν", "τ", "ι", "κ", "χ", "υ",
    ]
    /// Зоны, в которых кириллические имена — норма.
    static let cyrillicZones: Set<String> = ["рф", "бел", "укр", "срб", "мкд", "қаз", "рус", "москва", "дети", "онлайн", "сайт", "орг", "ком", "мон"]

    /// Как показывать хост в адресной строке: Unicode, если это обычное имя на одном языке, и punycode,
    /// если буквы разных алфавитов смешаны или кириллица изображает латинское имя («аррӏе.com»).
    static func display(_ host: String) -> (text: String, suspicious: Bool) {
        let labels = host.lowercased().split(separator: ".").map(String.init)
        guard labels.contains(where: { $0.hasPrefix("xn--") }) else { return (host, false) }
        var decoded: [String] = []
        for label in labels {
            guard label.hasPrefix("xn--") else { decoded.append(label); continue }
            guard let u = decode(String(label.dropFirst(4))) else { return (host, true) }
            decoded.append(u)
        }
        let tld = decoded.last ?? ""
        for label in decoded.dropLast() {
            let scripts = Set(label.unicodeScalars.map(script)).subtracting([.common])
            if scripts.count > 1 { return (host, true) }
            if scripts == [.cyrillic] || scripts == [.greek] {
                let letters = label.filter { $0 != "-" && !$0.isNumber }
                if !cyrillicZones.contains(tld), !letters.isEmpty, letters.allSatisfy({ latinLookalikes.contains($0) }) { return (host, true) }
            }
        }
        return (decoded.joined(separator: "."), false)
    }
}

/// Сайты, которые притворяются популярными: «sberbamk.ru», «paypa1.com», «gosuslugi-pay.ru», «paypal.com.secure-login.net».
nonisolated enum Lookalike {
    static let popular: [String] = [
        "google.com", "youtube.com", "gmail.com", "facebook.com", "instagram.com", "whatsapp.com", "telegram.org", "twitter.com",
        "linkedin.com", "microsoft.com", "live.com", "outlook.com", "office.com", "apple.com", "icloud.com", "amazon.com", "paypal.com",
        "netflix.com", "spotify.com", "github.com", "gitlab.com", "dropbox.com", "binance.com", "coinbase.com", "kraken.com",
        "metamask.io", "blockchain.com", "steampowered.com", "steamcommunity.com", "epicgames.com", "discord.com", "twitch.tv",
        "tiktok.com", "reddit.com", "wikipedia.org", "yahoo.com", "ebay.com", "aliexpress.com", "booking.com", "airbnb.com",
        "adobe.com", "zoom.us", "slack.com", "notion.so", "openai.com", "chatgpt.com", "anthropic.com", "claude.ai", "proton.me",
        "protonmail.com", "bybit.com", "okx.com", "trustwallet.com", "ledger.com", "roblox.com",
        "yandex.ru", "sberbank.ru", "sber.ru", "tinkoff.ru", "tbank.ru", "vtb.ru", "alfabank.ru", "gazprombank.ru", "raiffeisen.ru",
        "pochtabank.ru", "otpbank.ru", "sovcombank.ru", "rshb.ru", "mtsbank.ru", "gosuslugi.ru", "nalog.gov.ru", "mos.ru", "mail.ru",
        "vk.com", "ozon.ru", "wildberries.ru", "avito.ru", "kinopoisk.ru", "cian.ru", "hh.ru", "dzen.ru", "rutube.ru",
        "megamarket.ru", "mvideo.ru", "dns-shop.ru", "citilink.ru", "rzd.ru", "aeroflot.ru", "pobeda.aero", "kaspi.kz",
        "halykbank.kz", "monobank.ua", "privatbank.ua", "payeer.com", "yoomoney.ru", "webmoney.ru", "beeline.ru", "megafon.ru",
        "mts.ru", "tele2.ru", "rt.ru", "pochta.ru", "cdek.ru", "x.com",
    ]

    /// Слова, которые мошенники добавляют к названию бренда.
    static let bait: Set<String> = [
        "login", "signin", "sign", "secure", "security", "verify", "verification", "account", "accounts", "support", "online",
        "bonus", "bonuses", "gift", "gifts", "auth", "wallet", "recovery", "unlock", "update", "help", "service", "services",
        "pay", "payment", "payments", "card", "cards", "kyc", "airdrop", "promo", "prize", "win", "lk", "cabinet", "kabinet",
        "vyplata", "vyplaty", "kompensaciya", "kompensacia", "refund", "claim", "reward", "rewards", "giveaway", "free",
        "check", "confirm", "client", "clients", "id", "my", "web", "app", "official", "ru", "com", "net", "verif", "safe",
        "protect", "restore", "access", "invest", "crypto", "nft", "drop", "event",
    ]
    /// Настоящие сайты компаний, которые иначе выглядели бы как «бренд + слово».
    static let knownGood: Set<String> = [
        "microsoftonline.com", "googlemail.com", "youtube-nocookie.com", "office365.com", "icloud-content.com", "applecard.com",
        "paypal-community.com", "sberbank-ast.ru", "binance.us", "coinbase-wallet.com", "metamask-io.github.io",
    ]

    /// Похожие на вид символы: одна замена внутри такой группы — опечатка «под оригинал».
    static let similar: [Set<Character>] = [
        ["i", "l", "1", "j", "!"], ["o", "0", "q"], ["g", "q", "9"], ["u", "v"], ["n", "m", "r"], ["b", "d", "6", "h"],
        ["s", "5"], ["z", "2"], ["e", "c", "3"], ["a", "o", "4"], ["t", "7", "f"], ["w", "v"],
    ]

    static let homoglyphs: [Character: Character] = [
        "а": "a", "в": "b", "е": "e", "к": "k", "м": "m", "н": "h", "о": "o", "р": "p", "с": "c", "т": "t", "у": "y", "х": "x",
        "і": "i", "ј": "j", "ѕ": "s", "ԁ": "d", "ӏ": "l", "ԛ": "q", "ԝ": "w", "ү": "y", "һ": "h", "ɡ": "g",
        "α": "a", "ο": "o", "ρ": "p", "ν": "v", "τ": "t", "ι": "i", "κ": "k", "χ": "x", "υ": "u",
        "0": "o", "1": "l", "3": "e", "5": "s", "8": "b", "|": "l",
    ]

    /// «Скелет» имени: так оно выглядит, если не присматриваться.
    static func skeleton(_ label: String) -> String {
        var s = String(label.lowercased().map { homoglyphs[$0] ?? $0 }).replacingOccurrences(of: "-", with: "")
        s = s.replacingOccurrences(of: "rn", with: "m").replacingOccurrences(of: "vv", with: "w").replacingOccurrences(of: "cl", with: "d")
        return s.replacingOccurrences(of: "i", with: "l")
    }

    static func label(of site: String) -> String {
        String(site.split(separator: ".").first ?? "")
    }

    /// Хост в Unicode (для «xn--» меток), чтобы сравнивать то, что видит человек.
    static func unicodeHost(_ host: String) -> String {
        host.lowercased().split(separator: ".").map { l in
            l.hasPrefix("xn--") ? (Punycode.decode(String(l.dropFirst(4))) ?? String(l)) : String(l)
        }.joined(separator: ".")
    }

    /// Какой популярный сайт этот адрес изображает. nil — не похоже на подделку.
    static func imitated(host: String) -> String? {
        let h = unicodeHost(host)
        let site = Hosts.site(h)
        if popular.contains(site) || knownGood.contains(site) { return nil }
        // «paypal.com.secure-login.net»: настоящий адрес — только в начале, а сайт другой.
        for p in popular where h.hasPrefix(p + ".") || h.contains("." + p + ".") { return p }
        let name = label(of: site)
        guard name.count >= 3 else { return nil }
        let sk = skeleton(name)
        for p in popular {
            let pl = label(of: p)
            if name == pl { continue } // то же имя в другой зоне — частый законный случай
            // Подмена букв: кириллица вместо латиницы — для любых имён, цифры вместо букв — для имён от 5 знаков.
            if sk == skeleton(pl), pl.count >= 4, !name.allSatisfy(\.isASCII) || pl.count >= 5 { return p }
            if pl.count >= 6, name.count >= 5, abs(name.count - pl.count) <= 1, typo(name, pl) { return p }
            if pl.count >= 4, name.count > pl.count, name.contains(pl) {
                let rest = name.replacingOccurrences(of: pl, with: "-").split(separator: "-").map(String.init).filter { !$0.isEmpty }
                if !rest.isEmpty, rest.allSatisfy({ bait.contains($0) || $0.allSatisfy(\.isNumber) }) { return p }
            }
        }
        return nil
    }

    /// Одна «опечатка под оригинал»: похожая буква, лишняя или пропущенная буква, переставленные соседние.
    static func typo(_ a: String, _ b: String) -> Bool {
        let x = Array(a), y = Array(b)
        if x.count == y.count {
            var diffs: [Int] = []
            for i in 0..<x.count where x[i] != y[i] { diffs.append(i) }
            if diffs.count == 1 {
                let (c, d) = (x[diffs[0]], y[diffs[0]])
                return similar.contains { $0.contains(c) && $0.contains(d) }
            }
            if diffs.count == 2, diffs[1] == diffs[0] + 1 {
                return x[diffs[0]] == y[diffs[1]] && x[diffs[1]] == y[diffs[0]]
            }
            return false
        }
        let (long, short) = x.count > y.count ? (x, y) : (y, x)
        guard long.count == short.count + 1 else { return false }
        // Множественное число («bookings») — обычное законное имя, а не опечатка.
        if long.last == "s", Array(long.dropLast()) == short { return false }
        var i = 0, j = 0, skipped = false
        while i < long.count && j < short.count {
            if long[i] == short[j] { i += 1; j += 1; continue }
            if skipped { return false }
            skipped = true
            i += 1
        }
        return true
    }
}
