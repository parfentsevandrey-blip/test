import Foundation

/// Веб-сиды (BEP 19, url-list): куски берутся с обычного HTTP-сервера запросами Range.
/// Адрес — файл (одиночный торрент) или папка, в которой лежит папка раздачи.
nonisolated enum WebSeed {
    private static let allowed: CharacterSet = {
        var set = CharacterSet.urlPathAllowed
        set.remove(charactersIn: "/;?#")
        return set
    }()

    static func url(base: URL, meta: Metainfo, file: TorrentFile) -> URL? {
        var text = base.absoluteString
        if meta.isMultiFile {
            if !text.hasSuffix("/") { text += "/" }
            text += file.path.map { $0.addingPercentEncoding(withAllowedCharacters: allowed) ?? $0 }.joined(separator: "/")
        } else if text.hasSuffix("/") {
            text += meta.name.addingPercentEncoding(withAllowedCharacters: allowed) ?? meta.name
        }
        return URL(string: text)
    }

    /// Скачивает кусок целиком (может пересекать несколько файлов; заполнители — нули).
    static func fetch(base: URL, meta: Metainfo, piece: Int, anonymous: Bool) async throws -> Data {
        let start = Int64(piece) * Int64(meta.pieceLength)
        let size = meta.pieceSize(piece)
        let end = start + Int64(size)
        var out = Data(count: size)
        for file in meta.files where file.length > 0 && file.offset < end && file.offset + file.length > start {
            guard !file.isPadding else { continue }
            let from = max(start, file.offset) - file.offset
            let to = min(end, file.offset + file.length) - file.offset
            guard let url = url(base: base, meta: meta, file: file) else { throw PeerError("Неверный адрес веб-сида") }
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
            request.setValue("bytes=\(from)-\(to - 1)", forHTTPHeaderField: "Range")
            request.setValue(anonymous ? "Mozilla/5.0" : PeerIdentity.clientName, forHTTPHeaderField: "User-Agent")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw PeerError("Веб-сид не ответил") }
            let part: Data
            switch http.statusCode {
            case 206:
                part = data
            case 200 where Int64(data.count) == file.length:
                // Сервер не понял Range и прислал файл целиком.
                part = data.subdata(in: Int(from)..<Int(to))
            default:
                throw PeerError("Веб-сид ответил \(http.statusCode)")
            }
            guard Int64(part.count) == to - from else { throw PeerError("Веб-сид прислал не тот объём") }
            let at = Int(file.offset + from - start)
            out.replaceSubrange(at..<(at + part.count), with: part)
        }
        return out
    }
}
