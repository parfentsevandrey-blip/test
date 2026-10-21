import Foundation

/// `--demo-ui <модуль>`: CI запускает приложение, оно само выполняет сканирование (только чтение,
/// без запросов доступа к личным папкам), и с окна снимается скриншот.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var module: Module {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count, let m = Module(rawValue: args[i + 1]) else { return .smart }
        return m
    }

    static func prepare(_ model: AppModel) async {
        try? await Task.sleep(for: .milliseconds(400))
        switch module {
        case .smart: model.runSmartScan()
        case .cleanup: model.scanCleanup()
        case .privacy: model.scanPrivacy()
        case .clutter: model.scanClutter()
        case .space: model.scanSpace()
        case .uninstaller:
            for _ in 0..<40 where model.apps.isEmpty { try? await Task.sleep(for: .milliseconds(250)) }
            let preferred = ["Google Chrome", "Visual Studio Code", "Firefox", "Microsoft Edge", "Xcode"]
            let app = preferred.lazy.compactMap { name in model.apps.first { $0.bundle.name == name } }.first ?? model.apps.first
            if let app { model.selectApp(app.id) }
        case .performance: model.loadLaunchItems()
        case .journal: break
        }
    }
}
