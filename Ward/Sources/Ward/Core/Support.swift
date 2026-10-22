import Foundation

nonisolated enum AppPaths {
    /// `WARD_HOME` — отдельная папка для самопроверок, чтобы не трогать настоящие данные.
    static var support: URL {
        let base = ProcessInfo.processInfo.environment["WARD_HOME"].map { URL(fileURLWithPath: $0) }
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Ward", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    static var rules: URL {
        let dir = support.appendingPathComponent("Rules", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static var downloads: URL {
        FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first ?? URL(fileURLWithPath: NSHomeDirectory())
    }
}

nonisolated enum Format {
    static func count(_ n: Int) -> String {
        switch n {
        case ..<1000: return "\(n)"
        case ..<1_000_000: return String(format: "%.1f тыс.", Double(n) / 1000).replacingOccurrences(of: ".0 ", with: " ").replacingOccurrences(of: ".", with: ",")
        default: return String(format: "%.1f млн", Double(n) / 1_000_000).replacingOccurrences(of: ".", with: ",")
        }
    }

    static func bytes(_ n: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: n, countStyle: .file)
    }

    static func relative(_ date: Date) -> String {
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.unitsStyle = .full
        return f.localizedString(for: date, relativeTo: Date())
    }

    /// Окончание по числу: 1 трекер, 2 трекера, 5 трекеров.
    static func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let mod10 = n % 10, mod100 = n % 100
        if mod10 == 1 && mod100 != 11 { return one }
        if (2...4).contains(mod10) && !(12...14).contains(mod100) { return few }
        return many
    }
}

extension URL {
    /// Хост без «www.».
    nonisolated var siteHost: String {
        let h = (host ?? "").lowercased()
        return h.hasPrefix("www.") ? String(h.dropFirst(4)) : h
    }
}

nonisolated enum Hosts {
    /// Хост и все его «родители»: a.b.example.com → a.b.example.com, b.example.com, example.com.
    static func chain(_ host: String) -> [String] {
        var parts = host.lowercased().split(separator: ".").map(String.init)
        var result: [String] = []
        while parts.count >= 2 {
            result.append(parts.joined(separator: "."))
            parts.removeFirst()
        }
        return result
    }

    /// Приблизительно «сайт» (регистрируемый домен): последние две метки, три — для зон вида co.uk, com.ru.
    static func site(_ host: String) -> String {
        let parts = host.lowercased().split(separator: ".").map(String.init)
        guard parts.count > 2, !parts.allSatisfy({ Int($0) != nil }) else { return parts.joined(separator: ".") }
        let second = parts[parts.count - 2]
        let short = ["co", "com", "net", "org", "gov", "edu", "ac", "msk", "spb"]
        let n = short.contains(second) && parts.last!.count == 2 ? 3 : 2
        return parts.suffix(n).joined(separator: ".")
    }

    /// Локальные адреса: им не нужен HTTPS и проверка по спискам.
    static func isLocal(_ host: String) -> Bool {
        let h = host.lowercased()
        if h == "localhost" || h.hasSuffix(".local") || h.hasSuffix(".localhost") || h == "[::1]" { return true }
        let octets = h.split(separator: ".").compactMap { Int($0) }
        guard octets.count == 4, h.split(separator: ".").count == 4 else { return false }
        switch (octets[0], octets[1]) {
        case (127, _), (10, _), (192, 168), (169, 254): return true
        case (172, 16...31): return true
        default: return false
        }
    }
}
