import Foundation

/// Сценарии самопроверки на одной машине: несколько движков обмениваются данными по петле.
/// Каждый сценарий проверяет свой протокол: шифрование, uTP, ut_metadata, Fast extension, v2 и гибрид,
/// запрос хешей v2, DHT (анонс и поиск), LSD, веб-сид, чёрный список, анонимный режим.
nonisolated enum LoopbackSelfTest {
    /// Веб-сид поднимает локальный HTTP-сервер; на стенде без сети его можно пропустить.
    static func run(_ base: URL, webSeed: (@Sendable (URL) async throws -> (URL, @Sendable () -> Void))? = nil) async -> Bool {
        let fm = FileManager.default
        let root = base.appendingPathComponent("loopback-\(UUID().uuidString.prefix(6))")
        defer { try? fm.removeItem(at: root) }
        let payload = root.appendingPathComponent("seed/Payload")
        try? fm.createDirectory(at: payload.appendingPathComponent("sub"), withIntermediateDirectories: true)
        func random(_ n: Int) -> Data { Data((0..<n).map { _ in UInt8.random(in: 0...255) }) }
        try? random(3_000_000).write(to: payload.appendingPathComponent("movie.bin"))
        try? random(1_234_567).write(to: payload.appendingPathComponent("sub/extra.bin"))
        try? Data("hello swarm".utf8).write(to: payload.appendingPathComponent("readme.txt"))

        var options = TorrentCreator.Options()
        options.pieceLength = 65536
        options.version = .v1
        guard let meta = try? TorrentCreator.create(from: payload, options: options) else {
            return check(false, "не удалось создать торрент")
        }
        options.version = .v2
        guard let metaV2 = try? TorrentCreator.create(from: payload, options: options) else { return check(false, "не удалось создать v2-торрент") }
        options.version = .hybrid
        guard let metaHybrid = try? TorrentCreator.create(from: payload, options: options) else { return check(false, "не удалось создать гибрид") }

        var plain = EngineSettings()
        plain.dhtEnabled = false
        plain.trackersEnabled = false
        plain.lsdEnabled = false
        plain.portMapping = false
        var ok = true
        var engines: [Engine] = []
        func engine(_ name: String, _ s: EngineSettings = plain, _ o: EngineOptions = EngineOptions()) async -> Engine {
            let e = Engine(supportDirectory: root.appendingPathComponent(name), port: UInt16.random(in: 30000...60000), settings: s, options: o)
            await e.start(restore: false)
            engines.append(e)
            return e
        }
        defer { for e in engines { Task { await e.shutdown() } } }
        let seedFolder = root.appendingPathComponent("seed")

        // Сидер: все три формата одной папки.
        let seeder = await engine("A")
        await seeder.add(meta: meta, savePath: seedFolder)
        await seeder.add(meta: metaV2, savePath: seedFolder)
        await seeder.add(meta: metaHybrid, savePath: seedFolder)
        let seedPort = await seeder.port
        let seedAddress = PeerAddress(host: "127.0.0.1", port: seedPort)
        for m in [meta, metaV2, metaHybrid] {
            guard await waitFor(seeder, m.infoHash.hex, timeout: 30, { $0.status == .seeding }) != nil else {
                return check(false, "сидер не проверил свои файлы (\(m.version.title))")
            }
        }
        ok = check(true, "сидер: файлы проверены в трёх форматах (v1 \(meta.pieceCount), v2 \(metaV2.pieceCount), гибрид \(metaHybrid.pieceCount) кусков)") && ok

        func download(_ e: Engine, _ m: Metainfo, folder: String, label: String, timeout: TimeInterval = 60,
                      peers: [PeerAddress]? = nil) async -> TorrentSnapshot? {
            let start = Date()
            await e.add(meta: m, savePath: root.appendingPathComponent(folder))
            let list = peers ?? [seedAddress]
            if !list.isEmpty { await e.addPeers(m.infoHash.hex, list) }
            let done = await waitFor(e, m.infoHash.hex, timeout: timeout, { $0.status == .seeding })
            ok = check(done != nil, "\(label): скачано за \(String(format: "%.1f", Date().timeIntervalSince(start))) с") && ok
            ok = check(same(payload, root.appendingPathComponent("\(folder)/Payload")), "\(label): файлы совпадают побайтно") && ok
            return done
        }

        // 1. TCP + MSE (по умолчанию «предпочитать»): соединение зашифровано, Fast extension согласован.
        let b = await engine("B")
        if let c = await download(b, meta, folder: "B-data", label: "TCP + шифрование MSE")?.connections {
            ok = check(c.tcp == 1 && c.utp == 0 && c.encrypted == 1 && c.fast == 1,
                       "соединение: TCP \(c.tcp), uTP \(c.utp), RC4 \(c.encrypted), Fast extension \(c.fast)") && ok
        }

        // 2. Только uTP, требовать шифрование.
        var requireSettings = plain
        requireSettings.encryption = .require
        var utpOnly = EngineOptions()
        utpOnly.utpOnly = true
        let d = await engine("D", requireSettings, utpOnly)
        if let c = await download(d, meta, folder: "D-data", label: "uTP (только шифрованные)")?.connections {
            ok = check(c.utp == 1 && c.tcp == 0 && c.encrypted == 1, "uTP-соединение зашифровано") && ok
        }

        // 3. Без шифрования, анонимный режим: сидер не узнаёт программу.
        var anonymous = plain
        anonymous.encryption = .off
        anonymous.anonymous = true
        let f = await engine("F", anonymous)
        let fc = await download(f, meta, folder: "F-data", label: "открытое соединение, анонимный режим")?.connections
        ok = check(fc?.encrypted == 0 && fc?.tcp == 1, "соединение без шифрования") && ok
        let seen = await seeder.snapshots(detailed: nil).first { $0.id == meta.infoHash.hex }?.connections.clients ?? []
        ok = check(seen.last == "Неизвестный" && seen.dropLast().contains { $0.hasPrefix("Swarm") },
                   "анонимный режим: сидер видит «\(seen.last ?? "?")» вместо «Swarm»") && ok

        // 4. Magnet: метаданные по ut_metadata.
        let c = await engine("C")
        guard let magnet = MagnetLink("magnet:?xt=urn:btih:\(meta.infoHash.hex)&x.pe=127.0.0.1:\(seedPort)") else { return check(false, "magnet не разобран") }
        await c.add(magnet: magnet, savePath: root.appendingPathComponent("C-data"))
        ok = check(await waitFor(c, meta.infoHash.hex, timeout: 60, { $0.status == .seeding }) != nil,
                   "magnet: метаданные (ut_metadata) и данные получены") && ok
        ok = check(same(payload, root.appendingPathComponent("C-data/Payload")), "magnet: файлы совпадают") && ok

        // 5. BitTorrent v2 и гибрид по .torrent.
        let v = await engine("V")
        _ = await download(v, metaV2, folder: "V-data", label: "BitTorrent v2 (деревья Меркла SHA-256)")
        let h = await engine("H")
        _ = await download(h, metaHybrid, folder: "H-data", label: "гибрид v1 + v2")

        // 6. v2 magnet (urn:btmh): метаданные, затем слои хешей запросами hash request.
        let w = await engine("W")
        let v2link = MagnetLink.make(hash: metaV2.infoHash, v2: metaV2.infoHashV2, v2Only: true, name: nil, trackers: []) + "&x.pe=127.0.0.1:\(seedPort)"
        if let m2 = MagnetLink(v2link) {
            await w.add(magnet: m2, savePath: root.appendingPathComponent("W-data"))
            ok = check(await waitFor(w, metaV2.infoHash.hex, timeout: 60, { $0.status == .seeding }) != nil,
                       "v2 magnet: метаданные и слои кусков (hash request) получены, данные проверены") && ok
            ok = check(same(payload, root.appendingPathComponent("W-data/Payload")), "v2 magnet: файлы совпадают") && ok
        } else {
            ok = check(false, "v2 magnet не разобран")
        }

        // 7. Чёрный список: петлевые адреса запрещены — соединиться нельзя.
        let listFile = root.appendingPathComponent("block.p2p")
        try? Data("loopback:127.0.0.0-127.255.255.255\n".utf8).write(to: listFile)
        var blocked = plain
        blocked.blocklistEnabled = true
        blocked.blocklistSource = listFile.path
        let x = await engine("X", blocked)
        try? await Task.sleep(for: .milliseconds(500))
        await x.add(meta: meta, savePath: root.appendingPathComponent("X-data"))
        await x.addPeers(meta.infoHash.hex, [seedAddress])
        try? await Task.sleep(for: .seconds(3))
        let xs = await x.snapshots(detailed: meta.infoHash.hex).first
        let xn = await x.networkStatus()
        ok = check(xs?.peersConnected == 0 && xn.blocklistRanges == 1 && xn.blocklistBlocked > 0,
                   "чёрный список: соединений \(xs?.peersConnected ?? -1), отказов \(xn.blocklistBlocked)") && ok

        // 8. DHT: узел-«роутер» N, сидер анонсирует раздачу, новый участник находит его без трекеров.
        var dhtSettings = plain
        dhtSettings.dhtEnabled = true
        // Узел-«роутер» не ходит в публичную DHT: иначе анонсы уйдут к настоящим узлам, ближе к хешу.
        var isolated = EngineOptions()
        isolated.dhtBootstrap = []
        let n = await engine("N", dhtSettings, isolated)
        var dhtOptions = EngineOptions()
        dhtOptions.dhtBootstrap = [PeerAddress(host: "127.0.0.1", port: await n.port)]
        let s2 = await engine("S2", dhtSettings, dhtOptions)
        await s2.add(meta: meta, savePath: seedFolder)
        _ = await waitFor(s2, meta.infoHash.hex, timeout: 30, { $0.status == .seeding })
        // Сидер анонсирует себя при первом поиске; ждём, пока анонс дойдёт до узла.
        var stored = 0
        for _ in 0..<60 {
            stored = await n.networkStatus().dhtStoredPeers
            if stored > 0 { break }
            try? await Task.sleep(for: .milliseconds(500))
        }
        ok = check(stored > 0, "DHT: анонс сидера (announce_peer с токеном) сохранён узлом") && ok
        let l2 = await engine("L2", dhtSettings, dhtOptions)
        if let dm = MagnetLink("magnet:?xt=urn:btih:\(meta.infoHash.hex)") {
            await l2.add(magnet: dm, savePath: root.appendingPathComponent("L2-data"))
            ok = check(await waitFor(l2, meta.infoHash.hex, timeout: 90, { $0.status == .seeding }) != nil,
                       "DHT: пир найден через get_peers, magnet скачан без трекеров") && ok
        }
        let nn = await n.networkStatus()
        ok = check(nn.dhtQueries > 0, "DHT-узел ответил на \(nn.dhtQueries) запросов, знает \(nn.dhtNodes) узлов") && ok

        // 9. LSD: два движка находят друг друга мультикастом.
        var lsdSettings = plain
        lsdSettings.lsdEnabled = true
        var lsdOptions = EngineOptions()
        lsdOptions.lsdLoopback = true
        lsdOptions.lsdInterval = 2
        let s3 = await engine("S3", lsdSettings, lsdOptions)
        let lsdActive = await s3.networkStatus().lsdActive
        if lsdActive {
            await s3.add(meta: meta, savePath: seedFolder)
            _ = await waitFor(s3, meta.infoHash.hex, timeout: 30, { $0.status == .seeding })
            let l3 = await engine("L3", lsdSettings, lsdOptions)
            await l3.add(meta: meta, savePath: root.appendingPathComponent("L3-data"))
            let viaLSD = await waitFor(l3, meta.infoHash.hex, timeout: 60, { $0.status == .seeding })
            ok = check(viaLSD != nil, "LSD: пир из локальной сети найден мультикастом, раздача скачана") && ok
        } else {
            print("  LSD: мультикаст на этой машине недоступен — пропуск")
        }

        // 10. Веб-сид: только HTTP-сервер, без пиров.
        if let webSeed {
            do {
                let (url, stop) = try await webSeed(seedFolder)
                defer { stop() }
                options.version = .v1
                options.webSeeds = [url]
                if let ws = try? TorrentCreator.create(from: payload, options: options) {
                    let wsEngine = await engine("WS")
                    _ = await download(wsEngine, ws, folder: "WS-data", label: "веб-сид (HTTP Range)", peers: [])
                }
            } catch {
                ok = check(false, "веб-сид: сервер не запустился (\(error.localizedDescription))") && ok
            }
        }

        // 11. Отдача: сидер видел исходящий трафик.
        let up = await seeder.snapshots(detailed: nil).reduce(Int64(0)) { $0 + $1.uploadedTotal }
        ok = check(up >= meta.totalLength * 5, "сидер отдал \(up / 1_000_000) МБ") && ok
        return ok
    }

    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
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
                        _ condition: @Sendable (TorrentSnapshot) -> Bool) async -> TorrentSnapshot? {
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
}
