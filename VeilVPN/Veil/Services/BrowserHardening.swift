import AppKit
import Foundation

/// What a site sees inside HTTPS — the browser and its version, the operating system, the
/// languages, WebRTC's view of the local address — is the browser's to reveal, and no proxy can
/// reach into an encrypted connection to change it. What Veil can do is start a browser the way
/// Tor Browser is built: a separate profile that goes through Veil alone, WebRTC kept off the real
/// address, QUIC off, and a generic identity — Firefox through its own fingerprinting resistance
/// (which reports a Windows Firefox, a UTC clock and a standard screen), Chromium browsers through
/// a fixed user agent with client hints switched off.
enum BrowserHardening {
    enum Family: String, Sendable { case firefox, chromium }

    struct Browser: Identifiable, Equatable, Sendable {
        let id: String          // bundle identifier
        let name: String
        let family: Family
        let url: URL
    }

    /// Looked up in this order; the first is the best fit because its resistance is built in.
    static let known: [(bundle: String, name: String, family: Family)] = [
        ("org.mozilla.firefox", "Firefox", .firefox),
        ("org.mozilla.firefoxdeveloperedition", "Firefox Developer Edition", .firefox),
        ("app.zen-browser.zen", "Zen", .firefox),
        ("org.mozilla.librewolf", "LibreWolf", .firefox),
        ("com.brave.Browser", "Brave", .chromium),
        ("com.google.Chrome", "Google Chrome", .chromium),
        ("com.microsoft.edgemac", "Microsoft Edge", .chromium),
        ("com.vivaldi.Vivaldi", "Vivaldi", .chromium),
        ("org.chromium.Chromium", "Chromium", .chromium),
    ]

    /// A widely shared identity: Windows, current Chrome — the largest crowd to stand in.
    static let chromiumUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"

    static func installed() -> [Browser] {
        known.compactMap { entry in
            guard let url = NSWorkspace.shared.urlForApplication(withBundleIdentifier: entry.bundle) else { return nil }
            return Browser(id: entry.bundle, name: entry.name, family: entry.family, url: url)
        }
    }

    /// Chromium: everything goes on the command line, and `--user-data-dir` keeps it apart from
    /// the everyday profile, its cookies and its extensions.
    static func chromiumArguments(httpPort: UInt16, profile: URL) -> [String] {
        [
            "--user-data-dir=\(profile.path)",
            "--proxy-server=http://127.0.0.1:\(httpPort)",
            "--proxy-bypass-list=<-loopback>",
            "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE 127.0.0.1",
            "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
            "--disable-quic",
            "--disable-features=UserAgentClientHint,AcceptCHFrame",
            "--user-agent=\(chromiumUserAgent)",
            "--lang=en-US",
            "--incognito",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-background-networking",
            "--disable-sync",
            "--disable-component-update",
        ]
    }

    /// Firefox: a `user.js` in a profile of its own. `privacy.resistFingerprinting` is Tor
    /// Browser's own defence, shipped in every Firefox and merely switched off.
    static func firefoxUserJS(httpPort: UInt16) -> String {
        let prefs: [(String, String)] = [
            ("network.proxy.type", "1"),
            ("network.proxy.http", "\"127.0.0.1\""),
            ("network.proxy.http_port", "\(httpPort)"),
            ("network.proxy.ssl", "\"127.0.0.1\""),
            ("network.proxy.ssl_port", "\(httpPort)"),
            ("network.proxy.share_proxy_settings", "true"),
            ("network.proxy.no_proxies_on", "\"\""),
            ("network.proxy.allow_hijacking_localhost", "false"),
            ("network.proxy.socks_remote_dns", "true"),
            ("network.trr.mode", "5"),
            ("network.http.http3.enable", "false"),
            ("media.peerconnection.enabled", "false"),
            ("privacy.resistFingerprinting", "true"),
            ("privacy.resistFingerprinting.letterboxing", "true"),
            ("privacy.firstparty.isolate", "true"),
            ("privacy.trackingprotection.enabled", "true"),
            ("webgl.disabled", "true"),
            ("geo.enabled", "false"),
            ("dom.battery.enabled", "false"),
            ("intl.accept_languages", "\"en-US, en\""),
            ("javascript.use_us_english_locale", "true"),
            ("browser.startup.page", "0"),
            ("browser.startup.homepage", "\"about:blank\""),
            ("browser.aboutwelcome.enabled", "false"),
            ("browser.shell.checkDefaultBrowser", "false"),
            ("datareporting.healthreport.uploadEnabled", "false"),
            ("toolkit.telemetry.enabled", "false"),
            ("app.update.auto", "false"),
            ("browser.sessionstore.resume_from_crash", "false"),
        ]
        return prefs.map { "user_pref(\"\($0.0)\", \($0.1));" }.joined(separator: "\n") + "\n"
    }

    static func profileDirectory(for browser: Browser) -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Veil", isDirectory: true)
            .appendingPathComponent("browser", isDirectory: true)
            .appendingPathComponent(browser.id, isDirectory: true)
    }

    /// Opens a fresh instance of `browser` on its hardened profile. `open -n` starts a second
    /// instance even when the everyday one is running, which a separate profile requires.
    static func launch(_ browser: Browser, httpPort: UInt16) throws {
        let profile = profileDirectory(for: browser)
        try FileManager.default.createDirectory(at: profile, withIntermediateDirectories: true,
                                                attributes: [.posixPermissions: 0o700])
        var arguments = ["-n", "-a", browser.url.path, "--args"]
        switch browser.family {
        case .chromium:
            arguments.append(contentsOf: chromiumArguments(httpPort: httpPort, profile: profile))
        case .firefox:
            // Rewritten on every launch: the proxy port can change between connections.
            try firefoxUserJS(httpPort: httpPort).write(to: profile.appendingPathComponent("user.js"),
                                                        atomically: true, encoding: .utf8)
            arguments.append(contentsOf: ["-profile", profile.path, "-no-remote"])
        }
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = arguments
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
    }
}
