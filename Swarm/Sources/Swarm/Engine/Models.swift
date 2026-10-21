import Foundation
import os

nonisolated enum TorrentStatus: String, Sendable, Codable {
    case checking, metadata, downloading, seeding, paused, finished, error

    var title: String {
        switch self {
        case .checking: "Проверка"
        case .metadata: "Метаданные"
        case .downloading: "Загрузка"
        case .seeding: "Раздача"
        case .paused: "Пауза"
        case .finished: "Готово"
        case .error: "Ошибка"
        }
    }

    var isActive: Bool { self == .downloading || self == .seeding || self == .metadata || self == .checking }
}

nonisolated enum FilePriority: Int, Sendable, Codable, CaseIterable {
    case skip = 0, normal = 1, high = 2

    var title: String {
        switch self {
        case .skip: "Не скачивать"
        case .normal: "Обычный"
        case .high: "Высокий"
        }
    }
}

nonisolated enum PeerSource: String, Sendable, Codable {
    case tracker, dht, pex, incoming, manual

    var title: String {
        switch self {
        case .tracker: "Трекер"
        case .dht: "DHT"
        case .pex: "PEX"
        case .incoming: "Входящий"
        case .manual: "Вручную"
        }
    }
}

nonisolated struct PeerSnapshot: Sendable, Identifiable, Hashable {
    var id: String { address }
    var address: String
    var client: String
    var progress: Double
    var downRate: Double
    var upRate: Double
    var isSeed: Bool
    var source: PeerSource
    var isIncoming: Bool
    var isChokingUs: Bool
    var isInterestedInUs: Bool
    /// Устойчивый угол для визуализации роя.
    var angle: Double
}

nonisolated struct TrackerSnapshot: Sendable, Identifiable, Hashable {
    var id: String { url }
    var url: String
    var status: String
    var seeders: Int?
    var leechers: Int?
    var lastPeers: Int
    var nextAnnounce: Date?
}

nonisolated struct FileSnapshot: Sendable, Identifiable, Hashable {
    var id: Int
    var path: String
    var size: Int64
    var progress: Double
    var priority: FilePriority
    var localURL: URL
}

nonisolated struct TorrentSnapshot: Sendable, Identifiable {
    var id: String
    var name: String
    var status: TorrentStatus
    var error: String?
    var progress: Double
    var checkProgress: Double?
    var metadataProgress: Double?
    var totalSize: Int64
    var wantedSize: Int64
    var doneSize: Int64
    var downRate: Double
    var upRate: Double
    var downloadedTotal: Int64
    var uploadedTotal: Int64
    var peersConnected: Int
    var seedsConnected: Int
    var peersKnown: Int
    var pieceCount: Int
    var pieceLength: Int
    var havePieces: Data
    var inProgressPieces: [Int]
    var recentPieces: [Int: Date]
    var files: [FileSnapshot]
    var peers: [PeerSnapshot]
    var trackers: [TrackerSnapshot]
    var savePath: URL
    var contentURL: URL?
    var isSequential: Bool
    var addedAt: Date
    var completedAt: Date?
    var magnet: String
    var isPrivate: Bool

    var ratio: Double { downloadedTotal > 0 ? Double(uploadedTotal) / Double(downloadedTotal) : 0 }
    var eta: TimeInterval? {
        guard status == .downloading, downRate > 1024 else { return nil }
        return Double(max(0, wantedSize - doneSize)) / downRate
    }
}

/// То, что сохраняется между запусками.
nonisolated struct ResumeData: Codable, Sendable {
    var infoHash: String
    var name: String
    var magnet: String?
    var savePath: String
    var have: Data?
    var priorities: [Int]
    var sequential: Bool
    var paused: Bool
    var uploaded: Int64
    var downloaded: Int64
    var addedAt: Date
    var completedAt: Date?
}

nonisolated struct EngineSettings: Codable, Sendable, Equatable {
    var maxPeersPerTorrent = 60
    var downloadLimit = 0      // байт/с, 0 — без ограничения
    var uploadLimit = 0
    var seedRatioLimit = 0.0   // 0 — раздавать всегда
    var dhtEnabled = true
    var trackersEnabled = true
}

/// Глобальные ограничения скорости: корзина жетонов, пополняется каждые 250 мс.
nonisolated final class RateLimiter: Sendable {
    private struct State {
        var downTokens = 0
        var upTokens = 0
        var downLimit = 0
        var upLimit = 0
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    func configure(down: Int, up: Int) {
        state.withLock { $0.downLimit = down; $0.upLimit = up }
    }

    func refill(interval: TimeInterval) {
        state.withLock { s in
            if s.downLimit > 0 { s.downTokens = min(s.downLimit, s.downTokens + Int(Double(s.downLimit) * interval)) }
            if s.upLimit > 0 { s.upTokens = min(s.upLimit, s.upTokens + Int(Double(s.upLimit) * interval)) }
        }
    }

    func takeDown(_ n: Int) -> Bool {
        state.withLock { s in
            guard s.downLimit > 0 else { return true }
            guard s.downTokens >= n else { return false }
            s.downTokens -= n
            return true
        }
    }

    func refundDown(_ n: Int) { state.withLock { if $0.downLimit > 0 { $0.downTokens += n } } }

    func takeUp(_ n: Int) -> Bool {
        state.withLock { s in
            guard s.upLimit > 0 else { return true }
            guard s.upTokens >= n else { return false }
            s.upTokens -= n
            return true
        }
    }
}

/// Скользящая скорость (экспоненциальное сглаживание).
nonisolated struct RateMeter: Sendable {
    private(set) var rate: Double = 0
    private var accumulated = 0

    mutating func add(_ bytes: Int) { accumulated += bytes }

    mutating func tick(_ dt: TimeInterval) {
        let instant = Double(accumulated) / max(dt, 0.05)
        rate = rate * 0.65 + instant * 0.35
        if rate < 1 { rate = 0 }
        accumulated = 0
    }
}

nonisolated extension Data {
    var base16: String { map { String(format: "%02x", $0) }.joined() }
}
