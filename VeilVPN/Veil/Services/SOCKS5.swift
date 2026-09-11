import Foundation
import Network

/// Minimal SOCKS5 CONNECT client (RFC 1928) on top of an open TCP connection to Tor's SOCKS port.
/// The destination is always sent as a host name, so Tor resolves it — including `.onion`
/// addresses — and nothing is looked up locally.
///
/// With `credentials`, RFC 1929 username/password authentication is negotiated. Tor stores the pair
/// verbatim and, with `IsolateSOCKSAuth` (its default), never shares a circuit between two streams
/// that presented different pairs. That is what turns N credential pairs into N parallel circuits
/// without a single control-port command.
enum SOCKS5 {
    struct Credentials: Equatable, Sendable {
        /// ASCII and opaque. NEVER a hostname: Tor stores the pair verbatim and echoes it in
        /// STREAM events, so browsing history must not travel here.
        let username: String
        /// 32 lowercase hex characters. Tor never validates it; the 128 bits exist because any
        /// local process that could guess the pair would join the user's circuit.
        let password: String
    }

    struct Outcome: Sendable {
        var replyCode: UInt8?
        /// True only when method 0x02 was negotiated and accepted. False means Tor chose no-auth
        /// and isolation is silently gone — which the caller must never paper over.
        var isolationApplied: Bool
    }

    enum Failure: LocalizedError {
        case closed
        case protocolError(String)
        case rejected(UInt8)
        case authMethodRejected
        case authFailed(UInt8)

        var errorDescription: String? {
            switch self {
            case .closed: return "SOCKS connection closed."
            case .protocolError(let detail): return "SOCKS protocol error: \(detail)"
            case .rejected(let code): return "SOCKS: \(Self.describe(code))"
            case .authMethodRejected: return "SOCKS: the proxy rejected username authentication."
            case .authFailed(let byte): return "SOCKS: username authentication failed (\(byte))."
            }
        }

        static func describe(_ code: UInt8) -> String {
            switch code {
            case 0: return "success"
            case 1: return "general failure"
            case 2: return "connection not allowed"
            case 3: return "network unreachable"
            case 4: return "host unreachable (Tor cannot find the destination yet)"
            case 5: return "connection refused"
            case 6: return "TTL expired"
            case 7: return "command not supported"
            case 8: return "address type not supported"
            case 0xF0...0xF7: return "onion service error (0x\(String(code, radix: 16)))"
            default: return "reply code \(code)"
            }
        }
    }

    /// The reply codes that blame the circuit rather than the destination, and are therefore worth
    /// one retry somewhere else. 0x05 is the destination refusing; a new circuit will not help.
    static func laneAttributable(_ code: UInt8) -> Bool {
        code == 0x01 || code == 0x02 || code == 0x03 || code == 0x04 || code == 0x06
    }

    // MARK: Byte builders (the unit-test surface)

    /// nil → `05 01 00` (the legacy greeting). With credentials only `0x02` is offered: Veil owns
    /// the torrc and never sets `PreferSOCKSNoAuth`, so the answer is deterministic, and a foreign
    /// SOCKS server on that port fails loudly instead of silently dropping isolation.
    static func greeting(for credentials: Credentials?) -> Data {
        credentials == nil ? Data([0x05, 0x01, 0x00]) : Data([0x05, 0x01, 0x02])
    }

    /// RFC 1929. The version byte is 0x01, not 0x05. Lengths are UTF-8 byte counts, clamped to 255;
    /// an empty field is padded to one byte because ULEN and PLEN must be at least 1.
    static func authRequest(_ credentials: Credentials) -> Data {
        var user = Array(credentials.username.utf8.prefix(255))
        var password = Array(credentials.password.utf8.prefix(255))
        if user.isEmpty { user = [0x2D] }
        if password.isEmpty { password = [0x2D] }
        var data = Data([0x01, UInt8(user.count)])
        data.append(contentsOf: user)
        data.append(UInt8(password.count))
        data.append(contentsOf: password)
        return data
    }

    static func connectRequest(host: String, port: UInt16) -> Data {
        var request = Data([0x05, 0x01, 0x00])
        if let octets = ipv4Octets(host) {
            // An address the caller already resolved: send it as one, so the exit does not
            // look it up again. Host names still travel as names — Tor resolves those.
            request.append(0x01)
            request.append(contentsOf: octets)
        } else {
            let hostBytes = Array(host.utf8.prefix(255))
            request.append(0x03)
            request.append(UInt8(hostBytes.count))
            request.append(contentsOf: hostBytes)
        }
        request.append(UInt8(port >> 8))
        request.append(UInt8(port & 0xFF))
        return request
    }

    /// The four octets of a dotted-quad IPv4 literal, or nil for anything else.
    static func ipv4Octets(_ host: String) -> [UInt8]? {
        let parts = host.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return nil }
        var octets: [UInt8] = []
        for part in parts {
            guard !part.isEmpty, part.count <= 3, part.allSatisfy(\.isNumber), let value = UInt8(part) else { return nil }
            octets.append(value)
        }
        return octets
    }

    // MARK: Handshake

    /// Runs the greeting and CONNECT request on `connection`, which must already be `.ready`.
    /// On success the connection is a transparent tunnel to `host:port`.
    static func connect(on connection: NWConnection, host: String, port: UInt16,
                        completion: @escaping (Error?) -> Void) {
        // No default value for `credentials` on the richer overload: a defaulted parameter would
        // make this call ambiguous between the two.
        connect(on: connection, host: host, port: port, credentials: nil) { error, _ in completion(error) }
    }

    static func connect(on connection: NWConnection, host: String, port: UInt16,
                        credentials: Credentials?,
                        completion: @escaping (Error?, Outcome) -> Void) {
        negotiateMethod(on: connection, credentials: credentials) { error, isolated in
            if let error {
                completion(error, Outcome(replyCode: nil, isolationApplied: false))
                return
            }
            let finish: (Error?, UInt8?) -> Void = { error, code in
                completion(error, Outcome(replyCode: code, isolationApplied: isolated))
            }
            if isolated, let credentials {
                negotiateAuth(on: connection, credentials: credentials) { error in
                    if let error {
                        finish(error, nil)
                    } else {
                        sendConnect(on: connection, host: host, port: port, completion: finish)
                    }
                }
            } else {
                sendConnect(on: connection, host: host, port: port, completion: finish)
            }
        }
    }

    private static func negotiateMethod(on connection: NWConnection, credentials: Credentials?,
                                        completion: @escaping (Error?, Bool) -> Void) {
        connection.send(content: greeting(for: credentials), completion: .contentProcessed { error in
            if let error {
                completion(error, false)
                return
            }
            receiveExactly(connection, 2) { data, error in
                guard let data, error == nil else {
                    completion(error ?? Failure.closed, false)
                    return
                }
                let bytes = [UInt8](data)
                guard bytes[0] == 0x05 else {
                    completion(Failure.protocolError("unexpected version \(bytes[0])"), false)
                    return
                }
                switch bytes[1] {
                case 0x02: completion(nil, true)
                case 0x00: completion(nil, false)
                case 0xFF: completion(Failure.authMethodRejected, false)
                default: completion(Failure.protocolError("authentication method \(bytes[1])"), false)
                }
            }
        })
    }

    private static func negotiateAuth(on connection: NWConnection, credentials: Credentials,
                                      completion: @escaping (Error?) -> Void) {
        connection.send(content: authRequest(credentials), completion: .contentProcessed { error in
            if let error {
                completion(error)
                return
            }
            receiveExactly(connection, 2) { data, error in
                guard let data, error == nil else {
                    completion(error ?? Failure.closed)
                    return
                }
                let bytes = [UInt8](data)
                guard bytes[0] == 0x01 else {
                    completion(Failure.protocolError("unexpected auth version \(bytes[0])"))
                    return
                }
                completion(bytes[1] == 0x00 ? nil : Failure.authFailed(bytes[1]))
            }
        })
    }

    private static func sendConnect(on connection: NWConnection, host: String, port: UInt16,
                                    completion: @escaping (Error?, UInt8?) -> Void) {
        connection.send(content: connectRequest(host: host, port: port), completion: .contentProcessed { error in
            if let error {
                completion(error, nil)
                return
            }
            receiveExactly(connection, 4) { data, error in
                guard let data, error == nil else {
                    completion(error ?? Failure.closed, nil)
                    return
                }
                let header = [UInt8](data)
                guard header[0] == 0x05 else {
                    completion(Failure.protocolError("unexpected version \(header[0])"), nil)
                    return
                }
                guard header[1] == 0x00 else {
                    completion(Failure.rejected(header[1]), header[1])
                    return
                }
                drainBoundAddress(on: connection, atyp: header[3]) { error in
                    completion(error, error == nil ? 0x00 : nil)
                }
            }
        })
    }

    /// Reads the bound address the server echoes back, so the tunnel starts clean.
    private static func drainBoundAddress(on connection: NWConnection, atyp: UInt8,
                                          completion: @escaping (Error?) -> Void) {
        switch atyp {
        case 0x01:
            receiveExactly(connection, 4 + 2) { _, error in completion(error) }
        case 0x04:
            receiveExactly(connection, 16 + 2) { _, error in completion(error) }
        case 0x03:
            receiveExactly(connection, 1) { lengthData, error in
                guard let lengthData, error == nil else {
                    completion(error ?? Failure.closed)
                    return
                }
                let length = Int([UInt8](lengthData)[0])
                receiveExactly(connection, length + 2) { _, error in completion(error) }
            }
        default:
            completion(Failure.protocolError("bad address type \(atyp)"))
        }
    }

    private static func receiveExactly(_ connection: NWConnection, _ count: Int, completion: @escaping (Data?, Error?) -> Void) {
        guard count > 0 else {
            completion(Data(), nil)
            return
        }
        connection.receive(minimumIncompleteLength: count, maximumLength: count) { data, _, _, error in
            if let error {
                completion(nil, error)
                return
            }
            guard let data, data.count == count else {
                completion(nil, Failure.closed)
                return
            }
            completion(data, nil)
        }
    }
}
