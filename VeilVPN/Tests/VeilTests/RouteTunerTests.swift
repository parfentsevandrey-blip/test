import XCTest
@testable import Veil

final class RouteTunerTests: XCTestCase {
    private func circuit(_ id: String, exit: String, middle: String, build: TimeInterval, status: CircuitInfo.Status = .built) -> CircuitInfo {
        let created = Date(timeIntervalSince1970: 1_000)
        return CircuitInfo(
            id: id, status: status,
            path: [RelayRef(fingerprint: "BRIDGE", nickname: "b"), RelayRef(fingerprint: middle, nickname: middle.lowercased()), RelayRef(fingerprint: exit, nickname: exit.lowercased())],
            purpose: "GENERAL", created: created, builtAt: created.addingTimeInterval(build), reason: nil
        )
    }

    @MainActor
    func testRankingPrefersFastExitsAndDropsLaggards() {
        let circuits = [
            circuit("1", exit: "FAST", middle: "M1", build: 0.9),
            circuit("2", exit: "OK", middle: "M2", build: 1.4),
            circuit("3", exit: "FAST", middle: "M3", build: 1.1),
            circuit("4", exit: "SLOW", middle: "M4", build: 4.0),
            circuit("5", exit: "NEVER", middle: "M5", build: 0.1, status: .failed),
        ]
        let ranking = RouteTuner.rank(circuits)
        XCTAssertEqual(ranking.exits.map(\.fingerprint), ["FAST", "OK"])
        XCTAssertEqual(ranking.exits.first?.buildMilliseconds, 900)
        XCTAssertEqual(ranking.middles.first?.fingerprint, "M1")
        XCTAssertFalse(ranking.middles.contains { $0.fingerprint == "M4" })
    }

    @MainActor
    func testBandwidthBreaksNearTies() {
        var a = RelayCandidate(fingerprint: "A", nickname: "a", countryCode: nil, buildMilliseconds: 1000, bandwidth: 1_000)
        var b = RelayCandidate(fingerprint: "B", nickname: "b", countryCode: nil, buildMilliseconds: 1100, bandwidth: 9_000)
        XCTAssertEqual(RouteTuner.preferBandwidth([a, b]).map(\.fingerprint), ["B", "A"])
        b.buildMilliseconds = 1600
        XCTAssertEqual(RouteTuner.preferBandwidth([a, b]).map(\.fingerprint), ["A", "B"])
        a.bandwidth = nil
        XCTAssertEqual(RouteTuner.preferBandwidth([a, b]).map(\.fingerprint), ["A", "B"])
    }

    func testPinnedRouteRendersFingerprints() {
        var route = TorRoute(middleCountry: "de", exitCountry: "se", excludedCountries: ["us"])
        XCTAssertEqual(route.torrcLines, ["MiddleNodes {de}", "ExitNodes {se}", "ExcludeNodes {us}", "StrictNodes 1"])
        route.pinnedExits = ["aaaa", "BBBB"]
        route.pinnedMiddles = ["cccc"]
        route.avoidedRelays = ["dddd", "aaaa"]
        XCTAssertEqual(route.torrcLines, ["MiddleNodes $CCCC", "ExitNodes $AAAA,$BBBB", "ExcludeNodes {us},$DDDD", "StrictNodes 1"])
        XCTAssertTrue(route.isPinned)
        XCTAssertFalse(route.base.isPinned)
        XCTAssertEqual(route.base, TorRoute(middleCountry: "de", exitCountry: "se", excludedCountries: ["us"]))
    }

    @MainActor
    func testPinsApplyOnlyToTheirBaseRoute() async {
        let tuner = RouteTuner()
        let engine = SimulatedTorEngine()
        var settings = AppSettings()
        settings.exitCountry = "se"
        try? await engine.start(settings: settings, ports: ActivePorts(socks: 9050, http: 8118, control: 9051))
        let base = settings.route
        let tuned = await tuner.tune(engine: engine, route: base, circuits: 3, pinMiddle: false, timeout: .seconds(10))
        XCTAssertNotNil(tuned)
        XCTAssertTrue(tuner.isPinned)
        XCTAssertEqual(tuned?.pinnedExits, tuner.pinnedExits.map(\.fingerprint))
        XCTAssertEqual(tuner.route(for: base).pinnedExits, tuner.pinnedExits.map(\.fingerprint))
        var other = base
        other.exitCountry = "de"
        XCTAssertTrue(tuner.route(for: other).pinnedExits.isEmpty)
        await engine.stop()
    }

    func testPerformanceTorrcLines() {
        var settings = AppSettings()
        XCTAssertEqual(TorConfiguration.performanceLines(for: settings), ["MaxClientCircuitsPending 48", "ConfluxEnabled 1", "ConfluxClientUX latency"])
        settings.confluxLatency = false
        XCTAssertEqual(TorConfiguration.performanceLines(for: settings), ["MaxClientCircuitsPending 48"])
        let line = "snowflake 192.0.2.3:80 2B28 url=https://x fronts=a,b"
        XCTAssertEqual(TorConfiguration.snowflakeLine(line, peers: 3), line + " max=3")
        XCTAssertEqual(TorConfiguration.snowflakeLine(line, peers: 1), line)
        XCTAssertEqual(TorConfiguration.snowflakeLine(line + " max=2", peers: 3), line + " max=2")
        XCTAssertEqual(TorConfiguration.snowflakeLine("obfs4 1.2.3.4:443 ABC", peers: 3), "obfs4 1.2.3.4:443 ABC")
    }
}
