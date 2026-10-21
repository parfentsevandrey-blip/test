import AppKit
import Foundation

/// Полный доступ к диску: без него часть мусора (контейнеры программ, Корзина, Почта) не видна.
enum Permissions {
    /// Проверка без системных запросов: базу TCC и папку Safari читает только программа с полным доступом.
    nonisolated static func hasFullDiskAccess() -> Bool {
        let home = FileManager.default.homeDirectoryForCurrentUser
        if FileManager.default.isReadableFile(atPath: "/Library/Application Support/com.apple.TCC/TCC.db") { return true }
        return (try? FileManager.default.contentsOfDirectory(atPath: home.appendingPathComponent("Library/Safari").path)) != nil
    }

    static func openFullDiskAccessSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles") {
            NSWorkspace.shared.open(url)
        }
    }

    static func openLoginItemsSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.LoginItems-Settings.extension") {
            NSWorkspace.shared.open(url)
        }
    }
}
