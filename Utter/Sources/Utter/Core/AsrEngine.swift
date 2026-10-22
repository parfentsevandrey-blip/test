import CLlama
import Foundation

nonisolated enum AsrError: LocalizedError {
    case notLoaded
    case loadFailed(String)
    case noAudioSupport
    case failed(String)

    var errorDescription: String? {
        switch self {
        case .notLoaded: "Модель распознавания не загружена."
        case .loadFailed(let why): "Не удалось открыть модель распознавания. \(why)"
        case .noAudioSupport: "Файл проектора не поддерживает звук."
        case .failed(let why): "Распознавание не удалось: \(why)"
        }
    }
}

/// Последние сообщения llama.cpp — для понятных ошибок.
nonisolated final class EngineLog: @unchecked Sendable {
    static let shared = EngineLog()
    private let lock = NSLock()
    private var lines: [String] = []
    var echo = ProcessInfo.processInfo.environment["UTTER_VERBOSE"] != nil

    func append(_ level: Int, _ text: String) {
        let line = text.trimmingCharacters(in: .newlines)
        guard !line.isEmpty else { return }
        lock.withLock {
            lines.append(level >= 3 ? "⚠️ " + line : line)
            if lines.count > 300 { lines.removeFirst(lines.count - 300) }
        }
        // 4 — ошибка (5 — продолжение строки, им llama.cpp рисует точки прогресса).
        if echo || level == 4 { FileHandle.standardError.write(Data(("[llama] " + line + "\n").utf8)) }
    }

    var lastProblem: String { lock.withLock { lines.last(where: { $0.hasPrefix("⚠️") }).map { String($0.dropFirst(2)) } ?? "" } }
}

nonisolated private func asrLogCallback(_ level: ggml_log_level, _ text: UnsafePointer<CChar>?, _ user: UnsafeMutableRawPointer?) {
    guard let text else { return }
    EngineLog.shared.append(Int(level.rawValue), String(cString: text))
}

/// Qwen3-ASR в llama.cpp: звук → аудиокодер (mmproj, libmtmd) → языковая модель → текст.
///
/// Потоковость строится снаружи (см. `Dictation`): фраза перераспознаётся каждые полсекунды, а уже
/// утверждённый текст подаётся как начало ответа (`prefix`) — модель лишь продолжает его. Так делает
/// и официальный потоковый режим Qwen3-ASR, только здесь без vLLM.
nonisolated final class AsrEngine: @unchecked Sendable {
    static let shared = AsrEngine()

    private let queue = DispatchQueue(label: "app.utter.asr", qos: .userInteractive)
    private let lock = NSLock()

    // Только на `queue`.
    private var model: OpaquePointer?
    private var ctx: OpaquePointer?
    private var vocab: OpaquePointer?
    private var mtmd: OpaquePointer?
    private var batch: llama_batch?

    // Потоковое распознавание (Voxtral Realtime). Только на `queue`.
    private var stream: OpaquePointer?
    private var streamSampler: UnsafeMutablePointer<llama_sampler>?
    private var embdBatch: llama_batch?
    private let embdCapacity: Int32 = 64
    private var streamPos: llama_pos = 0
    private var streamToken: llama_token = LLAMA_TOKEN_NULL
    private var streamBytes: [UInt8] = []
    private var streamTokens: [llama_token] = []
    private var streamEnded = false

    // Под `lock`.
    private var _loaded: String?
    private var _sampleRate = 16000
    private var _streaming = false

    var loadedID: String? { lock.withLock { _loaded } }
    var sampleRate: Int { lock.withLock { _sampleRate } }
    /// Загружена потоковая модель: распознавание идёт через `streamStart/streamPush/streamFinish`.
    var streaming: Bool { lock.withLock { _streaming } }

    private init() {
        llama_log_set(asrLogCallback, nil)
        mtmd_helper_log_set(asrLogCallback, nil)
        llama_backend_init()
    }

    // MARK: - Загрузка

    func load(_ asr: AsrModel, gpu: Bool) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            queue.async {
                do {
                    try self.loadSync(asr, gpu: gpu)
                    cont.resume()
                } catch {
                    cont.resume(throwing: error)
                }
            }
        }
    }

    /// Освободить модель перед выходом из программы. Иначе при выходе срабатывает проверка llama.cpp:
    /// у видеоядра остались занятые буферы (ggml_metal_rsets_free → GGML_ASSERT → abort, «программа
    /// неожиданно завершилась»). Если ядро занято дольше `timeout`, выходим сразу, без деструкторов.
    func shutdown(timeout: TimeInterval = 3) {
        let done = DispatchSemaphore(value: 0)
        queue.async {
            self.freeAll()
            llama_backend_free()
            done.signal()
        }
        if done.wait(timeout: .now() + timeout) == .timedOut { _exit(0) }
    }

    func unload() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            queue.async {
                self.freeAll()
                cont.resume()
            }
        }
    }

    private func loadSync(_ asr: AsrModel, gpu: Bool) throws {
        freeAll()
        var mparams = llama_model_default_params()
        mparams.n_gpu_layers = gpu ? 999 : 0
        guard let model = llama_model_load_from_file(asr.modelPath, mparams) else {
            throw AsrError.loadFailed(EngineLog.shared.lastProblem)
        }
        let cores = Hardware.performanceCores
        var cparams = llama_context_default_params()
        // Потоковая модель ведёт один поток на всю диктовку: 12,5 строк в секунду, 8192 — около 10 минут.
        cparams.n_ctx = asr.realtime ? 8192 : 4096
        cparams.n_batch = 2048
        cparams.n_ubatch = 512
        cparams.n_threads = Int32(cores)
        cparams.n_threads_batch = Int32(max(cores, ProcessInfo.processInfo.activeProcessorCount - 2))
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO
        cparams.no_perf = true
        cparams.offload_kqv = gpu
        guard let ctx = llama_init_from_model(model, cparams) else {
            llama_model_free(model)
            throw AsrError.loadFailed(EngineLog.shared.lastProblem)
        }
        var mp = mtmd_context_params_default()
        mp.use_gpu = gpu
        mp.n_threads = Int32(cores)
        mp.print_timings = false
        mp.warmup = true
        mp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO
        guard let mtmd = mtmd_init_from_file(asr.projectorPath, model, mp) else {
            llama_free(ctx)
            llama_model_free(model)
            throw AsrError.loadFailed(EngineLog.shared.lastProblem)
        }
        guard mtmd_support_audio(mtmd) else {
            mtmd_free(mtmd)
            llama_free(ctx)
            llama_model_free(model)
            throw AsrError.noAudioSupport
        }
        let streaming = mtmd_support_audio_stream(mtmd)
        if asr.realtime && !streaming {
            mtmd_free(mtmd)
            llama_free(ctx)
            llama_model_free(model)
            throw AsrError.loadFailed("Эта сборка движка не умеет потоковое распознавание.")
        }
        self.model = model
        self.ctx = ctx
        self.vocab = llama_model_get_vocab(model)
        self.mtmd = mtmd
        self.batch = llama_batch_init(1, 0, 1)
        if streaming { self.embdBatch = llama_batch_init(embdCapacity, llama_model_n_embd_inp(model), 1) }
        let rate = Int(mtmd_get_audio_sample_rate(mtmd))
        lock.withLock {
            _loaded = asr.id
            _sampleRate = rate > 0 ? rate : 16000
            _streaming = streaming
        }
        // Прогрев: первая фраза не должна ждать компиляции шейдеров.
        if streaming {
            if (try? streamStartSync()) != nil {
                _ = try? streamPushSync([Float](repeating: 0, count: rate > 0 ? rate : 16000))
                _ = try? streamFinishSync()
            }
        } else {
            _ = try? transcribeSync([Float](repeating: 0, count: 16000), prefix: "", context: "", language: "Russian")
        }
    }

    private func freeAll() {
        freeStream()
        if let embdBatch { llama_batch_free(embdBatch) }
        embdBatch = nil
        if let batch { llama_batch_free(batch) }
        if let mtmd { mtmd_free(mtmd) }
        if let ctx { llama_free(ctx) }
        if let model { llama_model_free(model) }
        batch = nil
        mtmd = nil
        ctx = nil
        model = nil
        vocab = nil
        lock.withLock {
            _loaded = nil
            _streaming = false
        }
    }

    // MARK: - Распознавание

    /// Распознаёт звук (моно, `sampleRate` Гц). `prefix` — уже утверждённый текст: модель продолжит его
    /// и вернёт только продолжение. `context` — подсказки (имена, термины) для модели.
    func transcribe(_ samples: [Float], prefix: String = "", context: String = "", language: String = "Russian") async throws -> String {
        try await withCheckedThrowingContinuation { cont in
            queue.async {
                do { cont.resume(returning: try self.transcribeSync(samples, prefix: prefix, context: context, language: language)) }
                catch { cont.resume(throwing: error) }
            }
        }
    }

    private func transcribeSync(_ samples: [Float], prefix: String, context: String, language: String) throws -> String {
        guard let ctx, let vocab, let mtmd, var batch else { throw AsrError.notLoaded }
        defer { self.batch = batch }
        let mem = llama_get_memory(ctx)
        llama_memory_clear(mem, true)

        // Формат Qwen3-ASR: система — контекст, пользователь — звук, ответ — «language X<asr_text>текст».
        // Язык задаём сами: модель не тратит время на определение и не путает русский с украинским.
        let marker = String(cString: mtmd_default_marker())
        let prompt = "<|im_start|>system\n\(context)<|im_end|>\n<|im_start|>user\n\(marker)<|im_end|>\n"
            + "<|im_start|>assistant\nlanguage \(language)<asr_text>\(prefix)"

        let bitmap = samples.withUnsafeBufferPointer { mtmd_bitmap_init_from_audio($0.count, $0.baseAddress) }
        defer { mtmd_bitmap_free(bitmap) }
        guard let chunks = mtmd_input_chunks_init() else { throw AsrError.failed("нет памяти") }
        defer { mtmd_input_chunks_free(chunks) }
        let bitmaps: [OpaquePointer?] = [bitmap]
        let rc = prompt.withCString { cstr -> Int32 in
            var input = mtmd_input_text(text: cstr, text_len: strlen(cstr), add_special: true, parse_special: true)
            return bitmaps.withUnsafeBufferPointer { mtmd_tokenize(mtmd, chunks, &input, $0.baseAddress, $0.count) }
        }
        guard rc == 0 else { throw AsrError.failed("разбор звука, код \(rc)") }

        var position: llama_pos = 0
        guard mtmd_helper_eval_chunks(mtmd, ctx, chunks, 0, 0, 2048, true, &position) == 0 else {
            throw AsrError.failed(EngineLog.shared.lastProblem)
        }

        // Жадный выбор: для распознавания нужен самый вероятный текст, а не разнообразие.
        let sampler = llama_sampler_chain_init(llama_sampler_chain_default_params())!
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy())
        defer { llama_sampler_free(sampler) }

        // По-русски ~3 слова в секунду ≈ 6–8 токенов; с запасом — чтобы не уйти в повторы.
        let seconds = Double(samples.count) / Double(max(1, sampleRate))
        let maxTokens = Int(seconds * 10) + 24
        var tokens: [llama_token] = []
        var pieces: [[UInt8]] = []
        let nCtx = Int(llama_n_ctx(ctx))
        while tokens.count < maxTokens, Int(position) < nCtx - 1 {
            let next = llama_sampler_sample(sampler, ctx, -1)
            if llama_vocab_is_eog(vocab, next) { break }
            tokens.append(next)
            pieces.append(piece(next, vocab: vocab))
            if Self.repeating(tokens) {
                // Оставляем одну копию повторяющегося куска.
                tokens.removeLast(16)
                pieces.removeLast(16)
                break
            }

            batch.n_tokens = 1
            batch.token[0] = next
            batch.pos[0] = position
            batch.n_seq_id[0] = 1
            batch.seq_id[0]![0] = 0
            batch.logits[0] = 1
            guard llama_decode(ctx, batch) == 0 else { break }
            position += 1
        }
        return String(decoding: pieces.flatMap { $0 }, as: UTF8.self)
    }

    // MARK: - Потоковое распознавание (Voxtral Realtime)

    /// Начать новую фразу: свежий поток звука и чистая память модели.
    /// Вызовы выполняются строго по очереди: `streamPush` после `streamStart` того же потока.
    func streamStart(completion: @escaping @Sendable (Error?) -> Void) {
        queue.async {
            do { try self.streamStartSync(); completion(nil) } catch { completion(error) }
        }
    }

    /// Добавить звук; `completion` получает весь текст фразы на этот момент.
    func streamPush(_ samples: [Float], completion: @escaping @Sendable (Result<String, Error>) -> Void) {
        queue.async {
            do { completion(.success(try self.streamPushSync(samples))) } catch { completion(.failure(error)) }
        }
    }

    /// Пауза в речи: поток остаётся открытым, модель дописывает то, что было «в задержке».
    /// `completion` получает весь текст потока и признак, что поток пора начать заново (память почти занята
    /// или модель остановилась).
    func streamPause(completion: @escaping @Sendable (Result<(text: String, restart: Bool), Error>) -> Void) {
        queue.async {
            do { completion(.success(try self.streamPauseSync())) } catch { completion(.failure(error)) }
        }
    }

    /// Закрыть поток без результата (конец диктовки).
    func streamClose() {
        queue.async { self.freeStream() }
    }

    /// Конец фразы: модель дописывает то, что ещё «в задержке». `completion` получает окончательный текст.
    func streamFinish(completion: @escaping @Sendable (Result<String, Error>) -> Void) {
        queue.async {
            do { completion(.success(try self.streamFinishSync())) } catch { completion(.failure(error)) }
        }
    }

    /// Распознать запись целиком потоковой моделью (самопроверка): кусками по `chunk` отсчётов.
    func streamTranscribe(_ samples: [Float], chunk: Int = 5120) async throws -> String {
        try await withCheckedThrowingContinuation { cont in
            queue.async {
                do {
                    try self.streamStartSync()
                    var i = 0
                    while i < samples.count {
                        _ = try self.streamPushSync(Array(samples[i..<min(samples.count, i + chunk)]))
                        i += chunk
                    }
                    cont.resume(returning: try self.streamFinishSync())
                } catch {
                    cont.resume(throwing: error)
                }
            }
        }
    }

    private func freeStream() {
        if let stream { mtmd_audio_stream_free(stream) }
        stream = nil
        if let streamSampler { llama_sampler_free(streamSampler) }
        streamSampler = nil
    }

    private func streamStartSync() throws {
        guard let ctx, let mtmd else { throw AsrError.notLoaded }
        freeStream()
        llama_memory_clear(llama_get_memory(ctx), true)
        guard let s = mtmd_audio_stream_init(mtmd, mtmd_audio_stream_params_default()) else {
            throw AsrError.failed("поток звука не открылся: \(EngineLog.shared.lastProblem)")
        }
        stream = s
        // Жадный выбор: самый вероятный текст.
        let sampler = llama_sampler_chain_init(llama_sampler_chain_default_params())!
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy())
        streamSampler = sampler
        streamPos = 0
        streamToken = LLAMA_TOKEN_NULL
        streamBytes = []
        streamTokens = []
        streamEnded = false
    }

    private func streamPushSync(_ samples: [Float]) throws -> String {
        guard let stream else { throw AsrError.notLoaded }
        if !streamEnded {
            _ = samples.withUnsafeBufferPointer { mtmd_audio_stream_push(stream, $0.baseAddress, $0.count) }
            try drainStream()
        }
        return streamText
    }

    private func streamPauseSync() throws -> (text: String, restart: Bool) {
        guard let stream, let ctx else { throw AsrError.notLoaded }
        if !streamEnded {
            _ = mtmd_audio_stream_flush(stream, false)
            try drainStream()
        }
        let nearlyFull = Int(streamPos) > Int(llama_n_ctx(ctx)) * 3 / 4
        return (streamText, nearlyFull || streamEnded)
    }

    private func streamFinishSync() throws -> String {
        guard let stream else { throw AsrError.notLoaded }
        if !streamEnded {
            _ = mtmd_audio_stream_flush(stream, true)
            try drainStream()
        }
        let text = streamText
        freeStream()
        return text
    }

    private var streamText: String {
        String(decoding: streamBytes, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Всё, что поток готов отдать: строки эмбеддингов (звук + прошлый токен) → модель → следующий токен.
    /// Модель пишет служебные токены пауз и границ слов — в текст они не идут.
    private func drainStream() throws {
        guard let stream, let ctx, let vocab, let sampler = streamSampler, var batch = embdBatch, let model else { throw AsrError.notLoaded }
        defer { embdBatch = batch }
        let nEmbd = Int(llama_model_n_embd_inp(model))
        let nCtx = Int(llama_n_ctx(ctx))
        while !streamEnded {
            let n = mtmd_audio_stream_step(stream, streamToken)
            if n == 0 { return }
            if n == -1 { streamEnded = true; return }
            guard n > 0, let rows = mtmd_audio_stream_get_output_embd(stream) else {
                throw AsrError.failed("поток звука: \(EngineLog.shared.lastProblem)")
            }
            if Int(streamPos) + Int(n) >= nCtx - 1 {
                // Фраза длиннее памяти модели — дальше не пишем (Dictation режет фразы раньше).
                streamEnded = true
                return
            }
            var done: Int32 = 0
            while done < n {
                let m = min(embdCapacity, n - done)
                batch.n_tokens = m
                memcpy(batch.embd, rows + Int(done) * nEmbd, Int(m) * nEmbd * MemoryLayout<Float>.size)
                for k in 0..<Int(m) {
                    batch.pos[k] = streamPos + llama_pos(k)
                    batch.n_seq_id[k] = 1
                    batch.seq_id[k]![0] = 0
                    batch.logits[k] = done + Int32(k) == n - 1 ? 1 : 0
                }
                guard llama_decode(ctx, batch) == 0 else { throw AsrError.failed("декодер: \(EngineLog.shared.lastProblem)") }
                streamPos += llama_pos(m)
                done += m
            }
            let token = llama_sampler_sample(sampler, ctx, -1)
            streamToken = token
            if llama_vocab_is_eog(vocab, token) {
                streamEnded = true
                return
            }
            if !llama_vocab_is_control(vocab, token) {
                streamTokens.append(token)
                streamBytes += piece(token, vocab: vocab)
                if Self.repeating(streamTokens) {
                    // Зациклилась на шуме — дальше эту фразу не пишем.
                    streamEnded = true
                    return
                }
            }
        }
    }

    /// Утверждённый текст + продолжение от модели. Утверждаются только целые слова, поэтому продолжение,
    /// начатое с буквы, — это новое слово (модель иногда опускает пробел после подсказанного начала).
    static func join(_ prefix: String, _ continuation: String) -> String {
        guard !prefix.isEmpty, let first = continuation.first, first.isLetter || first.isNumber else { return prefix + continuation }
        return prefix + " " + continuation
    }

    /// Защита от зацикливания (известная беда моделей распознавания на тишине и шуме):
    /// последние 8 токенов повторяют предыдущие 8 трижды подряд.
    static func repeating(_ tokens: [llama_token]) -> Bool {
        let n = 8
        guard tokens.count >= n * 3 else { return false }
        let tail = tokens.suffix(n * 3)
        let a = Array(tail.prefix(n)), b = Array(tail.dropFirst(n).prefix(n)), c = Array(tail.suffix(n))
        return a == b && b == c
    }

    private func piece(_ token: llama_token, vocab: OpaquePointer) -> [UInt8] {
        var buf = [CChar](repeating: 0, count: 64)
        var n = llama_token_to_piece(vocab, token, &buf, Int32(buf.count), 0, false)
        if n < 0 {
            buf = [CChar](repeating: 0, count: Int(-n))
            n = llama_token_to_piece(vocab, token, &buf, Int32(buf.count), 0, false)
        }
        return buf.prefix(Int(max(0, n))).map { UInt8(bitPattern: $0) }
    }
}
