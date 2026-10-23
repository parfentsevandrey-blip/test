import Foundation
import Network

/// Крошечный HTTP-сервер для самопроверки веб-сидов: отдаёт файлы папки с поддержкой Range.
nonisolated final class RangeServer: @unchecked Sendable {
    private let listener: NWListener
    private let root: URL
    private let queue = DispatchQueue(label: "swarm.rangeserver")
    private(set) var port: UInt16 = 0
    private(set) var served = 0

    private init(root: URL) throws {
        self.root = root
        let params = NWParameters.tcp
        params.requiredInterfaceType = .loopback
        listener = try NWListener(using: params, on: .any)
    }

    static func start(root: URL) async throws -> RangeServer {
        let server = try RangeServer(root: root)
        try await server.run()
        return server
    }

    private func run() async throws {
        let once = Once()
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            listener.newConnectionHandler = { [weak self] c in self?.handle(c) }
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.port = self?.listener.port?.rawValue ?? 0
                    once.run { cont.resume() }
                case .failed(let e): once.run { cont.resume(throwing: e) }
                default: break
                }
            }
            listener.start(queue: queue)
        }
    }

    func stop() { listener.cancel() }

    private func handle(_ c: NWConnection) {
        c.start(queue: queue)
        receive(c, Data())
    }

    private func receive(_ c: NWConnection, _ buffer: Data) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, done, error in
            guard let self else { return }
            var buffer = buffer
            if let data { buffer.append(data) }
            if let end = buffer.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(decoding: buffer[..<end.lowerBound], as: UTF8.self)
                self.respond(c, head)
                self.receive(c, Data(buffer[end.upperBound...]))
                return
            }
            if done || error != nil { c.cancel(); return }
            self.receive(c, buffer)
        }
    }

    private func respond(_ c: NWConnection, _ head: String) {
        let lines = head.components(separatedBy: "\r\n")
        let parts = lines.first?.split(separator: " ") ?? []
        guard parts.count >= 2 else { c.cancel(); return }
        let path = String(parts[1]).removingPercentEncoding ?? String(parts[1])
        let file = path.split(separator: "/").reduce(root) { $0.appendingPathComponent(String($1)) }
        guard !path.contains(".."), let data = try? Data(contentsOf: file) else {
            send(c, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n", Data())
            return
        }
        served += 1
        if let range = lines.first(where: { $0.lowercased().hasPrefix("range: bytes=") }) {
            let spec = range.dropFirst("range: bytes=".count).split(separator: "-").compactMap { Int($0) }
            guard spec.count == 2, spec[0] <= spec[1], spec[1] < data.count else {
                send(c, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\n\r\n", Data())
                return
            }
            let body = data.subdata(in: spec[0]..<(spec[1] + 1))
            send(c, "HTTP/1.1 206 Partial Content\r\nContent-Length: \(body.count)\r\nContent-Range: bytes \(spec[0])-\(spec[1])/\(data.count)\r\n\r\n", body)
        } else {
            send(c, "HTTP/1.1 200 OK\r\nContent-Length: \(data.count)\r\n\r\n", data)
        }
    }

    private func send(_ c: NWConnection, _ header: String, _ body: Data) {
        c.send(content: Data(header.utf8) + body, completion: .contentProcessed { _ in })
    }
}
