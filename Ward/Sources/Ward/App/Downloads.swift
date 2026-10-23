import AppKit
import CoreServices
import CryptoKit
import WebKit

/// Загрузки: в папку «Загрузки», с пометкой «скачано из интернета» — macOS проверит файл перед открытием.
@Observable
final class Downloads: NSObject, WKDownloadDelegate {
    @Observable
    final class Item: Identifiable {
        let id = UUID()
        var name: String
        var source: URL?
        var file: URL?
        var progress = 0.0
        var received: Int64 = 0
        var total: Int64 = 0
        var state: State = .running
        /// SHA-256 готового файла — сверить с тем, что указан на сайте программы.
        var sha256: String?
        let started = Date()
        @ObservationIgnored weak var window: NSWindow?
        @ObservationIgnored weak var download: WKDownload?
        @ObservationIgnored var observation: NSKeyValueObservation?

        enum State: Equatable { case running, done, failed(String), cancelled }

        init(name: String, source: URL?) {
            self.name = name
            self.source = source
        }
    }

    private(set) var items: [Item] = []
    var active: Int { items.filter { $0.state == .running }.count }
    /// Счётчик для анимации кнопки загрузок.
    private(set) var pulse = 0

    /// Расширения программ и установщиков: такие файлы без шифрования могут подменить по пути.
    nonisolated static let executableExtensions: Set<String> = [
        "dmg", "pkg", "mpkg", "app", "zip", "command", "sh", "tool", "terminal", "scpt", "jar", "exe", "msi", "iso", "img", "xip",
    ]

    func add(_ download: WKDownload, window: NSWindow? = nil) {
        let source = download.originalRequest?.url
        // Адрес из списков вредоносного ПО — загрузку не начинаем вовсе.
        if let source, AppModel.shared.blocker.index.isDangerous(source) {
            download.cancel { _ in }
            let item = Item(name: source.lastPathComponent.isEmpty ? (source.host ?? "файл") : source.lastPathComponent, source: source)
            item.state = .failed("Остановлено: адрес в списках вредоносного ПО")
            items.insert(item, at: 0)
            pulse += 1
            return
        }
        let item = Item(name: source?.lastPathComponent ?? "файл", source: source)
        item.window = window
        item.download = download
        item.observation = download.progress.observe(\.fractionCompleted, options: [.new]) { @Sendable [weak item] progress, _ in
            let fraction = progress.fractionCompleted
            let done = progress.completedUnitCount
            let total = progress.totalUnitCount
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    item?.progress = fraction
                    item?.received = done
                    item?.total = total
                }
            }
        }
        download.delegate = self
        items.insert(item, at: 0)
        pulse += 1
    }

    private func item(_ download: WKDownload) -> Item? { items.first { $0.download === download } }

    func cancel(_ item: Item) {
        item.download?.cancel { _ in }
        item.state = .cancelled
    }

    func reveal(_ item: Item) {
        guard let file = item.file else { return }
        NSWorkspace.shared.activateFileViewerSelecting([file])
    }

    func open(_ item: Item) {
        guard let file = item.file else { return }
        NSWorkspace.shared.open(file)
    }

    func clearFinished() { items.removeAll { $0.state != .running } }

    // MARK: - WKDownloadDelegate

    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String) async -> URL? {
        let kind = (suggestedFilename as NSString).pathExtension.lowercased()
        let source = response.url ?? download.originalRequest?.url
        if Self.executableExtensions.contains(kind), source?.scheme == "http", let item = item(download) {
            let alert = NSAlert()
            alert.alertStyle = .warning
            alert.messageText = "Программа скачивается без шифрования"
            alert.informativeText = "«\(suggestedFilename)» идёт по незащищённому соединению: по пути её могут подменить. Лучше найти ссылку на HTTPS-странице разработчика."
            alert.addButton(withTitle: "Не скачивать")
            alert.addButton(withTitle: "Всё равно скачать")
            let answer: NSApplication.ModalResponse
            if let window = item.window { answer = await alert.beginSheetModal(for: window) } else { answer = alert.runModal() }
            if answer == .alertFirstButtonReturn {
                item.state = .cancelled
                return nil
            }
        }
        let folder = AppPaths.downloads
        let name = suggestedFilename.isEmpty ? "download" : suggestedFilename.replacingOccurrences(of: "/", with: "_")
        var target = folder.appendingPathComponent(name)
        let base = (name as NSString).deletingPathExtension
        let ext = (name as NSString).pathExtension
        var n = 2
        while FileManager.default.fileExists(atPath: target.path) {
            target = folder.appendingPathComponent(ext.isEmpty ? "\(base) \(n)" : "\(base) \(n).\(ext)")
            n += 1
        }
        if let item = item(download) {
            item.name = target.lastPathComponent
            item.file = target
            item.total = response.expectedContentLength
        }
        return target
    }

    func downloadDidFinish(_ download: WKDownload) {
        guard let item = item(download) else { return }
        item.state = .done
        item.progress = 1
        if let file = item.file {
            Self.quarantine(file, source: item.source)
            Task {
                let hash = await Task.detached { Downloads.sha256(of: file) }.value
                item.sha256 = hash
            }
        }
        pulse += 1
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        item(download)?.state = .failed(error.localizedDescription)
    }

    nonisolated static func sha256(of file: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: file) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try? handle.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// Пометка Gatekeeper: файл из интернета, при открытии macOS проверит подпись и предупредит.
    nonisolated static func quarantine(_ file: URL, source: URL?) {
        var values = URLResourceValues()
        var props: [String: Any] = [
            kLSQuarantineAgentNameKey as String: "Ward",
            kLSQuarantineTypeKey as String: kLSQuarantineTypeWebDownload as String,
        ]
        if let source { props[kLSQuarantineDataURLKey as String] = source }
        values.quarantineProperties = props
        var url = file
        try? url.setResourceValues(values)
    }
}
