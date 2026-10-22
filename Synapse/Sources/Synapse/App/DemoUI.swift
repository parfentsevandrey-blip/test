import AppKit
import SwiftUI

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
        // Окно — во весь экран раннера и активное (иначе кнопки рисуются приглушёнными).
        Task {
            for delay in [0.3, 1.5, 4, 8] {
                try? await Task.sleep(for: .seconds(delay))
                NSApp.activate()
                if let screen = NSScreen.main {
                    for w in NSApp.windows where w.isVisible && w.canBecomeMain && w.frame != screen.visibleFrame {
                        w.setFrame(screen.visibleFrame, display: true)
                    }
                }
            }
        }
        try? await Task.sleep(for: .milliseconds(300))
        switch screen {
        case "foryou": model.section = .forYou
        case "catalog": model.section = .catalog
        case "stretch", "quiet", "images":
            // Как на Mac пользователя: M4 Pro с 24 ГБ.
            model.mac = Hardware.simulated("Apple M4 Pro", memoryGB: 24, gpuCores: 20, freeDiskGB: 400)
            model.section = .catalog
        case "settings", "settings-memory":
            if let m = model.installed.first { model.select(m) }
            showSettings(model, memory: screen == "settings-memory")
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
        case "vision":
            model.section = .chat
            guard !model.installed.isEmpty else { return }
            model.newChat()
            for _ in 0..<80 where model.engineState != .ready { try? await Task.sleep(for: .milliseconds(250)) }
            model.settings.thinking = false
            model.settings.temperature = 0.2
            if let name = sceneImage().flatMap(ImageInput.store) {
                model.send("Какие фигуры и каких цветов ты видишь на картинке?", images: [name])
            }
        default: break
        }
    }

    /// Настройки — отдельным окном поверх главного (окно Settings из SwiftUI демо открыть не может).
    private static var settingsWindow: NSWindow?

    static func showSettings(_ model: AppModel, memory: Bool) {
        let root = ScrollView {
            Group {
                if memory { EngineSettingsView() } else { GenerationSettingsView() }
            }
            .padding(24)
        }
        .environment(model)
        .frame(width: 640, height: 760)
        let window = NSWindow(contentViewController: NSHostingController(rootView: root))
        window.title = memory ? "Настройки — Память" : "Настройки — Ответы"
        window.center()
        window.makeKeyAndOrderFront(nil)
        settingsWindow = window
    }

    /// Картинка для демонстрации зрения: три цветные фигуры на белом.
    static func sceneImage() -> CGImage? {
        let w = 640, h = 400
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.setFillColor(CGColor(srgbRed: 0.92, green: 0.15, blue: 0.15, alpha: 1))
        ctx.fillEllipse(in: CGRect(x: 40, y: 110, width: 170, height: 170))
        ctx.setFillColor(CGColor(srgbRed: 0.1, green: 0.3, blue: 0.95, alpha: 1))
        ctx.fill(CGRect(x: 240, y: 115, width: 160, height: 160))
        ctx.setFillColor(CGColor(srgbRed: 0.1, green: 0.75, blue: 0.3, alpha: 1))
        ctx.move(to: CGPoint(x: 515, y: 290))
        ctx.addLine(to: CGPoint(x: 600, y: 110))
        ctx.addLine(to: CGPoint(x: 430, y: 110))
        ctx.closePath()
        ctx.fillPath()
        return ctx.makeImage()
    }
}
