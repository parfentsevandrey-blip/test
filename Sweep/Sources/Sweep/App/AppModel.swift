import AppKit
import Observation
import SwiftUI

enum Module: String, CaseIterable, Identifiable {
    case smart, cleanup, privacy, uninstaller, clutter, space, performance, journal

    var id: String { rawValue }

    var title: String {
        switch self {
        case .smart: "Умная проверка"
        case .cleanup: "Очистка"
        case .privacy: "Приватность"
        case .uninstaller: "Программы"
        case .clutter: "Крупные файлы"
        case .space: "Карта диска"
        case .performance: "Скорость"
        case .journal: "Журнал"
        }
    }

    var symbol: String {
        switch self {
        case .smart: "sparkles"
        case .cleanup: "trash.circle.fill"
        case .privacy: "hand.raised.fill"
        case .uninstaller: "square.grid.2x2.fill"
        case .clutter: "doc.on.doc.fill"
        case .space: "chart.pie.fill"
        case .performance: "gauge.with.dots.needle.67percent"
        case .journal: "clock.arrow.circlepath"
        }
    }

    /// Каждый модуль — со своим сиянием фона.
    var palette: [Color] {
        switch self {
        case .smart: [Color(red: 0.1, green: 0.85, blue: 0.7), Color(red: 0.15, green: 0.55, blue: 1), Color(red: 0.55, green: 0.35, blue: 1)]
        case .cleanup: [Color(red: 1, green: 0.35, blue: 0.6), Color(red: 0.75, green: 0.3, blue: 1), Color(red: 1, green: 0.55, blue: 0.4)]
        case .privacy: [Color(red: 0.3, green: 0.75, blue: 0.95), Color(red: 0.25, green: 0.4, blue: 0.95), Color(red: 0.4, green: 0.9, blue: 0.8)]
        case .uninstaller: [Color(red: 0.2, green: 0.6, blue: 1), Color(red: 0.35, green: 0.35, blue: 0.95), Color(red: 0.2, green: 0.85, blue: 1)]
        case .clutter: [Color(red: 1, green: 0.6, blue: 0.2), Color(red: 1, green: 0.4, blue: 0.35), Color(red: 1, green: 0.8, blue: 0.3)]
        case .space: [Color(red: 0.6, green: 0.35, blue: 1), Color(red: 0.95, green: 0.35, blue: 0.75), Color(red: 0.3, green: 0.5, blue: 1)]
        case .performance: [Color(red: 1, green: 0.5, blue: 0.25), Color(red: 1, green: 0.3, blue: 0.4), Color(red: 1, green: 0.75, blue: 0.3)]
        case .journal: [Color(red: 0.45, green: 0.55, blue: 0.7), Color(red: 0.35, green: 0.45, blue: 0.75), Color(red: 0.55, green: 0.65, blue: 0.75)]
        }
    }

    var tint: Color { palette[0] }
}

/// Программа в списке «Программы».
struct AppInfo: Identifiable, Hashable {
    var id: String { bundle.id }
    var bundle: AppBundle
    var size: Int64?
    var lastUsed: Date?

    var unused: Bool { (lastUsed ?? .distantPast).daysBefore(Date()) > 90 }
}

enum ScanPhase: Equatable {
    case idle
    case scanning(Double, String)
    case results
    case cleaning(Double)
    case done(Int64)
}

/// Что сейчас подтверждает пользователь.
struct PendingRemoval: Identifiable {
    let id = UUID()
    var title: String
    var items: [JunkItem]
    var category: String
    var after: (@MainActor (RemovalReport) -> Void)?

    var size: Int64 { items.reduce(0) { $0 + $1.size } }
    var trashCount: Int { items.filter { $0.method == .trash }.count }
    var adminCount: Int { items.filter { $0.method == .admin }.count }
}

@Observable
final class AppModel {
    var module: Module = .smart
    private(set) var fullDiskAccess = Permissions.hasFullDiskAccess()
    private(set) var installed = InstalledIndex()
    var toast: String?
    var pendingRemoval: PendingRemoval?

    // Умная проверка
    private(set) var smartPhase: ScanPhase = .idle
    var smartCategories: [JunkCategory] = []
    private(set) var smartFreedTotal: Int64 = 0

    // Очистка и приватность
    var categories: [CategoryID: JunkCategory] = [:]
    private(set) var cleanupPhase: ScanPhase = .idle
    var selectedCategory: CategoryID = .appCaches
    private(set) var privacyPhase: ScanPhase = .idle

    // Программы
    private(set) var apps: [AppInfo] = []
    private(set) var appsLoading = false
    var selectedAppID: String?
    var leftovers: [JunkItem] = []
    private(set) var leftoversLoading = false
    var orphans: [JunkItem] = []
    private(set) var orphansLoading = false

    // Крупные файлы и дубликаты
    private(set) var clutter = ClutterResult()
    private(set) var clutterPhase: ScanPhase = .idle
    var clutterSelection: Set<String> = []

    // Карта диска
    private(set) var spaceRoot: SpaceNode?
    var spacePath: [SpaceNode] = []
    private(set) var spacePhase: ScanPhase = .idle

    // Скорость
    private(set) var launchItems: [LaunchItem] = []
    private(set) var memory = MemoryStats()
    private(set) var disk = DiskStats()
    private(set) var processes: [ProcessStat] = []
    var maintenanceSelection: Set<MaintenanceTask> = [.flushDNS, .quickLookCache, .rebuildLaunchServices]
    private(set) var maintenanceRunning = false
    private(set) var maintenanceResults: [MaintenanceTask: Bool] = [:]
    private(set) var trashSize: Int64 = 0

    // Журнал
    private(set) var journal: [JournalEntry] = []
    private(set) var ignored: [String] = UserDefaults.standard.stringArray(forKey: "ignoredPaths") ?? []
    var deleteCachesToTrash = UserDefaults.standard.bool(forKey: "cachesToTrash") {
        didSet { UserDefaults.standard.set(deleteCachesToTrash, forKey: "cachesToTrash") }
    }

    @ObservationIgnored private let sampler = SystemStats.ProcessSampler()
    @ObservationIgnored private var statsTask: Task<Void, Never>?
    @ObservationIgnored private var toastTask: Task<Void, Never>?
    let home = FileManager.default.homeDirectoryForCurrentUser
    /// Демо-режим для скриншотов в CI: без запросов доступа к личным папкам.
    let demo = DemoUI.isOn

    static let supportDirectory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("Sweep", isDirectory: true)

    init() {
        loadJournal()
    }

    func start() {
        guard statsTask == nil else { return }
        refreshStats()
        statsTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                self?.refreshStats()
            }
        }
        Task { await reloadInstalled() }
        if demo {
            module = DemoUI.module
            Task { await DemoUI.prepare(self) }
        }
    }

    // MARK: - Контекст сканирования

    func makeContext() -> ScanContext {
        let running = Set(NSWorkspace.shared.runningApplications.compactMap(\.bundleIdentifier))
        let volumes = (FileManager.default.mountedVolumeURLs(includingResourceValuesForKeys: nil, options: [.skipHiddenVolumes]) ?? [])
            .filter { $0.path.hasPrefix("/Volumes/") }
        return ScanContext(home: home, applicationDirs: AppScanner.defaultDirectories(home: home), hasFullDiskAccess: fullDiskAccess,
                           runningBundleIDs: running, installed: installed, ignored: ignored, avoidPrivacyPrompts: demo,
                           volumes: volumes)
    }

    func reloadInstalled() async {
        appsLoading = true
        let dirs = AppScanner.defaultDirectories(home: home)
        let bundles = await Task.detached(priority: .userInitiated) { AppScanner.scan(dirs) }.value
        installed = InstalledIndex(apps: bundles)
        let visible = bundles.filter { !$0.isSystem }
        apps = visible.map { AppInfo(bundle: $0) }
        appsLoading = false
        // Размеры и дата последнего запуска — в фоне, порциями: список оживает сразу, а не после самой большой программы.
        for start in stride(from: 0, to: visible.count, by: 6) {
            let chunk = Array(visible[start..<min(start + 6, visible.count)])
            let sized = await Task.detached(priority: .utility) {
                JunkScanner.parallelMap(chunk) { ($0.id, DiskUsage.size(of: $0.url), AppScanner.lastUsed($0.url)) }
            }.value
            for (id, size, last) in sized {
                if let i = apps.firstIndex(where: { $0.id == id }) {
                    apps[i].size = size
                    apps[i].lastUsed = last
                }
            }
        }
    }

    func refreshPermissions() {
        fullDiskAccess = Permissions.hasFullDiskAccess()
    }

    func refreshStats() {
        memory = SystemStats.memory()
        disk = SystemStats.disk()
        let sampler = self.sampler
        let home = self.home
        let fda = fullDiskAccess
        Task.detached(priority: .utility) {
            let list = sampler.sample()
            let trash = fda ? DiskUsage.size(of: home.appendingPathComponent(".Trash")) : 0
            await MainActor.run { [weak self] in
                self?.processes = list
                self?.trashSize = trash
            }
        }
    }

    // MARK: - Умная проверка

    var smartSelectedSize: Int64 { smartCategories.reduce(0) { $0 + $1.selectedSize } }

    func runSmartScan() {
        guard !isBusy(smartPhase) else { return }
        smartPhase = .scanning(0, "Готовлюсь…")
        smartCategories = []
        refreshPermissions()
        let ctx = makeContext()
        let ids = CategoryID.smart
        Task {
            var done: [JunkCategory] = []
            for (i, id) in ids.enumerated() {
                smartPhase = .scanning(Double(i) / Double(ids.count), id.title)
                let category = await Task.detached(priority: .userInitiated) { JunkScanner.scan(ctx, categories: [id]).first }.value
                if let category { done.append(category) }
                withAnimation(.smooth) { smartCategories = done.filter { !$0.items.isEmpty || $0.notice != nil } }
            }
            withAnimation(.bouncy) { smartPhase = .results }
            loadLaunchItems()
        }
    }

    func cleanSmart() {
        let items = smartCategories.flatMap { $0.items.filter(\.selected) }
        guard !items.isEmpty else { return }
        smartPhase = .cleaning(0)
        perform(items, category: "Умная проверка") { [weak self] report in
            guard let self else { return }
            self.smartFreedTotal = report.freed + report.adminSize
            withAnimation(.bouncy) { self.smartPhase = .done(self.smartFreedTotal) }
            self.smartCategories = []
        }
    }

    func resetSmart() {
        withAnimation(.smooth) { smartPhase = .idle }
    }

    // MARK: - Очистка

    var cleanupCategories: [CategoryID] {
        CategoryID.allCases.filter { $0 != .privacy }
    }

    var cleanupSelectedSize: Int64 { cleanupCategories.reduce(0) { $0 + (categories[$1]?.selectedSize ?? 0) } }

    func scanCleanup() {
        guard !isBusy(cleanupPhase) else { return }
        refreshPermissions()
        let ctx = makeContext()
        let ids = cleanupCategories
        cleanupPhase = .scanning(0, "")
        Task {
            for (i, id) in ids.enumerated() {
                cleanupPhase = .scanning(Double(i) / Double(ids.count), id.title)
                let category = await Task.detached(priority: .userInitiated) { JunkScanner.scan(ctx, categories: [id]).first }.value
                if let category { withAnimation(.smooth) { categories[id] = category } }
            }
            cleanupPhase = .results
            if categories[selectedCategory]?.items.isEmpty ?? true,
               let first = ids.first(where: { !(categories[$0]?.items.isEmpty ?? true) }) {
                selectedCategory = first
            }
        }
    }

    func scanPrivacy() {
        guard !isBusy(privacyPhase) else { return }
        refreshPermissions()
        let ctx = makeContext()
        privacyPhase = .scanning(0.5, "Браузеры")
        Task {
            let category = await Task.detached { JunkScanner.scan(ctx, categories: [.privacy]).first }.value
            if let category { categories[.privacy] = category }
            privacyPhase = .results
        }
    }

    func toggle(_ item: JunkItem, in id: CategoryID, smart: Bool = false) {
        if smart {
            guard let c = smartCategories.firstIndex(where: { $0.id == id }),
                  let i = smartCategories[c].items.firstIndex(where: { $0.id == item.id }) else { return }
            smartCategories[c].items[i].selected.toggle()
        } else {
            guard let i = categories[id]?.items.firstIndex(where: { $0.id == item.id }) else { return }
            categories[id]?.items[i].selected.toggle()
        }
    }

    func setAll(_ id: CategoryID, _ on: Bool, smart: Bool = false) {
        if smart {
            guard let c = smartCategories.firstIndex(where: { $0.id == id }) else { return }
            for i in smartCategories[c].items.indices where smartCategories[c].items[i].risk != .caution || !on {
                smartCategories[c].items[i].selected = on
            }
        } else {
            guard var category = categories[id] else { return }
            for i in category.items.indices where category.items[i].risk != .caution || !on {
                category.items[i].selected = on
            }
            categories[id] = category
        }
    }

    func cleanCategories(_ ids: [CategoryID], title: String) {
        let items = ids.flatMap { categories[$0]?.items.filter(\.selected) ?? [] }
        guard !items.isEmpty else { return }
        pendingRemoval = PendingRemoval(title: title, items: items, category: title) { [weak self] _ in
            guard let self else { return }
            for id in ids {
                categories[id]?.items.removeAll(where: \.selected)
            }
        }
    }

    // MARK: - Удаление

    /// Удаление после подтверждения в листе: в фоне, затем — команды администратора одним запросом.
    func confirmPending() {
        guard let pending = pendingRemoval else { return }
        pendingRemoval = nil
        perform(pending.items, category: pending.category, then: pending.after)
    }

    func perform(_ items: [JunkItem], category: String, then: (@MainActor (RemovalReport) -> Void)? = nil) {
        let home = self.home
        let adjusted = items.map { item -> JunkItem in
            var item = item
            if deleteCachesToTrash && item.method == .delete && item.special == nil { item.method = .trash }
            return item
        }
        Task {
            var report = await Task.detached(priority: .userInitiated) {
                Remover.remove(adjusted, category: category, home: home)
            }.value
            if !report.adminCommands.isEmpty {
                switch Admin.run(report.adminCommands) {
                case .success:
                    report.freed += report.adminSize
                    report.journal += report.adminEntries
                case .failure(let failure):
                    report.failures.append(failure)
                }
            }
            appendJournal(report.journal)
            then?(report)
            refreshStats()
            if report.failures.isEmpty {
                showToast("Освобождено \(Format.bytes(report.freed))")
            } else {
                showToast("Освобождено \(Format.bytes(report.freed)) · не удалось: \(report.failures.count)")
            }
        }
    }

    // MARK: - Программы

    var selectedApp: AppInfo? { apps.first { $0.id == selectedAppID } }

    func selectApp(_ id: String?) {
        selectedAppID = id
        leftovers = []
        guard let app = selectedApp?.bundle else { return }
        leftoversLoading = true
        let ctx = makeContext()
        Task {
            let found = await Task.detached(priority: .userInitiated) { Leftovers.find(for: app, ctx) }.value
            guard selectedAppID == app.id else { return }
            leftovers = found
            leftoversLoading = false
        }
    }

    func toggleLeftover(_ item: JunkItem) {
        if let i = leftovers.firstIndex(where: { $0.id == item.id }) { leftovers[i].selected.toggle() }
    }

    /// Удалить программу (в Корзину) вместе с выбранными следами. `keepApp` — «сбросить»: данные удаляются, программа остаётся.
    func uninstall(keepApp: Bool = false) {
        guard let app = selectedApp else { return }
        var items = leftovers.filter(\.selected)
        if !keepApp {
            items.insert(JunkItem(url: app.bundle.url, targets: [app.bundle.url], title: app.bundle.name, detail: app.bundle.url.path,
                                  size: app.size ?? 0, modified: nil, selected: true, risk: .safe, method: .trash,
                                  bundleID: app.bundle.bundleID, group: "Программа"), at: 0)
        }
        guard !items.isEmpty else { return }
        pendingRemoval = PendingRemoval(title: keepApp ? "Сбросить «\(app.bundle.name)»" : "Удалить «\(app.bundle.name)»",
                                        items: items, category: "Программы") { [weak self] _ in
            guard let self else { return }
            if !keepApp {
                withAnimation(.smooth) { apps.removeAll { $0.id == app.id } }
                selectedAppID = nil
                Task { await self.reloadInstalled() }
            }
            leftovers = []
        }
    }

    func quit(_ app: AppBundle) {
        for running in NSRunningApplication.runningApplications(withBundleIdentifier: app.bundleID) {
            running.terminate()
        }
    }

    func isRunning(_ app: AppBundle) -> Bool {
        !NSRunningApplication.runningApplications(withBundleIdentifier: app.bundleID).isEmpty
    }

    func scanOrphans() {
        guard !orphansLoading else { return }
        orphansLoading = true
        let ctx = makeContext()
        Task {
            orphans = await Task.detached { Leftovers.orphans(ctx) }.value.sorted { $0.size > $1.size }
            orphansLoading = false
        }
    }

    func cleanOrphans() {
        let items = orphans.filter(\.selected)
        guard !items.isEmpty else { return }
        pendingRemoval = PendingRemoval(title: "Остатки удалённых программ", items: items, category: "Остатки") { [weak self] _ in
            self?.orphans.removeAll(where: \.selected)
        }
    }

    /// Программа, перетащенная в окно.
    func openApp(_ url: URL) {
        module = .uninstaller
        if let app = apps.first(where: { $0.bundle.url.standardizedFileURL == url.standardizedFileURL }) {
            selectApp(app.id)
        } else if let bundle = AppScanner.read(url) {
            apps.insert(AppInfo(bundle: bundle, size: DiskUsage.size(of: url)), at: 0)
            selectApp(bundle.id)
        }
    }

    // MARK: - Крупные файлы

    func scanClutter() {
        guard !isBusy(clutterPhase) else { return }
        clutterPhase = .scanning(0, "Ищу крупные файлы…")
        clutterSelection = []
        let ctx = makeContext()
        Task {
            let result = await Task.detached(priority: .userInitiated) {
                ClutterScanner.scan(ctx) { n in
                    Task { @MainActor [weak self] in self?.clutterPhase = .scanning(0, "Просмотрено \(n) файлов…") }
                }
            }.value
            clutter = result
            // В каждой группе дубликатов заранее отмечены все копии, кроме одной — оригинала.
            for group in result.duplicates {
                let keep = ClutterScanner.keeper(in: group, home: home)
                for file in group.files where file.id != keep?.id { clutterSelection.insert(file.id) }
            }
            clutterPhase = .results
        }
    }

    /// Нельзя отметить все копии дубликата: одна всегда остаётся.
    func toggleClutter(_ file: FileEntry) {
        if clutterSelection.contains(file.id) {
            clutterSelection.remove(file.id)
            return
        }
        if let group = clutter.duplicates.first(where: { $0.files.contains(file) }) {
            let others = group.files.filter { $0.id != file.id }
            if others.allSatisfy({ clutterSelection.contains($0.id) }) {
                showToast("Хотя бы одна копия должна остаться")
                return
            }
        }
        clutterSelection.insert(file.id)
    }

    func cleanClutter() {
        let all = clutter.large + clutter.old + clutter.duplicates.flatMap(\.files)
        var seen = Set<String>()
        let files = all.filter { clutterSelection.contains($0.id) && seen.insert($0.id).inserted }
        let items = files.map {
            JunkItem(url: $0.url, targets: [$0.url], title: $0.url.lastPathComponent, detail: Format.path($0.url, home: home),
                     size: $0.size, modified: $0.modified, selected: true, risk: .review, method: .trash)
        }
        guard !items.isEmpty else { return }
        pendingRemoval = PendingRemoval(title: "Файлы в Корзину", items: items, category: "Крупные файлы") { [weak self] report in
            guard let self else { return }
            let removed = Set(report.journal.map(\.original))
            clutter.large.removeAll { removed.contains($0.url.path) }
            clutter.old.removeAll { removed.contains($0.url.path) }
            for i in clutter.duplicates.indices { clutter.duplicates[i].files.removeAll { removed.contains($0.url.path) } }
            clutter.duplicates.removeAll { $0.files.count < 2 }
            clutterSelection.subtract(removed)
        }
    }

    // MARK: - Карта диска

    func scanSpace(_ root: URL? = nil) {
        guard !isBusy(spacePhase) else { return }
        let target = root ?? home
        spacePhase = .scanning(0, "Измеряю «\(target.lastPathComponent)»…")
        let ctx = makeContext()
        Task {
            let node = await Task.detached(priority: .userInitiated) {
                SpaceScanner.scan(target, ctx: ctx) { n in
                    Task { @MainActor [weak self] in self?.spacePhase = .scanning(0, "Посчитано \(n) файлов…") }
                }
            }.value
            spaceRoot = node
            spacePath = [node]
            spacePhase = .results
        }
    }

    func trashSpaceNode(_ node: SpaceNode) {
        let verdict = Safety.verdict(for: node.url, home: home)
        guard !verdict.isForbidden, !node.isRest else {
            showToast(verdict.reason ?? "Это нельзя удалить")
            return
        }
        let item = JunkItem(url: node.url, targets: [node.url], title: node.name, detail: Format.path(node.url, home: home),
                            size: node.size, modified: nil, selected: true, risk: .review, method: .trash)
        pendingRemoval = PendingRemoval(title: "В Корзину", items: [item], category: "Карта диска") { [weak self] _ in
            guard let self else { return }
            spacePath = spacePath.map { Self.removing(node, from: $0) }
            if let first = spacePath.first { spaceRoot = first }
        }
    }

    static func removing(_ target: SpaceNode, from node: SpaceNode) -> SpaceNode {
        var n = node
        if n.children.contains(where: { $0.id == target.id }) {
            n.children.removeAll { $0.id == target.id }
            n.size -= target.size
            return n
        }
        n.children = n.children.map { removing(target, from: $0) }
        n.size = n.children.isEmpty ? n.size : n.children.reduce(0) { $0 + $1.size }
        return n
    }

    // MARK: - Скорость

    func loadLaunchItems() {
        let home = self.home, installed = self.installed
        Task {
            launchItems = await Task.detached { LaunchItems.scan(home: home, installed: installed) }.value
        }
    }

    func setLaunchItem(_ item: LaunchItem, enabled: Bool) {
        switch LaunchItems.setEnabled(item, enabled, home: home) {
        case .success:
            showToast(enabled ? "«\(item.ownerName ?? item.label)» снова запускается" : "Автозапуск «\(item.ownerName ?? item.label)» выключен")
        case .failure(let f):
            showToast(f.reason)
        }
        loadLaunchItems()
    }

    func runMaintenance() {
        let tasks = MaintenanceTask.allCases.filter { maintenanceSelection.contains($0) }
        guard !tasks.isEmpty else { return }
        maintenanceRunning = true
        maintenanceResults = [:]
        Task {
            var results = await Task.detached { MaintenanceTask.runUserTasks(tasks) }.value
            let admin = tasks.filter(\.needsAdmin)
            if !admin.isEmpty {
                let ok: Bool
                if case .success = Admin.run(admin.map(\.command)) { ok = true } else { ok = false }
                for t in admin { results[t] = ok }
            }
            maintenanceResults = results
            maintenanceRunning = false
            refreshStats()
            let failed = results.values.filter { !$0 }.count
            showToast(failed == 0 ? "Обслуживание выполнено" : "Выполнено, но с ошибками: \(failed)")
        }
    }

    func quitProcess(_ p: ProcessStat) {
        if let app = NSRunningApplication(processIdentifier: p.pid) {
            app.terminate()
        }
    }

    // MARK: - Журнал и исключения

    private var journalURL: URL { Self.supportDirectory.appendingPathComponent("journal.json") }

    private func loadJournal() {
        guard let data = try? Data(contentsOf: journalURL),
              let list = try? JSONDecoder().decode([JournalEntry].self, from: data) else { return }
        journal = list
    }

    private func appendJournal(_ entries: [JournalEntry]) {
        guard !entries.isEmpty else { return }
        journal = Array((entries.reversed() + journal).prefix(5000))
        saveJournal()
    }

    private func saveJournal() {
        try? FileManager.default.createDirectory(at: Self.supportDirectory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(journal) { try? data.write(to: journalURL, options: .atomic) }
    }

    func restore(_ entry: JournalEntry) {
        switch Remover.restore(entry) {
        case .success:
            if let i = journal.firstIndex(where: { $0.id == entry.id }) { journal[i].restored = true }
            saveJournal()
            showToast("«\(URL(fileURLWithPath: entry.original).lastPathComponent)» возвращён на место")
        case .failure(let f):
            showToast(f.reason)
        }
    }

    func ignore(_ url: URL) {
        guard !ignored.contains(url.path) else { return }
        ignored.append(url.path)
        UserDefaults.standard.set(ignored, forKey: "ignoredPaths")
        for id in categories.keys { categories[id]?.items.removeAll { $0.url == url } }
        for c in smartCategories.indices { smartCategories[c].items.removeAll { $0.url == url } }
        orphans.removeAll { $0.url == url }
        showToast("«\(url.lastPathComponent)» больше не будет предлагаться к удалению")
    }

    func unignore(_ path: String) {
        ignored.removeAll { $0 == path }
        UserDefaults.standard.set(ignored, forKey: "ignoredPaths")
    }

    // MARK: - Разное

    func isBusy(_ phase: ScanPhase) -> Bool {
        switch phase {
        case .scanning, .cleaning: true
        default: false
        }
    }

    func showToast(_ text: String) {
        withAnimation(.bouncy) { toast = text }
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3.5))
            guard !Task.isCancelled else { return }
            withAnimation(.smooth) { self?.toast = nil }
        }
    }

    func emptyTrashWithFinder() {
        let script = NSAppleScript(source: "tell application \"Finder\" to empty trash")
        var error: NSDictionary?
        script?.executeAndReturnError(&error)
        showToast(error == nil ? "Корзина очищена" : "Finder не разрешил очистить Корзину")
        refreshStats()
    }
}
