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

    /// Открыть щит сразу (для скриншота).
    static var showsShield: Bool { isOn && screen == "shield" }

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
                if !screen.hasPrefix("settings") { browser.window?.makeKeyAndOrderFront(nil) }
            }
        }
        await model.blocker.waitReady(timeout: 90)

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
        case "settings", "settings-lists", "settings-data", "settings-sites", "settings-network", "settings-search":
            model.settingsRequest += 1
        default:
            break
        }
    }
}
