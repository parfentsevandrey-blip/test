import Foundation
import Security
import SystemConfiguration

enum SystemProxyError: LocalizedError {
    case authorizationDenied
    case cancelled
    case preferencesUnavailable
    case commitFailed(String)
    case helperFailed(String)

    var errorDescription: String? {
        switch self {
        case .authorizationDenied: String(localized: "Administrator authorization was not granted.")
        case .cancelled: String(localized: "Authorization was cancelled.")
        case .preferencesUnavailable: String(localized: "Network preferences are unavailable.")
        case .commitFailed(let detail): String(localized: "Could not save network settings: \(detail)")
        case .helperFailed(let detail): String(localized: "networksetup failed: \(detail)")
        }
    }
}

enum ProxyChange: Sendable {
    /// `socks == nil` leaves the SOCKS proxy off (YouTube Turbo: no Tor).
    case enabled(socks: UInt16?, http: UInt16)
    case disabled
}

/// Points the system-wide SOCKS and HTTP(S) proxies of every active network service at Veil.
/// Uses the SystemConfiguration framework with a one-time administrator authorization; falls
/// back to `networksetup` (elevated through AppleScript) if that is not possible.
enum SystemProxy {
    static let bypassList = ["127.0.0.1", "localhost", "*.local", "169.254/16"]

    static func enable(socksPort: UInt16?, httpPort: UInt16) async throws -> [String] {
        try await apply(.enabled(socks: socksPort, http: httpPort))
    }

    static func disable() async throws {
        _ = try await apply(.disabled)
    }

    private static func apply(_ change: ProxyChange) async throws -> [String] {
        do {
            return try await Task.detached(priority: .userInitiated) {
                try SystemConfigurationProxyWriter.apply(change)
            }.value
        } catch SystemProxyError.cancelled {
            throw SystemProxyError.cancelled
        } catch {
            return try await NetworkSetupProxyWriter.apply(change)
        }
    }
}

// MARK: - SystemConfiguration writer

enum SystemConfigurationProxyWriter {
    private static let lock = NSLock()
    private static var authorization: AuthorizationRef?

    static func apply(_ change: ProxyChange) throws -> [String] {
        let authorization = try Self.obtainAuthorization()
        guard let preferences = SCPreferencesCreateWithAuthorization(nil, "Veil" as CFString, nil, authorization) else {
            throw SystemProxyError.preferencesUnavailable
        }
        guard SCPreferencesLock(preferences, true) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        defer { _ = SCPreferencesUnlock(preferences) }

        guard let set = SCNetworkSetCopyCurrent(preferences),
              let services = SCNetworkSetCopyServices(set) as? [SCNetworkService] else {
            throw SystemProxyError.preferencesUnavailable
        }

        var touched: [String] = []
        for service in services where SCNetworkServiceGetEnabled(service) {
            guard let proxies = SCNetworkServiceCopyProtocol(service, kSCNetworkProtocolTypeProxies) else { continue }
            var config = (SCNetworkProtocolGetConfiguration(proxies) as? [String: Any]) ?? [:]
            switch change {
            case .enabled(let socks, let http):
                if let socks {
                    config[kSCPropNetProxiesSOCKSEnable as String] = 1
                    config[kSCPropNetProxiesSOCKSProxy as String] = "127.0.0.1"
                    config[kSCPropNetProxiesSOCKSPort as String] = Int(socks)
                } else {
                    config[kSCPropNetProxiesSOCKSEnable as String] = 0
                }
                config[kSCPropNetProxiesHTTPEnable as String] = 1
                config[kSCPropNetProxiesHTTPProxy as String] = "127.0.0.1"
                config[kSCPropNetProxiesHTTPPort as String] = Int(http)
                config[kSCPropNetProxiesHTTPSEnable as String] = 1
                config[kSCPropNetProxiesHTTPSProxy as String] = "127.0.0.1"
                config[kSCPropNetProxiesHTTPSPort as String] = Int(http)
                config[kSCPropNetProxiesExceptionsList as String] = SystemProxy.bypassList
            case .disabled:
                config[kSCPropNetProxiesSOCKSEnable as String] = 0
                config[kSCPropNetProxiesHTTPEnable as String] = 0
                config[kSCPropNetProxiesHTTPSEnable as String] = 0
            }
            guard SCNetworkProtocolSetConfiguration(proxies, config as CFDictionary) else {
                throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
            }
            touched.append(SCNetworkServiceGetName(service).map { $0 as String } ?? "Network service")
        }

        guard SCPreferencesCommitChanges(preferences) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        guard SCPreferencesApplyChanges(preferences) else {
            throw SystemProxyError.commitFailed(String(cString: SCErrorString(SCError())))
        }
        return touched
    }

    /// Asks for the `system.services.systemconfiguration.network` right once per app run.
    static func obtainAuthorization() throws -> AuthorizationRef {
        lock.lock()
        defer { lock.unlock() }
        if let authorization { return authorization }

        var created: AuthorizationRef?
        guard AuthorizationCreate(nil, nil, [], &created) == errAuthorizationSuccess, let reference = created else {
            throw SystemProxyError.authorizationDenied
        }
        let rightName = strdup("system.services.systemconfiguration.network")! // intentionally never freed
        var item = AuthorizationItem(name: rightName, valueLength: 0, value: nil, flags: 0)
        let flags: AuthorizationFlags = [.interactionAllowed, .extendRights, .preAuthorize]
        let status = withUnsafeMutablePointer(to: &item) { itemPointer -> OSStatus in
            var rights = AuthorizationRights(count: 1, items: itemPointer)
            return AuthorizationCopyRights(reference, &rights, nil, flags, nil)
        }
        guard status == errAuthorizationSuccess else {
            AuthorizationFree(reference, [])
            throw status == errAuthorizationCanceled ? SystemProxyError.cancelled : SystemProxyError.authorizationDenied
        }
        authorization = reference
        return reference
    }
}

// MARK: - networksetup fallback

enum NetworkSetupProxyWriter {
    private static let tool = "/usr/sbin/networksetup"

    static func apply(_ change: ProxyChange) async throws -> [String] {
        let services = try await networkServices()
        var commands: [[String]] = []
        for service in services {
            switch change {
            case .enabled(let socks, let http):
                if let socks {
                    commands.append(["-setsocksfirewallproxy", service, "127.0.0.1", String(socks)])
                } else {
                    commands.append(["-setsocksfirewallproxystate", service, "off"])
                }
                commands.append(["-setwebproxy", service, "127.0.0.1", String(http)])
                commands.append(["-setsecurewebproxy", service, "127.0.0.1", String(http)])
                commands.append(["-setproxybypassdomains", service] + SystemProxy.bypassList)
            case .disabled:
                commands.append(["-setsocksfirewallproxystate", service, "off"])
                commands.append(["-setwebproxystate", service, "off"])
                commands.append(["-setsecurewebproxystate", service, "off"])
            }
        }
        try await run(commands)
        return services
    }

    private static func networkServices() async throws -> [String] {
        let output = try await ProcessRunner.run(tool, ["-listallnetworkservices"])
        guard output.status == 0 else { throw SystemProxyError.helperFailed(output.stderr + output.stdout) }
        return output.stdout
            .split(separator: "\n")
            .dropFirst() // "An asterisk (*) denotes that a network service is disabled."
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && !$0.hasPrefix("*") }
    }

    private static func run(_ commands: [[String]]) async throws {
        // Try without privileges first; admins often can change proxies directly.
        var needsElevation = false
        for command in commands {
            let result = try await ProcessRunner.run(tool, command)
            if result.status != 0 || result.stdout.contains("Error") || result.stderr.contains("Error") {
                needsElevation = true
                break
            }
        }
        guard needsElevation else { return }

        let script = commands
            .map { ([tool] + $0).map(shellQuote).joined(separator: " ") }
            .joined(separator: " && ")
        let escaped = script
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        let appleScript = "do shell script \"\(escaped)\" with administrator privileges"
        let result = try await ProcessRunner.run("/usr/bin/osascript", ["-e", appleScript])
        guard result.status == 0 else {
            if result.stderr.contains("-128") || result.stderr.localizedCaseInsensitiveContains("cancel") {
                throw SystemProxyError.cancelled
            }
            throw SystemProxyError.helperFailed(result.stderr.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    private static func shellQuote(_ value: String) -> String {
        "'" + value.replacingOccurrences(of: "'", with: "'\\''") + "'"
    }
}

// MARK: - Process helper

enum ProcessRunner {
    struct Output: Sendable {
        let status: Int32
        let stdout: String
        let stderr: String
    }

    static func run(_ executable: String, _ arguments: [String]) async throws -> Output {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Output, Error>) in
            let process = Process()
            process.executableURL = URL(fileURLWithPath: executable)
            process.arguments = arguments
            let stdout = Pipe()
            let stderr = Pipe()
            process.standardOutput = stdout
            process.standardError = stderr
            process.standardInput = FileHandle.nullDevice
            process.terminationHandler = { finished in
                let out = String(decoding: stdout.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
                let err = String(decoding: stderr.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
                continuation.resume(returning: Output(status: finished.terminationStatus, stdout: out, stderr: err))
            }
            do {
                try process.run()
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }
}
