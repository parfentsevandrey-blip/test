import AppKit
import Foundation
import SwiftUI

/// Демо-режим интерфейса (`--demo-ui [ширина]`): CI запускает приложение и снимает скриншот окна.

/// Открывает окно настроек в демо-режиме.
struct DemoSettingsOpener: View {
    @Environment(\.openSettings) private var openSettings

    var body: some View {
        Color.clear.onAppear {
            guard DemoUI.settingsTab != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { openSettings() }
        }
    }
}

enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    /// Ширина окна, если передана после флага.
    static var width: CGFloat? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count, let w = Double(args[i + 1]) else { return nil }
        return CGFloat(w)
    }

    /// `--demo-settings N`: открыть настройки на вкладке N (для скриншотов).
    static var settingsTab: Int? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-settings"), i + 1 < args.count else { return nil }
        return Int(args[i + 1])
    }

    static func network() -> NetworkStatus {
        var n = NetworkStatus()
        n.port = 51413
        n.udpOpen = true
        n.mapping = PortMappingStatus(method: "NAT-PMP", externalIP: "203.0.113.24", externalPort: 51413, gateway: "192.168.1.1", message: "Порт открыт")
        n.dhtNodes = 312
        n.dhtQueries = 4821
        n.dhtStoredPeers = 57
        n.utpConnections = 9
        n.lsdActive = true
        n.blocklistRanges = 241_337
        n.blocklistBlocked = 18
        return n
    }

    static func torrents() -> [TorrentSnapshot] {
        let now = Date()
        var a = RenderSelfTest.sample(progress: 0.37, now: now)
        a.id = "demo-a"
        a.name = "The.Grand.Tour.S05.Jetvis.Studio.1080p"
        a.totalSize = 27_600_000_000
        a.wantedSize = a.totalSize
        a.doneSize = Int64(Double(a.totalSize) * 0.37)
        a.version = .hybrid
        a.connections = ConnectionStats(tcp: 31, utp: 17, encrypted: 39, fast: 44, incoming: 12, clients: [])
        for i in a.peers.indices {
            a.peers[i].transport = i % 3 == 0 ? .utp : .tcp
            a.peers[i].isEncrypted = i % 4 != 1
            a.peers[i].supportsFast = true
        }
        var b = RenderSelfTest.sample(progress: 0.004, now: now)
        b.id = "demo-b"
        b.name = "debian-13.7.0-amd64-netinst.iso"
        b.status = .paused
        b.downRate = 0
        b.upRate = 0
        b.peers = []
        var c = RenderSelfTest.sample(progress: 1, now: now)
        c.id = "demo-c"
        c.name = "Swarm — исходники и сборка.zip"
        c.status = .seeding
        c.downRate = 0
        c.version = .v2
        c.webSeeds = [TrackerSnapshot(url: "https://mirror.example.org/swarm/", status: "Работает, кусков: 214", seeders: nil, leechers: nil, lastPeers: 0, nextAnnounce: nil)]
        return [a, b, c]
    }
}
