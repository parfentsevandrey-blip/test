import Foundation

/// `--demo-ui <экран>`: CI запускает приложение с выдуманным состоянием (ничего в системе не меняется),
/// и с окна снимается скриншот.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count else { return "overview" }
        return args[i + 1]
    }

    static func app(_ name: String, _ path: String, _ id: String?, system: Bool = false, apple: Bool = false) -> NetApp {
        NetApp(key: id ?? path, name: name, path: path, bundleID: id, isSystem: system, isApple: apple)
    }

    static func demoLock() -> LockSettings {
        var lock = LockSettings()
        lock.on = true
        lock.directResolved = ["185.199.110.40", "203.0.113.17"]
        let now = Date()
        func rule(_ a: NetApp, _ d: NetDecision, _ hosts: [String], _ ok: Int, _ no: Int, _ mb: Int64, _ minutes: Double) -> NetAppRule {
            var r = NetAppRule(app: a, decision: d)
            r.hosts = hosts
            r.allowed = ok
            r.denied = no
            r.bytes = mb * 1_048_576
            r.lastSeen = now.addingTimeInterval(-minutes * 60)
            return r
        }
        let list = [
            rule(app("Ward", "/Applications/Ward.app", "app.ward.browser"), .allow, ["github.com:443", "duckduckgo.com:443", "ru.wikipedia.org:443"], 412, 0, 186, 1),
            rule(app("Telegram", "/Applications/Telegram.app", "org.telegram.desktop"), .allow, ["149.154.167.51:443"], 96, 0, 54, 3),
            rule(app("Veil", "/Applications/Veil.app", "app.veil.client"), .allow, ["sub.example.net:443"], 4, 0, 1, 40),
            rule(app("Harbor", "/Applications/Harbor.app", "app.harbor.store"), .allow, ["github.com:443", "objects.githubusercontent.com:443"], 12, 0, 22, 90),
            rule(app("Zoom", "/Applications/zoom.us.app", "us.zoom.xos"), .deny, ["log.zoom.us:443", "us05web.zoom.us:443"], 0, 31, 0, 12),
            rule(app("Microsoft AutoUpdate", "/Library/Application Support/Microsoft/MAU2.0/Microsoft AutoUpdate.app", "com.microsoft.autoupdate2"), .deny, ["officecdnmac.microsoft.com:443"], 0, 18, 0, 25),
            rule(app("git", "/usr/bin/git", nil, apple: true), .allow, ["github.com:443"], 7, 0, 3, 55),
            rule(app("Notion", "/Applications/Notion.app", "notion.id"), .ask, ["www.notion.so:443"], 0, 2, 0, 5),
            rule(app("softwareupdated", "", nil, system: true, apple: true), .ask, ["swscan.apple.com:443"], 0, 9, 0, 30),
            rule(app("Помощь Apple", "/System/Library/CoreServices/HelpViewer.app", "com.apple.helpviewer", apple: true), .deny, ["help.apple.com:443"], 0, 4, 0, 70),
        ]
        for r in list { lock.apps[r.id] = r }
        return lock
    }

    static func demoFeed() -> [NetFeedItem] {
        let now = Date()
        func item(_ a: NetApp, _ host: String, _ ok: Bool, _ kb: Int64, _ s: Double) -> NetFeedItem {
            NetFeedItem(date: now.addingTimeInterval(-s), app: a, host: host, port: 443, allowed: ok, bytes: kb * 1024, failure: nil)
        }
        let ward = app("Ward", "/Applications/Ward.app", "app.ward.browser")
        let zoom = app("Zoom", "/Applications/zoom.us.app", "us.zoom.xos")
        let tg = app("Telegram", "/Applications/Telegram.app", "org.telegram.desktop")
        let sys = app("Системная служба macOS", "", nil, system: true, apple: true)
        return [
            item(ward, "github.com", true, 840, 4), item(zoom, "log.zoom.us", false, 0, 9), item(tg, "149.154.167.51", true, 120, 14),
            item(sys, "gateway.icloud.com", false, 0, 21), item(ward, "ru.wikipedia.org", true, 410, 33),
            item(app("Microsoft AutoUpdate", "/Library/Application Support/Microsoft/MAU2.0/Microsoft AutoUpdate.app", "com.microsoft.autoupdate2"), "officecdnmac.microsoft.com", false, 0, 48),
            item(ward, "duckduckgo.com", true, 96, 61), item(sys, "xp.apple.com", false, 0, 75),
        ]
    }

    static func prepare(_ model: AppModel) async {
        // Как будто профиль «Полностью» уже применён: состояние только в памяти, система не трогается.
        var s = BlockState()
        s.onboarded = true
        s.profile = .full
        s.answers = Answers(usesICloud: false, needsFindMy: false, usesIPhone: false)
        for rule in Catalog.rules where rule.level != .network || rule.id != "net.icloud" {
            s.baseline[rule.id] = .flag
        }
        s.manualDone = ["intelligence.off"]
        s.stoppedLaunches = ["com.apple.Music": 14, "com.apple.AppStore": 6, "com.apple.tips": 3, "com.apple.news": 2]
        let samples: [(String, JournalEntry.Kind, String)] = [
            ("guard.music", .launch, "закрыта при запуске"),
            ("siri.off", .applied, "было: com.apple.assistant.support Assistant Enabled = да"),
            ("default.browser", .applied, "было: scheme:http → Safari"),
            ("agent.com.apple.tipsd", .applied, "было: запись: none, работала: да"),
            ("net.telemetry", .applied, "с правами администратора"),
            ("ads.personal", .drift, "macOS обновилась до 26.1"),
        ]
        for (id, kind, note) in samples.reversed() {
            if let rule = Catalog.rule(id) { s.log(kind, rule, note) }
        }
        s.observedHosts = ["api-adservices.apple.com", "xp.apple.com"]
        s.lock = demoLock()
        model.state = s
        let now = Date()
        func o(_ process: String, _ path: String, _ host: String?, _ ip: String, _ minutes: Double, _ kb: Int64) -> Sighting {
            Sighting(process: process, pid: 400, path: path, endpoint: host ?? ip, ip: ip, port: 443, firstSeen: now.addingTimeInterval(-3600),
                        lastSeen: now.addingTimeInterval(-minutes * 60), samples: 12, bytesIn: kb * 600, bytesOut: kb * 424,
                        verdict: AppleKnowledge.verdict(process: process, host: host))
        }
        model.monitor.show([
            o("promotedcontentd", "/System/Library/PrivateFrameworks/AdPlatforms.framework/promotedcontentd", "iadsdk.apple.com", "17.253.5.203", 2, 96),
            o("adprivacyd", "/System/Library/PrivateFrameworks/AdPlatforms.framework/adprivacyd", "api-adservices.apple.com", "17.253.5.210", 40, 12),
            o("analyticsd", "/usr/libexec/analyticsd", "xp.apple.com", "17.253.83.140", 6, 220),
            o("analyticsd", "/usr/libexec/analyticsd", "metrics.icloud.com", "17.248.150.71", 9, 48),
            o("parsecd", "/System/Library/PrivateFrameworks/CoreParsec.framework/parsecd", "api-glb-siri.smoot.apple.com", "17.36.202.8", 1, 31),
            o("triald", "/usr/libexec/triald", nil, "17.253.21.207", 15, 64),
            o("cloudd", "/System/Library/PrivateFrameworks/CloudKitDaemon.framework/cloudd", "gateway.icloud.com", "17.248.192.33", 3, 540),
            o("apsd", "/System/Library/PrivateFrameworks/ApplePushService.framework/apsd", "courier.push.apple.com", "17.57.146.52", 0.5, 18),
            o("trustd", "/usr/libexec/trustd", "ocsp2.apple.com", "17.253.83.132", 4, 9),
            o("softwareupdated", "/System/Library/PrivateFrameworks/MobileSoftwareUpdate.framework/softwareupdated", "swscan.apple.com", "17.253.83.205", 30, 1200),
            o("nsurlsessiond", "/usr/libexec/nsurlsessiond", "a23-55-1-2.deploy.static.akamaitechnologies.com", "23.55.1.2", 12, 3400),
        ])
        var states: [String: RuleState] = [:]
        for rule in Catalog.rules {
            switch rule.level {
            case .services where rule.confidence == .experimental: states[rule.id] = .protected("защищена системой")
            default: states[rule.id] = rule.id == "net.icloud" ? .active : .blocked
            }
        }
        states["guard.playground"] = .unavailable("нет в этой системе")
        states["net.observed"] = .blocked
        states["dock.clean"] = .blocked
        model.states = states
        let feed = demoFeed()
        let prompt = screen == "lockprompt"
            ? NetPrompt(app: NetApp(key: "org.telegram.desktop", name: "Telegram", path: "/Applications/Telegram.app", bundleID: "org.telegram.desktop", isSystem: false, isApple: false),
                        host: "api.telegram.org", port: 443)
            : nil
        model.lock.showDemo(feed: feed, prompt: prompt)
        if prompt != nil { NetPromptPanel.shared.show() }
        model.screen = Screen(rawValue: screen == "lockprompt" ? "lock" : screen) ?? .overview
        if screen == "onboarding" { model.showOnboarding = true }
    }
}
