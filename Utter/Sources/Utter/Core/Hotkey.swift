import AppKit
import Carbon.HIToolbox
import Foundation

/// Сочетание клавиш: код клавиши + модификаторы Carbon (как их понимает RegisterEventHotKey).
nonisolated struct Shortcut: Codable, Hashable, Sendable {
    var keyCode: UInt32
    var modifiers: UInt32
    var key: String

    static let `default` = Shortcut(keyCode: UInt32(kVK_Space), modifiers: UInt32(optionKey), key: "Пробел")

    var display: String {
        var s = ""
        if modifiers & UInt32(controlKey) != 0 { s += "⌃" }
        if modifiers & UInt32(optionKey) != 0 { s += "⌥" }
        if modifiers & UInt32(shiftKey) != 0 { s += "⇧" }
        if modifiers & UInt32(cmdKey) != 0 { s += "⌘" }
        return s + " " + key
    }

    /// Из нажатия при записи сочетания в настройках.
    static func from(_ event: NSEvent) -> Shortcut? {
        let flags = event.modifierFlags.intersection(.deviceIndependentFlagsMask)
        var mods: UInt32 = 0
        if flags.contains(.control) { mods |= UInt32(controlKey) }
        if flags.contains(.option) { mods |= UInt32(optionKey) }
        if flags.contains(.shift) { mods |= UInt32(shiftKey) }
        if flags.contains(.command) { mods |= UInt32(cmdKey) }
        let code = UInt32(event.keyCode)
        let isFunctionKey = Self.functionKeys[code] != nil
        // Без модификатора можно только функциональные клавиши — иначе пропадёт обычная буква.
        guard mods != 0 || isFunctionKey else { return nil }
        return Shortcut(keyCode: code, modifiers: mods, key: name(for: code, event: event))
    }

    private static let functionKeys: [UInt32: String] = [
        UInt32(kVK_F1): "F1", UInt32(kVK_F2): "F2", UInt32(kVK_F3): "F3", UInt32(kVK_F4): "F4", UInt32(kVK_F5): "F5",
        UInt32(kVK_F6): "F6", UInt32(kVK_F7): "F7", UInt32(kVK_F8): "F8", UInt32(kVK_F9): "F9", UInt32(kVK_F10): "F10",
        UInt32(kVK_F11): "F11", UInt32(kVK_F12): "F12", UInt32(kVK_F13): "F13", UInt32(kVK_F14): "F14", UInt32(kVK_F15): "F15",
        UInt32(kVK_F16): "F16", UInt32(kVK_F17): "F17", UInt32(kVK_F18): "F18", UInt32(kVK_F19): "F19",
    ]

    private static func name(for code: UInt32, event: NSEvent) -> String {
        if let f = functionKeys[code] { return f }
        switch Int(code) {
        case kVK_Space: return "Пробел"
        case kVK_Return: return "↩"
        case kVK_Tab: return "⇥"
        case kVK_Escape: return "Esc"
        case kVK_LeftArrow: return "←"
        case kVK_RightArrow: return "→"
        case kVK_UpArrow: return "↑"
        case kVK_DownArrow: return "↓"
        case kVK_ANSI_Grave: return "`"
        default: return (event.charactersIgnoringModifiers ?? "?").uppercased()
        }
    }
}

/// Глобальные горячие клавиши через Carbon (RegisterEventHotKey): работают в любой программе,
/// не требуют разрешений и сообщают и о нажатии, и об отпускании — это нужно режиму «пока держу».
/// Дополнительно: удержание правого ⌥ (через монитор событий, нужен «Универсальный доступ»).
nonisolated final class HotkeyCenter: @unchecked Sendable {
    static let shared = HotkeyCenter()

    var onPress: (@MainActor () -> Void)?
    var onRelease: (@MainActor () -> Void)?
    /// Esc во время диктовки — остановить.
    var onEscape: (@MainActor () -> Void)?
    /// Удержание правого ⌥: начало и конец.
    var onHoldStart: (@MainActor () -> Void)?
    var onHoldEnd: (@MainActor () -> Void)?
    /// Короткое нажатие правого ⌥ (без других клавиш): начать или закончить диктовку.
    var onTap: (@MainActor () -> Void)?

    private var mainRef: EventHotKeyRef?
    private var escRef: EventHotKeyRef?
    private var handler: EventHandlerRef?
    private var monitors: [Any] = []
    private var rightOptionTimer: Timer?
    private var rightOptionActive = false
    private var rightOptionDown: Date?
    /// Пока ⌥ был нажат, нажали другую клавишу — это сочетание (⌥+буква), а не нажатие ⌥.
    private var rightOptionSpoiled = false
    private var rightOptionEnabled = false

    private static let signature: OSType = 0x5554_5452 // 'UTTR'

    private func installHandler() {
        guard handler == nil else { return }
        var types = [
            EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed)),
            EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyReleased)),
        ]
        let me = Unmanaged.passUnretained(self).toOpaque()
        InstallEventHandler(GetApplicationEventTarget(), { _, event, user in
            guard let event, let user else { return OSStatus(eventNotHandledErr) }
            var id = EventHotKeyID()
            GetEventParameter(event, EventParamName(kEventParamDirectObject), EventParamType(typeEventHotKeyID), nil,
                              MemoryLayout<EventHotKeyID>.size, nil, &id)
            let center = Unmanaged<HotkeyCenter>.fromOpaque(user).takeUnretainedValue()
            let pressed = GetEventKind(event) == UInt32(kEventHotKeyPressed)
            let hotkeyID = id.id
            DispatchQueue.main.async {
                MainActor.assumeIsolated { center.dispatch(id: hotkeyID, pressed: pressed) }
            }
            return noErr
        }, types.count, &types, me, &handler)
    }

    @MainActor
    private func dispatch(id: UInt32, pressed: Bool) {
        switch (id, pressed) {
        case (1, true): onPress?()
        case (1, false): onRelease?()
        case (2, true): onEscape?()
        default: break
        }
    }

    /// Зарегистрировать основное сочетание. false — оно занято другой программой.
    @discardableResult
    func register(_ shortcut: Shortcut) -> Bool {
        installHandler()
        unregister()
        let id = EventHotKeyID(signature: Self.signature, id: 1)
        let status = RegisterEventHotKey(shortcut.keyCode, shortcut.modifiers, id, GetApplicationEventTarget(), 0, &mainRef)
        return status == noErr
    }

    func unregister() {
        if let mainRef { UnregisterEventHotKey(mainRef) }
        mainRef = nil
    }

    /// Esc перехватывается только пока идёт диктовка.
    func captureEscape(_ on: Bool) {
        installHandler()
        if on, escRef == nil {
            let id = EventHotKeyID(signature: Self.signature, id: 2)
            RegisterEventHotKey(UInt32(kVK_Escape), 0, id, GetApplicationEventTarget(), 0, &escRef)
        } else if !on, let escRef {
            UnregisterEventHotKey(escRef)
            self.escRef = nil
        }
    }

    /// Правый ⌥: короткое нажатие — начать или закончить диктовку; удержание (дольше трети секунды
    /// без других клавиш) — диктовка, отпустили — стоп, как рация.
    @MainActor
    func setRightOption(_ enabled: Bool) {
        // Уже следим (или уже не следим) — не переустанавливаем: иначе можно пропустить нажатие.
        if enabled == rightOptionEnabled && (!enabled || !monitors.isEmpty) { return }
        rightOptionEnabled = enabled
        monitors.forEach { NSEvent.removeMonitor($0) }
        monitors = []
        guard enabled else { return }
        let flags: (NSEvent) -> Void = { [weak self] event in
            MainActor.assumeIsolated { self?.handleFlags(event) }
        }
        let keys: (NSEvent) -> Void = { [weak self] _ in
            MainActor.assumeIsolated { self?.cancelRightOption() }
        }
        if let m = NSEvent.addGlobalMonitorForEvents(matching: .flagsChanged, handler: flags) { monitors.append(m) }
        if let m = NSEvent.addGlobalMonitorForEvents(matching: .keyDown, handler: keys) { monitors.append(m) }
        if let m = NSEvent.addLocalMonitorForEvents(matching: .flagsChanged, handler: { flags($0); return $0 }) { monitors.append(m) }
    }

    @MainActor
    private func handleFlags(_ event: NSEvent) {
        guard event.keyCode == UInt16(kVK_RightOption) else {
            cancelRightOption()
            return
        }
        let down = event.modifierFlags.contains(.option)
        if down {
            rightOptionDown = Date()
            rightOptionSpoiled = false
            rightOptionTimer?.invalidate()
            rightOptionTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self else { return }
                    self.rightOptionActive = true
                    self.onHoldStart?()
                }
            }
        } else {
            let wasTimer = rightOptionTimer != nil
            rightOptionTimer?.invalidate()
            rightOptionTimer = nil
            if rightOptionActive {
                rightOptionActive = false
                onHoldEnd?()
            } else if wasTimer, !rightOptionSpoiled, let downAt = rightOptionDown, Date().timeIntervalSince(downAt) < 0.35 {
                onTap?()
            }
            rightOptionDown = nil
        }
    }

    @MainActor
    private func cancelRightOption() {
        rightOptionTimer?.invalidate()
        rightOptionTimer = nil
        rightOptionSpoiled = true
    }
}
