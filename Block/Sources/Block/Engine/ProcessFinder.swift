import AppKit
import Darwin

/// Кто стоит за соединением со шлюзом: по порту клиента находим процесс, затем программу.
/// Без прав администратора видны только процессы этого пользователя — всё остальное честно
/// называется «системная служба macOS».
nonisolated struct NetApp: Codable, Hashable, Sendable {
    /// Ключ правила: идентификатор программы или путь к исполняемому файлу.
    var key: String
    var name: String
    var path: String
    var bundleID: String?
    /// Процесс другого пользователя (обычно root) — служба macOS.
    var isSystem: Bool
    /// Программа Apple из /System или /usr.
    var isApple: Bool

    static let unknownSystem = NetApp(key: "system", name: "Системная служба macOS", path: "", bundleID: nil, isSystem: true, isApple: true)
}

nonisolated enum ProcessFinder {
    // Константы из <sys/proc_info.h>.
    private static let listFDs: Int32 = 1          // PROC_PIDLISTFDS
    private static let fdSocketInfo: Int32 = 3     // PROC_PIDFDSOCKETINFO
    private static let fdTypeSocket: UInt32 = 2    // PROX_FDTYPE_SOCKET
    private static let sockinfoTCP: Int32 = 2      // SOCKINFO_TCP
    private static let uidOnly: UInt32 = 4         // PROC_UID_ONLY

    /// Процесс, у которого есть TCP-сокет с локальным портом `clientPort`, подключённый к порту `serverPort`.
    static func pid(clientPort: UInt16, serverPort: UInt16) -> pid_t? {
        let uid = getuid()
        let count = proc_listpids(uidOnly, UInt32(uid), nil, 0)
        guard count > 0 else { return nil }
        var pids = [pid_t](repeating: 0, count: Int(count) / MemoryLayout<pid_t>.stride + 32)
        let filled = proc_listpids(uidOnly, UInt32(uid), &pids, Int32(pids.count * MemoryLayout<pid_t>.stride))
        let n = Int(filled) / MemoryLayout<pid_t>.stride
        let me = getpid()
        for pid in pids.prefix(n) where pid > 0 && pid != me {
            if owns(pid, local: clientPort, remote: serverPort) { return pid }
        }
        return nil
    }

    private static func owns(_ pid: pid_t, local: UInt16, remote: UInt16) -> Bool {
        let size = proc_pidinfo(pid, listFDs, 0, nil, 0)
        guard size > 0 else { return false }
        let stride = MemoryLayout<proc_fdinfo>.stride
        var fds = [proc_fdinfo](repeating: proc_fdinfo(), count: Int(size) / stride + 8)
        let got = proc_pidinfo(pid, listFDs, 0, &fds, Int32(fds.count * stride))
        guard got > 0 else { return false }
        for fd in fds.prefix(Int(got) / stride) where fd.proc_fdtype == fdTypeSocket {
            var info = socket_fdinfo()
            let r = proc_pidfdinfo(pid, fd.proc_fd, fdSocketInfo, &info, Int32(MemoryLayout<socket_fdinfo>.size))
            guard r == Int32(MemoryLayout<socket_fdinfo>.size), info.psi.soi_kind == sockinfoTCP else { continue }
            let ini = info.psi.soi_proto.pri_tcp.tcpsi_ini
            let lport = UInt16(bigEndian: UInt16(truncatingIfNeeded: ini.insi_lport))
            let fport = UInt16(bigEndian: UInt16(truncatingIfNeeded: ini.insi_fport))
            if lport == local && fport == remote { return true }
        }
        return false
    }

    static func path(of pid: pid_t) -> String {
        var buffer = [CChar](repeating: 0, count: 4096)
        guard proc_pidpath(pid, &buffer, UInt32(buffer.count)) > 0 else { return "" }
        return String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    /// Процесс, «отвечающий» за этот: у служб XPC (сетевой процесс WebKit, помощники программ) — сама программа.
    /// Функция libsystem без заголовка; если её нет, остаётся сам процесс.
    static func responsible(_ pid: pid_t) -> pid_t {
        typealias Fn = @convention(c) (pid_t) -> pid_t
        guard let handle = dlopen(nil, RTLD_NOW),
              let symbol = dlsym(handle, "responsibility_get_pid_responsible_for_pid") else { return pid }
        let resolved = unsafeBitCast(symbol, to: Fn.self)(pid)
        return resolved > 0 ? resolved : pid
    }

    /// Программа по пути исполняемого файла: самый внешний .app (помощник Chrome → Google Chrome).
    static func app(path: String) -> NetApp {
        let parts = path.split(separator: "/", omittingEmptySubsequences: false)
        var bundlePath: String?
        for (i, part) in parts.enumerated() where part.hasSuffix(".app") {
            bundlePath = parts.prefix(i + 1).joined(separator: "/")
            break
        }
        let isApple = path.hasPrefix("/System/") || path.hasPrefix("/usr/") || path.hasPrefix("/bin/") || path.hasPrefix("/sbin/")
            || path.hasPrefix("/Library/Apple/")
        if let bundlePath, let bundle = Bundle(path: bundlePath) {
            let name = (bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String)
                ?? (bundle.object(forInfoDictionaryKey: "CFBundleName") as? String)
                ?? URL(fileURLWithPath: bundlePath).deletingPathExtension().lastPathComponent
            let id = bundle.bundleIdentifier
            return NetApp(key: id ?? bundlePath, name: name, path: bundlePath, bundleID: id, isSystem: false, isApple: isApple)
        }
        let name = URL(fileURLWithPath: path).lastPathComponent
        return NetApp(key: path.isEmpty ? "unknown" : path, name: name.isEmpty ? "Неизвестная программа" : name, path: path,
                      bundleID: nil, isSystem: false, isApple: isApple)
    }

    /// Программа за клиентом шлюза.
    /// - внутри .app (помощники Chrome, Electron) — сама программа;
    /// - служба XPC (сетевой процесс WebKit) — программа, для которой она работает;
    /// - обычный исполняемый файл (curl, git, node) — он сам, а не Терминал, который его запустил.
    static func identify(clientPort: UInt16, serverPort: UInt16) -> NetApp {
        guard let pid = pid(clientPort: clientPort, serverPort: serverPort) else { return .unknownSystem }
        let own = path(of: pid)
        if own.contains(".xpc/") {
            let ownerPath = path(of: responsible(pid))
            if !ownerPath.isEmpty { return app(path: ownerPath) }
        }
        return app(path: own)
    }
}
