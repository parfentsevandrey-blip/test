import CLlama
import CoreGraphics
import Foundation
import ImageIO

/// Самопроверки для CI: `Synapse.app/Contents/MacOS/Synapse --selftest-<режим> [папка]`.
///
/// - `logic`  — без сети: квантования, разбор ответов, форматы чата, подбор моделей для разных Mac;
/// - `hub`    — живой Hugging Face: каталог, файлы с SHA-256, все отобранные модели, лента;
/// - `engine` — настоящая модель: загрузка с докачкой и проверкой SHA-256, генерация, диалог, отмена, API;
/// - `gpu`    — то же на Metal (на виртуальных машинах CI может не работать — проверка необязательная);
/// - `render` — PNG визуализаций.
nonisolated enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        setvbuf(stdout, nil, _IOLBF, 0)
        let rest = Array(args.drop { $0 != mode }.dropFirst())
        let dir = URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory())
        var ok = false
        switch mode {
        case "--selftest-logic": ok = logic()
        case "--selftest-hub": ok = blocking { await hub() }
        case "--selftest-engine": ok = blocking { await engine(gpu: false) }
        case "--selftest-gpu": ok = blocking { await engine(gpu: true) }
        case "--selftest-render": ok = MainActor.assumeIsolated { RenderSelfTest.run(dir) }
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

    final class Box<T>: @unchecked Sendable { var value: T?; }

    static func blocking(_ work: @escaping @Sendable () async -> Bool) -> Bool {
        let box = Box<Bool>()
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            box.value = await work()
            done.signal()
        }
        done.wait()
        return box.value ?? false
    }

    // MARK: - Логика без сети

    static func logic() -> Bool {
        var ok = true

        print("— Квантования из имён файлов")
        let names: [(String, String?)] = [
            ("Qwen3-4B-Q4_K_M.gguf", "Q4_K_M"), ("qwen2.5-0.5b-instruct-q4_k_m.gguf", "Q4_K_M"),
            ("Qwen3.5-9B-UD-Q4_K_XL.gguf", "UD-Q4_K_XL"), ("Qwen3.6-35B-A3B-MXFP4_MOE.gguf", "MXFP4_MOE"),
            ("gpt-oss-120b-MXFP4.gguf", "MXFP4"), ("gemma-4-12b-it-IQ4_XS.gguf", "IQ4_XS"),
            ("BF16/Qwen3.8-27B-BF16-00001-of-00002.gguf", "BF16"), ("Qwen3-0.6B-Q8_0.gguf", "Q8_0"),
            ("UD-Q2_K_XL/Big-UD-Q2_K_XL-00001-of-00005.gguf", "UD-Q2_K_XL"), ("model-00001-of-00004.gguf", nil),
            ("gemma-4-E2B-it-Q4_0.gguf", "Q4_0"), ("x-IQ3_XXS.gguf", "IQ3_XXS"),
        ]
        for (file, want) in names {
            ok = check(Quant.parse(file)?.name == want, "\(file) → \(Quant.parse(file)?.name ?? "нет")") && ok
        }

        print("— Группировка файлов в варианты")
        func f(_ p: String, _ s: Int64) -> HubFile { HubFile(type: "file", path: p, size: s, lfs: .init(oid: String(repeating: "a", count: 64), size: s)) }
        let groups = ModelVariant.group([
            f("M-Q4_K_M.gguf", 5), f("M-Q8_0.gguf", 9), f("mmproj-F16.gguf", 1), f("MTP/mtp-M-Q8_0.gguf", 1),
            f("BF16/M-BF16-00001-of-00002.gguf", 10), f("BF16/M-BF16-00002-of-00002.gguf", 10), f("README.md", 1),
            f("mmproj-BF16.gguf", 1), f("mmproj-F32.gguf", 2),
        ])
        ok = check(groups.map(\.quant.name) == ["Q4_K_M", "Q8_0", "BF16"], "варианты: \(groups.map(\.quant.name))") && ok
        ok = check(groups.last?.files.count == 2 && groups.last?.weightsSize == 20, "split-файл собран из двух частей") && ok
        ok = check(groups.first?.projector?.path == "mmproj-F16.gguf" && groups.first?.size == 6, "проектор зрения F16 выбран и входит в размер") && ok

        print("— Разбор ответов")
        let r1 = ReplyParser.parse("<think>считаю</think>\n\nОтвет: 4")
        ok = check(r1.reasoning == "считаю" && r1.answer == "Ответ: 4", "<think>: \(r1)") && ok
        let r2 = ReplyParser.parse("думаю дальше\n</think>Готово", startsInReasoning: true)
        ok = check(r2.reasoning == "думаю дальше" && r2.answer == "Готово", "размышления с префилла: \(r2)") && ok
        let r3 = ReplyParser.parse("<|channel|>analysis<|message|>Need answer.<|end|><|start|>assistant<|channel|>final<|message|>Paris")
        ok = check(r3.reasoning == "Need answer." && r3.answer == "Paris", "Harmony (gpt-oss): \(r3)") && ok
        let r4 = ReplyParser.parse("<|channel>thought\nхм<channel|>Привет")
        ok = check(r4.reasoning == "хм" && r4.answer == "Привет", "Gemma 4: \(r4)") && ok
        let r5 = ReplyParser.parse("Привет, мир <thi")
        ok = check(r5.answer == "Привет, мир ", "недописанный маркер придержан: «\(r5.answer)»") && ok
        let r6 = ReplyParser.parse("<think>долго", startsInReasoning: false)
        ok = check(r6.reasoningOpen && r6.answer.isEmpty, "незакрытые размышления") && ok

        print("— UTF-8 по кусочкам")
        var acc = UTF8Accumulator()
        let bytes = Array("Привет 👋".utf8)
        var out = ""
        for b in bytes { out += acc.push([b]) }
        out += acc.flush()
        ok = check(out == "Привет 👋", "посимвольная сборка: «\(out)»") && ok

        print("— Форматы чата")
        let turns = [ChatTurn(role: .system, content: "Будь краток."), ChatTurn(role: .user, content: "Привет")]
        let gemma = ChatFormat.gemma4.render(turns, thinking: false) { _, _ in nil }
        ok = check(gemma.text == "<|turn>system\nБудь краток.<turn|>\n<|turn>user\nПривет<turn|>\n<|turn>model\n<|channel>thought\n<channel|>", "Gemma 4 без размышлений") && ok
        let qwen = ChatFormat.chatmlThinking.render(turns, thinking: true) { _, _ in nil }
        ok = check(qwen.text.hasSuffix("<|im_start|>assistant\n<think>\n") && qwen.startsInReasoning, "Qwen с размышлениями") && ok
        let qwenOff = ChatFormat.chatmlThinking.render(turns, thinking: false) { _, _ in nil }
        ok = check(qwenOff.text.hasSuffix("<think>\n\n</think>\n\n") && !qwenOff.startsInReasoning, "Qwen без размышлений") && ok
        ok = check(ChatFormat.detect(template: "{{ '<|turn>' }}{{ '<turn|>' }}") == .gemma4, "распознан шаблон Gemma 4") && ok
        ok = check(ChatFormat.detect(template: "<|start|>x<|channel|>") == .harmony, "распознан Harmony") && ok

        print("— Защита от зацикливания")
        do {
            // «это V8 нет я перепроверил» по кругу в размышлениях — размышления закрываются.
            let phrase: [llama_token] = [101, 102, 103, 104, 105, 106, 107]
            var g = LoopGuard(enabled: true, budget: 0, startsInReasoning: true, format: .chatmlThinking)
            var action = LoopGuard.Action.none
            var fed = 0
            loop: for _ in 0..<20 { for t in phrase { fed += 1; action = g.observe(t, text: "x"); if action != .none { break loop } } }
            if case .closeThinking(_, let closing) = action {
                ok = check(closing.contains("</think>") && fed <= phrase.count * 4, "круг в размышлениях замечен через \(fed) токенов — размышления закрываются") && ok
            } else {
                ok = check(false, "круг в размышлениях не замечен") && ok
            }
            // Обычный ответ без повторов — не трогаем; таблица с разными строками — тоже.
            var plain = LoopGuard(enabled: true, budget: 0, startsInReasoning: false, format: .chatmlThinking)
            var calm = true
            for i in 0..<600 where plain.observe(llama_token(1000 + (i * 7919) % 5000), text: "y") != .none { calm = false }
            let row: [llama_token] = [5, 6, 7, 8]
            for r in 0..<40 { for t in row + [llama_token(9000 + r)] where plain.observe(t, text: "|") != .none { calm = false } }
            ok = check(calm, "обычный ответ и таблица — без ложных срабатываний") && ok
            // Ответ по кругу — остановка.
            var answer = LoopGuard(enabled: true, budget: 0, startsInReasoning: false, format: .gemma4)
            var stopped = false
            for _ in 0..<10 { for t in phrase where answer.observe(t, text: "z") == .stop { stopped = true } }
            ok = check(stopped, "ответ по кругу останавливается") && ok
            // Предел размышлений.
            var budget = LoopGuard(enabled: false, budget: 50, startsInReasoning: true, format: .gemma4)
            var cut: LoopGuard.Action = .none
            for i in 0..<60 where cut == .none { cut = budget.observe(llama_token(2000 + i), text: "w") }
            if case .closeThinking(_, let closing) = cut { ok = check(closing == "<channel|>", "предел размышлений: Gemma закрывает свой блок") && ok }
            else { ok = check(false, "предел размышлений не сработал") && ok }
            // Старые настройки без новых полей читаются, защита включена по умолчанию.
            let old = try? JSONDecoder().decode(GenerationSettings.self, from: Data(#"{"temperature":0.3,"topP":0.9,"topK":40,"minP":0.05,"repeatPenalty":1,"maxTokens":4096,"thinking":true,"seed":1}"#.utf8))
            ok = check(old?.temperature == 0.3 && old?.loopGuard == true, "настройки прошлой версии читаются, защита включена") && ok
        }

        print("— Что теряется при сжатии")
        let q4 = Quant.named("Q4_K_M")!.tradeoff(params: 9), q8 = Quant.named("Q8_0")!.tradeoff(params: 9)
        let iq2 = Quant.named("IQ2_XXS")!.tradeoff(params: 80), mx = Quant.named("MXFP4_MOE")!.tradeoff(params: 20)
        let tiny = Quant.named("Q4_K_M")!.tradeoff(params: 0.8)
        ok = check(q4.contains("Стандарт") && q8.contains("без потерь") && iq2.contains("Экстремальное") && mx.contains("gpt-oss")
                   && tiny.contains("маленьких"), "у каждого сжатия — своё объяснение: «\(q4.prefix(60))…»") && ok

        print("— HTTP-запрос")
        let raw = "POST /v1/chat/completions?x=1 HTTP/1.1\r\nHost: localhost\r\nContent-Length: 13\r\n\r\n{\"model\":\"a\"}"
        let req = HTTPRequest.parse(Data(raw.utf8))
        ok = check(req?.path == "/v1/chat/completions" && req?.json["model"] as? String == "a", "разбор запроса") && ok
        ok = check(HTTPRequest.parse(Data("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\n{}".utf8)) == nil, "неполное тело — ждём остальное") && ok
        var delta = DeltaTracker()
        delta.startsInReasoning = true
        let d1 = delta.push("хм")
        let d2 = delta.push("</think>Ответ")
        ok = check(d1?.reasoning == "хм" && d2?.answer == "Ответ", "приращения для потока API") && ok

        print("— Подбор моделей для разных Mac")
        let macs: [(String, MacProfile)] = [
            ("MacBook Air M1 8 ГБ", Hardware.simulated("Apple M1", memoryGB: 8, gpuCores: 7)),
            ("MacBook Air M2 16 ГБ", Hardware.simulated("Apple M2", memoryGB: 16, gpuCores: 10)),
            ("MacBook Pro M3 Pro 36 ГБ", Hardware.simulated("Apple M3 Pro", memoryGB: 36, gpuCores: 18)),
            ("Mac Studio M4 Max 128 ГБ", Hardware.simulated("Apple M4 Max", memoryGB: 128, gpuCores: 40)),
            ("Mac Studio M3 Ultra 512 ГБ", Hardware.simulated("Apple M3 Ultra", memoryGB: 512, gpuCores: 80)),
        ]
        var picks: [String: [Advisor.Recommendation]] = [:]
        for (name, mac) in macs {
            let recs = Advisor.recommend(cards: Curated.cards, variants: [:], mac: mac)
            picks[name] = recs
            print("  \(name): " + recs.map { "\($0.role.title) — \($0.card.name) \($0.variant.quant.name) \(Format.bytes($0.variant.size)) ≈\(Int($0.fit.tokensPerSecond)) ток/с [\($0.fit.title)]" }.joined(separator: "; "))
            ok = check(!recs.isEmpty && recs.first?.role == .balanced, "\(name): есть сбалансированная рекомендация") && ok
            ok = check(recs.allSatisfy { $0.fit.runnable }, "\(name): все рекомендации запустятся") && ok
        }
        let air8 = picks["MacBook Air M1 8 ГБ"] ?? []
        ok = check(air8.allSatisfy { $0.card.params <= 9 }, "8 ГБ: только компактные модели") && ok
        let studio = picks["Mac Studio M4 Max 128 ГБ"] ?? []
        ok = check(studio.contains { $0.card.params >= 25 }, "128 ГБ: предложена большая модель") && ok
        let m2 = picks["MacBook Air M2 16 ГБ"]?.first
        ok = check((m2?.card.params ?? 0) >= 4 && (m2?.card.params ?? 99) <= 15, "16 ГБ: сбалансированная — 4–15 млрд (\(m2?.card.name ?? "—"))") && ok

        let big = Curated.cards.first { $0.id == "ggml-org/gpt-oss-120b-GGUF" }!
        let fit8 = Advisor.pick(ModelVariant.estimates(params: big.params), card: big, mac: macs[0].1)!.fit
        ok = check(fit8.level == .tooBig, "gpt-oss 120B на 8 ГБ — «\(fit8.title)»: \(fit8.reasons.first ?? "")") && ok
        let fit128 = Advisor.pick(ModelVariant.estimates(params: big.params), card: big, mac: macs[3].1)!.fit
        ok = check(fit128.runnable, "gpt-oss 120B на 128 ГБ — «\(fit128.title)»") && ok
        let noDisk = Advisor.estimate(size: 20_000_000_000, params: 30, active: nil, mac: Hardware.simulated("Apple M4", memoryGB: 64, freeDiskGB: 10))
        ok = check(noDisk.level == .noDisk, "мало места на диске — предупреждение «\(noDisk.title)»") && ok

        print("— Выжать максимум")
        let pro24 = Hardware.simulated("Apple M4 Pro", memoryGB: 24, gpuCores: 20)
        let everyday = Advisor.recommend(cards: Curated.cards, variants: [:], mac: pro24).first { $0.role == .balanced }
        let stretch = Advisor.stretch(cards: Curated.cards, variants: [:], mac: pro24, above: everyday?.card.quality ?? 0)
        print("  M4 Pro 24 ГБ, на каждый день — \(everyday?.card.name ?? "—"); на пределе: " + stretch.map {
            "\($0.card.name) \($0.variant.quant.name) \(Format.bytes($0.variant.size)) ≈\(Int($0.fit.tokensPerSecond)) ток/с, контекст \($0.fit.context) [\($0.fit.title)]" + ($0.needsMoreGPU ? " +память видеоядра" : "")
        }.joined(separator: "; "))
        ok = check(!stretch.isEmpty && stretch.allSatisfy { $0.card.quality > (everyday?.card.quality ?? 0) }, "24 ГБ: есть модели классом выше рекомендованной") && ok
        ok = check(stretch.contains { $0.card.params >= 25 && !$0.needsMoreGPU }, "24 ГБ: модель на 25+ млрд запустится и без подъёма предела") && ok
        ok = check(stretch.allSatisfy { $0.variant.quant.quality >= Advisor.stretchFloor && $0.fit.tokensPerSecond >= Advisor.stretchMinSpeed },
                   "сжатие не ниже ~2 бит, скорость не ниже 3 ток/с") && ok
        let tight = Advisor.loadPlan(size: 15_500_000_000, params: 27, active: nil, trainContext: 262_144, mac: pro24)
        ok = check(tight.fit.runnable && tight.compressKV, "тесная модель: память контекста сжата, контекст \(tight.context) — «\(tight.fit.title)»") && ok
        let roomy = Advisor.loadPlan(size: 5_000_000_000, params: 8, active: nil, trainContext: 131_072, mac: pro24)
        ok = check(roomy.context == Advisor.defaultContext(pro24) && !roomy.compressKV, "просторная модель: обычный контекст без сжатия") && ok
        let fixed = Advisor.loadPlan(size: 5_000_000_000, params: 8, active: nil, trainContext: 32_768, mac: pro24, context: 65_536, compressKV: true)
        ok = check(fixed.context == 32_768 && fixed.compressKV, "контекст из настроек не больше обученного модели") && ok
        print("— Тихие и лёгкие")
        for (name, m) in [("M4 Pro 24 ГБ", pro24), ("Air M1 8 ГБ", macs[0].1)] {
            let quiet = Advisor.quiet(cards: Curated.cards, variants: [:], mac: m)
            print("  \(name): " + quiet.map { "\($0.card.name) \($0.variant.quant.name) \(Format.gb(Double($0.fit.memory))) ≈\(Int($0.fit.tokensPerSecond)) ток/с" }.joined(separator: "; "))
            ok = check(!quiet.isEmpty && quiet.allSatisfy { $0.share(m) <= max(Advisor.quietShare, 2.5 * Advisor.gb / Double(m.memory)) + 0.001
                && $0.fit.tokensPerSecond >= Advisor.quietSpeed && ($0.card.activeParams ?? $0.card.params) <= Advisor.quietActive },
                       "\(name): лёгкие модели — не больше четверти памяти, быстрые, мало активных параметров") && ok
        }

        let (a, b) = Format.gbDistinct(20.4 * Advisor.gb, 19.8 * Advisor.gb)
        ok = check(a != b, "объёмы в предупреждении не сливаются: «\(a)» и «\(b)»") && ok
        let shrunk = Advisor.estimate(size: 17_500_000_000, params: 27, active: nil, mac: pro24)
        ok = check(shrunk.runnable && shrunk.shrunk && shrunk.context == 2048, "почти влезает — «\(shrunk.title)» с контекстом 2K: \(shrunk.reasons.first ?? "")") && ok
        let installedBig = Advisor.estimate(size: 20_000_000_000, params: 30, active: nil, mac: Hardware.simulated("Apple M4", memoryGB: 64, freeDiskGB: 10), onDisk: true)
        ok = check(installedBig.level != .noDisk, "уже скачанная модель не требует места на диске") && ok
        let raise = GPUMemory.target(pro24)
        ok = check(raise.map { $0 > pro24.gpuBudget && $0 <= pro24.memory - 4 * 1_073_741_824 } == true,
                   "предел видеоядра на 24 ГБ можно поднять до \(raise.map { Format.gb(Double($0)) } ?? "—"), системе остаётся 4 ГБ") && ok
        ok = check(GPUMemory.target(macs[0].1) == nil, "на 8 ГБ предел не трогаем") && ok

        print("— Модели переживают обновление")
        ok = library() && ok

        print("— Это железо")
        let mac = Hardware.scan()
        print("  \(mac.chip) · \(mac.modelID) · \(Int(mac.memoryGB)) ГБ · P\(mac.performanceCores)+E\(mac.efficiencyCores) · GPU \(mac.gpuCores) ядер (\(mac.gpuName)) · \(Int(mac.bandwidth)) ГБ/с · бюджет GPU \(Format.gb(Double(mac.gpuBudget))) · свободно \(Format.bytes(mac.freeDisk)) · VM: \(mac.virtualized)")
        ok = check(mac.memory > 0 && mac.performanceCores > 0, "железо определено") && ok
        let recs = Advisor.recommend(cards: Curated.cards, variants: [:], mac: mac)
        print("  Рекомендации: " + recs.map { "\($0.role.title): \($0.card.name)" }.joined(separator: ", "))
        return ok
    }

    /// Список моделей потерян, повреждён или записан другой версией — модели всё равно на месте.
    static func library() -> Bool {
        var ok = true
        let saved = AppPaths.override
        let home = FileManager.default.temporaryDirectory.appendingPathComponent("synapse-library-\(UUID().uuidString)")
        AppPaths.override = home
        defer {
            AppPaths.override = saved
            try? FileManager.default.removeItem(at: home)
        }
        let folder = AppPaths.models.appendingPathComponent("unsloth__Qwen3.5-0.8B-GGUF")
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        for name in ["Qwen3.5-0.8B-Q4_K_M.gguf", "mmproj-F16.gguf", "Qwen3.5-0.8B-Q8_0.gguf.part"] {
            FileManager.default.createFile(atPath: folder.appendingPathComponent(name).path, contents: Data(repeating: 7, count: 4096))
        }
        let found = Library.load()
        ok = check(found.count == 1 && found.first?.name == "Qwen3.5 0.8B" && found.first?.quant == "Q4_K_M" && found.first?.vision == true,
                   "без списка модели найдены в папке: \(found.map { "\($0.name) \($0.quant)\($0.vision ? " +зрение" : "")" })") && ok
        try? Data("{испорчено".utf8).write(to: Library.manifest)
        ok = check(Library.load().count == 1, "повреждённый список — восстановлен из запасной копии") && ok
        try? FileManager.default.removeItem(at: Library.backup)
        let entry = String(decoding: (try? JSONEncoder.iso.encode(found)) ?? Data("[]".utf8), as: UTF8.self)
        let mixed = "[{\"будущее поле\": 1}, " + entry.dropFirst()
        try? Data(mixed.utf8).write(to: Library.manifest)
        let afterMixed = Library.load()
        ok = check(afterMixed.count == 1 && afterMixed.first?.lastUsed == nil, "непонятная запись не стирает остальные") && ok
        return ok
    }

    // MARK: - Живой Hugging Face

    static func hub() async -> Bool {
        var ok = true
        let client = HubClient.shared
        do {
            let fresh = try await client.models(author: "unsloth", gguf: true, sort: .createdAt, limit: 15)
            ok = check(!fresh.isEmpty, "свежие GGUF от unsloth: \(fresh.count)") && ok
            let withParams = fresh.filter { $0.params != nil }.count
            ok = check(withParams >= fresh.count / 2, "у \(withParams) из \(fresh.count) есть число параметров (expand[]=gguf)") && ok
            for m in fresh.prefix(5) { print("  \(m.id) · \(m.pipeline_tag ?? "—") · \(m.gguf?.architecture ?? "—") · \(Format.relative(m.createdAt))") }
            let cards = fresh.compactMap(CardBuilder.card(from:))
            print("  карточек: \(cards.count) — " + cards.prefix(4).map { "\($0.name) [\($0.lab)]" }.joined(separator: ", "))
        } catch {
            ok = check(false, "список моделей: \(error.localizedDescription)") && ok
        }

        do {
            let variants = try await client.variants(Curated.smoke)
            let q4 = variants.first { $0.quant.name == "Q4_K_M" }
            ok = check(q4 != nil, "\(Curated.smoke): варианты \(variants.map(\.quant.name).joined(separator: " "))") && ok
            ok = check(q4?.primary?.lfs?.oid.count == 64, "у файла есть SHA-256 \(q4?.primary?.lfs?.oid.prefix(12) ?? "")…") && ok
        } catch {
            ok = check(false, "файлы \(Curated.smoke): \(error.localizedDescription)") && ok
        }

        print("— Все отобранные модели существуют и имеют GGUF")
        var missing: [String] = []
        for card in Curated.cards {
            do {
                let v = try await client.variants(card.id)
                let pick = Advisor.pick(v, card: card, mac: Hardware.simulated("Apple M4 Max", memoryGB: 128, gpuCores: 40))
                print("  ✓ \(card.id): \(v.count) вариантов, по умолчанию \(pick?.variant.quant.name ?? "—") \(Format.bytes(pick?.variant.size ?? 0))")
                if v.isEmpty { missing.append(card.id) }
                if !EngineCatalog.architectures.isEmpty, let arch = card.architecture, !EngineCatalog.supports(arch) {
                    print("    ⚠︎ архитектура \(arch) не входит во встроенный движок")
                }
            } catch HubError.rateLimited {
                print("  … \(card.id): лимит запросов, пропускаю")
            } catch {
                missing.append(card.id)
                print("  ✗ \(card.id): \(error.localizedDescription)")
            }
            try? await Task.sleep(for: .milliseconds(250))
        }
        ok = check(missing.isEmpty, "отобранные модели доступны (\(Curated.cards.count - missing.count)/\(Curated.cards.count))") && ok

        print("— Лента")
        let (items, failures) = await FeedSource.fetch(supported: EngineCatalog.architectures, engineTag: EngineCatalog.version)
        let ready = items.filter { $0.kind == .ready }.count, releases = items.filter { $0.kind == .release }.count, engine = items.filter { $0.kind == .engine }.count
        print("  событий: \(items.count) · готовых сборок \(ready) · новых моделей \(releases) · выпусков движка \(engine) · источников без ответа \(failures)")
        for item in items.prefix(8) { print("  [\(item.kind.rawValue)] \(Format.relative(item.date)) · \(item.title) — \(item.subtitle)") }
        ok = check(ready >= 10, "в ленте есть готовые к установке сборки") && ok
        ok = check(releases >= 3, "в ленте есть новые модели лабораторий") && ok
        if engine == 0 { print("  (выпуски llama.cpp не получены — GitHub API мог ограничить частоту запросов с адреса CI)") }
        return ok
    }

    // MARK: - Настоящая модель

    static func engine(gpu: Bool) async -> Bool {
        var ok = true
        let mac = Hardware.scan()
        print("Движок: llama.cpp \(EngineCatalog.version), архитектур: \(EngineCatalog.architectures.count)")
        print(LlamaEngine.systemInfo)

        // 1. Модель: точные файлы, загрузка с обрывом и докачкой, проверка SHA-256.
        guard let card = Curated.cards.first(where: { $0.id == Curated.smoke }),
              let variants = try? await HubClient.shared.variants(card.id),
              let variant = variants.first(where: { $0.quant.name == "Q4_K_M" }) ?? variants.first,
              let file = variant.primary else {
            return check(false, "не удалось получить список файлов \(Curated.smoke)")
        }
        let folder = AppPaths.models.appendingPathComponent(card.id.replacingOccurrences(of: "/", with: "__"))
        let destination = folder.appendingPathComponent((file.path as NSString).lastPathComponent)
        let url = HubClient.downloadURL(repo: card.id, path: file.path)
        if !FileManager.default.fileExists(atPath: destination.path) {
            let started = Date()
            let progress = Box<Int64>()
            progress.value = 0
            let first = FileFetcher(url: url, destination: destination, expectedSize: file.bytes, sha256: file.lfs?.oid) { progress.value = $0 }
            let task = Task { try await first.run() }
            while (progress.value ?? 0) < file.bytes * 2 / 5 { try? await Task.sleep(for: .milliseconds(50)) }
            task.cancel()
            _ = try? await task.value
            let part = (try? FileManager.default.attributesOfItem(atPath: destination.path + ".part"))?[.size] as? Int64 ?? 0
            ok = check(part > 0 && part < file.bytes, "загрузка прервана на \(Format.bytes(part)) из \(Format.bytes(file.bytes))") && ok
            do {
                let second = FileFetcher(url: url, destination: destination, expectedSize: file.bytes, sha256: file.lfs?.oid) { _ in }
                try await second.run()
                let seconds = Date().timeIntervalSince(started)
                ok = check(true, "докачана с места обрыва и проверена по SHA-256 за \(Format.duration(seconds)) (\(Format.speed(Double(file.bytes) / seconds)))") && ok
            } catch {
                return check(false, "докачка: \(error.localizedDescription)")
            }
        } else {
            print("  модель уже скачана: \(destination.path)")
        }
        let installed = InstalledModel(repo: card.id, name: card.name, lab: card.lab, quant: variant.quant.name, files: [destination.lastPathComponent],
                                       size: file.bytes, params: card.params, activeParams: nil, architecture: card.architecture,
                                       contextLength: nil, installedAt: Date(), lastUsed: nil, verified: true)
        var library = Library.load().filter { $0.id != installed.id }
        library.append(installed)
        Library.save(library)

        // 2. Загрузка в память.
        let loadStart = Date()
        let info: ModelInfo
        do {
            info = try await LlamaEngine.shared.load(path: destination.path, context: 4096, gpu: gpu) { _ in }
        } catch {
            print(EngineLog.shared.recent.suffix(20).joined(separator: "\n"))
            return check(false, "загрузка модели: \(error.localizedDescription)")
        }
        ok = check(true, "модель в памяти за \(Format.duration(Date().timeIntervalSince(loadStart))): \(info.name) · \(info.description) · \(info.architecture) · \(info.layers) слоёв · контекст \(info.context) · формат «\(info.format.title)» · \(gpu ? "Metal" : "CPU")") && ok
        ok = check(info.format == .chatmlThinking, "Qwen3.5 распознан как ChatML с размышлениями") && ok

        // 3. Ответ на вопрос.
        func ask(_ turns: [ChatTurn], _ settings: GenerationSettings = .precise) async -> (ReplyParts, GenerationStats)? {
            var raw = ""
            var stats = GenerationStats()
            var reasoning = false
            do {
                for try await e in LlamaEngine.shared.generate(turns, settings: settings) {
                    switch e {
                    case .started(let r): reasoning = r
                    case .text(let t): raw += t
                    case .finished(let s): stats = s
                    case .prompt: break
                    }
                }
            } catch {
                print("  ошибка генерации: \(error.localizedDescription)")
                return nil
            }
            return (ReplyParser.parse(raw, startsInReasoning: reasoning), stats)
        }

        var turns = [ChatTurn(role: .system, content: "You are a helpful assistant. Answer briefly."),
                     ChatTurn(role: .user, content: "What is the capital of France? Answer with one word.")]
        guard let first = await ask(turns) else { return check(false, "генерация не удалась") }
        let (a1, s1) = first
        print("  → «\(a1.answer)» · \(s1.tokens) ток. · \(String(format: "%.1f", s1.tokensPerSecond)) ток/с · промпт \(s1.promptTokens) ток. за \(Format.duration(s1.promptSeconds))")
        ok = check(a1.answer.localizedCaseInsensitiveContains("paris"), "столица Франции — Paris") && ok
        ok = check(a1.reasoning.isEmpty, "без размышлений, когда они выключены") && ok

        turns.append(ChatTurn(role: .assistant, content: a1.answer))
        turns.append(ChatTurn(role: .user, content: "And of Germany? One word."))
        if let second = await ask(turns) {
            let (a2, s2) = second
            print("  → «\(a2.answer)» · повторно использовано \(s2.reusedTokens) из \(s2.promptTokens) токенов промпта")
            ok = check(a2.answer.localizedCaseInsensitiveContains("berlin"), "диалог помнит контекст: Berlin") && ok
        } else { ok = false }

        let ru = [ChatTurn(role: .user, content: "Какая столица России? Ответь одним словом.")]
        if let third = await ask(ru) {
            let a3 = third.0
            print("  → по-русски: «\(a3.answer)»")
            check(a3.answer.localizedCaseInsensitiveContains("москв"), "ответ по-русски (не обязательная проверка для крошечной модели)")
        }

        var thinking = GenerationSettings.precise
        thinking.thinking = true
        thinking.maxTokens = 600
        if let fourth = await ask([ChatTurn(role: .user, content: "Сколько будет 17 + 25?")], thinking) {
            let (a4, s4) = fourth
            print("  → размышления: \(a4.reasoning.count) символов; ответ «\(a4.answer.prefix(80))» · \(s4.tokens) ток.")
            ok = check(!a4.reasoning.isEmpty, "режим размышлений отделён от ответа") && ok
        }

        // 4. Остановка посреди ответа.
        var long = GenerationSettings.precise
        long.maxTokens = 2000
        let stopStart = Date()
        var pieces = 0
        let stream = LlamaEngine.shared.generate([ChatTurn(role: .user, content: "Write a very long story about a cat.")], settings: long)
        do {
            for try await e in stream {
                if case .text = e {
                    pieces += 1
                    if pieces == 8 { LlamaEngine.shared.stop() }
                }
            }
        } catch {}
        let stopTime = Date().timeIntervalSince(stopStart)
        ok = check(pieces < 40 && stopTime < 30, "остановка: после \(pieces) кусочков за \(Format.duration(stopTime))") && ok

        // 5. API: Ollama и OpenAI.
        let state = Box<APIServer.State>()
        let backend = APIServer.Backend(models: { [installed] }, prepare: { _ in installed }, activity: { _ in })
        let server = APIServer(backend: backend) { state.value = $0 }
        let port: UInt16 = 11_517
        server.start(port: port)
        for _ in 0..<50 { if case .running = state.value { break }; try? await Task.sleep(for: .milliseconds(100)) }
        ok = check(state.value == .running(port), "API-сервер слушает localhost:\(port)") && ok
        func post(_ path: String, _ body: [String: Any]) async -> Data? {
            var r = URLRequest(url: URL(string: "http://127.0.0.1:\(port)\(path)")!)
            r.httpMethod = body.isEmpty ? "GET" : "POST"
            if !body.isEmpty { r.httpBody = try? JSONSerialization.data(withJSONObject: body) }
            r.timeoutInterval = 120
            return try? await URLSession.shared.data(for: r).0
        }
        let tags = await post("/api/tags", [:]).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        ok = check((tags?["models"] as? [[String: Any]])?.first?["name"] as? String == installed.apiName, "GET /api/tags → \(installed.apiName)") && ok
        let message: [String: Any] = ["role": "user", "content": "What is the capital of France? Answer with one word."]
        let openai = await post("/v1/chat/completions", ["model": installed.apiName, "messages": [message], "temperature": 0, "max_tokens": 64, "reasoning_effort": "none"])
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        let content = ((openai?["choices"] as? [[String: Any]])?.first?["message"] as? [String: Any])?["content"] as? String ?? ""
        ok = check(content.localizedCaseInsensitiveContains("paris"), "POST /v1/chat/completions → «\(content)»") && ok
        let ndjson = await post("/api/chat", ["model": installed.apiName, "messages": [message], "think": false, "options": ["temperature": 0, "num_predict": 64]])
        let lines = ndjson.map { String(decoding: $0, as: UTF8.self).split(separator: "\n") } ?? []
        let last = lines.last.flatMap { try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: Any] }
        let streamed = lines.dropLast().compactMap { try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: Any] }
            .compactMap { ($0["message"] as? [String: Any])?["content"] as? String }.joined()
        ok = check(lines.count >= 2 && last?["done"] as? Bool == true && streamed.localizedCaseInsensitiveContains("paris"),
                   "POST /api/chat (поток NDJSON): \(lines.count) строк, «\(streamed)»") && ok
        // 6. Зрение: проектор (mmproj) скачивается, модель отвечает на вопрос про картинку.
        if let projector = variant.projector {
            let projectorURL = folder.appendingPathComponent((projector.path as NSString).lastPathComponent)
            do {
                let fetcher = FileFetcher(url: HubClient.downloadURL(repo: card.id, path: projector.path), destination: projectorURL,
                                          expectedSize: projector.bytes, sha256: projector.lfs?.oid) { _ in }
                try await fetcher.run()
                ok = check(true, "проектор зрения \(projector.path) (\(Format.bytes(projector.bytes))) скачан и проверен по SHA-256") && ok
            } catch {
                ok = check(false, "проектор зрения: \(error.localizedDescription)") && ok
            }
            var withVision = installed
            withVision.projector = projectorURL.lastPathComponent
            withVision.size += projector.bytes
            Library.save(Library.load().filter { $0.id != withVision.id } + [withVision])

            do {
                let visionInfo = try await LlamaEngine.shared.load(path: destination.path, projector: projectorURL.path, context: 4096, gpu: gpu) { _ in }
                ok = check(visionInfo.vision, "модель загружена со зрением") && ok
                for (shape, color, cg, expect) in [("circle", "красный", CGColor(srgbRed: 0.9, green: 0.1, blue: 0.1, alpha: 1), "red"),
                                                   ("square", "синий", CGColor(srgbRed: 0.1, green: 0.25, blue: 0.95, alpha: 1), "blue")] {
                    guard let image = testImage(shape: shape, color: cg) else { ok = check(false, "тестовая картинка не создана") && ok; continue }
                    let started = Date()
                    let turns = [ChatTurn(role: .user, content: "What color is the \(shape) in this image? Answer with one word.", images: [image.path])]
                    if let result = await ask(turns) {
                        let (answer, stats) = result
                        print("  → \(color) \(shape == "circle" ? "круг" : "квадрат"): «\(answer.answer)» · картинка + промпт \(stats.promptTokens) ток. за \(Format.duration(Date().timeIntervalSince(started)))")
                        ok = check(answer.answer.localizedCaseInsensitiveContains(expect), "модель видит цвет: \(expect)") && ok
                    } else {
                        ok = check(false, "ответ про картинку не получен") && ok
                    }
                }
                // После картинки обычный текстовый вопрос — кэш должен сброситься корректно.
                if let after = await ask([ChatTurn(role: .user, content: "What is the capital of France? Answer with one word.")]) {
                    ok = check(after.0.answer.localizedCaseInsensitiveContains("paris"), "после картинки текст работает: «\(after.0.answer)»") && ok
                }
                // Картинка через API OpenAI (data-URL).
                if let image = testImage(shape: "circle", color: CGColor(srgbRed: 0.9, green: 0.1, blue: 0.1, alpha: 1)),
                   let data = try? Data(contentsOf: image) {
                    let content: [[String: Any]] = [
                        ["type": "text", "text": "What color is the circle in this image? Answer with one word."],
                        ["type": "image_url", "image_url": ["url": "data:image/png;base64," + data.base64EncodedString()]],
                    ]
                    let response = await post("/v1/chat/completions", ["model": installed.apiName, "messages": [["role": "user", "content": content] as [String: Any]],
                                                                       "temperature": 0, "max_tokens": 32, "reasoning_effort": "none"])
                        .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
                    let text = ((response?["choices"] as? [[String: Any]])?.first?["message"] as? [String: Any])?["content"] as? String ?? ""
                    ok = check(text.localizedCaseInsensitiveContains("red"), "POST /v1/chat/completions с картинкой → «\(text)»") && ok
                }
            } catch {
                print(EngineLog.shared.recent.suffix(20).joined(separator: "\n"))
                ok = check(false, "загрузка со зрением: \(error.localizedDescription)") && ok
            }
        } else {
            ok = check(false, "у \(card.id) не найден проектор зрения") && ok
        }

        server.stop()
        await LlamaEngine.shared.unload()
        _ = mac
        return ok
    }

    /// Тестовая картинка: белый фон, одна крупная фигура заданного цвета.
    static func testImage(shape: String, color: CGColor) -> URL? {
        let size = 384
        guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
        ctx.setFillColor(color)
        let rect = CGRect(x: 72, y: 72, width: 240, height: 240)
        if shape == "circle" { ctx.fillEllipse(in: rect) } else { ctx.fill(rect) }
        guard let image = ctx.makeImage() else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("synapse-test-\(shape).png")
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest) ? url : nil
    }
}
