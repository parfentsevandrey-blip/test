import Foundation

/// Версия приложения: «1.2.0» и номер сборки. Сравниваются сначала числа версии, затем сборка.
nonisolated struct AppVersion: Comparable, Hashable, Sendable, CustomStringConvertible {
    let marketing: String
    let build: Int

    init(_ marketing: String, build: Int) {
        self.marketing = marketing
        self.build = build
    }

    /// Из Info.plist установленного приложения.
    init?(bundle url: URL) {
        guard let info = NSDictionary(contentsOf: url.appendingPathComponent("Contents/Info.plist")) as? [String: Any] else { return nil }
        marketing = info["CFBundleShortVersionString"] as? String ?? "0"
        build = Int(info["CFBundleVersion"] as? String ?? "") ?? 0
    }

    private var parts: [Int] { marketing.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 } }

    static func < (a: AppVersion, b: AppVersion) -> Bool {
        let x = a.parts, y = b.parts
        for i in 0..<max(x.count, y.count) {
            let l = i < x.count ? x[i] : 0, r = i < y.count ? y[i] : 0
            if l != r { return l < r }
        }
        return a.build < b.build
    }

    static func == (a: AppVersion, b: AppVersion) -> Bool { !(a < b) && !(b < a) }

    func hash(into hasher: inout Hasher) {
        hasher.combine(parts.reversed().drop { $0 == 0 }.reversed().map { $0 })
        hasher.combine(build)
    }

    var description: String { "\(marketing) (\(build))" }
}

/// Одно приложение каталога: описание для витрины и последняя опубликованная сборка.
nonisolated struct CatalogApp: Codable, Identifiable, Hashable, Sendable {
    var id: String
    var name: String
    var bundleID: String
    var tagline: String
    var summary: String
    var description: String
    var features: [String]
    var replaces: String?
    var category: String
    var accent: [Double]
    var order: Int
    var available: Bool

    // Сборка (нет, если приложение ещё не публиковалось).
    var version: String?
    var build: Int?
    var url: URL?
    var sha256: String?
    var size: Int64?
    var minimumSystem: String?
    var icon: URL?
    var screenshots: [URL]?
    var changes: [String]?
    var commit: String?
    var date: Date?
    /// SHA-1 сертификата подписи этой сборки (пусто — ad-hoc).
    var signer: String?

    var latest: AppVersion? {
        guard let version, let build else { return nil }
        return AppVersion(version, build: build)
    }

    var isInstallable: Bool { available && url != nil && sha256 != nil && latest != nil }
}

nonisolated struct Catalog: Codable, Sendable {
    var schema: Int
    var generated: Date?
    /// SHA-1 сертификата, которым подписаны все приложения (пусто — подпись ad-hoc).
    var signer: String?
    var apps: [CatalogApp]

    static func decode(_ data: Data) throws -> Catalog {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { d in
            let text = try d.singleValueContainer().decode(String.self)
            if let date = ISO8601DateFormatter().date(from: text) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: d.codingPath, debugDescription: "Дата: \(text)"))
        }
        var catalog = try decoder.decode(Catalog.self, from: data)
        guard catalog.schema == 1 else { throw HarborError("Каталог новой версии — обновите Harbor") }
        catalog.apps.sort { $0.order < $1.order }
        return catalog
    }
}

nonisolated struct HarborError: LocalizedError, Sendable {
    let message: String
    var recovery: Recovery?

    enum Recovery: Sendable { case appManagement, retry }

    init(_ message: String, recovery: Recovery? = nil) {
        self.message = message
        self.recovery = recovery
    }

    var errorDescription: String? { message }
}
