import AVFoundation
import AppKit

/// Безголовый прогон настоящего движка плеера: открытие (с перепаковкой Matroska),
/// старт, «Исполняется» с обложкой, перемотка, цветокоррекция. Ловит в CI ошибки,
/// которые проявляются только при воспроизведении — например, нарушения изоляции акторов
/// в колбэках системных фреймворков.
enum PlaybackSelfTest {
    static func run(_ url: URL) -> Never {
        Prefs.registerDefaults()
        UserDefaults.standard.set(false, forKey: Prefs.resumePlayback)
        let library = MediaLibrary()
        let engine = PlayerEngine(library: library)
        engine.volume = 0

        Task { @MainActor in
            func report(_ ok: Bool, _ message: String) -> Never {
                print("{\"ok\": \(ok), \"message\": \"\(message)\", \"time\": \(engine.currentTime), \"duration\": \(engine.duration)}")
                exit(ok ? 0 : 1)
            }
            engine.open(library.add([url]))
            let deadline = Date().addingTimeInterval(120)
            while engine.phase != .ready {
                if case .failed(let message) = engine.phase { report(false, message) }
                if Date() > deadline { report(false, "timeout while preparing") }
                try? await Task.sleep(for: .milliseconds(100))
            }
            // Даём системе запросить обложку для «Исполняется» и поиграть.
            try? await Task.sleep(for: .seconds(2))
            engine.seek(to: engine.duration / 2)
            try? await Task.sleep(for: .seconds(1))
            engine.applyPreset(VideoAdjustments.presets[2])
            try? await Task.sleep(for: .seconds(1.5))
            engine.pause()
            engine.seek(to: 1)
            engine.adjustments.warmth = 0.4
            try? await Task.sleep(for: .seconds(1))
            engine.setRate(1.5)
            engine.play()
            try? await Task.sleep(for: .seconds(1.5))
            if case .failed(let message) = engine.phase { report(false, message) }
            report(true, "played")
        }
        RunLoop.main.run()
        exit(1)
    }
}
