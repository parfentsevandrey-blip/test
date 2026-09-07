import XCTest
@testable import Veil

final class PaddingMachineTests: XCTestCase {
    func testLightLevelEmitsBackgroundNoise() {
        var machine = PaddingMachine(level: .light, now: 0)
        var frames: [PaddingFrame] = []
        var now = 0.0
        for _ in 0..<240 { // two minutes of half-second ticks
            now += 0.5
            frames += machine.tick(now: now, elapsed: 0.5, realUpstream: 0, realDownstream: 0, paddingUpstream: 0, paddingDownstream: 0)
        }
        XCTAssertGreaterThan(frames.count, 5)
        XCTAssertTrue(frames.allSatisfy { $0.upstreamBytes <= PaddingFrame.maximumBytes && $0.downstreamBytes <= PaddingFrame.maximumBytes })
    }

    func testBalancedLevelBurstsOnActivity() {
        var machine = PaddingMachine(level: .balanced, now: 0)
        var burstFrames = 0
        var now = 0.0
        // Real activity starts: expect a front-loaded volley over the following seconds.
        for tick in 0..<40 {
            now += 0.5
            let real = tick == 0 ? 50_000 : 0
            burstFrames += machine.tick(now: now, elapsed: 0.5, realUpstream: real, realDownstream: real, paddingUpstream: 0, paddingDownstream: 0).count
        }
        XCTAssertGreaterThanOrEqual(burstFrames, 8)
    }

    func testStrongLevelFillsToConstantRate() {
        var machine = PaddingMachine(level: .strong, now: 0)
        _ = machine.tick(now: 0.5, elapsed: 0.5, realUpstream: 0, realDownstream: 0, paddingUpstream: 0, paddingDownstream: 0)
        let frames = machine.tick(now: 1.0, elapsed: 0.5, realUpstream: 0, realDownstream: 0, paddingUpstream: 0, paddingDownstream: 0)
        let up = frames.reduce(0) { $0 + $1.upstreamBytes }
        let down = frames.reduce(0) { $0 + $1.downstreamBytes }
        XCTAssertGreaterThan(up, 2_000)
        XCTAssertGreaterThan(down, 5_000)
    }

    func testHeaderRoundTrip() {
        let frame = PaddingFrame(upstreamBytes: 1234, downstreamBytes: 65_000, replyDelayMilliseconds: 250)
        let header = PaddingServer.header(for: frame)
        XCTAssertEqual(header.count, PaddingFrame.headerSize)
        XCTAssertEqual(Int(PaddingServer.readUInt32(header, at: 0)), 1234)
        XCTAssertEqual(Int(PaddingServer.readUInt32(header, at: 4)), 65_000)
        XCTAssertEqual(Int(PaddingServer.readUInt32(header, at: 8)), 250)
        XCTAssertEqual(PaddingServer.noiseBytes(70_000).count, 70_000)
    }
}
