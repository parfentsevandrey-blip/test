import AppKit
import Foundation
import VeilShared

/// Связь с помощником Veil (root): системный прокси и TUN.
nonisolated enum HelperClient {
    enum Failure: Error, CustomStringConvertible {
        case cancelled
        case install(String)
        case helper(String)

        var description: String {
            switch self {
            case .cancelled: "Установка помощника отменена"
            case .install(let why): "Не удалось установить помощника: \(why)"
            case .helper(let why): why
            }
        }
    }

    /// Путь сокета (для самопроверок — свой, без установки в систему).
    static var socketPath: String { ProcessInfo.processInfo.environment["VEIL_HELPER_SOCKET"] ?? HelperInfo.socketPath }

    static func call(_ request: HelperRequest, timeout: TimeInterval = 25) async throws -> HelperResponse {
        let path = socketPath
        return try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let response = try HelperSocket.send(request, path: path, timeout: timeout)
                    if response.ok { cont.resume(returning: response) } else { cont.resume(throwing: Failure.helper(response.message)) }
                } catch {
                    cont.resume(throwing: Failure.helper("\(error)"))
                }
            }
        }
    }

    /// Установлен ли помощник нужной версии.
    static func status() async -> HelperResponse? {
        guard let r = try? await call(HelperRequest(command: .status), timeout: 3), r.version == HelperInfo.version else { return nil }
        return r
    }

    /// Установка одним паролем администратора: копия помощника и ядра в /Library/PrivilegedHelperTools,
    /// служба launchd. Пароль спрашивает сама macOS.
    static func install() async throws {
        let helper = AppPaths.helperBinary.path
        let core = AppPaths.xray.path
        guard FileManager.default.fileExists(atPath: helper), FileManager.default.fileExists(atPath: core) else {
            throw Failure.install("в приложении нет файлов помощника — переустановите Veil")
        }
        let dir = HelperInfo.directory
        let plist = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0"><dict>
          <key>Label</key><string>\(HelperInfo.label)</string>
          <key>ProgramArguments</key><array><string>\(HelperInfo.helperPath)</string><string>--uid</string><string>\(getuid())</string></array>
          <key>RunAtLoad</key><true/>
          <key>KeepAlive</key><true/>
          <key>StandardErrorPath</key><string>/var/log/app.veil.helper.log</string>
        </dict></plist>
        """
        let script = """
        set -e
        launchctl bootout system/\(HelperInfo.label) 2>/dev/null || true
        mkdir -p '\(dir)'
        cp -f '\(helper)' '\(HelperInfo.helperPath)'
        cp -f '\(core)' '\(HelperInfo.corePath)'
        chown -R root:wheel '\(dir)'
        chmod 755 '\(dir)' '\(HelperInfo.helperPath)' '\(HelperInfo.corePath)'
        cat > '\(HelperInfo.plistPath)' <<'PLIST'
        \(plist)
        PLIST
        chown root:wheel '\(HelperInfo.plistPath)'
        chmod 644 '\(HelperInfo.plistPath)'
        launchctl bootstrap system '\(HelperInfo.plistPath)'
        """
        try await runAsAdmin(script, prompt: "Veil устанавливает помощника для системного прокси и режима TUN.")
        // Дождаться, пока служба поднимет сокет.
        for _ in 0..<40 {
            if await status() != nil { return }
            try? await Task.sleep(for: .milliseconds(250))
        }
        throw Failure.install("помощник не отвечает после установки")
    }

    static func uninstall() async throws {
        let script = """
        launchctl bootout system/\(HelperInfo.label) 2>/dev/null || true
        rm -f '\(HelperInfo.plistPath)'
        rm -rf '\(HelperInfo.directory)'
        rm -f '\(HelperInfo.socketPath)'
        """
        try await runAsAdmin(script, prompt: "Veil удаляет своего помощника.")
    }

    /// Скрипт с правами администратора через стандартный запрос пароля macOS.
    static func runAsAdmin(_ script: String, prompt: String) async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("veil-\(UUID().uuidString).sh")
        try script.write(to: file, atomically: true, encoding: .utf8)
        defer { try? FileManager.default.removeItem(at: file) }
        let escapedPrompt = prompt.replacingOccurrences(of: "\"", with: "\\\"")
        let source = "do shell script \"/bin/sh '\(file.path)'\" with prompt \"\(escapedPrompt)\" with administrator privileges"
        let result: String? = await withCheckedContinuation { cont in
            DispatchQueue.main.async {
                var error: NSDictionary?
                NSAppleScript(source: source)?.executeAndReturnError(&error)
                if let error {
                    let code = error[NSAppleScript.errorNumber] as? Int ?? 0
                    cont.resume(returning: code == -128 ? "cancel" : (error[NSAppleScript.errorMessage] as? String ?? "ошибка \(code)"))
                } else {
                    cont.resume(returning: nil)
                }
            }
        }
        if result == "cancel" { throw Failure.cancelled }
        if let result { throw Failure.install(result) }
    }
}
