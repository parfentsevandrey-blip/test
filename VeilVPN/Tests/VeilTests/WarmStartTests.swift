import XCTest
@testable import Veil

/// Everything that decides how a connection attempt is shaped, without a network or a Tor process.
final class WarmStartTests: XCTestCase {
    // MARK: Control events

    func testStatusClientBootstrapParsesQuotedValues() throws {
        let event = #"STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=25 TAG=requesting_descriptors SUMMARY="Asking for relay descriptors" WARNING="Connection refused = 1" REASON=CONNECTREFUSED COUNT=2 RECOMMENDATION=warn"#
        let status = try XCTUnwrap(TorControlEvents.parseStatusClient(event))
        guard case .bootstrap(let percent, let tag, let summary, let warning, let reason, let count, let recommendation, _) = status else {
            return XCTFail("expected a bootstrap status")
        }
        XCTAssertEqual(percent, 25)
        XCTAssertEqual(tag, "requesting_descriptors")
        XCTAssertEqual(summary, "Asking for relay descriptors")
        XCTAssertEqual(warning, "Connection refused = 1", "a warning containing spaces and = must survive")
        XCTAssertEqual(reason, "CONNECTREFUSED")
        XCTAssertEqual(count, 2)
        XCTAssertEqual(recommendation, "warn")
    }

    func testOtherClientStatusesAndNoise() {
        XCTAssertEqual(TorControlEvents.parseStatusClient("STATUS_CLIENT NOTICE CIRCUIT_ESTABLISHED"), .circuitEstablished)
        XCTAssertEqual(TorControlEvents.parseStatusClient("STATUS_CLIENT NOTICE TRANSPORT_LAUNCHED TYPE=client NAME=obfs4 ADDRESS=127.0.0.1 PORT=51000"),
                       .transportLaunched("obfs4"))
        XCTAssertNil(TorControlEvents.parseStatusClient("CIRC 12 BUILT $AAAA~relay"))
        XCTAssertNil(TorControlEvents.parseStatusClient("STATUS_CLIENT NOTICE SOMETHING_NEW"))
    }

    func testORConnAndBandwidthParsing() throws {
        let connection = try XCTUnwrap(TorControlEvents.parseORConn("ORCONN 1.2.3.4:9001 FAILED REASON=CONNECTREFUSED NCIRCS=0"))
        XCTAssertEqual(connection.target, "1.2.3.4:9001")
        XCTAssertEqual(connection.status, "FAILED")
        XCTAssertEqual(connection.reason, "CONNECTREFUSED")

        let bandwidth = try XCTUnwrap(TorControlEvents.parseBW("BW 1234 5678"))
        XCTAssertEqual(bandwidth.read, 1234)
        XCTAssertEqual(bandwidth.written, 5678)
        XCTAssertNil(TorControlEvents.parseBW("BW nope"))
    }

    func testRelayTargetsAreNormalisedSoOneRelayCountsOnce() {
        // Tor versions name the same relay both ways; a doubled target must not inflate a count.
        XCTAssertEqual(TorControlEvents.normalizeORTarget("$AAAA1111~moria"), "AAAA1111")
        XCTAssertEqual(TorControlEvents.normalizeORTarget("$aaaa1111=moria"), "AAAA1111")
        XCTAssertEqual(TorControlEvents.normalizeORTarget("1.2.3.4:9001"), "1.2.3.4:9001")
    }

    func testBindAndLockFailuresAreRecognised() {
        XCTAssertEqual(TorControlEvents.parseBindFailure("Could not bind to 127.0.0.1:9051: Address already in use"), 9051)
        XCTAssertNil(TorControlEvents.parseBindFailure("Bootstrapped 10%"))
        XCTAssertTrue(TorControlEvents.parseLockFailure("Could not lock data directory."))
        XCTAssertFalse(TorControlEvents.parseLockFailure("Opening Socks listener"))
    }

    func testListenerParsingStripsQuotes() {
        XCTAssertEqual(TorControlEvents.parseListeners("\"127.0.0.1:9050\" \"127.0.0.1:9060\""),
                       ["127.0.0.1:9050", "127.0.0.1:9060"])
        XCTAssertEqual(TorControlEvents.parseListeners(""), [])
    }

    // MARK: Watchdog

    private func config(stall: Double = 10, hard: Double = 60, firstHop: Double = 8,
                        distinct: Int = 3) -> BootstrapWatchdog.Config {
        var budgets: [BootstrapStage: Duration] = [:]
        for stage in BootstrapStage.allCases { budgets[stage] = .seconds(stall) }
        return BootstrapWatchdog.Config(stall: budgets, hardTimeout: .seconds(hard),
                                        firstHopDeadline: .seconds(firstHop),
                                        distinctFailuresToAbort: distinct)
    }

    func testReachingAHundredPercentSucceeds() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(), startedAt: start)
        XCTAssertEqual(watchdog.handle(.bootstrap(percent: 50, tag: "x", warning: nil, reason: nil, count: nil,
                                                  recommendation: nil, hostAddress: nil), at: start), .keepWaiting)
        XCTAssertEqual(watchdog.handle(.bootstrap(percent: 100, tag: "done", warning: nil, reason: nil, count: nil,
                                                  recommendation: nil, hostAddress: nil), at: start), .succeeded)
    }

    func testAnEstablishedCircuitSucceedsEvenWhenThePercentageNeverMoves() {
        // Tor's bootstrap counter is monotone and does not reset when the bridge lines change.
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(), startedAt: start)
        XCTAssertEqual(watchdog.handle(.circuitEstablished, at: start), .succeeded)
    }

    func testDistinctFirstHopRefusalsAbort() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(distinct: 3), startedAt: start)
        watchdog.handle(.transportLaunched("obfs4"), at: start)
        XCTAssertEqual(watchdog.handle(.orConn(target: "$AAAA~a", status: "FAILED", reason: "CONNECTREFUSED"), at: start), .keepWaiting)
        // The same relay named two ways must count once.
        XCTAssertEqual(watchdog.handle(.orConn(target: "$aaaa=a", status: "FAILED", reason: "CONNECTREFUSED"), at: start), .keepWaiting)
        XCTAssertEqual(watchdog.handle(.orConn(target: "$BBBB~b", status: "FAILED", reason: "CONNECTREFUSED"), at: start), .keepWaiting)
        XCTAssertEqual(watchdog.handle(.orConn(target: "$CCCC~c", status: "FAILED", reason: "CONNECTREFUSED"), at: start),
                       .abort(.firstHopRefused))
    }

    func testAnUnknownFailureReasonCanNeverAbortOnItsOwn() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(distinct: 1), startedAt: start)
        watchdog.handle(.transportLaunched("obfs4"), at: start)
        for name in ["$A~a", "$B~b", "$C~c", "$D~d"] {
            XCTAssertEqual(watchdog.handle(.orConn(target: name, status: "FAILED", reason: "SOMETHING_NEW"), at: start),
                           .keepWaiting)
        }
    }

    func testBytesRescueASlowButWorkingDirectoryFetch() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(stall: 10), startedAt: start)
        watchdog.handle(.transportLaunched("obfs4"), at: start)
        var now = start
        // Nine seconds of silence but real bytes arriving: this must not be killed.
        for _ in 0..<9 {
            now = now.advanced(by: .seconds(1))
            XCTAssertEqual(watchdog.handle(.bytes(read: 20_000, written: 0), at: now), .keepWaiting)
            XCTAssertEqual(watchdog.handle(.tick, at: now), .keepWaiting)
        }
        XCTAssertGreaterThan(watchdog.bytesEscapes, 0)
    }

    func testAProcessThatNeverLaunchesItsTransportAborts() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(firstHop: 5), startedAt: start)
        XCTAssertEqual(watchdog.handle(.tick, at: start.advanced(by: .seconds(6))), .abort(.ptLaunchFailed))
    }

    func testASilentControlConnectionAbortsQuickly() {
        let start = ContinuousClock.now
        var watchdog = BootstrapWatchdog(config: config(), startedAt: start)
        watchdog.handle(.transportLaunched("obfs4"), at: start)
        watchdog.handle(.bytes(read: 1, written: 1), at: start)
        XCTAssertEqual(watchdog.handle(.tick, at: start.advanced(by: .seconds(7))), .abort(.controlUnavailable))
    }

    func testFailuresThatSayNothingAboutTheTransportAreNotRecorded() {
        XCTAssertFalse(AttemptFailure.portInUse.countsAgainstTransport)
        XCTAssertFalse(AttemptFailure.dataDirectoryLocked.countsAgainstTransport)
        XCTAssertFalse(AttemptFailure.noInternet.countsAgainstTransport)
        XCTAssertTrue(AttemptFailure.directoryStalled.countsAgainstTransport)
    }

    // MARK: Disk evidence

    func testConsensusFreshness() throws {
        let header = """
        network-status-version 3 microdesc
        vote-status consensus
        valid-after 2026-09-10 18:00:00
        fresh-until 2026-09-10 19:00:00
        valid-until 2026-09-10 21:00:00
        """
        let info = try XCTUnwrap(ConsensusInfo.parse(header))
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let at = { (text: String) in formatter.date(from: text)! }
        XCTAssertEqual(info.freshness(now: at("2026-09-10T18:30:00Z")), .fresh)
        XCTAssertEqual(info.freshness(now: at("2026-09-10T20:00:00Z")), .live)
        XCTAssertEqual(info.freshness(now: at("2026-09-11T10:00:00Z")), .stale)
        XCTAssertEqual(info.freshness(now: at("2026-09-13T10:00:00Z")), .expired)
        // A consensus from the future means the clock is wrong; never trust it.
        XCTAssertEqual(info.freshness(now: at("2026-09-10T12:00:00Z")), .stale)
        XCTAssertNil(ConsensusInfo.parse("not a consensus"))
    }

    func testStateFileGuardsAndBuildTimes() {
        let text = """
        Guard in=default rsa_id=9695 nickname=moria sampled_on=2026-08-01T12:00:00 listed=1 confirmed_on=2026-08-02T09:14:00
        Guard in=default rsa_id=1111 nickname=other sampled_on=2026-08-01T12:00:00 listed=1
        Guard in=bridges bridge_addr=192.0.2.3:80 rsa_id=2B28 sampled_on=2026-09-09T18:11:02 listed=1 confirmed_on=2026-09-09T18:11:40
        CircuitBuildTimeBin 1500 3
        TotalBuildTimes 143
        """
        let summary = TorStateSummary.parse(text)
        XCTAssertEqual(summary.defaultGuards, 2)
        XCTAssertEqual(summary.confirmedDefaultGuards, 1, "an unconfirmed guard is not evidence")
        XCTAssertEqual(summary.bridgeGuards.count, 1)
        XCTAssertEqual(summary.totalBuildTimes, 143)
        XCTAssertTrue(summary.learnedTimeoutReady)
        // A truncated file yields zeros rather than throwing.
        XCTAssertEqual(TorStateSummary.parse("garbage").defaultGuards, 0)
    }

    func testWarmthTierIsMonotone() {
        let cold = WarmthProfile.tier(consensus: .missing, microdescsUsable: false, hasCerts: false,
                                      guardsKnown: false, processWarm: false)
        let cool = WarmthProfile.tier(consensus: .stale, microdescsUsable: false, hasCerts: false,
                                      guardsKnown: false, processWarm: false)
        let warm = WarmthProfile.tier(consensus: .live, microdescsUsable: true, hasCerts: true,
                                      guardsKnown: false, processWarm: false)
        let hot = WarmthProfile.tier(consensus: .fresh, microdescsUsable: true, hasCerts: true,
                                     guardsKnown: true, processWarm: true)
        XCTAssertEqual(cold, .cold)
        XCTAssertEqual(cool, .cool)
        XCTAssertEqual(warm, .warm)
        XCTAssertEqual(hot, .hot)
        XCTAssertLessThan(cold, cool)
        XCTAssertLessThan(warm, hot)
    }

    func testEvidencedTransportsComeFromWhatTorConfirmed() throws {
        let defaults = PluggableTransportDefaults.builtin
        let line = try XCTUnwrap(defaults.bridges["obfs4"]?.first)
        let address = String(line.split(separator: " ")[1])
        var summary = TorStateSummary()
        summary.bridgeGuards = [BridgeGuard(address: address, fingerprint: nil,
                                            listed: true, sampledOn: .now, confirmedOn: .now)]
        let transports = TorStateStore.evidencedTransports(summary, defaults: defaults, customBridges: "")
        XCTAssertEqual(transports.first, .obfs4)
    }

    // MARK: Planning

    func testTheOrderComesFromEvidenceRatherThanAProbe() {
        var warmth = WarmthProfile()
        warmth.evidencedTransports = [.obfs4]
        var settings = AppSettings()
        settings.transport = .auto
        let plan = AttemptPlanner.candidates(settings: settings, warmth: warmth,
                                             history: NetworkHistory(), reachability: nil)
        XCTAssertEqual(plan.ordered.first, .obfs4, "tor confirmed this transport on this machine")
    }

    func testATransportThatKeepsFailingHereIsSkipped() {
        var history = NetworkHistory()
        var record = TransportRecord()
        for _ in 0..<40 {
            record.record(.failure(.firstHopRefused, stage: .firstHop, percent: 5), at: .now)
        }
        history.transports[AppSettings.Transport.direct.rawValue] = record
        var settings = AppSettings()
        settings.transport = .auto
        let plan = AttemptPlanner.candidates(settings: settings, warmth: WarmthProfile(),
                                             history: history, reachability: nil)
        XCTAssertFalse(plan.ordered.isEmpty, "the queue must never be empty")
        XCTAssertEqual(plan.ordered.first != .direct, true)
    }

    func testAConcreteTransportIsNeverReordered() {
        var settings = AppSettings()
        settings.transport = .meek
        let plan = AttemptPlanner.candidates(settings: settings, warmth: WarmthProfile(),
                                             history: NetworkHistory(), reachability: nil)
        XCTAssertEqual(plan.ordered, [.meek])
    }

    func testHistoryIsSmoothedAndDecayed() {
        var record = TransportRecord()
        record.record(.failure(.directoryStalled, stage: .directory, percent: 30), at: .now)
        XCTAssertGreaterThan(record.successRate, 0, "one bad evening must not zero a transport")
        XCTAssertEqual(record.streak, -1)
        record.record(.success(BootstrapOutcome(totalMillis: 5_000, firstHopMillis: 900,
                                                stageMillis: [:], peakPercent: 100, warm: true)), at: .now)
        XCTAssertEqual(record.streak, 1, "a sign change resets the streak")
        for _ in 0..<70 {
            record.record(.failure(.directoryStalled, stage: .directory, percent: 30), at: .now)
        }
        XCTAssertLessThan(record.attempts, 70, "a network that changes behaviour must be believed again")
    }
}
