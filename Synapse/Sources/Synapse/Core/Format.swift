import Foundation

nonisolated enum Format {
    static func bytes(_ v: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: max(0, v), countStyle: .file)
    }

    static func relative(_ date: Date?, now: Date = Date()) -> String {
        guard let date else { return "—" }
        if date > now.addingTimeInterval(-60) { return "только что" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.unitsStyle = .full
        return f.localizedString(for: date, relativeTo: now)
    }

    static func date(_ date: Date?) -> String {
        guard let date else { return "—" }
        return date.formatted(.dateTime.day().month(.wide).year().locale(Locale(identifier: "ru_RU")))
    }

    /// 1 234 567 → «1,2 млн».
    static func count(_ n: Int) -> String {
        switch n {
        case 1_000_000...: String(format: "%.1f млн", Double(n) / 1e6).replacingOccurrences(of: ".", with: ",")
        case 10_000...: "\(n / 1000) тыс."
        case 1000...: String(format: "%.1f тыс.", Double(n) / 1e3).replacingOccurrences(of: ".", with: ",")
        default: "\(n)"
        }
    }

    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "—" }
        if seconds < 60 { return "\(Int(seconds.rounded())) с" }
        if seconds < 3600 { return "\(Int(seconds / 60)) мин \(Int(seconds.truncatingRemainder(dividingBy: 60))) с" }
        return "\(Int(seconds / 3600)) ч \(Int(seconds.truncatingRemainder(dividingBy: 3600) / 60)) мин"
    }

    static func speed(_ bytesPerSecond: Double) -> String {
        bytes(Int64(bytesPerSecond)) + "/с"
    }
}
