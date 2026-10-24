import Foundation

/// Своя секция в /etc/hosts с пометками начала и конца. Всё вне секции не трогается, а снятая секция
/// возвращает файл байт в байт.
nonisolated enum HostsFile {
    static let path = "/etc/hosts"
    static let begin = "# >>> Block: адреса сервисов Apple (правится программой Block) >>>"
    static let end = "# <<< Block <<<"

    static func read(_ path: String = path) -> String {
        (try? String(contentsOfFile: path, encoding: .utf8)) ?? ""
    }

    /// Имена, закрытые секцией Block.
    static func blockedHosts(in content: String) -> Set<String> {
        guard let section = sectionRange(in: content) else { return [] }
        var hosts: Set<String> = []
        for line in content[section].split(separator: "\n") where !line.hasPrefix("#") {
            let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" })
            if parts.count >= 2 { hosts.insert(String(parts[1]).lowercased()) }
        }
        return hosts
    }

    /// Секция вместе с переводом строки перед ней (его добавил Block).
    static func sectionRange(in content: String) -> Range<String.Index>? {
        guard let start = content.range(of: "\n" + begin), let stop = content.range(of: end + "\n", range: start.upperBound..<content.endIndex) else { return nil }
        return start.lowerBound..<stop.upperBound
    }

    static func without(_ content: String) -> String {
        guard let range = sectionRange(in: content) else { return content }
        var c = content
        c.removeSubrange(range)
        return c
    }

    /// Новое содержимое файла: прежнее без секции Block и, если есть что закрывать, новая секция в конце.
    static func render(_ content: String, hosts: [String]) -> String {
        let base = without(content)
        let list = hosts.map { $0.lowercased() }.filter { !AppleNetwork.isUntouchable($0) }
        guard !list.isEmpty else { return base }
        var section = "\n" + begin + "\n"
        for host in Array(Set(list)).sorted() {
            section += "0.0.0.0 \(host)\n:: \(host)\n"
        }
        section += end + "\n"
        return base + section
    }

    /// Команды администратора: записать файл и сбросить кэш DNS.
    static func installCommands(from temp: String, to target: String = path) -> [String] {
        ["/bin/cp \(Shell.quote(temp)) \(Shell.quote(target))", "/usr/bin/dscacheutil -flushcache", "(/usr/bin/killall -HUP mDNSResponder || true)"]
    }

    /// Резолвер системы отвечает «0.0.0.0» (или «::») — адрес действительно закрыт.
    static func resolvesToNowhere(_ host: String) -> Bool {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let first = result else { return false }
        defer { freeaddrinfo(first) }
        var node: UnsafeMutablePointer<addrinfo>? = first
        var any = false
        while let n = node {
            any = true
            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(n.pointee.ai_addr, n.pointee.ai_addrlen, &buffer, socklen_t(buffer.count), nil, 0, NI_NUMERICHOST) == 0 {
                let address = String(cString: buffer)
                if address != "0.0.0.0" && address != "::" { return false }
            }
            node = n.pointee.ai_next
        }
        return any
    }
}
