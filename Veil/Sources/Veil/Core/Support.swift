import Foundation

nonisolated enum Format {
    static func bytes(_ v: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: max(0, v), countStyle: .binary)
    }

    /// Скорость: «1,2 МБ/с».
    static func speed(_ bytesPerSecond: Double) -> String {
        guard bytesPerSecond >= 1 else { return "0 КБ/с" }
        return ByteCountFormatter.string(fromByteCount: Int64(bytesPerSecond), countStyle: .binary) + "/с"
    }

    static func relative(_ date: Date) -> String {
        if abs(date.timeIntervalSinceNow) < 60 { return "только что" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.unitsStyle = .full
        return f.localizedString(for: date, relativeTo: Date())
    }

    static func date(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "d MMMM yyyy"
        return f.string(from: date)
    }

    static func clock(_ seconds: TimeInterval) -> String {
        let s = max(0, Int(seconds))
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }

    /// «через 12 дней», «истекла».
    static func expiry(_ date: Date) -> String {
        let days = Int((date.timeIntervalSinceNow / 86400).rounded(.up))
        if days < 0 { return "истекла \(Self.date(date))" }
        if days == 0 { return "истекает сегодня" }
        return "ещё \(days) \(plural(days, "день", "дня", "дней")) · до \(Self.date(date))"
    }

    static func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let n10 = n % 10, n100 = n % 100
        if n10 == 1 && n100 != 11 { return one }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return few }
        return many
    }
}

nonisolated enum AppPaths {
    /// `VEIL_HOME` — другая папка данных (самопроверки).
    static let support: URL = {
        if let custom = ProcessInfo.processInfo.environment["VEIL_HOME"] {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Veil", isDirectory: true)
    }()

    static var state: URL { support.appendingPathComponent("state.json") }
    static var geo: URL { support.appendingPathComponent("geo", isDirectory: true) }
    static var runtime: URL { support.appendingPathComponent("run", isDirectory: true) }

    /// Ядро Xray внутри приложения (или `VEIL_XRAY` для самопроверок).
    static var xray: URL {
        if let custom = ProcessInfo.processInfo.environment["VEIL_XRAY"] { return URL(fileURLWithPath: custom) }
        return Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/xray")
    }

    static var helperBinary: URL { Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/VeilHelper") }

    static func ensure(_ url: URL) {
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
    }
}

nonisolated enum Flags {
    /// Флаг страны из двухбуквенного кода: «NL» → 🇳🇱.
    static func emoji(_ code: String) -> String {
        let upper = code.uppercased()
        guard upper.count == 2, upper.unicodeScalars.allSatisfy({ (65...90).contains($0.value) }) else { return "" }
        return String(String.UnicodeScalarView(upper.unicodeScalars.compactMap { UnicodeScalar(127397 + $0.value) }))
    }

    /// Флаг в начале названия сервера («🇩🇪 Германия») — его показываем отдельно, крупно.
    static func leading(_ name: String) -> (flag: String, rest: String) {
        let scalars = Array(name.unicodeScalars)
        guard scalars.count >= 2,
              (0x1F1E6...0x1F1FF).contains(scalars[0].value), (0x1F1E6...0x1F1FF).contains(scalars[1].value) else { return ("", name) }
        let flag = String(String.UnicodeScalarView(scalars[0...1]))
        let rest = String(String.UnicodeScalarView(scalars[2...])).trimmingCharacters(in: .whitespaces)
        return (flag, rest.isEmpty ? name : rest)
    }

    /// Код страны по названию сервера, если флага нет («Germany», «Нидерланды», «NL-1»).
    static func guess(_ name: String) -> String? {
        let lower = name.lowercased()
        let table: [(String, [String])] = [
            ("NL", ["нидерланд", "netherlands", "amsterdam", "амстердам", "holland"]),
            ("DE", ["герман", "germany", "frankfurt", "франкфурт", "berlin"]),
            ("FI", ["финлянд", "finland", "helsinki", "хельсинки"]),
            ("US", ["сша", "usa", "united states", "america", "new york", "нью-йорк"]),
            ("GB", ["британ", "united kingdom", "london", "лондон", "england"]),
            ("FR", ["франц", "france", "paris", "париж"]),
            ("SE", ["швец", "sweden", "stockholm"]),
            ("PL", ["польш", "poland", "warsaw", "варшав"]),
            ("TR", ["турц", "turkey", "türkiye", "istanbul", "стамбул"]),
            ("KZ", ["казахстан", "kazakhstan", "almaty", "алматы"]),
            ("JP", ["япон", "japan", "tokyo", "токио"]),
            ("SG", ["сингапур", "singapore"]),
            ("EE", ["эстон", "estonia", "tallinn"]),
            ("LV", ["латв", "latvia", "riga", "рига"]),
            ("CH", ["швейцар", "switzerland", "zurich"]),
            ("AT", ["австри", "austria", "vienna", "вена"]),
            ("RU", ["росси", "russia", "moscow", "москва"]),
            ("AE", ["оаэ", "uae", "dubai", "дубай"]),
            ("CA", ["канад", "canada", "toronto"]),
            ("ES", ["испан", "spain", "madrid"]),
            ("IT", ["итал", "italy", "milan"]),
        ]
        for (code, words) in table where words.contains(where: { lower.contains($0) }) { return code }
        let token = name.split(whereSeparator: { !$0.isLetter }).first.map(String.init) ?? ""
        if token.count == 2, token == token.uppercased(), !emoji(token).isEmpty { return token }
        return nil
    }
}
