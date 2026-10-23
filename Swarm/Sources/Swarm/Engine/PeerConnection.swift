import Foundation
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

nonisolated enum TransportKind: String, Sendable, Codable {
    case tcp, utp

    var title: String { self == .tcp ? "TCP" : "uTP" }
}

/// Поток байт до пира: TCP (Network.framework) или uTP поверх общего UDP-порта.
/// Колбэки могут приходить с любой очереди.
nonisolated protocol PeerTransport: AnyObject, Sendable {
    var kind: TransportKind { get }
    func start(onReady: @escaping @Sendable ((any Error)?) -> Void,
               onData: @escaping @Sendable (Data) -> Void,
               onClosed: @escaping @Sendable ((any Error)?) -> Void)
    func send(_ data: Data)
    func close()
}

/// Соединение с пиром поверх любого транспорта.
///
/// Всё состояние трогается только на собственной последовательной очереди: и приём байт от транспорта,
/// и чтения рукопожатия, и отправка (шифрование RC4 должно идти строго в порядке отправки).
/// После рукопожатия сообщения отдаются упорядоченным AsyncStream.
nonisolated final class PeerConnection: @unchecked Sendable {
    let address: PeerAddress
    let isIncoming: Bool
    let transport: any PeerTransport
    let counter = TransferCounter()

    private let queue: DispatchQueue
    private let info = OSAllocatedUnfairLock(initialState: false)
    // Состояние ниже — только на `queue`.
    private var buffer: [UInt8] = []
    private var head = 0
    private var encryptor: RC4?
    private var decryptor: RC4?
    private var waiter: Waiter?
    private var waiterSerial = 0
    private var continuation: AsyncThrowingStream<PeerMessage, any Error>.Continuation?
    private var streaming = false
    private var started = false
    private var closed = false
    private var closeError: (any Error)?

    private enum Need {
        case exact(Int, consume: Bool)
        case scan([UInt8], within: Int)
    }

    private struct Waiter {
        let id: Int
        let need: Need
        let cont: CheckedContinuation<Data, any Error>
    }

    init(address: PeerAddress, transport: any PeerTransport, incoming: Bool) {
        self.address = address
        self.transport = transport
        self.isIncoming = incoming
        queue = DispatchQueue(label: incoming ? "swarm.peer.in" : "swarm.peer")
    }

    var kind: TransportKind { transport.kind }

    /// Договорились ли о шифровании RC4 (MSE).
    var isEncrypted: Bool {
        get { info.withLock { $0 } }
        set { info.withLock { $0 = newValue } }
    }

    /// Запускает транспорт и ждёт установления соединения.
    func open(timeout: TimeInterval = 10) async throws {
        let once = Once()
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            queue.async {
                guard !self.started else { once.run { cont.resume() }; return }
                self.started = true
                self.transport.start(
                    onReady: { error in
                        if let error {
                            if once.run({ cont.resume(throwing: error) }) { self.close() }
                        } else {
                            once.run { cont.resume() }
                        }
                    },
                    onData: { [weak self] data in
                        guard let self else { return }
                        self.queue.async { self.received(data) }
                    },
                    onClosed: { [weak self] error in
                        once.run { cont.resume(throwing: error ?? PeerError("Соединение закрыто")) }
                        guard let self else { return }
                        self.queue.async { self.finish(error) }
                    })
            }
            queue.asyncAfter(deadline: .now() + timeout) { [weak self] in
                if once.run({ cont.resume(throwing: PeerError("Таймаут соединения")) }) { self?.close() }
            }
        }
    }

    // MARK: - Чтение рукопожатий

    /// Читает ровно `count` байт.
    func read(_ count: Int, timeout: TimeInterval = 15) async throws -> Data {
        try await wait(.exact(count, consume: true), timeout: timeout)
    }

    /// Смотрит первые `count` байт, не забирая их из буфера.
    func peek(_ count: Int, timeout: TimeInterval = 15) async throws -> Data {
        try await wait(.exact(count, consume: false), timeout: timeout)
    }

    /// Ищет `pattern` среди ближайших `within + pattern.count` байт и забирает всё до него включительно.
    /// Возвращает пропущенные перед образцом байты.
    func scan(for pattern: [UInt8], within: Int, timeout: TimeInterval = 15) async throws -> Data {
        try await wait(.scan(pattern, within: within), timeout: timeout)
    }

    /// Возвращает байты в начало буфера (MSE: расшифрованная начальная нагрузка IA).
    func unread(_ data: Data) {
        queue.async {
            self.buffer = [UInt8](data) + self.buffer[self.head...]
            self.head = 0
            self.process()
        }
    }

    /// Включает RC4. Уже принятые, но ещё не прочитанные байты расшифровываются на месте.
    func setCiphers(encrypt: RC4?, decrypt: RC4?) {
        queue.async {
            self.encryptor = encrypt
            if let decrypt {
                if self.head < self.buffer.count {
                    var rest = Array(self.buffer[self.head...])
                    decrypt.process(&rest)
                    self.buffer = rest
                    self.head = 0
                }
                self.decryptor = decrypt
            }
        }
        isEncrypted = encrypt != nil
    }

    private func wait(_ need: Need, timeout: TimeInterval) async throws -> Data {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, any Error>) in
            queue.async {
                if self.closed {
                    cont.resume(throwing: self.closeError ?? PeerError("Пир закрыл соединение"))
                    return
                }
                self.waiterSerial += 1
                let id = self.waiterSerial
                self.waiter = Waiter(id: id, need: need, cont: cont)
                self.process()
                self.queue.asyncAfter(deadline: .now() + timeout) {
                    guard let w = self.waiter, w.id == id else { return }
                    self.waiter = nil
                    w.cont.resume(throwing: PeerError("Пир не ответил на рукопожатие"))
                }
            }
        }
    }

    // MARK: - Отправка

    func sendRaw(_ data: Data) {
        counter.addUp(data.count)
        queue.async {
            guard !self.closed else { return }
            self.transport.send(self.encryptor.map { $0.apply(data) } ?? data)
        }
    }

    func send(_ message: PeerMessage) {
        sendRaw(message.encoded)
    }

    // MARK: - Поток сообщений

    /// Поток сообщений пира. Вызывать один раз, после рукопожатия.
    func messages() -> AsyncThrowingStream<PeerMessage, any Error> {
        AsyncThrowingStream { continuation in
            queue.async {
                self.continuation = continuation
                self.streaming = true
                if self.closed {
                    continuation.finish(throwing: self.closeError)
                    return
                }
                self.process()
            }
            continuation.onTermination = { [weak self] _ in self?.close() }
        }
    }

    func close() {
        queue.async {
            guard !self.closed else { return }
            self.transport.close()
            self.finish(nil)
        }
    }

    // MARK: - На очереди

    private func received(_ data: Data) {
        guard !closed else { return }
        counter.addDown(data.count)
        if let decryptor {
            var bytes = [UInt8](data)
            decryptor.process(&bytes)
            buffer.append(contentsOf: bytes)
        } else {
            buffer.append(contentsOf: data)
        }
        process()
    }

    private func finish(_ error: (any Error)?) {
        guard !closed else { return }
        closed = true
        closeError = error
        if let w = waiter {
            waiter = nil
            w.cont.resume(throwing: error ?? PeerError("Пир закрыл соединение"))
        }
        if let error { continuation?.finish(throwing: error) } else { continuation?.finish() }
        continuation = nil
        transport.close()
    }

    private func process() {
        if let w = waiter {
            switch w.need {
            case .exact(let n, let consume):
                if buffer.count - head >= n {
                    waiter = nil
                    let out = Data(buffer[head..<(head + n)])
                    if consume { head += n }
                    w.cont.resume(returning: out)
                }
            case .scan(let pattern, let within):
                let available = buffer.count - head
                if let at = find(pattern, limit: within) {
                    waiter = nil
                    let skipped = Data(buffer[head..<(head + at)])
                    head += at + pattern.count
                    w.cont.resume(returning: skipped)
                } else if available >= within + pattern.count {
                    waiter = nil
                    w.cont.resume(throwing: PeerError("Шифрование: нет метки синхронизации"))
                    close()
                }
            }
        }
        if streaming, waiter == nil { drain() }
        compact()
    }

    private func find(_ pattern: [UInt8], limit: Int) -> Int? {
        let available = buffer.count - head
        guard available >= pattern.count else { return nil }
        let last = min(limit, available - pattern.count)
        var i = 0
        while i <= last {
            var match = true
            for k in 0..<pattern.count where buffer[head + i + k] != pattern[k] {
                match = false
                break
            }
            if match { return i }
            i += 1
        }
        return nil
    }

    private func compact() {
        if head > 1 << 20 || head == buffer.count {
            buffer.removeFirst(head)
            head = 0
        }
    }

    /// Вынимает из буфера все целые сообщения.
    private func drain() {
        while buffer.count - head >= 4 {
            let length = Int(UInt32(buffer[head]) << 24 | UInt32(buffer[head + 1]) << 16 | UInt32(buffer[head + 2]) << 8 | UInt32(buffer[head + 3]))
            guard length <= 4 * 1024 * 1024 else {
                continuation?.finish(throwing: PeerError("Слишком большое сообщение"))
                close()
                return
            }
            guard buffer.count - head >= 4 + length else { break }
            let body = Array(buffer[(head + 4)..<(head + 4 + length)])
            head += 4 + length
            if let message = PeerMessage.decode(body) {
                continuation?.yield(message)
            }
        }
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
