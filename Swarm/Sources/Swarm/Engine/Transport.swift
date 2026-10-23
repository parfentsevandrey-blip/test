import Foundation
import Network

/// TCP-транспорт на Network.framework. Колбэки NWConnection создаются внутри nonisolated-класса:
/// они вызываются на очереди соединения, и привязка к главному актору уронила бы процесс.
nonisolated final class TCPTransport: PeerTransport, @unchecked Sendable {
    let kind = TransportKind.tcp
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "swarm.tcp")

    init(address: PeerAddress) {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        tcp.connectionTimeout = 8
        tcp.enableKeepalive = true
        let params = NWParameters(tls: nil, tcp: tcp)
        let port = NWEndpoint.Port(rawValue: address.port) ?? .any
        connection = NWConnection(host: NWEndpoint.Host(address.host), port: port, using: params)
    }

    init(incoming: NWConnection) {
        connection = incoming
    }

    static func address(of connection: NWConnection) -> PeerAddress {
        guard case .hostPort(let host, let port) = connection.endpoint else { return PeerAddress(host: "?", port: 0) }
        var text = "\(host)"
        if let pct = text.firstIndex(of: "%") { text = String(text[..<pct]) }
        if text.hasPrefix("::ffff:"), !text.dropFirst(7).contains(":") { text = String(text.dropFirst(7)) }
        return PeerAddress(host: text, port: port.rawValue)
    }

    func start(onReady: @escaping @Sendable ((any Error)?) -> Void,
               onData: @escaping @Sendable (Data) -> Void,
               onClosed: @escaping @Sendable ((any Error)?) -> Void) {
        let ready = Once()
        let closed = Once()
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                if ready.run({ onReady(nil) }) { self?.receive(onData: onData, onClosed: onClosed, closed: closed) }
            case .failed(let error), .waiting(let error):
                if !ready.run({ onReady(error) }) { closed.run { onClosed(error) } }
                self?.connection.cancel()
            case .cancelled:
                if !ready.run({ onReady(PeerError("Соединение закрыто")) }) { closed.run { onClosed(nil) } }
            default:
                break
            }
        }
        connection.start(queue: queue)
    }

    private func receive(onData: @escaping @Sendable (Data) -> Void, onClosed: @escaping @Sendable ((any Error)?) -> Void, closed: Once) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 256 * 1024) { [weak self] data, _, isComplete, error in
            if let data, !data.isEmpty { onData(data) }
            if let error {
                closed.run { onClosed(error) }
                self?.connection.cancel()
                return
            }
            if isComplete {
                closed.run { onClosed(nil) }
                self?.connection.cancel()
                return
            }
            self?.receive(onData: onData, onClosed: onClosed, closed: closed)
        }
    }

    func send(_ data: Data) {
        connection.send(content: data, completion: .contentProcessed { [weak self] error in
            if error != nil { self?.connection.cancel() }
        })
    }

    func close() { connection.cancel() }
}

/// Приём входящих TCP-соединений.
nonisolated final class PeerListener: @unchecked Sendable {
    private let listener: NWListener
    let port: UInt16

    init(port: UInt16, loopbackOnly: Bool = false, onConnection: @escaping @Sendable (PeerConnection) -> Void) throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        if loopbackOnly { params.requiredInterfaceType = .loopback }
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { throw PeerError("Неверный порт") }
        listener = try NWListener(using: params, on: nwPort)
        self.port = port
        listener.newConnectionHandler = { connection in
            onConnection(PeerConnection(address: TCPTransport.address(of: connection),
                                        transport: TCPTransport(incoming: connection), incoming: true))
        }
    }

    func start() async throws {
        let once = Once()
        let queue = DispatchQueue(label: "swarm.listener")
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready: once.run { cont.resume() }
                case .failed(let e), .waiting(let e): once.run { cont.resume(throwing: e) }
                case .cancelled: once.run { cont.resume(throwing: PeerError("Порт закрыт")) }
                default: break
                }
            }
            listener.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 5) { once.run { cont.resume(throwing: PeerError("Порт не открылся")) } }
        }
    }

    func cancel() { listener.cancel() }
}

/// UDP-обмен «запрос → ответ» для трекеров (BEP 15).
nonisolated final class UDPSession: @unchecked Sendable {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "swarm.udp.tracker")

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
