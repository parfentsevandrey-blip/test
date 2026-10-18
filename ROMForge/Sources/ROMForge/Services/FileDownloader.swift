import CryptoKit
import Foundation

struct DownloadProgress: Sendable {
    var received: Int64
    var expected: Int64
    var bytesPerSecond: Double

    var fraction: Double { expected > 0 ? min(1, Double(received) / Double(expected)) : 0 }
}

enum DownloadError: LocalizedError {
    case badStatus(Int)
    case checksumMismatch(file: String)
    case looksLikeWebPage

    var errorDescription: String? {
        switch self {
        case .badStatus(let code): "Сервер ответил кодом \(code)."
        case .checksumMismatch(let file): "Контрольная сумма \(file) не совпала — файл повреждён. Скачайте заново."
        case .looksLikeWebPage: "Вместо файла пришла веб-страница. Попробуйте скачать прошивку вручную."
        }
    }
}

/// Загрузка больших файлов с прогрессом через URLSessionDownloadDelegate.
final class FileDownloader: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let destination: URL
    private let onProgress: @Sendable (DownloadProgress) -> Void
    private var continuation: CheckedContinuation<Void, Error>?
    private var moveError: Error?
    private var startedAt = Date()
    private var lastReport = Date.distantPast

    private init(destination: URL, onProgress: @escaping @Sendable (DownloadProgress) -> Void) {
        self.destination = destination
        self.onProgress = onProgress
    }

    static func download(
        from url: URL,
        to destination: URL,
        onProgress: @escaping @Sendable (DownloadProgress) -> Void
    ) async throws {
        let delegate = FileDownloader(destination: destination, onProgress: onProgress)
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 60
        configuration.timeoutIntervalForResource = 60 * 60 * 6
        let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }

        var request = URLRequest(url: url)
        // SourceForge отдаёт прямую ссылку на зеркало только «консольным» клиентам.
        request.setValue("Wget/1.21.4", forHTTPHeaderField: "User-Agent")
        let task = session.downloadTask(with: request)

        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                delegate.continuation = continuation
                delegate.startedAt = Date()
                task.resume()
            }
        } onCancel: {
            task.cancel()
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        let now = Date()
        guard now.timeIntervalSince(lastReport) > 0.2 else { return }
        lastReport = now
        let elapsed = max(now.timeIntervalSince(startedAt), 0.001)
        onProgress(DownloadProgress(received: totalBytesWritten,
                                    expected: totalBytesExpectedToWrite,
                                    bytesPerSecond: Double(totalBytesWritten) / elapsed))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        if let http = downloadTask.response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            moveError = DownloadError.badStatus(http.statusCode)
            return
        }
        if let mime = downloadTask.response?.mimeType, mime.hasPrefix("text/html"),
           !destination.pathExtension.lowercased().hasSuffix("html") {
            moveError = DownloadError.looksLikeWebPage
            return
        }
        do {
            let fm = FileManager.default
            try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
            if fm.fileExists(atPath: destination.path) { try fm.removeItem(at: destination) }
            try fm.moveItem(at: location, to: destination)
        } catch {
            moveError = error
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error ?? moveError {
            continuation?.resume(throwing: error)
        } else {
            continuation?.resume()
        }
        continuation = nil
    }
}

enum Checksum {
    enum Algorithm { case sha256, md5 }

    /// Потоковый хэш — прошивки весят несколько гигабайт.
    static func compute(_ algorithm: Algorithm, of file: URL,
                        onProgress: (@Sendable (Double) -> Void)? = nil) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            let handle = try FileHandle(forReadingFrom: file)
            defer { try? handle.close() }
            let total = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? Int64) ?? 0
            var processed: Int64 = 0
            var sha = SHA256()
            var md5 = Insecure.MD5()
            while let chunk = try handle.read(upToCount: 8 * 1024 * 1024), !chunk.isEmpty {
                try Task.checkCancellation()
                switch algorithm {
                case .sha256: sha.update(data: chunk)
                case .md5: md5.update(data: chunk)
                }
                processed += Int64(chunk.count)
                if total > 0 { onProgress?(Double(processed) / Double(total)) }
            }
            let bytes: [UInt8] = algorithm == .sha256 ? Array(sha.finalize()) : Array(md5.finalize())
            return bytes.map { String(format: "%02x", $0) }.joined()
        }.value
    }

    /// Проверяет файл по доступному хэшу. Возвращает false, если сверять нечем.
    static func verify(_ file: BuildFile, at url: URL,
                       onProgress: (@Sendable (Double) -> Void)? = nil) async throws -> Bool {
        if let expected = file.sha256?.lowercased(), !expected.isEmpty {
            let actual = try await compute(.sha256, of: url, onProgress: onProgress)
            guard actual == expected else { throw DownloadError.checksumMismatch(file: file.fileName) }
            return true
        }
        if let expected = file.md5?.lowercased(), !expected.isEmpty {
            let actual = try await compute(.md5, of: url, onProgress: onProgress)
            guard actual == expected else { throw DownloadError.checksumMismatch(file: file.fileName) }
            return true
        }
        return false
    }
}

extension Int64 {
    var formattedBytes: String {
        ByteCountFormatter.string(fromByteCount: self, countStyle: .file)
    }
}
