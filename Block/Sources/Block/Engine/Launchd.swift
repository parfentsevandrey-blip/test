import Foundation

/// Фоновые агенты пользователя: `launchctl disable/enable` в домене gui/<uid>. Службы под защитой SIP
/// система не даёт трогать — Block это проверяет и честно показывает.
nonisolated enum Launchd {
    /// Запись о службе в списке отключённых: нет записи, «включена» или «выключена».
    enum Override: String, Codable, Sendable { case none, enabled, disabled }

    static var domain: String { "gui/\(getuid())" }

    static func plistPath(_ label: String) -> String? {
        for dir in ["/System/Library/LaunchAgents", "/Library/LaunchAgents"] {
            let path = "\(dir)/\(label).plist"
            if FileManager.default.fileExists(atPath: path) { return path }
        }
        return nil
    }

    static func exists(_ label: String) -> Bool { plistPath(label) != nil }

    /// Все записи `launchctl print-disabled` (в разных версиях: «=> disabled», «=> true»).
    static func overrides() -> [String: Override] {
        parse(Shell.run("/bin/launchctl", ["print-disabled", domain]).output)
    }

    static func parse(_ text: String) -> [String: Override] {
        var result: [String: Override] = [:]
        for line in text.split(separator: "\n") {
            let parts = line.components(separatedBy: "=>")
            guard parts.count == 2 else { continue }
            let label = parts[0].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
            let value = parts[1].trimmingCharacters(in: .whitespaces).lowercased()
            if value.hasPrefix("disabled") || value.hasPrefix("true") { result[label] = .disabled }
            else if value.hasPrefix("enabled") || value.hasPrefix("false") { result[label] = .enabled }
        }
        return result
    }

    static func override(_ label: String) -> Override { overrides()[label] ?? .none }

    static func isLoaded(_ label: String) -> Bool {
        Shell.run("/bin/launchctl", ["print", "\(domain)/\(label)"]).ok
    }

    /// Отключить и остановить. Возвращает текст ошибки, если система не дала.
    static func disable(_ label: String) -> String? {
        let r = Shell.run("/bin/launchctl", ["disable", "\(domain)/\(label)"])
        if !r.ok { return r.output.trimmingCharacters(in: .whitespacesAndNewlines) }
        if isLoaded(label) {
            let out = Shell.run("/bin/launchctl", ["bootout", "\(domain)/\(label)"])
            if !out.ok, isLoaded(label) { return "остановить не дала система: " + out.output.trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        return nil
    }

    /// Вернуть, как было: запись «включена» (или отключена) и, если служба работала, — запустить снова.
    static func restore(_ label: String, override previous: Override, loaded: Bool) {
        switch previous {
        case .disabled: Shell.run("/bin/launchctl", ["disable", "\(domain)/\(label)"])
        case .enabled, .none: Shell.run("/bin/launchctl", ["enable", "\(domain)/\(label)"])
        }
        if loaded, !isLoaded(label), let path = plistPath(label) {
            Shell.run("/bin/launchctl", ["bootstrap", domain, path])
        }
    }
}
