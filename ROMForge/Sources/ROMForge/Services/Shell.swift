import Foundation

struct ShellResult: Sendable {
    let status: Int32
    let stdout: String
    let stderr: String

    var succeeded: Bool { status == 0 }
    /// adb и fastboot пишут половину полезного вывода в stderr — удобно смотреть сразу всё.
    var combined: String { stdout + (stderr.isEmpty ? "" : "\n" + stderr) }
}

enum ShellError: LocalizedError {
    case launchFailed(String)
    case nonZeroExit(command: String, status: Int32, output: String)

    var errorDescription: String? {
        switch self {
        case .launchFailed(let message):
            return "Не удалось запустить процесс: \(message)"
        case .nonZeroExit(let command, let status, let output):
            let tail = output.split(separator: "\n").suffix(4).joined(separator: "\n")
            return "Команда «\(command)» завершилась с кодом \(status).\n\(tail)"
        }
    }
}

/// Асинхронный запуск внешних процессов (adb, fastboot, ditto, bash) с построчной
/// трансляцией вывода — нужна для прогресса `adb sideload`, который печатает `\r`.
enum Shell {
    static func run(
        _ executable: URL,
        _ arguments: [String],
        environment: [String: String]? = nil,
        currentDirectory: URL? = nil,
        onLine: (@Sendable (String) -> Void)? = nil
    ) async throws -> ShellResult {
        let runner = ProcessRunner(onLine: onLine)
        return try await withTaskCancellationHandler {
            try await runner.run(executable, arguments, environment: environment, currentDirectory: currentDirectory)
        } onCancel: {
            runner.terminate()
        }
    }

    @discardableResult
    static func runChecked(
        _ executable: URL,
        _ arguments: [String],
        environment: [String: String]? = nil,
        currentDirectory: URL? = nil,
        onLine: (@Sendable (String) -> Void)? = nil
    ) async throws -> ShellResult {
        let result = try await run(executable, arguments, environment: environment,
                                   currentDirectory: currentDirectory, onLine: onLine)
        guard result.succeeded else {
            throw ShellError.nonZeroExit(
                command: ([executable.lastPathComponent] + arguments).joined(separator: " "),
                status: result.status,
                output: result.combined
            )
        }
        return result
    }
}

private final class ProcessRunner: @unchecked Sendable {
    private let lock = NSLock()
    private let process = Process()
    private let onLine: (@Sendable (String) -> Void)?
    private var stdoutData = Data()
    private var stderrData = Data()
    private var pendingLine = Data()
    private var finished = false

    init(onLine: (@Sendable (String) -> Void)?) {
        self.onLine = onLine
    }

    func terminate() {
        if process.isRunning { process.terminate() }
    }

    func run(
        _ executable: URL,
        _ arguments: [String],
        environment: [String: String]?,
        currentDirectory: URL?
    ) async throws -> ShellResult {
        let outPipe = Pipe()
        let errPipe = Pipe()
        process.executableURL = executable
        process.arguments = arguments
        process.standardOutput = outPipe
        process.standardError = errPipe
        process.standardInput = FileHandle.nullDevice
        if let currentDirectory { process.currentDirectoryURL = currentDirectory }
        if let environment {
            process.environment = ProcessInfo.processInfo.environment.merging(environment) { _, new in new }
        }

        return try await withCheckedThrowingContinuation { continuation in
            let group = DispatchGroup()
            group.enter()  // stdout EOF
            group.enter()  // stderr EOF
            group.enter()  // завершение процесса

            outPipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
                let chunk = handle.availableData
                if chunk.isEmpty {
                    handle.readabilityHandler = nil
                    group.leave()
                } else {
                    self?.append(chunk, isStdout: true)
                }
            }
            errPipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
                let chunk = handle.availableData
                if chunk.isEmpty {
                    handle.readabilityHandler = nil
                    group.leave()
                } else {
                    self?.append(chunk, isStdout: false)
                }
            }

            let complete: @Sendable () -> Void = { [self] in
                lock.lock()
                if finished { lock.unlock(); return }
                finished = true
                let result = ShellResult(
                    status: process.terminationStatus,
                    stdout: String(decoding: stdoutData, as: UTF8.self),
                    stderr: String(decoding: stderrData, as: UTF8.self)
                )
                let rest = pendingLine
                lock.unlock()
                if !rest.isEmpty { onLine?(String(decoding: rest, as: UTF8.self)) }
                continuation.resume(returning: result)
            }

            process.terminationHandler = { _ in
                group.leave()
                // Если процесс породил демон (например, `adb start-server`), он может
                // удерживать пайпы открытыми — не ждём EOF дольше пары секунд.
                DispatchQueue.global().asyncAfter(deadline: .now() + 2) {
                    outPipe.fileHandleForReading.readabilityHandler = nil
                    errPipe.fileHandleForReading.readabilityHandler = nil
                    complete()
                }
            }
            group.notify(queue: .global()) { complete() }

            do {
                try process.run()
            } catch {
                lock.lock()
                finished = true
                lock.unlock()
                outPipe.fileHandleForReading.readabilityHandler = nil
                errPipe.fileHandleForReading.readabilityHandler = nil
                continuation.resume(throwing: ShellError.launchFailed(error.localizedDescription))
            }
        }
    }

    private func append(_ chunk: Data, isStdout: Bool) {
        var lines: [String] = []
        lock.lock()
        if isStdout { stdoutData.append(chunk) } else { stderrData.append(chunk) }
        if onLine != nil {
            pendingLine.append(chunk)
            // Разбиваем и по \n, и по \r — adb рисует прогресс возвратом каретки.
            while let index = pendingLine.firstIndex(where: { $0 == 0x0A || $0 == 0x0D }) {
                let line = pendingLine[pendingLine.startIndex..<index]
                pendingLine.removeSubrange(pendingLine.startIndex...index)
                if !line.isEmpty { lines.append(String(decoding: line, as: UTF8.self)) }
            }
        }
        lock.unlock()
        lines.forEach { onLine?($0) }
    }
}
