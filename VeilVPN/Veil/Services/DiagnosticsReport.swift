import AppKit
import Foundation
import UniformTypeIdentifiers

/// One text file with everything needed to debug a connection problem (no secrets beyond bridge lines).
@MainActor
enum DiagnosticsReport {
    /// Replaces IPv4/IPv6 literals and host names with placeholders. Diagnostics are pasted into
    /// bug reports and chat windows; the shape of the problem is what matters, not the addresses.
    static func redact(_ text: String) -> String {
        var result = text
        let patterns = [
            #"\b\d{1,3}(\.\d{1,3}){3}\b"#,
            #"\b[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{0,4}){3,7}\b"#,
            #"\b[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)+\.(com|org|net|io|dev|ru|ua|onion|co|app|me|tv|cn)\b"#,
        ]
        for pattern in patterns {
            guard let expression = try? NSRegularExpression(pattern: pattern) else { continue }
            result = expression.stringByReplacingMatches(
                in: result, range: NSRange(result.startIndex..., in: result), withTemplate: "[redacted]")
        }
        return result
    }

    /// Loopback stays readable: 127.0.0.1 identifies nothing and the ports are the whole point.
    private static func redactIfWanted(_ text: String, state: AppState) -> String {
        guard state.settings.redactDiagnostics else { return text }
        return redact(text).replacingOccurrences(of: "[redacted]:", with: "127.0.0.1:")
    }

    static func generate(state: AppState) -> String {
        let formatter = ISO8601DateFormatter()
        var lines: [String] = []
        lines.append("# Veil diagnostics")
        lines.append("Generated: \(formatter.string(from: .now))")
        lines.append("Veil: \(UpdateChecker.currentVersion) (build \(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"))")
        lines.append("macOS: \(ProcessInfo.processInfo.operatingSystemVersionString)")
        lines.append("Tor: \(state.torVersion ?? "not started")  demo: \(state.isDemo)")
        lines.append("")
        lines.append("## State")
        lines.append("connection: \(state.connection)")
        lines.append("turbo: \(state.turboActive)  killSwitchEngaged: \(state.killSwitchEngaged)")
        lines.append("ports: \(state.ports.map { "socks \($0.socks) http \($0.http) control \($0.control)" } ?? "none")")
        lines.append("proxy: \(state.proxyStatus)")
        lines.append("padding: \(state.padding.status)")
        lines.append("circuit: \(state.circuit.map { "\($0.role) \($0.nickname) \($0.countryCode ?? "??")" }.joined(separator: " -> "))")
        lines.append("torCheck: \(state.torCheck.map { "isTor=\($0.isTor) ip=\($0.ip)" } ?? "none")  fetch: \(state.torCheckSeconds.map { "\(Int($0 * 1000)) ms" } ?? "n/a")")
        if let latency = state.latency.summary {
            lines.append("routeLatency: median \(Int(latency.median * 1000)) ms  best \(Int(latency.best * 1000)) ms  jitter \(Int(latency.jitter * 1000)) ms  samples \(latency.samples)  failures \(latency.failures)")
        } else {
            lines.append("routeLatency: not measured")
        }
        lines.append("youtubeTest: \(state.youtubeTest.map { "success=\($0.success) \($0.milliseconds) ms \($0.detail) viaTor=\($0.viaTor)" } ?? "none")")
        lines.append("bridge stats: \(state.bridgeStats)")
        if let lanes = state.lanes {
            lines.append("lanes: \(lanes.readyLanes)/\(lanes.rows.count) ready, best \(lanes.bestP50.map { "\(Int($0 * 1000)) ms" } ?? "n/a"), hedges \(lanes.hedgesStarted)/\(lanes.hedgesWon)")
            for lane in lanes.rows {
                lines.append("  lane \(lane.id) gen \(lane.generation) \(lane.state.rawValue) p50 \(lane.p50Milliseconds.map(String.init) ?? "—") ms inflight \(lane.inFlight) sites \(lane.assignedSites) fails \(lane.consecutiveFailures)")
            }
        }
        lines.append("warmth: \(state.warmth?.summary ?? "unknown")  standby: \(state.standby)")
        let posture = state.securityPosture
        lines.append("security: \(posture.score.map(String.init) ?? "n/a")/\(SecurityPosture.absoluteMaximum) \(posture.gradeTitle), findings \(posture.findings.map(\.id).joined(separator: ", "))")
        lines.append("")
        lines.append("## Settings")
        if let data = try? JSONEncoder().encode(state.settings), let json = String(data: data, encoding: .utf8) {
            lines.append(json)
        }
        lines.append("")
        lines.append("## Log (last \(min(state.logs.count, 800)) entries)")
        let timeFormat = Date.FormatStyle(date: .omitted, time: .standard)
        for entry in state.logs.suffix(800) {
            lines.append("\(entry.date.formatted(timeFormat)) [\(entry.level.label)] \(entry.source == .veil ? "veil: " : "")\(entry.message)")
        }
        return redactIfWanted(lines.joined(separator: "\n") + "\n", state: state)
    }

    static func save(state: AppState) {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.plainText]
        panel.nameFieldStringValue = "Veil-diagnostics.txt"
        panel.title = String(localized: "Save diagnostics report")
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try generate(state: state).write(to: url, atomically: true, encoding: .utf8)
        } catch {
            NSAlert(error: error).runModal()
        }
    }

    static func copy(state: AppState) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(generate(state: state), forType: .string)
    }
}
