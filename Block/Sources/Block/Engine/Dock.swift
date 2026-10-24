import Foundation

/// Значки программ в Dock (`com.apple.dock persistent-apps`). Убранные значки запоминаются вместе с местом,
/// и возврат ставит их туда же, даже если вы потом что-то добавили.
nonisolated enum Dock {
    static var domain: CFString { "com.apple.dock" as CFString }
    static var key: CFString { "persistent-apps" as CFString }

    static func tiles() -> [[String: Any]] {
        CFPreferencesAppSynchronize(domain)
        return (CFPreferencesCopyAppValue(key, domain) as? [[String: Any]]) ?? []
    }

    static func bundleID(_ tile: [String: Any]) -> String? {
        (tile["tile-data"] as? [String: Any])?["bundle-identifier"] as? String
    }

    static func present(_ ids: Set<String>) -> [String] {
        tiles().compactMap(bundleID).filter(ids.contains)
    }

    /// Убрать значки; вернуть убранные с их местами (binary plist — для журнала).
    static func remove(_ ids: Set<String>) -> Data? {
        let all = tiles()
        var removed: [[String: Any]] = []
        var kept: [[String: Any]] = []
        for (i, tile) in all.enumerated() {
            if let id = bundleID(tile), ids.contains(id) { removed.append(["index": i, "tile": tile]) } else { kept.append(tile) }
        }
        guard !removed.isEmpty else { return nil }
        write(kept)
        return try? PropertyListSerialization.data(fromPropertyList: removed, format: .binary, options: 0)
    }

    /// Поставить убранные значки на прежние места.
    static func restore(_ data: Data?) {
        guard let data, let removed = (try? PropertyListSerialization.propertyList(from: data, format: nil)) as? [[String: Any]] else { return }
        var all = tiles()
        let present = Set(all.compactMap(bundleID))
        for entry in removed.sorted(by: { ($0["index"] as? Int ?? 0) < ($1["index"] as? Int ?? 0) }) {
            guard let tile = entry["tile"] as? [String: Any], let id = bundleID(tile), !present.contains(id) else { continue }
            all.insert(tile, at: min(entry["index"] as? Int ?? all.count, all.count))
        }
        write(all)
    }

    static func write(_ tiles: [[String: Any]]) {
        CFPreferencesSetAppValue(key, tiles as CFArray, domain)
        CFPreferencesAppSynchronize(domain)
        Shell.killall("Dock")
    }
}
