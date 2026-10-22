import SwiftUI

/// Цвет состояния подключения.
extension ConnectionStatus {
    var tint: Color {
        switch self {
        case .connected: Color(red: 0.2, green: 0.82, blue: 0.55)
        case .connecting, .reconnecting: Color(red: 1.0, green: 0.68, blue: 0.2)
        case .failed: Color(red: 1.0, green: 0.36, blue: 0.36)
        case .disconnected: Color(red: 0.5, green: 0.52, blue: 0.62)
        }
    }
}

/// Мягкое свечение позади стекла: цвет — состояние подключения. Стеклу нужен фон, чтобы быть стеклом.
struct Aura: View {
    var status: ConnectionStatus

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 20)) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            Canvas { gc, size in
                let tint = status.tint
                let blobs: [(CGFloat, CGFloat, CGFloat, Color, Double)] = [
                    (0.22, 0.30, 0.55, tint, 0.55),
                    (0.80, 0.18, 0.45, Color(red: 0.35, green: 0.4, blue: 1.0), 0.35),
                    (0.65, 0.85, 0.50, tint.opacity(0.8), 0.30),
                ]
                for (i, b) in blobs.enumerated() {
                    let dx = CGFloat(sin(t * 0.13 + Double(i) * 2.1)) * 0.05
                    let dy = CGFloat(cos(t * 0.11 + Double(i) * 1.3)) * 0.05
                    let c = CGPoint(x: (b.0 + dx) * size.width, y: (b.1 + dy) * size.height)
                    let r = b.2 * max(size.width, size.height)
                    gc.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                            with: .radialGradient(Gradient(colors: [b.3.opacity(b.4), b.3.opacity(0)]), center: c, startRadius: 0, endRadius: r))
                }
            }
        }
        .background(Color(nsColor: .windowBackgroundColor))
        .animation(.easeInOut(duration: 0.8), value: status)
        .ignoresSafeArea()
    }
}

/// Большая кнопка подключения: стеклянная сфера со щитом, кольца расходятся, пока идёт подключение,
/// и мягко дышат, пока соединение живо.
struct ShieldOrb: View {
    var status: ConnectionStatus
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            TimelineView(.animation(minimumInterval: 1 / 60, paused: status == .disconnected)) { ctx in
                let t = ctx.date.timeIntervalSinceReferenceDate
                ZStack {
                    Canvas { gc, size in
                        let c = CGPoint(x: size.width / 2, y: size.height / 2)
                        let r = min(size.width, size.height) / 2
                        let busy = status == .connecting || { if case .reconnecting = status { true } else { false } }()
                        let speed = busy ? 1.4 : 0.35
                        for i in 0..<3 {
                            let phase = (t * speed + Double(i) / 3).truncatingRemainder(dividingBy: 1)
                            let rr = r * (0.62 + 0.38 * CGFloat(phase))
                            let alpha = status.isOn ? (1 - phase) * (busy ? 0.6 : 0.35) : 0
                            gc.stroke(Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: rr * 2, height: rr * 2)),
                                      with: .color(status.tint.opacity(alpha)), lineWidth: 2)
                        }
                    }
                    Circle()
                        .fill(RadialGradient(colors: [status.tint.opacity(0.95), status.tint.opacity(0.55)],
                                             center: UnitPoint(x: 0.35, y: 0.3), startRadius: 4, endRadius: 90))
                        .frame(width: 118, height: 118)
                        .shadow(color: status.tint.opacity(status.isOn ? 0.6 : 0.2), radius: 24)
                        .glassEffect(.regular.tint(status.tint.opacity(0.2)).interactive(), in: .circle)
                    Image(systemName: status.isOn ? "shield.lefthalf.filled" : "power")
                        .font(.system(size: 40, weight: .semibold))
                        .foregroundStyle(.white)
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            .frame(width: 190, height: 190)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(status.isOn ? "Отключить" : "Подключить")
    }
}

struct PingBadge: View {
    var result: Ping.Result?
    var busy: Bool

    var body: some View {
        Group {
            if busy {
                ProgressView().controlSize(.mini)
            } else if let result {
                switch result {
                case .ms(let v):
                    Text("\(v) мс").foregroundStyle(color(v))
                case .timeout:
                    Text("нет ответа").foregroundStyle(.red.opacity(0.85))
                case .unsupported:
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                }
            } else {
                Text("—").foregroundStyle(.tertiary)
            }
        }
        .font(.system(size: 11.5, weight: .semibold, design: .rounded))
        .monospacedDigit()
        .frame(minWidth: 58, alignment: .trailing)
    }

    func color(_ v: Int) -> Color {
        v < 300 ? .green : v < 800 ? .yellow : .orange
    }
}

/// Значок «VLESS · Reality» — маленькая капсула.
struct Tag: View {
    var text: String
    var tint: Color = .secondary

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Capsule().fill(tint.opacity(0.12)))
    }
}

/// График скорости за последние полторы минуты: загрузка — заливкой, отдача — линией.
struct SpeedGraph: View {
    var points: [SpeedPoint]
    var tint: Color

    var body: some View {
        Canvas { gc, size in
            guard points.count > 1 else { return }
            let peak = max(64 * 1024, points.map { max($0.down, $0.up) }.max() ?? 1)
            let step = size.width / CGFloat(max(1, 89))
            let offset = size.width - CGFloat(points.count - 1) * step
            func y(_ v: Double) -> CGFloat { size.height - CGFloat(v / peak) * (size.height - 4) }
            var down = Path()
            down.move(to: CGPoint(x: offset, y: size.height))
            for (i, p) in points.enumerated() { down.addLine(to: CGPoint(x: offset + CGFloat(i) * step, y: y(p.down))) }
            down.addLine(to: CGPoint(x: offset + CGFloat(points.count - 1) * step, y: size.height))
            down.closeSubpath()
            gc.fill(down, with: .linearGradient(Gradient(colors: [tint.opacity(0.55), tint.opacity(0.05)]),
                                                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            var up = Path()
            for (i, p) in points.enumerated() {
                let pt = CGPoint(x: offset + CGFloat(i) * step, y: y(p.up))
                i == 0 ? up.move(to: pt) : up.addLine(to: pt)
            }
            gc.stroke(up, with: .color(Color(red: 0.55, green: 0.5, blue: 1)), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
        }
    }
}

/// Подложка-карточка из стекла.
struct GlassCard<Content: View>: View {
    var padding: CGFloat = 14
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }
}

/// Всплывающее сообщение внизу окна.
struct Toast: View {
    var text: String

    var body: some View {
        Text(text)
            .font(.system(size: 12.5, weight: .medium))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glassEffect(.regular, in: .capsule)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// Полоса израсходованного трафика подписки.
struct UsageBar: View {
    var used: Int64
    var total: Int64

    var body: some View {
        GeometryReader { geo in
            let frac = total > 0 ? min(1, Double(used) / Double(total)) : 0
            ZStack(alignment: .leading) {
                Capsule().fill(.primary.opacity(0.08))
                Capsule()
                    .fill(LinearGradient(colors: frac > 0.9 ? [.orange, .red] : [.teal, .green], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(4, geo.size.width * frac))
            }
        }
        .frame(height: 5)
    }
}
