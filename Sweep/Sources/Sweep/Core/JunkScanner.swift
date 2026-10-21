import Foundation

/// Сканер «системного мусора». Ничего не удаляет — только находит и объясняет.
nonisolated enum JunkScanner {
    static func scan(_ ctx: ScanContext, categories: [CategoryID],
                     progress: @Sendable (CategoryID) -> Void = { _ in }) -> [JunkCategory] {
        categories.map { id in
            progress(id)
            var category = scan(id, ctx)
            category.items = category.items
                .filter { $0.size > 0 || $0.special != nil }
                .filter { !ctx.isIgnored($0.url) }
                .filter { item in
                    // Защита в глубину: каждую цель ещё раз проверяет политика безопасности.
                    item.targets.allSatisfy {
                        !Safety.verdict(for: $0, home: ctx.home, explicitlyAllowed: item.explicit, allowAdmin: item.method == .admin).isForbidden
                    }
                }
                .sorted { $0.size > $1.size }
            return category
        }
    }

    static func scan(_ id: CategoryID, _ ctx: ScanContext) -> JunkCategory {
        if id.needsFullDiskAccess && !ctx.hasFullDiskAccess && id != .trash {
            return JunkCategory(id: id, items: [], notice: "Нужен полный доступ к диску")
        }
        switch id {
        case .appCaches: return JunkCategory(id: id, items: appCaches(ctx))
        case .sandboxCaches: return JunkCategory(id: id, items: sandboxCaches(ctx))
        case .logs: return JunkCategory(id: id, items: logs(ctx))
        case .crashReports: return JunkCategory(id: id, items: crashReports(ctx))
        case .browserCaches: return JunkCategory(id: id, items: browserCaches(ctx))
        case .developer: return JunkCategory(id: id, items: developer(ctx))
        case .devArtifacts: return JunkCategory(id: id, items: devArtifacts(ctx))
        case .leftovers: return JunkCategory(id: id, items: Leftovers.orphans(ctx))
        case .installers:
            if ctx.avoidPrivacyPrompts { return JunkCategory(id: id, items: [], notice: "Загрузки не проверялись") }
            return JunkCategory(id: id, items: installers(ctx))
        case .deviceUpdates: return JunkCategory(id: id, items: deviceUpdates(ctx))
        case .deviceBackups: return JunkCategory(id: id, items: deviceBackups(ctx))
        case .mailDownloads: return JunkCategory(id: id, items: mailDownloads(ctx))
        case .trash: return trash(ctx)
        case .privacy: return JunkCategory(id: id, items: Privacy.items(ctx))
        }
    }

    // MARK: - Кэши

    /// Кэши, которые не трогаем никогда: iCloud и облачные диски (иначе синхронизация начинается заново),
    /// шрифты (после чистки — глюки в Adobe и Office), фоновые загрузки, анализ фото и ключи.
    static let protectedCachePrefixes = [
        "com.apple.bird", "com.apple.cloud", "com.apple.icloud", "cloudkit", "com.apple.akd", "familycircle",
        "com.apple.findmy", "com.apple.nsurlsessiond", "com.apple.containermanagerd", "com.apple.homed", "com.apple.home",
        "com.apple.passd", "com.apple.mediaanalysisd", "com.apple.photoanalysisd", "com.apple.photos", "com.apple.iconservices",
        "com.apple.safari.safebrowsing", "com.apple.tcc", "com.apple.keychain", "com.apple.security", "com.apple.trustd",
        "com.apple.ap.", "com.apple.applemediaservices", "com.apple.screentime", "com.apple.callhistory", "com.apple.siri",
        "com.apple.parsecd", "com.apple.knowledge", "com.apple.biome", "com.apple.mobilesync", "com.apple.itunescloud",
        "com.apple.coreduet", "com.apple.geo", "com.apple.passkit", "com.apple.appleaccount", "com.apple.identityservices",
        "com.apple.imfoundation", "com.apple.messages", "com.apple.mail", "com.apple.notes", "com.apple.amsengagementd",
    ]
    static let protectedCacheWords = ["font", "dropbox", "onedrive", "drivefs", "googledrive", "google.drive", "box.desktop",
                                      "pcloud", "yandex.disk", "mega.", "nextcloud", "owncloud", "synology", "icloud", "keychain"]

    /// Папки внутри ~/Library/Caches, которые показываем в «Мусоре разработчика», а не здесь.
    static let developerCacheNames: Set<String> = ["homebrew", "pip", "yarn", "cocoapods", "org.swift.swiftpm", "com.apple.dt.xcode",
                                                    "go-build", "pnpm", "deno", "node-gyp", "com.apple.dt.instruments", "jetbrains"]

    static func isProtectedCache(_ name: String) -> Bool {
        let l = name.lowercased()
        return protectedCachePrefixes.contains { l.hasPrefix($0) } || protectedCacheWords.contains { l.contains($0) }
    }

    static func runningOwner(_ name: String, _ ctx: ScanContext) -> String? {
        let l = name.lowercased()
        return ctx.runningBundleIDs.first { r in
            let rl = r.lowercased()
            return l == rl || l.hasPrefix(rl + ".") || rl.hasPrefix(l + ".")
        }
    }

    static func displayName(for name: String, _ ctx: ScanContext) -> String {
        if let app = ctx.installed.app(withID: name) { return app.name }
        return name
    }

    static func appCaches(_ ctx: ScanContext) -> [JunkItem] {
        let root = ctx.lib("Caches")
        return parallelMap(DiskUsage.children(of: root)) { entry -> JunkItem? in
            let name = entry.lastPathComponent
            guard !isProtectedCache(name), !developerCacheNames.contains(name.lowercased()),
                  !BrowserCatalog.cacheRootNames.contains(name) else { return nil }
            let s = DiskUsage.summary(of: entry)
            guard s.size > 0 else { return nil }
            var item = JunkItem(url: entry, targets: [entry], title: displayName(for: name, ctx), detail: name, size: s.size,
                                modified: s.newest, selected: true, risk: .safe, method: .delete,
                                bundleID: InstalledIndex.looksLikeBundleID(name) ? name : nil)
            if runningOwner(name, ctx) != nil {
                item.selected = false
                item.risk = .review
                item.note = "Программа запущена — закройте её, чтобы очистить кэш"
            } else if name.hasPrefix("com.apple."), let newest = s.newest, newest.daysBefore(ctx.now) < 1 {
                item.selected = false
                item.note = "Системный кэш используется прямо сейчас"
            }
            return item
        }
    }

    static func sandboxCaches(_ ctx: ScanContext) -> [JunkItem] {
        var places: [(URL, String)] = []
        for container in DiskUsage.children(of: ctx.lib("Containers")) {
            let id = container.lastPathComponent
            if id.hasPrefix("com.apple.") && id != "com.apple.Safari" { continue }
            places.append((container.appendingPathComponent("Data/Library/Caches"), id))
        }
        for group in DiskUsage.children(of: ctx.lib("Group Containers")) {
            let id = group.lastPathComponent
            if id.lowercased().contains("com.apple") { continue }
            places.append((group.appendingPathComponent("Library/Caches"), id))
        }
        return parallelMap(places) { pair -> JunkItem? in
            let (place, id) = pair
            guard DiskUsage.exists(place), !isProtectedCache(id) else { return nil }
            // Удаляем содержимое папки Caches, а не саму папку: её создаёт система для контейнера.
            let targets = DiskUsage.children(of: place, hidden: true)
            let s = DiskUsage.summary(of: place)
            guard s.size > 0, !targets.isEmpty else { return nil }
            var item = JunkItem(url: place, targets: targets, title: displayName(for: id, ctx), detail: id, size: s.size,
                                modified: s.newest, selected: true, risk: .safe, method: .delete, bundleID: id)
            if runningOwner(id, ctx) != nil {
                item.selected = false
                item.risk = .review
                item.note = "Программа запущена"
            }
            return item
        }
    }

    // MARK: - Журналы и отчёты

    static func logs(_ ctx: ScanContext) -> [JunkItem] {
        let cutoff = ctx.now.addingTimeInterval(-7 * 86_400)
        return parallelMap(DiskUsage.children(of: ctx.lib("Logs"))) { entry -> JunkItem? in
            let name = entry.lastPathComponent
            guard name != "DiagnosticReports", name != "CrashReporter" else { return nil }
            var targets: [URL] = []
            var size: Int64 = 0
            var newestOld: Date?
            if DiskUsage.isDirectory(entry) {
                _ = DiskUsage.summary(of: entry) { url, v in
                    if let m = v.contentModificationDate, m < cutoff {
                        targets.append(url)
                        size += Int64(v.totalFileAllocatedSize ?? v.fileAllocatedSize ?? 0)
                        if m > (newestOld ?? .distantPast) { newestOld = m }
                    }
                    return false
                }
            } else if let m = DiskUsage.modified(entry), m < cutoff {
                targets = [entry]
                size = DiskUsage.size(of: entry)
                newestOld = m
            }
            guard !targets.isEmpty, size > 0 else { return nil }
            return JunkItem(url: entry, targets: targets, title: displayName(for: name, ctx),
                            detail: targets.count == 1 ? nil : "\(targets.count) файлов старше недели", size: size,
                            modified: newestOld, selected: true, risk: .safe, method: .delete)
        }
    }

    static func crashReports(_ ctx: ScanContext) -> [JunkItem] {
        let exts: Set<String> = ["ips", "crash", "diag", "spin", "hang", "panic", "shutdownstall", "cpu_resource", "wakeups_resource"]
        var byApp: [String: [URL]] = [:]
        let root = ctx.lib("Logs/DiagnosticReports")
        _ = DiskUsage.summary(of: root) { url, _ in
            if exts.contains(url.pathExtension.lowercased()) || url.lastPathComponent.contains(".ips") {
                let app = url.lastPathComponent.split(whereSeparator: { $0 == "-" || $0 == "_" }).first.map(String.init) ?? "?"
                byApp[app, default: []].append(url)
            }
            return false
        }
        return byApp.map { app, files in
            let size = files.reduce(Int64(0)) { $0 + DiskUsage.size(of: $1) }
            let newest = files.compactMap(DiskUsage.modified).max()
            return JunkItem(url: root.appendingPathComponent(app), targets: files, title: app,
                            detail: "\(files.count) отчёт(ов)", size: size, modified: newest, selected: true, risk: .safe, method: .delete)
        }
    }

    // MARK: - Браузеры

    static func browserCaches(_ ctx: ScanContext) -> [JunkItem] {
        BrowserCatalog.chromium.compactMap { browser -> JunkItem? in
            let root = ctx.lib("Application Support/\(browser.supportPath)")
            guard DiskUsage.exists(root) else { return nil }
            var targets: [URL] = []
            for name in BrowserCatalog.rootCacheDirs {
                let u = root.appendingPathComponent(name)
                if DiskUsage.exists(u) { targets.append(u) }
            }
            for profile in BrowserCatalog.profiles(in: root) {
                for name in BrowserCatalog.profileCacheDirs {
                    let u = profile.appendingPathComponent(name)
                    if DiskUsage.exists(u) { targets.append(u) }
                }
            }
            // Основной кэш Chromium лежит в ~/Library/Caches/<производитель>/<браузер>.
            let cacheRoot = ctx.lib("Caches/\(browser.supportPath)")
            if DiskUsage.exists(cacheRoot) { targets.append(cacheRoot) }
            let size = targets.reduce(Int64(0)) { $0 + DiskUsage.size(of: $1) }
            guard size > 0 else { return nil }
            var item = JunkItem(url: root, targets: targets, title: browser.name, detail: "Кэш страниц, скриптов и шейдеров",
                                size: size, modified: nil, selected: true, risk: .safe, method: .delete, bundleID: browser.bundleID)
            if ctx.runningBundleIDs.contains(browser.bundleID) {
                item.selected = false
                item.risk = .review
                item.note = "Браузер открыт — закройте его, чтобы очистить кэш"
            }
            return item
        } + firefoxCaches(ctx)
    }

    static func firefoxCaches(_ ctx: ScanContext) -> [JunkItem] {
        let root = ctx.lib("Caches/Firefox")
        guard DiskUsage.exists(root) else { return [] }
        let size = DiskUsage.size(of: root)
        guard size > 0 else { return [] }
        var item = JunkItem(url: root, targets: DiskUsage.children(of: root, hidden: true), title: "Firefox", detail: "Кэш профилей",
                            size: size, modified: nil, selected: true, risk: .safe, method: .delete, bundleID: "org.mozilla.firefox")
        if ctx.runningBundleIDs.contains("org.mozilla.firefox") {
            item.selected = false
            item.risk = .review
            item.note = "Браузер открыт"
        }
        return [item]
    }

    // MARK: - Разработка

    nonisolated struct DevPlace {
        let path: String
        let title: String
        let detail: String
        let risk: Risk
        var contentsOnly = false
        var method: RemovalMethod = .delete
    }

    static let devPlaces: [DevPlace] = [
        DevPlace(path: "Library/Developer/Xcode/DerivedData", title: "Xcode: DerivedData", detail: "Промежуточные файлы сборки", risk: .safe, contentsOnly: true),
        DevPlace(path: "Library/Developer/CoreSimulator/Caches", title: "Симуляторы: кэши", detail: "Кэши dyld и среды выполнения", risk: .safe, contentsOnly: true),
        DevPlace(path: "Library/Caches/com.apple.dt.Xcode", title: "Xcode: кэш", detail: "Документация, индексы", risk: .safe),
        DevPlace(path: "Library/Caches/org.swift.swiftpm", title: "Swift Package Manager", detail: "Скачанные пакеты", risk: .safe),
        DevPlace(path: "Library/Caches/Homebrew", title: "Homebrew", detail: "Скачанные бутылки и исходники", risk: .safe),
        DevPlace(path: ".npm/_cacache", title: "npm", detail: "Кэш пакетов", risk: .safe),
        DevPlace(path: "Library/Caches/Yarn", title: "Yarn", detail: "Кэш пакетов", risk: .safe),
        DevPlace(path: "Library/Caches/pnpm", title: "pnpm", detail: "Кэш метаданных", risk: .safe),
        DevPlace(path: "Library/Caches/pip", title: "pip", detail: "Кэш колёс Python", risk: .safe),
        DevPlace(path: ".cache/pip", title: "pip", detail: "Кэш колёс Python", risk: .safe),
        DevPlace(path: "Library/Caches/CocoaPods", title: "CocoaPods", detail: "Кэш подов", risk: .safe),
        DevPlace(path: "Library/Caches/go-build", title: "Go", detail: "Кэш сборки", risk: .safe),
        DevPlace(path: ".cargo/registry/cache", title: "Cargo", detail: "Скачанные крейты", risk: .safe),
        DevPlace(path: ".bun/install/cache", title: "Bun", detail: "Кэш пакетов", risk: .safe),
        DevPlace(path: "Library/Caches/deno", title: "Deno", detail: "Кэш модулей", risk: .safe),
        DevPlace(path: ".gradle/caches", title: "Gradle", detail: "Зависимости — скачаются при следующей сборке", risk: .review),
        DevPlace(path: "Library/Developer/Xcode/Archives", title: "Xcode: архивы", detail: "Нужны, чтобы символизировать сбои выпущенных версий", risk: .caution, contentsOnly: true, method: .trash),
    ]

    static func developer(_ ctx: ScanContext) -> [JunkItem] {
        var items: [JunkItem] = parallelMap(devPlaces) { place -> JunkItem? in
            let url = ctx.home.appendingPathComponent(place.path)
            guard DiskUsage.exists(url) else { return nil }
            let targets = place.contentsOnly ? DiskUsage.children(of: url, hidden: true) : [url]
            let s = DiskUsage.summary(of: url)
            guard s.size > 0, !targets.isEmpty else { return nil }
            var item = JunkItem(url: url, targets: targets, title: place.title, detail: place.detail, size: s.size, modified: s.newest,
                                selected: place.risk == .safe, risk: place.risk, method: place.method)
            if place.path.contains("Xcode") && ctx.runningBundleIDs.contains("com.apple.dt.Xcode") {
                item.selected = false
                item.note = "Xcode открыт"
            }
            return item
        }
        items += deviceSupport(ctx)
        return items
    }

    /// Символы устройств: оставляем самую новую версию каждой платформы, старые — отмечаем.
    static func deviceSupport(_ ctx: ScanContext) -> [JunkItem] {
        var items: [JunkItem] = []
        for platform in ["iOS", "watchOS", "tvOS", "visionOS", "macOS"] {
            let root = ctx.lib("Developer/Xcode/\(platform) DeviceSupport")
            let versions = DiskUsage.children(of: root).sorted {
                ($0.lastPathComponent).compare($1.lastPathComponent, options: .numeric) == .orderedAscending
            }
            for (i, v) in versions.enumerated() {
                let latest = i == versions.count - 1
                let s = DiskUsage.summary(of: v)
                items.append(JunkItem(url: v, targets: [v], title: "\(platform) \(v.lastPathComponent)",
                                      detail: latest ? "Самая новая версия — оставьте для отладки на устройстве" : "Символы старой версии \(platform)",
                                      size: s.size, modified: s.newest, selected: !latest, risk: latest ? .review : .safe, method: .delete))
            }
        }
        return items
    }

    /// node_modules, .build, target, Pods — только рядом с файлом проекта.
    static func devArtifacts(_ ctx: ScanContext) -> [JunkItem] {
        let roots = ["Developer", "Projects", "projects", "GitHub", "Github", "dev", "Dev", "Code", "code", "src", "Sites", "repos", "Repos", "work"]
            .map { ctx.home.appendingPathComponent($0) }
            .filter { DiskUsage.exists($0) }
        let markers: [String: String] = ["node_modules": "package.json", ".build": "Package.swift", "target": "Cargo.toml", "Pods": "Podfile"]
        var found: [URL] = []
        func walk(_ dir: URL, depth: Int) {
            guard depth < 6, found.count < 2000 else { return }
            for child in DiskUsage.children(of: dir, hidden: true) {
                guard DiskUsage.isDirectory(child),
                      (try? child.resourceValues(forKeys: [.isSymbolicLinkKey]))?.isSymbolicLink != true else { continue }
                let name = child.lastPathComponent
                if let marker = markers[name] {
                    if DiskUsage.exists(dir.appendingPathComponent(marker)) { found.append(child) }
                    continue
                }
                if name.hasPrefix(".") || name.hasSuffix(".app") { continue }
                walk(child, depth: depth + 1)
            }
        }
        for root in roots { walk(root, depth: 0) }
        return parallelMap(found) { url -> JunkItem? in
            let s = DiskUsage.summary(of: url)
            let idle = (s.newest ?? .distantPast).daysBefore(ctx.now)
            let project = url.deletingLastPathComponent().lastPathComponent
            return JunkItem(url: url, targets: [url], title: "\(project)/\(url.lastPathComponent)",
                            detail: idle > 3650 ? nil : "Не менялось \(Int(idle)) дн.", size: s.size, modified: s.newest,
                            selected: idle > 60, risk: .review, method: .delete,
                            note: idle > 60 ? nil : "Проект в работе — восстановится командой сборки, но не сразу")
        }
    }

    // MARK: - Загрузки, устройства, почта

    static func installers(_ ctx: ScanContext) -> [JunkItem] {
        let exts: Set<String> = ["dmg", "pkg", "mpkg", "xip"]
        var items: [JunkItem] = []
        for folder in ["Downloads", "Desktop"] {
            for file in DiskUsage.children(of: ctx.home.appendingPathComponent(folder)) where exts.contains(file.pathExtension.lowercased()) {
                let m = DiskUsage.modified(file)
                guard (m ?? ctx.now).daysBefore(ctx.now) > 7 else { continue }
                items.append(JunkItem(url: file, targets: [file], title: file.lastPathComponent,
                                      detail: folder == "Downloads" ? "Загрузки" : "Рабочий стол", size: DiskUsage.size(of: file),
                                      modified: m, selected: false, risk: .review, method: .trash))
            }
        }
        return items
    }

    static func deviceUpdates(_ ctx: ScanContext) -> [JunkItem] {
        var items: [JunkItem] = []
        for folder in ["iPhone Software Updates", "iPad Software Updates", "iPod Software Updates"] {
            for file in DiskUsage.children(of: ctx.lib("iTunes/\(folder)")) where file.pathExtension.lowercased() == "ipsw" {
                items.append(JunkItem(url: file, targets: [file], title: file.lastPathComponent, detail: folder,
                                      size: DiskUsage.size(of: file), modified: DiskUsage.modified(file), selected: true, risk: .safe, method: .delete))
            }
        }
        return items
    }

    static func deviceBackups(_ ctx: ScanContext) -> [JunkItem] {
        parallelMap(DiskUsage.children(of: ctx.lib("Application Support/MobileSync/Backup"))) { backup -> JunkItem? in
            let info = NSDictionary(contentsOf: backup.appendingPathComponent("Info.plist"))
            let device = (info?["Device Name"] as? String) ?? (info?["Display Name"] as? String) ?? "Устройство"
            let date = info?["Last Backup Date"] as? Date
            let product = info?["Product Name"] as? String
            let s = DiskUsage.summary(of: backup)
            return JunkItem(url: backup, targets: [backup], title: device,
                            detail: [product, "копия от \(Format.date(date ?? s.newest))"].compactMap { $0 }.joined(separator: " · "),
                            size: s.size, modified: date ?? s.newest, selected: false, risk: .caution, method: .trash,
                            note: "Убедитесь, что есть копия в iCloud или на другом диске", explicit: true)
        }
    }

    static func mailDownloads(_ ctx: ScanContext) -> [JunkItem] {
        let root = ctx.lib("Containers/com.apple.mail/Data/Library/Mail Downloads")
        let entries = DiskUsage.children(of: root, hidden: true)
        let s = DiskUsage.summary(of: root)
        guard s.size > 0, !entries.isEmpty else { return [] }
        return [JunkItem(url: root, targets: entries, title: "Открытые вложения", detail: "\(entries.count) папок",
                         size: s.size, modified: s.newest, selected: true, risk: .safe, method: .delete, bundleID: "com.apple.mail", explicit: true)]
    }

    // MARK: - Корзина

    static func trash(_ ctx: ScanContext) -> JunkCategory {
        var items: [JunkItem] = []
        var notice: String?
        if ctx.avoidPrivacyPrompts {
            notice = "Корзина не проверялась"
        } else if ctx.hasFullDiskAccess {
            let root = ctx.home.appendingPathComponent(".Trash")
            let entries = DiskUsage.children(of: root, hidden: true).filter { $0.lastPathComponent != ".DS_Store" }
            let s = DiskUsage.summary(of: root)
            if s.size > 0 {
                items.append(JunkItem(url: root, targets: entries, title: "Корзина", detail: "\(entries.count) объектов",
                                      size: s.size, modified: s.newest, selected: true, risk: .safe, method: .delete))
            }
        } else {
            notice = "Без полного доступа к диску Корзина очищается через Finder"
        }
        let uid = getuid()
        for volume in ctx.volumes {
            let root = volume.appendingPathComponent(".Trashes/\(uid)")
            let entries = DiskUsage.children(of: root, hidden: true)
            let s = DiskUsage.summary(of: root)
            guard s.size > 0 else { continue }
            items.append(JunkItem(url: root, targets: entries, title: "Корзина на «\(volume.lastPathComponent)»",
                                  detail: "\(entries.count) объектов", size: s.size, modified: s.newest, selected: true, risk: .safe, method: .delete))
        }
        return JunkCategory(id: .trash, items: items, notice: notice)
    }

    // MARK: - Утилиты

    static func parallelMap<T, R>(_ input: [T], _ transform: (T) -> R?) -> [R] {
        let lock = NSLock()
        var out: [R?] = Array(repeating: nil, count: input.count)
        DispatchQueue.concurrentPerform(iterations: input.count) { i in
            let r = transform(input[i])
            lock.withLock { out[i] = r }
        }
        return out.compactMap { $0 }
    }
}

/// Браузеры на Chromium: где лежат профили и какие папки внутри — чистый кэш.
nonisolated enum BrowserCatalog {
    nonisolated struct Browser: Sendable {
        let name: String
        let bundleID: String
        /// Путь внутри Application Support (и такой же — внутри Caches).
        let supportPath: String
    }

    static let chromium: [Browser] = [
        Browser(name: "Google Chrome", bundleID: "com.google.Chrome", supportPath: "Google/Chrome"),
        Browser(name: "Яндекс Браузер", bundleID: "ru.yandex.desktop.yandex-browser", supportPath: "Yandex/YandexBrowser"),
        Browser(name: "Microsoft Edge", bundleID: "com.microsoft.edgemac", supportPath: "Microsoft Edge"),
        Browser(name: "Brave", bundleID: "com.brave.Browser", supportPath: "BraveSoftware/Brave-Browser"),
        Browser(name: "Arc", bundleID: "company.thebrowser.Browser", supportPath: "Arc/User Data"),
        Browser(name: "Opera", bundleID: "com.operasoftware.Opera", supportPath: "com.operasoftware.Opera"),
        Browser(name: "Vivaldi", bundleID: "com.vivaldi.Vivaldi", supportPath: "Vivaldi"),
        Browser(name: "Chromium", bundleID: "org.chromium.Chromium", supportPath: "Chromium"),
    ]

    /// Верхние имена в ~/Library/Caches, которые относятся к браузерам (их считаем в «Кэшах браузеров»).
    static let cacheRootNames: Set<String> = ["Google", "Yandex", "Microsoft Edge", "BraveSoftware", "Arc", "com.operasoftware.Opera",
                                              "Vivaldi", "Chromium", "Firefox"]

    static let rootCacheDirs = ["GrShaderCache", "ShaderCache", "GraphiteDawnCache", "component_crx_cache"]
    static let profileCacheDirs = ["Cache", "Code Cache", "GPUCache", "DawnCache", "DawnGraphiteCache", "DawnWebGPUCache",
                                   "Service Worker/CacheStorage", "Service Worker/ScriptCache", "Application Cache"]

    static func profiles(in root: URL) -> [URL] {
        DiskUsage.children(of: root).filter {
            let n = $0.lastPathComponent
            return n == "Default" || n.hasPrefix("Profile ") || n == "Guest Profile" || n == "System Profile"
        }
    }
}
