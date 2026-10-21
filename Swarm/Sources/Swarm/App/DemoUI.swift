import AppKit
import Foundation

/// Демо-режим интерфейса (`--demo-ui [ширина]`): CI запускает приложение и снимает скриншот окна.
enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    /// Ширина окна, если передана после флага.
    static var width: CGFloat? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count, let w = Double(args[i + 1]) else { return nil }
        return CGFloat(w)
    }

    static func torrents() -> [TorrentSnapshot] {
        let now = Date()
        var a = RenderSelfTest.sample(progress: 0.37, now: now)
        a.id = "demo-a"
        a.name = "The.Grand.Tour.S05.Jetvis.Studio.1080p"
        a.totalSize = 27_600_000_000
        a.wantedSize = a.totalSize
        a.doneSize = Int64(Double(a.totalSize) * 0.37)
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
        return [a, b, c]
    }
}
