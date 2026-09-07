import Foundation

struct UpdateInfo: Equatable, Sendable {
    let version: String
    let tag: String
    let downloadURL: URL
    let pageURL: URL
    let notes: String
}

/// Looks at the GitHub releases of this repository for a newer `veil-v*` tag.
enum UpdateChecker {
    static let repository = "parfentsevandrey-blip/test"
    static let tagPrefix = "veil-v"

    static var currentVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
    }

    static func check() async throws -> UpdateInfo? {
        var request = URLRequest(url: URL(string: "https://api.github.com/repos/\(repository)/releases?per_page=20")!)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let releases = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }

        var best: UpdateInfo?
        for release in releases {
            guard let tag = release["tag_name"] as? String, tag.hasPrefix(tagPrefix),
                  (release["draft"] as? Bool) != true else { continue }
            let version = String(tag.dropFirst(tagPrefix.count))
            guard let assets = release["assets"] as? [[String: Any]],
                  let dmg = assets.first(where: { ($0["name"] as? String)?.hasSuffix(".dmg") == true }),
                  let download = (dmg["browser_download_url"] as? String).flatMap(URL.init(string:)),
                  let page = (release["html_url"] as? String).flatMap(URL.init(string:)) else { continue }
            if let current = best, !isNewer(version, than: current.version) { continue }
            best = UpdateInfo(version: version, tag: tag, downloadURL: download, pageURL: page, notes: (release["body"] as? String) ?? "")
        }
        guard let best, isNewer(best.version, than: currentVersion) else { return nil }
        return best
    }

    /// Semantic-version comparison on dotted numbers ("0.3.1" < "0.4.0").
    static func isNewer(_ candidate: String, than reference: String) -> Bool {
        let a = candidate.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        let b = reference.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        for index in 0..<max(a.count, b.count) {
            let x = index < a.count ? a[index] : 0
            let y = index < b.count ? b[index] : 0
            if x != y { return x > y }
        }
        return false
    }
}
