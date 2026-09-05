import Foundation

/// The bridge lines the app ships with.
///
/// Public bridges, which is worth being plain about: they are the first ones a
/// censor enumerates, and the app can also be given private ones. They are
/// enough to connect on most networks and they cost nothing to carry, which is
/// the point — the app works out of the box, with no account and nothing to
/// paste in.
enum Bridges {

    private static var cache: [Transport: [BridgeLine]] = [:]

    static func lines(for transport: Transport) -> [BridgeLine] {
        if let cached = cache[transport] { return cached }
        let all = loadBuiltIn()
        cache = all
        return all[transport] ?? []
    }

    /// How many bridge lines to hand tor for one route.
    ///
    /// More is not better. tor opens a connection to each, and a fan of
    /// near-simultaneous handshakes is one of the things current DPI is
    /// reported to score. For the broker-based transports the line is
    /// configuration rather than an address, so a second one buys nothing.
    static func budget(for transport: Transport) -> Int {
        switch transport {
        case .meek, .snowflake, .conjure: return 1
        case .obfs4, .webtunnel: return 3
        case .direct: return 0
        }
    }

    private static func loadBuiltIn() -> [Transport: [BridgeLine]] {
        guard let url = Bundle.main.url(forResource: "builtin_bridges", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }

        var out: [Transport: [BridgeLine]] = [:]
        for transport in Transport.allCases {
            guard let raw = root[transport.torName] as? [String] else { continue }
            out[transport] = raw.prefix(budget(for: transport)).map { BridgeLine(raw: $0) }
        }
        return out
    }
}

/// The network directory, shipped inside the app.
///
/// The longest part of a first connect is not starting tor or the transports;
/// it is fetching and validating the consensus and the microdescriptors, which
/// on a slow obfuscated path is most of the wait. A snapshot travels in the
/// app so a fresh installation starts with the directory already in hand.
///
/// Planted only when tor has no cache of its own. A running installation has
/// something newer, and overwriting it would trade a working directory for a
/// stale one.
enum DirectorySeed {

    private static let files = [
        "cached-microdesc-consensus", "cached-microdescs", "cached-certs",
    ]

    static func isPresent(in dataDirectory: String) -> Bool {
        let path = dataDirectory + "/cached-microdescs"
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: path),
              let size = attributes[.size] as? Int64 else { return false }
        return size > 0
    }

    static func plantIfNeeded(into dataDirectory: String, log: @escaping (String) -> Void) {
        guard !isPresent(in: dataDirectory) else {
            log("tor has its own directory cache; not planting")
            return
        }
        var planted: [String] = []
        for name in files {
            guard let packed = Bundle.main.url(
                forResource: name, withExtension: "xz", subdirectory: "seed"
            ) else { continue }
            let target = dataDirectory + "/" + name
            do {
                try Core.extractXz(from: packed.path, to: target)
                planted.append(name)
            } catch {
                log("could not unpack \(name): \(error.localizedDescription)")
            }
        }
        // All three or none: a consensus without the certificates that sign it
        // is ignored by tor, and a half-planted directory is worse than none
        // because it looks present.
        if planted.count != files.count {
            for name in planted {
                try? FileManager.default.removeItem(atPath: dataDirectory + "/" + name)
            }
            log("the directory seed was incomplete; starting without it")
        } else {
            log("planted the directory seed; this connect skips the download")
        }
    }
}

/// The ad blocker's list of names.
///
/// Every name anything on the machine resolves goes through the tunnel's own
/// resolver — that is what stops DNS leaking to the local network — so it is
/// also the place to refuse the names advertising and tracking come from. On a
/// path that runs through a volunteer's browser this is not only about
/// banners: a page that loads a dozen trackers over Tor takes many times
/// longer than one that does not.
enum Blocklist {

    /// Unpacks the shipped list if the unpacked copy is missing or older, and
    /// returns the path the tunnel should read.
    static func plant(into container: String) -> String? {
        let target = container + "/blocklist.txt"
        let stampPath = container + "/blocklist.stamp"

        guard let packed = Bundle.main.url(
            forResource: "hosts", withExtension: "xz", subdirectory: "blocklist"
        ), let shipped = Bundle.main.url(
            forResource: "STAMP", withExtension: nil, subdirectory: "blocklist"
        ), let stamp = try? String(contentsOf: shipped, encoding: .utf8) else { return nil }

        let current = try? String(contentsOfFile: stampPath, encoding: .utf8)
        if FileManager.default.fileExists(atPath: target), current == stamp {
            return target
        }
        do {
            try Core.extractXz(from: packed.path, to: target)
            try stamp.write(toFile: stampPath, atomically: true, encoding: .utf8)
            return target
        } catch {
            return nil
        }
    }
}
