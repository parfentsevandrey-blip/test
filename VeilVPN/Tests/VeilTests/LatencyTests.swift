import XCTest
@testable import Veil

final class LatencyTests: XCTestCase {
    private func samples(_ values: [TimeInterval]) -> [LatencySample] {
        values.map { LatencySample(date: .now, seconds: $0, target: "1.1.1.1") }
    }

    func testMedianIgnoresASingleSpike() throws {
        // Nine sane round trips and one four-second outlier: the headline number must not move.
        let summary = try XCTUnwrap(LatencySummary.make(from: samples([0.31, 0.29, 0.33, 0.30, 4.0, 0.32, 0.28, 0.34, 0.30, 0.31]), failures: 1))
        XCTAssertEqual(summary.median, 0.31, accuracy: 0.02)
        XCTAssertEqual(summary.best, 0.28, accuracy: 0.001)
        XCTAssertEqual(summary.samples, 10)
        XCTAssertEqual(summary.failures, 1)
        // A mean would have landed near 0.68 s — more than twice the truth.
        let mean = [0.31, 0.29, 0.33, 0.30, 4.0, 0.32, 0.28, 0.34, 0.30, 0.31].reduce(0, +) / 10
        XCTAssertGreaterThan(mean, summary.median * 2)
    }

    func testJitterSeparatesSteadyRoutesFromUnsteadyOnes() throws {
        let steady = try XCTUnwrap(LatencySummary.make(from: samples([0.40, 0.42, 0.39, 0.41, 0.40, 0.43]), failures: 0))
        XCTAssertTrue(steady.isStable)
        XCTAssertLessThan(steady.jitter, 0.05)

        let unsteady = try XCTUnwrap(LatencySummary.make(from: samples([0.40, 2.4, 0.45, 3.1, 0.42, 2.9]), failures: 0))
        XCTAssertFalse(unsteady.isStable)
        XCTAssertGreaterThan(unsteady.jitter, 1.0)
    }

    func testEmptyWindowHasNoSummary() {
        XCTAssertNil(LatencySummary.make(from: [], failures: 5))
        XCTAssertEqual(LatencySummary.percentile([], 0.5), 0)
        XCTAssertEqual(LatencySummary.percentile([0.2], 0.9), 0.2, accuracy: 0.0001)
        XCTAssertEqual(LatencySummary.percentile([0.1, 0.2, 0.3], 0.5), 0.2, accuracy: 0.0001)
    }

    func testProbeTargetsRotateAndAreAddressLiterals() {
        XCTAssertEqual(LatencyProbe.target(at: 0).host, LatencyProbe.targets[0].host)
        XCTAssertEqual(LatencyProbe.target(at: LatencyProbe.targets.count).host, LatencyProbe.targets[0].host)
        XCTAssertEqual(LatencyProbe.target(at: -1).host, LatencyProbe.targets[LatencyProbe.targets.count - 1].host)
        for target in LatencyProbe.targets {
            XCTAssertNotNil(SOCKS5.ipv4Octets(target.host), "probe targets must be literals so no exit DNS enters the measurement")
            XCTAssertEqual(target.port, 443)
        }
    }

    func testSOCKSAddressLiteralDetection() {
        XCTAssertEqual(SOCKS5.ipv4Octets("1.1.1.1"), [1, 1, 1, 1])
        XCTAssertEqual(SOCKS5.ipv4Octets("192.168.0.255"), [192, 168, 0, 255])
        XCTAssertNil(SOCKS5.ipv4Octets("example.com"))
        XCTAssertNil(SOCKS5.ipv4Octets("duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion"))
        XCTAssertNil(SOCKS5.ipv4Octets("256.1.1.1"))
        XCTAssertNil(SOCKS5.ipv4Octets("1.1.1"))
        XCTAssertNil(SOCKS5.ipv4Octets("1.1.1.1.1"))
        XCTAssertNil(SOCKS5.ipv4Octets("1.1.1."))
    }

    @MainActor
    func testAttemptBudgetsScaleWithWhatIsAlreadyOnDisk() {
        var hot = WarmthProfile()
        hot.consensus = .fresh
        hot.tier = .hot
        var cold = WarmthProfile()
        cold.consensus = .missing
        cold.tier = .cold

        let warm = AttemptPlanner.config(transport: .snowflake, index: 0, count: 2, warmth: hot,
                                         history: NetworkHistory(), marginalLink: false, warmEngine: true,
                                         elapsed: 0, deadline: 90, bridgeLineCount: 2)
        let chilly = AttemptPlanner.config(transport: .snowflake, index: 0, count: 2, warmth: cold,
                                           history: NetworkHistory(), marginalLink: false, warmEngine: false,
                                           elapsed: 0, deadline: 210, bridgeLineCount: 2)
        XCTAssertLessThan(warm.budget(for: .directory), chilly.budget(for: .directory),
                          "a fresh consensus means no directory work; a missing one means all of it")
        XCTAssertLessThan(warm.budget(for: .launch), chilly.budget(for: .launch))
        XCTAssertGreaterThan(AttemptPlanner.overallDeadline(tier: .cold),
                             AttemptPlanner.overallDeadline(tier: .hot))
    }

    func testEveryStallBudgetHasAFloor() {
        // A learned p80 may widen a budget, never narrow it into brittleness.
        for transport in AppSettings.Transport.allCases {
            for stage in BootstrapStage.allCases {
                let budget = BootstrapDefaults.stall(transport: transport, stage: stage, consensus: .fresh,
                                                     warmEngine: true, marginalLink: false, learnedP80: 0.001)
                XCTAssertGreaterThanOrEqual(budget, .seconds(4))
            }
        }
    }
}
