import Darwin
import Foundation

// MARK: - Автозапуск (launchd)

nonisolated struct LaunchItem: Identifiable, Hashable, Sendable {
    nonisolated enum Domain: String, Sendable { case user, systemAgent, systemDaemon }

    var id: String { url.path }
    var url: URL
    var label: String
    var program: String?
    var domain: Domain
    var enabled: Bool
    var runAtLoad: Bool
    var keepAlive: Bool
    /// Программа-хозяин (по пути к исполняемому файлу или по идентификатору).
    var ownerName: String?
    var ownerURL: URL?
    var ownerMissing: Bool

    var needsAdmin: Bool { domain != .user }
}

nonisolated enum LaunchItems {
    static func disabledStore(home: URL) -> URL {
        home.appendingPathComponent("Library/Application Support/Sweep/Disabled LaunchAgents")
    }
    static let systemDisabledStore = "/Library/Application Support/Sweep Disabled"

    static func scan(home: URL, installed: InstalledIndex) -> [LaunchItem] {
        var items: [LaunchItem] = []
        let places: [(URL, LaunchItem.Domain, Bool)] = [
            (home.appendingPathComponent("Library/LaunchAgents"), .user, true),
            (disabledStore(home: home), .user, false),
            (URL(fileURLWithPath: "/Library/LaunchAgents"), .systemAgent, true),
            (URL(fileURLWithPath: "/Library/LaunchDaemons"), .systemDaemon, true),
            (URL(fileURLWithPath: systemDisabledStore + "/LaunchAgents"), .systemAgent, false),
            (URL(fileURLWithPath: systemDisabledStore + "/LaunchDaemons"), .systemDaemon, false),
        ]
        for (dir, domain, enabled) in places {
            for plist in DiskUsage.children(of: dir) where plist.pathExtension == "plist" {
                guard let dict = NSDictionary(contentsOf: plist) as? [String: Any] else { continue }
                let label = (dict["Label"] as? String) ?? plist.deletingPathExtension().lastPathComponent
                if label.hasPrefix("com.apple.") { continue }
                let program = (dict["Program"] as? String) ?? (dict["ProgramArguments"] as? [String])?.first
                let keepAlive = (dict["KeepAlive"] as? Bool) ?? (dict["KeepAlive"] is [String: Any])
                var ownerName: String?, ownerURL: URL?
                if let program, let range = program.range(of: ".app/") {
                    let appPath = String(program[..<range.lowerBound]) + ".app"
                    ownerURL = URL(fileURLWithPath: appPath)
                    ownerName = ownerURL?.deletingPathExtension().lastPathComponent
                } else if let app = installed.apps.first(where: { app in app.allIDs.contains { label.hasPrefix($0) } }) {
                    ownerURL = app.url
                    ownerName = app.name
                }
                let missing = program.map { !FileManager.default.fileExists(atPath: $0) } ?? false
                items.append(LaunchItem(url: plist, label: label, program: program, domain: domain, enabled: enabled,
                                        runAtLoad: (dict["RunAtLoad"] as? Bool) ?? false, keepAlive: keepAlive,
                                        ownerName: ownerName, ownerURL: ownerURL, ownerMissing: missing))
            }
        }
        return items.sorted { ($0.ownerName ?? $0.label).localizedCaseInsensitiveCompare($1.ownerName ?? $1.label) == .orderedAscending }
    }

    /// Выключение обратимо: plist переезжает в нашу папку, задание выгружается. Включение — наоборот.
    @MainActor
    static func setEnabled(_ item: LaunchItem, _ enabled: Bool, home: URL) -> Result<Void, RemovalFailure> {
        let uid = getuid()
        let name = item.url.lastPathComponent
        switch item.domain {
        case .user:
            let fm = FileManager.default
            let store = disabledStore(home: home)
            let active = home.appendingPathComponent("Library/LaunchAgents/\(name)")
            let parked = store.appendingPathComponent(name)
            do {
                if enabled {
                    try fm.moveItem(at: parked, to: active)
                    launchctl(["bootstrap", "gui/\(uid)", active.path])
                } else {
                    launchctl(["bootout", "gui/\(uid)", item.url.path])
                    try fm.createDirectory(at: store, withIntermediateDirectories: true)
                    try fm.moveItem(at: item.url, to: parked)
                }
                return .success(())
            } catch {
                return .failure(RemovalFailure(path: item.url.path, reason: Remover.describe(error)))
            }
        case .systemAgent, .systemDaemon:
            let folder = item.domain == .systemDaemon ? "LaunchDaemons" : "LaunchAgents"
            let active = "/Library/\(folder)/\(name)"
            let parked = "\(systemDisabledStore)/\(folder)/\(name)"
            let q = Remover.shellQuote
            let commands = enabled
                ? ["/bin/mv \(q(parked)) \(q(active))", "/usr/sbin/chown root:wheel \(q(active))",
                   item.domain == .systemDaemon ? "/bin/launchctl bootstrap system \(q(active)) 2>/dev/null || true" : "true"]
                : [item.domain == .systemDaemon ? "/bin/launchctl bootout system \(q(active)) 2>/dev/null || true" : "true",
                   "/bin/mkdir -p \(q("\(systemDisabledStore)/\(folder)"))", "/bin/mv \(q(active)) \(q(parked))"]
            return Admin.run(commands).map { _ in () }
        }
    }

    @discardableResult
    static func launchctl(_ args: [String]) -> Int32 {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/bin/launchctl")
        p.arguments = args
        p.standardOutput = FileHandle.nullDevice
        p.standardError = FileHandle.nullDevice
        do { try p.run() } catch { return -1 }
        p.waitUntilExit()
        return p.terminationStatus
    }
}

// MARK: - Обслуживание

nonisolated enum MaintenanceTask: String, CaseIterable, Identifiable, Sendable {
    case flushDNS, freeMemory, reindexSpotlight, rebuildLaunchServices, quickLookCache, thinSnapshots, fontCache

    var id: String { rawValue }

    var title: String {
        switch self {
        case .flushDNS: "Сбросить кэш DNS"
        case .freeMemory: "Освободить оперативную память"
        case .reindexSpotlight: "Переиндексировать Spotlight"
        case .rebuildLaunchServices: "Перестроить базу «Открыть с помощью»"
        case .quickLookCache: "Очистить миниатюры Quick Look"
        case .thinSnapshots: "Удалить локальные снимки Time Machine"
        case .fontCache: "Перестроить кэш шрифтов"
        }
    }

    var explanation: String {
        switch self {
        case .flushDNS: "Помогает, если сайты не открываются после смены сети или DNS."
        case .freeMemory: "Выгружает дисковый кэш из памяти. macOS управляет памятью сама — нужно редко, например перед тяжёлой задачей."
        case .reindexSpotlight: "Если поиск не находит файлы. Индекс строится заново час-другой в фоне."
        case .rebuildLaunchServices: "Убирает дубли и удалённые программы из меню «Открыть с помощью»."
        case .quickLookCache: "Если превью файлов показывают старое содержимое."
        case .thinSnapshots: "Снимки, которые Time Machine хранит на диске до следующей копии. Освобождает «очищаемое» место сразу."
        case .fontCache: "Если шрифты отображаются неправильно. Перезагрузите Mac после выполнения."
        }
    }

    var symbol: String {
        switch self {
        case .flushDNS: "network"
        case .freeMemory: "memorychip.fill"
        case .reindexSpotlight: "magnifyingglass"
        case .rebuildLaunchServices: "square.grid.3x3.square"
        case .quickLookCache: "eye.fill"
        case .thinSnapshots: "clock.arrow.circlepath"
        case .fontCache: "textformat"
        }
    }

    var needsAdmin: Bool {
        switch self {
        case .flushDNS, .freeMemory, .reindexSpotlight, .thinSnapshots: true
        case .rebuildLaunchServices, .quickLookCache, .fontCache: false
        }
    }

    /// Рекомендуется в «Умной проверке» (безопасно и полезно в любой момент).
    var recommended: Bool { [.flushDNS, .quickLookCache, .rebuildLaunchServices].contains(self) }

    var command: String {
        switch self {
        case .flushDNS: "/usr/bin/dscacheutil -flushcache; /usr/bin/killall -HUP mDNSResponder"
        case .freeMemory: "/usr/sbin/purge"
        case .reindexSpotlight: "/usr/bin/mdutil -E / >/dev/null"
        case .rebuildLaunchServices:
            "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -r -domain local -domain system -domain user"
        case .quickLookCache: "/usr/bin/qlmanage -r cache >/dev/null 2>&1; /usr/bin/qlmanage -r >/dev/null 2>&1"
        case .thinSnapshots: "/usr/bin/tmutil thinlocalsnapshots / 999999999999 4 >/dev/null"
        case .fontCache: "/usr/bin/atsutil databases -removeUser >/dev/null 2>&1; /usr/bin/atsutil server -shutdown >/dev/null 2>&1; /usr/bin/atsutil server -ping >/dev/null 2>&1"
        }
    }

    /// Выполняет задачи без прав администратора; возвращает команды, которым права нужны.
    static func runUserTasks(_ tasks: [MaintenanceTask]) -> [MaintenanceTask: Bool] {
        var results: [MaintenanceTask: Bool] = [:]
        for task in tasks where !task.needsAdmin {
            let p = Process()
            p.executableURL = URL(fileURLWithPath: "/bin/sh")
            p.arguments = ["-c", task.command]
            p.standardOutput = FileHandle.nullDevice
            p.standardError = FileHandle.nullDevice
            do {
                try p.run()
                p.waitUntilExit()
                results[task] = p.terminationStatus == 0
            } catch {
                results[task] = false
            }
        }
        return results
    }
}

// MARK: - Состояние системы

nonisolated struct MemoryStats: Sendable {
    var total: UInt64 = ProcessInfo.processInfo.physicalMemory
    var used: UInt64 = 0
    var wired: UInt64 = 0
    var compressed: UInt64 = 0
    var cached: UInt64 = 0
    var free: UInt64 = 0
    /// 1 — норма, 2 — предупреждение, 4 — критично.
    var pressure: Int = 1

    var usedFraction: Double { total > 0 ? Double(used) / Double(total) : 0 }
}

nonisolated struct DiskStats: Sendable {
    var total: Int64 = 0
    var available: Int64 = 0
    /// Доступно с учётом «очищаемого» места (кэши iCloud, снимки Time Machine).
    var availableImportant: Int64 = 0
    var usedFraction: Double { total > 0 ? 1 - Double(availableImportant) / Double(total) : 0 }
    var purgeable: Int64 { max(0, availableImportant - available) }
}

nonisolated struct ProcessStat: Identifiable, Sendable, Hashable {
    var id: Int32 { pid }
    var pid: Int32
    var name: String
    var memory: UInt64
    var cpu: Double
}

nonisolated enum SystemStats {
    static func memory() -> MemoryStats {
        var stats = MemoryStats()
        var vm = vm_statistics64_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<vm_statistics64_data_t>.size / MemoryLayout<integer_t>.size)
        let result = withUnsafeMutablePointer(to: &vm) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &count) }
        }
        if result == KERN_SUCCESS {
            let page = UInt64(getpagesize())
            let active = UInt64(vm.active_count) * page
            let inactive = UInt64(vm.inactive_count) * page
            let speculative = UInt64(vm.speculative_count) * page
            let purgeable = UInt64(vm.purgeable_count) * page
            let external = UInt64(vm.external_page_count) * page
            stats.wired = UInt64(vm.wire_count) * page
            stats.compressed = UInt64(vm.compressor_page_count) * page
            stats.free = UInt64(vm.free_count) * page + speculative
            stats.cached = external + purgeable
            // Как в Мониторе активности: «приложения» + связанная + сжатая.
            let app = (active + inactive) > (external + purgeable) ? active + inactive - external - purgeable : 0
            stats.used = app + stats.wired + stats.compressed
        }
        var level: Int32 = 1
        var size = MemoryLayout<Int32>.size
        if sysctlbyname("kern.memorystatus_vm_pressure_level", &level, &size, nil, 0) == 0 { stats.pressure = Int(level) }
        return stats
    }

    static func disk(_ url: URL = URL(fileURLWithPath: "/")) -> DiskStats {
        let v = try? url.resourceValues(forKeys: [.volumeTotalCapacityKey, .volumeAvailableCapacityKey, .volumeAvailableCapacityForImportantUsageKey])
        return DiskStats(total: Int64(v?.volumeTotalCapacity ?? 0), available: Int64(v?.volumeAvailableCapacity ?? 0),
                         availableImportant: v?.volumeAvailableCapacityForImportantUsage ?? Int64(v?.volumeAvailableCapacity ?? 0))
    }

    /// Процессы текущего пользователя с памятью и загрузкой CPU (по разнице времени между замерами).
    nonisolated final class ProcessSampler: @unchecked Sendable {
        private var previous: [Int32: (UInt64, Date)] = [:]
        private let lock = NSLock()

        func sample(limit: Int = 12) -> [ProcessStat] {
            let uid = getuid()
            var pids = [Int32](repeating: 0, count: 4096)
            let n = proc_listallpids(&pids, Int32(pids.count * MemoryLayout<Int32>.size))
            guard n > 0 else { return [] }
            let now = Date()
            var out: [ProcessStat] = []
            var next: [Int32: (UInt64, Date)] = [:]
            for pid in pids.prefix(Int(n)) where pid > 0 {
                var info = proc_bsdinfo()
                guard proc_pidinfo(pid, PROC_PIDTBSDINFO, 0, &info, Int32(MemoryLayout<proc_bsdinfo>.size)) > 0, info.pbi_uid == uid else { continue }
                var usage = rusage_info_v4()
                let ok = withUnsafeMutablePointer(to: &usage) {
                    $0.withMemoryRebound(to: rusage_info_t?.self, capacity: 1) { proc_pid_rusage(pid, RUSAGE_INFO_V4, $0) }
                }
                guard ok == 0 else { continue }
                let cpuTime = usage.ri_user_time + usage.ri_system_time
                next[pid] = (cpuTime, now)
                var cpu = 0.0
                if let prev = lock.withLock({ previous[pid] }) {
                    let dt = now.timeIntervalSince(prev.1)
                    if dt > 0, cpuTime >= prev.0 { cpu = Double(cpuTime - prev.0) / 1e9 / dt * 100 * timebaseScale }
                }
                var name = [CChar](repeating: 0, count: 256)
                proc_name(pid, &name, 256)
                let title = String(decoding: name.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self)
                guard !title.isEmpty else { continue }
                out.append(ProcessStat(pid: pid, name: title, memory: usage.ri_phys_footprint, cpu: cpu))
            }
            lock.withLock { previous = next }
            return Array(out.sorted { $0.memory > $1.memory }.prefix(limit))
        }

        /// Время процессов в rusage — в «тиках» mach, на Apple Silicon их нужно перевести в наносекунды.
        private let timebaseScale: Double = {
            var info = mach_timebase_info_data_t()
            mach_timebase_info(&info)
            return info.denom == 0 ? 1 : Double(info.numer) / Double(info.denom)
        }()
    }
}
