import Foundation
import CFNetwork

/// Что человек решил про программу.
nonisolated enum NetDecision: String, Codable, Sendable, CaseIterable {
    case allow, deny, ask

    var title: String {
        switch self {
        case .allow: "Разрешено"
        case .deny: "Запрещено"
        case .ask: "Спросить"
        }
    }
}

/// Программа в списке замка: решение и что она делала.
nonisolated struct NetAppRule: Codable, Identifiable, Sendable, Equatable {
    var app: NetApp
    var decision: NetDecision
    var firstSeen = Date()
    var lastSeen = Date()
    var allowed = 0
    var denied = 0
    var bytes: Int64 = 0
    /// Последние адреса (без повторов, новые сверху).
    var hosts: [String] = []

    var id: String { app.key }
}

/// Прокси одной сетевой службы macOS до замка — чтобы вернуть ровно как было.
nonisolated struct ProxySnapshot: Codable, Sendable, Equatable {
    struct Entry: Codable, Sendable, Equatable {
        var enabled: Bool
        var server: String
        var port: Int
    }
    var service: String
    var web: Entry
    var secure: Entry
    var socks: Entry
    var bypass: [String]
    var autoURL: String
    var autoURLEnabled: Bool
    var autoDiscovery: Bool

    var summary: String {
        func e(_ x: Entry) -> String { x.enabled ? "\(x.server):\(x.port)" : "выкл" }
        return "\(service): HTTP \(e(web)), HTTPS \(e(secure)), SOCKS \(e(socks))"
    }
}

/// Настройки замка (часть состояния Block).
nonisolated struct LockSettings: Codable, Sendable, Equatable {
    /// Замок включён: фильтр пакетов загружен, системный прокси указывает на шлюз.
    var on = false
    var port = Int(Gateway.defaultPort)
    /// Домашняя сеть (принтеры, NAS, AirPlay) — без шлюза.
    var allowLAN = true
    /// Серверы Veil — напрямую (VPN должен до них достучаться).
    var veilServers = true
    /// Разрешённое отправлять через SOCKS Veil.
    var viaVeil = false
    /// Спрашивать и про службы macOS (иначе — молча запрещать, пока замок не открыт).
    var askSystem = false
    /// Адреса, куда можно напрямую (IP, сеть /маска или имя — переводится в адреса при включении).
    var direct: [String] = []
    /// Программы и решения.
    var apps: [String: NetAppRule] = [:]
    /// Прокси служб до замка.
    var proxyBefore: [ProxySnapshot] = []
    /// Адреса, которые сейчас пропускает фильтр (для показа).
    var directResolved: [String] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = LockSettings()
        on = try c.decodeIfPresent(Bool.self, forKey: .on) ?? d.on
        port = try c.decodeIfPresent(Int.self, forKey: .port) ?? d.port
        allowLAN = try c.decodeIfPresent(Bool.self, forKey: .allowLAN) ?? d.allowLAN
        veilServers = try c.decodeIfPresent(Bool.self, forKey: .veilServers) ?? d.veilServers
        viaVeil = try c.decodeIfPresent(Bool.self, forKey: .viaVeil) ?? d.viaVeil
        askSystem = try c.decodeIfPresent(Bool.self, forKey: .askSystem) ?? d.askSystem
        direct = try c.decodeIfPresent([String].self, forKey: .direct) ?? d.direct
        apps = try c.decodeIfPresent([String: NetAppRule].self, forKey: .apps) ?? d.apps
        proxyBefore = try c.decodeIfPresent([ProxySnapshot].self, forKey: .proxyBefore) ?? d.proxyBefore
        directResolved = try c.decodeIfPresent([String].self, forKey: .directResolved) ?? d.directResolved
    }
}

/// Сетевой замок: фильтр пакетов macOS (pf) закрывает новые исходящие соединения всем, кроме шлюза Block,
/// DNS, DHCP, времени, домашней сети и адресов, которые человек разрешил напрямую. Правила живут в своём
/// якоре `com.apple/block` — системный /etc/pf.conf не меняется. После перезагрузки их загружает
/// задание launchd (только pfctl, без своих программ с правами root).
nonisolated enum NetLock {
    static let anchor = "com.apple/block"
    static let directory = "/Library/Application Support/Block"
    static var rulesPath: String { directory + "/netlock.pf" }
    static let daemonLabel = "app.block.netlock"
    static var daemonPath: String { "/Library/LaunchDaemons/\(daemonLabel).plist" }

    static let lanV4 = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "224.0.0.0/4", "255.255.255.255"]
    static let lanV6 = ["fe80::/10", "fc00::/7", "ff00::/8"]

    // MARK: Правила

    static func rules(direct: [String], allowLAN: Bool) -> String {
        let ports = Gateway.sourcePorts
        var lines = [
            "# Block — сетевой замок. Снять: Block → «Замок» → «Снять замок».",
            "# Новые исходящие соединения — только у шлюза Block (порты \(ports.lowerBound)–\(ports.upperBound)).",
            "pass out quick on lo0 all",
            "pass out quick proto { tcp, udp } from any port \(ports.lowerBound):\(ports.upperBound) to any keep state",
            "pass out quick proto { tcp, udp } to any port 53 keep state",
            "pass out quick proto udp from any port 68 to any port 67 keep state",
            "pass out quick inet6 proto udp from any port 546 to any port 547 keep state",
            "pass out quick proto udp to any port 123 keep state",
            "pass out quick inet6 proto icmp6 all keep state",
        ]
        if allowLAN {
            lines.append("pass out quick to { \((lanV4 + lanV6).joined(separator: ", ")) } keep state")
        }
        let addresses = direct.filter(isAddress)
        if !addresses.isEmpty {
            lines.append("pass out quick to { \(addresses.joined(separator: ", ")) } keep state")
        }
        // Только новые соединения (SYN): уже открытые до замка не рвутся.
        lines += [
            "block return out quick proto tcp from any to any flags S/SA",
            "block return out quick proto udp from any to any",
        ]
        return lines.joined(separator: "\n") + "\n"
    }

    /// IPv4, IPv6 или сеть с маской — то, что можно вставить в правило pf.
    static func isAddress(_ s: String) -> Bool {
        let parts = s.split(separator: "/", maxSplits: 1)
        guard let host = parts.first, !host.isEmpty else { return false }
        if parts.count == 2 {
            guard let bits = Int(parts[1]) else { return false }
            if host.contains(":") { guard (0...128).contains(bits) else { return false } } else { guard (0...32).contains(bits) else { return false } }
        }
        var v4 = in_addr(), v6 = in6_addr()
        return inet_pton(AF_INET, String(host), &v4) == 1 || inet_pton(AF_INET6, String(host), &v6) == 1
    }

    /// Имена → адреса (для правила pf), адреса остаются как есть.
    static func resolve(_ items: [String]) -> [String] {
        var out: [String] = []
        for item in items.map({ $0.trimmingCharacters(in: .whitespaces) }) where !item.isEmpty {
            if isAddress(item) { out.append(item) } else { out += ConnectionMonitor.addresses(of: item) }
        }
        return Array(Set(out)).sorted()
    }

    // MARK: Команды администратора

    static func daemonPlist() -> Data {
        let q = Shell.quote(rulesPath)
        let script = "/sbin/pfctl -s rules 2>/dev/null | /usr/bin/grep -q 'com.apple' || /sbin/pfctl -q -f /etc/pf.conf; "
            + "/sbin/pfctl -q -a \(anchor) -f \(q); /sbin/pfctl -q -E 2>/dev/null; true"
        let plist: [String: Any] = ["Label": daemonLabel, "ProgramArguments": ["/bin/sh", "-c", script], "RunAtLoad": true]
        return (try? PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0)) ?? Data()
    }

    /// Включить (или обновить) замок: правила во временных файлах, установка — одной пачкой с паролем.
    static func enableCommands(rulesFile: String, plistFile: String) -> String {
        let dir = Shell.quote(directory), rules = Shell.quote(rulesPath), daemon = Shell.quote(daemonPath)
        let marker = Shell.quote(directory + "/pf-was-enabled")
        return [
            "/bin/mkdir -p \(dir)",
            // Был ли pf включён до Block — запоминается один раз, при первом включении.
            "if [ ! -f \(daemon) ]; then if /sbin/pfctl -s info 2>/dev/null | /usr/bin/grep -q 'Status: Enabled'; then /usr/bin/touch \(marker); else /bin/rm -f \(marker); fi; fi",
            "/bin/cp \(Shell.quote(rulesFile)) \(rules)",
            "/bin/cp \(Shell.quote(plistFile)) \(daemon)",
            "/usr/sbin/chown root:wheel \(rules) \(daemon)",
            "/bin/chmod 644 \(rules) \(daemon)",
            "(/sbin/pfctl -s rules 2>/dev/null | /usr/bin/grep -q 'com.apple' || /sbin/pfctl -q -f /etc/pf.conf)",
            "/sbin/pfctl -q -a \(anchor) -f \(rules)",
            "(/sbin/pfctl -q -E 2>/dev/null; true)",
            "(/bin/launchctl bootout system/\(daemonLabel) 2>/dev/null; /bin/launchctl bootstrap system \(daemon) 2>/dev/null; true)",
        ].joined(separator: " && ")
    }

    static func disableCommands() -> String {
        let dir = Shell.quote(directory), daemon = Shell.quote(daemonPath)
        let marker = Shell.quote(directory + "/pf-was-enabled")
        return "(/sbin/pfctl -q -a \(anchor) -F all 2>/dev/null; /bin/launchctl bootout system/\(daemonLabel) 2>/dev/null; /bin/rm -f \(daemon); "
            + "[ -f \(marker) ] || /sbin/pfctl -q -d 2>/dev/null; /bin/rm -rf \(dir); true)"
    }

    // MARK: Системный прокси (networksetup)

    static let networksetup = "/usr/sbin/networksetup"

    /// Включённые сетевые службы (Wi‑Fi, Ethernet…).
    static func services() -> [String] {
        Shell.run(networksetup, ["-listallnetworkservices"]).output.split(separator: "\n").dropFirst()
            .map(String.init).filter { !$0.hasPrefix("*") && !$0.isEmpty }
    }

    static func snapshot(_ service: String) -> ProxySnapshot {
        func entry(_ flag: String) -> ProxySnapshot.Entry {
            let fields = parseFields(Shell.run(networksetup, [flag, service]).output)
            return .init(enabled: fields["Enabled"] == "Yes", server: fields["Server"] ?? "", port: Int(fields["Port"] ?? "") ?? 0)
        }
        let bypassText = Shell.run(networksetup, ["-getproxybypassdomains", service]).output
        let bypass = bypassText.contains("aren't any") ? [] : bypassText.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let auto = parseFields(Shell.run(networksetup, ["-getautoproxyurl", service]).output)
        let discovery = Shell.run(networksetup, ["-getproxyautodiscovery", service]).output.contains(": On")
        return ProxySnapshot(service: service, web: entry("-getwebproxy"), secure: entry("-getsecurewebproxy"),
                             socks: entry("-getsocksfirewallproxy"), bypass: bypass,
                             autoURL: auto["URL"].flatMap { $0 == "(null)" ? nil : $0 } ?? "", autoURLEnabled: auto["Enabled"] == "Yes",
                             autoDiscovery: discovery)
    }

    static func parseFields(_ text: String) -> [String: String] {
        var map: [String: String] = [:]
        for line in text.split(separator: "\n") {
            guard let colon = line.firstIndex(of: ":") else { continue }
            map[line[..<colon].trimmingCharacters(in: .whitespaces)] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        return map
    }

    /// Все службы смотрят на шлюз; автонастройка прокси выключена (иначе она главнее ручной).
    static func proxyOnCommands(services: [String], port: Int) -> [String] {
        services.flatMap { s -> [String] in
            let q = Shell.quote(s)
            return [
                "\(networksetup) -setwebproxy \(q) 127.0.0.1 \(port) off",
                "\(networksetup) -setsecurewebproxy \(q) 127.0.0.1 \(port) off",
                "\(networksetup) -setsocksfirewallproxy \(q) 127.0.0.1 \(port) off",
                "\(networksetup) -setproxybypassdomains \(q) '*.local' '169.254/16' localhost 127.0.0.1 '::1'",
                "\(networksetup) -setautoproxystate \(q) off",
                "\(networksetup) -setproxyautodiscovery \(q) off",
            ]
        }
    }

    static func proxyRestoreCommands(_ list: [ProxySnapshot]) -> [String] {
        list.flatMap { p -> [String] in
            let q = Shell.quote(p.service)
            func restore(_ set: String, _ state: String, _ e: ProxySnapshot.Entry) -> [String] {
                var out: [String] = []
                if e.server.isEmpty {
                    // Сервера не было: стереть адрес шлюза (если networksetup не примет пустой — не страшно, прокси выключен).
                    out.append("(\(networksetup) \(set) \(q) '' 0 off 2>/dev/null || true)")
                } else {
                    out.append("\(networksetup) \(set) \(q) \(Shell.quote(e.server)) \(e.port) off")
                }
                out.append("\(networksetup) \(state) \(q) \(e.enabled ? "on" : "off")")
                return out
            }
            var out = restore("-setwebproxy", "-setwebproxystate", p.web)
                + restore("-setsecurewebproxy", "-setsecurewebproxystate", p.secure)
                + restore("-setsocksfirewallproxy", "-setsocksfirewallproxystate", p.socks)
            out.append("\(networksetup) -setproxybypassdomains \(q) " + (p.bypass.isEmpty ? "Empty" : p.bypass.map(Shell.quote).joined(separator: " ")))
            if !p.autoURL.isEmpty { out.append("\(networksetup) -setautoproxyurl \(q) \(Shell.quote(p.autoURL))") }
            out.append("\(networksetup) -setautoproxystate \(q) \(p.autoURLEnabled ? "on" : "off")")
            out.append("\(networksetup) -setproxyautodiscovery \(q) \(p.autoDiscovery ? "on" : "off")")
            return out
        }
    }

    // MARK: Проверка

    enum Probe: Equatable { case closed, open, offline }

    /// Прямое соединение мимо шлюза: под замком фильтр сразу отвечает отказом.
    static func probeDirect(timeout: Int32 = 3) -> Probe {
        var sawOpen = false, sawRefused = false
        for target in ["1.1.1.1", "17.253.144.10"] {
            let fd = socket(AF_INET, SOCK_STREAM, 0)
            guard fd >= 0 else { continue }
            defer { close(fd) }
            var addr = sockaddr_in()
            addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = UInt16(443).bigEndian
            addr.sin_addr.s_addr = inet_addr(target)
            let flags = fcntl(fd, F_GETFL, 0)
            _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)
            let r = withUnsafePointer(to: &addr) { $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) } }
            if r == 0 { sawOpen = true; continue }
            guard errno == EINPROGRESS else { sawRefused = true; continue }
            var p = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
            guard poll(&p, 1, timeout * 1000) == 1 else { continue }
            var code: Int32 = 0
            var size = socklen_t(MemoryLayout<Int32>.size)
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &code, &size)
            if code == 0 { sawOpen = true } else { sawRefused = true }
        }
        if sawOpen { return .open }
        // Фильтр отвечает по-разному (отказ, «нет маршрута», «запрещено») или молчит — с VPN и без.
        // «Сети нет» — только когда у Mac нет ни одного подключения с адресом.
        return sawRefused || hasNetwork() ? .closed : .offline
    }

    /// Есть ли подключение с настоящим адресом (не петля и не 169.254 / fe80 без сети).
    static func hasNetwork() -> Bool {
        var list: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&list) == 0, let first = list else { return false }
        defer { freeifaddrs(first) }
        var node: UnsafeMutablePointer<ifaddrs>? = first
        while let n = node {
            node = n.pointee.ifa_next
            let flags = Int32(n.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_RUNNING != 0, flags & IFF_LOOPBACK == 0, let addr = n.pointee.ifa_addr else { continue }
            if addr.pointee.sa_family == sa_family_t(AF_INET) {
                let ip = addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { UInt32(bigEndian: $0.pointee.sin_addr.s_addr) }
                if ip >> 16 != 0xA9FE { return true }   // не 169.254.x.x
            } else if addr.pointee.sa_family == sa_family_t(AF_INET6) {
                let first = addr.withMemoryRebound(to: sockaddr_in6.self, capacity: 1) { $0.pointee.sin6_addr.__u6_addr.__u6_addr8.0 }
                if first & 0xE0 == 0x20 { return true }  // глобальный адрес 2000::/3
            }
        }
        return false
    }

    /// Системный прокси сейчас указывает на шлюз (его могла поменять другая программа).
    static func systemProxyPointsTo(port: Int) -> Bool {
        guard let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any] else { return false }
        let enabled = (settings["HTTPSEnable"] as? Int) == 1
        let host = settings["HTTPSProxy"] as? String
        let p = settings["HTTPSPort"] as? Int
        return enabled && host == "127.0.0.1" && p == port
    }

    // MARK: Veil

    /// Серверы из Veil (их адреса должны быть доступны напрямую, иначе VPN не подключится) и порт SOCKS.
    static func veil() -> (servers: [String], socksPort: Int?) {
        let file = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Veil/state.json")
        guard let data = try? Data(contentsOf: file),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return ([], nil) }
        let servers = (json["servers"] as? [[String: Any]] ?? []).compactMap { ($0["endpoint"] as? [String: Any])?["address"] as? String }
            .filter { !$0.isEmpty }
        let port = (json["settings"] as? [String: Any])?["socksPort"] as? Int
        return (Array(Set(servers)).sorted(), port)
    }

    /// Строка для Терминала: программы командной строки читают прокси из переменных окружения.
    static func shellSnippet(port: Int) -> String {
        "export http_proxy=http://127.0.0.1:\(port) https_proxy=http://127.0.0.1:\(port) all_proxy=socks5h://127.0.0.1:\(port) no_proxy=localhost,127.0.0.1,.local"
    }
}
