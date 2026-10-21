import CoreServices
import Foundation
import Security

/// Программа на диске со всем, что нужно, чтобы безошибочно найти её следы.
nonisolated struct AppBundle: Identifiable, Hashable, Sendable {
    var id: String { url.path }
    var url: URL
    var name: String
    var bundleID: String
    var version: String?
    var executable: String?
    /// Идентификаторы вложенных помощников, расширений и служб — их данные тоже принадлежат программе.
    var helperIDs: [String]
    var teamID: String?
    /// Группы приложений из подписи (общие контейнеры в ~/Library/Group Containers).
    var appGroups: [String]
    var isApple: Bool
    var isSystem: Bool
    var isAppStore: Bool
    var architectures: [String]

    var allIDs: [String] { [bundleID] + helperIDs }
    var isIntelOnly: Bool { !architectures.isEmpty && !architectures.contains("arm64") && !architectures.contains("arm64e") }
}

/// Все программы, которые есть на Mac. По нему решаем, «чьи» это файлы и остались ли хозяева.
nonisolated struct InstalledIndex: Sendable {
    var apps: [AppBundle] = []
    private(set) var ids: Set<String> = []
    private(set) var names: Set<String> = []
    private(set) var groups: [String: Int] = [:]
    private(set) var productPrefixes: Set<String> = []

    init(apps: [AppBundle]) {
        self.apps = apps
        for app in apps {
            for id in app.allIDs {
                let l = id.lowercased()
                ids.insert(l)
                productPrefixes.insert(Self.prefix3(l))
            }
            names.insert(Self.normalize(app.name))
            if let exe = app.executable { names.insert(Self.normalize(exe)) }
            for g in app.appGroups { groups[g.lowercased(), default: 0] += 1 }
        }
    }

    init() {}

    static func normalize(_ s: String) -> String {
        s.lowercased().filter { $0.isLetter || $0.isNumber }
    }

    /// «com.vendor.product» — первые три компонента идентификатора.
    static func prefix3(_ id: String) -> String {
        id.split(separator: ".").prefix(3).joined(separator: ".")
    }

    /// Похоже ли имя на идентификатор программы: минимум три компонента, только буквы, цифры и дефисы.
    static func looksLikeBundleID(_ s: String) -> Bool {
        let parts = s.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count >= 3, parts.allSatisfy({ !$0.isEmpty }) else { return false }
        return parts.allSatisfy { $0.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" } }
    }

    /// Есть ли у этих данных живой хозяин. Осторожная проверка: совпадение идентификатора,
    /// вложенность (помощник ↔ программа) или тот же «продукт» (первые три компонента).
    func owns(_ id: String, useLaunchServices: Bool) -> Bool {
        let l = id.lowercased()
        if ids.contains(l) || productPrefixes.contains(Self.prefix3(l)) { return true }
        for known in ids where l.hasPrefix(known + ".") || known.hasPrefix(l + ".") { return true }
        if useLaunchServices, Self.launchServicesKnows(id) { return true }
        return false
    }

    func isInstalledName(_ name: String) -> Bool { names.contains(Self.normalize(name)) }

    func app(withID id: String) -> AppBundle? {
        let l = id.lowercased()
        return apps.first { $0.allIDs.contains { $0.lowercased() == l } }
    }

    func sharedGroupCount(_ group: String) -> Int { groups[group.lowercased()] ?? 0 }

    /// Launch Services знает программы и вне стандартных папок (например, на внешнем диске).
    static func launchServicesKnows(_ id: String) -> Bool {
        guard let urls = LSCopyApplicationURLsForBundleIdentifier(id as CFString, nil)?.takeRetainedValue() as? [URL] else {
            return false
        }
        return !urls.isEmpty
    }
}

nonisolated enum AppScanner {
    /// Стандартные места, где живут программы.
    static func defaultDirectories(home: URL) -> [URL] {
        [URL(fileURLWithPath: "/Applications"), home.appendingPathComponent("Applications"),
         URL(fileURLWithPath: "/System/Applications"), URL(fileURLWithPath: "/System/Library/CoreServices")]
    }

    /// Ищет .app на двух уровнях вложенности (папки вроде «Utilities» или «Microsoft Office»).
    static func scan(_ dirs: [URL], signing: Bool = true) -> [AppBundle] {
        var urls: [URL] = []
        func walk(_ dir: URL, depth: Int) {
            for child in DiskUsage.children(of: dir) {
                if child.pathExtension == "app" {
                    urls.append(child)
                } else if depth < 2, DiskUsage.isDirectory(child), child.pathExtension.isEmpty {
                    walk(child, depth: depth + 1)
                }
            }
        }
        for dir in dirs { walk(dir, depth: 0) }

        let lock = NSLock()
        var result: [AppBundle] = []
        DispatchQueue.concurrentPerform(iterations: urls.count) { i in
            guard let app = read(urls[i], signing: signing) else { return }
            lock.withLock { result.append(app) }
        }
        var seen = Set<String>()
        return result
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            .filter { seen.insert($0.url.path).inserted }
    }

    static func read(_ url: URL, signing: Bool = true) -> AppBundle? {
        guard let info = NSDictionary(contentsOf: url.appendingPathComponent("Contents/Info.plist")) as? [String: Any],
              let id = info["CFBundleIdentifier"] as? String else { return nil }
        let name = (info["CFBundleDisplayName"] as? String) ?? (info["CFBundleName"] as? String)
            ?? url.deletingPathExtension().lastPathComponent
        let path = url.path
        let isSystem = path.hasPrefix("/System/")
        var team: String?
        var groups: [String] = []
        if signing, !isSystem { (team, groups) = signingInfo(url) }
        let exe = info["CFBundleExecutable"] as? String
        return AppBundle(
            url: url, name: url.deletingPathExtension().lastPathComponent.isEmpty ? name : url.deletingPathExtension().lastPathComponent,
            bundleID: id, version: (info["CFBundleShortVersionString"] as? String) ?? (info["CFBundleVersion"] as? String),
            executable: exe, helperIDs: helperIDs(url), teamID: team, appGroups: groups,
            isApple: id.hasPrefix("com.apple."), isSystem: isSystem,
            isAppStore: FileManager.default.fileExists(atPath: url.appendingPathComponent("Contents/_MASReceipt/receipt").path),
            architectures: exe.map { architectures(url.appendingPathComponent("Contents/MacOS/\($0)")) } ?? [])
    }

    /// Вложенные программы, расширения и XPC-службы.
    static func helperIDs(_ app: URL) -> [String] {
        var ids: [String] = []
        let places = ["Contents/Library/LoginItems", "Contents/Library/LaunchServices", "Contents/PlugIns", "Contents/Helpers",
                      "Contents/XPCServices", "Contents/Library/SystemExtensions", "Contents/Frameworks"]
        for place in places {
            for child in DiskUsage.children(of: app.appendingPathComponent(place)) {
                let ext = child.pathExtension
                if ["app", "appex", "xpc", "systemextension"].contains(ext) {
                    if let d = NSDictionary(contentsOf: child.appendingPathComponent("Contents/Info.plist")),
                       let id = d["CFBundleIdentifier"] as? String { ids.append(id) }
                } else if place == "Contents/Library/LaunchServices", InstalledIndex.looksLikeBundleID(child.lastPathComponent) {
                    ids.append(child.lastPathComponent) // привилегированный помощник называется своим идентификатором
                }
            }
        }
        return Array(Set(ids)).sorted()
    }

    /// Команда разработчика и группы приложений — из подписи.
    static func signingInfo(_ url: URL) -> (String?, [String]) {
        var code: SecStaticCode?
        guard SecStaticCodeCreateWithPath(url as CFURL, [], &code) == errSecSuccess, let code else { return (nil, []) }
        var info: CFDictionary?
        guard SecCodeCopySigningInformation(code, SecCSFlags(rawValue: kSecCSSigningInformation), &info) == errSecSuccess,
              let dict = info as? [String: Any] else { return (nil, []) }
        let team = dict[kSecCodeInfoTeamIdentifier as String] as? String
        let entitlements = dict[kSecCodeInfoEntitlementsDict as String] as? [String: Any]
        let groups = (entitlements?["com.apple.security.application-groups"] as? [String]) ?? []
        return (team, groups)
    }

    /// Архитектуры исполняемого файла из заголовка Mach-O (без запуска lipo).
    static func architectures(_ exe: URL) -> [String] {
        guard let h = try? FileHandle(forReadingFrom: exe) else { return [] }
        defer { try? h.close() }
        guard let head = try? h.read(upToCount: 4096), head.count >= 8 else { return [] }
        let bytes = [UInt8](head)
        func be32(_ o: Int) -> UInt32 { UInt32(bytes[o]) << 24 | UInt32(bytes[o + 1]) << 16 | UInt32(bytes[o + 2]) << 8 | UInt32(bytes[o + 3]) }
        func le32(_ o: Int) -> UInt32 { UInt32(bytes[o]) | UInt32(bytes[o + 1]) << 8 | UInt32(bytes[o + 2]) << 16 | UInt32(bytes[o + 3]) << 24 }
        func name(_ cpu: UInt32) -> String {
            switch cpu {
            case 0x0100000C: "arm64"
            case 0x01000007: "x86_64"
            case 7: "i386"
            default: "other"
            }
        }
        let magic = be32(0)
        if magic == 0xCAFEBABE || magic == 0xCAFEBABF {
            let count = Int(be32(4))
            let stride = magic == 0xCAFEBABF ? 32 : 20
            var archs: [String] = []
            for i in 0..<min(count, 8) where 8 + i * stride + 4 <= bytes.count {
                archs.append(name(be32(8 + i * stride)))
            }
            return archs
        }
        if le32(0) == 0xFEEDFACF || le32(0) == 0xFEEDFACE { return [name(le32(4))] }
        return []
    }

    /// Когда программу последний раз открывали (по данным Spotlight).
    static func lastUsed(_ url: URL) -> Date? {
        guard let item = MDItemCreateWithURL(nil, url as CFURL),
              let date = MDItemCopyAttribute(item, kMDItemLastUsedDate) as? Date else { return nil }
        return date
    }
}
