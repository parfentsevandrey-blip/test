import AppKit
import SwiftUI

/// Окна настроек и знакомства. Пока открыто хотя бы одно, Utter — обычная программа (значок в Dock, ⌘Tab,
/// окно в фокусе); закрыли — снова только строка меню. Без этого у программы из строки меню окна открываются
/// позади других и выглядят неактивными.
final class WindowPresenter: NSObject, NSWindowDelegate {
    private var windows: [String: NSWindow] = [:]

    func show<V: View>(_ id: String, title: String, size: NSSize, content: () -> V) {
        let window = windows[id] ?? make(title: title, size: size, content: content())
        windows[id] = window
        NSApp.setActivationPolicy(.regular)
        NSApp.activate()
        window.makeKeyAndOrderFront(nil)
    }

    func close(_ id: String) {
        windows[id]?.close()
    }

    var anyVisible: Bool { windows.values.contains { $0.isVisible } }

    private func make<V: View>(title: String, size: NSSize, content: V) -> NSWindow {
        let window = NSWindow(contentRect: NSRect(origin: .zero, size: size),
                              styleMask: [.titled, .closable, .fullSizeContentView], backing: .buffered, defer: false)
        window.title = title
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        window.isReleasedWhenClosed = false
        window.delegate = self
        window.contentView = NSHostingView(rootView: content.environment(AppModel.shared))
        window.center()
        return window
    }

    func windowWillClose(_ notification: Notification) {
        // Окно ещё видно в момент закрытия — проверяем на следующем витке.
        DispatchQueue.main.async {
            MainActor.assumeIsolated {
                if !self.anyVisible { NSApp.setActivationPolicy(.accessory) }
            }
        }
    }
}
