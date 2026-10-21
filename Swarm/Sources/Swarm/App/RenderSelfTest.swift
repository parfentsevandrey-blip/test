import AppKit
import SwiftUI

/// `--selftest-render <папка>` — рисует визуализации в PNG, чтобы их можно было посмотреть глазами из CI.
enum RenderSelfTest {
    static func run(_ dir: URL) -> Bool {
        _ = NSApplication.shared
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var ok = true
        let now = Date().timeIntervalSinceReferenceDate
        let snapshot = sample(progress: 0.37, now: Date())
        let tint = Color.blue

        // Кадры одной сцены: вылет пиров, установившийся рой и волна от проверенного куска.
        let memory = ConstellationMemory()
        for (name, offset, snap) in [("constellation-spawn", 0.3, snapshot), ("constellation", 3.0, snapshot),
                                     ("constellation-ripple-start", 3.3, withNewPieces(snapshot)),
                                     ("constellation-ripple", 3.75, withNewPieces(snapshot))] {
            let scene = memory.scene(for: snap, size: CGSize(width: 376, height: 280), now: now + offset, settled: false)
            let view = Canvas { g, size in
                ConstellationRenderer(scene: scene, tint: tint, focused: scene.nodes.last?.id).draw(&g, size: size)
            }
            .frame(width: 376, height: 280)
            ok = save(view, dir.appendingPathComponent("\(name).png")) && ok
        }

        var searching = sample(progress: 0, now: Date())
        searching.peers = []
        searching.status = .metadata
        let empty = ConstellationMemory().scene(for: searching, size: CGSize(width: 376, height: 280), now: now + 0.6, settled: false)
        ok = save(Canvas { g, size in ConstellationRenderer(scene: empty, tint: tint, focused: nil).draw(&g, size: size) }
            .frame(width: 376, height: 280), dir.appendingPathComponent("constellation-searching.png")) && ok

        for (name, rate, progress, active) in [("dock-fast", 10_100_000.0, 0.2, 1), ("dock-slow", 840_000.0, 0.73, 3)] {
            let tile = DockTileView(icon: NSApplication.shared.applicationIconImage, speed: DockSpeed(rate),
                                    progress: progress, active: active)
            ok = save(tile, dir.appendingPathComponent("\(name).png")) && ok
        }
        return SelfTest.check(ok, "визуализации отрисованы в \(dir.path)")
    }

    private static func save(_ view: some View, _ url: URL) -> Bool {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        guard let image = renderer.nsImage, let tiff = image.tiffRepresentation,
              let png = NSBitmapImageRep(data: tiff)?.representation(using: .png, properties: [:]) else { return false }
        return (try? png.write(to: url)) != nil
    }

    private static func withNewPieces(_ s: TorrentSnapshot) -> TorrentSnapshot {
        var s = s
        s.recentPieces[999] = Date()
        return s
    }

    private static func sample(progress: Double, now: Date) -> TorrentSnapshot {
        var generator = SystemRandomNumberGenerator()
        let peers = (0..<26).map { i -> PeerSnapshot in
            let seed = i % 3 != 0
            let busy = i % 4 != 3
            return PeerSnapshot(address: "10.0.\(i).\(i * 7 % 255):\(6881 + i)", client: seed ? "qBittorrent 5.1" : "Transmission 4.0",
                                progress: seed ? 1 : Double.random(in: 0...0.9, using: &generator),
                                downRate: busy ? Double.random(in: 20_000...2_000_000, using: &generator) : 0,
                                upRate: i % 5 == 0 ? Double.random(in: 10_000...300_000, using: &generator) : 0,
                                isSeed: seed, source: .tracker, isIncoming: i % 6 == 0, isChokingUs: false,
                                isInterestedInUs: true, angle: Double(i) * 2.399963)
        }
        return TorrentSnapshot(
            id: "sample", name: "Sample", status: .downloading, error: nil, progress: progress, checkProgress: nil,
            metadataProgress: nil, totalSize: 1 << 30, wantedSize: 1 << 30, doneSize: Int64(Double(1 << 30) * progress),
            downRate: peers.reduce(0) { $0 + $1.downRate }, upRate: peers.reduce(0) { $0 + $1.upRate },
            downloadedTotal: 0, uploadedTotal: 0, peersConnected: peers.count, seedsConnected: 17, peersKnown: 144,
            pieceCount: 1000, pieceLength: 1 << 20, havePieces: Data(count: 125), inProgressPieces: [],
            recentPieces: [1: now, 2: now], files: [], peers: peers, trackers: [],
            savePath: URL(fileURLWithPath: "/tmp"), contentURL: nil, isSequential: false, addedAt: now,
            completedAt: nil, magnet: "", isPrivate: false)
    }
}
