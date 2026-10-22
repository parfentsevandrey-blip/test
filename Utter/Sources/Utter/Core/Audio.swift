@preconcurrency import AVFoundation
import Foundation

nonisolated enum AudioError: LocalizedError {
    case noInput
    case denied

    var errorDescription: String? {
        switch self {
        case .noInput: "Не найден микрофон. Подключите микрофон или выберите его в Системных настройках → Звук."
        case .denied: "Нет доступа к микрофону. Разрешите его в Системных настройках → Конфиденциальность → Микрофон."
        }
    }
}

/// Микрофон → 16 кГц моно Float32 (формат моделей распознавания). Звук никуда не уходит с Mac.
nonisolated final class AudioCapture: @unchecked Sendable {
    let sampleRate: Double
    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var converter: AVAudioConverter?
    private var running = false
    private var configObserver: NSObjectProtocol?
    private var onSamples: (@Sendable ([Float]) -> Void)?

    init(sampleRate: Double = 16000) {
        self.sampleRate = sampleRate
    }

    static var permission: AVAuthorizationStatus { AVCaptureDevice.authorizationStatus(for: .audio) }

    static func requestPermission() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .audio)
    }

    /// Название текущего микрофона — для меню.
    static var deviceName: String {
        AVCaptureDevice.default(for: .audio)?.localizedName ?? "Микрофон по умолчанию"
    }

    func start(_ onSamples: @escaping @Sendable ([Float]) -> Void) throws {
        guard Self.permission != .denied && Self.permission != .restricted else { throw AudioError.denied }
        lock.withLock { self.onSamples = onSamples }
        try startEngine()
        // Сменили микрофон (подключили AirPods) — переподключаемся на лету.
        configObserver = NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: nil) { [weak self] _ in
            guard let self, self.lock.withLock({ self.running }) else { return }
            self.engine.inputNode.removeTap(onBus: 0)
            try? self.startEngine()
        }
    }

    private func startEngine() throws {
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0,
              let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false),
              let converter = AVAudioConverter(from: format, to: target) else { throw AudioError.noInput }
        lock.withLock {
            self.converter = converter
            self.running = true
        }
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.process(buffer, target: target)
        }
        engine.prepare()
        try engine.start()
    }

    func stop() {
        lock.withLock {
            running = false
            onSamples = nil
        }
        if let configObserver { NotificationCenter.default.removeObserver(configObserver) }
        configObserver = nil
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
    }

    private final class Once: @unchecked Sendable { var done = false }

    private func process(_ buffer: AVAudioPCMBuffer, target: AVAudioFormat) {
        let (converter, callback) = lock.withLock { (self.converter, self.onSamples) }
        guard let converter, let callback else { return }
        let ratio = target.sampleRate / buffer.format.sampleRate
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: AVAudioFrameCount(Double(buffer.frameLength) * ratio + 32)) else { return }
        let once = Once()
        nonisolated(unsafe) let input = buffer
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if once.done {
                status.pointee = .noDataNow
                return nil
            }
            once.done = true
            status.pointee = .haveData
            return input
        }
        guard error == nil, let channel = out.floatChannelData?[0], out.frameLength > 0 else { return }
        callback(Array(UnsafeBufferPointer(start: channel, count: Int(out.frameLength))))
    }
}

/// Детектор речи по энергии с плавающим порогом шума.
///
/// Кадр 20 мс. Шумовой фон медленно подстраивается под тишину в комнате; речь — когда кадр громче фона
/// на 10 дБ (и не тише абсолютного порога). Начало — 3 громких кадра подряд, конец — пауза заданной длины.
/// Перед началом сохраняется 300 мс звука, чтобы не обрезать первый звук слова.
nonisolated struct VoiceDetector {
    enum Event: Equatable {
        /// Началась речь.
        case start
        /// Пауза после речи — фраза закончилась; в событии весь её звук.
        case end([Float])
    }

    let frame: Int
    var pauseMs: Int
    private(set) var speaking = false
    /// Звук текущей фразы (с 300 мс до начала речи).
    private(set) var utterance: [Float] = []
    private(set) var noiseDB: Float = -60
    private(set) var levelDB: Float = -90
    private var pending: [Float] = []
    private var history: [Float] = []
    private var loudRun = 0
    private var quietRun = 0
    private let prerollFrames = 15

    init(sampleRate: Int = 16000, pauseMs: Int = 700) {
        frame = sampleRate / 50
        self.pauseMs = pauseMs
    }

    /// Порог «речь/не речь» сейчас.
    var threshold: Float { max(noiseDB + 10, -48) }

    /// Громкость последнего кадра 0…1 — для анимации.
    var level: Float { max(0, min(1, (levelDB + 60) / 50)) }

    mutating func process(_ samples: [Float]) -> [Event] {
        pending += samples
        var events: [Event] = []
        while pending.count >= frame {
            let chunk = Array(pending.prefix(frame))
            pending.removeFirst(frame)
            if let e = processFrame(chunk) { events.append(e) }
        }
        return events
    }

    private mutating func processFrame(_ chunk: [Float]) -> Event? {
        var sum: Float = 0
        for s in chunk { sum += s * s }
        let rms = sqrt(sum / Float(chunk.count))
        let db = 20 * log10(max(rms, 1e-7))
        levelDB = db

        // Фон: быстро опускается к тишине, медленно поднимается (чтобы речь его не «съела»).
        if db < noiseDB { noiseDB = noiseDB * 0.7 + db * 0.3 } else if !speaking { noiseDB = noiseDB * 0.995 + db * 0.005 }

        history.append(contentsOf: chunk)
        if history.count > frame * prerollFrames { history.removeFirst(history.count - frame * prerollFrames) }

        let loud = db > threshold
        if !speaking {
            loudRun = loud ? loudRun + 1 : 0
            if loudRun >= 3 {
                speaking = true
                quietRun = 0
                utterance = history
                return .start
            }
        } else {
            utterance += chunk
            quietRun = loud ? 0 : quietRun + 1
            if quietRun * 20 >= pauseMs {
                return .end(finish())
            }
        }
        return nil
    }

    /// Закончить фразу сейчас (остановили диктовку или фраза слишком длинная).
    mutating func finish() -> [Float] {
        defer {
            utterance = []
            speaking = false
            loudRun = 0
            quietRun = 0
        }
        return utterance
    }

    /// Длительность текущей фразы, секунды.
    func utteranceSeconds(sampleRate: Int = 16000) -> Double { Double(utterance.count) / Double(sampleRate) }
}

/// Звуковой файл → моно Float32 нужной частоты (самопроверки, демо).
nonisolated enum AudioFile {
    static func load(_ url: URL, sampleRate: Double = 16000) -> [Float]? {
        guard let file = try? AVAudioFile(forReading: url),
              let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false),
              let input = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)),
              (try? file.read(into: input)) != nil,
              let converter = AVAudioConverter(from: file.processingFormat, to: target) else { return nil }
        let capacity = AVAudioFrameCount(Double(input.frameLength) * sampleRate / file.processingFormat.sampleRate + 1024)
        guard let output = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return nil }
        final class Once: @unchecked Sendable { var done = false }
        let once = Once()
        nonisolated(unsafe) let source = input
        var error: NSError?
        converter.convert(to: output, error: &error) { _, status in
            if once.done { status.pointee = .endOfStream; return nil }
            once.done = true
            status.pointee = .haveData
            return source
        }
        guard error == nil, let channel = output.floatChannelData?[0] else { return nil }
        return Array(UnsafeBufferPointer(start: channel, count: Int(output.frameLength)))
    }

    /// Русская речь встроенным синтезатором macOS (`say`). `[[slnc 900]]` в тексте — пауза 0,9 с.
    static func speak(_ text: String, to url: URL) -> Bool {
        guard let voice = russianVoice else { return false }
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/say")
        p.arguments = ["-v", voice, "-o", url.path, "--file-format=WAVE", "--data-format=LEF32@16000", text]
        do { try p.run() } catch { return false }
        p.waitUntilExit()
        return p.terminationStatus == 0 && FileManager.default.fileExists(atPath: url.path)
    }

    static var russianVoice: String? {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/say")
        p.arguments = ["-v", "?"]
        let pipe = Pipe()
        p.standardOutput = pipe
        do { try p.run() } catch { return nil }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        let lines = String(decoding: data, as: UTF8.self).split(separator: "\n")
        let russian = lines.filter { $0.contains("ru_RU") }
        let preferred = russian.first { $0.hasPrefix("Milena") } ?? russian.first
        guard let line = preferred, let range = line.range(of: "ru_RU") else { return nil }
        return line[..<range.lowerBound].trimmingCharacters(in: .whitespaces)
    }
}
