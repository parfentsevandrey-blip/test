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
        case "settings", "settings-lists", "settings-data", "settings-sites", "settings-network", "settings-search":
            model.settingsRequest += 1
        default:
            break
        }
    }
}
