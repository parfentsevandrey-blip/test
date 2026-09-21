import XCTest
@testable import Veil

final class RoutingPolicyTests: XCTestCase {
    func testYouTubeFollowsItsMode() {
        var policy = RoutingPolicy()
        XCTAssertEqual(policy.decision(for: "rr3---sn-4g5e6nzl.googlevideo.com", torAvailable: true), .tor)
        policy.youtubeMode = .directAntiThrottle
        XCTAssertEqual(policy.decision(for: "www.youtube.com", torAvailable: true), .direct(antiThrottle: true))
        policy.youtubeMode = .direct
        XCTAssertEqual(policy.decision(for: "i.ytimg.com", torAvailable: true), .direct(antiThrottle: false))
    }

    func testTurboModeSendsYouTubeDirectWithAntiThrottling() {
        let policy = RoutingPolicy()
        XCTAssertEqual(policy.decision(for: "www.youtube.com", torAvailable: false), .direct(antiThrottle: true))
        XCTAssertEqual(policy.decision(for: "example.org", torAvailable: false), .direct(antiThrottle: false))
    }

    func testServicesAndCustomDomains() {
        var policy = RoutingPolicy()
        policy.serviceModes["discord"] = .directAntiThrottle
        XCTAssertEqual(policy.decision(for: "gateway.discord.gg", torAvailable: true), .direct(antiThrottle: true))
        XCTAssertEqual(policy.decision(for: "twitch.tv", torAvailable: true), .tor)
        policy.customDirectDomains = RoutingPolicy.parseDomains("rutube.ru\n*.example.net, # comment\nbad")
        XCTAssertEqual(policy.customDirectDomains, ["rutube.ru", "example.net"])
        XCTAssertEqual(policy.decision(for: "static.rutube.ru", torAvailable: true), .direct(antiThrottle: true))
        policy.customDirectAntiThrottle = false
        XCTAssertEqual(policy.decision(for: "cdn.example.net", torAvailable: true), .direct(antiThrottle: false))
        XCTAssertEqual(policy.decision(for: "notrutube.ru", torAvailable: true), .tor)
    }

    func testAppleServicesAreDirectByDefaultAndCanBeOverridden() {
        var settings = AppSettings()
        XCTAssertEqual(settings.routingPolicy.decision(for: "apps.apple.com", torAvailable: true), .direct(antiThrottle: false))
        XCTAssertEqual(settings.routingPolicy.decision(for: "gateway.icloud.com", torAvailable: true), .direct(antiThrottle: false))
        XCTAssertEqual(settings.routingPolicy.decision(for: "web.telegram.org", torAvailable: true), .tor)
        settings.serviceRoutes["apple"] = .tor
        XCTAssertEqual(settings.routingPolicy.decision(for: "apps.apple.com", torAvailable: true), .tor)
        XCTAssertEqual(ServiceCatalog.defaultModes, ["apple": .direct])
    }

    func testSettingsProduceRoute() {
        var settings = AppSettings()
        settings.exitCountry = "DE"
        settings.middleCountry = "nl"
        settings.excludedCountries = ["us", "de"]
        settings.avoidFiveEyes = true
        // Multihop off: the middle country does not apply, but an exclusion is a protective
        // choice in its own right and is no longer silently ignored.
        let withoutMultihop = settings.route.torrcLines
        XCTAssertFalse(withoutMultihop.contains { $0.hasPrefix("MiddleNodes") })
        XCTAssertTrue(withoutMultihop.contains("ExitNodes {de}"))
        XCTAssertTrue(withoutMultihop.contains("ExcludeNodes {us},{gb},{ca},{au},{nz}"))
        settings.multihopEnabled = true
        let lines = settings.route.torrcLines
        XCTAssertTrue(lines.contains("MiddleNodes {nl}"))
        XCTAssertTrue(lines.contains("ExitNodes {de}"))
        // The exit country must never be excluded; Five Eyes are appended once.
        XCTAssertTrue(lines.contains("ExcludeNodes {us},{gb},{ca},{au},{nz}"))
        XCTAssertTrue(lines.contains("StrictNodes 1"))
    }

    func testExclusionsNeverReachTheBridgesThemselves() {
        var settings = AppSettings()
        settings.avoidFiveEyes = true
        let route = settings.route

        // Direct: the exclusion covers every hop, as before.
        let direct = route.configuration(usingBridges: false)
        XCTAssertTrue(direct.set.contains { $0.key == "ExcludeNodes" && $0.value == "{us},{gb},{ca},{au},{nz}" })
        XCTAssertTrue(direct.reset.contains("ExcludeExitNodes"))

        // Behind a bridge: never ExcludeNodes. Tor matches it against the bridges and refuses
        // every one in an excluded country — "Not using bridge at …: it is in ExcludeNodes" —
        // which leaves nothing to bootstrap through.
        let bridged = route.configuration(usingBridges: true)
        XCTAssertFalse(bridged.set.contains { $0.key == "ExcludeNodes" })
        XCTAssertTrue(bridged.set.contains { $0.key == "ExcludeExitNodes" && $0.value == "{us},{gb},{ca},{au},{nz}" })
        XCTAssertTrue(bridged.reset.contains("ExcludeNodes"),
                      "a switch from a direct connection has to clear it, or the bridges stay refused")
        XCTAssertTrue(bridged.set.contains { $0.key == "StrictNodes" && $0.value == "1" })
    }

    func testWithoutExclusionsBothFormsAreCleared() {
        let route = AppSettings().route
        for usingBridges in [false, true] {
            let configuration = route.configuration(usingBridges: usingBridges)
            XCTAssertTrue(configuration.reset.contains("ExcludeNodes"))
            XCTAssertTrue(configuration.reset.contains("ExcludeExitNodes"))
            XCTAssertTrue(configuration.reset.contains("StrictNodes"))
            XCTAssertTrue(configuration.set.isEmpty)
        }
    }

    func testAnExcludedExitCountryStillHoldsBehindABridge() {
        var settings = AppSettings()
        settings.exitCountry = "de"
        settings.excludedCountries = ["us", "de"]
        let bridged = settings.route.configuration(usingBridges: true)
        // The country we asked to exit in is never excluded, whichever key carries the list.
        XCTAssertTrue(bridged.set.contains { $0.key == "ExcludeExitNodes" && $0.value == "{us}" })
        XCTAssertTrue(bridged.set.contains { $0.key == "ExitNodes" && $0.value == "{de}" })
    }
}
