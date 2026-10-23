import AppKit
import Foundation
import Network

/// Подставное приложение для самопроверки: копия Harbor с идентификатором app.harbor.selftest.dummy.
/// Записывает каждый свой запуск и создаёт «пользовательские данные», которые обновление не должно тронуть.
enum Dummy {
    static let bundleID = "app.harbor.selftest.dummy"
    static var isDummy: Bool { Bundle.main.bundleIdentifier == bundleID }

    static var home: URL {
        FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/HarborDummy", isDirectory: true)
    }

    static func run() -> Never {
        let fm = FileManager.default
        try? fm.createDirectory(at: home, withIntermediateDirectories: true)
        let version = AppVersion(bundle: Bundle.main.bundleURL)?.description ?? "?"
        let data = home.appendingPathComponent("data.txt")
        if !fm.fileExists(atPath: data.path) { try? Data("создано версией \(version)".utf8).write(to: data) }
        record("\(version) \(ProcessInfo.processInfo.processIdentifier)")
        let app = NSApplication.shared
        let delegate = DummyDelegate(version: version)
        app.delegate = delegate
        app.setActivationPolicy(.accessory)
        app.run()
        exit(0)
    }

    static func record(_ text: String) {
        let log = home.appendingPathComponent("launches.log")
        let line = Data((text + "\n").utf8)
        if let h = try? FileHandle(forWritingTo: log) { h.seekToEndOfFile(); h.write(line); try? h.close() } else { try? line.write(to: log) }
    }
}

/// Отмечает штатное завершение: так тест отличает «закрыли вежливо» от «убили принудительно».
final class DummyDelegate: NSObject, NSApplicationDelegate {
    let version: String
    init(version: String) { self.version = version }
    func applicationWillTerminate(_ notification: Notification) { Dummy.record("quit \(version)") }
}

/// Самопроверки для CI:
///   --selftest-core              — версии, каталог, контрольные суммы, карантин, подпись самого Harbor
///   --selftest-install <папка>   — полный цикл на подставном приложении через локальный HTTP-сервер
enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        let rest = Array(args.drop { $0 != mode }.dropFirst())
        setvbuf(stdout, nil, _IOLBF, 0)
        Task {
            var ok = false
            switch mode {
            case "--selftest-core": ok = core()
            case "--selftest-install": ok = await install(URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory()))
            default: print("Неизвестный режим \(mode)")
            }
            print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
            exit(ok ? 0 : 1)
        }
        RunLoop.main.run()
    }

    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    // MARK: - Ядро

    static func core() -> Bool {
        var ok = check(AppVersion("1.2.0", build: 5) < AppVersion("1.10.0", build: 1), "версии: 1.2.0 < 1.10.0")
        ok = check(AppVersion("1.0.0", build: 7) < AppVersion("1.0.0", build: 12), "версии: одинаковая версия, сборка 7 < 12") && ok
        ok = check(AppVersion("1.0", build: 3) == AppVersion("1.0.0", build: 3), "версии: 1.0 = 1.0.0") && ok

        let json = """
        {"schema":1,"generated":"2026-10-23T12:00:00Z","signer":null,"apps":[
         {"id":"b","name":"B","bundleID":"app.b","tagline":"t","summary":"s","description":"d","features":["f"],"category":"c",
          "accent":[0.1,0.2,0.3],"order":2,"available":false},
         {"id":"a","name":"A","bundleID":"app.a","tagline":"t","summary":"s","description":"d","features":[],"category":"c",
          "accent":[1,0,0],"order":1,"available":true,"version":"2.0.0","build":9,"url":"https://example.org/A.zip",
          "sha256":"00","size":10,"changes":["x"],"date":"2026-10-23T12:00:00Z"}]}
        """
        if let catalog = try? Catalog.decode(Data(json.utf8)) {
            ok = check(catalog.apps.map(\.id) == ["a", "b"] && catalog.apps[0].isInstallable && !catalog.apps[1].isInstallable,
                       "каталог: разбор, порядок, доступность") && ok
            ok = check(catalog.apps[0].latest == AppVersion("2.0.0", build: 9), "каталог: версия сборки") && ok
        } else {
            ok = check(false, "каталог не разобран") && ok
        }

        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("harbor-core-\(UUID().uuidString)")
        try? Data("abc".utf8).write(to: tmp)
        ok = check((try? Checksum.sha256(of: tmp)) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "SHA-256 файла") && ok
        Quarantine.mark(tmp)
        let marked = Quarantine.has(tmp)
        Quarantine.remove(from: tmp)
        ok = check(marked && !Quarantine.has(tmp), "метка карантина ставится и снимается") && ok
        try? FileManager.default.removeItem(at: tmp)

        let me = Bundle.main.bundleURL
        if me.pathExtension == "app", let id = Bundle.main.bundleIdentifier {
            let signer = CodeCheck.signer(of: me)
            ok = check((try? CodeCheck.validate(me, bundleID: id, signer: signer)) != nil,
                       "подпись Harbor проверяется (\(signer.map { "сертификат \($0.prefix(8))…" } ?? "ad-hoc"))") && ok
            ok = check((try? CodeCheck.validate(me, bundleID: "app.someone.else", signer: nil)) == nil, "чужой идентификатор отвергается") && ok
        }
        return ok
    }

    // MARK: - Полный цикл установки

    static func install(_ base: URL) async -> Bool {
        let fm = FileManager.default
        let root = base.appendingPathComponent("harbor-install")
        try? fm.removeItem(at: root)
        try? fm.removeItem(at: Dummy.home)
        let apps = root.appendingPathComponent("Applications")
        let www = root.appendingPathComponent("www")
        for dir in [apps, www, root.appendingPathComponent("support")] { try? fm.createDirectory(at: dir, withIntermediateDirectories: true) }
        // Процесс самопроверки — полноценное приложение для Launch Services (нужно, чтобы просить другие закрыться).
        NSApplication.shared.setActivationPolicy(.prohibited)
        NSApplication.shared.finishLaunching()
        setenv("HARBOR_INSTALL_DIR", apps.path, 1)
        setenv("HARBOR_SUPPORT_DIR", root.appendingPathComponent("support").path, 1)
        defer { for app in AppControl.running(bundleID: Dummy.bundleID) { app.forceTerminate() } }

        // Сборки подставного приложения: 1.0.0 (1) и (2), плюс испорченные.
        guard let v1 = await makeDummy(root, build: 1), let v2 = await makeDummy(root, build: 2),
              let wrongID = await makeDummy(root, build: 3, bundleID: "app.harbor.selftest.other"),
              let broken = await makeDummy(root, build: 4, breakSignature: true) else {
            return check(false, "не удалось собрать подставное приложение")
        }
        var zips: [Int: (String, String, Int64)] = [:]
        for (build, bundle) in [(1, v1), (2, v2), (3, wrongID), (4, broken)] {
            guard let z = await zip(bundle, build: build, into: www) else { return check(false, "не удалось упаковать сборку \(build)") }
            zips[build] = z
        }
        guard let server = try? await FileServer.start(root: www) else { return check(false, "HTTP-сервер не запустился") }
        defer { server.stop() }
        let base = "http://127.0.0.1:\(server.port)/"

        func entry(_ build: Int, sha: String? = nil, signer: String? = nil) -> CatalogApp {
            let z = zips[build]!
            return CatalogApp(id: "harbordummy", name: "HarborDummy", bundleID: Dummy.bundleID, tagline: "", summary: "", description: "",
                              features: [], replaces: nil, category: "", accent: [0, 0, 0], order: 0, available: true,
                              version: "1.0.0", build: build, url: URL(string: base + z.0), sha256: sha ?? z.1, size: z.2,
                              minimumSystem: nil, icon: nil, screenshots: nil, changes: nil, commit: nil, date: nil, signer: signer)
        }
        // Каталоги для проверки install.sh (file://).
        writeCatalog(root.appendingPathComponent("catalog-v1.json"), [entry(1)], fileBase: www)
        writeCatalog(root.appendingPathComponent("catalog-v2.json"), [entry(2)], fileBase: www)

        let installer = Installer(signer: nil)
        let target = apps.appendingPathComponent("HarborDummy.app")
        var ok = true

        // 1. Установка: метка карантина снимается.
        do {
            let staged = try await installer.stage(entry(1)) { _ in }
            Quarantine.mark(staged.bundle)
            Quarantine.mark(staged.bundle.appendingPathComponent("Contents/Info.plist"))
            let result = try await installer.install(staged)
            ok = check(result.location.standardizedFileURL == target.standardizedFileURL && AppVersion(bundle: target) == AppVersion("1.0.0", build: 1),
                       "установка: скачано по HTTP, SHA-256 и подпись проверены, приложение в папке") && ok
            ok = check(result.quarantineRemoved >= 2 && !Quarantine.has(target) && !Quarantine.has(target.appendingPathComponent("Contents/Info.plist")),
                       "установка: метка карантина снята (\(result.quarantineRemoved) файла) — Gatekeeper не спросит") && ok
        } catch {
            return check(false, "установка: \(error.localizedDescription)")
        }

        // 2. Обновление запущенного приложения: штатное закрытие, замена, перезапуск, данные на месте.
        let launched = await AppControl.launch(target)
        let firstRun = await waitForLaunches(1)
        ok = check(launched && firstRun.last?.hasPrefix("1.0.0 (1)") == true, "версия 1 запущена и создала свои данные") && ok
        let data = Dummy.home.appendingPathComponent("data.txt")
        let before = try? String(contentsOf: data, encoding: .utf8)
        do {
            let staged = try await installer.stage(entry(2)) { _ in }
            let result = try await installer.install(staged, quitTimeout: 10)
            let launches = await waitForLaunches(3)
            ok = check(result.previous == AppVersion("1.0.0", build: 1) && AppVersion(bundle: target) == AppVersion("1.0.0", build: 2),
                       "обновление 1 → 2 поверх установленной версии") && ok
            ok = check(launches.contains("quit 1.0.0 (1)"), "запущенная версия закрыта штатно (успела сохраниться), а не принудительно") && ok
            ok = check(result.relaunched && launches.last?.hasPrefix("1.0.0 (2)") == true,
                       "после замены приложение открыто снова — уже новой версией") && ok
            ok = check(before != nil && (try? String(contentsOf: data, encoding: .utf8)) == before,
                       "данные приложения не тронуты: «\(before ?? "?")»") && ok
            let backup = await installer.backup(for: Dummy.bundleID)
            ok = check(backup.flatMap { AppVersion(bundle: $0) } == AppVersion("1.0.0", build: 1), "предыдущая версия сохранена для отката") && ok
        } catch {
            ok = check(false, "обновление: \(error.localizedDescription)") && ok
        }

        // 3. Защита: испорченные сборки не ставятся, установленная версия остаётся.
        let bad: [(CatalogApp, String)] = [
            (entry(2, sha: String(repeating: "0", count: 64)), "неверная контрольная сумма"),
            (entry(3), "другое приложение в архиве"),
            (entry(4), "нарушенная подпись"),
            (entry(2, signer: String(repeating: "AB", count: 20)), "подпись не тем сертификатом"),
        ]
        for (app, label) in bad {
            var rejected = false
            do { _ = try await installer.stage(app) { _ in } } catch {
                rejected = true
                print("   отказ: \(error.localizedDescription)")
            }
            ok = check(rejected && AppVersion(bundle: target) == AppVersion("1.0.0", build: 2), "отвергнуто: \(label)") && ok
        }

        // 4. Откат и удаление (данные по умолчанию остаются).
        do {
            let back = try await installer.rollback(entry(2))
            ok = check(back == AppVersion("1.0.0", build: 1), "откат к предыдущей версии") && ok
            try await installer.uninstall(entry(2), removeData: false)
            ok = check(!fm.fileExists(atPath: target.path) && fm.fileExists(atPath: data.path), "удаление: программа в Корзине, данные остались") && ok
        } catch {
            ok = check(false, "откат/удаление: \(error.localizedDescription)") && ok
        }
        return ok
    }

    private static func waitForLaunches(_ count: Int) async -> [String] {
        let log = Dummy.home.appendingPathComponent("launches.log")
        var lines: [String] = []
        for _ in 0..<100 {
            lines = ((try? String(contentsOf: log, encoding: .utf8)) ?? "").split(separator: "\n").map(String.init)
            if lines.count >= count { break }
            try? await Task.sleep(for: .milliseconds(200))
        }
        return lines
    }

    /// Копия Harbor.app с идентификатором подставного приложения и нужным номером сборки, подписанная ad-hoc.
    private static func makeDummy(_ root: URL, build: Int, bundleID: String = Dummy.bundleID, breakSignature: Bool = false) async -> URL? {
        let fm = FileManager.default
        let dir = root.appendingPathComponent("build/\(build)")
        let app = dir.appendingPathComponent("HarborDummy.app")
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        guard (try? fm.copyItem(at: Bundle.main.bundleURL, to: app)) != nil else { return nil }
        let plist = app.appendingPathComponent("Contents/Info.plist")
        guard let info = NSMutableDictionary(contentsOf: plist) else { return nil }
        info["CFBundleIdentifier"] = bundleID
        info["CFBundleName"] = "HarborDummy"
        info["CFBundleShortVersionString"] = "1.0.0"
        info["CFBundleVersion"] = "\(build)"
        info["LSUIElement"] = true
        guard info.write(to: plist, atomically: true) else { return nil }
        guard (try? await Tool.run("/usr/bin/codesign", ["--force", "--sign", "-", "--timestamp=none", app.path])) != nil else { return nil }
        if breakSignature {
            // Подпись запечатывает ресурсы: изменение любого из них должно её сломать.
            try? Data("подмена".utf8).write(to: app.appendingPathComponent("Contents/Resources/tampered.txt"))
        }
        return app
    }

    private static func zip(_ app: URL, build: Int, into dir: URL) async -> (String, String, Int64)? {
        let name = "HarborDummy-1.0.0-\(build).zip"
        let out = dir.appendingPathComponent(name)
        guard (try? await Tool.run("/usr/bin/ditto", ["-c", "-k", "--sequesterRsrc", "--keepParent", app.path, out.path])) != nil,
              let sum = try? Checksum.sha256(of: out),
              let size = (try? FileManager.default.attributesOfItem(atPath: out.path))?[.size] as? NSNumber else { return nil }
        return (name, sum, size.int64Value)
    }

    private static func writeCatalog(_ url: URL, _ apps: [CatalogApp], fileBase: URL) {
        var list = apps
        for i in list.indices { list[i].url = fileBase.appendingPathComponent(list[i].url!.lastPathComponent) }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let catalog = Catalog(schema: 1, generated: Date(), signer: nil, apps: list)
        try? encoder.encode(catalog).write(to: url)
    }
}

nonisolated final class OnceFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func first() -> Bool {
        lock.withLock {
            defer { done = true }
            return !done
        }
    }
}

/// Простой HTTP-сервер для самопроверки: отдаёт файлы папки целиком.
nonisolated final class FileServer: @unchecked Sendable {
    private let listener: NWListener
    private let root: URL
    private let queue = DispatchQueue(label: "harbor.fileserver")
    private(set) var port: UInt16 = 0

    private init(root: URL) throws {
        self.root = root
        let params = NWParameters.tcp
        params.requiredInterfaceType = .loopback
        listener = try NWListener(using: params, on: .any)
    }

    static func start(root: URL) async throws -> FileServer {
        let server = try FileServer(root: root)
        try await server.run()
        return server
    }

    private func run() async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            let once = OnceFlag()
            @Sendable func finish(_ error: (any Error)?) {
                guard once.first() else { return }
                if let error { cont.resume(throwing: error) } else { cont.resume() }
            }
            listener.newConnectionHandler = { [weak self] c in self?.handle(c) }
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.port = self?.listener.port?.rawValue ?? 0
                    finish(nil)
                case .failed(let e): finish(e)
                default: break
                }
            }
            listener.start(queue: queue)
        }
    }

    func stop() { listener.cancel() }

    private func handle(_ c: NWConnection) {
        c.start(queue: queue)
        c.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, _, _ in
            guard let self, let data, let head = String(data: data, encoding: .utf8) else { c.cancel(); return }
            let path = head.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
            let name = (path.removingPercentEncoding ?? path).split(separator: "/").last.map(String.init) ?? ""
            let file = self.root.appendingPathComponent(name)
            if !name.isEmpty, !name.contains(".."), let body = try? Data(contentsOf: file) {
                let header = "HTTP/1.1 200 OK\r\nContent-Length: \(body.count)\r\nContent-Type: application/zip\r\nConnection: close\r\n\r\n"
                c.send(content: Data(header.utf8) + body, completion: .contentProcessed { _ in c.cancel() })
            } else {
                c.send(content: Data("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".utf8),
                       completion: .contentProcessed { _ in c.cancel() })
            }
        }
    }
}
