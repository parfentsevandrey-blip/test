import SwiftUI

struct InstallView: View {
    @Environment(AppModel.self) private var model
    var session: InstallSession
    @State private var showLog = false

    var body: some View {
        ZStack {
            AmbientBackground(colors: session.project.colors)
            HStack(alignment: .top, spacing: 20) {
                stepsColumn
                    .frame(width: 320)
                VStack(spacing: 16) {
                    focusCard
                    if showLog { LogView(lines: session.log) }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .navigationTitle("Установка \(session.project.name)")
        .navigationSubtitle(session.specs.displayName)
        .toolbar {
            ToolbarItemGroup {
                Toggle(isOn: $showLog.animation()) {
                    Label("Журнал", systemImage: "terminal")
                }
                if session.isActive {
                    Button(role: .destructive) { session.cancel() } label: {
                        Label("Отменить", systemImage: "xmark.circle")
                    }
                }
            }
        }
    }

    // MARK: - Список шагов

    private var stepsColumn: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                IconTile(symbol: session.project.symbol, colors: session.project.colors, size: 40)
                VStack(alignment: .leading) {
                    Text(session.project.name).font(.headline)
                    Text(session.build?.version ?? "Из файла").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.bottom, 16)

            ForEach(Array(session.steps.enumerated()), id: \.element.id) { index, step in
                StepRow(step: step, isLast: index == session.steps.count - 1)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxHeight: .infinity, alignment: .top)
        .glassCard(cornerRadius: 24)
    }

    // MARK: - Карточка текущего действия

    @ViewBuilder
    private var focusCard: some View {
        if let outcome = session.outcome {
            OutcomeCard(outcome: outcome, session: session)
        } else if let prompt = session.prompt {
            PromptCard(prompt: prompt, session: session)
                .id(prompt.title)
                .transition(.asymmetric(insertion: .push(from: .bottom), removal: .opacity))
        } else {
            let step = session.steps[session.currentIndex]
            VStack(spacing: 18) {
                Image(systemName: step.symbol)
                    .font(.system(size: 54, weight: .light))
                    .foregroundStyle(.tint)
                    .symbolEffect(.pulse)
                Text(step.title).font(.title.weight(.semibold))
                if let progress = step.progress {
                    ProgressView(value: progress).frame(maxWidth: 420)
                } else {
                    ProgressView().controlSize(.large)
                }
                if let detail = step.detail {
                    Text(detail).font(.callout.monospacedDigit()).foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                Text("Всё происходит автоматически — просто не отключайте телефон.")
                    .font(.callout)
                    .foregroundStyle(.tertiary)
            }
            .padding(36)
            .frame(maxWidth: .infinity)
            .glassCard(cornerRadius: 28)
        }
    }
}

private struct StepRow: View {
    var step: InstallSession.Step
    var isLast: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                indicator
                    .frame(width: 26, height: 26)
                if !isLast {
                    Rectangle()
                        .fill(lineColor)
                        .frame(width: 2)
                        .frame(minHeight: 18, maxHeight: .infinity)
                }
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(step.title)
                    .font(.callout.weight(isCurrent ? .semibold : .regular))
                    .foregroundStyle(step.state == .pending ? .secondary : .primary)
                if let caption {
                    Text(caption)
                        .font(.caption)
                        .foregroundStyle(captionColor)
                        .lineLimit(3)
                }
                if isCurrent, let progress = step.progress {
                    ProgressView(value: progress).controlSize(.small)
                }
            }
            .padding(.top, 3)
            .padding(.bottom, 12)
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var isCurrent: Bool { step.state == .running || step.state == .waiting }

    @ViewBuilder
    private var indicator: some View {
        switch step.state {
        case .pending:
            Circle().strokeBorder(.quaternary, lineWidth: 2)
        case .running:
            ProgressView().controlSize(.small)
        case .waiting:
            Image(systemName: "hand.tap.fill")
                .foregroundStyle(.orange)
                .symbolEffect(.pulse)
        case .done:
            Image(systemName: "checkmark.circle.fill").font(.title3).foregroundStyle(.green)
        case .skipped:
            Image(systemName: "arrow.uturn.right.circle.fill").font(.title3).foregroundStyle(.secondary)
        case .failed:
            Image(systemName: "xmark.circle.fill").font(.title3).foregroundStyle(.red)
        }
    }

    private var lineColor: Color {
        switch step.state {
        case .done, .skipped: .green.opacity(0.5)
        default: .secondary.opacity(0.2)
        }
    }

    private var caption: String? {
        switch step.state {
        case .waiting: "Нужно ваше действие"
        case .skipped(let reason): reason
        case .failed(let message): message
        case .running: step.detail
        default: nil
        }
    }

    private var captionColor: Color {
        switch step.state {
        case .waiting: .orange
        case .failed: .red
        default: .secondary
        }
    }
}

private struct PromptCard: View {
    var prompt: InstallSession.Prompt
    var session: InstallSession

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 16) {
                Image(systemName: prompt.symbol)
                    .font(.system(size: 34))
                    .foregroundStyle(prompt.destructive ? Color.orange : Color.accentColor)
                    .symbolRenderingMode(.hierarchical)
                    .frame(width: 48)
                VStack(alignment: .leading, spacing: 4) {
                    Text(prompt.title).font(.title2.weight(.semibold))
                    Text(prompt.message).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            if !prompt.bullets.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(Array(prompt.bullets.enumerated()), id: \.offset) { index, bullet in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text("\(index + 1)")
                                .font(.caption.weight(.bold))
                                .frame(width: 20, height: 20)
                                .background(.tint.opacity(0.15), in: Circle())
                                .foregroundStyle(.tint)
                            Text(bullet).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.leading, 4)
            }

            HStack {
                if prompt.autoDetect {
                    HStack(spacing: 8) {
                        ProgressView().controlSize(.small)
                        Text("Отслеживаем автоматически…").font(.callout).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if let link = prompt.link {
                    Link(destination: link) { Label("Подробнее", systemImage: "arrow.up.right.square") }
                }
                if let secondary = prompt.secondary {
                    Button(secondary) { session.respond(.secondary) }
                        .glassButton()
                        .controlSize(.large)
                }
                if let primary = prompt.primary {
                    Button(primary) { session.respond(.primary) }
                        .prominentButton()
                        .tint(prompt.destructive ? Color.orange : nil)
                        .controlSize(.large)
                        .keyboardShortcut(.defaultAction)
                }
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard(cornerRadius: 28, tint: prompt.destructive ? .orange : nil)
    }
}

private struct OutcomeCard: View {
    @Environment(AppModel.self) private var model
    var outcome: InstallSession.Outcome
    var session: InstallSession

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: symbol)
                .font(.system(size: 64))
                .foregroundStyle(color)
                .symbolEffect(.bounce, value: outcome)
            Text(title).font(.largeTitle.weight(.bold))
            Text(message)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 480)
            HStack {
                if outcome != .success {
                    Button("Повторить с этого шага") { session.retry() }
                        .prominentButton()
                        .controlSize(.large)
                }
                Button("К прошивкам") {
                    model.selection = .roms
                    if !session.isActive { model.installSession = nil }
                }
                .glassButton()
                .controlSize(.large)
            }
        }
        .padding(36)
        .frame(maxWidth: .infinity)
        .glassCard(cornerRadius: 28, tint: color)
    }

    private var symbol: String {
        switch outcome {
        case .success: "checkmark.seal.fill"
        case .cancelled: "pause.circle.fill"
        case .failed: "exclamationmark.octagon.fill"
        }
    }

    private var color: Color {
        switch outcome {
        case .success: .green
        case .cancelled: .secondary
        case .failed: .red
        }
    }

    private var title: String {
        switch outcome {
        case .success: "Готово!"
        case .cancelled: "Установка остановлена"
        case .failed: "Что-то пошло не так"
        }
    }

    private var message: String {
        switch outcome {
        case .success: "\(session.project.name) установлена на \(session.specs.displayName). Наслаждайтесь новой системой."
        case .cancelled: "Можно продолжить с того же места — скачанные файлы сохранены."
        case .failed(let error): error
        }
    }
}

private struct LogView: View {
    var lines: [InstallSession.LogLine]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 2) {
                    ForEach(lines) { line in
                        Text(line.isCommand ? "$ \(line.text)" : line.text)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(line.isCommand ? Color.accentColor : .secondary)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .id(line.id)
                    }
                }
                .padding(14)
            }
            .onChange(of: lines.count) {
                if let last = lines.last { proxy.scrollTo(last.id, anchor: .bottom) }
            }
        }
        .frame(minHeight: 160, maxHeight: 260)
        .glassCard(cornerRadius: 18)
    }
}
