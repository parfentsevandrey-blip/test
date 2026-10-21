import Foundation

/// Самопроверки для CI: запускают настоящий движок без окна.
///   --selftest-core                      — bencode, magnet, создание торрента
///   --selftest-loopback <папка>          — раздача между тремя движками на одной машине (TCP + ut_metadata)
///   --selftest-internet <.torrent> <папка> — реальный рой: куски с проверкой SHA-1 и поиск пиров через DHT
enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        let rest = Array(args.drop { $0 != mode }.dropFirst())
        setvbuf(stdout, nil, _IOLBF, 0)
        Task {
            var ok = false
            switch mode {
            case "--selftest-core": ok = core()
            case "--selftest-loopback": ok = await loopback(URL(fileURLWithPath: rest.first ?? NSTemporaryDirectory()))
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

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("swarm-core-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir.appendingPathComponent("Folder/sub"), withIntermediateDirectories: true)
        try? Data(repeating: 7, count: 100_000).write(to: dir.appendingPathComponent("Folder/a.bin"))
        try? Data(repeating: 9, count: 33_333).write(to: dir.appendingPathComponent("Folder/sub/b.bin"))
        if let meta = try? TorrentCreator.create(from: dir.appendingPathComponent("Folder"), options: .init(pieceLength: 16384)),
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

    // MARK: - Петля

    static func loopback(_ base: URL) async -> Bool {
        let fm = FileManager.default
        let root = base.appendingPathComponent("loopback-\(UUID().uuidString.prefix(6))")
        let payload = root.appendingPathComponent("seed/Payload")
        try? fm.createDirectory(at: payload.appendingPathComponent("sub"), withIntermediateDirectories: true)
        func random(_ n: Int) -> Data { Data((0..<n).map { _ in UInt8.random(in: 0...255) }) }
        try? random(3_000_000).write(to: payload.appendingPathComponent("movie.bin"))
        try? random(1_234_567).write(to: payload.appendingPathComponent("sub/extra.bin"))
        try? Data("hello swarm".utf8).write(to: payload.appendingPathComponent("readme.txt"))

        guard let meta = try? TorrentCreator.create(from: payload, options: .init(pieceLength: 65536)) else {
            return check(false, "не удалось создать торрент")
        }
        var settings = EngineSettings()
        settings.dhtEnabled = false
        settings.trackersEnabled = false

        let seeder = Engine(supportDirectory: root.appendingPathComponent("A"), port: UInt16.random(in: 30000...39999), settings: settings)
        await seeder.start(restore: false)
        await seeder.add(meta: meta, savePath: root.appendingPathComponent("seed"))
        let seedPort = await seeder.port
        guard let seeded = await waitFor(seeder, meta.infoHash.hex, timeout: 30, { $0.status == .seeding }) else {
            return check(false, "сидер не проверил свои файлы")
        }
        var ok = check(seeded.progress == 1, "сидер: проверка существующих файлов, \(meta.pieceCount) кусков")

        // 1. Обычный торрент: подключаемся к сидеру напрямую.
        let leecher = Engine(supportDirectory: root.appendingPathComponent("B"), port: UInt16.random(in: 40000...49999), settings: settings)
        await leecher.start(restore: false)
        let start = Date()
        await leecher.add(meta: meta, savePath: root.appendingPathComponent("B-data"))
        await leecher.addPeers(meta.infoHash.hex, [PeerAddress(host: "127.0.0.1", port: seedPort)])
        let done = await waitFor(leecher, meta.infoHash.hex, timeout: 60, { $0.status == .seeding })
        ok = check(done != nil, "торрент скачан по TCP за \(String(format: "%.1f", Date().timeIntervalSince(start))) с") && ok
        ok = check(same(payload, root.appendingPathComponent("B-data/Payload")), "файлы совпадают побайтно") && ok

        // 2. Magnet: метаданные приходят от сидера по ut_metadata.
        let magnetEngine = Engine(supportDirectory: root.appendingPathComponent("C"), port: UInt16.random(in: 50000...59999), settings: settings)
        await magnetEngine.start(restore: false)
        let link = "magnet:?xt=urn:btih:\(meta.infoHash.hex)&x.pe=127.0.0.1:\(seedPort)"
        guard let magnet = MagnetLink(link) else { return check(false, "magnet не разобран") }
        await magnetEngine.add(magnet: magnet, savePath: root.appendingPathComponent("C-data"))
        let viaMagnet = await waitFor(magnetEngine, meta.infoHash.hex, timeout: 60, { $0.status == .seeding })
        ok = check(viaMagnet != nil, "magnet: метаданные (ut_metadata) и данные получены") && ok
        ok = check(same(payload, root.appendingPathComponent("C-data/Payload")), "magnet: файлы совпадают") && ok

        // 3. Отдача: сидер видел исходящий трафик.
        let seederAfter = await seeder.snapshots(detailed: nil).first
        ok = check((seederAfter?.uploadedTotal ?? 0) >= meta.totalLength, "сидер отдал \(seederAfter?.uploadedTotal ?? 0) байт") && ok
        try? fm.removeItem(at: root)
        return ok
    }

    static func same(_ a: URL, _ b: URL) -> Bool {
        let fm = FileManager.default
        let files = (fm.enumerator(at: a, includingPropertiesForKeys: nil)?.allObjects as? [URL]) ?? []
        let base = a.resolvingSymlinksInPath().pathComponents.count
        var compared = 0
        for f in files where !f.hasDirectoryPath {
            let rel = f.resolvingSymlinksInPath().pathComponents.dropFirst(base).joined(separator: "/")
            guard let x = try? Data(contentsOf: f), let y = try? Data(contentsOf: b.appendingPathComponent(rel)), x == y else {
                print("   различается: \(rel)")
                return false
            }
            compared += 1
        }
        return compared > 0
    }

    static func waitFor(_ engine: Engine, _ id: String, timeout: TimeInterval,
                        _ condition: (TorrentSnapshot) -> Bool) async -> TorrentSnapshot? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let s = await engine.snapshots(detailed: id).first(where: { $0.id == id }) {
                if condition(s) { return s }
                if s.status == .error { print("   ошибка: \(s.error ?? "?")"); return nil }
            }
            try? await Task.sleep(for: .milliseconds(200))
        }
        if let s = await engine.snapshots(detailed: id).first(where: { $0.id == id }) {
            print("   по таймауту: \(s.status.rawValue), \(Int(s.progress * 100))%, пиров \(s.peersConnected)/\(s.peersKnown)")
        }
        return nil
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
        }
        await engine.pauseAll()

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
        return ok
    }
}
