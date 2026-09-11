import Foundation
import Darwin

/// Picks free loopback ports for SOCKS, the HTTP bridge and Tor's control port.
enum PortAllocator {
    enum AllocationError: LocalizedError {
        case exhausted
        var errorDescription: String? { String(localized: "No free local ports available.") }
    }

    static func allocate(preferredSocks: Int, preferredHTTP: Int) throws -> ActivePorts {
        var taken: Set<UInt16> = []
        let socks = try pick(preferred: preferredSocks, fallback: 9050...9150, taken: &taken)
        let http = try pick(preferred: preferredHTTP, fallback: 8118...8218, taken: &taken)
        let control = try pick(preferred: Int(socks) + 1, fallback: 9151...9250, taken: &taken)
        let pool = try pick(preferred: Int(socks) + 10, fallback: 9251...9350, taken: &taken)
        return ActivePorts(socks: socks, http: http, control: control, pool: pool)
    }

    /// Re-picks just the SOCKS port. Chosen at connect time rather than at warm-up time, so the
    /// window between "this port is free" and "tor bound it" is milliseconds instead of minutes.
    static func freeSocksPort(preferred: Int, excluding: Set<UInt16>) throws -> UInt16 {
        var taken = excluding
        return try pick(preferred: preferred, fallback: 9050...9150, taken: &taken)
    }

    private static func pick(preferred: Int, fallback: ClosedRange<Int>, taken: inout Set<UInt16>) throws -> UInt16 {
        var candidates: [Int] = []
        if (1024...65535).contains(preferred) { candidates.append(preferred) }
        candidates.append(contentsOf: fallback)
        for candidate in candidates {
            let port = UInt16(candidate)
            if taken.contains(port) { continue }
            if isFree(port) {
                taken.insert(port)
                return port
            }
        }
        throw AllocationError.exhausted
    }

    /// True when nothing is bound to 127.0.0.1:port.
    static func isFree(_ port: UInt16) -> Bool {
        let socket = Darwin.socket(AF_INET, SOCK_STREAM, 0)
        guard socket >= 0 else { return false }
        defer { Darwin.close(socket) }

        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = port.bigEndian
        address.sin_addr = in_addr(s_addr: inet_addr("127.0.0.1"))

        let result = withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
                Darwin.bind(socket, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        return result == 0
    }
}
