import Foundation
import Network

/// Задержка серверов.
///
/// «Настоящая» задержка — время полного HTTPS-запроса через сервер: подключение, рукопожатие протокола,
/// ответ. Для этого запускается отдельное ядро-тестер, где у каждого сервера свой локальный порт,
/// и запросы идут параллельно. Это то, что почувствует пользователь, — в отличие от TCP-пинга, который
/// видит только, открыт ли порт.
nonisolated enum Ping {
    enum Result: Codable, Hashable, Sendable {
        case ms(Int)
        case timeout
        case unsupported

        var value: Int? { if case .ms(let v) = self { v } else { nil } }
    }

    /// Свободные порты для тестера — с запасом выше рабочих.
    static func freePorts(_ count: Int, from start: Int = 21300) -> [Int] {
        var ports: [Int] = []
        var p = start
        while ports.count < count && p < 65000 {
            if isFree(p) { ports.append(p) }
            p += 1
        }
        return ports
    }

    static func isFree(_ port: Int) -> Bool {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = in_port_t(UInt16(port).bigEndian)
        addr.sin_addr.s_addr = inet_addr("127.0.0.1")
        return withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        } == 0
    }

    /// Настоящая задержка для списка серверов. Результаты приходят по мере готовности.
    static func measure(_ servers: [Server], timeout: TimeInterval = 7,
                        onResult: @escaping @Sendable (String, Result) -> Void) async {
        let usable = servers.filter { $0.issue == nil }
        for s in servers where s.issue != nil { onResult(s.id, .unsupported) }
        // Пачками по 48 — тестер с сотней исходящих соединений не нужен.
        for batch in stride(from: 0, to: usable.count, by: 48).map({ Array(usable[$0..<min(usable.count, $0 + 48)]) }) {
            let ports = freePorts(batch.count)
            guard ports.count == batch.count else { break }
            let config = XrayConfig.json(XrayConfig.tester(Array(zip(batch, ports))))
            let tester = XrayProcess()
            do {
                try tester.start(config: config, assets: AppPaths.geo, name: "tester")
            } catch {
                LogBook.shared.add("Проверка задержки не запустилась: \(error)", level: .warning)
                for s in batch { onResult(s.id, .timeout) }
                continue
            }
            await withTaskGroup(of: Void.self) { group in
                var running = 0
                for (server, port) in zip(batch, ports) {
                    if running >= 12 { _ = await group.next(); running -= 1 }
                    running += 1
                    group.addTask {
                        // Первый запрос — с подключением; если не прошёл, вторая попытка.
                        var ms = await Probe.delay(httpPort: port, timeout: timeout)
                        if ms == nil { ms = await Probe.delay(httpPort: port, timeout: timeout) }
                        onResult(server.id, ms.map { .ms($0) } ?? .timeout)
                    }
                }
            }
            tester.stop()
        }
    }

    /// TCP-пинг: время установки соединения с портом сервера.
    static func tcp(host: String, port: Int, timeout: TimeInterval = 3) async -> Int? {
        guard let p = NWEndpoint.Port(rawValue: UInt16(clamping: port)) else { return nil }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: p, using: .tcp)
        let start = Date()
        let box = OnceBox()
        return await withCheckedContinuation { (cont: CheckedContinuation<Int?, Never>) in
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if box.claim() { cont.resume(returning: Int(Date().timeIntervalSince(start) * 1000)) }
                    connection.cancel()
                case .failed, .cancelled:
                    if box.claim() { cont.resume(returning: nil) }
                default: break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + timeout) {
                if box.claim() { cont.resume(returning: nil) }
                connection.cancel()
            }
        }
    }

    final class OnceBox: @unchecked Sendable {
        private let lock = NSLock()
        private var done = false
        func claim() -> Bool { lock.withLock { defer { done = true }; return !done } }
    }
}
