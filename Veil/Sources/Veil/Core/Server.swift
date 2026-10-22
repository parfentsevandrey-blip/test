import CryptoKit
import Foundation

nonisolated enum Proto: String, Codable, Sendable, CaseIterable {
    case vless, vmess, trojan, shadowsocks, socks, hysteria2
    /// Готовая конфигурация из JSON-подписки (outbound как есть).
    case json

    var title: String {
        switch self {
        case .vless: "VLESS"
        case .vmess: "VMess"
        case .trojan: "Trojan"
        case .shadowsocks: "Shadowsocks"
        case .socks: "SOCKS5"
        case .hysteria2: "Hysteria 2"
        case .json: "Xray JSON"
        }
    }
}

/// Всё, что нужно ядру, чтобы подключиться к серверу. Поля названы по смыслу; соответствие параметрам
/// ссылок и полям конфигурации Xray — в `Links` и `XrayConfig`.
nonisolated struct Endpoint: Codable, Hashable, Sendable {
    var proto: Proto
    var address = ""
    var port = 0

    // Учётные данные
    var id = ""
    var password = ""
    var user = ""
    /// Шифр Shadowsocks или способ шифрования VMess.
    var method = ""
    var flow = ""
    var encryption = "none"

    // Транспорт: raw, ws, grpc, xhttp, httpupgrade, kcp, hysteria
    var network = "raw"
    var headerType = ""
    var host = ""
    var path = ""
    var serviceName = ""
    var authority = ""
    var mode = ""
    /// Дополнительные параметры xhttp (JSON).
    var extra = ""
    var seed = ""

    // Защита: none, tls, reality
    var security = "none"
    var sni = ""
    var alpn: [String] = []
    var fingerprint = ""
    var publicKey = ""
    var shortId = ""
    var spiderX = ""
    var mldsa65Verify = ""
    /// SHA-256 сертификата сервера (вместо отключения проверки, которое Xray больше не поддерживает).
    var pinnedCert = ""
    var verifyName = ""
    var echConfig = ""
    /// В ссылке было «не проверять сертификат».
    var insecure = false

    // Hysteria 2
    var obfs = ""
    var obfsPassword = ""
    var hopPorts = ""
    var hopInterval = ""

    /// Фрагментация TLS ClientHello из ссылки Happ: «длина,интервал,пакеты[,maxSplit]».
    var fragment = ""

    /// Outbound из JSON-подписки — используется как есть.
    var rawOutbound: String?

    init(proto: Proto) { self.proto = proto }

    var transportTitle: String {
        if proto == .hysteria2 { return "QUIC" }
        switch network {
        case "raw", "tcp": return security == "reality" ? "Reality" : "TCP"
        case "ws": return "WebSocket"
        case "grpc": return "gRPC"
        case "xhttp": return "XHTTP"
        case "httpupgrade": return "HTTPUpgrade"
        case "kcp": return "mKCP"
        default: return network.uppercased()
        }
    }

    /// Короткое описание: «VLESS · Reality · Vision».
    var summary: String {
        var parts = [proto.title]
        if proto == .json { return proto.title }
        if security == "reality" { parts.append(network == "raw" ? "Reality" : "\(transportTitle) + Reality") }
        else if network != "raw" || proto == .hysteria2 { parts.append(transportTitle) }
        if security == "tls" && proto != .hysteria2 { parts.append("TLS") }
        if flow.contains("vision") { parts.append("Vision") }
        if !obfs.isEmpty { parts.append(obfs.capitalized) }
        return parts.joined(separator: " · ")
    }
}

nonisolated struct Server: Codable, Hashable, Identifiable, Sendable {
    /// Стабильный: не меняется при обновлении подписки, если не изменилась сама ссылка.
    var id: String
    var name: String
    var endpoint: Endpoint
    /// Исходная ссылка — для «Поделиться» и QR-кода.
    var link: String
    var subscriptionID: String?
    var note: String?
    /// Почему ядро не сможет подключиться (устаревший транспорт, плагин…), если известно заранее.
    var issue: String?
    /// Предупреждение, с которым подключиться можно.
    var warning: String?

    var flag: String {
        let lead = Flags.leading(name)
        if !lead.flag.isEmpty { return lead.flag }
        return Flags.guess(name).map(Flags.emoji) ?? ""
    }

    var title: String { Flags.leading(name).rest }

    static func stableID(_ link: String) -> String {
        let base = link.split(separator: "#", maxSplits: 1).first.map(String.init) ?? link
        return SHA256.hash(data: Data(base.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }
}

/// Подписка: ссылка, по которой провайдер отдаёт список серверов и сведения о тарифе.
nonisolated struct Subscription: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var url: String
    var title: String
    var updated: Date?
    var error: String?
    var info = SubscriptionInfo()

    var displayTitle: String {
        if !info.title.isEmpty { return info.title }
        if !title.isEmpty { return title }
        return URL(string: url)?.host ?? "Подписка"
    }
}

/// Сведения из заголовков подписки (формат общий для Happ, v2rayN, Streisand, Hiddify).
nonisolated struct SubscriptionInfo: Codable, Hashable, Sendable {
    var title = ""
    var upload: Int64 = 0
    var download: Int64 = 0
    var total: Int64 = 0
    var expire: Date?
    var updateHours = 0
    var supportURL = ""
    var webPageURL = ""
    var announce = ""
    /// Профиль маршрутизации Happ, присланный подпиской.
    var routing: String?

    var used: Int64 { upload + download }
}
