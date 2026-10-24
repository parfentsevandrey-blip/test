import SwiftUI

/// Первый запуск: три вопроса, профиль и предпросмотр всех изменений до применения.
struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var step = 0
    @State private var answers = Answers()
    @State private var profile: Profile = .gentle

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text(step < 3 ? "Вопрос \(step + 1) из 3" : "Что изменится").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
                Spacer()
                Button("Позже") { finish(apply: false) }.buttonStyle(.plain).foregroundStyle(.secondary)
            }
            switch step {
            case 0:
                question("Вы пользуетесь iCloud?", "Синхронизация заметок, фото и файлов между устройствами Apple.", $answers.usesICloud)
            case 1:
                question("Нужен ли «Найти Mac»?", "Поиск и блокировка украденного Mac. Он работает только через iCloud — без него Block не закрывает адреса iCloud.", $answers.needsFindMy)
            case 2:
                question("Пользуетесь iPhone вместе с этим Mac?", "Handoff, общий буфер обмена, Сообщения и FaceTime.", $answers.usesIPhone)
            default:
                preview
            }
            Spacer(minLength: 0)
            HStack {
                if step > 0 { Button("Назад") { withAnimation { step -= 1 } }.buttonStyle(.glass) }
                Spacer()
                if step < 3 {
                    Button("Дальше") { withAnimation { step += 1; model.state.answers = answers } }
                        .buttonStyle(.glassProminent).keyboardShortcut(.defaultAction)
                } else {
                    Button("Применить: \(changes.count)") { finish(apply: true) }
                        .buttonStyle(.glassProminent).keyboardShortcut(.defaultAction)
                        .disabled(changes.isEmpty)
                }
            }
        }
        .padding(28)
        .frame(width: 600, height: 560)
        .onAppear { answers = model.state.answers; profile = model.state.profile == .custom ? .gentle : model.state.profile }
    }

    private func question(_ title: String, _ detail: String, _ value: Binding<Bool>) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.system(size: 26, weight: .bold, design: .rounded))
            Text(detail).font(.system(size: 14)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                choice("Да", value.wrappedValue) { value.wrappedValue = true }
                choice("Нет", !value.wrappedValue) { value.wrappedValue = false }
            }
            .padding(.top, 8)
        }
    }

    private func choice(_ title: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if selected { Image(systemName: "checkmark") }
                Text(title)
            }
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .frame(width: 120, height: 48)
            .background(selected ? AnyShapeStyle(Color.indigo.gradient) : AnyShapeStyle(.clear), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 14))
    }

    private var changes: [Rule] { model.preview(for: profile) }

    private var preview: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("", selection: $profile) {
                ForEach([Profile.gentle, .full]) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented).labelsHidden().frame(width: 260)
            Text(profile.subtitle).font(.system(size: 12)).foregroundStyle(.secondary)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Level.allCases) { level in
                        let list = changes.filter { $0.level == level }
                        if !list.isEmpty {
                            Label(level.title, systemImage: level.symbol).font(.system(size: 13, weight: .semibold)).padding(.top, 4)
                            ForEach(list) { rule in
                                HStack(alignment: .firstTextBaseline, spacing: 6) {
                                    Text("•").foregroundStyle(.secondary)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(rule.title).font(.system(size: 12))
                                        if !rule.breaks.isEmpty {
                                            Text("перестанет работать: \(rule.breaks)").font(.system(size: 10)).foregroundStyle(.orange)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text("Всё обратимо из журнала. Пароль администратора понадобится только для сети и аналитики Mac.")
                .font(.system(size: 11)).foregroundStyle(.secondary)
        }
    }

    private func finish(apply: Bool) {
        model.state.answers = answers
        model.state.onboarded = true
        model.state.profile = profile
        model.state.save()
        model.refresh()
        dismiss()
        if apply { Task { await model.applyProfile(profile) } }
    }
}
