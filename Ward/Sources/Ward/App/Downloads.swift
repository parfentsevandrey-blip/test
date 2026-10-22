import AppKit
import CoreServices
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
        let started = Date()
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

    func add(_ download: WKDownload) {
        let item = Item(name: download.originalRequest?.url?.lastPathComponent ?? "файл", source: download.originalRequest?.url)
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
        if let file = item.file { Self.quarantine(file, source: item.source) }
        pulse += 1
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        item(download)?.state = .failed(error.localizedDescription)
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
