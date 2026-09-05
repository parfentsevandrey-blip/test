import Foundation

/// Tor's control protocol, and the small part of it this app needs.
///
/// The command set is the Android build's, because the lessons behind it were
/// expensive and none of them are Android-specific:
///
///   - Routes are changed with `RESETCONF` then `SETCONF`, never by restarting
///     tor. Restarting costs half a minute and does not reliably work: tor
///     locks its data directory and the previous instance is often still
///     shutting down when the next one starts.
///   - `Bridge` is a list option, so setting one means sending all of them.
///   - `DisableNetwork` is tor's "close everything but this connection". It is
///     how a route is torn down cleanly and how a parked tor is kept.
///   - The bootstrap percentage is **not** evidence that traffic will flow.
///     It never goes down, so a tor whose network was switched off and on
///     again reports 100% while its link is still being rebuilt. Only
///     `orconn-status`, `circuit-status` and a real stream say anything true.
///
/// Replies are framed as `250-` (more to come), `250+` (a data block ending
/// in a lone dot) and `250 ` (last line). Anything beginning `650` is an
/// asynchronous event and may arrive between any two lines of a reply.
actor TorControl {

    struct Reply {
        var code: Int
        var lines: [String]
        var isOK: Bool { code >= 200 && code < 300 }
        var joined: String { lines.joined(separator: "\n") }
    }

    enum Failure: Error, LocalizedError {
        case notConnected
        case rejected(Int, String)

        var errorDescription: String? {
            switch self {
            case .notConnected: return "the control connection is not open"
            case .rejected(let code, let text): return "tor answered \(code): \(text)"
            }
        }
    }

    private var socket: UnixSocket?

    /// Asynchronous notices from tor: bootstrap progress, warnings, errors.
    nonisolated let eventStream: AsyncStream<String>
    private nonisolated let eventSink: AsyncStream<String>.Continuation

    init() {
        var sink: AsyncStream<String>.Continuation!
        eventStream = AsyncStream { sink = $0 }
        eventSink = sink
    }

    // MARK: - Connecting

    /// Opens the control socket and authenticates with the cookie tor wrote.
    ///
    /// Cookie authentication rather than a password: the cookie is a file in
    /// tor's data directory, which is inside the container, so possession of
    /// it is already proof of being this app.
    func connect(socketPath: String, cookiePath: String) throws {
        let connection = try UnixSocket(path: socketPath, timeout: 20)
        socket = connection

        let cookie = try Data(contentsOf: URL(fileURLWithPath: cookiePath))
        let hex = cookie.map { String(format: "%02x", $0) }.joined()
        let reply = try send("AUTHENTICATE \(hex)")
        guard reply.isOK else {
            connection.close()
            socket = nil
            throw Failure.rejected(reply.code, reply.joined)
        }
    }

    func close() {
        socket?.close()
        socket = nil
    }

    var isConnected: Bool { socket?.isOpen == true }

    // MARK: - The wire

    /// Sends one command and reads its reply, passing any events that arrive
    /// in the middle to the event stream rather than confusing them for it.
    @discardableResult
    func send(_ command: String) throws -> Reply {
        guard let socket else { throw Failure.notConnected }
        try socket.write(command + "\r\n")
        return try readReply(on: socket)
    }

    private func readReply(on socket: UnixSocket) throws -> Reply {
        var lines: [String] = []
        var code = 0
        while true {
            let line = try socket.readLine()
            guard line.count >= 4, let value = Int(line.prefix(3)) else { continue }
            let separator = line[line.index(line.startIndex, offsetBy: 3)]
            let body = String(line.dropFirst(4))

            // An asynchronous event can land between the lines of a reply.
            if value == 650 {
                eventSink.yield(body)
                if separator == "+" { _ = try readDataBlock(on: socket) }
                continue
            }

            code = value
            switch separator {
            case "+":
                let block = try readDataBlock(on: socket)
                lines.append(body + "\n" + block)
            case "-":
                lines.append(body)
            default:
                lines.append(body)
                return Reply(code: code, lines: lines)
            }
        }
    }

    /// A `250+` block: everything up to a line containing only a dot, with
    /// leading-dot escaping undone.
    private func readDataBlock(on socket: UnixSocket) throws -> String {
        var block: [String] = []
        while true {
            let line = try socket.readLine()
            if line == "." { break }
            block.append(line.hasPrefix("..") ? String(line.dropFirst()) : line)
        }
        return block.joined(separator: "\n")
    }

    // MARK: - What the app asks for

    func getInfo(_ key: String) throws -> String {
        let reply = try send("GETINFO \(key)")
        guard reply.isOK else { throw Failure.rejected(reply.code, reply.joined) }
        // `key=value`, or `key=` followed by a block.
        let prefix = key + "="
        for line in reply.lines where line.hasPrefix(prefix) {
            return String(line.dropFirst(prefix.count))
        }
        return reply.lines.first ?? ""
    }

    func setEvents(_ events: [String]) throws {
        try send("SETEVENTS " + events.joined(separator: " "))
    }

    /// Points tor at a route.
    ///
    /// `RESETCONF` first because bridge lines are a list: setting new ones
    /// without clearing the old would leave tor trying both. Both the bridges
    /// and `UseBridges` go in one `SETCONF`, which tor applies atomically, so
    /// a single malformed line rejects the whole route rather than half of it.
    func applyRoute(transport: Transport, bridges: [BridgeLine]) throws {
        try send("RESETCONF Bridge UseBridges")
        var arguments: [String] = []
        if transport == .direct || bridges.isEmpty {
            arguments.append("UseBridges=0")
        } else {
            arguments.append("UseBridges=1")
            for bridge in bridges {
                arguments.append("Bridge=\(quoted(bridge.raw))")
            }
        }
        // Circuit timings travel with the route: a path through a volunteer's
        // browser needs different patience from a direct one, and getting this
        // wrong is what makes a working Snowflake feel broken.
        arguments.append(contentsOf: Torrc.tuning(for: transport).map { "\($0.0)=\($0.1)" })
        let reply = try send("SETCONF " + arguments.joined(separator: " "))
        guard reply.isOK else { throw Failure.rejected(reply.code, reply.joined) }
    }

    func setNetworkEnabled(_ enabled: Bool) throws {
        let reply = try send("SETCONF DisableNetwork=\(enabled ? 0 : 1)")
        guard reply.isOK else { throw Failure.rejected(reply.code, reply.joined) }
    }

    /// Reads the bootstrap line directly rather than waiting to be told.
    ///
    /// tor announces progress only when it increases and keeps its counter
    /// across a route change, so a route that resumes where the last one
    /// stopped can be working perfectly while saying nothing at all.
    func bootstrap() throws -> Bootstrap {
        Bootstrap.parse(try getInfo("status/bootstrap-phase"))
    }

    /// Whether tor still has a live connection to any of its bridges.
    func hasLiveConnection() -> Bool {
        guard let status = try? getInfo("orconn-status") else { return false }
        return status.contains("CONNECTED")
    }

    /// Whether a general-purpose circuit is fully built.
    func hasBuiltCircuit() -> Bool {
        guard let status = try? getInfo("circuit-status") else { return false }
        return status.split(separator: "\n").contains {
            $0.contains(" BUILT ") && $0.contains("PURPOSE=GENERAL")
        }
    }

    /// Keeps a circuit standing by so the next stream does not build one.
    ///
    /// Tor stops keeping spares when a client has been quiet, so the first
    /// thing done after a pause pays the full cost of building one — over
    /// Snowflake, the difference between a page loading and a page hanging.
    @discardableResult
    func ensureSpareCircuit() -> Bool {
        guard let status = try? getInfo("circuit-status") else { return false }
        let built = status.split(separator: "\n").filter {
            $0.contains(" BUILT ") && $0.contains("PURPOSE=GENERAL")
        }.count
        guard built < 2 else { return false }
        _ = try? send("EXTENDCIRCUIT 0")
        return true
    }

    func newIdentity() { _ = try? send("SIGNAL NEWNYM") }

    /// The circuit carrying traffic, for the interface to name.
    func describeCircuit() -> String? {
        guard let status = try? getInfo("circuit-status") else { return nil }
        guard let line = status.split(separator: "\n").first(where: { $0.contains("BUILT") })
        else { return nil }
        // `$FINGERPRINT~nickname` per hop; only the nicknames are shown.
        let names = line.split(separator: ",").compactMap { hop -> String? in
            guard let tilde = hop.firstIndex(of: "~") else { return nil }
            let name = hop[hop.index(after: tilde)...]
            return name.isEmpty ? nil : String(name).trimmingCharacters(in: .whitespaces)
        }
        return names.isEmpty ? nil : names.joined(separator: " → ")
    }

    /// Bytes tor itself says it moved, which is not the same as what the
    /// tunnel counted: a large gap between them is traffic being dropped
    /// rather than carried.
    func trafficSummary() -> String? {
        guard let read = try? getInfo("traffic/read"), let written = try? getInfo("traffic/written"),
              let inBytes = Int64(read), let outBytes = Int64(written) else { return nil }
        return "\(inBytes / 1024) KB in, \(outBytes / 1024) KB out"
    }

    /// Turns the network off and on again, which drops every connection tor
    /// holds — including the half-open ones it is still waiting on — and lets
    /// it dial its bridges from scratch. The consensus and the listeners
    /// survive, so this costs seconds rather than a bootstrap.
    func redial() async throws {
        try setNetworkEnabled(false)
        try? await Task.sleep(for: .milliseconds(500))
        try setNetworkEnabled(true)
    }

    private func quoted(_ value: String) -> String {
        "\"" + value.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"") + "\""
    }
}

extension Bootstrap {
    /// Parses `NOTICE BOOTSTRAP PROGRESS=25 TAG=requesting_status SUMMARY="…"`.
    ///
    /// A failure report carries COUNT and RECOMMENDATION and repeats the
    /// percentage it is stuck at, so it must not be read as progress.
    static func parse(_ text: String) -> Bootstrap {
        var result = Bootstrap()
        result.percent = Int(field("PROGRESS", in: text) ?? "") ?? 0
        result.tag = field("TAG", in: text) ?? ""
        result.problems = Int(field("COUNT", in: text) ?? "") ?? 0
        result.recommendation = field("RECOMMENDATION", in: text) ?? ""
        if let open = text.range(of: "SUMMARY=\""),
           let close = text.range(of: "\"", range: open.upperBound..<text.endIndex) {
            result.summary = String(text[open.upperBound..<close.lowerBound])
        }
        return result
    }

    /// Reads `KEY=value` out of a control-port status line, without a regular
    /// expression: the keys are fixed and the values never contain a space.
    private static func field(_ key: String, in text: String) -> String? {
        for token in text.split(separator: " ") where token.hasPrefix(key + "=") {
            return String(token.dropFirst(key.count + 1))
        }
        return nil
    }
}
