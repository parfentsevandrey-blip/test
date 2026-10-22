@preconcurrency import AVFoundation
import Foundation

nonisolated enum AudioError: LocalizedError {
    case noInput
    case denied
    case busy(device: String, code: Int)

    var errorDescription: String? {
        switch self {
        case .noInput: "Не найден микрофон. Подключите микрофон или выберите его в Системных настройках → Звук."
        case .denied: "Нет доступа к микрофону. Разрешите его в Системных настройках → Конфиденциальность → Микрофон."
        case .busy(let device, let code):
            "Микрофон «\(device)» сейчас недоступен (\(AudioError.fourCC(code))). Так бывает, когда AirPods переключаются в режим гарнитуры, "
                + "микрофон занят другой программой или выбран микрофон iPhone. Попробуйте ещё раз или выберите другой вход в Настройках → Звук."
        }
    }

    /// Коды CoreAudio — четыре буквы: 2003329396 → «what».
    static func fourCC(_ code: Int) -> String {
        let v = UInt32(truncatingIfNeeded: code)
        let bytes = [24, 16, 8, 0].map { UInt8((v >> UInt32($0)) & 0xFF) }
        guard bytes.allSatisfy({ (32...126).contains($0) }) else { return "код \(code)" }
        return "«" + String(decoding: bytes, as: UTF8.self) + "»"
    }
}

/// Микрофон → 16 кГц моно Float32 (формат моделей распознавания). Звук никуда не уходит с Mac.
///
/// Основной путь — AVAudioEngine. Он капризен: CoreAudio отвечает «what» (kAudioHardwareUnspecifiedError),
/// если формат устройства меняется в момент запуска (AirPods переходят в режим гарнитуры, микрофон
/// переключили, его держит другая программа). Поэтому: несколько попыток со свежим движком, формат берётся
/// из самих данных, а не заранее, и запасной путь — AVCaptureSession, который такие переключения переносит.
nonisolated final class AudioCapture: @unchecked Sendable {
    let sampleRate: Double
    private var engine = AVAudioEngine()
    private let lock = NSLock()
    private var converter: AVAudioConverter?
    private var converterFormat: AVAudioFormat?
    private var running = false
    private var configObserver: NSObjectProtocol?
    private var onSamples: (@Sendable ([Float]) -> Void)?
    private var fallback: CaptureSessionInput?
    /// Каким путём идёт звук — для журнала и самопроверки.
    private(set) var path = ""

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
        guard AVCaptureDevice.default(for: .audio) != nil else { throw AudioError.noInput }
        lock.withLock {
            self.onSamples = onSamples
            self.running = true
        }
        var lastError: Error = AudioError.noInput
        // Попытки со свежим движком: после переключения AirPods формат устаканивается за доли секунды.
        for attempt in 0..<3 {
            if attempt > 0 { usleep(UInt32(150_000 * attempt)) }
            do {
                try startEngine()
                path = "AVAudioEngine"
                observeChanges()
                return
            } catch {
                lastError = error
                resetEngine()
            }
        }
        // Запасной путь.
        do {
            let capture = CaptureSessionInput(sampleRate: sampleRate) { [weak self] samples in self?.deliver(samples) }
            try capture.start()
            fallback = capture
            path = "AVCaptureSession"
            return
        } catch {
            lock.withLock { running = false; self.onSamples = nil }
            throw Self.explain(lastError)
        }
    }

    static func explain(_ error: Error) -> Error {
        if let e = error as? AudioError { return e }
        let ns = error as NSError
        return AudioError.busy(device: deviceName, code: ns.code)
    }

    private func startEngine() throws {
        let input = engine.inputNode
        let format = input.inputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { throw AudioError.noInput }
        // Формат отвода — nil: движок сам берёт формат шины; преобразователь создаётся по первому буферу.
        input.installTap(onBus: 0, bufferSize: 1024, format: nil) { [weak self] buffer, _ in
            self?.process(buffer)
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            throw error
        }
    }

    private func resetEngine() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        engine.reset()
        engine = AVAudioEngine()
        lock.withLock {
            converter = nil
            converterFormat = nil
        }
    }

    /// Сменили микрофон (подключили AirPods) — переподключаемся на лету, со свежим движком.
    private func observeChanges() {
        if let configObserver { NotificationCenter.default.removeObserver(configObserver) }
        configObserver = NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: nil) { [weak self] _ in
            guard let self, self.lock.withLock({ self.running }) else { return }
            DispatchQueue.global().asyncAfter(deadline: .now() + 0.2) {
                guard self.lock.withLock({ self.running }) else { return }
                self.resetEngine()
                for attempt in 0..<3 {
                    if attempt > 0 { usleep(200_000) }
                    if (try? self.startEngine()) != nil { self.observeChanges(); return }
                    self.resetEngine()
                }
            }
        }
    }

    func stop() {
        lock.withLock {
            running = false
            onSamples = nil
        }
        if let configObserver { NotificationCenter.default.removeObserver(configObserver) }
        configObserver = nil
        fallback?.stop()
        fallback = nil
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
    }

    private func deliver(_ samples: [Float]) {
        guard let callback = lock.withLock({ onSamples }), !samples.isEmpty else { return }
        callback(samples)
    }

    private final class Once: @unchecked Sendable { var done = false }

    private func process(_ buffer: AVAudioPCMBuffer) {
        guard let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false) else { return }
        let converter: AVAudioConverter? = lock.withLock {
            if converterFormat != buffer.format {
                self.converter = AVAudioConverter(from: buffer.format, to: target)
                converterFormat = buffer.format
            }
            return self.converter
        }
        guard let converter else { return }
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
        deliver(Array(UnsafeBufferPointer(start: channel, count: Int(out.frameLength))))
    }
}

/// Запасной захват через AVCaptureSession: macOS сама отдаёт 16 кГц моно Float32.
nonisolated final class CaptureSessionInput: NSObject, AVCaptureAudioDataOutputSampleBufferDelegate, @unchecked Sendable {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "app.utter.capture", qos: .userInteractive)
    private let sampleRate: Double
    private let onSamples: @Sendable ([Float]) -> Void

    init(sampleRate: Double, onSamples: @escaping @Sendable ([Float]) -> Void) {
        self.sampleRate = sampleRate
        self.onSamples = onSamples
    }

    func start() throws {
        guard let device = AVCaptureDevice.default(for: .audio) else { throw AudioError.noInput }
        let input = try AVCaptureDeviceInput(device: device)
        let output = AVCaptureAudioDataOutput()
        output.audioSettings = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: sampleRate,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 32,
            AVLinearPCMIsFloatKey: true,
            AVLinearPCMIsNonInterleaved: true,
            AVLinearPCMIsBigEndianKey: false,
        ]
        output.setSampleBufferDelegate(self, queue: queue)
        session.beginConfiguration()
        guard session.canAddInput(input), session.canAddOutput(output) else {
            session.commitConfiguration()
            throw AudioError.noInput
        }
        session.addInput(input)
        session.addOutput(output)
        session.commitConfiguration()
        session.startRunning()
        guard session.isRunning else { throw AudioError.busy(device: device.localizedName, code: 0) }
    }

    func stop() {
        session.stopRunning()
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        var samples: [Float] = []
        try? sampleBuffer.withAudioBufferList { list, _ in
            guard let buffer = list.first, let data = buffer.mData else { return }
            let count = Int(buffer.mDataByteSize) / MemoryLayout<Float>.size
            samples = Array(UnsafeBufferPointer(start: data.assumingMemoryBound(to: Float.self), count: count))
        }
        if !samples.isEmpty { onSamples(samples) }
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
