import AppKit
import ApplicationServices
import Carbon.HIToolbox
import Foundation

/// Ввод текста в активное поле любой программы.
///
/// Основной способ — системные события клавиатуры с Unicode-строкой (как печатает человек): работает
/// в браузерах, мессенджерах, редакторах, терминале. Нужен доступ «Универсальный доступ».
/// Запасной — вставка через буфер обмена (содержимое буфера восстанавливается).
nonisolated final class Typer: @unchecked Sendable {
    enum Mode: String, Codable, CaseIterable, Sendable {
        case typing, paste

        var title: String {
            switch self {
            case .typing: "Печатать по мере речи"
            case .paste: "Вставлять готовыми фразами"
            }
        }
    }

    static let shared = Typer()
    private let queue = DispatchQueue(label: "app.utter.typer", qos: .userInteractive)
    /// Для самопроверок и демо: вместо событий клавиатуры — сюда.
    nonisolated(unsafe) var sink: (@Sendable (String, Int) -> Void)?

    /// Есть ли разрешение «Универсальный доступ» (без него macOS не даёт печатать в чужие окна).
    static var trusted: Bool { AXIsProcessTrusted() }

    static func requestTrust() {
        let key = kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String
        _ = AXIsProcessTrustedWithOptions([key: true] as CFDictionary)
    }

    /// Поле пароля: macOS включает защищённый ввод, и печатать туда нельзя (и не нужно).
    static var secureInput: Bool { IsSecureEventInputEnabled() }

    /// Программа, в которую сейчас пойдёт текст.
    static var targetApp: String? { NSWorkspace.shared.frontmostApplication?.localizedName }

    func type(_ text: String, mode: Mode = .typing) {
        guard !text.isEmpty else { return }
        if let sink { sink(text, 0); return }
        queue.async {
            if mode == .paste { Self.paste(text) } else { Self.typeNow(text) }
        }
    }

    func backspace(_ count: Int) {
        guard count > 0 else { return }
        if let sink { sink("", count); return }
        queue.async {
            for _ in 0..<count {
                Self.key(UInt16(kVK_Delete))
                usleep(1500)
            }
        }
    }

    /// Дождаться, пока всё напечатается (перед восстановлением буфера, в самопроверках).
    func flush() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in queue.async { cont.resume() } }
    }

    // MARK: - События клавиатуры

    private static let source = CGEventSource(stateID: .hidSystemState)

    private static func typeNow(_ text: String) {
        // Перевод строки — Shift+Return: в мессенджерах обычный Return отправил бы сообщение.
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false)
        for (i, line) in lines.enumerated() {
            if i > 0 {
                key(UInt16(kVK_Return), flags: .maskShift)
                usleep(3000)
            }
            var chunk: [UniChar] = []
            for ch in line {
                let units = Array(String(ch).utf16)
                // Не больше 20 UTF-16 единиц в событии — больше часть программ теряет.
                if chunk.count + units.count > 20 {
                    unicode(chunk)
                    chunk = []
                }
                chunk += units
            }
            if !chunk.isEmpty { unicode(chunk) }
        }
    }

    private static func unicode(_ units: [UniChar]) {
        for down in [true, false] {
            guard let event = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: down) else { continue }
            units.withUnsafeBufferPointer { event.keyboardSetUnicodeString(stringLength: units.count, unicodeString: $0.baseAddress) }
            // Пользователь может ещё держать ⌥ от горячей клавиши — модификаторы явно сбрасываем.
            event.flags = []
            event.post(tap: .cghidEventTap)
        }
        usleep(2000)
    }

    private static func key(_ code: UInt16, flags: CGEventFlags = []) {
        for down in [true, false] {
            guard let event = CGEvent(keyboardEventSource: source, virtualKey: code, keyDown: down) else { continue }
            event.flags = flags
            event.post(tap: .cghidEventTap)
        }
    }

    /// Вставка: кладём текст в буфер, ⌘V, затем возвращаем прежнее содержимое буфера.
    private static func paste(_ text: String) {
        let saved: [NSPasteboardItem] = DispatchQueue.main.sync {
            let pb = NSPasteboard.general
            let items = (pb.pasteboardItems ?? []).map { item -> NSPasteboardItem in
                let copy = NSPasteboardItem()
                for type in item.types { if let data = item.data(forType: type) { copy.setData(data, forType: type) } }
                return copy
            }
            pb.clearContents()
            pb.setString(text.replacingOccurrences(of: "\n", with: "\n"), forType: .string)
            return items
        }
        usleep(30_000)
        key(UInt16(kVK_ANSI_V), flags: .maskCommand)
        usleep(250_000)
        DispatchQueue.main.sync {
            let pb = NSPasteboard.general
            pb.clearContents()
            if !saved.isEmpty { pb.writeObjects(saved) }
        }
    }
}
