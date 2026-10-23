import AppKit
import Observation
import ServiceManagement
import SwiftUI
import UserNotifications

/// Состояние приложения каталога на этом Mac.
enum AppState: Equatable {
    case notInstalled
    case installed(AppVersion)
    case update(from: AppVersion, to: AppVersion)
    case working(String, Double?)
    case comingSoon

    var isWorking: Bool { if case .working = self { true } else { false } }
}

enum ShelfSection: String, CaseIterable, Identifiable {
    case discover, updates, installed

    var id: String { rawValue }

    var title: String {
        switch self {
        case .discover: "Все приложения"
        case .updates: "Обновления"
        case .installed: "Установленные"
        }
    }

    var symbol: String {
        switch self {
        case .discover: "square.grid.2x2"
        case .updates: "arrow.down.circle"
        case .installed: "checkmark.seal"
        }
    }
}

/// Каталог, установленные версии и автообновление.
@Observable
final class Store {
    static let defaultCatalog = URL(string: "https://github.com/parfentsevandrey-blip/test/releases/download/catalog/catalog.json")!

    private(set) var catalog: Catalog?
    private(set) var states: [String: AppState] = [:]
    private(set) var lastCheck: Date?
    private(set) var loadError: String?
    private(set) var checking = false
    var section: ShelfSection = .discover
    var selectedID: String?
    var alert: HarborError?
    var toast: String?

    var autoUpdate: Bool {
        didSet { UserDefaults.standard.set(autoUpdate, forKey: "autoUpdate") }
    }
    var onboarded: Bool {
        didSet { if !DemoUI.isOn { UserDefaults.standard.set(onboarded, forKey: "onboarded") } }
    }

    @ObservationIgnored let installer: Installer
    @ObservationIgnored private var staged: [String: StagedUpdate] = [:]
    @ObservationIgnored private var loopTask: Task<Void, Never>?
    @ObservationIgnored private var toastTask: Task<Void, Never>?
    @ObservationIgnored private var observers: [NSObjectProtocol] = []

    var catalogURL: URL {
        if let env = ProcessInfo.processInfo.environment["HARBOR_CATALOG_URL"], let url = URL(string: env) { return url }
        return UserDefaults.standard.string(forKey: "catalogURL").flatMap(URL.init(string:)) ?? Self.defaultCatalog
    }

    init() {
        let defaults = UserDefaults.standard
        defaults.register(defaults: ["autoUpdate": true])
        autoUpdate = defaults.bool(forKey: "autoUpdate")
        onboarded = defaults.bool(forKey: "onboarded")
        // Устанавливаем только то, что подписано тем же сертификатом, что и сам Harbor.
        installer = Installer(signer: CodeCheck.signer(of: Bundle.main.bundleURL))
    }

    var apps: [CatalogApp] { catalog?.apps.filter { $0.bundleID != Bundle.main.bundleIdentifier || $0.available } ?? [] }

    var updates: [CatalogApp] { apps.filter { if case .update = state($0) { true } else { false } } }

    var installedApps: [CatalogApp] {
        apps.filter {
            switch state($0) {
            case .installed, .update: true
            case .working: !DemoUI.isOn && Locations.installed(bundleID: $0.bundleID, name: $0.name) != nil
            default: false
            }
        }
    }

    var selected: CatalogApp? { apps.first { $0.id == selectedID } }

    func state(_ app: CatalogApp) -> AppState { states[app.id] ?? .notInstalled }

    // MARK: - Запуск и проверка

    func start() {
        guard loopTask == nil else { return }
        if DemoUI.isOn {
            catalog = DemoUI.catalog()
            states = DemoUI.states(catalog?.apps ?? [])
            onboarded = DemoUI.onboardingPage == nil
            selectedID = DemoUI.selected
            if DemoUI.showsUpdates { section = .updates }
            return
        }
        let center = NSWorkspace.shared.notificationCenter
        observers.append(center.addObserver(forName: NSWorkspace.didTerminateApplicationNotification, object: nil, queue: .main) { [weak self] note in
            let id = (note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication)?.bundleIdentifier
            MainActor.assumeIsolated { self?.appTerminated(id) }
        })
        observers.append(center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { Task { await self?.check() } }
        })
        loopTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.check()
                try? await Task.sleep(for: .seconds(4 * 3600))
            }
        }
        _ = Task { try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) }
    }

    /// Загружает каталог, сверяет с установленным и (если включено) обновляет сам.
    func check() async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        do {
            var request = URLRequest(url: catalogURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
            request.setValue("Harbor", forHTTPHeaderField: "User-Agent")
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode != 200 { throw HarborError("Каталог недоступен (\(http.statusCode))") }
            catalog = try Catalog.decode(data)
            loadError = nil
            lastCheck = Date()
        } catch {
            loadError = error.localizedDescription
        }
        refreshStates()
        updateDockBadge()
        if autoUpdate && onboarded { await autoUpdateAll() }
    }

    func refreshStates() {
        guard let catalog else { return }
        for app in catalog.apps where !(states[app.id]?.isWorking ?? false) {
            states[app.id] = computeState(app)
        }
    }

    private func computeState(_ app: CatalogApp) -> AppState {
        let installed = Locations.installed(bundleID: app.bundleID, name: app.name).flatMap { AppVersion(bundle: $0) }
        guard app.isInstallable, let latest = app.latest else {
            return installed.map { .installed($0) } ?? .comingSoon
        }
        guard let installed else { return .notInstalled }
        return installed < latest ? .update(from: installed, to: latest) : .installed(installed)
    }

    // MARK: - Действия

    func install(_ app: CatalogApp) async {
        await perform(app, label: "Загрузка") { store in
            let staged = try await store.stageWithProgress(app)
            store.states[app.id] = .working("Установка", nil)
            let result = try await store.installer.install(staged)
            store.finished(app, result)
        }
    }

    func updateAll() async {
        for app in updates { await install(app) }
    }

    func open(_ app: CatalogApp) {
        guard let url = Locations.installed(bundleID: app.bundleID, name: app.name) else { return }
        Task { _ = await AppControl.launch(url); NSRunningApplication.runningApplications(withBundleIdentifier: app.bundleID).first?.activate() }
    }

    func reveal(_ app: CatalogApp) {
        guard let url = Locations.installed(bundleID: app.bundleID, name: app.name) else { return }
        NSWorkspace.shared.activateFileViewerSelecting([url])
    }

    func rollback(_ app: CatalogApp) async {
        await perform(app, label: "Возврат") { store in
            let version = try await store.installer.rollback(app)
            store.showToast("\(app.name): возвращена версия \(version?.description ?? "")")
        }
    }

    func uninstall(_ app: CatalogApp, removeData: Bool) async {
        await perform(app, label: "Удаление") { store in
            try await store.installer.uninstall(app, removeData: removeData)
            store.showToast(removeData ? "\(app.name) и его данные — в Корзине" : "\(app.name) — в Корзине, данные сохранены")
        }
    }

    func hasBackup(_ app: CatalogApp) async -> Bool { await installer.backup(for: app.bundleID) != nil }

    private func perform(_ app: CatalogApp, label: String, _ body: (Store) async throws -> Void) async {
        guard !state(app).isWorking else { return }
        states[app.id] = .working(label, nil)
        do {
            try await body(self)
        } catch let e as HarborError {
            alert = e
        } catch {
            alert = HarborError(error.localizedDescription)
        }
        states[app.id] = computeState(app)
        updateDockBadge()
    }

    private func stageWithProgress(_ app: CatalogApp) async throws -> StagedUpdate {
        if let ready = staged.removeValue(forKey: app.id), ready.version == app.latest,
           FileManager.default.fileExists(atPath: ready.bundle.path) { return ready }
        let id = app.id
        return try await installer.stage(app) { p in
            Task { @MainActor [weak self] in
                if case .working(let label, _) = self?.states[id] { self?.states[id] = .working(label, p) }
            }
        }
    }

    private func finished(_ app: CatalogApp, _ result: InstallResult) {
        let version = AppVersion(bundle: result.location)?.description ?? ""
        if app.bundleID == Bundle.main.bundleIdentifier {
            AppControl.relaunchSelf(at: result.location)
            NSApp.terminate(nil)
            return
        }
        let text = result.previous == nil ? "\(app.name) \(version) установлен" : "\(app.name) обновлён до \(version)"
        showToast(text)
        notify(text, body: result.relaunched ? "Приложение перезапущено, данные на месте." : "Все настройки и данные на месте.")
    }

    // MARK: - Автообновление

    /// Незапущенные приложения обновляются сразу; запущенные — когда их закроют
    /// (новая версия уже скачана и проверена, замена занимает доли секунды).
    private func autoUpdateAll() async {
        for app in updates {
            if app.bundleID == Bundle.main.bundleIdentifier {
                // Сам Harbor — когда его окно не на экране, чтобы перезапуск не мешал.
                if !NSApp.windows.contains(where: { $0.isVisible && $0.isKeyWindow }) { await install(app) }
                continue
            }
            if AppControl.running(bundleID: app.bundleID).isEmpty {
                await install(app)
            } else if staged[app.id] == nil {
                states[app.id] = .working("Загрузка", nil)
                do {
                    staged[app.id] = try await stageWithProgress(app)
                    notify("Обновление \(app.name) готово", body: "Установится, когда вы закроете \(app.name). Данные и настройки сохранятся.")
                } catch {
                    loadError = error.localizedDescription
                }
                states[app.id] = computeState(app)
            }
        }
    }

    private func appTerminated(_ bundleID: String?) {
        guard let bundleID, let app = apps.first(where: { $0.bundleID == bundleID }) else { return }
        if autoUpdate, staged[app.id] != nil, case .update = computeState(app) {
            Task { await install(app) }
        } else {
            states[app.id] = computeState(app)
        }
    }

    // MARK: - Система

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            do { newValue ? try SMAppService.mainApp.register() : try SMAppService.mainApp.unregister() } catch {
                alert = HarborError("Не удалось изменить запуск при входе: \(error.localizedDescription)")
            }
        }
    }

    /// Убирает метку карантина с уже установленных приложений каталога (ставили вручную из DMG).
    func clearQuarantine() -> Int {
        var count = 0
        for app in apps {
            guard let url = Locations.installed(bundleID: app.bundleID, name: app.name), Quarantine.has(url) else { continue }
            if Quarantine.remove(from: url) > 0 { count += 1 }
        }
        return count
    }

    static func openAppManagementSettings() {
        let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_AppBundles")!
        NSWorkspace.shared.open(url)
    }

    private func updateDockBadge() {
        let n = updates.count
        NSApp.dockTile.badgeLabel = n > 0 && !autoUpdate ? "\(n)" : nil
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

    private func notify(_ title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil))
    }
}
