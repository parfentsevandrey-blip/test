import AppKit
import SwiftUI

/// `--demo-ui <экран>`: CI запускает Veil, он сам показывает нужный экран, с окна снимается скриншот.
///
/// Подключение в демо настоящее: на этом же Mac поднимается сервер Xray со всеми протоколами (стенд),
/// подписка с ним «приходит» от провайдера, задержка меряется по-настоящему, и через сервер идёт
/// реальный трафик в интернет — чтобы на скриншоте был живой график скорости.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count else { return "main" }
        return args[i + 1]
    }

    static var settingsTab: Int {
        guard isOn else { return 0 }
        switch screen {
        case "settings-routing": return 1
        case "settings-network": return 2
        case "settings-helper": return 3
        default: return 0
        }
    }

    nonisolated(unsafe) private static var bed: TestBed?

    static func run(_ model: AppModel) async {
        Task {
            for delay in [0.5, 2.0, 5.0] {
                try? await Task.sleep(for: .seconds(delay))
                NSApp.activate()
                NSApp.windows.first { $0.identifier?.rawValue.contains("main") == true }?.makeKeyAndOrderFront(nil)
            }
        }
        if screen == "empty" { return }
        await fill(model)
        switch screen {
        case "add":
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString("https://sub.freedom-vpn.example/s/9f2c41d7e0a8", forType: .string)
            model.showAdd = true
        case "share":
            model.shareServer = model.servers.first
        case "settings", "settings-routing", "settings-network", "settings-helper":
            model.settingsRequest += 1
        case "log":
            model.logRequest += 1
        default:
            break
        }
    }

    private static func fill(_ model: AppModel) async {
        let bed = TestBed(redirect: false)
        do { try bed.start() } catch {
            LogBook.shared.add("Стенд не запустился: \(error)", level: .error)
            return
        }
        Self.bed = bed
        let links = Dictionary(uniqueKeysWithValues: bed.links)
        let names: [(String, String)] = [
            ("🇳🇱 Нидерланды · Амстердам", "VLESS Reality Vision"),
            ("🇩🇪 Германия · Франкфурт", "VLESS Reality XHTTP"),
            ("🇫🇮 Финляндия · Хельсинки", "VLESS XHTTP TLS"),
            ("🇰🇿 Казахстан · Алматы", "Hysteria 2"),
            ("🇹🇷 Турция · Стамбул", "Trojan TLS"),
            ("🇺🇸 США · Нью-Йорк", "VLESS gRPC TLS"),
            ("🇬🇧 Великобритания · Лондон", "Shadowsocks 2022"),
            ("🇯🇵 Япония · Токио", "VLESS WebSocket"),
        ]
        var servers: [Server] = []
        for (name, key) in names {
            guard let link = links[key], case .success(var s) = Links.parse(link) else { continue }
            s.name = name
            s.subscriptionID = "demo"
            servers.append(s)
        }
        if let link = links["VLESS TLS Vision"], case .success(var mine) = Links.parse(link) {
            mine.name = "🏠 Мой сервер"
            servers.append(mine)
        }
        var info = SubscriptionInfo()
        info.title = "Freedom VPN"
        info.upload = 2_254_857_830
        info.download = 39_084_202_301
        info.total = 107_374_182_400
        info.expire = Date().addingTimeInterval(86400 * 23)
        info.updateHours = 12
        info.supportURL = "https://t.me/freedom_vpn_support"
        info.webPageURL = "https://freedom-vpn.example"
        info.announce = "Новые серверы в Казахстане и Турции — уже в списке"
        var sub = Subscription(id: "demo", url: "https://sub.freedom-vpn.example/s/9f2c41d7e0a8", title: "")
        sub.updated = Date().addingTimeInterval(-2 * 3600)
        sub.info = info
        var state = SavedState()
        state.servers = servers
        state.subscriptions = [sub]
        state.selectedID = servers.first?.id
        state.routing.mode = .smart
        state.mode = .local
        model.saved = state

        await model.pingAll()
        guard ["main", "share", "log", "add"].contains(screen) else { return }
        await model.connect()
        // Подключение в демо — через локальный порт (помощник в CI не ставим); на экране — обычный режим.
        model.saved.mode = .proxy
        traffic(port: model.options().httpPort)
    }

    /// Живой трафик через сервер стенда для графика скорости.
    private static func traffic(port: Int) {
        Task.detached {
            let session = Probe.session(httpPort: port, timeout: 20)
            for i in 0..<40 {
                let size = [3_000_000, 8_000_000, 1_500_000, 12_000_000][i % 4]
                if let url = URL(string: "https://speed.cloudflare.com/__down?bytes=\(size)") {
                    _ = try? await session.data(from: url)
                }
                try? await Task.sleep(for: .milliseconds(300))
            }
        }
    }
}
