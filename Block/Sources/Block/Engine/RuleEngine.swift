import AppKit

/// Четыре шага каждого правила: проба, применение (снимок — до изменения), проверка, возврат.
/// Шаги с правами администратора (системные настройки, /etc/hosts) не выполняются здесь по одному,
/// а собираются в пачку команд — один запрос пароля на всё применение.
enum RuleEngine {
    // MARK: Проба

    static func probe(_ rule: Rule, _ state: BlockState) -> RuleState {
        switch rule.action {
        case .prefs(let keys, _):
            let done = keys.filter { Preferences.matches(Preferences.read($0), $0.value) }.count
            return count(done, keys.count)

        case .systemPlist(let plist):
            let done = plist.keys.filter { Preferences.matches(Preferences.readSystem(plist.path, key: $0.key), $0.value) }.count
            return count(done, plist.keys.count)

        case .pane:
            return .manual(done: state.manualDone.contains(rule.id))

        case .handlers(let handlers, let app):
            guard Handlers.appURL(app.bundleID) != nil else { return .unavailable("\(app.name) не установлен") }
            let known = handlers.filter { $0.kind == .scheme || Handlers.type($0) != nil }
            let done = known.filter { Handlers.currentBundleID($0) == app.bundleID }.count
            return count(done, known.count)

        case .launchGuard(let bundle, _):
            guard Handlers.appURL(bundle) != nil else { return .unavailable("нет в этой системе") }
            return state.baseline[rule.id] != nil ? .blocked : .active

        case .agent(let label):
            guard Launchd.exists(label) else { return .unavailable("нет в этой версии macOS") }
            if Launchd.override(label) == .disabled { return .blocked }
            return state.baseline[rule.id] != nil ? .protected("защищена системой") : .active

        case .dock(let ids):
            return Dock.present(Set(ids)).isEmpty ? .blocked : .active

        case .network(let group):
            switch state.networkMethod {
            case .hosts:
                let hosts = AppleNetwork.hosts(group, observed: state.observedHosts)
                if hosts.isEmpty { return .unavailable("наблюдение пока ничего не нашло") }
                let blocked = HostsFile.blockedHosts(in: HostsFile.read())
                let done = hosts.filter { blocked.contains($0) }.count
                return count(done, hosts.count)
            case .veil:
                guard VeilRoutes.installed else { return .unavailable("Veil не установлен") }
                if AppleNetwork.hosts(group, observed: state.observedHosts).isEmpty { return .unavailable("наблюдение пока ничего не нашло") }
                return state.veilGroups.contains(group.id) ? .blocked : .active
            }
        }
    }

    /// Всё, что сейчас должно быть закрыто в Veil: группы и найденное наблюдением.
    static func veilDomains(_ state: BlockState) -> [String] {
        (AppleNetwork.groups + [AppleNetwork.observed]).filter { state.veilGroups.contains($0.id) }.flatMap { group in
            group.id == AppleNetwork.observed.id ? AppleNetwork.hosts(group, observed: state.observedHosts) : group.hosts + group.suffixes
        }
    }

    private static func count(_ done: Int, _ total: Int) -> RuleState {
        if total == 0 { return .unavailable("неприменимо") }
        if done == total { return .blocked }
        if done == 0 { return .active }
        return .partial("сделано \(done) из \(total)")
    }

    // MARK: Снимок

    static func snapshot(_ rule: Rule) -> Snapshot {
        switch rule.action {
        case .prefs(let keys, _):
            return .prefs(keys.map { PrefBefore(key: $0, value: Preferences.read($0)) })
        case .systemPlist(let plist):
            var values: [String: PlistValue?] = [:]
            for key in plist.keys.keys { values[key] = .some(Preferences.readSystem(plist.path, key: key)) }
            return .system(path: plist.path, values: values)
        case .handlers(let handlers, _):
            var map: [String: String] = [:]
            for h in handlers { map["\(h.kind.rawValue):\(h.value)"] = Handlers.current(h)?.path ?? "" }
            return .handlers(map)
        case .agent(let label):
            return .agent(Launchd.override(label), loaded: Launchd.isLoaded(label))
        case .dock:
            return .dock(nil)
        case .pane, .launchGuard, .network:
            return .flag
        }
    }

    // MARK: Применение без прав администратора

    /// Применить правило. Возвращает снимок «до» и текст ошибки (если что-то не получилось).
    static func apply(_ rule: Rule, _ state: inout BlockState) async -> (Snapshot, String?) {
        let before = snapshot(rule)
        switch rule.action {
        case .prefs(let keys, let restart):
            for key in keys { Preferences.write(key, key.value) }
            for process in restart { Shell.killall(process) }
            return (before, nil)

        case .handlers(let handlers, let app):
            guard let url = Handlers.appURL(app.bundleID) else { return (before, "\(app.name) не установлен — поставьте его через Harbor") }
            var failures: [String] = []
            for h in handlers where h.kind == .scheme || Handlers.type(h) != nil {
                do { try await Handlers.set(h, to: url) } catch { failures.append(h.value) }
            }
            return (before, failures.isEmpty ? nil : "не удалось: " + failures.joined(separator: ", "))

        case .launchGuard(let bundle, _):
            for app in NSRunningApplication.runningApplications(withBundleIdentifier: bundle) { app.terminate() }
            return (before, nil)

        case .agent(let label):
            return (before, Launchd.disable(label))

        case .dock(let ids):
            return (.dock(Dock.remove(Set(ids))), nil)

        case .pane(let url, _):
            if let u = URL(string: url) { NSWorkspace.shared.open(u) }
            return (before, nil)

        case .network(let group):
            if state.networkMethod == .veil {
                state.veilGroups.insert(group.id)
                return (before, VeilRoutes.send(veilDomains(state)) ? nil : "Veil не установлен")
            }
            return (before, nil)   // hosts — пачкой с правами администратора

        case .systemPlist:
            return (before, nil)   // пачкой с правами администратора
        }
    }

    /// Команды администратора для правила (применение или возврат к снимку).
    static func adminCommands(_ rule: Rule, revertTo snapshot: Snapshot?) -> [String] {
        guard case .systemPlist(let plist) = rule.action else { return [] }
        if case .system(_, let values)? = snapshot {
            return values.keys.sorted().map { Preferences.systemCommand(plist.path, key: $0, value: values[$0] ?? nil) }
        }
        return plist.keys.keys.sorted().map { Preferences.systemCommand(plist.path, key: $0, value: plist.keys[$0]) }
    }

    // MARK: Возврат

    static func revert(_ rule: Rule, to snapshot: Snapshot, _ state: inout BlockState) async {
        switch (rule.action, snapshot) {
        case (.prefs(_, let restart), .prefs(let list)):
            for item in list { Preferences.write(item.key, item.value) }
            for process in restart { Shell.killall(process) }

        case (.handlers(let handlers, _), .handlers(let map)):
            for h in handlers {
                guard let path = map["\(h.kind.rawValue):\(h.value)"], !path.isEmpty, FileManager.default.fileExists(atPath: path) else { continue }
                try? await Handlers.set(h, to: URL(fileURLWithPath: path))
            }

        case (.agent(let label), .agent(let override, let loaded)):
            Launchd.restore(label, override: override, loaded: loaded)

        case (.dock, .dock(let data)):
            Dock.restore(data)

        case (.pane, _):
            state.manualDone.remove(rule.id)

        case (.network(let group), _):
            if state.networkMethod == .veil, state.veilGroups.remove(group.id) != nil {
                VeilRoutes.send(veilDomains(state))
            }

        default:
            break   // сторож — состояние в Block; системные настройки и hosts — пачкой администратора
        }
    }
}
