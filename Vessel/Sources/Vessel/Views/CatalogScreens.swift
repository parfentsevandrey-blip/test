import SwiftUI

private let grid = [GridItem(.adaptive(minimum: 230, maximum: 320), spacing: 18)]

/// «Горячие»: о чём сейчас говорят — большие карточки с причиной.
struct HotView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "Горячие", subtitle: "Системы, о которых сейчас говорят: свежие выпуски, новые рабочие столы и громкие идеи.")
                let hot = Catalog.hot
                if let first = hot.first {
                    NavigationLink(value: first.id) { HotHero(distro: first, big: true) }.buttonStyle(.plain)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 360), spacing: 18)], spacing: 18) {
                    ForEach(hot.dropFirst()) { d in
                        NavigationLink(value: d.id) { HotHero(distro: d, big: false) }.buttonStyle(.plain)
                    }
                }
                Text("Новые выпуски Vessel находит сам: каталог хранит правило поиска образа, а не ссылку, поэтому всегда ставится последняя версия.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            .padding(28)
            .frame(maxWidth: 1180)
            .frame(maxWidth: .infinity)
        }
    }
}

private struct HotHero: View {
    let distro: Distro
    let big: Bool
    @State private var hover = false

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            ScreenshotImage(title: distro.screenshots.first, distro: distro)
                .frame(height: big ? 360 : 230)
                .scaleEffect(hover ? 1.03 : 1)
            LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
            HStack(alignment: .bottom, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Горячее", systemImage: "flame.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(.orange.gradient, in: Capsule())
                    Text(distro.name).font(.system(size: big ? 40 : 24, weight: .bold, design: .rounded)).foregroundStyle(.white)
                    Text(distro.hot ?? distro.tagline)
                        .font(.system(size: big ? 15 : 12, weight: .medium))
                        .foregroundStyle(.white.opacity(0.88))
                        .lineLimit(2)
                }
                Spacer()
                if !distro.runsHere {
                    Pill(text: "только Intel", symbol: "cpu", tint: .white).background(.black.opacity(0.4), in: Capsule())
                }
            }
            .padding(big ? 26 : 18)
        }
        .clipShape(RoundedRectangle(cornerRadius: 26))
        .overlay(RoundedRectangle(cornerRadius: 26).strokeBorder(.white.opacity(0.18)))
        .shadow(color: distro.color.opacity(hover ? 0.5 : 0.25), radius: hover ? 28 : 16, y: 10)
        .animation(.spring(duration: 0.4), value: hover)
        .onHover { hover = $0 }
    }
}

/// «Популярные»: рейтинг от самых распространённых.
struct PopularView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Популярные", subtitle: "От самых распространённых систем к нишевым. Те, что не запускаются на Apple Silicon, остались в списке — с подсказкой, чем их заменить.")
                VStack(spacing: 10) {
                    ForEach(model.filtered(Catalog.popular)) { d in
                        NavigationLink(value: d.id) { PopularRow(distro: d) }.buttonStyle(.plain)
                    }
                }
            }
            .padding(28)
            .frame(maxWidth: 980)
            .frame(maxWidth: .infinity)
        }
    }
}

private struct PopularRow: View {
    let distro: Distro
    @State private var hover = false

    var body: some View {
        HStack(spacing: 16) {
            Text("\(distro.rank)")
                .font(.system(size: distro.rank <= 3 ? 30 : 22, weight: .heavy, design: .rounded))
                .foregroundStyle(distro.rank <= 3 ? AnyShapeStyle(distro.color.gradient) : AnyShapeStyle(.secondary))
                .frame(width: 44)
            ScreenshotImage(title: distro.screenshots.first, distro: distro)
                .frame(width: 120, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(distro.name).font(.system(size: 16, weight: .semibold))
                    Pill(text: distro.family.short, tint: distro.color)
                    if distro.hot != nil { Image(systemName: "flame.fill").foregroundStyle(.orange).font(.system(size: 12)) }
                }
                Text(distro.tagline).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                Text(distro.facts.desktop).font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1)
            }
            Spacer()
            if distro.runsHere {
                Label("Apple Silicon", systemImage: "checkmark.circle.fill").font(.system(size: 11, weight: .medium)).foregroundStyle(.green)
            } else {
                Label("только Intel", systemImage: "xmark.circle").font(.system(size: 11, weight: .medium)).foregroundStyle(.secondary)
            }
            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
        }
        .padding(12)
        .glassEffect(hover ? .regular.tint(distro.color.opacity(0.15)) : .regular, in: .rect(cornerRadius: 18))
        .onHover { hover = $0 }
    }
}

/// Фильтры: теги, «только для этого Mac» и поиск (в панели окна).
struct FilterBar: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 10) {
            FlowLayout(spacing: 8) {
                ForEach(Tag.allCases) { tag in
                    Button {
                        withAnimation(.snappy) {
                            if model.tags.contains(tag) { model.tags.remove(tag) } else { model.tags.insert(tag) }
                        }
                    } label: { TagChip(tag: tag, selected: model.tags.contains(tag)) }
                    .buttonStyle(.plain)
                }
            }
            HStack(spacing: 14) {
                Toggle("Только те, что запустятся на этом Mac", isOn: $model.onlyRunnable).toggleStyle(.switch).controlSize(.small)
                if !model.tags.isEmpty {
                    Button("Сбросить фильтры") { withAnimation(.snappy) { model.tags = [] } }.buttonStyle(.link)
                }
            }
            .font(.system(size: 12))
        }
    }
}

/// Все системы: фильтры сверху, ниже — разделы по семействам.
struct CatalogView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "Все системы", subtitle: "\(Catalog.all.count) дистрибутивов Linux, \(Catalog.all.filter(\.runsHere).count) из них запускаются на Apple Silicon. Отберите по вкусу.")
                FilterBar()
                let any = Family.allCases.contains { f in !model.filtered(Catalog.popular.filter { d in d.family == f }).isEmpty }
                if !any {
                    EmptyHint(title: "Ничего не нашлось", symbol: "line.3.horizontal.decrease.circle", text: "Снимите часть фильтров или измените запрос.")
                }
                ForEach(Family.allCases) { family in
                    let list = model.filtered(Catalog.popular.filter { $0.family == family })
                    if !list.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 8) {
                                Image(systemName: family.symbol).foregroundStyle(.secondary)
                                Text(family.title).font(.system(size: 20, weight: .bold, design: .rounded))
                                Text("\(list.count)").font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary)
                                Spacer()
                                Button("Всё семейство") { model.screen = .family(family) }.buttonStyle(.link).font(.system(size: 12))
                            }
                            LazyVGrid(columns: grid, spacing: 18) {
                                ForEach(list) { d in NavigationLink(value: d.id) { DistroCard(distro: d) }.buttonStyle(.plain) }
                            }
                        }
                    }
                }
            }
            .padding(28)
            .frame(maxWidth: 1240)
            .frame(maxWidth: .infinity)
        }
    }
}

/// Раздел семейства: что общего у систем и их сетка.
struct FamilyView: View {
    @Environment(AppModel.self) private var model
    let family: Family

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack(alignment: .top, spacing: 18) {
                    Image(systemName: family.symbol)
                        .font(.system(size: 30, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 68, height: 68)
                        .background(familyTint.gradient, in: RoundedRectangle(cornerRadius: 18))
                    ScreenHeader(title: family.title, subtitle: family.about)
                }
                FilterBar()
                let list = model.filtered(Catalog.popular.filter { $0.family == family })
                if list.isEmpty {
                    EmptyHint(title: "Ничего не нашлось", symbol: "line.3.horizontal.decrease.circle", text: "Снимите часть фильтров.")
                }
                LazyVGrid(columns: grid, spacing: 18) {
                    ForEach(list) { d in NavigationLink(value: d.id) { DistroCard(distro: d) }.buttonStyle(.plain) }
                }
            }
            .padding(28)
            .frame(maxWidth: 1240)
            .frame(maxWidth: .infinity)
        }
    }

    private var familyTint: Color {
        Catalog.popular.first { $0.family == family }?.color ?? .accentColor
    }
}
