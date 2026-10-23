import AppKit
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
    private var isPrivate: Bool { tab.browser?.isPrivate ?? false }

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
            let owner = Companies.owner(of: tab.cloaked[host] ?? host)
            map[owner, default: Group(company: owner, hosts: [], kinds: [])].hosts.append(host)
            map[owner]?.kinds.insert(kind)
        }
        return map.values.map { g in var g = g; g.hosts.sort(); return g }
            .sorted { $0.hosts.count != $1.hosts.count ? $0.hosts.count > $1.hosts.count : $0.company < $1.company }
    }

    var body: some View {
        ScrollView {
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
        }
        .frame(width: 400)
        .frame(maxHeight: 680)
        .fixedSize(horizontal: false, vertical: true)
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
                Text(site.isEmpty ? "Новая вкладка" : Punycode.display(site).text).font(.title3.weight(.semibold)).lineLimit(1)
                Text(summary).font(.callout).foregroundStyle(.secondary)
                if tab.inIsolator {
                    Label("Изолятор", systemImage: "cube.transparent.fill").font(.caption).foregroundStyle(.teal)
                } else if tab.jarKind == .corporation, let jar = tab.jar {
                    Label("Банка «\(Jars.title(of: jar))»", systemImage: "building.2.crop.circle.fill").font(.caption).foregroundStyle(.orange)
                } else if tab.jarKind == .mask {
                    Label("Маска: отдельная вымышленная личность", systemImage: "theatermasks.fill").font(.caption).foregroundStyle(.purple)
                } else if let c = tab.container {
                    Label("Контейнер «\(c.name)»", systemImage: "square.stack.3d.up.fill").font(.caption).foregroundStyle(ContainerPalette.color(c.color))
                } else if tab.ephemeral {
                    Label("Одноразовая вкладка", systemImage: "flame").font(.caption).foregroundStyle(.orange)
                }
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
                                    Text(tab.cloaked[host].map { "\(host) → \($0)" } ?? host)
                                        .font(.caption.monospaced()).foregroundStyle(.secondary).padding(.leading, 26)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var notes: some View {
        VStack(alignment: .leading, spacing: 7) {
            if tab.jarKind == .corporation, let jar = tab.jar {
                let name = Jars.title(of: jar)
                if tab.corporationBlocked.isEmpty {
                    note("building.2", "\(name) живёт в своей банке: вход во все её сервисы работает, но другие корпорации её cookie не видят, а отсюда к ним запросы не уходят.", color: .orange)
                } else {
                    let others = Set(tab.corporationBlocked.values).sorted().joined(separator: ", ")
                    note("building.2", "\(name) из своей банки пыталась обратиться к: \(others) — \(tab.corporationBlocked.count) \(Format.plural(tab.corporationBlocked.count, "запрос не ушёл", "запроса не ушли", "запросов не ушли")).", color: .orange)
                }
            }
            if tab.jarKind == .mask {
                let legend = model.jarLegend(tab.jar ?? "")
                note("theatermasks", "Маска: этот сайт видит отдельного человека — устройство № \(legend.tag) со своим отпечатком, своё хранилище. С другими сайтами его не связать.", color: .purple)
            }
            if tab.inIsolator {
                let tried = Isolation.Attempt.allCases.filter { (tab.isolationAttempts[$0.rawValue] ?? 0) > 0 && $0 != .trackers }
                if tried.isEmpty {
                    note("cube.transparent", "Сайт в изоляторе: свои cookie и данные, вымышленное устройство. Пока он ничего лишнего не просил.", color: .teal)
                } else {
                    note("cube.transparent", "Сайт в изоляторе пытался получить: \(tried.map { "\($0.title.lowercased()) — \($0.answer)" }.joined(separator: "; ")).", color: .teal)
                }
            }
            if tab.unwrappedRedirect {
                note("arrow.uturn.right", "Ссылка-переходник пропущена: сайт, откуда вы пришли, не узнал, куда вы ушли.")
            }
            if !tab.cleanedParams.isEmpty {
                note("scissors", "Из адреса убраны метки слежки: \(tab.cleanedParams.prefix(6).joined(separator: ", ")).")
            }
            if !tab.cloaked.isEmpty {
                note("theatermasks", "Найдены скрытые трекеры: поддомен сайта на самом деле ведёт к счётчику. Дальше он блокируется.", color: .purple)
            }
            if let cmp = tab.consentRejected {
                note("hand.raised", "Окно про cookie: Ward нажал «Отклонить» (\(cmp)).")
            }
            if tab.scriptletCount > 0 && tab.protectedHere {
                note("wand.and.stars", "Для этого сайта работают \(tab.scriptletCount) \(Format.plural(tab.scriptletCount, "скриптлет", "скриптлета", "скриптлетов")) uBlock Origin: против рекламы, встроенной в сам сайт, и стен «отключите блокировщик».")
            }
            if tab.annoyancesHidden > 0 {
                note("rectangle.on.rectangle.slash", "Убрано \(tab.annoyancesHidden) \(Format.plural(tab.annoyancesHidden, "окно", "окна", "окон")) поверх страницы («подпишитесь», рассылка, затемнение), прокрутка возвращена.")
            }
            if tab.sponsorSkipped > 0 {
                note("forward.end", "SponsorBlock пропустил \(tab.sponsorSkipped) \(Format.plural(tab.sponsorSkipped, "вставку", "вставки", "вставок")) в ролике.")
            }
            if tab.upgradedToHTTPS {
                note("lock.fill", "Сайт открыт по защищённому соединению HTTPS, хотя ссылка вела на незащищённое.")
            } else if tab.url?.scheme == "http" {
                note("lock.open.fill", "Соединение не зашифровано: пароли и данные на этой странице видны по пути.", color: .orange)
            }
            if let cert = tab.certificate, tab.url?.scheme == "https" {
                let validity = cert.notAfter.map { " до \($0.formatted(date: .abbreviated, time: .omitted))" } ?? ""
                note("checkmark.seal", "Сертификат: \(cert.issuer.isEmpty ? "центр сертификации" : cert.issuer), действует\(validity).")
                if cert.isFresh && !model.isFamiliar(site) {
                    note("exclamationmark.triangle", "Сертификат выпущен меньше трёх дней назад, а сайт вам незнаком. Так часто выглядят однодневные мошеннические сайты — не вводите здесь пароли и данные карт.", color: .orange)
                }
            }
            if tab.strictHere {
                note("lock.shield", "Строгий режим: без JIT, WebGL и сложных шрифтов — через них чаще всего атакуют браузеры.", color: .blue)
            }
            if !tab.fingerprinting.isEmpty && tab.protectedHere {
                let ways = FingerprintTechnique.allCases.filter { tab.fingerprinting.contains($0.rawValue) }.map { $0.title.lowercased() }
                note("touchid", "Страница пыталась узнать вас без cookie (\(ways.joined(separator: ", "))) — и получила подделку.", color: .pink)
            } else if model.prefs.fingerprint && tab.protectedHere && tab.url != nil {
                note("touchid", "Отпечаток браузера подменён: для этого сайта он свой и меняется при каждом запуске.")
            }
            if tab.hiddenCount > 0 && tab.protectedHere {
                note("eye.slash", "Скрыто \(tab.hiddenCount) \(Format.plural(tab.hiddenCount, "рекламный блок", "рекламных блока", "рекламных блоков")), баннеров и кнопок.", color: .blue)
            }
            if tab.browser?.usesProxy == true {
                note("network.badge.shield.half.filled", "Окно идёт через прокси \(model.prefs.proxyHost):\(model.prefs.proxyPort) — сайт видит его адрес, а не ваш.", color: .blue)
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
                Toggle(isOn: Binding(get: { tab.strictHere }, set: { on in
                    model.setStrict(on, site: site)
                    tab.reload()
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Строгий режим")
                        Text("Режим блокировки Apple для этого сайта: меньше возможностей у страницы — меньше путей для атаки.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                if !isPrivate && !tab.ephemeral {
                    Toggle(isOn: Binding(get: { model.isIsolated(site) }, set: { on in
                        Task { await model.setIsolated(on, site: site) }
                    })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Изолятор")
                            Text(model.isIsolated(site)
                                 ? "Сайт заперт: своя «банка» данных, вымышленное устройство, все разрешения отклоняются. Если выключить, всё, что он накопил в банке (и вход), удалится."
                                 : "Запереть сайт в отдельной «банке»: вход сохранится, но сайт увидит вымышленное устройство и не получит ни геопозиции, ни камеры, ни буфера обмена, ни данных других сайтов.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Toggle(isOn: Binding(get: { model.prefs.rememberedSites.contains(site) }, set: { model.setRemembered($0, site: site) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Помнить вход")
                            Text("Cookie этого сайта не удаляются ни после закрытия вкладки, ни при выходе из Ward.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Picker("Всегда открывать в", selection: Binding(get: { model.container(forSite: site)?.id }, set: { id in
                        model.setContainer(id.flatMap { i in model.containers.first { $0.id == i } }, forSite: site)
                    })) {
                        Text("Обычной вкладке").tag(UUID?.none)
                        ForEach(model.containers) { c in
                            Text("Контейнере «\(c.name)»").tag(Optional(c.id))
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
                    .help("Удалить cookie, хранилище и кэш \(site) во всех контейнерах, а также историю посещений")
                }
            }
            .toggleStyle(.switch)
        }
    }

    private var footer: some View {
        HStack {
            Button("Всего остановлено: \(Format.count(model.stats.total)) →") { model.openWindow?("dashboard") }
                .buttonStyle(.link).font(.caption)
                .help("Панель безопасности")
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

/// Цвета контейнеров.
enum ContainerPalette {
    static let colors: [Color] = [.blue, .orange, .green, .red, .purple, .pink, .teal, .yellow]
    static func color(_ i: Int) -> Color { colors[((i % colors.count) + colors.count) % colors.count] }
}
