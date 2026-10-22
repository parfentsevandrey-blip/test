import AppKit
import Observation
import ServiceManagement
import SwiftUI

/// Что надиктовано — для истории в меню.
struct HistoryEntry: Codable, Identifiable, Hashable {
    var id = UUID()
    var date = Date()
    var text: String
    var app: String?
}

/// Как запускать диктовку горячей клавишей.
enum Trigger: String, CaseIterable, Codable, Identifiable {
    /// Короткое нажатие — вкл/выкл, удержание — пока держу.
    case auto
    case toggle
    case hold

    var id: String { rawValue }

    var title: String {
        switch self {
        case .auto: "Умный режим"
        case .toggle: "Нажать — начать, нажать — закончить"
        case .hold: "Пока держу клавиши"
        }
    }

    var hint: String {
        switch self {
        case .auto: "Короткое нажатие включает и выключает диктовку, удержание — диктовка, пока держите."
        case .toggle: "Одно нажатие начинает диктовку, следующее — заканчивает."
        case .hold: "Говорите, пока держите сочетание; отпустили — текст допечатается."
        }
    }
}

/// Буфер для вставки готовыми фразами (живёт на очереди конвейера).
nonisolated final class InsertionSink: @unchecked Sendable {
    private let lock = NSLock()
    private var buffer = ""
    private let mode: Typer.Mode
    private(set) var typedAnything = false

    init(mode: Typer.Mode) { self.mode = mode }

    func type(_ text: String) {
        lock.withLock { typedAnything = true }
        if mode == .typing { Typer.shared.type(text) } else { lock.withLock { buffer += text } }
    }

    func backspace(_ n: Int) {
        if mode == .typing { Typer.shared.backspace(n); return }
        let leftover: Int = lock.withLock {
            let drop = min(n, buffer.count)
            buffer.removeLast(drop)
            return n - drop
        }
        Typer.shared.backspace(leftover)
    }

    func flush() {
        guard mode == .paste else { return }
        let text: String = lock.withLock { defer { buffer = "" }; return buffer }
        Typer.shared.type(text, mode: .paste)
    }
}

@Observable
final class AppModel {
    static let shared = AppModel()

    enum Phase: Equatable { case idle, listening, finishing }

    enum ModelState: Equatable {
        case missing
        case downloading(Double)
        case loading
        case ready
        case failed(String)
    }

    // MARK: Диктовка
    var phase: Phase = .idle
    var level: Float = 0
    var speaking = false
    var liveTyped = ""
    var liveTail = ""
    var startedAt: Date?
    var notice: String?
    var engineInUse: Dictation.Engine?
    var lastText = ""

    // MARK: Модель
    var modelState: ModelState = .missing
    var downloadSpeed: Double = 0

    // MARK: Разрешения
    var micGranted = AudioCapture.permission == .authorized
    var accessibilityGranted = Typer.trusted
    var speechGranted = AppleSession.available

    // MARK: Настройки
    var asrModelID = UserDefaults.standard.string(forKey: "asrModel") ?? AsrModel.recommended.id {
        didSet { UserDefaults.standard.set(asrModelID, forKey: "asrModel") }
    }
    var shortcut: Shortcut = {
        if let data = UserDefaults.standard.data(forKey: "shortcut"), let s = try? JSONDecoder().decode(Shortcut.self, from: data) { return s }
        return .default
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(shortcut) { UserDefaults.standard.set(data, forKey: "shortcut") }
            registerHotkey()
        }
    }
    var trigger = Trigger(rawValue: UserDefaults.standard.string(forKey: "trigger") ?? "") ?? .auto {
        didSet { UserDefaults.standard.set(trigger.rawValue, forKey: "trigger") }
    }
    var rightOption = UserDefaults.standard.object(forKey: "rightOption") as? Bool ?? true {
        didSet {
            UserDefaults.standard.set(rightOption, forKey: "rightOption")
            HotkeyCenter.shared.setRightOption(rightOption)
        }
    }
    var pauseMs = UserDefaults.standard.object(forKey: "pauseMs") as? Int ?? 700 {
        didSet { UserDefaults.standard.set(pauseMs, forKey: "pauseMs") }
    }
    var dictionary = UserDefaults.standard.string(forKey: "dictionary") ?? "" {
        didSet { UserDefaults.standard.set(dictionary, forKey: "dictionary") }
    }
    var commands = UserDefaults.standard.object(forKey: "commands") as? Bool ?? true {
        didSet { UserDefaults.standard.set(commands, forKey: "commands") }
    }
    var insertion = Typer.Mode(rawValue: UserDefaults.standard.string(forKey: "insertion") ?? "") ?? .typing {
        didSet { UserDefaults.standard.set(insertion.rawValue, forKey: "insertion") }
    }
    var sounds = UserDefaults.standard.object(forKey: "sounds") as? Bool ?? true {
        didSet { UserDefaults.standard.set(sounds, forKey: "sounds") }
    }
    var keepHistory = UserDefaults.standard.object(forKey: "keepHistory") as? Bool ?? true {
        didSet {
            UserDefaults.standard.set(keepHistory, forKey: "keepHistory")
            if !keepHistory { history = [] }
        }
    }
    var history: [HistoryEntry] = {
        guard let data = UserDefaults.standard.data(forKey: "history") else { return [] }
        return (try? JSONDecoder().decode([HistoryEntry].self, from: data)) ?? []
    }() {
        didSet { if let data = try? JSONEncoder().encode(history) { UserDefaults.standard.set(data, forKey: "history") } }
    }
    var onboardingDone = UserDefaults.standard.bool(forKey: "onboardingDone") {
        didSet { UserDefaults.standard.set(onboardingDone, forKey: "onboardingDone") }
    }
    var hotkeyConflict = false
    /// Идёт запись нового сочетания в настройках — основное сочетание временно отключено.
    var recordingShortcut = false {
        didSet { if recordingShortcut { HotkeyCenter.shared.unregister() } else { registerHotkey() } }
    }

    var asrModel: AsrModel { AsrModel.named(asrModelID) ?? AsrModel.recommended }

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            try? newValue ? SMAppService.mainApp.register() : SMAppService.mainApp.unregister()
        }
    }

    var setupComplete: Bool { micGranted && accessibilityGranted && (modelState == .ready || speechGranted) }

    let hud = HUDController()
    private var dictation: Dictation?
    private var sink: InsertionSink?
    private var pressedAt: Date?
    private var started = false
    private var downloadTask: Task<Void, Never>?
    private var speedSample: (date: Date, bytes: Int64) = (Date(), 0)

    // MARK: - Запуск

    func start() {
        guard !started else { return }
        started = true
        let center = HotkeyCenter.shared
        center.onPress = { [weak self] in self?.hotkeyPressed() }
        center.onRelease = { [weak self] in self?.hotkeyReleased() }
        center.onEscape = { [weak self] in self?.finish() }
        center.onHoldStart = { [weak self] in self?.begin() }
        center.onHoldEnd = { [weak self] in self?.finish() }
        registerHotkey()
        center.setRightOption(rightOption)
        refreshPermissions()
        // Разрешения выдаются в Системных настройках — следим, пока не все на месте.
        Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                refreshPermissions()
            }
        }
        if asrModel.installed { loadModel() } else if onboardingDone || DemoUI.isOn { downloadModel() }
    }

    func registerHotkey() {
        hotkeyConflict = !HotkeyCenter.shared.register(shortcut)
    }

    func refreshPermissions() {
        micGranted = AudioCapture.permission == .authorized
        accessibilityGranted = Typer.trusted
        speechGranted = AppleSession.available
        if rightOption && accessibilityGranted { HotkeyCenter.shared.setRightOption(true) }
    }

    func requestMicrophone() {
        Task {
            micGranted = await AudioCapture.requestPermission()
            if !micGranted { openPrivacy("Privacy_Microphone") }
        }
    }

    func requestAccessibility() {
        Typer.requestTrust()
        openPrivacy("Privacy_Accessibility")
    }

    func requestSpeech() {
        Task { speechGranted = await AppleSession.requestPermission() && AppleSession.available }
    }

    func openPrivacy(_ anchor: String) {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?\(anchor)") { NSWorkspace.shared.open(url) }
    }

    // MARK: - Модель

    func downloadModel(_ model: AsrModel? = nil) {
        let model = model ?? asrModel
        if model.id != asrModelID { asrModelID = model.id }
        if model.installed { loadModel(); return }
        guard downloadTask == nil else { return }
        modelState = .downloading(0)
        downloadTask = Task {
            defer { downloadTask = nil }
            let activity = ProcessInfo.processInfo.beginActivity(options: [.userInitiated], reason: "Загрузка модели распознавания")
            defer { ProcessInfo.processInfo.endActivity(activity) }
            var done: Int64 = 0
            speedSample = (Date(), 0)
            for file in [model.projector, model.model] {
                var attempt = 0
                while true {
                    let base = done
                    let (stream, cont) = AsyncStream<Int64>.makeStream(bufferingPolicy: .bufferingNewest(1))
                    let watcher = Task {
                        for await value in stream {
                            let total = base + value
                            modelState = .downloading(Double(total) / Double(model.size))
                            let now = Date()
                            let dt = now.timeIntervalSince(speedSample.date)
                            if dt > 0.7 {
                                downloadSpeed = Double(total - speedSample.bytes) / dt
                                speedSample = (now, total)
                            }
                        }
                    }
                    let fetcher = FileFetcher(url: AsrModel.url(repo: model.repo, path: file.path),
                                              destination: model.folder.appendingPathComponent(file.path),
                                              expectedSize: file.size, sha256: file.sha256) { cont.yield($0) }
                    do {
                        try await fetcher.run()
                        cont.finish()
                        await watcher.value
                        break
                    } catch {
                        cont.finish()
                        watcher.cancel()
                        attempt += 1
                        if attempt > 5 || Task.isCancelled {
                            modelState = .failed(error.localizedDescription)
                            return
                        }
                        try? await Task.sleep(for: .seconds(min(30, 2 << attempt)))
                    }
                }
                done += file.size
            }
            loadModel()
        }
    }

    func loadModel() {
        let model = asrModel
        guard model.installed else { modelState = .missing; return }
        if AsrEngine.shared.loadedID == model.id { modelState = .ready; return }
        modelState = .loading
        Task {
            do {
                try await AsrEngine.shared.load(model, gpu: !Hardware.virtualized)
                modelState = .ready
            } catch {
                // Видеоядро не справилось — на процессоре.
                do {
                    try await AsrEngine.shared.load(model, gpu: false)
                    modelState = .ready
                } catch {
                    modelState = .failed(error.localizedDescription)
                }
            }
        }
    }

    func deleteModel(_ model: AsrModel) {
        Task {
            if AsrEngine.shared.loadedID == model.id {
                await AsrEngine.shared.unload()
                modelState = .missing
            }
            try? FileManager.default.removeItem(at: model.folder)
        }
    }

    // MARK: - Горячая клавиша

    private func hotkeyPressed() {
        switch phase {
        case .idle:
            pressedAt = Date()
            begin()
        case .listening:
            pressedAt = nil
            finish()
        case .finishing:
            break
        }
    }

    private func hotkeyReleased() {
        guard phase == .listening, let pressedAt else { return }
        let held = Date().timeIntervalSince(pressedAt)
        self.pressedAt = nil
        switch trigger {
        case .hold: finish()
        case .auto: if held >= 0.45 { finish() }
        case .toggle: break
        }
    }

    func toggle() {
        if phase == .idle { begin() } else if phase == .listening { finish() }
    }

    // MARK: - Диктовка

    /// `feeding` — звук не с микрофона, а из файла (демо): подаётся в реальном времени.
    func begin(feeding samples: [Float]? = nil) {
        guard phase == .idle else { return }
        refreshPermissions()
        if samples == nil, AudioCapture.permission == .notDetermined {
            requestMicrophone()
            showOnboarding()
            return
        }
        guard micGranted || samples != nil else {
            flash("Нет доступа к микрофону — разрешите его в настройках")
            showOnboarding()
            return
        }
        let engine: Dictation.Engine
        if modelState == .ready {
            engine = .qwen
        } else if speechGranted {
            engine = .apple
        } else {
            if case .downloading(let p) = modelState {
                flash("Модель распознавания скачивается — \(Int(p * 100))%. Или разрешите распознавание macOS в меню.")
            } else {
                flash("Модель распознавания не готова — откройте меню Utter")
            }
            return
        }
        if samples == nil, Typer.secureInput {
            flash("Сейчас активно поле пароля — диктовка туда не печатает")
            return
        }
        let canType = accessibilityGranted || Typer.shared.sink != nil
        notice = canType ? nil : "Нет доступа к вводу — текст будет скопирован в буфер обмена"
        let sink = InsertionSink(mode: canType ? insertion : .paste)
        self.sink = sink
        var options = Dictation.Options()
        options.engine = engine
        options.pauseMs = pauseMs
        options.commands = commands
        options.sampleRate = engine == .qwen ? AsrEngine.shared.sampleRate : 16000
        let terms = dictionary.split(whereSeparator: { $0 == "," || $0 == "\n" }).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if !terms.isEmpty { options.context = Dictation.contextLead + terms.joined(separator: ", ") + "." }

        let accessibility = canType
        let dictation = Dictation(options: options) { [weak self] event in
            // Печать — сразу и по порядку (очередь печати последовательная), интерфейс — на главном потоке.
            switch event {
            case .type(let text): if accessibility { sink.type(text) }
            case .backspace(let n): if accessibility { sink.backspace(n) }
            case .utteranceDone: if accessibility { sink.flush() }
            default: break
            }
            DispatchQueue.main.async { MainActor.assumeIsolated { self?.handle(event) } }
        }
        if let samples {
            // Демо: файл подаётся кусками по 100 мс в реальном времени, в конце — тишина и стоп.
            let rate = options.sampleRate
            Task.detached {
                let step = rate / 10
                var i = 0
                while i < samples.count {
                    dictation.feed(Array(samples[i..<min(samples.count, i + step)]))
                    i += step
                    try? await Task.sleep(for: .milliseconds(100))
                }
                dictation.feed([Float](repeating: 0, count: rate))
                try? await Task.sleep(for: .seconds(1))
                await MainActor.run { AppModel.shared.finish() }
            }
        } else {
            do {
                try dictation.start()
            } catch {
                flash(error.localizedDescription)
                return
            }
        }
        self.dictation = dictation
        engineInUse = engine
        phase = .listening
        startedAt = Date()
        liveTyped = ""
        liveTail = ""
        lastText = ""
        HotkeyCenter.shared.captureEscape(true)
        if sounds { NSSound(named: "Tink")?.play() }
        hud.show(self)
    }

    func finish() {
        guard phase == .listening else { return }
        phase = .finishing
        HotkeyCenter.shared.captureEscape(false)
        dictation?.stop()
        if sounds { NSSound(named: "Pop")?.play() }
    }

    private func handle(_ event: Dictation.Event) {
        switch event {
        case .level(let l): level = l
        case .speaking(let s): speaking = s
        case .live(let typed, let tail):
            liveTyped = typed
            liveTail = tail
        case .type, .backspace, .utteranceDone: break
        case .error(let message): notice = message
        case .finished(let text): complete(text)
        }
    }

    private func complete(_ text: String) {
        dictation = nil
        sink = nil
        phase = .idle
        level = 0
        speaking = false
        lastText = text
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            if !accessibilityGranted && Typer.shared.sink == nil {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(trimmed, forType: .string)
                notice = "Текст скопирован — вставьте его ⌘V"
            }
            if keepHistory {
                history.insert(HistoryEntry(text: trimmed, app: Typer.targetApp), at: 0)
                if history.count > 50 { history.removeLast(history.count - 50) }
            }
        }
        hud.hide(after: trimmed.isEmpty ? 0.3 : 1.2)
    }

    /// Короткое сообщение в плавающей панели без записи.
    func flash(_ text: String) {
        notice = text
        hud.show(self)
        hud.hide(after: 3)
    }

    func copy(_ entry: HistoryEntry) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(entry.text, forType: .string)
    }

    // MARK: - Окна

    var onboardingWindow: NSWindow?

    func showOnboarding() {
        if onboardingWindow == nil {
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 640, height: 720),
                                  styleMask: [.titled, .closable, .fullSizeContentView], backing: .buffered, defer: false)
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.isReleasedWhenClosed = false
            window.contentView = NSHostingView(rootView: OnboardingView().environment(self))
            window.center()
            onboardingWindow = window
        }
        NSApp.activate()
        onboardingWindow?.makeKeyAndOrderFront(nil)
    }

    func closeOnboarding() {
        onboardingDone = true
        onboardingWindow?.close()
        if !asrModel.installed { downloadModel() }
    }
}
