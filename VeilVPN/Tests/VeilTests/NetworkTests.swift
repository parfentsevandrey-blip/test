import XCTest
@testable import Veil

final class NetworkTests: XCTestCase {
    func testConnectivityReportClassification() {
        let dead = ConnectivityProbe.Report(reachedByAddress: 0, addressTargets: 6, nameResolution: false, elapsed: 4, date: .now)
        XCTAssertFalse(dead.internetReachable)
        XCTAssertFalse(dead.isUsable)

        let staleDNS = ConnectivityProbe.Report(reachedByAddress: 3, addressTargets: 6, nameResolution: false, elapsed: 0.2, date: .now)
        XCTAssertTrue(staleDNS.internetReachable)
        XCTAssertFalse(staleDNS.isUsable)

        let healthy = ConnectivityProbe.Report(reachedByAddress: 2, addressTargets: 6, nameResolution: true, elapsed: 0.12, date: .now)
        XCTAssertTrue(healthy.isUsable)
        XCTAssertTrue(healthy.summary.contains("2/6"))
        XCTAssertTrue(healthy.summary.contains("DNS ok"))
    }

    func testTunnelInterfacesAreNeverReset() {
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("utun3"))
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("ipsec0"))
        XCTAssertTrue(NetworkReset.Primary.isTunnelInterface("ppp0"))
        XCTAssertFalse(NetworkReset.Primary.isTunnelInterface("en0"))
        XCTAssertFalse(NetworkReset.Primary.isTunnelInterface("en5"))

        let wifi = NetworkReset.Primary(interface: "en0", serviceID: "ABC", serviceName: "Wi-Fi", isWiFi: true)
        XCTAssertFalse(wifi.isTunnel)
        XCTAssertEqual(wifi.displayName, "Wi-Fi (en0)")
        let bare = NetworkReset.Primary(interface: "en5", serviceID: nil, serviceName: nil, isWiFi: false)
        XCTAssertEqual(bare.displayName, "en5")
    }

    func testProbeTargetsAreDistinctAndWellFormed() {
        let hosts = ConnectivityProbe.addressTargets.map(\.host)
        XCTAssertEqual(Set(hosts).count, hosts.count)
        for target in ConnectivityProbe.addressTargets {
            XCTAssertEqual(target.host.split(separator: ".").count, 4, "address targets must be IPv4 literals so DNS plays no part")
        }
        XCTAssertFalse(ConnectivityProbe.nameTargets.isEmpty)
    }

    func testTelegramProxyLink() {
        XCTAssertEqual(TelegramIntegration.proxyURL(socksPort: 9050).absoluteString, "tg://socks?server=127.0.0.1&port=9050")
        XCTAssertEqual(TelegramIntegration.proxyURL(socksPort: 9150).query, "server=127.0.0.1&port=9150")
    }

    func testFlowCometMathsStaysBounded() {
        XCTAssertEqual(TunnelFlowView.cometCount(for: 0), 1)
        XCTAssertEqual(TunnelFlowView.cometCount(for: 100_000), 2)
        XCTAssertEqual(TunnelFlowView.cometCount(for: 5_000_000), 3)
        XCTAssertGreaterThan(TunnelFlowView.cometSpeed(for: 1_000_000), TunnelFlowView.cometSpeed(for: 1_000))
        XCTAssertLessThanOrEqual(TunnelFlowView.cometSpeed(for: 1e9), 0.12, "the flow must stay unhurried even at full speed")
        XCTAssertLessThanOrEqual(TunnelFlowView.cometLength(for: 1e9), 0.2)
        XCTAssertLessThanOrEqual(TunnelFlowView.cometBrightness(for: 1e9), 1.0 + 1e-9)
        XCTAssertGreaterThanOrEqual(TunnelFlowView.cometBrightness(for: 0), 0.5)
        for time in stride(from: 0.0, through: 100.0, by: 7.3) {
            let progress = TunnelFlowView.progress(time: time, speed: 0.07, lane: 1, segment: 3, seed: 1)
            XCTAssertGreaterThanOrEqual(progress, 0)
            XCTAssertLessThan(progress, 1)
        }
    }
}
