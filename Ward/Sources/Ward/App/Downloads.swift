import AppKit
import CoreServices
import CryptoKit
import WebKit

/// Загрузки: в папку «Загрузки», с пометкой «скачано из интернета» — macOS проверит файл перед открытием.
/// Список хранится между запусками (кроме загрузок из частных окон).
@Observable
final class Downloads: NSObject, WKDownloadDelegate {
    @Observable
    final class Item: Identifiable {
        let id: UUID
        var name: String
        var source: URL?
        var file: URL?
        var progress = 0.0
        var received: Int64 = 0
        var total: Int64 = 0
        var state: State = .running
        /// SHA-256 готового файла — сверить с тем, что указан на сайте программы.
        var sha256: String?
        let started: Date
        var finished: Date?
        /// Скорость, байт в секунду (сглаженная) — для оставшегося времени.
        var speed = 0.0
        /// Из частного окна: в сохранённый список не попадает.
        var isPrivate = false
        @ObservationIgnored weak var window: NSWindow?
        @ObservationIgnored weak var webView: WKWebView?
        @ObservationIgnored weak var download: WKDownload?
        @ObservationIgnored var observation: NSKeyValueObservation?
        @ObservationIgnored var resumeData: Data?
        @ObservationIgnored var sample: (time: Date, bytes: Int64)?

        enum State: Equatable { case running, done, failed(String), cancelled }

        init(id: UUID = UUID(), name: String, source: URL?, started: Date = Date()) {
            self.id = id
            self.name = name
            self.source = source
            self.started = started
        }

        /// Файл ещё лежит там, куда его скачали.
        var fileExists: Bool { file.map { FileManager.default.fileExists(atPath: $0.path) } ?? false }

        /// Сколько осталось ждать, если известен размер и скорость.
        var remaining: TimeInterval? {
            guard state == .running, total > 0, speed > 1 else { return nil }
            return Double(max(0, total - received)) / speed
        }

        var canRetry: Bool {
            switch state {
            case .failed, .cancelled: source != nil
            default: false
            }
        }
    }

    /// Запись для сохранения на диск.
    nonisolated struct Record: Codable, Sendable {
        var id: UUID
        var name: String
        var source: URL?
        var file: URL?
        var size: Int64
        var sha256: String?
        var started: Date
        var finished: Date?
        /// done / failed / cancelled
        var state: String
        var message: String?
    }

    private(set) var items: [Item] = []
    var active: Int { items.filter { $0.state == .running }.count }
    /// Общий ход активных загрузок — для кольца на кнопке.
    var activeProgress: Double {
        let running = items.filter { $0.state == .running }
        guard !running.isEmpty else { return 0 }
        let total = running.reduce(Int64(0)) { $0 + max($1.total, 0) }
        if total > 0 { return Double(running.reduce(Int64(0)) { $0 + $1.received }) / Double(total) }
        return running.reduce(0) { $0 + $1.progress } / Double(running.count)
    }
    /// Счётчик для анимации кнопки загрузок.
    private(set) var pulse = 0
    static let file = "downloads.json"
    static let limit = 500

    /// Расширения программ и установщиков: такие файлы без шифрования могут подменить по пути.
    nonisolated static let executableExtensions: Set<String> = [
        "dmg", "pkg", "mpkg", "app", "zip", "command", "sh", "tool", "terminal", "scpt", "jar", "exe", "msi", "iso", "img", "xip",
    ]

    override init() {
        super.init()
        if !SelfTest.isRunning, !DemoUI.isOn, let records = Storage.load([Record].self, Self.file) {
            items = records.map(Self.item(from:))
        }
    }

    func add(_ download: WKDownload, window: NSWindow? = nil, webView: WKWebView? = nil, isPrivate: Bool = false) {
        let source = download.originalRequest?.url
        // Адрес из списков вредоносного ПО — загрузку не начинаем вовсе.
        if let source, AppModel.shared.blocker.index.isDangerous(source) {
            download.cancel { _ in }
            let item = Item(name: source.lastPathComponent.isEmpty ? (source.host ?? "файл") : source.lastPathComponent, source: source)
            item.state = .failed("Остановлено: адрес в списках вредоносного ПО")
            item.isPrivate = isPrivate
            item.finished = Date()
            items.insert(item, at: 0)
            pulse += 1
            save()
            return
        }
        let item = Item(name: source?.lastPathComponent ?? "файл", source: source)
        item.window = window
        item.webView = webView
        item.isPrivate = isPrivate
        attach(download, to: item)
        items.insert(item, at: 0)
        pulse += 1
    }

    private func attach(_ download: WKDownload, to item: Item) {
        item.download = download
        item.state = .running
        item.finished = nil
        item.speed = 0
        item.sample = nil
        item.observation = download.progress.observe(\.fractionCompleted, options: [.new]) { @Sendable [weak item] progress, _ in
            let fraction = progress.fractionCompleted
            let done = progress.completedUnitCount
            let total = progress.totalUnitCount
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    guard let item else { return }
                    item.progress = fraction
                    item.received = done
                    if total > 0 { item.total = total }
                    Downloads.measure(item)
                }
            }
        }
        download.delegate = self
    }

    /// Скорость — по разнице раз в полсекунды, со сглаживанием: цифра не прыгает.
    private static func measure(_ item: Item) {
        let now = Date()
        guard let last = item.sample else { item.sample = (now, item.received); return }
        let dt = now.timeIntervalSince(last.time)
        guard dt >= 0.5 else { return }
        let rate = Double(item.received - last.bytes) / dt
        item.speed = item.speed == 0 ? rate : item.speed * 0.7 + rate * 0.3
        item.sample = (now, item.received)
    }

    private func item(_ download: WKDownload) -> Item? { items.first { $0.download === download } }

    func cancel(_ item: Item) {
        guard let download = item.download else { item.state = .cancelled; return }
        download.cancel { [weak item] data in
            DispatchQueue.main.async { MainActor.assumeIsolated { item?.resumeData = data } }
        }
        item.state = .cancelled
        item.finished = Date()
        save()
    }

    /// Скачать заново: с места обрыва, если сайт позволяет, иначе с начала.
    func retry(_ item: Item) {
        guard item.canRetry, let source = item.source else { return }
        guard let view = item.webView ?? (item.isPrivate ? nil : AppModel.shared.activeBrowser?.selected?.webViewIfLoaded) else {
            AppModel.shared.open(source)
            return
        }
        item.webView = view
        item.state = .running
        if let data = item.resumeData {
            item.resumeData = nil
            view.resumeDownload(fromResumeData: data) { [weak self, weak item] download in
                guard let self, let item else { return }
                self.attach(download, to: item)
            }
        } else {
            item.progress = 0
            item.received = 0
            view.startDownload(using: URLRequest(url: source)) { [weak self, weak item] download in
                guard let self, let item else { return }
                self.attach(download, to: item)
            }
        }
        pulse += 1
    }

    func reveal(_ item: Item) {
        guard let file = item.file, item.fileExists else { return }
        NSWorkspace.shared.activateFileViewerSelecting([file])
    }

    func open(_ item: Item) {
        guard let file = item.file, item.fileExists else { return }
        NSWorkspace.shared.open(file)
    }

    /// Для скриншотов: готовый список.
    func demo(_ list: [Item]) { items = list }

    func openFolder() { NSWorkspace.shared.open(AppPaths.downloads) }

    /// Убрать из списка (сам файл остаётся на месте).
    func remove(_ item: Item) {
        if item.state == .running { cancel(item) }
        items.removeAll { $0 === item }
        save()
    }

    /// Файл — в Корзину, запись — из списка.
    func trash(_ item: Item) {
        if let file = item.file, item.fileExists { try? FileManager.default.trashItem(at: file, resultingItemURL: nil) }
        remove(item)
    }

    func clearFinished() {
        items.removeAll { $0.state != .running }
        save()
    }

    /// Частное окно закрылось — его загрузки исчезают из списка (файлы остаются в «Загрузках»).
    func forgetPrivate() {
        items.removeAll { $0.isPrivate && $0.state != .running }
    }

    func copyLink(_ item: Item) {
        guard let source = item.source else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(source.absoluteString, forType: .string)
    }

    // MARK: - Сохранение

    func save() {
        guard !SelfTest.isRunning, !DemoUI.isOn else { return }
        let records = items.filter { !$0.isPrivate }.prefix(Self.limit).map(Self.record(from:))
        Storage.save(Array(records), Self.file)
    }

    private static func record(from item: Item) -> Record {
        var state = "done", message: String?
        switch item.state {
        case .done: state = "done"
        case .cancelled: state = "cancelled"
        case .failed(let m): state = "failed"; message = m
        case .running: state = "failed"; message = "Прервано: Ward был закрыт"
        }
        return Record(id: item.id, name: item.name, source: item.source, file: item.file, size: max(item.total, item.received),
                      sha256: item.sha256, started: item.started, finished: item.finished, state: state, message: message)
    }

    private static func item(from r: Record) -> Item {
        let item = Item(id: r.id, name: r.name, source: r.source, started: r.started)
        item.file = r.file
        item.total = r.size
        item.received = r.size
        item.sha256 = r.sha256
        item.finished = r.finished
        switch r.state {
        case "done": item.state = .done; item.progress = 1
        case "cancelled": item.state = .cancelled
        default: item.state = .failed(r.message ?? "Не удалось скачать")
        }
        return item
    }

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
                item.finished = Date()
                save()
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
        item.finished = Date()
        item.received = max(item.received, item.total)
        if let file = item.file {
            if let size = (try? file.resourceValues(forKeys: [.fileSizeKey]))?.fileSize { item.total = Int64(size); item.received = Int64(size) }
            Self.quarantine(file, source: item.source)
            Task {
                let hash = await Task.detached { Downloads.sha256(of: file) }.value
                item.sha256 = hash
                self.save()
            }
        }
        pulse += 1
        save()
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        guard let item = item(download) else { return }
        item.resumeData = resumeData
        item.finished = Date()
        // Отмену пользователем WebKit тоже присылает как ошибку — её не путаем со сбоем.
        if item.state != .cancelled { item.state = .failed(error.localizedDescription) }
        save()
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
