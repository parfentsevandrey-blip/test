import AppKit
import SwiftUI

/// Чат: список разговоров слева, переписка и поле ввода справа.
struct ChatView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 0) {
            ConversationList()
                .frame(width: 236)
            Divider().opacity(0.4)
            ChatPane()
        }
    }
}

struct ConversationList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 0) {
            Button {
                model.newChat()
            } label: {
                Label("Новый чат", systemImage: "square.and.pencil").frame(maxWidth: .infinity)
            }
            .buttonStyle(.glass)
            .controlSize(.large)
            .keyboardShortcut("n")
            .padding(12)

            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(model.conversations) { c in
                        ConversationRow(chat: c, selected: c.id == model.currentChatID)
                            .onTapGesture { model.currentChatID = c.id }
                            .contextMenu {
                                Button("Удалить чат", role: .destructive) { model.deleteChat(c) }
                            }
                    }
                }
                .padding(.horizontal, 8)
            }
            .scrollIndicators(.never)
        }
        .background(.background.opacity(0.35))
    }
}

struct ConversationRow: View {
    let chat: Conversation
    let selected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(chat.title).font(.system(size: 12.5, weight: .medium)).lineLimit(1)
            Text(preview).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(selected ? Section.chat.tint.opacity(0.18) : .clear))
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }

    private var preview: String {
        guard let last = chat.messages.last else { return "Пусто" }
        let text = last.text.isEmpty ? (last.error ?? "…") : last.text
        return Format.relative(chat.updated) + " · " + text.replacingOccurrences(of: "\n", with: " ")
    }
}

struct ChatPane: View {
    @Environment(AppModel.self) private var model
    @State private var draft = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            ChatTopBar()
            if model.installed.isEmpty {
                NoModelState()
            } else if let chat = model.currentChat, !chat.messages.isEmpty {
                messages(chat)
            } else {
                EmptyChatState { draft = $0; send() }
            }
            composer
        }
        .onAppear { focused = true }
    }

    private func messages(_ chat: Conversation) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    ForEach(chat.messages) { m in
                        MessageView(message: m, streaming: m.id == model.streamingID, isLast: m.id == chat.messages.last?.id)
                            .id(m.id)
                    }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 18)
                .frame(maxWidth: 900)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.automatic)
            .onChange(of: chat.messages.last?.text) { _, _ in
                if model.isGenerating { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            .onChange(of: chat.messages.count) { _, _ in
                withAnimation(.smooth) { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            .onAppear { proxy.scrollTo("bottom", anchor: .bottom) }
        }
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField(placeholder, text: $draft, axis: .vertical)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .lineLimit(1...8)
                .focused($focused)
                .onSubmit(send)
                .padding(.vertical, 6)
                .disabled(model.installed.isEmpty)
            if model.isGenerating {
                Button { model.stopGenerating() } label: {
                    Image(systemName: "stop.fill").font(.system(size: 13, weight: .bold)).frame(width: 30, height: 30)
                }
                .buttonStyle(.glassProminent)
                .tint(.red)
                .buttonBorderShape(.circle)
                .keyboardShortcut(".", modifiers: .command)
                .help("Остановить (⌘.)")
            } else {
                Button(action: send) {
                    Image(systemName: "arrow.up").font(.system(size: 14, weight: .bold)).frame(width: 30, height: 30)
                }
                .buttonStyle(.glassProminent)
                .tint(Section.chat.tint)
                .buttonBorderShape(.circle)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.installed.isEmpty)
            }
        }
        .padding(.leading, 18)
        .padding(.trailing, 8)
        .padding(.vertical, 8)
        .glassEffect(.regular, in: .rect(cornerRadius: 26))
        .padding(.horizontal, 24)
        .padding(.bottom, 16)
        .padding(.top, 6)
        .frame(maxWidth: 900)
    }

    private var placeholder: String {
        if model.installed.isEmpty { return "Сначала установите модель" }
        return "Спросите что угодно…  ⏎ — отправить, ⌥⏎ — новая строка"
    }

    private func send() {
        let text = draft
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !model.isGenerating else { return }
        draft = ""
        model.send(text)
    }
}

/// Верхняя панель: выбор модели, её состояние и настройки ответа.
struct ChatTopBar: View {
    @Environment(AppModel.self) private var model
    @State private var showSettings = false

    var body: some View {
        HStack(spacing: 12) {
            if let m = model.selectedModel { ModelAvatar(lab: m.lab, size: 26) }
            Menu {
                ForEach(model.installed) { m in
                    Button {
                        model.select(m)
                    } label: {
                        Text("\(m.name) · \(m.quant) · \(Format.bytes(m.size))")
                    }
                }
                Divider()
                Button("Установить другие…") { model.section = .forYou }
            } label: {
                Text(model.selectedModel.map { "\($0.name) · \($0.quant)" } ?? "Выберите модель").font(.system(size: 13, weight: .semibold))
            }
            .menuStyle(.button)
            .buttonStyle(.glass)
            .fixedSize()
            .disabled(model.installed.isEmpty)

            status
            Spacer()
            if model.isGenerating && model.liveTokensPerSecond > 0 {
                Label("\(Int(model.liveTokensPerSecond.rounded())) ток/с", systemImage: "speedometer")
                    .font(.system(size: 11.5, weight: .medium)).monospacedDigit().foregroundStyle(.secondary)
                    .contentTransition(.numericText())
            }
            if model.modelInfo?.format.thinkingControl == true {
                Toggle(isOn: Binding(get: { model.settings.thinking }, set: { model.settings.thinking = $0 })) {
                    Label("Размышлять", systemImage: "brain")
                }
                .toggleStyle(.button)
                .buttonStyle(.glass)
                .help("Модель сначала думает, потом отвечает: точнее, но дольше")
            }
            Button { showSettings.toggle() } label: { Image(systemName: "slider.horizontal.3") }
                .buttonStyle(.glass)
                .popover(isPresented: $showSettings, arrowEdge: .bottom) { GenerationSettingsView().padding(18).frame(width: 340) }
                .help("Настройки ответа")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }

    @ViewBuilder
    private var status: some View {
        switch model.engineState {
        case .loading(let p):
            HStack(spacing: 6) {
                ProgressView(value: p).frame(width: 80)
                Text("Загружаю в память…").font(.system(size: 11.5)).foregroundStyle(.secondary)
            }
        case .ready:
            if let info = model.modelInfo {
                Text("\(info.gpu ? "Metal" : "CPU") · контекст \(info.context / 1024)K").font(.system(size: 11)).foregroundStyle(.tertiary)
            }
        case .failed(let why):
            Label(why, systemImage: "exclamationmark.triangle.fill").font(.system(size: 11)).foregroundStyle(.red).lineLimit(1)
        case .idle:
            EmptyView()
        }
    }
}

struct GenerationSettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 14) {
            Text("Как отвечать").font(.system(size: 15, weight: .bold))
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Творчество")
                    Spacer()
                    Text(String(format: "%.1f", model.settings.temperature)).monospacedDigit().foregroundStyle(.secondary)
                }
                Slider(value: $model.settings.temperature, in: 0...1.5, step: 0.1)
                Text("0 — точно и одинаково, 1+ — смелее и разнообразнее").font(.system(size: 10.5)).foregroundStyle(.tertiary)
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Длина ответа")
                    Spacer()
                    Text("до \(model.settings.maxTokens) токенов").monospacedDigit().foregroundStyle(.secondary)
                }
                Slider(value: Binding(get: { Double(model.settings.maxTokens) }, set: { model.settings.maxTokens = Int($0) }), in: 256...16384, step: 256)
            }
            Toggle("Размышлять перед ответом (если модель умеет)", isOn: $model.settings.thinking)
            if model.currentChat != nil {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Инструкция для этого чата").font(.system(size: 12, weight: .semibold))
                    TextEditor(text: Binding(get: { model.currentChat?.systemPrompt ?? "" }, set: { value in
                        if var c = model.currentChat { c.systemPrompt = value; model.currentChat = c }
                    }))
                        .font(.system(size: 12))
                        .frame(height: 80)
                        .scrollContentBackground(.hidden)
                        .padding(6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(.primary.opacity(0.05)))
                }
            }
            Button("Сбросить") { model.settings = GenerationSettings() }.buttonStyle(.glass)
        }
        .font(.system(size: 12.5))
    }
}

/// Одно сообщение: вопрос справа, ответ слева с размышлениями и статистикой.
struct MessageView: View {
    @Environment(AppModel.self) private var model
    let message: ChatMessage
    let streaming: Bool
    let isLast: Bool
    @State private var showReasoning = false

    var body: some View {
        if message.role == .user {
            HStack {
                Spacer(minLength: 80)
                Text(message.text)
                    .font(.system(size: 14))
                    .textSelection(.enabled)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(LinearGradient(colors: [Section.chat.palette[0].opacity(0.85), Section.chat.palette[1].opacity(0.85)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    .foregroundStyle(.white)
            }
        } else {
            HStack(alignment: .top, spacing: 12) {
                NeuralOrb(energy: streaming ? 1.2 : 0.15, palette: Section.chat.palette)
                    .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 10) {
                    if let reasoning = message.reasoning, !reasoning.isEmpty {
                        reasoningView(reasoning)
                    }
                    if !message.text.isEmpty {
                        MarkdownText(text: message.text)
                    } else if streaming, message.reasoning == nil {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text(model.streamingPhase ?? "Думаю…").font(.system(size: 12.5)).foregroundStyle(.secondary)
                        }
                    }
                    if let error = message.error {
                        Label(error, systemImage: "exclamationmark.triangle.fill").font(.system(size: 12.5)).foregroundStyle(.red)
                    }
                    if !streaming { footer }
                }
                Spacer(minLength: 0)
            }
        }
    }

    private func reasoningView(_ text: String) -> some View {
        let open = streaming && message.text.isEmpty
        return VStack(alignment: .leading, spacing: 6) {
            Button {
                withAnimation(.snappy) { showReasoning.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "brain").symbolEffect(.pulse, isActive: open)
                    Text(open ? "Размышляю…" : "Размышления" + (message.thinkingSeconds.map { " · \(Format.duration($0))" } ?? ""))
                    Image(systemName: "chevron.right").rotationEffect(.degrees(showReasoning || open ? 90 : 0)).font(.system(size: 9, weight: .bold))
                }
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            if showReasoning || open {
                Text(open ? String(text.suffix(1200)) : text)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
                    .padding(.leading, 12)
                    .overlay(alignment: .leading) { Capsule().fill(.secondary.opacity(0.35)).frame(width: 2) }
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    @ViewBuilder
    private var footer: some View {
        HStack(spacing: 12) {
            if let s = message.stats {
                Text("\(Int(s.tokensPerSecond.rounded())) ток/с · \(s.tokens) ток. · \(Format.duration(s.seconds + s.promptSeconds))")
                    .monospacedDigit()
                if s.stoppedByLimit { Text("· обрезано по длине").foregroundStyle(.orange) }
            }
            if let name = message.model { Text(name).foregroundStyle(.tertiary) }
            Spacer()
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(message.text, forType: .string)
            } label: { Image(systemName: "doc.on.doc") }
                .buttonStyle(.plain)
                .help("Скопировать ответ")
            if isLast {
                Button { model.regenerate() } label: { Image(systemName: "arrow.clockwise") }
                    .buttonStyle(.plain)
                    .help("Ответить заново")
            }
        }
        .font(.system(size: 11))
        .foregroundStyle(.secondary)
    }
}

/// Пустой чат: сфера и подсказки, с чего начать.
struct EmptyChatState: View {
    @Environment(AppModel.self) private var model
    let suggest: (String) -> Void

    private let ideas: [(String, String)] = [
        ("lightbulb.fill", "Объясни простыми словами, как работает квантовый компьютер"),
        ("envelope.fill", "Напиши вежливое письмо с просьбой перенести встречу на четверг"),
        ("chevron.left.forwardslash.chevron.right", "Напиши на Swift функцию, которая проверяет, является ли строка палиндромом"),
        ("globe", "Переведи на английский: «Сделано с любовью на моём Mac»"),
    ]

    var body: some View {
        VStack(spacing: 22) {
            Spacer(minLength: 10)
            NeuralOrb(energy: engineEnergy, palette: Section.chat.palette, progress: loadingProgress)
                .frame(width: 200, height: 200)
            VStack(spacing: 6) {
                Text(headline).font(.system(size: 26, weight: .bold))
                Text(subline).font(.system(size: 13)).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 10)], spacing: 10) {
                ForEach(ideas, id: \.1) { icon, text in
                    Button { suggest(text) } label: {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: icon).foregroundStyle(Section.chat.palette[1]).frame(width: 18)
                            Text(text).font(.system(size: 12.5)).multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, minHeight: 58, alignment: .topLeading)
                        .glassEffect(.regular, in: .rect(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: 640)
            Spacer(minLength: 10)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var loadingProgress: Double? {
        if case .loading(let p) = model.engineState { return p }
        return nil
    }

    private var engineEnergy: Double {
        if case .loading = model.engineState { return 1 }
        return 0.35
    }

    private var headline: String {
        if case .loading = model.engineState { return "Загружаю модель…" }
        return "Чем помочь?"
    }

    private var subline: String {
        guard let m = model.selectedModel else { return "Выберите модель вверху" }
        return "\(m.name) работает прямо на вашем Mac — ничего не уходит в интернет."
    }
}

/// Моделей ещё нет: показываем загрузку или зовём выбрать.
struct NoModelState: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            if let job = model.downloads.first(where: { $0.isActive || $0.state == .paused }) {
                NeuralOrb(energy: 0.9, palette: Section.library.palette, progress: job.fraction)
                    .frame(width: 210, height: 210)
                Text("Скачиваю «\(job.card.name)»").font(.system(size: 24, weight: .bold))
                VStack(spacing: 8) {
                    GlowBar(fraction: job.fraction).frame(width: 360)
                    Text(progressText(job)).font(.system(size: 12)).foregroundStyle(.secondary).monospacedDigit()
                }
                Text("Как только загрузка закончится, модель проверится и сразу будет готова к разговору.")
                    .font(.system(size: 12.5)).foregroundStyle(.tertiary)
            } else {
                NeuralOrb(energy: 0.25, palette: Section.forYou.palette).frame(width: 190, height: 190)
                Text("Установите первую модель").font(.system(size: 24, weight: .bold))
                Text("Synapse уже подобрал модели под ваш Mac — осталось нажать одну кнопку.")
                    .font(.system(size: 13)).foregroundStyle(.secondary)
                if let rec = model.recommendations.first {
                    Button {
                        model.install(rec.card, variant: rec.variant.estimated ? nil : rec.variant)
                    } label: {
                        Text("Установить «\(rec.card.name)» · \(Format.bytes(rec.variant.size))")
                            .font(.system(size: 15, weight: .semibold)).padding(.horizontal, 20).padding(.vertical, 4)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(Section.forYou.tint)
                    .controlSize(.extraLarge)
                }
                Button("Посмотреть все рекомендации") { model.section = .forYou }.buttonStyle(.glass)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func progressText(_ job: DownloadJob) -> String {
        let eta = job.eta.isFinite ? " · осталось \(Format.duration(job.eta))" : ""
        return "\(Int(job.fraction * 100))% · \(Format.bytes(job.received)) из \(Format.bytes(job.total)) · \(Format.speed(job.speed))\(eta)"
    }
}
