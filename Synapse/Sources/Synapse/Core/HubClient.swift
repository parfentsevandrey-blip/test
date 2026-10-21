import Foundation

nonisolated enum HubError: LocalizedError {
    case http(Int, String)
    case offline
    case rateLimited

    var errorDescription: String? {
        switch self {
        case .http(let code, let url): "Сервер ответил \(code) (\(url))."
        case .offline: "Нет подключения к интернету."
        case .rateLimited: "Hugging Face просит подождать — слишком много запросов. Попробую чуть позже."
        }
    }
}

/// Клиент открытого API Hugging Face Hub (без ключа) и GitHub Releases.
///
/// - `GET /api/models?author=…&filter=gguf&sort=…&expand[]=gguf` — список моделей с параметрами и архитектурой;
/// - `GET /api/models/{repo}/tree/main?recursive=true` — файлы с размерами и SHA-256 (lfs.oid);
/// - `https://huggingface.co/{repo}/resolve/main/{file}` — сам файл (перенаправление на CDN).
/// Ответы кэшируются на диске: без интернета каталог и лента остаются.
nonisolated final class HubClient: Sendable {
    static let shared = HubClient()

    let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 30
        c.httpAdditionalHeaders = ["User-Agent": "Synapse/1.0 (macOS; local LLM app)", "Accept": "application/json"]
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: c)
    }()

    nonisolated(unsafe) static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { dec in
            let s = try dec.singleValueContainer().decode(String.self)
            if let date = parseDate(s) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: dec.codingPath, debugDescription: "Дата: \(s)"))
        }
        return d
    }()

    static func parseDate(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }

    // MARK: - Hugging Face

    enum Sort: String { case downloads, createdAt, trendingScore, likes, lastModified }

    func models(author: String? = nil, search: String? = nil, gguf: Bool = true, sort: Sort = .downloads,
                limit: Int = 50, extraFilter: String? = nil) async throws -> [HubModel] {
        var c = URLComponents(string: "https://huggingface.co/api/models")!
        var q: [URLQueryItem] = [
            .init(name: "sort", value: sort.rawValue), .init(name: "direction", value: "-1"), .init(name: "limit", value: String(limit)),
        ]
        if let author { q.append(.init(name: "author", value: author)) }
        if let search, !search.isEmpty { q.append(.init(name: "search", value: search)) }
        if gguf { q.append(.init(name: "filter", value: "gguf")) }
        if let extraFilter { q.append(.init(name: "filter", value: extraFilter)) }
        for e in ["gguf", "safetensors", "pipeline_tag", "downloads", "likes", "createdAt", "lastModified", "tags", "author"] {
            q.append(.init(name: "expand[]", value: e))
        }
        c.queryItems = q
        let data = try await get(c.url!, cacheKey: "models-\(author ?? "all")-\(search ?? "")-\(gguf)-\(sort.rawValue)-\(limit)-\(extraFilter ?? "")")
        return try Self.decoder.decode([HubModel].self, from: data)
    }

    func files(_ repo: String) async throws -> [HubFile] {
        let url = URL(string: "https://huggingface.co/api/models/\(repo)/tree/main?recursive=true")!
        let data = try await get(url, cacheKey: "tree-\(repo)")
        return try Self.decoder.decode([HubFile].self, from: data)
    }

    func variants(_ repo: String) async throws -> [ModelVariant] {
        ModelVariant.group(try await files(repo))
    }

    static func downloadURL(repo: String, path: String) -> URL {
        let encoded = path.split(separator: "/").map { $0.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? String($0) }.joined(separator: "/")
        return URL(string: "https://huggingface.co/\(repo)/resolve/main/\(encoded)?download=true")!
    }

    // MARK: - GitHub

    nonisolated struct Release: Codable, Sendable, Hashable {
        var tag_name: String
        var name: String?
        var body: String?
        var html_url: String
        var published_at: Date?
        var prerelease: Bool?
    }

    func llamaReleases() async throws -> [Release] {
        let url = URL(string: "https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=12")!
        let data = try await get(url, cacheKey: "llama-releases", accept: "application/vnd.github+json")
        return try Self.decoder.decode([Release].self, from: data)
    }

    // MARK: - Сеть с повторами и кэшем

    /// GET с повтором при 429/5xx. Без сети — последний сохранённый ответ.
    func get(_ url: URL, cacheKey: String, accept: String? = nil) async throws -> Data {
        var request = URLRequest(url: url)
        if let accept { request.setValue(accept, forHTTPHeaderField: "Accept") }
        var lastError: Error = HubError.offline
        for attempt in 0..<3 {
            do {
                let (data, response) = try await session.data(for: request)
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                if code == 200 {
                    ResponseCache.store(data, key: cacheKey)
                    return data
                }
                if code == 429 || code >= 500 {
                    lastError = code == 429 ? HubError.rateLimited : HubError.http(code, url.host() ?? "")
                    let retry = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Retry-After").flatMap(Double.init)
                        ?? Self.retryAfter(in: data) ?? Double(4 << attempt)
                    try await Task.sleep(for: .seconds(min(retry, 30) + Double.random(in: 0...1)))
                    continue
                }
                throw HubError.http(code, url.host() ?? "")
            } catch let e as URLError {
                lastError = [.notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .dataNotAllowed].contains(e.code) ? HubError.offline : e
                if attempt < 2 { try await Task.sleep(for: .seconds(1 << attempt)) }
            }
        }
        if let cached = ResponseCache.load(key: cacheKey) { return cached }
        throw lastError
    }

    private static func retryAfter(in data: Data) -> Double? {
        (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["retryAfter"] as? Double
    }
}

/// Последние ответы API на диске.
nonisolated enum ResponseCache {
    static var directory: URL {
        let dir = AppPaths.support.appendingPathComponent("Cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private static func file(_ key: String) -> URL {
        let safe = key.map { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "." ? $0 : "_" }
        return directory.appendingPathComponent(String(String(safe).prefix(180)) + ".json")
    }

    static func store(_ data: Data, key: String) { try? data.write(to: file(key), options: .atomic) }
    static func load(key: String) -> Data? { try? Data(contentsOf: file(key)) }
    static func age(key: String) -> TimeInterval? {
        (try? file(key).resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate.map { Date().timeIntervalSince($0) }
    }
}

/// Где приложение хранит данные.
nonisolated enum AppPaths {
    /// Переопределяется в самопроверках, чтобы не трогать настоящую библиотеку.
    nonisolated(unsafe) static var override: URL? = ProcessInfo.processInfo.environment["SYNAPSE_HOME"].map { URL(fileURLWithPath: $0) }

    static var support: URL {
        let base = override ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Synapse", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    static var models: URL {
        let dir = support.appendingPathComponent("Models", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static var chats: URL {
        let dir = support.appendingPathComponent("Chats", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
