import AppKit
import Foundation

/// Telegram for macOS talks MTProto over its own sockets and ignores the system proxy, so it never
/// notices that the Mac is on Tor. Every official client understands `tg://socks` links, which add a
/// SOCKS5 proxy with one click — pointed at Veil, Telegram connects through Tor like everything else.
enum TelegramIntegration {
    static func proxyURL(socksPort: UInt16) -> URL {
        URL(string: "tg://socks?server=127.0.0.1&port=\(socksPort)")!
    }

    /// Whether an app claims the `tg://` scheme.
    static var isInstalled: Bool {
        guard let probe = URL(string: "tg://resolve") else { return false }
        return NSWorkspace.shared.urlForApplication(toOpen: probe) != nil
    }

    @discardableResult
    static func open(_ url: URL) -> Bool {
        guard isInstalled else { return false }
        return NSWorkspace.shared.open(url)
    }
}
