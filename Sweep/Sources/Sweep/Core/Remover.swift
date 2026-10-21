import AppKit
import Foundation

/// Запись журнала: что удалено, когда и как вернуть.
nonisolated struct JournalEntry: Codable, Identifiable, Sendable, Hashable {
    var id = UUID()
    var date: Date
    var original: String
    /// Куда объект попал в Корзине (если удаляли в Корзину) — по этому пути его можно вернуть.
    var trashed: String?
    var size: Int64
    var category: String
    var method: RemovalMethod
    var restored = false
}

nonisolated struct RemovalFailure: Error, Sendable, Hashable {
    var path: String
    var reason: String
}

nonisolated struct RemovalReport: Sendable {
    var freed: Int64 = 0
    var removed = 0
    var failures: [RemovalFailure] = []
    var journal: [JournalEntry] = []
    /// Команды, которым нужен пароль администратора: выполняются одним запросом на главном потоке.
    var adminCommands: [String] = []
    var adminSize: Int64 = 0
    var adminEntries: [JournalEntry] = []
}

/// Единственное место, где что-то удаляется. Перед каждой операцией — повторная проверка политики безопасности.
nonisolated enum Remover {
    static func remove(_ items: [JunkItem], category: String, home: URL, forceDelete: Bool = false,
                       progress: @Sendable (Double) -> Void = { _ in }) -> RemovalReport {
        var report = RemovalReport()
        let fm = FileManager.default
        let total = max(1, items.count)
        for (index, item) in items.enumerated() {
            progress(Double(index) / Double(total))
            if let special = item.special {
                switch special {
                case .clearSafariHistory(let db):
                    if Privacy.clearSafariHistory(db) {
                        report.removed += 1
                        report.journal.append(JournalEntry(date: Date(), original: db.path, size: 0, category: category, method: .delete))
                    } else {
                        report.failures.append(RemovalFailure(path: db.path, reason: "Не удалось открыть историю — закройте Safari"))
                    }
                case .forgetReceipt(let pkg):
                    report.adminCommands.append("/usr/sbin/pkgutil --forget \(shellQuote(pkg)) >/dev/null 2>&1 || true")
                }
                continue
            }
            if item.method == .admin {
                for target in item.targets {
                    guard !Safety.verdict(for: target, home: home, allowAdmin: true).isForbidden else {
                        report.failures.append(RemovalFailure(path: target.path, reason: "Запрещено политикой безопасности"))
                        continue
                    }
                    if target.path.hasPrefix("/Library/LaunchDaemons/") {
                        report.adminCommands.append("/bin/launchctl bootout system \(shellQuote(target.path)) 2>/dev/null || true")
                    }
                    report.adminCommands.append("/bin/rm -rf \(shellQuote(target.path))")
                    report.adminEntries.append(JournalEntry(date: Date(), original: target.path, size: 0, category: category, method: .admin))
                }
                report.adminSize += item.size
                continue
            }
            var itemOK = true
            for target in item.targets {
                let verdict = Safety.verdict(for: target, home: home, explicitlyAllowed: item.explicit)
                guard !verdict.isForbidden else {
                    report.failures.append(RemovalFailure(path: target.path, reason: verdict.reason ?? "Запрещено"))
                    itemOK = false
                    continue
                }
                guard DiskUsage.exists(target) else { continue }
                if target.pathExtension == "plist", target.deletingLastPathComponent().path == home.appendingPathComponent("Library/LaunchAgents").path {
                    unloadAgent(target)
                }
                do {
                    if item.method == .trash && !forceDelete {
                        var resulting: NSURL?
                        try fm.trashItem(at: target, resultingItemURL: &resulting)
                        report.journal.append(JournalEntry(date: Date(), original: target.path, trashed: (resulting as URL?)?.path,
                                                           size: item.targets.count == 1 ? item.size : 0, category: category, method: .trash))
                    } else {
                        try fm.removeItem(at: target)
                        report.journal.append(JournalEntry(date: Date(), original: target.path, size: item.targets.count == 1 ? item.size : 0,
                                                           category: category, method: .delete))
                    }
                } catch {
                    itemOK = false
                    report.failures.append(RemovalFailure(path: target.path, reason: describe(error)))
                }
            }
            if itemOK {
                report.removed += 1
                report.freed += item.size
                if item.targets.count != 1, let last = report.journal.indices.last {
                    report.journal[last].size = item.size // для группы файлов размер пишем один раз
                }
            }
        }
        progress(1)
        return report
    }

    /// Выключает задание launchd пользователя перед удалением его plist.
    static func unloadAgent(_ plist: URL) {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/bin/launchctl")
        p.arguments = ["bootout", "gui/\(getuid())", plist.path]
        p.standardOutput = FileHandle.nullDevice
        p.standardError = FileHandle.nullDevice
        try? p.run()
        p.waitUntilExit()
    }

    static func shellQuote(_ s: String) -> String {
        "'" + s.replacingOccurrences(of: "'", with: "'\\''") + "'"
    }

    static func describe(_ error: Error) -> String {
        let ns = error as NSError
        if ns.domain == NSCocoaErrorDomain && (ns.code == NSFileWriteNoPermissionError || ns.code == NSFileReadNoPermissionError) {
            return "Нет доступа — проверьте полный доступ к диску"
        }
        if let underlying = ns.userInfo[NSUnderlyingErrorKey] as? NSError, underlying.domain == NSPOSIXErrorDomain {
            if underlying.code == Int(EPERM) || underlying.code == Int(EACCES) { return "Нет доступа (защищено системой)" }
            if underlying.code == Int(EBUSY) { return "Файл занят" }
        }
        return ns.localizedDescription
    }

    /// Вернуть из Корзины на прежнее место.
    static func restore(_ entry: JournalEntry) -> Result<Void, RemovalFailure> {
        guard let trashed = entry.trashed else {
            return .failure(RemovalFailure(path: entry.original, reason: "Удалено без Корзины — вернуть нельзя"))
        }
        let fm = FileManager.default
        let from = URL(fileURLWithPath: trashed), to = URL(fileURLWithPath: entry.original)
        guard DiskUsage.exists(from) else { return .failure(RemovalFailure(path: trashed, reason: "Уже нет в Корзине")) }
        guard !DiskUsage.exists(to) else { return .failure(RemovalFailure(path: entry.original, reason: "На прежнем месте уже есть файл")) }
        do {
            try fm.createDirectory(at: to.deletingLastPathComponent(), withIntermediateDirectories: true)
            try fm.moveItem(at: from, to: to)
            return .success(())
        } catch {
            return .failure(RemovalFailure(path: entry.original, reason: describe(error)))
        }
    }
}

/// Действия с паролем администратора — через стандартный системный запрос (AppleScript).
/// Отдельный привилегированный помощник не ставим: меньше прав — меньше риска.
enum Admin {
    @discardableResult
    static func run(_ commands: [String]) -> Result<String, RemovalFailure> {
        guard !commands.isEmpty else { return .success("") }
        let shell = commands.joined(separator: " ; ")
        let escaped = shell.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
        let source = "do shell script \"\(escaped)\" with administrator privileges"
        var error: NSDictionary?
        guard let script = NSAppleScript(source: source) else {
            return .failure(RemovalFailure(path: "", reason: "Не удалось подготовить запрос"))
        }
        let result = script.executeAndReturnError(&error)
        if let error {
            let code = error[NSAppleScript.errorNumber] as? Int
            return .failure(RemovalFailure(path: "", reason: code == -128 ? "Отменено" : (error[NSAppleScript.errorMessage] as? String) ?? "Ошибка"))
        }
        return .success(result.stringValue ?? "")
    }
}
