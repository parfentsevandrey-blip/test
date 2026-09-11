import XCTest
@testable import Veil

final class NetworkTests: XCTestCase {
    func testConnectivityReportClassification() {
        let dead = ConnectivityProbe.Report(reachedByAddress: 0, addressTargets: 6, nameResolution: false, elapsed: 4, date: .now)
        XCTAssertFalse(dead.internetReachable)
        XCTAssertFalse(dead.isUsable)

        let staleDNS = ConnectivityProbe.Report(reachedByAddress: 3, addressTargets: 6, nameResolution: false, elapsed: 0.2, date: .now)
        XCTAssertTrue(staleDNS.internetReachable)
        XCTAssertFalse(staleDNS.isUsable)

        let healthy = ConnectivityProbe.Report(reachedByAddress: 2, addressTargets: 6, nameResolution: true, elapsed: 0.12, date: .now)
        XCTAssertTrue(healthy.isUsable)
        XCTAssertTrue(healthy.summary.contains("2/6"))
        XCTAssertTrue(healthy.summary.contains("DNS ok"))
    }

    func testTunnelInterfacesAreNeverReset() {
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("utun3"))
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("ipsec0"))
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("ppp0"))
        XCTAssertFalse(NetworkReset.Primary.isTunnelInterface("en0"))
        XCTAssertFalse(NetworkReset.Primary.isTunnelInterface("en5"))

        let wifi = NetworkReset.Primary(interface: "en0", serviceID: "ABC", serviceName: "Wi-Fi", isWiFi: true)
        XCTAssertFalse(wifi.isTunnel)
        XCTAssertEqual(wifi.displayName, "Wi-Fi (en0)")
        let bare = NetworkReset.Primary(interface: "en5", serviceID: nil, serviceName: nil, isWiFi: false)
        XCTAssertEqual(bare.displayName, "en5")
    }

    func testProbeTargetsAreDistinctAndWellFormed() {
        let hosts = ConnectivityProbe.addressTargets.map(\.host)
        XCTAssertEqual(Set(hosts).count, hosts.count)
        for target in ConnectivityProbe.addressTargets {
            XCTAssertEqual(target.host.split(separator: ".").count, 4, "address targets must be IPv4 literals so DNS plays no part")
        }
        XCTAssertFalse(ConnectivityProbe.nameTargets.isEmpty)
    }

    func testTelegramProxyLink() {
        XCTAssertEqual(TelegramIntegration.proxyURL(socksPort: 9050).absoluteString, "tg://socks?server=127.0.0.1&port=9050")
        XCTAssertEqual(TelegramIntegration.proxyURL(socksPort: 9150).query, "server=127.0.0.1&port=9150")
    }

    func testPipelineRowsCoverEveryStageAndNameTheBottleneck() {
        var snapshot = DiagnosticSnapshot()
        snapshot.connection = .connected
        snapshot.connectivity = ConnectivityProbe.Report(reachedByAddress: 0, addressTargets: 6,
                                                         nameResolution: false, elapsed: 4, date: .now)
        snapshot.latency = LatencySummary(median: 9, best: 9, jitter: 8, samples: 6, failures: 0)
        let rows = DiagnosticSnapshot.rows(from: snapshot)
        XCTAssertEqual(rows.count, PipelineStage.allCases.count, "a diagnostic must never hide a row")
        XCTAssertEqual(DiagnosticSnapshot.bottleneck(in: rows)?.stage, .network,
                       "a downstream number is meaningless when an upstream stage is broken")
    }

    func testFailedExitVerificationOutranksEverything() {
        var snapshot = DiagnosticSnapshot()
        snapshot.connection = .connected
        snapshot.torCheck = TorCheckResult(isTor: false, ip: "203.0.113.9")
        snapshot.connectivity = ConnectivityProbe.Report(reachedByAddress: 0, addressTargets: 6,
                                                         nameResolution: false, elapsed: 4, date: .now)
        let rows = DiagnosticSnapshot.rows(from: snapshot)
        XCTAssertEqual(DiagnosticSnapshot.bottleneck(in: rows)?.stage, .verify)
        XCTAssertTrue(DiagnosticSnapshot.headline(rows: rows, snapshot: snapshot).contains("NOT going through Tor"))
    }

    func testABypassIsNeverRenderedAsHealthy() {
        var snapshot = DiagnosticSnapshot()
        snapshot.connection = .connected
        snapshot.bypassClasses = 2
        let row = DiagnosticSnapshot.rows(from: snapshot).first { $0.stage == .bypass }
        XCTAssertEqual(row?.state, .degraded)
    }

    func testIdleThroughputIsNotAFault() {
        var snapshot = DiagnosticSnapshot()
        snapshot.connection = .connected
        snapshot.bridgeInFlight = 0
        let idle = DiagnosticSnapshot.rows(from: snapshot).first { $0.stage == .throughput }
        XCTAssertEqual(idle?.state, .ok)

        // A quiet second with connections open is not a fault: an idle SSH session reads zero.
        snapshot.bridgeInFlight = 4
        let quiet = DiagnosticSnapshot.rows(from: snapshot).first { $0.stage == .throughput }
        XCTAssertEqual(quiet?.state, .ok)

        // A counter that has stopped being reported while connections are open is.
        snapshot.trafficStalled = true
        let stalled = DiagnosticSnapshot.rows(from: snapshot).first { $0.stage == .throughput }
        XCTAssertEqual(stalled?.state, .degraded, "a stopped counter with connections open is a stated fault")
    }

    func testStalenessOnlyReportedPastTheStageCadence() {
        let now = Date.now
        XCTAssertNil(StageThresholds.age(of: now.addingTimeInterval(-1), stage: .circuit, now: now))
        XCTAssertNotNil(StageThresholds.age(of: now.addingTimeInterval(-120), stage: .circuit, now: now))
        XCTAssertNil(StageThresholds.age(of: now.addingTimeInterval(-999), stage: .verify, now: now),
                     "an on-demand check is never stale")
    }
}
