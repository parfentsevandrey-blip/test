import SwiftUI

/// Проект альтернативной прошивки (LineageOS, crDroid, GrapheneOS…).
struct ROMProject: Identifiable, Hashable, Sendable {
    enum Tag: String, CaseIterable, Sendable {
        case privacy = "Приватность"
        case degoogled = "Без Google"
        case features = "Много функций"
        case pixel = "Pixel-стиль"
        case stock = "Чистый Android"
        case linux = "Linux"

        var symbol: String {
            switch self {
            case .privacy: "lock.shield"
            case .degoogled: "eye.slash"
            case .features: "slider.horizontal.3"
            case .pixel: "sparkles"
            case .stock: "leaf"
            case .linux: "terminal"
            }
        }
    }

    let id: String
    let name: String
    let tagline: String
    let summary: String
    let symbol: String
    let gradient: [UInt32]
    let website: URL
    let basedOn: String
    let features: [String]
    let tags: [Tag]
    let source: ROMSource

    var colors: [Color] { gradient.map { Color(hex: $0) } }
}

/// Откуда приложение узнаёт о сборках прошивки для конкретного устройства.
enum ROMSource: Hashable, Sendable {
    /// Официальный API download.lineageos.org.
    case lineageAPI
    /// JSON формата LineageOS Updater (crDroid, Evolution X и др.). `{codename}` подставляется.
    case otaJSON(templates: [String])
    /// releases.grapheneos.org — заводские образы с flash-all.sh.
    case grapheneOS
    /// API /e/OS (images.ecloud.global): автоматическая установка с recovery проекта.
    case eos
    /// Каталог-листинг с ZIP'ами (LineageOS for microG). `{codename}` подставляется;
    /// recovery берётся от LineageOS того же устройства.
    case directoryIndex(template: String)
    /// Сборки только на сайте проекта. Приложение сперва проверяет, поддерживается ли
    /// устройство (страница существует), и лишь тогда предлагает ссылку и установку из файла.
    case manual(guide: String)
}

enum BuildKind: Hashable, Sendable {
    /// ZIP, устанавливаемый через recovery и `adb sideload`.
    case sideloadZip
    /// Архив заводских образов со скриптом flash-all.sh.
    case factoryImage
}

struct BuildFile: Hashable, Sendable, Identifiable {
    var fileName: String
    var url: URL
    var size: Int64?
    var sha256: String?
    var md5: String?

    var id: String { url.absoluteString }
    /// Имя раздела для fastboot: boot.img → boot.
    var partition: String { (fileName as NSString).deletingPathExtension }
}

struct ROMBuild: Identifiable, Hashable, Sendable {
    var romID: String
    var version: String
    var androidVersion: String?
    var date: Date?
    var channel: String?
    var kind: BuildKind
    var package: BuildFile
    /// Образы для первичной установки recovery (boot, dtbo, vendor_boot, recovery…).
    var recoveryImages: [BuildFile] = []
    var recoveryImagesSource: String?
    var notesURL: URL?
    var maintainer: String?

    var id: String { package.id }
}

enum ROMAvailability: Hashable, Sendable {
    case checking
    case available([ROMBuild])
    case notFound
    case manual(URL)
    case failed(String)

    var builds: [ROMBuild] {
        if case .available(let builds) = self { return builds }
        return []
    }

    var isAvailable: Bool { !builds.isEmpty }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
