import Foundation

/// Событие в ленте: вышла модель, появилась готовая к установке сборка, обновился движок.
nonisolated struct FeedItem: Codable, Identifiable, Sendable, Hashable {
    enum Kind: String, Codable, Sendable {
        /// Готовая GGUF-сборка — ставится в один клик.
        case ready
        /// Лаборатория выпустила модель (исходные веса). GGUF может появиться позже.
        case release
        /// Новый выпуск llama.cpp: поддержка новых архитектур и ускорения.
        case engine
    }

    var id: String
    var kind: Kind
    var title: String
    var subtitle: String
    var date: Date
    var repo: String?
    var lab: String?
    var params: Double?
    var activeParams: Double?
    var architecture: String?
    var downloads: Int?
    var likes: Int?
    var notes: [String] = []
    var url: URL
    /// Для выпуска модели — найденная GGUF-сборка.
    var ggufRepo: String?
    /// Встроенный движок знает архитектуру.
    var supported = true
    /// Дообученная сообществом версия чужой модели.
    var finetune = false
}

/// Источники ленты.
///
/// Hugging Face: новые репозитории доверенных сборщиков GGUF (unsloth, ggml-org, LM Studio, bartowski)
/// и новые модели ведущих лабораторий; GitHub: выпуски llama.cpp. Всё через открытые API без ключей.
nonisolated enum FeedSource {
    static let labs = ["Qwen", "google", "meta-llama", "mistralai", "deepseek-ai", "microsoft", "nvidia", "openai",
                       "ibm-granite", "allenai", "HuggingFaceTB", "LiquidAI", "zai-org", "moonshotai", "MiniMaxAI", "tencent"]
    static let skipWords = ["-fp8", "-awq", "-gptq", "-mlx", "-bnb", "-int4", "-int8", "-nvfp4", "-4bit", "-8bit", "-base", "_base", "-eagle", "-mtp", "-onnx"]

    static func fetch(client: HubClient = .shared, supported: Set<String>, engineTag: String) async -> (items: [FeedItem], failures: Int) {
        var jobs: [@Sendable () async throws -> [FeedItem]] = []
        for q in CardBuilder.quantizers {
            jobs.append { try await ready(client: client, author: q, supported: supported) }
        }
        for lab in labs {
            jobs.append { try await releases(client: client, author: lab) }
        }
        jobs.append { try await engine(client: client, current: engineTag) }

        var items: [FeedItem] = []
        var failures = 0
        // Не больше четырёх запросов одновременно — бережём лимиты открытого API.
        await withTaskGroup(of: [FeedItem]?.self) { group in
            var next = 0
            func launch() {
                guard next < jobs.count else { return }
                let job = jobs[next]
                next += 1
                group.addTask { try? await job() }
            }
            for _ in 0..<4 { launch() }
            while let result = await group.next() {
                if let result { items += result } else { failures += 1 }
                launch()
            }
        }

        // Выпуск лаборатории ↔ готовая сборка той же модели.
        let readyItems = items.filter { $0.kind == .ready }
        for i in items.indices where items[i].kind == .release {
            let id = items[i].repo?.lowercased()
            if let match = readyItems.first(where: { $0.notes.first?.lowercased() == id }) { items[i].ggufRepo = match.repo }
        }
        var seen = Set<String>()
        items = items.filter { seen.insert($0.id).inserted }
        items.sort { $0.date > $1.date }
        return (Array(items.prefix(240)), failures)
    }

    private static func ready(client: HubClient, author: String, supported: Set<String>) async throws -> [FeedItem] {
        try await client.models(author: author, gguf: true, sort: .createdAt, limit: 30).compactMap { m in
            guard m.isTextModel, let date = m.createdAt else { return nil }
            let base = m.baseModel
            let baseOrg = base?.split(separator: "/").first.map { String($0).lowercased() }
            let finetune = baseOrg.map { CardBuilder.labs[$0] == nil } ?? false
            let lab = baseOrg.flatMap { CardBuilder.labs[$0] } ?? base?.split(separator: "/").first.map(String.init)
            let name = CardBuilder.prettyName(base?.split(separator: "/").last.map(String.init) ?? m.repoName)
            let arch = m.gguf?.architecture
            var subtitle = "Сборка \(m.publisher)"
            if let p = m.params { subtitle += " · \(p < 10 ? String(format: "%.1f", p) : String(Int(p.rounded()))) млрд параметров" }
            if finetune, let org = base?.split(separator: "/").first { subtitle += " · дообучение от \(org)" }
            return FeedItem(id: "hf:" + m.id, kind: .ready, title: name, subtitle: subtitle, date: date, repo: m.id, lab: lab,
                            params: m.params, activeParams: CardBuilder.activeParams(in: m.id), architecture: arch,
                            downloads: m.downloads, likes: m.likes, notes: [base ?? ""], url: URL(string: "https://huggingface.co/\(m.id)")!,
                            supported: arch.map { supported.isEmpty || supported.contains($0) } ?? true, finetune: finetune)
        }
    }

    private static func releases(client: HubClient, author: String) async throws -> [FeedItem] {
        try await client.models(author: author, gguf: false, sort: .createdAt, limit: 8).compactMap { m in
            guard m.isTextModel, let date = m.createdAt else { return nil }
            let lower = m.id.lowercased()
            if lower.contains("gguf") || skipWords.contains(where: { lower.contains($0) }) { return nil }
            if let p = m.pipeline_tag, !["text-generation", "image-text-to-text", "any-to-any"].contains(p) { return nil }
            let lab = CardBuilder.labs[author.lowercased()] ?? author
            var subtitle = "Новая модель \(lab)"
            if let p = m.params { subtitle += " · \(p < 10 ? String(format: "%.1f", p) : String(Int(p.rounded()))) млрд параметров" }
            return FeedItem(id: "hf:" + m.id, kind: .release, title: CardBuilder.prettyName(m.repoName), subtitle: subtitle, date: date,
                            repo: m.id, lab: lab, params: m.params, activeParams: CardBuilder.activeParams(in: m.id),
                            downloads: m.downloads, likes: m.likes, url: URL(string: "https://huggingface.co/\(m.id)")!)
        }
    }

    private static func engine(client: HubClient, current: String) async throws -> [FeedItem] {
        let currentNumber = Int(current.dropFirst()) ?? 0
        return try await client.llamaReleases().compactMap { r in
            guard let date = r.published_at else { return nil }
            let number = Int(r.tag_name.dropFirst()) ?? 0
            let notes = releaseNotes(r.body ?? "")
            let newer = number > currentNumber
            let subtitle = newer ? "Новее встроенного движка (\(current)) — войдёт в следующее обновление Synapse"
                : number == currentNumber ? "Эта версия встроена в Synapse" : "Уже входит во встроенный движок"
            return FeedItem(id: "gh:" + r.tag_name, kind: .engine, title: "llama.cpp \(r.tag_name)", subtitle: subtitle, date: date,
                            notes: notes, url: URL(string: r.html_url) ?? URL(string: "https://github.com/ggml-org/llama.cpp/releases")!)
        }
    }

    /// Из описания выпуска — строки об изменениях, без списков бинарных файлов.
    static func releaseNotes(_ body: String) -> [String] {
        let noise = ["download", "macos", "windows", "linux", "ios", "android", "openeuler", ".zip", ".tar", "xcframework", "cuda", "vulkan", "rocm", "sycl", "cpu", "](http", "|", "<"]
        return body.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " *-#>\t")) }
            .filter { line in
                line.count > 8 && line.count < 220 && !noise.contains(where: { line.lowercased().contains($0) })
            }
            .prefix(4)
            .map { $0 }
    }
}

/// Лента на диске: события и отметка «прочитано до».
nonisolated struct FeedState: Codable, Sendable {
    var items: [FeedItem] = []
    var lastSeen: Date = .distantPast
    var lastRefresh: Date?
    var notified: Set<String> = []

    static var file: URL { AppPaths.support.appendingPathComponent("feed.json") }

    static func load() -> FeedState {
        guard let data = try? Data(contentsOf: file), let s = try? JSONDecoder.iso.decode(FeedState.self, from: data) else { return FeedState() }
        return s
    }

    func save() {
        if let data = try? JSONEncoder.iso.encode(self) { try? data.write(to: Self.file, options: .atomic) }
    }
}

/// Какие архитектуры знает встроенный движок (список собирается вместе с llama.cpp).
nonisolated enum EngineCatalog {
    static let architectures: Set<String> = {
        guard let url = Bundle.main.url(forResource: "architectures", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return Set(text.split(whereSeparator: \.isNewline).map(String.init))
    }()

    static let version: String = {
        guard let url = Bundle.main.url(forResource: "ENGINE_TAG", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return "b11800" }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }()

    static func supports(_ arch: String?) -> Bool {
        guard let arch, !architectures.isEmpty else { return true }
        return architectures.contains(arch)
    }
}
