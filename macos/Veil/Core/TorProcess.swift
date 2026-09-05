import Foundation

/// The tor process.
///
/// The Android build embeds tor as a library through a JNI wrapper. There is
/// no equivalent on macOS, and building tor here would mean building OpenSSL
/// and libevent with it, so this spawns the Tor Project's own binary — the
/// expert bundle, which they publish for exactly this purpose. `scripts/
/// fetch-tor.sh` puts it in the app bundle.
///
/// It is the app that spawns it, not the network extension. An extension is
/// sandboxed for a narrow job and spawning a child process from one is at best
/// fragile; the app is an ordinary process that may. So the app owns tor and
/// the transports, publishes tor's SOCKS as a unix socket in the shared
/// container, and the extension does nothing but move packets into it. That
/// split also means the extension cannot wedge the connection logic, and the
/// connection logic cannot wedge the datapath.
@MainActor
final class TorProcess {

    enum Failure: Error, LocalizedError {
        case binaryMissing
        case cookieNeverAppeared
        case exited(Int32, String)

        var errorDescription: String? {
            switch self {
            case .binaryMissing:
                return "tor is not in the app bundle. Run scripts/fetch-tor.sh and rebuild."
            case .cookieNeverAppeared:
                return "tor started but never wrote its control cookie"
            case .exited(let code, let tail):
                return "tor exited with \(code): \(tail)"
            }
        }
    }

    private var process: Process?
    private var recentOutput: [String] = []
    private let outputLimit = 40

    /// Everything tor said, for the log view.
    private(set) var log: [String] = []
    var onLine: ((String) -> Void)?

    var isRunning: Bool { process?.isRunning == true }

    /// Writes the torrc and starts tor, returning once its control cookie
    /// exists — which is the first moment the control socket can be used.
    func start(session: Torrc.Session, torrcPath: String) async throws {
        guard let binary = Bundle.main.url(forResource: "tor", withExtension: nil,
                                           subdirectory: "tor")
                ?? Bundle.main.url(forResource: "tor", withExtension: nil) else {
            throw Failure.binaryMissing
        }

        try FileManager.default.createDirectory(
            atPath: session.dataDirectory, withIntermediateDirectories: true,
            // tor refuses to start if its data directory is group- or
            // world-readable, and says so in a way that reads like a crash.
            attributes: [.posixPermissions: 0o700]
        )
        try Torrc.build(session).write(toFile: torrcPath, atomically: true, encoding: .utf8)

        // A stale cookie from a previous run would be read as this run's.
        try? FileManager.default.removeItem(atPath: session.cookieFile)

        let task = Process()
        task.executableURL = binary
        task.arguments = ["-f", torrcPath, "--ignore-missing-torrc"]
        let pipe = Pipe()
        task.standardOutput = pipe
        task.standardError = pipe

        pipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
            Task { @MainActor in self?.absorb(text) }
        }

        try task.run()
        process = task

        // Wait for the cookie rather than for a fixed delay: it appears when
        // tor has read its configuration and opened the control socket, which
        // is the only thing worth waiting for and is much sooner than any
        // guess would be.
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if FileManager.default.fileExists(atPath: session.cookieFile),
               FileManager.default.fileExists(atPath: session.controlSocket) {
                return
            }
            if !task.isRunning {
                throw Failure.exited(task.terminationStatus, recentOutput.suffix(6).joined(separator: "\n"))
            }
            try await Task.sleep(for: .milliseconds(120))
        }
        throw Failure.cookieNeverAppeared
    }

    func stop() {
        guard let task = process else { return }
        process = nil
        // A control-port HALT would be tidier, but a wedged control socket
        // never answers it and the point of a stop is that it happens.
        task.terminate()
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            if task.isRunning { kill(task.processIdentifier, SIGKILL) }
        }
    }

    private func absorb(_ text: String) {
        for line in text.split(separator: "\n").map(String.init) where !line.isEmpty {
            recentOutput.append(line)
            if recentOutput.count > outputLimit { recentOutput.removeFirst() }
            log.append(line)
            if log.count > 500 { log.removeFirst() }
            onLine?(line)
        }
    }
}
