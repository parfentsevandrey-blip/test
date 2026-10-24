import AppKit
import SwiftUI

/// Сторож запуска: программа из списка закрывается в момент запуска, вместо неё открывается замена
/// из каталога (если установлена), а в углу экрана — короткая плашка с кнопкой «Открыть один раз».
@Observable
final class LaunchGuard {
    /// Программа → чем заменить.
    private(set) var guarded: [String: CatalogApp?] = [:]
    /// Разрешено один раз (до следующего запуска программы).
    @ObservationIgnored private var allowOnce: Set<String> = []
    /// Уже обработанные процессы: о запуске сообщают дважды (начало и готовность).
    @ObservationIgnored private var handled: Set<pid_t> = []
    @ObservationIgnored private var observer: NSObjectProtocol?
    @ObservationIgnored private var running: NSKeyValueObservation?
    @ObservationIgnored private var banner: NSPanel?
    @ObservationIgnored private var bannerTimer: Timer?
    /// Последняя остановка — для самопроверки (сколько прошло от запуска до закрытия).
    private(set) var lastStop: (bundle: String, seconds: Double)?
    var onStop: ((String) -> Void)?

    func update(_ map: [String: CatalogApp?]) {
        guarded = map
        if observer == nil && !map.isEmpty { start() }
    }

    private func start() {
        observer = NSWorkspace.shared.notificationCenter.addObserver(forName: NSWorkspace.didLaunchApplicationNotification, object: nil, queue: .main) { note in
            guard let app = note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication else { return }
            MainActor.assumeIsolated { AppModel.shared.guardian.handle(app) }
        }
        // Второй путь — список запущенных программ: он меняется, даже когда уведомление не пришло.
        running = NSWorkspace.shared.observe(\.runningApplications, options: []) { _, _ in
            DispatchQueue.main.async { MainActor.assumeIsolated { AppModel.shared.guardian.scan() } }
        }
        // Ещё раньше: программа только начала запускаться (окна ещё нет).
        NSWorkspace.shared.notificationCenter.addObserver(forName: NSWorkspace.willLaunchApplicationNotification, object: nil, queue: .main) { note in
            guard let app = note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication else { return }
            MainActor.assumeIsolated { AppModel.shared.guardian.handle(app) }
        }
    }

    /// Проверить все запущенные программы (после изменения их списка).
    func scan() {
        for app in NSWorkspace.shared.runningApplications where !app.isTerminated { handle(app) }
    }

    func handle(_ app: NSRunningApplication) {
        guard let bundle = app.bundleIdentifier, let replacement = guarded[bundle], handled.insert(app.processIdentifier).inserted else { return }
        if handled.count > 500 { handled.removeAll() }
        if allowOnce.remove(bundle) != nil { return }
        let started = app.launchDate ?? Date()
        if !app.forceTerminate() { app.terminate() }
        lastStop = (bundle, Date().timeIntervalSince(started))
        onStop?(bundle)
        let name = app.localizedName ?? bundle
        var opened: String?
        if let replacement, let url = Handlers.appURL(replacement.bundleID) {
            let configuration = NSWorkspace.OpenConfiguration()
            NSWorkspace.shared.openApplication(at: url, configuration: configuration)
            opened = replacement.name
        }
        showBanner(name: name, bundle: bundle, replacement: opened)
    }

    /// Открыть программу один раз в обход сторожа.
    func openOnce(_ bundle: String) {
        allowOnce.insert(bundle)
        hideBanner()
        if let url = Handlers.appURL(bundle) {
            NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration())
        }
    }

    // MARK: Плашка

    private func showBanner(name: String, bundle: String, replacement: String?) {
        if SelfTest.isRunning { return }
        hideBanner()
        let view = GuardBanner(name: name, replacement: replacement, openOnce: { [weak self] in self?.openOnce(bundle) },
                               close: { [weak self] in self?.hideBanner() })
        let host = NSHostingView(rootView: view)
        host.frame = NSRect(x: 0, y: 0, width: 360, height: 84)
        let panel = NSPanel(contentRect: host.frame, styleMask: [.nonactivatingPanel, .borderless], backing: .buffered, defer: false)
        panel.isFloatingPanel = true
        panel.level = .statusBar
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = true
        panel.contentView = host
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        if let screen = NSScreen.main {
            let f = screen.visibleFrame
            panel.setFrameOrigin(NSPoint(x: f.maxX - host.frame.width - 16, y: f.maxY - host.frame.height - 12))
        }
        panel.orderFrontRegardless()
        banner = panel
        bannerTimer = Timer.scheduledTimer(withTimeInterval: 6, repeats: false) { _ in
            MainActor.assumeIsolated { AppModel.shared.guardian.hideBanner() }
        }
    }

    func hideBanner() {
        bannerTimer?.invalidate()
        banner?.orderOut(nil)
        banner = nil
    }
}

struct GuardBanner: View {
    let name: String
    let replacement: String?
    let openOnce: () -> Void
    let close: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "hand.raised.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(Circle().fill(LinearGradient(colors: [.orange, .pink], startPoint: .top, endPoint: .bottom)))
            VStack(alignment: .leading, spacing: 2) {
                Text("Block не дал открыться «\(name)»").font(.system(size: 13, weight: .semibold)).lineLimit(1)
                Text(replacement.map { "Открыт \($0)" } ?? "Сервис Apple выключен").font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button("Один раз", action: openOnce).buttonStyle(.glass).controlSize(.small)
            Button(action: close) { Image(systemName: "xmark") }.buttonStyle(.plain).foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .frame(width: 360, height: 84)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}
