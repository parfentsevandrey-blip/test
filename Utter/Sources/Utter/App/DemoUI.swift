import AppKit
import SwiftUI

/// `--demo-ui <экран>`: CI запускает приложение, оно само открывает нужный экран, и с экрана снимается скриншот.
///
/// - `menu`, `settings`, `settings-recognition`, `onboarding` — окна интерфейса;
/// - `hud` — плавающая панель над «чужим» окном с живым текстом;
/// - `dictate --audio <файл.wav>` — настоящая диктовка: звук из файла подаётся как с микрофона в реальном времени,
///   а текст печатается в окно-«блокнот» тем же конвейером, что и в любое поле.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String { argument(after: "--demo-ui") ?? "menu" }

    static func argument(after flag: String) -> String? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: flag), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    static var settingsTab: Int {
        guard isOn else { return 0 }
        switch screen {
        case "settings-recognition": return 1
        case "settings-input": return 2
        default: return 0
        }
    }

    private static var windows: [NSWindow] = []

    static func run(_ model: AppModel) async {
        guard isOn else { return }
        // Окна — активные (иначе кнопки рисуются приглушёнными): у программы из строки меню фокус приходится просить.
        Task {
            for delay in [0.4, 1.5, 3.5, 6] {
                try? await Task.sleep(for: .seconds(delay))
                NSApp.activate()
                NSApp.windows.first { $0.isVisible && $0.alphaValue > 0 && $0.canBecomeKey && !($0 is NSPanel) }?.makeKeyAndOrderFront(nil)
            }
        }
        try? await Task.sleep(for: .milliseconds(300))
        switch screen {
        case "menu":
            show(MenuView().environment(model), title: "Utter", size: NSSize(width: 360, height: 420), glass: true)
        case "settings", "settings-recognition", "settings-input":
            // Настоящее окно настроек (сцена Settings) — с панелью вкладок; открывается действием SwiftUI.
            show(SettingsOpener(), title: "", size: NSSize(width: 1, height: 1))
            windows.last?.alphaValue = 0
        case "onboarding":
            model.showOnboarding()
        case "hud":
            let notepad = Notepad(text: "Встреча в четверг\n\n")
            notepad.show()
            model.phase = .listening
            model.speaking = true
            model.startedAt = Date().addingTimeInterval(-14)
            model.engineInUse = .qwen
            model.liveTyped = "Обсудили план релиза: тестирование до пятницы, потом"
            model.liveTail = "выпускаем бету для"
            notepad.append(model.liveTyped)
            model.hud.show(model)
            Task {
                while true {
                    model.level = Float.random(in: 0.35...0.8)
                    try? await Task.sleep(for: .milliseconds(80))
                }
            }
        case "dictate":
            await dictate(model)
        default: break
        }
    }

    private static func show<V: View>(_ view: V, title: String, size: NSSize, glass: Bool = false) {
        let window = NSWindow(contentRect: NSRect(origin: .zero, size: size),
                              styleMask: [.titled, .closable, .fullSizeContentView], backing: .buffered, defer: false)
        window.title = title
        window.titlebarAppearsTransparent = glass
        window.isReleasedWhenClosed = false
        let host = NSHostingView(rootView: view.padding(.top, glass ? 18 : 0))
        window.contentView = host
        window.setContentSize(host.fittingSize.width > 0 ? host.fittingSize : size)
        window.center()
        windows.append(window)
        NSApp.activate()
        window.makeKeyAndOrderFront(nil)
    }

    /// Настоящая диктовка в окно-блокнот.
    private static func dictate(_ model: AppModel) async {
        let notepad = Notepad(text: "")
        notepad.show()
        guard let path = argument(after: "--audio") else {
            notepad.append("Нет звука: --audio <файл.wav>")
            return
        }
        for _ in 0..<1200 where model.modelState != .ready { try? await Task.sleep(for: .milliseconds(250)) }
        guard model.modelState == .ready else {
            notepad.append("Модель не готова: \(model.modelState)")
            return
        }
        guard let samples = AudioFile.load(URL(fileURLWithPath: path), sampleRate: Double(AsrEngine.shared.sampleRate)) else {
            notepad.append("Не удалось прочитать \(path)")
            return
        }
        Typer.shared.sink = { text, erase in
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    if erase > 0 { notepad.erase(erase) }
                    if !text.isEmpty { notepad.append(text) }
                }
            }
        }
        model.sounds = false
        model.begin(feeding: samples)
    }
}

private struct SettingsOpener: View {
    @Environment(\.openSettings) private var openSettings
    var body: some View { Color.clear.frame(width: 1, height: 1).onAppear { openSettings() } }
}

/// Окно «чужой программы» для демонстрации: текст появляется так же, как в любом поле ввода.
final class Notepad {
    private let window: NSWindow
    private let textView: NSTextView

    init(text: String) {
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 760, height: 460),
                          styleMask: [.titled, .closable, .resizable], backing: .buffered, defer: false)
        window.title = "Заметки — Новая заметка"
        window.isReleasedWhenClosed = false
        let scroll = NSTextView.scrollableTextView()
        textView = scroll.documentView as! NSTextView
        textView.font = .systemFont(ofSize: 20)
        textView.textContainerInset = NSSize(width: 24, height: 24)
        textView.string = text
        window.contentView = scroll
    }

    func show() {
        if let screen = NSScreen.main?.visibleFrame {
            window.setFrameOrigin(NSPoint(x: screen.midX - 380, y: screen.midY - 260))
        }
        NSApp.activate()
        window.makeKeyAndOrderFront(nil)
        window.makeFirstResponder(textView)
    }

    func append(_ text: String) {
        textView.string += text
        textView.scrollToEndOfDocument(nil)
    }

    func erase(_ count: Int) {
        textView.string = String(textView.string.dropLast(count))
    }
}
