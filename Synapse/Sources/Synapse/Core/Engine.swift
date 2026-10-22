import CLlama
import Foundation

/// Сведения о загруженной модели — из её собственных метаданных GGUF.
nonisolated struct ModelInfo: Sendable, Equatable {
    var name: String
    var description: String
    var architecture: String
    var parameters: UInt64
    var sizeBytes: UInt64
    var trainContext: Int
    var context: Int
    var layers: Int
    var format: ChatFormat
    var gpu: Bool
    /// Подключён проектор зрения — модель понимает изображения.
    var vision: Bool
    /// Память контекста (KV-кэш) сжата до 8 бит.
    var compressedKV = false
}

/// Как держать модель в памяти. Меняется в Настройках и применяется при следующей загрузке модели.
nonisolated struct EngineSettings: Sendable, Codable, Hashable {
    /// Контекст в токенах; 0 — подобрать под память Mac и модель.
    var context = 0
    /// Сжимать память контекста до 8 бит: вдвое меньше памяти на контекст, качество почти не меняется.
    /// `nil` — сжимать сами, когда модель еле помещается.
    var compressKV: Bool?
    /// Считать на видеоядре (Metal). Выключать стоит, только если видеоядро сбоит.
    var gpu = true

    static let contextChoices = [0, 2048, 4096, 8192, 16384, 32768, 65536, 131072]

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        context = try c.decodeIfPresent(Int.self, forKey: .context) ?? 0
        compressKV = try c.decodeIfPresent(Bool.self, forKey: .compressKV)
        gpu = try c.decodeIfPresent(Bool.self, forKey: .gpu) ?? true
    }
}

nonisolated struct GenerationSettings: Sendable, Codable, Hashable {
    var temperature: Double = 0.7
    var topP: Double = 0.95
    var topK: Int = 40
    var minP: Double = 0.05
    var repeatPenalty: Double = 1.0
    var maxTokens: Int = 8192
    var thinking = true
    var seed: UInt32 = 0xFFFF_FFFF // случайный

    static let precise = GenerationSettings(temperature: 0, maxTokens: 256, thinking: false)
}

nonisolated struct GenerationStats: Sendable, Codable, Hashable {
    var promptTokens = 0
    var reusedTokens = 0
    var promptSeconds = 0.0
    var tokens = 0
    var seconds = 0.0
    var stoppedByLimit = false

    var tokensPerSecond: Double { seconds > 0 ? Double(tokens) / seconds : 0 }
    var promptPerSecond: Double { promptSeconds > 0 ? Double(promptTokens - reusedTokens) / promptSeconds : 0 }
}

nonisolated enum GenerationEvent: Sendable {
    /// Промпт собран. Если `startsInReasoning` — ответ начинается внутри блока размышлений.
    case started(startsInReasoning: Bool)
    /// Модель читает промпт: сколько токенов из скольки.
    case prompt(done: Int, total: Int)
    /// Новый кусок текста (вместе со служебными маркерами — их разбирает ReplyParser).
    case text(String)
    case finished(GenerationStats)
}

nonisolated enum EngineError: LocalizedError {
    case notLoaded
    case loadFailed(String)
    case contextFailed(String)
    case decodeFailed(Int32)
    case promptTooLong(Int, Int)
    case noVision
    case badImage(String)

    var errorDescription: String? {
        switch self {
        case .notLoaded: "Модель не загружена."
        case .loadFailed(let why): "Не удалось открыть модель. \(why)"
        case .contextFailed(let why): "Не хватило памяти для контекста модели. \(why)"
        case .decodeFailed(let code): "Движок остановился с ошибкой \(code)."
        case .promptTooLong(let n, let max): "Сообщение слишком длинное: \(n) токенов при контексте \(max)."
        case .noVision: "Эта модель не видит изображения. Выберите модель с пометкой «Видит картинки» или добавьте ей зрение в «Моих моделях»."
        case .badImage(let name): "Не удалось прочитать изображение \(name)."
        }
    }
}

/// Последние сообщения llama.cpp — для понятных ошибок и диагностики.
nonisolated final class EngineLog: @unchecked Sendable {
    static let shared = EngineLog()
    private let lock = NSLock()
    private var lines: [String] = []
    var echo = ProcessInfo.processInfo.environment["SYNAPSE_VERBOSE"] != nil

    func append(_ level: Int, _ text: String) {
        let line = text.trimmingCharacters(in: .newlines)
        guard !line.isEmpty else { return }
        lock.withLock {
            lines.append(level >= 3 ? "⚠️ " + line : line)
            if lines.count > 400 { lines.removeFirst(lines.count - 400) }
        }
        if echo || level >= 4 { FileHandle.standardError.write(Data(("[llama] " + line + "\n").utf8)) }
    }

    var lastProblem: String {
        lock.withLock { lines.last(where: { $0.hasPrefix("⚠️") }).map { String($0.dropFirst(2)) } ?? "" }
    }

    var recent: [String] { lock.withLock { lines } }
}

nonisolated private func engineLogCallback(_ level: ggml_log_level, _ text: UnsafePointer<CChar>?, _ user: UnsafeMutableRawPointer?) {
    guard let text else { return }
    EngineLog.shared.append(Int(level.rawValue), String(cString: text))
}

/// Флажок остановки генерации. Движок проверяет его между токенами и внутри обработки промпта.
nonisolated final class CancelToken: @unchecked Sendable {
    private let lock = NSLock()
    private var flag = false
    var isCancelled: Bool { lock.withLock { flag } }
    func cancel() { lock.withLock { flag = true } }
}

/// Обёртка над C API llama.cpp. Всё состояние движка живёт на одной последовательной очереди:
/// контекст llama.cpp не потокобезопасен.
nonisolated final class LlamaEngine: @unchecked Sendable {
    static let shared = LlamaEngine()

    private let queue = DispatchQueue(label: "app.synapse.engine", qos: .userInitiated)
    private let lock = NSLock()

    // Только на `queue`.
    private var model: OpaquePointer?
    private var ctx: OpaquePointer?
    private var vocab: OpaquePointer?
    private var evaluated: [llama_token] = []
    private var format: ChatFormat = .chatml
    private var nBatch = 2048
    /// Мультимодальный контекст (проектор зрения), если он есть у модели.
    private var mtmd: OpaquePointer?
    /// После запроса с картинками кэш не совпадает со списком токенов — следующий запрос начинается с чистого листа.
    private var kvDirty = false

    // Под `lock`.
    private var _loaded: (path: String, info: ModelInfo)?
    private var _token: CancelToken?

    var loaded: (path: String, info: ModelInfo)? { lock.withLock { _loaded } }

    private init() {
        llama_log_set(engineLogCallback, nil)
        mtmd_helper_log_set(engineLogCallback, nil)
        llama_backend_init()
    }

    static var systemInfo: String { String(cString: llama_print_system_info()) }

    // MARK: - Загрузка

    /// Загружает модель и создаёт контекст. `gpu: false` — всё на процессоре (запасной путь).
    /// `projector` — файл mmproj: с ним модель видит изображения.
    /// `compressKV` — память контекста в 8 битах (если движок не сможет, останется 16 бит).
    func load(path: String, projector: String? = nil, context: Int, gpu: Bool, compressKV: Bool = false,
              progress: @escaping @Sendable (Double) -> Void) async throws -> ModelInfo {
        try await withCheckedThrowingContinuation { cont in
            queue.async {
                do { cont.resume(returning: try self.loadSync(path: path, projector: projector, context: context, gpu: gpu,
                                                              compressKV: compressKV, progress: progress)) }
                catch { cont.resume(throwing: error) }
            }
        }
    }

    /// Освободить модель перед выходом из программы. Иначе при выходе срабатывает проверка llama.cpp:
    /// у видеоядра остались занятые буферы (ggml_metal_rsets_free → GGML_ASSERT → abort, «программа
    /// неожиданно завершилась»). Если ядро занято дольше `timeout`, выходим сразу, без деструкторов.
    func shutdown(timeout: TimeInterval = 3) {
        stop()
        let done = DispatchSemaphore(value: 0)
        queue.async {
            self.freeAll()
            llama_backend_free()
            done.signal()
        }
        if done.wait(timeout: .now() + timeout) == .timedOut { _exit(0) }
    }

    func unload() async {
        await withCheckedContinuation { cont in
            queue.async {
                self.freeAll()
                cont.resume()
            }
        }
    }

    private final class ProgressBox {
        let report: @Sendable (Double) -> Void
        init(_ report: @escaping @Sendable (Double) -> Void) { self.report = report }
    }

    private func loadSync(path: String, projector: String?, context: Int, gpu: Bool, compressKV: Bool,
                          progress: @escaping @Sendable (Double) -> Void) throws -> ModelInfo {
        freeAll()

        var mparams = llama_model_default_params()
        mparams.n_gpu_layers = gpu ? 999 : 0
        let box = Unmanaged.passRetained(ProgressBox(progress))
        defer { box.release() }
        mparams.progress_callback = { value, user in
            if let user { Unmanaged<ProgressBox>.fromOpaque(user).takeUnretainedValue().report(Double(value)) }
            return true
        }
        mparams.progress_callback_user_data = box.toOpaque()

        guard let model = llama_model_load_from_file(path, mparams) else {
            throw EngineError.loadFailed(EngineLog.shared.lastProblem)
        }

        let train = Int(llama_model_n_ctx_train(model))
        let nCtx = max(512, train > 0 ? min(context, train) : context)
        let cores = Hardware.performanceCores

        var cparams = llama_context_default_params()
        cparams.n_ctx = UInt32(nCtx)
        cparams.n_batch = UInt32(min(nBatch, nCtx))
        cparams.n_ubatch = 512
        cparams.n_threads = Int32(cores)
        cparams.n_threads_batch = Int32(max(cores, ProcessInfo.processInfo.activeProcessorCount - 2))
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO
        cparams.no_perf = true
        cparams.offload_kqv = gpu
        cparams.abort_callback = { data in
            guard let data else { return false }
            return Unmanaged<LlamaEngine>.fromOpaque(data).takeUnretainedValue().currentCancelled
        }
        cparams.abort_callback_data = Unmanaged.passUnretained(self).toOpaque()

        var compressed = false
        var created: OpaquePointer?
        if compressKV {
            // 8-битный KV-кэш требует flash attention; если движок его не включит, контекст не создастся —
            // тогда обычный 16-битный.
            var q = cparams
            q.type_k = GGML_TYPE_Q8_0
            q.type_v = GGML_TYPE_Q8_0
            created = llama_init_from_model(model, q)
            compressed = created != nil
        }
        if created == nil { created = llama_init_from_model(model, cparams) }
        guard let ctx = created else {
            llama_model_free(model)
            throw EngineError.contextFailed(EngineLog.shared.lastProblem)
        }

        self.model = model
        self.ctx = ctx
        self.vocab = llama_model_get_vocab(model)
        self.evaluated = []
        let template = llama_model_chat_template(model, nil).map { String(cString: $0) }
        self.format = ChatFormat.detect(template: template)

        // Зрение: проектор переводит картинку в «токены», которые понимает языковая модель.
        if let projector, FileManager.default.fileExists(atPath: projector) {
            var mp = mtmd_context_params_default()
            mp.use_gpu = gpu
            mp.n_threads = Int32(cores)
            mp.print_timings = false
            mp.warmup = false
            mp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO
            mtmd = mtmd_init_from_file(projector, model, mp)
            if mtmd == nil { EngineLog.shared.append(4, "Проектор зрения не загрузился: \(EngineLog.shared.lastProblem)") }
            if let m = mtmd, !mtmd_support_vision(m) { mtmd_free(m); mtmd = nil }
        }

        let info = ModelInfo(
            name: meta(model, "general.name") ?? URL(fileURLWithPath: path).deletingPathExtension().lastPathComponent,
            description: describe(model),
            architecture: meta(model, "general.architecture") ?? "",
            parameters: llama_model_n_params(model),
            sizeBytes: llama_model_size(model),
            trainContext: train,
            context: Int(llama_n_ctx(ctx)),
            layers: Int(llama_model_n_layer(model)),
            format: format,
            gpu: gpu,
            vision: mtmd != nil,
            compressedKV: compressed
        )
        lock.withLock { _loaded = (path, info) }
        return info
    }

    private func freeAll() {
        if let mtmd { mtmd_free(mtmd) }
        mtmd = nil
        kvDirty = false
        if let ctx { llama_free(ctx) }
        if let model { llama_model_free(model) }
        ctx = nil
        model = nil
        vocab = nil
        evaluated = []
        lock.withLock { _loaded = nil }
    }

    private func meta(_ model: OpaquePointer, _ key: String) -> String? {
        var buf = [CChar](repeating: 0, count: 512)
        let n = llama_model_meta_val_str(model, key, &buf, buf.count)
        guard n > 0 else { return nil }
        return String(decoding: buf.prefix(Int(min(n, Int32(buf.count - 1)))).map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    private func describe(_ model: OpaquePointer) -> String {
        var buf = [CChar](repeating: 0, count: 256)
        let n = llama_model_desc(model, &buf, buf.count)
        return n > 0 ? String(decoding: buf.prefix(Int(n)).map { UInt8(bitPattern: $0) }, as: UTF8.self) : ""
    }

    // MARK: - Генерация

    fileprivate var currentCancelled: Bool { lock.withLock { _token?.isCancelled ?? false } }

    /// Останавливает текущую генерацию.
    func stop() { lock.withLock { _token?.cancel() } }

    /// Поток событий генерации. Отмена задачи-потребителя останавливает движок.
    func generate(_ turns: [ChatTurn], settings: GenerationSettings) -> AsyncThrowingStream<GenerationEvent, Error> {
        let token = CancelToken()
        return AsyncThrowingStream { cont in
            cont.onTermination = { reason in
                if case .cancelled = reason { token.cancel() }
            }
            queue.async {
                self.lock.withLock { self._token = token }
                defer { self.lock.withLock { if self._token === token { self._token = nil } } }
                do {
                    try self.generateSync(turns, settings: settings, token: token) { cont.yield($0) }
                    cont.finish()
                } catch {
                    cont.finish(throwing: error)
                }
            }
        }
    }

    private func generateSync(_ turns: [ChatTurn], settings: GenerationSettings, token: CancelToken,
                              emit: (GenerationEvent) -> Void) throws {
        guard let ctx, let vocab, model != nil else { throw EngineError.notLoaded }
        let nCtx = Int(llama_n_ctx(ctx))
        let reserve = min(max(256, settings.maxTokens), nCtx / 3)

        if turns.contains(where: { !$0.images.isEmpty }) {
            guard let mtmd else { throw EngineError.noVision }
            try generateMultimodal(turns, settings: settings, token: token, ctx: ctx, vocab: vocab, mtmd: mtmd, emit: emit)
            return
        }
        if kvDirty {
            llama_memory_clear(llama_get_memory(ctx), true)
            evaluated = []
            kvDirty = false
        }

        // Длинный диалог: забываем самые старые реплики (системную оставляем), пока не влезет.
        var turns = turns
        var rendered = render(turns, thinking: settings.thinking)
        var tokens = tokenize(rendered.text)
        while tokens.count > nCtx - reserve, let drop = turns.firstIndex(where: { $0.role != .system }), turns.count > 2 {
            turns.remove(at: drop)
            rendered = render(turns, thinking: settings.thinking)
            tokens = tokenize(rendered.text)
        }
        guard tokens.count < nCtx - 64 else { throw EngineError.promptTooLong(tokens.count, nCtx) }

        var stats = GenerationStats()
        stats.promptTokens = tokens.count
        emit(.started(startsInReasoning: rendered.startsInReasoning))

        // Общее начало с тем, что уже лежит в кэше, повторно не считаем.
        let mem = llama_get_memory(ctx)
        var common = zip(evaluated, tokens).prefix(while: { $0 == $1 }).count
        if common >= tokens.count { common = tokens.count - 1 } // нужен хотя бы один токен для логитов
        if common < evaluated.count {
            if !llama_memory_seq_rm(mem, 0, llama_pos(common), -1) {
                // Рекуррентные и гибридные модели не умеют частичный откат — начинаем заново.
                llama_memory_clear(mem, true)
                common = 0
            }
        }
        evaluated = Array(tokens.prefix(common))
        stats.reusedTokens = common

        let promptStart = Date()
        var i = common
        while i < tokens.count {
            let n = min(nBatch, tokens.count - i)
            var chunk = Array(tokens[i..<(i + n)])
            let rc = chunk.withUnsafeMutableBufferPointer { llama_decode(ctx, llama_batch_get_one($0.baseAddress, Int32(n))) }
            if rc != 0 {
                resync(mem, tokens)
                if token.isCancelled || rc == 2 { emit(.finished(stats)); return }
                throw EngineError.decodeFailed(rc)
            }
            evaluated += chunk
            i += n
            emit(.prompt(done: i, total: tokens.count))
        }
        stats.promptSeconds = Date().timeIntervalSince(promptStart)

        let sampler = makeSampler(settings, vocab: vocab)
        defer { llama_sampler_free(sampler) }

        var utf8 = UTF8Accumulator()
        let genStart = Date()
        while stats.tokens < settings.maxTokens {
            if token.isCancelled { break }
            if evaluated.count >= nCtx - 1 { stats.stoppedByLimit = true; break }
            let next = llama_sampler_sample(sampler, ctx, -1)
            if llama_vocab_is_eog(vocab, next) { break }
            let text = utf8.push(piece(next, vocab: vocab))
            if !text.isEmpty { emit(.text(text)) }
            stats.tokens += 1

            var one = [next]
            let rc = one.withUnsafeMutableBufferPointer { llama_decode(ctx, llama_batch_get_one($0.baseAddress, 1)) }
            if rc != 0 {
                resync(mem, evaluated)
                if token.isCancelled || rc == 2 { break }
                throw EngineError.decodeFailed(rc)
            }
            evaluated.append(next)
        }
        if stats.tokens >= settings.maxTokens { stats.stoppedByLimit = true }
        let tail = utf8.flush()
        if !tail.isEmpty { emit(.text(tail)) }
        stats.seconds = Date().timeIntervalSince(genStart)
        emit(.finished(stats))
    }

    /// Запрос с изображениями: текст и картинки превращаются в куски (mtmd_tokenize), картинки кодируются
    /// проектором, всё вместе декодируется (mtmd_helper_eval_chunks берёт на себя позиции M-RoPE и
    /// некаузальное внимание у Gemma). Дальше генерация как обычно, но с явными позициями.
    private func generateMultimodal(_ turns: [ChatTurn], settings: GenerationSettings, token: CancelToken,
                                    ctx: OpaquePointer, vocab: OpaquePointer, mtmd: OpaquePointer,
                                    emit: (GenerationEvent) -> Void) throws {
        let nCtx = Int(llama_n_ctx(ctx))
        let reserve = min(max(256, settings.maxTokens), nCtx / 3)
        let marker = String(cString: mtmd_default_marker())

        // Картинки декодируем сами (ImageIO) и отдаём проектору как RGB.
        var bitmaps: [OpaquePointer?] = []
        defer { bitmaps.forEach { if let b = $0 { mtmd_bitmap_free(b) } } }
        func prepare(_ turns: [ChatTurn]) throws -> [ChatTurn] {
            bitmaps.forEach { if let b = $0 { mtmd_bitmap_free(b) } }
            bitmaps = []
            return try turns.map { t in
                guard !t.images.isEmpty else { return t }
                for path in t.images {
                    guard let image = ImageInput.rgb(path: path) else { throw EngineError.badImage((path as NSString).lastPathComponent) }
                    let bitmap = image.pixels.withUnsafeBufferPointer { mtmd_bitmap_init(UInt32(image.width), UInt32(image.height), $0.baseAddress) }
                    bitmaps.append(bitmap)
                }
                var t = t
                t.content = String(repeating: marker + "\n", count: t.images.count) + t.content
                return t
            }
        }

        var turns = turns
        var chunks: OpaquePointer?
        defer { if let chunks { mtmd_input_chunks_free(chunks) } }
        var rendered = (text: "", startsInReasoning: false)
        while true {
            let prepared = try prepare(turns)
            rendered = render(prepared, thinking: settings.thinking)
            if let old = chunks { mtmd_input_chunks_free(old) }
            let fresh = mtmd_input_chunks_init()
            chunks = fresh
            let rc = rendered.text.withCString { cstr -> Int32 in
                var input = mtmd_input_text(text: cstr, text_len: strlen(cstr), add_special: true, parse_special: true)
                return bitmaps.withUnsafeBufferPointer { mtmd_tokenize(mtmd, fresh, &input, $0.baseAddress, $0.count) }
            }
            guard rc == 0 else { throw EngineError.badImage("код \(rc)") }
            let total = Int(mtmd_helper_get_n_tokens(fresh))
            // Слишком длинно — забываем старые реплики (последний вопрос с картинкой оставляем).
            if total > nCtx - reserve, let drop = turns.firstIndex(where: { $0.role != .system }), drop < turns.count - 1 {
                turns.remove(at: drop)
                continue
            }
            guard total < nCtx - 64 else { throw EngineError.promptTooLong(total, nCtx) }
            break
        }

        var stats = GenerationStats()
        stats.promptTokens = Int(mtmd_helper_get_n_tokens(chunks))
        emit(.started(startsInReasoning: rendered.startsInReasoning))

        // Кэш начинаем заново: позиции картинок не совпадают с числом токенов.
        let mem = llama_get_memory(ctx)
        llama_memory_clear(mem, true)
        evaluated = []
        kvDirty = true

        let promptStart = Date()
        var position: llama_pos = 0
        let rc = mtmd_helper_eval_chunks(mtmd, ctx, chunks, 0, 0, Int32(nBatch), true, &position)
        if rc != 0 {
            if token.isCancelled { emit(.finished(stats)); return }
            throw EngineError.decodeFailed(rc)
        }
        stats.promptSeconds = Date().timeIntervalSince(promptStart)
        emit(.prompt(done: stats.promptTokens, total: stats.promptTokens))

        let sampler = makeSampler(settings, vocab: vocab)
        defer { llama_sampler_free(sampler) }
        var batch = llama_batch_init(1, 0, 1)
        defer { llama_batch_free(batch) }

        var utf8 = UTF8Accumulator()
        let genStart = Date()
        while stats.tokens < settings.maxTokens {
            if token.isCancelled { break }
            if Int(position) >= nCtx - 1 { stats.stoppedByLimit = true; break }
            let next = llama_sampler_sample(sampler, ctx, -1)
            if llama_vocab_is_eog(vocab, next) { break }
            let text = utf8.push(piece(next, vocab: vocab))
            if !text.isEmpty { emit(.text(text)) }
            stats.tokens += 1

            batch.n_tokens = 1
            batch.token[0] = next
            batch.pos[0] = position
            batch.n_seq_id[0] = 1
            batch.seq_id[0]![0] = 0
            batch.logits[0] = 1
            let rc = llama_decode(ctx, batch)
            if rc != 0 {
                if token.isCancelled || rc == 2 { break }
                throw EngineError.decodeFailed(rc)
            }
            position += 1
        }
        if stats.tokens >= settings.maxTokens { stats.stoppedByLimit = true }
        let tail = utf8.flush()
        if !tail.isEmpty { emit(.text(tail)) }
        stats.seconds = Date().timeIntervalSince(genStart)
        emit(.finished(stats))
    }

    /// После прерванного decode в памяти остаётся то, что успело посчитаться, — синхронизируем список токенов.
    private func resync(_ mem: llama_memory_t?, _ tokens: [llama_token]) {
        let maxPos = Int(llama_memory_seq_pos_max(mem, 0))
        evaluated = maxPos >= 0 ? Array(tokens.prefix(min(maxPos + 1, tokens.count))) : []
    }

    private func render(_ turns: [ChatTurn], thinking: Bool) -> (text: String, startsInReasoning: Bool) {
        format.render(turns, thinking: thinking) { template, turns in
            Self.applyBuiltin(template, turns)
        }
    }

    /// llama_chat_apply_template: шаблон распознаётся по характерным маркерам.
    static func applyBuiltin(_ template: String, _ turns: [ChatTurn]) -> String? {
        let roles = turns.map { strdup($0.role.rawValue) }
        let contents = turns.map { strdup($0.content) }
        defer {
            roles.forEach { free($0) }
            contents.forEach { free($0) }
        }
        var messages = zip(roles, contents).map { llama_chat_message(role: UnsafePointer($0), content: UnsafePointer($1)) }
        let total = turns.reduce(0) { $0 + $1.content.utf8.count } * 2 + 4096
        var buf = [CChar](repeating: 0, count: total)
        var n = llama_chat_apply_template(template, &messages, messages.count, true, &buf, Int32(buf.count))
        if n > Int32(buf.count) {
            buf = [CChar](repeating: 0, count: Int(n) + 1)
            n = llama_chat_apply_template(template, &messages, messages.count, true, &buf, Int32(buf.count))
        }
        guard n > 0 else { return nil }
        return String(decoding: buf.prefix(Int(n)).map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    private func tokenize(_ text: String) -> [llama_token] {
        guard let vocab else { return [] }
        let length = Int32(text.utf8.count)
        var tokens = [llama_token](repeating: 0, count: Int(length) + 16)
        var n = llama_tokenize(vocab, text, length, &tokens, Int32(tokens.count), true, true)
        if n < 0 {
            tokens = [llama_token](repeating: 0, count: Int(-n))
            n = llama_tokenize(vocab, text, length, &tokens, Int32(tokens.count), true, true)
        }
        return Array(tokens.prefix(Int(max(0, n))))
    }

    /// Счётчик токенов для интерфейса и API (на очереди движка).
    func countTokens(_ text: String) async -> Int {
        await withCheckedContinuation { cont in
            queue.async { cont.resume(returning: self.tokenize(text).count) }
        }
    }

    private func piece(_ token: llama_token, vocab: OpaquePointer) -> [UInt8] {
        var buf = [CChar](repeating: 0, count: 64)
        var n = llama_token_to_piece(vocab, token, &buf, Int32(buf.count), 0, true)
        if n < 0 {
            buf = [CChar](repeating: 0, count: Int(-n))
            n = llama_token_to_piece(vocab, token, &buf, Int32(buf.count), 0, true)
        }
        return buf.prefix(Int(max(0, n))).map { UInt8(bitPattern: $0) }
    }

    private func makeSampler(_ s: GenerationSettings, vocab: OpaquePointer) -> UnsafeMutablePointer<llama_sampler> {
        let chain = llama_sampler_chain_init(llama_sampler_chain_default_params())!
        if s.temperature <= 0.01 {
            llama_sampler_chain_add(chain, llama_sampler_init_greedy())
            return chain
        }
        if s.repeatPenalty > 1.001 {
            llama_sampler_chain_add(chain, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 256, Float(s.repeatPenalty), 0, 0))
        }
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(Int32(s.topK)))
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(Float(s.topP), 1))
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(Float(s.minP), 1))
        llama_sampler_chain_add(chain, llama_sampler_init_temp(Float(s.temperature)))
        llama_sampler_chain_add(chain, llama_sampler_init_dist(s.seed))
        return chain
    }
}
