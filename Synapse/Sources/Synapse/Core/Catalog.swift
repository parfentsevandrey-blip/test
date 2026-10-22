import Foundation

/// Модель на Hugging Face (ответ /api/models).
nonisolated struct HubModel: Codable, Sendable, Hashable, Identifiable {
    struct GGUF: Codable, Sendable, Hashable {
        var total: Int64?
        var architecture: String?
        var context_length: Int?
    }
    struct Safetensors: Codable, Sendable, Hashable {
        var total: Int64?
    }

    var id: String
    var author: String?
    var downloads: Int?
    var likes: Int?
    var createdAt: Date?
    var lastModified: Date?
    var pipeline_tag: String?
    var tags: [String]?
    var gguf: GGUF?
    var safetensors: Safetensors?
    var trendingScore: Double?

    var publisher: String { author ?? String(id.split(separator: "/").first ?? "") }
    var repoName: String { String(id.split(separator: "/").last ?? Substring(id)) }

    /// Исходная модель, если это квантованная копия: тег base_model:quantized:org/name.
    var baseModel: String? {
        tags?.first(where: { $0.hasPrefix("base_model:quantized:") }).map { String($0.dropFirst("base_model:quantized:".count)) }
            ?? tags?.first(where: { $0.hasPrefix("base_model:") && $0.split(separator: ":").count == 2 }).map { String($0.dropFirst("base_model:".count)) }
    }

    var license: String? {
        tags?.first(where: { $0.hasPrefix("license:") }).map { String($0.dropFirst("license:".count)) }
    }

    var params: Double? {
        if let t = gguf?.total, t > 0 { return Double(t) / 1e9 }
        if let t = safetensors?.total, t > 0 { return Double(t) / 1e9 }
        return nil
    }

    /// Только текстовые модели: изображения, видео, речь и эмбеддинги llama.cpp здесь не запускает.
    var isTextModel: Bool {
        let banned = ["text-to-image", "image-to-image", "text-to-video", "image-to-video", "image-text-to-video", "text-to-speech",
                      "text-to-audio", "automatic-speech-recognition", "feature-extraction", "sentence-similarity", "text-ranking",
                      "audio-text-to-text", "image-to-text", "depth-estimation", "image-classification", "object-detection"]
        if let p = pipeline_tag, banned.contains(p) { return false }
        let name = id.lowercased()
        let junk = ["embedding", "rerank", "-tts", "tts-", "-asr", "whisper", "diffusion", "flux", "-image", "image-", "vae", "clip-",
                    "stories", "test-model", "tiny-llamas", "models-moved", "-ocr", "ocr-", "speech", "guard", "-mtp-gguf", "imatrix", "lora"]
        if junk.contains(where: { name.contains($0) }) { return false }
        if let arch = gguf?.architecture, ["clip", "bert", "nomic-bert", "t5encoder", "wavtokenizer-dec", "qwen3tts", "pockettts"].contains(arch) { return false }
        return true
    }
}

/// Файл в репозитории (/api/models/{repo}/tree/main).
nonisolated struct HubFile: Codable, Sendable, Hashable {
    struct LFS: Codable, Sendable, Hashable {
        var oid: String
        var size: Int64
    }
    var type: String
    var path: String
    var size: Int64?
    var lfs: LFS?

    var bytes: Int64 { lfs?.size ?? size ?? 0 }
}

/// Вариант модели для скачивания: одно квантование, один или несколько файлов (split GGUF).
nonisolated struct ModelVariant: Codable, Sendable, Hashable, Identifiable {
    var quant: Quant
    var files: [HubFile]
    /// Оценка без списка файлов (до запроса к Hugging Face).
    var estimated = false
    /// Проектор зрения (mmproj): с ним модель понимает изображения. Скачивается вместе с весами.
    var projector: HubFile?

    var id: String { quant.name + (files.first?.path ?? "") }
    /// Веса модели.
    var weightsSize: Int64 { files.reduce(0) { $0 + $1.bytes } }
    /// Всё, что скачается и займёт память: веса + проектор зрения.
    var size: Int64 { weightsSize + (projector?.bytes ?? 0) }
    var primary: HubFile? { files.first }
    var allFiles: [HubFile] { files + (projector.map { [$0] } ?? []) }
    var vision: Bool { projector != nil }

    /// Лучший проектор зрения в репозитории: F16 работает на любом видеоядре, BF16 — запасной, F32 — крайний случай.
    static func projector(in files: [HubFile]) -> HubFile? {
        let all = files.filter { let n = $0.path.lowercased(); return n.contains("mmproj") && n.hasSuffix(".gguf") }
        for pref in ["f16", "bf16", "q8_0", "f32"] {
            if let f = all.first(where: { ($0.path as NSString).lastPathComponent.lowercased().contains(pref) && !(pref == "f16" && $0.path.lowercased().contains("bf16")) }) {
                return f
            }
        }
        return all.first
    }

    /// Группировка файлов репозитория по квантованию. Проекторы зрения (mmproj) идут отдельно,
    /// MTP-головы, черновые модели и imatrix — не модели для чата.
    static func group(_ files: [HubFile]) -> [ModelVariant] {
        let projector = projector(in: files)
        var groups: [String: (Quant, [HubFile])] = [:]
        for f in files where f.path.lowercased().hasSuffix(".gguf") {
            let name = (f.path as NSString).lastPathComponent.lowercased()
            if ["mmproj", "mtp-", "eagle", "dflash", "draft", "imatrix", "vocab"].contains(where: { name.contains($0) }) { continue }
            guard let q = Quant.parse(f.path) else { continue }
            // Части одного split-файла: «…-00001-of-00003.gguf».
            let key = f.path.replacingOccurrences(of: #"-\d{5}-of-\d{5}\.gguf$"#, with: "", options: [.regularExpression, .caseInsensitive])
            groups[key, default: (q, [])].1.append(f)
        }
        return groups.values
            .map { ModelVariant(quant: $0.0, files: $0.1.sorted { $0.path < $1.path }, projector: projector) }
            .sorted { $0.size < $1.size }
    }

    /// Варианты «на глаз» по числу параметров — когда списка файлов ещё нет.
    /// `deep` — вместе с сильным сжатием (до 2 бит): для «Выжать максимум».
    static func estimates(params: Double, deep: Bool = false) -> [ModelVariant] {
        let names = deep ? ["IQ2_XXS", "IQ2_M", "Q2_K", "IQ3_XXS", "IQ3_M", "Q3_K_M", "IQ4_XS", "Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0"]
            : ["Q3_K_M", "IQ4_XS", "Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0"]
        return names.compactMap { name in
            guard let q = Quant.named(name) else { return nil }
            let bytes = Int64(params * 1e9 * q.bits / 8 * 1.02)
            return ModelVariant(quant: q, files: [HubFile(type: "file", path: "", size: bytes, lfs: nil)], estimated: true)
        }
    }
}

nonisolated enum ModelTag: String, Codable, Sendable, CaseIterable, Hashable {
    case chat, code, reasoning, vision, small, multilingual, agent

    var title: String {
        switch self {
        case .chat: "Общение"
        case .code: "Код"
        case .reasoning: "Рассуждения"
        case .vision: "Видит картинки"
        case .small: "Лёгкая"
        case .multilingual: "Хорошо знает русский"
        case .agent: "Инструменты"
        }
    }

    var symbol: String {
        switch self {
        case .chat: "bubble.left.and.bubble.right.fill"
        case .code: "chevron.left.forwardslash.chevron.right"
        case .reasoning: "brain.head.profile"
        case .vision: "eye.fill"
        case .small: "hare.fill"
        case .multilingual: "globe"
        case .agent: "wrench.and.screwdriver.fill"
        }
    }
}

/// Карточка модели в каталоге: что это, кто сделал, насколько большая.
nonisolated struct ModelCard: Codable, Sendable, Hashable, Identifiable {
    var id: String               // репозиторий GGUF на Hugging Face
    var name: String
    var lab: String              // кто обучил
    var params: Double           // млрд, всего
    var activeParams: Double?    // млрд, для MoE — сколько работает на каждый токен
    var architecture: String?
    var contextLength: Int?
    var downloads = 0
    var likes = 0
    var created: Date?
    var updated: Date?
    var summary: String
    var tags: Set<ModelTag> = []
    var curated = false
    /// Относительное качество 0…100 — для выбора рекомендаций.
    var quality: Double
    var license: String?
    var baseModel: String?

    var isMoE: Bool { activeParams != nil }
    var hubURL: URL { URL(string: "https://huggingface.co/\(id)")! }
    var quantizer: String { String(id.split(separator: "/").first ?? "") }

    var sizeClass: String {
        switch params {
        case ..<1.5: "Крошечная"
        case ..<5: "Маленькая"
        case ..<15: "Средняя"
        case ..<40: "Большая"
        case ..<130: "Очень большая"
        default: "Гигантская"
        }
    }

    var paramsText: String {
        let p = params < 10 ? String(format: "%.1f", params).replacingOccurrences(of: ".0", with: "") : String(Int(params.rounded()))
        if let a = activeParams { return "\(p) млрд · активно \(a < 10 ? String(format: "%.1f", a).replacingOccurrences(of: ".0", with: "") : String(Int(a)))" }
        return "\(p) млрд"
    }
}

/// Отобранные модели: проверенные сборки GGUF, описания на русском, оценка качества.
/// Это стартовый набор для рекомендаций и запасной каталог без интернета; живой каталог
/// приходит с Hugging Face и дополняет его.
nonisolated enum Curated {
    static let cards: [ModelCard] = [
        card("unsloth/Qwen3.5-0.8B-GGUF", "Qwen3.5 0.8B", "Qwen", 0.8, nil, "qwen35", 22,
             "Крошечная модель Alibaba: мгновенные ответы даже на базовом MacBook Air. Для простых вопросов и проверки, что всё работает.", [.small, .chat, .multilingual, .vision]),
        card("unsloth/Qwen3.5-2B-GGUF", "Qwen3.5 2B", "Qwen", 1.9, nil, "qwen35", 33,
             "Лёгкая и шустрая: пересказы, короткие письма, перевод. Умеет размышлять перед ответом.", [.small, .chat, .reasoning, .multilingual, .vision]),
        card("unsloth/gemma-4-E2B-it-GGUF", "Gemma 4 E2B", "Google", 4.6, nil, "gemma4", 35,
             "Компактная Gemma 4 от Google для слабых Mac: работает как модель на 2 млрд параметров, знает много языков.", [.small, .chat, .multilingual, .vision]),
        card("ibm-granite/granite-4.2-3b-GGUF", "Granite 4.2 3B", "IBM", 3.7, nil, "granite", 36,
             "Деловая модель IBM: аккуратные ответы, работа с документами и инструментами, умеет размышлять. Открытая лицензия Apache 2.0.", [.small, .chat, .agent, .reasoning]),
        card("unsloth/Qwen3.5-4B-GGUF", "Qwen3.5 4B", "Qwen", 4.2, nil, "qwen35", 47,
             "Лучшая маленькая модель для повседневных задач: быстро, по-русски, с режимом размышлений.", [.chat, .reasoning, .multilingual, .small, .vision]),
        card("unsloth/gemma-4-E4B-it-GGUF", "Gemma 4 E4B", "Google", 7.5, nil, "gemma4", 48,
             "Gemma 4 «эффективная 4B»: качество заметно выше размера, хороший русский и творческие тексты.", [.chat, .multilingual, .vision]),
        card("mistralai/Ministral-3-8B-Instruct-2512-GGUF", "Ministral 3 8B", "Mistral AI", 8.5, nil, "mistral3", 52,
             "Европейская модель Mistral: ровный стиль, хорошо следует инструкциям, длинный контекст.", [.chat, .multilingual, .vision]),
        card("ibm-granite/granite-4.2-8b-GGUF", "Granite 4.2 8B", "IBM", 8.8, nil, "granite", 51,
             "Надёжная рабочая лошадка IBM для документов, таблиц и вызова инструментов, с режимом размышлений.", [.chat, .agent, .reasoning]),
        card("unsloth/Qwen3.5-9B-GGUF", "Qwen3.5 9B", "Qwen", 9.0, nil, "qwen35", 61,
             "Золотая середина для Mac с 16 ГБ: умная, быстрая, отлично пишет по-русски и решает задачи с размышлениями.", [.chat, .reasoning, .code, .multilingual, .vision]),
        card("unsloth/gemma-4-12b-it-GGUF", "Gemma 4 12B", "Google", 11.9, nil, "gemma4", 63,
             "Сильная универсальная модель Google: тексты, анализ, перевод, творческие задачи.", [.chat, .multilingual, .reasoning, .vision]),
        card("mistralai/Ministral-3-14B-Instruct-2512-GGUF", "Ministral 3 14B", "Mistral AI", 13.5, nil, "mistral3", 60,
             "Старшая Ministral: точнее и глубже, хороша для работы с длинными документами.", [.chat, .multilingual, .vision]),
        card("unsloth/gpt-oss-20b-GGUF", "gpt-oss 20B", "OpenAI", 20.9, 3.6, "gpt-oss", 66,
             "Открытая модель OpenAI с размышлениями. Смесь экспертов: на каждый токен работает лишь 3,6 млрд параметров — поэтому быстрая.", [.reasoning, .chat, .agent, .code]),
        card("lmstudio-community/Devstral-Small-2-24B-Instruct-2512-GGUF", "Devstral Small 2 24B", "Mistral AI", 23.6, nil, "mistral3", 68,
             "Модель для программистов от Mistral: правит код в проектах, понимает задачи целиком.", [.code, .agent, .vision]),
        card("unsloth/gemma-4-26B-A4B-it-GGUF", "Gemma 4 26B A4B", "Google", 25.2, 4, "gemma4", 72,
             "Большая Gemma 4 со смесью экспертов: качество крупной модели при скорости 4-миллиардной.", [.chat, .reasoning, .multilingual, .vision]),
        card("unsloth/Qwen3.8-27B-GGUF", "Qwen3.8 27B", "Qwen", 27.3, nil, "qwen35", 82,
             "Флагман среди моделей, которые помещаются на Mac с 32 ГБ: глубокие рассуждения, код, длинные тексты.", [.chat, .reasoning, .code, .multilingual, .agent, .vision]),
        card("unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF", "Qwen3 Coder 30B A3B", "Qwen", 30.5, 3.3, "qwen3moe", 66,
             "Специалист по коду: пишет, объясняет и исправляет программы. Быстрая благодаря смеси экспертов.", [.code, .agent]),
        card("ggml-org/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-GGUF", "Nemotron 3.5 Lightning 30B", "NVIDIA", 31.6, 3, "nemotron_h_moe", 72,
             "Гибридная модель NVIDIA: очень быстрая генерация и очень длинный контекст, рассуждает перед ответом. Русский официально не поддерживается.", [.reasoning, .chat, .agent]),
        card("unsloth/gemma-4-31B-it-GGUF", "Gemma 4 31B", "Google", 30.7, nil, "gemma4", 78,
             "Старшая плотная Gemma 4: максимум качества от Google для Mac с 32 ГБ и больше.", [.chat, .reasoning, .multilingual, .vision]),
        card("unsloth/Qwen3.6-35B-A3B-GGUF", "Qwen3.6 35B A3B", "Qwen", 34.7, 3, "qwen35moe", 76,
             "Умная и при этом очень быстрая: 35 млрд параметров знаний, но на токен работают только 3 млрд.", [.chat, .reasoning, .code, .multilingual, .agent, .vision]),
        card("Qwen/Qwen3-Next-80B-A3B-Instruct-GGUF", "Qwen3 Next 80B A3B", "Qwen", 79.7, 3, "qwen3next", 80,
             "Огромные знания при скорости маленькой модели — для Mac с 64 ГБ и больше. Отвечает сразу, без режима размышлений.", [.chat, .multilingual]),
        card("ggml-org/gpt-oss-120b-GGUF", "gpt-oss 120B", "OpenAI", 116.8, 5.1, "gpt-oss", 85,
             "Старшая открытая модель OpenAI: уровень коммерческих ассистентов, нужен Mac со 96–128 ГБ памяти.", [.reasoning, .chat, .code, .agent]),
        card("unsloth/Qwen3.8-Flash-Next-GGUF", "Qwen3.8 Flash Next", "Qwen", 176.9, 6, "qwen4exp", 90,
             "Экспериментальный гигант Qwen: для Mac Studio с 192 ГБ и больше.", [.chat, .reasoning, .code, .agent, .vision]),
    ]

    /// Самая маленькая модель — для проверки движка и первых шагов без ожидания.
    static let smoke = "unsloth/Qwen3.5-0.8B-GGUF"

    private static func card(_ id: String, _ name: String, _ lab: String, _ params: Double, _ active: Double?, _ arch: String,
                             _ quality: Double, _ summary: String, _ tags: Set<ModelTag>) -> ModelCard {
        ModelCard(id: id, name: name, lab: lab, params: params, activeParams: active, architecture: arch,
                  summary: summary, tags: tags, curated: true, quality: quality)
    }
}

/// Превращение живых данных Hugging Face в карточки каталога.
nonisolated enum CardBuilder {
    /// Сборщики GGUF в порядке доверия: при дублях оставляем лучшую сборку.
    static let quantizers = ["unsloth", "ggml-org", "lmstudio-community", "bartowski"]
    static let labs: [String: String] = [
        "qwen": "Qwen", "google": "Google", "meta-llama": "Meta", "mistralai": "Mistral AI", "deepseek-ai": "DeepSeek",
        "microsoft": "Microsoft", "nvidia": "NVIDIA", "openai": "OpenAI", "ibm-granite": "IBM", "allenai": "Ai2",
        "huggingfacetb": "Hugging Face", "liquidai": "Liquid AI", "zai-org": "Z.ai", "moonshotai": "Moonshot",
        "minimaxai": "MiniMax", "tencent": "Tencent", "baidu": "Baidu", "coherelabs": "Cohere", "xiaomimimo": "Xiaomi",
        "stepfun-ai": "StepFun", "inclusionai": "Ant Group", "servicenow-ai": "ServiceNow",
    ]

    static func card(from m: HubModel) -> ModelCard? {
        guard m.isTextModel, let params = m.params, params >= 0.1 else { return nil }
        if let curated = Curated.cards.first(where: { $0.id.caseInsensitiveCompare(m.id) == .orderedSame }) {
            var c = curated
            c.downloads = m.downloads ?? 0
            c.likes = m.likes ?? 0
            c.created = m.createdAt
            c.updated = m.lastModified
            c.contextLength = m.gguf?.context_length
            c.license = m.license
            c.baseModel = m.baseModel
            if let arch = m.gguf?.architecture { c.architecture = arch }
            return c
        }
        let base = m.baseModel
        let labKey = (base?.split(separator: "/").first.map(String.init) ?? m.publisher).lowercased()
        let lab = labs[labKey] ?? (base?.split(separator: "/").first.map(String.init) ?? m.publisher)
        let name = prettyName(base?.split(separator: "/").last.map(String.init) ?? m.repoName)
        let active = activeParams(in: m.id)
        let lower = m.id.lowercased()
        var tags: Set<ModelTag> = [.chat]
        if lower.contains("coder") || lower.contains("code") || lower.contains("devstral") { tags.insert(.code) }
        if lower.contains("think") || lower.contains("reason") || lower.contains("r1") || lower.contains("qwq") { tags.insert(.reasoning) }
        if m.pipeline_tag == "image-text-to-text" || lower.contains("-vl") { tags.insert(.vision) }
        if params < 5 { tags.insert(.small) }
        if lower.contains("qwen") || lower.contains("gemma") || lower.contains("gpt-oss") { tags.insert(.multilingual) }

        return ModelCard(id: m.id, name: name, lab: lab, params: params, activeParams: active,
                         architecture: m.gguf?.architecture, contextLength: m.gguf?.context_length,
                         downloads: m.downloads ?? 0, likes: m.likes ?? 0, created: m.createdAt, updated: m.lastModified,
                         summary: autoSummary(lab: lab, params: params, active: active, tags: tags),
                         tags: tags, curated: false, quality: estimatedQuality(params: params, active: active, created: m.createdAt),
                         license: m.license, baseModel: base)
    }

    /// «Qwen_Qwen3-4B-Instruct-2507-GGUF» → «Qwen3 4B Instruct 2507».
    static func prettyName(_ raw: String) -> String {
        var s = raw
        for suffix in ["-GGUF", "_GGUF", "-gguf", "_gguf", "-it", "-Instruct"] where s.hasSuffix(suffix) { s.removeLast(suffix.count) }
        if let us = s.firstIndex(of: "_"), s[..<us].count < 20, !s[..<us].contains("-") { s = String(s[s.index(after: us)...]) }
        s = s.replacingOccurrences(of: "-it-", with: "-").replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: "-", with: " ")
        let words = s.split(separator: " ").map { w -> String in
            if w.range(of: #"^\d+(\.\d+)?[bB]$"#, options: .regularExpression) != nil { return w.uppercased() }
            if w.range(of: #"^[eaEA]\d+[bB]$"#, options: .regularExpression) != nil { return w.uppercased() }
            return w.first!.isLowercase && w.count > 2 && !w.contains(where: \.isNumber) ? w.prefix(1).uppercased() + w.dropFirst() : String(w)
        }
        return words.joined(separator: " ")
    }

    /// «…-35B-A3B…» → 3 млрд активных параметров.
    static func activeParams(in id: String) -> Double? {
        guard let r = id.range(of: #"[-_]A(\d+(?:\.\d+)?)B(?=[-_.]|$)"#, options: [.regularExpression, .caseInsensitive]) else { return nil }
        return Double(id[r].dropFirst(2).dropLast())
    }

    static func estimatedQuality(params: Double, active: Double?, created: Date?) -> Double {
        // Чем больше модель, тем умнее (логарифмически); MoE — между активным и полным размером.
        let effective = active.map { sqrt($0 * params) } ?? params
        var q = 20 + 14 * log2(max(0.5, effective) + 1)
        // Свежие поколения заметно сильнее старых того же размера.
        if let created {
            let years = Date().timeIntervalSince(created) / (365 * 86_400)
            q -= min(25, max(0, years - 0.3) * 12)
        }
        return min(95, max(5, q))
    }

    static func autoSummary(lab: String, params: Double, active: Double?, tags: Set<ModelTag>) -> String {
        var parts: [String] = []
        if tags.contains(.code) { parts.append("Модель для программирования") }
        else if tags.contains(.reasoning) { parts.append("Модель с рассуждениями") }
        else { parts.append("Универсальная модель") }
        parts[0] += " от \(lab)"
        if let active { parts.append("смесь экспертов: на токен работает \(String(format: "%.0f", active)) млрд параметров — быстрее, чем можно подумать по размеру") }
        if tags.contains(.vision) { parts.append("понимает изображения: фото, скриншоты, схемы, документы") }
        return parts.joined(separator: "; ") + "."
    }

    /// Дубли одной модели от разных сборщиков: остаётся сборка с лучшим доверием.
    static func dedupe(_ cards: [ModelCard]) -> [ModelCard] {
        var best: [String: ModelCard] = [:]
        for c in cards {
            let key = (c.baseModel ?? c.name).lowercased()
            if let existing = best[key] {
                if rank(c) < rank(existing) { best[key] = c }
            } else {
                best[key] = c
            }
        }
        return Array(best.values)
    }

    private static func rank(_ c: ModelCard) -> Int {
        if c.curated { return -1 }
        return quantizers.firstIndex(of: c.quantizer.lowercased()) ?? quantizers.count
    }
}
