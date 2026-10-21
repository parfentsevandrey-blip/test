import Foundation

/// Поиск следов программ — для удаления программы и для «остатков удалённых программ».
///
/// Правило, выведенное из ошибок других деинсталляторов (Pearcleaner, CCleaner): главный признак —
/// идентификатор программы и её вложенных помощников. Совпадение по имени — только «вероятно» и только
/// точное (без «похоже на»), а общие папки производителя («Google», «Microsoft», «Adobe») не трогаются,
/// если ими пользуется другая установленная программа.
nonisolated enum Leftovers {
    nonisolated enum Style { case idOnly, idOrName, groups, launchd }

    nonisolated struct Place {
        let path: String
        let kind: String
        let style: Style
        let method: RemovalMethod
        /// Восстановимое (кэши, состояние окон): отмечаем даже при совпадении только по имени.
        let regenerable: Bool
    }

    static let userPlaces: [Place] = [
        Place(path: "Library/Application Support", kind: "Данные программы", style: .idOrName, method: .trash, regenerable: false),
        Place(path: "Library/Caches", kind: "Кэш", style: .idOrName, method: .delete, regenerable: true),
        Place(path: "Library/Preferences", kind: "Настройки", style: .idOnly, method: .trash, regenerable: false),
        Place(path: "Library/Preferences/ByHost", kind: "Настройки", style: .idOnly, method: .trash, regenerable: false),
        Place(path: "Library/Containers", kind: "Контейнер", style: .idOnly, method: .trash, regenerable: false),
        Place(path: "Library/Group Containers", kind: "Общий контейнер", style: .groups, method: .trash, regenerable: false),
        Place(path: "Library/Saved Application State", kind: "Состояние окон", style: .idOnly, method: .delete, regenerable: true),
        Place(path: "Library/HTTPStorages", kind: "Сетевые данные", style: .idOnly, method: .delete, regenerable: true),
        Place(path: "Library/WebKit", kind: "Данные WebKit", style: .idOnly, method: .delete, regenerable: true),
        Place(path: "Library/Cookies", kind: "Cookie", style: .idOnly, method: .delete, regenerable: true),
        Place(path: "Library/Logs", kind: "Журналы", style: .idOrName, method: .delete, regenerable: true),
        Place(path: "Library/Application Scripts", kind: "Скрипты", style: .idOnly, method: .delete, regenerable: true),
        Place(path: "Library/LaunchAgents", kind: "Автозапуск", style: .launchd, method: .trash, regenerable: false),
        Place(path: "Library/Internet Plug-Ins", kind: "Плагин", style: .idOrName, method: .trash, regenerable: false),
        Place(path: "Library/PreferencePanes", kind: "Панель настроек", style: .idOrName, method: .trash, regenerable: false),
        Place(path: "Library/QuickLook", kind: "Плагин Quick Look", style: .idOrName, method: .trash, regenerable: false),
        Place(path: "Library/Spotlight", kind: "Плагин Spotlight", style: .idOrName, method: .trash, regenerable: false),
        Place(path: "Library/Services", kind: "Служба", style: .idOrName, method: .trash, regenerable: false),
    ]

    static let systemPlaces: [Place] = [
        Place(path: "/Library/Application Support", kind: "Данные программы (система)", style: .idOrName, method: .admin, regenerable: false),
        Place(path: "/Library/Caches", kind: "Кэш (система)", style: .idOnly, method: .admin, regenerable: true),
        Place(path: "/Library/Preferences", kind: "Настройки (система)", style: .idOnly, method: .admin, regenerable: false),
        Place(path: "/Library/LaunchAgents", kind: "Автозапуск (система)", style: .launchd, method: .admin, regenerable: false),
        Place(path: "/Library/LaunchDaemons", kind: "Фоновая служба", style: .launchd, method: .admin, regenerable: false),
        Place(path: "/Library/PrivilegedHelperTools", kind: "Привилегированный помощник", style: .idOnly, method: .admin, regenerable: false),
        Place(path: "/Library/Logs", kind: "Журналы (система)", style: .idOrName, method: .admin, regenerable: true),
        Place(path: "/Library/Internet Plug-Ins", kind: "Плагин (система)", style: .idOrName, method: .admin, regenerable: false),
        Place(path: "/Library/PreferencePanes", kind: "Панель настроек (система)", style: .idOrName, method: .admin, regenerable: false),
    ]

    static let strippedExtensions: Set<String> = ["plist", "savedstate", "binarycookies", "appex", "plugin", "bundle", "prefpane",
                                                  "qlgenerator", "mdimporter", "workflow", "log", "service", "app"]

    static func stem(_ url: URL) -> String {
        var name = url.lastPathComponent
        while let dot = name.lastIndex(of: "."), strippedExtensions.contains(String(name[name.index(after: dot)...]).lowercased()) {
            name = String(name[..<dot])
        }
        return name
    }

    nonisolated enum Match: Equatable { case exact, likely }

    // MARK: - Следы конкретной программы

    static func find(for app: AppBundle, _ ctx: ScanContext, includeSystem: Bool = true) -> [JunkItem] {
        let others = ctx.installed.apps.filter { $0.url != app.url && $0.bundleID != app.bundleID }
        let ids = app.allIDs.map { $0.lowercased() }
        var names = Set([app.name, app.url.deletingPathExtension().lastPathComponent].map(InstalledIndex.normalize))
        if let exe = app.executable { names.insert(InstalledIndex.normalize(exe)) }
        names = names.filter { $0.count >= 4 }
        let otherIDs = Set(others.flatMap { $0.allIDs.map { $0.lowercased() } })
        let otherNames = Set(others.flatMap { [InstalledIndex.normalize($0.name)] + ($0.executable.map { [InstalledIndex.normalize($0)] } ?? []) })

        func idMatch(_ stem: String) -> Bool {
            let l = stem.lowercased()
            guard !otherIDs.contains(l) else { return false }
            return ids.contains { l == $0 || l.hasPrefix($0 + ".") }
        }
        func nameMatch(_ stem: String) -> Bool {
            let n = InstalledIndex.normalize(stem)
            return names.contains(n) && !otherNames.contains(n)
        }

        var items: [JunkItem] = []
        let places = userPlaces.map { (ctx.home.appendingPathComponent($0.path), $0) }
            + (includeSystem ? systemPlaces.map { (URL(fileURLWithPath: $0.path), $0) } : [])
        for (root, place) in places {
            for entry in DiskUsage.children(of: root) {
                let s = stem(entry)
                var match: Match?
                switch place.style {
                case .idOnly:
                    if idMatch(s) { match = .exact }
                case .idOrName:
                    if idMatch(s) { match = .exact } else if nameMatch(s) { match = .likely }
                case .groups:
                    let l = s.lowercased()
                    if app.appGroups.contains(where: { $0.lowercased() == l }), ctx.installed.sharedGroupCount(l) <= 1 { match = .exact }
                case .launchd:
                    if idMatch(s) || launchdPoints(entry, into: app) { match = .exact }
                }
                if let match {
                    items.append(item(entry, place: place, match: match, ctx: ctx, title: entry.lastPathComponent))
                }
            }
            // «Производитель/Программа» внутри Application Support (например, «JetBrains/PyCharm»).
            if place.style == .idOrName, place.path.hasSuffix("Application Support"), let vendor = vendorName(app) {
                for entry in DiskUsage.children(of: root) where InstalledIndex.normalize(entry.lastPathComponent) == vendor {
                    for inner in DiskUsage.children(of: entry) where nameMatch(stem(inner)) || idMatch(stem(inner)) {
                        items.append(item(inner, place: place, match: .likely, ctx: ctx,
                                          title: "\(entry.lastPathComponent)/\(inner.lastPathComponent)"))
                    }
                }
            }
        }
        // Отчёты о сбоях этой программы.
        if let exe = app.executable {
            let reports = DiskUsage.children(of: ctx.lib("Logs/DiagnosticReports")).filter {
                let n = $0.lastPathComponent
                return n.hasPrefix(exe + "-") || n.hasPrefix(exe + "_")
            }
            if !reports.isEmpty {
                let size = reports.reduce(Int64(0)) { $0 + DiskUsage.size(of: $1) }
                items.append(JunkItem(url: ctx.lib("Logs/DiagnosticReports/\(exe)"), targets: reports, title: "\(reports.count) отчёт(ов) о сбоях",
                                      detail: "~/Library/Logs/DiagnosticReports", size: size, modified: nil, selected: true, risk: .safe,
                                      method: .delete, group: "Отчёты о сбоях"))
            }
        }
        // Квитанции установщика (pkg) — только «забыть», сами файлы pkg разбросаны по системе и не трогаются.
        if includeSystem {
            for receipt in DiskUsage.children(of: URL(fileURLWithPath: "/private/var/db/receipts")) where receipt.pathExtension == "plist" {
                let pkg = receipt.deletingPathExtension().lastPathComponent
                if idMatch(pkg) {
                    items.append(JunkItem(url: receipt, targets: [], title: pkg, detail: "Квитанция установщика", size: 0, modified: nil,
                                          selected: true, risk: .safe, method: .admin, special: .forgetReceipt(pkg), group: "Квитанции установщика"))
                }
            }
        }

        var seen = Set<String>()
        return items.filter { item in
            guard seen.insert(item.url.path).inserted, !ctx.isIgnored(item.url) else { return false }
            if item.special != nil { return true }
            return !Safety.verdict(for: item.url, home: ctx.home, allowAdmin: item.method == .admin).isForbidden
        }
    }

    private static func item(_ entry: URL, place: Place, match: Match, ctx: ScanContext, title: String) -> JunkItem {
        let s = DiskUsage.summary(of: entry)
        let verdict = Safety.verdict(for: entry, home: ctx.home, allowAdmin: place.method == .admin)
        var selected = match == .exact || place.regenerable
        var note: String?
        if case .sensitive(let reason) = verdict {
            selected = false
            note = reason
        }
        if place.path.hasSuffix("Containers") && !ctx.hasFullDiskAccess {
            note = "Размер не виден без полного доступа к диску"
        }
        if match == .likely && !place.regenerable { note = note ?? "Совпадение по имени — проверьте" }
        return JunkItem(url: entry, targets: [entry], title: title, detail: Format.path(entry, home: ctx.home), size: s.size,
                        modified: s.newest, selected: selected, risk: match == .exact ? .safe : .review, method: place.method,
                        note: note, group: place.kind)
    }

    /// Второй компонент идентификатора — производитель («com.jetbrains.pycharm» → «jetbrains»).
    static func vendorName(_ app: AppBundle) -> String? {
        let parts = app.bundleID.split(separator: ".")
        guard parts.count >= 3 else { return nil }
        let v = InstalledIndex.normalize(String(parts[1]))
        return v.count >= 3 ? v : nil
    }

    /// Задание launchd запускает что-то изнутри программы?
    static func launchdPoints(_ plist: URL, into app: AppBundle) -> Bool {
        guard let dict = NSDictionary(contentsOf: plist) as? [String: Any] else { return false }
        var paths: [String] = []
        if let p = dict["Program"] as? String { paths.append(p) }
        if let args = dict["ProgramArguments"] as? [String], let first = args.first { paths.append(first) }
        if let ids = dict["AssociatedBundleIdentifiers"] as? [String], ids.contains(where: { app.allIDs.contains($0) }) { return true }
        if let id = dict["AssociatedBundleIdentifiers"] as? String, app.allIDs.contains(id) { return true }
        let root = app.url.path + "/"
        return paths.contains { $0.hasPrefix(root) }
    }

    // MARK: - Остатки уже удалённых программ

    nonisolated struct OrphanPlace {
        let path: String
        let kind: String
        let method: RemovalMethod
        /// Можно отмечать заранее (данные восстановимые).
        let regenerable: Bool
    }

    static let orphanPlaces: [OrphanPlace] = [
        OrphanPlace(path: "Library/Caches", kind: "Кэш", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/Saved Application State", kind: "Состояние окон", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/HTTPStorages", kind: "Сетевые данные", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/WebKit", kind: "Данные WebKit", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/Cookies", kind: "Cookie", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/Logs", kind: "Журналы", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/Application Scripts", kind: "Скрипты", method: .delete, regenerable: true),
        OrphanPlace(path: "Library/Application Support", kind: "Данные программы", method: .trash, regenerable: false),
        OrphanPlace(path: "Library/Preferences", kind: "Настройки", method: .trash, regenerable: false),
        OrphanPlace(path: "Library/Containers", kind: "Контейнер", method: .trash, regenerable: false),
    ]

    /// Отсутствующий хозяин определяется очень осторожно: имя должно быть идентификатором программы,
    /// ни одна установленная программа (и Launch Services) не должна его знать, а сами данные
    /// не должны меняться последние 30 дней — иначе ими явно кто-то пользуется.
    static func orphans(_ ctx: ScanContext) -> [JunkItem] {
        var candidates: [(URL, OrphanPlace, String)] = []
        for place in orphanPlaces {
            if place.path.hasSuffix("Containers") && !ctx.hasFullDiskAccess { continue }
            for entry in DiskUsage.children(of: ctx.home.appendingPathComponent(place.path)) {
                let id = stem(entry)
                let l = id.lowercased()
                guard InstalledIndex.looksLikeBundleID(id), !l.hasPrefix("com.apple."), !l.hasPrefix("group.com.apple"),
                      !l.hasPrefix("apple."), !JunkScanner.isProtectedCache(id), !Safety.isVault(l) else { continue }
                candidates.append((entry, place, id))
            }
        }
        // Проверка владельца — последовательно (Launch Services), размеры — параллельно.
        let owned = Dictionary(grouping: candidates.map(\.2), by: { $0 }).keys
            .reduce(into: [String: Bool]()) { $0[$1] = ctx.installed.owns($1, useLaunchServices: ctx.useLaunchServices) }
        let orphans = candidates.filter { owned[$0.2] == false && JunkScanner.runningOwner($0.2, ctx) == nil }
        return JunkScanner.parallelMap(orphans) { candidate -> JunkItem? in
            let (entry, place, id) = candidate
            let s = DiskUsage.summary(of: entry)
            let idle = (s.newest ?? .distantPast).daysBefore(ctx.now)
            guard idle > 30, s.size > 0 else { return nil }
            return JunkItem(url: entry, targets: [entry], title: id, detail: place.kind, size: s.size, modified: s.newest,
                            selected: place.regenerable && idle > 90, risk: place.regenerable ? .safe : .review, method: place.method,
                            bundleID: id, note: place.regenerable ? nil : "Данные программы — если собираетесь её переустановить, оставьте",
                            group: id)
        }
    }
}
