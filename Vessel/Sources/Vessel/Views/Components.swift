import SwiftUI

/// Обложка дистрибутива — своя, без логотипов: градиент цвета проекта, буквы в стекле и знак семейства.
struct DistroCover: View {
    let distro: Distro
    var compact = false

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            ZStack {
                LinearGradient(colors: [distro.color, distro.color.mix(with: .black, by: 0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)
                RadialGradient(colors: [.white.opacity(0.35), .clear], center: .init(x: 0.25, y: 0.2), startRadius: 0, endRadius: s * 0.9)
                Image(systemName: distro.family.symbol)
                    .font(.system(size: s * 0.9))
                    .foregroundStyle(.white.opacity(0.07))
                    .offset(x: geo.size.width * 0.28, y: geo.size.height * 0.18)
                Text(distro.monogram)
                    .font(.system(size: s * (distro.monogram.count > 1 ? 0.3 : 0.38), weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: s * 0.56, height: s * 0.56)
                    .glassEffect(.regular.tint(distro.color.opacity(0.35)), in: .rect(cornerRadius: s * 0.15))
                    .shadow(color: .black.opacity(0.25), radius: s * 0.05, y: s * 0.02)
            }
        }
    }
}

/// Скриншот с Commons; пока грузится или если не загрузился — обложка.
struct ScreenshotImage: View {
    let title: String?
    let distro: Distro
    var fill = true
    @State private var image: NSImage?

    var body: some View {
        ZStack {
            DistroCover(distro: distro)
            if let image {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: fill ? .fill : .fit)
                    .transition(.opacity)
            }
        }
        .clipped()
        .task(id: title) {
            image = ScreenshotImages.cached(title)
            guard let title else { return }
            if let hit = ScreenshotImages.cached(title) { image = hit; return }
            let loaded = await ScreenshotImages.image(title)
            withAnimation(.easeOut(duration: 0.35)) { image = loaded }
        }
    }
}

struct TagChip: View {
    let tag: Tag
    var selected = false
    var small = false

    var body: some View {
        Label(tag.title, systemImage: tag.symbol)
            .font(.system(size: small ? 10 : 12, weight: .semibold))
            .labelStyle(.titleAndIcon)
            .padding(.horizontal, small ? 7 : 11)
            .padding(.vertical, small ? 3 : 6)
            .foregroundStyle(selected ? .white : tag.tint)
            .background(selected ? tag.tint : tag.tint.opacity(0.13), in: Capsule())
    }
}

struct Pill: View {
    let text: String
    var symbol: String?
    var tint: Color = .secondary

    var body: some View {
        HStack(spacing: 4) {
            if let symbol { Image(systemName: symbol) }
            Text(text)
        }
        .font(.system(size: 11, weight: .medium))
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(tint.opacity(0.12), in: Capsule())
    }
}

/// Заголовок экрана: крупно, с подзаголовком и местом для кнопок справа.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .bottom, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.system(size: 34, weight: .bold, design: .rounded))
                Text(subtitle).font(.system(size: 14)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            trailing
        }
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String, subtitle: String) {
        self.init(title: title, subtitle: subtitle) { EmptyView() }
    }
}

/// Строки, которые переносятся, когда не помещаются (фильтры, теги).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width { y += row + spacing; x = 0; row = 0 }
            x += size.width + spacing
            row = max(row, size.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, width), height: y + row)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, row: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX { y += row + spacing; x = bounds.minX; row = 0 }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            row = max(row, size.height)
        }
    }
}

/// Живой фон: медленно переливающаяся сетка цветов. Под стеклом должно быть что-то живое.
struct AuroraBackground: View {
    var tint: Color = Color(red: 0.98, green: 0.72, blue: 0.18)
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 20, paused: DemoUI.isOn)) { context in
            let t = DemoUI.isOn ? 2.0 : context.date.timeIntervalSinceReferenceDate / 9
            let base: Color = scheme == .dark ? Color(red: 0.05, green: 0.06, blue: 0.13) : Color(red: 0.96, green: 0.95, blue: 0.99)
            let dark = scheme == .dark
            let cx = Float(0.5 + 0.18 * sin(t)), cy = Float(0.5 + 0.14 * cos(t * 0.8))
            let points: [SIMD2<Float>] = [
                SIMD2(0, 0), SIMD2(0.5, 0), SIMD2(1, 0),
                SIMD2(0, 0.5), SIMD2(cx, cy), SIMD2(1, 0.5),
                SIMD2(0, 1), SIMD2(0.5, 1), SIMD2(1, 1),
            ]
            let colors: [Color] = [
                base, tint.opacity(dark ? 0.28 : 0.22), base,
                Color.indigo.opacity(dark ? 0.35 : 0.18), base, Color.teal.opacity(dark ? 0.22 : 0.15),
                base, Color.purple.opacity(dark ? 0.25 : 0.14), base,
            ]
            MeshGradient(width: 3, height: 3, points: points, colors: colors)
        }
        .ignoresSafeArea()
    }
}

/// Карточка дистрибутива для сеток каталога.
struct DistroCard: View {
    let distro: Distro
    @State private var hover = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScreenshotImage(title: distro.screenshots.first, distro: distro)
                .frame(height: 150)
                .overlay(alignment: .topTrailing) {
                    if !distro.runsHere {
                        Pill(text: "только Intel", symbol: "cpu", tint: .white)
                            .background(.black.opacity(0.45), in: Capsule())
                            .padding(10)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    Text(distro.monogram)
                        .font(.system(size: 15, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(distro.color.gradient, in: RoundedRectangle(cornerRadius: 10))
                        .shadow(color: .black.opacity(0.3), radius: 6, y: 2)
                        .padding(10)
                }
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(distro.name).font(.system(size: 15, weight: .semibold))
                    Spacer()
                    Text(distro.family.short).font(.system(size: 10, weight: .medium)).foregroundStyle(.secondary)
                }
                Text(distro.tagline).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(2, reservesSpace: true)
                HStack(spacing: 5) {
                    ForEach(Array(Tag.allCases.filter { distro.tags.contains($0) }.prefix(2))) { TagChip(tag: $0, small: true) }
                }
            }
            .padding(12)
        }
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .scaleEffect(hover ? 1.015 : 1)
        .shadow(color: distro.color.opacity(hover ? 0.35 : 0), radius: 18, y: 8)
        .animation(.spring(duration: 0.3), value: hover)
        .onHover { hover = $0 }
    }
}

struct EmptyHint: View {
    let title: String
    let symbol: String
    let text: String

    var body: some View {
        ContentUnavailableView(title, systemImage: symbol, description: Text(text))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }
}
