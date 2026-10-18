import Foundation

/// Устройство, которое видит adb или fastboot.
struct ConnectedDevice: Identifiable, Hashable, Sendable {
    enum Mode: String, Sendable {
        case android        // обычная загрузка, adb авторизован
        case unauthorized   // ждём подтверждения RSA-ключа на телефоне
        case offline
        case recovery
        case sideload
        case bootloader     // fastboot
        case fastbootd      // userspace fastboot
        case unknown

        var title: String {
            switch self {
            case .android: "Подключён"
            case .unauthorized: "Ждёт разрешения"
            case .offline: "Не отвечает"
            case .recovery: "Recovery"
            case .sideload: "Режим sideload"
            case .bootloader: "Fastboot"
            case .fastbootd: "Fastbootd"
            case .unknown: "Неизвестно"
            }
        }

        var isFastboot: Bool { self == .bootloader || self == .fastbootd }
    }

    let serial: String
    var mode: Mode
    /// Модель из `adb devices -l` (до авторизации больше ничего не известно).
    var model: String?
    var codenameHint: String?

    var id: String { serial }

    var displayName: String {
        model?.replacingOccurrences(of: "_", with: " ") ?? serial
    }
}

/// Полный набор характеристик, собранных через getprop / dumpsys / proc.
struct DeviceSpecs: Hashable, Sendable {
    var serial: String
    var manufacturer: String = ""
    var brand: String = ""
    var model: String = ""
    var marketName: String?
    var codename: String = ""
    var codenameCandidates: [String] = []

    var androidVersion: String = ""
    var sdkLevel: Int?
    var securityPatch: String?
    var buildID: String?
    var fingerprint: String?
    var romName: String?
    var kernel: String?

    var chipset: String?
    var cpuABI: String?
    var cpuCores: Int?
    var ramBytes: Int64?
    var storageTotalBytes: Int64?
    var storageFreeBytes: Int64?

    var screenResolution: String?
    var screenDensity: Int?

    var batteryLevel: Int?
    var batteryHealth: String?
    var batteryTemperature: Double?
    var batteryVoltage: Double?

    var bootloaderLocked: Bool?
    var oemUnlockAllowed: Bool?
    var verifiedBootState: String?
    var slotSuffix: String?
    var trebleEnabled: Bool?
    var dynamicPartitions: Bool?
    var vndkVersion: String?

    var displayName: String {
        if let marketName, !marketName.isEmpty { return marketName }
        let lowerModel = model.lowercased()
        if !brand.isEmpty, !lowerModel.hasPrefix(brand.lowercased()) {
            return "\(brand.capitalized) \(model)"
        }
        return model
    }

    var isSeamlessUpdate: Bool { !(slotSuffix ?? "").isEmpty }

    /// Производители, у которых путь установки отличается от стандартного fastboot.
    var vendorFamily: VendorFamily {
        VendorFamily(manufacturer: manufacturer, brand: brand)
    }
}

enum VendorFamily: Sendable {
    case google, samsung, xiaomi, oneplus, motorola, sony, fairphone, nothing, huawei, other

    init(manufacturer: String, brand: String) {
        let value = (manufacturer + " " + brand).lowercased()
        if value.contains("google") { self = .google }
        else if value.contains("samsung") { self = .samsung }
        else if value.contains("xiaomi") || value.contains("redmi") || value.contains("poco") { self = .xiaomi }
        else if value.contains("oneplus") { self = .oneplus }
        else if value.contains("motorola") || value.contains("moto") { self = .motorola }
        else if value.contains("sony") { self = .sony }
        else if value.contains("fairphone") { self = .fairphone }
        else if value.contains("nothing") { self = .nothing }
        else if value.contains("huawei") || value.contains("honor") { self = .huawei }
        else { self = .other }
    }

    /// Поддерживает ли автоматизация стандартный `fastboot flashing unlock`.
    var supportsStandardFastbootUnlock: Bool {
        switch self {
        case .google, .oneplus, .fairphone, .nothing, .other: true
        case .motorola, .sony, .xiaomi, .samsung, .huawei: false
        }
    }
}
