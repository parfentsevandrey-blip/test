import SwiftUI

/// «Для этого Mac»: что за железо, что оно тянет, что ставить и чего не ставить.
struct ForYouView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                SectionHeader(title: "Для этого Mac", subtitle: "Подбор по памяти, скорости памяти и видеоядру — только то, что будет работать хорошо.") {
                    if model.catalogLoading { ProgressView().controlSize(.small) }
                }
                .padding(.horizontal, -28)

                hardware
                recommendations
                tooBig
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.never)
        .task { await model.resolveRecommendedVariants() }
    }

    private var hardware: some View {
        let m = model.mac
        let budget = Advisor.budget(m)
        return ContentCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center, spacing: 18) {
                    NeuralOrb(energy: 0.25, palette: Section.forYou.palette)
                        .frame(width: 96, height: 96)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(m.chipShort).font(.system(size: 30, weight: .bold, design: .rounded))
                        Text(m.summary).font(.system(size: 13)).foregroundStyle(.secondary)
                        if m.virtualized {
                            Label("Виртуальная машина: модели работают на процессоре, медленнее обычного", systemImage: "exclamationmark.triangle.fill")
                                .font(.system(size: 11.5)).foregroundStyle(.orange)
                        }
                    }
                    Spacer()
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 10)], spacing: 10) {
                    StatTile(symbol: "memorychip.fill", title: "Объединённая память", value: "\(Int(m.memoryGB.rounded())) ГБ",
                             detail: "Видеоядру доступно ≈\(Format.gb(Double(m.gpuBudget)))", tint: Section.forYou.palette[0])
                    StatTile(symbol: "bolt.horizontal.fill", title: "Скорость памяти", value: "\(Int(m.bandwidth)) ГБ/с",
                             detail: m.bandwidthKnown ? "Главное для скорости ответа" : "Оценка для нового чипа", tint: Section.forYou.palette[1])
                    StatTile(symbol: "cpu.fill", title: "Процессор", value: "\(m.performanceCores) + \(m.efficiencyCores) ядер",
                             detail: "производительные + эффективные", tint: Section.forYou.palette[2])
                    StatTile(symbol: "square.grid.3x3.fill", title: "Видеоядро", value: m.gpuCores > 0 ? "\(m.gpuCores) ядер" : "Apple GPU",
                             detail: "Metal · \(m.gpuName)", tint: Section.chat.palette[1])
                    StatTile(symbol: "brain.head.profile", title: "Для моделей с комфортом", value: Format.gb(Double(budget.comfortable)),
                             detail: "на пределе — до \(Format.gb(Double(budget.hard)))", tint: Section.chat.palette[0])
                    StatTile(symbol: "internaldrive.fill", title: "Свободно на диске", value: Format.bytes(m.freeDisk),
                             detail: "Модели: \(Format.bytes(Library.diskUsage(model.installed)))", tint: Section.library.palette[0])
                }
            }
        }
    }

    private var recommendations: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Рекомендуем установить").font(.system(size: 18, weight: .bold))
            let recs = model.recommendations
            if recs.isEmpty {
                Text("Подходящих моделей не нашлось — проверьте свободное место на диске.").foregroundStyle(.secondary)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 270), spacing: 14)], spacing: 14) {
                    ForEach(recs) { rec in
                        VStack(spacing: 0) {
                            RecommendationCard(rec: rec, highlighted: rec.role == .balanced)
                                .onTapGesture { model.detailCard = rec.card }
                            HStack {
                                Button("Подробнее") { model.detailCard = rec.card }.buttonStyle(.glass)
                                Spacer()
                                InstallButton(card: rec.card, variant: rec.variant.estimated ? nil : rec.variant)
                            }
                            .padding(.top, 10)
                        }
                    }
                }
            }
        }
    }

    /// Популярные модели, которые этому Mac не по силам, — и почему.
    private var tooBig: some View {
        let big = Curated.cards.compactMap { card -> (ModelCard, FitReport)? in
            guard let fit = model.fit(card), !fit.runnable || fit.level == .tight else { return nil }
            return (card, fit)
        }
        return Group {
            if !big.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Что не стоит ставить на этот Mac").font(.system(size: 18, weight: .bold))
                    Text("Эти модели популярны, но для вашего железа слишком велики или будут работать на пределе.")
                        .font(.system(size: 12.5)).foregroundStyle(.secondary)
                    ContentCard {
                        VStack(spacing: 0) {
                            ForEach(big, id: \.0.id) { card, fit in
                                HStack(spacing: 12) {
                                    ModelAvatar(lab: card.lab, size: 30)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(card.name).font(.system(size: 13, weight: .semibold))
                                        Text(fit.reasons.first ?? "").font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(2)
                                    }
                                    Spacer()
                                    FitBadge(fit: fit, compact: true)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .contentShape(Rectangle())
                                .onTapGesture { model.detailCard = card }
                                if card.id != big.last?.0.id { Divider().opacity(0.4).padding(.leading, 56) }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
    }
}
