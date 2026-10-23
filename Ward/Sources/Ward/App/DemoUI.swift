import AppKit
import SwiftUI

/// `--demo-ui <экран>`: CI запускает Ward, он сам открывает нужный экран, с окна снимается скриншот.
///
/// Страницы в демо настоящие: Ward открывает живые сайты из интернета, и счётчики в щите —
/// то, что защита на самом деле остановила на этих сайтах.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count else { return "start" }
        return args[i + 1]
    }

    static var settingsTab: Int {
        guard isOn else { return 0 }
        switch screen {
        case "settings-lists": return 1
        case "settings-data": return 2
        case "settings-sites": return 3
        case "settings-network": return 4
        case "settings-search": return 5
        default: return 0
        }
    }

    /// Панель безопасности, прокрученная вниз (для второго скриншота).
    static var dashboardBottom: Bool { isOn && screen == "dashboard-bottom" }
    /// Панель с открытым отчётом по трекерам (скриншот «как в Quick Look»).
    static var dashboardReport: DashboardReport? { isOn && screen == "dashboard-report" ? .tracking : nil }

    /// Открыть щит сразу (для скриншота).
    static var showsShield: Bool { isOn && screen == "shield" }

    /// Закладки для скриншотов: папки на панели, как у живого человека.
    static func seedBookmarks() {
        let model = AppModel.shared
        guard model.bookmarks.allLinks.count <= 4, model.bookmarks.bar.allSatisfy({ !$0.isFolder }) else { return }
        let news = model.addFolder("Новости")
        for (url, title) in [("https://lenta.ru", "Лента.ру"), ("https://www.rbc.ru", "РБК"), ("https://www.theverge.com", "The Verge"), ("https://meduza.io", "Медуза")] {
            model.addBookmark(URL(string: url)!, title: title, to: .folder(news.id))
        }
        let work = model.addFolder("Работа")
        for (url, title) in [("https://gitlab.com", "GitLab"), ("https://developer.apple.com", "Apple Developer"), ("https://swift.org", "Swift")] {
            model.addBookmark(URL(string: url)!, title: title, to: .folder(work.id))
        }
        model.addBookmark(URL(string: "https://habr.com")!, title: "Хабр")
        model.addBookmark(URL(string: "https://music.yandex.ru")!, title: "Музыка")
        model.addBookmark(URL(string: "https://maps.apple.com")!, title: "Карты", to: .other)
    }

    /// Загрузки для скриншота: настоящие файлы во временной папке, одна идёт, одна оборвалась.
    static func seedDownloads() {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("WardDemo", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        func file(_ name: String, _ size: Int, source: String, minutesAgo: Double) -> Downloads.Item {
            let url = folder.appendingPathComponent(name)
            if !FileManager.default.fileExists(atPath: url.path) { FileManager.default.createFile(atPath: url.path, contents: Data(count: size)) }
            let item = Downloads.Item(name: name, source: URL(string: source), started: Date().addingTimeInterval(-minutesAgo * 60))
            item.file = url
            item.state = .done
            item.progress = 1
            item.total = Int64(size)
            item.received = Int64(size)
            item.sha256 = Downloads.sha256(of: url)
            return item
        }
        let running = Downloads.Item(name: "Xcode_27.xip", source: URL(string: "https://download.developer.apple.com/Xcode_27.xip"))
        running.total = 9_800_000_000
        running.received = 4_120_000_000
        running.progress = 0.42
        running.speed = 38_000_000
        let broken = Downloads.Item(name: "dataset-2026.zip", source: URL(string: "https://data.example.org/dataset-2026.zip"), started: Date().addingTimeInterval(-40 * 60))
        broken.state = .failed("Соединение прервано — можно скачать заново")
        AppModel.shared.downloads.demo([
            running,
            file("Swarm-1.4.dmg", 3_400_000, source: "https://github.com/parfentsevandrey-blip/test/releases/download/swarm/Swarm-1.4.dmg", minutesAgo: 6),
            file("Отчёт за квартал.pdf", 820_000, source: "https://www.rbc.ru/files/report.pdf", minutesAgo: 25),
            broken,
            file("photo-2026-10-22.jpg", 2_100_000, source: "https://images.unsplash.com/photo.jpg", minutesAgo: 60 * 26),
        ])
    }

    /// Новостные сайты с типичным набором рекламы и счётчиков.
    static let sites = ["https://lenta.ru", "https://www.theverge.com", "https://www.rbc.ru"]

    static func run() async {
        let model = AppModel.shared
        // Окно появляется не сразу.
        for _ in 0..<50 where model.browsers.isEmpty { try? await Task.sleep(for: .milliseconds(200)) }
        guard let browser = model.browsers.first else { return }
        Task {
            for delay in [0.5, 2.0, 5.0] {
                try? await Task.sleep(for: .seconds(delay))
                NSApp.activate()
                // Экран раннера CI меньше окна по умолчанию — подогнать, чтобы панель была видна целиком.
                if let window = browser.window, let frame = window.screen?.visibleFrame ?? NSScreen.main?.visibleFrame {
                    window.setFrame(frame, display: true)
                }
                if !screen.hasPrefix("settings") && !screen.hasPrefix("dashboard") && screen != "bookmarks" { browser.window?.makeKeyAndOrderFront(nil) }
            }
        }
        await model.blocker.waitReady(timeout: 90)
        seedBookmarks()

        switch screen {
        case "start":
            // Сначала по-настоящему открыть несколько сайтов, чтобы счётчики были живыми.
            for site in sites { browser.newTab(url: URL(string: site)!, select: false) }
            try? await Task.sleep(for: .seconds(18))
            browser.select(browser.tabs[0])
        case "page", "shield":
            browser.selected?.load(URL(string: sites[0])!)
            for site in sites.dropFirst() { browser.newTab(url: URL(string: site)!, select: false) }
        case "danger":
            if let bad = model.blocker.index.danger.first(where: { $0.value.contains("") })?.key {
                browser.selected?.load(URL(string: "http://\(bad)/")!)
            }
        case "insecure":
            browser.selected?.load(URL(string: "http://neverssl.com/")!)
        case "private":
            // В этом демо первое окно само частное (см. WardApp).
            browser.newTab(url: URL(string: "https://duckduckgo.com")!, select: false)
        case "dashboard", "dashboard-bottom", "dashboard-report":
            // Настоящие счётчики: сначала открыть сайты, потом показать панель.
            for site in sites { browser.newTab(url: URL(string: site)!, select: false) }
            browser.selected?.load(URL(string: "https://www.theverge.com")!)
            try? await Task.sleep(for: .seconds(22))
            model.openWindow?("dashboard")
            try? await Task.sleep(for: .seconds(1))
            NSApp.activate()
            if let window = NSApp.windows.first(where: { $0.title == "Безопасность" }) {
                if let frame = window.screen?.visibleFrame ?? NSScreen.main?.visibleFrame { window.setFrame(frame, display: true) }
                window.makeKeyAndOrderFront(nil)
            }
        case "bookmarks":
            browser.selected?.load(URL(string: sites[0])!)
            try? await Task.sleep(for: .seconds(6))
            model.openWindow?("bookmarks")
            try? await Task.sleep(for: .seconds(1))
            NSApp.windows.first { $0.title == "Закладки" }?.makeKeyAndOrderFront(nil)
        case "newtab":
            // Новая вкладка: курсор сразу в поле адреса — проверка для CI (в журнале демо).
            browser.selected?.load(URL(string: sites[0])!)
            try? await Task.sleep(for: .seconds(6))
            browser.newTab()
            try? await Task.sleep(for: .seconds(1.5))
            let editor = browser.window?.firstResponder as? NSTextView
            let field = editor?.delegate as? NSTextField
            print("Ward demo: курсор в поле адреса новой вкладки: \((field?.placeholderString ?? field?.placeholderAttributedString?.string) == AddressFocus.placeholder)")
            if let editor { editor.insertText("ward типизация", replacementRange: editor.selectedRange()) }
        case "downloads":
            browser.selected?.load(URL(string: sites[1])!)
            seedDownloads()
            try? await Task.sleep(for: .seconds(6))
            model.openWindow?("downloads")
            try? await Task.sleep(for: .seconds(1))
            NSApp.activate()
            NSApp.windows.first { $0.title == "Загрузки" }?.makeKeyAndOrderFront(nil)
        case "settings", "settings-lists", "settings-data", "settings-sites", "settings-network", "settings-search":
            model.settingsRequest += 1
        default:
            break
        }
    }
}
