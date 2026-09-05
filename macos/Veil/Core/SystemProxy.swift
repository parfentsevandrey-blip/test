import AppKit
import Foundation

/// The way in that needs no permission from Apple.
///
/// A packet tunnel — the whole machine's traffic, every application, no
/// configuration — needs an entitlement that Apple issues only to a paid
/// developer team, and macOS will not load a network extension without it.
/// That is a rule about signatures, not about code, and nothing in this
/// project can get round it.
///
/// What needs no entitlement at all is tor itself. It is an ordinary process,
/// it connects through Snowflake exactly as it would inside the tunnel, and it
/// offers a SOCKS proxy and an HTTP CONNECT proxy on loopback. So when the
/// tunnel cannot be started, the app does what a person would do by hand in
/// System Settings → Network → Proxies: it points the system's proxies at
/// tor. Safari, Chrome, Firefox and most applications built on the system's
/// networking follow those settings; every request they make then goes
/// through tor, hostnames included, so nothing is resolved on the local
/// network either.
///
/// The one honest limitation: an application that ignores the system proxy —
/// some messengers, most games, anything with its own networking — keeps
/// using the network directly. The interface says so rather than pretending
/// otherwise, and for Telegram in particular there is a one-click way in
/// (see `telegramURL`).
///
/// Changing the proxy settings needs administrator rights. They are asked for
/// through the system's own password dialogue, once per connect, which is the
/// same prompt any network utility shows; nothing is run in a terminal.
enum SystemProxy {

    enum Failure: Error, LocalizedError {
        case declined
        case failed(String)

        var errorDescription: String? {
            switch self {
            case .declined: return "the proxy settings were not changed"
            case .failed(let why): return why
            }
        }
    }

    /// Points every network service's SOCKS proxy at tor, and its HTTP and
    /// HTTPS proxies at the app's own proxy in front of tor (tor's CONNECT
    /// port would answer a plain-HTTP fetch with 405).
    ///
    /// Everything happens inside one privileged shell script so that the
    /// password is asked for once, not once per service. Loopback and the
    /// local network are excluded so that nothing local ends up going into
    /// tor and coming back out of an exit relay.
    static func enable(socksPort: Int, httpPort: Int) throws {
        let script = """
        set -e
        /usr/sbin/networksetup -listallnetworkservices | tail -n +2 | sed 's/^\\*//' | while IFS= read -r service; do
          /usr/sbin/networksetup -setsocksfirewallproxy "$service" 127.0.0.1 \(socksPort) || true
          /usr/sbin/networksetup -setwebproxy "$service" 127.0.0.1 \(httpPort) || true
          /usr/sbin/networksetup -setsecurewebproxy "$service" 127.0.0.1 \(httpPort) || true
          /usr/sbin/networksetup -setproxybypassdomains "$service" "localhost" "127.0.0.1" "*.local" "169.254/16" || true
          /usr/sbin/networksetup -setsocksfirewallproxystate "$service" on || true
          /usr/sbin/networksetup -setwebproxystate "$service" on || true
          /usr/sbin/networksetup -setsecurewebproxystate "$service" on || true
        done
        """
        try runPrivileged(script, prompt: "Veil направит сетевые запросы Mac через Tor.")
    }

    /// Turns the proxies off again. The addresses are left in place, which is
    /// harmless and what System Settings does too.
    static func disable() throws {
        let script = """
        /usr/sbin/networksetup -listallnetworkservices | tail -n +2 | sed 's/^\\*//' | while IFS= read -r service; do
          /usr/sbin/networksetup -setsocksfirewallproxystate "$service" off || true
          /usr/sbin/networksetup -setwebproxystate "$service" off || true
          /usr/sbin/networksetup -setsecurewebproxystate "$service" off || true
        done
        """
        try runPrivileged(script, prompt: "Veil вернёт сетевые настройки Mac как были.")
    }

    /// Telegram's own way of taking a proxy: a link it opens and confirms with
    /// one click. It does not honour the system proxy, so without this it
    /// would be the one thing on the machine left outside.
    static func telegramURL(socksPort: Int) -> URL? {
        URL(string: "tg://socks?server=127.0.0.1&port=\(socksPort)")
    }

    static func openTelegram(socksPort: Int) {
        guard let url = telegramURL(socksPort: socksPort) else { return }
        NSWorkspace.shared.open(url)
    }

    // MARK: - Running with administrator rights

    /// Runs a shell script through AppleScript's `with administrator
    /// privileges`, which is what produces the system's password dialogue.
    ///
    /// The script is handed over through a temporary file rather than inlined
    /// into the AppleScript string, so that no quoting of either language
    /// can go wrong — the very thing that broke this app's release step once.
    private static func runPrivileged(_ script: String, prompt: String) throws {
        let file = FileManager.default.temporaryDirectory
            .appendingPathComponent("veil-proxy-\(UUID().uuidString).sh")
        try script.write(to: file, atomically: true, encoding: .utf8)
        defer { try? FileManager.default.removeItem(at: file) }
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: file.path)

        let apple = """
        do shell script "/bin/sh \(file.path)" with prompt "\(prompt)" with administrator privileges
        """
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        process.arguments = ["-e", apple]
        let errors = Pipe()
        process.standardError = errors
        process.standardOutput = Pipe()
        try process.run()
        process.waitUntilExit()

        guard process.terminationStatus == 0 else {
            let text = String(
                data: errors.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8
            ) ?? ""
            // -128 is AppleScript's "user cancelled": the password dialogue was
            // dismissed, which is a decision rather than a failure.
            if text.contains("-128") { throw Failure.declined }
            throw Failure.failed(text.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }
}

/// A free TCP port on loopback, reserved the only way there is: by binding it
/// and letting go. The same approach the Android build takes, and for the same
/// reason — tor's `auto` binds a different port every time its network is
/// switched off and on, so an address read once cannot be relied on.
enum LoopbackPort {
    /// Several at once, each held until all are known: two reservations made
    /// one after the other can come back with the same port, because the
    /// first is free again by the time the second asks.
    static func reserve(_ count: Int) -> [Int]? {
        var fds: [Int32] = []
        defer { fds.forEach { close($0) } }
        var ports: [Int] = []
        for _ in 0..<count {
            guard let reserved = bindEphemeral() else { return nil }
            let (fd, port) = reserved
            fds.append(fd)
            ports.append(port)
        }
        return ports
    }

    private static func bindEphemeral() -> (Int32, Int)? {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return nil }
        var address = sockaddr_in()
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = 0
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        let bound = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard bound == 0 else { close(fd); return nil }
        var assigned = sockaddr_in()
        var length = socklen_t(MemoryLayout<sockaddr_in>.size)
        let read = withUnsafeMutablePointer(to: &assigned) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                getsockname(fd, $0, &length)
            }
        }
        guard read == 0 else { close(fd); return nil }
        return (fd, Int(UInt16(bigEndian: assigned.sin_port)))
    }
}
