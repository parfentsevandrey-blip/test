import AppKit
import SwiftUI

/// Кнопка внутри стеклянного «острова»: без собственного стекла (стекло на стекле
/// противоречит гайдлайнам), с мягкой подсветкой при наведении и пружинным нажатием.
struct IslandButton: View {
    let symbol: String
    var size: CGFloat = 15
    var weight: Font.Weight = .semibold
    var help: String = ""
    var isActive = false
    var isEnabled = true
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: size, weight: weight))
                .symbolRenderingMode(.hierarchical)
                .contentTransition(.symbolEffect(.replace.magic(fallback: .replace)))
                .frame(width: size * 2.35, height: size * 2.35)
                .background {
                    Circle()
                        .fill(.white.opacity(hovering ? 0.16 : (isActive ? 0.1 : 0)))
                }
                .contentShape(Circle())
        }
        .buttonStyle(PressScaleStyle())
        .foregroundStyle(isActive ? AnyShapeStyle(.tint) : AnyShapeStyle(.primary))
        .opacity(isEnabled ? 1 : 0.35)
        .disabled(!isEnabled)
        .onHover { h in withAnimation(.smooth(duration: 0.18)) { hovering = h } }
        .help(help)
    }
}

struct PressScaleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.86 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

/// Капсула-чип (скорость, пресеты) — выбранный подсвечивается тонированным стеклом.
struct GlassChip: View {
    let title: String
    var symbol: String?
    var isSelected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let symbol { Image(systemName: symbol).font(.system(size: 12, weight: .semibold)) }
                Text(title).font(.system(size: 12.5, weight: .semibold, design: .rounded)).monospacedDigit()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .contentShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
        .glassEffect(isSelected ? .regular.tint(.accentColor.opacity(0.55)).interactive() : .regular.interactive(), in: .capsule)
    }
}

/// Кольцо прогресса с градиентом и свечением.
struct ProgressRing: View {
    var progress: Double?
    var lineWidth: CGFloat = 7
    var colors: [Color] = [.cyan, .blue, .purple, .pink, .cyan]

    @State private var spin = false

    var body: some View {
        ZStack {
            Circle().stroke(.white.opacity(0.1), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: progress.map { max(0.02, $0) } ?? 0.28)
                .stroke(AngularGradient(colors: colors, center: .center),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .rotationEffect(.degrees(progress == nil && spin ? 360 : 0))
                .shadow(color: .cyan.opacity(0.6), radius: 10)
                .animation(.smooth(duration: 0.4), value: progress)
        }
        .onAppear {
            withAnimation(.linear(duration: 1.1).repeatForever(autoreverses: false)) { spin = true }
        }
    }
}

/// Название с переливом — медленный блик проходит по градиентному тексту.
struct ShimmerTitle: View {
    let text: String
    var size: CGFloat = 46

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let phase = CGFloat((t / 4).truncatingRemainder(dividingBy: 1))
            Text(text)
                .font(.system(size: size, weight: .heavy, design: .rounded))
                .foregroundStyle(
                    LinearGradient(colors: [.white, Color(red: 0.75, green: 0.85, blue: 1), Color(red: 0.95, green: 0.75, blue: 1)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                .overlay {
                    LinearGradient(stops: [
                        .init(color: .clear, location: phase - 0.15),
                        .init(color: .white.opacity(0.9), location: phase),
                        .init(color: .clear, location: phase + 0.15),
                    ], startPoint: .leading, endPoint: .trailing)
                    .blendMode(.plusLighter)
                    .mask {
                        Text(text).font(.system(size: size, weight: .heavy, design: .rounded))
                    }
                }
        }
    }
}

/// Даёт доступ к NSWindow, в котором живёт SwiftUI-иерархия.
struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> Probe {
        let view = Probe()
        view.onWindow = onWindow
        return view
    }

    func updateNSView(_ nsView: Probe, context: Context) {}

    final class Probe: NSView {
        var onWindow: ((NSWindow) -> Void)?
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { onWindow?(window) }
        }
    }
}

extension View {
    /// Стеклянная панель-карточка с концентрическими скруглениями.
    func glassPanel(cornerRadius: CGFloat = 26, tint: Color? = nil) -> some View {
        glassEffect(.regular.tint(tint), in: .rect(cornerRadius: cornerRadius))
    }
}

/// Форматирование размеров и битрейтов.
enum Units {
    static func bytes(_ value: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: value, countStyle: .file)
    }

    static func bitrate(_ bps: Double) -> String {
        guard bps > 0 else { return "—" }
        return bps >= 1_000_000 ? String(format: "%.1f Мбит/с", bps / 1_000_000) : String(format: "%.0f кбит/с", bps / 1000)
    }

    static func channels(_ n: Int) -> String {
        switch n {
        case 0: "—"
        case 1: "Моно"
        case 2: "Стерео"
        case 6: "5.1"
        case 8: "7.1"
        default: "\(n) кан."
        }
    }
}
