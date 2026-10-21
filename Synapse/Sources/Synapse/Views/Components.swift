import AppKit
import SwiftUI

/// Сияние за содержимым раздела — у каждого своя палитра, движение медленное.
struct SectionBackground: View {
    let section: Section
    var energy: Double = 0

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { ctx in
            let t = Float(ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600)) * Float(0.5 + energy)
            MeshGradient(width: 3, height: 3, points: points(t), colors: colors, smoothsColors: true)
        }
        .ignoresSafeArea()
        .animation(.smooth(duration: 1.2), value: section)
    }

    private var colors: [Color] {
        let p = section.palette
        if scheme == .dark {
            let base = Color(red: 0.045, green: 0.05, blue: 0.09)
            return [base, p[0].mix(with: base, by: 0.7), base,
                    p[1].mix(with: base, by: 0.78), p[2].mix(with: base, by: 0.72), p[0].mix(with: base, by: 0.8),
                    base, p[1].mix(with: base, by: 0.75), base]
        }
        let base = Color(red: 0.97, green: 0.97, blue: 0.99)
        return [base, p[0].mix(with: base, by: 0.8), base,
                p[1].mix(with: base, by: 0.84), p[2].mix(with: base, by: 0.78), p[0].mix(with: base, by: 0.86),
                base, p[1].mix(with: base, by: 0.82), base]
    }

    private func points(_ t: Float) -> [SIMD2<Float>] {
        [SIMD2(0, 0), SIMD2(0.5 + 0.12 * sin(t * 0.13), 0), SIMD2(1, 0),
         SIMD2(0, 0.5 + 0.12 * cos(t * 0.11)), SIMD2(0.5 + 0.15 * sin(t * 0.09), 0.5 + 0.12 * cos(t * 0.12)), SIMD2(1, 0.5 + 0.12 * sin(t * 0.15)),
         SIMD2(0, 1), SIMD2(0.5 + 0.12 * cos(t * 0.1), 1), SIMD2(1, 1)]
    }
}

/// Карточка-подложка для содержимого (стекло — для элементов управления поверх).
struct ContentCard<Content: View>: View {
    var padding: CGFloat = 0
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.background.opacity(0.6)))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.primary.opacity(0.07)))
    }
}

/// Заголовок раздела.
struct SectionHeader<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 26, weight: .bold))
                Text(subtitle).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer(minLength: 12)
            trailing
        }
        .padding(.horizontal, 28)
        .padding(.top, 22)
        .padding(.bottom, 12)
    }
}

extension SectionHeader where Trailing == EmptyView {
    init(title: String, subtitle: String) {
        self.init(title: title, subtitle: subtitle) { EmptyView() }
    }
}

extension FitReport.Level {
    var color: Color {
        switch self {
        case .great: Color(red: 0.2, green: 0.78, blue: 0.5)
        case .good: Color(red: 0.25, green: 0.7, blue: 0.95)
        case .ok: Color(red: 0.45, green: 0.55, blue: 1)
        case .slow: Color(red: 0.95, green: 0.65, blue: 0.2)
        case .tight: .orange
        case .tooBig, .noDisk: Color(red: 0.95, green: 0.3, blue: 0.3)
        }
    }
}

/// Значок «как пойдёт на этом Mac».
struct FitBadge: View {
    let fit: FitReport
    var compact = false

    var body: some View {
        Label(fit.title, systemImage: fit.symbol)
            .font(.system(size: compact ? 10 : 11, weight: .semibold))
            .foregroundStyle(fit.level.color)
            .padding(.horizontal, compact ? 6 : 8)
            .padding(.vertical, compact ? 2 : 3)
            .background(Capsule().fill(fit.level.color.opacity(0.14)))
            .help(fit.reasons.joined(separator: "\n"))
            .fixedSize()
    }
}

/// Монограмма лаборатории в цветном скруглённом квадрате.
struct ModelAvatar: View {
    let lab: String
    var size: CGFloat = 40

    var body: some View {
        let colors = Self.colors(for: lab)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
            Text(Self.monogram(lab))
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
        .shadow(color: colors[0].opacity(0.35), radius: size * 0.12, y: size * 0.05)
    }

    static func monogram(_ lab: String) -> String {
        let words = lab.split(whereSeparator: { $0 == " " || $0 == "-" })
        if words.count >= 2 { return String(words[0].prefix(1) + words[1].prefix(1)).uppercased() }
        return String(lab.prefix(2)).capitalized
    }

    static func colors(for lab: String) -> [Color] {
        switch lab.lowercased() {
        case "qwen": return [Color(red: 0.45, green: 0.3, blue: 1), Color(red: 0.25, green: 0.55, blue: 1)]
        case "google": return [Color(red: 0.2, green: 0.55, blue: 1), Color(red: 0.2, green: 0.8, blue: 0.6)]
        case "openai": return [Color(red: 0.15, green: 0.15, blue: 0.2), Color(red: 0.35, green: 0.4, blue: 0.45)]
        case "mistral ai": return [Color(red: 1, green: 0.5, blue: 0.1), Color(red: 0.95, green: 0.25, blue: 0.2)]
        case "ibm": return [Color(red: 0.1, green: 0.35, blue: 0.85), Color(red: 0.3, green: 0.6, blue: 1)]
        case "nvidia": return [Color(red: 0.4, green: 0.75, blue: 0.1), Color(red: 0.2, green: 0.55, blue: 0.1)]
        case "meta": return [Color(red: 0.1, green: 0.45, blue: 1), Color(red: 0.55, green: 0.3, blue: 1)]
        case "deepseek": return [Color(red: 0.3, green: 0.4, blue: 1), Color(red: 0.2, green: 0.25, blue: 0.7)]
        case "microsoft": return [Color(red: 0.95, green: 0.45, blue: 0.15), Color(red: 0.2, green: 0.6, blue: 0.95)]
        default:
            let hue = Double(lab.unicodeScalars.reduce(UInt32(7)) { $0 &* 31 &+ $1.value } % 360) / 360
            return [Color(hue: hue, saturation: 0.65, brightness: 0.9), Color(hue: (hue + 0.12).truncatingRemainder(dividingBy: 1), saturation: 0.7, brightness: 0.75)]
        }
    }
}

/// Маленькая плашка с тегом.
struct TagPill: View {
    let tag: ModelTag

    var body: some View {
        Label(tag.title, systemImage: tag.symbol)
            .font(.system(size: 10, weight: .medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(Capsule().fill(.primary.opacity(0.06)))
            .fixedSize()
    }
}

/// Плитка с цифрой: «Память · 16 ГБ».
struct StatTile: View {
    let symbol: String
    let title: String
    let value: String
    var detail: String? = nil
    var tint: Color = .accentColor

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(tint.gradient))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 11)).foregroundStyle(.secondary)
                Text(value).font(.system(size: 15, weight: .semibold, design: .rounded)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.8)
                if let detail { Text(detail).font(.system(size: 10)).foregroundStyle(.tertiary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.primary.opacity(0.04)))
    }
}

/// Полоса прогресса с градиентом.
struct GlowBar: View {
    let fraction: Double
    var colors: [Color] = Section.library.palette

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(.primary.opacity(0.08))
                Capsule()
                    .fill(LinearGradient(colors: [colors[0], colors[1]], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(6, g.size.width * min(1, max(0, fraction))))
                    .shadow(color: colors[0].opacity(0.5), radius: 4)
            }
        }
        .frame(height: 6)
        .animation(.smooth(duration: 0.4), value: fraction)
    }
}

/// Кнопка «Установить» с состоянием: ставится, установлена, ошибка.
struct InstallButton: View {
    @Environment(AppModel.self) private var model
    let card: ModelCard
    var variant: ModelVariant? = nil
    var prominent = true

    var body: some View {
        if let job = model.job(for: card), job.isActive || job.state == .paused {
            Button {
                model.section = .library
            } label: {
                HStack(spacing: 6) {
                    ProgressView(value: job.fraction).progressViewStyle(.circular).controlSize(.small)
                    Text(job.state == .paused ? "Пауза" : "\(Int(job.fraction * 100))%").monospacedDigit()
                }
            }
            .buttonStyle(.glass)
        } else if let variant, model.installed.contains(where: { $0.repo == card.id && $0.quant == variant.quant.name }) {
            Label("Установлена", systemImage: "checkmark.circle.fill").foregroundStyle(.green).font(.system(size: 12, weight: .semibold))
        } else if variant == nil, model.isInstalled(card) {
            Label("Установлена", systemImage: "checkmark.circle.fill").foregroundStyle(.green).font(.system(size: 12, weight: .semibold))
        } else if prominent {
            Button { model.install(card, variant: variant) } label: { Label("Установить", systemImage: "arrow.down.circle.fill") }
                .buttonStyle(.glassProminent)
                .tint(Section.catalog.tint)
        } else {
            Button { model.install(card, variant: variant) } label: { Label("Установить", systemImage: "arrow.down.circle") }
                .buttonStyle(.glass)
        }
    }
}

/// Плашка-предупреждение с причинами.
struct FitExplanation: View {
    let fit: FitReport

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: fit.symbol).foregroundStyle(fit.level.color).font(.system(size: 15))
            VStack(alignment: .leading, spacing: 3) {
                Text(headline).font(.system(size: 12.5, weight: .semibold))
                ForEach(fit.reasons, id: \.self) { Text($0).font(.system(size: 11.5)).foregroundStyle(.secondary) }
                if fit.runnable { Text(fit.speedText).font(.system(size: 11.5)).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(fit.level.color.opacity(0.1)))
    }

    private var headline: String {
        switch fit.level {
        case .great, .good, .ok: "Подходит вашему Mac — \(fit.title.lowercased())"
        case .slow: "Запустится, но будет медленно"
        case .tight: "На пределе возможностей этого Mac"
        case .tooBig: "Слишком большая для этого Mac"
        case .noDisk: "Не хватает места на диске"
        }
    }
}

struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> Probe {
        let v = Probe()
        v.onWindow = onWindow
        return v
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
