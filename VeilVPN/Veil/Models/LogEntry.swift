import Foundation

/// A line from Tor's log or from Veil itself.
struct LogEntry: Identifiable, Equatable, Sendable {
    enum Level: Int, Comparable, Sendable {
        case debug, info, notice, warn, error

        static func < (lhs: Level, rhs: Level) -> Bool { lhs.rawValue < rhs.rawValue }

        var label: String {
            switch self {
            case .debug: "debug"
            case .info: "info"
            case .notice: "notice"
            case .warn: "warn"
            case .error: "err"
            }
        }
    }

    enum Source: Equatable, Sendable {
        case tor
        case veil
    }

    let id: UUID
    let date: Date
    let level: Level
    let source: Source
    let message: String

    init(date: Date = .now, level: Level, source: Source, message: String) {
        self.id = UUID()
        self.date = date
        self.level = level
        self.source = source
        self.message = message
    }

    static func veil(_ level: Level, _ message: String) -> LogEntry {
        LogEntry(level: level, source: .veil, message: message)
    }

    /// Parses `Sep 06 12:00:00.000 [notice] Bootstrapped 5% (conn): Connecting to a relay`.
    static func parseTorLine(_ line: String) -> LogEntry {
        var level: Level = .notice
        var message = line
        if let open = line.range(of: "["), let close = line.range(of: "]", range: open.upperBound..<line.endIndex) {
            let tag = line[open.upperBound..<close.lowerBound]
            switch tag {
            case "debug": level = .debug
            case "info": level = .info
            case "notice": level = .notice
            case "warn": level = .warn
            case "err": level = .error
            default: break
            }
            if ["debug", "info", "notice", "warn", "err"].contains(String(tag)) {
                message = String(line[close.upperBound...]).trimmingCharacters(in: .whitespaces)
            }
        }
        return LogEntry(level: level, source: .tor, message: message)
    }

    /// Extracts bootstrap progress from a Tor log line, if it carries one.
    static func parseBootstrap(_ line: String) -> BootstrapProgress? {
        guard let marker = line.range(of: "Bootstrapped ") else { return nil }
        let rest = line[marker.upperBound...]
        guard let percentEnd = rest.firstIndex(of: "%"), let percent = Int(rest[..<percentEnd]) else { return nil }
        var tag = ""
        var summary = ""
        if let open = rest.range(of: "("), let close = rest.range(of: ")", range: open.upperBound..<rest.endIndex) {
            tag = String(rest[open.upperBound..<close.lowerBound])
            let afterClose = rest[close.upperBound...]
            summary = afterClose.trimmingCharacters(in: CharacterSet(charactersIn: ": ")).trimmingCharacters(in: .whitespaces)
        }
        return BootstrapProgress(percent: min(100, max(0, percent)), tag: tag, summary: summary)
    }
}

struct AppError: Identifiable, Equatable, Sendable {
    let id = UUID()
    let title: String
    let message: String

    static func == (lhs: AppError, rhs: AppError) -> Bool { lhs.id == rhs.id }
}
