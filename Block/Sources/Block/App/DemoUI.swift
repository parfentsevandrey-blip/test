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
        model.screen = Screen(rawValue: screen) ?? .overview
        if screen == "onboarding" { model.showOnboarding = true }
    }
}
