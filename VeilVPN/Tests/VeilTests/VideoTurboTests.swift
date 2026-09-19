import XCTest
@testable import Veil

final class VideoTurboTests: XCTestCase {
    /// A user who had made every choice the other way.
    private func contrary() -> AppSettings {
        var settings = AppSettings()
        settings.youtubeMode = .directAntiThrottle
        settings.youtubeModeChosen = true
        settings.videoExitEnabled = false
        settings.videoGuardPinning = false
        settings.videoKeepWarm = false
        settings.videoKeepWarmAlways = false
        settings.videoKeepWarmKilobytes = 128
        settings.confluxLatency = true
        settings.paddingEnabled = true
        settings.multihopEnabled = true
        settings.latencyTuning = true
        settings.isolatePerSite = true
        settings.snowflakePeers = 2
        return settings
    }

    func testTurboSetsEverythingAndRemembersWhatItChanged() {
        let base = contrary()
        let on = VideoTurbo.applied(to: base)
        XCTAssertTrue(on.videoTurbo)
        XCTAssertTrue(VideoTurbo.holds(in: on))
        XCTAssertEqual(on.youtubeMode, .tor)
        XCTAssertTrue(on.videoExitEnabled)
        XCTAssertTrue(on.videoGuardPinning)
        XCTAssertTrue(on.videoKeepWarm)
        XCTAssertTrue(on.videoKeepWarmAlways)
        XCTAssertEqual(on.videoKeepWarmKilobytes, VideoTurbo.minimumTonusKilobytes)
        XCTAssertFalse(on.confluxLatency)
        XCTAssertFalse(on.paddingEnabled)
        XCTAssertFalse(on.multihopEnabled)
        XCTAssertFalse(on.latencyTuning)
        XCTAssertFalse(on.isolatePerSite, "the lane race needs the pool's lanes")
        XCTAssertEqual(on.snowflakePeers, VideoTurbo.snowflakePeers)
        XCTAssertEqual(on.videoTurboRestore, VideoTurbo.snapshot(of: base))
        // Nothing outside its remit moves.
        XCTAssertEqual(on.transport, base.transport)
        XCTAssertEqual(on.lanePoolEnabled, base.lanePoolEnabled)
        XCTAssertEqual(on.excludedCountries, base.excludedCountries)
        XCTAssertEqual(on.killSwitch, base.killSwitch)
    }

    func testSwitchingOffPutsTheUsersOwnChoicesBack() {
        let base = contrary()
        let off = VideoTurbo.restored(VideoTurbo.applied(to: base))
        XCTAssertFalse(off.videoTurbo)
        XCTAssertNil(off.videoTurboRestore)
        XCTAssertEqual(off, base, "choices, not defaults")
    }

    func testAHigherTonusRateStands() {
        var base = AppSettings()
        base.videoKeepWarmKilobytes = 1024
        let on = VideoTurbo.applied(to: base)
        XCTAssertEqual(on.videoKeepWarmKilobytes, 1024)
        XCTAssertTrue(VideoTurbo.holds(in: on))
        XCTAssertEqual(VideoTurbo.restored(on).videoKeepWarmKilobytes, 1024)
    }

    func testAHandChangeStandsWhileTheRestGoesBack() {
        let base = contrary()
        var on = VideoTurbo.applied(to: base)
        on.paddingEnabled = true // the user switched padding on by hand: Turbo no longer holds
        XCTAssertFalse(VideoTurbo.holds(in: on))
        let off = VideoTurbo.restored(on)
        XCTAssertFalse(off.videoTurbo)
        XCTAssertTrue(off.paddingEnabled, "the change that switched Turbo off stands")
        XCTAssertEqual(off.youtubeMode, .directAntiThrottle)
        XCTAssertTrue(off.confluxLatency)
        XCTAssertTrue(off.multihopEnabled)
        XCTAssertTrue(off.latencyTuning)
        XCTAssertTrue(off.isolatePerSite)
        XCTAssertEqual(off.snowflakePeers, 2)
        XCTAssertEqual(off.videoKeepWarmKilobytes, 128)
        var lowered = VideoTurbo.applied(to: base)
        lowered.videoKeepWarmKilobytes = 256
        XCTAssertFalse(VideoTurbo.holds(in: lowered))
        XCTAssertEqual(VideoTurbo.restored(lowered).videoKeepWarmKilobytes, 256)
    }

    func testApplyingTwiceKeepsTheFirstSnapshot() {
        let base = contrary()
        var on = VideoTurbo.applied(to: base)
        on.videoTurbo = false // as if the switch alone had been flipped without a restore
        let again = VideoTurbo.applied(to: on)
        XCTAssertEqual(again.videoTurboRestore, VideoTurbo.snapshot(of: base))
        XCTAssertEqual(VideoTurbo.restored(again), base)
    }

    func testRestoringWithoutASnapshotOnlyClearsTheSwitch() {
        var on = VideoTurbo.applied(to: AppSettings())
        on.videoTurboRestore = nil
        let off = VideoTurbo.restored(on)
        XCTAssertFalse(off.videoTurbo)
        XCTAssertTrue(off.videoGuardPinning, "nothing to put back, so nothing moves")
    }

    func testTurboSurvivesEncoding() throws {
        let on = VideoTurbo.applied(to: contrary())
        let data = try JSONEncoder().encode(on)
        let decoded = try JSONDecoder().decode(AppSettings.self, from: data)
        XCTAssertTrue(decoded.videoTurbo)
        XCTAssertEqual(decoded.videoTurboRestore, on.videoTurboRestore)
        XCTAssertEqual(VideoTurbo.restored(decoded).youtubeMode, .directAntiThrottle)
        // Settings written before the switch existed decode with it off.
        let old = try JSONDecoder().decode(AppSettings.self, from: Data("{}".utf8))
        XCTAssertFalse(old.videoTurbo)
        XCTAssertNil(old.videoTurboRestore)
    }

    func testTurboLengthensTheCircuitLifetime() {
        var settings = AppSettings()
        XCTAssertTrue(TorConfiguration.performanceLines(for: settings).contains("MaxCircuitDirtiness 600"))
        XCTAssertTrue(TorConfiguration.optionalAssignments(settings: settings).contains { $0.key == "MaxCircuitDirtiness" && $0.value == "600" })
        settings = VideoTurbo.applied(to: settings)
        let lines = TorConfiguration.performanceLines(for: settings)
        XCTAssertTrue(lines.contains("MaxCircuitDirtiness 1800"))
        XCTAssertFalse(lines.contains("MaxCircuitDirtiness 600"))
        XCTAssertTrue(TorConfiguration.optionalAssignments(settings: settings).contains { $0.key == "MaxCircuitDirtiness" && $0.value == "1800" })
        XCTAssertLessThan(VideoTurbo.laneLifetime, TimeInterval(VideoTurbo.circuitLifetimeSeconds),
                          "lanes retire before tor stops attaching streams to their circuits")
        settings.lanePoolEnabled = false
        XCTAssertTrue(TorConfiguration.performanceLines(for: settings).contains("MaxCircuitDirtiness 1800"),
                      "the lifetime is Turbo's whether or not the pool runs")
    }

    func testAPresetIsToleratedOnlyWhenItSetsNothingTheOtherWay() {
        let base = AppSettings()
        XCTAssertFalse(VideoTurbo.tolerates(.balanced, over: base), "per-site isolation contradicts the lane race")
        XCTAssertFalse(VideoTurbo.tolerates(.privacyFirst, over: base), "padding on contradicts Turbo")
        XCTAssertFalse(VideoTurbo.tolerates(.speedFirst, over: base), "YouTube outside Tor contradicts Turbo")
        // A preset that leaves Turbo's remit alone lands on top of it.
        var tolerant = VideoTurbo.applied(to: base)
        tolerant.killSwitch = false
        XCTAssertTrue(VideoTurbo.holds(in: tolerant))
    }
}
