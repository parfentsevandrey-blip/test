import AppKit
import CryptoKit
import Foundation

/// Скриншоты дистрибутивов с Wikimedia Commons (свободные лицензии; автор и лицензия — на странице файла).
/// Адрес строится по правилу Commons (папки из MD5 имени), без запросов к API. Картинки кэшируются на диске.
nonisolated enum Commons {
    static func fileName(_ title: String) -> String { title.replacingOccurrences(of: " ", with: "_") }

    private static func hashPath(_ name: String) -> String {
        let md5 = Insecure.MD5.hash(data: Data(name.utf8)).map { String(format: "%02x", $0) }.joined()
        return "\(md5.prefix(1))/\(md5.prefix(2))"
    }

    private static func encoded(_ name: String) -> String {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "?#")
        return name.addingPercentEncoding(withAllowedCharacters: allowed) ?? name
    }

    /// Оригинал файла.
    static func original(_ title: String) -> URL {
        let name = fileName(title)
        return URL(string: "https://upload.wikimedia.org/wikipedia/commons/\(hashPath(name))/\(encoded(name))")!
    }

    /// Уменьшенная копия стандартной ширины (Commons отдаёт только стандартные размеры).
    static func thumbnail(_ title: String, width: Int = 1280) -> URL {
        let name = fileName(title)
        let thumbName = name.lowercased().hasSuffix(".svg") ? "\(width)px-\(name).png" : "\(width)px-\(name)"
        return URL(string: "https://upload.wikimedia.org/wikipedia/commons/thumb/\(hashPath(name))/\(encoded(name))/\(encoded(thumbName))")!
    }

    /// Страница файла с автором и лицензией.
    static func page(_ title: String) -> URL {
        URL(string: "https://commons.wikimedia.org/wiki/File:\(encoded(fileName(title)))")!
    }
}

/// Загрузка и кэш скриншотов (байты; картинку из них делает интерфейс). Ошибки не всплывают:
/// нет картинки — показывается обложка дистрибутива.
actor ScreenshotStore {
    static let shared = ScreenshotStore()

    private var memory: [String: Data] = [:]
    private var failed: Set<String> = []
    private var running: [String: Task<Data?, Never>] = [:]

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 25
        c.httpMaximumConnectionsPerHost = 2
        // Wikimedia просит у программ понятный User-Agent со ссылкой на проект.
        c.httpAdditionalHeaders = ["User-Agent": "Vessel/1.0 (https://github.com/parfentsevandrey-blip/test; macOS virtual machines)"]
        return URLSession(configuration: c)
    }()

    func data(_ title: String) async -> Data? {
        if let hit = memory[title] { return hit }
        if failed.contains(title) { return nil }
        if let task = running[title] { return await task.value }
        let task = Task<Data?, Never> { await self.load(title) }
        running[title] = task
        let result = await task.value
        running[title] = nil
        if let result { memory[title] = result } else { failed.insert(title) }
        return result
    }

    private func cacheURL(_ title: String) -> URL {
        let key = SHA256.hash(data: Data(title.utf8)).map { String(format: "%02x", $0) }.joined().prefix(32)
        return Paths.screenshots.appendingPathComponent(String(key))
    }

    private func load(_ title: String) async -> Data? {
        let cached = cacheURL(title)
        if let data = try? Data(contentsOf: cached), NSImage(data: data) != nil { return data }
        for url in [Commons.thumbnail(title), Commons.original(title)] {
            for attempt in 0..<2 {
                guard let (data, response) = try? await session.data(from: url) else { continue }
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                if code == 429 || code == 503 {
                    // Commons просит подождать — ждём и пробуем ещё раз.
                    try? await Task.sleep(for: .seconds(2 + attempt * 3))
                    continue
                }
                guard code == 200, NSImage(data: data) != nil else { break }
                try? FileManager.default.createDirectory(at: Paths.screenshots, withIntermediateDirectories: true)
                try? data.write(to: cached)
                return data
            }
        }
        return nil
    }
}

/// Готовые картинки для интерфейса: одна расшифровка на снимок.
enum ScreenshotImages {
    private static var cache: [String: NSImage] = [:]

    static func image(_ title: String) async -> NSImage? {
        if let hit = cache[title] { return hit }
        guard let data = await ScreenshotStore.shared.data(title), let image = NSImage(data: data) else { return nil }
        cache[title] = image
        return image
    }

    static func cached(_ title: String?) -> NSImage? { title.flatMap { cache[$0] } }
}
