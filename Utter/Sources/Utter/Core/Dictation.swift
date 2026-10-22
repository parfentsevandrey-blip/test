@preconcurrency import AVFoundation
import Foundation
@preconcurrency import Speech

/// Конвейер диктовки: микрофон → детектор речи → распознавание фразы каждые полсекунды →
/// утверждение слов (LocalAgreement) → печать. Всё состояние — на одной последовательной очереди.
nonisolated final class Dictation: @unchecked Sendable {
    enum Engine: String, Sendable, Codable {
        /// Qwen3-ASR в llama.cpp — основной.
        case qwen
        /// Встроенное распознавание macOS (на устройстве) — пока модель не скачана.
        case apple
    }

    enum Event: Sendable {
        case level(Float)
        case speaking(Bool)
        /// Хвост уже напечатанного и неустоявшиеся слова (для плавающей панели).
        case live(typed: String, tail: String)
        case type(String)
        case backspace(Int)
        /// Фраза дописана целиком (для вставки готовыми фразами).
        case utteranceDone
        case finished(String)
        case error(String)
    }

    /// Начало подсказки модели со словарём пользователя.
    static let contextLead = "Термины и имена: "

    struct Options: Sendable {
        var engine: Engine = .qwen
        var pauseMs = 700
        /// Подсказки модели: имена, термины.
        var context = ""
        var commands = true
        var maxUtterance = 25.0
        var sampleRate = 16000
    }

    private let queue = DispatchQueue(label: "app.utter.pipeline", qos: .userInteractive)
    private let emit: @Sendable (Event) -> Void
    private let options: Options
    private var vad: VoiceDetector
    private var committer = Committer()
    private var committerID = -1
    private var joiner = TextJoiner()
    private var utteranceID = 0
    private var finals: [(id: Int, audio: [Float])] = []
    /// Предыдущая фраза — левый контекст: её звук идёт перед звуком новой, её текст — началом ответа модели.
    /// Без него модель не узнаёт первые слова фразы: в CI ошибка на стыках фраз была 13%, с ним — 4%,
    /// как при распознавании всей записи целиком (подсказка одним текстом не помогала).
    private var previous: (audio: [Float], text: String)?
    private let contextLimit = 8.0
    /// `UTTER_TRACE=1` — печатать каждый проход распознавания (отладка, демо в CI).
    static let trace = ProcessInfo.processInfo.environment["UTTER_TRACE"] != nil
    private var passRunning = false
    private var lastPassStart = Date.distantPast
    private var passInterval = 0.45
    private var stopping = false
    private var finished = false
    private var lastLevelEmit = Date.distantPast
    private var capture: AudioCapture?

    // Встроенный распознаватель macOS: сеанс на каждую фразу.
    private var apple: [Int: AppleSession] = [:]
    private var appleHypothesis: [Int: String] = [:]

    /// Статистика для самопроверки: сколько слов напечатано до конца фразы (потоково).
    private(set) var streamedWords = 0
    private(set) var passCount = 0
    /// Сколько символов поправлено окончательным распознаванием фраз.
    private(set) var corrected = 0

    init(options: Options, emit: @escaping @Sendable (Event) -> Void) {
        self.options = options
        self.emit = emit
        self.vad = VoiceDetector(sampleRate: options.sampleRate, pauseMs: options.pauseMs)
    }

    // MARK: - Запуск и остановка

    func start() throws {
        let capture = AudioCapture(sampleRate: Double(options.sampleRate))
        try capture.start { [weak self] samples in self?.feed(samples) }
        self.capture = capture
    }

    /// Звук из другого источника (самопроверка, демо).
    func feed(_ samples: [Float]) {
        queue.async { self.process(samples) }
    }

    /// Остановить: текущая фраза дораспознаётся и допечатается, затем придёт `.finished`.
    func stop() {
        capture?.stop()
        capture = nil
        queue.async {
            guard !self.stopping else { return }
            self.stopping = true
            if self.vad.speaking { self.endUtterance(self.vad.finish(), paused: false) }
            self.schedule()
            self.finishIfDone()
        }
    }

    // MARK: - Звук

    private func process(_ samples: [Float]) {
        guard !stopping else { return }
        let events = vad.process(samples)
        let now = Date()
        if now.timeIntervalSince(lastLevelEmit) > 1.0 / 30 {
            lastLevelEmit = now
            emit(.level(vad.level))
        }
        for e in events {
            switch e {
            case .start:
                utteranceID += 1
                emit(.speaking(true))
                if options.engine == .apple { startApple(utteranceID, preroll: vad.utterance) }
            case .end(let audio):
                emit(.speaking(false))
                endUtterance(audio, paused: true)
            }
        }
        if vad.speaking {
            if options.engine == .apple { apple[utteranceID]?.append(samples) }
            // Очень длинная фраза без пауз — режем, иначе растёт задержка.
            if vad.utteranceSeconds(sampleRate: options.sampleRate) > options.maxUtterance { endUtterance(vad.finish(), paused: false) }
        }
        schedule()
    }

    /// `paused` — фраза закончилась паузой (её длина входит в звук), а не остановкой.
    private func endUtterance(_ audio: [Float], paused: Bool) {
        if options.engine == .apple {
            apple[utteranceID]?.end()
            // Ждём окончательный результат macOS не дольше 2 секунд.
            let id = utteranceID
            queue.asyncAfter(deadline: .now() + 2) { self.finalizeApple(id, text: self.appleHypothesis[id] ?? "") }
            return
        }
        // Обрывок короче четверти секунды речи (щелчок, кашель) — не фраза. В звуке ещё 0,3 с до речи и пауза.
        let minimum = (paused ? Double(options.pauseMs) / 1000 : 0) + 0.3 + 0.25
        guard Double(audio.count) / Double(options.sampleRate) >= minimum else { return }
        finals.append((utteranceID, audio))
    }

    // MARK: - Qwen3-ASR: проходы распознавания

    private func schedule() {
        guard options.engine == .qwen, !passRunning else { return }
        if let job = finals.first {
            if committerID != job.id { committer.reset(); committerID = job.id }
            // Окончательно — вся фраза заново (после предыдущей), без подсказки напечатанного: так точнее всего
            // и со всеми знаками препинания.
            run(audio: (previous?.audio ?? []) + job.audio, prefix: previous?.text ?? "", typed: "", own: job.audio, id: job.id, final: true)
            return
        }
        guard vad.speaking, !stopping,
              vad.utteranceSeconds(sampleRate: options.sampleRate) >= 0.7,
              Date().timeIntervalSince(lastPassStart) >= passInterval else { return }
        if committerID != utteranceID { committer.reset(); committerID = utteranceID }
        let typed = committer.committedText
        run(audio: (previous?.audio ?? []) + vad.utterance, prefix: AsrEngine.join(previous?.text ?? "", typed), typed: typed,
            own: [], id: utteranceID, final: false)
    }

    /// `prefix` — начало ответа модели (текст предыдущей фразы и уже напечатанное из этой), `typed` — его часть
    /// из этой фразы, `own` — звук самой фразы (станет контекстом для следующей).
    private func run(audio: [Float], prefix: String, typed: String, own: [Float], id: Int, final: Bool) {
        passRunning = true
        let started = Date()
        lastPassStart = started
        let context = options.context
        Task.detached(priority: .userInitiated) {
            let result: Result<String, Error>
            do { result = .success(try await AsrEngine.shared.transcribe(audio, prefix: prefix, context: context)) }
            catch { result = .failure(error) }
            self.queue.async {
                self.passRunning = false
                self.passCount += 1
                let elapsed = Date().timeIntervalSince(started)
                // Темп подстраивается под скорость Mac: проход не чаще, чем успевает модель.
                self.passInterval = max(0.35, min(1.5, elapsed * 1.3))
                if Self.trace, case .success(let c) = result {
                    print("[\(final ? "итог" : "проход") \(id), \(String(format: "%.2f", elapsed)) с, звук \(String(format: "%.1f", Double(audio.count) / Double(self.options.sampleRate))) с] «\(prefix)» → «\(c)»")
                }
                switch result {
                case .success(let continuation):
                    let hypothesis = AsrEngine.join(typed, typed.isEmpty ? continuation.trimmingCharacters(in: .whitespaces) : continuation)
                    if final { self.finalize(id: id, text: hypothesis, audio: own) } else { self.partial(id: id, text: hypothesis) }
                case .failure(let error):
                    if final { self.finals.removeAll { $0.id == id } }
                    self.emit(.error(error.localizedDescription))
                }
                self.schedule()
                self.finishIfDone()
            }
        }
    }

    // MARK: - Гипотезы → печать

    private func partial(id: Int, text: String) {
        guard id == committerID, finals.isEmpty else { return }
        let words = committer.update(text)
        if !words.isEmpty {
            streamedWords += words.count
            emit(.type(joiner.append(words)))
        }
        let all = Committer.words(text)
        let tail = all.count > committer.committed.count ? all[committer.committed.count...].joined(separator: " ") : ""
        emit(.live(typed: String(joiner.typed.suffix(90)), tail: tail))
    }

    private func finalize(id: Int, text: String, audio: [Float]? = nil) {
        finals.removeAll { $0.id == id }
        defer {
            committer.reset()
            committerID = -1
            emit(.utteranceDone)
            emit(.live(typed: String(joiner.typed.suffix(90)), tail: ""))
        }
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        // Только знаки препинания — модель «услышала» шум.
        guard clean.unicodeScalars.contains(where: { CharacterSet.alphanumerics.contains($0) }) else {
            joiner.closeUtterance()
            if audio != nil { previous = nil }
            return
        }
        if let audio {
            previous = Double(audio.count) / Double(options.sampleRate) <= contextLimit ? (audio, clean) : nil
        }
        if options.commands, let command = VoiceCommand.match(clean) {
            // Команда — не текст: следующей фразе контекстом не служит (с ней модель пишет продолжение «новой строки»).
            if audio != nil { previous = nil }
            // Если начало команды уже успело напечататься — стираем его.
            let (_, undo) = joiner.settle([])
            let (insert, erase) = joiner.apply(command)
            if undo + erase > 0 { emit(.backspace(undo + erase)) }
            if !insert.isEmpty { emit(.type(insert)) }
            return
        }
        // Напечатанное по ходу речи приводится к окончательному варианту: обычно это лишь знаки
        // препинания на стыках слов, иногда — слово, которое по обрывку звука было не разобрать.
        let (insert, erase) = joiner.settle(Committer.words(clean))
        if erase > 0 {
            corrected += erase
            emit(.backspace(erase))
        }
        if !insert.isEmpty { emit(.type(insert)) }
        joiner.closeUtterance()
    }

    private func finishIfDone() {
        guard stopping, !finished, !passRunning, finals.isEmpty, apple.isEmpty else { return }
        finished = true
        emit(.finished(joiner.typed))
    }

    // MARK: - Встроенное распознавание macOS

    private func startApple(_ id: Int, preroll: [Float]) {
        guard let session = AppleSession(sampleRate: Double(options.sampleRate), context: options.context, onResult: { [weak self] text, isFinal in
            guard let self else { return }
            self.queue.async {
                // Ошибка распознавания приходит пустым финалом — оставляем последнюю гипотезу.
                let text = text.isEmpty ? (self.appleHypothesis[id] ?? "") : text
                self.appleHypothesis[id] = text
                if isFinal { self.finalizeApple(id, text: text) } else {
                    if self.committerID != id { self.committer.reset(); self.committerID = id }
                    self.partial(id: id, text: text)
                }
            }
        }) else {
            emit(.error("Распознавание macOS недоступно для русского языка на этом Mac."))
            return
        }
        apple[id] = session
        session.append(preroll)
    }

    private func finalizeApple(_ id: Int, text: String) {
        guard apple.removeValue(forKey: id) != nil else { return }
        appleHypothesis[id] = nil
        if committerID != id { committer.reset(); committerID = id }
        finalize(id: id, text: text)
        finishIfDone()
    }
}

/// Один сеанс встроенного распознавания macOS (SFSpeechRecognizer, русский, на устройстве).
nonisolated final class AppleSession: @unchecked Sendable {
    private let request = SFSpeechAudioBufferRecognitionRequest()
    private var task: SFSpeechRecognitionTask?
    private let format: AVAudioFormat

    static var recognizer: SFSpeechRecognizer? { SFSpeechRecognizer(locale: Locale(identifier: "ru-RU")) }

    static var available: Bool {
        SFSpeechRecognizer.authorizationStatus() == .authorized && recognizer?.isAvailable == true
    }

    static func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            SFSpeechRecognizer.requestAuthorization { cont.resume(returning: $0 == .authorized) }
        }
    }

    init?(sampleRate: Double, context: String, onResult: @escaping @Sendable (String, Bool) -> Void) {
        guard let recognizer = Self.recognizer, recognizer.isAvailable,
              let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false) else { return nil }
        self.format = format
        request.shouldReportPartialResults = true
        request.addsPunctuation = true
        request.taskHint = .dictation
        if recognizer.supportsOnDeviceRecognition { request.requiresOnDeviceRecognition = true }
        let terms = context.replacingOccurrences(of: Dictation.contextLead, with: "").trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .split(whereSeparator: { $0 == "," || $0 == "\n" }).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if !terms.isEmpty { request.contextualStrings = terms }
        task = recognizer.recognitionTask(with: request) { result, error in
            if let result {
                onResult(result.bestTranscription.formattedString, result.isFinal)
            } else if error != nil {
                onResult("", true)
            }
        }
    }

    func append(_ samples: [Float]) {
        guard !samples.isEmpty, let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)) else { return }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { src in
            buffer.floatChannelData?[0].update(from: src.baseAddress!, count: samples.count)
        }
        request.append(buffer)
    }

    func end() { request.endAudio() }
}
