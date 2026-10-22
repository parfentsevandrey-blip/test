import AppKit
import Network
import Observation
import ServiceManagement
import SwiftUI
import VeilShared

nonisolated struct AppSettings: Codable, Hashable, Sendable {
    var connectOnLaunch = false
    /// Сервер перестал отвечать — переключиться на самый быстрый из той же подписки.
    var autoFailover = true
    var socksPort = 10808
    var httpPort = 10809
    var lanShare = false
    var userAgent = "Veil/1.0 (macOS)"
    var sendHWID = true
    var hwid = UUID().uuidString.lowercased()
    var fragment = FragmentSettings()
    var showInDock = true
    var autoUpdateSubscriptions = true
    var remoteDNS = "https://1.1.1.1/dns-query"
    var domesticDNS = "https+local://77.88.8.8/dns-query"
}

nonisolated struct SavedState: Codable, Sendable {
    var servers: [Server] = []
    var subscriptions: [Subscription] = []
    var selectedID: String?
    var routing = RoutingSettings()
    var mode: ConnectionMode = .proxy
    var settings = AppSettings()
    var pings: [String: Ping.Result] = [:]
    var onboarded = false
}

enum ConnectionStatus: Equatable {
    case disconnected
    case connecting
    case connected(since: Date)
    case reconnecting(String)
    case failed(String)

    var isOn: Bool {
        switch self {
        case .connected, .reconnecting, .connecting: true
        default: false
        }
    }

    var title: String {
        switch self {
        case .disconnected: "Не подключено"
        case .connecting: "Подключаюсь…"
        case .connected: "Подключено"
        case .reconnecting: "Переподключаюсь…"
        case .failed: "Ошибка подключения"
        }
    }
}

struct SpeedPoint: Identifiable {
    let id: Int
    let up: Double
    let down: Double
}

@Observable
final class AppModel {
    static let shared = AppModel()

    var saved = SavedState() { didSet { scheduleSave() } }
    var status: ConnectionStatus = .disconnected
    var exitInfo: Probe.Exit?
    var speeds: [SpeedPoint] = []
    var speedUp: Double = 0
    var speedDown: Double = 0
    var sessionUp: Int64 = 0
    var sessionDown: Int64 = 0
    var pinging: Set<String> = []
    var pingingAll = false
    var updating: Set<String> = []
    var helperReady = false
    var geoDownloading = false
    var geoUpdated: Date? = GeoFiles.updated
    var toast: String?
    var logVersion = 0
    var coreVersion = ""
    var search = ""
    var showAdd = false
    var shareServer: Server?
    var pasteText = ""
    var importResult: String?
    /// Запросы окнам от демо и меню (окна открывает SwiftUI).
    var settingsRequest = 0
    var logRequest = 0

    private let core = XrayProcess()
    private var statsTask: Task<Void, Never>?
    private var healthTask: Task<Void, Never>?
    private var updateTask: Task<Void, Never>?
    private var saveWork: DispatchWorkItem?
    private var pathMonitor: NWPathMonitor?
    private var lastPathSatisfied = true
    private var restarts: [Date] = []
    private var counter = 0
    private var lastSample: (date: Date, up: Int64, down: Int64)?
    private var baseline: (up: Int64, down: Int64) = (0, 0)
    private var started = false

    // MARK: - Состояние

    var servers: [Server] { saved.servers }
    var selected: Server? { saved.servers.first { $0.id == saved.selectedID } }
    var settings: AppSettings { saved.settings }

    func servers(of subscription: Subscription?) -> [Server] {
        let list = saved.servers.filter { $0.subscriptionID == subscription?.id }
        guard !search.isEmpty else { return list }
        return list.filter { $0.name.localizedCaseInsensitiveContains(search) || $0.endpoint.address.localizedCaseInsensitiveContains(search) }
    }

    var manualServers: [Server] { servers(of: nil) }

    func ping(_ id: String) -> Ping.Result? { saved.pings[id] }

    private func scheduleSave() {
        saveWork?.cancel()
        let work = Self.saveWork(saved)
        saveWork = work
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 0.4, execute: work)
    }

    /// Запись на фоне. Замыкание создаётся вне главного актора: иначе Swift 6 считает его
    /// принадлежащим главному потоку и останавливает программу, когда оно выполняется на фоновом.
    nonisolated private static func saveWork(_ snapshot: SavedState) -> DispatchWorkItem {
        DispatchWorkItem {
            AppPaths.ensure(AppPaths.support)
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .secondsSince1970
            if let data = try? encoder.encode(snapshot) {
                FileManager.default.createFile(atPath: AppPaths.state.path, contents: data, attributes: [.posixPermissions: 0o600])
            }
        }
    }

    private func load() {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        if let data = try? Data(contentsOf: AppPaths.state), let s = try? decoder.decode(SavedState.self, from: data) {
            saved = s
        }
    }

    // MARK: - Запуск

    func start() {
        guard !started else { return }
        started = true
        if !DemoUI.isOn { load() }
        LogBook.shared.onChange = { [weak self] in
            DispatchQueue.main.async { MainActor.assumeIsolated { self?.logVersion += 1 } }
        }
        core.onExit = { [weak self] code in
            DispatchQueue.main.async { MainActor.assumeIsolated { self?.coreCrashed(code) } }
        }
        Task.detached { let v = XrayProcess.version(); await MainActor.run { AppModel.shared.coreVersion = v } }
        Task { helperReady = await HelperClient.status() != nil }
        watchNetwork()
        NSWorkspace.shared.notificationCenter.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { AppModel.shared.networkChanged(reason: "Mac проснулся") }
        }
        // Обновление подписок и баз сайтов в фоне.
        updateTask = Task {
            try? await Task.sleep(for: .seconds(3))
            while !Task.isCancelled {
                await autoUpdate()
                try? await Task.sleep(for: .seconds(1800))
            }
        }
        if saved.settings.connectOnLaunch && selected != nil && !DemoUI.isOn {
            Task { await connect() }
        }
    }

    func shutdown() {
        // Выход: вернуть системе её настройки (синхронно — приложение сейчас закроется).
        guard status.isOn else { return }
        let mode = saved.mode
        let socketPath = HelperClient.socketPath
        let done = DispatchSemaphore(value: 0)
        DispatchQueue.global().async {
            if mode == .proxy { _ = try? HelperSocket.send(HelperRequest(command: .proxyOff), path: socketPath, timeout: 4) }
            if mode == .tun { _ = try? HelperSocket.send(HelperRequest(command: .tunStop), path: socketPath, timeout: 6) }
            done.signal()
        }
        _ = done.wait(timeout: .now() + 7)
        core.stop()
    }

    // MARK: - Подключение

    func toggle() {
        Task { status.isOn ? await disconnect() : await connect() }
    }

    func options() -> CoreOptions {
        var o = CoreOptions()
        o.mode = saved.mode
        o.socksPort = saved.settings.socksPort
        o.httpPort = saved.settings.httpPort
        o.lanShare = saved.settings.lanShare
        o.routing = saved.routing
        o.fragment = saved.settings.fragment
        o.remoteDNS = saved.settings.remoteDNS
        o.domesticDNS = saved.settings.domesticDNS
        return o
    }

    func connect(_ server: Server? = nil) async {
        if let server { saved.selectedID = server.id }
        if saved.selectedID == nil || selected == nil {
            // Ничего не выбрано — самый быстрый из проверенных или первый.
            saved.selectedID = bestServer(excluding: nil)?.id ?? saved.servers.first(where: { $0.issue == nil })?.id
        }
        guard let target = selected else {
            status = .failed("Добавьте сервер: вставьте ссылку или подписку")
            return
        }
        status = .connecting
        let mode = saved.mode
        if mode != .local && !helperReady {
            do {
                try await HelperClient.install()
                helperReady = true
            } catch {
                status = .failed("\(error)")
                flash(mode == .tun ? "Для режима TUN нужен помощник" : "Для системного прокси нужен помощник. Без него работает режим «Только локальный порт».")
                return
            }
        }
        if saved.routing.mode != .all && !GeoFiles.present { updateGeo() }

        do {
            try await start(target, mode: mode)
            status = .connected(since: Date())
            restarts = []
            LogBook.shared.add("Подключено: \(target.name) · \(mode.title) · \(saved.routing.mode.title)")
            startMonitoring()
            refreshExit()
        } catch {
            status = .failed("\(error)")
            LogBook.shared.add("Не подключилось: \(error)", level: .error)
            core.stop()
        }
    }

    /// Запуск ядра и включение режима. Повторный вызов (смена сервера) — без выключения системного прокси.
    private func start(_ server: Server, mode: ConnectionMode) async throws {
        let o = options()
        let geo = await Task.detached { GeoFiles.categories() }.value
        let config = XrayConfig.json(try XrayConfig.build(server, o, geo: geo))
        switch mode {
        case .local, .proxy:
            let core = self.core
            try await Task.detached { try core.start(config: config, assets: AppPaths.geo) }.value
            if mode == .proxy {
                _ = try await HelperClient.call(HelperRequest(command: .proxyOn, httpPort: o.httpPort, socksPort: o.socksPort))
            }
        case .tun:
            core.stop()
            let response = try await HelperClient.call(HelperRequest(command: .tunStart, config: config, assets: AppPaths.geo.path), timeout: 30)
            if !response.tunRunning { throw HelperClient.Failure.helper(response.log.last ?? "ядро TUN не запустилось") }
        }
        baseline = (0, 0)
        lastSample = nil
    }

    func disconnect() async {
        stopMonitoring()
        let mode = saved.mode
        if mode == .proxy { _ = try? await HelperClient.call(HelperRequest(command: .proxyOff)) }
        if mode == .tun { _ = try? await HelperClient.call(HelperRequest(command: .tunStop)) }
        let core = self.core
        await Task.detached { core.stop() }.value
        status = .disconnected
        exitInfo = nil
        speedUp = 0
        speedDown = 0
        LogBook.shared.add("Отключено")
    }

    /// Смена сервера на лету.
    func switchTo(_ server: Server) {
        saved.selectedID = server.id
        guard status.isOn else { return }
        Task { await reconnect(reason: "Сервер: \(server.name)") }
    }

    /// Смена режима (прокси / TUN / локальный) — с переподключением, если подключено.
    func setMode(_ mode: ConnectionMode) {
        guard mode != saved.mode else { return }
        let wasOn = status.isOn
        Task {
            if wasOn { await disconnect() }
            saved.mode = mode
            if wasOn { await connect() }
        }
    }

    func setRouting(_ change: (inout RoutingSettings) -> Void) {
        change(&saved.routing)
        if status.isOn { Task { await reconnect(reason: "Правила маршрутизации обновлены") } }
    }

    func reconnect(reason: String) async {
        guard let server = selected else { return }
        status = .reconnecting(reason)
        LogBook.shared.add(reason)
        let mode = saved.mode
        do {
            try await start(server, mode: mode)
            status = .connected(since: Date())
            startMonitoring()
            refreshExit()
        } catch {
            status = .failed("\(error)")
            LogBook.shared.add("Не подключилось: \(error)", level: .error)
            if mode == .proxy { _ = try? await HelperClient.call(HelperRequest(command: .proxyOff)) }
        }
    }

    private func coreCrashed(_ code: Int32) {
        guard status.isOn, saved.mode != .tun else { return }
        restarts = restarts.filter { $0.timeIntervalSinceNow > -60 } + [Date()]
        LogBook.shared.add("Ядро завершилось (код \(code))", level: .error)
        if restarts.count > 3 {
            Task {
                await disconnect()
                status = .failed(LogBook.shared.lastError ?? "Ядро постоянно падает с этим сервером")
            }
            return
        }
        Task { await reconnect(reason: "Ядро перезапущено после сбоя") }
    }

    // MARK: - Наблюдение

    private func startMonitoring() {
        stopMonitoring()
        let port = options().metricsPort
        statsTask = Task {
            while !Task.isCancelled {
                if let s = await CoreStats.sample(port: port) { addSample(s) }
                try? await Task.sleep(for: .seconds(1))
            }
        }
        // Сторож связи: каждые 20 секунд запрос через сервер. Два провала подряд — переподключение
        // или переключение на лучший сервер.
        let check = options().checkPort
        healthTask = Task {
            var failures = 0
            try? await Task.sleep(for: .seconds(15))
            while !Task.isCancelled {
                let ok = await Probe.delay(httpPort: check, timeout: 8) != nil
                if Task.isCancelled { return }
                failures = ok ? 0 : failures + 1
                if failures >= 2 {
                    failures = 0
                    await recoverDeadServer()
                }
                try? await Task.sleep(for: .seconds(20))
            }
        }
    }

    private func stopMonitoring() {
        statsTask?.cancel()
        healthTask?.cancel()
        statsTask = nil
        healthTask = nil
    }

    private func recoverDeadServer() async {
        guard status.isOn, let current = selected else { return }
        LogBook.shared.add("Сервер «\(current.name)» не отвечает", level: .warning)
        if saved.settings.autoFailover {
            await pingAll(scope: current.subscriptionID)
            if let best = bestServer(excluding: current.id) {
                saved.selectedID = best.id
                flash("«\(current.title)» не отвечает — переключился на «\(best.title)»")
                await reconnect(reason: "Автопереключение на «\(best.name)»")
                return
            }
        }
        await reconnect(reason: "Переподключаюсь к «\(current.name)»")
    }

    func bestServer(excluding id: String?) -> Server? {
        let candidates = saved.servers.filter { $0.id != id && $0.issue == nil }
        let preferred = candidates.filter { $0.subscriptionID == selected?.subscriptionID }
        let pool = preferred.contains(where: { saved.pings[$0.id]?.value != nil }) ? preferred : candidates
        return pool.compactMap { s in saved.pings[s.id]?.value.map { (s, $0) } }.min { $0.1 < $1.1 }?.0
    }

    private func addSample(_ s: CoreStats.Sample) {
        let now = Date()
        if let last = lastSample {
            let dt = max(0.2, now.timeIntervalSince(last.date))
            speedUp = Double(max(0, s.up - last.up)) / dt
            speedDown = Double(max(0, s.down - last.down)) / dt
        }
        lastSample = (now, s.up, s.down)
        sessionUp = s.up
        sessionDown = s.down
        counter += 1
        speeds.append(SpeedPoint(id: counter, up: speedUp, down: speedDown))
        if speeds.count > 90 { speeds.removeFirst(speeds.count - 90) }
    }

    func refreshExit() {
        let port = options().checkPort
        Task {
            try? await Task.sleep(for: .milliseconds(400))
            exitInfo = await Probe.exit(httpPort: port)
        }
    }

    private func watchNetwork() {
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { path in
            let ok = path.status == .satisfied
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    let model = AppModel.shared
                    // Сеть вернулась (сменили Wi-Fi, проснулись) — старые соединения ядра мертвы.
                    if ok && !model.lastPathSatisfied { model.networkChanged(reason: "Сеть сменилась") }
                    model.lastPathSatisfied = ok
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "app.veil.path"))
        pathMonitor = monitor
    }

    func networkChanged(reason: String) {
        guard case .connected = status else { return }
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            await reconnect(reason: "\(reason) — переподключаюсь")
        }
    }

    // MARK: - Задержка

    func pingAll(scope: String?? = nil) async {
        let list: [Server]
        if let scope { list = saved.servers.filter { $0.subscriptionID == scope } } else { list = saved.servers }
        guard !list.isEmpty else { return }
        pingingAll = true
        pinging.formUnion(list.map(\.id))
        await Ping.measure(list) { id, result in
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    AppModel.shared.saved.pings[id] = result
                    AppModel.shared.pinging.remove(id)
                }
            }
        }
        try? await Task.sleep(for: .milliseconds(100))
        pinging.subtract(list.map(\.id))
        pingingAll = false
    }

    func pingOne(_ server: Server) {
        Task {
            pinging.insert(server.id)
            await Ping.measure([server]) { id, result in
                DispatchQueue.main.async { MainActor.assumeIsolated { AppModel.shared.saved.pings[id] = result } }
            }
            try? await Task.sleep(for: .milliseconds(100))
            pinging.remove(server.id)
        }
    }

    // MARK: - Подписки и импорт

    /// Всё, что пользователь вставил: подписка, ссылки серверов, профиль маршрутизации, JSON.
    @discardableResult
    func importText(_ raw: String) async -> String {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return "Пусто" }
        if SubscriptionLoader.isEncryptedHapp(text) { return SubscriptionLoader.Failure.encrypted.description }
        if text.lowercased().hasPrefix("happ://routing/off") {
            setRouting { $0.mode = .smart }
            return "Профиль маршрутизации выключен"
        }
        if text.lowercased().hasPrefix("happ://routing/"), let parsed = HappRoutingProfile.parse(text) {
            applyProfile(parsed.profile)
            return "Профиль маршрутизации «\(parsed.profile.name)» включён"
        }
        var messages: [String] = []
        // Подписки — по одной на строку.
        let lines = text.split(whereSeparator: \.isNewline).map { String($0).trimmingCharacters(in: .whitespaces) }
        for line in lines {
            if let url = SubscriptionLoader.url(from: line) { messages.append(await addSubscription(url)) }
        }
        // Ссылки серверов.
        var (servers, failures) = Links.parseAll(text)
        if servers.isEmpty, let json = SubscriptionLoader.jsonConfigs(Data(text.utf8)) { servers = json }
        if !servers.isEmpty {
            let existing = Set(saved.servers.map(\.id))
            let fresh = servers.filter { !existing.contains($0.id) }
            saved.servers.append(contentsOf: fresh)
            if saved.selectedID == nil { saved.selectedID = fresh.first?.id }
            messages.append(fresh.count == 1 ? "Добавлен сервер «\(fresh[0].name)»" : "Добавлено серверов: \(fresh.count)")
            Task { await pingAll(scope: .some(nil)) }
        }
        if !failures.isEmpty { messages.append("Не разобрано: " + failures.prefix(3).joined(separator: "; ")) }
        return messages.isEmpty ? "Не нашёл ни ссылок серверов, ни подписки" : messages.joined(separator: "\n")
    }

    func addSubscription(_ url: String) async -> String {
        if let existing = saved.subscriptions.first(where: { $0.url == url }) {
            await update(existing)
            return "Подписка «\(existing.displayTitle)» обновлена"
        }
        let sub = Subscription(id: UUID().uuidString, url: url, title: "")
        saved.subscriptions.append(sub)
        await update(sub)
        let updated = saved.subscriptions.first { $0.id == sub.id }
        if let error = updated?.error {
            return error
        }
        if saved.selectedID == nil { saved.selectedID = servers(of: updated).first(where: { $0.issue == nil })?.id }
        Task { await pingAll(scope: .some(sub.id)) }
        return "Подписка «\(updated?.displayTitle ?? "")»: серверов — \(servers(of: updated).count)"
    }

    func update(_ sub: Subscription) async {
        updating.insert(sub.id)
        defer { updating.remove(sub.id) }
        let s = saved.settings
        do {
            let loaded = try await SubscriptionLoader.fetch(sub.url, userAgent: s.userAgent, hwid: s.sendHWID ? s.hwid : nil)
            guard let index = saved.subscriptions.firstIndex(where: { $0.id == sub.id }) else { return }
            saved.subscriptions[index].info = loaded.info
            saved.subscriptions[index].updated = Date()
            saved.subscriptions[index].error = nil
            // Замена серверов подписки с сохранением порядка остальных.
            let fresh = loaded.servers.map { s -> Server in var s = s; s.subscriptionID = sub.id; return s }
            let firstIndex = saved.servers.firstIndex { $0.subscriptionID == sub.id } ?? saved.servers.count
            saved.servers.removeAll { $0.subscriptionID == sub.id }
            saved.servers.insert(contentsOf: fresh, at: min(firstIndex, saved.servers.count))
            if let routing = loaded.info.routing, let parsed = HappRoutingProfile.parse(routing),
               saved.routing.profile?.name != parsed.profile.name || saved.routing.profile?.lastUpdated != parsed.profile.lastUpdated {
                saved.routing.profile = parsed.profile
                LogBook.shared.add("Подписка прислала профиль маршрутизации «\(parsed.profile.name)»")
            }
            LogBook.shared.add("Подписка «\(saved.subscriptions[index].displayTitle)»: \(fresh.count) серверов")
            // Выбранный сервер исчез из подписки — выбираем лучший.
            if let selectedID = saved.selectedID, !saved.servers.contains(where: { $0.id == selectedID }) {
                saved.selectedID = bestServer(excluding: nil)?.id ?? fresh.first?.id
                if status.isOn, let next = selected { switchTo(next) }
            }
        } catch {
            if let index = saved.subscriptions.firstIndex(where: { $0.id == sub.id }) {
                saved.subscriptions[index].error = "\(error)"
            }
            LogBook.shared.add("Подписка не обновилась: \(error)", level: .warning)
        }
    }

    func removeSubscription(_ sub: Subscription) {
        saved.servers.removeAll { $0.subscriptionID == sub.id }
        saved.subscriptions.removeAll { $0.id == sub.id }
        if selected == nil { saved.selectedID = nil }
    }

    func remove(_ server: Server) {
        saved.servers.removeAll { $0.id == server.id }
        saved.pings[server.id] = nil
        if saved.selectedID == server.id { saved.selectedID = nil }
    }

    func applyProfile(_ profile: HappRoutingProfile) {
        setRouting {
            $0.profile = profile
            $0.mode = .profile
        }
        if !profile.geoipURL.isEmpty || !profile.geositeURL.isEmpty {
            LogBook.shared.add("Профиль «\(profile.name)» использует свои базы сайтов; Veil берёт общие базы runetfreedom")
        }
    }

    private func autoUpdate() async {
        if saved.settings.autoUpdateSubscriptions {
            for sub in saved.subscriptions {
                let hours = Double(sub.info.updateHours > 0 ? sub.info.updateHours : 12)
                if sub.updated.map({ Date().timeIntervalSince($0) > hours * 3600 }) ?? true { await update(sub) }
            }
        }
        if saved.routing.mode != .all && GeoFiles.stale && !geoDownloading { updateGeo() }
    }

    func updateGeo() {
        guard !geoDownloading else { return }
        geoDownloading = true
        Task {
            defer { geoDownloading = false }
            do {
                try await GeoFiles.download()
                geoUpdated = GeoFiles.updated
                LogBook.shared.add("Базы сайтов для маршрутизации обновлены")
                if status.isOn { await reconnect(reason: "Базы сайтов скачаны — правила применены") }
            } catch {
                LogBook.shared.add("Базы сайтов не скачались: \(error.localizedDescription)", level: .warning)
            }
        }
    }

    // MARK: - Помощник и мелочи

    func installHelper() {
        Task {
            do {
                try await HelperClient.install()
                helperReady = true
                flash("Помощник установлен")
            } catch {
                flash("\(error)")
            }
        }
    }

    func uninstallHelper() {
        Task {
            if status.isOn && saved.mode != .local { await disconnect() }
            do {
                try await HelperClient.uninstall()
                helperReady = false
                flash("Помощник удалён")
            } catch {
                flash("\(error)")
            }
        }
    }

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            do { newValue ? try SMAppService.mainApp.register() : try SMAppService.mainApp.unregister() } catch {
                flash("Не получилось: \(error.localizedDescription)")
            }
        }
    }

    func flash(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(for: .seconds(4))
            if toast == text { toast = nil }
        }
    }

    func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        flash("Скопировано")
    }

    func applyDockPolicy() {
        NSApp.setActivationPolicy(saved.settings.showInDock ? .regular : .accessory)
    }

    /// Ссылки, открытые в Veil (vless://, happ://add/…, veil://…).
    func open(_ url: URL) {
        Task {
            let result = await importText(url.absoluteString)
            importResult = result
            flash(result)
        }
    }
}
