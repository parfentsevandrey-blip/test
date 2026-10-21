import AppKit
import SwiftUI

/// Сияние за содержимым модуля: у каждого модуля своя палитра, движение медленное и спокойное.
struct ModuleBackground: View {
    let module: Module
    var energy: Double = 0

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { ctx in
            let t = Float(ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600)) * Float(0.5 + energy)
            MeshGradient(width: 3, height: 3, points: points(t), colors: colors, smoothsColors: true)
        }
        .ignoresSafeArea()
        .animation(.smooth(duration: 1.2), value: module)
    }

    private var colors: [Color] {
        let p = module.palette
        if scheme == .dark {
            let base = Color(red: 0.05, green: 0.06, blue: 0.1)
            return [base, p[0].mix(with: base, by: 0.72), base,
                    p[1].mix(with: base, by: 0.78), p[2].mix(with: base, by: 0.7), p[0].mix(with: base, by: 0.8),
                    base, p[1].mix(with: base, by: 0.75), base]
        }
        let base = Color(red: 0.97, green: 0.97, blue: 0.99)
        return [base, p[0].mix(with: base, by: 0.78), base,
                p[1].mix(with: base, by: 0.82), p[2].mix(with: base, by: 0.76), p[0].mix(with: base, by: 0.85),
                base, p[1].mix(with: base, by: 0.8), base]
    }

    private func points(_ t: Float) -> [SIMD2<Float>] {
        [SIMD2(0, 0), SIMD2(0.5 + 0.12 * sin(t * 0.13), 0), SIMD2(1, 0),
         SIMD2(0, 0.5 + 0.12 * cos(t * 0.11)), SIMD2(0.5 + 0.15 * sin(t * 0.09), 0.5 + 0.12 * cos(t * 0.12)), SIMD2(1, 0.5 + 0.12 * sin(t * 0.15)),
         SIMD2(0, 1), SIMD2(0.5 + 0.12 * cos(t * 0.1), 1), SIMD2(1, 1)]
    }
}

/// Иконка программы или файла.
struct FileIcon: View {
    var url: URL?
    var bundleID: String?
    var symbol: String = "doc.fill"
    var size: CGFloat = 28

    var body: some View {
        Group {
            if let image = resolve() {
                Image(nsImage: image).resizable().interpolation(.high)
            } else {
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .padding(size * 0.18)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
    }

    private func resolve() -> NSImage? {
        if let bundleID, let app = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID) {
            return NSWorkspace.shared.icon(forFile: app.path)
        }
        if let url, FileManager.default.fileExists(atPath: url.path) {
            return NSWorkspace.shared.icon(forFile: url.path)
        }
        return nil
    }
}

struct RiskBadge: View {
    let risk: Risk

    var body: some View {
        Text(title)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.13)))
    }

    private var title: String {
        switch risk {
        case .safe: "Безопасно"
        case .review: "Проверьте"
        case .caution: "Осторожно"
        }
    }

    private var color: Color {
        switch risk {
        case .safe: Color(red: 0.15, green: 0.7, blue: 0.45)
        case .review: .orange
        case .caution: .red
        }
    }
}

/// Строка находки: флажок, иконка, имя, путь, пояснение и размер. Контекстное меню — Finder и исключения.
struct ItemRow: View {
    @Environment(AppModel.self) private var model
    let item: JunkItem
    var symbol: String = "folder.fill"
    let toggle: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Toggle("", isOn: Binding(get: { item.selected }, set: { _ in toggle() }))
                .toggleStyle(.checkbox)
                .labelsHidden()
            FileIcon(url: item.url, bundleID: item.bundleID, symbol: symbol, size: 30)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(item.title).font(.system(size: 13, weight: .medium)).lineLimit(1).truncationMode(.middle)
                    if item.risk != .safe { RiskBadge(risk: item.risk) }
                    if item.method == .admin { Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(.secondary).help("Нужен пароль администратора") }
                    if item.method == .trash { Image(systemName: "trash").font(.system(size: 10)).foregroundStyle(.secondary).help("Отправится в Корзину — можно вернуть") }
                }
                Text(item.detail ?? Format.path(item.url, home: model.home))
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if let note = item.note {
                    Label(note, systemImage: "info.circle")
                        .font(.system(size: 10.5))
                        .foregroundStyle(.orange)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 8)
            if let m = item.modified {
                Text(Format.relative(m)).font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1)
            }
            Text(Format.bytes(item.size))
                .font(.system(size: 12.5, weight: .semibold))
                .monospacedDigit()
                .frame(minWidth: 70, alignment: .trailing)
        }
        .padding(.vertical, 7)
        .padding(.horizontal, 12)
        .contentShape(Rectangle())
        .onTapGesture(perform: toggle)
        .contextMenu {
            Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([item.targets.first ?? item.url]) }
            Button("Никогда не трогать") { model.ignore(item.url) }
        }
    }
}

/// Плавающая стеклянная панель действия внизу экрана.
struct ActionBar<Leading: View>: View {
    let title: String
    let subtitle: String?
    let action: String
    var tint: Color = .accentColor
    var disabled = false
    @ViewBuilder var leading: Leading
    let perform: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            leading
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 15, weight: .semibold)).monospacedDigit().contentTransition(.numericText())
                if let subtitle { Text(subtitle).font(.system(size: 11.5)).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 12)
            Button(action: perform) {
                Text(action).font(.system(size: 14, weight: .semibold)).padding(.horizontal, 10)
            }
            .buttonStyle(.glassProminent)
            .tint(tint)
            .controlSize(.extraLarge)
            .disabled(disabled)
        }
        .padding(.leading, 20)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .glassEffect(.regular, in: .capsule)
        .padding(.horizontal, 24)
        .padding(.bottom, 18)
    }
}

extension ActionBar where Leading == EmptyView {
    init(title: String, subtitle: String?, action: String, tint: Color = .accentColor, disabled: Bool = false, perform: @escaping () -> Void) {
        self.init(title: title, subtitle: subtitle, action: action, tint: tint, disabled: disabled, leading: { EmptyView() }, perform: perform)
    }
}

/// Стартовый экран модуля: крупный символ, объяснение и кнопка.
struct ModuleHero: View {
    let module: Module
    let headline: String
    let text: String
    let button: String
    var busy: String?
    let action: () -> Void

    var body: some View {
        VStack(spacing: 22) {
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [module.tint.opacity(0.45), .clear], center: .center, startRadius: 10, endRadius: 120))
                    .frame(width: 240, height: 240)
                Image(systemName: module.symbol)
                    .font(.system(size: 76, weight: .medium))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, module.palette[1])
                    .shadow(color: module.tint.opacity(0.6), radius: 20)
                    .symbolEffect(.breathe, options: .repeating, isActive: busy != nil)
            }
            VStack(spacing: 8) {
                Text(headline).font(.system(size: 28, weight: .bold))
                Text(text).font(.system(size: 14)).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 520)
            }
            if let busy {
                VStack(spacing: 10) {
                    ProgressView().controlSize(.large)
                    Text(busy).font(.system(size: 12.5)).foregroundStyle(.secondary).monospacedDigit().contentTransition(.numericText())
                }
                .frame(height: 70)
            } else {
                Button(action: action) {
                    Text(button).font(.system(size: 15, weight: .semibold)).padding(.horizontal, 26).padding(.vertical, 4)
                }
                .buttonStyle(.glassProminent)
                .tint(module.tint)
                .controlSize(.extraLarge)
                .frame(height: 70)
            }
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Шапка модуля: заголовок, пояснение и кнопка повторного сканирования.
struct ModuleHeader<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 24, weight: .bold))
                Text(subtitle).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer()
            trailing
        }
        .padding(.horizontal, 28)
        .padding(.top, 22)
        .padding(.bottom, 12)
    }
}

/// Карточка-подложка для содержимого (не стекло: стекло — для элементов управления поверх).
struct ContentCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.background.opacity(0.62)))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(.primary.opacity(0.06)))
    }
}

/// Кольцевой индикатор (память, диск).
struct RingGauge: View {
    let fraction: Double
    let colors: [Color]
    var lineWidth: CGFloat = 14

    var body: some View {
        ZStack {
            Circle().stroke(.primary.opacity(0.08), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0.001, min(1, fraction)))
                .stroke(AngularGradient(colors: colors + [colors[0]], center: .center), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .shadow(color: colors[0].opacity(0.5), radius: 8)
        }
        .animation(.smooth(duration: 0.8), value: fraction)
    }
}
