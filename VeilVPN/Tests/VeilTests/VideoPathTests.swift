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

    func testMeasurementHostsAreMappedNextToTheVideoHosts() {
        let pairs = ExitCatalog.mapAddressPairs(exit: "F" + String(repeating: "0", count: 39),
                                                domains: ThroughputProbe.measurementHosts)
        XCTAssertEqual(pairs.count, ThroughputProbe.measurementHosts.count * 2)
        XCTAssertTrue(pairs.allSatisfy { $0.key == "MapAddress" && ($0.value ?? "").hasSuffix(".exit") })
    }
}
