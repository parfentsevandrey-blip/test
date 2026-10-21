import Foundation

/// Окружение, которое движок передаёт каждой раздаче.
nonisolated struct TorrentEnvironment: Sendable {
    let peerID: Data
    let port: UInt16
    let dht: DHTClient
    let limiter: RateLimiter
    let settings: @Sendable () -> EngineSettings
    /// Состояние изменилось — пора сохранить продолжение.
    let changed: @Sendable (InfoHash) -> Void
    /// Метаданные получены по magnet — сохранить .torrent.
    let gotMetadata: @Sendable (Metainfo) -> Void
    let finished: @Sendable (String) -> Void
}

nonisolated struct BlockKey: Hashable, Sendable {
    let piece: Int
    let begin: Int
}

/// Состояние скачиваемого куска: 0 — нет, 1 — запрошен, 2 — получен.
nonisolated struct PieceProgress {
    var state: [UInt8]
    var requesters: [UInt8]
    var received = 0
    var written = 0

    init(blocks: Int) {
        state = [UInt8](repeating: 0, count: blocks)
        requesters = [UInt8](repeating: 0, count: blocks)
    }

    var isFullyRequested: Bool { !state.contains(0) }
}

/// Пир внутри раздачи. Живёт только в изоляции актора Torrent.
nonisolated final class PeerSession {
    let conn: PeerConnection
    let source: PeerSource
    var remoteID = Data()
    var client = "…"
    var bitfield: Bitfield?
    var rawBitfield: Data?
    var pendingHaves: [Int] = []
    var amChoking = true
    var amInterested = false
    var peerChoking = true
    var peerInterested = false
    var requests: [BlockKey: Date] = [:]
    var extensions: [String: UInt8] = [:]
    var listenPort: UInt16?
    var lastReceived = Date()
    var lastSent = Date()
    var down = RateMeter()
    var up = RateMeter()
    var snubbed = false
    var uploadQueue: [(Int, Int, Int)] = []
    var uploading = false

    init(conn: PeerConnection, source: PeerSource) {
        self.conn = conn
        self.source = source
    }

    var key: ObjectIdentifier { ObjectIdentifier(self) }
    var isSeed: Bool { bitfield?.isComplete ?? false }

    func send(_ m: PeerMessage) {
        lastSent = Date()
        conn.send(m)
    }
}

nonisolated struct AddressInfo {
    var source: PeerSource
    var failures = 0
    var nextAttempt = Date.distantPast
}

nonisolated struct TrackerState {
    var url: URL
    var status = "Ожидание"
    var seeders: Int?
    var leechers: Int?
    var lastPeers = 0
    var nextAnnounce = Date()
    var running = false
    var failures = 0
}

/// Одна раздача: подключения к пирам, выбор кусков, проверка хешей, отдача.
actor Torrent {
    nonisolated let infoHash: InfoHash
    nonisolated let env: TorrentEnvironment
    nonisolated let addedAt: Date

    private(set) var meta: Metainfo?
    private var name: String
    private var trackers: [URL]
    private(set) var savePath: URL
    private(set) var status: TorrentStatus = .paused
    private var errorMessage: String?
    private var have = Bitfield(count: 0)
    private var wanted = Bitfield(count: 0)
    private var piecePriority: [UInt8] = []
    private var priorities: [FilePriority] = []
    private var sequential = false
    private var availability: [Int] = []
    private var progress: [Int: PieceProgress] = [:]
    private var storage: Storage?
    private var peers: [ObjectIdentifier: PeerSession] = [:]
    private var addresses: [PeerAddress: AddressInfo] = [:]
    private var connecting: Set<PeerAddress> = []
    private var trackerStates: [String: TrackerState] = [:]
    private var metadataSize: Int?
    private var metadataPieces: [Int: Data] = [:]
    private var metadataRequested: [Int: Date] = [:]
    private var uploadedTotal: Int64 = 0
    private var downloadedTotal: Int64 = 0
    private var rateDown = RateMeter()
    private var rateUp = RateMeter()
    private var recentPieces: [Int: Date] = [:]
    private var checkProgress: Double?
    private var lastDHT = Date.distantPast
    private var dhtRunning = false
    private var lastRechoke = Date.distantPast
    private var optimistic: ObjectIdentifier?
    private var lastOptimistic = Date.distantPast
    private var completedAt: Date?
    private var tickTask: Task<Void, Never>?
    private var trackerTask: Task<Void, Never>?
    private var resumeHave: Data?
    private var sequentialCursor = 0
    private var lastTick = Date()

    init(meta: Metainfo?, magnet: MagnetLink?, savePath: URL, env: TorrentEnvironment, resume: ResumeData?) {
        let hash = meta?.infoHash ?? magnet!.infoHash
        infoHash = hash
        self.env = env
        self.meta = meta
        self.savePath = savePath
        name = meta?.name ?? magnet?.name ?? resume?.name ?? hash.hex
        var trackerList = meta.map { $0.trackers.flatMap { $0 } } ?? magnet?.trackers ?? []
        if let extra = magnet?.trackers { trackerList += extra }
        var seen = Set<String>()
        trackers = trackerList.filter { seen.insert($0.absoluteString).inserted }
        addedAt = resume?.addedAt ?? Date()
        for p in magnet?.peers ?? [] { addresses[p] = AddressInfo(source: .manual) }
        if let resume {
            uploadedTotal = resume.uploaded
            downloadedTotal = resume.downloaded
            sequential = resume.sequential
            priorities = resume.priorities.compactMap(FilePriority.init(rawValue:))
            resumeHave = resume.have
            completedAt = resume.completedAt
            // Чтобы приостановленная раздача сразу показывала свой прогресс.
            if let meta, let saved = resume.have {
                have = Bitfield(count: meta.pieceCount, data: saved)
                let w = Self.wantedPieces(meta, priorities)
                wanted = w.0
                piecePriority = w.1
            }
        }
    }

    // MARK: - Жизненный цикл

    func start() async {
        guard status == .paused || status == .error || status == .finished else { return }
        errorMessage = nil
        if let meta {
            await prepareStorage(meta)
        } else {
            status = .metadata
        }
        startLoops()
    }

    func pause() async {
        tickTask?.cancel()
        trackerTask?.cancel()
        tickTask = nil
        trackerTask = nil
        for s in peers.values { s.conn.close() }
        peers.removeAll()
        connecting.removeAll()
        progress.removeAll()
        let announce = currentAnnounce(.stopped)
        if env.settings().trackersEnabled {
            for url in trackers.prefix(5) { Task.detached { _ = try? await TrackerClient.announce(url, announce) } }
        }
        status = .paused
        env.changed(infoHash)
    }

    func remove(deleteFiles: Bool) async {
        await pause()
        if deleteFiles { await storage?.deleteFiles() } else { await storage?.closeAll() }
    }

    private func prepareStorage(_ meta: Metainfo) async {
        let storage = Storage(meta: meta, root: savePath)
        self.storage = storage
        if priorities.count != meta.files.count { priorities = Array(repeating: .normal, count: meta.files.count) }
        availability = Array(repeating: 0, count: meta.pieceCount)
        recomputeWanted()
        status = .checking
        if let resumeHave, Bitfield(count: meta.pieceCount, data: resumeHave).setCount > 0, filesPresent(meta) {
            have = Bitfield(count: meta.pieceCount, data: resumeHave)
        } else {
            checkProgress = 0
            have = await storage.check { [weak self] p in
                Task { await self?.setCheckProgress(p) }
            }
            checkProgress = nil
        }
        resumeHave = nil
        updateCompletionState(announce: false)
        env.changed(infoHash)
    }

    private func setCheckProgress(_ p: Double) { if status == .checking { checkProgress = p } }

    private func filesPresent(_ meta: Metainfo) -> Bool {
        let fm = FileManager.default
        return meta.files.allSatisfy { f in
            f.path.contains(".pad") || f.length == 0 || fm.fileExists(atPath: f.path.reduce(savePath) { $0.appendingPathComponent($1) }.path)
        }
    }

    private func startLoops() {
        lastTick = Date()
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                await self?.tick()
            }
        }
        trackerTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.announceDue()
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    // MARK: - Пиры

    func addPeers(_ list: [PeerAddress], source: PeerSource) {
        for address in list where address.port > 0 && addresses[address] == nil {
            addresses[address] = AddressInfo(source: source)
        }
    }

    private var connectedAddresses: Set<PeerAddress> { Set(peers.values.map(\.conn.address)) }

    private func connectMore() {
        guard status.isActive, status != .checking else { return }
        let limit = env.settings().maxPeersPerTorrent
        let busy = peers.count + connecting.count
        guard busy < limit, connecting.count < 16 else { return }
        if status == .seeding { return } // раздача ждёт входящих и не тянет лишних соединений
        let now = Date()
        let connected = connectedAddresses
        let candidates = addresses
            .filter { !connected.contains($0.key) && !connecting.contains($0.key) && $0.value.nextAttempt <= now }
            .sorted { ($0.value.failures, $0.value.source == .manual ? 0 : 1) < ($1.value.failures, $1.value.source == .manual ? 0 : 1) }
            .prefix(min(16 - connecting.count, limit - busy))
        for (address, info) in candidates {
            connecting.insert(address)
            Task { await self.connect(address, source: info.source) }
        }
    }

    private func connect(_ address: PeerAddress, source: PeerSource) async {
        let conn = PeerConnection(address: address)
        do {
            try await conn.open()
            conn.sendRaw(Handshake.make(infoHash: infoHash.bytes, peerID: env.peerID))
            let raw = try await conn.read(68)
            guard let hs = Handshake(raw), hs.infoHash == infoHash.bytes else {
                throw PeerError("Чужая раздача")
            }
            guard hs.peerID != env.peerID else { throw PeerError("Соединение с самим собой") }
            connecting.remove(address)
            addresses[address]?.failures = 0
            attach(conn, handshake: hs, source: source)
        } catch {
            conn.close()
            connecting.remove(address)
            if var info = addresses[address] {
                info.failures += 1
                info.nextAttempt = Date().addingTimeInterval(min(1800, 20 * pow(2, Double(info.failures))))
                addresses[address] = info
                if info.failures > 6 && addresses.count > 400 { addresses[address] = nil }
            }
        }
    }

    /// Входящее соединение: движок уже прочитал рукопожатие пира.
    func acceptIncoming(_ conn: PeerConnection, handshake: Handshake) -> Bool {
        guard status.isActive, status != .checking, peers.count < env.settings().maxPeersPerTorrent + 10 else { return false }
        conn.sendRaw(Handshake.make(infoHash: infoHash.bytes, peerID: env.peerID))
        attach(conn, handshake: handshake, source: .incoming)
        return true
    }

    private func attach(_ conn: PeerConnection, handshake: Handshake, source: PeerSource) {
        let s = PeerSession(conn: conn, source: source)
        s.remoteID = handshake.peerID
        s.client = PeerIdentity.clientName(for: handshake.peerID)
        peers[s.key] = s
        if handshake.supportsExtensions { sendExtendedHandshake(s) }
        if meta != nil, have.setCount > 0 { s.send(.bitfield(have.data)) }
        Task { await self.run(s) }
    }

    private func run(_ s: PeerSession) async {
        do {
            for try await message in s.conn.messages() {
                await handle(s, message)
                if peers[s.key] == nil { break }
            }
        } catch {}
        detach(s)
    }

    private func detach(_ s: PeerSession) {
        guard peers.removeValue(forKey: s.key) != nil else { return }
        s.conn.close()
        releaseRequests(s)
        if let bf = s.bitfield {
            for i in 0..<min(bf.count, availability.count) where bf[i] { availability[i] -= 1 }
        }
        if optimistic == s.key { optimistic = nil }
    }

    // MARK: - Сообщения

    private func handle(_ s: PeerSession, _ message: PeerMessage) async {
        s.lastReceived = Date()
        switch message {
        case .keepAlive:
            break
        case .choke:
            s.peerChoking = true
            releaseRequests(s)
        case .unchoke:
            s.peerChoking = false
            fillRequests(s)
        case .interested:
            s.peerInterested = true
            if peers.values.filter({ !$0.amChoking }).count < 4 { unchoke(s) }
        case .notInterested:
            s.peerInterested = false
        case .have(let index):
            guard let meta else { s.pendingHaves.append(index); return }
            guard index >= 0, index < meta.pieceCount else { return }
            if s.bitfield == nil { s.bitfield = Bitfield(count: meta.pieceCount) }
            if s.bitfield?[index] == false {
                s.bitfield?[index] = true
                availability[index] += 1
            }
            updateInterest(s)
            fillRequests(s)
        case .bitfield(let data):
            if meta == nil { s.rawBitfield = data } else { applyBitfield(s, data) }
        case .request(let index, let begin, let length):
            serve(s, index: index, begin: begin, length: length)
        case .piece(let index, let begin, let block):
            await receive(s, index: index, begin: begin, block: block)
        case .cancel(let index, let begin, let length):
            s.uploadQueue.removeAll { $0 == (index, begin, length) }
        case .port:
            break
        case .extended(let id, let payload):
            await handleExtended(s, id: id, payload: payload)
        case .unknown:
            break
        }
    }

    private func applyBitfield(_ s: PeerSession, _ data: Data) {
        guard let meta else { return }
        if let old = s.bitfield {
            for i in 0..<old.count where old[i] { availability[i] -= 1 }
        }
        let bf = Bitfield(count: meta.pieceCount, data: data)
        for i in 0..<bf.count where bf[i] { availability[i] += 1 }
        s.bitfield = bf
        for i in s.pendingHaves where i >= 0 && i < meta.pieceCount && s.bitfield?[i] == false {
            s.bitfield?[i] = true
            availability[i] += 1
        }
        s.pendingHaves.removeAll()
        if status == .seeding, bf.isComplete { s.conn.close(); return }
        updateInterest(s)
        fillRequests(s)
    }

    private func updateInterest(_ s: PeerSession) {
        guard let bf = s.bitfield, status == .downloading else {
            if s.amInterested { s.amInterested = false; s.send(.notInterested) }
            return
        }
        var interesting = false
        let a = bf.bytes, w = wanted.bytes, h = have.bytes
        for i in 0..<min(a.count, w.count) where a[i] & w[i] & ~h[i] != 0 {
            interesting = true
            break
        }
        if interesting != s.amInterested {
            s.amInterested = interesting
            s.send(interesting ? .interested : .notInterested)
        }
    }

    // MARK: - Скачивание

    private func pipelineDepth(_ s: PeerSession) -> Int {
        if s.snubbed { return 2 }
        // Держим в полёте примерно 2 секунды трафика от этого пира.
        let byRate = Int(s.down.rate * 2 / Double(PeerMessage.maxBlock))
        return max(8, min(200, byRate))
    }

    private func fillRequests(_ s: PeerSession) {
        guard let meta, status == .downloading, !s.peerChoking, s.amInterested, let bf = s.bitfield else { return }
        let depth = pipelineDepth(s)
        while s.requests.count < depth {
            guard env.limiter.takeDown(PeerMessage.maxBlock) else { break }
            guard let block = pickBlock(for: s, bitfield: bf, meta: meta) else {
                env.limiter.refundDown(PeerMessage.maxBlock)
                break
            }
            s.requests[block.key] = Date()
            s.send(.request(index: block.key.piece, begin: block.key.begin, length: block.length))
        }
    }

    private func blockCount(_ meta: Metainfo, _ piece: Int) -> Int {
        (meta.pieceSize(piece) + PeerMessage.maxBlock - 1) / PeerMessage.maxBlock
    }

    private func pickBlock(for s: PeerSession, bitfield bf: Bitfield, meta: Metainfo) -> (key: BlockKey, length: Int)? {
        func take(_ piece: Int, _ b: Int, duplicate: Bool) -> (key: BlockKey, length: Int) {
            progress[piece]?.state[b] = 1
            progress[piece]?.requesters[b] += 1
            let begin = b * PeerMessage.maxBlock
            return (BlockKey(piece: piece, begin: begin), min(PeerMessage.maxBlock, meta.pieceSize(piece) - begin))
        }

        // 1. Докачиваем начатые куски, которые есть у пира.
        let started = progress.keys.sorted()
        let ordered = sequential ? started : started.sorted {
            (progress[$0]!.received, -$0) > (progress[$1]!.received, -$1)
        }
        for piece in ordered where bf[piece] {
            if let b = progress[piece]?.state.firstIndex(of: 0) { return take(piece, b, duplicate: false) }
        }

        // 2. Новый кусок: приоритет файла, затем редкость (или порядок при последовательной загрузке).
        let unchokedBy = max(1, peers.values.filter { !$0.peerChoking }.count)
        if progress.count < max(8, unchokedBy * 3), let piece = choosePiece(bf, meta: meta) {
            progress[piece] = PieceProgress(blocks: blockCount(meta, piece))
            return take(piece, 0, duplicate: false)
        }

        // 3. Эндшпиль: всё уже запрошено — дублируем запросы у других пиров.
        if wantedRemaining(meta) <= progress.count {
            for piece in started where bf[piece] {
                guard let p = progress[piece] else { continue }
                for b in p.state.indices where p.state[b] == 1 && p.requesters[b] < 3 {
                    let key = BlockKey(piece: piece, begin: b * PeerMessage.maxBlock)
                    if s.requests[key] == nil { return take(piece, b, duplicate: true) }
                }
            }
        }
        return nil
    }

    private func wantedRemaining(_ meta: Metainfo) -> Int {
        var n = 0
        for (w, h) in zip(wanted.bytes, have.bytes) { n += (w & ~h).nonzeroBitCount }
        return n
    }

    private func choosePiece(_ bf: Bitfield, meta: Metainfo) -> Int? {
        let count = meta.pieceCount
        if sequential {
            while sequentialCursor < count && (have[sequentialCursor] || !wanted[sequentialCursor]) { sequentialCursor += 1 }
            var i = sequentialCursor
            while i < count {
                if wanted[i] && !have[i] && progress[i] == nil && bf[i] { return i }
                i += 1
            }
            return nil
        }
        var best: [Int] = []
        var bestKey = (0, Int.max)
        for i in 0..<count where wanted[i] && !have[i] && progress[i] == nil && bf[i] {
            let key = (Int(piecePriority[i]), availability[i])
            if key.0 > bestKey.0 || (key.0 == bestKey.0 && key.1 < bestKey.1) {
                bestKey = key
                best = [i]
            } else if key == bestKey, best.count < 32 {
                best.append(i)
            }
        }
        return best.randomElement()
    }

    private func releaseRequests(_ s: PeerSession) {
        for key in s.requests.keys { releaseBlock(key) }
        s.requests.removeAll()
    }

    private func releaseBlock(_ key: BlockKey) {
        let b = key.begin / PeerMessage.maxBlock
        guard var p = progress[key.piece], b < p.state.count, p.state[b] == 1 else { return }
        p.requesters[b] = p.requesters[b] > 0 ? p.requesters[b] - 1 : 0
        if p.requesters[b] == 0 { p.state[b] = 0 }
        progress[key.piece] = p
    }

    private func receive(_ s: PeerSession, index: Int, begin: Int, block: Data) async {
        let key = BlockKey(piece: index, begin: begin)
        s.requests[key] = nil
        s.down.add(block.count)
        rateDown.add(block.count)
        downloadedTotal += Int64(block.count)
        s.snubbed = false
        guard let meta, let storage, !have[index], var p = progress[index] else { fillRequests(s); return }
        let b = begin / PeerMessage.maxBlock
        guard begin % PeerMessage.maxBlock == 0, b < p.state.count, p.state[b] != 2,
              block.count == min(PeerMessage.maxBlock, meta.pieceSize(index) - begin) else { fillRequests(s); return }
        p.state[b] = 2
        p.received += 1
        progress[index] = p
        // Эндшпиль: отменяем тот же блок у остальных.
        for other in peers.values where other !== s && other.requests[key] != nil {
            other.requests[key] = nil
            other.send(.cancel(index: index, begin: begin, length: block.count))
        }
        fillRequests(s)
        do {
            try await storage.write(piece: index, begin: begin, data: block)
        } catch {
            fail(error.localizedDescription)
            return
        }
        guard progress[index] != nil else { return }
        progress[index]?.written += 1
        guard let done = progress[index], done.written == done.state.count else { return }
        let ok = await storage.verify(piece: index)
        progress[index] = nil
        if ok {
            have[index] = true
            recentPieces[index] = Date()
            for other in peers.values where other.bitfield?[index] != true { other.send(.have(index)) }
            for other in peers.values { updateInterest(other) }
            updateCompletionState(announce: true)
        }
        for other in peers.values { fillRequests(other) }
    }

    private func updateCompletionState(announce: Bool) {
        guard let meta else { return }
        let wantedDone = wantedRemaining(meta) == 0
        if have.isComplete {
            if status != .seeding && status != .finished {
                status = .seeding
                if announce {
                    completedAt = Date()
                    env.finished(name)
                    announceNow(.completed)
                }
                for s in peers.values where s.isSeed { s.conn.close() }
            }
        } else if wantedDone {
            if status == .downloading && announce { env.finished(name); completedAt = Date() }
            status = .seeding
        } else {
            status = .downloading
        }
        for s in peers.values { updateInterest(s) }
        env.changed(infoHash)
    }

    private func fail(_ message: String) {
        errorMessage = message
        status = .error
        tickTask?.cancel()
        trackerTask?.cancel()
        for s in peers.values { s.conn.close() }
        peers.removeAll()
    }

    // MARK: - Отдача

    private func unchoke(_ s: PeerSession) {
        guard s.amChoking else { return }
        s.amChoking = false
        s.send(.unchoke)
    }

    private func choke(_ s: PeerSession) {
        guard !s.amChoking else { return }
        s.amChoking = true
        s.uploadQueue.removeAll()
        s.send(.choke)
    }

    private func serve(_ s: PeerSession, index: Int, begin: Int, length: Int) {
        guard let meta, !s.amChoking, index >= 0, index < meta.pieceCount, have[index],
              length > 0, length <= 128 * 1024, begin >= 0, begin + length <= meta.pieceSize(index) else { return }
        s.uploadQueue.append((index, begin, length))
        Task { await self.processUploads(s) }
    }

    private func processUploads(_ s: PeerSession) async {
        guard !s.uploading, let storage else { return }
        s.uploading = true
        defer { s.uploading = false }
        while let (index, begin, length) = s.uploadQueue.first, peers[s.key] != nil, !s.amChoking {
            guard env.limiter.takeUp(length) else { break }
            s.uploadQueue.removeFirst()
            guard let data = try? await storage.read(piece: index, begin: begin, length: length) else { continue }
            guard peers[s.key] != nil, !s.amChoking else { break }
            s.send(.piece(index: index, begin: begin, block: data))
            s.up.add(length)
            rateUp.add(length)
            uploadedTotal += Int64(length)
        }
    }

    /// Каждые 10 с отдаём четырём самым щедрым (при раздаче — самым быстрым) пирам
    /// и одному случайному «оптимистичному».
    private func rechoke() {
        let interested = peers.values.filter(\.peerInterested)
        let ranked = interested.sorted {
            status == .seeding ? $0.up.rate > $1.up.rate : $0.down.rate > $1.down.rate
        }
        var allowed = Set(ranked.prefix(4).map(\.key))
        if Date().timeIntervalSince(lastOptimistic) > 30 || optimistic == nil || peers[optimistic!] == nil {
            optimistic = ranked.dropFirst(4).randomElement()?.key
            lastOptimistic = Date()
        }
        if let optimistic { allowed.insert(optimistic) }
        for s in peers.values {
            if allowed.contains(s.key) { unchoke(s) } else { choke(s) }
        }
    }

    // MARK: - Расширения (BEP 10, ut_metadata BEP 9, PEX BEP 11)

    private nonisolated static let utMetadataID: UInt8 = 1
    private nonisolated static let utPexID: UInt8 = 2

    private func sendExtendedHandshake(_ s: PeerSession) {
        var dict: [String: BValue] = [
            "m": .dict(["ut_metadata": .int(Int64(Self.utMetadataID)), "ut_pex": .int(Int64(Self.utPexID))]),
            "p": .int(Int64(env.port)),
            "v": .str(PeerIdentity.clientName),
            "reqq": .int(250),
        ]
        if let meta { dict["metadata_size"] = .int(Int64(meta.infoDictionary.count)) }
        s.send(.extended(id: 0, payload: BValue.dict(dict).encoded))
    }

    private func handleExtended(_ s: PeerSession, id: UInt8, payload: Data) async {
        if id == 0 {
            guard let hs = try? Bencode.decode(payload) else { return }
            for (name, value) in hs["m"]?.dict ?? [:] {
                if let v = value.int, v > 0, v < 256 { s.extensions[name] = UInt8(v) }
            }
            if let v = hs["v"]?.string, !v.isEmpty { s.client = v }
            if let p = hs["p"]?.int, let port = UInt16(exactly: p) { s.listenPort = port }
            if meta == nil, let size = hs["metadata_size"]?.int, size > 0, size < 16 << 20 {
                if metadataSize == nil { metadataSize = Int(size) }
                requestMetadata()
            }
            return
        }
        if id == Self.utMetadataID {
            guard let decoded = try? Bencode.decodePrefix(payload) else { return }
            let (dict, consumed) = decoded
            guard let type = dict["msg_type"]?.int, let piece = dict["piece"]?.int.map(Int.init), piece >= 0 else { return }
            switch type {
            case 0:
                guard let their = s.extensions["ut_metadata"] else { return }
                if let meta, piece * 16384 < meta.infoDictionary.count {
                    let chunk = meta.infoDictionary.subdata(in: (piece * 16384)..<min(meta.infoDictionary.count, (piece + 1) * 16384))
                    var out = BValue.dict(["msg_type": .int(1), "piece": .int(Int64(piece)), "total_size": .int(Int64(meta.infoDictionary.count))]).encoded
                    out.append(chunk)
                    s.send(.extended(id: their, payload: out))
                } else {
                    s.send(.extended(id: their, payload: BValue.dict(["msg_type": .int(2), "piece": .int(Int64(piece))]).encoded))
                }
            case 1:
                guard meta == nil, let size = metadataSize else { return }
                metadataPieces[piece] = Data(payload.dropFirst(consumed))
                let total = (size + 16383) / 16384
                if metadataPieces.count == total {
                    var assembled = Data()
                    for i in 0..<total { assembled.append(metadataPieces[i] ?? Data()) }
                    await metadataAssembled(assembled.prefix(size))
                }
            case 2:
                metadataRequested[piece] = nil
            default:
                break
            }
            return
        }
        if id == Self.utPexID {
            guard let dict = try? Bencode.decode(payload) else { return }
            var list: [PeerAddress] = []
            if let added = dict["added"]?.data { list += PeerAddress.compact(added) }
            if let added6 = dict["added6"]?.data { list += PeerAddress.compact(added6, ipv6: true) }
            addPeers(list, source: .pex)
        }
    }

    private func requestMetadata() {
        guard meta == nil, let size = metadataSize else { return }
        let total = (size + 16383) / 16384
        let capable = peers.values.filter { $0.extensions["ut_metadata"] != nil }
        guard !capable.isEmpty else { return }
        var turn = 0
        for piece in 0..<total where metadataPieces[piece] == nil {
            if let at = metadataRequested[piece], Date().timeIntervalSince(at) < 8 { continue }
            let s = capable[turn % capable.count]
            turn += 1
            metadataRequested[piece] = Date()
            s.send(.extended(id: s.extensions["ut_metadata"]!, payload: BValue.dict(["msg_type": .int(0), "piece": .int(Int64(piece))]).encoded))
        }
    }

    private func metadataAssembled(_ data: Data) async {
        guard meta == nil else { return }
        guard let m = try? Metainfo(infoDictionary: Data(data), trackers: trackers.map { [$0] }), m.infoHash == infoHash else {
            // Испорченные метаданные: начинаем заново.
            metadataPieces.removeAll()
            metadataRequested.removeAll()
            return
        }
        meta = m
        name = m.name
        env.gotMetadata(m)
        await prepareStorage(m)
        for s in peers.values {
            if let raw = s.rawBitfield { applyBitfield(s, raw) } else if !s.pendingHaves.isEmpty { applyBitfield(s, Data()) }
            s.rawBitfield = nil
            if have.setCount > 0 { s.send(.bitfield(have.data)) }
            sendExtendedHandshake(s)
        }
    }

    // MARK: - Трекеры и DHT

    private func currentAnnounce(_ event: AnnounceEvent) -> AnnounceRequest {
        AnnounceRequest(infoHash: infoHash, peerID: env.peerID, port: env.port,
                        uploaded: uploadedTotal, downloaded: downloadedTotal,
                        left: meta.map { max(0, $0.totalLength - doneBytes($0)) } ?? 1 << 30,
                        event: event, key: UInt32(truncatingIfNeeded: env.peerID.hashValue))
    }

    private func announceDue() {
        guard status.isActive, env.settings().trackersEnabled, !trackers.isEmpty else { return }
        let now = Date()
        for url in trackers.prefix(20) {
            let id = url.absoluteString
            var st = trackerStates[id] ?? TrackerState(url: url)
            guard !st.running, st.nextAnnounce <= now else { trackerStates[id] = st; continue }
            st.running = true
            st.status = "Опрос…"
            let first = st.lastPeers == 0 && st.failures == 0 && st.seeders == nil
            trackerStates[id] = st
            let request = currentAnnounce(first ? .started : .none)
            Task {
                do {
                    let response = try await TrackerClient.announce(url, request)
                    self.trackerSucceeded(id, response)
                } catch {
                    self.trackerFailed(id, error.localizedDescription)
                }
            }
        }
    }

    private func announceNow(_ event: AnnounceEvent) {
        guard env.settings().trackersEnabled else { return }
        let request = currentAnnounce(event)
        for url in trackers.prefix(10) { Task.detached { _ = try? await TrackerClient.announce(url, request) } }
    }

    private func trackerSucceeded(_ id: String, _ r: AnnounceResponse) {
        guard var st = trackerStates[id] else { return }
        st.running = false
        st.failures = 0
        st.status = "Работает"
        st.seeders = r.seeders
        st.leechers = r.leechers
        st.lastPeers = r.peers.count
        st.nextAnnounce = Date().addingTimeInterval(TimeInterval(max(120, min(r.interval, 3600))))
        trackerStates[id] = st
        addPeers(r.peers, source: .tracker)
    }

    private func trackerFailed(_ id: String, _ message: String) {
        guard var st = trackerStates[id] else { return }
        st.running = false
        st.failures += 1
        st.status = message
        st.nextAnnounce = Date().addingTimeInterval(min(3600, 60 * pow(2, Double(min(st.failures, 6)))))
        trackerStates[id] = st
    }

    private func lookupDHTIfNeeded() {
        guard env.settings().dhtEnabled, meta?.isPrivate != true, !dhtRunning, status != .seeding else { return }
        let interval: TimeInterval = peers.count < 15 ? 90 : 600
        guard Date().timeIntervalSince(lastDHT) > interval else { return }
        dhtRunning = true
        lastDHT = Date()
        let hash = infoHash
        let dht = env.dht
        Task {
            await dht.findPeers(infoHash: hash) { [weak self] found in
                await self?.addPeers(found, source: .dht)
            }
            self.dhtFinished()
        }
    }

    private func dhtFinished() { dhtRunning = false }

    // MARK: - Такт

    private func tick() {
        let now = Date()
        let dt = now.timeIntervalSince(lastTick)
        lastTick = now
        rateDown.tick(dt)
        rateUp.tick(dt)
        for s in peers.values {
            s.down.tick(dt)
            s.up.tick(dt)
        }
        guard status.isActive, status != .checking else { return }

        connectMore()
        lookupDHTIfNeeded()
        if meta == nil { requestMetadata() }

        // Таймауты запросов и молчащие пиры.
        for s in peers.values {
            if now.timeIntervalSince(s.lastReceived) > 150 { s.conn.close(); continue }
            let stale = s.requests.filter { now.timeIntervalSince($0.value) > 30 }
            if !stale.isEmpty {
                s.snubbed = true
                for key in stale.keys { s.requests[key] = nil; releaseBlock(key) }
            }
            if now.timeIntervalSince(s.lastSent) > 90 { s.send(.keepAlive) }
            if !s.uploadQueue.isEmpty { Task { await self.processUploads(s) } }
            fillRequests(s)
        }
        if now.timeIntervalSince(lastRechoke) > 10 {
            lastRechoke = now
            rechoke()
        }
        for (piece, at) in recentPieces where now.timeIntervalSince(at) > 4 { recentPieces[piece] = nil }

        // Ограничение рейтинга раздачи.
        let ratioLimit = env.settings().seedRatioLimit
        if status == .seeding, ratioLimit > 0, let meta, Double(uploadedTotal) >= Double(meta.totalLength) * ratioLimit {
            tickTask?.cancel()
            trackerTask?.cancel()
            for s in peers.values { s.conn.close() }
            peers.removeAll()
            status = .finished
            announceNow(.stopped)
            env.changed(infoHash)
        }
    }

    // MARK: - Управление

    func setPriority(_ priority: FilePriority, files: [Int]) {
        for i in files where i < priorities.count { priorities[i] = priority }
        recomputeWanted()
        if meta != nil, status == .seeding || status == .downloading { updateCompletionState(announce: false) }
        env.changed(infoHash)
    }

    func setSequential(_ on: Bool) {
        sequential = on
        sequentialCursor = 0
        env.changed(infoHash)
    }

    private func recomputeWanted() {
        guard let meta else { return }
        (wanted, piecePriority) = Self.wantedPieces(meta, priorities)
    }

    private nonisolated static func wantedPieces(_ meta: Metainfo, _ priorities: [FilePriority]) -> (Bitfield, [UInt8]) {
        var wanted = Bitfield(count: meta.pieceCount)
        var piecePriority = [UInt8](repeating: 0, count: meta.pieceCount)
        let pl = Int64(meta.pieceLength)
        for (i, file) in meta.files.enumerated() where file.length > 0 {
            let priority = i < priorities.count ? priorities[i] : .normal
            guard priority != .skip else { continue }
            let first = Int(file.offset / pl)
            let last = Int((file.offset + file.length - 1) / pl)
            for p in first...last {
                wanted[p] = true
                piecePriority[p] = max(piecePriority[p], UInt8(priority.rawValue))
            }
        }
        return (wanted, piecePriority)
    }

    private func doneBytes(_ meta: Metainfo) -> Int64 {
        var bytes = Int64(have.setCount) * Int64(meta.pieceLength)
        if have[meta.pieceCount - 1] { bytes -= Int64(meta.pieceLength - meta.pieceSize(meta.pieceCount - 1)) }
        return bytes
    }

    func resumeData() -> ResumeData {
        ResumeData(infoHash: infoHash.hex, name: name,
                   magnet: meta == nil ? MagnetLink.make(hash: infoHash, name: name, trackers: trackers) : nil,
                   savePath: savePath.path, have: meta == nil ? nil : have.data,
                   priorities: priorities.map(\.rawValue), sequential: sequential,
                   paused: status == .paused, uploaded: uploadedTotal, downloaded: downloadedTotal,
                   addedAt: addedAt, completedAt: completedAt)
    }

    // MARK: - Снимок для интерфейса

    func snapshot(detailed: Bool) -> TorrentSnapshot {
        let meta = self.meta
        var files: [FileSnapshot] = []
        var wantedSize: Int64 = 0
        var doneSize: Int64 = 0
        if let meta {
            let pl = Int64(meta.pieceLength)
            for (i, f) in meta.files.enumerated() where !f.path.contains(".pad") {
                let priority = i < priorities.count ? priorities[i] : .normal
                var fileDone: Int64 = 0
                if f.length > 0 {
                    let first = Int(f.offset / pl), last = Int((f.offset + f.length - 1) / pl)
                    for p in first...last where have[p] {
                        let start = max(f.offset, Int64(p) * pl)
                        let end = min(f.offset + f.length, Int64(p) * pl + Int64(meta.pieceSize(p)))
                        fileDone += max(0, end - start)
                    }
                }
                if priority != .skip {
                    wantedSize += f.length
                    doneSize += fileDone
                }
                if detailed {
                    files.append(FileSnapshot(id: i, path: f.path.dropFirst(meta.isMultiFile ? 1 : 0).joined(separator: "/"),
                                              size: f.length, progress: f.length > 0 ? Double(fileDone) / Double(f.length) : 1,
                                              priority: priority, localURL: f.path.reduce(savePath) { $0.appendingPathComponent($1) }))
                }
            }
        }
        let peerList: [PeerSnapshot] = detailed ? peers.values.map { s in
            let prog = s.bitfield.map { Double($0.setCount) / Double(max(1, $0.count)) } ?? 0
            let addr = s.conn.address.description
            let angle = Double(UInt(bitPattern: addr.utf8.reduce(5381) { ($0 << 5) &+ $0 &+ Int($1) }) % 3600) / 3600 * 2 * .pi
            return PeerSnapshot(address: addr, client: s.client, progress: prog, downRate: s.down.rate, upRate: s.up.rate,
                                isSeed: s.isSeed, source: s.source, isIncoming: s.conn.isIncoming,
                                isChokingUs: s.peerChoking, isInterestedInUs: s.peerInterested, angle: angle)
        }.sorted { $0.downRate + $0.upRate > $1.downRate + $1.upRate } : []
        let trackerList: [TrackerSnapshot] = detailed ? trackers.map { url in
            let st = trackerStates[url.absoluteString]
            return TrackerSnapshot(url: url.absoluteString, status: st?.status ?? (env.settings().trackersEnabled ? "Ожидание" : "Отключены"),
                                   seeders: st?.seeders, leechers: st?.leechers, lastPeers: st?.lastPeers ?? 0, nextAnnounce: st?.nextAnnounce)
        } : []
        let metaProgress: Double? = meta == nil ? metadataSize.map { size in
            Double(metadataPieces.count) / Double(max(1, (size + 16383) / 16384))
        } ?? 0 : nil
        let contentURL = meta.map { m in m.isMultiFile ? savePath.appendingPathComponent(m.name) : m.files[0].path.reduce(savePath) { $0.appendingPathComponent($1) } }
        return TorrentSnapshot(
            id: infoHash.hex, name: name, status: status, error: errorMessage,
            progress: wantedSize > 0 ? Double(doneSize) / Double(wantedSize) : (meta == nil ? 0 : 1),
            checkProgress: checkProgress, metadataProgress: metaProgress,
            totalSize: meta?.totalLength ?? 0, wantedSize: wantedSize, doneSize: doneSize,
            downRate: rateDown.rate, upRate: rateUp.rate,
            downloadedTotal: downloadedTotal, uploadedTotal: uploadedTotal,
            peersConnected: peers.count, seedsConnected: peers.values.filter(\.isSeed).count,
            peersKnown: addresses.count,
            pieceCount: meta?.pieceCount ?? 0, pieceLength: meta?.pieceLength ?? 0,
            havePieces: have.data, inProgressPieces: Array(progress.keys), recentPieces: recentPieces,
            files: files, peers: peerList, trackers: trackerList,
            savePath: savePath, contentURL: contentURL, isSequential: sequential,
            addedAt: addedAt, completedAt: completedAt,
            magnet: MagnetLink.make(hash: infoHash, name: name, trackers: Array(trackers.prefix(8))),
            isPrivate: meta?.isPrivate ?? false)
    }
}
