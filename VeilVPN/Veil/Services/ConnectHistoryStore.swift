import Foundation

/// Owns `connect-history.json` off the main actor. Writes are debounced: nothing here may ever
/// sit in front of a connection attempt.
actor ConnectHistoryStore {
    static let shared = ConnectHistoryStore()

    private let url: URL
    private var file = ConnectHistoryFile()
    private var loaded = false
    private var dirty = false
    private var readOnly = false
    private var lastWrite = Date.distantPast

    init(directory: URL? = nil) {
        let base = directory ?? (FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory).appendingPathComponent("Veil", isDirectory: true)
        url = base.appendingPathComponent("connect-history.json")
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard let data = try? Data(contentsOf: url) else { return }
        guard let decoded = try? JSONDecoder.veilHistory.decode(ConnectHistoryFile.self, from: data) else { return }
        // A file written by a newer version keeps its contents: run empty rather than clobber it.
        guard decoded.version <= ConnectHistoryFile.currentVersion else {
            readOnly = true
            return
        }
        file = decoded
        file.prune()
    }

    func history(for fingerprint: NetworkFingerprint) -> NetworkHistory {
        load()
        return file.networks[fingerprint.id] ?? NetworkHistory(kind: fingerprint.kind.rawValue)
    }

    func record(_ result: AttemptResult, for fingerprint: NetworkFingerprint) {
        load()
        guard !readOnly else { return }
        file.record(result, for: fingerprint)
        dirty = true
        if Date.now.timeIntervalSince(lastWrite) > 5 { write() }
    }

    func flush() {
        load()
        guard dirty, !readOnly else { return }
        write()
    }

    func forgetEverything() {
        load()
        file = ConnectHistoryFile()
        dirty = false
        readOnly = false
        try? FileManager.default.removeItem(at: url)
    }

    private func write() {
        dirty = false
        lastWrite = .now
        let manager = FileManager.default
        let directory = url.deletingLastPathComponent()
        try? manager.createDirectory(at: directory, withIntermediateDirectories: true,
                                     attributes: [.posixPermissions: 0o700])
        guard let data = try? JSONEncoder.veilHistory.encode(file) else { return }
        try? data.write(to: url, options: [.atomic])
        try? manager.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
    }
}

extension JSONEncoder {
    static let veilHistory: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()
}

extension JSONDecoder {
    static let veilHistory: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
