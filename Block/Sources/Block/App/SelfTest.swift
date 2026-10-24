import AppKit
import Foundation

/// Самопроверки, которые CI запускает прямо в собранном бинарнике.
///   --selftest-logic   — каталог правил, секция hosts, ссылка для Veil, профили (система не трогается)
///   --selftest-system  — на настоящем Mac раннера: применить и вернуть правила, сравнить снимки до и после;
///                        hosts и системные настройки — через `sudo -n` (BLOCK_SUDO=1); программа по умолчанию;
///                        сторож запуска на безопасной программе Apple
enum SelfTest {
    nonisolated(unsafe) static var isRunning = false

    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        setvbuf(stdout, nil, _IOLBF, 0)
        isRunning = true
        var ok = false
        switch mode {
        case "--selftest-logic": ok = logic()
        case "--selftest-system": ok = blocking { await system() }
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

    static func info(_ message: String) { print("· \(message)") }

    /// Асинхронная проверка на главном потоке: крутим цикл событий, пока она не закончится.
    static func blocking(_ body: @escaping @MainActor () async -> Bool) -> Bool {
        _ = NSApplication.shared
        NSApp.setActivationPolicy(.accessory)
        let box = ResultBox()
        Task { @MainActor in box.value = await body() }
        while box.value == nil { RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.05)) }
        return box.value ?? false
    }

    final class ResultBox { var value: Bool? }

    static func spin(_ seconds: Double, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline && !done() { RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.02)) }
    }

    // MARK: - Логика

    static func logic() -> Bool {
        var ok = true
        let rules = Catalog.rules
        print("— Каталог: \(rules.count) правил")
        ok = check(Set(rules.map(\.id)).count == rules.count, "у каждого правила свой id") && ok
        ok = check(rules.allSatisfy { !$0.testedOn.isEmpty && !$0.title.isEmpty && !$0.detail.isEmpty }, "у каждого правила есть название, описание и версии macOS") && ok
        for level in Level.allCases {
            ok = check(rules.contains { $0.level == level }, "уровень \(level.rawValue) «\(level.title)» не пуст") && ok
        }
        // У каждого правила есть проба, применение, проверка и возврат: снимок соответствует действию.
        let snapshotsMatch = rules.allSatisfy { rule in
            switch (rule.action, RuleEngine.snapshot(rule)) {
            case (.prefs(let keys, _), .prefs(let list)): return keys.count == list.count
            case (.systemPlist(let p), .system(let path, let values)): return p.path == path && values.count == p.keys.count
            case (.handlers(let h, _), .handlers(let map)): return h.count == map.count
            case (.agent, .agent), (.dock, .dock), (.pane, .flag), (.launchGuard, .flag), (.network, .flag): return true
            default: return false
            }
        }
        ok = check(snapshotsMatch, "снимок «до» есть у каждого правила и совпадает с его действием") && ok
        let guarded = Catalog.guardedBundles
        ok = check(Set(guarded).count == guarded.count && !guarded.contains("com.apple.Safari") && !guarded.contains("com.apple.systempreferences"),
                   "сторож: без повторов; Safari и «Системные настройки» никогда не закрываются") && ok
        let agentLabels = rules.compactMap(\.action.agentLabel)
        let vital = ["com.apple.Finder", "com.apple.Dock", "com.apple.WindowServer", "com.apple.softwareupdated", "com.apple.trustd", "com.apple.syspolicyd", "com.apple.securityd"]
        ok = check(Set(agentLabels).count == agentLabels.count && !agentLabels.contains(where: vital.contains), "службы: без повторов и без жизненно важных") && ok

        print("— Сеть")
        let all = AppleNetwork.groups.flatMap { $0.hosts + $0.suffixes }
        let leaks = all.filter(AppleNetwork.isUntouchable)
        ok = check(leaks.isEmpty, "ни одна группа не закрывает обновления, сертификаты, нотаризацию и время\(leaks.isEmpty ? "" : ": \(leaks)")") && ok
        for original in ["127.0.0.1 localhost\n255.255.255.255 broadcasthost\n::1 localhost\n", "127.0.0.1 localhost", ""] {
            let rendered = HostsFile.render(original, hosts: AppleNetwork.groups.flatMap(\.hosts) + ["swscan.apple.com", "ocsp.apple.com"])
            let blocked = HostsFile.blockedHosts(in: rendered)
            ok = check(rendered.hasPrefix(original) && blocked.contains("xp.apple.com") && !blocked.contains { AppleNetwork.isUntouchable($0) },
                       "hosts (\(original.count) байт): секция дописана в конец, неприкосновенные адреса не попали") && ok
            ok = check(HostsFile.without(rendered) == original && HostsFile.render(rendered, hosts: []) == original, "hosts: снятая секция возвращает файл байт в байт") && ok
            let twice = HostsFile.render(rendered, hosts: AppleNetwork.telemetry.hosts)
            ok = check(twice.components(separatedBy: HostsFile.begin).count == 2, "hosts: повторное применение не плодит секции") && ok
        }
        if let url = VeilRoutes.url(domains: VeilRoutes.domains(AppleNetwork.groups) + ["mesu.apple.com"]),
           let comps = URLComponents(url: url, resolvingAgainstBaseURL: false),
           var encoded = comps.queryItems?.first(where: { $0.name == "domains" })?.value {
            encoded = encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            while encoded.count % 4 != 0 { encoded += "=" }
            let list = Data(base64Encoded: encoded).flatMap { try? JSONDecoder().decode([String].self, from: $0) } ?? []
            ok = check(url.scheme == "veil" && url.host == "block-list" && list.contains("icloud.com") && !list.contains("mesu.apple.com"),
                       "ссылка для Veil: суффиксы и имена, без неприкосновенных (\(list.count) доменов)") && ok
        } else {
            ok = check(false, "ссылка для Veil собрана") && ok
        }

        print("— Профили")
        let model = AppModel.shared
        model.state = BlockState()
        let gentle = model.selection(for: .gentle)
        ok = check(!gentle.isEmpty && gentle.allSatisfy { !$0.needsAdmin && $0.level <= .launchGuard }, "«Мягко»: \(gentle.count) правил, без пароля администратора и без служб и сети") && ok
        let full = model.selection(for: .full)
        ok = check(full.count > gentle.count && full.contains { $0.level == .network } && !full.contains { $0.isManual || $0.confidence == .experimental },
                   "«Полностью»: \(full.count) правил, с сетью; ручные и экспериментальные — только по кнопке") && ok
        ok = check(!full.contains { $0.id == "net.icloud" || $0.id == "icloud.signout" }, "iCloud не трогается, пока вы не ответили, что он не нужен") && ok
        model.state.answers = Answers(usesICloud: true, needsFindMy: true, usesIPhone: true)
        ok = check(!model.rules.contains { $0.condition != nil }, "пользуетесь iCloud и iPhone — правила для них скрыты") && ok
        model.state.answers = Answers()
        ok = check(model.rules.contains { $0.id == "net.icloud" } && model.selection(for: .full).contains { $0.id == "net.icloud" },
                   "не пользуетесь iCloud — его адреса в «Полностью»") && ok

        print("— Значения и службы")
        ok = check(Preferences.matches(.int(0), .bool(false)) && Preferences.matches(.bool(true), .int(1)) && !Preferences.matches(nil, .bool(false)),
                   "0/1 и да/нет для системы одно и то же; нет ключа — не совпадение") && ok
        let parsed = Launchd.parse("disabled services = {\n\t\"com.apple.tipsd\" => disabled\n\t\"com.apple.gamed\" => enabled\n\t\"com.example.old\" => true\n}")
        ok = check(parsed["com.apple.tipsd"] == .disabled && parsed["com.apple.gamed"] == .enabled && parsed["com.example.old"] == .disabled,
                   "список отключённых служб launchctl разобран (старый и новый вид)") && ok
        ok = check(model.plural(1) == "правило" && model.plural(3) == "правила" && model.plural(11) == "правил" && model.plural(22) == "правила", "склонения") && ok
        return ok
    }

    // MARK: - Настоящая система

    /// Всё, что Block может поменять без прав администратора: значения ключей, записи служб, Dock.
    struct Fingerprint: Equatable {
        var prefs: [String: String]
        var agents: [String: Bool]
        var dock: [String]
    }

    static func fingerprint(_ rules: [Rule]) -> Fingerprint {
        var prefs: [String: String] = [:]
        var agents: [String: Bool] = [:]
        let overrides = Launchd.overrides()
        for rule in rules {
            switch rule.action {
            case .prefs(let keys, _):
                for k in keys { prefs["\(k.domain) \(k.key) \(k.currentHost)"] = Preferences.read(k)?.description ?? "—" }
            case .agent(let label):
                agents[label] = overrides[label] == .disabled
            default: break
            }
        }
        return Fingerprint(prefs: prefs, agents: agents, dock: Dock.tiles().compactMap(Dock.bundleID))
    }

    static func system() async -> Bool {
        var ok = true
        let model = AppModel.shared
        model.state = BlockState()
        NetworkMethod.current = .hosts
        info("macOS \(BlockState.osVersion), домашняя папка Block: \(BlockState.directory.path)")

        // 1. Настройки, службы и Dock: применить, проверить, вернуть — снимок совпадает.
        let local = Catalog.rules.filter { rule in
            switch rule.action {
            case .prefs: return true
            case .agent(let label): return Launchd.exists(label)
            case .dock: return true
            default: return false
            }
        }
        let before = fingerprint(local)
        info("правил без пароля: \(local.count); Apple в Dock: \(Dock.present(Set(Catalog.guardedBundles)))")
        await model.apply(local)
        model.refresh()
        for rule in local {
            let st = model.status(of: rule)
            switch rule.action {
            case .agent:
                ok = check(st == .blocked || st == .protected("защищена системой"), "служба \(rule.title): \(st.label)") && ok
            default:
                ok = check(st == .blocked, "\(rule.title): \(st.label)") && ok
            }
        }
        let during = fingerprint(local)
        ok = check(during != before || local.isEmpty, "изменения действительно дошли до системы") && ok
        await model.revertAll()
        let after = fingerprint(local)
        for (key, value) in before.prefs where after.prefs[key] != value { info("ключ \(key): было \(value), стало \(after.prefs[key] ?? "?")") }
        for (key, value) in before.agents where after.agents[key] != value { info("служба \(key): было \(value), стало \(after.agents[key] ?? false)") }
        ok = check(after.prefs == before.prefs, "ключи настроек после возврата — как до Block (\(before.prefs.count))") && ok
        ok = check(after.agents == before.agents, "службы после возврата — как до Block (\(before.agents.count))") && ok
        ok = check(after.dock == before.dock, "Dock после возврата — те же значки на тех же местах (\(before.dock.count))") && ok
        ok = check(model.state.baseline.isEmpty, "в журнале не осталось незакрытых изменений") && ok

        // 2. Права администратора: системные настройки и /etc/hosts (в CI — sudo -n).
        if Privileged.usesSudo {
            let hostsBefore = HostsFile.read()
            let diagnostics = Catalog.rules.filter(\.action.isSystemPlist)
            let net = Catalog.network.filter { $0.id != "net.icloud" }
            let adminBefore = diagnostics.map { RuleEngine.snapshot($0) }
            await model.apply(diagnostics + net)
            model.refresh()
            for rule in diagnostics + net { ok = check(model.status(of: rule) == .blocked, "\(rule.title): \(model.status(of: rule).label)") && ok }
            ok = check(HostsFile.resolvesToNowhere("xp.apple.com"), "резолвер: xp.apple.com → 0.0.0.0") && ok
            ok = check(!HostsFile.resolvesToNowhere("swscan.apple.com"), "резолвер: обновления macOS (swscan.apple.com) открыты") && ok
            await model.revert(diagnostics + net)
            ok = check(HostsFile.read() == hostsBefore, "/etc/hosts после возврата — байт в байт как был") && ok
            ok = check(diagnostics.map { RuleEngine.snapshot($0) } == adminBefore, "аналитика Mac после возврата — как была") && ok
        } else {
            info("BLOCK_SUDO не задан — шаги с паролем администратора пропущены")
        }

        // 3. Программа по умолчанию: свой тип файла → TextEdit и обратно.
        let handler = Handler(kind: .contentType, value: "app.block.selftest")
        let previous = Handlers.current(handler)
        let textEdit = URL(fileURLWithPath: "/System/Applications/TextEdit.app")
        info("тип app.block.selftest открывает: \(previous?.path ?? "никто")")
        do {
            try await Handlers.set(handler, to: textEdit)
            spin(3) { Handlers.bundleID(of: Handlers.current(handler)) == "com.apple.TextEdit" }
            ok = check(Handlers.bundleID(of: Handlers.current(handler)) == "com.apple.TextEdit", "программа по умолчанию назначена (TextEdit)") && ok
            if let previous {
                try await Handlers.set(handler, to: previous)
                spin(3) { Handlers.current(handler)?.standardizedFileURL == previous.standardizedFileURL }
                ok = check(Handlers.current(handler)?.standardizedFileURL == previous.standardizedFileURL, "и возвращена прежняя (\(previous.lastPathComponent))") && ok
            }
        } catch {
            ok = check(false, "LaunchServices: \(error.localizedDescription)") && ok
        }

        // 4. Сторож запуска: безопасная программа Apple закрывается почти сразу.
        let candidates = [("com.apple.tips", "Советы"), ("com.apple.Chess", "Шахматы"), ("com.apple.Stickies", "Записки")]
        if let candidate = candidates.first(where: { Handlers.appURL($0.0) != nil }), let url = Handlers.appURL(candidate.0) {
            let (bundle, name) = candidate
            let map: [String: CatalogApp?] = [bundle: nil]
            model.guardian.update(map)
            let started = Date()
            NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration(), completionHandler: nil)
            spin(8) { model.guardian.lastStop?.bundle == bundle }
            spin(2) { NSRunningApplication.runningApplications(withBundleIdentifier: bundle).allSatisfy(\.isTerminated) }
            let stop = model.guardian.lastStop
            let alive = NSRunningApplication.runningApplications(withBundleIdentifier: bundle).filter { !$0.isTerminated }
            info("«\(name)»: закрыта через \(String(format: "%.2f", stop?.seconds ?? -1)) с после старта процесса, \(String(format: "%.2f", Date().timeIntervalSince(started))) с от запроса")
            ok = check(stop?.bundle == bundle && alive.isEmpty, "сторож закрыл «\(name)»") && ok
            ok = check((stop?.seconds ?? 99) < 1.0, "меньше чем за секунду после запуска") && ok
            model.guardian.update([:])
        } else {
            info("безопасной программы для проверки сторожа нет")
        }
        return ok
    }
}
