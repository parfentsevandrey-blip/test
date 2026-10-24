import AppKit
import SwiftUI

/// Сетевой замок: в интернет выходит только то, что разрешил человек.
struct LockView: View {
    @Environment(AppModel.self) private var model
    @State private var filter: Filter = .all
    @State private var newAddress = ""

    enum Filter: String, CaseIterable, Identifiable {
        case all, allowed, denied, ask, system
        var id: String { rawValue }
        var title: String {
            switch self {
            case .all: "Все"
            case .allowed: "Разрешено"
            case .denied: "Запрещено"
            case .ask: "Не решено"
            case .system: "Службы macOS"
            }
        }
    }

    private var lock: LockSettings { model.state.lock }

    private var apps: [NetAppRule] { rules(for: filter) }

    private func rules(for filter: Filter) -> [NetAppRule] {
        lock.apps.values.filter { rule in
            switch filter {
            case .all: !rule.app.isSystem && !rule.app.isApple
            case .allowed: rule.decision == .allow
            case .denied: rule.decision == .deny
            case .ask: rule.decision == .ask && !rule.app.isSystem && !rule.app.isApple
            case .system: rule.app.isSystem || rule.app.isApple
            }
        }.sorted { $0.lastSeen > $1.lastSeen }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Замок", subtitle: "В интернет выходит только то, что разрешили вы. Остальное — и программы, и службы macOS — остаётся внутри Mac.") {
                    EmptyView()
                }
                hero
                if lock.on && !model.lock.proxyOK { proxyWarning }
                tiles
                appsSection
                HStack(alignment: .top, spacing: 16) {
                    feedSection
                    directSection
                }
                terminal
                footnote
            }
            .padding(28)
            .frame(maxWidth: 1040, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Состояние

    private var hero: some View {
        let open = model.lock.gateOpen
        let tint: Color = !lock.on ? .gray : (open ? .orange : .green)
        return HStack(spacing: 22) {
            ZStack {
                Circle().fill(tint.opacity(0.15))
                Circle().strokeBorder(tint.opacity(0.5), lineWidth: 3)
                Image(systemName: !lock.on ? "lock.open" : (open ? "lock.open.fill" : "lock.fill"))
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(tint.gradient)
                    .contentTransition(.symbolEffect(.replace))
            }
            .frame(width: 104, height: 104)
            VStack(alignment: .leading, spacing: 6) {
                Text(!lock.on ? "Замок выключен" : (open ? "Сеть открыта на время" : "Замок закрыт"))
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                Text(statusLine).font(.system(size: 13)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if lock.on, let until = model.lock.gateUntil, open {
                    HStack(spacing: 4) {
                        Text("Закроется сама через")
                        Text(until, style: .timer).monospacedDigit()
                    }
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(.orange)
                }
                HStack(spacing: 10) {
                    if lock.on {
                        if open {
                            Button { model.lock.closeGate() } label: { Label("Закрыть сейчас", systemImage: "lock.fill") }
                                .buttonStyle(.glassProminent).tint(.green)
                        } else {
                            Menu {
                                Button("На 15 минут") { model.lock.openGate(minutes: 15) }
                                Button("На час") { model.lock.openGate(minutes: 60) }
                                Button("На 3 часа") { model.lock.openGate(minutes: 180) }
                            } label: { Label("Открыть сеть на время", systemImage: "lock.open") }
                                .menuStyle(.button).buttonStyle(.glass).fixedSize()
                        }
                        Button("Снять замок") { Task { await model.setLock(false) } }.buttonStyle(.glass).disabled(model.busy)
                    } else {
                        Button { Task { await model.setLock(true) } } label: {
                            Label("Закрыть Mac от сети", systemImage: "lock.shield.fill").padding(.horizontal, 6)
                        }
                        .buttonStyle(.glassProminent).tint(.red).controlSize(.large).disabled(model.busy)
                        Text("Один раз пароль администратора").font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(22)
        .glassEffect(.regular.tint(tint.opacity(0.08)), in: .rect(cornerRadius: 26))
        .animation(.smooth, value: lock.on)
        .animation(.smooth, value: open)
    }

    private var statusLine: String {
        guard lock.on else {
            return "Сейчас любая программа и любая служба macOS выходит в сеть, когда хочет. Замок закроет все новые соединения, кроме программ, которые вы разрешите."
        }
        var parts: [String] = []
        switch model.lock.probe {
        case .closed?: parts.append("Проверено: прямой выход в сеть закрыт")
        case .open?: parts.append("Внимание: прямой выход открыт — фильтр не загружен")
        case .offline?: parts.append("Сети сейчас нет")
        case nil: parts.append("Проверяю…")
        }
        if !model.lock.running { parts.append("шлюз не запущен — программы без сети") }
        if model.lock.gateOpen { parts.append("шлюз пропускает все программы") }
        return parts.joined(separator: " · ")
    }

    private var proxyWarning: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 20)).foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 2) {
                Text("Системный прокси изменила другая программа").font(.system(size: 13, weight: .semibold))
                Text("Программы идут не через шлюз Block — и без сети, потому что фильтр их не пускает. Обычно это VPN в режиме прокси: включите в нём TUN.")
                    .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Button("Вернуть шлюз") { Task { await model.setLock(true) } }.buttonStyle(.glassProminent)
        }
        .padding(14)
        .glassEffect(.regular.tint(.orange.opacity(0.14)), in: .rect(cornerRadius: 18))
    }

    private var tiles: some View {
        let all = Array(lock.apps.values)
        let today = model.lock.feed.filter { Calendar.current.isDateInToday($0.date) }
        return HStack(spacing: 14) {
            Tile(value: "\(all.filter { $0.decision == .allow }.count)", title: "программ разрешено", symbol: "checkmark.shield.fill", tint: .green)
            Tile(value: "\(all.filter { $0.decision == .deny }.count)", title: "запрещено", symbol: "xmark.shield.fill", tint: .red)
            Tile(value: "\(today.filter(\.allowed).count)", title: "соединений пропущено", symbol: "arrow.up.right.circle.fill", tint: .blue)
            Tile(value: "\(today.filter { !$0.allowed }.count)", title: "остановлено", symbol: "hand.raised.fill", tint: .orange)
        }
    }

    // MARK: Программы

    private var appsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Программы").font(.system(size: 18, weight: .bold, design: .rounded))
            SegmentBar(items: Filter.allCases.map { (value: $0, title: $0.title) }, selection: $filter, count: { rules(for: $0).count })
            if apps.isEmpty {
                Text(filter == .all ? "Программы появятся здесь, как только попросятся в сеть. Новую программу Block спросит: разрешить, только сейчас или запретить."
                                    : "Пусто.")
                    .font(.system(size: 12)).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .glassEffect(.regular, in: .rect(cornerRadius: 18))
            } else {
                VStack(spacing: 0) {
                    ForEach(apps) { rule in
                        AppRuleRow(rule: rule)
                        if rule.id != apps.last?.id { Divider().padding(.leading, 56) }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .glassEffect(.regular, in: .rect(cornerRadius: 20))
            }
        }
    }

    // MARK: Лента

    private var feedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Сейчас в сети").font(.system(size: 15, weight: .semibold))
            if model.lock.feed.isEmpty {
                Text(lock.on ? "Пока тихо." : "Включите замок — здесь будет видно каждое соединение.").font(.system(size: 12)).foregroundStyle(.secondary)
            }
            ForEach(model.lock.feed.prefix(14)) { item in
                HStack(spacing: 8) {
                    Image(systemName: item.allowed ? "arrow.up.right.circle.fill" : "xmark.circle.fill")
                        .foregroundStyle(item.allowed ? .green : .red).font(.system(size: 12))
                    Text(item.app.name).font(.system(size: 12, weight: .medium)).lineLimit(1).frame(width: 110, alignment: .leading)
                    Text(item.host).font(.system(size: 11, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 4)
                    if item.bytes > 0 {
                        Text(ByteCountFormatter.string(fromByteCount: item.bytes, countStyle: .file)).font(.system(size: 10).monospacedDigit()).foregroundStyle(.tertiary)
                    }
                    Text(item.date, style: .time).font(.system(size: 10).monospacedDigit()).foregroundStyle(.tertiary)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    // MARK: Напрямую

    private var directSection: some View {
        @Bindable var model = model
        return VStack(alignment: .leading, spacing: 10) {
            Text("Без шлюза, напрямую").font(.system(size: 15, weight: .semibold))
            Toggle("Домашняя сеть: принтеры, NAS, AirPlay", isOn: $model.state.lock.allowLAN).toggleStyle(.switch).controlSize(.small)
            let veil = NetLock.veil()
            Toggle("Серверы Veil (\(veil.servers.count)) — чтобы VPN подключался", isOn: $model.state.lock.veilServers).toggleStyle(.switch).controlSize(.small)
            Toggle("Разрешённое — через Veil (SOCKS \(veil.socksPort ?? 10808))", isOn: $model.state.lock.viaVeil).toggleStyle(.switch).controlSize(.small)
                .onChange(of: model.state.lock.viaVeil) { model.lock.applyUpstream(); model.state.save() }
            Toggle("Спрашивать и про службы macOS", isOn: $model.state.lock.askSystem).toggleStyle(.switch).controlSize(.small)
                .onChange(of: model.state.lock.askSystem) { model.state.save() }
            HStack {
                TextField("Адрес, сеть или имя: 203.0.113.5, 10.8.0.0/16", text: $newAddress)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(add)
                Button("Добавить", action: add).disabled(newAddress.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .font(.system(size: 12))
            if !lock.direct.isEmpty {
                FlowChips(items: lock.direct) { item in
                    model.state.lock.direct.removeAll { $0 == item }
                    model.state.save()
                }
            }
            if lock.on {
                Button("Применить изменения") { Task { await model.setLock(true) } }.buttonStyle(.glass).controlSize(.small).disabled(model.busy)
                Text("Сейчас напрямую пропускается адресов: \(lock.directResolved.count)").font(.system(size: 10)).foregroundStyle(.tertiary)
            }
        }
        .font(.system(size: 12))
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
        .onChange(of: model.state.lock.allowLAN) { model.state.save() }
        .onChange(of: model.state.lock.veilServers) { model.state.save() }
    }

    private func add() {
        let item = newAddress.trimmingCharacters(in: .whitespaces)
        guard !item.isEmpty, !model.state.lock.direct.contains(item) else { return }
        model.state.lock.direct.append(item)
        model.state.save()
        newAddress = ""
    }

    // MARK: Терминал

    private var terminal: some View {
        let snippet = NetLock.shellSnippet(port: lock.port)
        return VStack(alignment: .leading, spacing: 8) {
            Text("Терминал").font(.system(size: 15, weight: .semibold))
            Text("Программы командной строки (git, curl, brew) берут прокси из переменных окружения. Выполните это в Терминале или добавьте в ~/.zshrc — и они тоже пойдут через шлюз и появятся в списке программ.")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack {
                Text(snippet).font(.system(size: 11, design: .monospaced)).textSelection(.enabled).lineLimit(2)
                Spacer()
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(snippet, forType: .string)
                    model.flash("Скопировано")
                } label: { Image(systemName: "doc.on.doc") }.buttonStyle(.glass)
            }
            .padding(10)
            .background(.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    private var footnote: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Как это устроено").font(.system(size: 13, weight: .semibold))
            Text("Системный фильтр пакетов macOS (pf) закрывает все новые исходящие соединения, кроме шлюза Block, DNS, времени, DHCP, домашней сети и адресов, которые вы разрешили напрямую. Программы ходят в сеть через шлюз — системный прокси указывает на него. Шлюз видит, какая программа просится, и пускает только разрешённые. Правила живут в своём якоре pf, системный pf.conf не меняется; после перезагрузки их загружает задание launchd. Программы, которые не слушают системный прокси, остаются без сети — для них «Открыть сеть на время» или «Снять замок». Пока Block закрыт, программы под замком без сети: замок не открывается сам.")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

/// Строка программы: значок, что делала, решение.
private struct AppRuleRow: View {
    @Environment(AppModel.self) private var model
    let rule: NetAppRule

    var body: some View {
        HStack(spacing: 12) {
            NetAppIcon(app: rule.app, size: 32)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(rule.app.name).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    if rule.app.isApple || rule.app.isSystem { Text("macOS").font(.system(size: 9, weight: .bold)).foregroundStyle(.secondary)
                        .padding(.horizontal, 5).padding(.vertical, 1).background(.secondary.opacity(0.15), in: Capsule()) }
                }
                Text(rule.hosts.prefix(3).joined(separator: " · ")).font(.system(size: 10, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                Text("пропущено \(rule.allowed) · остановлено \(rule.denied) · \(ByteCountFormatter.string(fromByteCount: rule.bytes, countStyle: .file))")
                    .font(.system(size: 10)).foregroundStyle(.tertiary)
            }
            Spacer()
            SegmentBar(items: NetDecision.allCases.map { (value: $0, title: $0.title) },
                       selection: Binding(get: { rule.decision }, set: { model.lock.setDecision(rule.app, $0) }),
                       tint: { $0 == .allow ? .green : ($0 == .deny ? .red : .blue) })
                .frame(width: 300)
            Button { model.lock.forget(rule.id) } label: { Image(systemName: "trash") }
                .buttonStyle(.plain).foregroundStyle(.secondary).help("Забыть программу")
        }
        .padding(.vertical, 8)
    }
}

/// Перенос по строкам: адреса-«фишки» с крестиком.
private struct FlowChips: View {
    let items: [String]
    let remove: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(items, id: \.self) { item in
                HStack(spacing: 6) {
                    Text(item).font(.system(size: 11, design: .monospaced))
                    Button { remove(item) } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain).foregroundStyle(.secondary)
                }
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(.secondary.opacity(0.12), in: Capsule())
            }
        }
    }
}
