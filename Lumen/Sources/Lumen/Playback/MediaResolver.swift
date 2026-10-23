import Foundation

/// Какие файлы открываем и каким движком.
nonisolated enum MediaResolver {
    /// Расширения, которые понимает сама AVFoundation.
    static let nativeExtensions: Set<String> = [
        "mp4", "m4v", "mov", "qt", "3gp", "3g2", "mqv",
        "m4a", "mp3", "aac", "wav", "wave", "aif", "aiff", "aifc", "caf", "flac", "ac3", "eac3", "amr",
    ]
    static let matroskaExtensions: Set<String> = ["mkv", "mk3d", "mka", "webm"]
    static let videoExtensions: Set<String> = nativeExtensions.union(matroskaExtensions)

    static func isSupported(_ url: URL) -> Bool {
        videoExtensions.contains(url.pathExtension.lowercased())
    }

    /// Matroska по расширению или по сигнатуре EBML — если расширение незнакомое.
    static func isMatroska(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        if matroskaExtensions.contains(ext) { return true }
        return !nativeExtensions.contains(ext) && sniffMatroska(url)
    }

    static func sniffMatroska(_ url: URL) -> Bool {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
        defer { try? handle.close() }
        let head = (try? handle.read(upToCount: 4)) ?? Data()
        return head == Data([0x1A, 0x45, 0xDF, 0xA3])
    }

    /// Каталог, где прежние версии хранили перепакованные копии Matroska. Больше не нужен — удаляем.
    static func removeLegacyCache() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        try? FileManager.default.removeItem(at: base.appendingPathComponent("Lumen/Converted", isDirectory: true))
        try? FileManager.default.removeItem(at: base.appendingPathComponent("Lumen/Thumbnails", isDirectory: true))
    }
}
