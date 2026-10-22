import Foundation
import Network

/// Испытательный стенд без интернета: настоящий сервер Xray на этом Mac со всеми протоколами
/// и транспортами, крошечный HTTP-сервер (цель запросов и подписка) и ссылки на серверы в тех форматах,
/// которые выдают панели провайдеров. Используется самопроверками и демо для скриншотов.
nonisolated final class TestBed: @unchecked Sendable {
    let dir: URL
    let uuid = "95fcca28-f3ef-4b2d-b6f0-677f8a479af9"
    var privateKey = ""
    var publicKey = ""
    var certHash = ""
    let ss2022Key = Data((0..<16).map { _ in UInt8.random(in: 0...255) }).base64EncodedString()
    let server = XrayProcess()
    let http = MiniHTTP()
    /// Ссылки: название → ссылка.
    var links: [(String, String)] = []
    var base = 23000
    /// Сервер отправляет любой запрос на HTTP-цель стенда — проверки не зависят от интернета.
    let redirect: Bool

    init(redirect: Bool = true) {
        self.redirect = redirect
        dir = FileManager.default.temporaryDirectory.appendingPathComponent("veil-testbed-\(UUID().uuidString.prefix(6))")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    static func run(_ args: [String]) -> String {
        let p = Process()
        p.executableURL = AppPaths.xray
        p.arguments = args
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        guard (try? p.run()) != nil else { return "" }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        return String(decoding: data, as: UTF8.self)
    }

    static func value(_ text: String, after label: String) -> String {
        for line in text.split(separator: "\n") where line.contains(label) {
            return line.components(separatedBy: label).last?.trimmingCharacters(in: .whitespaces) ?? ""
        }
        return ""
    }

    /// Ключи Reality, самоподписанный сертификат, сервер и HTTP-цель.
    func start() throws {
        let keys = Self.run(["x25519"])
        privateKey = Self.value(keys, after: "PrivateKey:")
        publicKey = Self.value(keys, after: "(PublicKey):")
        if publicKey.isEmpty { publicKey = Self.value(keys, after: "PublicKey:") }
        _ = Self.run(["tls", "cert", "--domain=veil.test", "--name=veil.test", "--file=\(dir.appendingPathComponent("cert").path)"])
        certHash = Self.value(Self.run(["tls", "hash", "--cert", dir.appendingPathComponent("cert.crt").path]), after: "SHA256:")
        guard !privateKey.isEmpty, !publicKey.isEmpty, !certHash.isEmpty else { throw XrayProcess.Failure.failed("ядро не выдало ключи: \(keys)") }
        try http.start()
        while !Ping.isFree(base) || !Ping.isFree(base + 20) { base += 37 }
        try server.start(config: XrayConfig.json(serverConfig()), assets: dir, name: "server")
        // Сервер поднимается не мгновенно (особенно QUIC Hysteria).
        Thread.sleep(forTimeInterval: 0.5)
        links = makeLinks()
    }

    func stop() {
        server.stop()
        http.stop()
    }

    func port(_ i: Int) -> Int { base + i }

    func serverConfig() -> [String: Any] {
        let cert: [String: Any] = ["certificateFile": dir.appendingPathComponent("cert.crt").path, "keyFile": dir.appendingPathComponent("cert.key").path]
        let tls: [String: Any] = ["security": "tls", "tlsSettings": ["certificates": [cert], "alpn": ["h2", "http/1.1"]] as [String: Any]]
        func withTLS(_ base: [String: Any]) -> [String: Any] { base.merging(tls) { $1 } }
        func vless(_ flow: String = "") -> [String: Any] {
            var c: [String: Any] = ["id": uuid]
            if !flow.isEmpty { c["flow"] = flow }
            return ["clients": [c], "decryption": "none"]
        }
        func reality(_ network: String, _ extra: [String: Any]) -> [String: Any] {
            var s: [String: Any] = ["network": network, "security": "reality",
                                    "realitySettings": ["target": "127.0.0.1:\(port(7))", "serverNames": ["veil.test"],
                                                        "privateKey": privateKey, "shortIds": ["", "6ba85179e30d4fc2"]] as [String: Any]]
            for (k, v) in extra { s[k] = v }
            return s
        }
        let inbounds: [[String: Any]] = [
            ["port": port(1), "protocol": "vless", "settings": vless(), "streamSettings": ["network": "ws", "wsSettings": ["path": "/ws"]] as [String: Any]],
            ["port": port(2), "protocol": "vless", "settings": vless("xtls-rprx-vision"), "streamSettings": withTLS(["network": "raw"])],
            ["port": port(3), "protocol": "vless", "settings": vless(), "streamSettings": withTLS(["network": "xhttp", "xhttpSettings": ["path": "/x"]])],
            ["port": port(4), "protocol": "vless", "settings": vless(), "streamSettings": withTLS(["network": "grpc", "grpcSettings": ["serviceName": "svc"]])],
            ["port": port(5), "protocol": "vmess", "settings": ["clients": [["id": uuid]]], "streamSettings": ["network": "raw"]],
            ["port": port(6), "protocol": "vmess", "settings": ["clients": [["id": uuid]]], "streamSettings": ["network": "httpupgrade", "httpupgradeSettings": ["path": "/up"]] as [String: Any]],
            ["port": port(7), "protocol": "trojan", "settings": ["clients": [["password": "secret"]]], "streamSettings": withTLS(["network": "raw"])],
            ["port": port(8), "protocol": "shadowsocks", "settings": ["method": "2022-blake3-aes-128-gcm", "password": ss2022Key, "network": "tcp,udp"]],
            ["port": port(9), "protocol": "shadowsocks", "settings": ["method": "chacha20-ietf-poly1305", "password": "pw", "network": "tcp,udp"]],
            ["port": port(10), "protocol": "socks", "settings": ["auth": "password", "accounts": [["user": "u", "pass": "p"]], "udp": true] as [String: Any]],
            ["port": port(11), "protocol": "hysteria", "settings": ["version": 2, "clients": [["auth": "hy-pass"]]] as [String: Any],
             "streamSettings": ["network": "hysteria", "hysteriaSettings": ["version": 2], "security": "tls",
                                "tlsSettings": ["certificates": [cert], "alpn": ["h3"]] as [String: Any],
                                "finalmask": ["udp": [["type": "salamander", "settings": ["password": "obfs-pw"]]]]] as [String: Any]],
            ["port": port(12), "protocol": "vless", "settings": vless("xtls-rprx-vision"), "streamSettings": reality("raw", [:])],
            ["port": port(13), "protocol": "vless", "settings": vless(), "streamSettings": reality("xhttp", ["xhttpSettings": ["path": "/r"]])],
        ]
        var freedom: [String: Any] = ["finalRules": [["action": "allow"]]]
        if redirect { freedom["redirect"] = "127.0.0.1:\(http.port)" }
        return [
            "log": ["loglevel": "warning"],
            "inbounds": inbounds.map { var i = $0; i["listen"] = "127.0.0.1"; return i },
            // Сервер стенда ходит на 127.0.0.1 — по умолчанию Xray такие адреса не пускает.
            "outbounds": [["protocol": "freedom", "settings": freedom]],
        ]
    }

    func makeLinks() -> [(String, String)] {
        let h = "127.0.0.1"
        let pcs = certHash
        let vmessJSON = #"{"v":"2","ps":"VMess TCP (v2rayN)","add":"\#(h)","port":"\#(port(5))","id":"\#(uuid)","aid":"0","scy":"auto","net":"tcp","type":"none","host":"","path":"","tls":""}"#
        let ssKey = ss2022Key.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ss2022Key
        return [
            ("VLESS WebSocket", "vless://\(uuid)@\(h):\(port(1))?type=ws&path=%2Fws&host=example.com&security=none&encryption=none#VLESS%20WebSocket"),
            ("VLESS TLS Vision", "vless://\(uuid)@\(h):\(port(2))?type=tcp&security=tls&sni=veil.test&fp=chrome&flow=xtls-rprx-vision&pcs=\(pcs)#VLESS%20TLS%20Vision"),
            ("VLESS XHTTP TLS", "vless://\(uuid)@\(h):\(port(3))?type=xhttp&path=%2Fx&mode=auto&security=tls&sni=veil.test&alpn=h2&pcs=\(pcs)#VLESS%20XHTTP%20TLS"),
            ("VLESS gRPC TLS", "vless://\(uuid)@\(h):\(port(4))?type=grpc&serviceName=svc&security=tls&sni=veil.test&pcs=\(pcs)#VLESS%20gRPC%20TLS"),
            ("VLESS Reality Vision", "vless://\(uuid)@\(h):\(port(12))?type=tcp&security=reality&sni=veil.test&fp=chrome&pbk=\(publicKey)&sid=6ba85179e30d4fc2&spx=%2F&flow=xtls-rprx-vision#VLESS%20Reality%20Vision"),
            ("VLESS Reality XHTTP", "vless://\(uuid)@\(h):\(port(13))?type=xhttp&path=%2Fr&security=reality&sni=veil.test&fp=firefox&pbk=\(publicKey)&sid=#VLESS%20Reality%20XHTTP"),
            ("VMess TCP (v2rayN)", "vmess://" + Data(vmessJSON.utf8).base64EncodedString()),
            ("VMess HTTPUpgrade", "vmess://\(uuid)@\(h):\(port(6))?type=httpupgrade&path=%2Fup&encryption=aes-128-gcm#VMess%20HTTPUpgrade"),
            ("Trojan TLS", "trojan://secret@\(h):\(port(7))?security=tls&sni=veil.test&pcs=\(pcs)&type=tcp#Trojan%20TLS"),
            ("Shadowsocks 2022", "ss://2022-blake3-aes-128-gcm:\(ssKey)@\(h):\(port(8))#Shadowsocks%202022"),
            ("Shadowsocks SIP002", "ss://" + Data("chacha20-ietf-poly1305:pw".utf8).base64EncodedString() + "@\(h):\(port(9))#Shadowsocks%20SIP002"),
            ("Shadowsocks (старый формат)", "ss://" + Data("chacha20-ietf-poly1305:pw@\(h):\(port(9))".utf8).base64EncodedString() + "#Shadowsocks%20(%D1%81%D1%82%D0%B0%D1%80%D1%8B%D0%B9%20%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82)"),
            ("SOCKS5", "socks://" + Data("u:p".utf8).base64EncodedString() + "@\(h):\(port(10))#SOCKS5"),
            ("Hysteria 2", "hy2://hy-pass@\(h):\(port(11))/?sni=veil.test&obfs=salamander&obfs-password=obfs-pw&pinSHA256=\(pcs)#Hysteria%202"),
        ]
    }

    /// Обычное имя сайта (не localhost — такие адреса системы ходят мимо прокси); сервер стенда
    /// перенаправляет запрос на свою HTTP-цель.
    var targetURL: URL { URL(string: "http://check.veil.example/204")! }
}

/// Крошечный HTTP-сервер стенда: /204 — пустой ответ, /big — 4 МБ, /sub — подписка с заголовками.
nonisolated final class MiniHTTP: @unchecked Sendable {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "app.veil.minihttp")
    private(set) var port = 0
    private let lock = NSLock()
    private var routes: [String: (headers: [String: String], body: Data)] = [:]

    func set(_ path: String, headers: [String: String] = [:], body: Data) {
        lock.withLock { routes[path] = (headers, body) }
    }

    func start() throws {
        set("/204", body: Data())
        set("/big", body: Data(repeating: 0x61, count: 4 << 20))
        let l = try NWListener(using: .tcp, on: .any)
        let ready = DispatchSemaphore(value: 0)
        l.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        l.newConnectionHandler = { [weak self] c in self?.serve(c) }
        l.start(queue: queue)
        _ = ready.wait(timeout: .now() + 5)
        port = Int(l.port?.rawValue ?? 0)
        listener = l
    }

    func stop() { listener?.cancel() }

    private func serve(_ c: NWConnection) {
        c.start(queue: queue)
        c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            guard let self, let data else { c.cancel(); return }
            let request = String(decoding: data, as: UTF8.self)
            let path = request.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
            let route = self.lock.withLock { self.routes[path.split(separator: "?").first.map(String.init) ?? path] }
            var head: String
            var body = Data()
            if let route {
                let status = route.body.isEmpty && path.hasPrefix("/204") ? "204 No Content" : "200 OK"
                head = "HTTP/1.1 \(status)\r\nContent-Length: \(route.body.count)\r\nConnection: close\r\n"
                for (k, v) in route.headers { head += "\(k): \(v)\r\n" }
                body = request.hasPrefix("HEAD") ? Data() : route.body
            } else {
                head = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n"
            }
            c.send(content: Data((head + "\r\n").utf8) + body, completion: .contentProcessed { _ in c.cancel() })
        }
    }
}
