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

        func render(_ scene: ConstellationScene, _ size: CGSize, _ name: String, focus: Bool = false) -> Bool {
            let view = Canvas { g, sz in
                ConstellationRenderer(scene: scene, tint: tint, focused: focus ? scene.nodes.last?.id : nil).draw(&g, size: sz)
            }
            .frame(width: size.width, height: size.height)
            return save(view, dir.appendingPathComponent("\(name).png"))
        }

        // Кадры одной сцены в инспекторе: вылет пиров, установившийся рой, волна от проверенного куска, повёрнутая камера.
        let small = CGSize(width: 376, height: 340)
        let memory = ConstellationMemory()
        _ = memory.scene(for: snapshot, size: small, now: now, settled: false)
        ok = render(memory.scene(for: snapshot, size: small, now: now + 0.5, settled: false), small, "constellation-spawn") && ok
        ok = render(memory.scene(for: snapshot, size: small, now: now + 3, settled: false), small, "constellation", focus: true) && ok
        _ = memory.scene(for: withNewPieces(snapshot), size: small, now: now + 3.3, settled: false)
        ok = render(memory.scene(for: withNewPieces(snapshot), size: small, now: now + 3.8, settled: false), small, "constellation-ripple") && ok
        ok = render(memory.scene(for: snapshot, size: small, now: now + 4, settled: false, yaw: 1.4, pitch: 0.4), small,
                    "constellation-rotated") && ok

        // Большое окно роя.
        let large = CGSize(width: 1180, height: 800)
        let big = ConstellationMemory()
        _ = big.scene(for: snapshot, size: large, now: now, settled: false)
        ok = render(big.scene(for: snapshot, size: large, now: now + 3, settled: false), large, "constellation-window") && ok

        // Как на паузе у пользователя: почти 0%, пиров нет.
        var paused = sample(progress: 0.004, now: Date())
        paused.peers = []
        paused.status = .paused
        paused.downRate = 0
        paused.upRate = 0
        ok = render(ConstellationMemory().scene(for: paused, size: small, now: now + 1, settled: false), small,
                    "constellation-paused") && ok

        var searching = sample(progress: 0, now: Date())
        searching.peers = []
        searching.pieceCount = 0
        searching.status = .metadata
        ok = render(ConstellationMemory().scene(for: searching, size: small, now: now + 0.6, settled: false), small,
                    "constellation-searching") && ok

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

    private static func sampleHave(_ progress: Double) -> Data {
        var bf = Bitfield(count: 1000)
        for i in 0..<1000 where frac(sin(Double(i) * 3.77) * 9431.7) < progress { bf[i] = true }
        return bf.data
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
            pieceCount: 1000, pieceLength: 1 << 20, havePieces: sampleHave(progress), inProgressPieces: Array(400..<412),
            recentPieces: [1: now, 2: now], files: [], peers: peers, trackers: [],
            savePath: URL(fileURLWithPath: "/tmp"), contentURL: nil, isSequential: false, addedAt: now,
            completedAt: nil, magnet: "", isPrivate: false)
    }
}
