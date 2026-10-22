import Foundation

/// Помощник Veil — маленькая программа с правами root, которая умеет ровно три вещи: включить и выключить
/// системный прокси, запустить и остановить TUN-туннель (весь трафик Mac) и сообщить своё состояние.
/// Приложение говорит с ним через Unix-сокет строками JSON. Помощник отвечает только пользователю,
/// который его установил (проверка по uid собеседника).
public enum HelperInfo {
    public static let label = "app.veil.helper"
    /// Версия протокола и файлов помощника: если в приложении новее — помощник переустанавливается.
    public static let version = "3"
    public static let socketPath = "/var/run/app.veil.helper.sock"
    public static let directory = "/Library/PrivilegedHelperTools/app.veil"
    public static var helperPath: String { directory + "/VeilHelper" }
    /// Своя копия ядра, принадлежащая root: запускать с правами root файл, который может подменить
    /// обычный пользователь, нельзя.
    public static var corePath: String { directory + "/xray" }
    public static var assetsPath: String { directory + "/assets" }
    public static var statePath: String { directory + "/state.json" }
    public static let plistPath = "/Library/LaunchDaemons/app.veil.helper.plist"
}

public enum HelperCommand: String, Codable, Sendable {
    case status
    case proxyOn
    case proxyOff
    case tunStart
    case tunStop
}

public struct HelperRequest: Codable, Sendable {
    public var command: HelperCommand
    public var httpPort: Int?
    public var socksPort: Int?
    /// Адреса в обход системного прокси.
    public var bypass: [String]?
    /// Конфигурация ядра для TUN (помощник её очищает, см. `ConfigSanitizer`).
    public var config: String?
    /// Папка с файлами geoip.dat / geosite.dat пользователя — помощник копирует их к себе.
    public var assets: String?
    /// DNS-серверы на время TUN (пакеты к ним идут через туннель).
    public var dns: [String]?

    public init(command: HelperCommand, httpPort: Int? = nil, socksPort: Int? = nil, bypass: [String]? = nil,
                config: String? = nil, assets: String? = nil, dns: [String]? = nil) {
        self.command = command
        self.httpPort = httpPort
        self.socksPort = socksPort
        self.bypass = bypass
        self.config = config
        self.assets = assets
        self.dns = dns
    }
}

public struct HelperResponse: Codable, Sendable {
    public var ok: Bool
    public var message: String
    public var version: String
    public var proxyOn: Bool
    public var tunRunning: Bool
    /// Последние строки журнала ядра в режиме TUN.
    public var log: [String]

    public init(ok: Bool, message: String = "", proxyOn: Bool = false, tunRunning: Bool = false, log: [String] = []) {
        self.ok = ok
        self.message = message
        self.version = HelperInfo.version
        self.proxyOn = proxyOn
        self.tunRunning = tunRunning
        self.log = log
    }
}

/// Очистка конфигурации, которую ядро запустит с правами root.
///
/// Xray умеет писать файлы (журналы, ключи TLS) и читать произвольные (сертификаты, «ext:»-базы).
/// С правами root это была бы лазейка: кто угодно от имени пользователя мог бы через помощника
/// перезаписать системный файл. Поэтому такие поля удаляются, а ссылки на внешние файлы запрещены.
public enum ConfigSanitizer {
    static let forbiddenKeys: Set<String> = [
        "masterKeyLog", "certificateFile", "keyFile", "access", "error", "dnsLog", "api",
    ]

    public enum Failure: Error, CustomStringConvertible {
        case notJSON
        case externalFile(String)

        public var description: String {
            switch self {
            case .notJSON: "конфигурация — не JSON-объект"
            case .externalFile(let value): "ссылка на внешний файл запрещена: \(value)"
            }
        }
    }

    public static func sanitize(_ text: String) throws -> String {
        guard let data = text.data(using: .utf8),
              var root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure.notJSON }
        root = try clean(root) as? [String: Any] ?? [:]
        root["log"] = ["loglevel": "warning"]
        let out = try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
        return String(decoding: out, as: UTF8.self)
    }

    private static func clean(_ value: Any) throws -> Any {
        switch value {
        case let dict as [String: Any]:
            var result: [String: Any] = [:]
            for (key, item) in dict where !forbiddenKeys.contains(key) {
                result[key] = try clean(item)
            }
            return result
        case let array as [Any]:
            return try array.map(clean)
        case let string as String:
            let lower = string.lowercased()
            if lower.hasPrefix("ext:") || lower.hasPrefix("ext-domain:") || lower.hasPrefix("ext-ip:") || lower.hasPrefix("file:") {
                throw Failure.externalFile(string)
            }
            return string
        default:
            return value
        }
    }
}

/// Строки JSON поверх Unix-сокета: запрос — одна строка, ответ — одна строка.
public enum HelperSocket {
    public enum Failure: Error, CustomStringConvertible {
        case unavailable(String)
        case io(String)

        public var description: String {
            switch self {
            case .unavailable(let why): "помощник недоступен: \(why)"
            case .io(let why): "обмен с помощником: \(why)"
            }
        }
    }

    /// Запрос к помощнику (блокирующий; вызывать не на главном потоке).
    public static func send(_ request: HelperRequest, path: String = HelperInfo.socketPath, timeout: TimeInterval = 20) throws -> HelperResponse {
        let fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { throw Failure.unavailable(String(cString: strerror(errno))) }
        defer { close(fd) }
        var tv = timeval(tv_sec: Int(timeout), tv_usec: 0)
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var addr = sockaddr_un()
        guard fill(&addr, path: path) else { throw Failure.unavailable("слишком длинный путь сокета") }
        let connected = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { connect(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size)) }
        }
        guard connected == 0 else { throw Failure.unavailable(String(cString: strerror(errno))) }
        var data = try JSONEncoder().encode(request)
        data.append(0x0A)
        try writeAll(fd, data)
        guard let line = readLine(fd) else { throw Failure.io("нет ответа") }
        do {
            return try JSONDecoder().decode(HelperResponse.self, from: line)
        } catch {
            throw Failure.io("непонятный ответ")
        }
    }

    public static func fill(_ addr: inout sockaddr_un, path: String) -> Bool {
        addr.sun_family = sa_family_t(AF_UNIX)
        let bytes = Array(path.utf8)
        let capacity = MemoryLayout.size(ofValue: addr.sun_path)
        guard bytes.count < capacity else { return false }
        withUnsafeMutableBytes(of: &addr.sun_path) { raw in
            raw.copyBytes(from: bytes)
            raw[bytes.count] = 0
        }
        return true
    }

    public static func writeAll(_ fd: Int32, _ data: Data) throws {
        try data.withUnsafeBytes { raw in
            var offset = 0
            while offset < raw.count {
                let n = write(fd, raw.baseAddress! + offset, raw.count - offset)
                if n <= 0 { throw Failure.io(String(cString: strerror(errno))) }
                offset += n
            }
        }
    }

    /// Строка до перевода строки (не больше 4 МБ — конфигурация с маршрутами бывает большой).
    public static func readLine(_ fd: Int32) -> Data? {
        var result = Data()
        var buffer = [UInt8](repeating: 0, count: 65536)
        while result.count < 4 << 20 {
            let n = read(fd, &buffer, buffer.count)
            if n <= 0 { return result.isEmpty ? nil : result }
            if let newline = buffer[0..<n].firstIndex(of: 0x0A) {
                result.append(contentsOf: buffer[0..<newline])
                return result
            }
            result.append(contentsOf: buffer[0..<n])
        }
        return nil
    }
}
