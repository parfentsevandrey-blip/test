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
    func testBootstrapBudgetsStayBoundedAndFavourAKnownTransport() {
        let known = AppState.bootstrapBudget(isLast: false, isKnownGood: true, elapsed: 0)
        let fresh = AppState.bootstrapBudget(isLast: false, isKnownGood: false, elapsed: 0)
        XCTAssertLessThan(known.timeout, fresh.timeout)
        XCTAssertLessThan(known.stall, fresh.stall)

        // The final attempt gets whatever is left of the overall deadline, never less than a minute.
        let early = AppState.bootstrapBudget(isLast: true, isKnownGood: false, elapsed: 0)
        let late = AppState.bootstrapBudget(isLast: true, isKnownGood: false, elapsed: AppState.connectDeadline + 100)
        XCTAssertEqual(early.timeout, .seconds(Int(AppState.connectDeadline)))
        XCTAssertEqual(late.timeout, .seconds(60))
    }
}
