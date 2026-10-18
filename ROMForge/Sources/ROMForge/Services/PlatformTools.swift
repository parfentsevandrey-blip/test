import Foundation

/// Находит adb и fastboot на Mac, а если их нет — скачивает официальный
/// пакет Android SDK Platform-Tools от Google в Application Support.
@Observable
@MainActor
final class PlatformTools {
    enum State: Equatable {
        case searching
        case downloading(Double)
        case ready(directory: URL)
        case failed(String)
    }

    static let downloadURL = URL(string: "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip")!

    private(set) var state: State = .searching
    private(set) var version: String?

    var adb: URL? { directory?.appendingPathComponent("adb") }
    var fastboot: URL? { directory?.appendingPathComponent("fastboot") }

    var directory: URL? {
        if case .ready(let directory) = state { return directory }
        return nil
    }

    private var preparing: Task<URL, Error>?

    static var managedDirectory: URL {
        AppPaths.support.appendingPathComponent("platform-tools", isDirectory: true)
    }

    /// Возвращает каталог с adb/fastboot, при необходимости скачивая их.
    @discardableResult
    func prepare(forceDownload: Bool = false) async throws -> URL {
        if !forceDownload, let directory { return directory }
        if let preparing { return try await preparing.value }

        let task = Task<URL, Error> { @MainActor in
            state = .searching
            if !forceDownload, let found = Self.locateExisting() {
                return found
            }
            return try await download()
        }
        preparing = task
        defer { preparing = nil }

        do {
            let directory = try await task.value
            state = .ready(directory: directory)
            await startServer(in: directory)
            return directory
        } catch {
            state = .failed(error.localizedDescription)
            throw error
        }
    }

    private static func locateExisting() -> URL? {
        let fm = FileManager.default
        let home = fm.homeDirectoryForCurrentUser
        var candidates: [URL] = []
        if let custom = UserDefaults.standard.string(forKey: "customPlatformToolsPath"), !custom.isEmpty {
            candidates.append(URL(fileURLWithPath: custom))
        }
        if let bundled = Bundle.main.resourceURL?.appendingPathComponent("platform-tools") {
            candidates.append(bundled)
        }
        candidates += [
            managedDirectory,
            home.appendingPathComponent("Library/Android/sdk/platform-tools"),
            URL(fileURLWithPath: "/opt/homebrew/bin"),
            URL(fileURLWithPath: "/usr/local/bin"),
        ]
        return candidates.first { dir in
            fm.isExecutableFile(atPath: dir.appendingPathComponent("adb").path)
                && fm.isExecutableFile(atPath: dir.appendingPathComponent("fastboot").path)
        }
    }

    private func download() async throws -> URL {
        state = .downloading(0)
        let downloads = AppPaths.caches.appendingPathComponent("platform-tools-download", isDirectory: true)
        try? FileManager.default.removeItem(at: downloads)
        try FileManager.default.createDirectory(at: downloads, withIntermediateDirectories: true)

        let zip = downloads.appendingPathComponent("platform-tools.zip")
        try await FileDownloader.download(from: Self.downloadURL, to: zip) { [weak self] progress in
            Task { @MainActor in self?.state = .downloading(progress.fraction) }
        }

        let ditto = URL(fileURLWithPath: "/usr/bin/ditto")
        try await Shell.runChecked(ditto, ["-x", "-k", zip.path, downloads.path])

        let target = Self.managedDirectory
        try? FileManager.default.removeItem(at: target)
        try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try FileManager.default.moveItem(at: downloads.appendingPathComponent("platform-tools"), to: target)
        try? FileManager.default.removeItem(at: downloads)
        return target
    }

    /// Запускаем сервер adb заранее и без пайпов: демон, унаследовавший stdout,
    /// не даёт корректно дождаться завершения последующих команд.
    private func startServer(in directory: URL) async {
        let adb = directory.appendingPathComponent("adb")
        await Task.detached {
            let process = Process()
            process.executableURL = adb
            process.arguments = ["start-server"]
            process.standardOutput = FileHandle.nullDevice
            process.standardError = FileHandle.nullDevice
            try? process.run()
            process.waitUntilExit()
        }.value
        if let result = try? await Shell.run(adb, ["version"]) {
            version = result.stdout
                .split(separator: "\n")
                .first { $0.hasPrefix("Version") }
                .map { String($0.dropFirst("Version ".count)) }
        }
    }
}

enum AppPaths {
    static var support: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ROMForge", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static var caches: URL {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ROMForge", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static var downloads: URL {
        let url = caches.appendingPathComponent("Downloads", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
