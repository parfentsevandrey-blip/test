import Foundation
import os

/// Чёрный список адресов (форматы PeerGuardian .p2p, eMule .dat, CIDR, по одному адресу в строке;
/// файл может быть сжат gzip). С адресами из списка Swarm не соединяется и не принимает от них
/// соединений — обычно туда входят известные подставные пиры и сети слежки за раздачами.
nonisolated final class Blocklist: Sendable {
    private struct State {
        var starts: [UInt32] = []
        var ends: [UInt32] = []
        var enabled = false
        var blocked = 0
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    var rangeCount: Int { state.withLock { $0.starts.count } }
    var blockedCount: Int { state.withLock { $0.blocked } }
    var isActive: Bool { state.withLock { $0.enabled && !$0.starts.isEmpty } }

    /// Входит ли адрес в список (и учесть отказ в счётчике).
    func blocks(_ host: String) -> Bool {
        guard let ip = Blocklist.ipv4(host) else { return false }
        return state.withLock { s in
            guard s.enabled, !s.starts.isEmpty else { return false }
            var lo = 0, hi = s.starts.count - 1, found = -1
            while lo <= hi {
                let mid = (lo + hi) / 2
                if s.starts[mid] <= ip { found = mid; lo = mid + 1 } else { hi = mid - 1 }
            }
            guard found >= 0, ip <= s.ends[found] else { return false }
            s.blocked += 1
            return true
        }
    }

    func setEnabled(_ on: Bool) { state.withLock { $0.enabled = on } }

    func replace(_ ranges: [(UInt32, UInt32)]) {
        let merged = Blocklist.merge(ranges)
        state.withLock {
            $0.starts = merged.map(\.0)
            $0.ends = merged.map(\.1)
            $0.blocked = 0
        }
    }

    // MARK: - Разбор

    static func ipv4(_ host: String) -> UInt32? {
        var h = host
        if h.hasPrefix("::ffff:") { h = String(h.dropFirst(7)) }
        guard let b = UDPAddress.parseV4(h) else { return nil }
        return UInt32(b[0]) << 24 | UInt32(b[1]) << 16 | UInt32(b[2]) << 8 | UInt32(b[3])
    }

    static func parse(_ text: String) -> [(UInt32, UInt32)] {
        var out: [(UInt32, UInt32)] = []
        text.enumerateLines { raw, _ in
            let line = raw.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("#"), !line.hasPrefix("//") else { return }
            if let range = parseLine(line) { out.append(range) }
        }
        return out
    }

    static func parseLine(_ line: String) -> (UInt32, UInt32)? {
        // eMule: «001.002.003.004 - 001.002.003.255 , 000 , описание» (уровень ≥ 128 — разрешено).
        let fields = line.split(separator: ",", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }
        if fields.count >= 2, let level = Int(fields[1]), let r = range(fields[0]) {
            return level >= 128 ? nil : r
        }
        // PeerGuardian: «описание:1.2.3.4-1.2.3.255» (в описании тоже бывают двоеточия).
        if let colon = line.lastIndex(of: ":"), let r = range(String(line[line.index(after: colon)...])) { return r }
        return range(line)
    }

    private static func range(_ s: String) -> (UInt32, UInt32)? {
        let t = s.trimmingCharacters(in: .whitespaces)
        if let dash = t.firstIndex(of: "-") {
            guard let a = ipv4(t[..<dash].trimmingCharacters(in: .whitespaces)),
                  let b = ipv4(t[t.index(after: dash)...].trimmingCharacters(in: .whitespaces)), a <= b else { return nil }
            return (a, b)
        }
        if let slash = t.firstIndex(of: "/") {
            guard let a = ipv4(String(t[..<slash])), let bits = Int(t[t.index(after: slash)...]), (0...32).contains(bits) else { return nil }
            let mask: UInt32 = bits == 0 ? 0 : ~UInt32(0) << UInt32(32 - bits)
            return (a & mask, (a & mask) | ~mask)
        }
        return ipv4(t).map { ($0, $0) }
    }

    static func merge(_ ranges: [(UInt32, UInt32)]) -> [(UInt32, UInt32)] {
        let sorted = ranges.sorted { $0.0 < $1.0 }
        var out: [(UInt32, UInt32)] = []
        for r in sorted {
            if let last = out.last, UInt64(r.0) <= UInt64(last.1) + 1 {
                out[out.count - 1].1 = max(last.1, r.1)
            } else {
                out.append(r)
            }
        }
        return out
    }

    // MARK: - Загрузка

    /// Источник: путь к файлу или адрес http(s).
    static func load(_ source: String) async throws -> [(UInt32, UInt32)] {
        let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines)
        let data: Data
        if let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" {
            var request = URLRequest(url: url, timeoutInterval: 60)
            request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            let (body, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw PeerError("Сервер списка ответил \(http.statusCode)")
            }
            data = body
        } else {
            let path = (trimmed as NSString).expandingTildeInPath
            data = try Data(contentsOf: URL(fileURLWithPath: path))
        }
        let raw = data.starts(with: [0x1F, 0x8B]) ? try Gzip.decompress(data) : data
        let ranges = parse(String(decoding: raw, as: UTF8.self))
        guard !ranges.isEmpty else { throw PeerError("В списке не нашлось ни одного адреса") }
        return ranges
    }
}
