import Foundation

/// Каталог известных альтернативных прошивок. Для проектов с открытым API
/// приложение само находит сборки под устройство, для остальных — ведёт на
/// страницу загрузки и ставит скачанный пользователем ZIP.
enum ROMCatalog {
    static let all: [ROMProject] = [
        ROMProject(
            id: "lineageos",
            name: "LineageOS",
            tagline: "Самая популярная открытая прошивка",
            summary: "Наследница CyanogenMod. Чистый Android без лишних приложений, регулярные обновления безопасности и поддержка сотен устройств. Сервисы Google по желанию устанавливаются отдельно.",
            symbol: "drop.halffull",
            gradient: [0x167C80, 0x34C3A9],
            website: URL(string: "https://lineageos.org")!,
            basedOn: "AOSP",
            features: ["Ежемесячные патчи безопасности", "Встроенный trust-центр", "Профили и гибкая настройка", "Официальный recovery"],
            tags: [.stock, .privacy],
            source: .lineageAPI
        ),
        ROMProject(
            id: "crdroid",
            name: "crDroid",
            tagline: "LineageOS с огромным количеством настроек",
            summary: "Основан на LineageOS и добавляет десятки опций кастомизации: строка состояния, жесты, тонкая настройка экрана блокировки и анимаций.",
            symbol: "slider.horizontal.3",
            gradient: [0x5B2C83, 0xC86DD7],
            website: URL(string: "https://crdroid.net")!,
            basedOn: "LineageOS",
            features: ["Кастомизация интерфейса", "Ежемесячные сборки", "Поддержка GApps", "Большое сообщество"],
            tags: [.features],
            source: .otaJSON(templates: [
                "https://raw.githubusercontent.com/crdroidandroid/android_vendor_crDroidOTA/16.0/{codename}.json",
                "https://raw.githubusercontent.com/crdroidandroid/android_vendor_crDroidOTA/15.0/{codename}.json",
                "https://raw.githubusercontent.com/crdroidandroid/android_vendor_crDroidOTA/14.0/{codename}.json",
            ])
        ),
        ROMProject(
            id: "evolutionx",
            name: "Evolution X",
            tagline: "Опыт Pixel на любом устройстве",
            summary: "Повторяет интерфейс и функции Google Pixel, добавляя собственные настройки. Сервисы Google уже встроены.",
            symbol: "sparkles",
            gradient: [0x1F4FD8, 0x56CCF2],
            website: URL(string: "https://evolution-x.org")!,
            basedOn: "LineageOS / AOSP",
            features: ["Pixel-лаунчер и обои", "Встроенные GApps", "Pixel-функции", "Тонкая настройка"],
            tags: [.pixel, .features],
            source: .otaJSON(templates: [
                "https://raw.githubusercontent.com/Evolution-X/OTA/bka/builds/{codename}.json",
                "https://raw.githubusercontent.com/Evolution-X/OTA/vic/builds/{codename}.json",
                "https://raw.githubusercontent.com/Evolution-X/OTA/udc/builds/{codename}.json",
            ])
        ),
        ROMProject(
            id: "grapheneos",
            name: "GrapheneOS",
            tagline: "Максимальная защита и приватность",
            summary: "Укреплённая ОС для Google Pixel: изоляция приложений, защищённый аллокатор памяти, отказ от сервисов Google с возможностью запуска Play в песочнице. Поддерживает повторную блокировку загрузчика.",
            symbol: "lock.shield.fill",
            gradient: [0x1C1C1E, 0x5E5CE6],
            website: URL(string: "https://grapheneos.org")!,
            basedOn: "AOSP",
            features: ["Песочница для Google Play", "Повторная блокировка загрузчика", "Усиленная защита памяти", "Контроль сети и датчиков"],
            tags: [.privacy, .degoogled],
            source: .grapheneOS
        ),
        ROMProject(
            id: "eos",
            name: "/e/OS",
            tagline: "Жизнь без Google",
            summary: "Прошивка от e Foundation на базе LineageOS с microG вместо сервисов Google, собственным облаком Murena и магазином App Lounge.",
            symbol: "leaf.fill",
            gradient: [0x0B6E4F, 0x7BD389],
            website: URL(string: "https://e.foundation")!,
            basedOn: "LineageOS + microG",
            features: ["microG вместо Google", "Облако Murena", "App Lounge", "Оценка приватности приложений"],
            tags: [.degoogled, .privacy],
            source: .eos
        ),
        ROMProject(
            id: "lineage-microg",
            name: "LineageOS for microG",
            tagline: "LineageOS + microG из коробки",
            summary: "Неофициальная сборка LineageOS с предустановленным microG и F-Droid — push-уведомления и геолокация работают без сервисов Google.",
            symbol: "drop.degreesign",
            gradient: [0x0E5E6F, 0x3A8891],
            website: URL(string: "https://lineage.microg.org")!,
            basedOn: "LineageOS",
            features: ["microG", "F-Droid", "Подписанные сборки", "Обновления по воздуху"],
            tags: [.degoogled, .stock],
            source: .directoryIndex(template: "https://download.lineage.microg.org/{codename}/")
        ),
        ROMProject(
            id: "calyxos",
            name: "CalyxOS",
            tagline: "Приватность без компромиссов в удобстве",
            summary: "ОС от Calyx Institute для Pixel, Fairphone и Motorola: microG по желанию, встроенный VPN, Datura firewall. Есть собственный установщик.",
            symbol: "shield.lefthalf.filled",
            gradient: [0x0F3057, 0x00587A],
            website: URL(string: "https://calyxos.org")!,
            basedOn: "AOSP",
            features: ["Datura firewall", "Опциональный microG", "Блокировка загрузчика", "Seedvault бэкапы"],
            tags: [.privacy, .degoogled],
            source: .manual(guide: "https://calyxos.org/install/devices/{codename}/")
        ),
        ROMProject(
            id: "iodeos",
            name: "iodéOS",
            tagline: "Встроенный блокировщик рекламы и трекеров",
            summary: "Французская прошивка на базе LineageOS с системным блокировщиком трекеров, microG и вниманием к удобству.",
            symbol: "hand.raised.fill",
            gradient: [0x3D5AFE, 0x00B0FF],
            website: URL(string: "https://iode.tech")!,
            basedOn: "LineageOS + microG",
            features: ["Системный блокировщик трекеров", "microG", "Ежемесячные обновления"],
            tags: [.privacy, .degoogled],
            source: .manual(guide: "https://iode.tech/iodeos-installation/")
        ),
        ROMProject(
            id: "pixelos",
            name: "PixelOS",
            tagline: "Стабильный Pixel-опыт без излишеств",
            summary: "Максимально близко к стоковой прошивке Pixel: минимум модификаций, встроенные GApps и акцент на стабильность.",
            symbol: "circle.hexagongrid.fill",
            gradient: [0x4285F4, 0x34A853],
            website: URL(string: "https://pixelos.net")!,
            basedOn: "AOSP",
            features: ["Встроенные GApps", "Pixel-функции", "Стабильность"],
            tags: [.pixel, .stock],
            source: .manual(guide: "https://pixelos.net/download/{codename}")
        ),
        ROMProject(
            id: "risingos",
            name: "RisingOS",
            tagline: "Кастомизация в стиле Pixel и One UI",
            summary: "Яркая прошивка с большим количеством визуальных настроек, виджетами и вариантами с GApps и без.",
            symbol: "sun.max.fill",
            gradient: [0xF2994A, 0xEB5757],
            website: URL(string: "https://sourceforge.net/projects/risingos-official/")!,
            basedOn: "LineageOS",
            features: ["Темы и стили", "Варианты Vanilla / GApps", "Pixel-функции"],
            tags: [.features, .pixel],
            source: .manual(guide: "https://sourceforge.net/projects/risingos-official/files/")
        ),
        ROMProject(
            id: "paranoid",
            name: "Paranoid Android",
            tagline: "Продуманный дизайн и плавность",
            summary: "Один из старейших проектов, известный аккуратным дизайном и оптимизацией под Snapdragon.",
            symbol: "circle.dotted.circle",
            gradient: [0x232526, 0x8E9EAB],
            website: URL(string: "https://paranoidandroid.co")!,
            basedOn: "CodeAurora / AOSP",
            features: ["Оптимизация Qualcomm", "Минималистичный дизайн", "OTA-обновления"],
            tags: [.stock],
            source: .manual(guide: "https://paranoidandroid.co/{codename}")
        ),
        ROMProject(
            id: "ubuntu-touch",
            name: "Ubuntu Touch",
            tagline: "Мобильный Linux от UBports",
            summary: "Не Android: полноценная Linux-система для смартфонов с собственным интерфейсом Lomiri и поддержкой Android-приложений через Waydroid.",
            symbol: "terminal.fill",
            gradient: [0xE95420, 0x772953],
            website: URL(string: "https://ubports.com")!,
            basedOn: "Ubuntu",
            features: ["Интерфейс Lomiri", "Конвергенция", "Waydroid"],
            tags: [.linux, .degoogled],
            source: .manual(guide: "https://devices.ubuntu-touch.io/device/{codename}")
        ),
        ROMProject(
            id: "postmarketos",
            name: "postmarketOS",
            tagline: "Настоящий Linux на годы вперёд",
            summary: "Дистрибутив на базе Alpine Linux с цельной поддержкой старых устройств и выбором оболочек: Phosh, Plasma Mobile, SXMO.",
            symbol: "cube.transparent",
            gradient: [0x009900, 0x66CC33],
            website: URL(string: "https://postmarketos.org")!,
            basedOn: "Alpine Linux",
            features: ["Phosh / Plasma Mobile", "Mainline-ядро", "Долгая поддержка"],
            tags: [.linux, .degoogled],
            source: .manual(guide: "https://wiki.postmarketos.org/index.php?search={codename}")
        ),
    ]
}
