import XCTest
@testable import Veil

/// The posture rubric, with exact integers: all of it is integer arithmetic, so these numbers are
/// the same on every machine.
final class SecurityTests: XCTestCase {
    private func input(_ mutate: (inout SecurityInput) -> Void = { _ in }) -> SecurityInput {
        var value = SecurityInput()
        value.connection = .connected
        value.torCheck = TorCheckResult(isTor: true, ip: "198.51.100.7")
        value.transportDirectoryIsPrivate = true
        mutate(&value)
        return value
    }

    func testTheCeilingFallsOutOfTheWeights() {
        let total = Adversary.allCases.reduce(0) { $0 + $1.weight }
        XCTAssertEqual(total, 100)
        let maximum = Adversary.allCases.reduce(0) { $0 + $1.weight * $1.ceiling }
        XCTAssertEqual((maximum + 50) / 100, SecurityPosture.absoluteMaximum)
    }

    func testTheScoreCanNeverExceedTheCeiling() {
        // Coverage is capped per adversary by construction, so this is a theorem, not a hope.
        for settings in [AppSettings(), SecurityPreset.privacyFirst.applied(to: AppSettings()),
                         SecurityPreset.speedFirst.applied(to: AppSettings())] {
            let posture = SecurityPostureEvaluator.evaluate(input { $0.settings = settings })
            XCTAssertLessThanOrEqual(posture.score ?? 0, SecurityPosture.absoluteMaximum)
            for adversary in Adversary.allCases {
                XCTAssertLessThanOrEqual(posture.coverage[adversary] ?? 0, adversary.ceiling)
            }
        }
    }

    func testTurboCollapsesEveryNetworkAdversary() {
        let posture = SecurityPostureEvaluator.evaluate(input {
            $0.turboActive = true
            $0.connection = .disconnected
        })
        XCTAssertEqual(posture.coverage[.localNetwork], 0)
        XCTAssertEqual(posture.coverage[.ispDPI], 0)
        XCTAssertEqual(posture.coverage[.exitRelay], 0)
        XCTAssertEqual(posture.live, .turbo)
        XCTAssertEqual(posture.grade, .exposed)
    }

    func testAKillSwitchWithNoProxyToHoldIsCalledInert() {
        let posture = SecurityPostureEvaluator.evaluate(input {
            $0.settings.killSwitch = true
            $0.settings.configureSystemProxy = false
        })
        XCTAssertTrue(posture.findings.contains { $0.id == "kill-switch-inert" && $0.severity == .critical })
        // 60 + 25 + 8 exceeds the ceiling; coverage floors at zero rather than going negative.
        XCTAssertEqual(posture.coverage[.localNetwork], 0)
    }

    func testPinnedExitsAreNamedEvenWithIsolationOn() {
        // Isolation gives separate circuits, but they all still end at the same pinned exits.
        let posture = SecurityPostureEvaluator.evaluate(input {
            $0.settings.latencyTuning = true
            $0.settings.isolatePerSite = true
            $0.pinnedExitCount = 2
        })
        let finding = posture.findings.first { $0.id == "shared-exit" }
        XCTAssertNotNil(finding)
        XCTAssertEqual(finding?.penalty, 35)

        let worse = SecurityPostureEvaluator.evaluate(input {
            $0.settings.latencyTuning = true
            $0.settings.isolatePerSite = false
            $0.pinnedExitCount = 2
        })
        XCTAssertEqual(worse.findings.first { $0.id == "shared-exit" }?.penalty, 55)
    }

    func testStockSettingsShipWithOneBypassClass() {
        // The catalogue ships Apple direct, because App Store downloads stall through exits.
        XCTAssertEqual(SecurityPostureEvaluator.bypassClasses(SecurityInput()), 1)
    }

    func testClearingBypassesMustWriteExplicitTorRoutes() {
        // Emptying the dictionary would put Apple straight back to direct, because the policy
        // merges over the catalogue's own defaults.
        var settings = AppSettings()
        settings.serviceRoutes = [:]
        XCTAssertEqual(SecurityPostureEvaluator.bypassClasses(SecurityInput(settings: settings)), 1)
        settings.serviceRoutes = SecurityPreset.allServicesThroughTor
        XCTAssertEqual(SecurityPostureEvaluator.bypassClasses(SecurityInput(settings: settings)), 0)
    }

    func testPrivacyFirstReachesTheRecommendedMaximumAndRaisesNoCriticalFinding() {
        let posture = SecurityPostureEvaluator.evaluate(input {
            $0.settings = SecurityPreset.privacyFirst.applied(to: AppSettings())
            $0.paddingActive = true
            $0.lanePoolActive = true
        })
        XCTAssertEqual(posture.score, SecurityPosture.recommendedMaximum)
        XCTAssertEqual(posture.grade, .hardened)
        XCTAssertTrue(posture.criticalFindings.isEmpty)
    }

    func testTheGuardRowOffersNoButton() {
        let posture = SecurityPostureEvaluator.evaluate(input())
        let finding = posture.findings.first { $0.id == "tor-state-persists" }
        XCTAssertEqual(finding?.fix, SecurityFinding.Fix.none,
                       "clearing it discards Tor's entry guard, which is worse for anonymity")
    }

    func testFindingsAreOrderedDeterministically() {
        let first = SecurityPostureEvaluator.evaluate(input()).findings.map(\.id)
        for _ in 0..<20 {
            XCTAssertEqual(SecurityPostureEvaluator.evaluate(input()).findings.map(\.id), first,
                           "serviceRoutes is a dictionary; order must never leak into the output")
        }
    }

    func testDemoModeRefusesToScore() {
        let posture = SecurityPostureEvaluator.evaluate(input { $0.isDemo = true })
        XCTAssertNil(posture.score)
        XCTAssertEqual(posture.grade, .unknown)
        XCTAssertEqual(posture.live, .unavailable)
    }

    func testEveryFindingAndLimitHasCopy() {
        var ids = Set(SecurityLimits.all.map(\.id))
        for settings in [AppSettings(), SecurityPreset.privacyFirst.applied(to: AppSettings()),
                         SecurityPreset.speedFirst.applied(to: AppSettings())] {
            for turbo in [true, false] {
                let posture = SecurityPostureEvaluator.evaluate(input {
                    $0.settings = settings
                    $0.turboActive = turbo
                    $0.pinnedExitCount = 2
                    $0.settings.verboseLogs = true
                    $0.settings.customBridges = "obfs4 1.2.3.4:443"
                })
                ids.formUnion(posture.findings.map(\.id))
            }
        }
        for id in ids {
            XCTAssertFalse(SecurityCopy.detail(id).isEmpty, "\(id) has no explanation")
        }
    }

    func testExclusionsApplyWithoutMultihop() {
        var settings = AppSettings()
        settings.avoidFiveEyes = true
        settings.multihopEnabled = false
        XCTAssertFalse(settings.route.excludedCountries.isEmpty,
                       "asking to avoid a country must not be silently ignored")
    }

    func testSelfTestReductionReportsTheWorstVerdict() {
        let results = [SelfTestResult(id: "a", verdict: .pass), SelfTestResult(id: "b", verdict: .warn)]
        XCTAssertEqual(SelfTestReport.reduce(results, at: .now).worst, .warn)
        XCTAssertEqual(SelfTestReport.reduce(results + [SelfTestResult(id: "c", verdict: .fail)], at: .now).worst, .fail)
        XCTAssertEqual(SelfTestReport.reduce([SelfTestResult(id: "a", verdict: .skipped)], at: .now).worst, .skipped)
    }

    func testTheLanePoolAndRelayPinningAreMutuallyExclusive() throws {
        var stored = AppSettings()
        stored.latencyTuning = true
        stored.lanePoolEnabled = true
        let data = try JSONEncoder().encode(stored)
        let decoded = try JSONDecoder().decode(AppSettings.self, from: data)
        XCTAssertTrue(decoded.lanePoolEnabled)
        XCTAssertFalse(decoded.latencyTuning,
                       "pinned exits collapse every lane onto one relay")
    }

    func testSettingsWrittenByOlderVersionsStillDecode() throws {
        let legacy = #"{"transport":"snowflake","killSwitch":false}"#
        let decoded = try JSONDecoder().decode(AppSettings.self, from: Data(legacy.utf8))
        XCTAssertEqual(decoded.transport, .snowflake)
        XCTAssertFalse(decoded.killSwitch)
        XCTAssertEqual(decoded.warmStart, AppSettings().warmStart)
        XCTAssertEqual(decoded.lanePoolSize, AppSettings().lanePoolSize)
        XCTAssertEqual(decoded.forgetPolicy, .off)
    }
}
