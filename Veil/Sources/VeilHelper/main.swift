import Foundation
import SystemConfiguration
import VeilShared

// Помощник Veil: служба launchd с правами root.
//   VeilHelper --uid 501 [--socket путь] [--dir папка]
//
// Делает только то, на что обычной программе не хватает прав: включает системный прокси и
// DNS (через SystemConfiguration, запоминая прежние настройки пользователя), запускает ядро
// в режиме TUN. Сторож каждые 5 секунд следит, чтобы Mac не остался без интернета: если прокси
// включён, а Veil не отвечает на его порту, — настройки возвращаются.

setvbuf(stdout, nil, _IOLBF, 0)
setvbuf(stderr, nil, _IOLBF, 0)

nonisolated final class Helper: @unchecked Sendable {
    struct State: Codable {
        var proxy: ProxyState?
        /// Прежние настройки прокси служб (plist), чтобы вернуть ровно то, что было.
        var proxyBackup: [String: Data] = [:]
        /// Прежние DNS служб на время TUN.
        var dnsBackup: [String: Data] = [:]
        var tun = false
    }

    struct ProxyState: Codable {
        var httpPort: Int
        var socksPort: Int
    }

    let allowedUID: uid_t
    let socketPath: String
    let directory: String
    private let queue = DispatchQueue(label: "app.veil.helper")
    private var state = State()
    private var tunProcess: Process?
    private var tunLog: [String] = []
    private var proxyMisses = 0

    init(uid: uid_t, socket: String, directory: String) {
        allowedUID = uid
        socketPath = socket
        self.directory = directory
    }

    var statePath: String { directory + "/state.json" }
    var corePath: String { directory + "/xray" }
    var assetsPath: String { directory + "/assets" }

    func log(_ text: String) {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        FileHandle.standardError.write(Data("\(f.string(from: Date())) \(text)\n".utf8))
    }

    // MARK: - Запуск

    func run() {
        queue.sync {
            loadState()
            // После перезапуска (сбой, перезагрузка) ядра TUN уже нет — возвращаем DNS.
            if state.tun || !state.dnsBackup.isEmpty {
                restoreDNS()
                state.tun = false
                saveState()
            }
        }
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 5, repeating: 5)
        timer.setEventHandler { [unowned self] in self.watchdog() }
        timer.resume()
        let thread = Thread { [self] in self.acceptLoop() }
        thread.start()
        log("помощник Veil \(HelperInfo.version) запущен, uid \(allowedUID), сокет \(socketPath)")
        withExtendedLifetime(timer) { dispatchMain() }
    }

    private func loadState() {
        guard let data = FileManager.default.contents(atPath: statePath),
              let s = try? JSONDecoder().decode(State.self, from: data) else { return }
        state = s
    }

    private func saveState() {
        try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(state) {
            FileManager.default.createFile(atPath: statePath, contents: data, attributes: [.posixPermissions: 0o600])
        }
    }

    // MARK: - Сокет

    private func acceptLoop() {
        unlink(socketPath)
        let fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { log("socket: \(String(cString: strerror(errno)))"); exit(1) }
        var addr = sockaddr_un()
        guard HelperSocket.fill(&addr, path: socketPath) else { log("путь сокета слишком длинный"); exit(1) }
        let bound = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { bind(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size)) }
        }
        guard bound == 0 else { log("bind: \(String(cString: strerror(errno)))"); exit(1) }
        // Подключиться может любой, но отвечаем только владельцу (проверка uid ниже).
        chmod(socketPath, 0o666)
        listen(fd, 16)
        while true {
            let client = accept(fd, nil, nil)
            if client < 0 { continue }
            DispatchQueue.global().async { [self] in self.serve(client) }
        }
    }

    private func serve(_ client: Int32) {
        defer { close(client) }
        var tv = timeval(tv_sec: 30, tv_usec: 0)
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        var uid: uid_t = 0, gid: gid_t = 0
        guard getpeereid(client, &uid, &gid) == 0, uid == allowedUID || uid == 0 else {
            reply(client, HelperResponse(ok: false, message: "Помощник Veil принадлежит другому пользователю"))
            return
        }
        guard let line = HelperSocket.readLine(client),
              let request = try? JSONDecoder().decode(HelperRequest.self, from: line) else {
            reply(client, HelperResponse(ok: false, message: "непонятный запрос"))
            return
        }
        let response = queue.sync { handle(request) }
        reply(client, response)
    }

    private func reply(_ fd: Int32, _ response: HelperResponse) {
        guard var data = try? JSONEncoder().encode(response) else { return }
        data.append(0x0A)
        try? HelperSocket.writeAll(fd, data)
    }

    // MARK: - Команды

    private func handle(_ r: HelperRequest) -> HelperResponse {
        switch r.command {
        case .status:
            return status(ok: true)
        case .proxyOn:
            guard let http = r.httpPort, let socks = r.socksPort, (1024...65535).contains(http), (1024...65535).contains(socks) else {
                return HelperResponse(ok: false, message: "неверные порты")
            }
            let bypass = r.bypass ?? ["127.0.0.1", "localhost", "*.local", "169.254/16", "10/8", "172.16/12", "192.168/16"]
            guard setProxy(http: http, socks: socks, bypass: bypass) else { return HelperResponse(ok: false, message: "macOS не приняла настройки прокси") }
            state.proxy = ProxyState(httpPort: http, socksPort: socks)
            proxyMisses = 0
            saveState()
            log("системный прокси включён: HTTP \(http), SOCKS \(socks)")
            return status(ok: true)
        case .proxyOff:
            restoreProxy()
            return status(ok: true)
        case .tunStart:
            return startTun(r)
        case .tunStop:
            stopTun()
            return status(ok: true)
        }
    }

    private func status(ok: Bool, message: String = "") -> HelperResponse {
        HelperResponse(ok: ok, message: message, proxyOn: state.proxy != nil,
                       tunRunning: tunProcess?.isRunning ?? false, log: Array(tunLog.suffix(30)))
    }

    // MARK: - Системный прокси

    /// Изменение настроек всех включённых сетевых служб (Wi-Fi, Ethernet, …) одной транзакцией.
    private func modifyServices(_ protocolType: CFString, _ change: (String, [String: Any]) -> [String: Any]?) -> Bool {
        guard let prefs = SCPreferencesCreate(nil, "app.veil.helper" as CFString, nil) else { return false }
        guard SCPreferencesLock(prefs, true) else { return false }
        defer { SCPreferencesUnlock(prefs) }
        guard let set = SCNetworkSetCopyCurrent(prefs),
              let services = SCNetworkSetCopyServices(set) as? [SCNetworkService] else { return false }
        for service in services where SCNetworkServiceGetEnabled(service) {
            guard let id = SCNetworkServiceGetServiceID(service) as String?,
                  let proto = SCNetworkServiceCopyProtocol(service, protocolType) else { continue }
            let current = SCNetworkProtocolGetConfiguration(proto) as? [String: Any] ?? [:]
            if let updated = change(id, current) {
                SCNetworkProtocolSetConfiguration(proto, updated.isEmpty ? nil : updated as CFDictionary)
            }
        }
        let ok = SCPreferencesCommitChanges(prefs) && SCPreferencesApplyChanges(prefs)
        SCPreferencesSynchronize(prefs)
        return ok
    }

    private func setProxy(http: Int, socks: Int, bypass: [String]) -> Bool {
        let firstTime = state.proxy == nil
        return modifyServices(kSCNetworkProtocolTypeProxies) { id, current in
            if firstTime, let data = try? PropertyListSerialization.data(fromPropertyList: current, format: .binary, options: 0) {
                state.proxyBackup[id] = data
            }
            var c = current
            c[kSCPropNetProxiesHTTPEnable as String] = 1
            c[kSCPropNetProxiesHTTPProxy as String] = "127.0.0.1"
            c[kSCPropNetProxiesHTTPPort as String] = http
            c[kSCPropNetProxiesHTTPSEnable as String] = 1
            c[kSCPropNetProxiesHTTPSProxy as String] = "127.0.0.1"
            c[kSCPropNetProxiesHTTPSPort as String] = http
            c[kSCPropNetProxiesSOCKSEnable as String] = 1
            c[kSCPropNetProxiesSOCKSProxy as String] = "127.0.0.1"
            c[kSCPropNetProxiesSOCKSPort as String] = socks
            c[kSCPropNetProxiesExceptionsList as String] = bypass
            c[kSCPropNetProxiesExcludeSimpleHostnames as String] = 1
            c[kSCPropNetProxiesProxyAutoConfigEnable as String] = 0
            return c
        }
    }

    private func restoreProxy() {
        let backup = state.proxyBackup
        _ = modifyServices(kSCNetworkProtocolTypeProxies) { id, current in
            if let data = backup[id], let old = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] {
                return old
            }
            // Нет копии — просто выключаем наше.
            var c = current
            for key in [kSCPropNetProxiesHTTPEnable, kSCPropNetProxiesHTTPSEnable, kSCPropNetProxiesSOCKSEnable] { c[key as String] = 0 }
            return c
        }
        if state.proxy != nil { log("системный прокси выключен, прежние настройки возвращены") }
        state.proxy = nil
        state.proxyBackup = [:]
        saveState()
    }

    // MARK: - TUN

    private func startTun(_ r: HelperRequest) -> HelperResponse {
        stopTun()
        guard let raw = r.config else { return HelperResponse(ok: false, message: "нет конфигурации") }
        let config: String
        do { config = try ConfigSanitizer.sanitize(raw) } catch {
            return HelperResponse(ok: false, message: "конфигурация отклонена: \(error)")
        }
        guard FileManager.default.isExecutableFile(atPath: corePath) else {
            return HelperResponse(ok: false, message: "у помощника нет ядра — переустановите помощника")
        }
        // Базы сайтов — копия у себя: ядро с правами root читает только файлы root.
        try? FileManager.default.createDirectory(atPath: assetsPath, withIntermediateDirectories: true)
        if let assets = r.assets {
            for name in ["geoip.dat", "geosite.dat"] {
                let src = (assets as NSString).appendingPathComponent(name)
                var st = stat()
                guard lstat(src, &st) == 0, (st.st_mode & S_IFMT) == S_IFREG, st.st_size < 400 << 20 else { continue }
                let dst = (assetsPath as NSString).appendingPathComponent(name)
                try? FileManager.default.removeItem(atPath: dst)
                try? FileManager.default.copyItem(atPath: src, toPath: dst)
                chmod(dst, 0o644)
            }
        }
        let file = directory + "/tun.json"
        FileManager.default.createFile(atPath: file, contents: Data(config.utf8), attributes: [.posixPermissions: 0o600])

        let p = Process()
        p.executableURL = URL(fileURLWithPath: corePath)
        p.arguments = ["run", "-c", file]
        p.environment = ["XRAY_LOCATION_ASSET": assetsPath, "PATH": "/usr/bin:/bin:/usr/sbin:/sbin"]
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        let started = DispatchSemaphore(value: 0)
        let lines = LineBox()
        pipe.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            guard !data.isEmpty else { handle.readabilityHandler = nil; return }
            for line in String(decoding: data, as: UTF8.self).split(separator: "\n") {
                lines.append(String(line))
                if line.contains("Xray") && line.contains(" started") { started.signal() }
            }
        }
        p.terminationHandler = { _ in started.signal() }
        do { try p.run() } catch {
            return HelperResponse(ok: false, message: "ядро не запустилось: \(error.localizedDescription)")
        }
        _ = started.wait(timeout: .now() + 10)
        tunLog = lines.all
        guard p.isRunning else {
            let why = tunLog.last(where: { $0.contains("rror") || $0.contains("ailed") }) ?? "ядро завершилось с кодом \(p.terminationStatus)"
            return HelperResponse(ok: false, message: why, log: Array(tunLog.suffix(30)))
        }
        tunProcess = p
        lineBox = lines
        state.tun = true
        // DNS системы — на адреса, пакеты к которым идут через туннель (их отвечает DNS ядра).
        setDNS(r.dns ?? ["1.1.1.1", "8.8.8.8"])
        saveState()
        log("TUN запущен")
        return status(ok: true)
    }

    private var lineBox: LineBox?

    private func stopTun() {
        if let p = tunProcess, p.isRunning {
            p.terminate()
            let deadline = Date().addingTimeInterval(4)
            while p.isRunning && Date() < deadline { usleep(20_000) }
            if p.isRunning { kill(p.processIdentifier, SIGKILL) }
            log("TUN остановлен")
        }
        tunProcess = nil
        if state.tun || !state.dnsBackup.isEmpty { restoreDNS() }
        state.tun = false
        saveState()
    }

    private func setDNS(_ servers: [String]) {
        let firstTime = state.dnsBackup.isEmpty
        _ = modifyServices(kSCNetworkProtocolTypeDNS) { id, current in
            if firstTime, let data = try? PropertyListSerialization.data(fromPropertyList: current, format: .binary, options: 0) {
                state.dnsBackup[id] = data
            }
            var c = current
            c[kSCPropNetDNSServerAddresses as String] = servers
            return c
        }
    }

    private func restoreDNS() {
        let backup = state.dnsBackup
        guard !backup.isEmpty else { return }
        _ = modifyServices(kSCNetworkProtocolTypeDNS) { id, _ in
            guard let data = backup[id] else { return nil }
            return (try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]) ?? [:]
        }
        state.dnsBackup = [:]
        log("DNS системы возвращены")
    }

    // MARK: - Сторож

    private func watchdog() {
        if let box = lineBox { tunLog = box.all }
        if state.tun, tunProcess?.isRunning != true {
            log("ядро TUN завершилось само — возвращаю DNS")
            tunProcess = nil
            restoreDNS()
            state.tun = false
            saveState()
        }
        if let proxy = state.proxy {
            if Self.listening(port: proxy.httpPort) {
                proxyMisses = 0
            } else {
                proxyMisses += 1
                // 15 секунд никто не слушает порт прокси (Veil закрылся или упал) — без интернета не оставляем.
                if proxyMisses >= 3 {
                    log("порт \(proxy.httpPort) не отвечает — выключаю системный прокси")
                    restoreProxy()
                }
            }
        }
    }

    static func listening(port: Int) -> Bool {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = in_port_t(UInt16(port).bigEndian)
        addr.sin_addr.s_addr = inet_addr("127.0.0.1")
        return withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
        } == 0
    }

    final class LineBox: @unchecked Sendable {
        private let lock = NSLock()
        private var lines: [String] = []
        func append(_ s: String) { lock.withLock { lines.append(s); if lines.count > 200 { lines.removeFirst(lines.count - 200) } } }
        var all: [String] { lock.withLock { lines } }
    }
}

var uid: uid_t = getuid()
var socketPath = HelperInfo.socketPath
var directory = HelperInfo.directory
var args = CommandLine.arguments.dropFirst().makeIterator()
while let arg = args.next() {
    switch arg {
    case "--uid": uid = uid_t(args.next().flatMap { UInt32($0) } ?? getuid())
    case "--socket": socketPath = args.next() ?? socketPath
    case "--dir": directory = args.next() ?? directory
    case "--version": print(HelperInfo.version); exit(0)
    default: break
    }
}
guard getuid() == 0 || ProcessInfo.processInfo.environment["VEIL_HELPER_UNPRIVILEGED"] != nil else {
    FileHandle.standardError.write(Data("VeilHelper нужно запускать с правами root (его ставит Veil)\n".utf8))
    exit(1)
}
Helper(uid: uid, socket: socketPath, directory: directory).run()
