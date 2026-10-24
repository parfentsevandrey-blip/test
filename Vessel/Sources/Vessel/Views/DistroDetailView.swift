import SwiftUI

/// Страница дистрибутива: скриншоты, в чём хорош и в чём слабее, для кого, факты, варианты и установка.
struct DistroDetailView: View {
    @Environment(AppModel.self) private var model
    let distro: Distro
    @State private var shot = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                hero
                if let why = distro.unavailable { unavailableBanner(why) }
                HStack(alignment: .top, spacing: 16) {
                    listCard("В чём хорош", symbol: "hand.thumbsup.fill", tint: .green, items: distro.pros, bullet: "checkmark")
                    listCard("В чём слабее", symbol: "exclamationmark.triangle.fill", tint: .orange, items: distro.cons, bullet: "minus")
                    listCard("Для кого", symbol: "person.2.fill", tint: distro.color, items: distro.audience, bullet: "arrow.right")
                }
                HStack(alignment: .top, spacing: 16) {
                    facts
                    if !distro.editions.isEmpty { editions } else { alternatives }
                }
                if !distro.installTips.isEmpty { tips }
                credits
            }
            .padding(28)
            .frame(maxWidth: 1180)
            .frame(maxWidth: .infinity)
        }
        .background(alignment: .top) {
            // Цвет дистрибутива растекается под стеклом страницы.
            RadialGradient(colors: [distro.color.opacity(0.35), .clear], center: .top, startRadius: 0, endRadius: 700)
                .frame(height: 700)
                .ignoresSafeArea()
                .allowsHitTesting(false)
        }
        .navigationTitle(distro.name)
    }

    // MARK: Шапка со скриншотами

    private var hero: some View {
        VStack(alignment: .leading, spacing: 14) {
            ZStack {
                // Размытый скриншот — фон, поверх него тот же кадр целиком.
                ScreenshotImage(title: distro.screenshots[safe: shot], distro: distro)
                    .blur(radius: 40)
                    .opacity(0.7)
                ScreenshotImage(title: distro.screenshots[safe: shot], distro: distro, fill: false)
                    .padding(18)
                    .shadow(color: .black.opacity(0.35), radius: 20, y: 10)
                    .id(shot)
                    .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.98)), removal: .opacity))
                if distro.screenshots.count > 1 {
                    HStack {
                        arrow("chevron.left") { shot = (shot - 1 + distro.screenshots.count) % distro.screenshots.count }
                        Spacer()
                        arrow("chevron.right") { shot = (shot + 1) % distro.screenshots.count }
                    }
                    .padding(14)
                }
            }
            .frame(height: 430)
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(.white.opacity(0.15)))

            if distro.screenshots.count > 1 {
                HStack(spacing: 10) {
                    ForEach(Array(distro.screenshots.enumerated()), id: \.offset) { i, title in
                        Button { withAnimation(.smooth) { shot = i } } label: {
                            ScreenshotImage(title: title, distro: distro)
                                .frame(width: 110, height: 66)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(i == shot ? distro.color : .clear, lineWidth: 2.5))
                                .opacity(i == shot ? 1 : 0.7)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack(alignment: .center, spacing: 18) {
                DistroCover(distro: distro)
                    .frame(width: 78, height: 78)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                VStack(alignment: .leading, spacing: 5) {
                    Text(distro.name).font(.system(size: 34, weight: .bold, design: .rounded))
                    Text(distro.tagline).font(.system(size: 15, weight: .medium)).foregroundStyle(.secondary)
                    FlowLayout(spacing: 6) {
                        Pill(text: distro.family.title, symbol: distro.family.symbol, tint: distro.color)
                        Pill(text: distro.facts.desktop, symbol: "macwindow")
                        if distro.editions.contains(where: \.live) { Pill(text: "можно попробовать без установки", symbol: "play.circle") }
                        ForEach(Tag.allCases.filter { distro.tags.contains($0) }) { TagChip(tag: $0, small: true) }
                    }
                }
                Spacer(minLength: 12)
                VStack(alignment: .trailing, spacing: 8) {
                    if distro.runsHere {
                        Button {
                            model.installEdition = nil
                            model.installTarget = distro
                        } label: {
                            Label("Установить", systemImage: "arrow.down.circle.fill").font(.system(size: 15, weight: .semibold)).padding(.horizontal, 10).padding(.vertical, 4)
                        }
                        .buttonStyle(.glassProminent)
                        .tint(distro.color)
                        .controlSize(.large)
                        if let e = distro.defaultEdition {
                            Text("\(Format.gb(e.sizeGB)) · \(e.desktop)").font(.system(size: 11)).foregroundStyle(.secondary)
                        }
                    }
                    Link(destination: URL(string: distro.homepage)!) { Label("Сайт проекта", systemImage: "safari") }
                        .font(.system(size: 12))
                }
            }
            Text(distro.summary).font(.system(size: 15)).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
        }
    }

    private func arrow(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button { withAnimation(.smooth) { action() } } label: {
            Image(systemName: symbol).font(.system(size: 16, weight: .bold)).frame(width: 40, height: 40)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
    }

    // MARK: Карточки

    private func listCard(_ title: String, symbol: String, tint: Color, items: [String], bullet: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title, systemImage: symbol).font(.system(size: 14, weight: .semibold)).foregroundStyle(tint)
            ForEach(items, id: \.self) { item in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Image(systemName: bullet).font(.system(size: 10, weight: .bold)).foregroundStyle(tint)
                    Text(item).font(.system(size: 13)).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .glassEffect(.regular.tint(tint.opacity(0.06)), in: .rect(cornerRadius: 20))
    }

    private var facts: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Коротко", systemImage: "info.circle.fill").font(.system(size: 14, weight: .semibold))
            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 7) {
                row("Основа", distro.facts.base)
                row("Пакеты", distro.facts.packages)
                row("Выпуски", distro.facts.releases)
                row("Рабочий стол", distro.facts.desktop)
                row("С года", "\(distro.facts.since)")
                row("Откуда", distro.facts.origin)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    private func row(_ key: String, _ value: String) -> some View {
        GridRow {
            Text(key).font(.system(size: 12)).foregroundStyle(.secondary)
            Text(value).font(.system(size: 12, weight: .medium)).fixedSize(horizontal: false, vertical: true)
        }
    }

    private var editions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Варианты", systemImage: "square.stack.3d.up.fill").font(.system(size: 14, weight: .semibold))
            ForEach(distro.editions) { e in
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(e.title).font(.system(size: 13, weight: .semibold))
                        Text([e.desktop, Format.gb(e.sizeGB), e.live ? "живой образ" : nil, e.note].compactMap { $0 }.joined(separator: " · "))
                            .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Button("Установить") { model.installEdition = e.id; model.installTarget = distro }
                        .buttonStyle(.glass).controlSize(.small)
                }
                if e.id != distro.editions.last?.id { Divider() }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    private func unavailableBanner(_ why: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "cpu").font(.system(size: 26)).foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 4) {
                Text("Не запустится на Apple Silicon").font(.system(size: 15, weight: .semibold))
                Text(why).font(.system(size: 13)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular.tint(.orange.opacity(0.12)), in: .rect(cornerRadius: 20))
    }

    private var alternatives: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Что поставить вместо", systemImage: "arrow.triangle.swap").font(.system(size: 14, weight: .semibold))
            ForEach(distro.alternatives.compactMap(Catalog.distro)) { alt in
                NavigationLink(value: alt.id) {
                    HStack(spacing: 10) {
                        DistroCover(distro: alt).frame(width: 36, height: 36).clipShape(RoundedRectangle(cornerRadius: 9))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(alt.name).font(.system(size: 13, weight: .semibold))
                            Text(alt.tagline).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    private var tips: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Как пройдёт установка", systemImage: "wand.and.stars").font(.system(size: 14, weight: .semibold))
            ForEach(Array(distro.installTips.enumerated()), id: \.offset) { i, tip in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text("\(i + 1)").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 20, height: 20).background(distro.color.gradient, in: Circle())
                    Text(tip).font(.system(size: 13)).fixedSize(horizontal: false, vertical: true)
                }
            }
            Text("Vessel скачает образ с сервера проекта, проверит контрольную сумму, создаст машину и запустит установку. Ваш Mac не затрагивается: всё происходит на диске машины.")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    private var credits: some View {
        HStack(spacing: 6) {
            Text("Скриншоты: Wikimedia Commons, свободные лицензии.")
            if let title = distro.screenshots[safe: shot] {
                Link("Автор и лицензия этого снимка", destination: Commons.page(title))
            }
        }
        .font(.system(size: 11))
        .foregroundStyle(.secondary)
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
