import AppKit
import Observation
import SwiftUI
import UniformTypeIdentifiers

@Observable
final class AppModel {
    let engine = PlayerEngine()
    private(set) var isFullScreen = false
    private(set) var chromeVisible = true

    @ObservationIgnored private weak var window: NSWindow?
    @ObservationIgnored private var windowObservers: [any NSObjectProtocol] = []

    init() {
        Prefs.registerDefaults()
        engine.onVideoReady = { [weak self] size in self?.fitWindow(to: size) }
    }

    // MARK: Открытие

    static let contentTypes: [UTType] = {
        var types: [UTType] = [.movie, .video, .audiovisualContent, .audio, .mpeg4Movie, .quickTimeMovie]
        for ext in MediaResolver.matroskaExtensions {
            if let t = UTType(filenameExtension: ext) { types.append(t) }
        }
        return types
    }()

    /// Бросили файл (или несколько, или папку) — сразу играем. Файлы субтитров подключаются к текущему видео.
    func open(_ urls: [URL]) {
        let subtitles = urls.filter { SubtitleParser.fileExtensions.contains($0.pathExtension.lowercased()) }
        if !subtitles.isEmpty, engine.hasMedia, subtitles.count == urls.count {
            subtitles.forEach(engine.addSubtitleFile)
            return
        }
        let files = expand(urls)
        guard !files.isEmpty else {
            NSSound.beep()
            return
        }
        engine.open(files)
        window?.makeKeyAndOrderFront(nil)
    }

    func presentOpenPanel() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = true
        panel.canChooseFiles = true
        panel.allowedContentTypes = Self.contentTypes
        panel.prompt = "Открыть"
        panel.begin { [weak self] response in
            guard response == .OK else { return }
            let urls = panel.urls
            MainActor.assumeIsolated { self?.open(urls) }
        }
    }

    func presentSubtitlePanel() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = SubtitleParser.fileExtensions.compactMap { UTType(filenameExtension: $0) }
        panel.prompt = "Добавить"
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url else { return }
            MainActor.assumeIsolated { self?.engine.addSubtitleFile(url) }
        }
    }

    func revealCurrentInFinder() {
        guard let url = engine.url else { return }
        NSWorkspace.shared.activateFileViewerSelecting([url])
    }

    /// Папки раскрываются рекурсивно, файлы сортируются «по-человечески» (2 < 10).
    func expand(_ urls: [URL]) -> [URL] {
        var result: [URL] = []
        let fm = FileManager.default
        for url in urls {
            var isDir: ObjCBool = false
            guard fm.fileExists(atPath: url.path, isDirectory: &isDir) else { continue }
            if isDir.boolValue {
                let found = (fm.enumerator(at: url, includingPropertiesForKeys: [.isRegularFileKey],
                                           options: [.skipsHiddenFiles, .skipsPackageDescendants])?.allObjects as? [URL]) ?? []
                result += found.filter(MediaResolver.isSupported).sorted {
                    $0.path.localizedStandardCompare($1.path) == .orderedAscending
                }
            } else {
                result.append(url)
            }
        }
        return result
    }

    // MARK: Окно

    func attach(window: NSWindow) {
        guard self.window !== window else { return }
        self.window = window
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        window.isMovableByWindowBackground = false
        window.collectionBehavior.insert(.fullScreenPrimary)
        window.tabbingMode = .disallowed
        for observer in windowObservers { NotificationCenter.default.removeObserver(observer) }
        let center = NotificationCenter.default
        windowObservers = [
            center.addObserver(forName: NSWindow.didEnterFullScreenNotification, object: window, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { withAnimation(.smooth) { self?.isFullScreen = true } }
            },
            center.addObserver(forName: NSWindow.didExitFullScreenNotification, object: window, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    withAnimation(.smooth) { self?.isFullScreen = false }
                    self?.setChromeVisible(true)
                }
            },
        ]
    }

    func toggleFullScreen() {
        window?.toggleFullScreen(nil)
    }

    func exitFullScreen() {
        if isFullScreen { window?.toggleFullScreen(nil) }
    }

    /// Плавно прячет «светофор» окна вместе с элементами управления плеера.
    func setChromeVisible(_ visible: Bool) {
        guard let window, chromeVisible != visible else { return }
        chromeVisible = visible
        let buttons: [NSWindow.ButtonType] = [.closeButton, .miniaturizeButton, .zoomButton]
        NSAnimationContext.runAnimationGroup { context in
            context.duration = visible ? 0.2 : 0.45
            for type in buttons {
                window.standardWindowButton(type)?.animator().alphaValue = visible ? 1 : 0
            }
        }
    }

    /// Подгоняет окно под пропорции кадра с анимацией, оставаясь в пределах экрана.
    func fitWindow(to video: CGSize) {
        guard UserDefaults.standard.bool(forKey: Prefs.fitWindowToVideo),
              let window, !isFullScreen, video.width > 0, video.height > 0,
              let screen = window.screen ?? NSScreen.main else { return }
        let stage = UserDefaults.standard.bool(forKey: Prefs.cinemaStage) ? PlayerLayout.stageInset * 2 : 0
        let visible = screen.visibleFrame
        let maxSize = CGSize(width: visible.width * 0.82, height: visible.height * 0.86)
        let scale = min(maxSize.width / (video.width + stage), maxSize.height / (video.height + stage), 1)
        var size = CGSize(width: (video.width + stage) * scale, height: (video.height + stage) * scale)
        if size.width < 760 {
            let up = 760 / size.width
            size = CGSize(width: 760, height: size.height * up)
        }
        let current = window.frame
        var frame = NSRect(x: current.midX - size.width / 2, y: current.midY - size.height / 2,
                           width: size.width, height: size.height)
        frame.origin.x = min(max(frame.origin.x, visible.minX), visible.maxX - frame.width)
        frame.origin.y = min(max(frame.origin.y, visible.minY), visible.maxY - frame.height)
        window.setFrame(frame, display: true, animate: true)
    }
}

nonisolated enum PlayerLayout {
    static let stageInset: CGFloat = 28
}
