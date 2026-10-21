import Foundation
import SQLite3

/// «Следы активности» в духе CCleaner. Ничего не отмечено заранее, а пароли, закладки,
/// автозаполнение и расширения браузеров не трогаются вообще.
nonisolated enum Privacy {
    static func items(_ ctx: ScanContext) -> [JunkItem] {
        var items: [JunkItem] = []
        for browser in BrowserCatalog.chromium {
            let root = ctx.lib("Application Support/\(browser.supportPath)")
            guard DiskUsage.exists(root) else { continue }
            let running = ctx.runningBundleIDs.contains(browser.bundleID)
            var history: [URL] = [], cookies: [URL] = []
            for profile in BrowserCatalog.profiles(in: root) {
                for name in ["History", "History-journal", "Visited Links", "Top Sites", "Top Sites-journal", "Shortcuts", "Shortcuts-journal"] {
                    let u = profile.appendingPathComponent(name)
                    if DiskUsage.exists(u) { history.append(u) }
                }
                for name in ["Cookies", "Cookies-journal", "Network/Cookies", "Network/Cookies-journal"] {
                    let u = profile.appendingPathComponent(name)
                    if DiskUsage.exists(u) { cookies.append(u) }
                }
            }
            if !history.isEmpty {
                items.append(entry(browser.name, "История посещений и загрузок", history, browser.bundleID, running, risk: .review))
            }
            if !cookies.isEmpty {
                items.append(entry(browser.name, "Cookie — вы выйдете из всех сайтов", cookies, browser.bundleID, running, risk: .caution))
            }
        }
        // Firefox: удаляем только cookies.sqlite — в places.sqlite вместе с историей лежат закладки.
        var firefoxCookies: [URL] = []
        for profile in DiskUsage.children(of: ctx.lib("Application Support/Firefox/Profiles")) {
            for name in ["cookies.sqlite", "cookies.sqlite-wal", "cookies.sqlite-shm"] {
                let u = profile.appendingPathComponent(name)
                if DiskUsage.exists(u) { firefoxCookies.append(u) }
            }
        }
        if !firefoxCookies.isEmpty {
            items.append(entry("Firefox", "Cookie — вы выйдете из всех сайтов", firefoxCookies, "org.mozilla.firefox",
                               ctx.runningBundleIDs.contains("org.mozilla.firefox"), risk: .caution))
        }
        // Safari: чистим строки истории в базе (сама база и закладки остаются). Нужен полный доступ.
        let safariHistory = ctx.lib("Safari/History.db")
        if ctx.hasFullDiskAccess, DiskUsage.exists(safariHistory) {
            var item = JunkItem(url: safariHistory, targets: [], title: "Safari", detail: "История посещений",
                                size: DiskUsage.size(of: safariHistory), modified: DiskUsage.modified(safariHistory), selected: false,
                                risk: .review, method: .delete, bundleID: "com.apple.Safari", explicit: true,
                                special: .clearSafariHistory(safariHistory))
            if ctx.runningBundleIDs.contains("com.apple.Safari") { item.note = "Закройте Safari" }
            items.append(item)
        }
        // Списки недавних документов, программ и серверов в меню Apple и Finder.
        let recentRoot = ctx.lib("Application Support/com.apple.sharedfilelist")
        let recents = DiskUsage.children(of: recentRoot).filter {
            let n = $0.lastPathComponent
            return n.contains("RecentDocuments") || n.contains("RecentApplications") || n.contains("RecentServers")
                || n.hasSuffix("ApplicationRecentDocuments")
        }
        if !recents.isEmpty {
            items.append(JunkItem(url: recentRoot, targets: recents, title: "Недавние объекты",
                                  detail: "Списки недавних документов, программ и серверов", size: recents.reduce(0) { $0 + DiskUsage.size(of: $1) },
                                  modified: nil, selected: false, risk: .review, method: .delete, explicit: true))
        }
        return items
    }

    private static func entry(_ name: String, _ detail: String, _ files: [URL], _ bundleID: String, _ running: Bool, risk: Risk) -> JunkItem {
        var item = JunkItem(url: files[0].deletingLastPathComponent(), targets: files, title: name, detail: detail,
                            size: files.reduce(0) { $0 + DiskUsage.size(of: $1) }, modified: files.compactMap(DiskUsage.modified).max(),
                            selected: false, risk: risk, method: .delete, bundleID: bundleID)
        if running { item.note = "Браузер открыт — закройте его перед очисткой" }
        return item
    }

    /// Удаляет историю Safari SQL-запросами: база остаётся целой, Safari не замечает подмены.
    static func clearSafariHistory(_ db: URL) -> Bool {
        var handle: OpaquePointer?
        guard sqlite3_open_v2(db.path, &handle, SQLITE_OPEN_READWRITE, nil) == SQLITE_OK else { return false }
        defer { sqlite3_close(handle) }
        guard sqlite3_exec(handle, "BEGIN; DELETE FROM history_visits; DELETE FROM history_items; COMMIT;", nil, nil, nil) == SQLITE_OK else {
            sqlite3_exec(handle, "ROLLBACK;", nil, nil, nil)
            return false
        }
        sqlite3_exec(handle, "DELETE FROM history_tombstones;", nil, nil, nil) // таблицы может не быть в старых версиях
        sqlite3_exec(handle, "VACUUM;", nil, nil, nil)
        return true
    }
}
