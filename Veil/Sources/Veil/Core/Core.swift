import Foundation

/// Журнал ядра и приложения: последние строки, без файлов на диске.
nonisolated final class LogBook: @unchecked Sendable {
    struct Entry: Identifiable, Sendable {
        let id: Int
        let date: Date
        let text: String
        let level: Level
    }

    enum Level: Sendable { case info, warning, error }

    static let shared = LogBook()
    private let lock = NSLock()
    private var entries: [Entry] = []
    private var counter = 0
    var onChange: (@Sendable () -> Void)?

    func add(_ text: String, level: Level = .info) {
        let line = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !line.isEmpty else { return }
        lock.withLock {
            counter += 1
            entries.append(Entry(id: counter, date: Date(), text: line, level: level))
            if entries.count > 600 { entries.removeFirst(entries.count - 600) }
        }
        onChange?()
    }

    /// Строка журнала Xray: уровень по метке «[Warning]», «[Error]».
    func core(_ line: String) {
        let level: Level = line.contains("[Error]") || line.contains("failed") || line.contains("Failed") ? .error
            : line.contains("[Warning]") ? .warning : .info
        // Предупреждения об устаревших протоколах — шум для пользователя.
        if line.contains("is deprecated, not recommended") { return }
        add(line.replacingOccurrences(of: #"^\d{4}/\d\d/\d\d \d\d:\d\d:\d\d(\.\d+)? "#, with: "", options: .regularExpression), level: level)
    }

    var all: [Entry] { lock.withLock { entries } }

    var lastError: String? {
        lock.withLock { entries.last(where: { $0.level == .error })?.text }
    }

    func clear() { lock.withLock { entries.removeAll() } }
}

/// Ядро Xray — отдельной программой из бандла. Упало — Veil узнаёт об этом и перезапускает.
nonisolated final class XrayProcess: @unchecked Sendable {
    enum Failure: Error, CustomStringConvertible {
        case missingBinary
        case failed(String)

        var description: String {
            switch self {
            case .missingBinary: "В приложении нет ядра Xray — переустановите Veil"
            case .failed(let why): why
            }
        }
    }

    private let lock = NSLock()
    private var process: Process?
    private var stopping = false
    /// Неожиданное завершение ядра (код выхода).
    var onExit: (@Sendable (Int32) -> Void)?

    var running: Bool { lock.withLock { process?.isRunning ?? false } }

    /// Запуск с ожиданием строки «Xray … started» (или ошибки) не дольше 8 секунд.
    func start(config: String, assets: URL, name: String = "core") throws {
        stop()
        let binary = AppPaths.xray
        guard FileManager.default.isExecutableFile(atPath: binary.path) else { throw Failure.missingBinary }
        AppPaths.ensure(AppPaths.runtime)
        let file = AppPaths.runtime.appendingPathComponent("\(name).json")
        FileManager.default.createFile(atPath: file.path, contents: Data(config.utf8), attributes: [.posixPermissions: 0o600])

        let p = Process()
        p.executableURL = binary
        p.arguments = ["run", "-c", file.path]
        var env = ProcessInfo.processInfo.environment
        env["XRAY_LOCATION_ASSET"] = assets.path
        p.environment = env
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe

        let started = DispatchSemaphore(value: 0)
        let box = StartBox()
        pipe.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            guard !data.isEmpty else { handle.readabilityHandler = nil; return }
            for line in String(decoding: data, as: UTF8.self).split(separator: "\n") {
                let text = String(line)
                if name == "core" { LogBook.shared.core(text) }
                if text.contains(" started") && text.contains("Xray") { box.signal(started) }
                if text.contains("Failed to start") || text.contains("failed to") || text.contains("Failed to") { box.failure = text }
            }
        }
        p.terminationHandler = { [weak self] proc in
            box.signal(started)
            guard let self else { return }
            let expected = self.lock.withLock { () -> Bool in
                let mine = self.process === proc
                if mine { self.process = nil }
                return self.stopping || !mine
            }
            if !expected { self.onExit?(proc.terminationStatus) }
        }
        lock.withLock {
            stopping = false
            process = p
        }
        do { try p.run() } catch {
            lock.withLock { process = nil }
            throw Failure.failed("Не удалось запустить ядро: \(error.localizedDescription)")
        }
        _ = started.wait(timeout: .now() + 8)
        if !p.isRunning {
            let why = box.failure ?? LogBook.shared.lastError ?? "ядро завершилось с кодом \(p.terminationStatus)"
            throw Failure.failed(Self.explain(why))
        }
    }

    func stop() {
        let p: Process? = lock.withLock {
            stopping = true
            defer { process = nil }
            return process
        }
        guard let p, p.isRunning else { return }
        p.terminate()
        let deadline = Date().addingTimeInterval(3)
        while p.isRunning && Date() < deadline { usleep(20_000) }
        if p.isRunning { kill(p.processIdentifier, SIGKILL) }
    }

    /// Проверка конфигурации без запуска (`xray run -test`).
    static func test(config: String, assets: URL) -> (ok: Bool, message: String) {
        let binary = AppPaths.xray
        guard FileManager.default.isExecutableFile(atPath: binary.path) else { return (false, Failure.missingBinary.description) }
        AppPaths.ensure(AppPaths.runtime)
        let file = AppPaths.runtime.appendingPathComponent("test-\(UUID().uuidString.prefix(8)).json")
        FileManager.default.createFile(atPath: file.path, contents: Data(config.utf8), attributes: [.posixPermissions: 0o600])
        defer { try? FileManager.default.removeItem(at: file) }
        let p = Process()
        p.executableURL = binary
        p.arguments = ["run", "-test", "-c", file.path]
        var env = ProcessInfo.processInfo.environment
        env["XRAY_LOCATION_ASSET"] = assets.path
        p.environment = env
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        do { try p.run() } catch { return (false, error.localizedDescription) }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        let text = String(decoding: data, as: UTF8.self)
        return (p.terminationStatus == 0, text.split(separator: "\n").suffix(3).joined(separator: "\n"))
    }

    static func version() -> String {
        let p = Process()
        p.executableURL = AppPaths.xray
        p.arguments = ["version"]
        let pipe = Pipe()
        p.standardOutput = pipe
        guard (try? p.run()) != nil else { return "" }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        // «Xray 26.9.30 (Xray, Penetrates Everything.) …»
        return String(decoding: data, as: UTF8.self).split(separator: " ").dropFirst().first.map(String.init) ?? ""
    }

    /// Ошибки ядра — человеческими словами.
    static func explain(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower.contains("address already in use") {
            return "Порт уже занят другой программой (возможно, другой VPN-клиент). Закройте её или смените порты в настройках."
        }
        if lower.contains("geosite") || lower.contains("geoip") { return "Не найдены базы сайтов для маршрутизации — они скачаются автоматически" }
        if lower.contains("operation not permitted") && lower.contains("tun") { return "Для режима TUN нужен помощник Veil" }
        return raw.replacingOccurrences(of: #"^.*?\] "#, with: "", options: .regularExpression)
    }

    private final class StartBox: @unchecked Sendable {
        private let lock = NSLock()
        private var signalled = false
        var failure: String? {
            get { lock.withLock { _failure } }
            set { lock.withLock { _failure = newValue } }
        }
        private var _failure: String?

        func signal(_ s: DispatchSemaphore) {
            let first = lock.withLock { () -> Bool in
                defer { signalled = true }
                return !signalled
            }
            if first { s.signal() }
        }
    }
}

/// Счётчики трафика ядра (`/debug/vars` метрик Xray).
nonisolated enum CoreStats {
    struct Sample: Sendable {
        var up: Int64
        var down: Int64
    }

    /// Сессия без системного прокси: запросы к самому себе не должны идти через прокси.
    static let direct: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.connectionProxyDictionary = [:]
        c.timeoutIntervalForRequest = 2
        return URLSession(configuration: c)
    }()

    static func sample(port: Int) async -> Sample? {
        guard let url = URL(string: "http://127.0.0.1:\(port)/debug/vars"),
              let (data, _) = try? await direct.data(from: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let stats = obj["stats"] as? [String: Any],
              let outbound = stats["outbound"] as? [String: Any] else { return nil }
        var up: Int64 = 0, down: Int64 = 0
        // Через VPN — только «proxy»; «direct» — то, что пошло мимо по правилам.
        if let proxy = outbound["proxy"] as? [String: Any] {
            up += (proxy["uplink"] as? NSNumber)?.int64Value ?? 0
            down += (proxy["downlink"] as? NSNumber)?.int64Value ?? 0
        }
        return Sample(up: up, down: down)
    }
}

/// Проверки соединения через локальный HTTP-прокси ядра.
nonisolated enum Probe {
    static func session(httpPort: Int, timeout: TimeInterval) -> URLSession {
        let c = URLSessionConfiguration.ephemeral
        c.connectionProxyDictionary = [
            kCFNetworkProxiesHTTPEnable as String: 1,
            kCFNetworkProxiesHTTPProxy as String: "127.0.0.1",
            kCFNetworkProxiesHTTPPort as String: httpPort,
            kCFNetworkProxiesHTTPSEnable as String: 1,
            kCFNetworkProxiesHTTPSProxy as String: "127.0.0.1",
            kCFNetworkProxiesHTTPSPort as String: httpPort,
        ]
        c.timeoutIntervalForRequest = timeout
        c.timeoutIntervalForResource = timeout
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: c)
    }

    static let checkURL = URL(string: "https://www.gstatic.com/generate_204")!

    /// Время полного запроса через прокси (с рукопожатием), мс; nil — не прошёл.
    static func delay(httpPort: Int, timeout: TimeInterval = 7, url: URL = checkURL) async -> Int? {
        let s = session(httpPort: httpPort, timeout: timeout)
        defer { s.invalidateAndCancel() }
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        let start = Date()
        guard let (_, response) = try? await s.data(for: request),
              let code = (response as? HTTPURLResponse)?.statusCode, (200..<400).contains(code) else { return nil }
        return Int(Date().timeIntervalSince(start) * 1000)
    }

    struct Exit: Sendable, Equatable {
        var ip: String
        var country: String
    }

    /// Внешний адрес и страна выхода (Cloudflare trace).
    static func exit(httpPort: Int) async -> Exit? {
        let s = session(httpPort: httpPort, timeout: 8)
        defer { s.invalidateAndCancel() }
        guard let url = URL(string: "https://www.cloudflare.com/cdn-cgi/trace"),
              let (data, _) = try? await s.data(from: url) else { return nil }
        var ip = "", loc = ""
        for line in String(decoding: data, as: UTF8.self).split(separator: "\n") {
            if line.hasPrefix("ip=") { ip = String(line.dropFirst(3)) }
            if line.hasPrefix("loc=") { loc = String(line.dropFirst(4)) }
        }
        return ip.isEmpty ? nil : Exit(ip: ip, country: loc)
    }
}
