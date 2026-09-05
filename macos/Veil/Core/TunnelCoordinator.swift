import Foundation
import Combine

/// What actually happens when the button is pressed.
///
/// This is the Android build's connection sequence, carried over with its
/// reasoning rather than rewritten, because every step of it is there because
/// something simpler was tried first and was wrong:
///
///  1. The transports start and listen; tor is spawned already declaring them,
///     so a later change of route is a control-port command and never a
///     restart.
///  2. The directory seed is planted if tor has no cache, so the longest part
///     of a first connect — fetching and validating the consensus — is
///     skipped.
///  3. tor bootstraps over the chosen obfuscation, watched with a stall timer
///     that scales with how far it got: below three quarters a silent route is
///     dead, above it a silent route is a slow link finishing.
///  4. **A stream is opened through tor, and only when it comes back is
///     anything called connected.** This is the whole difference between this
///     app and the version of it that said "connected" in one second over a
///     dead link.
///  5. The system tunnel is started, pointed at tor's socket.
///
/// Then the pulse runs, and the supervisor acts on what it measures.
@MainActor
final class TunnelCoordinator: ObservableObject {

    @Published private(set) var state: TunnelState = .idle
    @Published private(set) var stats = TunnelStats()
    @Published private(set) var pulse = PulseState()
    @Published private(set) var circuit: String?
    @Published private(set) var lines: [String] = []
    @Published var settings = VeilSettings()

    /// How the machine's traffic reaches tor once connected.
    ///
    /// The packet tunnel carries everything and needs an entitlement Apple
    /// issues only to a paid team; when macOS will not load it, the system's
    /// proxies are pointed at tor instead. See SystemProxy for what that
    /// covers and what it does not.
    @Published private(set) var mode: Mode?
    /// tor's loopback listeners, for the proxy mode and for Telegram.
    @Published private(set) var loopbackSocksPort = 0
    @Published private(set) var loopbackHTTPPort = 0
    /// The system proxy was left pointing at a previous run of this app —
    /// after a crash, a forced quit or a restart — and nothing outside Veil
    /// works until it is turned off or a new connection replaces it.
    @Published private(set) var staleProxy = SystemProxy.isArmed

    enum Mode { case tunnel, proxy }

    enum CoordinatorFailure: Error, LocalizedError {
        case noPorts
        case tunnelCarriesNothing
        var errorDescription: String? {
            switch self {
            case .noPorts: return "no free loopback port for tor to listen on"
            case .tunnelCarriesNothing: return "the tunnel reported connected but carried no connection"
            }
        }
    }

    private let tor = TorProcess()
    private let control = TorControl()
    private let vpn = VPNManager()
    private let paths = ContainerPaths()

    private var worker: Task<Void, Never>?
    private var supervisor: Task<Void, Never>?
    private var holdTimer: Task<Void, Never>?
    private var redialing = false
    private var connectedSince: Date?

    // MARK: - The knobs, and why they are these numbers

    /// One probe's budget when the answer is believed to be yes.
    private let settledProbeTimeout: TimeInterval = 20
    /// One probe's budget while waiting for a link that is still coming up,
    /// and the gap before the next. In a retry loop the timeout is not
    /// patience, it is the interval at which the question gets asked.
    private let waitingProbeTimeout: TimeInterval = 5
    private let waitingProbeGap: Duration = .milliseconds(500)
    /// How often the pulse beats, and how often it also samples throughput.
    private let pulseInterval: TimeInterval = 20
    private let pulseSpeedInterval: TimeInterval = 300
    /// Unanswered beats on a quiet path before the link is re-dialled.
    private let beatsBeforeRedial = 2
    private let redialCooldown: TimeInterval = 90
    /// How long the link is kept alive after a disconnect, so that a
    /// reconnect within it is instant *and* real rather than instant and
    /// false. Short, because this is idle traffic the user did not ask for.
    private let linkHold: TimeInterval = 45

    // MARK: - Starting and stopping

    func toggle() {
        if state.isLive || state.isBusy { disconnect() } else { connect() }
    }

    func connect() {
        guard worker == nil else { return }
        holdTimer?.cancel(); holdTimer = nil
        worker = Task { [weak self] in
            await self?.run()
            await MainActor.run { self?.worker = nil }
        }
    }

    func disconnect() {
        worker?.cancel(); worker = nil
        supervisor?.cancel(); supervisor = nil
        state = .stopping
        Task {
            await detachTraffic()
            park()
            state = .idle
            pulse = PulseState()
            connectedSince = nil
        }
    }

    /// Takes the machine's traffic back out of tor, whichever way it went in.
    ///
    /// Also what a quit runs before the process ends: a proxy left pointing
    /// at a port nothing listens on is a Mac with no internet, and the app
    /// that did it would not even be running to say why.
    func detachTraffic() async {
        switch mode {
        case .proxy:
            // Blocking, and it may show the password dialogue: off the main
            // thread.
            _ = try? await Task.detached(priority: .userInitiated) { try SystemProxy.disable() }.value
            Core.stopHTTPProxy()
        case .tunnel:
            await vpn.stop()
        case nil:
            break
        }
        mode = nil
        staleProxy = SystemProxy.isArmed
        Core.stopTunnel()
    }

    /// Turns off a proxy left behind by an earlier run, on request.
    func clearStaleProxy() {
        Task {
            _ = try? await Task.detached(priority: .userInitiated) { try SystemProxy.disable() }.value
            staleProxy = SystemProxy.isArmed
            if !staleProxy { log("the system proxy left by an earlier run is off") }
        }
    }

    /// Keeps the link, not just the process.
    ///
    /// For a short window after a disconnect the tunnel is gone but tor's
    /// connection to its bridge is left running, so a reconnect inside it
    /// finds a circuit already there and the first stream goes straight
    /// through. That is the "toggled off, toggled back on" case, which is most
    /// reconnects. After the window the network is switched off, which keeps
    /// everything tor has fetched while costing nothing.
    private func park() {
        holdTimer?.cancel()
        holdTimer = Task { [weak self] in
            guard let self else { return }
            self.log("holding the link warm for \(Int(self.linkHold))s")
            try? await Task.sleep(for: .seconds(self.linkHold))
            guard !Task.isCancelled else { return }
            try? await self.control.setNetworkEnabled(false)
            self.log("link parked: network off, tor kept")
            try? await Task.sleep(for: .seconds(600))
            guard !Task.isCancelled else { return }
            await self.shutDownEngine()
        }
    }

    private func shutDownEngine() async {
        await control.close()
        tor.stop()
        Core.stopTransports()
        log("nothing reconnected; the engine is down")
    }

    // MARK: - The sequence

    private func run() async {
        let began = Date()
        func elapsed() -> String { "+\(Int(Date().timeIntervalSince(began) * 1000))ms" }

        do {
            let transport = settings.transport

            // A tor left warm by the last disconnect is reused. Everything the
            // long part of a connect produces is still in that process.
            if await control.isConnected {
                state = .starting("Возобновляю")
                if await control.hasLiveConnection() {
                    // The link was only held, never dropped. Re-applying the
                    // route here would tear down exactly the connection the
                    // hold kept, turning an instant real reconnect into a slow
                    // one, so it is left alone and merely verified.
                    log("the link was held warm; reusing it as it stands")
                } else {
                    try await control.applyRoute(transport: transport, bridges: Bridges.lines(for: transport))
                    try await control.setNetworkEnabled(true)
                }
            } else {
                try await coldStart(transport: transport, elapsed: elapsed)
            }

            // Bootstrap, watched. On a warm resume this returns at once and
            // says a hundred per cent, which is exactly why it is not the
            // thing anything is decided on.
            state = .bootstrapping(transport, percent: 0, summary: "")
            let bootstrapped = await awaitBootstrap(transport: transport, elapsed: elapsed)
            guard bootstrapped else {
                try? await control.setNetworkEnabled(false)
                state = .failed("\(transport.label): не удалось построить канал")
                return
            }

            // The gate. Nothing below this line runs until a stream has
            // actually reached the internet and come back.
            state = .verifying(transport)
            guard await awaitUsablePath(transport: transport, elapsed: elapsed) else {
                try? await control.setNetworkEnabled(false)
                state = .failed("\(transport.label): канал не пропустил ни одного соединения")
                return
            }

            // Only now does the machine's traffic move — through the tunnel
            // if macOS will load it, through the system proxy if it will not.
            guard await attachTraffic(elapsed: elapsed) else { return }
            connectedSince = Date()
            state = .connected(transport, since: Date())
            startSupervisor()

        } catch is CancellationError {
            log("connect cancelled")
        } catch {
            state = .failed(error.localizedDescription)
            log("connect failed: \(error.localizedDescription)")
        }
    }

    /// Routes the machine's traffic into tor, one way or the other.
    ///
    /// The tunnel is tried first because it is better: every application,
    /// nothing to configure, and nothing left outside. On a build without
    /// Apple's entitlement it fails — usually at saving the configuration,
    /// otherwise by never coming up — and that is the case the proxy exists
    /// for.
    private func attachTraffic(elapsed: () -> String) async -> Bool {
        if await attachTunnel(elapsed: elapsed) { return true }
        return await attachProxy(elapsed: elapsed)
    }

    /// The packet tunnel, if macOS will load it — and proves it, because
    /// "started" is not "up".
    ///
    /// The start request being accepted says nothing about whether the
    /// extension loaded, so the session is watched until it reports
    /// connected, and then one plain connection with no proxy named is made
    /// the ordinary way: the system routes it, and if the tunnel is real it
    /// leaves through a Tor exit. A tunnel that took the default route and
    /// forwards nothing would pass every other test.
    private func attachTunnel(elapsed: () -> String) async -> Bool {
        do {
            try await vpn.start(
                socksSocket: paths.socksSocket,
                blockAds: settings.blockAds,
                blocklist: settings.blockAds ? Blocklist.plant(into: paths.container) : nil,
                blockUDP: settings.blockUDP,
                killSwitch: settings.killSwitch
            )
            try await vpn.awaitConnected(within: 30)
            log("timeline: the tunnel reports connected (\(elapsed())); checking that it carries traffic")
            let carried = await Task.detached(priority: .userInitiated) {
                TCPProbe.reaches("1.1.1.1", port: 443, timeout: 20)
            }.value
            guard carried else { throw CoordinatorFailure.tunnelCarriesNothing }
            Core.resetStats()
            mode = .tunnel
            log("timeline: tunnel attached and carrying traffic (\(elapsed()))")
            return true
        } catch {
            log("the packet tunnel is not available (\(error.localizedDescription)); using the system proxy")
            await vpn.stop()
            return false
        }
    }

    /// The system proxy: tor's SOCKS on loopback, and the app's own HTTP
    /// proxy in front of it for the HTTP and HTTPS settings. Set through the
    /// system's own password dialogue, which runs off the main thread so the
    /// interface does not freeze while it is up.
    private func attachProxy(elapsed: () -> String) async -> Bool {
        let socks = loopbackSocksPort
        do {
            let http = try Core.startHTTPProxy(
                socksNetwork: "unix",
                socksAddress: paths.socksSocket,
                blocklist: settings.blockAds ? Blocklist.plant(into: paths.container) : nil
            )
            loopbackHTTPPort = http
            try await Task.detached(priority: .userInitiated) {
                try SystemProxy.enable(socksPort: socks, httpPort: http)
            }.value
            mode = .proxy
            staleProxy = false
            log("timeline: system proxy points at tor — socks \(socks), http \(http) (\(elapsed()))")
            return true
        } catch {
            Core.stopHTTPProxy()
            try? await control.setNetworkEnabled(false)
            state = .failed("Туннель не загрузился, а системный прокси не удалось включить: \(error.localizedDescription)")
            return false
        }
    }

    private func coldStart(transport: Transport, elapsed: () -> String) async throws {
        state = .starting("Запускаю транспорты")
        let ports = try Core.startTransports(stateDirectory: paths.transportState) { [weak self] line in
            Task { @MainActor in self?.log(line) }
        }
        log("timeline: transports up (\(elapsed()))")

        // The network directory, shipped in the app. Planted only when tor has
        // no cache of its own, so a running installation is never disturbed.
        DirectorySeed.plantIfNeeded(into: paths.torData) { [weak self] line in
            Task { @MainActor in self?.log(line) }
        }

        state = .starting("Запускаю Tor")
        guard let socksPort = LoopbackPort.reserve(1)?.first else { throw CoordinatorFailure.noPorts }
        loopbackSocksPort = socksPort
        let session = Torrc.Session(
            dataDirectory: paths.torData,
            socksSocket: paths.socksSocket,
            socksPort: socksPort,
            controlSocket: paths.controlSocket,
            cookieFile: paths.cookieFile,
            dnsPort: 0,
            plugins: ports,
            opening: (transport, Bridges.lines(for: transport)),
            geoipDirectory: Bundle.main.resourceURL?.appendingPathComponent("tor").path
        )
        tor.onLine = { [weak self] line in Task { @MainActor in self?.absorbTorLine(line) } }
        try await tor.start(session: session, torrcPath: paths.torrc)
        try await control.connect(socketPath: paths.controlSocket, cookiePath: paths.cookieFile)
        try await control.setEvents(["STATUS_CLIENT", "NOTICE", "WARN", "ERR"])
        log("timeline: tor ready for a route (\(elapsed()))")
    }

    /// Waits for tor to finish bootstrapping, giving up when the budget runs
    /// out or when progress stops moving.
    ///
    /// The stall check is what separates a tool that gives up in half a minute
    /// from one that leaves the user staring at 10% for five, and it scales
    /// with progress on purpose: below about three quarters tor is still
    /// trying to reach the bridge and silence means a dead route, while above
    /// it the link is up and silence is what a slow path looks like.
    private func awaitBootstrap(transport: Transport, elapsed: () -> String) async -> Bool {
        let deadline = Date().addingTimeInterval(transport.budget)
        var lastPercent = -1
        var lastMovement = Date()

        while Date() < deadline, !Task.isCancelled {
            guard let progress = try? await control.bootstrap() else { return false }
            if progress.percent != lastPercent {
                lastPercent = progress.percent
                lastMovement = Date()
                state = .bootstrapping(transport, percent: progress.percent, summary: progress.summary)
                log("timeline: \(transport.label) \(progress.percent)% \(progress.summary) (\(elapsed()))")
            }
            if progress.isDone { return true }

            let quiet = Date().timeIntervalSince(lastMovement)
            // Tor knows before we do: a route that handshakes and is then cut
            // reports the same percentage with a rising failure count.
            if progress.isHopeless, quiet > 12 {
                log("\(transport.label) failed \(progress.problems) times at \(lastPercent)%")
                return false
            }
            if quiet > stallAllowance(at: lastPercent) {
                log("\(transport.label) stalled at \(lastPercent)%")
                return false
            }
            try? await Task.sleep(for: .milliseconds(400))
        }
        return false
    }

    private func stallAllowance(at percent: Int) -> TimeInterval {
        if percent >= 90 { return 60 }
        if percent >= 75 { return 45 }
        return 25
    }

    /// Waits for the tunnel to actually carry traffic.
    ///
    /// Patient rather than strict: a first stream over Snowflake, and every
    /// stream immediately after a warm resume while a proxy is found again,
    /// can take tens of seconds, and failing the connection for that would be
    /// the old mistake of calling a working tunnel dead. Halfway through it
    /// asks tor for a fresh circuit once, in case the one it built is through
    /// a proxy that has already gone.
    private func awaitUsablePath(transport: Transport, elapsed: () -> String) async -> Bool {
        let began = Date()
        let deadline = began.addingTimeInterval(transport.verifyBudget)
        var kicked = false
        var tries = 0

        while Date() < deadline, !Task.isCancelled {
            tries += 1
            if probe(timeout: waitingProbeTimeout) {
                log("timeline: a stream reached the internet on try \(tries) (\(elapsed()))")
                // Spares, now, before anything on the machine asks.
                await control.ensureSpareCircuit()
                circuit = await control.describeCircuit()
                return true
            }
            if !kicked, Date().timeIntervalSince(began) > transport.verifyBudget / 2 {
                kicked = true
                log("no stream yet; asking tor for a fresh circuit")
                await control.ensureSpareCircuit()
            }
            try? await Task.sleep(for: waitingProbeGap)
        }
        return false
    }

    /// One real stream through tor. The only test this file trusts.
    private func probe(timeout: TimeInterval) -> Bool {
        guard let socket = try? Socks5.connect(
            socketPath: paths.socksSocket, host: "one.one.one.one", port: 443, timeout: timeout
        ) else { return false }
        socket.close()
        return true
    }

    // MARK: - While it is up

    private func startSupervisor() {
        supervisor?.cancel()
        supervisor = Task { [weak self] in
            guard let self else { return }
            var lastBeat = Date.distantPast
            var lastSpeed = Date.distantPast
            var lastEvidence = Date()
            var lastRx: Int64 = -1
            var lastTx: Int64 = -1
            var failures = 0
            var lastRedial = Date.distantPast

            while !Task.isCancelled {
                // With a tunnel the counters are the tunnel's own. Without
                // one there is no tunnel to count, and tor's totals over the
                // control port are the truth instead.
                let snapshot: TunnelStats
                if await MainActor.run(body: { self.mode }) == .proxy {
                    let bytes = await self.control.trafficBytes() ?? (0, 0)
                    snapshot = TunnelStats(
                        rxBytes: bytes.0, txBytes: bytes.1,
                        dnsBlocked: Core.snapshot().dnsBlocked
                    )
                } else {
                    snapshot = Core.snapshot()
                }
                await MainActor.run { self.stats = snapshot }

                // Evidence first: bytes moving mean the path is there and
                // nothing more needs asking.
                if snapshot.rxBytes != lastRx || snapshot.txBytes != lastTx {
                    lastRx = snapshot.rxBytes; lastTx = snapshot.txBytes
                    lastEvidence = Date()
                }

                let wantsBeat = await MainActor.run { self.settings.pulse }
                let interval = wantsBeat ? self.pulseInterval : 45
                let due = Date().timeIntervalSince(lastBeat) > interval
                let quiet = Date().timeIntervalSince(lastEvidence) > 45
                let redialing = await MainActor.run { self.redialing }

                if due, wantsBeat || quiet, !redialing {
                    lastBeat = Date()
                    let socketPath = self.paths.socksSocket
                    let beat = await Task.detached(priority: .utility) {
                        try? Socks5.timedRequest(
                            socketPath: socketPath, host: "cp.cloudflare.com",
                            path: "/generate_204", timeout: 12
                        )
                    }.value

                    if let beat {
                        failures = 0
                        lastEvidence = Date()
                        var rate = await MainActor.run { self.pulse.kilobytesPerSecond }
                        if wantsBeat, Date().timeIntervalSince(lastSpeed) > self.pulseSpeedInterval {
                            lastSpeed = Date()
                            let measured = await Task.detached(priority: .utility) {
                                try? Socks5.throughput(
                                    socketPath: socketPath, host: "speed.cloudflare.com",
                                    path: "/__down?bytes=65536", timeout: 25
                                )
                            }.value
                            if let measured { rate = measured }
                        }
                        let path = await self.control.describeCircuit()
                        await MainActor.run {
                            self.pulse = PulseState(
                                rttMillis: beat.ttfbMillis, kilobytesPerSecond: rate,
                                measuredAt: Date(), failures: 0, ok: true
                            )
                            self.circuit = path
                        }
                    } else if Date().timeIntervalSince(lastEvidence) < interval {
                        // Traffic is moving; the beat merely lost a race with
                        // it. Not a failure.
                    } else {
                        failures += 1
                        // Whatever is wrong, a circuit standing by makes the
                        // recovery instant rather than another wait.
                        await self.control.ensureSpareCircuit()
                        await MainActor.run {
                            self.pulse = PulseState(
                                rttMillis: self.pulse.rttMillis,
                                kilobytesPerSecond: self.pulse.kilobytesPerSecond,
                                measuredAt: Date(), failures: failures, ok: false
                            )
                            self.log("pulse: no answer (\(failures)/\(self.beatsBeforeRedial))")
                        }
                        if failures >= self.beatsBeforeRedial,
                           Date().timeIntervalSince(lastRedial) > self.redialCooldown {
                            lastRedial = Date()
                            failures = 0
                            await self.redial("the pulse went unanswered")
                        }
                    }
                }
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    /// Re-dials the bridges, holding applications' connections open while it
    /// happens so they do not back off and take minutes to come back.
    private func redial(_ reason: String) async {
        guard !redialing else { return }
        redialing = true
        defer { redialing = false; Core.setRebuilding(false) }

        log("re-dialling: \(reason)")
        Core.setRebuilding(true)
        try? await control.redial()

        let deadline = Date().addingTimeInterval(45)
        while Date() < deadline, !Task.isCancelled {
            if probe(timeout: waitingProbeTimeout) {
                log("the path is back")
                await control.ensureSpareCircuit()
                return
            }
            try? await Task.sleep(for: waitingProbeGap)
        }
        log("the path is still down after 45s")
    }

    func newCircuit() {
        Task { await control.newIdentity() }
    }

    // MARK: - Log

    private func absorbTorLine(_ line: String) {
        log(line)
    }

    func log(_ text: String) {
        let stamp = Self.clock.string(from: Date())
        lines.append("\(stamp)  \(text)")
        if lines.count > 600 { lines.removeFirst(lines.count - 600) }
    }

    private static let clock: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()
}

/// Where everything lives inside the App Group container.
///
/// The container rather than the app's own directory because the extension
/// has to reach tor's socket, and a group container is the only place both
/// can see.
struct ContainerPaths {
    static let appGroup = "group.app.veil.mac"

    let container: String

    init() {
        // The group container when there is one; otherwise the app's own
        // Application Support, which is where an unsandboxed build belongs.
        // Never a temporary directory: tor's directory cache lives here, and
        // a cache that vanishes is a first connect that downloads it again.
        let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroup)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("Veil")
        container = url.path
        for directory in [torData, transportState, sockets] {
            try? FileManager.default.createDirectory(
                atPath: directory, withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o700]
            )
            // Whatever it was created with before, by an earlier build or by
            // the system: tor refuses to put a socket in a directory anyone
            // else can list, and a refused listener is a tor that exits.
            try? FileManager.default.setAttributes(
                [.posixPermissions: 0o700], ofItemAtPath: directory
            )
        }
    }

    var torData: String { container + "/tor" }
    var transportState: String { container + "/pt" }
    var torrc: String { container + "/torrc" }
    /// The sockets' own directory, mode 0700, which is what tor insists on
    /// for the directory that holds a unix socket.
    var sockets: String { container + "/run" }
    // Socket paths live in sockaddr_un, which is 104 bytes on darwin, so they
    // are kept short deliberately: a group container path is already long.
    var socksSocket: String { sockets + "/s.sock" }
    var controlSocket: String { sockets + "/c.sock" }
    var cookieFile: String { torData + "/cookie" }
}
