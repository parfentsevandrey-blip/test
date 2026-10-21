import AppKit
import Observation
import SwiftUI
@preconcurrency import UserNotifications

enum Section: String, CaseIterable, Identifiable {
    case chat, forYou, catalog, feed, library

    var id: String { rawValue }

    var title: String {
        switch self {
        case .chat: "Чат"
        case .forYou: "Для этого Mac"
        case .catalog: "Каталог"
        case .feed: "Лента"
        case .library: "Мои модели"
        }
    }

    var symbol: String {
        switch self {
        case .chat: "bubble.left.and.text.bubble.right.fill"
        case .forYou: "sparkles"
        case .catalog: "square.grid.2x2.fill"
        case .feed: "dot.radiowaves.left.and.right"
        case .library: "internaldrive.fill"
        }
    }

    var palette: [Color] {
        switch self {
        case .chat: [Color(red: 0.45, green: 0.35, blue: 1), Color(red: 0.2, green: 0.6, blue: 1), Color(red: 0.85, green: 0.35, blue: 0.95)]
        case .forYou: [Color(red: 1, green: 0.55, blue: 0.25), Color(red: 0.95, green: 0.3, blue: 0.55), Color(red: 0.6, green: 0.35, blue: 1)]
        case .catalog: [Color(red: 0.2, green: 0.75, blue: 0.95), Color(red: 0.3, green: 0.45, blue: 1), Color(red: 0.25, green: 0.9, blue: 0.7)]
        case .feed: [Color(red: 1, green: 0.4, blue: 0.4), Color(red: 1, green: 0.65, blue: 0.2), Color(red: 0.95, green: 0.3, blue: 0.6)]
        case .library: [Color(red: 0.3, green: 0.85, blue: 0.55), Color(red: 0.2, green: 0.6, blue: 0.9), Color(red: 0.55, green: 0.9, blue: 0.4)]
        }
    }

    var tint: Color { palette[0] }
}

/// Загрузка модели: прогресс, скорость, оставшееся время.
@Observable
final class DownloadJob: Identifiable {
    enum State: Equatable {
        case queued, running, paused, installing, done
        case failed(String)
    }

    let id = UUID()
    let card: ModelCard
    var variant: ModelVariant
    var state: State = .queued
    var received: Int64 = 0
    var speed: Double = 0
    var task: Task<Void, Never>?
    var startNow = false
    private var lastSample = (date: Date(), bytes: Int64(0))

    init(card: ModelCard, variant: ModelVariant) {
        self.card = card
        self.variant = variant
    }

    var total: Int64 { variant.size }
    var fraction: Double { total > 0 ? min(1, Double(received) / Double(total)) : 0 }
    var eta: Double { speed > 1 ? Double(total - received) / speed : .infinity }
    var isActive: Bool { state == .queued || state == .running || state == .installing }

    func update(received value: Int64) {
        received = value
        let now = Date()
        let dt = now.timeIntervalSince(lastSample.date)
        if dt >= 0.5 {
            let instant = Double(value - lastSample.bytes) / dt
            speed = speed == 0 ? instant : speed * 0.7 + instant * 0.3
            lastSample = (now, value)
        }
    }
}

enum EngineState: Equatable {
    case idle
    case loading(Double)
    case ready
    case failed(String)
}

/// Предупреждение перед загрузкой слишком большой модели.
struct FitWarning: Identifiable {
    let id = UUID()
    let model: InstalledModel
    let fit: FitReport
}

@Observable
final class AppModel {
    // MARK: Железо и онбординг
    var mac = Hardware.scan()
    var onboardingDone = UserDefaults.standard.bool(forKey: "onboardingDone")
    var section: Section = .chat

    // MARK: Каталог
    var cards: [ModelCard] = Curated.cards
    var catalogLoading = false
    var catalogError: String?
    var catalogUpdated: Date?
    var variants: [String: [ModelVariant]] = [:]
    var variantsLoading: Set<String> = []
    var searchResults: [ModelCard]?
    var searching = false
    var detailCard: ModelCard?

    // MARK: Установленное и загрузки
    var installed: [InstalledModel] = Library.load()
    var downloads: [DownloadJob] = []

    // MARK: Лента
    var feed = FeedState.load()
    var feedLoading = false
    var feedError: String?

    // MARK: Чат
    var conversations: [Conversation] = ChatArchive.load()
    var currentChatID: UUID?
    var streamingID: UUID?
    var streamingPhase: String?
    var generationTask: Task<Void, Never>?
    var settings: GenerationSettings = {
        if let data = UserDefaults.standard.data(forKey: "settings"), let s = try? JSONDecoder().decode(GenerationSettings.self, from: data) { return s }
        return GenerationSettings()
    }() {
        didSet { if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "settings") } }
    }

    // MARK: Движок
    var engineState: EngineState = .idle
    var loadedModelID: String?
    var modelInfo: ModelInfo?
    var fitWarning: FitWarning?
    var liveTokensPerSecond: Double = 0

    // MARK: Сервер
    var apiEnabled = UserDefaults.standard.bool(forKey: "apiEnabled") { didSet { UserDefaults.standard.set(apiEnabled, forKey: "apiEnabled"); applyAPI() } }
    var apiPort = UserDefaults.standard.object(forKey: "apiPort") as? Int ?? 11434 { didSet { UserDefaults.standard.set(apiPort, forKey: "apiPort") } }
    var apiState: APIServer.State = .stopped
    var apiRequests = 0
    var notifyNewModels = UserDefaults.standard.object(forKey: "notifyNewModels") as? Bool ?? true { didSet { UserDefaults.standard.set(notifyNewModels, forKey: "notifyNewModels") } }

    var toast: String?
    private var toastTask: Task<Void, Never>?
    private var server: APIServer?
    private var started = false

    // MARK: - Запуск

    func start() {
        guard !started else { return }
        started = true
        if let first = conversations.first { currentChatID = first.id }
        if !installed.isEmpty && !onboardingDone && !DemoUI.isOn { onboardingDone = true }
        if let id = UserDefaults.standard.string(forKey: "lastModel"), installed.contains(where: { $0.id == id }) {
            loadedModelID = id
        } else {
            loadedModelID = installed.first?.id
        }
        Task { await refreshCatalog() }
        Task { await refreshFeedIfNeeded() }
        Task { await resolveRecommendedVariants() }
        Task {
            // Лента обновляется в фоне каждые два часа.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2 * 3600))
                await refreshFeed()
            }
        }
        applyAPI()
        if section == .chat, loadedModelID != nil { Task { await loadSelectedModel() } }
    }

    func showToast(_ text: String) {
        toastTask?.cancel()
        withAnimation(.snappy) { toast = text }
        toastTask = Task {
            try? await Task.sleep(for: .seconds(3.2))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) { toast = nil }
        }
    }

    func finishOnboarding() {
        onboardingDone = true
        UserDefaults.standard.set(true, forKey: "onboardingDone")
    }

    // MARK: - Каталог

    /// Живой каталог: популярные GGUF-сборки доверенных сборщиков + отобранные модели.
    func refreshCatalog(force: Bool = false) async {
        if catalogLoading { return }
        if !force, let updated = catalogUpdated, Date().timeIntervalSince(updated) < 3 * 3600 { return }
        catalogLoading = true
        defer { catalogLoading = false }
        let client = HubClient.shared
        let authors = CardBuilder.quantizers + ["Qwen", "google", "mistralai", "ibm-granite", "microsoft"]
        let lists: [[HubModel]] = await withTaskGroup(of: [HubModel].self) { group in
            for a in authors {
                group.addTask { (try? await client.models(author: a, gguf: true, sort: .downloads, limit: 60)) ?? [] }
            }
            var all: [[HubModel]] = []
            for await list in group { all.append(list) }
            return all
        }
        let models = lists.flatMap { $0 }
        guard !models.isEmpty else {
            catalogError = "Не удалось обновить каталог — показываю отобранные модели. Проверьте подключение к интернету."
            return
        }
        catalogError = nil
        var live = models.compactMap(CardBuilder.card(from:))
        // Отобранные карточки, которых нет среди популярных, оставляем.
        for c in Curated.cards where !live.contains(where: { $0.id == c.id }) { live.append(c) }
        cards = CardBuilder.dedupe(live).sorted { $0.downloads > $1.downloads }
        catalogUpdated = Date()
    }

    func search(_ text: String) async {
        let q = text.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { searchResults = nil; return }
        searching = true
        defer { searching = false }
        let local = cards.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.id.localizedCaseInsensitiveContains(q) || $0.lab.localizedCaseInsensitiveContains(q) }
        searchResults = local
        if let remote = try? await HubClient.shared.models(search: q, gguf: true, sort: .downloads, limit: 50) {
            let extra = remote.compactMap(CardBuilder.card(from:)).filter { r in !local.contains(where: { $0.id == r.id }) }
            searchResults = CardBuilder.dedupe(local + extra).sorted { $0.downloads > $1.downloads }
        }
    }

    /// Точный список файлов модели (размеры и SHA-256) — по требованию.
    func loadVariants(_ card: ModelCard) async {
        guard variants[card.id] == nil, !variantsLoading.contains(card.id) else { return }
        variantsLoading.insert(card.id)
        defer { variantsLoading.remove(card.id) }
        if let list = try? await HubClient.shared.variants(card.id), !list.isEmpty { variants[card.id] = list }
    }

    /// Для рекомендаций — точные размеры у первых кандидатов.
    func resolveRecommendedVariants() async {
        for rec in recommendations.prefix(4) { await loadVariants(rec.card) }
    }

    var recommendations: [Advisor.Recommendation] {
        let pool = cards.filter { $0.curated && EngineCatalog.supports($0.architecture) }
        return Advisor.recommend(cards: pool.isEmpty ? Curated.cards : pool, variants: variants, mac: mac)
    }

    func bestVariant(for card: ModelCard) -> (variant: ModelVariant, fit: FitReport)? {
        Advisor.pick(variants[card.id] ?? ModelVariant.estimates(params: card.params), card: card, mac: mac)
    }

    func fit(_ card: ModelCard) -> FitReport? { bestVariant(for: card)?.fit }

    // MARK: - Установка

    func isInstalled(_ card: ModelCard) -> Bool { installed.contains { $0.repo == card.id } }
    func job(for card: ModelCard) -> DownloadJob? { downloads.last { $0.card.id == card.id && $0.state != .done } }

    /// Установка в один клик: точные файлы, проверка места, загрузка, проверка SHA-256, готово.
    func install(_ card: ModelCard, variant: ModelVariant? = nil) {
        if let existing = job(for: card), existing.isActive { section = .library; return }
        Task {
            await loadVariants(card)
            var chosen = variant
            if chosen == nil || chosen!.estimated {
                let list = variants[card.id] ?? []
                if let v = chosen, let exact = list.first(where: { $0.quant.name == v.quant.name }) { chosen = exact }
                else { chosen = Advisor.pick(list, card: card, mac: mac)?.variant }
            }
            guard let chosen, !chosen.estimated, !chosen.files.isEmpty else {
                showToast("Не удалось получить список файлов «\(card.name)». Проверьте интернет.")
                return
            }
            if installed.contains(where: { $0.repo == card.id && $0.quant == chosen.quant.name }) {
                showToast("«\(card.name)» уже установлена")
                return
            }
            let job = DownloadJob(card: card, variant: chosen)
            downloads.append(job)
            showToast("Скачиваю «\(card.name)» · \(Format.bytes(chosen.size))")
            pumpDownloads()
        }
    }

    /// Загрузки идут по одной: так каждая модель готова быстрее.
    func pumpDownloads() {
        guard !downloads.contains(where: { $0.state == .running || $0.state == .installing }) else { return }
        guard let next = downloads.first(where: { $0.state == .queued }) else {
            NSApp?.dockTile.badgeLabel = nil
            return
        }
        next.task = Task { await run(next) }
    }

    func pause(_ job: DownloadJob) {
        job.task?.cancel()
        job.state = .paused
    }

    func resume(_ job: DownloadJob) {
        job.state = .queued
        pumpDownloads()
    }

    func cancel(_ job: DownloadJob) {
        job.task?.cancel()
        downloads.removeAll { $0.id == job.id }
        // Недокачанные части удаляем, чтобы не занимали место.
        let folder = AppPaths.models.appendingPathComponent(job.card.id.replacingOccurrences(of: "/", with: "__"))
        for f in job.variant.files {
            try? FileManager.default.removeItem(at: folder.appendingPathComponent((f.path as NSString).lastPathComponent + ".part"))
        }
        if (try? FileManager.default.contentsOfDirectory(atPath: folder.path))?.isEmpty == true { try? FileManager.default.removeItem(at: folder) }
        pumpDownloads()
    }

    private func run(_ job: DownloadJob) async {
        job.state = .running
        let activity = ProcessInfo.processInfo.beginActivity(options: [.userInitiated, .idleSystemSleepDisabled], reason: "Загрузка модели")
        defer {
            ProcessInfo.processInfo.endActivity(activity)
            pumpDownloads()
        }
        let folder = AppPaths.models.appendingPathComponent(job.card.id.replacingOccurrences(of: "/", with: "__"), isDirectory: true)
        var done: Int64 = 0
        for file in job.variant.files {
            let destination = folder.appendingPathComponent((file.path as NSString).lastPathComponent)
            var attempt = 0
            while true {
                let base = done
                let (stream, cont) = AsyncStream<Int64>.makeStream(bufferingPolicy: .bufferingNewest(1))
                let watcher = Task {
                    for await value in stream {
                        job.update(received: base + value)
                        NSApp?.dockTile.badgeLabel = "\(Int(job.fraction * 100))%"
                    }
                }
                let fetcher = FileFetcher(url: HubClient.downloadURL(repo: job.card.id, path: file.path), destination: destination,
                                          expectedSize: file.bytes, sha256: file.lfs?.oid) { cont.yield($0) }
                do {
                    try await fetcher.run()
                    cont.finish()
                    await watcher.value
                    break
                } catch {
                    cont.finish()
                    watcher.cancel()
                    if Task.isCancelled || job.state == .paused { if job.state != .paused { job.state = .paused }; return }
                    attempt += 1
                    if let fetchError = error as? FetchError {
                        switch fetchError {
                        case .noSpace:
                            job.state = .failed(error.localizedDescription)
                            return
                        case .http(let code) where code == 404 || code == 403 || code == 401:
                            job.state = .failed("Файл недоступен на Hugging Face (код \(code)).")
                            return
                        default:
                            break
                        }
                    }
                    if attempt > 6 { job.state = .failed(error.localizedDescription); return }
                    // Обрыв связи — подождём и продолжим с того же места.
                    try? await Task.sleep(for: .seconds(min(30, 2 << attempt)))
                    if Task.isCancelled { job.state = .paused; return }
                }
            }
            done += file.bytes
        }
        job.state = .installing
        let card = job.card
        let model = InstalledModel(repo: card.id, name: card.name, lab: card.lab, quant: job.variant.quant.name,
                                   files: job.variant.files.map { ($0.path as NSString).lastPathComponent }, size: job.variant.size,
                                   params: card.params, activeParams: card.activeParams, architecture: card.architecture,
                                   contextLength: card.contextLength, installedAt: Date(), lastUsed: nil, verified: true)
        installed.removeAll { $0.id == model.id }
        installed.append(model)
        Library.save(installed)
        job.state = .done
        NSApp?.dockTile.badgeLabel = nil
        showToast("«\(card.name)» установлена и проверена")
        notify(title: "Модель готова", body: "«\(card.name)» установлена — можно общаться.")
        // Первая модель — сразу в работу.
        if loadedModelID == nil || modelInfo == nil {
            select(model)
            section = .chat
        }
    }

    func uninstall(_ model: InstalledModel) {
        Task {
            if loadedModelID == model.id {
                await LlamaEngine.shared.unload()
                modelInfo = nil
                engineState = .idle
                loadedModelID = nil
            }
            try? Library.remove(model)
            installed.removeAll { $0.id == model.id }
            Library.save(installed)
            showToast("«\(model.name)» удалена · освобождено \(Format.bytes(model.size))")
        }
    }

    // MARK: - Модель в памяти

    var selectedModel: InstalledModel? { installed.first { $0.id == loadedModelID } }

    func select(_ model: InstalledModel, force: Bool = false) {
        let fit = Advisor.estimate(size: model.size, params: model.params, active: model.activeParams, mac: mac)
        if fit.level == .tooBig && !force {
            fitWarning = FitWarning(model: model, fit: fit)
            return
        }
        loadedModelID = model.id
        UserDefaults.standard.set(model.id, forKey: "lastModel")
        Task { await loadSelectedModel() }
    }

    @discardableResult
    func loadSelectedModel() async -> Bool {
        guard let model = selectedModel else { return false }
        if let loaded = LlamaEngine.shared.loaded, loaded.path == model.primaryPath { modelInfo = loaded.info; engineState = .ready; return true }
        engineState = .loading(0)
        let context = min(Advisor.defaultContext(mac), model.contextLength ?? .max)
        let progress: @Sendable (Double) -> Void = { p in Task { @MainActor [weak self] in
            guard let self else { return }
            if case .loading = self.engineState { self.engineState = .loading(p) }
        } }
        do {
            // Виртуальная машина: видеоядро эмулируется — считаем на процессоре.
            let info = try await LlamaEngine.shared.load(path: model.primaryPath, context: context, gpu: !mac.virtualized, progress: progress)
            modelInfo = info
            engineState = .ready
        } catch {
            if !mac.virtualized, let info = try? await LlamaEngine.shared.load(path: model.primaryPath, context: min(context, 4096), gpu: false, progress: progress) {
                modelInfo = info
                engineState = .ready
                showToast("Видеоядро не справилось — модель работает на процессоре")
            } else {
                engineState = .failed(error.localizedDescription)
                return false
            }
        }
        if let i = installed.firstIndex(where: { $0.id == model.id }) {
            installed[i].lastUsed = Date()
            Library.save(installed)
        }
        return true
    }

    // MARK: - Чат

    var currentChat: Conversation? {
        get { conversations.first { $0.id == currentChatID } }
        set {
            guard let newValue, let i = conversations.firstIndex(where: { $0.id == newValue.id }) else { return }
            conversations[i] = newValue
        }
    }

    var isGenerating: Bool { streamingID != nil }

    @discardableResult
    func newChat() -> UUID {
        if let c = conversations.first, c.messages.isEmpty { currentChatID = c.id; return c.id }
        let c = Conversation(modelID: loadedModelID)
        conversations.insert(c, at: 0)
        currentChatID = c.id
        return c.id
    }

    func deleteChat(_ c: Conversation) {
        ChatArchive.delete(c)
        conversations.removeAll { $0.id == c.id }
        if currentChatID == c.id { currentChatID = conversations.first?.id }
    }

    func send(_ text: String) {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isGenerating else { return }
        if currentChatID == nil || currentChat == nil { newChat() }
        guard var chat = currentChat else { return }
        if chat.messages.isEmpty { chat.title = ChatArchive.title(from: text) }
        chat.messages.append(ChatMessage(role: .user, text: text))
        chat.updated = Date()
        currentChat = chat
        generateReply()
    }

    /// Ответить заново на последний вопрос.
    func regenerate() {
        guard !isGenerating, var chat = currentChat else { return }
        while chat.messages.last?.role == .assistant { chat.messages.removeLast() }
        currentChat = chat
        generateReply()
    }

    func stopGenerating() {
        generationTask?.cancel()
        LlamaEngine.shared.stop()
    }

    private func generateReply() {
        guard var chat = currentChat else { return }
        let reply = ChatMessage(role: .assistant, text: "", model: selectedModel?.name)
        chat.messages.append(reply)
        chat.modelID = loadedModelID
        currentChat = chat
        streamingID = reply.id
        streamingPhase = "Загружаю модель…"
        let chatID = chat.id
        let turns = chat.turns
        let settings = self.settings

        generationTask = Task {
            defer {
                streamingID = nil
                streamingPhase = nil
                liveTokensPerSecond = 0
                if let c = conversations.first(where: { $0.id == chatID }) { ChatArchive.save(c) }
            }
            guard selectedModel != nil else {
                update(chatID, reply.id) { $0.error = "Сначала установите модель — откройте «Для этого Mac»." }
                return
            }
            guard await loadSelectedModel() else {
                update(chatID, reply.id) { $0.error = "Модель не загрузилась: \(engineErrorText)" }
                return
            }
            var raw = ""
            var startsInReasoning = false
            var lastUI = Date.distantPast
            var thinkingStart: Date?
            let started = Date()
            var tokens = 0
            do {
                for try await event in LlamaEngine.shared.generate(turns, settings: settings) {
                    switch event {
                    case .started(let r):
                        startsInReasoning = r
                        streamingPhase = "Читаю…"
                    case .prompt(let done, let total):
                        if total > 400 { streamingPhase = "Читаю контекст · \(Int(Double(done) / Double(total) * 100))%" }
                    case .text(let t):
                        raw += t
                        tokens += 1
                        let now = Date()
                        if now.timeIntervalSince(lastUI) > 0.045 {
                            lastUI = now
                            let parts = ReplyParser.parse(raw, startsInReasoning: startsInReasoning)
                            if parts.reasoningOpen && thinkingStart == nil { thinkingStart = now }
                            streamingPhase = parts.reasoningOpen ? "Размышляю…" : nil
                            liveTokensPerSecond = Double(tokens) / max(0.2, now.timeIntervalSince(started))
                            let thinkSeconds = thinkingStart.map { now.timeIntervalSince($0) }
                            update(chatID, reply.id) {
                                $0.text = parts.answer
                                $0.reasoning = parts.reasoning.isEmpty ? nil : parts.reasoning
                                if parts.reasoningOpen { $0.thinkingSeconds = thinkSeconds }
                            }
                        }
                    case .finished(let stats):
                        let parts = ReplyParser.parse(raw, startsInReasoning: startsInReasoning)
                        update(chatID, reply.id) {
                            $0.text = parts.answer
                            $0.reasoning = parts.reasoning.isEmpty ? nil : parts.reasoning
                            $0.stats = stats
                            if $0.thinkingSeconds == nil, !parts.reasoning.isEmpty, let ts = thinkingStart { $0.thinkingSeconds = Date().timeIntervalSince(ts) }
                        }
                    }
                }
            } catch {
                update(chatID, reply.id) { $0.error = error.localizedDescription }
            }
        }
    }

    private var engineErrorText: String {
        if case .failed(let s) = engineState { return s }
        return "неизвестная ошибка"
    }

    private func update(_ chatID: UUID, _ messageID: UUID, _ change: (inout ChatMessage) -> Void) {
        guard let ci = conversations.firstIndex(where: { $0.id == chatID }),
              let mi = conversations[ci].messages.firstIndex(where: { $0.id == messageID }) else { return }
        change(&conversations[ci].messages[mi])
        conversations[ci].updated = Date()
    }

    // MARK: - Лента

    var unreadFeed: Int { feed.items.filter { $0.date > feed.lastSeen }.count }

    func markFeedRead() {
        feed.lastSeen = feed.items.first?.date ?? Date()
        feed.save()
    }

    func refreshFeedIfNeeded() async {
        if let last = feed.lastRefresh, Date().timeIntervalSince(last) < 3600, !feed.items.isEmpty { return }
        await refreshFeed()
    }

    func refreshFeed() async {
        guard !feedLoading else { return }
        feedLoading = true
        defer { feedLoading = false }
        let firstRun = feed.items.isEmpty
        let (items, failures) = await FeedSource.fetch(supported: EngineCatalog.architectures, engineTag: EngineCatalog.version)
        guard !items.isEmpty else {
            feedError = "Не удалось обновить ленту. Проверьте подключение к интернету."
            return
        }
        feedError = failures > 6 ? "Часть источников не ответила — лента может быть неполной." : nil
        let known = Set(feed.items.map(\.id))
        let fresh = items.filter { !known.contains($0.id) }
        feed.items = items
        feed.lastRefresh = Date()
        if firstRun {
            // При первом запуске не считаем всё «новым»: непрочитанным остаётся последняя неделя.
            feed.lastSeen = Date().addingTimeInterval(-7 * 86_400)
            feed.notified = Set(items.map(\.id))
        } else {
            notifyAbout(fresh)
        }
        feed.save()
    }

    /// Уведомление о новой готовой модели, которая подойдёт этому Mac.
    private func notifyAbout(_ fresh: [FeedItem]) {
        guard notifyNewModels else { return }
        let good = fresh.filter { item in
            guard item.kind == .ready, item.supported, !item.finetune, !feed.notified.contains(item.id), let p = item.params else { return false }
            let fit = Advisor.estimate(size: Int64(p * 1e9 * 4.89 / 8), params: p, active: item.activeParams, mac: mac)
            return fit.comfortable
        }
        for item in good.prefix(3) {
            feed.notified.insert(item.id)
            notify(title: "Новая модель для вашего Mac", body: "\(item.title) — \(item.subtitle). Установить можно в Synapse одним кликом.")
        }
    }

    private func notify(title: String, body: String) {
        guard !DemoUI.isOn, Bundle.main.bundleIdentifier != nil, !NSApp.isActive else { return }
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            center.add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
        }
    }

    /// Карточка для элемента ленты — чтобы открыть подробности и поставить.
    func card(for item: FeedItem) -> ModelCard? {
        guard let repo = item.kind == .release ? item.ggufRepo : item.repo else { return nil }
        if let c = cards.first(where: { $0.id == repo }) { return c }
        return ModelCard(id: repo, name: item.title, lab: item.lab ?? "", params: item.params ?? 1, activeParams: item.activeParams,
                         architecture: item.architecture, downloads: item.downloads ?? 0, likes: item.likes ?? 0, created: item.date,
                         summary: CardBuilder.autoSummary(lab: item.lab ?? "сообщества", params: item.params ?? 1, active: item.activeParams, tags: [.chat]),
                         tags: [.chat], quality: CardBuilder.estimatedQuality(params: item.params ?? 1, active: item.activeParams, created: item.date))
    }

    // MARK: - Сервер

    func applyAPI() {
        if apiEnabled {
            if server == nil {
                let backend = APIServer.Backend(
                    models: { await MainActor.run { self.installed } },
                    prepare: { name in try await self.prepareForAPI(name) },
                    activity: { _ in Task { @MainActor in self.apiRequests += 1 } })
                server = APIServer(backend: backend) { state in Task { @MainActor in self.apiState = state } }
            }
            server?.start(port: UInt16(clamping: apiPort))
        } else {
            server?.stop()
            server = nil
            apiState = .stopped
        }
    }

    func restartAPI() {
        server?.stop()
        server = nil
        applyAPI()
    }

    private func prepareForAPI(_ name: String?) async throws -> InstalledModel {
        var target = selectedModel ?? installed.first
        if let name, !name.isEmpty {
            let n = name.lowercased()
            target = installed.first { $0.apiName == n || $0.id.lowercased() == n || $0.repo.lowercased() == n }
                ?? installed.first { $0.apiName.hasPrefix(n.split(separator: ":").first.map(String.init) ?? n) }
                ?? target
        }
        guard let target else { throw EngineError.notLoaded }
        if loadedModelID != target.id || modelInfo == nil {
            loadedModelID = target.id
            guard await loadSelectedModel() else { throw EngineError.loadFailed(engineErrorText) }
        }
        return target
    }
}
