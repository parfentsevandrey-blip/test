import Foundation

extension AppState {
    /// Browsers on this Mac that can be opened on a hardened profile, best fit first.
    var hardenedBrowsers: [BrowserHardening.Browser] { BrowserHardening.installed() }

    /// The hardened window goes through Veil's proxy, so there has to be one.
    var canOpenHardenedBrowser: Bool { bridgeRunning && ports != nil }

    func openHardenedBrowser(_ browser: BrowserHardening.Browser) {
        guard let ports, bridgeRunning else {
            lastError = AppError(title: String(localized: "Connect first"),
                                 message: String(localized: "The hardened window goes through Veil’s proxy, so Tor (or YouTube Turbo) has to be running."))
            return
        }
        do {
            try BrowserHardening.launch(browser, httpPort: ports.http)
            append(.veil(.notice, "Opened a hardened \(browser.name) window on 127.0.0.1:\(ports.http) (separate profile, WebRTC off the real address, QUIC off, generic identity)"))
        } catch {
            lastError = AppError(title: String(localized: "Could not open \(browser.name)"), message: error.localizedDescription)
            append(.veil(.warn, "Hardened browser: \(browser.name) did not start: \(error.localizedDescription)"))
        }
    }
}
