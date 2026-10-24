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
        model.state = s
        var states: [String: RuleState] = [:]
        for rule in Catalog.rules {
            switch rule.level {
            case .services where rule.confidence == .experimental: states[rule.id] = .protected("защищена системой")
            default: states[rule.id] = rule.id == "net.icloud" ? .active : .blocked
            }
        }
        states["guard.playground"] = .unavailable("нет в этой системе")
        states["dock.clean"] = .blocked
        model.states = states
        model.screen = Screen(rawValue: screen) ?? .overview
        if screen == "onboarding" { model.showOnboarding = true }
    }
}
