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

    func testFlowParticleMathsStaysBounded() {
        XCTAssertEqual(TunnelFlowView.lanes(for: 0), 1)
        XCTAssertEqual(TunnelFlowView.lanes(for: 4096), 2)
        XCTAssertLessThanOrEqual(TunnelFlowView.lanes(for: 50_000_000), 7)
        XCTAssertGreaterThan(TunnelFlowView.speed(for: 1_000_000), TunnelFlowView.speed(for: 1_000))
        for time in stride(from: 0.0, through: 100.0, by: 7.3) {
            let progress = TunnelFlowView.progress(time: time, speed: 0.7, lane: 3, segment: 2, seed: 1)
            XCTAssertGreaterThanOrEqual(progress, 0)
            XCTAssertLessThan(progress, 1)
        }
    }
}
