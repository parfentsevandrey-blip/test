import AppKit

/// Сцены для скриншотов в CI: `--demo-open=файл` и уточнения — `--demo-seek=секунды`,
/// `--demo-subtitle=номер`, `--demo-audio=номер`, `--demo-panel=tracks|info`, `--demo-island=x,y`.
/// Видео ставится на паузу, чтобы панель управления не пряталась.
enum DemoUI {
    static func argument(_ flag: String) -> String? {
        CommandLine.arguments.first { $0.hasPrefix(flag + "=") }.map { String($0.dropFirst(flag.count + 1)) }
    }

    static var isActive: Bool { argument("--demo-open") != nil }

    static var panel: PlayerPanel? { argument("--demo-panel").flatMap(PlayerPanel.init(rawValue:)) }

    static func prepare() {
        guard let island = argument("--demo-island") else { return }
        let parts = island.split(separator: ",").compactMap { Double($0) }
        guard parts.count == 2 else { return }
        UserDefaults.standard.set(parts[0], forKey: Prefs.islandX)
        UserDefaults.standard.set(parts[1], forKey: Prefs.islandY)
    }

    static func run(_ model: AppModel) {
        guard let path = argument("--demo-open") else { return }
        UserDefaults.standard.set(false, forKey: Prefs.resumePlayback)
        NSApp.activate()
        model.open([URL(fileURLWithPath: path)])
        Task { @MainActor in
            let engine = model.engine
            for _ in 0..<300 where engine.phase != .ready { try? await Task.sleep(for: .milliseconds(20)) }
            if let audio = argument("--demo-audio").flatMap(Int.init) { engine.selectAudio(audio) }
            if let index = argument("--demo-subtitle").flatMap(Int.init), engine.subtitleOptions.indices.contains(index) {
                engine.selectSubtitle(engine.subtitleOptions[index].choice)
            }
            try? await Task.sleep(for: .seconds(0.6))
            engine.pause()
            engine.seek(to: argument("--demo-seek").flatMap(Double.init) ?? 2)
        }
    }
}
