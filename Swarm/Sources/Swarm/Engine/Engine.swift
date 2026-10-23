import Foundation
import os

nonisolated final class SettingsBox: Sendable {
    private let state: OSAllocatedUnfairLock<EngineSettings>
    init(_ s: EngineSettings) { state = OSAllocatedUnfairLock(initialState: s) }
    var value: EngineSettings {
        get { state.withLock { $0 } }
        set { state.withLock { $0 = newValue } }
    }
}

/// Значение, которое читают из любых потоков (внешний порт после проброса).
nonisolated final class PortBox: Sendable {
    private let state: OSAllocatedUnfairLock<UInt16>
    init(_ v: UInt16) { state = OSAllocatedUnfairLock(initialState: v) }
    var value: UInt16 {
        get { state.withLock { $0 } }
        set { state.withLock { $0 = newValue } }
    }
}

/// Движок: все раздачи, входящие соединения (TCP и uTP), DHT, LSD, проброс порта, сохранение состояния.
actor Engine {
    nonisolated let peerID = PeerIdentity.makePeerID()
    nonisolated let anonymousPeerID = PeerIdentity.makeAnonymousPeerID()
    nonisolated let limiter = RateLimiter()
    nonisolated let blocklist = Blocklist()
    nonisolated let settings: SettingsBox
    nonisolated let options: EngineOptions
    nonisolated let supportDirectory: URL
    nonisolated let finishedNames: AsyncStream<String>
    private nonisolated let finishedContinuation: AsyncStream<String>.Continuation
    private nonisolated let advertised = PortBox(0)

    private(set) var port: UInt16
    private(set) var dht: DHTNode?
    private var udp: UDPSocket?
    private var utp: UTPManager?
    private var lsd: LocalDiscovery?
    private var mapper: PortMapper?
    private var torrents: [InfoHash: Torrent] = [:]
    /// Все хеши роёв (у гибридов два) → раздача.
    private var bySwarmHash: [InfoHash: Torrent] = [:]
    private var order: [InfoHash] = []
    private var listener: PeerListener?
    private var dirty: Set<InfoHash> = []
    private var saveScheduled = false
    private var refillTask: Task<Void, Never>?
    private var serviceTask: Task<Void, Never>?
    private var blocklistMessage: String?
    private var loadedBlocklist: String?
    private var lastLSD = Date.distantPast

    init(supportDirectory: URL, port: UInt16, settings: EngineSettings, options: EngineOptions = EngineOptions()) {
        self.supportDirectory = supportDirectory
        self.port = port
        self.settings = SettingsBox(settings)
        self.options = options
        let stream = AsyncStream<String>.makeStream()
        finishedNames = stream.stream
        finishedContinuation = stream.continuation
        limiter.configure(down: settings.downloadLimit, up: settings.uploadLimit)
    }

    private var torrentsDirectory: URL { supportDirectory.appendingPathComponent("torrents", isDirectory: true) }
    private var dhtFile: URL { supportDirectory.appendingPathComponent("dht.json") }

    func start(restore: Bool = true) async {
        try? FileManager.default.createDirectory(at: torrentsDirectory, withIntermediateDirectories: true)
        await openPorts()
        let limiter = self.limiter
        refillTask = Task.detached {
            while !Task.isCancelled {
                limiter.refill(interval: 0.25)
                try? await Task.sleep(for: .milliseconds(250))
            }
        }
        await applyServices()
        serviceTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(self?.options.lsdLoopback == true ? 2 : 30))
                await self?.periodic()
            }
        }
        if restore { await restoreSaved() }
    }

    /// TCP-слушатель и UDP-сокет (DHT + uTP) на одном номере порта.
    private func openPorts() async {
        for candidate in [port] + (1...20).map({ _ in UInt16.random(in: 49152...65000) }) {
            do {
                let listener = try PeerListener(port: candidate) { [weak self] conn in
                    Task { await self?.accept(conn) }
                }
                try await listener.start()
                guard let socket = try? UDPSocket(port: candidate) else {
                    listener.cancel()
                    continue
                }
                self.listener = listener
                port = candidate
                advertised.value = candidate
                setUpUDP(socket)
                return
            } catch {
                continue
            }
        }
    }

    private func setUpUDP(_ socket: UDPSocket) {
        udp = socket
        let utp = UTPManager(queue: socket.queue) { [weak socket] bytes, to in socket?.send(bytes, to: to) }
        let box = settings
        let blocklist = self.blocklist
        utp.acceptFilter = { address in box.value.utpEnabled && !blocklist.blocks(address.host) }
        utp.onAccept = { [weak self] transport in
            let conn = PeerConnection(address: transport.remote.peerAddress, transport: transport, incoming: true)
            Task { await self?.accept(conn) }
        }
        utp.startTimer()
        self.utp = utp

        let saved = (try? Data(contentsOf: dhtFile)).flatMap { try? JSONDecoder().decode(SavedDHT.self, from: $0) }
        let node = DHTNode(id: saved.flatMap { InfoHash(hex: $0.id)?.bytes }, saved: (saved?.nodes ?? []).compactMap {
            PeerAddress(hostPort: $0).flatMap(UDPAddress.init)
        }, bootstrap: options.dhtBootstrap ?? DHTNode.defaultBootstrap) { [weak socket] bytes, to in socket?.send(bytes, to: to) }
        dht = node
        socket.onPacket = { [weak utp] bytes, from in
            if bytes.first == UInt8(ascii: "d") {
                guard box.value.dhtEnabled else { return }
                Task { await node.receive(bytes, from: from) }
            } else {
                utp?.receive(bytes, from: from)
            }
        }
        socket.onBatchEnd = { [weak utp] in utp?.endBatch() }
        socket.start()
    }

    private struct SavedDHT: Codable {
        var id: String
        var nodes: [String]
    }

    /// Включает и выключает службы по настройкам: DHT, LSD, проброс порта, чёрный список.
    private func applyServices() async {
        let s = settings.value
        if s.dhtEnabled { await dht?.start() } else { await dht?.stop() }

        let lsdWanted = s.lsdEnabled && !s.anonymous
        if lsdWanted, lsd == nil {
            if let discovery = try? LocalDiscovery(interface: options.lsdLoopback ? [127, 0, 0, 1] : nil) {
                discovery.onPeer = { [weak self] hex, address in Task { await self?.lsdPeer(hex, address) } }
                discovery.start()
                lsd = discovery
                lastLSD = .distantPast
            }
        } else if !lsdWanted, let discovery = lsd {
            discovery.stop()
            lsd = nil
        }

        if s.portMapping, mapper == nil {
            let m = PortMapper(port: port)
            mapper = m
            await m.start()
        } else if !s.portMapping, let m = mapper {
            mapper = nil
            await m.stop()
            advertised.value = port
        }

        blocklist.setEnabled(s.blocklistEnabled)
        let source = s.blocklistSource.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.blocklistEnabled, !source.isEmpty, source != loadedBlocklist {
            loadedBlocklist = source
            blocklistMessage = "Загрузка списка…"
            let list = blocklist
            Task {
                do {
                    let ranges = try await Blocklist.load(source)
                    list.replace(ranges)
                    await self.setBlocklistMessage(nil)
                } catch {
                    await self.setBlocklistMessage(error.localizedDescription)
                }
            }
        }
        if source.isEmpty { loadedBlocklist = nil; blocklist.replace([]); blocklistMessage = nil }
    }

    private func setBlocklistMessage(_ m: String?) { blocklistMessage = m }

    /// Скачать или перечитать чёрный список заново.
    func reloadBlocklist() async {
        loadedBlocklist = nil
        await applyServices()
    }

    /// Раз в полминуты: внешний порт после проброса, анонсы LSD, сохранение узлов DHT.
    private func periodic() async {
        if let mapper {
            let status = await mapper.status
            advertised.value = status.externalPort ?? port
        }
        if let lsd, Date().timeIntervalSince(lastLSD) >= options.lsdInterval {
            lastLSD = Date()
            var hashes: [String] = []
            for hash in order {
                guard let t = torrents[hash], await t.isActive, await !t.isPrivate else { continue }
                hashes.append(hash.hex)
            }
            if !hashes.isEmpty { lsd.announce(hashes, port: advertised.value) }
        }
        await saveDHT()
    }

    private func lsdPeer(_ hex: String, _ address: PeerAddress) async {
        guard let hash = InfoHash(hex: hex), let t = bySwarmHash[hash], await !t.isPrivate else { return }
        await t.addPeers([address], source: .lsd)
    }

    private func saveDHT() async {
        guard let dht else { return }
        let nodes = await dht.nodesToSave.map(\.description)
        guard !nodes.isEmpty else { return }
        let saved = SavedDHT(id: dht.id.base16, nodes: nodes)
        if let data = try? JSONEncoder().encode(saved) { try? data.write(to: dhtFile, options: .atomic) }
    }

    /// При выходе: убрать правила на роутере, сохранить узлы DHT.
    func shutdown() async {
        await saveDHT()
        if let mapper { await mapper.stop() }
        lsd?.stop()
    }

    // MARK: - Входящие

    private func accept(_ conn: PeerConnection) async {
        let s = settings.value
        guard !blocklist.blocks(conn.address.host) else { conn.close(); return }
        do {
            try await conn.open(timeout: 5)
            let first = try await conn.peek(20, timeout: 10)
            if first == Handshake.prefix {
                guard s.encryption != .require else { conn.close(); return }
            } else {
                // Не «BitTorrent protocol» — значит, шифрованное рукопожатие MSE.
                let hashes = Array(bySwarmHash.keys).map(\.bytes)
                _ = try await MSE.respond(conn, hashes: hashes, requireRC4: s.encryption == .require)
            }
            let raw = try await conn.read(68, timeout: 10)
            guard let hs = Handshake(raw), let hash = InfoHash(hs.infoHash), let torrent = bySwarmHash[hash],
                  await torrent.acceptIncoming(conn, handshake: hs) else {
                conn.close()
                return
            }
        } catch {
            conn.close()
        }
    }

    private func environment() -> TorrentEnvironment {
        let box = settings
        let continuation = finishedContinuation
        let normal = peerID, anonymous = anonymousPeerID
        let advertised = self.advertised
        return TorrentEnvironment(
            peerID: { box.value.anonymous ? anonymous : normal },
            ownIDs: [normal, anonymous],
            advertisedPort: { advertised.value },
            dht: dht, utp: utp, blocklist: blocklist, limiter: limiter, options: options,
            settings: { box.value },
            changed: { [weak self] hash in Task { await self?.markDirty(hash) } },
            gotMetadata: { [weak self] hash, meta in Task { await self?.saveMetainfo(meta, id: hash) } },
            finished: { name in continuation.yield(name) })
    }

    // MARK: - Добавление

    @discardableResult
    func add(meta: Metainfo, savePath: URL, priorities: [FilePriority]? = nil, sequential: Bool = false, paused: Bool = false) async -> InfoHash {
        if let existing = torrents[meta.infoHash] {
            await existing.start()
            return meta.infoHash
        }
        let resume = priorities.map {
            ResumeData(infoHash: meta.infoHash.hex, name: meta.name, magnet: nil, savePath: savePath.path, have: nil,
                       priorities: $0.map(\.rawValue), sequential: sequential, paused: paused,
                       uploaded: 0, downloaded: 0, addedAt: Date(), completedAt: nil)
        }
        let torrent = Torrent(meta: meta, magnet: nil, savePath: savePath, env: environment(), resume: resume)
        if sequential { await torrent.setSequential(true) }
        await insert(torrent)
        saveMetainfo(meta, id: meta.infoHash)
        if !paused { await torrent.start() }
        markDirty(meta.infoHash)
        lastLSD = .distantPast
        return meta.infoHash
    }

    @discardableResult
    func add(magnet: MagnetLink, savePath: URL) async -> InfoHash {
        if let existing = torrents[magnet.infoHash] {
            await existing.addPeers(magnet.peers, source: .manual)
            await existing.start()
            return magnet.infoHash
        }
        let torrent = Torrent(meta: nil, magnet: magnet, savePath: savePath, env: environment(), resume: nil)
        await insert(torrent)
        await torrent.start()
        markDirty(magnet.infoHash)
        lastLSD = .distantPast
        return magnet.infoHash
    }

    private func insert(_ t: Torrent) async {
        torrents[t.infoHash] = t
        order.insert(t.infoHash, at: 0)
        for h in await t.swarmHashes { bySwarmHash[h] = t }
    }

    // MARK: - Управление

    func torrent(_ id: String) -> Torrent? {
        InfoHash(hex: id).flatMap { torrents[$0] }
    }

    func pause(_ id: String) async { await torrent(id)?.pause() }
    func resume(_ id: String) async { await torrent(id)?.start() }

    func remove(_ id: String, deleteFiles: Bool) async {
        guard let hash = InfoHash(hex: id), let t = torrents[hash] else { return }
        await t.remove(deleteFiles: deleteFiles)
        torrents[hash] = nil
        for (h, other) in bySwarmHash where other === t { bySwarmHash[h] = nil }
        order.removeAll { $0 == hash }
        let fm = FileManager.default
        try? fm.removeItem(at: torrentsDirectory.appendingPathComponent("\(id).torrent"))
        try? fm.removeItem(at: torrentsDirectory.appendingPathComponent("\(id).resume.json"))
    }

    func setPriority(_ id: String, files: [Int], priority: FilePriority) async {
        await torrent(id)?.setPriority(priority, files: files)
    }

    func setSequential(_ id: String, _ on: Bool) async { await torrent(id)?.setSequential(on) }

    func addPeers(_ id: String, _ peers: [PeerAddress]) async { await torrent(id)?.addPeers(peers, source: .manual) }

    func pauseAll() async { for t in torrents.values { await t.pause() } }
    func resumeAll() async { for t in torrents.values { await t.start() } }

    func update(settings new: EngineSettings) async {
        settings.value = new
        limiter.configure(down: new.downloadLimit, up: new.uploadLimit)
        await applyServices()
    }

    func snapshots(detailed id: String?) async -> [TorrentSnapshot] {
        await snapshots(detailed: id.map { [$0] } ?? [])
    }

    /// Подробные снимки (пиры, куски, трекеры) — только для раздач, которые сейчас на экране.
    func snapshots(detailed ids: Set<String>) async -> [TorrentSnapshot] {
        var result: [TorrentSnapshot] = []
        for hash in order {
            guard let t = torrents[hash] else { continue }
            result.append(await t.snapshot(detailed: ids.contains(hash.hex)))
        }
        return result
    }

    var dhtNodes: Int { get async { await dht?.knownNodes ?? 0 } }

    func networkStatus() async -> NetworkStatus {
        var n = NetworkStatus()
        n.port = port
        n.udpOpen = udp != nil
        if let mapper { n.mapping = await mapper.status }
        if let dht, settings.value.dhtEnabled {
            let st = await dht.stats
            n.dhtNodes = st.nodes
            n.dhtQueries = st.queriesAnswered
            n.dhtStoredPeers = st.peersStored
        }
        n.utpConnections = utp?.connectionCount ?? 0
        n.lsdActive = lsd != nil
        n.lsdHeard = lsd?.received ?? 0
        n.blocklistRanges = blocklist.rangeCount
        n.blocklistBlocked = blocklist.blockedCount
        n.blocklistMessage = blocklistMessage
        return n
    }

    // MARK: - Сохранение

    private func markDirty(_ hash: InfoHash) {
        dirty.insert(hash)
        guard !saveScheduled else { return }
        saveScheduled = true
        Task {
            try? await Task.sleep(for: .seconds(2))
            await self.flush()
        }
    }

    private func flush() async {
        saveScheduled = false
        let hashes = dirty
        dirty.removeAll()
        for hash in hashes {
            guard let t = torrents[hash] else { continue }
            let data = await t.resumeData()
            if let json = try? JSONEncoder().encode(data) {
                try? json.write(to: torrentsDirectory.appendingPathComponent("\(hash.hex).resume.json"), options: .atomic)
            }
        }
    }

    func saveAll() async {
        dirty.formUnion(torrents.keys)
        await flush()
    }

    private func saveMetainfo(_ meta: Metainfo, id: InfoHash) {
        let url = torrentsDirectory.appendingPathComponent("\(id.hex).torrent")
        try? meta.torrentFileData().write(to: url, options: .atomic)
        if let t = torrents[id] {
            for h in meta.swarmHashes { bySwarmHash[h] = t }
        }
        markDirty(id)
    }

    private func restoreSaved() async {
        let fm = FileManager.default
        let files = (try? fm.contentsOfDirectory(at: torrentsDirectory, includingPropertiesForKeys: nil)) ?? []
        var restored: [(Date, Torrent, Bool)] = []
        for file in files where file.lastPathComponent.hasSuffix(".resume.json") {
            guard let data = try? Data(contentsOf: file),
                  let resume = try? JSONDecoder().decode(ResumeData.self, from: data) else { continue }
            let torrentURL = torrentsDirectory.appendingPathComponent("\(resume.infoHash).torrent")
            let meta = (try? Data(contentsOf: torrentURL)).flatMap { try? Metainfo(torrentData: $0) }
            let magnet = resume.magnet.flatMap(MagnetLink.init)
            guard meta != nil || magnet != nil else { continue }
            let t = Torrent(meta: meta, magnet: magnet, savePath: URL(fileURLWithPath: resume.savePath),
                            env: environment(), resume: resume)
            restored.append((resume.addedAt, t, resume.paused))
        }
        for (_, t, paused) in restored.sorted(by: { $0.0 < $1.0 }) {
            await insert(t)
            if !paused { await t.start() }
        }
    }
}
