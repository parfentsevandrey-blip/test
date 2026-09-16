import XCTest
@testable import Veil

final class ReconnectTests: XCTestCase {
    func testAPathChangeIsReportedOnlyBetweenTwoSatisfiedPathsThatDiffer() {
        // The first path seen is the baseline, never an event.
        XCTAssertNil(NetworkWatcher.event(previousSatisfied: true, previousSignature: nil,
                                          satisfied: true, signature: "en0:wifi,192.168.1.1"))
        XCTAssertNil(NetworkWatcher.event(previousSatisfied: true, previousSignature: "en0:wifi,192.168.1.1",
                                          satisfied: true, signature: "en0:wifi,192.168.1.1"))
        XCTAssertEqual(NetworkWatcher.event(previousSatisfied: true, previousSignature: "en0:wifi,192.168.1.1",
                                            satisfied: true, signature: "en5:wiredEthernet,10.0.0.1"), .pathChanged)
        // Loss and recovery outrank a change.
        XCTAssertEqual(NetworkWatcher.event(previousSatisfied: true, previousSignature: "en0:wifi,192.168.1.1",
                                            satisfied: false, signature: ""), .pathLost)
        XCTAssertEqual(NetworkWatcher.event(previousSatisfied: false, previousSignature: "en0:wifi,192.168.1.1",
                                            satisfied: true, signature: "en5:wiredEthernet,10.0.0.1"), .pathRestored)
        // Nothing while the path stays down.
        XCTAssertNil(NetworkWatcher.event(previousSatisfied: false, previousSignature: "x", satisfied: false, signature: ""))
    }

    func testReconnectBackoffDoublesToFiveMinutes() {
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 0), .seconds(15))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 1), .seconds(30))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 2), .seconds(60))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 3), .seconds(120))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 4), .seconds(240))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 5), .seconds(300))
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: 40), .seconds(300), "capped, never overflowing")
        XCTAssertEqual(ReconnectBackoff.delay(afterFailures: -3), .seconds(15))
    }

    func testTheUsersOwnBridgesOutrankTheAssistsAndCommentsDoNotCount() throws {
        var settings = AppSettings()
        settings.assistBridges = "obfs4 10.0.0.1:443 AAAA cert=x iat-mode=0"
        XCTAssertEqual(settings.effectiveCustomBridges, settings.assistBridges)
        settings.customBridges = "# nothing here\n\n"
        XCTAssertEqual(settings.effectiveCustomBridges, settings.assistBridges, "a comment is not a bridge")
        settings.customBridges = "obfs4 10.0.0.2:443 BBBB cert=y iat-mode=0"
        XCTAssertEqual(settings.effectiveCustomBridges, settings.customBridges)

        settings.assistBridgesFetchedAt = Date(timeIntervalSince1970: 1_700_000_000)
        let again = try JSONDecoder().decode(AppSettings.self, from: JSONEncoder().encode(settings))
        XCTAssertEqual(again.assistBridges, settings.assistBridges)
        XCTAssertEqual(again.assistBridgesFetchedAt, settings.assistBridgesFetchedAt)
    }

    @MainActor
    func testAssistBridgesMakeTheCustomTransportACandidate() {
        var settings = AppSettings()
        settings.transport = .auto
        let without = AttemptPlanner.candidates(settings: settings, warmth: WarmthProfile(),
                                                history: NetworkHistory(), reachability: nil)
        XCTAssertFalse(without.ordered.contains(.custom))
        settings.assistBridges = "obfs4 10.0.0.1:443 AAAA cert=x iat-mode=0"
        let with = AttemptPlanner.candidates(settings: settings, warmth: WarmthProfile(),
                                             history: NetworkHistory(), reachability: nil)
        XCTAssertTrue(with.ordered.contains(.custom) || with.skipped.contains { $0.transport == .custom })
        XCTAssertEqual(TorConfiguration.bridgeLines(for: .custom, settings: settings,
                                                    defaults: .builtin), [settings.assistBridges])
    }
}
