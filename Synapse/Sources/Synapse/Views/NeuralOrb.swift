import simd
import SwiftUI

/// «Нейросфера»: стеклянная сфера, внутри — вращающаяся сеть нейронов. По связям бегут импульсы;
/// чем выше `energy`, тем быстрее вращение и тем больше сигналов (модель думает или пишет).
struct NeuralOrb: View {
    var energy: Double = 0.3
    var palette: [Color] = Section.chat.palette
    /// Доля «загоревшихся» нейронов — например, прогресс загрузки или анализа.
    var progress: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            Canvas { ctx, size in
                draw(&ctx, size: size, t: t)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private static let nodes: [SIMD3<Double>] = {
        // Равномерно по сфере (спираль Фибоначчи) + немного внутри — объём, а не оболочка.
        var p: [SIMD3<Double>] = []
        let n = 84
        let golden = Double.pi * (3 - sqrt(5))
        for i in 0..<n {
            let y = 1 - (Double(i) + 0.5) / Double(n) * 2
            let r = sqrt(1 - y * y)
            let a = golden * Double(i)
            let depth = i % 5 == 0 ? 0.55 : 0.92
            p.append(SIMD3(cos(a) * r, y, sin(a) * r) * depth)
        }
        return p
    }()

    private static let edges: [(Int, Int)] = {
        var e: [(Int, Int)] = []
        for i in nodes.indices {
            let near = nodes.indices.filter { $0 != i }
                .sorted { simd_distance(nodes[i], nodes[$0]) < simd_distance(nodes[i], nodes[$1]) }
                .prefix(3)
            for j in near where i < j { e.append((i, j)) }
        }
        return e
    }()

    private func draw(_ ctx: inout GraphicsContext, size: CGSize, t: Double) {
        let side = min(size.width, size.height)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = side * 0.36
        let e = max(0, min(1.5, energy))

        // Свечение за сферой.
        let glow = Path(ellipseIn: CGRect(x: center.x - radius * 1.55, y: center.y - radius * 1.55, width: radius * 3.1, height: radius * 3.1))
        ctx.fill(glow, with: .radialGradient(Gradient(colors: [palette[0].opacity(0.32 + 0.25 * e), palette[1].opacity(0.12), .clear]),
                                             center: center, startRadius: radius * 0.2, endRadius: radius * 1.55))

        // Поворот: медленно в покое, быстро в работе.
        let speed = 0.12 + 0.55 * e
        let ay = t * speed
        let ax = 0.35 + sin(t * 0.21) * 0.18
        func project(_ v: SIMD3<Double>) -> (CGPoint, Double) {
            let x1 = v.x * cos(ay) + v.z * sin(ay)
            let z1 = -v.x * sin(ay) + v.z * cos(ay)
            let y2 = v.y * cos(ax) - z1 * sin(ax)
            let z2 = v.y * sin(ax) + z1 * cos(ax)
            let persp = 1.9 / (1.9 - z2 * 0.45)
            return (CGPoint(x: center.x + x1 * radius * persp, y: center.y + y2 * radius * persp), z2)
        }
        let projected = Self.nodes.map(project)
        let lit = progress.map { Int(Double(Self.nodes.count) * $0) }

        // Связи — дальние бледнее.
        for (a, b) in Self.edges {
            let (pa, za) = projected[a]
            let (pb, zb) = projected[b]
            let depth = (za + zb) / 2
            var line = Path()
            line.move(to: pa)
            line.addLine(to: pb)
            let alpha = 0.08 + 0.22 * (depth + 1) / 2 + 0.1 * e
            ctx.stroke(line, with: .linearGradient(Gradient(colors: [palette[0].opacity(alpha), palette[2].opacity(alpha)]),
                                                   startPoint: pa, endPoint: pb), lineWidth: 0.7 + 0.5 * (depth + 1) / 2)
        }

        // Импульсы бегут по связям.
        let pulses = Int(6 + 34 * e)
        for k in 0..<pulses {
            let edge = Self.edges[(k * 37 + Int(t * (0.6 + e)) * 11) % Self.edges.count]
            let phase = (t * (0.7 + 1.4 * e) + Double(k) * 0.173).truncatingRemainder(dividingBy: 1)
            let (pa, za) = projected[edge.0]
            let (pb, _) = projected[edge.1]
            let p = CGPoint(x: pa.x + (pb.x - pa.x) * phase, y: pa.y + (pb.y - pa.y) * phase)
            let s = 2.2 + 1.6 * (za + 1) / 2
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - s * 2.2, y: p.y - s * 2.2, width: s * 4.4, height: s * 4.4)),
                     with: .radialGradient(Gradient(colors: [Color.white.opacity(0.75), palette[1].opacity(0.4), .clear]),
                                           center: p, startRadius: 0, endRadius: s * 2.2))
        }

        // Нейроны: ближние крупнее и ярче; «загоревшиеся» — по прогрессу.
        for (i, (p, z)) in projected.enumerated().sorted(by: { $0.1.1 < $1.1.1 }) {
            let near = (z + 1) / 2
            let beat = 0.5 + 0.5 * sin(t * (1.5 + e * 3) + Double(i) * 1.7)
            let on = lit.map { i < $0 } ?? true
            let s = (1.6 + 2.4 * near) * (on ? 1 : 0.7)
            let color = on ? (i % 3 == 0 ? palette[2] : i % 3 == 1 ? palette[1] : palette[0]) : Color.gray
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - s * 2.6, y: p.y - s * 2.6, width: s * 5.2, height: s * 5.2)),
                     with: .radialGradient(Gradient(colors: [color.opacity((0.25 + 0.35 * beat * e) * near), .clear]), center: p, startRadius: 0, endRadius: s * 2.6))
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - s / 2, y: p.y - s / 2, width: s, height: s)),
                     with: .color(on ? Color.white.opacity(0.55 + 0.45 * near) : Color.gray.opacity(0.4)))
        }

        // Стеклянная оболочка: тонкий обод, блик сверху и отражённый свет снизу.
        let shell = Path(ellipseIn: CGRect(x: center.x - radius * 1.42, y: center.y - radius * 1.42, width: radius * 2.84, height: radius * 2.84))
        ctx.fill(shell, with: .radialGradient(Gradient(colors: [.white.opacity(0.0), .white.opacity(0.04), palette[0].opacity(0.16)]),
                                              center: center, startRadius: radius * 0.6, endRadius: radius * 1.42))
        ctx.stroke(shell, with: .linearGradient(Gradient(colors: [.white.opacity(0.55), .white.opacity(0.05), palette[1].opacity(0.45)]),
                                                startPoint: CGPoint(x: center.x - radius, y: center.y - radius * 1.4),
                                                endPoint: CGPoint(x: center.x + radius, y: center.y + radius * 1.4)), lineWidth: max(1, side * 0.006))
        let highlight = Path(ellipseIn: CGRect(x: center.x - radius * 0.85, y: center.y - radius * 1.32, width: radius * 1.2, height: radius * 0.55))
        ctx.fill(highlight, with: .linearGradient(Gradient(colors: [.white.opacity(0.32), .white.opacity(0)]),
                                                  startPoint: CGPoint(x: center.x, y: center.y - radius * 1.32),
                                                  endPoint: CGPoint(x: center.x, y: center.y - radius * 0.77)))
        let rim = Path(ellipseIn: CGRect(x: center.x - radius * 0.7, y: center.y + radius * 0.95, width: radius * 1.4, height: radius * 0.36))
        ctx.fill(rim, with: .radialGradient(Gradient(colors: [palette[2].opacity(0.35), .clear]),
                                            center: CGPoint(x: center.x, y: center.y + radius * 1.13), startRadius: 0, endRadius: radius * 0.7))
    }
}
