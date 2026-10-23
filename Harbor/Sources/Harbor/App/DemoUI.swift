import AppKit
import Foundation

/// Демо-режим для скриншотов в CI:
///   --demo-ui <apps.json> <папка иконок> [--demo-onboarding N | --demo-detail <id> | --demo-updates]
/// Каталог берётся из описаний репозитория, состояния приложений выдуманы, сети нет.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    private static func argument(after flag: String) -> String? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: flag), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    private static var iconDirectory: URL? {
        guard isOn, let i = CommandLine.arguments.firstIndex(of: "--demo-ui"), i + 2 < CommandLine.arguments.count else { return nil }
        return URL(fileURLWithPath: CommandLine.arguments[i + 2])
    }

    static var onboardingPage: Int? { argument(after: "--demo-onboarding").flatMap(Int.init) }
    static var selected: String? { argument(after: "--demo-detail") }
    static var showsUpdates: Bool { CommandLine.arguments.contains("--demo-updates") }

    private static var iconCache: [String: NSImage] = [:]

    static func icon(for app: CatalogApp) -> NSImage? {
        guard let dir = iconDirectory else { return nil }
        if let cached = iconCache[app.id] { return cached }
        let image = NSImage(contentsOf: dir.appendingPathComponent("\(app.name).png"))
        iconCache[app.id] = image
        return image
    }

    static func catalog() -> Catalog? {
        guard let path = argument(after: "--demo-ui"), let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }
        var apps: [CatalogApp] = []
        for (i, item) in raw.enumerated() {
            guard let id = item["id"] as? String, let name = item["name"] as? String, let bundleID = item["bundleID"] as? String else { continue }
            apps.append(CatalogApp(
                id: id, name: name, bundleID: bundleID,
                tagline: item["tagline"] as? String ?? "", summary: item["summary"] as? String ?? "",
                description: item["description"] as? String ?? "", features: item["features"] as? [String] ?? [],
                replaces: item["replaces"] as? String, category: item["category"] as? String ?? "",
                accent: item["accent"] as? [Double] ?? [0.2, 0.5, 0.9], order: item["order"] as? Int ?? i, available: true,
                version: "1.0.0", build: 40 + i * 3, url: URL(string: "https://example.org/\(name).zip"), sha256: "00",
                size: Int64(4_000_000 + i * 2_700_000), minimumSystem: "26.0", icon: nil, screenshots: nil,
                changes: ["Новая вкладка настроек «Сеть»", "Быстрее запуск на Apple Silicon", "Исправлены мелкие ошибки"],
                commit: nil, date: Date(), signer: "3F2A9C"))
        }
        return Catalog(schema: 1, generated: Date(), signer: nil, apps: apps.sorted { $0.order < $1.order })
    }

    /// Выдуманные состояния: часть установлена, у части есть обновления, одно скачивается.
    static func states(_ apps: [CatalogApp]) -> [String: AppState] {
        var out: [String: AppState] = [:]
        for app in apps {
            let latest = app.latest ?? AppVersion("1.0.0", build: 1)
            switch app.id {
            case "harbor", "lumen": out[app.id] = .installed(latest)
            case "ward", "swarm": out[app.id] = .update(from: AppVersion("1.0.0", build: latest.build - 6), to: latest)
            case "veil": out[app.id] = .working("Загрузка", 0.42)
            default: out[app.id] = .notInstalled
            }
        }
        return out
    }
}
