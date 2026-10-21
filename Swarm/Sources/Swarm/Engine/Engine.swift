import Foundation
import Network
import os

nonisolated final class SettingsBox: Sendable {
    private let state: OSAllocatedUnfairLock<EngineSettings>
    init(_ s: EngineSettings) { state = OSAllocatedUnfairLock(initialState: s) }
    var value: EngineSettings {
        get { state.withLock { $0 } }
        set { state.withLock { $0 = newValue } }
    }
}

/// Приём входящих TCP-соединений. Колбэки — в nonisolated-классе.
nonisolated final class PeerListener: @unchecked Sendable {
    private let listener: NWListener
    let port: UInt16

    init(port: UInt16, onConnection: @escaping @Sendable (PeerConnection) -> Void) throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { throw PeerError("Неверный порт") }
        listener = try NWListener(using: params, on: nwPort)
        self.port = port
        listener.newConnectionHandler = { connection in
            onConnection(PeerConnection(incoming: connection))
        }
    }

    func start() async throws {
        let once = Once()
        let queue = DispatchQueue(label: "swarm.listener")
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, any Error>) in
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready: once.run { cont.resume() }
                case .failed(let e), .waiting(let e): once.run { cont.resume(throwing: e) }
                case .cancelled: once.run { cont.resume(throwing: PeerError("Порт закрыт")) }
                default: break
                }
            }
            listener.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 5) { once.run { cont.resume(throwing: PeerError("Порт не открылся")) } }
        }
    }

    func cancel() { listener.cancel() }
}

/// Движок: все раздачи, входящие соединения, DHT, сохранение состояния.
actor Engine {
    nonisolated let peerID = PeerIdentity.makePeerID()
    nonisolated let dht = DHTClient()
    nonisolated let limiter = RateLimiter()
    nonisolated let settings: SettingsBox
    nonisolated let supportDirectory: URL
    nonisolated let finishedNames: AsyncStream<String>
    private nonisolated let finishedContinuation: AsyncStream<String>.Continuation

    private(set) var port: UInt16
    private var torrents: [InfoHash: Torrent] = [:]
    private var order: [InfoHash] = []
    private var listener: PeerListener?
    private var dirty: Set<InfoHash> = []
    private var saveScheduled = false
    private var refillTask: Task<Void, Never>?

    init(supportDirectory: URL, port: UInt16, settings: EngineSettings) {
        self.supportDirectory = supportDirectory
        self.port = port
        self.settings = SettingsBox(settings)
        let stream = AsyncStream<String>.makeStream()
        finishedNames = stream.stream
        finishedContinuation = stream.continuation
        limiter.configure(down: settings.downloadLimit, up: settings.uploadLimit)
    }

    private var torrentsDirectory: URL { supportDirectory.appendingPathComponent("torrents", isDirectory: true) }

    func start(restore: Bool = true) async {
        try? FileManager.default.createDirectory(at: torrentsDirectory, withIntermediateDirectories: true)
        await openListener()
        let limiter = self.limiter
        refillTask = Task.detached {
            while !Task.isCancelled {
                limiter.refill(interval: 0.25)
                try? await Task.sleep(for: .milliseconds(250))
            }
        }
        if restore { await restoreSaved() }
    }

    private func openListener() async {
        for candidate in [port] + (1...20).map({ _ in UInt16.random(in: 49152...65000) }) {
            do {
                let listener = try PeerListener(port: candidate) { [weak self] conn in
                    Task { await self?.accept(conn) }
                }
                try await listener.start()
                self.listener = listener
                port = candidate
                return
            } catch {
                continue
            }
        }
    }

    private func accept(_ conn: PeerConnection) async {
        do {
            try await conn.open(timeout: 5)
            let raw = try await conn.read(68, timeout: 10)
            guard let hs = Handshake(raw), let hash = InfoHash(hs.infoHash), let torrent = torrents[hash],
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
        return TorrentEnvironment(
            peerID: peerID, port: port, dht: dht, limiter: limiter,
            settings: { box.value },
            changed: { [weak self] hash in Task { await self?.markDirty(hash) } },
            gotMetadata: { [weak self] meta in Task { await self?.saveMetainfo(meta) } },
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
        insert(torrent)
        saveMetainfo(meta)
        if !paused { await torrent.start() }
        markDirty(meta.infoHash)
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
        insert(torrent)
        await torrent.start()
        markDirty(magnet.infoHash)
        return magnet.infoHash
    }

    private func insert(_ t: Torrent) {
        torrents[t.infoHash] = t
        order.insert(t.infoHash, at: 0)
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

    func update(settings new: EngineSettings) {
        settings.value = new
        limiter.configure(down: new.downloadLimit, up: new.uploadLimit)
    }

    func snapshots(detailed id: String?) async -> [TorrentSnapshot] {
        var result: [TorrentSnapshot] = []
        for hash in order {
            guard let t = torrents[hash] else { continue }
            result.append(await t.snapshot(detailed: hash.hex == id))
        }
        return result
    }

    var dhtNodes: Int { get async { await dht.knownNodes } }

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

    private func saveMetainfo(_ meta: Metainfo) {
        let url = torrentsDirectory.appendingPathComponent("\(meta.infoHash.hex).torrent")
        try? meta.torrentFileData().write(to: url, options: .atomic)
        markDirty(meta.infoHash)
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
            insert(t)
            if !paused { await t.start() }
        }
    }
}
