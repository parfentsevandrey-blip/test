import Foundation

/// Самопроверки для CI: `Utter.app/Contents/MacOS/Utter --selftest-<режим>`.
///
/// - `logic` — без сети: детектор речи, утверждение слов, склейка фраз, команды, сочетания клавиш;
/// - `asr`   — настоящая модель Qwen3-ASR: загрузка с проверкой SHA-256, распознавание русской речи,
///             потоковая диктовка в реальном времени (сколько слов напечатано до конца фразы, задержка),
///             голосовые команды, словарь.
nonisolated enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        setvbuf(stdout, nil, _IOLBF, 0)
        var ok = false
        switch mode {
        case "--selftest-logic": ok = logic()
        case "--selftest-asr": ok = blocking { await asr(gpu: false) }
        case "--selftest-asr-gpu": ok = blocking { await asr(gpu: true) }
        default: print("Неизвестный режим \(mode)")
        }
        print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
        exit(ok ? 0 : 1)
    }

    @discardableResult
    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    final class Box<T>: @unchecked Sendable {
        private let lock = NSLock()
        private var _value: T
        init(_ value: T) { _value = value }
        var value: T {
            get { lock.withLock { _value } }
            set { lock.withLock { _value = newValue } }
        }
    }

    static func blocking(_ work: @escaping @Sendable () async -> Bool) -> Bool {
        let box = Box(false)
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            box.value = await work()
            done.signal()
        }
        done.wait()
        return box.value
    }

    /// Пословная ошибка распознавания (WER) без учёта регистра, знаков препинания и ё/е.
    static func wer(_ reference: String, _ hypothesis: String) -> Double {
        func norm(_ s: String) -> [String] {
            s.lowercased().replacingOccurrences(of: "ё", with: "е")
                .unicodeScalars.map { CharacterSet.letters.contains($0) || CharacterSet.decimalDigits.contains($0) ? Character($0) : " " }
                .reduce(into: "") { $0.append($1) }
                .split(separator: " ").map(String.init)
        }
        let r = norm(reference), h = norm(hypothesis)
        guard !r.isEmpty else { return h.isEmpty ? 0 : 1 }
        var d = Array(0...h.count)
        for i in 1...r.count {
            var prev = d[0]
            d[0] = i
            for j in stride(from: 1, through: h.count, by: 1) {
                let tmp = d[j]
                d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] == h[j - 1] ? 0 : 1))
                prev = tmp
            }
        }
        return Double(d[h.count]) / Double(r.count)
    }

    // MARK: - Логика

    static func logic() -> Bool {
        var ok = true

        print("— Детектор речи")
        var vad = VoiceDetector(sampleRate: 16000, pauseMs: 600)
        func tone(_ seconds: Double, _ amp: Float) -> [Float] {
            (0..<Int(seconds * 16000)).map { amp * sin(Float($0) * 2 * .pi * 220 / 16000) }
        }
        func noise(_ seconds: Double, _ amp: Float) -> [Float] { (0..<Int(seconds * 16000)).map { _ in Float.random(in: -amp...amp) } }
        var events: [VoiceDetector.Event] = []
        for chunk in [noise(1.0, 0.003), tone(1.2, 0.3), noise(0.9, 0.003), tone(0.8, 0.25), noise(1.0, 0.003)] {
            for i in stride(from: 0, to: chunk.count, by: 1600) {
                events += vad.process(Array(chunk[i..<min(chunk.count, i + 1600)]))
            }
        }
        let starts = events.filter { $0 == .start }.count
        let ends = events.compactMap { if case .end(let a) = $0 { return a.count } else { return nil } }
        ok = check(starts == 2 && ends.count == 2, "две фразы с паузой между ними: начал \(starts), концов \(ends.count)") && ok
        ok = check(ends.first.map { Double($0) / 16000 > 1.2 && Double($0) / 16000 < 2.4 } ?? false,
                   "звук фразы с запасом в начале и паузой в конце: \(String(format: "%.2f", Double(ends.first ?? 0) / 16000)) с") && ok

        print("— Утверждение слов (LocalAgreement)")
        var c = Committer()
        var typed: [String] = []
        for h in ["Сегодня", "Сегодня я", "Сегодня я дикту", "Сегодня я диктую текст", "Сегодня я диктую текст на", "Сегодня я диктую текст на русском"] {
            typed += c.update(h)
        }
        ok = check(typed == ["Сегодня", "я", "диктую", "текст", "на"], "утверждено только то, в чём сошлись две гипотезы: \(typed)") && ok
        typed += c.finalize("Сегодня я диктую текст на русском языке.")
        ok = check(typed.joined(separator: " ") == "Сегодня я диктую текст на русском языке.", "конец фразы допечатан: «\(typed.joined(separator: " "))»") && ok
        var c2 = Committer()
        _ = c2.update("Новая")
        ok = check(c2.update("Новая строка").isEmpty, "первые слова придержаны (вдруг это команда)") && ok

        print("— Склейка фраз")
        var j = TextJoiner()
        let a = j.append(["Я", "думаю,"])
        let b = j.append(["что"])
        j.closeUtterance()
        let d = j.append(["Это", "хорошая", "идея."])
        j.closeUtterance()
        let e = j.append(["Завтра", "начнём."])
        ok = check(a + b + d + e == "Я думаю, что это хорошая идея. Завтра начнём.", "пауза посреди предложения не делает заглавную: «\(a + b + d + e)»") && ok
        ok = check(TextJoiner.decapitalize("МГУ") == "МГУ" && TextJoiner.decapitalize("Это") == "это", "аббревиатуры не трогаем") && ok
        let (_, erase) = j.apply(.deleteLast)
        ok = check(erase == " Завтра начнём.".count, "«удали это» стирает последнюю фразу: \(erase) символов") && ok
        var k = TextJoiner()
        _ = k.append(["Отправляю", "итоги", "встречи", "релиз"])
        let fix = k.settle(["Отправляю", "итоги", "встречи:", "релиз", "переносим."])
        ok = check(fix.backspaces == " релиз".count && fix.text == ": релиз переносим." && k.typed == "Отправляю итоги встречи: релиз переносим.",
                   "окончательный вариант фразы правится с места расхождения: −\(fix.backspaces) +«\(fix.text)»") && ok
        let undo = k.settle([])
        ok = check(undo.backspaces == "Отправляю итоги встречи: релиз переносим.".count && k.typed.isEmpty, "команда стирает успевшее напечататься начало") && ok

        print("— Голосовые команды")
        ok = check(VoiceCommand.match("Новая строка.") == .newLine && VoiceCommand.match("новый абзац") == .newParagraph
                   && VoiceCommand.match("Удали это!") == .deleteLast && VoiceCommand.match("Новая строка кода") == nil, "команды распознаются только целой фразой") && ok

        print("— Сочетания клавиш")
        ok = check(Shortcut.default.display == "⌥ Пробел", "по умолчанию: \(Shortcut.default.display)") && ok

        print("— Защита от зацикливания")
        let loop: [Int32] = Array(repeating: [1, 2, 3, 4, 5, 6, 7, 8], count: 3).flatMap { $0 }
        ok = check(AsrEngine.repeating(loop) && !AsrEngine.repeating(Array(1...24)), "повтор куска трижды подряд замечен") && ok

        ok = check(AsrEngine.join("Сегодня я", "диктую") == "Сегодня я диктую" && AsrEngine.join("Я думаю", ", что") == "Я думаю, что"
                   && AsrEngine.join("", "Привет") == "Привет", "склейка подсказки и продолжения") && ok

        print("— WER")
        ok = check(wer("Привет, мир!", "привет мир") == 0 && abs(wer("раз два три четыре", "раз два три") - 0.25) < 0.001, "метрика ошибки слов") && ok
        print("  Русский голос macOS: \(AudioFile.russianVoice ?? "не найден")")
        return ok
    }

    // MARK: - Настоящая модель

    static let reference = "Сегодня я диктую текст на русском языке. Утилита распознаёт речь и печатает её прямо в поле ввода. Это очень удобно для ежедневной работы."

    static func asr(gpu: Bool) async -> Bool {
        var ok = true
        let model = AsrModel.fast
        print("Модель: \(model.title) (\(model.repo)), \(gpu ? "Metal" : "процессор")")

        // 1. Загрузка с проверкой SHA-256.
        for file in [model.projector, model.model] {
            let started = Date()
            let fetcher = FileFetcher(url: AsrModel.url(repo: model.repo, path: file.path), destination: model.folder.appendingPathComponent(file.path),
                                      expectedSize: file.size, sha256: file.sha256) { _ in }
            do {
                try await fetcher.run()
                ok = check(true, "\(file.path): \(Format.bytes(file.size)), SHA-256 совпал, \(Format.duration(Date().timeIntervalSince(started)))") && ok
            } catch {
                return check(false, "\(file.path): \(error.localizedDescription)")
            }
        }

        // 2. Модель в память.
        let loadStart = Date()
        do {
            try await AsrEngine.shared.load(model, gpu: gpu)
        } catch {
            return check(false, "загрузка: \(error.localizedDescription)")
        }
        let rate = AsrEngine.shared.sampleRate
        ok = check(true, "модель в памяти с прогревом за \(Format.duration(Date().timeIntervalSince(loadStart))), звук \(rate) Гц") && ok

        // 3. Русская речь синтезатором macOS.
        guard let voice = AudioFile.russianVoice else { return check(false, "в системе нет русского голоса для синтеза речи") }
        let dir = FileManager.default.temporaryDirectory
        let speech = dir.appendingPathComponent("utter-speech.wav")
        let spoken = reference.replacingOccurrences(of: ". ", with: ". [[slnc 1100]] ")
        guard AudioFile.speak(spoken, to: speech), let samples = AudioFile.load(speech, sampleRate: Double(rate)) else {
            return check(false, "синтез речи голосом \(voice) не удался")
        }
        let seconds = Double(samples.count) / Double(rate)
        print("  речь: голос \(voice), \(String(format: "%.1f", seconds)) с")

        // 4. Распознавание целиком.
        let t0 = Date()
        guard let whole = try? await AsrEngine.shared.transcribe(samples) else { return check(false, "распознавание целиком") }
        let rtf = Date().timeIntervalSince(t0) / seconds
        let w1 = wer(reference, whole)
        print("  → «\(whole)»")
        ok = check(w1 <= 0.15, "распознавание целиком: ошибка слов \(Int(w1 * 100))%, скорость ×\(String(format: "%.1f", 1 / max(rtf, 0.001))) от реального времени") && ok
        ok = check(whole.contains(".") && whole.first?.isUppercase == true, "пунктуация и заглавные буквы на месте") && ok

        // 5. Продолжение с префиксом (основа потоковости).
        let half = Array(samples.prefix(samples.count / 3))
        if let first = try? await AsrEngine.shared.transcribe(half) {
            let words = Committer.words(first)
            let prefix = words.dropLast().joined(separator: " ")
            if let continuation = try? await AsrEngine.shared.transcribe(samples, prefix: prefix) {
                let combined = AsrEngine.join(prefix, continuation)
                print("  префикс «\(prefix)» + продолжение «\(continuation)»")
                ok = check(wer(reference, combined) <= 0.2, "модель продолжает утверждённый текст без повторов: ошибка \(Int(wer(reference, combined) * 100))%") && ok
            }
        }

        // 6. Потоковая диктовка в реальном времени: как с микрофона, кусками по 100 мс.
        let output = Box("")
        let commits = Box<[(Double, Int)]>([])
        let finished = Box(false)
        let fed = Box(0.0)
        var options = Dictation.Options()
        options.sampleRate = rate
        options.pauseMs = 700
        let dictation = Dictation(options: options) { event in
            switch event {
            case .type(let text):
                output.value += text
                commits.value.append((fed.value, Committer.words(text).count))
            case .backspace(let n):
                output.value.removeLast(min(n, output.value.count))
            case .finished:
                finished.value = true
            default: break
            }
        }
        let streamStart = Date()
        let step = rate / 10
        var i = 0
        while i < samples.count {
            dictation.feed(Array(samples[i..<min(samples.count, i + step)]))
            i += step
            fed.value = Double(i) / Double(rate)
            try? await Task.sleep(for: .milliseconds(100))
        }
        dictation.feed([Float](repeating: 0, count: rate))
        dictation.stop()
        while !finished.value && Date().timeIntervalSince(streamStart) < seconds + 30 { try? await Task.sleep(for: .milliseconds(50)) }
        let tailWait = Date().timeIntervalSince(streamStart) - seconds - 0.1
        print("  → напечатано: «\(output.value)»")
        let w2 = wer(reference, output.value)
        ok = check(finished.value, "диктовка завершилась") && ok
        ok = check(w2 <= 0.1, "потоковая диктовка: ошибка слов \(Int(w2 * 100))%, окончательным распознаванием поправлено \(dictation.corrected) символов") && ok
        ok = check(output.value.filter { $0 == "." }.count >= 3, "все три предложения закончены точкой") && ok
        let early = commits.value.filter { $0.0 < seconds - 0.2 }.reduce(0) { $0 + $1.1 }
        let total = Committer.words(output.value).count
        ok = check(early > 0, "слова печатаются во время речи: \(early) из \(total) появились, пока звучала речь (проходов: \(dictation.passCount))") && ok
        print("  после конца речи текст дописан за \(String(format: "%.1f", max(0, tailWait))) с")
        if let first = commits.value.first { print("  первое слово напечатано на \(String(format: "%.1f", first.0)) с звука") }

        // 7. Голосовая команда отдельной фразой.
        let commandFile = dir.appendingPathComponent("utter-command.wav")
        if AudioFile.speak("Первая строка. [[slnc 1100]] Новая строка. [[slnc 1100]] Вторая строка.", to: commandFile),
           let commandSamples = AudioFile.load(commandFile, sampleRate: Double(rate)) {
            let out = Box("")
            let done = Box(false)
            let d2 = Dictation(options: options) { event in
                if case .type(let t) = event { out.value += t }
                if case .finished = event { done.value = true }
            }
            for k in stride(from: 0, to: commandSamples.count, by: step) {
                d2.feed(Array(commandSamples[k..<min(commandSamples.count, k + step)]))
                try? await Task.sleep(for: .milliseconds(20))
            }
            d2.feed([Float](repeating: 0, count: rate))
            d2.stop()
            for _ in 0..<600 where !done.value { try? await Task.sleep(for: .milliseconds(50)) }
            print("  → «\(out.value.replacingOccurrences(of: "\n", with: "⏎"))»")
            ok = check(out.value.contains("\n") && !out.value.lowercased().contains("новая строка"), "«новая строка» — перенос, а не текст") && ok
        }

        await AsrEngine.shared.unload()
        return ok
    }
}
