import AppKit
import SwiftUI

// MARK: - Программы

struct AppsView: View {
    @Environment(AppModel.self) private var model
    private let columns = [GridItem(.adaptive(minimum: 250), spacing: 14)]

    var body: some View {
        let guards = model.rules.filter { $0.level == .launchGuard && $0.action.guardedBundle != nil }
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Программы", subtitle: "Сторож закрывает их в момент запуска — от значка, кнопки Play, AirPods или файла. Вместо них открывается замена из каталога.") {
                    Button("Включить сторож для всех") {
                        Task { await model.apply(guards.filter { model.status(of: $0) == .active }) }
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(model.busy || !guards.contains { model.status(of: $0) == .active })
                }
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(guards) { rule in GuardCard(rule: rule) }
                }
                if let dock = model.rules.first(where: { $0.id == "dock.clean" }) {
                    RuleRow(rule: dock)
                        .padding(.horizontal, 18)
                        .background(.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 18))
                }
                LoginItemRow()
            }
            .padding(28)
            .frame(maxWidth: 1040, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }
}

private struct GuardCard: View {
    @Environment(AppModel.self) private var model
    let rule: Rule

    var body: some View {
        let bundle = rule.action.guardedBundle ?? ""
        let state = model.status(of: rule)
        let stopped = model.state.stoppedLaunches[bundle] ?? 0
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                AppIcon(bundleID: bundle, fallback: rule.surface.symbol, size: 44)
                    .saturation(state.isBlocked ? 0 : 1)
                    .opacity(state.isBlocked ? 0.55 : 1)
                VStack(alignment: .leading, spacing: 3) {
                    Text(rule.title.replacingOccurrences(of: " не запускается", with: "")).font(.system(size: 14, weight: .semibold))
                    StateBadge(state: state)
                }
                Spacer()
                if state.counts || state.isBlocked {
                    Toggle("", isOn: Binding(get: { state.isBlocked }, set: { _ in Task { await model.toggle(rule) } }))
                        .toggleStyle(.switch).labelsHidden().disabled(model.busy)
                }
            }
            HStack(spacing: 6) {
                if case .launchGuard(_, let replacement?) = rule.action {
                    Image(systemName: "arrow.turn.down.right").foregroundStyle(.secondary)
                    Text("вместо неё — \(replacement.name)").foregroundStyle(model.installed(replacement) ? AnyShapeStyle(.green) : AnyShapeStyle(.secondary))
                    if !model.installed(replacement) && !DemoUI.isOn {
                        Button("Установить") { model.open(replacement) }.buttonStyle(.link)
                    }
                } else {
                    Text(rule.breaks.isEmpty ? "замена не нужна" : "перестанет работать: \(rule.breaks)").foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                if stopped > 0 {
                    Text("остановлено: \(stopped)").font(.system(size: 11, weight: .semibold).monospacedDigit()).foregroundStyle(.orange)
                }
            }
            .font(.system(size: 11))
            if state.isBlocked {
                Button("Открыть один раз") { model.guardian.openOnce(bundle) }.buttonStyle(.glass).controlSize(.small)
            }
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }
}

struct LoginItemRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("Сторож работает после входа в систему").font(.system(size: 13, weight: .semibold))
                Text("Block запускается при входе без окна — только значок в строке меню. Без него сторож работает, пока Block открыт.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("", isOn: $model.launchAtLogin).toggleStyle(.switch).labelsHidden()
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

// MARK: - Сеть

struct NetworkView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        RuleListScreen(title: "Сеть", subtitle: "Адреса Apple разбиты на группы. Обновления системы, проверка подписей и сертификатов, нотаризация и время не закрываются никогда.",
                       rules: model.rules.filter { $0.level == .network }, footer: AnyView(footer))
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Как закрывать").font(.system(size: 15, weight: .bold, design: .rounded))
                Picker("", selection: Binding(get: { model.state.networkMethod }, set: { m in Task { await model.setNetworkMethod(m) } })) {
                    ForEach(NetworkMethod.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .frame(maxWidth: 320)
                .disabled(model.busy)
                Text(model.state.networkMethod == .hosts
                     ? "Своя секция в /etc/hosts с пометками начала и конца: работает всегда, один раз нужен пароль администратора. Масок нет — только точные имена."
                     : "Правила блокировки в Veil: без пароля, с поддоменами (icloud.com — все его адреса), но только пока Veil включён.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("Неприкосновенные адреса").font(.system(size: 15, weight: .bold, design: .rounded))
                ForEach(AppleNetwork.untouchable.indices, id: \.self) { i in
                    let item = AppleNetwork.untouchable[i]
                    HStack {
                        Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(.green)
                        Text(item.host).font(.system(size: 12, design: .monospaced))
                        Text("— \(item.why)").font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(18)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

// MARK: - Замены

struct ReplacementsView: View {
    @Environment(AppModel.self) private var model
    private let columns = [GridItem(.adaptive(minimum: 300), spacing: 14)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Замены", subtitle: "Программы каталога вместо сервисов Apple: бесплатно, без входа и подписок. Ставятся через Harbor.")
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(CatalogApp.allCases) { app in
                        let installed = model.installed(app) || DemoUI.isOn
                        HStack(spacing: 14) {
                            AppIcon(bundleID: app.bundleID, fallback: app.symbol, size: 48)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(app.name).font(.system(size: 15, weight: .semibold))
                                Text(app.role).font(.system(size: 12)).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if installed {
                                Button("Открыть") { model.open(app) }.buttonStyle(.glass)
                            } else {
                                Button("Установить") { model.open(app) }.buttonStyle(.glassProminent)
                            }
                        }
                        .padding(16)
                        .glassEffect(.regular, in: .rect(cornerRadius: 20))
                    }
                }
                RuleListScreenBody(rules: model.rules.filter { $0.level == .defaults })
            }
            .padding(28)
            .frame(maxWidth: 1040, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }
}

/// Только строки правил (без заголовка) — для вставки в другие экраны.
struct RuleListScreenBody: View {
    let rules: [Rule]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Программы по умолчанию").font(.system(size: 17, weight: .bold, design: .rounded))
            VStack(spacing: 0) {
                ForEach(rules) { rule in
                    RuleRow(rule: rule)
                    if rule.id != rules.last?.id { Divider() }
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
            .background(.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 18))
        }
    }
}

// MARK: - Журнал

struct JournalView: View {
    @Environment(AppModel.self) private var model
    @State private var confirmAll = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Журнал", subtitle: "Что, когда и с каким прежним значением изменено. Любое правило возвращается по отдельности.") {
                    Button("Вернуть Mac как было", role: .destructive) { confirmAll = true }
                        .buttonStyle(.glass)
                        .disabled(model.state.baseline.isEmpty || model.busy)
                }
                if model.state.journal.isEmpty {
                    ContentUnavailableView("Пока ничего не менялось", systemImage: "clock", description: Text("Здесь появятся все изменения Block."))
                        .frame(maxWidth: .infinity).padding(.top, 60)
                }
                LazyVStack(spacing: 0) {
                    ForEach(model.state.journal.prefix(400)) { entry in
                        JournalRow(entry: entry)
                        Divider()
                    }
                }
                .padding(.horizontal, 18)
                .background(.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 18))
                VStack(alignment: .leading, spacing: 10) {
                    Toggle("После обновления macOS применять заново то, что Apple вернула", isOn: Binding(get: { model.state.autoReapply }, set: { model.state.autoReapply = $0; model.state.save() }))
                    Button("Проверить сейчас") { model.checkDrift(reason: "проверка вручную") }.buttonStyle(.glass)
                }
                .padding(16)
                .glassEffect(.regular, in: .rect(cornerRadius: 18))
            }
            .padding(28)
            .frame(maxWidth: 920, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .confirmationDialog("Вернуть все изменения Block?", isPresented: $confirmAll) {
            Button("Вернуть всё (\(model.state.baseline.count))", role: .destructive) { Task { await model.revertAll() } }
        } message: {
            Text("Каждый ключ, служба, значок Dock и программа по умолчанию получат ровно то значение, что было до Block.")
        }
    }
}

private struct JournalRow: View {
    @Environment(AppModel.self) private var model
    let entry: JournalEntry

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(color).frame(width: 18)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.title).font(.system(size: 13, weight: .medium))
                Text(entry.note).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer()
            Text(entry.date, format: .dateTime.day().month().hour().minute()).font(.system(size: 11).monospacedDigit()).foregroundStyle(.secondary)
            if entry.kind == .applied, model.state.baseline[entry.ruleID] != nil, let rule = Catalog.rule(entry.ruleID) {
                Button("Вернуть") { Task { await model.revert([rule]) } }.buttonStyle(.link).font(.system(size: 11))
            }
        }
        .padding(.vertical, 9)
    }

    private var symbol: String {
        switch entry.kind {
        case .applied: "checkmark.circle.fill"
        case .reverted: "arrow.uturn.backward.circle.fill"
        case .failed: "xmark.octagon.fill"
        case .drift: "exclamationmark.arrow.circlepath"
        case .launch: "hand.raised.fill"
        }
    }

    private var color: Color {
        switch entry.kind {
        case .applied: .green
        case .reverted: .blue
        case .failed: .red
        case .drift: .orange
        case .launch: .pink
        }
    }
}

// MARK: - Строка меню

struct MenuBarView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().stroke(.primary.opacity(0.1), lineWidth: 5)
                    Circle().trim(from: 0, to: model.score).stroke(Color.teal, style: StrokeStyle(lineWidth: 5, lineCap: .round)).rotationEffect(.degrees(-90))
                    Text("\(Int((model.score * 100).rounded()))").font(.system(size: 12, weight: .bold, design: .rounded))
                }
                .frame(width: 42, height: 42)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mac принадлежит вам").font(.system(size: 13, weight: .semibold))
                    Text("Сторож остановил запусков: \(model.stoppedTotal)").font(.system(size: 11)).foregroundStyle(.secondary)
                }
            }
            if !model.drift.isEmpty {
                Label("Apple вернула своё: \(model.drift.count)", systemImage: "arrow.uturn.backward.circle").foregroundStyle(.orange).font(.system(size: 12))
            }
            Divider()
            Button("Открыть Block") {
                openWindow(id: "main")
                NSApp.activate()
            }
            Button("Проверить, не вернула ли Apple своё") { model.checkDrift(reason: "проверка из строки меню") }
            Divider()
            Button("Выйти из Block") { NSApp.terminate(nil) }
        }
        .buttonStyle(.plain)
        .padding(16)
        .frame(width: 290)
    }
}
