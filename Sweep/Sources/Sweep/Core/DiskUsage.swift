import Foundation

/// Размеры на диске. Считаем занятое место (allocated), а не логический размер: для разреженных
/// и сжатых файлов это честная оценка того, сколько освободится.
nonisolated enum DiskUsage {
    nonisolated struct Summary: Sendable {
        var size: Int64 = 0
        var files = 0
        /// Самое свежее изменение внутри — чтобы не трогать то, чем пользуются прямо сейчас.
        var newest: Date?
    }

    static let keys: [URLResourceKey] = [.totalFileAllocatedSizeKey, .fileAllocatedSizeKey, .isRegularFileKey,
                                         .isDirectoryKey, .isSymbolicLinkKey, .contentModificationDateKey]

    /// Рекурсивно, без перехода по символическим ссылкам.
    static func summary(of url: URL, filter: ((URL, URLResourceValues) -> Bool)? = nil) -> Summary {
        var s = Summary()
        guard let values = try? url.resourceValues(forKeys: Set(keys)) else { return s }
        if values.isSymbolicLink == true || values.isDirectory != true {
            if filter == nil || filter!(url, values) {
                s.size = Int64(values.totalFileAllocatedSize ?? values.fileAllocatedSize ?? 0)
                s.files = 1
                s.newest = values.contentModificationDate
            }
            return s
        }
        s.newest = values.contentModificationDate
        guard let e = FileManager.default.enumerator(at: url, includingPropertiesForKeys: keys, options: [],
                                                     errorHandler: { _, _ in true }) else { return s }
        for case let file as URL in e {
            guard let v = try? file.resourceValues(forKeys: Set(keys)) else { continue }
            if v.isDirectory == true && v.isSymbolicLink != true { continue }
            if let filter, !filter(file, v) { continue }
            s.size += Int64(v.totalFileAllocatedSize ?? v.fileAllocatedSize ?? 0)
            s.files += 1
            if let m = v.contentModificationDate, m > (s.newest ?? .distantPast) { s.newest = m }
        }
        return s
    }

    static func size(of url: URL) -> Int64 { summary(of: url).size }

    /// Содержимое папки (без скрытых файлов вроде .DS_Store, если не попросили).
    static func children(of url: URL, hidden: Bool = false) -> [URL] {
        let options: FileManager.DirectoryEnumerationOptions = hidden ? [] : [.skipsHiddenFiles]
        return (try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: keys, options: options)) ?? []
    }

    static func exists(_ url: URL) -> Bool {
        var st = stat()
        return lstat(url.path, &st) == 0
    }

    static func isDirectory(_ url: URL) -> Bool {
        (try? url.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory == true
    }

    static func modified(_ url: URL) -> Date? {
        (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
    }
}

nonisolated enum Format {
    static func bytes(_ v: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: max(0, v), countStyle: .file)
    }

    static func relative(_ date: Date?, now: Date = Date()) -> String {
        guard let date else { return "—" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.unitsStyle = .full
        return f.localizedString(for: date, relativeTo: now)
    }

    static func date(_ date: Date?) -> String {
        guard let date else { return "—" }
        return date.formatted(.dateTime.day().month(.wide).year().locale(Locale(identifier: "ru_RU")))
    }

    /// «~/Library/Caches/…» — короче и понятнее полного пути.
    static func path(_ url: URL, home: URL) -> String {
        let p = url.path, h = home.path
        return p.hasPrefix(h + "/") ? "~" + p.dropFirst(h.count) : p
    }
}

extension Date {
    nonisolated func daysBefore(_ other: Date) -> Double { other.timeIntervalSince(self) / 86_400 }
}
