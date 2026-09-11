import Foundation

/// Removes what Tor left on disk, at the scope the user chose.
///
/// `.caches` keeps `state` and `keys`, so the entry guard survives — rotating guards every run
/// exposes a user to more of them over time, which is worse, not better. `.everything` discards the
/// guard as well; that is a real anonymity trade-off and the UI says so rather than calling it
/// "cleanup".
enum StateCleaner {
    static var defaultDataDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Veil/tor", isDirectory: true)
    }

    /// The names removed for a policy, as a pure function so the choice is testable without a disk.
    static func paths(for policy: AppSettings.ForgetPolicy, contents: [String]) -> [String] {
        switch policy {
        case .off:
            return []
        case .caches:
            return contents.filter { name in
                name.hasPrefix("cached-") || name == "diff-cache" || name == "lock" || name == "unverified-consensus"
            }
        case .everything:
            return contents
        }
    }

    @discardableResult
    static func applyForgetPolicy(_ policy: AppSettings.ForgetPolicy, dataDirectory: URL) -> Int {
        guard policy != .off else { return 0 }
        let manager = FileManager.default
        guard let contents = try? manager.contentsOfDirectory(atPath: dataDirectory.path) else { return 0 }
        var removed = 0
        for name in paths(for: policy, contents: contents) {
            if (try? manager.removeItem(at: dataDirectory.appendingPathComponent(name))) != nil {
                removed += 1
            }
        }
        return removed
    }
}
