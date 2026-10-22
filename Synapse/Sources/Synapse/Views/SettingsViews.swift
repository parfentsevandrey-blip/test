import SwiftUI

/// Строка настройки: название, значение, сам переключатель и объяснение простыми словами.
struct SettingBlock<Control: View>: View {
    let title: String
    var value: String? = nil
    let explain: String
    var compact = false
    @ViewBuilder var control: Control

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(title).font(.system(size: 12.5, weight: .semibold))
                Spacer()
                if let value { Text(value).monospacedDigit().foregroundStyle(.secondary) }
            }
            control
            Text(explain)
                .font(.system(size: compact ? 10.5 : 11))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Готовые наборы настроек ответа.
enum AnswerPreset: String, CaseIterable, Identifiable {
    case precise, balanced, creative
    var id: String { rawValue }

    var title: String {
        switch self {
        case .precise: "Точно"
        case .balanced: "Сбалансированно"
        case .creative: "Творчески"
        }
    }

    var hint: String {
        switch self {
        case .precise: "Факты, код, перевод, расчёты"
        case .balanced: "Обычное общение"
        case .creative: "Идеи, истории, стихи"
        }
    }

    func apply(_ s: inout GenerationSettings) {
        switch self {
        case .precise: (s.temperature, s.topP, s.topK, s.minP, s.repeatPenalty) = (0.2, 0.9, 40, 0.1, 1.0)
        case .balanced: (s.temperature, s.topP, s.topK, s.minP, s.repeatPenalty) = (0.7, 0.95, 40, 0.05, 1.0)
        case .creative: (s.temperature, s.topP, s.topK, s.minP, s.repeatPenalty) = (1.0, 0.98, 100, 0.03, 1.05)
        }
    }

    func matches(_ s: GenerationSettings) -> Bool {
        var copy = s
        apply(&copy)
        return copy == s
    }
}

/// Как модель отвечает: настройки выбора слов, длины и размышлений. В окне Настроек и во всплывающей панели чата.
struct GenerationSettingsView: View {
    @Environment(AppModel.self) private var model
    var compact = false

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 16) {
            Text("Как отвечать").font(.system(size: 15, weight: .bold))
            presets

            SettingBlock(title: "Творчество (температура)", value: String(format: "%.2f", model.settings.temperature),
                         explain: "Насколько смело модель выбирает слова. Низкая — предсказуемо и одинаково: хорошо для фактов, кода и перевода. Высокая — разнообразно и неожиданно: хорошо для идей и стихов, но чаще ошибки и выдумки. 0 — на один и тот же вопрос всегда один ответ.",
                         compact: compact) {
                Slider(value: $model.settings.temperature, in: 0...1.5, step: 0.05)
            }

            SettingBlock(title: "Длина ответа", value: "до \(model.settings.maxTokens) токенов · ≈\(words(model.settings.maxTokens)) слов",
                         explain: "Сколько модель может написать за один раз. Токен — кусочек слова: в русском тексте примерно 2–3 токена на слово. Размышления тоже расходуют токены. Если ответ обрывается на полуслове — увеличьте.",
                         compact: compact) {
                Slider(value: Binding(get: { Double(model.settings.maxTokens) }, set: { model.settings.maxTokens = Int($0) }), in: 256...32768, step: 256)
            }

            SettingBlock(title: "Размышлять перед ответом",
                         explain: "Модели с режимом размышлений сначала рассуждают «про себя» (это видно в сворачиваемом блоке), потом отвечают. Заметно точнее в логике, математике и коде, но ответ начинается позже. Для болтовни и простых вопросов можно выключить. На модели без этого режима не влияет.",
                         compact: compact) {
                Toggle("Включено", isOn: $model.settings.thinking)
            }

            Divider()
            Text("Тонкая настройка выбора слов").font(.system(size: 13, weight: .bold))
            Text("Модель пишет текст по одному кусочку: на каждом шаге у неё есть список вероятных продолжений. Эти настройки решают, из какой части списка выбирать.")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)

            SettingBlock(title: "Отсев маловероятного (Min-P)", value: String(format: "%.2f", model.settings.minP),
                         explain: "Отбрасывает варианты, которые сильно хуже лучшего: 0,05 значит «не меньше 5% от вероятности самого вероятного слова». Главный помощник высокой температуры: текст остаётся связным. 0 — выключено.",
                         compact: compact) {
                Slider(value: $model.settings.minP, in: 0...0.3, step: 0.01)
            }

            SettingBlock(title: "Ядро выбора (Top-P)", value: String(format: "%.2f", model.settings.topP),
                         explain: "Модель выбирает только из самых вероятных слов, которые вместе набирают эту долю вероятности. 0,9 — отрезает «хвост» странных вариантов, 1 — без ограничения. Уменьшите, если при высокой температуре появляется бессмыслица.",
                         compact: compact) {
                Slider(value: $model.settings.topP, in: 0.5...1, step: 0.01)
            }

            SettingBlock(title: "Число кандидатов (Top-K)", value: model.settings.topK == 0 ? "без ограничения" : "\(model.settings.topK)",
                         explain: "Сколько самых вероятных вариантов следующего слова вообще рассматривать. 40 — обычно. Меньше — суше и надёжнее, больше — богаче словарь. 0 — без ограничения.",
                         compact: compact) {
                Slider(value: Binding(get: { Double(model.settings.topK) }, set: { model.settings.topK = Int($0) }), in: 0...200, step: 5)
            }

            SettingBlock(title: "Штраф за повторы", value: model.settings.repeatPenalty <= 1.001 ? "выключен" : String(format: "%.2f", model.settings.repeatPenalty),
                         explain: "Если модель зацикливается и повторяет одни и те же фразы — поднимите до 1,05–1,15. Слишком сильный штраф портит текст: модель начинает избегать даже нужных слов, например названий из вашего вопроса. 1 — выключен.",
                         compact: compact) {
                Slider(value: $model.settings.repeatPenalty, in: 1...1.5, step: 0.01)
            }

            SettingBlock(title: "Повторяемость",
                         explain: "Фиксирует случайность: тот же вопрос с теми же настройками даст тот же ответ. Удобно, чтобы сравнить модели или настройки. Выключено — каждый раз немного по-новому.",
                         compact: compact) {
                Toggle("Одинаковый ответ на одинаковый вопрос", isOn: Binding(
                    get: { model.settings.seed != GenerationSettings().seed },
                    set: { model.settings.seed = $0 ? 42 : GenerationSettings().seed }))
            }

            if model.currentChat != nil {
                Divider()
                SettingBlock(title: "Инструкция для этого чата",
                             explain: "Постоянное указание, которое модель видит перед каждым ответом в этом чате: роль, тон, формат. Например: «Ты редактор. Отвечай кратко, по пунктам».",
                             compact: compact) {
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
            Button("Вернуть всё как было") { model.settings = GenerationSettings() }.buttonStyle(.glass)
        }
        .font(.system(size: 12.5))
    }

    private var presets: some View {
        HStack(spacing: 8) {
            ForEach(AnswerPreset.allCases) { p in
                let on = p.matches(model.settings)
                Button {
                    var s = model.settings
                    p.apply(&s)
                    model.settings = s
                } label: {
                    VStack(spacing: 2) {
                        Text(p.title).font(.system(size: 12, weight: .semibold))
                        Text(p.hint).font(.system(size: 9.5)).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 2)
                }
                .buttonStyle(.glass)
                .tint(on ? Section.chat.tint : nil)
                .background(Capsule().fill(on ? Section.chat.tint.opacity(0.2) : .clear))
            }
        }
    }

    private func words(_ tokens: Int) -> Int { Int((Double(tokens) / 2.5 / 10).rounded()) * 10 }
}

/// Как модель живёт в памяти: контекст, сжатие его памяти, видеоядро, предел памяти видеоядра.
struct EngineSettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 16) {
            Text("Модель в памяти").font(.system(size: 15, weight: .bold))
            Text("Эти настройки применяются, когда модель загружается. После изменений нажмите «Перезагрузить модель».")
                .font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)

            SettingBlock(title: "Контекст — память разговора", value: contextValue,
                         explain: "Сколько текста модель держит в голове одновременно: переписка, инструкция, картинки. Если разговор длиннее, начало забывается. Больше контекст — больше памяти и немного медленнее чтение длинных сообщений. «Авто» — Synapse выбирает сам и уменьшает контекст, если модель еле помещается.") {
                Picker("", selection: $model.engineSettings.context) {
                    ForEach(EngineSettings.contextChoices, id: \.self) { c in
                        Text(c == 0 ? "Авто" : "\(c / 1024)K · \(contextCost(c))").tag(c)
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
            }

            SettingBlock(title: "Сжатие памяти контекста",
                         explain: "Хранит память разговора (KV-кэш) в 8 битах вместо 16: вдвое меньше памяти на контекст, качество ответов почти не меняется. Полезно для длинного контекста и для больших моделей «на пределе». «Авто» — включается само, когда тесно.") {
                Picker("", selection: $model.engineSettings.compressKV) {
                    Text("Авто").tag(Bool?.none)
                    Text("Всегда").tag(Bool?.some(true))
                    Text("Никогда").tag(Bool?.some(false))
                }
                .labelsHidden()
                .pickerStyle(.segmented)
            }

            SettingBlock(title: "Видеоядро (Metal)",
                         explain: "Модель считается на видеоядре — это в разы быстрее процессора. Выключайте, только если на видеоядре модель сбоит: ответы станут медленнее в 3–10 раз.") {
                Toggle("Считать на видеоядре", isOn: $model.engineSettings.gpu)
            }

            GPUMemoryBlock()

            if let m = model.selectedModel {
                Divider()
                let plan = model.loadPlan(m)
                VStack(alignment: .leading, spacing: 6) {
                    Text("«\(m.name)» с этими настройками").font(.system(size: 12.5, weight: .semibold))
                    HStack(spacing: 8) {
                        FitBadge(fit: plan.fit, compact: true)
                        Text("контекст \(plan.context / 1024)K · память контекста \(plan.compressKV ? "8 бит" : "16 бит") · займёт ≈\(Format.gb(Double(plan.fit.memory)))")
                            .font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                    if let info = model.modelInfo {
                        Text("Сейчас загружена: контекст \(info.context / 1024)K, память контекста \(info.compressedKV ? "8 бит" : "16 бит"), \(info.gpu ? "видеоядро" : "процессор")")
                            .font(.system(size: 11)).foregroundStyle(.tertiary)
                    }
                    Button("Перезагрузить модель") { model.reloadModel() }
                        .buttonStyle(.glass)
                        .disabled(model.isGenerating)
                }
            }
            Button("Вернуть всё как было") { model.engineSettings = EngineSettings() }.buttonStyle(.glass)
        }
        .font(.system(size: 12.5))
    }

    private var contextValue: String {
        if model.engineSettings.context == 0, let m = model.selectedModel { return "сейчас \(model.loadPlan(m).context / 1024)K" }
        return model.engineSettings.context == 0 ? "авто" : "\(model.engineSettings.context / 1024)K"
    }

    /// Сколько памяти займёт контекст у выбранной модели.
    private func contextCost(_ tokens: Int) -> String {
        guard let m = model.selectedModel else { return "\(tokens / 1024)K" }
        return "≈" + Format.gb(Advisor.kvPerToken(params: m.params) * Double(tokens))
    }
}

/// Предел памяти видеоядра: сколько macOS разрешает ему занять и кнопка «отдать больше».
struct GPUMemoryBlock: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        SettingBlock(title: "Память для видеоядра", value: "до \(Format.gb(Double(model.mac.gpuBudget))) из \(Format.gb(Double(model.mac.memory)))",
                     explain: "macOS по умолчанию отдаёт видеоядру около двух третей памяти, остальное держит для себя и программ. Предел можно поднять — тогда поместятся модели крупнее. Действует до перезагрузки Mac, после всё вернётся само. Пока работает большая модель, тяжёлые программы лучше закрыть.") {
            HStack(spacing: 8) {
                if let target = GPUMemory.target(model.mac) {
                    Button("Отдать видеоядру до \(Format.gb(Double(target)))") { model.setGPUMemory(raise: true) }
                        .buttonStyle(.glassProminent)
                        .tint(Section.catalog.tint)
                }
                if GPUMemory.raisedLimit != nil {
                    Label("Предел поднят", systemImage: "bolt.fill").font(.system(size: 11, weight: .semibold)).foregroundStyle(.orange)
                    Button("Вернуть стандартный") { model.setGPUMemory(raise: false) }.buttonStyle(.glass)
                }
                if model.gpuMemoryBusy { ProgressView().controlSize(.small) }
            }
        }
    }
}

/// Поведение окна чата.
struct ChatBehaviourSettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 16) {
            Text("Чат").font(.system(size: 15, weight: .bold))
            SettingBlock(title: "Прокручивать за ответом",
                         explain: "Выключено: пока модель пишет, окно стоит на месте — ваш вопрос поднимается наверх, ответ пишется под ним, листать вверх можно свободно. Кнопка «Ответ продолжается ниже» вернёт к концу. Включено: окно всё время едет за новым текстом.") {
                Toggle("Прокручивать", isOn: $model.followReply)
            }
        }
        .font(.system(size: 12.5))
    }
}
