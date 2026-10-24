import Foundation

/// Настройки пользователя через CFPreferences — тот же механизм, что у `defaults` и «Системных настроек».
nonisolated enum Preferences {
    private static func host(_ currentHost: Bool) -> CFString { currentHost ? kCFPreferencesCurrentHost : kCFPreferencesAnyHost }

    /// Текущее значение ключа (nil — ключа нет).
    static func read(_ key: PrefKey) -> PlistValue? {
        let domain = key.domain as CFString
        CFPreferencesSynchronize(domain, kCFPreferencesCurrentUser, host(key.currentHost))
        guard let value = CFPreferencesCopyValue(key.key as CFString, domain, kCFPreferencesCurrentUser, host(key.currentHost)) else { return nil }
        return plist(value)
    }

    static func write(_ key: PrefKey, _ value: PlistValue?) {
        let domain = key.domain as CFString
        CFPreferencesSetValue(key.key as CFString, value.map { $0.object as AnyObject }, domain, kCFPreferencesCurrentUser, host(key.currentHost))
        CFPreferencesSynchronize(domain, kCFPreferencesCurrentUser, host(key.currentHost))
    }

    static func plist(_ value: Any) -> PlistValue? {
        if let n = value as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return .bool(n.boolValue) }
            return .int(n.intValue)
        }
        if let s = value as? String { return .string(s) }
        return nil
    }

    /// Совпадает ли значение с нужным (числа 0/1 и булевы значения для системы одно и то же).
    static func matches(_ current: PlistValue?, _ wanted: PlistValue) -> Bool {
        switch (current, wanted) {
        case (.bool(let a)?, .bool(let b)): a == b
        case (.int(let a)?, .bool(let b)): (a != 0) == b
        case (.bool(let a)?, .int(let b)): (b != 0) == a
        case (.int(let a)?, .int(let b)): a == b
        case (.string(let a)?, .string(let b)): a == b
        default: false
        }
    }

    // MARK: Системные файлы (чтение без прав, запись — через Privileged)

    static func readSystem(_ path: String, key: String) -> PlistValue? {
        guard let dict = NSDictionary(contentsOfFile: path + ".plist"), let value = dict[key] else { return nil }
        return plist(value)
    }

    /// Команда `defaults` для записи или удаления ключа системного файла.
    static func systemCommand(_ path: String, key: String, value: PlistValue?) -> String {
        let p = Shell.quote(path), k = Shell.quote(key)
        guard let value else { return "(/usr/bin/defaults delete \(p) \(k) 2>/dev/null || true)" }
        switch value {
        case .bool(let b): return "/usr/bin/defaults write \(p) \(k) -bool \(b ? "true" : "false")"
        case .int(let i): return "/usr/bin/defaults write \(p) \(k) -int \(i)"
        case .string(let s): return "/usr/bin/defaults write \(p) \(k) -string \(Shell.quote(s))"
        }
    }
}
