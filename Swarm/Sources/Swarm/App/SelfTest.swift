import Foundation

/// Самопроверки для CI: запускают настоящий движок без окна.
///   --selftest-core                      — bencode, magnet, создание торрента, векторы протоколов (MSE, uTP, DHT, v2, BEP 6)
///   --selftest-loopback <папка>          — движки на одной машине: TCP и uTP, шифрование, magnet, v2, DHT, LSD, веб-сид
///   --selftest-internet <.torrent> <папка> — реальный рой: куски с проверкой SHA-1, uTP и шифрование, DHT, проброс порта
enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        let rest = Array(args.drop { $0 != mode }.dropFirst())
        setvbuf(stdout, nil, _IOLBF, 0)
        Task {
            var ok = false
            switch mode {
            case "--selftest-core":
                ok = core()
                ok = await ProtocolSelfTest.run() && ok
            case "--selftest-loopback":
                ok = await LoopbackSelfTest.run(URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory()), webSeed: { folder in
                    let server = try await RangeServer.start(root: folder)
                    return (URL(string: "http://127.0.0.1:\(server.port)/")!, { server.stop() })
                })
            case "--selftest-render": ok = RenderSelfTest.run(URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory()))
            case "--selftest-internet":
                ok = rest.count >= 2 ? await internet(URL(fileURLWithPath: rest[0]), URL(fileURLWithPath: rest[1])) : false
            default: print("Неизвестный режим \(mode)")
            }
            print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
            exit(ok ? 0 : 1)
        }
        RunLoop.main.run()
    }

    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    // MARK: - Ядро

    static func core() -> Bool {
        var ok = true
        let value = BValue.dict(["b": .int(-42), "a": .list([.str("x"), .bytes(Data([0, 255]))])])
        let encoded = value.encoded
        ok = check(String(decoding: encoded.prefix(3), as: UTF8.self) == "d1:", "bencode: ключи отсортированы") && ok
        ok = check((try? Bencode.decode(encoded)) == value, "bencode: кодирование ↔ разбор") && ok

        let hex = "c12fe1c06bba254a9dc9f519b335aa7c1367a88a"
        let m1 = MagnetLink("magnet:?xt=urn:btih:\(hex)&dn=Test&tr=udp%3A%2F%2Ftracker.example%3A80")
        ok = check(m1?.infoHash.hex == hex && m1?.name == "Test" && m1?.trackers.count == 1, "magnet: hex, имя, трекер") && ok
        let m2 = MagnetLink("magnet:?xt=urn:btih:YEX6DQDLXISUVHOJ6UM3GNNKPQJWPKEK")
        ok = check(m2?.infoHash.hex == hex, "magnet: base32") && ok

        let compact = PeerAddress.compact(Data([127, 0, 0, 1, 0x1A, 0xE1]))
        ok = check(compact.first == PeerAddress(host: "127.0.0.1", port: 6881), "компактные адреса пиров") && ok

        var bf = Bitfield(count: 10)
        bf[0] = true; bf[9] = true; bf[9] = true
        ok = check(bf.setCount == 2 && bf.data == Data([0x80, 0x40]), "битовое поле") && ok
        // Чужое битовое поле: хвостовые биты за пределами числа кусков отбрасываются (65 кусков → маска 0x80).
        let remote = Bitfield(count: 65, data: Data(repeating: 0xFF, count: 9))
        ok = check(remote.setCount == 65 && remote.isComplete && remote.data.last == 0x80, "битовое поле от пира") && ok

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("swarm-core-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir.appendingPathComponent("Folder/sub"), withIntermediateDirectories: true)
        try? Data(repeating: 7, count: 100_000).write(to: dir.appendingPathComponent("Folder/a.bin"))
        try? Data(repeating: 9, count: 33_333).write(to: dir.appendingPathComponent("Folder/sub/b.bin"))
        if let meta = try? TorrentCreator.create(from: dir.appendingPathComponent("Folder"), options: .init(pieceLength: 16384, version: .v1)),
           let reparsed = try? Metainfo(torrentData: meta.torrentFileData()) {
            ok = check(reparsed.infoHash == meta.infoHash && reparsed.files.count == 2 && reparsed.totalLength == 133_333
                       && reparsed.pieceCount == 9, "создание .torrent и обратный разбор") && ok
            ok = check(reparsed.files[1].path == ["Folder", "sub", "b.bin"], "пути файлов") && ok
        } else {
            ok = check(false, "создание .torrent")
        }
        ok = check(Metainfo.sanitize("..") == "_" && Metainfo.sanitize("a/b") == "a_b", "защита путей") && ok
        return ok
    }

    static func waitFor(_ engine: Engine, _ id: String, timeout: TimeInterval,
                        _ condition: @Sendable (TorrentSnapshot) -> Bool) async -> TorrentSnapshot? {
        await LoopbackSelfTest.waitFor(engine, id, timeout: timeout, condition)
    }

    // MARK: - Интернет

    static func internet(_ torrentFile: URL, _ dir: URL) async -> Bool {
        guard let data = try? Data(contentsOf: torrentFile), let meta = try? Metainfo(torrentData: data) else {
            return check(false, "не удалось прочитать \(torrentFile.lastPathComponent)")
        }
        print("  \(meta.name): \(meta.pieceCount) кусков по \(meta.pieceLength / 1024) КБ, трекеров \(meta.trackers.flatMap { $0 }.count)")
        let engine = Engine(supportDirectory: dir.appendingPathComponent("support"), port: UInt16.random(in: 50000...60000), settings: EngineSettings())
        await engine.start(restore: false)
        await engine.add(meta: meta, savePath: dir.appendingPathComponent("data"))
        let start = Date()
        let target = 8
        let got = await waitFor(engine, meta.infoHash.hex, timeout: 150) { s in
            Bitfield(count: s.pieceCount, data: s.havePieces).setCount >= target
        }
        var ok = check(got != nil, "реальный рой: \(target) кусков проверено SHA-1 за \(Int(Date().timeIntervalSince(start))) с")
        if let s = await engine.snapshots(detailed: meta.infoHash.hex).first {
            print("  пиров \(s.peersConnected) (известно \(s.peersKnown)), ↓ \(Int(s.downRate / 1024)) КБ/с")
            for t in s.trackers.prefix(3) { print("  трекер \(t.url): \(t.status), сидов \(t.seeders ?? -1)") }
            // Трекер Debian работает по http:// — проверяем, что ATS его не блокирует.
            if !meta.trackers.isEmpty {
                ok = check(s.trackers.contains { $0.status == "Работает" }, "HTTP-трекер ответил") && ok
            }
            let c = s.connections
            print("  соединений \(c.total): TCP \(c.tcp), uTP \(c.utp), входящих \(c.incoming); клиенты: \(c.clients.suffix(6).joined(separator: ", "))")
            ok = check(c.encrypted > 0, "шифрование MSE с настоящими клиентами: \(c.encrypted) из \(c.total) соединений") && ok
            ok = check(c.fast > 0, "Fast extension с настоящими клиентами: \(c.fast) соединений") && ok
        }
        await engine.pauseAll()

        // Только uTP: те же куски с настоящих пиров поверх UDP.
        var utpOptions = EngineOptions()
        utpOptions.utpOnly = true
        // Без веб-сидов: у торрентов Debian есть url-list, и куски пришли бы по HTTP.
        var utpSettings = EngineSettings()
        utpSettings.webSeedsEnabled = false
        let utpEngine = Engine(supportDirectory: dir.appendingPathComponent("support-utp"), port: UInt16.random(in: 50000...60000),
                               settings: utpSettings, options: utpOptions)
        await utpEngine.start(restore: false)
        await utpEngine.add(meta: meta, savePath: dir.appendingPathComponent("data-utp"))
        let utpStart = Date()
        let viaUTP = await waitFor(utpEngine, meta.infoHash.hex, timeout: 120) { s in
            Bitfield(count: s.pieceCount, data: s.havePieces).setCount >= 4
        }
        let uc = await utpEngine.snapshots(detailed: nil).first?.connections
        ok = check(viaUTP != nil && (uc?.utp ?? 0) > 0,
                   "uTP с настоящими клиентами: 4 куска за \(Int(Date().timeIntervalSince(utpStart))) с, uTP-соединений \(uc?.utp ?? 0), шифрованных \(uc?.encrypted ?? 0)") && ok
        await utpEngine.pauseAll()

        // Только DHT: magnet без трекеров, метаданные от найденных пиров.
        var dhtOnly = EngineSettings()
        dhtOnly.trackersEnabled = false
        let dhtEngine = Engine(supportDirectory: dir.appendingPathComponent("support-dht"), port: UInt16.random(in: 50000...60000), settings: dhtOnly)
        await dhtEngine.start(restore: false)
        guard let magnet = MagnetLink("magnet:?xt=urn:btih:\(meta.infoHash.hex)") else { return false }
        let dhtStart = Date()
        await dhtEngine.add(magnet: magnet, savePath: dir.appendingPathComponent("data-dht"))
        let resolved = await waitFor(dhtEngine, meta.infoHash.hex, timeout: 150) { $0.status != .metadata }
        let nodes = await dhtEngine.dhtNodes
        ok = check(resolved != nil, "DHT без трекеров: метаданные получены за \(Int(Date().timeIntervalSince(dhtStart))) с, узлов \(nodes)") && ok
        // Первый движок работал всё это время: роутер и DHT-узел успели проявить себя.
        let net = await engine.networkStatus()
        print("  порт \(net.port), проброс: \(net.mapping.method ?? "—") \(net.mapping.message)\(net.mapping.externalIP.map { ", внешний адрес \($0)" } ?? "")")
        print("  DHT-узел: знает \(net.dhtNodes), ответил на \(net.dhtQueries) чужих запросов, хранит \(net.dhtStoredPeers) анонсов")
        return ok
    }
}
