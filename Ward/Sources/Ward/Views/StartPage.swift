import SwiftUI

/// Новая вкладка: избранное и что защита сделала для вас.
struct StartPage: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    let browser: BrowserState

    private let columns = [GridItem(.adaptive(minimum: 104, maximum: 120), spacing: 18)]

    var body: some View {
        ScrollView {
            VStack(spacing: 34) {
                Spacer(minLength: 30)
                VStack(spacing: 8) {
                    Image(systemName: browser.isPrivate ? "eye.slash.circle.fill" : "shield.lefthalf.filled")
                        .font(.system(size: 46))
                        .foregroundStyle(browser.isPrivate ? Color.purple.gradient : Color.green.gradient)
                    Text(browser.isPrivate ? "Частное окно" : "Ward")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                    Text(browser.isPrivate
                         ? "Cookie, история и кэш этого окна не сохраняются: закроете окно — всё исчезнет. Защита от слежки работает как обычно."
                         : "Браузер, который не даёт за вами следить.")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 520)
                }

                if !browser.isPrivate { statsRow }
                blockerStatus

                if !model.favorites.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Панель закладок").font(.headline).foregroundStyle(.secondary)
                        LazyVGrid(columns: columns, spacing: 18) {
                            ForEach(model.favorites) { fav in
                                FavoriteTile(favorite: fav) { if let url = fav.url { browser.selected?.load(url) } }
                            }
                        }
                    }
                    .frame(maxWidth: 640)
                }
                Spacer(minLength: 20)
            }
            .padding(.horizontal, 40)
            .frame(maxWidth: .infinity)
        }
        .background(.background)
    }

    private var statsRow: some View {
        VStack(spacing: 12) {
            HStack(spacing: 14) {
                StatCard(value: Format.count(model.stats.today), title: "сегодня", caption: "трекеров и рекламы остановлено", tint: .green)
                StatCard(value: Format.count(model.stats.total), title: "всего", caption: "с \(model.stats.since.formatted(.dateTime.day().month(.wide)))", tint: .teal)
                StatCard(value: Format.count(model.stats.cleanedLinks), title: "ссылок", caption: "очищено от меток слежки", tint: .indigo)
            }
            if model.stats.consentsRejected > 0 || model.stats.sponsorSeconds > 0 || model.stats.phishingBlocked > 0 {
                HStack(spacing: 18) {
                    if model.stats.consentsRejected > 0 {
                        Label("\(Format.count(model.stats.consentsRejected)) \(Format.plural(model.stats.consentsRejected, "отказ", "отказа", "отказов")) от cookie", systemImage: "hand.raised").foregroundStyle(.secondary)
                    }
                    if model.stats.sponsorSeconds > 0 {
                        Label("\(Int(model.stats.sponsorSeconds / 60)) мин рекламы в роликах пропущено", systemImage: "forward.end").foregroundStyle(.secondary)
                    }
                    if model.stats.phishingBlocked > 0 {
                        Label("\(Format.count(model.stats.phishingBlocked)) \(Format.plural(model.stats.phishingBlocked, "подделка остановлена", "подделки остановлено", "подделок остановлено"))", systemImage: "theatermasks").foregroundStyle(.secondary)
                    }
                }
                .font(.callout)
            }
            Button { openWindow(id: "dashboard") } label: {
                Label("Вся статистика защиты", systemImage: "chart.line.uptrend.xyaxis")
            }
            .buttonStyle(.glass)
            .padding(.top, 4)
        }
        .frame(maxWidth: 640)
    }

    @ViewBuilder
    private var blockerStatus: some View {
        switch model.blocker.phase {
        case .preparing(let text):
            HStack(spacing: 10) {
                ProgressView().controlSize(.small)
                Text("Готовлю защиту: \(text.lowercased())").foregroundStyle(.secondary)
            }
        case .failed(let text):
            Label(text, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
        case .ready:
            if let report = model.blocker.report {
                Text("Работает \(Format.count(report.rules)) правил из \(report.lists) \(Format.plural(report.lists, "списка", "списков", "списков")) фильтров")
                    .font(.callout).foregroundStyle(.tertiary)
            }
        case .idle:
            EmptyView()
        }
    }
}

struct StatCard: View {
    let value: String
    let title: String
    let caption: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(value).font(.system(size: 30, weight: .semibold, design: .rounded)).foregroundStyle(tint)
                    .contentTransition(.numericText())
                Text(title).font(.headline).foregroundStyle(.secondary)
            }
            Text(caption).font(.caption).foregroundStyle(.secondary).lineLimit(2)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct FavoriteTile: View {
    @Environment(AppModel.self) private var model
    let favorite: Bookmark
    let open: () -> Void
    @State private var hover = false

    var body: some View {
        Button(action: open) {
            VStack(spacing: 8) {
                SiteTile(host: favorite.url?.host ?? "", size: 54)
                    .shadow(color: .black.opacity(hover ? 0.2 : 0.08), radius: hover ? 8 : 3, y: 2)
                Text(favorite.title).font(.callout).lineLimit(1).foregroundStyle(.primary)
            }
            .frame(width: 104)
            .scaleEffect(hover ? 1.04 : 1)
            .animation(.snappy(duration: 0.18), value: hover)
        }
        .buttonStyle(.plain)
        .onHover { hover = $0 }
        .contextMenu {
            Button("Удалить из закладок") { model.removeBookmark(favorite.id) }
        }
        .help(favorite.url?.absoluteString ?? "")
    }
}
