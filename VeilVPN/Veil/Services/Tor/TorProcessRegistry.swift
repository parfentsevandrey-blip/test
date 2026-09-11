import Darwin
import Foundation

/// Orphan protection for a tor process that now lives for hours rather than minutes.
///
/// `__OwningControllerProcess` is the primary guard — tor polls it and exits within ~15 s if Veil
/// dies. This is the belt to that pair of braces: a pid file per run, swept at launch, and a
/// process is only ever signalled once its executable path has been confirmed to be ours.
enum TorProcessRegistry {
    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Veil/run", isDirectory: true)
    }

    static func register(pid: Int32, dataDirectory: URL) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                                 attributes: [.posixPermissions: 0o700])
        let url = directory.appendingPathComponent("\(pid).tor")
        try? Data(dataDirectory.path.utf8).write(to: url, options: [.atomic])
    }

    static func unregister(pid: Int32) {
        try? FileManager.default.removeItem(at: directory.appendingPathComponent("\(pid).tor"))
    }

    /// The executable behind a pid, or nil when it cannot be read.
    static func executablePath(of pid: Int32) -> String? {
        var buffer = [CChar](repeating: 0, count: Int(PATH_MAX) * 4)
        let length = proc_pidpath(pid, &buffer, UInt32(buffer.count))
        guard length > 0 else { return nil }
        return String(cString: buffer)
    }

    /// Kills only processes that are demonstrably ours: the pid must still be alive, its executable
    /// must be the tor we ship, and the recorded data directory must be Veil's. Never on pid alone.
    @discardableResult
    static func sweepOrphans(expectedExecutable: URL?, ownDataDirectory: URL) -> Int {
        let manager = FileManager.default
        guard let entries = try? manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) else {
            return 0
        }
        var killed = 0
        for entry in entries where entry.pathExtension == "tor" {
            guard let pid = Int32(entry.deletingPathExtension().lastPathComponent) else {
                try? manager.removeItem(at: entry)
                continue
            }
            if pid == ProcessInfo.processInfo.processIdentifier { continue }
            guard kill(pid, 0) == 0 else {
                try? manager.removeItem(at: entry)
                continue
            }
            let recorded = (try? Data(contentsOf: entry)).map { String(decoding: $0, as: UTF8.self) } ?? ""
            guard recorded == ownDataDirectory.path else { continue }
            if let expectedExecutable, executablePath(of: pid) != expectedExecutable.path { continue }
            kill(pid, SIGTERM)
            killed += 1
            try? manager.removeItem(at: entry)
        }
        return killed
    }
}
