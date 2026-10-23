import CryptoKit
import Foundation
import Network
import WebKit

/// Прокси для окон: весь трафик страниц (и запросы адресов сайтов, DNS) идёт через локальный SOCKS5 —
/// например, через Veil. Если прокси недоступен, страницы не откроются: Ward не уходит в обход.
enum NetworkPrivacy {
    static func usesProxy(_ prefs: Prefs, isPrivate: Bool) -> Bool {
        switch prefs.proxyMode {
        case .off: return false
        case .all: return true
        case .privateOnly: return isPrivate
        }
    }

    static func apply(to store: WKWebsiteDataStore, isPrivate: Bool, prefs: Prefs) {
        guard usesProxy(prefs, isPrivate: isPrivate), let port = NWEndpoint.Port(rawValue: UInt16(clamping: prefs.proxyPort)) else {
            store.proxyConfigurations = []
            return
        }
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(prefs.proxyHost), port: port)
        store.proxyConfigurations = [ProxyConfiguration(socksv5Proxy: endpoint)]
    }

    /// Сессия для собственных запросов Ward (SponsorBlock) — через тот же прокси, если он включён для всех окон.
    static func session(_ prefs: Prefs) -> URLSession {
        guard prefs.proxyMode == .all else { return .shared }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.connectionProxyDictionary = [
            kCFNetworkProxiesSOCKSEnable as String: 1,
            kCFNetworkProxiesSOCKSProxy as String: prefs.proxyHost,
            kCFNetworkProxiesSOCKSPort as String: prefs.proxyPort,
        ]
        return URLSession(configuration: configuration)
    }

    /// Отвечает ли прокси: просто TCP-соединение с портом.
    static func check(host: String, port: Int) async -> Bool {
        guard let p = NWEndpoint.Port(rawValue: UInt16(clamping: port)) else { return false }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: p, using: .tcp)
        let box = OnceBox()
        let ok: Bool = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            box.set(c)
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: box.resume(true)
                case .failed, .cancelled: box.resume(false)
                case .waiting: box.resume(false)
                default: break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 3) { box.resume(false) }
        }
        connection.cancel()
        return ok
    }

    nonisolated final class OnceBox: @unchecked Sendable {
        private let lock = NSLock()
        private var continuation: CheckedContinuation<Bool, Never>?
        func set(_ c: CheckedContinuation<Bool, Never>) { lock.withLock { continuation = c } }
        func resume(_ value: Bool) {
            let c = lock.withLock { () -> CheckedContinuation<Bool, Never>? in
                let c = continuation
                continuation = nil
                return c
            }
            c?.resume(returning: value)
        }
    }
}

/// Скрытые трекеры: «metrics.site.ru» выглядит как часть сайта, а в DNS это псевдоним (CNAME) счётчика
/// Adobe, Eulerian или Criteo. Такой адрес проходит мимо списков, где записан только настоящий домен трекера.
/// Ward спрашивает у системы настоящее имя поддомена и, если это трекер, блокирует поддомен дальше.
@Observable
final class CNAMEGuard {
    /// Найденные скрытые трекеры: поддомен → настоящее имя.
    private(set) var cloaked: [String: String] = Storage.load([String: String].self, "cname.json") ?? [:]
    private(set) var ruleList: WKContentRuleList?
    @ObservationIgnored private var checked: Set<String> = []
    @ObservationIgnored private var compileTask: Task<Void, Never>?
    var onChange: (() -> Void)?

    nonisolated static func canonicalName(_ host: String) -> String? {
        var hints = addrinfo()
        hints.ai_flags = AI_CANONNAME
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let first = result else { return nil }
        defer { freeaddrinfo(result) }
        guard let raw = first.pointee.ai_canonname else { return nil }
        var name = String(cString: raw).lowercased()
        if name.hasSuffix(".") { name.removeLast() }
        return name == host.lowercased() ? nil : name
    }

    /// Проверить поддомен сайта. `report` — вызывается, если это скрытый трекер.
    func inspect(_ host: String, page: String, index: ProtectionIndex, report: @escaping (String, TrackerKind) -> Void) {
        let h = host.lowercased()
        if let target = cloaked[h] {
            report(target, index.classify(target, page: page) ?? .tracking)
            return
        }
        guard checked.insert(h).inserted, checked.count < 5000 else { return }
        Task {
            let target = await Task.detached { CNAMEGuard.canonicalName(h) }.value
            guard let target, let kind = index.classify(target, page: page) else { return }
            cloaked[h] = target
            Storage.save(cloaked, "cname.json")
            report(target, kind)
            scheduleCompile()
        }
    }

    func forgetAll() {
        cloaked = [:]
        Storage.remove("cname.json")
        scheduleCompile()
    }

    /// Правила блокировки для найденных поддоменов (собираются отдельно, за доли секунды).
    func scheduleCompile(store: WKContentRuleListStore? = nil) {
        compileTask?.cancel()
        compileTask = Task {
            try? await Task.sleep(for: .seconds(1))
            if Task.isCancelled { return }
            await compile(store: store ?? AppModel.shared.blocker.store)
        }
    }

    func compile(store: WKContentRuleListStore) async {
        let rules: [[String: Any]] = cloaked.keys.sorted().compactMap { host in
            FilterParser.regex("||\(host)^").map { ["trigger": ["url-filter": $0], "action": ["type": "block"]] }
        }
        guard !rules.isEmpty else { ruleList = nil; onChange?(); return }
        let json = RuleCompiler.encode(rules)
        if case .success(let list) = await AppModel.shared.blocker.compile("ward-cname", json) {
            ruleList = list
            onChange?()
        }
    }
}

/// SponsorBlock: отрезки роликов YouTube из открытой базы sponsor.ajay.app (данные — CC BY-NC-SA 4.0).
/// Запрос идёт по первым четырём знакам SHA-256 от id ролика: в ответ приходят отрезки многих роликов,
/// и сервер не узнаёт, какой смотрите вы. Ward ничего не отправляет обратно (ни просмотров, ни голосов).
final class SponsorBlockClient {
    static let shared = SponsorBlockClient()
    private var cache: [String: (date: Date, segments: [[String: Any]])] = [:]

    nonisolated static func prefix(_ videoID: String) -> String {
        SHA256.hash(data: Data(videoID.utf8)).map { String(format: "%02x", $0) }.joined().prefix(4).description
    }

    func segments(videoID: String, prefs: Prefs) async -> [[String: Any]] {
        guard videoID.count == 11, videoID.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }) else { return [] }
        if let hit = cache[videoID], Date().timeIntervalSince(hit.date) < 1800 { return hit.segments }
        let categories = prefs.sponsorCategories.sorted()
        guard !categories.isEmpty,
              let catData = try? JSONSerialization.data(withJSONObject: categories),
              var comps = URLComponents(string: "https://sponsor.ajay.app/api/skipSegments/\(Self.prefix(videoID))") else { return [] }
        comps.queryItems = [
            URLQueryItem(name: "categories", value: String(decoding: catData, as: UTF8.self)),
            URLQueryItem(name: "actionTypes", value: #"["skip","mute","full"]"#),
        ]
        guard let url = comps.url else { return [] }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        request.setValue("Ward", forHTTPHeaderField: "User-Agent")
        guard let reply = try? await NetworkPrivacy.session(prefs).data(for: request),
              let http = reply.1 as? HTTPURLResponse else { return [] }
        let data = reply.0
        var result: [[String: Any]] = []
        if http.statusCode == 200, let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
           let video = list.first(where: { ($0["videoID"] as? String) == videoID }),
           let segments = video["segments"] as? [[String: Any]] {
            for s in segments {
                guard let range = s["segment"] as? [Double], range.count == 2, let category = s["category"] as? String,
                      prefs.sponsorCategories.contains(category) else { continue }
                result.append([
                    "segment": range, "category": category,
                    "actionType": (s["actionType"] as? String) ?? "skip", "UUID": (s["UUID"] as? String) ?? UUID().uuidString,
                ])
            }
        }
        cache[videoID] = (Date(), result)
        if cache.count > 300 { cache.removeAll() }
        return result
    }
}
