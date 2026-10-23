import AppKit
import CoreServices
import CryptoKit
import Foundation
import Security

/// Где лежат приложения и резервные копии.
nonisolated enum Locations {
    /// Папка для новых установок: /Applications, если в неё можно писать, иначе ~/Applications.
    /// Для самопроверок — HARBOR_INSTALL_DIR.
    static var installDirectory: URL {
        if let custom = ProcessInfo.processInfo.environment["HARBOR_INSTALL_DIR"] { return URL(fileURLWithPath: custom, isDirectory: true) }
        let system = URL(fileURLWithPath: "/Applications", isDirectory: true)
        if FileManager.default.isWritableFile(atPath: system.path) { return system }
        let user = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Applications", isDirectory: true)
        try? FileManager.default.createDirectory(at: user, withIntermediateDirectories: true)
        return user
    }

    static var support: URL {
        let base = ProcessInfo.processInfo.environment["HARBOR_SUPPORT_DIR"].map { URL(fileURLWithPath: $0) }
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Harbor")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    static var backups: URL { support.appendingPathComponent("Backups", isDirectory: true) }

    /// Установленная копия приложения: сначала там, где её знает система, затем в папке установки.
    static func installed(bundleID: String, name: String) -> URL? {
        let fm = FileManager.default
        var candidates: [URL] = [installDirectory.appendingPathComponent("\(name).app")]
        if ProcessInfo.processInfo.environment["HARBOR_INSTALL_DIR"] == nil {
            if let known = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID) { candidates.insert(known, at: 0) }
            candidates.append(URL(fileURLWithPath: "/Applications/\(name).app"))
            candidates.append(fm.homeDirectoryForCurrentUser.appendingPathComponent("Applications/\(name).app"))
        }
        for url in candidates where fm.fileExists(atPath: url.path) {
            // Копии внутри образов дисков и корзины не считаем установкой.
            if url.path.hasPrefix("/Volumes/") || url.path.contains("/.Trash/") { continue }
            if Bundle(url: url)?.bundleIdentifier == bundleID { return url }
        }
        return nil
    }
}

/// Метка «скачано из интернета», из-за которой macOS спрашивает про неизвестного разработчика.
nonisolated enum Quarantine {
    static let attribute = "com.apple.quarantine"

    static func has(_ url: URL) -> Bool {
        getxattr(url.path, attribute, nil, 0, 0, XATTR_NOFOLLOW) >= 0
    }

    /// Снимает метку с бандла и всего, что внутри. Возвращает, сколько файлов было помечено.
    @discardableResult
    static func remove(from url: URL) -> Int {
        var count = 0
        func strip(_ path: String) {
            if removexattr(path, attribute, XATTR_NOFOLLOW) == 0 { count += 1 }
        }
        strip(url.path)
        if let walker = FileManager.default.enumerator(atPath: url.path) {
            while let rel = walker.nextObject() as? String { strip(url.appendingPathComponent(rel).path) }
        }
        return count
    }

    /// Только для самопроверки: пометить файл, как это делает браузер.
    static func mark(_ url: URL) {
        let value = "0081;\(String(Int(Date().timeIntervalSince1970), radix: 16));Safari;"
        _ = value.withCString { setxattr(url.path, attribute, $0, strlen($0), 0, XATTR_NOFOLLOW) }
    }
}

/// Проверка подписи кода через Security.framework.
nonisolated enum CodeCheck {
    /// SHA-1 корневого сертификата подписи (как в требованиях `certificate root = H"…"`); nil — подпись ad-hoc.
    static func signer(of url: URL) -> String? {
        var code: SecStaticCode?
        guard SecStaticCodeCreateWithPath(url as CFURL, [], &code) == errSecSuccess, let code else { return nil }
        var info: CFDictionary?
        guard SecCodeCopySigningInformation(code, SecCSFlags(rawValue: kSecCSSigningInformation), &info) == errSecSuccess,
              let dict = info as? [String: Any],
              let certificates = dict[kSecCodeInfoCertificates as String] as? [SecCertificate],
              let root = certificates.last else { return nil }
        let der = SecCertificateCopyData(root) as Data
        return Insecure.SHA1.hash(data: der).map { String(format: "%02X", $0) }.joined()
    }

    /// Подпись цела, идентификатор совпадает и (если известен) подписано нашим сертификатом.
    static func validate(_ url: URL, bundleID: String, signer: String?) throws {
        var code: SecStaticCode?
        guard SecStaticCodeCreateWithPath(url as CFURL, [], &code) == errSecSuccess, let code else {
            throw HarborError("Не удалось прочитать подпись приложения")
        }
        var text = "identifier \"\(bundleID)\""
        if let signer, !signer.isEmpty { text += " and certificate root = H\"\(signer)\"" }
        var requirement: SecRequirement?
        guard SecRequirementCreateWithString(text as CFString, [], &requirement) == errSecSuccess else {
            throw HarborError("Неверное требование к подписи")
        }
        var error: Unmanaged<CFError>?
        let flags = SecCSFlags(rawValue: kSecCSCheckAllArchitectures | kSecCSStrictValidate | kSecCSCheckNestedCode)
        let status = SecStaticCodeCheckValidityWithErrors(code, flags, requirement, &error)
        guard status == errSecSuccess else {
            let reason = (error?.takeRetainedValue()).map { CFErrorCopyDescription($0) as String } ?? "код \(status)"
            throw HarborError("Подпись приложения не прошла проверку: \(reason)")
        }
    }
}

/// Скачивание с прогрессом.
nonisolated final class Download: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let progress: @Sendable (Double) -> Void
    private var continuation: CheckedContinuation<URL, any Error>?
    private let destination: URL
    private let lock = NSLock()

    private init(destination: URL, progress: @escaping @Sendable (Double) -> Void) {
        self.destination = destination
        self.progress = progress
    }

    static func fetch(_ url: URL, to destination: URL, progress: @escaping @Sendable (Double) -> Void) async throws -> URL {
        let delegate = Download(destination: destination, progress: progress)
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 60
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<URL, any Error>) in
                delegate.lock.withLock { delegate.continuation = cont }
                session.downloadTask(with: url).resume()
            }
        } onCancel: {
            session.invalidateAndCancel()
        }
    }

    private func finish(_ result: Result<URL, any Error>) {
        let cont = lock.withLock { () -> CheckedContinuation<URL, any Error>? in
            defer { continuation = nil }
            return continuation
        }
        cont?.resume(with: result)
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesExpectedToWrite > 0 { progress(Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)) }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        if let http = downloadTask.response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            finish(.failure(HarborError("Сервер ответил \(http.statusCode)", recovery: .retry)))
            return
        }
        do {
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: location, to: destination)
            finish(.success(destination))
        } catch {
            finish(.failure(error))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        if let error { finish(.failure(HarborError("Не удалось скачать: \(error.localizedDescription)", recovery: .retry))) }
    }
}

nonisolated enum Checksum {
    static func sha256(of url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

/// Запуск системной утилиты и ожидание её завершения.
nonisolated enum Tool {
    @discardableResult
    static func run(_ path: String, _ args: [String]) async throws -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: path)
        process.arguments = args
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<String, any Error>) in
            process.terminationHandler = { p in
                let out = String(decoding: pipe.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
                if p.terminationStatus == 0 { cont.resume(returning: out) }
                else { cont.resume(throwing: HarborError("\((path as NSString).lastPathComponent): \(out.trimmingCharacters(in: .whitespacesAndNewlines))")) }
            }
            do { try process.run() } catch { cont.resume(throwing: error) }
        }
    }
}

/// Скачанная и проверенная сборка, готовая встать на место старой.
nonisolated struct StagedUpdate: Sendable {
    let app: CatalogApp
    let bundle: URL
    let version: AppVersion
}

nonisolated struct InstallResult: Sendable {
    let location: URL
    let previous: AppVersion?
    let relaunched: Bool
    let quarantineRemoved: Int
}

/// Установка, обновление, откат и удаление. Данные приложений лежат вне бандла
/// (Application Support, контейнеры, настройки, связка ключей) и при замене бандла не трогаются.
actor Installer {
    /// Сертификат, которым подписан сам Harbor: сборки, подписанные им же, обязаны ему соответствовать.
    nonisolated let signer: String?

    init(signer: String?) {
        self.signer = signer
    }

    /// Требование к подписи сборки: сертификат, указанный для неё в каталоге (ad-hoc — только идентификатор).
    nonisolated func requiredSigner(for app: CatalogApp) -> String? {
        guard let expected = app.signer, !expected.isEmpty else { return nil }
        return expected
    }

    // MARK: - Подготовка

    /// Скачивает, проверяет SHA-256, распаковывает рядом с местом установки и проверяет подпись.
    func stage(_ app: CatalogApp, progress: @escaping @Sendable (Double) -> Void) async throws -> StagedUpdate {
        guard let url = app.url, let expected = app.sha256?.lowercased(), let version = app.latest else {
            throw HarborError("\(app.name) ещё не опубликован")
        }
        let target = Locations.installed(bundleID: app.bundleID, name: app.name)
            ?? Locations.installDirectory.appendingPathComponent("\(app.name).app")
        let fm = FileManager.default
        // Каталог для подмены на том же томе, что и цель: тогда замена — одно атомарное переименование.
        let work = try fm.url(for: .itemReplacementDirectory, in: .userDomainMask,
                              appropriateFor: target.deletingLastPathComponent(), create: true)
        let archive = work.appendingPathComponent("\(app.name).zip")
        _ = try await Download.fetch(url, to: archive, progress: { progress($0 * 0.9) })
        let actual = try Checksum.sha256(of: archive)
        guard actual == expected else {
            try? fm.removeItem(at: work)
            throw HarborError("Контрольная сумма \(app.name) не совпала — файл повреждён или подменён")
        }
        try await Tool.run("/usr/bin/ditto", ["-x", "-k", archive.path, work.path])
        try? fm.removeItem(at: archive)
        let bundle = work.appendingPathComponent("\(app.name).app")
        guard fm.fileExists(atPath: bundle.path), let info = Bundle(url: bundle) else {
            try? fm.removeItem(at: work)
            throw HarborError("В архиве нет \(app.name).app")
        }
        guard info.bundleIdentifier == app.bundleID else {
            try? fm.removeItem(at: work)
            throw HarborError("В архиве другое приложение (\(info.bundleIdentifier ?? "?"))")
        }
        do {
            try CodeCheck.validate(bundle, bundleID: app.bundleID, signer: requiredSigner(for: app))
        } catch {
            try? fm.removeItem(at: work)
            throw error
        }
        progress(1)
        return StagedUpdate(app: app, bundle: bundle, version: AppVersion(bundle: bundle) ?? version)
    }

    // MARK: - Установка

    /// Ставит подготовленную сборку на место старой. Запущенное приложение закрывается штатно
    /// (оно успевает сохранить состояние) и после замены открывается снова.
    func install(_ staged: StagedUpdate, quitTimeout: TimeInterval = 20) async throws -> InstallResult {
        let fm = FileManager.default
        let app = staged.app
        let target = Locations.installed(bundleID: app.bundleID, name: app.name)
            ?? Locations.installDirectory.appendingPathComponent("\(app.name).app")
        let previous = fm.fileExists(atPath: target.path) ? AppVersion(bundle: target) : nil
        let isSelf = app.bundleID == Bundle.main.bundleIdentifier

        var wasRunning = false
        if !isSelf {
            wasRunning = await AppControl.quit(bundleID: app.bundleID, at: target, timeout: quitTimeout)
        }

        var backup: URL?
        if fm.fileExists(atPath: target.path) {
            let dir = Locations.backups.appendingPathComponent(app.bundleID, isDirectory: true)
            try? fm.removeItem(at: dir)
            try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            let dest = dir.appendingPathComponent("\(app.name) \(previous?.description ?? "старая").app")
            backup = try moveAside(target, to: dest)
        }
        do {
            try fm.moveItem(at: staged.bundle, to: target)
        } catch {
            // Вернуть старую версию на место.
            if let backup { try? restore(backup, to: target) }
            throw Self.permissionAware(error, app: app.name, target: target)
        }
        try? fm.removeItem(at: staged.bundle.deletingLastPathComponent())
        let removed = Quarantine.remove(from: target)
        _ = LSRegisterURL(target as CFURL, true)
        try? fm.setAttributes([.modificationDate: Date()], ofItemAtPath: target.path)

        var relaunched = false
        if wasRunning { relaunched = await AppControl.launch(target) }
        return InstallResult(location: target, previous: previous, relaunched: relaunched, quarantineRemoved: removed)
    }

    /// Резервная копия — переносом на том же томе, иначе копированием.
    private func moveAside(_ target: URL, to dest: URL) throws -> URL {
        let fm = FileManager.default
        do {
            try fm.moveItem(at: target, to: dest)
            return dest
        } catch {
            let local = target.deletingLastPathComponent().appendingPathComponent(".\(target.lastPathComponent).harbor-old")
            try? fm.removeItem(at: local)
            do {
                try fm.moveItem(at: target, to: local)
            } catch {
                throw Self.permissionAware(error, app: target.deletingPathExtension().lastPathComponent, target: target)
            }
            try? fm.copyItem(at: local, to: dest)
            try? fm.removeItem(at: local)
            return fm.fileExists(atPath: dest.path) ? dest : local
        }
    }

    private func restore(_ backup: URL, to target: URL) throws {
        let fm = FileManager.default
        if (try? fm.moveItem(at: backup, to: target)) == nil { try fm.copyItem(at: backup, to: target) }
    }

    static func permissionAware(_ error: any Error, app: String, target: URL) -> any Error {
        let ns = error as NSError
        let posix = (ns.userInfo[NSUnderlyingErrorKey] as? NSError)?.code ?? ns.code
        if posix == Int(EPERM) || posix == Int(EACCES) || ns.code == NSFileWriteNoPermissionError {
            return HarborError("macOS не дала заменить \(app). Разрешите Harbor «Управление приложениями» в настройках конфиденциальности.",
                               recovery: .appManagement)
        }
        return HarborError("Не удалось установить \(app): \(ns.localizedDescription)")
    }

    // MARK: - Откат и удаление

    func backup(for bundleID: String) -> URL? {
        let dir = Locations.backups.appendingPathComponent(bundleID, isDirectory: true)
        return (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil))?.first { $0.pathExtension == "app" }
    }

    /// Возвращает предыдущую версию; текущая становится резервной.
    func rollback(_ app: CatalogApp) async throws -> AppVersion? {
        guard let backup = backup(for: app.bundleID), let target = Locations.installed(bundleID: app.bundleID, name: app.name) else {
            throw HarborError("Предыдущей версии \(app.name) нет")
        }
        let fm = FileManager.default
        let wasRunning = await AppControl.quit(bundleID: app.bundleID, at: target, timeout: 20)
        let current = AppVersion(bundle: target)
        let swap = backup.deletingLastPathComponent().appendingPathComponent("\(app.name) \(current?.description ?? "новая").app")
        _ = try moveAside(target, to: swap)
        do {
            try fm.moveItem(at: backup, to: target)
        } catch {
            try? restore(swap, to: target)
            throw Self.permissionAware(error, app: app.name, target: target)
        }
        Quarantine.remove(from: target)
        _ = LSRegisterURL(target as CFURL, true)
        if wasRunning { _ = await AppControl.launch(target) }
        return AppVersion(bundle: target)
    }

    /// Места, где приложение держит свои данные (по идентификатору и имени).
    nonisolated static func dataLocations(_ app: CatalogApp) -> [URL] {
        let lib = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library")
        let paths = [
            "Application Support/\(app.name)", "Application Support/\(app.bundleID)",
            "Containers/\(app.bundleID)", "Caches/\(app.bundleID)", "Caches/\(app.name)",
            "Preferences/\(app.bundleID).plist", "Saved Application State/\(app.bundleID).savedState",
            "HTTPStorages/\(app.bundleID)", "WebKit/\(app.bundleID)", "Logs/\(app.name)",
        ]
        return paths.map { lib.appendingPathComponent($0) }.filter { FileManager.default.fileExists(atPath: $0.path) }
    }

    /// Удаление в Корзину (можно вернуть). Данные — только по явной просьбе.
    func uninstall(_ app: CatalogApp, removeData: Bool) async throws {
        guard let target = Locations.installed(bundleID: app.bundleID, name: app.name) else { return }
        _ = await AppControl.quit(bundleID: app.bundleID, at: target, timeout: 20)
        let fm = FileManager.default
        do {
            try fm.trashItem(at: target, resultingItemURL: nil)
        } catch {
            throw Self.permissionAware(error, app: app.name, target: target)
        }
        if removeData {
            for url in Self.dataLocations(app) { try? fm.trashItem(at: url, resultingItemURL: nil) }
        }
        try? fm.removeItem(at: Locations.backups.appendingPathComponent(app.bundleID))
    }
}

/// Закрытие и запуск приложений.
nonisolated enum AppControl {
    static func running(bundleID: String, at url: URL? = nil) -> [NSRunningApplication] {
        NSRunningApplication.runningApplications(withBundleIdentifier: bundleID).filter { app in
            guard let url, let bundle = app.bundleURL else { return true }
            return bundle.standardizedFileURL.path == url.standardizedFileURL.path
        }
    }

    /// Просит приложение закрыться и ждёт. Возвращает, было ли оно запущено.
    static func quit(bundleID: String, at url: URL, timeout: TimeInterval) async -> Bool {
        let apps = running(bundleID: bundleID, at: url)
        guard !apps.isEmpty else { return false }
        for app in apps where !app.terminate() {
            print("Harbor: \(bundleID) не принял просьбу закрыться")
        }
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline, apps.contains(where: { !$0.isTerminated }) {
            try? await Task.sleep(for: .milliseconds(200))
        }
        // Не закрылось штатно (например, висит диалог) — закрываем принудительно: данные приложение
        // сохраняет по ходу работы, а бандл без замены оставлять нельзя.
        for app in apps where !app.isTerminated { app.forceTerminate() }
        try? await Task.sleep(for: .milliseconds(300))
        return true
    }

    static func launch(_ url: URL) async -> Bool {
        let config = NSWorkspace.OpenConfiguration()
        config.activates = false
        return (try? await NSWorkspace.shared.openApplication(at: url, configuration: config)) != nil
    }

    /// Перезапуск самого Harbor после замены: помощник ждёт выхода процесса и открывает новую версию.
    static func relaunchSelf(at url: URL) {
        let pid = ProcessInfo.processInfo.processIdentifier
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = ["-c", "while kill -0 \(pid) 2>/dev/null; do sleep 0.2; done; /usr/bin/open \"$1\"", "harbor-relaunch", url.path]
        try? process.run()
    }
}
