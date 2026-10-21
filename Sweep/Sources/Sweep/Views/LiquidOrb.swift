import AppKit
import SwiftUI

extension CategoryID {
    var color: Color {
        switch self {
        case .appCaches: Color(red: 0.2, green: 0.75, blue: 1)
        case .sandboxCaches: Color(red: 0.35, green: 0.55, blue: 1)
        case .logs: Color(red: 0.55, green: 0.65, blue: 0.85)
        case .crashReports: Color(red: 1, green: 0.55, blue: 0.3)
        case .browserCaches: Color(red: 0.3, green: 0.9, blue: 0.65)
        case .developer: Color(red: 0.65, green: 0.45, blue: 1)
        case .devArtifacts: Color(red: 0.8, green: 0.5, blue: 1)
        case .leftovers: Color(red: 1, green: 0.4, blue: 0.65)
        case .installers: Color(red: 1, green: 0.75, blue: 0.3)
        case .deviceUpdates: Color(red: 0.4, green: 0.85, blue: 0.95)
        case .deviceBackups: Color(red: 1, green: 0.35, blue: 0.35)
        case .mailDownloads: Color(red: 0.3, green: 0.6, blue: 0.95)
        case .trash: Color(red: 0.6, green: 0.6, blue: 0.7)
        case .privacy: Color(red: 0.4, green: 0.8, blue: 0.9)
        }
    }
}

/// Жидкая стеклянная сфера «Умной проверки».
/// Во время поиска в неё по спирали втягиваются частицы-файлы и поднимается уровень жидкости,
/// после — вокруг собирается кольцо из категорий, при очистке жидкость уходит воронкой,
/// а в конце сфера вспыхивает искрами.
struct LiquidOrb: View {
    let phase: ScanPhase
    let categories: [JunkCategory]
    let palette: [Color]
    /// Когда фаза сменилась — от этого момента идут анимации переходов.
    let since: Date

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 120, paused: reduceMotion)) { ctx in
            Canvas { g, size in
                draw(&g, size: size, now: ctx.date)
            }
        }
    }

    private var found: Int64 { categories.reduce(0) { $0 + $1.selectedSize } }

    /// Уровень жидкости: логарифм найденного (1 ГБ ≈ половина сферы, 20 ГБ ≈ почти полная).
    private func level(now: Date) -> Double {
        let base = found > 0 ? min(0.88, 0.12 + log10(1 + Double(found) / 50_000_000) / 3.0) : 0.1
        switch phase {
        case .idle: return 0.14
        case .scanning: return max(0.1, base)
        case .results: return base
        case .cleaning(let p): return base * (1 - p)
        case .done:
            let t = now.timeIntervalSince(since)
            return max(0, 0.6 * (1 - t / 0.9))
        }
    }

    private func draw(_ g: inout GraphicsContext, size: CGSize, now: Date) {
        let t = now.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 10_000)
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let r = min(size.width, size.height) * 0.33
        let tint = palette[0], second = palette[1], third = palette[2]

        var glow = g
        glow.blendMode = .plusLighter

        // Ореол.
        let energy: Double = {
            if case .scanning = phase { return 1 }
            if case .cleaning = phase { return 1 }
            return 0.4
        }()
        glow.fill(circle(c, r * 2), with: .radialGradient(Gradient(colors: [tint.opacity(0.35 * energy + 0.1), second.opacity(0.08), .clear]),
                                                          center: c, startRadius: r * 0.7, endRadius: r * 2))

        ring(&g, glow: glow, c: c, r: r, t: t, now: now)
        if case .scanning = phase { particles(glow: glow, g: g, c: c, r: r, t: t) }
        if case .cleaning = phase { vortex(glow: glow, c: c, r: r, t: t) }

        // Тело сферы: тёмное стекло.
        let box = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
        var inside = g
        inside.clip(to: Path(ellipseIn: box))
        inside.fill(Path(box), with: .radialGradient(Gradient(colors: [Color(red: 0.08, green: 0.1, blue: 0.2).opacity(0.85), Color(red: 0.02, green: 0.03, blue: 0.08).opacity(0.92)]),
                                                     center: CGPoint(x: c.x, y: c.y - r * 0.3), startRadius: 0, endRadius: r * 1.3))
        inside.fill(Path(box), with: .linearGradient(Gradient(colors: [tint.opacity(0.25), .clear]),
                                                     startPoint: CGPoint(x: c.x, y: box.minY), endPoint: CGPoint(x: c.x, y: c.midY)))

        // Жидкость.
        let lvl = level(now: now)
        if lvl > 0.005 {
            let surfaceY = box.maxY - box.height * lvl
            let busy: Double = { if case .scanning = phase { return 1 }; if case .cleaning = phase { return 1.6 }; return 0.5 }()
            let amp = r * (0.035 + 0.03 * busy)
            func wave(_ shift: Double, _ lift: CGFloat) -> [CGPoint] {
                (0...48).map { i in
                    let x = box.minX + box.width * Double(i) / 48
                    let y = surfaceY - lift + sin((x - box.minX) / r * 2.6 + t * (1.6 + busy) + shift) * amp
                        + sin((x - box.minX) / r * 5.1 - t * 1.1 + shift) * amp * 0.35
                    return CGPoint(x: x, y: y)
                }
            }
            func body(_ top: [CGPoint]) -> Path {
                var p = Path()
                p.move(to: CGPoint(x: box.minX, y: box.maxY))
                p.addLines(top)
                p.addLine(to: CGPoint(x: box.maxX, y: box.maxY))
                p.closeSubpath()
                return p
            }
            inside.fill(body(wave(2.1, r * 0.05)), with: .linearGradient(Gradient(colors: [third.opacity(0.75), second.opacity(0.9)]),
                                                                        startPoint: CGPoint(x: c.x, y: surfaceY), endPoint: CGPoint(x: c.x, y: box.maxY)))
            let front = wave(0, 0)
            inside.fill(body(front), with: .linearGradient(Gradient(colors: [tint.mix(with: .white, by: 0.35), tint, second.mix(with: .black, by: 0.2)]),
                                                          startPoint: CGPoint(x: c.x, y: surfaceY), endPoint: CGPoint(x: c.x, y: box.maxY)))
            var shine = inside
            shine.blendMode = .plusLighter
            var meniscus = Path()
            meniscus.addLines(front)
            shine.stroke(meniscus, with: .linearGradient(Gradient(colors: [.white.opacity(0.1), .white.opacity(0.9), .white.opacity(0.1)]),
                                                         startPoint: CGPoint(x: box.minX, y: surfaceY), endPoint: CGPoint(x: box.maxX, y: surfaceY)),
                         lineWidth: 2)
            for i in 0..<14 {
                let phase = frac(t * (0.18 + 0.25 * busy) + Double(i) * 0.0714)
                let x = box.minX + box.width * (0.15 + 0.7 * frac(sin(Double(i) * 91.3) * 437.5)) + sin(t * 2 + Double(i)) * r * 0.03
                let y = box.maxY - (box.maxY - surfaceY) * phase
                let br = r * (0.012 + 0.025 * frac(Double(i) * 0.37))
                shine.stroke(circle(CGPoint(x: x, y: y), br), with: .color(.white.opacity(0.6 * (1 - phase))), lineWidth: 1)
            }
            shine.fill(Path(box), with: .radialGradient(Gradient(colors: [tint.opacity(0.4), .clear]),
                                                        center: CGPoint(x: c.x, y: box.maxY), startRadius: 0, endRadius: r * 1.2))
        }
        // Объём, Френель и блики.
        inside.fill(Path(box), with: .radialGradient(Gradient(colors: [.clear, .black.opacity(0.35)]),
                                                     center: CGPoint(x: c.x - r * 0.2, y: c.y - r * 0.3), startRadius: r * 0.35, endRadius: r * 1.3))
        var rim = inside
        rim.blendMode = .plusLighter
        rim.fill(Path(box), with: .radialGradient(Gradient(colors: [.clear, .clear, tint.mix(with: .white, by: 0.5).opacity(0.5)]),
                                                  center: c, startRadius: 0, endRadius: r))
        let spec = CGRect(x: c.x - r * 0.66, y: c.y - r * 0.82, width: r * 0.92, height: r * 0.5)
        let turn = CGAffineTransform(translationX: spec.midX, y: spec.midY).rotated(by: -0.45).translatedBy(x: -spec.midX, y: -spec.midY)
        g.fill(Path(ellipseIn: spec).applying(turn), with: .linearGradient(Gradient(colors: [.white.opacity(0.85), .white.opacity(0)]),
                                                                          startPoint: CGPoint(x: spec.midX, y: spec.minY), endPoint: CGPoint(x: spec.midX, y: spec.maxY)))
        g.fill(circle(CGPoint(x: c.x + r * 0.45, y: c.y - r * 0.5), r * 0.05), with: .color(.white.opacity(0.7)))
        g.stroke(Path(ellipseIn: box), with: .linearGradient(Gradient(colors: [.white.opacity(0.85), .white.opacity(0.06), .white.opacity(0.35)]),
                                                             startPoint: CGPoint(x: box.minX, y: box.minY), endPoint: CGPoint(x: box.maxX, y: box.maxY)),
                 lineWidth: 1.5)

        if case .done = phase { sparkles(glow: glow, c: c, r: r, now: now) }
    }

    /// Кольцо вокруг сферы: при поиске — бегущий сканер, после — дуги категорий пропорционально размеру.
    private func ring(_ g: inout GraphicsContext, glow: GraphicsContext, c: CGPoint, r: CGFloat, t: Double, now: Date) {
        let rr = r + 22
        g.stroke(circle(c, rr), with: .color(.white.opacity(0.08)), lineWidth: 10)
        switch phase {
        case .scanning(let progress, _):
            var arc = Path()
            arc.addArc(center: c, radius: rr, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * max(0.02, progress)), clockwise: false)
            glow.stroke(arc, with: .color(palette[0].opacity(0.9)), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            let a = -Double.pi / 2 + t * 2.4
            let head = CGPoint(x: c.x + cos(a) * rr, y: c.y + sin(a) * rr)
            glow.fill(circle(head, 22), with: .radialGradient(Gradient(colors: [.white.opacity(0.9), palette[1].opacity(0.5), .clear]),
                                                              center: head, startRadius: 0, endRadius: 22))
        case .results, .cleaning:
            let total = max(1, categories.reduce(Int64(0)) { $0 + $1.selectedSize })
            let appear = min(1, now.timeIntervalSince(since) / 0.9)
            var start = -Double.pi / 2
            let gap = 0.04
            for cat in categories where cat.selectedSize > 0 {
                let sweep = 2 * .pi * Double(cat.selectedSize) / Double(total) * appear
                guard sweep > gap * 1.5 else { start += sweep; continue }
                var arc = Path()
                arc.addArc(center: c, radius: rr, startAngle: .radians(start + gap / 2), endAngle: .radians(start + sweep - gap / 2), clockwise: false)
                glow.stroke(arc, with: .color(cat.id.color.opacity(0.45)), style: StrokeStyle(lineWidth: 18, lineCap: .round))
                g.stroke(arc, with: .color(cat.id.color), style: StrokeStyle(lineWidth: 10, lineCap: .round))
                start += sweep
            }
        default:
            break
        }
    }

    /// Файлы втягиваются в сферу по спирали.
    private func particles(glow: GraphicsContext, g: GraphicsContext, c: CGPoint, r: CGFloat, t: Double) {
        let colors = categories.isEmpty ? palette : categories.map(\.id.color)
        for i in 0..<56 {
            let seed = Double(i) * 1.618
            let u = frac(t * 0.35 + seed)
            let angle = seed * 6.28 + u * 3.2
            let dist = r * (2.3 - 1.3 * u * u)
            let p = CGPoint(x: c.x + cos(angle) * dist, y: c.y + sin(angle) * dist * 0.92)
            let size = 1.5 + 2.5 * (1 - u)
            let color = colors[i % colors.count]
            let alpha = min(1, u * 4) * (1 - u * 0.4)
            glow.fill(circle(p, size * 3), with: .radialGradient(Gradient(colors: [color.opacity(0.6 * alpha), .clear]), center: p, startRadius: 0, endRadius: size * 3))
            g.fill(circle(p, size), with: .color(.white.opacity(0.9 * alpha)))
        }
    }

    /// Воронка при очистке: жидкость закручивается и уходит.
    private func vortex(glow: GraphicsContext, c: CGPoint, r: CGFloat, t: Double) {
        for arm in 0..<5 {
            var p = Path()
            for i in 0...60 {
                let u = Double(i) / 60
                let a = Double(arm) * 2 * .pi / 5 + u * 4 + t * 3
                let d = r * 1.9 * (1 - u)
                let pt = CGPoint(x: c.x + cos(a) * d, y: c.y + sin(a) * d)
                if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
            glow.stroke(p, with: .linearGradient(Gradient(colors: [.clear, palette[arm % palette.count].opacity(0.7)]),
                                                 startPoint: CGPoint(x: c.x + r * 2, y: c.y), endPoint: c), lineWidth: 2.5)
        }
    }

    /// Искры после очистки.
    private func sparkles(glow: GraphicsContext, c: CGPoint, r: CGFloat, now: Date) {
        let t = now.timeIntervalSince(since)
        guard t < 2.4 else { return }
        for i in 0..<70 {
            let a = Double(i) * 2.399963
            let speed = 0.6 + frac(Double(i) * 0.731) * 0.9
            let d = r * (0.3 + speed * t * 1.4)
            let p = CGPoint(x: c.x + cos(a) * d, y: c.y + sin(a) * d + t * t * 18)
            let alpha = max(0, 1 - t / 2.4)
            let s = 2 + 3 * frac(Double(i) * 0.37)
            glow.fill(circle(p, s * 3), with: .radialGradient(Gradient(colors: [palette[i % 3].opacity(alpha), .clear]), center: p, startRadius: 0, endRadius: s * 3))
            glow.fill(circle(p, s * 0.6), with: .color(.white.opacity(alpha)))
        }
    }

    private func circle(_ p: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
    }
}

func frac(_ x: Double) -> Double { x - x.rounded(.down) }
