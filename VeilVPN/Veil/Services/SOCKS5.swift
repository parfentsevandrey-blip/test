import Foundation
import Network

/// Minimal SOCKS5 CONNECT client (RFC 1928, no authentication) on top of an open TCP connection
/// to Tor's SOCKS port. The destination is always sent as a host name, so Tor resolves it —
/// including `.onion` addresses — and nothing is looked up locally.
enum SOCKS5 {
    enum Failure: LocalizedError {
        case closed
        case protocolError(String)
        case rejected(UInt8)

        var errorDescription: String? {
            switch self {
            case .closed: return "SOCKS connection closed."
            case .protocolError(let detail): return "SOCKS protocol error: \(detail)"
            case .rejected(let code): return "SOCKS: \(Self.describe(code))"
            }
        }

        static func describe(_ code: UInt8) -> String {
            switch code {
            case 1: return "general failure"
            case 2: return "connection not allowed"
            case 3: return "network unreachable"
            case 4: return "host unreachable (Tor cannot find the destination yet)"
            case 5: return "connection refused"
            case 6: return "TTL expired"
            case 7: return "command not supported"
            case 8: return "address type not supported"
            default: return "reply code \(code)"
            }
        }
    }

    /// Runs the greeting and CONNECT request on `connection`, which must already be `.ready`.
    /// On success the connection is a transparent tunnel to `host:port`.
    static func connect(on connection: NWConnection, host: String, port: UInt16, completion: @escaping (Error?) -> Void) {
        connection.send(content: Data([0x05, 0x01, 0x00]), completion: .contentProcessed { error in
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
                guard bytes[0] == 0x05, bytes[1] == 0x00 else {
                    completion(Failure.protocolError("authentication method rejected"))
                    return
                }
                let hostBytes = Array(host.utf8.prefix(255))
                var request = Data([0x05, 0x01, 0x00, 0x03, UInt8(hostBytes.count)])
                request.append(contentsOf: hostBytes)
                request.append(UInt8(port >> 8))
                request.append(UInt8(port & 0xFF))
                connection.send(content: request, completion: .contentProcessed { error in
                    if let error {
                        completion(error)
                        return
                    }
                    receiveExactly(connection, 4) { data, error in
                        guard let data, error == nil else {
                            completion(error ?? Failure.closed)
                            return
                        }
                        let header = [UInt8](data)
                        guard header[0] == 0x05 else {
                            completion(Failure.protocolError("unexpected version \(header[0])"))
                            return
                        }
                        guard header[1] == 0x00 else {
                            completion(Failure.rejected(header[1]))
                            return
                        }
                        // Drain the bound address so the tunnel starts clean.
                        switch header[3] {
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
                            completion(Failure.protocolError("bad address type \(header[3])"))
                        }
                    }
                })
            }
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
