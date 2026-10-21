import Foundation

/// `--demo-ui <экран>`: CI запускает приложение, оно само открывает нужный экран
/// (а в чате — задаёт вопрос установленной модели), и с окна снимается скриншот.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count else { return "chat" }
        return args[i + 1]
    }

    static var autoScan: Bool { isOn && screen == "onboarding" }

    static func prepareBeforeStart(_ model: AppModel) {
        guard isOn else { return }
        model.onboardingDone = screen != "onboarding" && screen != "welcome"
        model.notifyNewModels = false
    }

    static func run(_ model: AppModel) async {
        guard isOn else { return }
        try? await Task.sleep(for: .milliseconds(300))
        switch screen {
        case "foryou": model.section = .forYou
        case "catalog": model.section = .catalog
        case "feed": model.section = .feed
        case "library": model.section = .library
        case "detail":
            model.section = .catalog
            for _ in 0..<40 where model.catalogLoading || model.catalogUpdated == nil { try? await Task.sleep(for: .milliseconds(250)) }
            model.detailCard = model.cards.first { $0.id == "unsloth/gemma-4-12b-it-GGUF" } ?? Curated.cards[9]
        case "chat":
            model.section = .chat
            guard !model.installed.isEmpty else { return }
            model.newChat()
            for _ in 0..<60 where model.engineState != .ready { try? await Task.sleep(for: .milliseconds(250)) }
            model.settings.thinking = false
            model.settings.temperature = 0.3
            model.send("Объясни в трёх коротких пунктах, чем полезны нейросети, которые работают прямо на компьютере.")
        default: break
        }
    }
}
