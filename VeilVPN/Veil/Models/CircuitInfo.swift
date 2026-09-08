import Foundation

/// A relay as named in circuit paths: `$FINGERPRINT~nickname`.
struct RelayRef: Equatable, Hashable, Sendable {
    let fingerprint: String
    let nickname: String

    /// Parses one path element (`$ABCD~nick`, `$ABCD=nick` or a bare fingerprint).
    static func parse(_ token: some StringProtocol) -> RelayRef {
        var fingerprint = String(token)
        var nickname = ""
        if let separator = fingerprint.firstIndex(where: { $0 == "~" || $0 == "=" }) {
            nickname = String(fingerprint[fingerprint.index(after: separator)...])
            fingerprint = String(fingerprint[..<separator])
        }
        if fingerprint.hasPrefix("$") {
            fingerprint.removeFirst()
        }
        return RelayRef(fingerprint: fingerprint.uppercased(), nickname: nickname)
    }

    static func parsePath(_ path: some StringProtocol) -> [RelayRef] {
        path.split(separator: ",").map { parse($0) }
    }
}

/// A circuit as reported by the control port (`CIRC` events and `circuit-status`).
struct CircuitInfo: Equatable, Sendable, Identifiable {
    enum Status: String, Sendable {
        case launched = "LAUNCHED"
        case extended = "EXTENDED"
        case guardWait = "GUARD_WAIT"
        case built = "BUILT"
        case failed = "FAILED"
        case closed = "CLOSED"

        var isFinal: Bool { self == .built || self == .failed || self == .closed }
    }

    let id: String
    var status: Status
    var path: [RelayRef]
    var purpose: String?
    /// When Tor started building it (`TIME_CREATED`), or when Veil first saw it.
    var created: Date?
    var builtAt: Date?
    var reason: String?

    /// Seconds from launch to BUILT — a direct measure of how fast the relays on the path answer.
    var buildTime: TimeInterval? {
        guard let created, let builtAt else { return nil }
        return max(0, builtAt.timeIntervalSince(created))
    }

    var exit: RelayRef? { path.last }
    var middle: RelayRef? { path.count >= 3 ? path[path.count - 2] : nil }
}
