import Foundation

/// Проверки протоколов без сети: эталонные векторы и модели (часть `--selftest-core`).
nonisolated enum ProtocolSelfTest {
    static func run() async -> Bool {
        var ok = true
        ok = crypto() && ok
        ok = await mse() && ok
        ok = utp() && ok
        ok = await dht() && ok
        ok = blocklist() && ok
        ok = fast() && ok
        ok = v2() && ok
        ok = lsd() && ok
        return ok
    }

    private static func check(_ c: Bool, _ m: String) -> Bool { LoopbackSelfTest.check(c, m) }

    // MARK: - Шифрование

    static func crypto() -> Bool {
        var ok = check(RC4(key: Data("Key".utf8), discard: 0).apply(Data("Plaintext".utf8)).base16 == "bbf316e8d940af0ad3", "RC4: эталонный вектор")
        // Ключи 01…14 и 15…28, эталон посчитан pow() в Python.
        let a = Data((1...20).map { UInt8($0) }), b = Data((21...40).map { UInt8($0) })
        let ya = DiffieHellman.publicKey(a)
        ok = check(ya.base16.hasPrefix("96e112dab29e8c5272accb9b17b26887") && ya.base16.hasSuffix("821eb693"), "Диффи — Хеллман 768 бит: открытый ключ совпадает с эталоном") && ok
        let s1 = DiffieHellman.sharedSecret(DiffieHellman.publicKey(b), a), s2 = DiffieHellman.sharedSecret(ya, b)
        ok = check(s1 == s2 && s1?.base16.hasPrefix("994aac6c359990cf") == true, "Диффи — Хеллман: общий секрет совпадает с эталоном") && ok
        return ok
    }

    /// Два соединения, связанные в памяти; данные режутся на случайные куски.
    final class MemoryTransport: PeerTransport, @unchecked Sendable {
        let kind = TransportKind.tcp
        weak var peer: MemoryTransport?
        private var onData: (@Sendable (Data) -> Void)?
        let queue = DispatchQueue(label: "selftest.mem")
        var sent = Data()

        func start(onReady: @escaping @Sendable ((any Error)?) -> Void, onData: @escaping @Sendable (Data) -> Void,
                   onClosed: @escaping @Sendable ((any Error)?) -> Void) {
            queue.async {
                self.onData = onData
                onReady(nil)
            }
        }

        func send(_ data: Data) {
            queue.async { self.sent.append(data) }
            var rest = data
            while !rest.isEmpty {
                let n = min(rest.count, Int.random(in: 1...700))
                let chunk = Data(rest.prefix(n))
                rest = rest.dropFirst(n)
                peer?.queue.async { self.peer?.onData?(chunk) }
            }
        }

        func close() {}

        var wire: Data { queue.sync { sent } }
    }

    static func mse() async -> Bool {
        var ok = true
        for (requireRC4, label) in [(false, "предпочитать"), (true, "только шифрованные")] {
            let ta = MemoryTransport(), tb = MemoryTransport()
            ta.peer = tb
            tb.peer = ta
            let a = PeerConnection(address: PeerAddress(host: "a", port: 1), transport: ta, incoming: false)
            let b = PeerConnection(address: PeerAddress(host: "b", port: 2), transport: tb, incoming: true)
            try? await a.open()
            try? await b.open()
            let hash = Data((0..<20).map { UInt8($0 * 3) })
            async let responded = try? MSE.respond(b, hashes: [Data(repeating: 9, count: 20), hash], requireRC4: requireRC4)
            let initiated = (try? await MSE.initiate(a, infoHash: hash, allowPlaintext: !requireRC4)) != nil
            let skey = await responded
            a.sendRaw(Handshake.make(infoHash: hash, peerID: Data(repeating: 1, count: 20)))
            let hs = (try? await b.read(68)).flatMap(Handshake.init)
            for i in 0..<20 { b.send(.have(i)) }
            var got = 0
            var it = a.messages().makeAsyncIterator()
            for _ in 0..<20 { if case .have(let i)? = try? await it.next(), i == got { got += 1 } }
            let hidden = ta.wire.range(of: Data("BitTorrent protocol".utf8)) == nil
            ok = check(initiated && skey == hash && hs?.infoHash == hash && got == 20 && a.isEncrypted && hidden,
                       "MSE (\(label)): ключи согласованы, обмен идёт, «BitTorrent protocol» в канале не виден") && ok
            a.close()
            b.close()
        }
        return ok
    }

    // MARK: - uTP на модели сети

    static func utp() -> Bool {
        var ok = true
        for (loss, delay, jitter, size) in [(0.0, 20.0, 0.0, 3_000_000), (0.05, 30.0, 15.0, 1_000_000), (0.15, 40.0, 30.0, 200_000)] {
            let r = simulate(loss: loss, delayMs: delay, jitterMs: jitter, size: size, back: size / 6)
            ok = check(r.ok, "uTP: потери \(Int(loss * 100))%, задержка \(Int(delay)) ± \(Int(jitter)) мс — \(size / 1000) КБ доставлены побайтно за \(String(format: "%.1f", r.seconds)) с модельного времени") && ok
        }
        return ok
    }

    private static func simulate(loss: Double, delayMs: Double, jitterMs: Double, size: Int, back: Int) -> (ok: Bool, seconds: Double) {
        struct Event { let at: UInt64; let bytes: [UInt8]; let toA: Bool }
        var events: [Event] = []
        var now: UInt64 = 1_000_000
        func send(_ b: [UInt8], toA: Bool) {
            if Double.random(in: 0..<1) < loss { return }
            events.append(Event(at: now + UInt64(delayMs * 1000) + UInt64(Double.random(in: 0...max(0, jitterMs)) * 1000), bytes: b, toA: toA))
        }
        let addrA = UDPAddress(host: "10.0.0.1", port: 1)!, addrB = UDPAddress(host: "10.0.0.2", port: 2)!
        let a = UTPSocket(connectTo: addrB, id: 777, now: now)
        var b: UTPSocket?
        a.emit = { send($0, toA: false) }
        var gotA = Data(), gotB = Data()
        var connected = false
        a.onConnected = { connected = $0 == nil }
        a.onData = { gotA.append($0) }
        let ab = Data((0..<size).map { UInt8(truncatingIfNeeded: $0 &* 31 &+ 7) })
        let ba = Data((0..<back).map { UInt8(truncatingIfNeeded: $0 &* 17 &+ 3) })
        a.connect(now: now)
        var wroteA = false, wroteB = false
        var lastTick: UInt64 = 0
        let limit = now + 600_000_000
        while now < limit, gotB.count < size || gotA.count < back {
            events.sort { $0.at < $1.at }
            let nextTick = lastTick + 10_000
            now = min(events.first?.at ?? UInt64.max, nextTick)
            var touchedA = false, touchedB = false
            while let e = events.first, e.at <= now {
                events.removeFirst()
                guard let (h, off) = UTPHeader.parse(e.bytes) else { continue }
                if e.toA {
                    a.receive(h, payload: e.bytes[off...], now: now)
                    touchedA = true
                } else {
                    if b == nil, h.type == .syn {
                        let s = UTPSocket(accepting: h, from: addrA, now: now)
                        s.emit = { send($0, toA: true) }
                        s.onData = { gotB.append($0) }
                        b = s
                    }
                    b?.receive(h, payload: e.bytes[off...], now: now)
                    touchedB = true
                }
            }
            if connected, !wroteA { a.write(ab); wroteA = true; touchedA = true }
            if let b, !wroteB { b.write(ba); wroteB = true; touchedB = true }
            if touchedA { a.flush(now: now) }
            if touchedB { b?.flush(now: now) }
            if now >= nextTick {
                lastTick = now
                a.tick(now: now)
                b?.tick(now: now)
            }
        }
        return (gotB == ab && gotA == ba, Double(now - 1_000_000) / 1e6)
    }

    // MARK: - DHT в памяти

    final class Registry: @unchecked Sendable {
        let lock = NSLock()
        var nodes: [UDPAddress: DHTNode] = [:]
        func node(_ a: UDPAddress) -> DHTNode? { lock.withLock { nodes[a] } }
    }

    final class Collector: @unchecked Sendable {
        let lock = NSLock()
        var peers: [PeerAddress] = []
        func add(_ p: [PeerAddress]) { lock.withLock { peers += p } }
        var all: [PeerAddress] { lock.withLock { peers } }
    }

    static func dht() async -> Bool {
        let registry = Registry()
        var list: [(UDPAddress, DHTNode)] = []
        let boot = PeerAddress(host: "10.1.0.1", port: 6881)
        for i in 0..<60 {
            let addr = UDPAddress(host: "10.1.0.\(i + 1)", port: 6881)!
            let node = DHTNode(bootstrap: i == 0 ? [] : [boot]) { bytes, to in
                Task { await registry.node(to)?.receive(bytes, from: addr) }
            }
            list.append((addr, node))
        }
        registry.lock.withLock { for (a, n) in list { registry.nodes[a] = n } }
        for (_, n) in list.dropFirst() { await n.bootstrap() }
        var smallest = Int.max
        for (_, n) in list { smallest = min(smallest, await n.knownNodes) }
        var ok = check(smallest >= 8, "DHT: 60 узлов заполнили таблицы маршрутизации (минимум \(smallest) узлов)")
        let hash = InfoHash(Data((0..<20).map { UInt8($0 &* 7) }))!
        await list[5].1.findPeers(infoHash: hash, announcePort: 7777, timeBudget: 10) { _ in }
        let found = Collector()
        await list[40].1.findPeers(infoHash: hash, announcePort: nil, timeBudget: 10) { found.add($0) }
        ok = check(found.all.contains(PeerAddress(host: "10.1.0.6", port: 7777)), "DHT: announce_peer с токеном, другой узел нашёл пира через get_peers") && ok
        var stored = 0
        for (_, n) in list { stored += await n.stats.peersStored }
        ok = check(stored >= 3, "DHT: анонс хранят \(stored) ближайших узлов") && ok
        return ok
    }

    // MARK: - Прочее

    static func blocklist() -> Bool {
        let text = """
        Some org:1.2.3.0-1.2.3.255
        Weird: name: with colons:10.0.0.5-10.0.0.9
        001.002.004.000 - 001.002.004.255 , 000 , eMule
        5.5.5.0 - 5.5.5.255 , 200 , разрешено
        192.168.7.0/24
        8.8.8.8
        """
        let list = Blocklist()
        list.replace(Blocklist.parse(text))
        list.setEnabled(true)
        return check(list.blocks("1.2.3.4") && list.blocks("1.2.4.77") && list.blocks("10.0.0.7") && !list.blocks("10.0.0.10")
                     && list.blocks("192.168.7.255") && list.blocks("::ffff:8.8.8.8") && !list.blocks("5.5.5.5") && !list.blocks("9.9.9.9"),
                     "чёрный список: форматы PeerGuardian, eMule, CIDR, уровень доступа")
    }

    static func fast() -> Bool {
        let hash = Data(repeating: 0xAA, count: 20)
        let seven = FastExtension.allowedSet(ip: [80, 4, 4, 200], infoHash: hash, pieceCount: 1313, k: 7)
        let nine = FastExtension.allowedSet(ip: [80, 4, 4, 200], infoHash: hash, pieceCount: 1313, k: 9)
        return check(seven == [1059, 431, 808, 1217, 287, 376, 1188] && nine == seven + [353, 508], "Fast extension: набор allowed fast совпадает с примером BEP 6")
    }

    static func v2() -> Bool {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("swarm-v2-\(UUID().uuidString)")
        defer { try? fm.removeItem(at: dir) }
        let folder = dir.appendingPathComponent("Folder")
        try? fm.createDirectory(at: folder.appendingPathComponent("sub"), withIntermediateDirectories: true)
        func write(_ path: String, _ n: Int, _ a: Int, _ b: Int) {
            try? Data((0..<n).map { UInt8(truncatingIfNeeded: $0 &* a &+ b) }).write(to: folder.appendingPathComponent(path))
        }
        write("a.bin", 300_000, 7, 3)
        write("b.bin", 16384, 13, 1)
        write("sub/c.bin", 1000, 17, 5)
        write("sub/d.bin", 196_608, 19, 11)
        try? Data().write(to: folder.appendingPathComponent("empty.txt"))
        var ok = true
        // Эталоны — из генератора, приложенного к BEP 52 (bep_0052_torrent_creator.py).
        for (piece, expected) in [(65536, "99061f74a4579a7fd636701911f818ca3ac8c6a31e39757a512e905a178b6636"),
                                  (16384, "9c36c22e300f1920578da13ce1a79d5406a8700fb277f419e2d1bc6a9eb05b61")] {
            var o = TorrentCreator.Options()
            o.pieceLength = piece
            o.version = .v2
            let m = try? TorrentCreator.create(from: folder, options: o)
            ok = check(m?.infoHashV2?.base16 == expected, "BitTorrent v2: info-хеш (кусок \(piece / 1024) КБ) совпадает с эталонным генератором BEP 52") && ok
            if let m, let again = try? Metainfo(torrentData: m.torrentFileData()) {
                ok = check(again.infoHash == m.infoHash && again.pieceLayers == m.pieceLayers && again.version == .v2,
                           "BitTorrent v2: .torrent со слоями кусков читается обратно") && ok
            }
        }
        var o = TorrentCreator.Options()
        o.pieceLength = 65536
        o.version = .hybrid
        if let h = try? TorrentCreator.create(from: folder, options: o) {
            ok = check(h.version == .hybrid && h.swarmHashes.count == 2 && h.files.contains(where: \.isPadding), "гибрид: два роя и файлы-заполнители BEP 47") && ok
        }
        // Доказательство для запроса хешей.
        let layer = (0..<5).map { Hashing.sha256(Data([UInt8($0)])) }
        let pad = Merkle.padHash(levels: 2)
        let root = Merkle.root(layer, width: 8, pad: pad)
        let tree = Merkle.layers(layer, pad: pad)
        if let proof = Merkle.proof(layers: tree, index: 2, length: 2, proofLayers: tree.count - 2) {
            let hashes = stride(from: 0, to: proof.count, by: 32).map { proof.subdata(in: $0..<($0 + 32)) }
            ok = check(Merkle.verify(hashes: Array(hashes.prefix(2)), uncles: Array(hashes.dropFirst(2)), index: 2, root: root),
                       "запрос хешей v2: доказательство сходится к корню файла") && ok
        } else {
            ok = check(false, "запрос хешей v2: доказательство не построено") && ok
        }
        let m = MagnetLink("magnet:?xt=urn:btmh:1220\(String(repeating: "ab", count: 32))&dn=x")
        ok = check(m?.infoHashV2?.count == 32 && m?.infoHash.hex == String(repeating: "ab", count: 20), "magnet: urn:btmh (v2)") && ok
        return ok
    }

    static func lsd() -> Bool {
        let msg = LocalDiscovery.message(hashes: [String(repeating: "a", count: 40), String(repeating: "b", count: 40)], port: 51413, cookie: "c00k1e")
        let parsed = LocalDiscovery.parse(msg)
        return check(parsed?.port == 51413 && parsed?.hashes.count == 2 && parsed?.cookie == "c00k1e", "LSD: анонс BEP 14 с несколькими Infohash")
    }
}
