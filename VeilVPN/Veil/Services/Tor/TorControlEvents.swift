import Foundation

/// Every parser for Tor's control events and log lines, in one place: pure, `nonisolated`, and
/// reachable from tests without a running Tor. `TorControlClient` strips the `650 ` prefix, so
/// handlers see `STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=10 …`.
enum TorControlEvents {
    /// What a `STATUS_CLIENT` line says. Everything except `percent` and `tag` is a bonus: not
    /// every build emits COUNT, RECOMMENDATION or HOSTADDR, and nothing here may depend on them.
    enum ClientStatus: Equatable, Sendable {
        case bootstrap(percent: Int, tag: String, summary: String, warning: String?, reason: String?,
                       count: Int?, recommendation: String?, hostAddress: String?)
        case transportLaunched(String)
        case circuitEstablished
        case circuitNotEstablished(String?)
        case enoughDirInfo
        case notEnoughDirInfo
    }

    /// Splits `K=V` and `K="quoted V with spaces and \" escapes"`. Never a plain split on " ":
    /// a WARNING or SUMMARY value routinely contains spaces and `=`.
    static func parseKeyValues(_ text: String) -> [String: String] {
        var result: [String: String] = [:]
        var key = ""
        var value = ""
        var inKey = true
        var quoted = false
        var escaped = false
        func flush() {
            if !key.isEmpty { result[key] = value }
            key = ""
            value = ""
            inKey = true
        }
        for character in text {
            if escaped {
                value.append(character)
                escaped = false
                continue
            }
            if quoted {
                switch character {
                case "\\": escaped = true
                case "\"": quoted = false
                default: value.append(character)
                }
                continue
            }
            switch character {
            case "=" where inKey:
                inKey = false
            case "\"" where !inKey:
                quoted = true
            case " ":
                flush()
            default:
                if inKey { key.append(character) } else { value.append(character) }
            }
        }
        flush()
        return result
    }

    static func parseStatusClient(_ text: String) -> ClientStatus? {
        let parts = text.split(separator: " ", maxSplits: 2, omittingEmptySubsequences: true)
        guard parts.count >= 2, parts[0] == "STATUS_CLIENT" else { return nil }
        let body = parts.count > 2 ? String(parts[2]) : ""
        let action = body.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true).first.map(String.init) ?? ""
        let rest = body.dropFirst(action.count).trimmingCharacters(in: .whitespaces)
        let fields = parseKeyValues(rest)
        switch action {
        case "BOOTSTRAP":
            guard let raw = fields["PROGRESS"], let percent = Int(raw) else { return nil }
            return .bootstrap(
                percent: min(100, max(0, percent)),
                tag: fields["TAG"] ?? "",
                summary: fields["SUMMARY"] ?? "",
                warning: fields["WARNING"],
                reason: fields["REASON"],
                count: fields["COUNT"].flatMap { Int($0) },
                recommendation: fields["RECOMMENDATION"],
                hostAddress: fields["HOSTADDR"]
            )
        case "TRANSPORT_LAUNCHED":
            guard let name = fields["NAME"] else { return nil }
            return .transportLaunched(name)
        case "CIRCUIT_ESTABLISHED": return .circuitEstablished
        case "CIRCUIT_NOT_ESTABLISHED": return .circuitNotEstablished(fields["REASON"])
        case "ENOUGH_DIR_INFO": return .enoughDirInfo
        case "NOT_ENOUGH_DIR_INFO": return .notEnoughDirInfo
        default: return nil
        }
    }

    /// `ORCONN $FP~nick CONNECTED ID=7` / `ORCONN 1.2.3.4:9001 FAILED REASON=CONNECTREFUSED`.
    static func parseORConn(_ text: String) -> (target: String, status: String, reason: String?)? {
        let parts = text.split(separator: " ", omittingEmptySubsequences: true)
        guard parts.count >= 3, parts[0] == "ORCONN" else { return nil }
        let fields = parseKeyValues(parts.dropFirst(3).joined(separator: " "))
        return (target: String(parts[1]), status: String(parts[2]), reason: fields["REASON"])
    }

    /// `BW 1234 5678` — per-second deltas, not cumulative counters.
    static func parseBW(_ text: String) -> (read: UInt64, written: UInt64)? {
        let parts = text.split(separator: " ", omittingEmptySubsequences: true)
        guard parts.count >= 3, parts[0] == "BW",
              let read = UInt64(parts[1]), let written = UInt64(parts[2]) else { return nil }
        return (read: read, written: written)
    }

    /// `BUILDTIMEOUT_SET COMPUTED TOTAL_TIMES=100 TIMEOUT_MS=1500 …` — Tor's own estimate of how
    /// slow the network is, steadier than any single probe of ours.
    static func parseBuildTimeoutSet(_ text: String) -> Int? {
        guard text.hasPrefix("BUILDTIMEOUT_SET") else { return nil }
        guard let raw = parseKeyValues(text)["TIMEOUT_MS"] else { return nil }
        return Int(raw)
    }

    /// `$FP~nick` / `$FP=nick` → `FP`; an `ip:port` target is left alone. Different Tor versions
    /// name the same relay both ways, and a doubled target must never inflate a failure count.
    static func normalizeORTarget(_ raw: String) -> String {
        var value = raw
        if value.hasPrefix("$") { value.removeFirst() }
        if let separator = value.firstIndex(where: { $0 == "~" || $0 == "=" }) {
            value = String(value[..<separator])
        }
        return value.uppercased()
    }

    /// `Could not bind to 127.0.0.1:9051: Address already in use` → 9051.
    static func parseBindFailure(_ logLine: String) -> UInt16? {
        guard let range = logLine.range(of: "Could not bind to ") else { return nil }
        let rest = logLine[range.upperBound...]
        guard let colon = rest.firstIndex(of: ":") else { return nil }
        let digits = rest[rest.index(after: colon)...].prefix { $0.isNumber }
        return UInt16(digits)
    }

    /// Tor refuses to run twice against one DataDirectory; say so in 200 ms instead of timing out.
    static func parseLockFailure(_ logLine: String) -> Bool {
        logLine.contains("Could not lock data directory")
            || logLine.contains("another Tor process")
            || logLine.contains("Is another Tor process running")
    }

    /// `"127.0.0.1:9050" "127.0.0.1:9060"` → the two endpoints. Values are control-spec
    /// QuotedStrings, so the quotes come off and `\\`/`\"` are unescaped.
    static func parseListeners(_ value: String) -> [String] {
        var result: [String] = []
        var current = ""
        var quoted = false
        var escaped = false
        for character in value {
            if escaped {
                current.append(character)
                escaped = false
                continue
            }
            switch character {
            case "\\" where quoted: escaped = true
            case "\"":
                if quoted {
                    result.append(current)
                    current = ""
                }
                quoted.toggle()
            default:
                if quoted { current.append(character) }
            }
        }
        return result
    }
}
