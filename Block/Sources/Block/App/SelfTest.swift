import AppKit
import CoreServices
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

    /// Дождаться асинхронной операции, но не дольше `seconds` (LaunchServices иногда не отвечает).
    static func withTimeout(_ seconds: Double, _ body: @escaping @MainActor () async -> Void) async -> Bool {
        let box = ResultBox()
        Task { @MainActor in await body(); box.value = true }
        let deadline = Date().addingTimeInterval(seconds)
        while box.value == nil && Date() < deadline { try? await Task.sleep(for: .milliseconds(50)) }
        if box.value == nil { info("не дождались за \(Int(seconds)) с") }
        return box.value == true
    }

    /// Ожидание внутри асинхронной проверки: главный поток свободен, уведомления системы доходят.
    static func wait(_ seconds: Double, until done: () -> Bool) async {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline && !done() { try? await Task.sleep(for: .milliseconds(30)) }
    }

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
        model.state.answers = Answers(usesICloud: false, needsFindMy: false, usesIPhone: false)
        ok = check(model.rules.contains { $0.id == "net.icloud" } && model.selection(for: .full).contains { $0.id == "net.icloud" },
                   "не пользуетесь iCloud — его адреса в «Полностью»") && ok

        print("— Наблюдение за соединениями")
        let fixture = """
        time,,bytes_in,bytes_out,
        12:00:01.000000,apsd.312,1200,800,
        12:00:01.000000,tcp4 192.168.1.5:50123<->17.57.144.10:5223,1000,700,
        12:00:01.000000,analyticsd.455,10,20,
        12:00:01.000000,tcp6 2a01:e0a::1.50000<->2620:149:a44::1.443,5,7,
        12:00:01.000000,udp4 *:*<->*:*,0,0,
        12:00:01.000000,promotedcontentd.77,1,1,
        12:00:01.000000,tcp4 10.0.0.2:61000<->10.0.0.1:53,1,1,
        """
        let parsedConnections = NettopParser.parse(fixture)
        ok = check(parsedConnections.count == 3
                   && parsedConnections[0] == ConnectionSample(process: "apsd", pid: 312, proto: "tcp4", ip: "17.57.144.10", port: 5223, bytesIn: 1000, bytesOut: 700)
                   && parsedConnections[1].process == "analyticsd" && parsedConnections[1].ip == "2620:149:a44::1" && parsedConnections[1].port == 443
                   && NettopParser.isPrivate(parsedConnections[2].ip),
                   "nettop: процессы, IPv4 и IPv6 с портами, байты; «*:*» пропущено, домашняя сеть распознана") && ok
        ok = check(AppleKnowledge.verdict(process: "analyticsd", host: nil) == .telemetry
                   && AppleKnowledge.verdict(process: "promotedcontentd", host: "iadsdk.apple.com") == .ads
                   && AppleKnowledge.verdict(process: "cloudd", host: "xp.apple.com") == .telemetry
                   && AppleKnowledge.verdict(process: "trustd", host: "ocsp.apple.com") == .essential
                   && AppleKnowledge.verdict(process: "analyticsd", host: "swscan.apple.com") == .essential
                   && AppleKnowledge.verdict(process: "someone", host: "example.org") == .unknown
                   && AppleKnowledge.verdict(process: "intelligenceplat", host: nil) == .suggestions,
                   "вердикты: телеметрия, реклама, имя важнее процесса, неприкосновенное — всегда «нужно системе», обрезанные имена") && ok
        ok = check(AppleKnowledge.isApple(path: "/usr/libexec/apsd", name: "apsd") && !AppleKnowledge.isApple(path: "/usr/bin/curl", name: "curl")
                   && !AppleKnowledge.isApple(path: "/Applications/Telegram.app/Contents/MacOS/Telegram", name: "Telegram"),
                   "процессы Apple — по системному пути; curl и чужие программы — нет") && ok
        let probe = ConnectionMonitor()
        probe.show([])
        // Процесс, которого уже нет (путь не узнать), опознаётся по имени; сам Block — по пути, и это не Apple.
        probe.ingest([ConnectionSample(process: "analyticsd", pid: 999_999, proto: "tcp4", ip: "17.253.144.10", port: 443, bytesIn: 1, bytesOut: 1),
                      ConnectionSample(process: "curl", pid: getpid(), proto: "tcp4", ip: "17.253.144.10", port: 443, bytesIn: 1, bytesOut: 1)])
        ok = check(probe.observations.count == 1 && probe.observations.first?.process == "analyticsd" && probe.observations.first?.verdict == .telemetry,
                   "в журнал попадают только процессы Apple (analyticsd — да, Block — нет)") && ok
        probe.clear()
        let fake = Sighting(process: "analyticsd", pid: 1, path: "/usr/libexec/analyticsd", endpoint: "ocsp.apple.com", ip: "17.0.0.1", port: 443,
                               firstSeen: Date(), lastSeen: Date(), samples: 1, bytesIn: 0, bytesOut: 0, verdict: .telemetry)
        var foreign = fake
        foreign.endpoint = "tracker.example.com"
        var nameless = fake
        nameless.endpoint = nameless.ip
        var xp = fake
        xp.endpoint = "xp.apple.com"
        ok = check(!model.canBlock(fake) && !model.canBlock(foreign) && !model.canBlock(nameless) && model.canBlock(xp),
                   "закрыть можно только имя Apple: не неприкосновенное, не чужой домен, не голый адрес") && ok
        ok = check(AppleNetwork.hosts(AppleNetwork.observed, observed: ["xp.apple.com", "swscan.apple.com"]) == ["xp.apple.com"],
                   "найденное наблюдением не закрывает обновления, даже если попало в список") && ok

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

    /// Пробная программа во временной папке: объявляет тип app.block.selftest, LaunchServices её знает.
    static func probeApp() -> URL? {
        let app = FileManager.default.temporaryDirectory.appendingPathComponent("BlockProbe-\(getpid()).app")
        let contents = app.appendingPathComponent("Contents")
        try? FileManager.default.createDirectory(at: contents.appendingPathComponent("MacOS"), withIntermediateDirectories: true)
        let plist: NSDictionary = [
            "CFBundleIdentifier": "app.block.probe", "CFBundleName": "BlockProbe", "CFBundleExecutable": "probe",
            "CFBundlePackageType": "APPL", "CFBundleShortVersionString": "1.0", "CFBundleVersion": "1",
            "CFBundleDocumentTypes": [["CFBundleTypeName": "Проверочный файл", "CFBundleTypeRole": "Viewer",
                                       "LSHandlerRank": "Alternate", "LSItemContentTypes": ["app.block.selftest"]]],
        ]
        guard plist.write(to: contents.appendingPathComponent("Info.plist"), atomically: true) else { return nil }
        try? FileManager.default.copyItem(atPath: "/usr/bin/true", toPath: contents.appendingPathComponent("MacOS/probe").path)
        let status = LSRegisterURL(app as CFURL, true)
        if status != noErr { info("LSRegisterURL: \(status)") }
        return app
    }

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
        info("шаг 1: снимок")
        let before = fingerprint(local)
        info("правил без пароля: \(local.count); Apple в Dock: \(Dock.present(Set(Catalog.guardedBundles)))")
        info("шаг 1: применение")
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
        info("шаг 1: возврат")
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
            info("шаг 2: права администратора")
            let hostsBefore = HostsFile.read()
            let diagnostics = Catalog.rules.filter(\.action.isSystemPlist)
            let net = Catalog.network.filter { $0.id != "net.icloud" && $0.id != "net.observed" }
            let adminBefore = diagnostics.map { RuleEngine.snapshot($0) }
            await model.apply(diagnostics + net)
            model.refresh()
            for rule in diagnostics + net { ok = check(model.status(of: rule) == .blocked, "\(rule.title): \(model.status(of: rule).label)") && ok }
            info("аналитика: до \(adminBefore.map(\.summary)), сейчас \(diagnostics.map { RuleEngine.snapshot($0).summary })")
            ok = check(HostsFile.resolvesToNowhere("xp.apple.com"), "резолвер: xp.apple.com → 0.0.0.0") && ok
            ok = check(!HostsFile.resolvesToNowhere("swscan.apple.com"), "резолвер: обновления macOS (swscan.apple.com) открыты") && ok
            await model.revert(diagnostics + net)
            ok = check(HostsFile.read() == hostsBefore, "/etc/hosts после возврата — байт в байт как был") && ok
            ok = check(diagnostics.map { RuleEngine.snapshot($0) } == adminBefore, "аналитика Mac после возврата — как была") && ok
        } else {
            info("BLOCK_SUDO не задан — шаги с паролем администратора пропущены")
        }

        // 3. Программа по умолчанию: свой тип файла → TextEdit и обратно.
        info("шаг 2½: наблюдение за соединениями")
        // Своя нагрузка: медленная загрузка страницы apple.com, чтобы в выборке точно было соединение.
        let curl = Process()
        curl.executableURL = URL(fileURLWithPath: "/usr/bin/curl")
        curl.arguments = ["-s", "-o", "/dev/null", "--limit-rate", "3k", "-m", "40", "https://www.apple.com/"]
        try? curl.run()
        await wait(3) { false }
        let raw = Shell.run("/usr/bin/nettop", ["-L", "1", "-n", "-x", "-t", "external", "-J", "bytes_in,bytes_out"], timeout: 15)
        let connections = NettopParser.parse(raw.output)
        let pids = Set(connections.map(\.pid))
        info("nettop: строк \(raw.output.split(separator: "\n").count), соединений \(connections.count), процессов \(pids.count): \(Set(connections.map(\.process)).sorted().prefix(20))")
        if connections.isEmpty { print(raw.output.split(separator: "\n").prefix(12).joined(separator: "\n")) }
        ok = check(connections.contains { $0.process == "curl" && $0.port == 443 }, "nettop видит соединение curl к apple.com (без прав администратора)") && ok
        ok = check(pids.contains { $0 != curl.processIdentifier && $0 != getpid() }, "видны и чужие процессы, не только свои") && ok
        await model.monitor.sample()
        let apple = model.monitor.observations
        info("процессы Apple в сети: \(Set(apple.map(\.process)).sorted()); адреса: \(apple.prefix(8).map { "\($0.process)→\($0.endpoint) [\($0.verdict.title)]" })")
        ok = check(model.monitor.lastError == nil && model.monitor.lastSample != nil, "наблюдение сделало выборку") && ok
        curl.terminate()
        if Privileged.usesSudo {
            let before = HostsFile.read()
            await model.blockHosts(["xp.apple.com", "swscan.apple.com"])
            let inFile = HostsFile.blockedHosts(in: HostsFile.read())
            await wait(4) { HostsFile.resolvesToNowhere("xp.apple.com") }
            info("найденное в hosts: \(inFile.sorted()); состояние правила: \(model.observedRule.map { model.status(of: $0).label } ?? "-")")
            ok = check(inFile.contains("xp.apple.com") && !inFile.contains("swscan.apple.com") && HostsFile.resolvesToNowhere("xp.apple.com")
                       && !HostsFile.resolvesToNowhere("swscan.apple.com"),
                       "найденное наблюдением закрыто (xp.apple.com), обновления открыты") && ok
            await model.unblockHost("xp.apple.com")
            ok = check(HostsFile.read() == before && model.state.observedHosts.isEmpty, "и открыто обратно — hosts как был") && ok
        }

        info("шаг 3: программа по умолчанию")
        // Своя пробная программа, которая объявляет проверочный тип файла (как Lumen объявляет видео).
        let handler = Handler(kind: .contentType, value: "app.block.selftest")
        let probe = probeApp()
        let previous = Handlers.current(handler)
        info("тип app.block.selftest открывает: \(previous?.path ?? "никто"); пробная программа: \(probe?.path ?? "не создана")")
        if let probe {
            let done = await withTimeout(12) { try? await Handlers.set(handler, to: probe) }
            if let shots = ProcessInfo.processInfo.environment["BLOCK_SHOTS"] {
                Shell.run("/usr/sbin/screencapture", ["-x", shots + "/default-handler.png"])
            }
            await wait(3) { Handlers.currentBundleID(handler) == "app.block.probe" }
            let changed = Handlers.currentBundleID(handler) == "app.block.probe"
            if !changed && !done {
                // macOS спрашивает человека (системное окно) — в CI подтвердить некому; снимок экрана — в артефакте.
                info("программа по умолчанию: macOS ждёт подтверждения человека (снимок default-handler.png)")
            } else {
                ok = check(changed, "программа по умолчанию назначена (\(Handlers.currentBundleID(handler) ?? "никто"))") && ok
            }
            if let previous {
                try? await Handlers.set(handler, to: previous)
                await wait(3) { Handlers.current(handler)?.standardizedFileURL == previous.standardizedFileURL }
                ok = check(Handlers.current(handler)?.standardizedFileURL == previous.standardizedFileURL, "и возвращена прежняя (\(previous.lastPathComponent))") && ok
            }
        }

        info("шаг 4: сторож запуска")
        // 4. Сторож запуска: безопасная программа Apple закрывается почти сразу.
        let candidates = [("com.apple.tips", "Советы"), ("com.apple.Chess", "Шахматы"), ("com.apple.Stickies", "Записки")]
        if let candidate = candidates.first(where: { Handlers.appURL($0.0) != nil }), let url = Handlers.appURL(candidate.0) {
            let (bundle, name) = candidate
            let map: [String: CatalogApp?] = [bundle: nil]
            model.guardian.update(map)
            let started = Date()
            NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration()) { app, error in
                print("· запуск «\(name)»: \(app.map { "pid \($0.processIdentifier)" } ?? "нет") \(error.map { "\($0)" } ?? "")")
            }
            await wait(8) { model.guardian.lastStop?.bundle == bundle }
            await wait(2) { NSRunningApplication.runningApplications(withBundleIdentifier: bundle).allSatisfy(\.isTerminated) }
            info("запущено сейчас: \(NSWorkspace.shared.runningApplications.compactMap(\.bundleIdentifier).filter { $0.hasPrefix("com.apple.") }.suffix(8))")
            let stop = model.guardian.lastStop
            let alive = NSRunningApplication.runningApplications(withBundleIdentifier: bundle).filter { !$0.isTerminated }
            info("«\(name)»: закрыта через \(String(format: "%.2f", stop?.seconds ?? -1)) с после старта процесса, \(String(format: "%.2f", Date().timeIntervalSince(started))) с от запроса")
            ok = check(stop?.bundle == bundle && alive.isEmpty, "сторож закрыл «\(name)»") && ok
            ok = check((stop?.seconds ?? 99) < 1.0, "меньше чем за секунду после запуска") && ok
            model.guardian.update([:])
        } else {
            info("безопасной программы для проверки сторожа нет")
        }

        info("шаг 5: плашка сторожа")
        // 5. Плашка: показ, закрытие кнопкой, по таймеру и снова показ — окно одно и не освобождается
        // (macOS 27 падал, когда окно уничтожалось во время обработки нажатия стеклянной кнопки).
        let guardian = model.guardian
        guardian.showBanner(name: "Проверка", bundle: "app.block.selftest", replacement: "Harbor", force: true)
        await wait(1) { guardian.bannerWindow?.isVisible == true }
        let first = guardian.bannerWindow
        ok = check(first?.isVisible == true, "плашка показана") && ok
        var reused = true
        for i in 0..<25 {
            guardian.showBanner(name: "Проверка \(i)", bundle: "app.block.selftest", replacement: nil, force: true)
            try? await Task.sleep(for: .milliseconds(30))
            if i.isMultiple(of: 2) { guardian.pressBannerClose() } else { guardian.hideBanner() }
            try? await Task.sleep(for: .milliseconds(30))
            reused = reused && guardian.bannerWindow === first
        }
        await wait(1) { guardian.bannerWindow?.isVisible == false }
        ok = check(reused && guardian.bannerWindow?.isVisible == false, "25 раз показана и закрыта (кнопкой и напрямую) — окно то же, программа жива") && ok
        guardian.showBanner(name: "Проверка", bundle: "app.block.selftest", replacement: nil, force: true)
        await wait(8) { guardian.bannerWindow?.isVisible == false }
        ok = check(guardian.bannerWindow?.isVisible == false && guardian.bannerWindow === first, "плашка спряталась сама через 6 с") && ok
        return ok
    }
}
