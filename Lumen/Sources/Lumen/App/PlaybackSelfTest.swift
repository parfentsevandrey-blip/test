import AVFoundation
import AppKit

/// Безголовый прогон настоящего движка плеера на файле: открытие, воспроизведение, перемотка,
/// переключение звуковых дорожек и субтитров, покадровый шаг, снимок. Печатает отчёт в JSON —
/// ожидания по каждому тестовому файлу проверяет scripts/selftest.py.
enum PlaybackSelfTest {
    static func run(_ url: URL) -> Never {
        Prefs.registerDefaults()
        UserDefaults.standard.set(false, forKey: Prefs.resumePlayback)
        UserDefaults.standard.set(false, forKey: Prefs.autoSubtitles)
        _ = NSApplication.shared
        NSApp.setActivationPolicy(.accessory)
        let engine = PlayerEngine()
        engine.volume = 0.01

        Task { @MainActor in
            var report: [String: Any] = ["file": url.lastPathComponent]
            var problems: [String] = []
            let started = Date()

            func finish() -> Never {
                report["problems"] = problems
                report["ok"] = problems.isEmpty
                if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) {
                    print(String(decoding: data, as: UTF8.self))
                }
                exit(problems.isEmpty ? 0 : 1)
            }

            func wait(_ seconds: Double) async {
                try? await Task.sleep(for: .milliseconds(Int(seconds * 1000)))
            }

            engine.open([url])
            while engine.phase != .ready {
                if case .failed(let message) = engine.phase {
                    problems.append("open: \(message)")
                    finish()
                }
                if Date().timeIntervalSince(started) > 30 {
                    problems.append("open: timeout")
                    finish()
                }
                await wait(0.02)
            }
            report["openSeconds"] = Date().timeIntervalSince(started)
            guard let core = engine.core else {
                problems.append("no core")
                finish()
            }
            report["engine"] = core is MatroskaCore ? "matroska" : "avplayer"

            // Видео в настоящем окне — так же, как в приложении.
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 640, height: 360), styleMask: [.borderless],
                                  backing: .buffered, defer: false)
            window.contentView?.wantsLayer = true
            core.videoLayer.frame = window.contentView?.bounds ?? .zero
            window.contentView?.layer?.addSublayer(core.videoLayer)
            window.orderFrontRegardless()

            let info = engine.info
            report["duration"] = info.duration
            report["hasVideo"] = info.hasVideo
            report["videoCodec"] = info.videoCodec ?? ""
            report["size"] = [info.displaySize.width, info.displaySize.height]
            report["chapters"] = engine.chapters.count
            report["audio"] = engine.audioOptions.map { ["title": $0.title, "detail": $0.detail, "available": $0.available] as [String: Any] }
            report["selectedAudio"] = engine.selectedAudio
            report["subtitles"] = engine.subtitleOptions.dropFirst().map(\.title)

            // 1. Воспроизведение идёт: часы движутся.
            let t0 = engine.liveTime
            await wait(2)
            let played = engine.liveTime - t0
            report["playedSeconds"] = played
            if played < 0.8 { problems.append("play: clock advanced only \(played) s") }
            report["ambientFrame"] = core.ambientFrame() != nil

            // 2. Перемотка в середину.
            if info.duration > 4 {
                let target = (info.duration / 2).rounded()
                engine.seek(to: target)
                await wait(1.5)
                let now = engine.liveTime
                report["seekLanded"] = now
                if abs(now - target) > 2.5 { problems.append("seek: expected ~\(target), got \(now)") }
            }

            // 3. Каждая воспроизводимая звуковая дорожка играет.
            var switched: [Double] = []
            for option in engine.audioOptions where option.available && option.id != engine.selectedAudio {
                engine.selectAudio(option.id)
                await wait(0.4)
                let before = engine.liveTime
                await wait(1.2)
                let advanced = engine.liveTime - before
                switched.append(advanced)
                if engine.selectedAudio != option.id { problems.append("audio \(option.id): not selected") }
                if advanced < 0.5 { problems.append("audio \(option.id) (\(option.title)): clock advanced only \(advanced) s") }
            }
            report["audioSwitch"] = switched

            // 4. Субтитры: каждая дорожка что-то показывает в первые секунды.
            var subtitleResults: [String: String] = [:]
            for option in engine.subtitleOptions where option.choice != .off {
                engine.selectSubtitle(option.choice)
                var seen = ""
                for probe in [1.5, 2.5, 4.5, 6.0] where probe < max(info.duration, 7) {
                    engine.seek(to: probe)
                    await wait(0.9)
                    if let text = engine.subtitleText, !text.isEmpty { seen = text; break }
                    if let image = engine.subtitleImage {
                        seen = "bitmap \(image.pieces.count) @\(Int(image.canvas.width))x\(Int(image.canvas.height))"
                        break
                    }
                }
                subtitleResults[option.title] = seen
            }
            report["subtitleSeen"] = subtitleResults
            engine.selectSubtitle(.off)

            // 5. Пауза, шаг кадра, снимок.
            engine.pause()
            await wait(0.3)
            let paused = engine.liveTime
            await wait(0.6)
            if abs(engine.liveTime - paused) > 0.05 { problems.append("pause: clock still moving") }
            engine.stepFrame(1)
            await wait(0.3)
            report["stepDelta"] = engine.liveTime - paused
            if info.hasVideo {
                report["snapshot"] = await core.snapshot().map { "\($0.width)x\($0.height)" } ?? ""
            }

            // 6. Скорость и повторное воспроизведение.
            engine.setRate(1.5)
            engine.play()
            await wait(1)
            if case .failed(let message) = engine.phase { problems.append("late failure: \(message)") }
            report["finalTime"] = engine.liveTime
            report["totalSeconds"] = Date().timeIntervalSince(started)
            engine.close()
            finish()
        }
        RunLoop.main.run()
        exit(1)
    }
}
