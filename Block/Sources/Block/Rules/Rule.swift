import Foundation

/// Уровни защиты: чем выше, тем глубже Block меняет систему.
nonisolated enum Level: Int, Codable, CaseIterable, Comparable, Sendable, Identifiable {
    case settings = 1, defaults, launchGuard, services, network

    var id: Int { rawValue }
    static func < (a: Level, b: Level) -> Bool { a.rawValue < b.rawValue }

    var title: String {
        switch self {
        case .settings: "Настройки"
        case .defaults: "Программы по умолчанию"
        case .launchGuard: "Сторож запуска"
        case .services: "Фоновые службы"
        case .network: "Сеть"
        }
    }

    var symbol: String {
        switch self {
        case .settings: "switch.2"
        case .defaults: "arrow.triangle.swap"
        case .launchGuard: "hand.raised.fill"
        case .services: "gearshape.2.fill"
        case .network: "network.slash"
        }
    }
}

/// «Поверхность Apple» — сервис, как его видит человек. На ней строится созвездие на главном экране.
nonisolated enum Surface: String, Codable, CaseIterable, Sendable, Identifiable {
    case appStore, iCloud, siri, intelligence, media, news, games, home, messages, safari, ads, photos, search, quiet

    var id: String { rawValue }

    var title: String {
        switch self {
        case .appStore: "App Store"
        case .iCloud: "iCloud"
        case .siri: "Siri"
        case .intelligence: "Apple Intelligence"
        case .media: "Музыка и TV"
        case .news: "News и «Акции»"
        case .games: "Игры и Game Center"
        case .home: "«Дом» и «Советы»"
        case .messages: "Сообщения и FaceTime"
        case .safari: "Safari"
        case .ads: "Реклама и аналитика"
        case .photos: "«Фото» с iPhone"
        case .search: "Подсказки поиска"
        case .quiet: "Сеть без спроса"
        }
    }

    var symbol: String {
        switch self {
        case .appStore: "bag.fill"
        case .iCloud: "icloud.fill"
        case .siri: "waveform.circle.fill"
        case .intelligence: "apple.intelligence"
        case .media: "music.note"
        case .news: "newspaper.fill"
        case .games: "gamecontroller.fill"
        case .home: "house.fill"
        case .messages: "message.fill"
        case .safari: "safari.fill"
        case .ads: "chart.bar.xaxis"
        case .photos: "photo.on.rectangle"
        case .search: "magnifyingglass"
        case .quiet: "antenna.radiowaves.left.and.right.slash"
        }
    }

    /// Чем из каталога заменяется (если есть замена).
    var replacement: CatalogApp? {
        switch self {
        case .appStore: .harbor
        case .media: .lumen
        case .safari: .ward
        case .siri: .utter
        case .intelligence: .synapse
        case .iCloud, .news, .games, .home, .messages, .ads, .photos, .search, .quiet: nil
        }
    }
}

/// Программы каталога, которые занимают место сервисов Apple.
nonisolated enum CatalogApp: String, Codable, CaseIterable, Sendable, Identifiable {
    case harbor, ward, lumen, swarm, utter, synapse, veil

    var id: String { rawValue }

    var name: String {
        switch self {
        case .harbor: "Harbor"
        case .ward: "Ward"
        case .lumen: "Lumen"
        case .swarm: "Swarm"
        case .utter: "Utter"
        case .synapse: "Synapse"
        case .veil: "Veil"
        }
    }

    var bundleID: String {
        switch self {
        case .harbor: "app.harbor.store"
        case .ward: "app.ward.browser"
        case .lumen: "app.lumen.player"
        case .swarm: "app.swarm.torrent"
        case .utter: "app.utter.dictation"
        case .synapse: "app.synapse.local-llm"
        case .veil: "app.veil.client"
        }
    }

    var role: String {
        switch self {
        case .harbor: "вместо App Store"
        case .ward: "вместо Safari"
        case .lumen: "вместо «Музыки» и TV"
        case .swarm: "торренты и magnet-ссылки"
        case .utter: "диктовка вместо Siri"
        case .synapse: "ИИ на этом Mac вместо Apple Intelligence"
        case .veil: "сетевые правила без пароля"
        }
    }

    var symbol: String {
        switch self {
        case .harbor: "shippingbox.fill"
        case .ward: "shield.lefthalf.filled"
        case .lumen: "play.rectangle.fill"
        case .swarm: "arrow.down.circle.fill"
        case .utter: "mic.fill"
        case .synapse: "brain.head.profile"
        case .veil: "network.badge.shield.half.filled"
        }
    }
}

/// Значение ключа настроек. Хранится в журнале, поэтому Codable.
nonisolated enum PlistValue: Codable, Hashable, Sendable, CustomStringConvertible {
    case bool(Bool), int(Int), string(String)

    var object: Any {
        switch self {
        case .bool(let v): v
        case .int(let v): v
        case .string(let v): v
        }
    }

    var description: String {
        switch self {
        case .bool(let v): v ? "да" : "нет"
        case .int(let v): String(v)
        case .string(let v): "«\(v)»"
        }
    }
}

/// Один ключ настроек: домен, ключ, нужное Block значение.
nonisolated struct PrefKey: Codable, Hashable, Sendable {
    var domain: String
    var key: String
    var value: PlistValue
    /// Настройка этого Mac (`defaults -currentHost`), а не учётной записи.
    var currentHost = false
}

/// Системный файл настроек (нужен пароль администратора).
nonisolated struct SystemPlist: Codable, Hashable, Sendable {
    /// Путь без «.plist», как его понимает `defaults`.
    var path: String
    var keys: [String: PlistValue]
}

/// Тип файлов или схема ссылок, которые должна открывать программа каталога.
nonisolated struct Handler: Codable, Hashable, Sendable {
    enum Kind: String, Codable, Sendable { case scheme, contentType, fileExtension }
    var kind: Kind
    var value: String
}

/// Что именно делает правило.
nonisolated enum Action: Sendable {
    /// Ключи настроек пользователя. `restart` — процессы, которые перечитывают настройку после перезапуска.
    case prefs([PrefKey], restart: [String])
    /// Системные настройки (пароль администратора).
    case systemPlist(SystemPlist)
    /// Надёжного ключа нет: Block открывает ровно ту панель и подсказывает, что нажать.
    case pane(url: String, hint: String)
    /// Программа по умолчанию.
    case handlers([Handler], app: CatalogApp)
    /// Сторож запуска: программа закрывается при запуске, вместо неё открывается замена.
    case launchGuard(bundleID: String, replacement: CatalogApp?)
    /// Фоновый агент пользователя (`launchctl disable gui/<uid>/<label>`).
    case agent(label: String)
    /// Значки программ Apple в Dock.
    case dock(bundleIDs: [String])
    /// Группа адресов Apple (через /etc/hosts или Veil).
    case network(NetGroup)
}

nonisolated extension Action {
    /// Программа, которую закрывает сторож.
    var guardedBundle: String? {
        guard case .launchGuard(let id, _) = self else { return nil }
        return id
    }

    var agentLabel: String? {
        guard case .agent(let label) = self else { return nil }
        return label
    }

    var isSystemPlist: Bool {
        guard case .systemPlist = self else { return false }
        return true
    }

    var isPane: Bool {
        guard case .pane = self else { return false }
        return true
    }
}

/// Когда правило имеет смысл — по ответам при первом запуске.
nonisolated enum Condition: String, Codable, Sendable {
    /// Человек не пользуется iCloud и ему не нужен «Найти Mac».
    case noICloud
    /// С этим Mac не используется iPhone (Handoff, Сообщения, FaceTime).
    case noIPhone
}

/// Насколько правило проверено: «надёжно» — известный ключ, который меняет и панель настроек;
/// «экспериментально» — ключ или служба, которые Apple меняет от версии к версии.
nonisolated enum Confidence: String, Codable, Sendable { case solid, experimental }

nonisolated struct Rule: Identifiable, Sendable {
    let id: String
    let title: String
    let detail: String
    /// Что перестанет работать (пусто — ничего заметного).
    let breaks: String
    let surface: Surface
    let level: Level
    let action: Action
    var confidence: Confidence = .solid
    var condition: Condition?
    /// Версии macOS (старшая цифра), на которых правило проверено.
    var testedOn: [Int] = [26]

    var needsAdmin: Bool {
        switch action {
        case .systemPlist: true
        case .network: NetworkMethod.current == .hosts
        default: false
        }
    }

    /// Ручное правило: Block открывает панель настроек, человек нажимает сам.
    var isManual: Bool { action.isPane }
}

/// Состояние правила на этом Mac.
nonisolated enum RuleState: Equatable, Sendable {
    /// Сервис Apple работает как обычно.
    case active
    /// Сервис выключен так, как хочет Block.
    case blocked
    /// Часть сделана (например, из трёх ключей поменялся один).
    case partial(String)
    /// Система не даёт (SIP, нет прав).
    case protected(String)
    /// Неприменимо: нет программы, нет службы в этой версии, не установлена замена.
    case unavailable(String)
    /// Делается вручную в панели настроек.
    case manual(done: Bool)

    var isBlocked: Bool {
        switch self {
        case .blocked: true
        case .manual(let done): done
        default: false
        }
    }

    /// Учитывается ли в доле «Mac принадлежит вам».
    var counts: Bool {
        switch self {
        case .protected, .unavailable: false
        default: true
        }
    }

    var label: String {
        switch self {
        case .active: "работает"
        case .blocked: "выключено"
        case .partial(let s): s
        case .protected(let s): s
        case .unavailable(let s): s
        case .manual(let done): done ? "сделано вручную" : "нужно нажать в настройках"
        }
    }
}

/// Профиль: набор уровней.
nonisolated enum Profile: String, Codable, CaseIterable, Sendable, Identifiable {
    case gentle, full, custom

    var id: String { rawValue }

    var title: String {
        switch self {
        case .gentle: "Мягко"
        case .full: "Полностью"
        case .custom: "Своё"
        }
    }

    var subtitle: String {
        switch self {
        case .gentle: "Настройки, программы по умолчанию и сторож запуска. Без пароля администратора."
        case .full: "Всё: ещё фоновые службы и адреса Apple в сети. Один раз пароль администратора."
        case .custom: "Только то, что вы выбрали сами."
        }
    }

    var levels: Set<Level> {
        switch self {
        case .gentle: [.settings, .defaults, .launchGuard]
        case .full: Set(Level.allCases)
        case .custom: []
        }
    }
}

/// Ответы при первом запуске.
/// Пока человек не ответил «нет», считается, что iCloud, «Найти Mac» и iPhone нужны, — их правила не трогаются.
nonisolated struct Answers: Codable, Equatable, Sendable {
    var usesICloud = true
    var needsFindMy = true
    var usesIPhone = true

    func allows(_ condition: Condition?) -> Bool {
        switch condition {
        case nil: true
        case .noICloud: !usesICloud && !needsFindMy
        case .noIPhone: !usesIPhone
        }
    }
}
