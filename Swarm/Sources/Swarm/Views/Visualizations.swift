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
