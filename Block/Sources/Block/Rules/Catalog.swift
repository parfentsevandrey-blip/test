import Foundation

/// Все правила Block. Ключи настроек — те же, что меняют «Системные настройки»; у каждого правила есть
/// проба, поэтому, если Apple переименует ключ, Block покажет «не сработало», а не будет молча врать.
nonisolated enum Catalog {
    static let rules: [Rule] = settings + defaults + guards + [dock] + services + network

    static func rule(_ id: String) -> Rule? { rules.first { $0.id == id } }

    // MARK: 1. Настройки

    static let settings: [Rule] = [
        Rule(id: "siri.off", title: "Siri выключена",
             detail: "Ассистент, сочетание клавиш и значок в строке меню.",
             breaks: "голосовой ассистент Apple", surface: .siri, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.assistant.support", key: "Assistant Enabled", value: .bool(false)),
                             PrefKey(domain: "com.apple.Siri", key: "StatusMenuVisible", value: .bool(false)),
                             PrefKey(domain: "com.apple.Siri", key: "UserHasDeclinedEnable", value: .bool(true))],
                            restart: ["SystemUIServer"])),
        Rule(id: "siri.voice", title: "«Привет, Siri» не слушает",
             detail: "Микрофон не ждёт фразу-пробуждение.",
             breaks: "", surface: .siri, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.Siri", key: "VoiceTriggerUserEnabled", value: .bool(false))], restart: [])),
        Rule(id: "siri.sharing", title: "Записи Siri и диктовки не отправляются",
             detail: "Отказ от «Улучшения Siri и диктовки»: записи голоса не уходят в Apple.",
             breaks: "", surface: .siri, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.assistant.support", key: "Siri Data Sharing Opt-In Status", value: .int(2))], restart: [])),
        Rule(id: "intelligence.off", title: "Apple Intelligence выключен",
             detail: "Надёжного ключа у Apple нет: Block открывает «Apple Intelligence и Siri» — выключите переключатель.",
             breaks: "инструменты письма, Image Playground, сводки уведомлений", surface: .intelligence, level: .settings,
             action: .pane(url: "x-apple.systempreferences:com.apple.Siri-Settings.extension",
                           hint: "Выключите «Apple Intelligence» вверху панели, затем вернитесь в Block и нажмите «Сделано».")),
        Rule(id: "ads.personal", title: "Персональная реклама Apple выключена",
             detail: "Apple не подбирает объявления в App Store и News по вашим данным.",
             breaks: "", surface: .ads, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.AdLib", key: "allowApplePersonalizedAdvertising", value: .bool(false)),
                             PrefKey(domain: "com.apple.AdLib", key: "allowIdentifierForAdvertising", value: .bool(false)),
                             PrefKey(domain: "com.apple.AdLib", key: "forceLimitAdTracking", value: .bool(true))], restart: [])),
        Rule(id: "analytics.system", title: "Аналитика Mac не отправляется",
             detail: "«Отправлять аналитику Mac» и «Делиться с разработчиками» выключены. Нужен пароль администратора.",
             breaks: "", surface: .ads, level: .settings,
             action: .systemPlist(SystemPlist(path: "/Library/Application Support/CrashReporter/DiagnosticMessagesHistory",
                                              keys: ["AutoSubmit": .bool(false), "ThirdPartyDataSubmit": .bool(false)]))),
        Rule(id: "search.lookup", title: "Поиск без подсказок из интернета",
             detail: "Spotlight и «Найти» не отправляют набранное в Apple ради подсказок Siri.",
             breaks: "подсказки Apple в Spotlight", surface: .search, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.lookup.shared", key: "LookupSuggestionsDisabled", value: .bool(true))], restart: [])),
        Rule(id: "photos.hotplug", title: "«Фото» не открывается при подключении iPhone",
             detail: "Подключили телефон — ничего не запускается само.",
             breaks: "", surface: .photos, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.ImageCapture", key: "disableHotPlug", value: .bool(true), currentHost: true)], restart: [])),
        Rule(id: "account.badge", title: "Без значка «Войдите в Apple Account»",
             detail: "Красная точка на «Системных настройках» в Dock больше не зовёт войти.",
             breaks: "", surface: .iCloud, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.systempreferences", key: "AttentionPrefBundleIDs", value: .int(0))], restart: ["Dock"]),
             confidence: .experimental),
        Rule(id: "handoff.off", title: "Handoff выключен",
             detail: "Mac не объявляет и не принимает «продолжить на другом устройстве».",
             breaks: "Handoff и универсальный буфер обмена с iPhone", surface: .messages, level: .settings,
             action: .prefs([PrefKey(domain: "com.apple.coreservices.useractivityd", key: "ActivityAdvertisingAllowed", value: .bool(false), currentHost: true),
                             PrefKey(domain: "com.apple.coreservices.useractivityd", key: "ActivityReceivingAllowed", value: .bool(false), currentHost: true)], restart: []),
             condition: .noIPhone),
        Rule(id: "icloud.signout", title: "Выход из Apple Account",
             detail: "Block открывает Apple Account в настройках: «Выйти». Делайте это, только если не нужен «Найти Mac».",
             breaks: "iCloud, «Найти Mac», Activation Lock, покупки", surface: .iCloud, level: .settings,
             action: .pane(url: "x-apple.systempreferences:com.apple.systempreferences.AppleIDSettings",
                           hint: "Нажмите «Выйти» внизу панели Apple Account, затем вернитесь в Block и нажмите «Сделано»."),
             condition: .noICloud),
    ]

    // MARK: 2. Программы по умолчанию

    static let defaults: [Rule] = [
        Rule(id: "default.browser", title: "Ссылки открывает Ward",
             detail: "Браузер по умолчанию — Ward. macOS один раз спросит подтверждение.",
             breaks: "", surface: .safari, level: .defaults,
             action: .handlers([Handler(kind: .scheme, value: "http"), Handler(kind: .scheme, value: "https")], app: .ward)),
        Rule(id: "default.video", title: "Видео открывает Lumen",
             detail: "MP4, MOV и MKV — в Lumen, а не в QuickTime или TV. macOS спросит подтверждение для каждого типа.",
             breaks: "", surface: .media, level: .defaults,
             action: .handlers(["mp4", "mov", "mkv"].map { Handler(kind: .fileExtension, value: $0) }, app: .lumen)),
        Rule(id: "default.audio", title: "Музыку открывает Lumen",
             detail: "MP3, M4A и FLAC — в Lumen, а не в «Музыке». macOS спросит подтверждение для каждого типа.",
             breaks: "", surface: .media, level: .defaults,
             action: .handlers(["mp3", "m4a", "flac"].map { Handler(kind: .fileExtension, value: $0) }, app: .lumen)),
        Rule(id: "default.torrent", title: "Торренты и magnet — в Swarm",
             detail: "Файлы .torrent и ссылки magnet:. macOS спросит подтверждение.",
             breaks: "", surface: .appStore, level: .defaults,
             action: .handlers([Handler(kind: .fileExtension, value: "torrent"), Handler(kind: .scheme, value: "magnet")], app: .swarm)),
    ]

    // MARK: 3. Сторож запуска

    static func launchGuard(_ id: String, _ name: String, _ bundle: String, _ surface: Surface, replacement: CatalogApp? = nil,
                            breaks: String = "", condition: Condition? = nil, confidence: Confidence = .solid) -> Rule {
        Rule(id: "guard." + id, title: "«\(name)» не запускается",
             detail: replacement.map { "Вместо неё открывается \($0.name)." } ?? "Программа закрывается в момент запуска.",
             breaks: breaks, surface: surface, level: .launchGuard,
             action: .launchGuard(bundleID: bundle, replacement: replacement), confidence: confidence, condition: condition)
    }

    static let guards: [Rule] = [
        launchGuard("appstore", "App Store", "com.apple.AppStore", .appStore, replacement: .harbor, breaks: "покупки и обновления из App Store"),
        launchGuard("music", "Музыка", "com.apple.Music", .media, replacement: .lumen, breaks: "Apple Music"),
        launchGuard("tv", "TV", "com.apple.TV", .media, replacement: .lumen, breaks: "Apple TV+"),
        launchGuard("podcasts", "Подкасты", "com.apple.podcasts", .media),
        launchGuard("news", "News", "com.apple.news", .news),
        launchGuard("stocks", "Акции", "com.apple.stocks", .news),
        launchGuard("books", "Книги", "com.apple.iBooksX", .news, breaks: "Apple Books"),
        launchGuard("tips", "Советы", "com.apple.tips", .home),
        launchGuard("home", "Дом", "com.apple.Home", .home, breaks: "управление HomeKit"),
        launchGuard("games", "Игры", "com.apple.games", .games, breaks: "Game Center"),
        launchGuard("siri", "Siri", "com.apple.Siri", .siri, replacement: .utter),
        launchGuard("playground", "Image Playground", "com.apple.GenerativePlaygroundApp", .intelligence, replacement: .synapse, confidence: .experimental),
        launchGuard("messages", "Сообщения", "com.apple.MobileSMS", .messages, breaks: "iMessage и SMS с iPhone", condition: .noIPhone),
        launchGuard("facetime", "FaceTime", "com.apple.FaceTime", .messages, breaks: "звонки FaceTime", condition: .noIPhone),
    ]

    /// Программы, которые сторож закрывает: для Dock и экрана «Программы».
    static var guardedBundles: [String] {
        guards.compactMap(\.action.guardedBundle)
    }

    static let dock = Rule(id: "dock.clean", title: "Программы Apple убраны из Dock",
                           detail: "Значки программ, которые сторож не пускает. Возврат — на те же места.",
                           breaks: "", surface: .appStore, level: .launchGuard,
                           action: .dock(bundleIDs: guards.compactMap(\.action.guardedBundle)))

    // MARK: 4. Фоновые службы

    static func agent(_ label: String, _ title: String, _ detail: String, _ surface: Surface, breaks: String = "",
                      condition: Condition? = nil, confidence: Confidence = .solid) -> Rule {
        Rule(id: "agent." + label, title: title, detail: detail + " (\(label))", breaks: breaks, surface: surface, level: .services,
             action: .agent(label: label), confidence: confidence, condition: condition)
    }

    static let services: [Rule] = [
        agent("com.apple.tipsd", "Подсказки «Советов»", "Уведомления «Советов» о новых функциях.", .home),
        agent("com.apple.gamed", "Game Center", "Вход в Game Center и уведомления о друзьях.", .games, breaks: "Game Center в играх"),
        agent("com.apple.newsd", "Служба News", "Фоновая загрузка ленты News.", .news),
        agent("com.apple.ap.promotedcontentd", "Продвигаемый контент", "Рекламные блоки Apple в App Store и News.", .ads),
        agent("com.apple.ap.adprivacyd", "Рекламная атрибуция", "Учёт показов рекламы Apple.", .ads, confidence: .experimental),
        agent("com.apple.parsecd", "Подсказки Siri в поиске", "Отправка запросов Spotlight на серверы Apple.", .search, breaks: "подсказки Apple в поиске"),
        agent("com.apple.assistantd", "Фоновая Siri", "Служба Siri.", .siri, confidence: .experimental),
        agent("com.apple.itunescloudd", "Медиатека iCloud", "Синхронизация медиатеки Apple Music.", .media, breaks: "медиатека iCloud в «Музыке»"),
        agent("com.apple.AMPDeviceDiscoveryAgent", "Поиск iPhone для «Музыки»", "Поиск устройств для синхронизации медиатеки.", .media, condition: .noIPhone),
        agent("com.apple.photoanalysisd", "Анализ фото для «Воспоминаний»", "Разбор фотографий для подборок «Фото».", .photos, breaks: "«Воспоминания» и поиск по лицам в «Фото»", confidence: .experimental),
        agent("com.apple.homed", "Служба «Дома»", "HomeKit.", .home, breaks: "управление HomeKit", confidence: .experimental),
        agent("com.apple.financed", "Apple Card и Wallet", "Финансовая служба Apple.", .ads, confidence: .experimental),
        agent("com.apple.appstoreagent", "Служба App Store", "Проверка обновлений и покупок App Store.", .appStore, breaks: "обновления программ из App Store"),
        agent("com.apple.bird", "iCloud Drive", "Синхронизация iCloud Drive.", .iCloud, breaks: "iCloud Drive", condition: .noICloud, confidence: .experimental),
    ]

    // MARK: 5. Сеть

    static let network: [Rule] = AppleNetwork.groups.map { group in
        let surface: Surface = switch group.id {
        case "appstore": .appStore
        case "icloud": .iCloud
        case "siri": .siri
        case "news": .news
        default: .ads
        }
        return Rule(id: "net." + group.id, title: "Адреса: \(group.title)", detail: group.detail,
                    breaks: group.id == "icloud" ? "iCloud и «Найти Mac»" : group.id == "appstore" ? "App Store" : "",
                    surface: surface, level: .network, action: .network(group),
                    condition: group.id == "icloud" ? .noICloud : nil)
    }
}
