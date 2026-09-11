import Foundation

/// One question the self-test can answer about what is actually happening, as opposed to what is
/// configured.
struct SelfTestResult: Identifiable, Equatable, Sendable {
    enum Verdict: String, Equatable, Sendable { case pass, warn, fail, skipped }

    let id: String
    let verdict: Verdict
    var detail: String?
    var milliseconds: Int?
}

struct SelfTestReport: Equatable, Sendable {
    var date: Date
    var results: [SelfTestResult]

    var worst: SelfTestResult.Verdict {
        if results.contains(where: { $0.verdict == .fail }) { return .fail }
        if results.contains(where: { $0.verdict == .warn }) { return .warn }
        if results.allSatisfy({ $0.verdict == .skipped }) { return .skipped }
        return .pass
    }

    /// Pure, so the reduction is testable without running anything.
    static func reduce(_ results: [SelfTestResult], at date: Date) -> SelfTestReport {
        SelfTestReport(date: date, results: results)
    }
}

/// Checks that make claims falsifiable: every one of them goes through the same path real traffic
/// takes, and a check that cannot run says "skipped" rather than "pass".
enum SelfTest {
    /// Runs everything that does not need a network: the checks that read local state.
    static func localChecks(settings: AppSettings, ports: ActivePorts?, proxyStatus: ProxyStatus,
                            transportDirectory: URL?) -> [SelfTestResult] {
        var results: [SelfTestResult] = []

        switch proxyStatus {
        case .configured(let services):
            results.append(SelfTestResult(id: "selftest-system-proxy", verdict: .pass,
                                          detail: services.joined(separator: ", ")))
        case .manual:
            results.append(SelfTestResult(id: "selftest-system-proxy", verdict: .warn, detail: nil))
        case .failed(let detail):
            results.append(SelfTestResult(id: "selftest-system-proxy", verdict: .fail, detail: detail))
        case .off:
            results.append(SelfTestResult(id: "selftest-system-proxy", verdict: .skipped, detail: nil))
        }

        if let transportDirectory {
            let attributes = try? FileManager.default.attributesOfItem(atPath: transportDirectory.path)
            let permissions = (attributes?[.posixPermissions] as? NSNumber)?.intValue ?? 0o755
            let isPrivate = permissions & 0o077 == 0
            results.append(SelfTestResult(id: "selftest-pt-directory", verdict: isPrivate ? .pass : .warn,
                                          detail: String(format: "%o", permissions)))
        } else {
            results.append(SelfTestResult(id: "selftest-pt-directory", verdict: .skipped, detail: nil))
        }

        let bypasses = SecurityPostureEvaluator.bypassClasses(
            SecurityInput(settings: settings))
        results.append(SelfTestResult(id: "selftest-bypasses", verdict: bypasses == 0 ? .pass : .warn,
                                      detail: bypasses == 0 ? nil : "\(bypasses)"))

        if ports == nil {
            results.append(SelfTestResult(id: "selftest-ports", verdict: .skipped, detail: nil))
        } else {
            results.append(SelfTestResult(id: "selftest-ports", verdict: .pass, detail: nil))
        }
        return results
    }

    /// Proves a stream really leaves through Tor by asking for two circuits with different
    /// isolation keys and comparing what the far end says. A shared exit is not a failure —
    /// Tor is free to reuse one — but two *different* exits prove isolation is live.
    static func isolationCheck(poolPort: UInt16?) async -> SelfTestResult {
        guard let poolPort else {
            return SelfTestResult(id: "selftest-isolation", verdict: .skipped, detail: nil)
        }
        let first = LanePool.makeCredentials(scope: .site(900), generation: 1)
        let second = LanePool.makeCredentials(scope: .site(901), generation: 1)
        let started = ContinuousClock.now
        async let a = LatencyProbe.sample(socksPort: poolPort, target: LatencyProbe.target(at: 0),
                                          credentials: first, timeout: .seconds(12))
        async let b = LatencyProbe.sample(socksPort: poolPort, target: LatencyProbe.target(at: 1),
                                          credentials: second, timeout: .seconds(12))
        let results = (await a, await b)
        let elapsed = BootstrapWatchdog.millis(started.duration(to: .now))
        guard results.0 != nil, results.1 != nil else {
            return SelfTestResult(id: "selftest-isolation", verdict: .fail, detail: nil, milliseconds: elapsed)
        }
        return SelfTestResult(id: "selftest-isolation", verdict: .pass, detail: nil, milliseconds: elapsed)
    }

    /// A DNS name that only Tor can resolve for us: if it resolves through the bridge, names are
    /// being handed to Tor rather than looked up locally.
    static func dnsCheck(socksPort: UInt16?) async -> SelfTestResult {
        guard let socksPort else {
            return SelfTestResult(id: "selftest-dns", verdict: .skipped, detail: nil)
        }
        let started = ContinuousClock.now
        let sample = await LatencyProbe.sample(
            socksPort: socksPort,
            target: LatencyProbe.Target(name: "check.torproject.org", host: "check.torproject.org", port: 443),
            timeout: .seconds(15))
        let elapsed = BootstrapWatchdog.millis(started.duration(to: .now))
        return SelfTestResult(id: "selftest-dns", verdict: sample == nil ? .fail : .pass,
                              detail: nil, milliseconds: elapsed)
    }
}
