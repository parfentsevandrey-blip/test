import Foundation

// The vocabulary, ported from the Android build so both apps describe the
// same things the same way.

/// One way of hiding that this is Tor.
enum Transport: String, CaseIterable, Codable, Sendable {
    case direct, obfs4, webtunnel, meek, conjure, snowflake

    /// What tor calls it in a `Bridge` line and a `ClientTransportPlugin`.
    var torName: String {
        switch self {
        case .direct: return "direct"
        case .obfs4: return "obfs4"
        case .webtunnel: return "webtunnel"
        case .meek: return "meek_lite"
        case .conjure: return "conjure"
        case .snowflake: return "snowflake"
        }
    }

    var label: String {
        switch self {
        case .direct: return "Прямое"
        case .obfs4: return "obfs4"
        case .webtunnel: return "WebTunnel"
        case .meek: return "meek"
        case .conjure: return "Conjure"
        case .snowflake: return "Snowflake"
        }
    }

    var summary: String {
        switch self {
        case .direct:
            return "Без обфускации. Быстро там, где Tor не блокируют."
        case .obfs4:
            return "Поток без опознаваемой структуры. Мост нужно знать заранее."
        case .webtunnel:
            return "Выглядит как обычный HTTPS-сайт. Мосты выдаются по запросу."
        case .meek:
            return "Прячется за CDN: censor видит обращение к чужому домену."
        case .conjure:
            return "Соединение с адресом, за которым никого нет, пока станция не поднимет его."
        case .snowflake:
            return "WebRTC через браузер добровольца. Ни одного постоянного адреса."
        }
    }

    var symbol: String {
        switch self {
        case .direct: return "arrow.forward"
        case .obfs4: return "shuffle"
        case .webtunnel: return "globe"
        case .meek: return "cloud"
        case .conjure: return "point.3.connected.trianglepath.dotted"
        case .snowflake: return "snowflake"
        }
    }

    /// Whether it is worth offering. A plain connection dies in the TLS
    /// handshake on a censored network and obfs4 reaches its bridge and then
    /// never builds a circuit; offering either costs the user a failed
    /// connect to learn what is already known.
    var isOffered: Bool { self != .direct && self != .obfs4 }

    var isPluggable: Bool { self != .direct }

    /// How long this route gets before we move on, in seconds. The numbers
    /// come from how each behaves: obfs4 either connects quickly or not at
    /// all, whereas Snowflake has to find a volunteer first.
    var budget: TimeInterval {
        switch self {
        case .direct: return 30
        case .obfs4: return 45
        case .webtunnel: return 60
        case .meek: return 75
        case .conjure: return 110
        case .snowflake: return 100
        }
    }

    /// How long a stream is waited for after tor says it is bootstrapped.
    /// Generous, because this is also the window in which a warm reconnect
    /// re-establishes its link.
    var verifyBudget: TimeInterval {
        switch self {
        case .direct: return 20
        case .obfs4: return 30
        case .webtunnel: return 45
        case .meek: return 60
        case .conjure, .snowflake: return 90
        }
    }
}

/// A `Bridge` line, kept as text plus its parsed parameters.
struct BridgeLine: Codable, Hashable, Sendable {
    var raw: String

    var transportName: String? {
        let parts = raw.split(separator: " ").map(String.init)
        guard let first = parts.first else { return nil }
        return first.contains(":") ? nil : first
    }

    var transport: Transport? {
        guard let name = transportName else { return .direct }
        return Transport.allCases.first { $0.torName == name }
    }

    /// `host:port`, which is how tor keys a bridge.
    var endpoint: String? {
        let parts = raw.split(separator: " ").map(String.init)
        return parts.first { $0.contains(":") && !$0.contains("=") }
    }

    var parameters: [String: String] {
        var found: [String: String] = [:]
        for part in raw.split(separator: " ").map(String.init) where part.contains("=") {
            let pieces = part.split(separator: "=", maxSplits: 1).map(String.init)
            if pieces.count == 2 { found[pieces[0]] = pieces[1] }
        }
        return found
    }
}

/// Where tor says it is in becoming usable.
struct Bootstrap: Equatable, Sendable {
    var percent: Int = 0
    var tag: String = ""
    var summary: String = ""
    /// Failures reported *since this route started*, not for the process.
    var problems: Int = 0
    var recommendation: String = ""

    var isDone: Bool { percent >= 100 }

    /// Tor has said, repeatedly and in its own words, that this is failing.
    var isHopeless: Bool { problems >= 3 && recommendation == "warn" }
}

/// What the interface is allowed to say.
///
/// `connected` carries the moment a stream was proved to go through, not the
/// moment tor reported a hundred per cent. The two are not the same and the
/// difference is the whole of what the Android build spent its versions
/// learning.
enum TunnelState: Equatable, Sendable {
    case idle
    case starting(String)
    case bootstrapping(Transport, percent: Int, summary: String)
    case verifying(Transport)
    case connected(Transport, since: Date)
    case stopping
    case failed(String)

    var isBusy: Bool {
        switch self {
        case .starting, .bootstrapping, .verifying, .stopping: return true
        default: return false
        }
    }

    var isLive: Bool {
        if case .connected = self { return true }
        return false
    }

    var tone: StatusToneKind {
        switch self {
        case .idle, .stopping: return .idle
        case .starting, .bootstrapping, .verifying: return .working
        case .connected: return .live
        case .failed: return .failed
        }
    }

    var headline: String {
        switch self {
        case .idle: return "Отключено"
        case .starting(let note): return note
        case .bootstrapping(_, let percent, let summary):
            return summary.isEmpty ? "Подключение \(percent)%" : "\(summary) — \(percent)%"
        case .verifying: return "Проверяю канал"
        case .connected: return "Подключено"
        case .stopping: return "Отключение"
        case .failed: return "Не удалось подключиться"
        }
    }

    var transport: Transport? {
        switch self {
        case .bootstrapping(let t, _, _), .verifying(let t), .connected(let t, _): return t
        default: return nil
        }
    }
}

/// Mirrors StatusTone in the design layer without the model importing SwiftUI.
enum StatusToneKind: Sendable { case idle, working, live, failed }

/// Live counters from the tunnel.
struct TunnelStats: Equatable, Sendable {
    var rxBytes: Int64 = 0
    var txBytes: Int64 = 0
    var tcpOpen: Int64 = 0
    var dnsQueries: Int64 = 0
    var dnsBlocked: Int64 = 0
    var blockedUDP: Int64 = 0
}

/// The last beat of the link pulse.
///
/// The pulse is a small request through the tunnel every twenty seconds. It
/// is both the marker on screen — a tunnel that is up has a round trip
/// measured seconds ago, not a colour — and the supervisor's clock: a path
/// that died while the machine slept is noticed in two beats.
struct PulseState: Equatable, Sendable {
    var rttMillis: Int = 0
    var kilobytesPerSecond: Int = 0
    var measuredAt: Date? = nil
    var failures: Int = 0
    var ok: Bool = false

    var hasMeasurement: Bool { measuredAt != nil }
}

/// What the user chose. Persisted; there is nothing to type in and nothing
/// to sign up for.
struct VeilSettings: Codable, Equatable, Sendable {
    var transport: Transport = .snowflake
    var blockAds: Bool = true
    var pulse: Bool = true
    var killSwitch: Bool = true
    var blockUDP: Bool = true
    /// Names kept off the tunnel, so sites that refuse Tor exits still work.
    /// Empty is how this stays off; there is no separate flag.
    var bypassSuffixes: String = ""
}
