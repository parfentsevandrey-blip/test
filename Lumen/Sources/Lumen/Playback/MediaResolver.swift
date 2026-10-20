import CryptoKit
import Foundation

/// Что в итоге отдаём AVPlayer: исходный файл или его перепакованную копию.
nonisolated struct PlaybackSource: Sendable {
    var url: URL
    var manifest: ConversionManifest?
    var isConverted: Bool { manifest != nil }
}

nonisolated enum MediaResolver {
    /// Расширения, которые понимает сама AVFoundation.
    static let nativeExtensions: Set<String> = [
        "mp4", "m4v", "mov", "qt", "3gp", "3g2", "mqv",
        "m4a", "mp3", "aac", "wav", "wave", "aif", "aiff", "aifc", "caf", "flac", "ac3", "eac3", "amr",
    ]
    static let videoExtensions: Set<String> = nativeExtensions.union(MatroskaRemuxer.fileExtensions)

    static func isSupported(_ url: URL) -> Bool {
        videoExtensions.contains(url.pathExtension.lowercased())
    }

    static func resolve(_ url: URL, progress: @escaping MatroskaRemuxer.Progress) async throws -> PlaybackSource {
        let ext = url.pathExtension.lowercased()
        if MatroskaRemuxer.fileExtensions.contains(ext) || (!nativeExtensions.contains(ext) && sniffMatroska(url)) {
            return try await convertedSource(for: url, progress: progress)
        }
        return PlaybackSource(url: url, manifest: nil)
    }

    /// Matroska узнаётся по сигнатуре EBML независимо от расширения.
    static func sniffMatroska(_ url: URL) -> Bool {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
        defer { try? handle.close() }
        let head = (try? handle.read(upToCount: 4)) ?? Data()
        return head == Data([0x1A, 0x45, 0xDF, 0xA3])
    }

    // MARK: Кэш

    static var cacheRoot: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Lumen/Converted", isDirectory: true)
    }

    static func cacheKey(for url: URL) -> String {
        let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
        let stamp = "\(url.standardizedFileURL.path)|\(values?.fileSize ?? 0)|\(values?.contentModificationDate?.timeIntervalSince1970 ?? 0)"
        return SHA256.hash(data: Data(stamp.utf8)).prefix(16).map { String(format: "%02x", $0) }.joined()
    }

    static func cachedSource(for url: URL) -> PlaybackSource? {
        let dir = cacheRoot.appendingPathComponent(cacheKey(for: url), isDirectory: true)
        let manifestURL = dir.appendingPathComponent("manifest.json")
        guard let data = try? Data(contentsOf: manifestURL),
              let manifest = try? JSONDecoder().decode(ConversionManifest.self, from: data),
              manifest.version == ConversionManifest.currentVersion
        else { return nil }
        let media = dir.appendingPathComponent(manifest.mediaFile)
        guard FileManager.default.fileExists(atPath: media.path) else { return nil }
        try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: dir.path)
        return PlaybackSource(url: media, manifest: manifest)
    }

    private static func convertedSource(for url: URL, progress: @escaping MatroskaRemuxer.Progress) async throws -> PlaybackSource {
        if let cached = cachedSource(for: url) { return cached }
        let fm = FileManager.default
        let key = cacheKey(for: url)
        let final = cacheRoot.appendingPathComponent(key, isDirectory: true)
        let partial = cacheRoot.appendingPathComponent(key + ".partial", isDirectory: true)
        try? fm.removeItem(at: partial)
        try fm.createDirectory(at: partial, withIntermediateDirectories: true)
        do {
            let manifest = try await MatroskaRemuxer.remux(input: url, outputDirectory: partial, progress: progress)
            let data = try JSONEncoder().encode(manifest)
            try data.write(to: partial.appendingPathComponent("manifest.json"), options: .atomic)
            try? fm.removeItem(at: final)
            try fm.moveItem(at: partial, to: final)
            return PlaybackSource(url: final.appendingPathComponent(manifest.mediaFile), manifest: manifest)
        } catch {
            try? fm.removeItem(at: partial)
            throw error
        }
    }

    static func cacheSize() -> Int64 {
        let fm = FileManager.default
        guard let e = fm.enumerator(at: cacheRoot, includingPropertiesForKeys: [.totalFileAllocatedSizeKey]) else { return 0 }
        var total: Int64 = 0
        for case let url as URL in e {
            total += Int64((try? url.resourceValues(forKeys: [.totalFileAllocatedSizeKey]).totalFileAllocatedSize) ?? 0)
        }
        return total
    }

    static func clearCache() {
        try? FileManager.default.removeItem(at: cacheRoot)
    }

    /// Удаляет давно не открывавшиеся копии и держит кэш в пределах лимита.
    static func trimCache(maxAge: TimeInterval = 21 * 86_400, maxBytes: Int64 = 60 << 30) {
        let fm = FileManager.default
        guard let dirs = try? fm.contentsOfDirectory(at: cacheRoot, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        var entries: [(URL, Date)] = dirs.map { ($0, (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) }
        for (url, date) in entries where url.pathExtension == "partial" || Date().timeIntervalSince(date) > maxAge {
            try? fm.removeItem(at: url)
        }
        entries.removeAll { !fm.fileExists(atPath: $0.0.path) }
        entries.sort { $0.1 < $1.1 }
        var size = cacheSize()
        for (url, _) in entries where size > maxBytes {
            let before = size
            try? fm.removeItem(at: url)
            size = cacheSize()
            if size == before { break }
        }
    }
}
