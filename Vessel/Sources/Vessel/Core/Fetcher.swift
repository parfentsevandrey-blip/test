import Foundation

nonisolated enum FetchError: LocalizedError {
    case http(Int)
    case corrupted(HashKind)
    case sizeMismatch(Int64, Int64)
    case noSpace(Int64)

    var errorDescription: String? {
        switch self {
        case .http(let code): "Сервер ответил кодом \(code)."
        case .corrupted(let kind): "Образ пришёл повреждённым: сумма \(kind.title) не совпала. Скачаю заново."
        case .sizeMismatch(let got, let want): "Получено \(Format.bytes(got)) из \(Format.bytes(want))."
        case .noSpace(let need): "Не хватает места на диске: нужно ещё \(Format.bytes(need))."
        }
    }
}

/// Скачивание образа: докачка с места обрыва (HTTP Range, и через переадресацию на зеркало),
/// сумма считается на лету. Пока образ не проверен, он лежит как «.part».
nonisolated final class FileFetcher: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    let url: URL
    let destination: URL
    let expectedSize: Int64
    let hashKind: HashKind
    let digest: String
    private let onProgress: @Sendable (Int64, Int64) -> Void

    private var part: URL { destination.appendingPathExtension("part") }
    private var hasher: StreamHasher
    private var handle: FileHandle?
    private var received: Int64 = 0
    private var total: Int64 = 0
    private var startOffset: Int64 = 0
    private var checkedResponse = false
    private var lastReport = Date.distantPast
    private var failure: Error?
    private var continuation: CheckedContinuation<Void, Error>?
    private var task: URLSessionDataTask?

    init(url: URL, destination: URL, expectedSize: Int64?, hash: HashKind, digest: String,
         onProgress: @escaping @Sendable (Int64, Int64) -> Void) {
        self.url = url
        self.destination = destination
        self.expectedSize = expectedSize ?? 0
        self.hashKind = hash
        self.digest = digest.lowercased()
        self.hasher = StreamHasher(hash)
        self.onProgress = onProgress
    }

    func run() async throws {
        let fm = FileManager.default
        try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fm.fileExists(atPath: destination.path) { return }

        // Уже скачанная часть: досчитываем её сумму и продолжаем с того же места.
        if !fm.fileExists(atPath: part.path) { fm.createFile(atPath: part.path, contents: nil) }
        var existing = ((try? fm.attributesOfItem(atPath: part.path))?[.size] as? Int64) ?? 0
        if expectedSize > 0 && existing > expectedSize {
            try? fm.removeItem(at: part); fm.createFile(atPath: part.path, contents: nil); existing = 0
        }
        if existing > 0 { try hashExisting(upTo: existing) }
        received = existing
        startOffset = existing
        total = expectedSize
        onProgress(existing, total)

        let need = expectedSize - existing
        if need > 0 && need + 1_073_741_824 > MacInfo.freeDisk() { throw FetchError.noSpace(need) }

        handle = try FileHandle(forWritingTo: part)
        try handle?.seekToEnd()
        if expectedSize > 0 && existing == expectedSize { try finish(); return }

        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 60
        c.timeoutIntervalForResource = 60 * 60 * 24
        c.httpAdditionalHeaders = ["User-Agent": "Vessel/1.0 (macOS; virtual machines)"]
        let session = URLSession(configuration: c, delegate: self, delegateQueue: queue)
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
                hasher.update(chunk)
                left -= Int64(chunk.count)
            }
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        // Переадресация на зеркало: заголовок Range должен дойти и до него.
        var r = request
        if startOffset > 0 { r.setValue("bytes=\(startOffset)-", forHTTPHeaderField: "Range") }
        completionHandler(r)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        if !checkedResponse {
            checkedResponse = true
            let http = dataTask.response as? HTTPURLResponse
            switch http?.statusCode ?? 0 {
            case 206:
                break
            case 200:
                if startOffset > 0 {
                    // Сервер не умеет докачку — начинаем заново.
                    try? handle?.truncate(atOffset: 0)
                    hasher = StreamHasher(hashKind)
                    received = 0
                    startOffset = 0
                }
                if total <= 0, let length = http?.expectedContentLength, length > 0 { total = length }
            case 416:
                dataTask.cancel()
                return
            default:
                failure = FetchError.http(http?.statusCode ?? 0)
                dataTask.cancel()
                return
            }
        }
        if failure != nil { return }
        do { try handle?.write(contentsOf: data) } catch {
            failure = error
            dataTask.cancel()
            return
        }
        hasher.update(data)
        received += Int64(data.count)
        let now = Date()
        if now.timeIntervalSince(lastReport) > 0.2 {
            lastReport = now
            onProgress(received, total)
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
        onProgress(received, max(total, received))
        if expectedSize > 0 && received != expectedSize {
            if received > expectedSize { try? FileManager.default.removeItem(at: part) }
            throw FetchError.sizeMismatch(received, expectedSize)
        }
        guard hasher.hex() == digest else {
            try? FileManager.default.removeItem(at: part)
            throw FetchError.corrupted(hashKind)
        }
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.moveItem(at: part, to: destination)
    }
}
