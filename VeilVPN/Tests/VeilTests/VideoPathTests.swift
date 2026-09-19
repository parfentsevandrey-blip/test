import XCTest
@testable import Veil

final class VideoPathTests: XCTestCase {
    private func relay(_ nickname: String, flags: Set<String>, bandwidth: Int) -> ExitRelay {
        ExitRelay(fingerprint: String(repeating: nickname.first.map(String.init) ?? "A", count: 40),
                  nickname: nickname, address: "192.0.2.1", bandwidth: bandwidth, flags: flags, allows443: true)
    }

    func testResponseHeadIsParsedOnceComplete() throws {
        XCTAssertNil(ThroughputProbe.parseHead(Data("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n".utf8)),
                     "half a header is not a header")
        let full = Data("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello".utf8)
        let head = try XCTUnwrap(ThroughputProbe.parseHead(full))
        XCTAssertEqual(head.status, 200)
        XCTAssertEqual(String(decoding: full[head.bodyStart...], as: UTF8.self), "hello")
        XCTAssertEqual(ThroughputProbe.parseHead(Data("HTTP/1.1 404 Not Found\r\n\r\n".utf8))?.status, 404)
        XCTAssertNil(ThroughputProbe.parseHead(Data("hello\r\n\r\n".utf8)), "not HTTP at all")
    }

    func testARateSaysWhichQualityItSustains() {
        XCTAssertEqual(ThroughputProbe.Sample(bytes: 1_000_000, seconds: 2).megabitsPerSecond, 4, accuracy: 0.001)
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 60), "8K")
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 30), "4K")
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 12), "1440p")
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 6), "1080p")
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 3), "720p")
        XCTAssertEqual(ThroughputProbe.quality(forMegabits: 1), "480p")
    }

    func testProtocolLinesSayWhetherARelayIsModern() throws {
        let text = """
        r Old AAAAAAAAAAAAAAAAAAAAAAAAAAA BBBBBBBBBBBBBBBBBBBBBBBBBBB 2026-09-01 00:00:00 192.0.2.1 9001 0
        s Exit Fast Running Stable Valid
        v Tor 0.4.5.7
        pr Cons=1-2 Desc=1-2 DirCache=2 FlowCtrl=1 HSDir=2 Link=1-5 Microdesc=1-2 Relay=1-3
        w Bandwidth=90000
        p accept 80,443
        r New AQEBAQEBAQEBAQEBAQEBAQEBAQE BBBBBBBBBBBBBBBBBBBBBBBBBBB 2026-09-01 00:00:00 192.0.2.2 9001 0
        s Exit Fast Running Stable Valid
        v Tor 0.4.8.10
        pr Conflux=1 Cons=1-2 Desc=1-2 DirCache=2 FlowCtrl=1-2 HSDir=2 Link=1-5 Microdesc=1-2 Relay=1-4
        w Bandwidth=50000
        p accept 80,443
        r Mute AgICAgICAgICAgICAgICAgICAgI BBBBBBBBBBBBBBBBBBBBBBBBBBB 2026-09-01 00:00:00 192.0.2.3 9001 0
        s Exit Fast Running Stable Valid
        w Bandwidth=40000
        p accept 443
        """
        let relays = ExitCatalog.parse(text)
        XCTAssertEqual(relays.map(\.nickname), ["Old", "New", "Mute"])
        let old = try XCTUnwrap(relays.first { $0.nickname == "Old" })
        XCTAssertFalse(old.supportsConflux)
        XCTAssertFalse(old.supportsCongestionControl, "FlowCtrl=1 is the old window, not congestion control")
        XCTAssertFalse(old.isModern)
        XCTAssertTrue(old.allows80)
        let new = try XCTUnwrap(relays.first { $0.nickname == "New" })
        XCTAssertTrue(new.isModern)
        let mute = try XCTUnwrap(relays.first { $0.nickname == "Mute" })
        XCTAssertTrue(mute.isModern, "no pr line is taken as capable, like no policy summary")
        XCTAssertFalse(mute.allows80, "the measurement stream is plain HTTP")
        XCTAssertEqual(ExitCatalog.rank(relays).map(\.nickname), ["Old", "New", "Mute"])
        XCTAssertEqual(ExitCatalog.rank(relays, modernOnly: true).map(\.nickname), ["New"],
                       "a video exit needs two legs, congestion control and port 80")
        XCTAssertTrue(ExitCatalog.protocolIncludes(["FlowCtrl=1-2"], name: "FlowCtrl", version: 2))
        XCTAssertFalse(ExitCatalog.protocolIncludes(["FlowCtrl=1"], name: "FlowCtrl", version: 2))
        XCTAssertFalse(ExitCatalog.protocolIncludes(["Relay=1-4"], name: "Conflux", version: 1))
    }

    func testTheRememberedPathIsFreshForAWeekOnTheSameTransport() throws {
        var memory = VideoPathMemory(exitFingerprint: String(repeating: "A", count: 40), exitNickname: "wide",
                                     exitCountry: "de", exitBandwidth: 90_000, guardFingerprint: nil, guardNickname: nil,
                                     megabits: 30, measuredAt: .now, transport: .direct)
        XCTAssertTrue(memory.isFresh(transport: .direct))
        XCTAssertFalse(memory.isFresh(transport: .snowflake), "a different first hop says nothing about the numbers")
        memory.measuredAt = Date.now.addingTimeInterval(-8 * 24 * 3600)
        XCTAssertFalse(memory.isFresh(transport: .direct))
        var settings = AppSettings()
        settings.videoPathMemory = memory
        settings.videoExitsRefused = [String(repeating: "B", count: 40): .now]
        let decoded = try JSONDecoder().decode(AppSettings.self, from: JSONEncoder().encode(settings))
        XCTAssertEqual(decoded.videoPathMemory?.exitFingerprint, memory.exitFingerprint)
        XCTAssertEqual(decoded.videoPathMemory?.megabits, 30)
        XCTAssertEqual(decoded.videoExitsRefused.count, 1)
        let old = try JSONDecoder().decode(AppSettings.self, from: Data("{}".utf8))
        XCTAssertNil(old.videoPathMemory)
        XCTAssertTrue(old.videoExitsRefused.isEmpty)
    }

    func testGuardsAreRankedByCapacityAmongStableGuards() {
        let a = relay("A", flags: ["Guard", "Fast", "Stable", "Running", "Valid"], bandwidth: 50_000)
        let b = relay("B", flags: ["Guard", "Fast", "Stable", "Running", "Valid"], bandwidth: 90_000)
        let exitOnly = relay("C", flags: ["Exit", "Fast", "Stable", "Running", "Valid"], bandwidth: 100_000)
        let unstable = relay("D", flags: ["Guard", "Fast", "Running", "Valid"], bandwidth: 200_000)
        XCTAssertEqual(ExitCatalog.rankGuards([a, b, exitOnly, unstable]).map(\.nickname), ["B", "A"])
        XCTAssertEqual(ExitCatalog.rankGuards([a, b], excluding: [b.fingerprint]).map(\.nickname), ["A"])
        XCTAssertTrue(ExitCatalog.rank([a, b, exitOnly, unstable]).map(\.nickname) == ["C"],
                      "an exit list is an exit list: guards without the Exit flag stay out of it")
    }

    func testTheTonusStreamIsReadInSlicesThatLandOnTheRate() {
        // 512 KB/s in ten slices a second is 51 KB a slice; the window then holds the sender there.
        XCTAssertEqual(VideoWarmer.slice(forKilobytes: 512), 512 * 1024 / 10)
        XCTAssertEqual(VideoWarmer.slice(forKilobytes: 128), 128 * 1024 / 10)
        XCTAssertEqual(VideoWarmer.slice(forKilobytes: 1), 4096, "never a slice so small the loop spins")
        XCTAssertEqual(VideoWarmer.contentLength(in: "HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\nServer: x\r\n"), 1_048_576)
        XCTAssertEqual(VideoWarmer.contentLength(in: "HTTP/1.1 200 OK\r\ncontent-length:  42 \r\n"), 42)
        XCTAssertNil(VideoWarmer.contentLength(in: "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n"))
        XCTAssertEqual(AppSettings().videoKeepWarmKilobytes, 512)
        XCTAssertTrue(AppSettings().videoKeepWarmAlways)
        XCTAssertTrue(VideoWarmer.rates.contains(512))
        XCTAssertFalse(VideoWarmer.sources.isEmpty)
    }

    func testMeasurementHostsAreMappedNextToTheVideoHosts() {
        let pairs = ExitCatalog.mapAddressPairs(exit: "F" + String(repeating: "0", count: 39),
                                                domains: ThroughputProbe.measurementHosts)
        XCTAssertEqual(pairs.count, ThroughputProbe.measurementHosts.count * 2)
        XCTAssertTrue(pairs.allSatisfy { $0.key == "MapAddress" && ($0.value ?? "").hasSuffix(".exit") })
    }
}
