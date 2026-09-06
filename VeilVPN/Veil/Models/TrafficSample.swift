import Foundation

/// Throughput measured over one second, in bytes per second.
struct TrafficSample: Identifiable, Equatable, Sendable {
    let date: Date
    let download: Double
    let upload: Double

    var id: Date { date }
}

enum ByteFormat {
    static func rate(_ bytesPerSecond: Double) -> String {
        let value = Int64(max(0, bytesPerSecond.rounded()))
        return value.formatted(.byteCount(style: .binary)) + "/s"
    }

    static func total(_ bytes: UInt64) -> String {
        Int64(clamping: bytes).formatted(.byteCount(style: .binary))
    }
}
