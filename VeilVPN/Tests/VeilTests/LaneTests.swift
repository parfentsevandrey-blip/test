import XCTest
@testable import Veil

/// The lane pool's decisions, without Tor and without a socket.
final class LaneTests: XCTestCase {
    private func row(_ id: Int, p50: TimeInterval?, state: LaneState = .ready, inFlight: Int = 0,
                     site: Bool = false, failures: Int = 0) -> LaneRow {
        LaneRow(id: id, scope: site ? .site(id) : .pool(id), generation: 1, state: state,
                p50: p50, p90: p50, liveP50: nil, probeSamples: p50 == nil ? 0 : 4,
                consecutiveFailures: failures, inFlight: inFlight, assignedSites: 0,
                openedAt: .now, lastActiveSampleAt: .now, recentPassiveSuccesses: 0)
    }

    // MARK: SOCKS5

    func testGreetingOffersOnlyUsernameAuthWhenCredentialsArePresent() {
        XCTAssertEqual([UInt8](SOCKS5.greeting(for: nil)), [0x05, 0x01, 0x00])
        let credentials = SOCKS5.Credentials(username: "veil-l2", password: "9f3c")
        XCTAssertEqual([UInt8](SOCKS5.greeting(for: credentials)), [0x05, 0x01, 0x02])
    }

    func testAuthRequestFollowsRFC1929() {
        let request = [UInt8](SOCKS5.authRequest(SOCKS5.Credentials(username: "veil-l2", password: "abc")))
        XCTAssertEqual(request[0], 0x01, "the sub-negotiation version is 0x01, not 0x05")
        XCTAssertEqual(Int(request[1]), 7)
        XCTAssertEqual(String(decoding: request[2..<9], as: UTF8.self), "veil-l2")
        XCTAssertEqual(Int(request[9]), 3)
        XCTAssertEqual(String(decoding: request[10..<13], as: UTF8.self), "abc")
    }

    func testEmptyCredentialFieldsArePaddedBecauseLengthsMustBeAtLeastOne() {
        let request = [UInt8](SOCKS5.authRequest(SOCKS5.Credentials(username: "", password: "")))
        XCTAssertEqual(request[1], 1)
        XCTAssertEqual(request[3], 1)
    }

    func testConnectRequestSendsNamesAsNamesAndAddressesAsAddresses() {
        let named = [UInt8](SOCKS5.connectRequest(host: "example.com", port: 443))
        XCTAssertEqual(named[3], 0x03, "a host name must be resolved by Tor, not here")
        let literal = [UInt8](SOCKS5.connectRequest(host: "1.1.1.1", port: 443))
        XCTAssertEqual(literal[3], 0x01)
        XCTAssertEqual(Array(literal[4..<8]), [1, 1, 1, 1])
    }

    func testOnlyCircuitAttributableRepliesEarnARetry() {
        for code in [0x01, 0x02, 0x03, 0x04, 0x06] {
            XCTAssertTrue(SOCKS5.laneAttributable(UInt8(code)))
        }
        // The destination refusing is not the circuit's fault; a new one will not help.
        for code in [0x05, 0x07, 0x08, 0xF0] {
            XCTAssertFalse(SOCKS5.laneAttributable(UInt8(code)))
        }
    }

    // MARK: Host keys

    func testSiteKeysGroupSubdomainsButNotSiblings() {
        XCTAssertEqual(HostKey.site("a.b.example.com"), "example.com")
        XCTAssertEqual(HostKey.site("EXAMPLE.COM."), "example.com")
        XCTAssertEqual(HostKey.site("example.org"), "example.org")
        XCTAssertEqual(HostKey.site("shop.example.co.uk"), "example.co.uk")
        XCTAssertEqual(HostKey.site("mysite.pages.dev"), "mysite.pages.dev",
                       "merging every *.pages.dev site onto one circuit would be a real grouping error")
        XCTAssertEqual(HostKey.site("1.2.3.4"), "1.2.3.4")
        XCTAssertEqual(HostKey.site("abcdefghij.onion"), "abcdefghij.onion")
        XCTAssertEqual(HostKey.site("localhost"), "localhost")
    }

    // MARK: Scheduling

    func testAffinityWinsAndIgnoresTheStreamCap() {
        let rows = [row(0, p50: 0.2), row(1, p50: 0.9, inFlight: 99)]
        let pick = LaneScheduler.pick(rows: rows, affinity: 1, avoiding: nil, maxStreamsPerLane: 12,
                                      fastSetFactor: 1.25, randomValue: 0)
        XCTAssertEqual(pick?.lane, 1)
        XCTAssertEqual(pick?.reason, .affinity)
    }

    func testSelectionStaysInsideTheFastSet() {
        let rows = [row(0, p50: 0.20), row(1, p50: 0.24), row(2, p50: 2.0)]
        for value in stride(from: 0.0, to: 1.0, by: 0.05) {
            let pick = LaneScheduler.pick(rows: rows, affinity: nil, avoiding: nil, maxStreamsPerLane: 12,
                                          fastSetFactor: 1.25, randomValue: value)
            XCTAssertNotEqual(pick?.lane, 2, "a circuit ten times slower is never in the fast set")
        }
    }

    func testTheCapRelaxesRatherThanReturningNothing() {
        let rows = [row(0, p50: 0.2, inFlight: 20), row(1, p50: 0.3, inFlight: 20)]
        let pick = LaneScheduler.pick(rows: rows, affinity: nil, avoiding: nil, maxStreamsPerLane: 12,
                                      fastSetFactor: 1.25, randomValue: 0)
        XCTAssertNotNil(pick)
        XCTAssertEqual(pick?.reason, .fallback)
    }

    func testWarmingLanesAreNeverPicked() {
        let rows = [row(0, p50: nil, state: .warming), row(1, p50: nil, state: .suspended)]
        XCTAssertNil(LaneScheduler.pick(rows: rows, affinity: nil, avoiding: nil, maxStreamsPerLane: 12,
                                        fastSetFactor: 1.25, randomValue: 0))
    }

    func testBestIsStrictlyTheLowestMeasured() {
        let rows = [row(0, p50: 0.5), row(1, p50: 0.2), row(2, p50: nil)]
        XCTAssertEqual(LaneScheduler.best(rows: rows, avoiding: nil), 1)
        XCTAssertEqual(LaneScheduler.best(rows: rows, avoiding: 1), 0)
    }

    // MARK: Retirement

    func testABrokenLaneIsRetiredOutOfBand() {
        let rows = [row(0, p50: 0.2), row(1, p50: 0.2, failures: 3)]
        let candidate = LanePolicy.retirementCandidate(rows: rows, deadlines: [:], now: .now,
                                                       lastRetirement: .now, slowFrozenUntil: nil)
        XCTAssertEqual(candidate?.lane, 1)
        XCTAssertEqual(candidate?.reason, .failures)
    }

    func testASlowLaneIsOnlyRetiredWhenItIsAlsoSlowOutright() {
        // Three times the best but only 400 ms: retiring this is pure churn.
        let tight = [row(0, p50: 0.12), row(1, p50: 0.13), row(2, p50: 0.40)]
        XCTAssertNil(LanePolicy.retirementCandidate(rows: tight, deadlines: [:], now: .now,
                                                    lastRetirement: nil, slowFrozenUntil: nil))
        let real = [row(0, p50: 0.4), row(1, p50: 0.5), row(2, p50: 3.0)]
        XCTAssertEqual(LanePolicy.retirementCandidate(rows: real, deadlines: [:], now: .now,
                                                      lastRetirement: nil, slowFrozenUntil: nil)?.reason, .slow)
    }

    func testRetirementIsGatedByTheMinimumGapButExpiryStillWins() {
        let rows = [row(0, p50: 0.4), row(1, p50: 0.5), row(2, p50: 3.0)]
        let now = Date.now
        XCTAssertNil(LanePolicy.retirementCandidate(rows: rows, deadlines: [:], now: now,
                                                    lastRetirement: now.addingTimeInterval(-10),
                                                    slowFrozenUntil: nil))
        let expired = LanePolicy.retirementCandidate(rows: rows, deadlines: [1: now.addingTimeInterval(-1)],
                                                     now: now, lastRetirement: now.addingTimeInterval(-200),
                                                     slowFrozenUntil: nil)
        XCTAssertEqual(expired?.reason, .expired)
    }

    func testTheChurnBrakeStopsSlowRetirementOnABadUplink() {
        let rows = [row(0, p50: 0.4), row(1, p50: 0.5), row(2, p50: 3.0)]
        XCTAssertNil(LanePolicy.retirementCandidate(rows: rows, deadlines: [:], now: .now, lastRetirement: nil,
                                                    slowFrozenUntil: Date.now.addingTimeInterval(600)))
        XCTAssertTrue(LanePolicy.shouldFreezeSlowRetirement(recentSlowRetirements: 5, perWindow: 4))
        XCTAssertFalse(LanePolicy.shouldFreezeSlowRetirement(recentSlowRetirements: 4, perWindow: 4))
    }

    func testSiteLanesAreNeverRankedForSlowness() {
        let rows = [row(0, p50: 0.4), row(1, p50: 0.5), row(2, p50: 9.0, site: true)]
        XCTAssertNil(LanePolicy.retirementCandidate(rows: rows, deadlines: [:], now: .now,
                                                    lastRetirement: nil, slowFrozenUntil: nil))
    }

    // MARK: Hedging

    func testTheHedgeNeverFiresBelowItsFloor() {
        // Everything answering under about a second is the median case and is never touched.
        let fast = HedgePolicy.hedgeDelay(bestP50: 0.30, isOnion: false, enabled: true, readyLanes: 4,
                                          transport: .obfs4, secondsSinceConnect: 300)
        XCTAssertEqual(fast ?? 0, HedgePolicy.floorSeconds, accuracy: 0.001)
        let slow = HedgePolicy.hedgeDelay(bestP50: 0.80, isOnion: false, enabled: true, readyLanes: 4,
                                          transport: .obfs4, secondsSinceConnect: 300)
        XCTAssertEqual(slow ?? 0, 1.6, accuracy: 0.001)
        let capped = HedgePolicy.hedgeDelay(bestP50: 5.0, isOnion: false, enabled: true, readyLanes: 4,
                                            transport: .obfs4, secondsSinceConnect: 300)
        XCTAssertEqual(capped ?? 0, HedgePolicy.ceilingSeconds, accuracy: 0.001)
    }

    func testTheHedgeStandsDownWhereItWouldCostMoreThanItSaves() {
        XCTAssertNil(HedgePolicy.hedgeDelay(bestP50: 0.3, isOnion: true, enabled: true, readyLanes: 4,
                                            transport: .obfs4, secondsSinceConnect: 300))
        XCTAssertNil(HedgePolicy.hedgeDelay(bestP50: 0.3, isOnion: false, enabled: false, readyLanes: 4,
                                            transport: .obfs4, secondsSinceConnect: 300))
        XCTAssertNil(HedgePolicy.hedgeDelay(bestP50: 0.3, isOnion: false, enabled: true, readyLanes: 1,
                                            transport: .obfs4, secondsSinceConnect: 300))
        XCTAssertNil(HedgePolicy.hedgeDelay(bestP50: nil, isOnion: false, enabled: true, readyLanes: 4,
                                            transport: .obfs4, secondsSinceConnect: 300))
        XCTAssertNil(HedgePolicy.hedgeDelay(bestP50: 0.3, isOnion: false, enabled: true, readyLanes: 4,
                                            transport: .snowflake, secondsSinceConnect: 10),
                     "a young snowflake session is still finding proxies")
    }

    func testDeadlinesAreBoundedOnEveryPath() {
        XCTAssertEqual(HedgePolicy.deadline(attempt: 0, isOnion: false, hedgeDelay: 1.2), 1.2)
        XCTAssertEqual(HedgePolicy.deadline(attempt: 0, isOnion: false, hedgeDelay: nil), 10)
        XCTAssertEqual(HedgePolicy.deadline(attempt: 1, isOnion: false, hedgeDelay: 1.2), 10)
        XCTAssertEqual(HedgePolicy.deadline(attempt: 0, isOnion: true, hedgeDelay: 1.2), 30)
    }

    // MARK: Credentials and torrc

    func testCredentialsCarryNoBrowsingHistoryAndEnoughEntropy() {
        let credentials = LanePool.makeCredentials(scope: .pool(2), generation: 7)
        XCTAssertEqual(credentials.username, "veil-l2")
        XCTAssertEqual(credentials.password.count, 32)
        XCTAssertTrue(credentials.password.allSatisfy { $0.isHexDigit })
        XCTAssertEqual(LanePool.makeCredentials(scope: .site(3), generation: 1).username, "veil-s3")
    }

    func testThePoolListenerCarriesIsolationAndNeverDestinationIsolation() {
        let line = TorConfiguration.poolSocksPortLine(port: 9060)
        XCTAssertTrue(line.contains("IsolateSOCKSAuth"))
        XCTAssertTrue(line.contains("KeepAliveIsolateSOCKSAuth"))
        // Isolation keys compose: IsolateDestAddr here would fan every lane into one circuit per
        // destination and the lane-to-circuit mapping would evaporate silently.
        XCTAssertFalse(line.contains("IsolateDestAddr"))
    }

    func testTheLaneListenerIsRenderedOnlyWhenThePoolIsOn() {
        var settings = AppSettings()
        settings.lanePoolEnabled = true
        let ports = ActivePorts(socks: 9050, http: 8118, control: 9051, pool: 9060)
        XCTAssertEqual(TorConfiguration.socksPortLines(settings: settings, ports: ports).count, 2)
        settings.lanePoolEnabled = false
        XCTAssertEqual(TorConfiguration.socksPortLines(settings: settings, ports: ports).count, 1)
        settings.lanePoolEnabled = true
        let noPool = ActivePorts(socks: 9050, http: 8118, control: 9051)
        XCTAssertEqual(TorConfiguration.socksPortLines(settings: settings, ports: noPool).count, 1)
    }

    func testActivationPutsDisableNetworkLast() throws {
        var settings = AppSettings()
        settings.transport = .obfs4
        let pairs = try TorConfiguration.activationAssignments(
            settings: settings,
            ports: ActivePorts(socks: 9050, http: 8118, control: 9051, pool: 9060),
            defaults: .builtin)
        // Load-bearing: the listener and the network must come up inside one options_act, so a
        // failed bind reverts everything instead of going live with nothing listening.
        XCTAssertEqual(pairs.last?.key, "DisableNetwork")
        XCTAssertEqual(pairs.last?.value, "0")
        XCTAssertTrue(pairs.contains { $0.key == "SocksPort" })
        XCTAssertTrue(pairs.contains { $0.key == "Bridge" })
    }

    func testADirectActivationResetsTheBridgeList() throws {
        var settings = AppSettings()
        settings.transport = .direct
        let pairs = try TorConfiguration.activationAssignments(
            settings: settings, ports: ActivePorts(socks: 9050, http: 8118, control: 9051), defaults: .builtin)
        let bridge = try XCTUnwrap(pairs.first { $0.key == "Bridge" })
        XCTAssertNil(bridge.value, "a bare key resets the option to its default")
    }

    func testQuotingSurvivesSpacesAndQuotes() {
        XCTAssertEqual(TorControlClient.quote("a b"), "\"a b\"")
        XCTAssertEqual(TorControlClient.quote("say \"hi\""), "\"say \\\"hi\\\"\"")
        let body = TorControlClient.setConfBody([("Bridge", "obfs4 1.2.3.4:443 cert=a b"), ("UseBridges", "1")])
        XCTAssertEqual(body, "Bridge=\"obfs4 1.2.3.4:443 cert=a b\" UseBridges=\"1\"")
        XCTAssertEqual(TorControlClient.setConfBody([("Bridge", nil)]), "Bridge")
    }

    func testStandbyKeepsTorOffTheWire() {
        var settings = AppSettings()
        settings.transport = .snowflake
        let torrc = TorConfiguration.renderStandby(
            controlPort: 9051, dataDirectory: URL(fileURLWithPath: "/tmp/veil-tor"),
            bundle: TorBundle(directory: URL(fileURLWithPath: "/tmp"), tor: URL(fileURLWithPath: "/tmp/tor"),
                              lyrebird: URL(fileURLWithPath: "/tmp/lyrebird"), conjure: nil,
                              geoip: nil, geoip6: nil, ptConfig: nil),
            pluggableTransportDirectory: URL(fileURLWithPath: "/tmp/pt"), defaults: .builtin,
            settings: settings, predicted: .snowflake, consensus: .fresh, preBootstrap: false)
        XCTAssertTrue(torrc.contains("SocksPort 0"), "no listener while on standby")
        XCTAssertTrue(torrc.contains("DisableNetwork 1"), "no packets while on standby")
        XCTAssertFalse(torrc.contains("AvoidDiskWrites"),
                       "state carries the guard selection this whole design depends on")
        XCTAssertTrue(torrc.contains("ClientTransportPlugin snowflake"))
    }

    func testForgetPolicyKeepsTheGuardUnlessEverythingIsAsked() {
        let contents = ["cached-microdesc-consensus", "cached-microdescs", "state", "keys", "lock", "diff-cache"]
        XCTAssertEqual(StateCleaner.paths(for: .off, contents: contents), [])
        let caches = StateCleaner.paths(for: .caches, contents: contents)
        XCTAssertTrue(caches.contains("cached-microdescs"))
        XCTAssertFalse(caches.contains("state"), "a guard that changes every run is worse, not better")
        XCTAssertFalse(caches.contains("keys"))
        XCTAssertEqual(Set(StateCleaner.paths(for: .everything, contents: contents)), Set(contents))
    }
}
