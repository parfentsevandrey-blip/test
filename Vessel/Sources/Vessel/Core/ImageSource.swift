import Foundation

nonisolated enum HashKind: String, Codable, Sendable {
    case sha256, blake2b

    var hexLength: Int { self == .sha256 ? 64 : 128 }
    var title: String { self == .sha256 ? "SHA-256" : "BLAKE2b" }
}

/// Где лежит контрольная сумма образа.
nonisolated enum Sums: Sendable {
    /// Общий файл сумм; путь относительно папки образа (можно «../b2sum.txt»).
    case file(String, HashKind)
    /// Файл рядом с образом: имя образа + суффикс.
    case sidecar(String, HashKind)
}

/// Откуда берётся образ. Имена файлов меняются с каждым выпуском, поэтому каталог хранит не ссылку,
/// а правило: где искать последнюю версию и её контрольную сумму. Так Vessel сам находит свежий выпуск.
nonisolated enum ImageSource: Sendable {
    /// Список файлов на сервере: шаги по папкам (регулярное выражение — берётся старшая версия), затем файл.
    case listing(base: String, dirs: [String], file: String, sums: Sums)
    /// Постоянная ссылка («latest», «Current»).
    case direct(url: String, sums: Sums)
    /// fedoraproject.org/releases.json: вариант, подвариант и вид образа (часть имени файла).
    case fedora(variant: String, subvariant: String, kind: String)
    /// api.pop-os.org: версии в порядке предпочтения.
    case pop(versions: [String], channel: String)
    /// latest-releases.yaml Alpine.
    case alpine(flavor: String)
    /// latest-iso.txt Gentoo.
    case gentoo(name: String)
    /// Каналы NixOS: последняя стабильная версия ГГ.05 или ГГ.11.
    case nixos(edition: String)
}

/// Найденный образ: точный адрес, версия и ожидаемая сумма.
nonisolated struct ResolvedImage: Codable, Sendable, Hashable {
    var url: URL
    var fileName: String
    var version: String
    var size: Int64?
    var hash: HashKind?
    var digest: String?
}

nonisolated enum ResolveError: LocalizedError {
    case notFound(String)
    case http(Int, String)
    case noChecksum(String)

    var errorDescription: String? {
        switch self {
        case .notFound(let what): "Не нашёл образ на сервере проекта (\(what)). Возможно, выпуск переехал — попробуйте позже."
        case .http(let code, let host): "Сервер \(host) ответил кодом \(code)."
        case .noChecksum(let name): "У образа \(name) нет контрольной суммы — без проверки Vessel его не ставит."
        }
    }
}

nonisolated enum Resolver {
    static let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 30
        c.httpAdditionalHeaders = ["User-Agent": "Vessel/1.0 (macOS; virtual machines)"]
        return URLSession(configuration: c)
    }()

    static let arch = "aarch64"

    static func resolve(_ source: ImageSource) async throws -> ResolvedImage {
        var image: ResolvedImage
        switch source {
        case .listing(let base, let dirs, let file, let sums):
            var dir = URL(string: base)!
            for step in dirs {
                let names = try await listing(dir).filter { $0.isDirectory }.map(\.name)
                guard let pick = highest(names, matching: step) else { throw ResolveError.notFound("\(dir.absoluteString) → \(step)") }
                dir = dir.appendingPathComponent(pick, isDirectory: true)
            }
            let files = try await listing(dir).filter { !$0.isDirectory }.map(\.name)
            guard let name = highest(files, matching: file) else { throw ResolveError.notFound("\(dir.absoluteString) → \(file)") }
            image = ResolvedImage(url: dir.appendingPathComponent(name), fileName: name, version: version(in: name))
            try await attach(sums, to: &image)

        case .direct(let link, let sums):
            let url = URL(string: link)!
            image = ResolvedImage(url: url, fileName: url.lastPathComponent, version: "")
            try await attach(sums, to: &image)
            if image.version.isEmpty { image.version = version(in: image.fileName) }

        case .fedora(let variant, let subvariant, let kind):
            let data = try await get(URL(string: "https://fedoraproject.org/releases.json")!)
            let items = (try JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
            let matches = items.filter {
                ($0["arch"] as? String) == arch && ($0["variant"] as? String) == variant &&
                ($0["subvariant"] as? String) == subvariant &&
                (($0["link"] as? String) ?? "").hasSuffix(".iso") &&
                (($0["link"] as? String) ?? "").localizedCaseInsensitiveContains(kind) &&
                Int(($0["version"] as? String) ?? "") != nil
            }
            guard let best = matches.max(by: { Int($0["version"] as! String)! < Int($1["version"] as! String)! }),
                  let link = best["link"] as? String, let url = URL(string: link) else {
                throw ResolveError.notFound("Fedora \(subvariant) \(kind)")
            }
            image = ResolvedImage(url: url, fileName: url.lastPathComponent, version: best["version"] as! String,
                                  size: (best["size"] as? String).flatMap { Int64($0) } ?? (best["size"] as? NSNumber)?.int64Value,
                                  hash: .sha256, digest: (best["sha256"] as? String)?.lowercased())

        case .pop(let versions, let channel):
            var found: ResolvedImage?
            for v in versions {
                guard let data = try? await get(URL(string: "https://api.pop-os.org/builds/\(v)/\(channel)?arch=arm64")!),
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let link = json["url"] as? String, let url = URL(string: link) else { continue }
                found = ResolvedImage(url: url, fileName: url.lastPathComponent, version: "\(v) (сборка \(json["build"] as? String ?? "?"))",
                                      size: (json["size"] as? NSNumber)?.int64Value, hash: .sha256,
                                      digest: (json["sha_sum"] as? String)?.lowercased())
                break
            }
            guard let found else { throw ResolveError.notFound("Pop!_OS \(versions.joined(separator: ", "))") }
            image = found

        case .alpine(let flavor):
            let base = URL(string: "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/\(arch)/")!
            let text = String(decoding: try await get(base.appendingPathComponent("latest-releases.yaml")), as: UTF8.self)
            guard let entry = yamlEntries(text).first(where: { $0["flavor"] == flavor && ($0["iso"] ?? "").hasSuffix(".iso") }),
                  let iso = entry["iso"] else { throw ResolveError.notFound("Alpine \(flavor)") }
            image = ResolvedImage(url: base.appendingPathComponent(iso), fileName: iso, version: entry["version"] ?? version(in: iso),
                                  size: entry["size"].flatMap { Int64($0) }, hash: .sha256, digest: entry["sha256"]?.lowercased())

        case .gentoo(let name):
            let base = URL(string: "https://distfiles.gentoo.org/releases/arm64/autobuilds/")!
            let text = String(decoding: try await get(base.appendingPathComponent("latest-iso.txt")), as: UTF8.self)
            guard let line = text.split(separator: "\n").first(where: { $0.contains(name) && !$0.hasPrefix("#") }),
                  let path = line.split(separator: " ").first else { throw ResolveError.notFound("Gentoo \(name)") }
            let url = base.appendingPathComponent(String(path))
            image = ResolvedImage(url: url, fileName: url.lastPathComponent, version: version(in: url.lastPathComponent),
                                  size: line.split(separator: " ").dropFirst().first.flatMap { Int64($0) })
            try await attach(.sidecar(".sha256", .sha256), to: &image)

        case .nixos(let edition):
            var found: URL?
            for channel in nixChannels() {
                let link = URL(string: "https://channels.nixos.org/nixos-\(channel)/latest-nixos-\(edition)-\(arch)-linux.iso")!
                if let target = await redirectTarget(link) { found = target; break }
            }
            guard let found else { throw ResolveError.notFound("NixOS \(edition)") }
            image = ResolvedImage(url: found, fileName: found.lastPathComponent, version: version(in: found.lastPathComponent))
            try await attach(.sidecar(".sha256", .sha256), to: &image)
        }
        if image.size == nil { image.size = await contentLength(image.url) }
        guard image.digest != nil else { throw ResolveError.noChecksum(image.fileName) }
        return image
    }

    // MARK: Контрольные суммы

    static func attach(_ sums: Sums, to image: inout ResolvedImage) async throws {
        let folder = image.url.deletingLastPathComponent()
        let (link, kind): (URL, HashKind) = switch sums {
        case .file(let path, let kind): (URL(string: path, relativeTo: folder)!.absoluteURL, kind)
        case .sidecar(let suffix, let kind): (URL(string: image.url.absoluteString + suffix)!, kind)
        }
        let text = String(decoding: try await get(link), as: UTF8.self)
        image.hash = kind
        image.digest = Checksums.find(image.fileName, in: text, kind: kind)
        if image.digest == nil { throw ResolveError.noChecksum(image.fileName) }
    }

    // MARK: Списки файлов

    struct Entry: Sendable { var name: String; var isDirectory: Bool }

    static func listing(_ url: URL) async throws -> [Entry] {
        let html = String(decoding: try await get(url), as: UTF8.self)
        return parseListing(html)
    }

    /// Ссылки из автоиндекса Apache/nginx/Caddy: имя — последний компонент пути, «/» в конце — папка.
    static func parseListing(_ html: String) -> [Entry] {
        let regex = try! NSRegularExpression(pattern: #"href\s*=\s*"([^"?#]+)""#, options: .caseInsensitive)
        let ns = html as NSString
        var seen = Set<String>()
        var result: [Entry] = []
        for m in regex.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let raw = ns.substring(with: m.range(at: 1))
            guard !raw.hasPrefix("http"), raw != "../", raw != "./", raw != "/" else { continue }
            let isDir = raw.hasSuffix("/")
            let trimmed = isDir ? String(raw.dropLast()) : raw
            guard let last = trimmed.split(separator: "/").last.map(String.init),
                  let name = last.removingPercentEncoding, !name.isEmpty, name != "..", name != "." else { continue }
            if seen.insert(name + (isDir ? "/" : "")).inserted { result.append(Entry(name: name, isDirectory: isDir)) }
        }
        return result
    }

    /// Старшая версия среди имён, целиком совпадающих с выражением.
    static func highest(_ names: [String], matching pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: "^(?:" + pattern + ")$") else { return nil }
        let matches = names.filter { regex.firstMatch(in: $0, range: NSRange(location: 0, length: ($0 as NSString).length)) != nil }
        return matches.max { compareVersions($0, $1) < 0 }
    }

    /// Сравнение «как sort -V»: числа — по значению, остальное — по символам.
    static func compareVersions(_ a: String, _ b: String) -> Int {
        let pa = parts(a), pb = parts(b)
        for (x, y) in zip(pa, pb) {
            switch (Int(x), Int(y)) {
            case (let i?, let j?): if i != j { return i < j ? -1 : 1 }
            default: if x != y { return x < y ? -1 : 1 }
            }
        }
        return pa.count == pb.count ? 0 : (pa.count < pb.count ? -1 : 1)
    }

    private static func parts(_ s: String) -> [String] {
        var out: [String] = []
        var current = ""
        var digits = false
        for ch in s {
            let d = ch.isASCII && ch.isNumber
            if !current.isEmpty && d != digits { out.append(current); current = "" }
            current.append(ch)
            digits = d
        }
        if !current.isEmpty { out.append(current) }
        return out
    }

    /// Версия из имени файла: первая группа «цифры с точками».
    static func version(in name: String) -> String {
        let regex = try! NSRegularExpression(pattern: #"\d+(?:\.\d+)+|\d{8}(?:T\d{6}Z)?|\d+"#)
        let ns = name as NSString
        guard let m = regex.firstMatch(in: name, range: NSRange(location: 0, length: ns.length)) else { return "" }
        return ns.substring(with: m.range)
    }

    /// Каналы NixOS от новых к старым: ГГ.11 и ГГ.05 за два года.
    static func nixChannels(now: Date = Date()) -> [String] {
        let year = Calendar(identifier: .gregorian).component(.year, from: now) % 100
        var out: [String] = []
        for y in stride(from: year, through: year - 2, by: -1) {
            out.append(String(format: "%02d.11", y))
            out.append(String(format: "%02d.05", y))
        }
        return out
    }

    /// Простой разбор latest-releases.yaml: список записей «- ключ: значение».
    static func yamlEntries(_ text: String) -> [[String: String]] {
        var entries: [[String: String]] = []
        var current: [String: String] = [:]
        for raw in text.split(separator: "\n") {
            var line = raw.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("-") {
                if !current.isEmpty { entries.append(current) }
                current = [:]
                line = String(line.dropFirst()).trimmingCharacters(in: .whitespaces)
            }
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = String(line[..<colon]).trimmingCharacters(in: .whitespaces)
            let value = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
            if !key.isEmpty && !value.isEmpty { current[key] = value }
        }
        if !current.isEmpty { entries.append(current) }
        return entries
    }

    // MARK: Сеть

    static func get(_ url: URL) async throws -> Data {
        let (data, response) = try await session.data(from: url)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw ResolveError.http(code, url.host ?? "") }
        return data
    }

    static func contentLength(_ url: URL) async -> Int64? {
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        guard let (_, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
        let length = http.expectedContentLength
        return length > 0 ? length : nil
    }

    /// Куда ведёт постоянная ссылка (302), не скачивая сам образ.
    static func redirectTarget(_ url: URL) async -> URL? {
        final class Stop: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
            func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                            newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
                completionHandler(nil)
            }
        }
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        guard let (_, response) = try? await session.data(for: request, delegate: Stop()),
              let http = response as? HTTPURLResponse else { return nil }
        if (300..<400).contains(http.statusCode), let location = http.value(forHTTPHeaderField: "Location") {
            return URL(string: location, relativeTo: url)?.absoluteURL
        }
        return http.statusCode == 200 ? url : nil
    }
}
