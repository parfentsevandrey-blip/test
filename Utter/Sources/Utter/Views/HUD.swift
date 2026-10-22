import AppKit
import SwiftUI

/// Плавающая стеклянная панель вверху экрана: что слышно и что уже напечатано.
/// Не забирает фокус — текст продолжает идти в ваше поле ввода.
final class HUDController {
    private var panel: NSPanel?
    private var hideWork: DispatchWorkItem?

    func show(_ model: AppModel) {
        hideWork?.cancel()
        let panel = self.panel ?? makePanel(model)
        self.panel = panel
        position(panel)
        if !panel.isVisible {
            panel.alphaValue = 0
            panel.orderFrontRegardless()
            NSAnimationContext.runAnimationGroup { ctx in
                ctx.duration = 0.18
                panel.animator().alphaValue = 1
            }
        }
    }

    func hide(after delay: Double = 0) {
        hideWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            MainActor.assumeIsolated {
                guard let panel = self?.panel, AppModel.shared.phase == .idle else { return }
                NSAnimationContext.runAnimationGroup({ ctx in
                    ctx.duration = 0.25
                    panel.animator().alphaValue = 0
                }, completionHandler: {
                    MainActor.assumeIsolated {
                        if AppModel.shared.phase == .idle { panel.orderOut(nil) }
                    }
                })
            }
        }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    private func makePanel(_ model: AppModel) -> NSPanel {
        let panel = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 620, height: 84),
                            styleMask: [.nonactivatingPanel, .borderless], backing: .buffered, defer: false)
        panel.isFloatingPanel = true
        panel.level = .statusBar
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary, .ignoresCycle]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = false
        panel.ignoresMouseEvents = true
        panel.hidesOnDeactivate = false
        let host = NSHostingView(rootView: HUDView().environment(model))
        host.frame = panel.contentRect(forFrameRect: panel.frame)
        panel.contentView = host
        return panel
    }

    /// По центру экрана, где сейчас курсор мыши, сразу под строкой меню.
    private func position(_ panel: NSPanel) {
        let mouse = NSEvent.mouseLocation
        let screen = NSScreen.screens.first { $0.frame.contains(mouse) } ?? NSScreen.main
        guard let frame = screen?.visibleFrame else { return }
        let size = panel.frame.size
        panel.setFrameOrigin(NSPoint(x: frame.midX - size.width / 2, y: frame.maxY - size.height - 10))
    }
}

struct HUDView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 14) {
            MicOrb(level: model.level, active: model.phase == .listening, speaking: model.speaking)
                .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 3) {
                transcript
                if let notice = model.notice {
                    Text(notice).font(.system(size: 11, weight: .medium)).foregroundStyle(.orange).lineLimit(1)
                } else {
                    Text(status).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            if model.phase != .idle, let started = model.startedAt {
                TimelineView(.periodic(from: started, by: 1)) { ctx in
                    Text(elapsed(ctx.date.timeIntervalSince(started)))
                        .font(.system(size: 12, weight: .medium, design: .rounded)).monospacedDigit().foregroundStyle(.secondary)
                }
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 20)
        .padding(.vertical, 10)
        .frame(width: 600, height: 68)
        .glassEffect(.regular, in: .capsule)
        .padding(8)
        .environment(\.colorScheme, .dark)
    }

    @ViewBuilder
    private var transcript: some View {
        if model.liveTyped.isEmpty && model.liveTail.isEmpty {
            Text(model.phase == .finishing ? "Дописываю…" : model.phase == .idle ? (model.notice == nil ? "Готово" : "Utter") : "Говорите — текст появится в поле ввода")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.primary.opacity(0.8))
                .lineLimit(1)
        } else {
            // Напечатанное — ярко, неустоявшийся хвост — приглушённо; длинный текст обрезается слева.
            (Text(model.liveTyped).foregroundStyle(.primary) + Text(model.liveTail.isEmpty ? "" : " " + model.liveTail).foregroundStyle(.secondary))
                .font(.system(size: 15, weight: .medium))
                .lineLimit(1)
                .truncationMode(.head)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var status: String {
        let engine = model.engineInUse == .apple ? "распознавание macOS" : model.asrModel.title
        switch model.phase {
        case .listening: return model.speaking ? "Слышу вас · \(engine)" : "Слушаю · \(engine) · Esc или \(model.shortcut.display) — закончить"
        case .finishing: return "Заканчиваю фразу…"
        case .idle: return model.lastText.isEmpty ? "" : "Напечатано: \(model.lastText.count) символов"
        }
    }

    private func elapsed(_ t: TimeInterval) -> String {
        let s = Int(t)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// Микрофон-сфера: кольца расходятся в такт голосу.
struct MicOrb: View {
    var level: Float
    var active: Bool
    var speaking: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: !active)) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            Canvas { gc, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let r = min(size.width, size.height) / 2
                let lvl = CGFloat(active ? level : 0)
                let tint: Color = speaking ? Color(red: 1, green: 0.3, blue: 0.35) : Color(red: 0.45, green: 0.5, blue: 1)
                // Кольца
                for i in 0..<3 {
                    let phase = (t * 0.9 + Double(i) / 3).truncatingRemainder(dividingBy: 1)
                    let rr = r * (0.55 + 0.45 * CGFloat(phase)) * (0.85 + lvl * 0.3)
                    let ring = Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: rr * 2, height: rr * 2))
                    gc.stroke(ring, with: .color(tint.opacity(active ? (1 - phase) * 0.55 * Double(0.4 + lvl) : 0)), lineWidth: 1.5)
                }
                // Ядро, дышит громкостью
                let core = r * (0.42 + lvl * 0.22)
                let disk = Path(ellipseIn: CGRect(x: c.x - core, y: c.y - core, width: core * 2, height: core * 2))
                gc.fill(disk, with: .radialGradient(Gradient(colors: [.white.opacity(0.95), tint, tint.opacity(0.7)]),
                                                    center: CGPoint(x: c.x - core * 0.3, y: c.y - core * 0.3), startRadius: 0, endRadius: core * 1.4))
            }
            .overlay {
                Image(systemName: "mic.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(.white.opacity(0.95))
            }
        }
    }
}
