import SwiftUI

/// Лента: новые модели лабораторий, свежие GGUF-сборки и выпуски движка llama.cpp.
struct FeedView: View {
    @Environment(AppModel.self) private var model
    @State private var kind: KindFilter = .all

    enum KindFilter: String, CaseIterable, Identifiable {
        case all, ready, mods, release, engine
        var id: String { rawValue }
        var title: String {
            switch self {
            case .all: "Всё"
            case .ready: "Готово к установке"
            case .mods: "Модификации"
            case .release: "Новые модели"
            case .engine: "Движок"
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Лента", subtitle: subtitle) {
                HStack(spacing: 10) {
                    if model.feedLoading { ProgressView().controlSize(.small) }
                    Toggle(isOn: Binding(get: { model.notifyNewModels }, set: { model.notifyNewModels = $0 })) {
                        Label("Уведомлять", systemImage: "bell.badge")
                    }
                    .toggleStyle(.button)
                    .buttonStyle(.glass)
                    .help("Уведомление, когда выходит модель, подходящая этому Mac")
                    Button { Task { await model.refreshFeed() } } label: { Label("Обновить", systemImage: "arrow.clockwise") }
                        .buttonStyle(.glass)
                        .disabled(model.feedLoading)
                }
            }
            // Фильтры строками с переносом: в узком окне не обрезаются.
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(KindFilter.allCases) { k in
                    Button {
                        withAnimation(.snappy) { kind = k }
                    } label: {
                        Text(k.title).font(.system(size: 12, weight: .medium)).padding(.horizontal, 4)
                    }
                    .buttonStyle(.glass)
                    .tint(kind == k ? Section.feed.tint : nil)
                    .background(Capsule().fill(kind == k ? Section.feed.tint.opacity(0.25) : .clear))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 28)
            .padding(.bottom, 12)
            if let error = model.feedError {
                Label(error, systemImage: "wifi.exclamationmark").font(.system(size: 12)).foregroundStyle(.orange)
                    .padding(.horizontal, 28).padding(.bottom, 8).frame(maxWidth: .infinity, alignment: .leading)
            }
            content
        }
        .task {
            await model.refreshFeedIfNeeded()
            try? await Task.sleep(for: .seconds(2.5))
            if !Task.isCancelled { model.markFeedRead() }
        }
    }

    private var subtitle: String {
        let when = model.feed.lastRefresh.map { "Обновлено \(Format.relative($0))" } ?? "Ещё не обновлялась"
        return "Выходы моделей и их сборок на Hugging Face, выпуски llama.cpp на GitHub. \(when), дальше — каждые два часа."
    }

    private var items: [FeedItem] {
        switch kind {
        case .all: model.feed.items
        case .ready: model.feed.items.filter { $0.kind == .ready && !$0.finetune }
        case .mods: model.feed.items.filter { $0.kind == .ready && $0.finetune }
        case .release: model.feed.items.filter { $0.kind == .release }
        case .engine: model.feed.items.filter { $0.kind == .engine }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.feed.items.isEmpty {
            VStack(spacing: 16) {
                NeuralOrb(energy: model.feedLoading ? 1 : 0.3, palette: Section.feed.palette).frame(width: 160, height: 160)
                Text(model.feedLoading ? "Собираю новости…" : "Лента пуста").font(.system(size: 18, weight: .semibold))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    ForEach(groups, id: \.0) { title, list in
                        Text(title).font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary).padding(.top, 8).padding(.leading, 4)
                        ForEach(list) { FeedCard(item: $0, unread: $0.date > model.feed.lastSeen) }
                    }
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 24)
            }
        }
    }

    /// Сегодня / вчера / на этой неделе / раньше.
    private var groups: [(String, [FeedItem])] {
        let cal = Calendar.current
        var result: [(String, [FeedItem])] = []
        func add(_ title: String, _ item: FeedItem) {
            if result.last?.0 == title { result[result.count - 1].1.append(item) } else { result.append((title, [item])) }
        }
        for item in items {
            if cal.isDateInToday(item.date) { add("Сегодня", item) }
            else if cal.isDateInYesterday(item.date) { add("Вчера", item) }
            else if item.date > Date().addingTimeInterval(-7 * 86_400) { add("На этой неделе", item) }
            else if item.date > Date().addingTimeInterval(-31 * 86_400) { add("В этом месяце", item) }
            else { add("Раньше", item) }
        }
        return result
    }
}

struct FeedCard: View {
    @Environment(AppModel.self) private var model
    let item: FeedItem
    let unread: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            icon
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    if unread { Circle().fill(Section.feed.tint).frame(width: 7, height: 7) }
                    Text(kindTitle).font(.system(size: 10.5, weight: .semibold)).foregroundStyle(kindColor)
                    Text("· \(Format.relative(item.date))").font(.system(size: 10.5)).foregroundStyle(.tertiary)
                }
                Text(item.title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                Text(item.subtitle).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(2)
                if !item.notes.isEmpty && item.kind == .engine {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(item.notes, id: \.self) { Text("• " + $0).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(2) }
                    }
                    .padding(.top, 2)
                }
                if item.kind == .release {
                    if item.ggufRepo != nil {
                        Label("Уже есть готовая сборка — можно ставить", systemImage: "checkmark.circle.fill").font(.system(size: 11)).foregroundStyle(.green)
                    } else {
                        Label("Сборка GGUF появится в ленте, как только её выпустят", systemImage: "clock").font(.system(size: 11)).foregroundStyle(.tertiary)
                    }
                }
                if item.kind == .ready && !item.supported {
                    Label("Архитектура «\(item.architecture ?? "")» новее встроенного движка", systemImage: "exclamationmark.octagon").font(.system(size: 11)).foregroundStyle(.red)
                }
            }
            Spacer(minLength: 10)
            VStack(alignment: .trailing, spacing: 8) {
                if let card = model.card(for: item), item.supported {
                    if let fit = model.fit(card) { FitBadge(fit: fit, compact: true) }
                    HStack(spacing: 6) {
                        Button("Подробнее") { model.detailCard = card }.buttonStyle(.glass).controlSize(.small)
                        InstallButton(card: card, prominent: false).controlSize(.small)
                    }
                } else if item.kind == .engine || item.kind == .release {
                    Link(destination: item.url) { Label("Открыть", systemImage: "arrow.up.right") }
                        .font(.system(size: 11.5)).buttonStyle(.glass).controlSize(.small)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.background.opacity(unread ? 0.7 : 0.5)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(unread ? Section.feed.tint.opacity(0.35) : .primary.opacity(0.06)))
    }

    private var kindTitle: String {
        switch item.kind {
        case .ready: item.finetune ? "МОДИФИКАЦИЯ · ГОТОВО К УСТАНОВКЕ" : "ГОТОВО К УСТАНОВКЕ"
        case .release: "НОВАЯ МОДЕЛЬ"
        case .engine: "ДВИЖОК"
        }
    }

    private var kindColor: Color {
        switch item.kind {
        case .ready: Section.library.tint
        case .release: Section.feed.tint
        case .engine: Section.catalog.palette[1]
        }
    }

    @ViewBuilder
    private var icon: some View {
        switch item.kind {
        case .engine:
            Image(systemName: "gearshape.2.fill")
                .font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 42, height: 42)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(LinearGradient(colors: [.gray, Color(white: 0.3)], startPoint: .top, endPoint: .bottom)))
        default:
            ModelAvatar(lab: item.lab ?? item.repo?.split(separator: "/").first.map(String.init) ?? "?", size: 42)
        }
    }
}
