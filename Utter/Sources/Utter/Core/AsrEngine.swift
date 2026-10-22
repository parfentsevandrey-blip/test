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
        if echo || level >= 4 { FileHandle.standardError.write(Data(("[llama] " + line + "\n").utf8)) }
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

    // Под `lock`.
    private var _loaded: String?
    private var _sampleRate = 16000

    var loadedID: String? { lock.withLock { _loaded } }
    var sampleRate: Int { lock.withLock { _sampleRate } }

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
        cparams.n_ctx = 4096
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
        self.model = model
        self.ctx = ctx
        self.vocab = llama_model_get_vocab(model)
        self.mtmd = mtmd
        self.batch = llama_batch_init(1, 0, 1)
        let rate = Int(mtmd_get_audio_sample_rate(mtmd))
        lock.withLock {
            _loaded = asr.id
            _sampleRate = rate > 0 ? rate : 16000
        }
        // Прогрев: первая фраза не должна ждать компиляции шейдеров.
        _ = try? transcribeSync([Float](repeating: 0, count: 16000), prefix: "", context: "", language: "Russian")
    }

    private func freeAll() {
        if let batch { llama_batch_free(batch) }
        if let mtmd { mtmd_free(mtmd) }
        if let ctx { llama_free(ctx) }
        if let model { llama_model_free(model) }
        batch = nil
        mtmd = nil
        ctx = nil
        model = nil
        vocab = nil
        lock.withLock { _loaded = nil }
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
