import Foundation
import Observation

/// Samples Tor's traffic counters once per second and keeps a short history for the chart.
@MainActor
@Observable
final class TrafficMonitor {
    private(set) var samples: [TrafficSample] = []
    private(set) var downloadRate: Double = 0
    private(set) var uploadRate: Double = 0
    private(set) var totalDownload: UInt64 = 0
    private(set) var totalUpload: UInt64 = 0

    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var baseline: TrafficCounters?
    @ObservationIgnored private var previous: (counters: TrafficCounters, date: Date)?
    /// Supplies the current padding rate so the chart can show noise as its own series.
    @ObservationIgnored var paddingRateProvider: (@MainActor () -> Double)?

    let capacity = 120

    func start(engine: any TorEngine) {
        stop()
        samples = []
        baseline = nil
        previous = nil
        totalDownload = 0
        totalUpload = 0
        task = Task { [weak self] in
            while !Task.isCancelled {
                if let counters = try? await engine.trafficCounters() {
                    self?.record(counters)
                }
                do {
                    try await Task.sleep(for: .seconds(1))
                } catch {
                    return
                }
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
        downloadRate = 0
        uploadRate = 0
    }

    private func record(_ counters: TrafficCounters) {
        let now = Date.now
        if baseline == nil { baseline = counters }
        if let baseline {
            totalDownload = counters.read >= baseline.read ? counters.read - baseline.read : 0
            totalUpload = counters.written >= baseline.written ? counters.written - baseline.written : 0
        }
        if let previous {
            let elapsed = max(0.2, now.timeIntervalSince(previous.date))
            let down = counters.read >= previous.counters.read ? Double(counters.read - previous.counters.read) / elapsed : 0
            let up = counters.written >= previous.counters.written ? Double(counters.written - previous.counters.written) / elapsed : 0
            downloadRate = down
            uploadRate = up
            samples.append(TrafficSample(date: now, download: down, upload: up, padding: paddingRateProvider?() ?? 0))
            if samples.count > capacity {
                samples.removeFirst(samples.count - capacity)
            }
        }
        previous = (counters, now)
    }
}
