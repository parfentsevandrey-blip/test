import AppKit
import SwiftUI

/// Значок состояния правила.
struct StateBadge: View {
    let state: RuleState
    /// Уровень правила: «выключено» по-разному звучит для настройки, программы по умолчанию и сторожа.
    var level: Level?

    private var text: String {
        guard state == .blocked, let level else { return state.label }
        switch level {
        case .defaults: return "назначено"
        case .launchGuard: return "закрывается при запуске"
        case .network: return "адреса закрыты"
        case .services: return "остановлена"
        case .settings: return state.label
        }
    }

    var body: some View {
        Label(text, systemImage: symbol)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.13), in: Capsule())
            .lineLimit(1)
    }

    private var symbol: String {
        switch state {
        case .active: "circle"
        case .blocked: "checkmark.circle.fill"
        case .partial: "circle.lefthalf.filled"
        case .protected: "lock.fill"
        case .unavailable: "minus.circle"
        case .manual(let done): done ? "hand.thumbsup.fill" : "hand.point.up.left.fill"
        }
    }

    private var color: Color {
        switch state {
        case .active: .secondary
        case .blocked: .green
        case .partial: .orange
        case .protected: .purple
        case .unavailable: .secondary
        case .manual(let done): done ? .green : .blue
        }
    }
}

/// Иконка программы по bundle ID (или символ, если программы нет).
struct AppIcon: View {
    let bundleID: String
    var fallback = "app.dashed"
    var size: CGFloat = 40

    var body: some View {
        if let url = Handlers.appURL(bundleID) {
            Image(nsImage: NSWorkspace.shared.icon(forFile: url.path))
                .resizable()
                .interpolation(.high)
                .frame(width: size, height: size)
        } else {
            Image(systemName: fallback)
                .font(.system(size: size * 0.45, weight: .medium))
                .foregroundStyle(.secondary)
                .frame(width: size, height: size)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: size * 0.22))
        }
    }
}

/// Строка правила: что делает, что перестанет работать, состояние и переключатель.
struct RuleRow: View {
    @Environment(AppModel.self) private var model
    let rule: Rule

    var body: some View {
        let state = model.status(of: rule)
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: rule.surface.symbol)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(Circle().fill(rule.level == .network ? Color.teal.gradient : rule.level == .services ? Color.purple.gradient : Color.blue.gradient))
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(rule.title).font(.system(size: 13, weight: .semibold))
                    if rule.confidence == .experimental {
                        Text("экспериментально").font(.system(size: 10, weight: .medium)).foregroundStyle(.orange)
                            .padding(.horizontal, 6).padding(.vertical, 1).background(.orange.opacity(0.12), in: Capsule())
                            .help("Apple меняет этот ключ или службу от версии к версии. Block проверит результат и покажет, если не сработало.")
                    }
                    if rule.needsAdmin {
                        Image(systemName: "key.fill").font(.system(size: 10)).foregroundStyle(.secondary).help("Нужен пароль администратора")
                    }
                }
                Text(rule.detail).font(.system(size: 12)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if !rule.breaks.isEmpty {
                    Label("Перестанет работать: \(rule.breaks)", systemImage: "exclamationmark.triangle")
                        .font(.system(size: 11)).foregroundStyle(.orange.opacity(0.9))
                }
                if case .pane(_, let hint) = rule.action, !state.isBlocked {
                    Text(hint).font(.system(size: 11)).foregroundStyle(.blue)
                }
            }
            Spacer(minLength: 12)
            VStack(alignment: .trailing, spacing: 8) {
                StateBadge(state: state, level: rule.level)
                control(state)
            }
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func control(_ state: RuleState) -> some View {
        switch (rule.action, state) {
        case (.pane(let url, _), .manual(let done)):
            HStack(spacing: 6) {
                Button("Открыть настройки") { if let u = URL(string: url) { NSWorkspace.shared.open(u) } }.buttonStyle(.glass).controlSize(.small)
                Button(done ? "Отменить" : "Сделано") { model.markManual(rule, done: !done) }.controlSize(.small)
            }
        case (_, .unavailable):
            if case .handlers(_, let app) = rule.action {
                Button("Установить \(app.name)") { model.open(app) }.buttonStyle(.glass).controlSize(.small)
            }
        default:
            Toggle("", isOn: Binding(get: { state.isBlocked || model.state.baseline[rule.id] != nil },
                                     set: { _ in Task { await model.toggle(rule) } }))
                .toggleStyle(.switch)
                .labelsHidden()
                .disabled(model.busy)
        }
    }
}

/// Заголовок экрана: крупное название, подпись и действие справа.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 28, weight: .bold, design: .rounded))
                Text(subtitle).font(.system(size: 13)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            trailing
        }
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = EmptyView()
    }
}

/// Список правил с кнопками «Применить все» и «Вернуть все».
struct RuleListScreen: View {
    @Environment(AppModel.self) private var model
    let title: String
    let subtitle: String
    let rules: [Rule]
    var footer: AnyView?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: title, subtitle: subtitle) {
                    GlassEffectContainer(spacing: 8) {
                        HStack(spacing: 8) {
                            Button("Вернуть все") { Task { await model.revert(rules) } }
                                .buttonStyle(.glass)
                                .disabled(model.busy || !rules.contains { model.state.baseline[$0.id] != nil })
                            Button("Выключить все") { Task { await model.apply(pending) } }
                                .buttonStyle(.glassProminent)
                                .disabled(model.busy || pending.isEmpty)
                        }
                    }
                }
                VStack(spacing: 0) {
                    ForEach(rules) { rule in
                        RuleRow(rule: rule)
                        if rule.id != rules.last?.id { Divider() }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 8)
                .background(.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 18))
                if let footer { footer }
            }
            .padding(28)
            .frame(maxWidth: 920, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }

    /// То, что ещё не выключено и не требует ручного шага.
    private var pending: [Rule] {
        rules.filter { rule in
            guard !rule.isManual else { return false }
            switch model.status(of: rule) {
            case .active, .partial: return true
            default: return false
            }
        }
    }
}
