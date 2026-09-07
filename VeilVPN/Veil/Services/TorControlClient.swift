import Foundation
import Network

/// Minimal client for Tor's control protocol (control-spec.txt) over a loopback TCP connection.
/// Commands are answered in order, so replies are matched to callers FIFO.
final class TorControlClient: @unchecked Sendable {
    struct ReplyLine: Sendable {
        let code: Int
        let separator: Character
        let text: String
    }

    enum ControlError: LocalizedError {
        case notConnected
        case closed
        case unavailable(String)
        case reply(code: Int, message: String)
        case missingValue(String)

        var errorDescription: String? {
            switch self {
            case .notConnected: return "Control connection is not open."
            case .closed: return "Control connection closed."
            case .unavailable(let detail): return "Control port unavailable: \(detail)"
            case .reply(let code, let message): return "Tor replied \(code): \(message)"
            case .missingValue(let key): return "Tor returned no value for \(key)."
            }
        }
    }

    private let port: UInt16
    private let queue = DispatchQueue(label: "app.veilvpn.torcontrol")
    private var connection: NWConnection?
    private var buffer = Data()
    private var pending: [CheckedContinuation<[ReplyLine], Error>] = []
    private var current: [ReplyLine] = []
    private var dataKey: String?
    private var dataLines: [String] = []
    private var readyContinuation: CheckedContinuation<Void, Error>?
    private var isReady = false
    private var isClosed = false
    private var eventLines: [String] = []
    private var dataBlockIsEvent = false
    private var eventHandler: ((String) -> Void)?

    init(port: UInt16) {
        self.port = port
    }

    func connect() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            queue.async {
                guard !self.isClosed else {
                    continuation.resume(throwing: ControlError.closed)
                    return
                }
                let connection = NWConnection(
                    host: "127.0.0.1",
                    port: NWEndpoint.Port(rawValue: self.port) ?? 9051,
                    using: .tcp
                )
                self.connection = connection
                self.readyContinuation = continuation
                connection.stateUpdateHandler = { [weak self] state in
                    self?.handleState(state)
                }
                connection.start(queue: self.queue)
            }
        }
    }

    func close() {
        queue.async {
            self.tearDown(error: ControlError.closed)
        }
    }

    /// Receives asynchronous `650` events (after `SETEVENTS`), one call per event, without the code.
    func setEventHandler(_ handler: ((String) -> Void)?) {
        queue.async {
            self.eventHandler = handler
        }
    }

    // MARK: Commands

    @discardableResult
    func send(_ command: String) async throws -> [ReplyLine] {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[ReplyLine], Error>) in
            queue.async {
                guard self.isReady, let connection = self.connection else {
                    continuation.resume(throwing: ControlError.notConnected)
                    return
                }
                self.pending.append(continuation)
                connection.send(content: Data((command + "\r\n").utf8), completion: .contentProcessed { [weak self] error in
                    if let error {
                        self?.tearDown(error: error)
                    }
                })
            }
        }
    }

    func authenticate(cookie: Data) async throws {
        let hex = cookie.map { String(format: "%02X", $0) }.joined()
        try await send("AUTHENTICATE \(hex)")
    }

    func getInfo(_ key: String) async throws -> String {
        let lines = try await send("GETINFO \(key)")
        for line in lines where line.code == 250 {
            if line.separator == "+" {
                let parts = line.text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
                if let header = parts.first, header.hasPrefix(key + "=") {
                    return parts.count > 1 ? String(parts[1]) : ""
                }
            } else if line.text.hasPrefix(key + "=") {
                return String(line.text.dropFirst(key.count + 1))
            }
        }
        throw ControlError.missingValue(key)
    }

    func signal(_ name: String) async throws {
        try await send("SIGNAL \(name)")
    }

    func setConf(_ assignments: [String: String]) async throws {
        let body = assignments.map { key, value in "\(key)=\(value)" }.joined(separator: " ")
        try await send("SETCONF \(body)")
    }

    func resetConf(_ keys: [String]) async throws {
        try await send("RESETCONF \(keys.joined(separator: " "))")
    }

    // MARK: Connection plumbing (always on `queue`)

    private func handleState(_ state: NWConnection.State) {
        switch state {
        case .ready:
            isReady = true
            readyContinuation?.resume()
            readyContinuation = nil
            receiveLoop()
        case .waiting(let error):
            // On loopback a refused connection surfaces as `.waiting`; fail fast so callers retry.
            tearDown(error: ControlError.unavailable(error.localizedDescription))
        case .failed(let error):
            tearDown(error: ControlError.unavailable(error.localizedDescription))
        case .cancelled:
            tearDown(error: ControlError.closed)
        default:
            break
        }
    }

    private func tearDown(error: Error) {
        isReady = false
        if let ready = readyContinuation {
            readyContinuation = nil
            ready.resume(throwing: error)
        }
        let waiting = pending
        pending = []
        waiting.forEach { $0.resume(throwing: error) }
        if !isClosed {
            isClosed = true
            connection?.cancel()
        }
    }

    private func receiveLoop() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.buffer.append(data)
                self.parseBuffer()
            }
            if isComplete || error != nil {
                self.tearDown(error: error ?? ControlError.closed)
                return
            }
            self.receiveLoop()
        }
    }

    private func parseBuffer() {
        let delimiter = Data("\r\n".utf8)
        while let range = buffer.range(of: delimiter) {
            let lineData = buffer.subdata(in: buffer.startIndex..<range.lowerBound)
            buffer.removeSubrange(buffer.startIndex..<range.upperBound)
            let line = String(decoding: lineData, as: UTF8.self)
            processLine(line)
        }
    }

    private func processLine(_ line: String) {
        if dataKey != nil {
            if line == "." {
                let text = ([dataKey ?? ""] + dataLines).joined(separator: "\n")
                if dataBlockIsEvent {
                    eventLines.append(text)
                } else {
                    current.append(ReplyLine(code: 250, separator: "+", text: text))
                }
                dataKey = nil
                dataLines = []
                dataBlockIsEvent = false
            } else {
                dataLines.append(line.hasPrefix("..") ? String(line.dropFirst()) : line)
            }
            return
        }
        guard line.count >= 4, let code = Int(line.prefix(3)) else { return }
        let separator = line[line.index(line.startIndex, offsetBy: 3)]
        let text = String(line.dropFirst(4))
        if code == 650 {
            // Asynchronous events never interleave with a reply's own lines, but they can arrive
            // between a command and its reply, so keep them out of `current`.
            switch separator {
            case "+":
                dataKey = text
                dataLines = []
                dataBlockIsEvent = true
            case "-":
                eventLines.append(text)
            case " ":
                eventLines.append(text)
                let events = eventLines
                eventLines = []
                if let eventHandler {
                    events.forEach(eventHandler)
                }
            default:
                break
            }
            return
        }
        switch separator {
        case "+":
            dataKey = text
            dataLines = []
        case "-":
            current.append(ReplyLine(code: code, separator: "-", text: text))
        case " ":
            current.append(ReplyLine(code: code, separator: " ", text: text))
            deliverReply()
        default:
            break
        }
    }

    private func deliverReply() {
        let lines = current
        current = []
        guard !pending.isEmpty else { return } // unsolicited (650) events are ignored
        let continuation = pending.removeFirst()
        if let last = lines.last, (200..<300).contains(last.code) {
            continuation.resume(returning: lines)
        } else {
            continuation.resume(throwing: ControlError.reply(code: lines.last?.code ?? 0, message: lines.last?.text ?? ""))
        }
    }
}
