import XCTest
@testable import Veil

final class ParsingTests: XCTestCase {
    func testTorLogLine() {
        let entry = LogEntry.parseTorLine("Sep 06 20:00:00.000 [warn] Proxy Client: unable to connect")
        XCTAssertEqual(entry.level, .warn)
        XCTAssertEqual(entry.message, "Proxy Client: unable to connect")
        let progress = LogEntry.parseBootstrap("Sep 06 20:00:00.000 [notice] Bootstrapped 45% (requesting_descriptors): Asking for relay descriptors")
        XCTAssertEqual(progress?.percent, 45)
        XCTAssertEqual(progress?.tag, "requesting_descriptors")
        XCTAssertEqual(progress?.summary, "Asking for relay descriptors")
    }

    @MainActor
    func testBootstrapPhaseAndCircuitStatus() {
        let phase = TorProcessEngine.parseBootstrapPhase("NOTICE BOOTSTRAP PROGRESS=100 TAG=done SUMMARY=\"Done\"")
        XCTAssertEqual(phase?.percent, 100)
        XCTAssertEqual(phase?.tag, "done")
        XCTAssertEqual(phase?.summary, "Done")

        let status = """
        3 BUILT $AAAA~bridge,$BBBB~middle,$CCCC~exit BUILD_FLAGS=NEED_CAPACITY PURPOSE=GENERAL
        5 EXTENDED $DDDD~x PURPOSE=GENERAL
        7 BUILT $EEEE~a,$FFFF=b PURPOSE=HS_CLIENT_REND
        """
        let path = TorProcessEngine.bestCircuitPath(in: status)
        XCTAssertEqual(path?.map(\.fingerprint), ["AAAA", "BBBB", "CCCC"])
        XCTAssertEqual(path?.map(\.nickname), ["bridge", "middle", "exit"])
        XCTAssertEqual(TorProcessEngine.address(fromRouterStatus: "r exit AAAA BBBB 2026-09-06 12:00:00 185.220.101.5 443 0\ns Exit Fast"), "185.220.101.5")
    }

    @MainActor
    func testCircuitEventsAndReplies() {
        let launched = TorProcessEngine.parseCircuitEvent("CIRC 12 LAUNCHED BUILD_FLAGS=NEED_CAPACITY PURPOSE=GENERAL TIME_CREATED=2026-09-08T10:00:00.250000")
        XCTAssertEqual(launched?.id, "12")
        XCTAssertEqual(launched?.status, .launched)
        XCTAssertNotNil(launched?.created)
        let built = TorProcessEngine.parseCircuitEvent("CIRC 12 BUILT $AAAA~bridge,$BBBB=mid,$CCCC~exit BUILD_FLAGS=NEED_CAPACITY PURPOSE=GENERAL TIME_CREATED=2026-09-08T10:00:00.250000")
        XCTAssertEqual(built?.path.map(\.fingerprint), ["AAAA", "BBBB", "CCCC"])
        XCTAssertEqual(built?.exit?.nickname, "exit")
        XCTAssertEqual(built?.middle?.nickname, "mid")
        XCTAssertEqual(built?.purpose, "GENERAL")
        XCTAssertNil(TorProcessEngine.parseCircuitEvent("STREAM 1 NEW 0 example.com:443"))
        XCTAssertEqual(TorProcessEngine.parseExtendedReply(["EXTENDED 34"]), "34")
        XCTAssertNil(TorProcessEngine.parseExtendedReply(["OK"]))
        XCTAssertEqual(TorProcessEngine.parseBandwidth("r exit AAAA BBBB 2026-09-06 12:00:00 185.220.101.5 443 0\ns Exit Fast\nw Bandwidth=23400"), 23400)
        XCTAssertNil(TorProcessEngine.parseBandwidth("r exit AAAA BBBB 2026-09-06 12:00:00 185.220.101.5 443 0"))
    }

    func testSemanticVersions() {
        XCTAssertTrue(UpdateChecker.isNewer("0.4.0", than: "0.3.1"))
        XCTAssertTrue(UpdateChecker.isNewer("1.0", than: "0.9.9"))
        XCTAssertFalse(UpdateChecker.isNewer("0.3.1", than: "0.3.1"))
        XCTAssertFalse(UpdateChecker.isNewer("0.3.0", than: "0.3.1"))
    }

    func testChunkedResponseDecoding() throws {
        let raw = Data("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n5\r\n{\"a\":\r\n2\r\n1}\r\n0\r\n\r\n".utf8)
        let response = try FrontedHTTPClient.parse(raw)
        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(String(decoding: response.body, as: UTF8.self), "{\"a\":1}")
    }

    func testMoatSettingsParsing() throws {
        let json = """
        {"settings":[{"bridges":{"type":"obfs4","source":"bridgedb","bridge_strings":["obfs4 1.2.3.4:443 ABC cert=x iat-mode=0"]}}],"country":"ru"}
        """
        let sets = try MoatClient.parseSettings(Data(json.utf8))
        XCTAssertEqual(sets.count, 1)
        XCTAssertEqual(sets[0].transport, "obfs4")
        XCTAssertEqual(sets[0].bridges.count, 1)
    }

    func testLegacySettingsDecodeWithDefaults() throws {
        let legacy = Data("{\"transport\":\"snowflake\",\"socksPort\":9150}".utf8)
        let settings = try JSONDecoder().decode(AppSettings.self, from: legacy)
        XCTAssertEqual(settings.transport, .snowflake)
        XCTAssertEqual(settings.socksPort, 9150)
        XCTAssertTrue(settings.killSwitch)
        XCTAssertEqual(settings.youtubeMode, .tor)
        XCTAssertTrue(settings.serviceRoutes.isEmpty)
    }

    func testBridgeLinesAndTransports() {
        let lines = TorConfiguration.parseBridgeLines("Bridge obfs4 1.2.3.4:443 ABC\n# comment\n\nwebtunnel [2001:db8::1]:443 DEF url=https://x\n")
        XCTAssertEqual(lines.count, 2)
        XCTAssertEqual(TorConfiguration.transportName(of: lines[0]), "obfs4")
        XCTAssertEqual(TorConfiguration.transportName(of: lines[1]), "webtunnel")
        XCTAssertNil(TorConfiguration.transportName(of: "1.2.3.4:443 ABC"))
        XCTAssertTrue(TorConfiguration.isValidCountryCode("de"))
        XCTAssertFalse(TorConfiguration.isValidCountryCode("d3"))
    }
}
