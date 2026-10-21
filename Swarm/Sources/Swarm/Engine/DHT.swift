import Foundation

/// Клиент DHT Mainline (BEP 5) в режиме поиска: итеративный get_peers по XOR-метрике.
/// Благодаря ему magnet-ссылки работают даже без трекеров.
actor DHTClient {
    nonisolated struct Node: Sendable, Hashable {
        let id: Data?
        let address: PeerAddress
    }

    nonisolated static let bootstrap: [PeerAddress] = [
        PeerAddress(host: "router.bittorrent.com", port: 6881),
        PeerAddress(host: "dht.transmissionbt.com", port: 6881),
        PeerAddress(host: "router.utorrent.com", port: 6881),
        PeerAddress(host: "dht.libtorrent.org", port: 25401),
    ]

    private let nodeID: Data = Data((0..<20).map { _ in UInt8.random(in: 0...255) })
    /// Узлы, которые недавно отвечали: стартовая точка следующих поисков.
    private var known: [Data: Node] = [:]

    var knownNodes: Int { known.count }

    /// Ищет пиров раздачи. Найденные адреса отдаются по мере появления.
    func findPeers(infoHash: InfoHash, timeBudget: TimeInterval = 40,
                   onPeers: @escaping @Sendable ([PeerAddress]) async -> Void) async {
        let target = infoHash.bytes
        let deadline = Date().addingTimeInterval(timeBudget)
        var candidates: [String: Node] = [:]
        for n in known.values { candidates[n.address.description] = n }
        for b in Self.bootstrap { candidates[b.description] = Node(id: nil, address: b) }
        var queried = Set<String>()
        var responded: [Node] = []
        var foundPeers = Set<PeerAddress>()
        var stagnantRounds = 0

        while Date() < deadline, !Task.isCancelled {
            let batch = candidates.values
                .filter { !queried.contains($0.address.description) }
                .sorted { Self.distance($0.id, target) < Self.distance($1.id, target) }
                .prefix(8)
            if batch.isEmpty { break }
            batch.forEach { queried.insert($0.address.description) }

            let bestBefore = responded.prefix(8).map(\.address)
            let myID = nodeID
            let results = await withTaskGroup(of: (Node, Response?).self) { group in
                for node in batch {
                    group.addTask { (node, await DHTClient.getPeers(node.address, nodeID: myID, infoHash: target)) }
                }
                var out: [(Node, Response?)] = []
                for await r in group { out.append(r) }
                return out
            }
            for (node, response) in results {
                guard let response else { continue }
                let identified = Node(id: response.id, address: node.address)
                responded.append(identified)
                if let id = response.id { known[id] = identified }
                for n in response.nodes where candidates[n.address.description] == nil {
                    candidates[n.address.description] = n
                }
                let fresh = response.peers.filter { foundPeers.insert($0).inserted }
                if !fresh.isEmpty { await onPeers(fresh) }
            }
            responded.sort { Self.distance($0.id, target) < Self.distance($1.id, target) }
            stagnantRounds = responded.prefix(8).map(\.address) == bestBefore ? stagnantRounds + 1 : 0
            if stagnantRounds >= 2 && foundPeers.count >= 20 { break }
        }
        if known.count > 400 {
            let keep = known.values.sorted { Self.distance($0.id, nodeID) < Self.distance($1.id, nodeID) }.prefix(300)
            known = Dictionary(uniqueKeysWithValues: keep.compactMap { n in n.id.map { ($0, n) } })
        }
    }

    nonisolated struct Response: Sendable {
        var id: Data?
        var nodes: [Node]
        var peers: [PeerAddress]
    }

    private nonisolated static func getPeers(_ address: PeerAddress, nodeID: Data, infoHash: Data) async -> Response? {
        let tid = Data([UInt8.random(in: 0...255), UInt8.random(in: 0...255)])
        let query = BValue.dict([
            "t": .bytes(tid),
            "y": .str("q"),
            "q": .str("get_peers"),
            "a": .dict(["id": .bytes(nodeID), "info_hash": .bytes(infoHash)]),
        ]).encoded
        let session = UDPSession(host: address.host, port: address.port)
        defer { session.close() }
        do {
            try await session.start(timeout: 4)
            let raw = try await session.exchange(query, timeout: 4) { data in
                (try? Bencode.decode(data))?["t"]?.data == tid
            }
            let msg = try Bencode.decode(raw)
            guard msg["y"]?.string == "r", let r = msg["r"] else { return nil }
            var nodes: [Node] = []
            if let compact = r["nodes"]?.data {
                let b = [UInt8](compact)
                for start in stride(from: 0, through: b.count - 26, by: 26) where b.count >= 26 {
                    let id = Data(b[start..<start + 20])
                    if let addr = PeerAddress.compact(Data(b[start + 20..<start + 26])).first {
                        nodes.append(Node(id: id, address: addr))
                    }
                }
            }
            let peers = (r["values"]?.list ?? []).flatMap { $0.data.map { PeerAddress.compact($0) } ?? [] }
            return Response(id: r["id"]?.data, nodes: nodes, peers: peers)
        } catch {
            return nil
        }
    }

    /// XOR-расстояние как 20 байт для лексикографического сравнения. Неизвестный id — дальше всех.
    private nonisolated static func distance(_ id: Data?, _ target: Data) -> [UInt8] {
        guard let id, id.count == 20 else { return [UInt8](repeating: 0xFF, count: 21) }
        return zip(id, target).map { $0 ^ $1 }
    }
}

nonisolated extension Array where Element == UInt8 {
    static func < (lhs: [UInt8], rhs: [UInt8]) -> Bool { lhs.lexicographicallyPrecedes(rhs) }
}
