import AppKit
import ServiceManagement
import SwiftUI

enum Screen: String, CaseIterable, Identifiable {
    case overview, apps, settings, services, network, replacements, journal

    var id: String { rawValue }

    var title: String {
        switch self {
        case .overview: "Обзор"
        case .apps: "Программы"
        case .settings: "Настройки Apple"
        case .services: "Службы"
        case .network: "Сеть"
        case .replacements: "Замены"
        case .journal: "Журнал"
        }
    }

    var symbol: String {
        switch self {
        case .overview: "circle.hexagongrid.fill"
        case .apps: "hand.raised.fill"
        case .settings: "switch.2"
        case .services: "gearshape.2.fill"
        case .network: "network.slash"
        case .replacements: "arrow.triangle.swap"
        case .journal: "clock.arrow.circlepath"
        }
    }

    var tint: Color {
        switch self {
        case .overview: .indigo
        case .apps: .orange
        case .settings: .blue
        case .services: .purple
        case .network: .teal
        case .replacements: .green
        case .journal: .gray
        }
    }
}

@Observable
final class AppModel {
    static let shared = AppModel()

    var state = BlockState.load()
    var states: [String: RuleState] = [:]
    var screen: Screen = .overview
    var busy = false
    var toast: String?
    var showOnboarding = false
    /// Правила, которые Apple вернула после обновления или вручную.
    var drift: [String] = []
    let guardian = LaunchGuard()
    @ObservationIgnored private var driftTimer: Timer?

    var rules: [Rule] { Catalog.rules.filter { state.answers.allows($0.condition) } }

    // MARK: Запуск

    func start() {
        NetworkMethod.current = state.networkMethod
        guardian.onStop = { [weak self] bundle in
            guard let self else { return }
            self.state.stoppedLaunches[bundle, default: 0] += 1
            if let rule = Catalog.guards.first(where: { $0.action.guardedBundle == bundle }) {
                self.state.log(.launch, rule, "закрыта при запуске")
            }
            self.state.save()
        }
        refreshGuard()
        refresh()
        if !state.onboarded && !DemoUI.isOn && !SelfTest.isRunning { showOnboarding = true }
        // После обновления macOS Apple часто возвращает свои значения — проверить всё.
        if state.lastOS != BlockState.osVersion {
            if !state.lastOS.isEmpty { checkDrift(reason: "macOS обновилась до \(BlockState.osVersion)") }
            state.lastOS = BlockState.osVersion
            state.save()
        }
        driftTimer = Timer.scheduledTimer(withTimeInterval: 6 * 3600, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.checkDrift(reason: nil) }
        }
        NSWorkspace.shared.notificationCenter.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { AppModel.shared.checkDrift(reason: nil) }
        }
    }

    func refresh() {
        var map: [String: RuleState] = [:]
        for rule in Catalog.rules { map[rule.id] = RuleEngine.probe(rule, state) }
        states = map
    }

    func refreshGuard() {
        var map: [String: CatalogApp?] = [:]
        for rule in Catalog.guards where state.baseline[rule.id] != nil {
            if case .launchGuard(let bundle, let replacement) = rule.action { map[bundle] = .some(replacement) }
        }
        guardian.update(map)
    }

    func status(of rule: Rule) -> RuleState { states[rule.id] ?? .active }

    // MARK: Доля «Mac принадлежит вам»

    var score: Double {
        let counted = rules.filter { status(of: $0).counts }
        guard !counted.isEmpty else { return 0 }
        return Double(counted.filter { status(of: $0).isBlocked }.count) / Double(counted.count)
    }

    func surfaceBlocked(_ surface: Surface) -> Double {
        let list = rules.filter { $0.surface == surface && status(of: $0).counts }
        guard !list.isEmpty else { return 0 }
        return Double(list.filter { status(of: $0).isBlocked }.count) / Double(list.count)
    }

    var stoppedTotal: Int { state.stoppedLaunches.values.reduce(0, +) }

    // MARK: Профиль

    /// Правила профиля с учётом ответов. Ручные шаги и выход из Apple Account в профиль не входят — только по кнопке.
    func selection(for profile: Profile) -> [Rule] {
        if profile == .custom { return rules.filter { state.customSelection.contains($0.id) } }
        // «Мягко» — без пароля администратора.
        return rules.filter { profile.levels.contains($0.level) && !$0.isManual && $0.confidence == .solid && (profile == .full || !$0.needsAdmin) }
    }

    /// Что изменится: правила профиля, которые сейчас не в нужном состоянии и применимы.
    func preview(for profile: Profile) -> [Rule] {
        selection(for: profile).filter { rule in
            switch status(of: rule) {
            case .active, .partial: true
            default: false
            }
        }
    }

    // MARK: Применение

    func apply(_ list: [Rule]) async {
        guard !busy, !list.isEmpty else { return }
        busy = true
        defer { busy = false }
        var s = state
        var applied = 0
        var failures: [String] = []
        var admin: [String] = []
        var adminRules: [Rule] = []
        var hostsChanged = false
        for rule in list {
            let (before, error) = await RuleEngine.apply(rule, &s)
            if s.baseline[rule.id] == nil { s.baseline[rule.id] = before }
            switch rule.action {
            case .systemPlist:
                admin += RuleEngine.adminCommands(rule, revertTo: nil)
                adminRules.append(rule)
                continue
            case .network where s.networkMethod == .hosts:
                hostsChanged = true
                adminRules.append(rule)
                continue
            default: break
            }
            if let error {
                failures.append("\(rule.title): \(error)")
                s.log(.failed, rule, error)
            } else {
                applied += 1
                s.log(.applied, rule, "было: " + before.summary)
            }
        }
        if hostsChanged, let commands = hostsCommands(s) { admin += commands }
        if !admin.isEmpty {
            switch Privileged.run(admin, reason: "Block выключает сервисы Apple, которым нужны права администратора.") {
            case .success:
                for rule in adminRules { applied += 1; s.log(.applied, rule, "с правами администратора") }
            case .failure(let error):
                for rule in adminRules { s.baseline[rule.id] = nil; s.log(.failed, rule, error.description) }
                failures.append(error.description)
            }
        }
        state = s
        state.save()
        refreshGuard()
        refresh()
        // Проверка: правило, которое не дошло до нужного состояния, видно сразу.
        let unverified = list.filter { rule in
            guard s.baseline[rule.id] != nil else { return false }
            switch status(of: rule) {
            case .active, .partial: return !rule.isManual
            default: return false
            }
        }
        for rule in unverified { state.log(.failed, rule, "после применения система вернула прежнее (\(status(of: rule).label))") }
        if !unverified.isEmpty { state.save() }
        flash(failures.isEmpty && unverified.isEmpty ? "Готово: \(applied) \(plural(applied))" : "Применено \(applied), не вышло: \(failures.count + unverified.count) — подробности в журнале")
    }

    /// Новое содержимое /etc/hosts — во временный файл и команды для его установки.
    private func hostsCommands(_ s: BlockState) -> [String]? {
        let groups = Catalog.network.compactMap { rule -> NetGroup? in
            guard s.baseline[rule.id] != nil, case .network(let g) = rule.action else { return nil }
            return g
        }
        let current = HostsFile.read()
        let next = HostsFile.render(current, hosts: groups.flatMap(\.hosts))
        guard next != current else { return nil }
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent("block-hosts-\(UUID().uuidString)")
        guard (try? next.write(to: temp, atomically: true, encoding: .utf8)) != nil else { return nil }
        return HostsFile.installCommands(from: temp.path)
    }

    func revert(_ list: [Rule]) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        var s = state
        var admin: [String] = []
        var adminRules: [Rule] = []
        var hostsChanged = false
        for rule in list {
            guard let before = s.baseline[rule.id] else {
                if rule.isManual { s.manualDone.remove(rule.id) }
                continue
            }
            await RuleEngine.revert(rule, to: before, &s)
            s.baseline[rule.id] = nil
            switch rule.action {
            case .systemPlist:
                admin += RuleEngine.adminCommands(rule, revertTo: before)
                adminRules.append(rule)
            case .network where s.networkMethod == .hosts:
                hostsChanged = true
                adminRules.append(rule)
            default:
                s.log(.reverted, rule, "возвращено: " + before.summary)
            }
        }
        if hostsChanged, let commands = hostsCommands(s) { admin += commands }
        if !admin.isEmpty {
            switch Privileged.run(admin, reason: "Block возвращает настройки Apple как было.") {
            case .success:
                for rule in adminRules { s.log(.reverted, rule, "возвращено с правами администратора") }
            case .failure(let error):
                for rule in adminRules {
                    s.baseline[rule.id] = state.baseline[rule.id]
                    s.log(.failed, rule, error.description)
                }
            }
        }
        state = s
        state.save()
        refreshGuard()
        refresh()
        flash("Возвращено как было")
    }

    func revertAll() async {
        await revert(Catalog.rules.filter { state.baseline[$0.id] != nil || state.manualDone.contains($0.id) })
    }

    func toggle(_ rule: Rule) async {
        if status(of: rule).isBlocked || state.baseline[rule.id] != nil { await revert([rule]) } else { await apply([rule]) }
    }

    /// Ручной шаг выполнен (человек нажал переключатель в настройках).
    func markManual(_ rule: Rule, done: Bool) {
        if done { state.manualDone.insert(rule.id); state.log(.applied, rule, "сделано вручную в настройках") }
        else { state.manualDone.remove(rule.id) }
        state.save()
        refresh()
    }

    func applyProfile(_ profile: Profile) async {
        state.profile = profile
        state.save()
        await apply(preview(for: profile))
    }

    // MARK: Дрейф

    func checkDrift(reason: String?) {
        refresh()
        drift = Catalog.rules.filter { rule in
            guard state.baseline[rule.id] != nil else { return false }
            switch status(of: rule) {
            case .active, .partial: return true
            default: return false
            }
        }.map(\.id)
        guard !drift.isEmpty else { return }
        for id in drift { if let rule = Catalog.rule(id) { state.log(.drift, rule, reason ?? "значение вернулось") } }
        state.save()
        if state.autoReapply {
            let list = drift.compactMap(Catalog.rule)
            Task { await reapply(list) }
        }
    }

    /// Применить заново то, что Apple вернула (снимок «до» остаётся первым).
    func reapply(_ list: [Rule]) async {
        await apply(list)
        drift = []
    }

    // MARK: Сеть

    func setNetworkMethod(_ method: NetworkMethod) async {
        guard method != state.networkMethod else { return }
        let applied = Catalog.network.filter { state.baseline[$0.id] != nil }
        if !applied.isEmpty { await revert(applied) }
        state.networkMethod = method
        NetworkMethod.current = method
        state.save()
        if !applied.isEmpty { await apply(applied) } else { refresh() }
    }

    // MARK: Вход в систему

    var launchAtLogin: Bool {
        get { SMAppService.mainApp.status == .enabled }
        set {
            do {
                if newValue { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
            } catch {
                flash("Не удалось: \(error.localizedDescription)")
            }
        }
    }

    // MARK: Мелочи

    func flash(_ text: String) {
        withAnimation(.smooth) { toast = text }
        Task {
            try? await Task.sleep(for: .seconds(3.5))
            if toast == text { withAnimation(.smooth) { toast = nil } }
        }
    }

    func plural(_ n: Int) -> String {
        let m10 = n % 10, m100 = n % 100
        if m10 == 1 && m100 != 11 { return "правило" }
        if (2...4).contains(m10) && !(12...14).contains(m100) { return "правила" }
        return "правил"
    }

    func installed(_ app: CatalogApp) -> Bool { Handlers.appURL(app.bundleID) != nil }

    func open(_ app: CatalogApp) {
        if let url = Handlers.appURL(app.bundleID) {
            NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration())
        } else if let harbor = Handlers.appURL(CatalogApp.harbor.bundleID) {
            NSWorkspace.shared.openApplication(at: harbor, configuration: NSWorkspace.OpenConfiguration())
            flash("\(app.name) — в Harbor, кнопка «Установить»")
        } else if let page = URL(string: "https://github.com/parfentsevandrey-blip/test/releases/tag/catalog") {
            NSWorkspace.shared.open(page)
        }
    }
}
