import Charts
import SwiftUI

/// Мозаика кусков. Готовые куски светятся оттенком раздачи, только что проверенные — вспыхивают.
/// Для больших раздач клетка объединяет несколько кусков, яркость — доля готовых.
struct PieceMosaic: View {
    let snapshot: TorrentSnapshot
    let tint: Color
    var cell: CGFloat = 7
    var gap: CGFloat = 1.5

    var body: some View {
        let animating = !snapshot.recentPieces.isEmpty || !snapshot.inProgressPieces.isEmpty
        TimelineView(.animation(minimumInterval: 1 / 20, paused: !animating)) { ctx in
            Canvas(rendersAsynchronously: true) { g, size in
                draw(g, size: size, now: ctx.date)
            }
        }
    }

    private func draw(_ g: GraphicsContext, size: CGSize, now: Date) {
        let count = snapshot.pieceCount
        guard count > 0 else {
            g.fill(Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 6), with: .color(.primary.opacity(0.05)))
            return
        }
        let step = cell + gap
        let columns = max(1, Int((size.width + gap) / step))
        let rows = max(1, Int((size.height + gap) / step))
        let cells = min(count, columns * rows)
        let perCell = Double(count) / Double(cells)
        let have = Bitfield(count: count, data: snapshot.havePieces)
        let inProgress = Set(snapshot.inProgressPieces)
        let pulse = 0.5 + 0.5 * sin(now.timeIntervalSinceReferenceDate * 5)

        var glow: [(CGRect, Double)] = []
        for c in 0..<cells {
            let first = Int(Double(c) * perCell)
            let last = max(first, Int(Double(c + 1) * perCell) - 1)
            var done = 0, active = false
            var freshest: Double = 10
            for p in first...min(last, count - 1) {
                if have[p] { done += 1 }
                if inProgress.contains(p) { active = true }
                if let at = snapshot.recentPieces[p] { freshest = min(freshest, now.timeIntervalSince(at)) }
            }
            let fraction = Double(done) / Double(last - first + 1)
            let rect = CGRect(x: CGFloat(c % columns) * step, y: CGFloat(c / columns) * step, width: cell, height: cell)
            let path = Path(roundedRect: rect, cornerRadius: cell * 0.28)
            if fraction > 0 {
                g.fill(path, with: .color(tint.opacity(0.35 + 0.65 * fraction)))
            } else if active {
                g.fill(path, with: .color(tint.opacity(0.12 + 0.18 * pulse)))
            } else {
                g.fill(path, with: .color(.primary.opacity(0.07)))
            }
            if freshest < 3 { glow.append((rect, 1 - freshest / 3)) }
        }
        guard !glow.isEmpty else { return }
        g.drawLayer { layer in
            layer.addFilter(.blur(radius: 4))
            for (rect, strength) in glow {
                layer.fill(Path(roundedRect: rect.insetBy(dx: -2, dy: -2), cornerRadius: cell * 0.4),
                           with: .color(.white.opacity(0.9 * strength)))
            }
        }
        for (rect, strength) in glow {
            g.fill(Path(roundedRect: rect, cornerRadius: cell * 0.28), with: .color(.white.opacity(0.7 * strength)))
        }
    }
}

/// Созвездие роя: мы в центре, пиры — на орбитах (сиды снаружи), по лучам летят частицы
/// со скоростью, пропорциональной реальному трафику.
struct SwarmConstellation: View {
    let snapshot: TorrentSnapshot
    let tint: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion)) { ctx in
            Canvas { g, size in
                draw(g, size: size, t: ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 10_000))
            }
        }
        .overlay(alignment: .bottomLeading) {
            HStack(spacing: 12) {
                legend(Color(red: 0.2, green: 0.85, blue: 0.7), "Сиды")
                legend(Color(red: 0.6, green: 0.5, blue: 1.0), "Качают")
                Text("\(snapshot.peersConnected) подключено · \(snapshot.peersKnown) известно")
                    .foregroundStyle(.secondary)
            }
            .font(.system(size: 10.5, weight: .medium))
            .padding(10)
        }
    }

    private func legend(_ color: Color, _ title: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(title)
        }
    }

    private func draw(_ g: GraphicsContext, size: CGSize, t: Double) {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let maxR = min(size.width, size.height) * 0.46
        let seedColor = Color(red: 0.2, green: 0.85, blue: 0.7)
        let leechColor = Color(red: 0.6, green: 0.5, blue: 1.0)

        // Орбиты.
        for r in [0.45, 0.72, 1.0] {
            let rr = maxR * r
            g.stroke(Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr * 0.62, width: rr * 2, height: rr * 1.24)),
                     with: .color(.primary.opacity(0.07)), style: StrokeStyle(lineWidth: 1, dash: [3, 5]))
        }

        var nodes: [(CGPoint, PeerSnapshot)] = []
        for peer in snapshot.peers.prefix(80) {
            let orbit = peer.isSeed ? 1.0 : 0.45 + 0.5 * peer.progress
            let angle = peer.angle + t * (0.05 + 0.04 * (1 - orbit))
            let p = CGPoint(x: c.x + cos(angle) * maxR * orbit, y: c.y + sin(angle) * maxR * orbit * 0.62)
            nodes.append((p, peer))
        }

        // Лучи и частицы трафика.
        for (p, peer) in nodes {
            let rate = peer.downRate + peer.upRate
            guard rate > 500 else { continue }
            var line = Path()
            line.move(to: p)
            line.addLine(to: c)
            let strength = min(1, log10(1 + rate / 5000) / 3)
            g.stroke(line, with: .linearGradient(Gradient(colors: [(peer.isSeed ? seedColor : leechColor).opacity(0.05 + 0.3 * strength), tint.opacity(0.4 * strength)]),
                                                 startPoint: p, endPoint: c), lineWidth: 1 + 1.5 * strength)
            let particles = min(6, 1 + Int(rate / 60_000))
            let speed = 0.25 + 0.6 * strength
            let seed = peer.angle * 7
            for i in 0..<particles {
                var phase = (t * speed + Double(i) / Double(particles) + seed).truncatingRemainder(dividingBy: 1)
                let incoming = peer.downRate >= peer.upRate
                if !incoming { phase = 1 - phase }
                let x = p.x + (c.x - p.x) * phase
                let y = p.y + (c.y - p.y) * phase
                let r = 1.6 + 1.4 * strength
                g.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                       with: .color(incoming ? Color.white.opacity(0.9) : tint.opacity(0.9)))
            }
        }

        // Пиры.
        for (p, peer) in nodes {
            let rate = peer.downRate + peer.upRate
            let r = 3 + 4 * min(1, log10(1 + rate / 10_000) / 2.5)
            let color = peer.isSeed ? seedColor : leechColor
            g.fill(Path(ellipseIn: CGRect(x: p.x - r * 2.2, y: p.y - r * 2.2, width: r * 4.4, height: r * 4.4)),
                   with: .radialGradient(Gradient(colors: [color.opacity(0.35), .clear]), center: p, startRadius: 0, endRadius: r * 2.2))
            g.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)), with: .color(color))
            if peer.isIncoming {
                g.stroke(Path(ellipseIn: CGRect(x: p.x - r - 2, y: p.y - r - 2, width: r * 2 + 4, height: r * 2 + 4)),
                         with: .color(.white.opacity(0.6)), lineWidth: 1)
            }
        }

        // Мы — в центре, с кольцом прогресса.
        let pulse = 1 + 0.06 * sin(t * 3)
        let coreR = 16 * pulse
        g.fill(Path(ellipseIn: CGRect(x: c.x - coreR * 2.6, y: c.y - coreR * 2.6, width: coreR * 5.2, height: coreR * 5.2)),
               with: .radialGradient(Gradient(colors: [tint.opacity(0.45), .clear]), center: c, startRadius: 0, endRadius: coreR * 2.6))
        g.fill(Path(ellipseIn: CGRect(x: c.x - coreR, y: c.y - coreR, width: coreR * 2, height: coreR * 2)), with: .color(tint))
        var ring = Path()
        ring.addArc(center: c, radius: coreR + 5, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * snapshot.progress), clockwise: false)
        g.stroke(ring, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
    }
}

/// График скорости: загрузка и отдача за последние две минуты.
struct SpeedChart: View {
    let samples: [SpeedSample]
    var compact = false

    var body: some View {
        Chart(samples) { s in
            AreaMark(x: .value("Время", s.date), y: .value("Загрузка", s.down), series: .value("Тип", "down"))
                .foregroundStyle(LinearGradient(colors: [Color.blue.opacity(0.45), Color.blue.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                .interpolationMethod(.catmullRom)
            LineMark(x: .value("Время", s.date), y: .value("Загрузка", s.down), series: .value("Тип", "down"))
                .foregroundStyle(Color.blue)
                .lineStyle(StrokeStyle(lineWidth: 1.8))
                .interpolationMethod(.catmullRom)
            LineMark(x: .value("Время", s.date), y: .value("Отдача", s.up), series: .value("Тип", "up"))
                .foregroundStyle(Color(red: 0.15, green: 0.8, blue: 0.6))
                .lineStyle(StrokeStyle(lineWidth: 1.5, dash: compact ? [] : [4, 3]))
                .interpolationMethod(.catmullRom)
        }
        .chartXAxis(compact ? .hidden : .automatic)
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 3)) { value in
                AxisGridLine().foregroundStyle(.primary.opacity(0.08))
                AxisValueLabel {
                    if let v = value.as(Double.self) { Text(Format.rate(v)).font(.system(size: 9)) }
                }
            }
        }
        .chartYAxis(compact ? .hidden : .automatic)
        .chartLegend(.hidden)
        .animation(.smooth, value: samples.last?.id)
    }
}
