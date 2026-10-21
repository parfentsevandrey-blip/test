import AppKit
import SwiftUI

/// `--selftest-render <папка>`: сфера «Умной проверки» во всех фазах — в PNG, чтобы посмотреть глазами из CI.
enum RenderSelfTest {
    static func run(_ dir: URL) -> Bool {
        _ = NSApplication.shared
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let sizes: [(CategoryID, Int64)] = [
            (.appCaches, 3_400_000_000), (.developer, 6_100_000_000), (.browserCaches, 1_200_000_000),
            (.leftovers, 640_000_000), (.logs, 210_000_000), (.trash, 2_300_000_000),
        ]
        let sample: [JunkCategory] = sizes.map { id, size in
            JunkCategory(id: id, items: [JunkItem(url: URL(fileURLWithPath: "/tmp/\(id)"), targets: [], title: "x", size: size,
                                                  selected: true, risk: .safe, method: .delete)])
        }
        var ok = true
        let phases: [(String, ScanPhase, [JunkCategory], Double)] = [
            ("orb-idle", .idle, [], 0), ("orb-scanning", .scanning(0.45, "Кэши"), Array(sample.prefix(3)), 0),
            ("orb-results", .results, sample, 2), ("orb-cleaning", .cleaning(0.5), sample, 2), ("orb-done", .done(13_850_000_000), [], 0.35),
        ]
        for (name, phase, cats, ago) in phases {
            let view = LiquidOrb(phase: phase, categories: cats, palette: Module.smart.palette, since: Date().addingTimeInterval(-ago))
                .frame(width: 560, height: 420)
                .background(Color(red: 0.05, green: 0.07, blue: 0.12))
            ok = save(view, dir.appendingPathComponent("\(name).png")) && ok
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
}
