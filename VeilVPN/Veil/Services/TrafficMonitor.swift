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
    /// When the last figure was produced, so the ledger can dim a stale one instead of showing it
    /// as if it were current.
    private(set) var lastSampleAt: Date?

    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var lastEventAt: Date?
    @ObservationIgnored private var fedByEvents = false
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
                // Tor's own BW event carries the same numbers once a second for free. When it is
                // arriving, the two GETINFOs are skipped — about 172,800 control round trips a day
                // that no longer run on the connection that owns the process.
                if self?.recentlyFedByEvents != true, let counters = try? await engine.trafficCounters() {
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
        lastEventAt = nil
        lastSampleAt = nil
        fedByEvents = false
    }

    private var recentlyFedByEvents: Bool {
        guard let lastEventAt else { return false }
        return Date.now.timeIntervalSince(lastEventAt) < 3
    }

    /// `BW` reports per-second deltas, not cumulative counters, so it cannot go through `record`:
    /// that does delta arithmetic against a baseline and would produce nonsense rates.
    func ingest(read: UInt64, written: UInt64, at date: Date = .now) {
        let elapsed = lastEventAt.map { max(0.2, date.timeIntervalSince($0)) } ?? 1
        lastEventAt = date
        lastSampleAt = date
        fedByEvents = true
        totalDownload &+= read
        totalUpload &+= written
        downloadRate = Double(read) / elapsed
        uploadRate = Double(written) / elapsed
        samples.append(TrafficSample(date: date, download: downloadRate, upload: uploadRate,
                                     padding: paddingRateProvider?() ?? 0))
        if samples.count > capacity {
            samples.removeFirst(samples.count - capacity)
        }
    }

    /// No byte counter has moved while connections are open: a stated fault, not a quiet flatline.
    func isStalled(openConnections: Int, now: Date = .now) -> Bool {
        guard openConnections > 0, let lastSampleAt else { return false }
        return now.timeIntervalSince(lastSampleAt) > 3
    }

    private func record(_ counters: TrafficCounters) {
        let now = Date.now
        if baseline == nil { baseline = counters }
        if let baseline {
            totalDownload = counters.read >= baseline.read ? counters.read - baseline.read : 0
            totalUpload = counters.written >= baseline.written ? counters.written - baseline.written : 0
        }
        if fedByEvents {
            // The counters advanced while the events were feeding us. Re-anchor rather than
            // reporting the whole quiet gap as one second's worth of traffic.
            fedByEvents = false
            previous = (counters, now)
            return
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
            lastSampleAt = now
        }
        previous = (counters, now)
    }
}
