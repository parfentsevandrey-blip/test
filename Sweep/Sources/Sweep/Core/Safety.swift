import Foundation

/// Политика безопасности — единственное место, которое решает, можно ли вообще трогать путь.
///
/// Каждый сканер спрашивает её перед тем, как показать находку, а `Remover` — ещё раз прямо
/// перед удалением (защита в глубину: даже если сканер ошибся, удаление не пройдёт).
///
/// Принципы, выведенные из жалоб на CleanMyMac/CCleaner:
/// - системные и «живые» пользовательские данные запрещены целиком (iCloud, Почта, Фото, Связка ключей…);
/// - хранилища паролей и криптокошельков — только по явному выбору пользователя, никогда по умолчанию;
/// - сами корневые папки (`~/Library/Caches`, `~/Documents`…) не удаляются никогда — только их содержимое;
/// - нельзя выйти за разрешённые корни через символические ссылки.
nonisolated enum Safety {
    nonisolated enum Verdict: Equatable, Sendable {
        case allowed
        /// Можно только по явному выбору пользователя; заранее не отмечается.
        case sensitive(String)
        /// Нельзя ни при каких условиях.
        case forbidden(String)

        var isForbidden: Bool { if case .forbidden = self { true } else { false } }
        var reason: String? {
            switch self {
            case .allowed: nil
            case .sensitive(let r), .forbidden(let r): r
            }
        }
    }

    /// Папки внутри дома, которые запрещены вместе со всем содержимым.
    static let forbiddenInHome: [String] = [
        ".ssh", ".gnupg", ".aws", ".azure", ".kube", ".config/gcloud", ".password-store", ".local/share/keyrings",
        "Library/Keychains", "Library/Mobile Documents", "Library/CloudStorage", "Library/Messages",
        "Library/Mail", "Library/Accounts", "Library/Calendars", "Library/Reminders", "Library/Photos",
        "Library/IdentityServices", "Library/Sharing", "Library/Suggestions", "Library/Passes",
        "Library/PersonalizationPortrait", "Library/Biome", "Library/Trial", "Library/Daemon Containers",
        "Library/Application Support/AddressBook", "Library/Application Support/CallHistoryDB",
        "Library/Application Support/CallHistoryTransactions", "Library/Application Support/com.apple.TCC",
        "Library/Application Support/CloudDocs", "Library/Application Support/FileProvider",
        "Library/Application Support/Knowledge", "Library/Application Support/com.apple.sharedfilelist",
        "Library/Application Support/com.apple.backgroundtaskmanagementagent",
        "Library/Application Support/iCloud", "Library/Application Support/com.apple.spotlight",
        "Library/Application Support/Dock", "Library/Application Support/com.apple.wallpaper",
        "Library/Preferences/.GlobalPreferences.plist", "Library/Preferences/ByHost/.GlobalPreferences",
        "Library/Fonts", "Library/Keyboard Layouts", "Library/Input Methods", "Library/Spelling",
        "Library/Safari", "Library/Cookies/Cookies.binarycookies",
        "Pictures/Photos Library.photoslibrary", "Music/Music", "Music/iTunes",
    ]

    /// Разрешено только в явно выбранных категориях (через `explicitlyAllowed`).
    static let explicitOnly: [String] = [
        "Library/Application Support/MobileSync",
        "Library/Containers/com.apple.mail/Data/Library/Mail Downloads",
        "Library/Safari/History.db",
        "Library/Application Support/com.apple.sharedfilelist",
    ]

    /// Корни, которые сами по себе удалять нельзя никогда (только содержимое).
    static let neverItselfInHome: [String] = [
        "", "Library", "Library/Caches", "Library/Logs", "Library/Application Support", "Library/Preferences",
        "Library/Preferences/ByHost", "Library/Containers", "Library/Group Containers", "Library/Saved Application State",
        "Library/HTTPStorages", "Library/WebKit", "Library/Cookies", "Library/LaunchAgents", "Library/Application Scripts",
        "Library/Logs/DiagnosticReports", "Library/Developer", "Library/Developer/Xcode", "Library/Developer/Xcode/DerivedData",
        "Library/Developer/Xcode/Archives", "Library/Developer/CoreSimulator", ".Trash", "Applications",
        "Desktop", "Documents", "Downloads", "Movies", "Music", "Pictures", "Public", "Developer", "Sites",
    ]

    /// Системные корни вне дома: разрешены только перечисленные подпапки (и только через пароль администратора).
    static let adminAllowedRoots: [String] = [
        "/Library/Application Support/", "/Library/Caches/", "/Library/Preferences/", "/Library/LaunchAgents/",
        "/Library/LaunchDaemons/", "/Library/PrivilegedHelperTools/", "/Library/Logs/", "/Library/Internet Plug-Ins/",
        "/Library/PreferencePanes/", "/Library/Logs/DiagnosticReports/",
    ]

    static let adminForbidden: [String] = [
        "/Library/Application Support/Apple", "/Library/Application Support/com.apple", "/Library/Preferences/com.apple",
        "/Library/Preferences/SystemConfiguration", "/Library/LaunchDaemons/com.apple", "/Library/LaunchAgents/com.apple",
        "/Library/Application Support/CrashReporter", "/Library/Application Support/Mobile Documents",
    ]

    /// Слова, по которым узнаются хранилища паролей и кошельков.
    static let vaultWords = [
        "1password", "bitwarden", "keepass", "dashlane", "enpass", "lastpass", "nordpass", "proton pass", "protonpass",
        "electrum", "exodus", "ledger live", "ledger-live", "trezor", "wasabi", "bitcoin", "ethereum", "monero",
        "metamask", "atomic wallet", "keychain", "authy", "yubico",
    ]

    /// Расширения «библиотек» приложений Apple и пользовательских медиатек — внутрь не заходим и не удаляем.
    static let protectedPackageExtensions: Set<String> = ["photoslibrary", "musiclibrary", "tvlibrary", "photolibrary", "migratedphotolibrary"]

    /// Главная проверка. `explicitlyAllowed` — пути из `explicitOnly`, разрешённые выбранной категорией.
    static func verdict(for url: URL, home: URL, explicitlyAllowed: Bool = false, allowAdmin: Bool = false) -> Verdict {
        let raw = url.standardizedFileURL.path
        guard raw.hasPrefix("/"), !raw.contains("/../"), !raw.hasSuffix("/..") else {
            return .forbidden("Некорректный путь")
        }
        // Проверяем реальное место: ссылки в цепочке родительских папок разрешаются (сам объект может быть
        // ссылкой — тогда удаляется только она). Так ссылка из кэша в чужую папку не уводит удаление наружу.
        let real = url.deletingLastPathComponent().resolvingSymlinksInPath().appendingPathComponent(url.lastPathComponent)
        let path = real.standardizedFileURL.path
        let homePath = home.resolvingSymlinksInPath().standardizedFileURL.path
        let lower = path.lowercased()

        for ext in protectedPackageExtensions where lower.contains(".\(ext)") {
            return .forbidden("Медиатека — управляйте ей из самого приложения")
        }

        if path == homePath || path.hasPrefix(homePath + "/") {
            let rel = path == homePath ? "" : String(path.dropFirst(homePath.count + 1))
            if neverItselfInHome.contains(rel) { return .forbidden("Системная папка — удаляется только содержимое") }
            let inExplicit = explicitOnly.contains { rel == $0 || rel.hasPrefix($0 + "/") }
            if inExplicit && !explicitlyAllowed { return .forbidden("Только по явному выбору") }
            if !inExplicit {
                for p in forbiddenInHome where rel == p || rel.hasPrefix(p + "/") {
                    return .forbidden("Личные или системные данные")
                }
            }
            if rel.hasPrefix("Library/Containers/com.apple.") && !rel.contains("/Data/Library/Caches") && !inExplicit {
                return .forbidden("Данные приложений Apple")
            }
            if rel.hasPrefix("Library/Group Containers/group.com.apple.") || rel.hasPrefix("Library/Group Containers/com.apple.") {
                return .forbidden("Общие данные приложений Apple")
            }
            if rel.hasPrefix("Library/Preferences/com.apple.") || rel.hasPrefix("Library/Preferences/ByHost/com.apple.") {
                return .forbidden("Настройки macOS")
            }
            if isVault(lower) && !rel.hasPrefix("Library/Caches/") {
                return .sensitive("Похоже на хранилище паролей или криптокошелёк")
            }
            return .allowed
        }

        if path.hasPrefix("/Applications/") {
            if path.hasPrefix("/Applications/Safari.app") || path.hasPrefix("/Applications/Utilities/") && isAppleBundleName(url) {
                return .forbidden("Системная программа")
            }
            return .allowed
        }

        if path.hasPrefix("/Volumes/") && path.contains("/.Trashes/") {
            return .allowed
        }

        if allowAdmin, adminAllowedRoots.contains(where: { path.hasPrefix($0) }) {
            if adminAllowedRoots.contains(where: { path + "/" == $0 }) { return .forbidden("Системная папка") }
            if adminForbidden.contains(where: { path.hasPrefix($0) }) { return .forbidden("Компоненты macOS") }
            return .allowed
        }
        return .forbidden("Вне разрешённых мест")
    }

    static func isVault(_ lowerPath: String) -> Bool {
        vaultWords.contains { lowerPath.contains($0) }
    }

    private static func isAppleBundleName(_ url: URL) -> Bool {
        let info = url.appendingPathComponent("Contents/Info.plist")
        guard let dict = NSDictionary(contentsOf: info), let id = dict["CFBundleIdentifier"] as? String else { return true }
        return id.hasPrefix("com.apple.")
    }

}
