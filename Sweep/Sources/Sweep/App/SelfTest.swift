import AppKit
import Foundation

/// Самопроверки, которые CI запускает прямо в собранном бинарнике.
///   --selftest-safety <папка>  — выдуманный «дом» с мусором, остатками и ценными файлами: всё лишнее найдено,
///                                 всё ценное пережило очистку и удаление программы
///   --selftest-real            — только сканирование настоящего дома раннера: сводка и проверка инвариантов
///   --selftest-render <папка>  — визуализации в PNG
enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        setvbuf(stdout, nil, _IOLBF, 0)
        let rest = Array(args.drop { $0 != mode }.dropFirst())
        let dir = URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory())
        var ok = false
        switch mode {
        case "--selftest-safety": ok = safety(dir)
        case "--selftest-real": ok = real()
        case "--selftest-render": ok = RenderSelfTest.run(dir)
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

    // MARK: - Выдуманный дом

    static func write(_ url: URL, size: Int = 4096, age days: Double = 0, random: Bool = false) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let data = random ? Data((0..<size).map { _ in UInt8.random(in: 0...255) }) : Data(repeating: 0x5A, count: size)
        try? data.write(to: url)
        if days > 0 {
            let date = Date().addingTimeInterval(-days * 86_400)
            try? FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
        }
    }

    static func age(_ url: URL, days: Double) {
        let date = Date().addingTimeInterval(-days * 86_400)
        if let e = FileManager.default.enumerator(at: url, includingPropertiesForKeys: nil) {
            for case let f as URL in e { try? FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: f.path) }
        }
        try? FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
    }

    static func makeApp(_ root: URL, name: String, id: String, helper: String? = nil) {
        let app = root.appendingPathComponent("\(name).app/Contents")
        try? FileManager.default.createDirectory(at: app.appendingPathComponent("MacOS"), withIntermediateDirectories: true)
        let info: NSDictionary = ["CFBundleIdentifier": id, "CFBundleName": name, "CFBundleExecutable": name]
        info.write(to: app.appendingPathComponent("Info.plist"), atomically: true)
        write(app.appendingPathComponent("MacOS/\(name)"), size: 20_000)
        if let helper {
            let ext = app.appendingPathComponent("PlugIns/\(name)Share.appex/Contents")
            try? FileManager.default.createDirectory(at: ext, withIntermediateDirectories: true)
            (["CFBundleIdentifier": helper] as NSDictionary).write(to: ext.appendingPathComponent("Info.plist"), atomically: true)
        }
    }

    static func safety(_ base: URL) -> Bool {
        let fm = FileManager.default
        let root = base.appendingPathComponent("sweep-safety-\(UUID().uuidString.prefix(6))")
        let home = root.appendingPathComponent("home")
        let lib = home.appendingPathComponent("Library")
        func L(_ p: String) -> URL { lib.appendingPathComponent(p) }
        func H(_ p: String) -> URL { home.appendingPathComponent(p) }
        var ok = true

        // Программы.
        let apps = H("Applications")
        makeApp(apps, name: "Foozle", id: "com.foo.app", helper: "com.foo.app.share")
        makeApp(apps, name: "Other", id: "com.installed.other")
        makeApp(apps, name: "Google Chrome", id: "com.google.Chrome")

        // Ценное — не должно пострадать ни при каком сценарии.
        let precious = [
            L("Keychains/login.keychain-db"), L("Mobile Documents/com~apple~CloudDocs/Документ.txt"), H("Documents/important.txt"),
            L("Caches/com.apple.bird/session.db"), L("Caches/CloudKit/records.db"), L("Caches/com.apple.FontRegistry/fonts.db"),
            L("Application Support/1Password/vault.sqlite"), L("Preferences/com.apple.finder.plist"),
            L("Containers/com.apple.Notes/Data/notes.db"), L("Application Support/MobileSync/Backup/abc/Manifest.db"),
            L("Logs/Tool/today.log"), L("Caches/com.running.app/blob"), L("Application Support/FoozleBar/settings.json"),
            L("Preferences/com.foobar.app.plist"), L("Application Support/com.foo.application/blob"), L("Preferences/com.installed.other.plist"),
            L("Application Support/Google/Chrome/Default/History"), L("Application Support/Google/Chrome/Default/Bookmarks"),
            L("Application Support/com.gone.recent/data"), L("Mail/V10/inbox.emlx"), H(".ssh/id_ed25519"),
            L("Application Support/com.gone.app/user-data.json"),
        ]
        for p in precious { write(p) }
        age(L("Application Support/com.gone.app"), days: 200)
        // Ссылка из кэшей наружу «дома» — по ней ходить нельзя.
        let outside = root.appendingPathComponent("outside/secret.txt")
        write(outside)
        try? fm.createSymbolicLink(at: L("Caches/escape"), withDestinationURL: outside.deletingLastPathComponent())

        // Мусор.
        let junk = [
            L("Caches/com.vendor.tool/blob.bin"), L("Logs/Tool/old.log"), L("Logs/DiagnosticReports/Tool-2026-01-01.ips"),
            L("Developer/Xcode/DerivedData/Proj-abcdef/Build/x.o"), L("Application Support/Google/Chrome/Default/Code Cache/js/1"),
            L("Caches/com.gone.app/blob"),
        ]
        for p in junk { write(p, size: 100_000) }
        age(L("Logs/Tool/old.log"), days: 30)
        age(L("Caches/com.gone.app"), days: 200)

        // Следы программы Foozle и приманки, похожие на них.
        let fooLeftovers = [
            L("Application Support/Foozle/data.db"), L("Application Support/com.foo.app/state"), L("Caches/com.foo.app/c.bin"),
            L("Preferences/com.foo.app.plist"), L("Containers/com.foo.app.share/Data/x"),
            L("Saved Application State/com.foo.app.savedState/windows.plist"), L("Logs/DiagnosticReports/Foozle-2026-02-02.ips"),
        ]
        for p in fooLeftovers { write(p, size: 10_000) }
        (["Label": "com.foo.app.helper", "ProgramArguments": ["/usr/bin/true"]] as NSDictionary)
            .write(to: L("LaunchAgents/com.foo.app.helper.plist"), atomically: true)
        try? fm.createDirectory(at: L("LaunchAgents"), withIntermediateDirectories: true)
        (["Label": "com.foo.app.helper", "ProgramArguments": ["/usr/bin/true"]] as NSDictionary)
            .write(to: L("LaunchAgents/com.foo.app.helper.plist"), atomically: true)
        (["Label": "com.vendor.updater", "ProgramArguments": [apps.appendingPathComponent("Foozle.app/Contents/MacOS/Foozle").path]] as NSDictionary)
            .write(to: L("LaunchAgents/com.vendor.updater.plist"), atomically: true)

        // Файлы для дубликатов и крупных.
        write(H("Documents/a.bin"), size: 1_500_000, random: true)
        try? fm.copyItem(at: H("Documents/a.bin"), to: H("Downloads/a copy.bin"))
        write(H("Documents/c.bin"), size: 1_500_000, random: true)
        write(H("Movies/big.mov"), size: 60_000_000)

        // MARK: Политика безопасности
        let v = { (u: URL) in Safety.verdict(for: u, home: home) }
        ok = check(v(home).isForbidden && v(lib).isForbidden && v(L("Caches")).isForbidden && v(H("Documents")).isForbidden,
                   "корневые папки не удаляются") && ok
        ok = check(v(L("Keychains/login.keychain-db")).isForbidden && v(H(".ssh/id_ed25519")).isForbidden
                   && v(L("Mobile Documents/x")).isForbidden && v(L("Mail/V10/inbox.emlx")).isForbidden, "ключи, iCloud и почта запрещены") && ok
        ok = check(v(L("Preferences/com.apple.finder.plist")).isForbidden && v(L("Containers/com.apple.Notes/Data/notes.db")).isForbidden,
                   "данные macOS и программ Apple запрещены") && ok
        ok = check(!v(L("Containers/com.apple.Safari/Data/Library/Caches/x")).isForbidden, "кэш Safari в контейнере разрешён") && ok
        ok = check(v(L("Application Support/MobileSync/Backup/abc")).isForbidden
                   && !Safety.verdict(for: L("Application Support/MobileSync/Backup/abc"), home: home, explicitlyAllowed: true).isForbidden,
                   "резервные копии iPhone — только по явному выбору") && ok
        ok = check(v(L("Application Support/1Password/vault.sqlite")) == .sensitive("Похоже на хранилище паролей или криптокошелёк"),
                   "хранилища паролей — только вручную") && ok
        ok = check(v(H("Pictures/Photos Library.photoslibrary/database/x")).isForbidden, "медиатека Фото запрещена") && ok
        ok = check(v(L("Caches/escape/secret.txt")).isForbidden, "символическая ссылка наружу не пропускается") && ok
        let adminV = { (p: String) in Safety.verdict(for: URL(fileURLWithPath: p), home: home, allowAdmin: true) }
        ok = check(!adminV("/Library/LaunchDaemons/com.vendor.helper.plist").isForbidden && adminV("/Library/LaunchDaemons/com.apple.x.plist").isForbidden
                   && adminV("/System/Library/x").isForbidden && adminV("/usr/bin/true").isForbidden && adminV("/Library/LaunchDaemons").isForbidden,
                   "системные папки: только сторонние объекты и только с паролем") && ok

        // MARK: Сканирование
        let installed = InstalledIndex(apps: AppScanner.scan([apps], signing: false))
        let ctx = ScanContext(home: home, applicationDirs: [apps], hasFullDiskAccess: true, runningBundleIDs: ["com.running.app"],
                              installed: installed, useLaunchServices: false)
        let cats = JunkScanner.scan(ctx, categories: CategoryID.allCases)
        func cat(_ id: CategoryID) -> [JunkItem] { cats.first { $0.id == id }?.items ?? [] }
        func item(_ id: CategoryID, _ name: String) -> JunkItem? { cat(id).first { $0.url.lastPathComponent == name || $0.title == name } }

        ok = check(item(.appCaches, "com.vendor.tool")?.selected == true, "кэш программы найден и отмечен") && ok
        ok = check(item(.appCaches, "com.running.app")?.selected == false, "кэш запущенной программы не отмечен") && ok
        ok = check(cat(.appCaches).allSatisfy { !["com.apple.bird", "CloudKit", "com.apple.FontRegistry"].contains($0.url.lastPathComponent) },
                   "кэши iCloud и шрифтов не предлагаются") && ok
        let logItem = item(.logs, "Tool")
        ok = check(logItem?.targets.map(\.lastPathComponent) == ["old.log"], "из журналов — только старше недели") && ok
        let derived = cat(.developer).first { $0.title.contains("DerivedData") }
        ok = check(derived?.targets.map(\.lastPathComponent) == ["Proj-abcdef"], "DerivedData очищается изнутри") && ok
        let chrome = cat(.browserCaches).first { $0.title == "Google Chrome" }
        ok = check(chrome?.targets.contains { $0.lastPathComponent == "Code Cache" } == true
                   && chrome?.targets.contains { $0.lastPathComponent == "History" } == false, "кэш Chrome — без истории и закладок") && ok
        let orphanCache = cat(.leftovers).first { $0.title == "com.gone.app" && $0.detail == "Кэш" }
        let orphanData = cat(.leftovers).first { $0.title == "com.gone.app" && $0.detail == "Данные программы" }
        ok = check(orphanCache?.selected == true && orphanData?.selected == false, "остатки удалённой программы: кэш отмечен, данные — нет") && ok
        ok = check(!cat(.leftovers).contains { ["com.gone.recent", "com.installed.other", "com.foo.app"].contains($0.title) },
                   "свежие и чужие данные не считаются остатками") && ok
        ok = check(cats.allSatisfy { $0.items.allSatisfy { !($0.selected && $0.risk == .caution) } }, "ничего «осторожного» не отмечено заранее") && ok

        // MARK: Следы программы Foozle
        guard let foo = installed.apps.first(where: { $0.bundleID == "com.foo.app" }) else { return check(false, "Foozle.app не найдена") }
        let left = Leftovers.find(for: foo, ctx, includeSystem: false)
        let leftPaths = Set(left.flatMap { $0.targets.map(\.path) } + left.map(\.url.path))
        let expected = [L("Application Support/Foozle"), L("Application Support/com.foo.app"), L("Caches/com.foo.app"),
                        L("Preferences/com.foo.app.plist"), L("Containers/com.foo.app.share"), L("Saved Application State/com.foo.app.savedState"),
                        L("LaunchAgents/com.foo.app.helper.plist"), L("LaunchAgents/com.vendor.updater.plist")]
        let missing = expected.filter { !leftPaths.contains($0.path) }
        ok = check(missing.isEmpty, "все следы Foozle найдены\(missing.isEmpty ? "" : ": не хватает \(missing.map(\.lastPathComponent))")") && ok
        ok = check(left.contains { $0.title.contains("отчёт") }, "отчёты о сбоях Foozle найдены") && ok
        let decoys = ["FoozleBar", "com.foobar.app.plist", "com.foo.application", "Google", "com.installed.other.plist"]
        ok = check(!left.contains { decoys.contains($0.url.lastPathComponent) }, "похожие имена и чужие файлы не задеты") && ok
        ok = check(left.first { $0.url.lastPathComponent == "Foozle" }?.risk == .review, "совпадение только по имени помечено «проверьте»") && ok

        // MARK: Удаление (навсегда, внутри песочницы)
        let selected = cats.flatMap { $0.items.filter(\.selected) } + left.filter(\.selected)
            + [JunkItem(url: foo.url, targets: [foo.url], title: "Foozle", size: 1, selected: true, risk: .safe, method: .trash)]
        let report = Remover.remove(selected, category: "test", home: home, forceDelete: true)
        print("   удалено объектов: \(report.removed), ошибок: \(report.failures.count)")
        for f in report.failures { print("   ошибка: \(f.path) — \(f.reason)") }
        ok = check(report.failures.isEmpty, "удаление без ошибок") && ok
        let survivors = precious.filter { !fm.fileExists(atPath: $0.path) }
        ok = check(survivors.isEmpty, "всё ценное на месте\(survivors.isEmpty ? "" : ": пропало \(survivors.map(\.lastPathComponent))")") && ok
        ok = check(fm.fileExists(atPath: outside.path), "файл за символической ссылкой цел") && ok
        // Совпадение только по имени («Foozle» в Application Support) не отмечено заранее — оно должно остаться.
        let gone = junk + fooLeftovers.filter { !$0.path.contains("Application Support/Foozle/") } + [foo.url]
        let stillThere = gone.filter { fm.fileExists(atPath: $0.path) }
        ok = check(stillThere.isEmpty, "мусор и следы Foozle удалены\(stillThere.isEmpty ? "" : ": остались \(stillThere.map(\.lastPathComponent))")") && ok
        ok = check(fm.fileExists(atPath: L("Developer/Xcode/DerivedData").path) && fm.fileExists(atPath: L("Logs/Tool").path),
                   "сами папки DerivedData и журналов остались") && ok

        // MARK: Дубликаты и крупные файлы
        let clutter = ClutterScanner.scan(ctx)
        print("   групп дубликатов: \(clutter.duplicates.map { $0.files.map(\.url.lastPathComponent) })")
        ok = check(clutter.duplicates.count == 1 && clutter.duplicates[0].files.count == 2, "одна группа дубликатов из двух файлов") && ok
        if let group = clutter.duplicates.first {
            ok = check(ClutterScanner.keeper(in: group, home: home)?.url.lastPathComponent == "a.bin", "оставляем оригинал, а не копию из Загрузок") && ok
        }
        ok = check(clutter.large.contains { $0.url.lastPathComponent == "big.mov" }, "крупный файл найден") && ok
        ok = check(!clutter.large.contains { $0.url.path.contains("/Library/") }, "в ~/Library за крупными файлами не ходим") && ok

        // MARK: Корзина и возврат
        let doomed = H("Documents/to-trash.txt")
        write(doomed)
        let trashReport = Remover.remove([JunkItem(url: doomed, targets: [doomed], title: "x", size: 1, selected: true, risk: .review, method: .trash)],
                                         category: "test", home: home)
        if let entry = trashReport.journal.first, entry.trashed != nil {
            let restored = Remover.restore(entry)
            ok = check((try? restored.get()) != nil && fm.fileExists(atPath: doomed.path), "файл возвращён из Корзины") && ok
        } else {
            print("   (Корзина недоступна на этом томе — проверка возврата пропущена: \(trashReport.failures.first?.reason ?? ""))")
        }

        try? fm.removeItem(at: root)
        return ok
    }

    // MARK: - Настоящий дом (только чтение)

    static func real() -> Bool {
        let home = FileManager.default.homeDirectoryForCurrentUser
        let start = Date()
        let apps = AppScanner.scan(AppScanner.defaultDirectories(home: home))
        let installed = InstalledIndex(apps: apps)
        print("  программ: \(apps.count) (сторонних: \(apps.filter { !$0.isApple }.count)), за \(String(format: "%.1f", Date().timeIntervalSince(start))) с")
        let ctx = ScanContext(home: home, applicationDirs: AppScanner.defaultDirectories(home: home),
                              hasFullDiskAccess: Permissions.hasFullDiskAccess(), runningBundleIDs: [], installed: installed,
                              avoidPrivacyPrompts: true)
        let scanStart = Date()
        let cats = JunkScanner.scan(ctx, categories: CategoryID.allCases)
        print("  сканирование: \(String(format: "%.1f", Date().timeIntervalSince(scanStart))) с, полный доступ: \(ctx.hasFullDiskAccess)")
        var ok = true
        for c in cats {
            print("  \(c.id.title): \(c.items.count) шт., \(Format.bytes(c.totalSize)), отмечено \(Format.bytes(c.selectedSize))\(c.notice.map { " — \($0)" } ?? "")")
            for i in c.items.prefix(3) { print("      \(i.selected ? "■" : "□") \(i.title) — \(Format.bytes(i.size))\(i.note.map { " (\($0))" } ?? "")") }
        }
        // Инварианты на живых данных.
        let all = cats.flatMap(\.items)
        let forbidden = all.flatMap(\.targets).filter { Safety.verdict(for: $0, home: home, explicitlyAllowed: true, allowAdmin: true).isForbidden }
        ok = check(forbidden.isEmpty, "ни одна находка не нарушает политику безопасности") && ok
        ok = check(!all.contains { $0.selected && $0.risk == .caution }, "ничего «осторожного» не отмечено") && ok
        ok = check(!all.flatMap(\.targets).contains { $0.path.contains("/Library/Mobile Documents") || $0.path.contains("/Library/Keychains") },
                   "iCloud Drive и связка ключей не затронуты") && ok
        // Следы нескольких программ: найденное не должно совпадать с идентификаторами других программ.
        for app in apps.filter({ !$0.isApple && !$0.isSystem }).prefix(8) {
            let left = Leftovers.find(for: app, ctx)
            let foreign = left.filter { item in
                apps.contains { other in other.bundleID != app.bundleID && Leftovers.stem(item.url).lowercased() == other.bundleID.lowercased() }
            }
            print("  \(app.name): следов \(left.count), \(Format.bytes(left.reduce(0) { $0 + $1.size }))")
            ok = check(foreign.isEmpty, "у «\(app.name)» нет чужих файлов") && ok
        }
        let clutterStart = Date()
        let clutter = ClutterScanner.scan(ctx)
        print("  крупные: \(clutter.large.count), старые: \(clutter.old.count), групп дубликатов: \(clutter.duplicates.count), файлов: \(clutter.scanned), за \(String(format: "%.1f", Date().timeIntervalSince(clutterStart))) с")
        let launch = LaunchItems.scan(home: home, installed: installed)
        print("  заданий автозапуска: \(launch.count)")
        let mem = SystemStats.memory(), disk = SystemStats.disk()
        print("  память: \(Format.bytes(Int64(mem.used))) из \(Format.bytes(Int64(mem.total))), давление \(mem.pressure); диск: свободно \(Format.bytes(disk.availableImportant)) из \(Format.bytes(disk.total))")
        ok = check(mem.total > 0 && mem.used > 0 && disk.total > 0, "статистика памяти и диска читается") && ok
        let sampler = SystemStats.ProcessSampler()
        _ = sampler.sample()
        Thread.sleep(forTimeInterval: 0.5)
        let procs = sampler.sample()
        ok = check(!procs.isEmpty, "список процессов: \(procs.prefix(3).map { "\($0.name) \(Format.bytes(Int64($0.memory)))" }.joined(separator: ", "))") && ok
        return ok
    }
}
