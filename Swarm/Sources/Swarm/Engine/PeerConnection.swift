import Foundation
import Network
import os

/// Счётчик переданных байт, общий для потока сети и актора раздачи.
nonisolated final class TransferCounter: Sendable {
    private let state = OSAllocatedUnfairLock(initialState: (down: Int64(0), up: Int64(0)))
    func addDown(_ n: Int) { state.withLock { $0.down += Int64(n) } }
    func addUp(_ n: Int) { state.withLock { $0.up += Int64(n) } }
    var totals: (down: Int64, up: Int64) { state.withLock { ($0.down, $0.up) } }
}

nonisolated struct PeerError: LocalizedError, Sendable {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/// TCP-соединение с пиром на Network.framework.
///
/// Все колбэки NWConnection создаются внутри этого nonisolated-класса: они вызываются
/// на очереди соединения, и привязка к главному актору уронила бы процесс.
/// Входящие сообщения отдаются упорядоченным AsyncStream.
nonisolated final class PeerConnection: @unchecked Sendable {
    let address: PeerAddress
    let isIncoming: Bool
    let counter = TransferCounter()

    private let connection: NWConnection
    private let queue: DispatchQueue
    // Состояние ниже трогается только на `queue`.
    private var buffer: [UInt8] = []
    private var head = 0
    private var continuation: AsyncThrowingStream<PeerMessage, any Error>.Continuation?
    private var closed = false

    init(address: PeerAddress) {
        self.address = address
        isIncoming = false
        queue = DispatchQueue(label: "swarm.peer")
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        tcp.connectionTimeout = 8
        tcp.enableKeepalive = true
        let params = NWParameters(tls: nil, tcp: tcp)
        let port = NWEndpoint.Port(rawValue: address.port) ?? .any
        connection = NWConnection(host: NWEndpoint.Host(address.host), port: port, using: params)
    }

    init(incoming: NWConnection) {
        isIncoming = true
        queue = DispatchQueue(label: "swarm.peer.in")
        connection = incoming
        if case .hostPort(let host, let port) = incoming.endpoint {
            var text = "\(host)"
            if let pct = text.firstIndex(of: "%") { text = String(text[..<pct]) }
            address = PeerAddress(host: text, port: port.rawValue)
        } else {
            address = PeerAddress(host: "?", port: 0)
        }
    }

    /// Ждёт установления соединения.
    func open(timeout: TimeInterval = 10) async throws {
        let once = Once()
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            connection.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    once.run { cont.resume() }
                case .failed(let error), .waiting(let error):
                    once.run { cont.resume(throwing: error) }
                    self?.close()
                case .cancelled:
                    once.run { cont.resume(throwing: PeerError("Соединение закрыто")) }
                default:
                    break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + timeout) { [weak self] in
                if once.run({ cont.resume(throwing: PeerError("Таймаут соединения")) }) { self?.close() }
            }
        }
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled: self?.close()
            default: break
            }
        }
    }

    /// Читает ровно `count` байт (используется только для рукопожатия).
    func read(_ count: Int, timeout: TimeInterval = 15) async throws -> Data {
        let once = Once()
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, any Error>) in
            connection.receive(minimumIncompleteLength: count, maximumLength: count) { [weak self] data, _, isComplete, error in
                if let data, data.count == count {
                    self?.counter.addDown(data.count)
                    once.run { cont.resume(returning: data) }
                } else {
                    once.run { cont.resume(throwing: error ?? PeerError(isComplete ? "Пир закрыл соединение" : "Обрыв рукопожатия")) }
                }
            }
            queue.asyncAfter(deadline: .now() + timeout) {
                once.run { cont.resume(throwing: PeerError("Пир не ответил на рукопожатие")) }
            }
        }
    }

    func sendRaw(_ data: Data) {
        counter.addUp(data.count)
        connection.send(content: data, completion: .contentProcessed { [weak self] error in
            if error != nil { self?.close() }
        })
    }

    func send(_ message: PeerMessage) {
        sendRaw(message.encoded)
    }

    /// Поток сообщений пира. Вызывать один раз, после рукопожатия.
    func messages() -> AsyncThrowingStream<PeerMessage, any Error> {
        AsyncThrowingStream { continuation in
            queue.async {
                self.continuation = continuation
                if self.closed {
                    continuation.finish()
                    return
                }
                self.receiveNext()
            }
            continuation.onTermination = { [weak self] _ in self?.close() }
        }
    }

    func close() {
        queue.async {
            guard !self.closed else { return }
            self.closed = true
            self.connection.cancel()
            self.continuation?.finish()
            self.continuation = nil
        }
    }

    private func receiveNext() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 256 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.counter.addDown(data.count)
                self.buffer.append(contentsOf: data)
                if !self.drain() { return }
            }
            if let error {
                self.continuation?.finish(throwing: error)
                self.close()
                return
            }
            if isComplete {
                self.close()
                return
            }
            self.receiveNext()
        }
    }

    /// Вынимает из буфера все целые сообщения. false — протокол нарушен.
    private func drain() -> Bool {
        while buffer.count - head >= 4 {
            let length = Int(UInt32(buffer[head]) << 24 | UInt32(buffer[head + 1]) << 16 | UInt32(buffer[head + 2]) << 8 | UInt32(buffer[head + 3]))
            guard length <= 4 * 1024 * 1024 else {
                continuation?.finish(throwing: PeerError("Слишком большое сообщение"))
                close()
                return false
            }
            guard buffer.count - head >= 4 + length else { break }
            let body = Array(buffer[(head + 4)..<(head + 4 + length)])
            head += 4 + length
            if let message = PeerMessage.decode(body) {
                continuation?.yield(message)
            }
        }
        if head > 1 << 20 || head == buffer.count {
            buffer.removeFirst(head)
            head = 0
        }
        return true
    }
}

/// Однократный вызов из любого потока (для continuation с таймаутом).
nonisolated final class Once: Sendable {
    private let done = OSAllocatedUnfairLock(initialState: false)

    /// Выполняет `body`, только если это первый вызов. Возвращает, был ли он первым.
    @discardableResult
    func run(_ body: () -> Void) -> Bool {
        let first = done.withLock { d -> Bool in
            if d { return false }
            d = true
            return true
        }
        if first { body() }
        return first
    }
}

/// UDP-обмен «запрос → ответ» для трекеров (BEP 15) и DHT (BEP 5).
nonisolated final class UDPSession: @unchecked Sendable {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "swarm.udp")

    init(host: String, port: UInt16) {
        connection = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port) ?? .any, using: .udp)
    }

    func start(timeout: TimeInterval) async throws {
        let once = Once()
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: once.run { cont.resume() }
                case .failed(let e), .waiting(let e): once.run { cont.resume(throwing: e) }
                case .cancelled: once.run { cont.resume(throwing: PeerError("UDP закрыт")) }
                default: break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + timeout) {
                once.run { cont.resume(throwing: PeerError("UDP таймаут")) }
            }
        }
    }

    func exchange(_ payload: Data, timeout: TimeInterval, accept: @escaping @Sendable (Data) -> Bool) async throws -> Data {
        let once = Once()
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, any Error>) in
            connection.send(content: payload, completion: .contentProcessed { error in
                if let error { once.run { cont.resume(throwing: error) } }
            })
            receive(once: once, accept: accept, cont: cont)
            queue.asyncAfter(deadline: .now() + timeout) {
                once.run { cont.resume(throwing: PeerError("Нет ответа")) }
            }
        }
    }

    private func receive(once: Once, accept: @escaping @Sendable (Data) -> Bool, cont: CheckedContinuation<Data, any Error>) {
        connection.receiveMessage { [weak self] data, _, _, error in
            if let data, accept(data) {
                once.run { cont.resume(returning: data) }
            } else if let error {
                once.run { cont.resume(throwing: error) }
            } else {
                self?.receive(once: once, accept: accept, cont: cont)
            }
        }
    }

    func close() { connection.cancel() }
}
