import Foundation

nonisolated struct PortMappingStatus: Sendable, Equatable {
    var method: String?
    var externalIP: String?
    var externalPort: UInt16?
    var gateway: String?
    var message: String

    static let off = PortMappingStatus(message: "Выключен")
}

/// Проброс порта на роутере, чтобы к Swarm могли подключаться извне: сначала PCP (RFC 6887),
/// затем NAT-PMP (RFC 6886), затем UPnP IGD (SSDP + SOAP). Открываются TCP и UDP на одном номере,
/// аренда продлевается, при выходе правила удаляются.
actor PortMapper {
    private enum Mapping {
        case pcp(gateway: String, nonces: [UInt8: Data])
        case natpmp(gateway: String)
        case upnp(control: URL, service: String)
    }

    let port: UInt16
    private(set) var status = PortMappingStatus(message: "Поиск роутера…")
    private var mapping: Mapping?
    private var task: Task<Void, Never>?

    init(port: UInt16) {
        self.port = port
    }

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            while !Task.isCancelled {
                let renew = await self?.refresh() ?? 600
                try? await Task.sleep(for: .seconds(renew))
            }
        }
    }

    /// Удаляет правила на роутере.
    func stop() async {
        task?.cancel()
        task = nil
        guard let mapping else { status = .off; return }
        self.mapping = nil
        switch mapping {
        case .pcp(let gateway, let nonces):
            for (proto, nonce) in nonces { _ = await PortMapper.pcp(gateway: gateway, proto: proto, port: port, lifetime: 0, nonce: nonce) }
        case .natpmp(let gateway):
            for op: UInt8 in [1, 2] { _ = await PortMapper.natpmp(gateway: gateway, op: op, port: port, lifetime: 0) }
        case .upnp(let control, let service):
            for proto in ["TCP", "UDP"] {
                _ = try? await PortMapper.soap(control, service, "DeletePortMapping", [
                    ("NewRemoteHost", ""), ("NewExternalPort", "\(port)"), ("NewProtocol", proto)])
            }
        }
        status = .off
    }

    /// Создаёт или продлевает правила. Возвращает, через сколько секунд повторить.
    private func refresh() async -> Int {
        let gateway = PortMapper.defaultGateway()
        if let gateway {
            // PCP: на каждый протокол свой nonce, при продлении — прежний.
            var nonces: [UInt8: Data] = [:]
            if case .pcp(_, let old) = mapping { nonces = old }
            var assigned: (UInt16, String)?
            var ok = true
            for proto: UInt8 in [6, 17] {
                let nonce = nonces[proto] ?? Data((0..<12).map { _ in UInt8.random(in: 0...255) })
                nonces[proto] = nonce
                guard let r = await PortMapper.pcp(gateway: gateway, proto: proto, port: port, lifetime: 7200, nonce: nonce) else { ok = false; break }
                if proto == 6 { assigned = r }
            }
            if ok, let assigned {
                mapping = .pcp(gateway: gateway, nonces: nonces)
                status = PortMappingStatus(method: "PCP", externalIP: assigned.1, externalPort: assigned.0, gateway: gateway, message: "Порт открыт")
                return 3600
            }
            // NAT-PMP.
            if let tcp = await PortMapper.natpmp(gateway: gateway, op: 2, port: port, lifetime: 7200),
               await PortMapper.natpmp(gateway: gateway, op: 1, port: port, lifetime: 7200) != nil {
                mapping = .natpmp(gateway: gateway)
                status = PortMappingStatus(method: "NAT-PMP", externalIP: await PortMapper.natpmpAddress(gateway: gateway),
                                           externalPort: tcp, gateway: gateway, message: "Порт открыт")
                return 3600
            }
        }
        // UPnP IGD.
        do {
            let (control, service, localIP) = try await PortMapper.findIGD(gateway: gateway)
            var lease = 3600
            for proto in ["TCP", "UDP"] {
                func add(_ l: Int) async throws {
                    _ = try await PortMapper.soap(control, service, "AddPortMapping", [
                        ("NewRemoteHost", ""), ("NewExternalPort", "\(port)"), ("NewProtocol", proto),
                        ("NewInternalPort", "\(port)"), ("NewInternalClient", localIP), ("NewEnabled", "1"),
                        ("NewPortMappingDescription", "Swarm"), ("NewLeaseDuration", "\(l)")])
                }
                do {
                    try await add(lease)
                } catch let e as UPnPError where e.code == 725 {
                    lease = 0 // роутер принимает только постоянные правила
                    try await add(0)
                }
            }
            let ip = try? await PortMapper.soap(control, service, "GetExternalIPAddress", [])["NewExternalIPAddress"]
            mapping = .upnp(control: control, service: service)
            status = PortMappingStatus(method: "UPnP", externalIP: ip, externalPort: port, gateway: control.host, message: "Порт открыт")
            return 1800
        } catch {
            mapping = nil
            status = PortMappingStatus(gateway: gateway, message: gateway == nil
                                       ? "Роутер не найден"
                                       : "Роутер не поддерживает проброс (PCP, NAT-PMP, UPnP)")
            return 900
        }
    }

    // MARK: - Шлюз

    /// Шлюз маршрута по умолчанию из таблицы маршрутизации ядра (sysctl NET_RT_FLAGS).
    nonisolated static func defaultGateway() -> String? {
        var mib: [Int32] = [4 /* CTL_NET */, 17 /* PF_ROUTE */, 0, AF_INET, 2 /* NET_RT_FLAGS */, 0x2 /* RTF_GATEWAY */]
        var length = 0
        guard sysctl(&mib, 6, nil, &length, nil, 0) == 0, length > 0 else { return nil }
        var buffer = [UInt8](repeating: 0, count: length)
        guard sysctl(&mib, 6, &buffer, &length, nil, 0) == 0 else { return nil }
        let headerSize = 92 // sizeof(struct rt_msghdr)
        var offset = 0
        while offset + headerSize <= length {
            let msgLength = Int(buffer[offset]) | Int(buffer[offset + 1]) << 8
            guard msgLength > 0 else { break }
            let addrs = Int32(buffer[offset + 12]) | Int32(buffer[offset + 13]) << 8 | Int32(buffer[offset + 14]) << 16 | Int32(buffer[offset + 15]) << 24
            var p = offset + headerSize
            var destination: [UInt8]?
            var gateway: [UInt8]?
            for i in 0..<8 where addrs & (1 << i) != 0 {
                guard p + 2 <= offset + msgLength else { break }
                let saLength = Int(buffer[p])
                let family = Int32(buffer[p + 1])
                if family == AF_INET, saLength >= 8, p + 8 <= buffer.count {
                    let ip = Array(buffer[(p + 4)..<(p + 8)])
                    if i == 0 { destination = ip }
                    if i == 1 { gateway = ip }
                } else if i == 0, saLength == 0 {
                    destination = [0, 0, 0, 0]
                }
                p += saLength > 0 ? (1 + ((saLength - 1) | 3)) : 4
            }
            if destination == [0, 0, 0, 0] || destination == nil, let gateway, gateway != [0, 0, 0, 0] {
                return gateway.map(String.init).joined(separator: ".")
            }
            offset += msgLength
        }
        return nil
    }

    /// Свой адрес в локальной сети, с которого видно шлюз.
    nonisolated static func localAddress(towards host: String) -> String? {
        guard let ip = UDPAddress.parseV4(host) else { return nil }
        let s = socket(AF_INET, SOCK_DGRAM, 0)
        guard s >= 0 else { return nil }
        defer { close(s) }
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = UInt16(1900).bigEndian
        withUnsafeMutableBytes(of: &addr.sin_addr) { $0.copyBytes(from: ip) }
        let connected = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { connect(s, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        }
        guard connected == 0 else { return nil }
        var local = sockaddr_in()
        var len = socklen_t(MemoryLayout<sockaddr_in>.size)
        let got = withUnsafeMutablePointer(to: &local) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(s, $0, &len) }
        }
        guard got == 0 else { return nil }
        return withUnsafeBytes(of: local.sin_addr) { Array($0) }.map(String.init).joined(separator: ".")
    }

    // MARK: - NAT-PMP и PCP

    private nonisolated static func exchange(_ gateway: String, _ payload: Data, accept: @escaping @Sendable (Data) -> Bool) async -> Data? {
        let session = UDPSession(host: gateway, port: 5351)
        defer { session.close() }
        guard (try? await session.start(timeout: 2)) != nil else { return nil }
        for wait in [0.25, 0.5, 1.0] {
            if let reply = try? await session.exchange(payload, timeout: wait, accept: accept) { return reply }
        }
        return nil
    }

    /// op: 1 — UDP, 2 — TCP. Возвращает внешний порт.
    nonisolated static func natpmp(gateway: String, op: UInt8, port: UInt16, lifetime: UInt32) async -> UInt16? {
        var req = Data([0, op, 0, 0])
        req.appendBE(port)
        req.appendBE(lifetime == 0 ? UInt16(0) : port)
        req.appendBE(lifetime)
        guard let r = await exchange(gateway, req, accept: { $0.count >= 16 && $0[$0.startIndex] == 0 && $0[$0.startIndex + 1] == 128 + op }) else { return nil }
        let b = [UInt8](r)
        guard b[2] == 0, b[3] == 0 else { return nil }
        return UInt16(b[10]) << 8 | UInt16(b[11])
    }

    nonisolated static func natpmpAddress(gateway: String) async -> String? {
        guard let r = await exchange(gateway, Data([0, 0]), accept: { $0.count >= 12 && $0[$0.startIndex + 1] == 128 }) else { return nil }
        let b = [UInt8](r)
        guard b[2] == 0, b[3] == 0 else { return nil }
        return b[8..<12].map(String.init).joined(separator: ".")
    }

    /// PCP MAP. proto: 6 — TCP, 17 — UDP. Возвращает внешний порт и адрес.
    nonisolated static func pcp(gateway: String, proto: UInt8, port: UInt16, lifetime: UInt32, nonce: Data) async -> (UInt16, String)? {
        guard let local = localAddress(towards: gateway), let localIP = UDPAddress.parseV4(local) else { return nil }
        var req = Data([2, 1, 0, 0])
        req.appendBE(lifetime)
        req.append(Data([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF] + localIP))
        req.append(nonce)
        req.append(Data([proto, 0, 0, 0]))
        req.appendBE(port)
        req.appendBE(lifetime == 0 ? UInt16(0) : port)
        req.append(Data([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0, 0, 0, 0]))
        guard let r = await exchange(gateway, req, accept: { $0.count >= 4 && $0[$0.startIndex + 1] == 0x81 }) else { return nil }
        let b = [UInt8](r)
        // Ответ версии 0 — роутер говорит только на NAT-PMP.
        guard b.count >= 60, b[0] == 2, b[3] == 0, Data(b[24..<36]) == nonce else { return nil }
        let external = UInt16(b[42]) << 8 | UInt16(b[43])
        return (external, b[56..<60].map(String.init).joined(separator: "."))
    }

    // MARK: - UPnP IGD

    nonisolated struct UPnPError: Error {
        let code: Int
    }

    /// Ищет интернет-шлюз (SSDP) и его сервис WANIPConnection/WANPPPConnection.
    nonisolated static func findIGD(gateway: String?) async throws -> (URL, String, String) {
        let locations = await ssdpSearch(gateway: gateway)
        for location in locations {
            guard let (data, _) = try? await URLSession.shared.data(from: location) else { continue }
            let parser = IGDDescriptionParser()
            let xml = XMLParser(data: data)
            xml.delegate = parser
            guard xml.parse() else { continue }
            let preferred = ["WANIPConnection:2", "WANIPConnection:1", "WANPPPConnection:1"]
            for kind in preferred {
                guard let service = parser.services.first(where: { $0.type.contains(kind) }) else { continue }
                let base = parser.urlBase.flatMap(URL.init(string:)) ?? location
                guard let control = URL(string: service.control, relativeTo: base)?.absoluteURL, let host = control.host,
                      let local = localAddress(towards: host) else { continue }
                return (control, service.type, local)
            }
        }
        throw PeerError("Шлюз UPnP не найден")
    }

    /// M-SEARCH по мультикасту (и напрямую шлюзу): ответы приходят на наш порт с адреса роутера.
    nonisolated static func ssdpSearch(gateway: String?, timeout: TimeInterval = 3) async -> [URL] {
        await withCheckedContinuation { (cont: CheckedContinuation<[URL], Never>) in
            DispatchQueue.global(qos: .utility).async {
                var found: [URL] = []
                let s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
                guard s >= 0 else { cont.resume(returning: []); return }
                defer { close(s) }
                var ttl: UInt8 = 2
                setsockopt(s, IPPROTO_IP, IP_MULTICAST_TTL, &ttl, socklen_t(1))
                var tv = timeval(tv_sec: 0, tv_usec: 300_000)
                setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
                let request = Data("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n".utf8)
                func sendTo(_ ip: [UInt8]) {
                    var addr = sockaddr_in()
                    addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
                    addr.sin_family = sa_family_t(AF_INET)
                    addr.sin_port = UInt16(1900).bigEndian
                    withUnsafeMutableBytes(of: &addr.sin_addr) { $0.copyBytes(from: ip) }
                    _ = request.withUnsafeBytes { b in
                        withUnsafePointer(to: &addr) {
                            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { sendto(s, b.baseAddress, b.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
                        }
                    }
                }
                let deadline = Date().addingTimeInterval(timeout)
                var round = 0
                var buffer = [UInt8](repeating: 0, count: 4096)
                while Date() < deadline {
                    if round % 3 == 0 {
                        sendTo([239, 255, 255, 250])
                        if let gateway, let ip = UDPAddress.parseV4(gateway) { sendTo(ip) }
                    }
                    round += 1
                    let capacity = buffer.count
                    let n = recv(s, &buffer, capacity, 0)
                    guard n > 0 else { continue }
                    let text = String(decoding: buffer[0..<n], as: UTF8.self)
                    for line in text.components(separatedBy: "\r\n") where line.lowercased().hasPrefix("location:") {
                        let value = line.dropFirst(9).trimmingCharacters(in: .whitespaces)
                        if let url = URL(string: value), !found.contains(url) { found.append(url) }
                    }
                    if !found.isEmpty, round > 3 { break }
                }
                cont.resume(returning: found)
            }
        }
    }

    /// Вызов SOAP-действия; возвращает поля ответа.
    @discardableResult
    nonisolated static func soap(_ control: URL, _ service: String, _ action: String, _ args: [(String, String)]) async throws -> [String: String] {
        let body = args.map { "<\($0.0)>\(escape($0.1))</\($0.0)>" }.joined()
        let envelope = """
        <?xml version="1.0"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">\
        <s:Body><u:\(action) xmlns:u="\(service)">\(body)</u:\(action)></s:Body></s:Envelope>
        """
        var request = URLRequest(url: control, timeoutInterval: 6)
        request.httpMethod = "POST"
        request.setValue("text/xml; charset=\"utf-8\"", forHTTPHeaderField: "Content-Type")
        request.setValue("\"\(service)#\(action)\"", forHTTPHeaderField: "SOAPAction")
        request.httpBody = Data(envelope.utf8)
        let (data, response) = try await URLSession.shared.data(for: request)
        let parser = FlatXMLParser()
        let xml = XMLParser(data: data)
        xml.delegate = parser
        _ = xml.parse()
        if let http = response as? HTTPURLResponse, http.statusCode != 200 {
            throw UPnPError(code: Int(parser.values["errorCode"] ?? "") ?? http.statusCode)
        }
        return parser.values
    }

    private nonisolated static func escape(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
    }
}

/// Описание устройства UPnP: все сервисы (тип и адрес управления) и URLBase.
nonisolated final class IGDDescriptionParser: NSObject, XMLParserDelegate {
    struct Service { var type: String; var control: String }
    private(set) var services: [Service] = []
    private(set) var urlBase: String?
    private var text = ""
    private var type = ""
    private var control = ""
    private var inService = false

    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
        text = ""
        if local(name) == "service" { inService = true; type = ""; control = "" }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch local(name) {
        case "serviceType" where inService: type = value
        case "controlURL" where inService: control = value
        case "service":
            inService = false
            if !type.isEmpty, !control.isEmpty { services.append(Service(type: type, control: control)) }
        case "URLBase": urlBase = value
        default: break
        }
        text = ""
    }

    private func local(_ name: String) -> String { name.split(separator: ":").last.map(String.init) ?? name }
}

/// Все листовые элементы SOAP-ответа как словарь «имя → текст».
nonisolated final class FlatXMLParser: NSObject, XMLParserDelegate {
    private(set) var values: [String: String] = [:]
    private var text = ""

    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
        text = ""
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = name.split(separator: ":").last.map(String.init) ?? name
        if !value.isEmpty { values[key] = value }
        text = ""
    }
}
