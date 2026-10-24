import AppKit
import SwiftUI

/// Вопрос «программа хочет в сеть».
struct NetPrompt: Identifiable, Equatable {
    let id = UUID()
    let app: NetApp
    let host: String
    let port: Int
    let date = Date()
}

/// Запись ленты «Сейчас в сети».
struct NetFeedItem: Identifiable {
    let id = UUID()
    let date: Date
    let app: NetApp
    let host: String
    let port: Int
    let allowed: Bool
    let bytes: Int64
    let failure: String?
}

/// Сетевой замок в работе: шлюз, решения, вопросы человеку, временно открытая сеть.
@Observable
final class NetGuard {
    /// Сеть открыта для всех программ до этого времени (замок не снимается, шлюз пропускает всё).
    private(set) var gateUntil: Date?
    private(set) var feed: [NetFeedItem] = []
    private(set) var prompt: NetPrompt?
    private(set) var queued = 0
    private(set) var probe: NetLock.Probe?
    /// Системный прокси указывает на шлюз (его могла переписать другая программа).
    private(set) var proxyOK = true
    private(set) var running = false
    private(set) var startError: String?

    @ObservationIgnored private var gateway: Gateway?
    @ObservationIgnored private var waiting: [String: [CheckedContinuation<Gateway.Verdict, Never>]] = [:]
    @ObservationIgnored private var pending: [NetPrompt] = []
    @ObservationIgnored private var tempAllow: [String: Date] = [:]
    @ObservationIgnored private var gateTimer: Timer?
    @ObservationIgnored private var saveTimer: Timer?
    @ObservationIgnored private var watchTimer: Timer?
    @ObservationIgnored private var dirty = false
    /// Самопроверка отвечает на вопросы сама.
    @ObservationIgnored var autoAnswer: ((NetPrompt) -> NetAnswer)?

    private var model: AppModel { AppModel.shared }

    var gateOpen: Bool { (gateUntil ?? .distantPast) > Date() }

    // MARK: Шлюз

    func startGateway() {
        guard gateway == nil else { return }
        let port = UInt16(clamping: model.state.lock.port)
        let gateway = Gateway(port: port) { request in
            await AppModel.shared.lock.resolve(request)
        }
        gateway.onEvent = { event in
            Task { @MainActor in AppModel.shared.lock.record(event) }
        }
        do {
            try gateway.start()
            self.gateway = gateway
            running = true
            startError = nil
            applyUpstream()
        } catch {
            startError = error.localizedDescription
            running = false
        }
        saveTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.lock.flush() }
        }
        watchTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { _ in
            MainActor.assumeIsolated { AppModel.shared.lock.watch() }
        }
    }

    func stopGateway() {
        gateway?.stop()
        gateway = nil
        running = false
        saveTimer?.invalidate()
        watchTimer?.invalidate()
        flush()
        for (_, list) in waiting { for c in list { c.resume(returning: .deny) } }
        waiting = [:]
        pending = []
        prompt = nil
        NetPromptPanel.shared.hide()
    }

    func applyUpstream() {
        let lock = model.state.lock
        if lock.viaVeil {
            gateway?.upstream = ("127.0.0.1", UInt16(clamping: NetLock.veil().socksPort ?? 10808))
        } else {
            gateway?.upstream = nil
        }
    }

    /// Проверить, что замок действует: прямой выход закрыт, прокси на месте.
    func watch() {
        guard model.state.lock.on else { probe = nil; proxyOK = true; return }
        proxyOK = NetLock.systemProxyPointsTo(port: model.state.lock.port)
        Task.detached {
            let result = NetLock.probeDirect()
            await MainActor.run { AppModel.shared.lock.probe = result }
        }
    }

    // MARK: Решение

    /// Решение по запросу шлюза; может ждать ответа человека.
    func resolve(_ request: Gateway.Request) async -> Gateway.Verdict {
        let key = request.app.key
        touch(request)
        if gateOpen { return .allow }
        if let until = tempAllow[key], until > Date() { return .allow }
        let lock = model.state.lock
        switch lock.apps[key]?.decision ?? .ask {
        case .allow: return .allow
        case .deny: return .deny
        case .ask: break
        }
        // Службы macOS без спроса не беспокоят: молча запрещены, пока замок не открыт.
        if (request.app.isSystem || request.app.isApple) && !lock.askSystem { return .deny }
        return await ask(NetPrompt(app: request.app, host: request.host, port: request.port))
    }

    private func ask(_ p: NetPrompt) async -> Gateway.Verdict {
        await withCheckedContinuation { (c: CheckedContinuation<Gateway.Verdict, Never>) in
            let key = p.app.key
            if waiting[key] != nil {
                waiting[key]?.append(c)
                return
            }
            waiting[key] = [c]
            pending.append(p)
            queued = pending.count
            showNext()
        }
    }

    private func showNext() {
        guard prompt == nil, !pending.isEmpty else { return }
        let next = pending.removeFirst()
        queued = pending.count
        prompt = next
        if let auto = autoAnswer {
            let answer = auto(next)
            DispatchQueue.main.async { AppModel.shared.lock.answer(answer) }
            return
        }
        NetPromptPanel.shared.show()
        // Без ответа за 40 с — запрет этого раза (решение не запоминается).
        let id = next.id
        DispatchQueue.main.asyncAfter(deadline: .now() + 40) {
            if AppModel.shared.lock.prompt?.id == id { AppModel.shared.lock.answer(.denyOnce) }
        }
    }

    func answer(_ answer: NetAnswer) {
        guard let current = prompt else { return }
        let key = current.app.key
        var verdict: Gateway.Verdict = .deny
        switch answer {
        case .always:
            setDecision(current.app, .allow)
            verdict = .allow
        case .once:
            tempAllow[key] = Date().addingTimeInterval(600)
            verdict = .allow
        case .never:
            setDecision(current.app, .deny)
        case .denyOnce:
            break
        }
        for c in waiting.removeValue(forKey: key) ?? [] { c.resume(returning: verdict) }
        prompt = nil
        if pending.isEmpty { NetPromptPanel.shared.hide() }
        showNext()
    }

    func setDecision(_ app: NetApp, _ decision: NetDecision) {
        var rule = model.state.lock.apps[app.key] ?? NetAppRule(app: app, decision: decision)
        rule.decision = decision
        model.state.lock.apps[app.key] = rule
        if decision != .allow { tempAllow[app.key] = nil }
        dirty = true
        flush()
    }

    func forget(_ key: String) {
        model.state.lock.apps[key] = nil
        tempAllow[key] = nil
        dirty = true
        flush()
    }

    // MARK: Учёт

    private func touch(_ request: Gateway.Request) {
        let key = request.app.key
        var rule = model.state.lock.apps[key] ?? NetAppRule(app: request.app, decision: .ask)
        rule.lastSeen = Date()
        rule.app = request.app
        let host = "\(request.host):\(request.port)"
        rule.hosts.removeAll { $0 == host }
        rule.hosts.insert(host, at: 0)
        if rule.hosts.count > 12 { rule.hosts.removeLast(rule.hosts.count - 12) }
        model.state.lock.apps[key] = rule
        dirty = true
    }

    func record(_ event: Gateway.Event) {
        let key = event.app.key
        if var rule = model.state.lock.apps[key] {
            if event.verdict == .allow { rule.allowed += 1 } else { rule.denied += 1 }
            rule.bytes += event.bytes
            model.state.lock.apps[key] = rule
            dirty = true
        }
        feed.insert(NetFeedItem(date: event.date, app: event.app, host: event.host, port: event.port, allowed: event.verdict == .allow,
                                bytes: event.bytes, failure: event.failure), at: 0)
        if feed.count > 300 { feed.removeLast(feed.count - 300) }
    }

    func flush() {
        guard dirty else { return }
        dirty = false
        model.state.save()
    }

    // MARK: Открыть сеть на время

    func openGate(minutes: Int) {
        gateUntil = Date().addingTimeInterval(Double(minutes) * 60)
        gateTimer?.invalidate()
        gateTimer = Timer.scheduledTimer(withTimeInterval: Double(minutes) * 60, repeats: false) { _ in
            MainActor.assumeIsolated { AppModel.shared.lock.closeGate() }
        }
        // Ждущие вопросы решаются сами: сеть открыта.
        for (_, list) in waiting { for c in list { c.resume(returning: .allow) } }
        waiting = [:]
        pending = []
        prompt = nil
        NetPromptPanel.shared.hide()
    }

    func closeGate() {
        gateUntil = nil
        gateTimer?.invalidate()
        gateTimer = nil
    }

    // MARK: Для демонстрации экрана

    func showDemo(feed items: [NetFeedItem], prompt p: NetPrompt?) {
        feed = items
        prompt = p
        running = true
        probe = .closed
    }
}

enum NetAnswer { case always, once, never, denyOnce }

/// Окно вопроса. Одно на всё время работы: его прячут и показывают, но не уничтожают
/// (освобождение окна во время обработки нажатия роняло Block на macOS 27).
final class NetPromptPanel {
    static let shared = NetPromptPanel()
    private var panel: NSPanel?

    func show() {
        let panel = self.panel ?? make()
        if let screen = NSScreen.main {
            let f = screen.visibleFrame
            panel.setFrameOrigin(NSPoint(x: f.maxX - panel.frame.width - 16, y: f.maxY - panel.frame.height - 12))
        }
        panel.orderFrontRegardless()
    }

    func hide() { panel?.orderOut(nil) }

    private func make() -> NSPanel {
        let host = NSHostingView(rootView: NetPromptView().environment(AppModel.shared))
        host.frame = NSRect(x: 0, y: 0, width: 400, height: 168)
        let panel = NSPanel(contentRect: host.frame, styleMask: [.nonactivatingPanel, .borderless], backing: .buffered, defer: false)
        panel.isReleasedWhenClosed = false
        panel.isFloatingPanel = true
        panel.level = .statusBar
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = true
        panel.hidesOnDeactivate = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.contentView = host
        self.panel = panel
        return panel
    }
}

struct NetPromptView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if let p = model.lock.prompt {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        NetAppIcon(app: p.app, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("«\(p.app.name)» хочет выйти в сеть").font(.system(size: 13, weight: .semibold)).lineLimit(1)
                            Text("\(p.host):\(p.port)").font(.system(size: 11, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                            if model.lock.queued > 0 {
                                Text("Ещё ждут ответа: \(model.lock.queued)").font(.system(size: 10)).foregroundStyle(.tertiary)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    HStack(spacing: 8) {
                        // Действие — после обработки нажатия: окно не трогается, пока SwiftUI с ним работает.
                        Button("Запретить") { later(.never) }.buttonStyle(.glass)
                        Spacer()
                        Button("Только сейчас") { later(.once) }.buttonStyle(.glass)
                        Button("Разрешить") { later(.always) }.buttonStyle(.glassProminent).tint(.green)
                    }
                    .controlSize(.regular)
                }
                .padding(16)
            } else {
                Color.clear
            }
        }
        .frame(width: 400, height: 168)
        .glassEffect(.regular, in: .rect(cornerRadius: 24))
    }

    private func later(_ answer: NetAnswer) {
        DispatchQueue.main.async { AppModel.shared.lock.answer(answer) }
    }
}

/// Значок программы (по пути) или знак службы macOS.
struct NetAppIcon: View {
    let app: NetApp
    var size: CGFloat = 28

    var body: some View {
        if !app.path.isEmpty, FileManager.default.fileExists(atPath: app.path) {
            Image(nsImage: NSWorkspace.shared.icon(forFile: app.path))
                .resizable()
                .frame(width: size, height: size)
        } else {
            Image(systemName: app.isSystem ? "gearshape.2.fill" : "terminal.fill")
                .font(.system(size: size * 0.45, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(RoundedRectangle(cornerRadius: size * 0.24).fill((app.isSystem ? Color.gray : Color.indigo).gradient))
        }
    }
}
