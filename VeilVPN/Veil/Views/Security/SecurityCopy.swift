import Foundation

/// The only place a finding id becomes text. Keeping it in one table means a rule can be added
/// without touching a view, and a missing translation is one grep away.
enum SecurityCopy {
    static func title(_ id: String) -> String {
        switch id {
        case "demo-mode": String(localized: "Demo mode")
        case "turbo-active", "turbo-active-isp", "turbo-active-exit": String(localized: "YouTube Turbo is on")
        case "tor-check-failed", "tor-check-failed-exit": String(localized: "The exit check says this is not Tor")
        case "system-proxy-off": String(localized: "Veil does not configure the system proxy")
        case "proxy-failed": String(localized: "The system proxy could not be configured")
        case "kill-switch-inert": String(localized: "The kill switch cannot block anything")
        case "kill-switch-off": String(localized: "The kill switch is off")
        case "direct-routes-active", "direct-routes-active-isp": String(localized: "Some destinations bypass Tor")
        case "sessions-survive-killswitch": String(localized: "Open connections survive the kill switch")
        case "tunnel-off": String(localized: "Not connected")
        case "transport-direct-blocked": String(localized: "Connecting to Tor directly on a network that blocks it")
        case "transport-fingerprintable": String(localized: "A direct Tor connection is recognisable")
        case "update-check-direct": String(localized: "Update checks go out before Tor is up")
        case "shared-exit": String(localized: "Every site shares the same pinned exit")
        case "no-isolation": String(localized: "One circuit carries every site")
        case "partial-isolation": String(localized: "Sites share a small set of circuits")
        case "plain-http-allowed": String(localized: "Plain HTTP is allowed through Tor")
        case "exit-country-mismatch": String(localized: "The exit is not in the country you chose")
        case "over-restricted": String(localized: "Too many countries excluded")
        case "padding-off": String(localized: "Traffic padding is off")
        case "padding-stalled": String(localized: "Traffic padding is not running")
        case "pt-dir-world-readable": String(localized: "The transport directory is readable by other users")
        case "diagnostics-on-pasteboard": String(localized: "Diagnostics are on the clipboard")
        case "diagnostics-unredacted": String(localized: "Diagnostics are exported unredacted")
        case "bridges-stored-plaintext": String(localized: "Bridge lines are stored in plain text")
        case "caches-persist": String(localized: "Tor’s caches stay on disk after you quit")
        case "tor-state-persists": String(localized: "Tor’s entry guard stays on disk")
        case "verbose-logs": String(localized: "Verbose logging is on")
        default: id
        }
    }

    static func detail(_ id: String) -> String {
        switch id {
        case "demo-mode":
            String(localized: "The tor binaries were not found, so nothing is actually routed and no score can be given.")
        case "turbo-active", "turbo-active-isp", "turbo-active-exit":
            String(localized: "Tor is not running. Every site, your provider and anyone on your network sees your real IP address.")
        case "tor-check-failed", "tor-check-failed-exit":
            String(localized: "check.torproject.org reported an address that is not a Tor exit. This is a leak, not a slowdown.")
        case "system-proxy-off":
            String(localized: "Apps are not pointed at Veil, so nothing is tunnelled unless you configure each one by hand.")
        case "proxy-failed":
            String(localized: "macOS refused the proxy change, so apps still connect directly.")
        case "kill-switch-inert":
            String(localized: "The kill switch works by holding the system proxy. With proxy configuration off it has nothing to hold.")
        case "kill-switch-off":
            String(localized: "If Tor dies, proxied apps fall back to connecting directly.")
        case "direct-routes-active", "direct-routes-active-isp":
            String(localized: "These destinations are sent outside Tor on purpose. They see your real IP address, and your provider sees which ones you visit.")
        case "sessions-survive-killswitch":
            String(localized: "New connections are refused, but a download that was already running keeps going.")
        case "tunnel-off":
            String(localized: "Nothing is being protected right now.")
        case "transport-direct-blocked":
            String(localized: "Directory authorities did not answer, so this network appears to block Tor. A bridge would be harder to spot.")
        case "transport-fingerprintable":
            String(localized: "A direct Tor connection is easy to identify. A bridge makes it look like ordinary traffic.")
        case "update-check-direct":
            String(localized: "The request goes out in the clear and tells whoever is watching that this Mac runs Veil.")
        case "shared-exit":
            String(localized: "Measured relays are pinned, so every circuit ends at the same one or two exits and one relay sees your whole session.")
        case "no-isolation":
            String(localized: "Without isolation one exit relay sees every site you visit in this session.")
        case "partial-isolation":
            String(localized: "Several measured circuits spread your traffic, but sites still share exits. A circuit per site is stricter.")
        case "plain-http-allowed":
            String(localized: "An exit relay can read and rewrite anything that is not encrypted.")
        case "exit-country-mismatch":
            String(localized: "Tor could not honour the country you chose and used another one.")
        case "over-restricted":
            String(localized: "Excluding this many countries leaves few relays, which makes your circuits unusual and slow.")
        case "padding-off":
            String(localized: "Without padding, the size and timing of your traffic describe what you are doing, even though nobody can read it.")
        case "padding-stalled":
            String(localized: "Padding is switched on but no cover traffic is flowing.")
        case "pt-dir-world-readable":
            String(localized: "Another user on this Mac could replace the pluggable transport binaries.")
        case "diagnostics-on-pasteboard":
            String(localized: "Anything on the clipboard can be read by other apps.")
        case "diagnostics-unredacted":
            String(localized: "Exported diagnostics would include addresses, ports and host names.")
        case "bridges-stored-plaintext":
            String(localized: "Anyone with this Mac can read which bridges you use, which may identify you.")
        case "caches-persist":
            String(localized: "Which relays you fetched stay readable after you quit.")
        case "tor-state-persists":
            String(localized: "Removing it would also discard Tor’s entry guard, and a guard that changes every run exposes you to more relays over time. Veil does not offer a button for this.")
        case "verbose-logs":
            String(localized: "Verbose logs record far more about what you connected to.")
        case "limit-apps-ignore-proxy":
            String(localized: "Apps that ignore the system proxy — many Electron apps, most installers, Telegram’s desktop app — connect directly. Veil cannot stop them.")
        case "limit-udp":
            String(localized: "Tor carries TCP only. Voice and video calls use UDP and never go through it.")
        case "limit-bridge-unauthenticated":
            String(localized: "Veil’s local proxy accepts connections from anything running as you on this Mac.")
        case "limit-global-observer":
            String(localized: "Padding adds cover traffic but never delays a real packet, so someone watching both ends of the network can still correlate timing.")
        case "limit-guard-persistence":
            String(localized: "Tor keeps its entry guard on disk on purpose. Clearing it every run would be worse for anonymity, not better.")
        default: ""
        }
    }
}
