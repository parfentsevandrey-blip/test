import Foundation
import VeilShared

/// Самопроверки для CI: `Veil.app/Contents/MacOS/Veil --selftest-<режим>`.
///
/// - `logic`  — без сети и ядра: ссылки всех форматов, подписки, профили Happ, правила, очистка конфигурации;
/// - `core`   — настоящее ядро: сервер со всеми протоколами на этом Mac, подключение по каждой ссылке,
///              задержка, счётчики трафика, подписка по HTTP, базы сайтов и все режимы маршрутизации;
/// - `helper` — помощник (запускается в CI через sudo): системный прокси, сторож, TUN и возврат DNS.
nonisolated enum SelfTest {
    static func runIfRequested() {
        guard let mode = CommandLine.arguments.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        var ok = false
        switch mode {
        case "--selftest-logic": ok = logic()
        case "--selftest-core": ok = blocking { await core() }
        case "--selftest-helper": ok = blocking { await helper() }
        default: print("Неизвестный режим \(mode)")
        }
        print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
        Foundation.exit(ok ? 0 : 1)
    }

    @discardableResult
    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    final class Box<T>: @unchecked Sendable {
        private let lock = NSLock()
        private var _value: T
        init(_ value: T) { _value = value }
        var value: T {
            get { lock.withLock { _value } }
            set { lock.withLock { _value = newValue } }
        }
    }

    static func blocking(_ work: @escaping @Sendable () async -> Bool) -> Bool {
        let box = Box(false)
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            box.value = await work()
            done.signal()
        }
        done.wait()
        return box.value
    }

    static func shell(_ command: String) -> String {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/bin/sh")
        p.arguments = ["-c", command]
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        guard (try? p.run()) != nil else { return "" }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        return String(decoding: data, as: UTF8.self)
    }

    // MARK: - Логика

    static func logic() -> Bool {
        var ok = true
        func parse(_ link: String) -> Server? {
            switch Links.parse(link) {
            case .success(let s): return s
            case .failure(let e): print("    ошибка: \(e)"); return nil
            }
        }

        print("— Ссылки")
        if let s = parse("vless://95fcca28-f3ef-4b2d-b6f0-677f8a479af9@nl.example.com:443?type=tcp&security=reality&sni=www.microsoft.com&fp=chrome&pbk=HR_gtTVrlTLp-0zDvtRuXGfTpP3RQ50YTCJtzyt_cnQ&sid=6ba8&spx=%2F&flow=xtls-rprx-vision&encryption=none#%F0%9F%87%B3%F0%9F%87%B1%20%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B") {
            let e = s.endpoint
            ok = check(e.proto == .vless && e.address == "nl.example.com" && e.port == 443 && e.security == "reality" && e.publicKey.hasPrefix("HR_")
                       && e.shortId == "6ba8" && e.flow == "xtls-rprx-vision" && e.network == "raw", "VLESS Reality Vision: \(e.summary)") && ok
            ok = check(s.flag == "🇳🇱" && s.title == "Нидерланды", "флаг и название из ссылки: \(s.flag) «\(s.title)»") && ok
        } else { ok = check(false, "VLESS Reality") && ok }

        if let s = parse("vless://uuid-1@[2001:db8::1]:8443?type=xhttp&path=%2Fapi&host=cdn.example.com&mode=packet-up&security=tls&sni=cdn.example.com&alpn=h2,http/1.1&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D#XHTTP?serverDescription=0JHRi9GB0YLRgNGL0Lk=&fragment=1-10,5-20,tlshello") {
            let e = s.endpoint
            ok = check(e.address == "2001:db8::1" && e.port == 8443 && e.network == "xhttp" && e.mode == "packet-up" && e.alpn == ["h2", "http/1.1"]
                       && e.extra.contains("xPaddingBytes"), "IPv6, XHTTP, alpn, extra") && ok
            ok = check(s.name == "XHTTP" && s.note == "Быстрый" && e.fragment == "1-10,5-20,tlshello", "параметры Happ после «#»: описание «\(s.note ?? "")», фрагментация") && ok
            if let stream = try? XrayConfig.stream(e, global: FragmentSettings()),
               let mask = stream["finalmask"] as? [String: Any], let tcp = mask["tcp"] as? [[String: Any]] {
                ok = check(tcp.first?["type"] as? String == "fragment", "фрагментация из ссылки попала в finalmask") && ok
            } else { ok = check(false, "finalmask фрагментации") && ok }
        }

        let vmess = #"{"v":"2","ps":"🇩🇪 Germany","add":"de.example.com","port":443,"id":"abc","aid":"0","scy":"auto","net":"ws","type":"none","host":"de.example.com","path":"/vm","tls":"tls","sni":"","fp":"safari"}"#
        if let s = parse("vmess://" + Data(vmess.utf8).base64EncodedString()) {
            let e = s.endpoint
            ok = check(e.proto == .vmess && e.network == "ws" && e.security == "tls" && e.sni == "de.example.com" && e.path == "/vm" && e.port == 443 && s.flag == "🇩🇪",
                       "VMess v2rayN (порт числом, SNI из host): \(e.summary)") && ok
        }
        if let s = parse("trojan://p%40ss@tr.example.com:443?type=grpc&serviceName=svc&mode=multi#Trojan") {
            ok = check(s.endpoint.password == "p@ss" && s.endpoint.security == "tls" && s.endpoint.mode == "multi", "Trojan: пароль раскодирован, TLS по умолчанию, gRPC multi") && ok
        }
        if let s = parse("ss://" + Data("aes-256-gcm:secret".utf8).base64EncodedString() + "@1.2.3.4:8388#SS"),
           let old = parse("ss://" + Data("aes-256-gcm:secret@1.2.3.4:8388".utf8).base64EncodedString() + "#Old"),
           let ss22 = parse("ss://2022-blake3-aes-256-gcm:a2V5JTJCa2V5%3D@5.6.7.8:443#SS22") {
            ok = check(s.endpoint.method == "aes-256-gcm" && s.endpoint.password == "secret" && old.endpoint.port == 8388 && old.endpoint.address == "1.2.3.4"
                       && ss22.endpoint.password == "a2V5JTJCa2V5=" && ss22.endpoint.method == "2022-blake3-aes-256-gcm", "Shadowsocks: SIP002, старый base64, 2022") && ok
        }
        if let s = parse("ss://YWVzLTEyOC1nY206eA==@1.1.1.1:1?plugin=v2ray-plugin%3Bmode%3Dwebsocket#P") {
            ok = check(s.issue != nil, "плагин Shadowsocks честно помечен неподдерживаемым") && ok
        }
        if let s = parse("hy2://user:pass@hy.example.com:443,20000-30000/?sni=hy.example.com&obfs=salamander&obfs-password=x&insecure=1#Hy") {
            ok = check(s.endpoint.password == "user:pass" && s.endpoint.port == 443 && s.endpoint.hopPorts == "443,20000-30000" && s.endpoint.obfs == "salamander"
                       && s.warning != nil, "Hysteria 2: смена портов, обфускация, предупреждение про insecure") && ok
        }
        if let s = parse("socks://" + Data("user:pw".utf8).base64EncodedString() + "@9.9.9.9:1080#S") {
            ok = check(s.endpoint.user == "user" && s.endpoint.password == "pw", "SOCKS с base64") && ok
        }
        if let s = parse("vless://id@h:443?type=h2&security=tls#Old") {
            ok = check(s.issue?.contains("XHTTP") == true, "устаревший транспорт h2 — понятная причина") && ok
        }
        ok = check(Links.parse("vless://@host:443").failureDescription != nil && Links.parse("ftp://x").failureDescription != nil, "битые ссылки отклоняются с объяснением") && ok
        let mixed = Links.parseAll("мусор\nvless://a@h:1#A\n  trojan://b@h:2#B  \nhttps://example.com\nvless://broken")
        ok = check(mixed.servers.count == 2 && mixed.failures.count == 1, "текст со смесью строк: \(mixed.servers.count) сервера, \(mixed.failures.count) ошибка") && ok
        if let s = parse("vless://95fcca28@h.example:443?type=ws&path=%2Fw&security=tls&sni=h.example#Back") {
            let again = Links.link(for: Server(id: "", name: s.name, endpoint: s.endpoint, link: ""))
            ok = check(parse(again)?.endpoint == s.endpoint, "ссылка собирается обратно без потерь") && ok
        }

        print("— Подписки")
        let body = Data("""
        #profile-title: base64:\(Data("Мой VPN".utf8).base64EncodedString())
        #announce: Скидка 20% до конца месяца
        vless://a@h:1#A
        trojan://b@h:2#B
        happ://routing/add/\(Data(#"{"Name":"RU","GlobalProxy":"true","DirectSites":["geosite:category-ru"],"DirectIp":["geoip:ru"]}"#.utf8).base64EncodedString())
        """.utf8).base64EncodedString()
        if let loaded = try? SubscriptionLoader.parse(body: Data(body.utf8), headers: [
            "subscription-userinfo": "upload=1073741824; download=2147483648; total=107374182400; expire=1924992000",
            "profile-update-interval": "6", "support-url": "https://t.me/support",
        ]) {
            let i = loaded.info
            ok = check(loaded.servers.count == 2 && i.title == "Мой VPN" && i.announce.hasPrefix("Скидка") && i.used == 3 << 30 && i.total == 100 << 30
                       && i.updateHours == 6 && i.expire != nil && i.routing != nil, "base64-тело, строки #параметров, userinfo, профиль маршрутизации") && ok
        } else { ok = check(false, "разбор подписки") && ok }
        let json = #"[{"remarks":"🇫🇮 Финляндия","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"fi.example","port":443,"users":[{"id":"x","encryption":"none"}]}]},"streamSettings":{"network":"raw"}},{"tag":"direct","protocol":"freedom"}]}]"#
        if let loaded = try? SubscriptionLoader.parse(body: Data(json.utf8), headers: [:]), let s = loaded.servers.first {
            ok = check(s.endpoint.proto == .json && s.endpoint.address == "fi.example" && s.flag == "🇫🇮", "JSON-подписка (Remnawave): сервер из outbound") && ok
        }
        do {
            _ = try SubscriptionLoader.parse(body: Data("proxies:\n  - name: x".utf8), headers: [:])
            ok = check(false, "Clash должен распознаваться") && ok
        } catch {
            ok = check("\(error)".contains("Clash"), "формат Clash — понятное сообщение") && ok
        }
        ok = check(SubscriptionLoader.url(from: "happ://add/https://sub.example.com/abc") == "https://sub.example.com/abc"
                   && SubscriptionLoader.isEncryptedHapp("happ://crypt3/xyz"), "happ://add/… и зашифрованные ссылки Happ") && ok

        print("— Маршрутизация")
        if let p = HappRoutingProfile.parse("happ://routing/onadd/" + Data(#"{"Name":"Мой","GlobalProxy":"false","ProxySites":["geosite:youtube"],"BlockSites":["geosite:category-ads-all"],"DomainStrategy":"AsIs","DnsHosts":{"dns.google":"8.8.8.8"}}"#.utf8).base64EncodedString()) {
            ok = check(p.activate && p.profile.name == "Мой" && !p.profile.globalProxy && p.profile.proxySites == ["geosite:youtube"] && p.profile.dnsHosts["dns.google"] == "8.8.8.8",
                       "профиль Happ: onadd, GlobalProxy=false, списки, DnsHosts") && ok
        }
        var geo = GeoCategories()
        geo.sites = ["private", "category-ru", "ru-blocked", "category-ai-chat-!cn", "youtube", "category-ads-all", "ru-available-only-inside"]
        geo.ips = ["private", "ru", "ru-blocked"]
        let smart = RoutingRules.rules(RoutingSettings(mode: .smart), available: geo)
        ok = check(smart.final == "proxy" && smart.rules.contains { $0.outbound == "direct" && $0.ips.contains("geoip:ru") }
                   && smart.rules.firstIndex { $0.domains.contains("geosite:ru-blocked") }! < smart.rules.firstIndex { $0.domains.contains("geosite:category-ru") }!,
                   "умный режим: заблокированное раньше российского, остальное — через VPN") && ok
        let blocked = RoutingRules.rules(RoutingSettings(mode: .blocked), available: geo)
        ok = check(blocked.final == "direct" && blocked.rules.contains { $0.outbound == "proxy" && $0.domains.contains("geosite:ru-blocked") }, "«только заблокированное»: остальное напрямую") && ok
        let none = RoutingRules.rules(RoutingSettings(mode: .smart), available: GeoCategories())
        ok = check(!none.rules.contains { $0.domains.contains { $0.hasPrefix("geosite:") } } && none.rules.contains { $0.domains.contains("domain:ru") },
                   "без баз сайтов правила geosite не попадают в конфигурацию (ядро бы не запустилось)") && ok
        var custom = RoutingSettings(mode: .all)
        custom.direct = ["https://sber.ru/", "10.0.0.0/8", "*.office.local"]
        let user = RoutingRules.rules(custom, available: geo)
        ok = check(user.rules.contains { $0.domains == ["domain:sber.ru", "domain:office.local"] } && user.rules.contains { $0.ips == ["10.0.0.0/8"] },
                   "свои правила: домены и подсети") && ok

        print("— Базы сайтов (protobuf)")
        func entry(_ name: String) -> [UInt8] {
            let inner: [UInt8] = [0x0A, UInt8(name.utf8.count)] + Array(name.utf8) + [0x12, 0x02, 0x08, 0x01]
            return [0x0A, UInt8(inner.count)] + inner
        }
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("veil-geo-test.dat")
        try? Data(entry("RU-BLOCKED") + entry("CATEGORY-RU")).write(to: file)
        ok = check(GeoCategories.read(file) == ["ru-blocked", "category-ru"], "названия категорий читаются из файла без ядра") && ok

        print("— Помощник: очистка конфигурации")
        let dirty = #"{"log":{"access":"/etc/passwd","loglevel":"debug"},"inbounds":[],"outbounds":[{"protocol":"freedom","streamSettings":{"tlsSettings":{"masterKeyLog":"/tmp/k","certificateFile":"/etc/x"}}}]}"#
        if let clean = try? ConfigSanitizer.sanitize(dirty) {
            ok = check(!clean.contains("/etc/passwd") && !clean.contains("masterKeyLog") && !clean.contains("certificateFile"), "файловые поля удалены") && ok
        }
        ok = check((try? ConfigSanitizer.sanitize(#"{"routing":{"rules":[{"domain":["ext:/etc/hosts:x"]}]}}"#)) == nil, "ссылки на внешние файлы запрещены") && ok

        print("— Мелочи")
        ok = check(Format.plural(1, "сервер", "сервера", "серверов") == "сервер" && Format.plural(3, "сервер", "сервера", "серверов") == "сервера"
                   && Format.plural(11, "сервер", "сервера", "серверов") == "серверов", "склонения") && ok
        ok = check(Flags.guess("Amsterdam #2") == "NL" && Flags.emoji("de") == "🇩🇪" && Flags.leading("🇺🇸 США").rest == "США", "флаги по названию") && ok
        ok = check(XrayConfig.dnsAddress(type: "DoH", domain: "https://cloudflare-dns.com/dns-query", ip: "1.1.1.1", local: false) == "https://1.1.1.1/dns-query",
                   "DoH по IP — без запроса DNS ради самого DNS") && ok
        return ok
    }

    // MARK: - Настоящее ядро

    static func core() async -> Bool {
        var ok = true
        let version = XrayProcess.version()
        guard check(!version.isEmpty, "ядро Xray \(version) на месте: \(AppPaths.xray.path)") else { return false }
        let bed = TestBed()
        do { try bed.start() } catch { return check(false, "стенд: \(error)") }
        defer { bed.stop() }
        print("  стенд: сервер Xray с \(bed.links.count) вариантами, сертификат \(bed.certHash.prefix(12))…, HTTP-цель :\(bed.http.port)")

        print("— Подключение по каждой ссылке")
        var servers: [Server] = []
        for (i, (title, link)) in bed.links.enumerated() {
            guard case .success(let server) = Links.parse(link) else { ok = check(false, "\(title): ссылка не разобралась") && ok; continue }
            servers.append(server)
            var o = CoreOptions()
            o.mode = .local
            o.routing.mode = .all
            o.socksPort = 24000 + i * 10
            o.httpPort = o.socksPort + 1
            o.checkPort = o.socksPort + 2
            o.metricsPort = o.socksPort + 3
            guard let config = try? XrayConfig.json(XrayConfig.build(server, o, geo: GeoCategories())) else {
                ok = check(false, "\(title): конфигурация не собралась") && ok
                continue
            }
            let test = XrayProcess.test(config: config, assets: AppPaths.geo)
            guard test.ok else { ok = check(false, "\(title): ядро отклонило конфигурацию: \(test.message)") && ok; continue }
            let client = XrayProcess()
            do { try client.start(config: config, assets: AppPaths.geo, name: "client") } catch {
                ok = check(false, "\(title): клиент не запустился: \(error)") && ok
                continue
            }
            var ms = await Probe.delay(httpPort: o.checkPort, timeout: 6, url: bed.targetURL)
            if ms == nil {
                if case .failure(let error) = await Probe.measure(httpPort: o.checkPort, timeout: 6, url: bed.targetURL) {
                    print("    \(error.localizedDescription)")
                    print("    журнал: " + LogBook.shared.all.suffix(3).map(\.text).joined(separator: " | "))
                }
                ms = await Probe.delay(httpPort: o.checkPort, timeout: 6, url: bed.targetURL)
            }
            let stats = await CoreStats.sample(port: o.metricsPort)
            // Обе стороны: у классического Shadowsocks ядро не засчитывает первый пакет отдачи.
            let bytes = (stats?.up ?? 0) + (stats?.down ?? 0)
            ok = check(ms != nil && bytes > 0, "\(title) [\(server.endpoint.summary)]: \(ms.map { "\($0) мс" } ?? "нет связи"), через сервер ↑\(stats?.up ?? 0) ↓\(stats?.down ?? 0) Б") && ok
            client.stop()
        }

        print("— Проверка задержки всех серверов сразу")
        let results = Box<[String: Ping.Result]>([:])
        let started = Date()
        // Цель — HTTP-сервер стенда (интернета может не быть); в приложении — gstatic.com.
        await Ping.measureForTest(servers, url: bed.targetURL) { id, r in results.value[id] = r }
        let good = results.value.values.compactMap(\.value)
        ok = check(good.count == servers.count, "ответили \(good.count) из \(servers.count) за \(String(format: "%.1f", Date().timeIntervalSince(started))) с, медиана \(good.sorted().dropFirst(good.count / 2).first ?? 0) мс") && ok

        print("— Подписка по HTTP")
        let body = Data(bed.links.map(\.1).joined(separator: "\n").utf8).base64EncodedString()
        bed.http.set("/sub", headers: [
            "profile-title": "base64:" + Data("Стенд Veil".utf8).base64EncodedString(),
            "subscription-userinfo": "upload=0; download=5368709120; total=53687091200; expire=\(Int(Date().timeIntervalSince1970) + 86400 * 20)",
            "announce": "Проверка", "profile-update-interval": "12",
        ], body: Data(body.utf8))
        do {
            let loaded = try await SubscriptionLoader.fetch("http://127.0.0.1:\(bed.http.port)/sub", userAgent: "Veil/1.0 (macOS)", hwid: "test")
            ok = check(loaded.servers.count == bed.links.count && loaded.info.title == "Стенд Veil" && loaded.info.total == 50 << 30,
                       "подписка: \(loaded.servers.count) серверов, «\(loaded.info.title)», \(Format.bytes(loaded.info.used)) из \(Format.bytes(loaded.info.total))") && ok
        } catch { ok = check(false, "подписка: \(error)") && ok }

        print("— Базы сайтов и режимы маршрутизации")
        do {
            let t0 = Date()
            if !GeoFiles.present { try await GeoFiles.download() }
            let geo = GeoFiles.categories()
            let need = ["ru-blocked", "category-ru", "category-ai-chat-!cn", "category-ads-all", "private", "youtube", "ru-available-only-inside"]
            ok = check(need.allSatisfy(geo.sites.contains) && ["ru", "ru-blocked", "private"].allSatisfy(geo.ips.contains),
                       "базы runetfreedom: \(geo.sites.count) категорий сайтов, \(geo.ips.count) адресов, \(Int(Date().timeIntervalSince(t0))) с") && ok
            for mode in RouteMode.allCases {
                var o = CoreOptions()
                o.routing.mode = mode
                o.routing.blockAds = true
                o.routing.direct = ["sber.ru", "10.0.0.0/8"]
                if mode == .profile, let p = HappRoutingProfile.parse(Data(#"{"Name":"Тест","GlobalProxy":"false","ProxySites":["geosite:youtube"],"DirectIp":["geoip:ru"]}"#.utf8).base64EncodedString()) {
                    o.routing.profile = p.profile
                }
                for m in [ConnectionMode.proxy, .tun] {
                    o.mode = m
                    let config = XrayConfig.json(try XrayConfig.build(servers[4], o, geo: geo))
                    let r = XrayProcess.test(config: config, assets: AppPaths.geo)
                    ok = check(r.ok, "«\(mode.title)» + \(m.title): ядро приняло конфигурацию\(r.ok ? "" : " — " + r.message)") && ok
                }
            }
        } catch { ok = check(false, "базы сайтов не скачались: \(error)") && ok }
        return ok
    }

    // MARK: - Помощник

    static func helper() async -> Bool {
        var ok = true
        guard let status = try? await HelperClient.call(HelperRequest(command: .status), timeout: 5) else {
            return check(false, "помощник не отвечает на \(HelperClient.socketPath)")
        }
        ok = check(status.version == HelperInfo.version, "помощник версии \(status.version) отвечает") && ok

        print("— Системный прокси")
        let before = shell("scutil --proxy")
        do {
            _ = try await HelperClient.call(HelperRequest(command: .proxyOn, httpPort: 24809, socksPort: 24808))
            let on = shell("scutil --proxy")
            ok = check(on.contains("HTTPEnable : 1") && on.contains("HTTPPort : 24809") && on.contains("SOCKSPort : 24808"), "включён: видно в scutil --proxy") && ok
            _ = try await HelperClient.call(HelperRequest(command: .proxyOff))
            let off = shell("scutil --proxy")
            ok = check(!off.contains("HTTPPort : 24809"), "выключен, прежние настройки вернулись") && ok
            _ = before
        } catch { ok = check(false, "прокси: \(error)") && ok }

        print("— Сторож: Veil «упал», порт прокси молчит")
        do {
            _ = try await HelperClient.call(HelperRequest(command: .proxyOn, httpPort: 24809, socksPort: 24808))
            try? await Task.sleep(for: .seconds(22))
            let after = shell("scutil --proxy")
            ok = check(!after.contains("HTTPPort : 24809"), "через 15–20 с без ответа прокси выключен сам — Mac не остался без интернета") && ok
        } catch { ok = check(false, "сторож: \(error)") && ok }

        print("— TUN: весь трафик Mac через ядро")
        var o = CoreOptions()
        o.mode = .tun
        o.routing.mode = .all
        var direct = Endpoint(proto: .json)
        direct.rawOutbound = #"{"protocol":"freedom"}"#
        let server = Server(id: "direct", name: "Напрямую через ядро", endpoint: direct, link: "")
        do {
            let dnsBefore = shell("scutil --dns | grep nameserver | sort -u")
            let config = XrayConfig.json(try XrayConfig.build(server, o, geo: GeoCategories()))
            let r = try await HelperClient.call(HelperRequest(command: .tunStart, config: config, assets: AppPaths.geo.path), timeout: 40)
            ok = check(r.tunRunning, "ядро TUN запущено помощником") && ok
            try? await Task.sleep(for: .seconds(2))
            let ifconfig = shell("ifconfig \(o.tunName)")
            ok = check(ifconfig.contains("UP"), "интерфейс \(o.tunName) поднят") && ok
            let code = shell("curl -s -o /dev/null -w '%{http_code}' --max-time 15 https://www.gstatic.com/generate_204")
            let stats = await CoreStats.sample(port: o.metricsPort)
            ok = check(code == "204" && (stats?.up ?? 0) > 0, "обычный curl без прокси прошёл через туннель: HTTP \(code), \(stats?.up ?? 0) Б через ядро") && ok
            let dnsOn = shell("scutil --dns | grep nameserver | sort -u")
            ok = check(dnsOn.contains("1.1.1.1"), "DNS системы направлен в туннель") && ok
            _ = try await HelperClient.call(HelperRequest(command: .tunStop), timeout: 20)
            try? await Task.sleep(for: .seconds(2))
            let dnsAfter = shell("scutil --dns | grep nameserver | sort -u")
            ok = check(dnsAfter == dnsBefore, "после остановки DNS как были") && ok
            let code2 = shell("curl -s -o /dev/null -w '%{http_code}' --max-time 15 https://www.gstatic.com/generate_204")
            ok = check(code2 == "204" && !shell("ifconfig").contains(o.tunName + ":"), "интернет работает напрямую, интерфейса туннеля нет") && ok
        } catch { ok = check(false, "TUN: \(error)") && ok }
        return ok
    }
}

nonisolated extension Result where Failure == Links.Failure {
    var failureDescription: String? { if case .failure(let e) = self { e.description } else { nil } }
}

nonisolated extension Ping {
    /// Как `measure`, но с целью на стенде.
    static func measureForTest(_ servers: [Server], url: URL, onResult: @escaping @Sendable (String, Result) -> Void) async {
        let ports = freePorts(servers.count)
        let config = XrayConfig.json(XrayConfig.tester(Array(zip(servers, ports))))
        let tester = XrayProcess()
        guard (try? tester.start(config: config, assets: AppPaths.geo, name: "tester")) != nil else { return }
        await withTaskGroup(of: Void.self) { group in
            for (server, port) in zip(servers, ports) {
                group.addTask {
                    var ms = await Probe.delay(httpPort: port, timeout: 6, url: url)
                    if ms == nil { ms = await Probe.delay(httpPort: port, timeout: 6, url: url) }
                    onResult(server.id, ms.map { .ms($0) } ?? .timeout)
                }
            }
        }
        tester.stop()
    }
}
