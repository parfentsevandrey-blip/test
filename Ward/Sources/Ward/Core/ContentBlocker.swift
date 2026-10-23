import CryptoKit
import Foundation
import WebKit

/// Блокировщик: списки фильтров → скомпилированные правила WebKit.
///
/// Правила применяет сам движок WebKit ещё до запроса — страница не может их обойти, а блокировка
/// не тормозит загрузку (так же работают блокировщики Safari). Скомпилированные правила хранятся
/// на диске: при следующем запуске они загружаются мгновенно, пересобираются только когда изменились списки.
@Observable
final class ContentBlocker {
    enum Phase: Equatable {
        case idle
        case preparing(String)
        case ready
        case failed(String)
    }

    /// Итоги сборки — для настроек и самопроверки.
    nonisolated struct Report: Codable, Sendable, Equatable {
        var lists = 0
        var lines = 0
        var rules = 0
        var network = 0
        var cosmetic = 0
        var skipped = 0
        var invalidSelectors = 0
        var dropped = 0
        var seconds = 0.0
        var built = Date()
    }

    nonisolated struct Manifest: Codable, Sendable {
        var key: String
        var identifiers: [String]
        var report: Report
    }

    private(set) var phase: Phase = .idle
    private(set) var ruleLists: [WKContentRuleList] = []
    private(set) var index = ProtectionIndex() { didSet { cleaner = index.cleaner } }
    /// Очиститель ссылок с метками из списков (собирается один раз, а не на каждый переход).
    private(set) var cleaner = LinkCleaner()
    private(set) var report: Report?
    /// Правила скрытия по сайтам — для счётчика скрытых элементов.
    private(set) var cosmetic = CosmeticIndex()
    /// Правила загружены (или собраны) — можно открывать страницы.
    var isReady: Bool { phase == .ready }
    /// Вызывается, когда правила поменялись: окна переприменяют их к вкладкам.
    var onChange: (() -> Void)?

    let folder: URL
    let store: WKContentRuleListStore
    private var running: Task<Void, Never>?
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var probes = 0

    /// Версия формата правил: при изменении компилятора всё собирается заново.
    nonisolated static let formatVersion = "5"

    init(folder: URL = AppPaths.rules) {
        self.folder = folder
        store = WKContentRuleListStore(url: folder) ?? WKContentRuleListStore.default()
    }

    /// Подготовить правила для этих списков. Повторный вызов отменяет незаконченную сборку.
    func prepare(lists: [FilterList], force: Bool = false) {
        running?.cancel()
        running = Task { await build(lists: lists, force: force) }
    }

    /// Дождаться готовности (или ошибки), не дольше `timeout` секунд.
    func waitReady(timeout: Double = 60) async {
        if phase == .ready { return }
        if case .failed = phase { return }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            waiters.append(c)
            Task {
                try? await Task.sleep(for: .seconds(timeout))
                self.wakeWaiters()
            }
        }
    }

    private func wakeWaiters() {
        let list = waiters
        waiters.removeAll()
        list.forEach { $0.resume() }
    }

    func build(lists: [FilterList], force: Bool) async {
        let started = Date()
        phase = .preparing("Читаю списки фильтров…")
        let key = await Task.detached { Self.sourceKey(lists) }.value

        // Ничего не изменилось — берём готовые правила из хранилища.
        if !force, let manifest = loadManifest(), manifest.key == key,
           let index = loadIndex(), let loaded = await lookUp(manifest.identifiers) {
            finish(loaded, index: index, report: manifest.report)
            let url = cosmeticURL
            cosmetic = await Task.detached {
                (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode(CosmeticIndex.self, from: $0) } ?? CosmeticIndex()
            }.value
            return
        }
        if Task.isCancelled { return }

        phase = .preparing("Разбираю \(lists.count) \(Format.plural(lists.count, "список", "списка", "списков"))…")
        let set = await Task.detached { FilterEngine.parse(lists: lists) }.value
        if Task.isCancelled { return }
        index = ProtectionIndex(set)

        phase = .preparing("Проверяю правила скрытия…")
        let selectors = await Task.detached { RuleCompiler.selectors(set) }.value
        let invalid = await Self.invalidSelectors(selectors)
        let valid = Set(selectors).subtracting(invalid)
        if Task.isCancelled { return }
        let cosmeticURL = self.cosmeticURL
        let cosmetic = await Task.detached {
            let c = CosmeticIndex(set, valid: valid)
            if let data = try? JSONEncoder().encode(c) { try? data.write(to: cosmeticURL, options: .atomic) }
            return c
        }.value
        self.cosmetic = cosmetic

        phase = .preparing("Готовлю правила…")
        let built = await Task.detached { FilterEngine.build(lists: lists, validSelectors: valid, parsed: set) }.value
        var compiled: [WKContentRuleList] = []
        var identifiers: [String] = []
        var total = 0
        var dropped = 0
        for (i, chunk) in built.chunks.enumerated() {
            if Task.isCancelled { return }
            phase = .preparing("Собираю правила: часть \(i + 1) из \(built.chunks.count)")
            let id = "ward-\(built.fingerprint)-\(chunk.name)"
            switch await compile(id, chunk.json) {
            case .success(let list):
                compiled.append(list)
                identifiers.append(id)
                total += chunk.count
            case .failure(let error):
                print("Ward: часть \(chunk.name) не собралась (\(error.localizedDescription)) — ищу неверные правила")
                let (list, lost, kept) = await repair(chunk, id: id)
                dropped += lost
                if let list {
                    compiled.append(list)
                    identifiers.append(id)
                    total += kept
                }
            }
        }
        guard !compiled.isEmpty else {
            phase = .failed("Не удалось собрать правила блокировки")
            wakeWaiters()
            return
        }
        let report = Report(
            lists: lists.count, lines: set.stats.lines, rules: total, network: set.stats.network, cosmetic: set.stats.cosmetic,
            skipped: set.stats.skipped, invalidSelectors: invalid.count, dropped: dropped,
            seconds: Date().timeIntervalSince(started), built: Date())
        saveIndex(ProtectionIndex(set))
        saveManifest(Manifest(key: key, identifiers: identifiers, report: report))
        await removeStale(keeping: Set(identifiers))
        finish(compiled, index: ProtectionIndex(set), report: report)
    }

    private func finish(_ lists: [WKContentRuleList], index: ProtectionIndex, report: Report) {
        ruleLists = lists
        self.index = index
        self.report = report
        phase = .ready
        wakeWaiters()
        onChange?()
    }

    // MARK: - Компиляция

    nonisolated private final class Outcome: @unchecked Sendable {
        var list: WKContentRuleList?
        var error: Error?
    }

    enum CompileResult {
        case success(WKContentRuleList)
        case failure(Error)
    }

    func compile(_ id: String, _ json: String) async -> CompileResult {
        let outcome: Outcome = await withCheckedContinuation { (c: CheckedContinuation<Outcome, Never>) in
            store.compileContentRuleList(forIdentifier: id, encodedContentRuleList: json) { list, error in
                let o = Outcome()
                o.list = list
                o.error = error
                c.resume(returning: o)
            }
        }
        if let list = outcome.list { return .success(list) }
        return .failure(outcome.error ?? NSError(domain: "Ward", code: 1))
    }

    private func lookUp(_ identifiers: [String]) async -> [WKContentRuleList]? {
        var result: [WKContentRuleList] = []
        for id in identifiers {
            let outcome: Outcome = await withCheckedContinuation { (c: CheckedContinuation<Outcome, Never>) in
                store.lookUpContentRuleList(forIdentifier: id) { list, error in
                    let o = Outcome()
                    o.list = list
                    o.error = error
                    c.resume(returning: o)
                }
            }
            guard let list = outcome.list else { return nil }
            result.append(list)
        }
        return result.isEmpty ? nil : result
    }

    private func removeStale(keeping: Set<String>) async {
        let ids: [String] = await withCheckedContinuation { (c: CheckedContinuation<[String], Never>) in
            store.getAvailableContentRuleListIdentifiers { c.resume(returning: $0 ?? []) }
        }
        for id in ids where !keeping.contains(id) {
            await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                store.removeContentRuleList(forIdentifier: id) { _ in c.resume() }
            }
        }
    }

    /// Часть не собралась: делением пополам находим правила, которые WebKit не принимает, и собираем без них.
    private func repair(_ chunk: RuleChunk, id: String) async -> (WKContentRuleList?, lost: Int, kept: Int) {
        guard let rules = try? JSONSerialization.jsonObject(with: Data(chunk.json.utf8)) as? [Any] else { return (nil, chunk.count, 0) }
        probes = 0
        let bad = await findBad(rules, 0..<rules.count)
        guard let bad else { return (nil, chunk.count, 0) }
        let kept = rules.enumerated().filter { !bad.contains($0.offset) }.map(\.element)
        guard let data = try? JSONSerialization.data(withJSONObject: kept) else { return (nil, chunk.count, 0) }
        if case .success(let list) = await compile(id, String(decoding: data, as: UTF8.self)) {
            print("Ward: в части \(chunk.name) отброшено \(bad.count) правил")
            return (list, bad.count, kept.count)
        }
        return (nil, chunk.count, 0)
    }

    /// nil — неверных правил слишком много, часть не спасти.
    private func findBad(_ rules: [Any], _ range: Range<Int>) async -> Set<Int>? {
        probes += 1
        guard probes < 600 else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: Array(rules[range])) else { return Set(range) }
        if case .success = await compile("ward-probe", String(decoding: data, as: UTF8.self)) { return [] }
        if range.count == 1 { return [range.lowerBound] }
        let mid = range.lowerBound + range.count / 2
        guard let left = await findBad(rules, range.lowerBound..<mid) else { return nil }
        guard let right = await findBad(rules, mid..<range.upperBound) else { return nil }
        return left.union(right)
    }

    /// Селекторы, которые движок страниц не понимает (синтаксис uBlock и AdGuard, которого нет в CSS).
    static func invalidSelectors(_ selectors: [String]) async -> Set<String> {
        guard !selectors.isEmpty else { return [] }
        let page = HiddenPage()
        _ = await page.loadHTML("<!doctype html><html><body></body></html>")
        let result = await page.run(Scripts.validateSelectors, arguments: ["list": selectors])
        return Set((result as? [String]) ?? [])
    }

    // MARK: - Хранение

    private var manifestURL: URL { folder.appendingPathComponent("manifest.json") }
    private var indexURL: URL { folder.appendingPathComponent("index.json") }
    private var cosmeticURL: URL { folder.appendingPathComponent("cosmetic.json") }

    private func loadManifest() -> Manifest? {
        guard let data = try? Data(contentsOf: manifestURL) else { return nil }
        return try? JSONDecoder().decode(Manifest.self, from: data)
    }

    private func saveManifest(_ m: Manifest) {
        if let data = try? JSONEncoder().encode(m) { try? data.write(to: manifestURL, options: .atomic) }
    }

    private func loadIndex() -> ProtectionIndex? {
        guard let data = try? Data(contentsOf: indexURL) else { return nil }
        return try? JSONDecoder().decode(ProtectionIndex.self, from: data)
    }

    private func saveIndex(_ index: ProtectionIndex) {
        if let data = try? JSONEncoder().encode(index) { try? data.write(to: indexURL, options: .atomic) }
    }

    /// Отпечаток источников: версия формата, включённые списки и их содержимое.
    nonisolated static func sourceKey(_ lists: [FilterList]) -> String {
        var hasher = SHA256()
        hasher.update(data: Data((formatVersion + "|" + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "")).utf8))
        for list in lists.sorted(by: { $0.id < $1.id }) {
            hasher.update(data: Data(list.id.utf8))
            if let text = FilterStorage.text(list) { hasher.update(data: Data(text.utf8)) }
        }
        hasher.update(data: Data((WardFilters.ads + WardFilters.tracking).utf8))
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// Для самопроверок: добавить в индекс правила, которых нет в списках (например, для localhost).
    func injectForTest(_ change: (inout ProtectionIndex) -> Void) {
        change(&index)
    }

    /// Применить правила к вкладке (или снять их, если защита на сайте выключена).
    func apply(to controller: WKUserContentController, enabled: Bool) {
        controller.removeAllContentRuleLists()
        guard enabled else { return }
        for list in ruleLists { controller.add(list) }
    }
}

/// Невидимая страница: для проверки селекторов и самопроверок.
final class HiddenPage: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    private var waiter: CheckedContinuation<Bool, Never>?
    private(set) var lastError: Error?

    init(configuration: WKWebViewConfiguration = WKWebViewConfiguration()) {
        webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 1000, height: 800), configuration: configuration)
        super.init()
        webView.navigationDelegate = self
    }

    func loadHTML(_ html: String, baseURL: URL? = nil, timeout: Double = 20) async -> Bool {
        await wait(timeout) { self.webView.loadHTMLString(html, baseURL: baseURL) }
    }

    func load(_ url: URL, timeout: Double = 30) async -> Bool {
        await wait(timeout) { self.webView.load(URLRequest(url: url)) }
    }

    private var generation = 0

    private func wait(_ timeout: Double, _ start: () -> Void) async -> Bool {
        finish(false)
        lastError = nil
        generation += 1
        let current = generation
        return await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            waiter = c
            start()
            Task {
                try? await Task.sleep(for: .seconds(timeout))
                if self.generation == current { self.finish(false) }
            }
        }
    }

    private func finish(_ ok: Bool) {
        let w = waiter
        waiter = nil
        w?.resume(returning: ok)
    }

    func run(_ js: String, arguments: [String: Any] = [:], world: WKContentWorld = .page) async -> Any? {
        do {
            return try await webView.callAsyncJavaScript(js, arguments: arguments, in: nil, contentWorld: world)
        } catch {
            lastError = error
            print("Ward: скрипт не выполнился: \(error.localizedDescription)")
            return nil
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { finish(true) }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { lastError = error; finish(false) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { lastError = error; finish(false) }
}
