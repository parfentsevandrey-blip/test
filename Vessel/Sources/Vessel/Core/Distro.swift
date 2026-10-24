import SwiftUI

/// Семейство дистрибутивов — «от кого происходит» система: пакеты, привычки и документация общие.
nonisolated enum Family: String, CaseIterable, Identifiable, Sendable {
    case debian, redhat, arch, suse, independent

    var id: String { rawValue }

    var title: String {
        switch self {
        case .debian: "Debian и Ubuntu"
        case .redhat: "Fedora и Red Hat"
        case .arch: "Arch"
        case .suse: "SUSE"
        case .independent: "Независимые"
        }
    }

    var short: String {
        switch self {
        case .debian: "Debian"
        case .redhat: "Red Hat"
        case .arch: "Arch"
        case .suse: "SUSE"
        case .independent: "Свои корни"
        }
    }

    var about: String {
        switch self {
        case .debian: "Пакеты .deb и apt. Самое большое семейство: всё, что написано «для Ubuntu», работает и здесь."
        case .redhat: "Пакеты .rpm и dnf. Fedora — передний край, Rocky, Alma и CentOS — серверная надёжность на годы."
        case .arch: "Pacman и «скользящие» выпуски: система обновляется каждый день и никогда не устаревает."
        case .suse: "Пакеты .rpm и zypper, снимки Btrfs перед каждым обновлением и YaST для настройки."
        case .independent: "Свои менеджеры пакетов и свои идеи: минимализм, воспроизводимость, сборка из исходников."
        }
    }

    var symbol: String {
        switch self {
        case .debian: "shippingbox.fill"
        case .redhat: "hat.widebrim.fill"
        case .arch: "triangle.fill"
        case .suse: "leaf.fill"
        case .independent: "sparkles"
        }
    }
}

/// Фильтры каталога. У дистрибутива их может быть несколько.
nonisolated enum Tag: String, CaseIterable, Identifiable, Sendable {
    case light, eyeCandy, privacy, beginner, developer, security, server, immutable, expert, rolling, gaming

    var id: String { rawValue }

    var title: String {
        switch self {
        case .light: "Быстрые и лёгкие"
        case .eyeCandy: "Красивые эффекты"
        case .privacy: "Для приватности"
        case .beginner: "Для новичков"
        case .developer: "Для разработчиков"
        case .security: "Безопасность и пентест"
        case .server: "Серверы"
        case .immutable: "Неизменяемые"
        case .expert: "Для опытных"
        case .rolling: "Самые свежие пакеты"
        case .gaming: "Игры"
        }
    }

    var symbol: String {
        switch self {
        case .light: "hare.fill"
        case .eyeCandy: "sparkles"
        case .privacy: "eye.slash.fill"
        case .beginner: "hand.wave.fill"
        case .developer: "chevron.left.forwardslash.chevron.right"
        case .security: "shield.lefthalf.filled"
        case .server: "server.rack"
        case .immutable: "lock.square.stack.fill"
        case .expert: "wrench.and.screwdriver.fill"
        case .rolling: "arrow.triangle.2.circlepath"
        case .gaming: "gamecontroller.fill"
        }
    }

    var tint: Color {
        switch self {
        case .light: .green
        case .eyeCandy: .pink
        case .privacy: .purple
        case .beginner: .orange
        case .developer: .blue
        case .security: .red
        case .server: .gray
        case .immutable: .teal
        case .expert: .brown
        case .rolling: .mint
        case .gaming: .indigo
        }
    }
}

/// Сколько машине нужно. Мастер установки предлагает это, но не больше половины ресурсов Mac.
nonisolated struct Resources: Sendable, Hashable, Codable {
    var cpu: Int
    var memoryGB: Int
    var diskGB: Int
}

/// Вариант дистрибутива: свой образ, своё окружение рабочего стола.
nonisolated struct Edition: Identifiable, Sendable {
    var id: String
    var title: String
    var desktop: String
    /// Примерный размер образа для подписи до того, как сервер назовёт точный.
    var sizeGB: Double
    /// Живой образ: систему можно попробовать до установки.
    var live: Bool
    var source: ImageSource
    var resources: Resources
    var note: String? = nil
}

nonisolated struct Facts: Sendable {
    var base: String
    var packages: String
    var releases: String
    var desktop: String
    var since: Int
    var origin: String
}

/// Дистрибутив в каталоге. Тексты — живым языком: в чём хорош, в чём слабее, для кого.
nonisolated struct Distro: Identifiable, Sendable {
    var id: String
    var name: String
    var tagline: String
    var summary: String
    var family: Family
    var tags: Set<Tag>
    var accent: (Double, Double, Double)
    /// Буквы на обложке — свои знаки вместо логотипов (логотипы — товарные знаки проектов).
    var monogram: String
    var pros: [String]
    var cons: [String]
    var audience: [String]
    var facts: Facts
    var editions: [Edition]
    /// Имена файлов на Wikimedia Commons (свободные лицензии).
    var screenshots: [String]
    var homepage: String
    /// Место в «Популярных» (1 — самый распространённый).
    var rank: Int
    /// Почему дистрибутив сейчас «горячий»; nil — не в подборке.
    var hot: String? = nil
    /// Для систем без образа под Apple Silicon: почему и что взять вместо.
    var unavailable: String? = nil
    var alternatives: [String] = []
    var installTips: [String] = []

    var color: Color { Color(red: accent.0, green: accent.1, blue: accent.2) }
    var runsHere: Bool { !editions.isEmpty }
    var defaultEdition: Edition? { editions.first }
}
