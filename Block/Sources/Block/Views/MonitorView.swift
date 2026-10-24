import SwiftUI

/// Наблюдение: куда ходят процессы Apple и что из этого подозрительно.
struct MonitorView: View {
    @Environment(AppModel.self) private var model
    @State private var filter: Filter = DemoUI.isOn ? .all : .suspicious

    enum Filter: String, CaseIterable, Identifiable {
        case suspicious, all, blocked
        var id: String { rawValue }
        var title: String {
            switch self {
            case .suspicious: "Подозрительное"
            case .all: "Все соединения"
            case .blocked: "Закрыто"
            }
        }
    }

    private var groups: [(process: String, items: [Sighting])] {
        let list: [Sighting] = switch filter {
        case .suspicious: model.monitor.observations.filter { $0.verdict.suspicious && !model.isBlocked($0) && !model.state.allowedObservations.contains($0.id) }
        case .all: model.monitor.observations
        case .blocked: model.monitor.observations.filter { model.isBlocked($0) }
        }
        let byProcess = Dictionary(grouping: list, by: \.process)
        return byProcess.keys.sorted { a, b in
            let va = byProcess[a]?.map(\.verdict).min() ?? .unknown, vb = byProcess[b]?.map(\.verdict).min() ?? .unknown
            return va == vb ? a < b : va < vb
        }.map { ($0, byProcess[$0] ?? []) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Наблюдение", subtitle: "Куда ходят в сеть процессы Apple на этом Mac. Телеметрия, реклама и подсказки помечены; закрыть адрес или остановить службу — одной кнопкой.") {
                    Toggle("Следить", isOn: Binding(get: { model.state.monitorOn }, set: { model.setMonitor($0) }))
                        .toggleStyle(.switch)
                }
                summary
                if !model.pendingHosts.isEmpty { pendingBanner }
                controls
                if groups.isEmpty {
                    ContentUnavailableView(filter == .suspicious ? "Подозрительного не видно" : "Пока пусто",
                                           systemImage: filter == .suspicious ? "checkmark.shield" : "dot.radiowaves.left.and.right",
                                           description: Text(model.state.monitorOn ? "Block смотрит соединения каждые несколько секунд." : "Включите наблюдение."))
                        .frame(maxWidth: .infinity).padding(.top, 40)
                }
                ForEach(groups, id: \.process) { group in
                    ProcessCard(process: group.process, items: group.items)
                }
                footnote
            }
            .padding(28)
            .frame(maxWidth: 1040, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }

    private var summary: some View {
        let all = model.monitor.observations
        let day = all.filter { $0.lastSeen > Date().addingTimeInterval(-86_400) }
        return HStack(spacing: 14) {
            Tile(value: "\(Set(day.map(\.process)).count)", title: "процессов Apple в сети за сутки", symbol: "cpu", tint: .blue)
            Tile(value: "\(day.count)", title: "адресов за сутки", symbol: "point.3.connected.trianglepath.dotted", tint: .indigo)
            Tile(value: "\(model.suspicious.count)", title: "подозрительных открыто", symbol: "exclamationmark.triangle.fill", tint: .orange)
            Tile(value: "\(all.filter { model.isBlocked($0) }.count)", title: "закрыто", symbol: "lock.fill", tint: .green)
        }
    }

    private var pendingBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "shield.lefthalf.filled.badge.checkmark").font(.system(size: 22)).foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 2) {
                Text("Найдено подозрительных адресов: \(model.pendingHosts.count)").font(.system(size: 13, weight: .semibold))
                Text(model.pendingHosts.prefix(4).joined(separator: " · ")).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Button("Закрыть все") { Task { await model.blockHosts(model.pendingHosts) } }.buttonStyle(.glassProminent)
        }
        .padding(14)
        .glassEffect(.regular.tint(.orange.opacity(0.15)), in: .rect(cornerRadius: 18))
    }

    private var controls: some View {
        HStack(spacing: 12) {
            Picker("", selection: $filter) {
                ForEach(Filter.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(width: 360)
            Spacer()
            Toggle("Закрывать само", isOn: Binding(get: { model.state.monitorAuto }, set: { model.state.monitorAuto = $0; model.state.save() }))
                .toggleStyle(.switch)
                .help(model.state.networkMethod == .veil ? "Через Veil — сразу и без пароля." : "Через hosts — найденное копится, закрывается одним паролем.")
            let open = model.suspicious
            Button("Закрыть всё: \(open.count)") { Task { await model.blockHosts(open.map(\.endpoint)) } }
                .buttonStyle(.glassProminent)
                .disabled(open.isEmpty || model.busy)
        }
    }

    private var footnote: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Как это устроено").font(.system(size: 13, weight: .semibold))
            Text("Соединения показывает системный nettop — помощник с правами администратора не нужен. Имена адресов Block узнаёт сам: заранее разрешает известные адреса Apple и спрашивает обратную запись DNS. Адреса без имени закрыть по имени нельзя — для них есть «Остановить службу». Обновления, сертификаты, нотаризация и время не закрываются никогда. Журнал соединений хранится неделю только на этом Mac.")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

private struct ProcessCard: View {
    @Environment(AppModel.self) private var model
    let process: String
    let items: [Sighting]

    var body: some View {
        let known = AppleKnowledge.process(process)
        let worst = items.map(\.verdict).min() ?? .unknown
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: worst.symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(color(worst).gradient))
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(known?.title ?? process).font(.system(size: 14, weight: .semibold))
                        VerdictBadge(verdict: worst)
                    }
                    Text(items.first?.path.isEmpty == false ? items[0].path : process).font(.system(size: 10, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                if worst != .essential, let first = items.first, !DemoUI.isOn ? model.agentLabel(for: first) != nil : known?.agent != nil {
                    Button("Остановить службу") { Task { await model.stopAgent(for: first) } }.buttonStyle(.glass).controlSize(.small)
                }
            }
            VStack(spacing: 0) {
                ForEach(items) { o in
                    HStack(spacing: 10) {
                        Text(o.endpoint).font(.system(size: 12, design: .monospaced)).lineLimit(1)
                        if o.named && o.endpoint != o.ip { Text(o.ip).font(.system(size: 10, design: .monospaced)).foregroundStyle(.tertiary).lineLimit(1) }
                        if let shared = o.shared {
                            Text("общий адрес Apple: \(shared.joined(separator: ", "))…").font(.system(size: 10)).foregroundStyle(.tertiary).lineLimit(1)
                                .help("По адресу нельзя узнать, какое из имён спрашивал процесс: за ним стоит сеть доставки Apple.")
                        }
                        Spacer()
                        if o.verdict != worst { VerdictBadge(verdict: o.verdict) }
                        Text(bytes(o.bytesIn + o.bytesOut)).font(.system(size: 11).monospacedDigit()).foregroundStyle(.secondary)
                        Text(o.lastSeen, format: .relative(presentation: .named)).font(.system(size: 11)).foregroundStyle(.secondary).frame(width: 110, alignment: .trailing)
                        action(o)
                    }
                    .padding(.vertical, 6)
                    if o.id != items.last?.id { Divider() }
                }
            }
            .padding(.horizontal, 12)
            .background(.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    @ViewBuilder
    private func action(_ o: Sighting) -> some View {
        if model.isBlocked(o) {
            if model.state.observedHosts.contains(o.endpoint) {
                Button("Открыть") { Task { await model.unblockHost(o.endpoint) } }.buttonStyle(.link).font(.system(size: 11))
            } else {
                Label("закрыт", systemImage: "lock.fill").font(.system(size: 11)).foregroundStyle(.green)
            }
        } else if model.canBlock(o) {
            HStack(spacing: 6) {
                Button("Закрыть") { Task { await model.blockHosts([o.endpoint]) } }.buttonStyle(.glass).controlSize(.small)
                if o.verdict.suspicious && !model.state.allowedObservations.contains(o.id) {
                    Button("Разрешить") { model.allow(o) }.buttonStyle(.link).font(.system(size: 11))
                }
            }
        } else {
            Text(o.verdict == .essential ? "не трогается" : "без имени").font(.system(size: 11)).foregroundStyle(.tertiary)
        }
    }

    private func bytes(_ n: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: n, countStyle: .file)
    }
}

struct VerdictBadge: View {
    let verdict: Verdict

    var body: some View {
        Text(verdict.title)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(color(verdict))
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(color(verdict).opacity(0.13), in: Capsule())
    }
}

func color(_ verdict: Verdict) -> Color {
    switch verdict {
    case .ads: .pink
    case .telemetry: .orange
    case .suggestions: .purple
    case .unknown: .gray
    case .service: .blue
    case .essential: .green
    }
}
