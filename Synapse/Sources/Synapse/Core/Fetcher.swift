import CryptoKit
import Foundation

nonisolated enum FetchError: LocalizedError {
    case http(Int)
    case corrupted
    case sizeMismatch(Int64, Int64)
    case noSpace(Int64)

    var errorDescription: String? {
        switch self {
        case .http(let code): "Сервер ответил кодом \(code)."
        case .corrupted: "Файл пришёл повреждённым (контрольная сумма не совпала) — скачаю заново."
        case .sizeMismatch(let got, let want): "Получено \(Format.bytes(got)) из \(Format.bytes(want))."
        case .noSpace(let need): "Не хватает места на диске: нужно ещё \(Format.bytes(need))."
        }
    }
}

/// Скачивание одного файла: докачка с места обрыва (HTTP Range), SHA-256 считается на лету
/// и сверяется с отпечатком из Hugging Face. Пока файл не проверен, он лежит как «.part».
nonisolated final class FileFetcher: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    let url: URL
    let destination: URL
    let expectedSize: Int64
    let sha256: String?
    private let onProgress: @Sendable (Int64) -> Void

    private var part: URL { destination.appendingPathExtension("part") }
    private var hasher = SHA256()
    private var handle: FileHandle?
    private var received: Int64 = 0
    private var startOffset: Int64 = 0
    private var checkedResponse = false
    private var lastReport = Date.distantPast
    private var failure: Error?
    private var continuation: CheckedContinuation<Void, Error>?
    private var task: URLSessionDataTask?
    private lazy var session: URLSession = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 60
        c.timeoutIntervalForResource = 60 * 60 * 24
        c.httpAdditionalHeaders = ["User-Agent": "Synapse/1.0 (macOS; local LLM app)"]
        return URLSession(configuration: c, delegate: self, delegateQueue: queue)
    }()

    init(url: URL, destination: URL, expectedSize: Int64, sha256: String?, onProgress: @escaping @Sendable (Int64) -> Void) {
        self.url = url
        self.destination = destination
        self.expectedSize = expectedSize
        self.sha256 = sha256?.lowercased()
        self.onProgress = onProgress
    }

    func run() async throws {
        let fm = FileManager.default
        try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if let size = (try? fm.attributesOfItem(atPath: destination.path))?[.size] as? Int64, size == expectedSize, expectedSize > 0 {
            onProgress(size)
            return
        }

        // Уже скачанная часть: досчитываем её хеш и продолжаем с того же места.
        if !fm.fileExists(atPath: part.path) { fm.createFile(atPath: part.path, contents: nil) }
        var existing = ((try? fm.attributesOfItem(atPath: part.path))?[.size] as? Int64) ?? 0
        if expectedSize > 0 && existing > expectedSize { try? fm.removeItem(at: part); fm.createFile(atPath: part.path, contents: nil); existing = 0 }
        if existing > 0, sha256 != nil { try hashExisting(upTo: existing) }
        received = existing
        startOffset = existing
        onProgress(existing)

        let need = expectedSize - existing
        if need > 0 && need + 512 * 1_048_576 > Hardware.freeDisk() { throw FetchError.noSpace(need) }

        handle = try FileHandle(forWritingTo: part)
        try handle?.seekToEnd()

        if expectedSize > 0 && existing == expectedSize {
            try finish()
            return
        }

        defer { session.finishTasksAndInvalidate() }
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
                self.continuation = cont
                var request = URLRequest(url: url)
                if existing > 0 { request.setValue("bytes=\(existing)-", forHTTPHeaderField: "Range") }
                let task = session.dataTask(with: request)
                self.task = task
                task.resume()
            }
        } onCancel: {
            self.task?.cancel()
        }
    }

    private func hashExisting(upTo length: Int64) throws {
        let reader = try FileHandle(forReadingFrom: part)
        defer { try? reader.close() }
        var left = length
        while left > 0 {
            try autoreleasepool {
                let chunk = try reader.read(upToCount: Int(min(left, 8 * 1_048_576))) ?? Data()
                if chunk.isEmpty { left = 0; return }
                hasher.update(data: chunk)
                left -= Int64(chunk.count)
            }
        }
    }

    // MARK: URLSessionDataDelegate (последовательная очередь)

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        // Перенаправление на CDN: заголовок Range должен дойти до него.
        var r = request
        if startOffset > 0 { r.setValue("bytes=\(startOffset)-", forHTTPHeaderField: "Range") }
        completionHandler(r)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        if !checkedResponse {
            checkedResponse = true
            let code = (dataTask.response as? HTTPURLResponse)?.statusCode ?? 0
            switch code {
            case 206:
                break
            case 200:
                if startOffset > 0 {
                    // Сервер не умеет докачку — начинаем файл заново.
                    try? handle?.truncate(atOffset: 0)
                    hasher = SHA256()
                    received = 0
                    startOffset = 0
                }
            case 416:
                // Диапазон за концом файла — значит, всё уже скачано.
                dataTask.cancel()
                return
            default:
                failure = FetchError.http(code)
                dataTask.cancel()
                return
            }
        }
        if failure != nil { return }
        do {
            try handle?.write(contentsOf: data)
        } catch {
            failure = error
            dataTask.cancel()
            return
        }
        hasher.update(data: data)
        received += Int64(data.count)
        let now = Date()
        if now.timeIntervalSince(lastReport) > 0.15 {
            lastReport = now
            onProgress(received)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        let code = (task.response as? HTTPURLResponse)?.statusCode ?? 0
        let cont = continuation
        continuation = nil
        if let failure {
            try? handle?.close()
            cont?.resume(throwing: failure)
            return
        }
        if let error, code != 416 {
            try? handle?.close()
            cont?.resume(throwing: error)
            return
        }
        do {
            try finish()
            cont?.resume()
        } catch {
            cont?.resume(throwing: error)
        }
    }

    private func finish() throws {
        try? handle?.synchronize()
        try? handle?.close()
        handle = nil
        onProgress(received)
        if expectedSize > 0 && received != expectedSize {
            if received > expectedSize { try? FileManager.default.removeItem(at: part) }
            throw FetchError.sizeMismatch(received, expectedSize)
        }
        if let sha256 {
            let digest = hasher.finalize().map { String(format: "%02x", $0) }.joined()
            guard digest == sha256 else {
                try? FileManager.default.removeItem(at: part)
                throw FetchError.corrupted
            }
        }
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.moveItem(at: part, to: destination)
    }
}
