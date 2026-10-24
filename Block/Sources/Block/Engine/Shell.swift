import Foundation

/// Запуск системных команд без оболочки (аргументы не склеиваются в строку).
nonisolated enum Shell {
    struct Result: Sendable {
        var status: Int32
        var output: String
        var ok: Bool { status == 0 }
    }

    @discardableResult
    static func run(_ tool: String, _ args: [String], input: String? = nil, timeout: Double = 20) -> Result {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: tool)
        process.arguments = args
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        if let input {
            let inPipe = Pipe()
            process.standardInput = inPipe
            inPipe.fileHandleForWriting.write(Data(input.utf8))
            try? inPipe.fileHandleForWriting.close()
        }
        do { try process.run() } catch { return Result(status: -1, output: error.localizedDescription) }
        // Зависшая команда не держит Block: через timeout секунд процесс останавливается.
        // Проверяем сам процесс, а не номер: номер завершившегося процесса может достаться другому.
        nonisolated(unsafe) let running = process
        DispatchQueue.global().asyncAfter(deadline: .now() + timeout) {
            if running.isRunning { kill(running.processIdentifier, SIGKILL) }
        }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        return Result(status: process.terminationStatus, output: String(decoding: data, as: UTF8.self))
    }

    static func killall(_ name: String) {
        run("/usr/bin/killall", [name])
    }

    /// Экранирование для `/bin/sh -c` (только для команд с правами администратора, где оболочка неизбежна).
    static func quote(_ s: String) -> String { "'" + s.replacingOccurrences(of: "'", with: "'\\''") + "'" }
}

/// Действия с правами администратора. Постоянного помощника нет: на каждое применение — один системный
/// запрос пароля для конкретного списка команд. В CI (`BLOCK_SUDO=1`) — `sudo -n` без запроса.
nonisolated enum Privileged {
    enum Failure: Error, CustomStringConvertible {
        case cancelled, failed(String)
        var description: String {
            switch self {
            case .cancelled: "Пароль не введён — системные изменения не сделаны"
            case .failed(let s): s
            }
        }
    }

    static var usesSudo: Bool { ProcessInfo.processInfo.environment["BLOCK_SUDO"] == "1" }

    /// Выполнить команды одной пачкой (одним запросом пароля). Команды — уже экранированные строки для sh.
    static func run(_ commands: [String], reason: String) -> Result<Void, Failure> {
        guard !commands.isEmpty else { return .success(()) }
        let script = commands.joined(separator: " && ")
        if usesSudo {
            let r = Shell.run("/usr/bin/sudo", ["-n", "/bin/sh", "-c", script])
            return r.ok ? .success(()) : .failure(.failed(r.output.trimmingCharacters(in: .whitespacesAndNewlines)))
        }
        let escaped = script.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
        let prompt = reason.replacingOccurrences(of: "\"", with: "'")
        let source = "do shell script \"\(escaped)\" with prompt \"\(prompt)\" with administrator privileges"
        var error: NSDictionary?
        let apple = NSAppleScript(source: source)
        _ = apple?.executeAndReturnError(&error)
        if let error {
            if (error[NSAppleScript.errorNumber] as? Int) == -128 { return .failure(.cancelled) }
            return .failure(.failed((error[NSAppleScript.errorMessage] as? String) ?? "Ошибка"))
        }
        return .success(())
    }
}
