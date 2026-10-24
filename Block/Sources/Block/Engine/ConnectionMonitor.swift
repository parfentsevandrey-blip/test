import Darwin
import Foundation

/// Одно соединение из выборки nettop.
nonisolated struct ConnectionSample: Sendable, Equatable {
    var process: String
    var pid: Int32
    var proto: String
    var ip: String
    var port: Int
    var bytesIn: Int64
    var bytesOut: Int64
}

/// Разбор вывода `nettop -L 1` (CSV): строка процесса «имя.pid», под ней — его соединения
/// «tcp4 10.0.0.2:52012<->17.253.1.2:443» (у IPv6 порт отделён точкой).
nonisolated enum NettopParser {
    static func parse(_ text: String) -> [ConnectionSample] {
        var result: [ConnectionSample] = []
        var inIndex: Int?, outIndex: Int?
        var current: (name: String, pid: Int32)?
        for raw in text.split(whereSeparator: \.isNewline) {
            let fields = raw.split(separator: ",", omittingEmptySubsequences: false).map { String($0).trimmingCharacters(in: .whitespaces) }
            if fields.contains("bytes_in") {
                inIndex = fields.firstIndex(of: "bytes_in")
                outIndex = fields.firstIndex(of: "bytes_out")
                continue
            }
            // Поле-идентификатор — первое непустое, похожее на процесс или соединение (в начале бывает время).
            guard let id = fields.prefix(2).first(where: { $0.contains("<->") || processID($0) != nil }) else { continue }
            if let p = processID(id), !id.contains("<->") {
                current = p
                continue
            }
            guard let owner = current, let connection = endpoint(id) else { continue }
            func number(_ i: Int?) -> Int64 { i.flatMap { $0 < fields.count ? Int64(fields[$0]) : nil } ?? 0 }
            result.append(ConnectionSample(process: owner.name, pid: owner.pid, proto: connection.proto, ip: connection.ip,
                                           port: connection.port, bytesIn: number(inIndex), bytesOut: number(outIndex)))
        }
        return result
    }

    /// «apsd.312» → («apsd», 312).
    static func processID(_ s: String) -> (name: String, pid: Int32)? {
        guard let dot = s.lastIndex(of: "."), let pid = Int32(s[s.index(after: dot)...]), !s[..<dot].isEmpty else { return nil }
        return (String(s[..<dot]), pid)
    }

    /// «tcp4 10.0.0.2:52012<->17.253.1.2:443» → («tcp4», «17.253.1.2», 443).
    static func endpoint(_ s: String) -> (proto: String, ip: String, port: Int)? {
        let parts = s.components(separatedBy: "<->")
        guard parts.count == 2 else { return nil }
        let proto = parts[0].split(separator: " ").first.map(String.init) ?? ""
        var remote = parts[1].trimmingCharacters(in: .whitespaces)
        if remote.hasPrefix("*") { return nil }
        let separator: Character = proto.hasSuffix("6") || remote.filter({ $0 == ":" }).count > 1 ? "." : ":"
        guard let cut = remote.lastIndex(of: separator), let port = Int(remote[remote.index(after: cut)...]) else { return nil }
        remote = String(remote[..<cut])
        if let zone = remote.firstIndex(of: "%") { remote = String(remote[..<zone]) }
        remote = remote.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        guard !remote.isEmpty, remote != "*" else { return nil }
        return (proto, remote, port)
    }

    static func isPrivate(_ ip: String) -> Bool {
        let privateV4 = ["10.", "127.", "192.168.", "169.254.", "0."]
        if privateV4.contains(where: ip.hasPrefix) { return true }
        if ip.hasPrefix("172."), let second = ip.split(separator: ".").dropFirst().first.flatMap({ Int($0) }), (16...31).contains(second) { return true }
        let v6 = ip.lowercased()
        return v6 == "::1" || v6.hasPrefix("fe80") || v6.hasPrefix("fc") || v6.hasPrefix("fd")
    }
}

/// Что Block увидел: процесс Apple → адрес. Хранится неделю, только на этом Mac.
nonisolated struct Observation: Codable, Identifiable, Sendable, Equatable {
    var process: String
    var pid: Int32
    var path: String
    /// Имя адреса, если удалось узнать; иначе IP.
    var endpoint: String
    var ip: String
    var port: Int
    var firstSeen: Date
    var lastSeen: Date
    var samples: Int
    var bytesIn: Int64
    var bytesOut: Int64
    var verdict: Verdict

    var id: String { process + "|" + endpoint }
    var named: Bool { endpoint != ip }
}

/// Наблюдение за соединениями процессов Apple. Без помощника с правами root: системный `nettop` показывает
/// соединения всех процессов; имена Block узнаёт сам — заранее разрешает известные адреса Apple
/// и спрашивает обратную запись DNS у остальных.
@Observable
final class ConnectionMonitor {
    private(set) var observations: [Observation] = []
    private(set) var running = false
    private(set) var lastSample: Date?
    private(set) var lastError: String?
    /// Сколько процессов nettop показал в последней выборке (для проверки, что он видит не только свои).
    private(set) var lastProcessCount = 0
    @ObservationIgnored private var timer: Timer?
    @ObservationIgnored private var names: [String: String] = [:]
    @ObservationIgnored private var reverse: [String: String] = [:]
    @ObservationIgnored private var namesRefreshed = Date.distantPast
    @ObservationIgnored private var sampling = false
    var onNew: (([Observation]) -> Void)?

    static var file: URL { BlockState.directory.appendingPathComponent("monitor.json") }

    init() {
        if let data = try? Data(contentsOf: Self.file), let list = try? JSONDecoder().decode([Observation].self, from: data) {
            observations = list
        }
    }

    func start(interval: Double = 8) {
        guard !running else { return }
        running = true
        Task { await sample() }
        timer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { _ in
            Task { @MainActor in await AppModel.shared.monitor.sample() }
        }
    }

    func stop() {
        running = false
        timer?.invalidate()
        timer = nil
    }

    /// Готовый список (демонстрация экрана) — без записи на диск.
    func show(_ list: [Observation]) { observations = list }

    func clear() {
        observations = []
        save()
    }

    /// Одна выборка: nettop → разбор → имена → вердикты.
    func sample() async {
        guard !sampling else { return }
        sampling = true
        defer { sampling = false }
        if Date().timeIntervalSince(namesRefreshed) > 1800 {
            names = await Task.detached { Self.resolveKnown() }.value
            namesRefreshed = Date()
        }
        let output = await Task.detached { Shell.run("/usr/bin/nettop", ["-L", "1", "-n", "-x", "-t", "external", "-J", "bytes_in,bytes_out"], timeout: 15) }.value
        guard output.ok || !output.output.isEmpty else { lastError = "nettop не ответил"; return }
        lastError = nil
        let samples = NettopParser.parse(output.output).filter { !NettopParser.isPrivate($0.ip) }
        lastProcessCount = Set(NettopParser.parse(output.output).map(\.pid)).count
        let unknownIPs = Set(samples.map(\.ip)).filter { names[$0] == nil && reverse[$0] == nil }
        if !unknownIPs.isEmpty {
            let found = await Task.detached { Self.reverseLookup(Array(unknownIPs.prefix(40))) }.value
            reverse.merge(found) { a, _ in a }
        }
        ingest(samples)
        lastSample = Date()
    }

    func ingest(_ samples: [ConnectionSample], now: Date = Date()) {
        var map = Dictionary(uniqueKeysWithValues: observations.map { ($0.id, $0) })
        var fresh: [Observation] = []
        var paths: [Int32: String] = [:]
        for s in samples {
            let path = paths[s.pid] ?? Self.path(of: s.pid)
            paths[s.pid] = path
            let name = path.isEmpty ? s.process : URL(fileURLWithPath: path).lastPathComponent
            guard AppleKnowledge.isApple(path: path, name: name) else { continue }
            let host = names[s.ip] ?? reverse[s.ip]
            let endpoint = host ?? s.ip
            let id = name + "|" + endpoint
            if var o = map[id] {
                o.lastSeen = now
                o.pid = s.pid
                o.samples += 1
                o.bytesIn = max(o.bytesIn, s.bytesIn)
                o.bytesOut = max(o.bytesOut, s.bytesOut)
                map[id] = o
            } else {
                let o = Observation(process: name, pid: s.pid, path: path, endpoint: endpoint, ip: s.ip, port: s.port, firstSeen: now, lastSeen: now,
                                    samples: 1, bytesIn: s.bytesIn, bytesOut: s.bytesOut, verdict: AppleKnowledge.verdict(process: name, host: host))
                map[id] = o
                fresh.append(o)
            }
        }
        let week = now.addingTimeInterval(-7 * 86_400)
        observations = map.values.filter { $0.lastSeen > week }.sorted { ($0.verdict, $1.lastSeen) < ($1.verdict, $0.lastSeen) }
        if observations.count > 1500 { observations = Array(observations.prefix(1500)) }
        save()
        if !fresh.isEmpty { onNew?(fresh) }
    }

    private func save() {
        try? FileManager.default.createDirectory(at: BlockState.directory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(observations) { try? data.write(to: Self.file, options: .atomic) }
    }

    // MARK: Имена и пути

    nonisolated static func path(of pid: Int32) -> String {
        var buffer = [CChar](repeating: 0, count: 4096)
        guard proc_pidpath(pid, &buffer, UInt32(buffer.count)) > 0 else { return "" }
        return String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    /// Адреса известных имён Apple (одно имя может стоять за многими адресами CDN).
    nonisolated static func resolveKnown() -> [String: String] {
        var map: [String: String] = [:]
        for host in AppleKnowledge.knownHosts {
            for ip in addresses(of: host) where map[ip] == nil { map[ip] = host }
        }
        return map
    }

    nonisolated static func addresses(of host: String) -> [String] {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let first = result else { return [] }
        defer { freeaddrinfo(first) }
        var list: [String] = []
        var node: UnsafeMutablePointer<addrinfo>? = first
        while let n = node {
            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(n.pointee.ai_addr, n.pointee.ai_addrlen, &buffer, socklen_t(buffer.count), nil, 0, NI_NUMERICHOST) == 0 {
                list.append(String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self))
            }
            node = n.pointee.ai_next
        }
        return list
    }

    /// Обратная запись DNS (PTR). Пусто — имени нет.
    nonisolated static func reverseLookup(_ ips: [String]) -> [String: String] {
        var map: [String: String] = [:]
        for ip in ips {
            var hints = addrinfo()
            hints.ai_flags = AI_NUMERICHOST
            var result: UnsafeMutablePointer<addrinfo>?
            guard getaddrinfo(ip, nil, &hints, &result) == 0, let first = result else { continue }
            defer { freeaddrinfo(first) }
            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(first.pointee.ai_addr, first.pointee.ai_addrlen, &buffer, socklen_t(buffer.count), nil, 0, NI_NAMEREQD) == 0 {
                map[ip] = String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self).lowercased()
            }
        }
        return map
    }
}
