import SwiftUI

/// Первый запуск: знакомство → анализ Mac → подборка моделей → установка в один клик.
struct OnboardingView: View {
    @Environment(AppModel.self) private var model

    enum Stage { case welcome, scanning, results }

    @State var stage: Stage = .welcome
    @State private var revealed = 0
    @State private var selected: Set<String> = []

    var body: some View {
        ZStack {
            SectionBackground(section: .forYou, energy: stage == .scanning ? 1.2 : 0.3)
            Group {
                switch stage {
                case .welcome: welcome
                case .scanning: scanning
                case .results: results
                }
            }
            .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.97)), removal: .opacity))
        }
        .animation(.smooth(duration: 0.5), value: stage)
        .onAppear {
            guard DemoUI.autoScan, stage == .welcome else { return }
            Task {
                try? await Task.sleep(for: .seconds(1))
                startScan()
            }
        }
    }

    // MARK: Приветствие

    private var welcome: some View {
        VStack(spacing: 26) {
            NeuralOrb(energy: 0.35, palette: Section.chat.palette)
                .frame(width: 260, height: 260)
            VStack(spacing: 10) {
                Text("Synapse").font(.system(size: 44, weight: .bold, design: .rounded))
                Text("Нейросети прямо на вашем Mac — приватно, бесплатно и без интернета")
                    .font(.system(size: 17)).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            HStack(spacing: 22) {
                promise("lock.fill", "Всё остаётся на Mac", "Переписка никуда не отправляется")
                promise("cpu.fill", "Подбор под железо", "Только то, что пойдёт быстро")
                promise("arrow.down.circle.fill", "Установка в один клик", "Без терминала и настроек")
            }
            .frame(maxWidth: 760)
            Button {
                startScan()
            } label: {
                Text("Начать").font(.system(size: 16, weight: .semibold)).padding(.horizontal, 34).padding(.vertical, 5)
            }
            .buttonStyle(.glassProminent)
            .tint(Section.chat.tint)
            .controlSize(.extraLarge)
            .keyboardShortcut(.defaultAction)
        }
        .padding(40)
    }

    private func promise(_ symbol: String, _ title: String, _ text: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 22)).foregroundStyle(Section.chat.palette[1])
            Text(title).font(.system(size: 13, weight: .semibold))
            Text(text).font(.system(size: 11.5)).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
    }

    // MARK: Анализ

    private var facts: [(String, String, String)] {
        let m = model.mac
        return [
            ("cpu.fill", "Процессор", "\(m.chipShort) · \(m.performanceCores + m.efficiencyCores) ядер"),
            ("square.grid.3x3.fill", "Видеоядро", m.gpuCores > 0 ? "\(m.gpuCores) ядер · Metal" : "Metal"),
            ("memorychip.fill", "Объединённая память", "\(Int(m.memoryGB.rounded())) ГБ"),
            ("bolt.horizontal.fill", "Скорость памяти", "\(Int(m.bandwidth)) ГБ/с" + (m.bandwidthKnown ? "" : " (оценка)")),
            ("rectangle.stack.fill", "Доступно нейросетям", Format.gb(Double(Advisor.budget(m).comfortable)) + " с комфортом"),
            ("internaldrive.fill", "Свободно на диске", Format.bytes(m.freeDisk)),
        ]
    }

    private var scanning: some View {
        VStack(spacing: 28) {
            NeuralOrb(energy: 1.1, palette: Section.forYou.palette, progress: Double(revealed) / Double(facts.count))
                .frame(width: 230, height: 230)
            Text(revealed < facts.count ? "Знакомлюсь с вашим Mac…" : "Подбираю модели…")
                .font(.system(size: 22, weight: .bold))
                .contentTransition(.opacity)
            VStack(spacing: 8) {
                ForEach(Array(facts.enumerated()), id: \.offset) { i, f in
                    HStack(spacing: 12) {
                        Image(systemName: f.0).frame(width: 22).foregroundStyle(Section.forYou.palette[1])
                        Text(f.1).foregroundStyle(.secondary)
                        Spacer()
                        Text(f.2).font(.system(size: 13, weight: .semibold)).monospacedDigit()
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                    }
                    .font(.system(size: 13))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 9)
                    .glassEffect(.regular, in: .capsule)
                    .opacity(i < revealed ? 1 : 0)
                    .offset(y: i < revealed ? 0 : 8)
                }
            }
            .frame(width: 460)
        }
        .padding(40)
    }

    private func startScan() {
        stage = .scanning
        revealed = 0
        Task {
            async let catalog: Void = model.refreshCatalog()
            for i in 1...facts.count {
                try? await Task.sleep(for: .milliseconds(380))
                withAnimation(.snappy) { revealed = i }
            }
            await catalog
            await model.resolveRecommendedVariants()
            try? await Task.sleep(for: .milliseconds(500))
            selected = Set(model.recommendations.filter { $0.role == .balanced }.map(\.card.id))
            stage = .results
        }
    }

    // MARK: Подборка

    private var results: some View {
        let recs = model.recommendations
        return ScrollView {
            VStack(spacing: 22) {
                VStack(spacing: 8) {
                    Text("Вот что подойдёт вашему Mac").font(.system(size: 30, weight: .bold))
                    Text("\(model.mac.chipShort) · " + model.mac.summary)
                        .font(.system(size: 14)).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 640)
                }
                .padding(.top, 30)

                if recs.isEmpty {
                    ContentUnavailableView("Не нашёл подходящих моделей", systemImage: "exclamationmark.triangle",
                                           description: Text("Похоже, на диске мало места или памяти совсем немного. Загляните в каталог — там видно, что и почему не подходит."))
                } else {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .top, spacing: 16) { cards(recs) }.frame(maxWidth: 1180)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 16)], spacing: 16) { cards(recs) }
                    }
                    .padding(.horizontal, 30)
                }

                VStack(spacing: 10) {
                    let size = recs.filter { selected.contains($0.card.id) }.reduce(Int64(0)) { $0 + $1.variant.size }
                    Button {
                        install(recs)
                    } label: {
                        Text(selected.isEmpty ? "Выберите модель" : "Установить · \(Format.bytes(size))")
                            .font(.system(size: 16, weight: .semibold)).padding(.horizontal, 30).padding(.vertical, 5)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(Section.forYou.tint)
                    .controlSize(.extraLarge)
                    .disabled(selected.isEmpty)
                    .keyboardShortcut(.defaultAction)
                    Button("Пропустить — посмотрю каталог") {
                        model.finishOnboarding()
                        model.section = .catalog
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    .font(.system(size: 12.5))
                    Text("Модели скачиваются с Hugging Face и проверяются по контрольной сумме. Пока идёт загрузка, можно пользоваться приложением.")
                        .font(.system(size: 11)).foregroundStyle(.tertiary).multilineTextAlignment(.center).frame(maxWidth: 520)
                }
                .padding(.bottom, 30)
            }
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.never)
    }

    @ViewBuilder
    private func cards(_ recs: [Advisor.Recommendation]) -> some View {
        ForEach(recs) { rec in
            RecommendationCard(rec: rec, selected: selected.contains(rec.card.id), highlighted: rec.role == .balanced) {
                if selected.contains(rec.card.id) { selected.remove(rec.card.id) } else { selected.insert(rec.card.id) }
            }
        }
    }

    private func install(_ recs: [Advisor.Recommendation]) {
        for rec in recs where selected.contains(rec.card.id) {
            model.install(rec.card, variant: rec.variant.estimated ? nil : rec.variant)
        }
        model.finishOnboarding()
        model.section = .chat
    }
}

/// Карточка рекомендации: роль, модель, размер, скорость, почему.
struct RecommendationCard: View {
    let rec: Advisor.Recommendation
    var selected = false
    var highlighted = false
    var toggle: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(rec.role.title, systemImage: rec.role.symbol)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(highlighted ? .white : .secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(highlighted ? AnyShapeStyle(Section.forYou.tint.gradient) : AnyShapeStyle(Color.primary.opacity(0.07))))
                Spacer()
                if let toggle {
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: 20))
                        .foregroundStyle(selected ? Section.forYou.tint : .secondary)
                        .onTapGesture(perform: toggle)
                }
            }
            HStack(spacing: 12) {
                ModelAvatar(lab: rec.card.lab, size: 46)
                VStack(alignment: .leading, spacing: 2) {
                    Text(rec.card.name).font(.system(size: 18, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
                    Text("\(rec.card.lab) · \(rec.card.paramsText)").font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Text(rec.card.summary).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(4)
                .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: 0)
            Divider().opacity(0.5)
            HStack(spacing: 10) {
                FitBadge(fit: rec.fit)
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(Format.bytes(rec.variant.size) + (rec.variant.estimated ? "*" : "")).font(.system(size: 13, weight: .semibold)).monospacedDigit()
                    Text("≈\(Int(rec.fit.tokensPerSecond.rounded())) ток/с · \(rec.variant.quant.name)").font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
                }
            }
            Text(rec.role.subtitle).font(.system(size: 11)).foregroundStyle(.tertiary)
        }
        .padding(18)
        .frame(minWidth: 260, maxWidth: 360, minHeight: 300, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.background.opacity(selected ? 0.75 : 0.5)))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
            .strokeBorder(selected ? Section.forYou.tint.opacity(0.8) : .primary.opacity(0.08), lineWidth: selected ? 2 : 1))
        .shadow(color: selected ? Section.forYou.tint.opacity(0.25) : .clear, radius: 18, y: 6)
        .contentShape(RoundedRectangle(cornerRadius: 24))
        .onTapGesture { toggle?() }
        .animation(.snappy, value: selected)
    }
}
