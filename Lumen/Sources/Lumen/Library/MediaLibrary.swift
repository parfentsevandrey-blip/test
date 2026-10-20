import AppKit
import Foundation
import Observation

nonisolated struct MediaItem: Codable, Identifiable, Hashable, Sendable {
    var id = UUID()
    var url: URL
    var title: String
    var addedAt = Date()
    var lastPlayedAt: Date?
    var duration: Double = 0
    var position: Double = 0
    var isFavorite = false
    var fileSize: Int64 = 0

    init(url: URL) {
        self.url = url
        self.title = url.deletingPathExtension().lastPathComponent
        self.fileSize = Int64((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
    }

    var progress: Double { duration > 0 ? min(max(position / duration, 0), 1) : 0 }
    var isInProgress: Bool { progress > 0.015 && progress < 0.95 }
    var isWatched: Bool { progress >= 0.95 }
    var remaining: Double { max(0, duration - position) }
    var exists: Bool { FileManager.default.fileExists(atPath: url.path) }
}

/// Библиотека: последние открытые файлы, позиция просмотра, избранное и обложки.
@Observable
final class MediaLibrary {
    private(set) var items: [MediaItem] = []
    private(set) var thumbnails: [UUID: NSImage] = [:]

    @ObservationIgnored private var pendingThumbnails: Set<UUID> = []
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private let storeURL: URL
    @ObservationIgnored let thumbnailDirectory: URL

    init() {
        let fm = FileManager.default
        let support = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Lumen", isDirectory: true)
        try? fm.createDirectory(at: support, withIntermediateDirectories: true)
        storeURL = support.appendingPathComponent("library.json")
        thumbnailDirectory = fm.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("Lumen/Thumbnails", isDirectory: true)
        if let data = try? Data(contentsOf: storeURL),
           let decoded = try? JSONDecoder().decode([MediaItem].self, from: data) {
            items = decoded
        }
    }

    // MARK: Списки

    var recent: [MediaItem] {
        items.sorted { ($0.lastPlayedAt ?? $0.addedAt) > ($1.lastPlayedAt ?? $1.addedAt) }
    }

    var continueWatching: [MediaItem] {
        recent.filter { $0.isInProgress && $0.exists }
    }

    var favorites: [MediaItem] { recent.filter(\.isFavorite) }

    var totalDuration: Double { items.reduce(0) { $0 + $1.duration } }

    func item(id: UUID) -> MediaItem? { items.first { $0.id == id } }

    // MARK: Изменения

    /// Добавляет файлы (или находит уже известные) и возвращает их в исходном порядке.
    @discardableResult
    func add(_ urls: [URL]) -> [MediaItem] {
        var result: [MediaItem] = []
        for url in urls {
            let standard = url.standardizedFileURL
            if let existing = items.first(where: { $0.url.standardizedFileURL == standard }) {
                result.append(existing)
            } else {
                let item = MediaItem(url: standard)
                items.append(item)
                result.append(item)
            }
        }
        scheduleSave()
        return result
    }

    func update(_ id: UUID, _ change: (inout MediaItem) -> Void) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        change(&items[index])
        scheduleSave()
    }

    func remove(_ id: UUID) {
        items.removeAll { $0.id == id }
        thumbnails[id] = nil
        try? FileManager.default.removeItem(at: thumbnailURL(id))
        scheduleSave()
    }

    func removeMissing() {
        let missing = items.filter { !$0.exists }.map(\.id)
        missing.forEach(remove)
    }

    func clearHistory() {
        for i in items.indices {
            items[i].position = 0
            items[i].lastPlayedAt = nil
        }
        scheduleSave()
    }

    func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(0.8))
            guard !Task.isCancelled else { return }
            self?.saveNow()
        }
    }

    func saveNow() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: storeURL, options: .atomic)
    }

    // MARK: Обложки

    func thumbnailURL(_ id: UUID) -> URL {
        thumbnailDirectory.appendingPathComponent("\(id.uuidString).jpg")
    }

    func loadThumbnail(for item: MediaItem) async {
        guard thumbnails[item.id] == nil, !pendingThumbnails.contains(item.id) else { return }
        let file = thumbnailURL(item.id)
        if let image = NSImage(contentsOf: file) {
            thumbnails[item.id] = image
            return
        }
        guard item.exists else { return }
        pendingThumbnails.insert(item.id)
        defer { pendingThumbnails.remove(item.id) }

        // Для Matroska берём кадр из уже перепакованной копии, если она есть.
        let source = MediaResolver.cachedSource(for: item.url)?.url
            ?? (MatroskaRemuxer.fileExtensions.contains(item.url.pathExtension.lowercased()) ? nil : item.url)
        guard let source else { return }
        let result = await Thumbnailer.make(for: source, cacheFile: file)
        if let image = result.image {
            thumbnails[item.id] = NSImage(cgImage: image, size: .zero)
        }
        if result.duration > 0, self.item(id: item.id)?.duration == 0 {
            update(item.id) { $0.duration = result.duration }
        }
    }

    /// Обложка из текущего кадра (после первого воспроизведения Matroska и т.п.).
    func setThumbnail(_ image: CGImage, for id: UUID) {
        thumbnails[id] = NSImage(cgImage: image, size: .zero)
        let url = thumbnailURL(id)
        Task.detached(priority: .utility) { Thumbnailer.writeJPEG(image, to: url) }
    }
}
