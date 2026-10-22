import SwiftUI

/// Щит в панели: сколько остановлено на этой странице. По нажатию — подробности и настройки сайта.
struct ShieldButton: View {
    @Environment(AppModel.self) private var model
    let tab: Tab
    @State private var shown = false

    var body: some View {
        Button { shown.toggle() } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .foregroundStyle(tab.protectedHere ? Color.green : Color.orange)
                if tab.blockedCount > 0 {
                    Text("\(tab.blockedCount)")
                        .font(.callout.monospacedDigit().weight(.semibold))
                        .contentTransition(.numericText())
                }
            }
        }
        .help("Защита на этой странице")
        .task {
            guard DemoUI.showsShield else { return }
            try? await Task.sleep(for: .seconds(20))
            shown = true
        }
        .popover(isPresented: $shown, arrowEdge: .bottom) {
            ShieldPanel(tab: tab)
                .environment(model)
        }
    }

    private var icon: String {
        if !tab.protectedHere { return "shield.slash" }
        if case .preparing = model.blocker.phase { return "shield" }
        return "shield.lefthalf.filled"
    }
}

struct ShieldPanel: View {
    @Environment(AppModel.self) private var model
    let tab: Tab
    @State private var forgotten = false
    @State private var expanded: Set<String> = []

    private var site: String { tab.site }

    /// Остановленное, сгруппированное по компаниям.
    struct Group: Identifiable {
        var company: String
        var hosts: [String]
        var kinds: Set<TrackerKind>
        var id: String { company }
    }

    private var groups: [Group] {
        var map: [String: Group] = [:]
        for (host, kind) in tab.trackers {
            let owner = Companies.owner(of: host)
            map[owner, default: Group(company: owner, hosts: [], kinds: [])].hosts.append(host)
            map[owner]?.kinds.insert(kind)
        }
        return map.values.map { g in var g = g; g.hosts.sort(); return g }
            .sorted { $0.hosts.count != $1.hosts.count ? $0.hosts.count > $1.hosts.count : $0.company < $1.company }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            if !site.isEmpty {
                Toggle(isOn: Binding(get: { tab.protectedHere }, set: { on in
                    model.setProtection(on, site: site)
                    tab.reload()
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Защита на \(site)").font(.headline)
                        Text(tab.protectedHere ? "Реклама, трекеры и отпечаток браузера блокируются." : "Выключена: сайт видит вас как обычный браузер без защиты.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .toggleStyle(.switch)
            }
            Divider()
            trackersSection
            notes
            Divider()
            siteControls
            footer
        }
        .padding(18)
        .frame(width: 380)
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill((tab.protectedHere ? Color.green : Color.orange).opacity(0.16)).frame(width: 46, height: 46)
                Image(systemName: tab.protectedHere ? "shield.lefthalf.filled" : "shield.slash")
                    .font(.title2)
                    .foregroundStyle(tab.protectedHere ? Color.green : Color.orange)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(site.isEmpty ? "Новая вкладка" : site).font(.title3.weight(.semibold)).lineLimit(1)
                Text(summary).font(.callout).foregroundStyle(.secondary)
            }
        }
    }

    private var summary: String {
        if !tab.protectedHere { return "Защита выключена" }
        let n = tab.blockedCount
        if n == 0 { return "Трекеров на странице не замечено" }
        return "Остановлено: \(n) \(Format.plural(n, "адрес", "адреса", "адресов")) рекламы и слежки"
    }

    @ViewBuilder
    private var trackersSection: some View {
        if !groups.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("Кто пытался следить").font(.subheadline.weight(.semibold))
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(groups) { group in
                            VStack(alignment: .leading, spacing: 2) {
                                Button {
                                    if expanded.contains(group.company) { expanded.remove(group.company) } else { expanded.insert(group.company) }
                                } label: {
                                    HStack {
                                        Image(systemName: kindIcon(group.kinds)).foregroundStyle(kindColor(group.kinds)).frame(width: 18)
                                        Text(group.company).lineLimit(1)
                                        Spacer()
                                        Text("\(group.hosts.count)").monospacedDigit().foregroundStyle(.secondary)
                                        Image(systemName: expanded.contains(group.company) ? "chevron.up" : "chevron.down")
                                            .font(.caption2).foregroundStyle(.tertiary)
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                if expanded.contains(group.company) {
                                    ForEach(group.hosts, id: \.self) { host in
                                        Text(host).font(.caption.monospaced()).foregroundStyle(.secondary).padding(.leading, 26)
                                    }
                                }
                            }
                        }
                    }
                }
                .frame(maxHeight: 200)
            }
        }
    }

    @ViewBuilder
    private var notes: some View {
        VStack(alignment: .leading, spacing: 6) {
            if tab.unwrappedRedirect {
                note("arrow.uturn.right", "Ссылка-переходник пропущена: сайт, откуда вы пришли, не узнал, куда вы ушли.")
            }
            if !tab.cleanedParams.isEmpty {
                note("scissors", "Из адреса убраны метки слежки: \(tab.cleanedParams.prefix(6).joined(separator: ", ")).")
            }
            if tab.upgradedToHTTPS {
                note("lock.fill", "Сайт открыт по защищённому соединению HTTPS, хотя ссылка вела на незащищённое.")
            } else if tab.url?.scheme == "http" {
                note("lock.open.fill", "Соединение не зашифровано: пароли и данные на этой странице видны по пути.", color: .orange)
            }
            if model.prefs.fingerprint && tab.protectedHere && tab.url != nil {
                note("touchid", "Отпечаток браузера подменён: для этого сайта он свой и меняется при каждом запуске.")
            }
        }
    }

    private func note(_ icon: String, _ text: String, color: Color = .green) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon).foregroundStyle(color).frame(width: 18)
            Text(text).font(.callout).fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private var siteControls: some View {
        if !site.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Toggle("JavaScript на этом сайте", isOn: Binding(get: { !model.prefs.noScriptSites.contains(site) }, set: { on in
                    model.setJavaScript(on, site: site)
                    tab.reload()
                }))
                if !(tab.browser?.isPrivate ?? false) {
                    Toggle(isOn: Binding(get: { model.prefs.rememberedSites.contains(site) }, set: { model.setRemembered($0, site: site) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Помнить вход")
                            Text(model.prefs.clearOnQuit ? "Cookie этого сайта не удаляются при выходе из Ward." : "Сохранится, даже если включите очистку при выходе.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Button(forgotten ? "Данные сайта удалены" : "Забыть этот сайт") {
                        Task {
                            await model.forget(site: site)
                            forgotten = true
                            tab.reload()
                        }
                    }
                    .disabled(forgotten)
                    .help("Удалить cookie, хранилище и кэш \(site), а также историю посещений")
                }
            }
            .toggleStyle(.switch)
        }
    }

    private var footer: some View {
        HStack {
            Text("Всего остановлено: \(Format.count(model.stats.total))").font(.caption).foregroundStyle(.secondary)
            Spacer()
            SettingsLink { Text("Настройки защиты") }.font(.caption)
        }
    }

    private func kindIcon(_ kinds: Set<TrackerKind>) -> String {
        if kinds.contains(.malware) { return "exclamationmark.octagon.fill" }
        if kinds.contains(.tracking) { return "eye.slash.fill" }
        if kinds.contains(.ads) { return "rectangle.slash.fill" }
        return "hand.raised.fill"
    }

    private func kindColor(_ kinds: Set<TrackerKind>) -> Color {
        if kinds.contains(.malware) { return .red }
        if kinds.contains(.tracking) { return .purple }
        if kinds.contains(.ads) { return .blue }
        return .gray
    }
}

/// Кнопка загрузок (появляется, когда есть загрузки).
struct DownloadsButton: View {
    @Environment(AppModel.self) private var model
    @State private var shown = false

    var body: some View {
        Button { shown.toggle() } label: {
            Image(systemName: model.downloads.active > 0 ? "arrow.down.circle.dotted" : "arrow.down.circle")
                .symbolEffect(.bounce, value: model.downloads.pulse)
        }
        .help("Загрузки")
        .popover(isPresented: $shown, arrowEdge: .bottom) {
            DownloadsPanel().environment(model)
        }
    }
}

struct DownloadsPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Загрузки").font(.headline)
                Spacer()
                Button("Очистить список") { model.downloads.clearFinished() }.buttonStyle(.borderless).font(.caption)
            }
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(model.downloads.items) { item in
                        DownloadRow(item: item)
                    }
                }
            }
            .frame(maxHeight: 320)
            Text("Файлы помечены как скачанные из интернета: macOS проверит их перед первым открытием.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(width: 360)
    }
}

struct DownloadRow: View {
    @Environment(AppModel.self) private var model
    let item: Downloads.Item

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "doc.fill").font(.title2).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name).lineLimit(1).truncationMode(.middle)
                switch item.state {
                case .running:
                    ProgressView(value: item.progress).controlSize(.small)
                    Text(item.total > 0 ? "\(Format.bytes(item.received)) из \(Format.bytes(item.total))" : Format.bytes(item.received))
                        .font(.caption).foregroundStyle(.secondary)
                case .done:
                    Text("Готово · \(Format.bytes(max(item.total, item.received)))").font(.caption).foregroundStyle(.secondary)
                case .failed(let message):
                    Text(message).font(.caption).foregroundStyle(.red).lineLimit(2)
                case .cancelled:
                    Text("Отменено").font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            switch item.state {
            case .running:
                Button { model.downloads.cancel(item) } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.borderless)
            case .done:
                Button { model.downloads.reveal(item) } label: { Image(systemName: "magnifyingglass.circle.fill") }
                    .buttonStyle(.borderless).help("Показать в Finder")
            default:
                EmptyView()
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { if item.state == .done { model.downloads.open(item) } }
    }
}
