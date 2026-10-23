import SwiftUI

/// История посещений: поиск, удаление записей, открытие. По желанию — только после Touch ID.
struct HistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var failed = false

    struct DayGroup: Identifiable {
        let day: String
        let entries: [HistoryEntry]
        var id: String { day }
    }

    private var groups: [DayGroup] {
        let q = query.lowercased()
        let filtered = model.history.entries.filter { q.isEmpty || $0.title.lowercased().contains(q) || $0.url.absoluteString.lowercased().contains(q) }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateStyle = .full
        var order: [String] = []
        var map: [String: [HistoryEntry]] = [:]
        for e in filtered.prefix(1500) {
            let day = Calendar.current.isDateInToday(e.visited) ? "Сегодня" : Calendar.current.isDateInYesterday(e.visited) ? "Вчера" : formatter.string(from: e.visited)
            if map[day] == nil { order.append(day) }
            map[day, default: []].append(e)
        }
        return order.map { DayGroup(day: $0, entries: map[$0] ?? []) }
    }

    var body: some View {
        Group {
            if model.historyUnlocked || !model.prefs.protectHistory {
                list
            } else {
                locked
            }
        }
        .frame(minWidth: 560, minHeight: 420)
        .task { _ = await model.unlockHistory() }
    }

    private var locked: some View {
        VStack(spacing: 14) {
            Image(systemName: "touchid").font(.system(size: 48)).foregroundStyle(.pink)
            Text("История защищена").font(.title2.weight(.semibold))
            Text(failed ? "Не получилось подтвердить. Попробуйте ещё раз." : "Подтвердите Touch ID или паролем Mac.").foregroundStyle(.secondary)
            Button("Открыть историю") { Task { failed = !(await model.unlockHistory()) } }
                .buttonStyle(.glassProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var list: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Искать в истории", text: $query).textFieldStyle(.plain)
                Spacer()
                Button("Очистить историю…", role: .destructive) { model.clearHistory() }
                    .disabled(model.history.entries.isEmpty)
            }
            .padding(12)
            Divider()
            if model.history.entries.isEmpty {
                ContentUnavailableView(model.prefs.keepHistory ? "История пуста" : "История не ведётся",
                                       systemImage: "clock",
                                       description: Text(model.prefs.keepHistory ? "Здесь появятся сайты, которые вы открывали." : "Включите «Вести историю» в настройках, вкладка «Данные»."))
            } else {
                List {
                    ForEach(groups) { group in
                        Section(group.day) {
                            ForEach(group.entries) { entry in
                                HStack(spacing: 10) {
                                    SiteTile(host: entry.url.host ?? "", size: 18)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(entry.title.isEmpty ? (entry.url.host ?? entry.url.absoluteString) : entry.title).lineLimit(1)
                                        Text(entry.url.absoluteString).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                    Spacer()
                                    Text(entry.visited.formatted(date: .omitted, time: .shortened)).font(.caption).foregroundStyle(.tertiary)
                                }
                                .contentShape(Rectangle())
                                .onTapGesture(count: 2) { AppModel.shared.open(entry.url) }
                                .contextMenu {
                                    Button("Открыть") { AppModel.shared.open(entry.url) }
                                    Button("Удалить из истории") { model.removeHistory(entry) }
                                    Button("Забыть этот сайт") { Task { await model.forget(site: Hosts.site(entry.url.host ?? "")) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
