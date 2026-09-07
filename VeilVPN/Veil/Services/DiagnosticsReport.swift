import AppKit
import Foundation
import UniformTypeIdentifiers

/// One text file with everything needed to debug a connection problem (no secrets beyond bridge lines).
@MainActor
enum DiagnosticsReport {
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
        lines.append("torCheck: \(state.torCheck.map { "isTor=\($0.isTor) ip=\($0.ip)" } ?? "none")  latency: \(state.routeLatency.map { "\(Int($0 * 1000)) ms" } ?? "n/a")")
        lines.append("youtubeTest: \(state.youtubeTest.map { "success=\($0.success) \($0.milliseconds) ms \($0.detail) viaTor=\($0.viaTor)" } ?? "none")")
        lines.append("bridge stats: \(state.bridgeStats)")
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
        return lines.joined(separator: "\n") + "\n"
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
