import Foundation
import Network

/// Local sink for the padding loop: reads dummy frames and answers each one with the requested
/// amount of noise after the requested delay. It is only ever reached through Tor via Veil's own
/// private onion service (or directly in demo mode).
final class PaddingServer: @unchecked Sendable {
    private let queue = DispatchQueue(label: "app.veilvpn.padding.server")
    private var listener: NWListener?
    private var connections: [ObjectIdentifier: NWConnection] = [:]
    private var buffers: [ObjectIdentifier: Data] = [:]
    private(set) var port: UInt16 = 0

    /// 64 KiB of random bytes, sliced to build replies (Tor encrypts everything anyway).
    static let noise: Data = {
        var data = Data(count: 65_536)
        for index in 0..<data.count {
            data[index] = UInt8.random(in: UInt8.min...UInt8.max)
        }
        return data
    }()

    func start() async throws -> UInt16 {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<UInt16, Error>) in
            queue.async {
                let parameters = NWParameters.tcp
                parameters.allowLocalEndpointReuse = true
                parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: "127.0.0.1", port: .any)
                let listener: NWListener
                do {
                    listener = try NWListener(using: parameters)
                } catch {
                    continuation.resume(throwing: error)
                    return
                }
                var resumed = false
                listener.stateUpdateHandler = { [weak self] state in
                    switch state {
                    case .ready:
                        let port = listener.port?.rawValue ?? 0
                        self?.port = port
                        if !resumed {
                            resumed = true
                            continuation.resume(returning: port)
                        }
                    case .failed(let error):
                        if !resumed {
                            resumed = true
                            continuation.resume(throwing: error)
                        }
                    case .cancelled:
                        if !resumed {
                            resumed = true
                            continuation.resume(throwing: PaddingError.cancelled)
                        }
                    default:
                        break
                    }
                }
                listener.newConnectionHandler = { [weak self] connection in
                    self?.accept(connection)
                }
                self.listener = listener
                listener.start(queue: self.queue)
            }
        }
    }

    func stop() {
        queue.async {
            self.listener?.cancel()
            self.listener = nil
            self.connections.values.forEach { $0.cancel() }
            self.connections = [:]
            self.buffers = [:]
        }
    }

    private func accept(_ connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        connections[id] = connection
        buffers[id] = Data()
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled:
                self?.queue.async {
                    self?.connections[id] = nil
                    self?.buffers[id] = nil
                }
            default:
                break
            }
        }
        connection.start(queue: queue)
        receive(on: connection, id: id)
    }

    private func receive(on connection: NWConnection, id: ObjectIdentifier) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty, self.buffers[id] != nil {
                self.buffers[id]!.append(data)
                self.drainFrames(on: connection, id: id)
            }
            if isComplete || error != nil {
                connection.cancel()
                return
            }
            self.receive(on: connection, id: id)
        }
    }

    private func drainFrames(on connection: NWConnection, id: ObjectIdentifier) {
        while var buffer = buffers[id], buffer.count >= PaddingFrame.headerSize {
            let upstream = Int(Self.readUInt32(buffer, at: 0))
            let downstream = Int(Self.readUInt32(buffer, at: 4))
            let delay = Int(Self.readUInt32(buffer, at: 8))
            guard upstream <= PaddingFrame.maximumBytes * 4, downstream <= PaddingFrame.maximumBytes * 4 else {
                connection.cancel()
                return
            }
            guard buffer.count >= PaddingFrame.headerSize + upstream else { return }
            buffer.removeFirst(PaddingFrame.headerSize + upstream)
            buffers[id] = buffer
            if downstream > 0 {
                let reply = Self.noiseBytes(downstream)
                queue.asyncAfter(deadline: .now() + .milliseconds(min(delay, 5_000))) {
                    connection.send(content: reply, completion: .contentProcessed { _ in })
                }
            }
        }
    }

    static func noiseBytes(_ count: Int) -> Data {
        var data = Data(capacity: count)
        var remaining = count
        while remaining > 0 {
            let chunk = min(remaining, noise.count)
            data.append(noise.prefix(chunk))
            remaining -= chunk
        }
        return data
    }

    static func readUInt32(_ data: Data, at offset: Int) -> UInt32 {
        let start = data.startIndex + offset
        return (UInt32(data[start]) << 24) | (UInt32(data[start + 1]) << 16) | (UInt32(data[start + 2]) << 8) | UInt32(data[start + 3])
    }

    static func header(for frame: PaddingFrame) -> Data {
        var header = Data(capacity: PaddingFrame.headerSize)
        for value in [UInt32(frame.upstreamBytes), UInt32(frame.downstreamBytes), UInt32(frame.replyDelayMilliseconds)] {
            header.append(UInt8((value >> 24) & 0xFF))
            header.append(UInt8((value >> 16) & 0xFF))
            header.append(UInt8((value >> 8) & 0xFF))
            header.append(UInt8(value & 0xFF))
        }
        return header
    }
}

enum PaddingError: LocalizedError {
    case cancelled
    case loopUnreachable
    case loopClosed

    var errorDescription: String? {
        switch self {
        case .cancelled: String(localized: "Padding loop cancelled.")
        case .loopUnreachable: String(localized: "Could not reach Veil’s private onion service through Tor.")
        case .loopClosed: String(localized: "The padding loop was closed.")
        }
    }
}
