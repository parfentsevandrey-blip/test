import CryptoKit
import Foundation
import UniformTypeIdentifiers

nonisolated enum FileKind: String, CaseIterable, Sendable {
    case video, audio, image, archive, diskImage, document, app, virtualMachine, other

    var title: String {
        switch self {
        case .video: "Видео"
        case .audio: "Музыка и звук"
        case .image: "Изображения"
        case .archive: "Архивы"
        case .diskImage: "Образы дисков"
        case .document: "Документы"
        case .app: "Программы"
        case .virtualMachine: "Виртуальные машины"
        case .other: "Другое"
        }
    }

    var symbol: String {
        switch self {
        case .video: "film.fill"
        case .audio: "music.note"
        case .image: "photo.fill"
        case .archive: "archivebox.fill"
        case .diskImage: "opticaldiscdrive.fill"
        case .document: "doc.richtext.fill"
        case .app: "app.dashed"
        case .virtualMachine: "pc"
        case .other: "doc.fill"
        }
    }

    static func of(_ url: URL) -> FileKind {
        let ext = url.pathExtension.lowercased()
        if ["dmg", "iso", "img", "sparseimage", "sparsebundle", "ipsw", "xip", "pkg", "mpkg"].contains(ext) { return .diskImage }
        if ["pvm", "utm", "vmwarevm", "vdi", "vmdk", "qcow2", "vhd", "vhdx", "ova"].contains(ext) { return .virtualMachine }
        if ext == "app" { return .app }
        guard let type = UTType(filenameExtension: ext) else { return .other }
        if type.conforms(to: .movie) || type.conforms(to: .video) { return .video }
        if type.conforms(to: .audio) { return .audio }
        if type.conforms(to: .image) { return .image }
        if type.conforms(to: .archive) || ["zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst"].contains(ext) { return .archive }
        if type.conforms(to: .text) || type.conforms(to: .pdf) || type.conforms(to: .presentation)
            || type.conforms(to: .spreadsheet) || type.conforms(to: .compositeContent) { return .document }
        return .other
    }
}

nonisolated struct FileEntry: Identifiable, Hashable, Sendable {
    var id: String { url.path }
    let url: URL
    let size: Int64
    let modified: Date?
    let accessed: Date?
    let kind: FileKind

    /// Когда файлом пользовались последний раз (открывали или меняли).
    var lastUse: Date? { [modified, accessed].compactMap { $0 }.max() }
}

nonisolated struct DuplicateGroup: Identifiable, Sendable {
    let id: String
    var files: [FileEntry]
    var size: Int64 { files.first?.size ?? 0 }
    /// Сколько освободится, если оставить одну копию.
    var wasted: Int64 { size * Int64(max(0, files.count - 1)) }
}

nonisolated struct ClutterResult: Sendable {
    var large: [FileEntry] = []
    var old: [FileEntry] = []
    var duplicates: [DuplicateGroup] = []
    var scanned = 0
}

/// «Крупные и старые файлы» и «Дубликаты». Ищем только в личных папках, мимо ~/Library и медиатек.
nonisolated enum ClutterScanner {
    static let packageExtensions: Set<String> = ["app", "pvm", "utm", "vmwarevm", "sparsebundle", "xcarchive", "fcpbundle",
                                                 "logicx", "band", "imovielibrary", "rtfd", "pages", "numbers", "key"]

    static func roots(_ ctx: ScanContext) -> [URL] {
        let skip: Set<String> = ctx.avoidPrivacyPrompts
            ? ["Library", "Applications", "Desktop", "Documents", "Downloads", "Pictures", "Movies", "Music"]
            : ["Library", "Applications"]
        return DiskUsage.children(of: ctx.home).filter { url in
            !skip.contains(url.lastPathComponent) && DiskUsage.isDirectory(url)
        }
    }

    static func scan(_ ctx: ScanContext, largeThreshold: Int64 = 50_000_000, oldDays: Double = 365, duplicates: Bool = true,
                     progress: @Sendable (Int) -> Void = { _ in }) -> ClutterResult {
        var result = ClutterResult()
        var candidates: [FileEntry] = []
        var seenInodes = Set<String>()
        let keys: [URLResourceKey] = [.totalFileAllocatedSizeKey, .fileAllocatedSizeKey, .isRegularFileKey, .isPackageKey,
                                      .isDirectoryKey, .contentModificationDateKey, .contentAccessDateKey, .fileResourceIdentifierKey,
                                      .isSymbolicLinkKey]
        for root in roots(ctx) {
            guard let e = FileManager.default.enumerator(at: root, includingPropertiesForKeys: keys,
                                                         options: [.skipsHiddenFiles, .skipsPackageDescendants],
                                                         errorHandler: { _, _ in true }) else { continue }
            for case let url as URL in e {
                autoreleasepool {
                    guard let v = try? url.resourceValues(forKeys: Set(keys)), v.isSymbolicLink != true else { return }
                    if ctx.isIgnored(url) { e.skipDescendants(); return }
                    let isPackage = v.isPackage == true || packageExtensions.contains(url.pathExtension.lowercased())
                    if v.isDirectory == true && !isPackage { return }
                    if Safety.verdict(for: url, home: ctx.home).isForbidden { return }
                    result.scanned += 1
                    if result.scanned % 500 == 0 { progress(result.scanned) }
                    let size = isPackage && v.isDirectory == true
                        ? DiskUsage.size(of: url)
                        : Int64(v.totalFileAllocatedSize ?? v.fileAllocatedSize ?? 0)
                    guard size > 0 else { return } // файлы iCloud без локальной копии места не занимают
                    let entry = FileEntry(url: url, size: size, modified: v.contentModificationDate, accessed: v.contentAccessDate,
                                          kind: FileKind.of(url))
                    if size >= largeThreshold { result.large.append(entry) }
                    if size >= 10_000_000, let last = entry.lastUse, last.daysBefore(ctx.now) > oldDays { result.old.append(entry) }
                    if duplicates, !isPackage, v.isRegularFile == true, size >= 1_000_000 {
                        // Жёсткие ссылки на один и тот же файл — не дубликаты: удаление места не освободит.
                        var st = stat()
                        let inode = lstat(url.path, &st) == 0 ? "\(st.st_dev):\(st.st_ino)" : url.path
                        if seenInodes.insert(inode).inserted { candidates.append(entry) }
                    }
                }
            }
        }
        result.large.sort { $0.size > $1.size }
        result.old.sort { ($0.lastUse ?? .distantPast) < ($1.lastUse ?? .distantPast) }
        if duplicates { result.duplicates = findDuplicates(candidates) }
        return result
    }

    /// Три ступени: размер → хеш начала и конца → полный SHA-256. Так большие уникальные файлы
    /// читаются только по 128 КБ, а целиком хешируются лишь настоящие кандидаты.
    static func findDuplicates(_ files: [FileEntry]) -> [DuplicateGroup] {
        let bySize = Dictionary(grouping: files, by: \.size).values.filter { $0.count > 1 }
        let groups = Collector<DuplicateGroup>()
        let sizeGroups = Array(bySize)
        DispatchQueue.concurrentPerform(iterations: sizeGroups.count) { i in
            let byPartial = Dictionary(grouping: sizeGroups[i]) { partialHash($0.url) ?? UUID().uuidString }.values.filter { $0.count > 1 }
            for partial in byPartial {
                let byFull = Dictionary(grouping: partial) { fullHash($0.url) ?? UUID().uuidString }
                for (hash, same) in byFull where same.count > 1 {
                    let group = DuplicateGroup(id: hash, files: same.sorted { ($0.modified ?? .distantPast) < ($1.modified ?? .distantPast) })
                    groups.append(group)
                }
            }
        }
        return groups.values.sorted { $0.wasted > $1.wasted }
    }

    static func partialHash(_ url: URL) -> String? {
        guard let h = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? h.close() }
        var hasher = SHA256()
        guard let head = try? h.read(upToCount: 65_536) else { return nil }
        hasher.update(data: head)
        if let end = try? h.seekToEnd(), end > 131_072 {
            try? h.seek(toOffset: end - 65_536)
            if let tail = try? h.read(upToCount: 65_536) { hasher.update(data: tail) }
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    static func fullHash(_ url: URL) -> String? {
        guard let h = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? h.close() }
        var hasher = SHA256()
        var more = true
        while more {
            autoreleasepool {
                if let chunk = try? h.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) } else { more = false }
            }
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// Какую копию оставить: не из Загрузок/Рабочего стола и самую старую (это обычно оригинал).
    static func keeper(in group: DuplicateGroup, home: URL) -> FileEntry? {
        let transient = ["Downloads", "Desktop"].map { home.appendingPathComponent($0).path + "/" }
        return group.files.min { a, b in
            let ta = transient.contains { a.url.path.hasPrefix($0) }, tb = transient.contains { b.url.path.hasPrefix($0) }
            if ta != tb { return !ta }
            return (a.modified ?? .distantPast) < (b.modified ?? .distantPast)
        }
    }
}

/// Узел карты диска.
nonisolated struct SpaceNode: Identifiable, Sendable, Hashable {
    var id: String { url.path }
    var name: String
    var url: URL
    var size: Int64
    var isDirectory: Bool
    var children: [SpaceNode] = []
    /// Узел-сводка «и ещё N объектов».
    var isRest = false
    /// Папка, внутрь которой без полного доступа заглядывать не стоит.
    var locked = false

    static func == (a: SpaceNode, b: SpaceNode) -> Bool { a.id == b.id && a.size == b.size }
    func hash(into h: inout Hasher) { h.combine(id) }
}

nonisolated enum SpaceScanner {
    /// Строит дерево размеров до глубины `depth` (ниже — только суммарный размер).
    static func scan(_ root: URL, ctx: ScanContext, depth: Int = 6, progress: @Sendable (Int) -> Void = { _ in }) -> SpaceNode {
        let counter = Counter()
        let top = DiskUsage.children(of: root, hidden: true)
        let nodes = Collector<SpaceNode>()
        DispatchQueue.concurrentPerform(iterations: top.count) { i in
            nodes.append(build(top[i], ctx: ctx, depth: depth - 1, counter: counter, progress: progress))
        }
        return finish(SpaceNode(name: root.lastPathComponent, url: root, size: 0, isDirectory: true), children: nodes.values)
    }

    nonisolated final class Counter: @unchecked Sendable {
        private let lock = NSLock()
        private var value = 0
        func add(_ n: Int) -> Int { lock.withLock { value += n; return value } }
    }

    static func lockedWithoutAccess(_ url: URL, ctx: ScanContext) -> Bool {
        let p = url.path
        if !ctx.hasFullDiskAccess {
            for sub in ["Library/Containers", "Library/Group Containers", "Library/Mail", "Library/Safari", "Library/Messages", ".Trash"] {
                if p == ctx.home.appendingPathComponent(sub).path { return true }
            }
        }
        if ctx.avoidPrivacyPrompts {
            for sub in ["Desktop", "Documents", "Downloads", "Pictures", "Movies", "Music", ".Trash"] where p == ctx.home.appendingPathComponent(sub).path {
                return true
            }
        }
        return false
    }

    static func build(_ url: URL, ctx: ScanContext, depth: Int, counter: Counter, progress: @Sendable (Int) -> Void) -> SpaceNode {
        let values = try? url.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey, .isPackageKey, .totalFileAllocatedSizeKey,
                                                      .fileAllocatedSizeKey, .volumeIdentifierKey])
        let name = url.lastPathComponent
        if lockedWithoutAccess(url, ctx: ctx) {
            return SpaceNode(name: name, url: url, size: 0, isDirectory: true, locked: true)
        }
        guard values?.isDirectory == true, values?.isSymbolicLink != true else {
            let n = counter.add(1)
            if n % 2000 == 0 { progress(n) }
            return SpaceNode(name: name, url: url, size: Int64(values?.totalFileAllocatedSize ?? values?.fileAllocatedSize ?? 0), isDirectory: false)
        }
        if depth <= 0 || values?.isPackage == true {
            let s = DiskUsage.summary(of: url)
            let n = counter.add(s.files)
            progress(n)
            return SpaceNode(name: name, url: url, size: s.size, isDirectory: true)
        }
        let kids = DiskUsage.children(of: url, hidden: true).map { build($0, ctx: ctx, depth: depth - 1, counter: counter, progress: progress) }
        return finish(SpaceNode(name: name, url: url, size: 0, isDirectory: true), children: kids)
    }

    /// Сортирует по размеру и сворачивает мелочь в один узел, чтобы дерево оставалось лёгким.
    static func finish(_ node: SpaceNode, children: [SpaceNode]) -> SpaceNode {
        var n = node
        let sorted = children.sorted { $0.size > $1.size }
        n.size = sorted.reduce(0) { $0 + $1.size }
        let keep = 48
        if sorted.count > keep {
            let rest = sorted[keep...]
            n.children = Array(sorted[..<keep])
            n.children.append(SpaceNode(name: "Ещё \(rest.count) объектов", url: node.url.appendingPathComponent("…rest"),
                                        size: rest.reduce(0) { $0 + $1.size }, isDirectory: false, isRest: true))
        } else {
            n.children = sorted
        }
        return n
    }
}
