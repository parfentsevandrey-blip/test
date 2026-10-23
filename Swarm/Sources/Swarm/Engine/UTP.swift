import Foundation

/// Адрес UDP: 16 байт IPv6 (IPv4 хранится как ::ffff:a.b.c.d) и порт.
nonisolated struct UDPAddress: Hashable, Sendable, CustomStringConvertible {
    let ip: [UInt8]
    let port: UInt16

    init(ip: [UInt8], port: UInt16) {
        self.ip = ip.count == 4 ? [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF] + ip : ip
        self.port = port
    }

    /// Только числовые адреса (имена узлов разрешаются отдельно).
    init?(host: String, port: UInt16) {
        var h = host
        if h.hasPrefix("["), h.hasSuffix("]") { h = String(h.dropFirst().dropLast()) }
        if let v4 = UDPAddress.parseV4(h) {
            self.init(ip: v4, port: port)
            return
        }
        var addr = in6_addr()
        guard h.contains(":"), inet_pton(AF_INET6, h, &addr) == 1 else { return nil }
        let bytes = withUnsafeBytes(of: &addr) { Array($0) }
        self.init(ip: bytes, port: port)
    }

    init?(_ peer: PeerAddress) { self.init(host: peer.host, port: peer.port) }

    static func parseV4(_ s: String) -> [UInt8]? {
        let parts = s.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return nil }
        var out: [UInt8] = []
        for p in parts {
            guard !p.isEmpty, p.count <= 3, p.allSatisfy(\.isASCII), let v = UInt8(p) else { return nil }
            out.append(v)
        }
        return out
    }

    var isV4: Bool { ip[0..<10].allSatisfy { $0 == 0 } && ip[10] == 0xFF && ip[11] == 0xFF }
    var v4: [UInt8]? { isV4 ? Array(ip[12..<16]) : nil }

    var host: String {
        if let v4 { return v4.map(String.init).joined(separator: ".") }
        var groups: [String] = []
        for g in stride(from: 0, to: 16, by: 2) { groups.append(String(UInt16(ip[g]) << 8 | UInt16(ip[g + 1]), radix: 16)) }
        return groups.joined(separator: ":")
    }

    var peerAddress: PeerAddress { PeerAddress(host: host, port: port) }
    var description: String { peerAddress.description }

    /// 6 байт компактного формата (только IPv4).
    var compact: Data? {
        guard let v4 else { return nil }
        var d = Data(v4)
        d.appendBE(port)
        return d
    }
}

nonisolated enum UTPClock {
    static func now() -> UInt64 { DispatchTime.now().uptimeNanoseconds / 1000 }
}

nonisolated enum UTPType: UInt8 {
    case data = 0, fin = 1, state = 2, reset = 3, syn = 4
}

nonisolated struct UTPHeader {
    var type: UTPType
    var connID: UInt16
    var timestamp: UInt32 = 0
    var timestampDiff: UInt32 = 0
    var window: UInt32 = 0
    var seq: UInt16
    var ack: UInt16
    /// Битовая маска выборочного подтверждения (SACK): бит i — пакет ack + 2 + i.
    var sack: [UInt8]?

    static let size = 20

    /// Разбор заголовка; возвращает его и смещение полезной нагрузки.
    static func parse(_ b: [UInt8]) -> (UTPHeader, Int)? {
        guard b.count >= size, b[0] & 0x0F == 1, let type = UTPType(rawValue: b[0] >> 4) else { return nil }
        func u16(_ i: Int) -> UInt16 { UInt16(b[i]) << 8 | UInt16(b[i + 1]) }
        func u32(_ i: Int) -> UInt32 { UInt32(b[i]) << 24 | UInt32(b[i + 1]) << 16 | UInt32(b[i + 2]) << 8 | UInt32(b[i + 3]) }
        var h = UTPHeader(type: type, connID: u16(2), timestamp: u32(4), timestampDiff: u32(8), window: u32(12), seq: u16(16), ack: u16(18))
        var ext = b[1]
        var at = size
        while ext != 0 {
            guard at + 2 <= b.count else { return nil }
            let next = b[at], len = Int(b[at + 1])
            guard at + 2 + len <= b.count else { return nil }
            if ext == 1 { h.sack = Array(b[(at + 2)..<(at + 2 + len)]) }
            ext = next
            at += 2 + len
        }
        return (h, at)
    }

    func encode(_ payload: ArraySlice<UInt8> = []) -> [UInt8] {
        var b = [UInt8]()
        b.reserveCapacity(UTPHeader.size + (sack.map { $0.count + 2 } ?? 0) + payload.count)
        b.append(type.rawValue << 4 | 1)
        b.append(sack == nil ? 0 : 1)
        func p16(_ v: UInt16) { b.append(UInt8(v >> 8)); b.append(UInt8(v & 0xFF)) }
        func p32(_ v: UInt32) { b.append(contentsOf: [UInt8(v >> 24), UInt8(v >> 16 & 0xFF), UInt8(v >> 8 & 0xFF), UInt8(v & 0xFF)]) }
        p16(connID)
        p32(timestamp)
        p32(timestampDiff)
        p32(window)
        p16(seq)
        p16(ack)
        if let sack {
            b.append(0)
            b.append(UInt8(sack.count))
            b.append(contentsOf: sack)
        }
        b.append(contentsOf: payload)
        return b
    }
}

/// Сравнение 16-битных номеров с переполнением: a раньше b.
nonisolated func utpBefore(_ a: UInt16, _ b: UInt16) -> Bool { a != b && (b &- a) < 0x8000 }

/// Одно uTP-соединение (BEP 29): нумерация пакетов, подтверждения (в том числе выборочные),
/// повторы, контроль перегрузки LEDBAT. Чистая логика без сокетов и таймеров: время передаётся
/// снаружи, пакеты уходят через `emit`. Все вызовы — с одной очереди.
nonisolated final class UTPSocket {
    enum State { case synSent, connected, finSent, closed }

    struct OutPacket {
        var seq: UInt16
        var type: UTPType
        var payload: [UInt8]
        var sentAt: UInt64
        var transmissions: Int
        var inFlight: Bool
    }

    static let mss = 1360
    static let target: Double = 100_000 // 100 мс задержки очереди — цель LEDBAT
    static let receiveBuffer = 1 << 20
    private static let ring = 2048
    private static let maxOutstanding = 1000

    let remote: UDPAddress
    let recvID: UInt16
    let sendID: UInt16
    private(set) var state: State
    private var seqNr: UInt16
    private var ackNr: UInt16 = 0
    private var oldest: UInt16
    private var out = [OutPacket?](repeating: nil, count: UTPSocket.ring)
    private(set) var outCount = 0
    private var curWindow = 0
    private(set) var maxWindow = Double(3 * UTPSocket.mss)
    private var slowStart = true
    private var peerWindow = UTPSocket.receiveBuffer
    private var sendBuffer: [UInt8] = []
    private var sendHead = 0
    private(set) var rtt = 0.0
    private var rttVar = 0.0
    private(set) var timeoutMs = 1000.0
    private var timeoutAt: UInt64 = 0
    private var timeouts = 0
    private var replyMicro: UInt32 = 0
    private var baseCurrent: UInt32?
    private var basePrevious: UInt32?
    private var baseSwitch: UInt64 = 0
    private var dupAcks = 0
    private var recoveryUntil: UInt16?
    private var reorder: [UInt16: [UInt8]] = [:]
    private var reorderBytes = 0
    private var eof: UInt16?
    private var ackPending = false
    /// Принимающая сторона: ответ на SYN (ST_STATE с начальным номером) ещё не отправлен.
    private var synAckPending = false
    private var initialSeq: UInt16 = 0
    private var finQueued = false
    private var lastReceived: UInt64
    private var lastSent: UInt64
    private var synSentAt: UInt64 = 0
    private var synRetries = 0
    private(set) var closeDeadline: UInt64?
    private var pending: [Data] = []
    private(set) var resent = 0

    var emit: ([UInt8]) -> Void = { _ in }
    var onConnected: (((any Error)?) -> Void)?
    var onData: ((Data) -> Void)? {
        didSet {
            guard let onData, !pending.isEmpty else { return }
            let queued = pending
            pending.removeAll()
            for d in queued { onData(d) }
        }
    }
    var onClosed: (((any Error)?) -> Void)?

    /// Исходящее соединение.
    init(connectTo remote: UDPAddress, id: UInt16, now: UInt64) {
        self.remote = remote
        recvID = id
        sendID = id &+ 1
        seqNr = 1
        oldest = 2
        state = .synSent
        lastReceived = now
        lastSent = now
    }

    /// Входящее: по пришедшему SYN.
    init(accepting syn: UTPHeader, from remote: UDPAddress, now: UInt64) {
        self.remote = remote
        recvID = syn.connID &+ 1
        sendID = syn.connID
        seqNr = UInt16.random(in: 0...UInt16.max)
        initialSeq = seqNr
        oldest = seqNr
        ackNr = syn.seq
        state = .connected
        lastReceived = now
        lastSent = now
        replyMicro = UInt32(truncatingIfNeeded: now) &- syn.timestamp
        peerWindow = Int(syn.window)
        synAckPending = true
    }

    var isClosed: Bool { state == .closed }

    var debugState: String {
        "\(state) seq \(seqNr) oldest \(oldest) ack \(ackNr) out \(outCount) cur \(curWindow) max \(Int(maxWindow)) peer \(peerWindow) timeouts \(timeouts) rto \(Int(timeoutMs)) reorder \(reorder.count) send \(bufferedToSend)"
    }
    var bufferedToSend: Int { sendBuffer.count - sendHead }

    private func index(_ seq: UInt16) -> Int { Int(seq) & (UTPSocket.ring - 1) }

    private var advertisedWindow: UInt32 { UInt32(max(0, UTPSocket.receiveBuffer - reorderBytes)) }

    private func header(_ type: UTPType, seq: UInt16, now: UInt64) -> UTPHeader {
        UTPHeader(type: type, connID: type == .syn ? recvID : sendID, timestamp: UInt32(truncatingIfNeeded: now),
                  timestampDiff: replyMicro, window: advertisedWindow, seq: seq, ack: ackNr)
    }

    // MARK: - Внешние события

    func connect(now: UInt64) {
        synSentAt = now
        emit(header(.syn, seq: 1, now: now).encode())
        seqNr = 2
        lastSent = now
    }

    func write(_ data: Data) {
        guard state == .connected || state == .synSent, !finQueued else { return }
        sendBuffer.append(contentsOf: data)
    }

    /// Мягкое закрытие: досылаем очередь и отправляем FIN.
    func close(now: UInt64) {
        switch state {
        case .synSent:
            state = .closed
        case .connected:
            finQueued = true
            closeDeadline = now + 10_000_000
            flush(now: now)
        case .finSent, .closed:
            break
        }
        onData = nil
        onClosed = nil
        onConnected = nil
    }

    func receive(_ h: UTPHeader, payload: ArraySlice<UInt8>, now: UInt64) {
        guard state != .closed else { return }
        lastReceived = now
        replyMicro = UInt32(truncatingIfNeeded: now) &- h.timestamp
        peerWindow = Int(h.window)

        switch h.type {
        case .reset:
            terminate(PeerError("uTP: соединение сброшено"))
            return
        case .syn:
            if state == .connected, h.seq == ackNr { synAckPending = true } // повтор SYN: наш ответ потерялся
            return
        default:
            break
        }

        if state == .synSent {
            // Начальный номер встречного потока знаем только из ответа на SYN (ST_STATE): данные,
            // обогнавшие его, отбрасываем — их пришлют повторно.
            guard h.type == .state, h.ack == 1 else { return }
            state = .connected
            ackNr = h.seq &- 1
            timeouts = 0
            let callback = onConnected
            onConnected = nil
            callback?(nil)
        }

        processAck(h, now: now)

        if h.type == .data || h.type == .fin {
            if h.type == .fin { eof = h.seq }
            let seq = h.seq
            if seq == ackNr &+ 1 {
                deliver(payload)
                ackNr = seq
                while let next = reorder.removeValue(forKey: ackNr &+ 1) {
                    reorderBytes -= next.count
                    deliver(next[...])
                    ackNr &+= 1
                }
            } else if utpBefore(ackNr, seq), seq &- ackNr < 2048, reorder[seq] == nil {
                reorder[seq] = Array(payload)
                reorderBytes += payload.count
            }
            ackPending = true
            if let eof, eof == ackNr {
                sendAck(now: now)
                terminate(nil)
            }
        }
    }

    func tick(now: UInt64) {
        switch state {
        case .closed:
            return
        case .synSent:
            if now > synSentAt + UInt64(timeoutMs * 1000) {
                synRetries += 1
                guard synRetries <= 3 else {
                    terminate(PeerError("uTP: пир не ответил"))
                    return
                }
                timeoutMs *= 2
                connect(now: now)
            }
            return
        case .connected, .finSent:
            break
        }
        if let closeDeadline, now > closeDeadline {
            state = .closed
            return
        }
        if outCount > 0, now >= timeoutAt {
            timeouts += 1
            guard timeouts <= 7 else {
                terminate(PeerError("uTP: пир пропал"))
                return
            }
            var seq = oldest
            while seq != seqNr {
                let i = index(seq)
                if var p = out[i], p.seq == seq, p.inFlight {
                    p.inFlight = false
                    curWindow -= p.payload.count
                    out[i] = p
                }
                seq &+= 1
            }
            maxWindow = Double(UTPSocket.mss)
            slowStart = false
            timeoutMs = min(timeoutMs * 2, 30_000)
            timeoutAt = now + UInt64(timeoutMs * 1000)
        }
        if now - lastReceived > 120_000_000 {
            terminate(PeerError("uTP: нет ответа"))
            return
        }
        if state == .connected, now - lastSent > 25_000_000 { ackPending = true }
        flush(now: now)
    }

    // MARK: - Отправка

    func flush(now: UInt64) {
        guard state == .connected || state == .finSent else { return }
        let window = max(Int(min(maxWindow, Double(peerWindow))), 0)
        var sentData = false
        // Ответ на SYN всегда уходит первым и с начальным номером: по нему инициатор
        // узнаёт, с какого номера пойдут наши данные.
        if synAckPending {
            synAckPending = false
            sendAck(now: now, seq: initialSeq)
        }

        // Сначала повторы потерянного.
        if curWindow < window || curWindow == 0, outCount > 0 {
            var seq = oldest
            while seq != seqNr {
                let i = index(seq)
                if let p = out[i], p.seq == seq, !p.inFlight {
                    if curWindow > 0 && curWindow + p.payload.count > window { break }
                    transmit(i, now: now)
                    sentData = true
                }
                seq &+= 1
            }
        }

        // Новые данные.
        while state == .connected, sendHead < sendBuffer.count, outCount < UTPSocket.maxOutstanding {
            let size = min(UTPSocket.mss, sendBuffer.count - sendHead)
            if curWindow > 0 && curWindow + size > window { break }
            let payload = Array(sendBuffer[sendHead..<(sendHead + size)])
            sendHead += size
            enqueue(.data, payload, now: now)
            sentData = true
        }
        if sendHead > 1 << 16 || sendHead == sendBuffer.count {
            sendBuffer.removeFirst(sendHead)
            sendHead = 0
        }

        if finQueued, state == .connected, sendHead == sendBuffer.count {
            finQueued = false
            state = .finSent
            enqueue(.fin, [], now: now)
            sentData = true
        }

        if ackPending, !sentData { sendAck(now: now) }
    }

    private func enqueue(_ type: UTPType, _ payload: [UInt8], now: UInt64) {
        let i = index(seqNr)
        if outCount == 0 { timeoutAt = now + UInt64(timeoutMs * 1000) }
        out[i] = OutPacket(seq: seqNr, type: type, payload: payload, sentAt: now, transmissions: 0, inFlight: false)
        outCount += 1
        seqNr &+= 1
        transmit(i, now: now)
    }

    private func transmit(_ i: Int, now: UInt64) {
        guard var p = out[i] else { return }
        if p.transmissions > 0 { resent += 1 }
        p.transmissions += 1
        p.sentAt = now
        if !p.inFlight {
            p.inFlight = true
            curWindow += p.payload.count
        }
        out[i] = p
        emit(header(p.type, seq: p.seq, now: now).encode(p.payload[...]))
        lastSent = now
        ackPending = false
    }

    private func sendAck(now: UInt64, seq: UInt16? = nil) {
        var h = header(.state, seq: seq ?? seqNr, now: now)
        if !reorder.isEmpty {
            var mask = [UInt8](repeating: 0, count: 4)
            for seq in reorder.keys {
                let bit = Int(seq &- ackNr &- 2)
                guard bit >= 0, bit < 256 else { continue }
                while mask.count * 8 <= bit { mask += [0, 0, 0, 0] }
                mask[bit / 8] |= 1 << UInt8(bit % 8)
            }
            h.sack = mask
        }
        emit(h.encode())
        lastSent = now
        ackPending = false
    }

    // MARK: - Подтверждения и перегрузка

    private func acknowledge(_ i: Int, now: UInt64) -> Int {
        guard let p = out[i] else { return 0 }
        out[i] = nil
        outCount -= 1
        if p.inFlight { curWindow -= p.payload.count }
        if p.transmissions == 1 { sampleRTT(Double(now &- p.sentAt) / 1000) }
        return p.payload.count
    }

    private func processAck(_ h: UTPHeader, now: UInt64) {
        var acked = 0
        let ack = h.ack
        if outCount > 0, !utpBefore(ack, oldest), utpBefore(ack, seqNr) {
            while !utpBefore(ack, oldest) && oldest != seqNr {
                let i = index(oldest)
                if let p = out[i], p.seq == oldest { acked += acknowledge(i, now: now) }
                oldest &+= 1
            }
            dupAcks = 0
            if let r = recoveryUntil, !utpBefore(ack, r) { recoveryUntil = nil }
        } else if outCount > 0, ack == oldest &- 1, h.type == .state {
            dupAcks += 1
            if dupAcks == 3 { fastRetransmit(now: now) }
        }
        if let sack = h.sack, outCount > 0 {
            var sacked = 0
            for byte in 0..<sack.count {
                for bit in 0..<8 where sack[byte] >> UInt8(bit) & 1 == 1 {
                    let seq = ack &+ 2 &+ UInt16(byte * 8 + bit)
                    guard utpBefore(seq, seqNr) else { continue }
                    let i = index(seq)
                    if let p = out[i], p.seq == seq { acked += acknowledge(i, now: now) }
                    sacked += 1
                }
            }
            if sacked >= 3 { fastRetransmit(now: now) }
        }
        // Пропускаем уже подтверждённые выборочно пакеты в начале окна.
        while oldest != seqNr, out[index(oldest)] == nil || out[index(oldest)]?.seq != oldest { oldest &+= 1 }

        if acked > 0 {
            updateWindow(acked: acked, delay: h.timestampDiff, now: now)
            timeouts = 0
            timeoutAt = now + UInt64(timeoutMs * 1000)
        }
        if state == .finSent, outCount == 0 { state = .closed }
    }

    private func fastRetransmit(now: UInt64) {
        let i = index(oldest)
        guard let p = out[i], p.seq == oldest else { return }
        if recoveryUntil == nil {
            maxWindow = max(Double(UTPSocket.mss), maxWindow * 0.5)
            slowStart = false
            recoveryUntil = seqNr &- 1
        }
        if p.inFlight, now &- p.sentAt < UInt64(max(rtt, 5) * 1000) { return }
        transmit(i, now: now)
    }

    private func sampleRTT(_ ms: Double) {
        if rtt == 0 {
            rtt = ms
            rttVar = ms / 2
        } else {
            let delta = rtt - ms
            rttVar += (abs(delta) - rttVar) / 4
            rtt += (ms - rtt) / 8
        }
        timeoutMs = max(rtt + rttVar * 4, 500)
    }

    /// LEDBAT: окно растёт, пока задержка очереди ниже 100 мс, и сжимается, когда выше.
    private func updateWindow(acked: Int, delay: UInt32, now: UInt64) {
        var ourDelay = 0.0
        if delay != 0 {
            if now > baseSwitch {
                basePrevious = baseCurrent
                baseCurrent = nil
                baseSwitch = now + 60_000_000
            }
            func lower(_ a: UInt32?, _ b: UInt32) -> UInt32 { a.map { Int32(bitPattern: b &- $0) < 0 ? b : $0 } ?? b }
            baseCurrent = lower(baseCurrent, delay)
            let base = basePrevious.map { lower($0, baseCurrent!) } ?? baseCurrent!
            ourDelay = Double(max(0, Int32(bitPattern: delay &- base)))
        }
        if slowStart {
            if ourDelay > UTPSocket.target * 0.9 { slowStart = false } else { maxWindow += Double(acked) }
        }
        if !slowStart {
            let offTarget = (UTPSocket.target - ourDelay) / UTPSocket.target
            let windowFactor = min(Double(acked), maxWindow) / max(maxWindow, Double(acked))
            maxWindow += 3000 * offTarget * windowFactor
        }
        maxWindow = min(max(maxWindow, Double(UTPSocket.mss)), 4 * 1024 * 1024)
    }

    // MARK: - Приём

    private func deliver(_ payload: ArraySlice<UInt8>) {
        guard !payload.isEmpty else { return }
        let data = Data(payload)
        if let onData { onData(data) } else { pending.append(data) }
    }

    private func terminate(_ error: (any Error)?) {
        guard state != .closed else { return }
        state = .closed
        let connected = onConnected
        let closed = onClosed
        onConnected = nil
        onClosed = nil
        onData = nil
        if let connected { connected(error ?? PeerError("uTP: соединение закрыто")) } else { closed?(error) }
    }
}

/// Все uTP-соединения на общем UDP-порту (тот же порт, что у DHT).
nonisolated final class UTPManager: @unchecked Sendable {
    struct Key: Hashable {
        let address: UDPAddress
        let id: UInt16
    }

    let queue: DispatchQueue
    private let output: @Sendable ([UInt8], UDPAddress) -> Void
    // Ниже — только на `queue`.
    private var sockets: [Key: UTPSocket] = [:]
    private var dirty: Set<Key> = []
    private var flushScheduled = false
    private var timer: DispatchSourceTimer?
    var onAccept: (@Sendable (UTPTransport) -> Void)?
    var acceptFilter: (@Sendable (UDPAddress) -> Bool)?

    init(queue: DispatchQueue, output: @escaping @Sendable ([UInt8], UDPAddress) -> Void) {
        self.queue = queue
        self.output = output
    }

    func startTimer() {
        queue.async {
            guard self.timer == nil else { return }
            let t = DispatchSource.makeTimerSource(queue: self.queue)
            t.schedule(deadline: .now() + .milliseconds(50), repeating: .milliseconds(50), leeway: .milliseconds(10))
            t.setEventHandler { [weak self] in self?.tickAll() }
            t.resume()
            self.timer = t
        }
    }

    func stop() {
        queue.async {
            self.timer?.cancel()
            self.timer = nil
        }
    }

    var connectionCount: Int { queue.sync { sockets.values.filter { !$0.isClosed }.count } }

    /// Новое исходящее соединение (устанавливается при `start` транспорта).
    func connect(to address: UDPAddress) -> UTPTransport {
        UTPTransport(manager: self, remote: address, incoming: nil)
    }

    // MARK: - На очереди

    func register(outgoing remote: UDPAddress) -> UTPSocket {
        var id = UInt16.random(in: 0...UInt16.max)
        while sockets[Key(address: remote, id: id)] != nil || sockets[Key(address: remote, id: id &+ 1)] != nil {
            id = UInt16.random(in: 0...UInt16.max)
        }
        let socket = UTPSocket(connectTo: remote, id: id, now: UTPClock.now())
        attach(socket)
        socket.connect(now: UTPClock.now())
        return socket
    }

    private func attach(_ socket: UTPSocket) {
        let remote = socket.remote
        let output = self.output
        socket.emit = { bytes in output(bytes, remote) }
        sockets[Key(address: remote, id: socket.recvID)] = socket
    }

    func markDirty(_ socket: UTPSocket) {
        dirty.insert(Key(address: socket.remote, id: socket.recvID))
        guard !flushScheduled else { return }
        flushScheduled = true
        queue.async { self.endBatch() }
    }

    /// Пакет с общего UDP-сокета.
    func receive(_ bytes: [UInt8], from: UDPAddress) {
        guard let (h, offset) = UTPHeader.parse(bytes) else { return }
        let now = UTPClock.now()
        if h.type == .syn {
            let key = Key(address: from, id: h.connID &+ 1)
            if let existing = sockets[key] {
                existing.receive(h, payload: [], now: now)
                dirty.insert(key)
                return
            }
            guard acceptFilter?(from) ?? false, sockets.count < 2000 else { return }
            let socket = UTPSocket(accepting: h, from: from, now: now)
            attach(socket)
            dirty.insert(key)
            onAccept?(UTPTransport(manager: self, remote: from, incoming: socket))
            return
        }
        let key = Key(address: from, id: h.connID)
        guard let socket = sockets[key] else { return }
        socket.receive(h, payload: bytes[offset...], now: now)
        dirty.insert(key)
    }

    /// Конец пачки входящих пакетов: отправляем накопленные подтверждения и данные.
    func endBatch() {
        flushScheduled = false
        let now = UTPClock.now()
        for key in dirty {
            guard let s = sockets[key] else { continue }
            s.flush(now: now)
            if s.isClosed, s.outCount == 0 { sockets[key] = nil }
        }
        dirty.removeAll()
    }

    private func tickAll() {
        let now = UTPClock.now()
        for (key, s) in sockets {
            s.tick(now: now)
            if s.isClosed { sockets[key] = nil }
        }
    }
}

/// uTP как транспорт для PeerConnection.
nonisolated final class UTPTransport: PeerTransport, @unchecked Sendable {
    let kind = TransportKind.utp
    let remote: UDPAddress
    private unowned let manager: UTPManager
    private var socket: UTPSocket?

    fileprivate init(manager: UTPManager, remote: UDPAddress, incoming: UTPSocket?) {
        self.manager = manager
        self.remote = remote
        socket = incoming
    }

    func start(onReady: @escaping @Sendable ((any Error)?) -> Void,
               onData: @escaping @Sendable (Data) -> Void,
               onClosed: @escaping @Sendable ((any Error)?) -> Void) {
        manager.queue.async {
            let s: UTPSocket
            if let existing = self.socket {
                s = existing
                s.onClosed = onClosed
                s.onData = onData
                onReady(s.isClosed ? PeerError("uTP: соединение закрыто") : nil)
            } else {
                s = self.manager.register(outgoing: self.remote)
                self.socket = s
                s.onConnected = onReady
                s.onClosed = onClosed
                s.onData = onData
            }
        }
    }

    func send(_ data: Data) {
        manager.queue.async {
            guard let s = self.socket else { return }
            s.write(data)
            self.manager.markDirty(s)
        }
    }

    func close() {
        manager.queue.async {
            guard let s = self.socket else { return }
            s.close(now: UTPClock.now())
            self.manager.markDirty(s)
        }
    }
}
