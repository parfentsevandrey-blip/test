import Foundation

/// Насколько уверенно можно удалять находку.
nonisolated enum Risk: Int, Codable, Sendable, Comparable {
    /// Восстановится само (кэши, логи) — отмечается заранее.
    case safe
    /// Скорее всего мусор, но решать пользователю.
    case review
    /// Может быть ценным (резервные копии, хранилища) — только вручную.
    case caution

    static func < (a: Risk, b: Risk) -> Bool { a.rawValue < b.rawValue }
}

nonisolated enum RemovalMethod: String, Codable, Sendable {
    /// Сразу и навсегда — для того, что программа пересоздаст сама.
    case delete
    /// В Корзину: можно вернуть из «Журнала».
    case trash
    /// Через пароль администратора (системные папки).
    case admin
}

/// Нестандартное действие вместо удаления файлов.
nonisolated enum SpecialAction: Codable, Sendable, Hashable {
    /// Очистить историю Safari (строки в History.db, сама база остаётся).
    case clearSafariHistory(URL)
    /// Забыть квитанцию установщика (`pkgutil --forget`), нужен пароль администратора.
    case forgetReceipt(String)
}

nonisolated struct JunkItem: Identifiable, Hashable, Sendable {
    var id: String { url.path }
    /// Что показываем (папка программы, профиль браузера…).
    var url: URL
    /// Что на самом деле удаляем. Обычно `[url]`; для логов — только старые файлы внутри.
    var targets: [URL]
    var title: String
    var detail: String?
    var size: Int64
    var modified: Date?
    var selected: Bool
    var risk: Risk
    var method: RemovalMethod
    /// Для иконки программы.
    var bundleID: String?
    /// Почему не отмечено заранее / на что обратить внимание.
    var note: String?
    /// Файлы внутри разрешены категорией явно (например, резервные копии iPhone).
    var explicit = false
    var special: SpecialAction?
    /// Для группировки в списке (вид остатка или программа-хозяин).
    var group: String?
}

nonisolated enum CategoryID: String, CaseIterable, Sendable, Codable {
    case appCaches, sandboxCaches, logs, crashReports, browserCaches, developer, devArtifacts
    case leftovers, installers, deviceUpdates, deviceBackups, mailDownloads, trash, privacy

    var title: String {
        switch self {
        case .appCaches: "Кэши программ"
        case .sandboxCaches: "Кэши программ из App Store"
        case .logs: "Журналы программ"
        case .crashReports: "Отчёты о сбоях"
        case .browserCaches: "Кэши браузеров"
        case .developer: "Мусор разработчика"
        case .devArtifacts: "Сборочные папки проектов"
        case .leftovers: "Остатки удалённых программ"
        case .installers: "Забытые установщики"
        case .deviceUpdates: "Прошивки iPhone и iPad"
        case .deviceBackups: "Резервные копии iPhone и iPad"
        case .mailDownloads: "Вложения, открытые из Почты"
        case .trash: "Корзина"
        case .privacy: "Следы активности"
        }
    }

    var explanation: String {
        switch self {
        case .appCaches:
            "Временные данные, которые программы пересоздают сами. Кэши запущенных программ, iCloud, шрифтов и облачных дисков не трогаем."
        case .sandboxCaches:
            "Кэши программ из App Store — лежат в их «контейнерах». Нужен полный доступ к диску."
        case .logs:
            "Журналы старше недели. Свежие оставляем — они нужны, если что-то сломалось прямо сейчас."
        case .crashReports:
            "Отчёты о падениях программ. Apple уже получила их, если вы разрешили отправку."
        case .browserCaches:
            "Кэш страниц, шейдеров и скриптов Chrome, Яндекс Браузера, Edge, Brave, Arc, Opera, Vivaldi. История и пароли не трогаются."
        case .developer:
            "DerivedData, кэши симуляторов и менеджеров пакетов — всё пересобирается или скачивается заново. Архивы Xcode — только вручную."
        case .devArtifacts:
            "node_modules, .build, target в ваших проектах. Отмечены только те, что не менялись два месяца."
        case .leftovers:
            "Файлы программ, которых больше нет на Mac. Проверяем по идентификатору программы, а не по похожему имени; всё, что менялось за последний месяц, не показываем."
        case .installers:
            "Образы .dmg, .pkg и .xip в Загрузках и на Рабочем столе старше недели. Отправляются в Корзину."
        case .deviceUpdates:
            "Скачанные прошивки iOS и iPadOS — Finder скачает их снова при необходимости."
        case .deviceBackups:
            "Локальные резервные копии устройств. Удаляйте, только если уверены, что копия есть в iCloud. Отправляются в Корзину."
        case .mailDownloads:
            "Копии вложений, которые вы открывали из Почты. Сами письма и вложения в них остаются."
        case .trash:
            "Файлы в Корзине — на Mac и на подключённых дисках."
        case .privacy:
            "История и cookie браузеров, списки недавних файлов. Ничего не отмечено заранее; cookie — это выход из всех сайтов."
        }
    }

    var symbol: String {
        switch self {
        case .appCaches: "square.stack.3d.up.fill"
        case .sandboxCaches: "shippingbox.fill"
        case .logs: "doc.text.fill"
        case .crashReports: "exclamationmark.triangle.fill"
        case .browserCaches: "globe"
        case .developer: "hammer.fill"
        case .devArtifacts: "folder.badge.gearshape"
        case .leftovers: "puzzlepiece.extension.fill"
        case .installers: "opticaldiscdrive.fill"
        case .deviceUpdates: "iphone.gen3"
        case .deviceBackups: "externaldrive.fill.badge.timemachine"
        case .mailDownloads: "paperclip"
        case .trash: "trash.fill"
        case .privacy: "hand.raised.fill"
        }
    }

    /// Без полного доступа к диску категория не видна целиком.
    var needsFullDiskAccess: Bool {
        switch self {
        case .sandboxCaches, .deviceBackups, .mailDownloads, .trash: true
        default: false
        }
    }

    /// Что входит в «Умную проверку».
    static let smart: [CategoryID] = [.appCaches, .sandboxCaches, .logs, .crashReports, .browserCaches, .developer,
                                       .leftovers, .deviceUpdates, .mailDownloads, .trash]
}

nonisolated struct JunkCategory: Identifiable, Sendable {
    var id: CategoryID
    var items: [JunkItem]
    /// Почему категория пустая или неполная (например, нет полного доступа к диску).
    var notice: String?

    var totalSize: Int64 { items.reduce(0) { $0 + $1.size } }
    var selectedSize: Int64 { items.reduce(0) { $0 + ($1.selected ? $1.size : 0) } }
    var selectedCount: Int { items.reduce(0) { $0 + ($1.selected ? 1 : 0) } }
}

/// Что сканер знает о системе. Всё внешнее (дом, папки программ, запущенные программы) передаётся сюда,
/// поэтому самопроверка может прогнать сканеры на выдуманном «доме» и проверить, что лишнее не удаляется.
nonisolated struct ScanContext: Sendable {
    var home: URL
    var applicationDirs: [URL]
    var hasFullDiskAccess: Bool
    var runningBundleIDs: Set<String>
    var now = Date()
    var installed: InstalledIndex
    /// Пути, которые пользователь попросил никогда не трогать.
    var ignored: [String] = []
    /// Не заходить в Рабочий стол, Документы, Загрузки и Корзину (без запросов доступа — для демо и CI).
    var avoidPrivacyPrompts = false
    /// Проверять установленные программы ещё и через Launch Services (в самопроверке — выключено).
    var useLaunchServices = true
    var volumes: [URL] = []

    func isIgnored(_ url: URL) -> Bool {
        let p = url.path
        return ignored.contains { p == $0 || p.hasPrefix($0 + "/") }
    }

    var library: URL { home.appendingPathComponent("Library") }
    func lib(_ sub: String) -> URL { library.appendingPathComponent(sub) }
}
