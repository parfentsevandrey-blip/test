import SwiftUI

/// Каталог всех моделей: живой список с Hugging Face, размер и вердикт для этого Mac у каждой.
struct CatalogView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var filter: Filter = .all
    @State private var sort: Sort = .popular

    enum Filter: String, CaseIterable, Identifiable {
        case all, fits, small, code, reasoning, installed
        var id: String { rawValue }
        var title: String {
            switch self {
            case .all: "Все"
            case .fits: "Подходят этому Mac"
            case .small: "Лёгкие"
            case .code: "Для кода"
            case .reasoning: "Рассуждения"
            case .installed: "Установленные"
            }
        }
    }

    enum Sort: String, CaseIterable, Identifiable {
        case popular, recommended, newest, smallest, largest
        var id: String { rawValue }
        var title: String {
            switch self {
            case .popular: "По популярности"
            case .recommended: "Лучшие для этого Mac"
            case .newest: "Сначала новые"
            case .smallest: "Сначала маленькие"
            case .largest: "Сначала большие"
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "Каталог", subtitle: subtitle) {
                HStack(spacing: 10) {
                    if model.catalogLoading || model.searching { ProgressView().controlSize(.small) }
                    Menu {
                        Picker("Сортировка", selection: $sort) { ForEach(Sort.allCases) { Text($0.title).tag($0) } }
                            .pickerStyle(.inline)
                    } label: {
                        Label(sort.title, systemImage: "arrow.up.arrow.down")
                    }
                    .menuStyle(.button)
                    .buttonStyle(.glass)
                    .fixedSize()
                    Button { Task { await model.refreshCatalog(force: true) } } label: { Image(systemName: "arrow.clockwise") }
                        .buttonStyle(.glass)
                        .help("Обновить каталог")
                }
            }
            controls
            if let error = model.catalogError {
                Label(error, systemImage: "wifi.exclamationmark").font(.system(size: 12)).foregroundStyle(.orange)
                    .padding(.horizontal, 28).padding(.bottom, 8).frame(maxWidth: .infinity, alignment: .leading)
            }
            list
        }
        .task(id: query) {
            try? await Task.sleep(for: .milliseconds(350))
            guard !Task.isCancelled else { return }
            await model.search(query)
        }
    }

    private var subtitle: String {
        let n = model.cards.count
        let updated = model.catalogUpdated.map { " · обновлён \(Format.relative($0))" } ?? ""
        return "\(n) моделей в формате GGUF от доверенных сборщиков; поиск — по всему Hugging Face\(updated)."
    }

    private var controls: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Поиск: qwen, gemma, llama, mistral, coder…", text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 14))
                if !query.isEmpty {
                    Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glassEffect(.regular, in: .capsule)

            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(Filter.allCases) { f in
                        Button {
                            withAnimation(.snappy) { filter = f }
                        } label: {
                            Text(f.title).font(.system(size: 12, weight: .medium)).padding(.horizontal, 4)
                        }
                        .buttonStyle(.glass)
                        .tint(filter == f ? Section.catalog.tint : nil)
                        .background(Capsule().fill(filter == f ? Section.catalog.tint.opacity(0.25) : .clear))
                    }
                }
            }
            .scrollIndicators(.never)
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 12)
    }

    private var items: [(ModelCard, FitReport?)] {
        let source = model.searchResults ?? model.cards
        var list = source.map { ($0, model.fit($0)) }
        switch filter {
        case .all: break
        case .fits: list = list.filter { $0.1?.comfortable == true }
        case .small: list = list.filter { $0.0.params < 5 }
        case .code: list = list.filter { $0.0.tags.contains(.code) }
        case .reasoning: list = list.filter { $0.0.tags.contains(.reasoning) }
        case .installed: list = list.filter { model.isInstalled($0.0) }
        }
        switch sort {
        case .popular: if model.searchResults == nil { list.sort { $0.0.downloads > $1.0.downloads } }
        case .recommended:
            list.sort { a, b in
                let fa = a.1?.comfortable == true, fb = b.1?.comfortable == true
                if fa != fb { return fa }
                return a.0.quality > b.0.quality
            }
        case .newest: list.sort { ($0.0.created ?? .distantPast) > ($1.0.created ?? .distantPast) }
        case .smallest: list.sort { $0.0.params < $1.0.params }
        case .largest: list.sort { $0.0.params > $1.0.params }
        }
        return list
    }

    private var list: some View {
        let rows = items
        return ScrollView {
            LazyVStack(spacing: 8) {
                if rows.isEmpty {
                    ContentUnavailableView(model.searching ? "Ищу…" : "Ничего не нашлось", systemImage: "magnifyingglass",
                                           description: Text("Попробуйте другое название или снимите фильтр."))
                        .padding(.top, 60)
                }
                ForEach(rows, id: \.0.id) { card, fit in
                    CatalogRow(card: card, fit: fit)
                        .onTapGesture { model.detailCard = card }
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.automatic)
    }
}

struct CatalogRow: View {
    @Environment(AppModel.self) private var model
    let card: ModelCard
    let fit: FitReport?

    var body: some View {
        HStack(spacing: 14) {
            ModelAvatar(lab: card.lab, size: 42)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(card.name).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    if card.curated {
                        Image(systemName: "checkmark.seal.fill").font(.system(size: 11)).foregroundStyle(Section.catalog.tint).help("Проверено Synapse")
                    }
                    if !EngineCatalog.supports(card.architecture) {
                        Text("Движок пока не умеет").font(.system(size: 10, weight: .semibold)).foregroundStyle(.red)
                            .padding(.horizontal, 6).padding(.vertical, 1).background(Capsule().fill(.red.opacity(0.12)))
                    }
                }
                Text("\(card.lab) · \(card.paramsText) параметров · \(card.quantizer)")
                    .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                HStack(spacing: 5) {
                    ForEach(Array(card.tags.sorted { $0.rawValue < $1.rawValue }.prefix(3)), id: \.self) { TagPill(tag: $0) }
                }
            }
            Spacer(minLength: 10)
            VStack(alignment: .trailing, spacing: 4) {
                if let best = model.bestVariant(for: card) {
                    Text(Format.bytes(best.variant.size) + (best.variant.estimated ? "*" : ""))
                        .font(.system(size: 13, weight: .semibold)).monospacedDigit()
                        .help(best.variant.estimated ? "Оценка по числу параметров; точный размер — в карточке модели" : best.variant.quant.name)
                }
                if card.downloads > 0 {
                    Label(Format.count(card.downloads), systemImage: "arrow.down").font(.system(size: 10.5)).foregroundStyle(.tertiary).labelStyle(.titleAndIcon)
                }
            }
            .frame(width: 92, alignment: .trailing)
            if let fit { FitBadge(fit: fit).frame(width: 128, alignment: .trailing) }
            InstallButton(card: card, prominent: false)
                .frame(width: 128, alignment: .trailing)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.background.opacity(0.55)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.primary.opacity(0.06)))
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }
}

/// Подробности модели: описание, вердикт для этого Mac и все варианты сжатия.
struct ModelDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let card: ModelCard

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 16) {
                ModelAvatar(lab: card.lab, size: 60)
                VStack(alignment: .leading, spacing: 4) {
                    Text(card.name).font(.system(size: 24, weight: .bold))
                    Text("\(card.lab) · \(card.paramsText) параметров" + (card.architecture.map { " · \($0)" } ?? ""))
                        .font(.system(size: 12.5)).foregroundStyle(.secondary)
                    HStack(spacing: 5) { ForEach(Array(card.tags.sorted { $0.rawValue < $1.rawValue }), id: \.self) { TagPill(tag: $0) } }
                }
                Spacer()
                Button { dismiss() } label: { Image(systemName: "xmark") }
                    .buttonStyle(.glass)
                    .keyboardShortcut(.cancelAction)
            }
            .padding(24)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(card.summary).font(.system(size: 13.5)).frame(maxWidth: .infinity, alignment: .leading)
                    facts
                    if let best = model.bestVariant(for: card) { FitExplanation(fit: best.fit) }
                    if !EngineCatalog.supports(card.architecture) {
                        Label("Архитектура «\(card.architecture ?? "")» появилась позже встроенного движка llama.cpp \(EngineCatalog.version). Модель заработает после обновления Synapse.",
                              systemImage: "exclamationmark.octagon.fill")
                            .font(.system(size: 12)).foregroundStyle(.red)
                    }
                    variantsTable
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
        }
        .frame(width: 760, height: 640)
        .background(SectionBackground(section: .catalog, energy: 0.1).opacity(0.6))
        .task { await model.loadVariants(card) }
    }

    private var facts: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
            StatTile(symbol: "cube.fill", title: "Размер", value: card.sizeClass, detail: card.paramsText, tint: Section.catalog.palette[0])
            if let ctx = card.contextLength {
                StatTile(symbol: "text.alignleft", title: "Контекст", value: "\(ctx / 1024)K токенов", detail: "сколько текста помнит", tint: Section.catalog.palette[1])
            }
            if card.downloads > 0 {
                StatTile(symbol: "arrow.down.circle.fill", title: "Загрузок за месяц", value: Format.count(card.downloads), detail: "\(Format.count(card.likes)) ♥", tint: Section.catalog.palette[2])
            }
            if let created = card.created {
                StatTile(symbol: "calendar", title: "Опубликована", value: Format.relative(created), detail: Format.date(created), tint: Section.chat.palette[0])
            }
            if let license = card.license {
                StatTile(symbol: "doc.text.fill", title: "Лицензия", value: license, tint: Section.library.palette[0])
            }
        }
    }

    private var variantsTable: some View {
        let list = model.variants[card.id]
        let best = model.bestVariant(for: card)?.variant
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Варианты сжатия").font(.system(size: 16, weight: .bold))
                if model.variantsLoading.contains(card.id) { ProgressView().controlSize(.small) }
                Spacer()
                Link(destination: card.hubURL) { Label("Hugging Face", systemImage: "arrow.up.right.square") }.font(.system(size: 11.5))
            }
            Text("Чем сильнее сжатие, тем меньше файл и быстрее ответы, но немного ниже качество. Synapse отмечает лучший вариант для вашего Mac.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary)
            ContentCard {
                VStack(spacing: 0) {
                    let rows = (list ?? ModelVariant.estimates(params: card.params)).filter { $0.quant.bits <= 17 }
                    ForEach(rows) { v in
                        let fit = Advisor.estimate(v, card: card, mac: model.mac)
                        let isBest = v.quant.name == best?.quant.name
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(v.quant.name).font(.system(size: 12.5, weight: .semibold, design: .monospaced))
                                    if isBest {
                                        Text("Рекомендуем").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                            .padding(.horizontal, 6).padding(.vertical, 1).background(Capsule().fill(Section.catalog.tint))
                                    }
                                    if v.files.count > 1 { Text("\(v.files.count) части").font(.system(size: 10)).foregroundStyle(.tertiary) }
                                }
                                Text(v.quant.label).font(.system(size: 11)).foregroundStyle(.secondary)
                            }
                            .frame(width: 180, alignment: .leading)
                            GlowBar(fraction: max(0, (v.quant.quality - 0.8) / 0.2), colors: Section.catalog.palette)
                                .frame(width: 70)
                                .help("Качество ≈\(Int(v.quant.quality * 100))% от оригинала")
                            Spacer()
                            Text(Format.bytes(v.size) + (v.estimated ? "*" : "")).font(.system(size: 12.5, weight: .medium)).monospacedDigit()
                                .frame(width: 76, alignment: .trailing)
                            FitBadge(fit: fit, compact: true).frame(width: 112, alignment: .trailing)
                            InstallButton(card: card, variant: v.estimated ? nil : v, prominent: isBest)
                                .frame(width: 122, alignment: .trailing)
                                .disabled(v.estimated || !fit.runnable && fit.level == .noDisk)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(isBest ? Section.catalog.tint.opacity(0.08) : .clear)
                        if v.id != rows.last?.id { Divider().opacity(0.35) }
                    }
                }
            }
            if list == nil && !model.variantsLoading.contains(card.id) {
                Text("* Размеры оценены по числу параметров — нет связи с Hugging Face.").font(.system(size: 10.5)).foregroundStyle(.tertiary)
            }
        }
    }
}
