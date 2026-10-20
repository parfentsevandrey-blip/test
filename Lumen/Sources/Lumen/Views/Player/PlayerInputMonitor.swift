import AppKit

/// Клавиатура и прокрутка в плеере. Локальный монитор событий срабатывает раньше
/// меню и не зависит от того, какое представление сейчас в фокусе.
final class PlayerInputMonitor {
    struct Context {
        var model: AppModel
        var togglePanel: (PlayerPanel) -> Void
        var dismissOverlays: () -> Bool
        var panelOpen: () -> Bool
        var poke: () -> Void
    }

    private var monitor: Any?
    private var context: Context?
    private var scrollAccumulator: CGFloat = 0

    func install(_ context: Context) {
        self.context = context
        guard monitor == nil else { return }
        monitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown, .scrollWheel]) { [weak self] event in
            guard let self, self.handle(event) else { return event }
            return nil
        }
    }

    func uninstall() {
        if let monitor { NSEvent.removeMonitor(monitor) }
        monitor = nil
        context = nil
    }

    private func handle(_ event: NSEvent) -> Bool {
        guard let context, context.model.screen == .player else { return false }
        if event.type == .scrollWheel { return scroll(event, context) }

        let mods = event.modifierFlags.intersection(.deviceIndependentFlagsMask)
        if mods.contains(.command) || mods.contains(.control) { return false }
        if let editor = event.window?.firstResponder as? NSTextView, editor.isEditable { return false }

        let engine = context.model.engine
        let skip = Prefs.skip
        let shift = mods.contains(.shift)
        let option = mods.contains(.option)
        context.poke()

        switch event.keyCode {
        case 49: engine.togglePlay(); return true                          // пробел
        case 123: engine.skip(by: option ? -1 : (shift ? -skip * 3 : -skip)); return true  // ←
        case 124: engine.skip(by: option ? 1 : (shift ? skip * 3 : skip)); return true     // →
        case 126: engine.changeVolume(by: 0.05); return true               // ↑
        case 125: engine.changeVolume(by: -0.05); return true              // ↓
        case 36, 76: context.model.toggleFullScreen(); return true         // Return
        case 53:                                                           // Esc
            if context.dismissOverlays() { return true }
            if context.model.isFullScreen { context.model.exitFullScreen(); return true }
            return false
        default: break
        }

        guard let key = event.charactersIgnoringModifiers?.lowercased().first else { return false }
        switch key {
        case "k": engine.togglePlay()
        case "j": engine.skip(by: -skip)
        case "l": engine.skip(by: skip)
        case "f": context.model.toggleFullScreen()
        case "m": engine.toggleMute()
        case ".": engine.stepFrame(1)
        case ",": engine.stepFrame(-1)
        case "]": engine.changeRate(by: 1)
        case "[": engine.changeRate(by: -1)
        case "\\": engine.setRate(1)
        case "s": engine.cycleSubtitles()
        case "a": engine.cycleAudio()
        case "g": engine.shiftSubtitles(by: -0.1)
        case "h": engine.shiftSubtitles(by: 0.1)
        case "b": engine.markLoop()
        case "p": engine.togglePictureInPicture()
        case "z": engine.cycleGravity()
        case "r": engine.rotate()
        case "n": shift ? engine.previous() : engine.next()
        case "i": context.togglePanel(.info)
        case "e": context.togglePanel(.adjust)
        case "q": context.togglePanel(.queue)
        case "=", "+": engine.changeVolume(by: 0.05)
        case "-": engine.changeVolume(by: -0.05)
        case "0"..."9":
            let digit = Double(String(key)) ?? 0
            engine.seek(fraction: digit / 10)
            engine.flash("percent", "\(Int(digit * 10))%")
        default: return false
        }
        return true
    }

    /// Горизонтальный свайп трекпада — перемотка, вертикальный — громкость.
    private func scroll(_ event: NSEvent, _ context: Context) -> Bool {
        if context.panelOpen() { return false }
        let engine = context.model.engine
        let dx = event.scrollingDeltaX
        let dy = event.scrollingDeltaY
        context.poke()
        if abs(dx) > abs(dy) * 1.2 {
            let factor: CGFloat = event.hasPreciseScrollingDeltas ? 0.12 : 2
            scrollAccumulator += -dx * factor
            if abs(scrollAccumulator) >= 1 {
                let seconds = Double(scrollAccumulator.rounded(.towardZero))
                scrollAccumulator -= CGFloat(seconds)
                engine.seek(to: engine.currentTime + seconds, precise: false)
                engine.flash(seconds > 0 ? "forward.fill" : "backward.fill", TimeFormat.string(engine.currentTime))
            }
            return true
        }
        if abs(dy) > 0 {
            let step: Float = event.hasPreciseScrollingDeltas ? Float(dy) * 0.004 : Float(dy) * 0.03
            engine.changeVolume(by: step)
            return true
        }
        return false
    }
}

enum PlayerPanel: String, CaseIterable, Identifiable {
    case adjust, queue, info

    var id: String { rawValue }

    var title: String {
        switch self {
        case .adjust: "Изображение"
        case .queue: "Очередь"
        case .info: "Инфо"
        }
    }

    var symbol: String {
        switch self {
        case .adjust: "slider.horizontal.3"
        case .queue: "list.bullet.below.rectangle"
        case .info: "info.circle"
        }
    }
}
