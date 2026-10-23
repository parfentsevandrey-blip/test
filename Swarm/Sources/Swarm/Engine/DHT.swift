import Foundation

/// Полноценный узел DHT Mainline (BEP 5): таблица маршрутизации из k-корзин, ответы на ping,
/// find_node, get_peers и announce_peer (с токенами), хранение анонсов других участников,
/// итеративный поиск и анонс своих раздач. Работает на общем UDP-порту вместе с uTP.
actor DHTNode {
    nonisolated struct Contact: Sendable {
        var id: Data
        var address: UDPAddress
        var lastSeen: Date
        var failures = 0
    }

    nonisolated struct Stats: Sendable {
        var nodes = 0
        var queriesAnswered = 0
        var peersStored = 0
        var torrentsStored = 0
        var announcesReceived = 0
    }

    nonisolated static let defaultBootstrap: [PeerAddress] = [
        PeerAddress(host: "router.bittorrent.com", port: 6881),
        PeerAddress(host: "dht.transmissionbt.com", port: 6881),
        PeerAddress(host: "router.utorrent.com", port: 6881),
        PeerAddress(host: "dht.libtorrent.org", port: 25401),
    ]

    nonisolated let id: Data
    private let send: @Sendable ([UInt8], UDPAddress) -> Void
    private let bootstrapList: [PeerAddress]
    private var buckets = [[Contact]](repeating: [], count: 160)
    private var replacements = [[Contact]](repeating: [], count: 160)
    private var pending: [Data: (cont: CheckedContinuation<BValue?, Never>, address: UDPAddress)] = [:]
    private var storage: [Data: [PeerAddress: Date]] = [:]
    private var secret = DHTNode.randomBytes(16)
    private var previousSecret = DHTNode.randomBytes(16)
    private var savedNodes: [UDPAddress]
    private var queriesAnswered = 0
    private var announcesReceived = 0
    private var maintenanceTask: Task<Void, Never>?

    init(id: Data? = nil, saved: [UDPAddress] = [], bootstrap: [PeerAddress] = DHTNode.defaultBootstrap,
         send: @escaping @Sendable ([UInt8], UDPAddress) -> Void) {
        self.id = id?.count == 20 ? id! : DHTNode.randomBytes(20)
        self.send = send
        self.savedNodes = saved
        self.bootstrapList = bootstrap
    }

    nonisolated static func randomBytes(_ n: Int) -> Data { Data((0..<n).map { _ in UInt8.random(in: 0...255) }) }

    var knownNodes: Int { buckets.reduce(0) { $0 + $1.count } }

    var stats: Stats {
        Stats(nodes: knownNodes, queriesAnswered: queriesAnswered,
              peersStored: storage.values.reduce(0) { $0 + $1.count }, torrentsStored: storage.count,
              announcesReceived: announcesReceived)
    }

    /// Узлы для сохранения между запусками.
    var nodesToSave: [UDPAddress] {
        buckets.flatMap { $0 }.filter { $0.failures == 0 }.sorted { $0.lastSeen > $1.lastSeen }.prefix(300).map(\.address)
    }

    func start() {
        guard maintenanceTask == nil else { return }
        maintenanceTask = Task { [weak self] in
            await self?.bootstrap()
            var round = 0
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                round += 1
                await self?.maintenance(round: round)
            }
        }
    }

    func stop() {
        maintenanceTask?.cancel()
        maintenanceTask = nil
        for (_, p) in pending { p.cont.resume(returning: nil) }
        pending.removeAll()
    }

    // MARK: - Входящие пакеты

    func receive(_ bytes: [UInt8], from: UDPAddress) {
        guard let msg = try? Bencode.decode(Data(bytes)), let y = msg["y"]?.string, let tid = msg["t"]?.data else { return }
        switch y {
        case "r", "e":
            guard let p = pending[tid], p.address == from else { return }
            pending[tid] = nil
            if y == "r", let r = msg["r"], let rid = r["id"]?.data {
                insert(rid, from, responded: true)
                p.cont.resume(returning: r)
            } else {
                p.cont.resume(returning: nil)
            }
        case "q":
            handleQuery(msg, tid: tid, from: from)
        default:
            break
        }
    }

    private func reply(_ tid: Data, _ r: [String: BValue], to: UDPAddress) {
        send([UInt8](BValue.dict(["t": .bytes(tid), "y": .str("r"), "r": .dict(r)]).encoded), to)
    }

    private func error(_ tid: Data, _ code: Int64, _ text: String, to: UDPAddress) {
        send([UInt8](BValue.dict(["t": .bytes(tid), "y": .str("e"), "e": .list([.int(code), .str(text)])]).encoded), to)
    }

    private func handleQuery(_ msg: BValue, tid: Data, from: UDPAddress) {
        guard let q = msg["q"]?.string, let a = msg["a"], let qid = a["id"]?.data, qid.count == 20, from.isV4 else { return }
        queriesAnswered += 1
        if a["ro"]?.int != 1 { insert(qid, from, responded: false) }
        var r: [String: BValue] = ["id": .bytes(id)]
        switch q {
        case "ping":
            break
        case "find_node":
            guard let target = a["target"]?.data, target.count == 20 else { return error(tid, 203, "Нет target", to: from) }
            r["nodes"] = .bytes(compact(closest(target, 8)))
        case "get_peers":
            guard let hash = a["info_hash"]?.data, hash.count == 20 else { return error(tid, 203, "Нет info_hash", to: from) }
            r["token"] = .bytes(token(for: from, secret))
            if let peers = storage[hash], !peers.isEmpty {
                r["values"] = .list(peers.keys.shuffled().prefix(50).compactMap { UDPAddress($0)?.compact.map(BValue.bytes) })
            }
            r["nodes"] = .bytes(compact(closest(hash, 8)))
        case "announce_peer":
            guard let hash = a["info_hash"]?.data, hash.count == 20, let tok = a["token"]?.data else {
                return error(tid, 203, "Неверный анонс", to: from)
            }
            guard tok == token(for: from, secret) || tok == token(for: from, previousSecret) else {
                return error(tid, 203, "Неверный токен", to: from)
            }
            let port = a["implied_port"]?.int == 1 ? from.port : (a["port"]?.int).flatMap { UInt16(exactly: $0) } ?? 0
            guard port > 0 else { return error(tid, 203, "Неверный порт", to: from) }
            if storage[hash] == nil, storage.count >= 5000 { return reply(tid, r, to: from) }
            var peers = storage[hash] ?? [:]
            if peers.count < 500 || peers[PeerAddress(host: from.host, port: port)] != nil {
                peers[PeerAddress(host: from.host, port: port)] = Date()
            }
            storage[hash] = peers
            announcesReceived += 1
        default:
            return error(tid, 204, "Неизвестный метод", to: from)
        }
        reply(tid, r, to: from)
    }

    private func token(for address: UDPAddress, _ secret: Data) -> Data {
        Hashing.sha1(Data(address.ip), secret).prefix(8)
    }

    // MARK: - Таблица маршрутизации

    private func bucketIndex(_ nid: Data) -> Int {
        var n = 0
        for (a, b) in zip(nid, id) {
            let x = a ^ b
            if x == 0 { n += 8; continue }
            n += x.leadingZeroBitCount
            break
        }
        return min(n, 159)
    }

    private func insert(_ nid: Data, _ address: UDPAddress, responded: Bool) {
        guard nid.count == 20, nid != id, address.isV4, address.port > 0 else { return }
        let b = bucketIndex(nid)
        if let i = buckets[b].firstIndex(where: { $0.id == nid }) {
            if buckets[b][i].address != address {
                // Не даём «перехватить» живой узел с другого адреса.
                guard buckets[b][i].failures > 0 || Date().timeIntervalSince(buckets[b][i].lastSeen) > 900 else { return }
                buckets[b][i].address = address
            }
            buckets[b][i].lastSeen = Date()
            if responded { buckets[b][i].failures = 0 }
            return
        }
        // Один адрес — один узел.
        if buckets[b].contains(where: { $0.address == address }) { return }
        let contact = Contact(id: nid, address: address, lastSeen: Date())
        if buckets[b].count < 8 {
            buckets[b].append(contact)
            return
        }
        if let bad = buckets[b].firstIndex(where: { $0.failures >= 2 }) {
            buckets[b][bad] = contact
            return
        }
        replacements[b].removeAll { $0.id == nid }
        replacements[b].append(contact)
        if replacements[b].count > 8 { replacements[b].removeFirst() }
        if let stale = buckets[b].min(by: { $0.lastSeen < $1.lastSeen }), Date().timeIntervalSince(stale.lastSeen) > 900 {
            Task { await self.ping(stale.address) }
        }
    }

    private func failed(_ address: UDPAddress) {
        for b in buckets.indices {
            guard let i = buckets[b].firstIndex(where: { $0.address == address }) else { continue }
            buckets[b][i].failures += 1
            if buckets[b][i].failures >= 2, let replacement = replacements[b].popLast() {
                buckets[b][i] = replacement
            }
            return
        }
    }

    func closest(_ target: Data, _ k: Int) -> [Contact] {
        buckets.flatMap { $0 }
            .filter { $0.failures < 2 }
            .sorted { DHTNode.distance($0.id, target) < DHTNode.distance($1.id, target) }
            .prefix(k).map { $0 }
    }

    private func compact(_ contacts: [Contact]) -> Data {
        var out = Data()
        for c in contacts {
            guard let addr = c.address.compact else { continue }
            out.append(c.id)
            out.append(addr)
        }
        return out
    }

    /// Узел, о котором сообщил пир (сообщение PORT): проверяем, жив ли он.
    func addNode(_ address: UDPAddress) {
        Task { await self.ping(address) }
    }

    // MARK: - Запросы

    private func query(_ method: String, _ args: [String: BValue], to address: UDPAddress, timeout: TimeInterval = 3) async -> BValue? {
        var tid = DHTNode.randomBytes(2)
        while pending[tid] != nil { tid = DHTNode.randomBytes(2) }
        var a = args
        a["id"] = .bytes(id)
        let message = [UInt8](BValue.dict(["t": .bytes(tid), "y": .str("q"), "q": .str(method), "a": .dict(a)]).encoded)
        let timer = Task {
            try? await Task.sleep(for: .seconds(timeout))
            await self.expire(tid)
        }
        let result: BValue? = await withCheckedContinuation { cont in
            pending[tid] = (cont, address)
            send(message, address)
        }
        timer.cancel()
        if result == nil { failed(address) }
        return result
    }

    private func expire(_ tid: Data) {
        pending.removeValue(forKey: tid)?.cont.resume(returning: nil)
    }

    @discardableResult
    func ping(_ address: UDPAddress) async -> Bool {
        await query("ping", [:], to: address) != nil
    }

    private struct Found {
        var id: Data?
        var address: UDPAddress
        var token: Data?
    }

    /// Итеративный поиск по XOR-метрике: find_node или get_peers.
    private func lookup(target: Data, getPeers: Bool, timeBudget: TimeInterval,
                        onPeers: (@Sendable ([PeerAddress]) async -> Void)?) async -> [Found] {
        let deadline = Date().addingTimeInterval(timeBudget)
        var candidates: [UDPAddress: Data?] = [:]
        for c in closest(target, 16) { candidates[c.address] = c.id }
        if candidates.count < 8 {
            for a in savedNodes.prefix(32) where candidates[a] == nil { candidates[a] = Data?.none }
            for a in await DHTNode.resolve(bootstrapList) where candidates[a] == nil { candidates[a] = Data?.none }
        }
        var queried = Set<UDPAddress>()
        var responded: [Found] = []
        var foundPeers = Set<PeerAddress>()
        var stagnant = 0

        while Date() < deadline, !Task.isCancelled {
            let batch = candidates
                .filter { !queried.contains($0.key) }
                .sorted { DHTNode.distance($0.value, target) < DHTNode.distance($1.value, target) }
                .prefix(8)
            if batch.isEmpty { break }
            batch.forEach { queried.insert($0.key) }
            let bestBefore = responded.prefix(8).map(\.address)
            let method = getPeers ? "get_peers" : "find_node"
            let args: [String: BValue] = getPeers ? ["info_hash": .bytes(target)] : ["target": .bytes(target)]
            let results = await withTaskGroup(of: (UDPAddress, BValue?).self) { group in
                for (address, _) in batch {
                    group.addTask { (address, await self.query(method, args, to: address)) }
                }
                var out: [(UDPAddress, BValue?)] = []
                for await r in group { out.append(r) }
                return out
            }
            for (address, r) in results {
                guard let r else { continue }
                responded.append(Found(id: r["id"]?.data, address: address, token: r["token"]?.data))
                if let nodes = r["nodes"]?.data {
                    let b = [UInt8](nodes)
                    var at = 0
                    while at + 26 <= b.count {
                        let nid = Data(b[at..<(at + 20)])
                        let port = UInt16(b[at + 24]) << 8 | UInt16(b[at + 25])
                        let addr = UDPAddress(ip: Array(b[(at + 20)..<(at + 24)]), port: port)
                        if port > 0, nid != id, b[at + 20] != 0, candidates[addr] == nil { candidates[addr] = nid }
                        at += 26
                    }
                }
                if getPeers, let values = r["values"]?.list {
                    let peers = values.flatMap { $0.data.map { PeerAddress.compact($0) } ?? [] }
                    let fresh = peers.filter { foundPeers.insert($0).inserted }
                    if !fresh.isEmpty, let onPeers { await onPeers(fresh) }
                }
            }
            responded.sort { DHTNode.distance($0.id, target) < DHTNode.distance($1.id, target) }
            stagnant = responded.prefix(8).map(\.address) == bestBefore ? stagnant + 1 : 0
            if stagnant >= 2 && (!getPeers || foundPeers.count >= 20 || stagnant >= 3) { break }
        }
        return responded
    }

    /// Ищет пиров раздачи и (если задан порт) сообщает ближайшим узлам, что мы тоже в рое.
    func findPeers(infoHash: InfoHash, announcePort: UInt16?, timeBudget: TimeInterval = 40,
                   onPeers: @escaping @Sendable ([PeerAddress]) async -> Void) async {
        let found = await lookup(target: infoHash.bytes, getPeers: true, timeBudget: timeBudget, onPeers: onPeers)
        guard let port = announcePort, port > 0 else { return }
        let targets = found.filter { $0.token != nil }.prefix(8)
        await withTaskGroup(of: Void.self) { group in
            for f in targets {
                guard let token = f.token else { continue }
                group.addTask {
                    _ = await self.query("announce_peer", ["info_hash": .bytes(infoHash.bytes), "port": .int(Int64(port)),
                                                           "token": .bytes(token), "implied_port": .int(0)], to: f.address)
                }
            }
        }
    }

    func bootstrap() async {
        _ = await lookup(target: id, getPeers: false, timeBudget: 20, onPeers: nil)
    }

    private func maintenance(round: Int) async {
        if round % 5 == 0 {
            previousSecret = secret
            secret = DHTNode.randomBytes(16)
        }
        let cutoff = Date().addingTimeInterval(-1800)
        for (hash, peers) in storage {
            let alive = peers.filter { $0.value > cutoff }
            storage[hash] = alive.isEmpty ? nil : alive
        }
        if knownNodes < 30 {
            await bootstrap()
        } else if round % 5 == 0 {
            // Освежаем таблицу: поиск случайной точки и проверка давно молчащих узлов.
            _ = await lookup(target: DHTNode.randomBytes(20), getPeers: false, timeBudget: 15, onPeers: nil)
            let stale = buckets.flatMap { $0 }.filter { Date().timeIntervalSince($0.lastSeen) > 900 }.prefix(16)
            await withTaskGroup(of: Void.self) { group in
                for c in stale { group.addTask { await self.ping(c.address) } }
            }
        }
    }

    // MARK: - Вспомогательное

    /// XOR-расстояние как 20 байт для лексикографического сравнения. Неизвестный id — дальше всех.
    nonisolated static func distance(_ id: Data?, _ target: Data) -> [UInt8] {
        guard let id, id.count == 20 else { return [UInt8](repeating: 0xFF, count: 21) }
        return zip(id, target).map { $0 ^ $1 }
    }

    /// Разрешение имён (узлы начальной загрузки) в IPv4-адреса.
    nonisolated static func resolve(_ list: [PeerAddress]) async -> [UDPAddress] {
        await Task.detached {
            var out: [UDPAddress] = []
            for p in list {
                if let numeric = UDPAddress(p) { out.append(numeric); continue }
                var hints = addrinfo()
                hints.ai_family = AF_INET
                hints.ai_socktype = 0
                var result: UnsafeMutablePointer<addrinfo>?
                guard getaddrinfo(p.host, nil, &hints, &result) == 0 else { continue }
                var cursor = result
                while let ai = cursor {
                    if ai.pointee.ai_family == AF_INET, let sa = ai.pointee.ai_addr {
                        let ip = sa.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { sin -> [UInt8] in
                            withUnsafeBytes(of: sin.pointee.sin_addr) { Array($0) }
                        }
                        out.append(UDPAddress(ip: ip, port: p.port))
                        break
                    }
                    cursor = ai.pointee.ai_next
                }
                freeaddrinfo(result)
            }
            return out
        }.value
    }
}

nonisolated extension Array where Element == UInt8 {
    static func < (lhs: [UInt8], rhs: [UInt8]) -> Bool { lhs.lexicographicallyPrecedes(rhs) }
}
