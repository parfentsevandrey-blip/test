import Foundation

/// How current the cached consensus is. A fresh one means Tor needs no directory work at all;
/// a missing one means it must fetch everything before the first circuit.
struct ConsensusInfo: Equatable, Sendable {
    enum Freshness: String, Sendable { case missing, expired, stale, live, fresh }

    var validAfter: Date
    var freshUntil: Date
    var validUntil: Date

    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()

    /// Reads the three timestamps from a consensus header. They always sit in the first ~400 bytes.
    static func parse(_ header: String) -> ConsensusInfo? {
        guard header.hasPrefix("network-status-version 3") else { return nil }
        var validAfter: Date?
        var freshUntil: Date?
        var validUntil: Date?
        for line in header.split(separator: "\n", omittingEmptySubsequences: true) {
            let parts = line.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true)
            guard parts.count == 2 else { continue }
            let value = formatter.date(from: String(parts[1]).trimmingCharacters(in: .whitespaces))
            switch parts[0] {
            case "valid-after": validAfter = value
            case "fresh-until": freshUntil = value
            case "valid-until": validUntil = value
            default: continue
            }
            if validAfter != nil, freshUntil != nil, validUntil != nil { break }
        }
        guard let validAfter, let freshUntil, let validUntil else { return nil }
        return ConsensusInfo(validAfter: validAfter, freshUntil: freshUntil, validUntil: validUntil)
    }

    func freshness(now: Date) -> Freshness {
        // A consensus from the future means the clock is wrong; never trust it.
        if validAfter > now.addingTimeInterval(300) { return .stale }
        if now < freshUntil { return .fresh }
        if now < validUntil { return .live }
        if now < validUntil.addingTimeInterval(24 * 3600) { return .stale }
        return .expired
    }
}

/// One entry of Tor's guard selection, from the `state` file.
struct BridgeGuard: Equatable, Sendable {
    var address: String
    var fingerprint: String?
    var listed: Bool
    var sampledOn: Date?
    var confirmedOn: Date?
}

/// What `state` says about guards and about Tor's learned circuit-build-time histogram.
struct TorStateSummary: Equatable, Sendable {
    var defaultGuards = 0
    var confirmedDefaultGuards = 0
    var bridgeGuards: [BridgeGuard] = []
    var totalBuildTimes = 0
    var buildTimeBins = 0

    /// Tor needs `cbtmincircs` (100) recorded builds before its adaptive timeout is meaningful.
    var learnedTimeoutReady: Bool { totalBuildTimes >= 100 }

    private static let formatters: [DateFormatter] = {
        ["yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"].map { format in
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = format
            return formatter
        }
    }()

    static func date(_ raw: String) -> Date? {
        for formatter in formatters {
            if let date = formatter.date(from: raw) { return date }
        }
        return nil
    }

    /// Never throws: a truncated or half-written `state` yields zeros and widens every budget.
    static func parse(_ text: String) -> TorStateSummary {
        var summary = TorStateSummary()
        for line in text.split(separator: "\n", omittingEmptySubsequences: true) {
            if line.hasPrefix("Guard ") {
                var fields: [String: String] = [:]
                for token in line.dropFirst("Guard ".count).split(separator: " ", omittingEmptySubsequences: true) {
                    let pair = token.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
                    guard pair.count == 2 else { continue }
                    fields[String(pair[0])] = String(pair[1])
                }
                let listed = fields["listed"] != "0"
                let confirmed = fields["confirmed_on"].flatMap(date)
                if fields["in"] == "bridges" {
                    summary.bridgeGuards.append(BridgeGuard(
                        address: fields["bridge_addr"] ?? "",
                        fingerprint: fields["rsa_id"],
                        listed: listed,
                        sampledOn: fields["sampled_on"].flatMap(date),
                        confirmedOn: confirmed
                    ))
                } else {
                    summary.defaultGuards += 1
                    if listed, confirmed != nil { summary.confirmedDefaultGuards += 1 }
                }
            } else if line.hasPrefix("CircuitBuildTimeBin ") {
                summary.buildTimeBins += 1
            } else if line.hasPrefix("TotalBuildTimes ") {
                summary.totalBuildTimes = Int(line.dropFirst("TotalBuildTimes ".count).trimmingCharacters(in: .whitespaces)) ?? 0
            }
        }
        return summary
    }
}

/// How much of a connection Tor can skip because it already has the answers on disk.
struct WarmthProfile: Equatable, Sendable {
    enum Tier: Int, Comparable, Sendable {
        case cold = 0, cool, warm, hot
        static func < (lhs: Tier, rhs: Tier) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    var tier: Tier = .cold
    var consensus: ConsensusInfo.Freshness = .missing
    var consensusAge: TimeInterval?
    var microdescBytes = 0
    var microdescsUsable = false
    var hasCerts = false
    var state = TorStateSummary()
    var processWarm = false
    /// Transports whose pluggable-transport process Tor has already launched this run.
    var launchedTransports: Set<String> = []
    /// Transports Tor itself confirmed on this machine, most recently confirmed first.
    var evidencedTransports: [AppSettings.Transport] = []

    var guardsKnown: Bool {
        state.confirmedDefaultGuards > 0 || state.bridgeGuards.contains { $0.listed && $0.confirmedOn != nil }
    }

    var summary: String {
        var parts: [String] = []
        switch consensus {
        case .missing: parts.append("no directory")
        case .expired: parts.append("directory expired")
        case .stale: parts.append("directory stale")
        case .live, .fresh:
            if let consensusAge {
                parts.append("directory \(consensus.rawValue) (\(Int(consensusAge / 60)) min)")
            } else {
                parts.append("directory \(consensus.rawValue)")
            }
        }
        parts.append(guardsKnown ? "guard known" : "no confirmed guard")
        if microdescsUsable { parts.append("descriptors cached") }
        if processWarm { parts.append("tor loaded") }
        return parts.joined(separator: " · ")
    }

    /// Monotone by construction: turning any input on never lowers the tier.
    static func tier(consensus: ConsensusInfo.Freshness, microdescsUsable: Bool, hasCerts: Bool,
                     guardsKnown: Bool, processWarm: Bool) -> Tier {
        if processWarm, microdescsUsable, hasCerts, guardsKnown, consensus == .fresh || consensus == .live {
            return .hot
        }
        if microdescsUsable, hasCerts, consensus == .fresh || consensus == .live || consensus == .stale {
            return .warm
        }
        if microdescsUsable || hasCerts || consensus != .missing { return .cool }
        return .cold
    }
}

/// Reads Tor's own DataDirectory to decide how warm the next connect will be, and which transport
/// actually worked here. Strictly better evidence than a remembered setting: it is what Tor used
/// and confirmed, it carries a timestamp, and the app did not write it.
enum TorStateStore {
    static func read(dataDirectory: URL, now: Date = .now, processWarm: Bool = false) -> WarmthProfile {
        var profile = WarmthProfile()
        profile.processWarm = processWarm

        var validAfter: Date?
        if let header = consensusHeader(dataDirectory.appendingPathComponent("cached-microdesc-consensus")),
           let info = ConsensusInfo.parse(header) {
            profile.consensus = info.freshness(now: now)
            profile.consensusAge = now.timeIntervalSince(info.validAfter)
            validAfter = info.validAfter
        }

        let manager = FileManager.default
        var bytes = 0
        var newest: Date?
        for name in ["cached-microdescs", "cached-microdescs.new"] {
            let url = dataDirectory.appendingPathComponent(name)
            guard let attributes = try? manager.attributesOfItem(atPath: url.path) else { continue }
            bytes += (attributes[.size] as? Int) ?? 0
            if let modified = attributes[.modificationDate] as? Date {
                newest = max(newest ?? modified, modified)
            }
        }
        profile.microdescBytes = bytes
        if let certs = try? manager.attributesOfItem(atPath: dataDirectory.appendingPathComponent("cached-certs").path) {
            profile.hasCerts = ((certs[.size] as? Int) ?? 0) > 0
        }
        if bytes >= 2_000_000, let newest, let validAfter {
            // Descriptors older than a day and a half describe a network that has moved on.
            profile.microdescsUsable = abs(newest.timeIntervalSince(validAfter)) <= 36 * 3600
        }

        if let text = try? String(contentsOf: dataDirectory.appendingPathComponent("state"), encoding: .utf8) {
            profile.state = TorStateSummary.parse(text)
        }
        profile.tier = WarmthProfile.tier(
            consensus: profile.consensus,
            microdescsUsable: profile.microdescsUsable,
            hasCerts: profile.hasCerts,
            guardsKnown: profile.guardsKnown,
            processWarm: processWarm
        )
        return profile
    }

    /// The first 4 KB of the consensus: enough for the header, and it never reads the whole file.
    private static func consensusHeader(_ url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        guard let data = try? handle.read(upToCount: 4096), !data.isEmpty else { return nil }
        return String(decoding: data, as: UTF8.self)
    }

    /// Maps confirmed bridge guards back to the transports Veil knows, most recent first.
    static func evidencedTransports(_ summary: TorStateSummary,
                                    defaults: PluggableTransportDefaults,
                                    customBridges: String) -> [AppSettings.Transport] {
        var byEndpoint: [String: AppSettings.Transport] = [:]
        func index(_ lines: [String], as transport: AppSettings.Transport) {
            for line in lines {
                let tokens = line.split(separator: " ", omittingEmptySubsequences: true)
                let position = TorConfiguration.transportName(of: line) == nil ? 0 : 1
                guard tokens.count > position else { continue }
                byEndpoint[String(tokens[position]).lowercased()] = transport
            }
        }
        index(defaults.bridges["snowflake"] ?? [], as: .snowflake)
        index(defaults.bridges["obfs4"] ?? [], as: .obfs4)
        index(defaults.bridges["meek"] ?? [], as: .meek)
        index(TorConfiguration.parseBridgeLines(customBridges), as: .custom)

        var seen: Set<AppSettings.Transport> = []
        var ordered: [AppSettings.Transport] = []
        let confirmed = summary.bridgeGuards
            .filter { $0.listed && $0.confirmedOn != nil }
            .sorted { ($0.confirmedOn ?? .distantPast) > ($1.confirmedOn ?? .distantPast) }
        for guardEntry in confirmed {
            guard let transport = byEndpoint[guardEntry.address.lowercased()], !seen.contains(transport) else { continue }
            seen.insert(transport)
            ordered.append(transport)
        }
        if summary.confirmedDefaultGuards > 0, !seen.contains(.direct) {
            ordered.append(.direct)
        }
        return ordered
    }
}
