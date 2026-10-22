import SwiftUI

/// Каталог всех моделей: живой список с Hugging Face, размер и вердикт для этого Mac у каждой.
struct CatalogView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var filter: Filter = !DemoUI.isOn ? .all
        : DemoUI.screen == "stretch" ? .stretch : DemoUI.screen == "quiet" ? .quiet : DemoUI.screen == "images" ? .vision : .all
    @State private var sort: Sort = .popular

    enum Filter: String, CaseIterable, Identifiable {
        case all, fits, vision, quiet, stretch, small, code, reasoning, installed
        var id: String { rawValue }
        var title: String {
            switch self {
            case .all: "Все"
            case .fits: "Подходят этому Mac"
            case .quiet: "Тихие и лёгкие"
            case .stretch: "Выжать максимум"
            case .small: "Лёгкие"
            case .vision: "Для картинок"
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
            if filter == .stretch && query.isEmpty {
                StretchList()
            } else if filter == .vision && query.isEmpty {
                VisionList()
            } else if filter == .quiet && query.isEmpty {
                QuietList()
            } else {
                list
            }
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

            FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(Filter.allCases) { f in
                        Button {
                            withAnimation(.snappy) { filter = f }
                        } label: {
                            if let symbol = f.symbol {
                                Label(f.title, systemImage: symbol).font(.system(size: 12, weight: .medium)).padding(.horizontal, 4)
                            } else {
                                Text(f.title).font(.system(size: 12, weight: .medium)).padding(.horizontal, 4)
                            }
                        }
                        .buttonStyle(.glass)
                        .tint(filter == f ? Section.catalog.tint : nil)
                        .background(Capsule().fill(filter == f ? Section.catalog.tint.opacity(0.25) : .clear))
                    }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 12)
    }

    private var items: [(ModelCard, FitReport?)] {
        let source = model.searchResults ?? model.cards
        var list = source.map { ($0, model.fit($0)) }
        switch filter {
        case .all, .stretch, .quiet: break
        case .fits: list = list.filter { $0.1?.comfortable == true }
        case .small: list = list.filter { $0.0.params < 5 }
        case .code: list = list.filter { $0.0.tags.contains(.code) }
        case .reasoning: list = list.filter { $0.0.tags.contains(.reasoning) }
        case .vision: list = list.filter { model.canSee($0.0) }
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
                // Сколько тегов поместится — столько и показываем, строка не должна раздвигать список.
                let tags = card.tags.sorted { $0.rawValue < $1.rawValue }
                ViewThatFits(in: .horizontal) {
                    ForEach([3, 2, 1, 0], id: \.self) { n in
                        HStack(spacing: 5) { ForEach(Array(tags.prefix(n)), id: \.self) { TagPill(tag: $0) } }
                    }
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
            if let fit { FitBadge(fit: fit).frame(width: 120, alignment: .trailing) }
            InstallButton(card: card, prominent: false)
                .frame(width: 122, alignment: .trailing)
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
                    if let guide = ModelGuides.guide(for: card) { GuideView(guide: guide) }
                    facts
                    if let best = model.bestVariant(for: card) { FitExplanation(fit: best.fit) }
                    if model.canSee(card) {
                        Label(visionNote, systemImage: "eye.fill")
                            .font(.system(size: 12)).foregroundStyle(Section.chat.palette[1])
                    }
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

    private var visionNote: String {
        if let p = model.variants[card.id]?.first?.projector {
            return "Видит изображения: проектор зрения (\(Format.bytes(p.bytes))) скачается вместе с моделью — можно спрашивать про фото, скриншоты, схемы и документы."
        }
        return "Видит изображения: проектор зрения скачается вместе с моделью."
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
            Text("Чем сильнее сжатие, тем меньше файл и быстрее ответы, но ниже качество — под каждым вариантом написано, что именно теряется. Synapse отмечает лучший вариант для вашего Mac.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary)
            ContentCard {
                VStack(spacing: 0) {
                    let rows = (list ?? ModelVariant.estimates(params: card.params)).filter { $0.quant.bits <= 17 }
                    ForEach(rows) { v in
                        let fit = Advisor.estimate(v, card: card, mac: model.mac)
                        let isBest = v.quant.name == best?.quant.name
                        VStack(alignment: .leading, spacing: 5) {
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
                        // Что теряется на этом сжатии.
                        Text(v.quant.tradeoff(params: card.params))
                            .font(.system(size: 11)).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
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

/// «Выжать максимум»: модели классом выше рекомендованных, которые этот Mac потянет только сжатыми.
struct StretchList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let rows = model.stretchModels
        let everyday = model.everydayCard
        ScrollView {
            LazyVStack(spacing: 8) {
                intro(everyday)
                if rows.isEmpty {
                    ContentUnavailableView("Крупнее не поместится", systemImage: "flame",
                                           description: Text("В рекомендациях уже самая сильная модель, которую потянет этот Mac. Попробуйте поиск — там больше моделей."))
                        .padding(.top, 30)
                }
                ForEach(rows) { s in
                    StretchRow(stretch: s, everyday: everyday)
                        .onTapGesture { model.detailCard = s.card }
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.automatic)
        .task(id: model.cards.count) { await model.resolveStretchVariants() }
    }

    private func intro(_ everyday: ModelCard?) -> some View {
        ContentCard(padding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Label("Предел \(model.mac.chipShort) · \(Int(model.mac.memoryGB.rounded())) ГБ", systemImage: "flame.fill")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(LinearGradient(colors: [.orange, .red], startPoint: .leading, endPoint: .trailing))
                let named = everyday.map { " («" + $0.name + "»)" } ?? ""
                Text("Здесь модели классом выше той, что Synapse советует на каждый день\(named). Чтобы поместиться в память, они сжаты сильнее обычного — до 2–4 бит на вес вместо 5.")
                Text("Большая модель даже сильно сжатой обычно умнее маленькой: знания и умение рассуждать сохраняются, страдают точность формулировок и редкие факты. Ниже 2,5 бит модель начинает путаться — такие варианты помечены.")
                Text("Цена — скорость и память. Synapse сам уменьшит контекст и сожмёт его память, чтобы модель запустилась; пока она работает, тяжёлые программы лучше закрыть.")
                if GPUMemory.target(model.mac) != nil || GPUMemory.raisedLimit != nil {
                    Divider().padding(.vertical, 2)
                    GPUMemoryBlock()
                }
            }
            .font(.system(size: 12))
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.bottom, 4)
    }
}

struct StretchRow: View {
    @Environment(AppModel.self) private var model
    let stretch: Advisor.Stretch
    let everyday: ModelCard?

    var body: some View {
        let s = stretch
        HStack(alignment: .top, spacing: 14) {
            ModelAvatar(lab: s.card.lab, size: 42)
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(s.card.name).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    if let everyday, s.times(everyday) >= 1.4 {
                        Text("в \(times(s.times(everyday))) крупнее «\(everyday.name)»")
                            .font(.system(size: 10, weight: .bold)).foregroundStyle(.orange)
                            .padding(.horizontal, 6).padding(.vertical, 1).background(Capsule().fill(.orange.opacity(0.14)))
                            .lineLimit(1)
                    }
                }
                Text("\(s.card.lab) · \(s.card.paramsText) параметров" + (s.card.isMoE ? " · смесь экспертов" : ""))
                    .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                HStack(spacing: 6) {
                    Text(s.variant.quant.name).font(.system(size: 11, weight: .semibold, design: .monospaced))
                    Text("\(s.variant.quant.label.lowercased()) · ≈\(Int(s.variant.quant.quality * 100))% качества оригинала")
                        .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                }
                Text(note).font(.system(size: 10.5)).foregroundStyle(.tertiary).lineLimit(2)
                if s.needsMoreGPU {
                    Label("Поместится, если отдать видеоядру больше памяти (кнопка выше)", systemImage: "bolt.fill")
                        .font(.system(size: 10.5, weight: .medium)).foregroundStyle(.orange)
                }
                if s.variant.quant.quality < 0.8 {
                    Label("Экстремальное сжатие: модель может путаться — попробуйте и сравните", systemImage: "exclamationmark.triangle")
                        .font(.system(size: 10.5)).foregroundStyle(.orange)
                }
            }
            Spacer(minLength: 10)
            VStack(alignment: .trailing, spacing: 6) {
                Text(Format.bytes(s.variant.size) + (s.variant.estimated ? "*" : ""))
                    .font(.system(size: 13, weight: .semibold)).monospacedDigit()
                FitBadge(fit: s.fit)
                if s.variant.estimated {
                    HStack(spacing: 5) {
                        ProgressView().controlSize(.mini)
                        Text("Уточняю файлы…").font(.system(size: 10.5)).foregroundStyle(.secondary)
                    }
                    .task { await model.loadVariants(s.card) }
                } else {
                    InstallButton(card: s.card, variant: s.variant, prominent: false)
                }
            }
            .frame(width: 150, alignment: .trailing)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.background.opacity(0.55)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.orange.opacity(0.18)))
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }

    private var note: String {
        let s = stretch
        var parts = [s.fit.speedText]
        parts.append("контекст \(s.fit.context / 1024)K" + (s.compressKV ? ", память контекста сжата" : ""))
        return parts.joined(separator: " · ")
    }

    /// «в 2,5 раза», «в 3 раза», «в 5 раз».
    private func times(_ x: Double) -> String {
        let tenth = (x * 10).rounded() / 10
        if tenth >= 10 || tenth == tenth.rounded() {
            let n = Int(tenth.rounded())
            return "\(n) " + ((2...4).contains(n % 10) && !(12...14).contains(n % 100) ? "раза" : "раз")
        }
        return String(format: "%.1f", tenth).replacingOccurrences(of: ".", with: ",") + " раза"
    }
}

/// Подробное описание модели: для чего, в чём сильна, где слабее, когда выбрать, факты.
struct GuideView: View {
    let guide: ModelGuide

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            part("Для чего создана", symbol: "target", tint: Section.catalog.palette[0]) {
                Text(guide.purpose).fixedSize(horizontal: false, vertical: true)
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 14, alignment: .top), GridItem(.flexible(), spacing: 14, alignment: .top)],
                      alignment: .leading, spacing: 14) {
                if !guide.strengths.isEmpty {
                    part("В чём сильна", symbol: "hand.thumbsup.fill", tint: Color(red: 0.2, green: 0.78, blue: 0.5)) { bullets(guide.strengths) }
                }
                if !guide.weaknesses.isEmpty {
                    part("Где слабее", symbol: "exclamationmark.circle.fill", tint: .orange) { bullets(guide.weaknesses) }
                }
                if !guide.bestFor.isEmpty {
                    part("Когда выбрать её", symbol: "checkmark.seal.fill", tint: Section.catalog.palette[1]) { bullets(guide.bestFor) }
                }
                if !guide.facts.isEmpty {
                    part("Факты", symbol: "info.circle.fill", tint: .secondary) { bullets(guide.facts) }
                }
            }
        }
        .font(.system(size: 12.5))
    }

    private func part<C: View>(_ title: String, symbol: String, tint: Color, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: symbol).font(.system(size: 13, weight: .bold)).foregroundStyle(tint)
            content().foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func bullets(_ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(items, id: \.self) { item in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("•").foregroundStyle(.secondary)
                    Text(item).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

/// «Тихие и лёгкие»: модели, которые этот Mac даже не заметит — без нагрева и шума вентилятора.
struct QuietList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let rows = model.quietModels
        ScrollView {
            LazyVStack(spacing: 8) {
                intro
                if rows.isEmpty {
                    ContentUnavailableView("Лёгких моделей не нашлось", systemImage: "leaf",
                                           description: Text("Обновите каталог или снимите фильтр — посмотрите вкладку «Подходят этому Mac»."))
                        .padding(.top, 30)
                }
                ForEach(rows) { q in
                    QuietRow(quiet: q)
                        .onTapGesture { model.detailCard = q.card }
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.automatic)
    }

    private var intro: some View {
        let green = Color(red: 0.2, green: 0.78, blue: 0.5)
        return ContentCard(padding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Label("Тихо и легко на \(model.mac.chipShort) · \(Int(model.mac.memoryGB.rounded())) ГБ", systemImage: "leaf.fill")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(green)
                Text("Эти модели можно держать открытыми весь день: каждая занимает не больше четверти памяти Mac, а на каждое слово работает не больше \(Int(Advisor.quietActive)) млрд параметров.")
                Text("Ответ в пару абзацев пишется за секунды, видеоядро не успевает нагреться — вентилятор не включится, батарея почти не тратится, остальные программы ничего не почувствуют.")
                Text("Они проще больших моделей: для писем, пересказов, перевода, быстрых вопросов и черновиков — в самый раз, для сложного анализа загляните в «Выжать максимум».")
                    .foregroundStyle(.secondary)
            }
            .font(.system(size: 12))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.bottom, 4)
    }
}

struct QuietRow: View {
    @Environment(AppModel.self) private var model
    let quiet: Advisor.Quiet

    var body: some View {
        let q = quiet
        let green = Color(red: 0.2, green: 0.78, blue: 0.5)
        HStack(alignment: .top, spacing: 14) {
            ModelAvatar(lab: q.card.lab, size: 42)
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(q.card.name).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    if q.card.curated {
                        Image(systemName: "checkmark.seal.fill").font(.system(size: 11)).foregroundStyle(Section.catalog.tint).help("Проверено Synapse")
                    }
                }
                Text("\(q.card.lab) · \(q.card.paramsText) параметров · \(q.variant.quant.name)")
                    .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                let tags = q.card.tags.sorted { $0.rawValue < $1.rawValue }
                ViewThatFits(in: .horizontal) {
                    ForEach([3, 2, 1, 0], id: \.self) { n in
                        HStack(spacing: 5) { ForEach(Array(tags.prefix(n)), id: \.self) { TagPill(tag: $0) } }
                    }
                }
                Text("≈\(Int(q.fit.tokensPerSecond.rounded())) ток/с · ответ в пару абзацев — за \(seconds(q.answerSeconds)) · \(percent(q.share(model.mac))) памяти Mac")
                    .font(.system(size: 10.5)).foregroundStyle(green).lineLimit(1)
            }
            Spacer(minLength: 10)
            VStack(alignment: .trailing, spacing: 6) {
                Text(Format.bytes(q.variant.size) + (q.variant.estimated ? "*" : ""))
                    .font(.system(size: 13, weight: .semibold)).monospacedDigit()
                FitBadge(fit: q.fit)
                InstallButton(card: q.card, variant: q.variant.estimated ? nil : q.variant, prominent: false)
            }
            .frame(width: 150, alignment: .trailing)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.background.opacity(0.55)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(green.opacity(0.2)))
        .contentShape(RoundedRectangle(cornerRadius: 18))
    }

    private func seconds(_ s: Double) -> String { s < 60 ? "\(max(1, Int(s.rounded()))) с" : "\(Int((s / 60).rounded())) мин" }
    private func percent(_ x: Double) -> String { x < 0.01 ? "меньше 1%" : "\(Int((x * 100).rounded()))%" }
}

extension CatalogView.Filter {
    /// Особые подборки — со значком.
    var symbol: String? {
        switch self {
        case .vision: "photo.on.rectangle.angled"
        case .quiet: "leaf.fill"
        case .stretch: "flame.fill"
        default: nil
        }
    }
}

/// «Для картинок»: модели, которым можно показать изображение, — лучшие для этого Mac сверху.
struct VisionList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let rows = model.visionModels
        ScrollView {
            LazyVStack(spacing: 8) {
                intro
                if rows.isEmpty {
                    ContentUnavailableView("Моделей со зрением не нашлось", systemImage: "eye.slash",
                                           description: Text("Обновите каталог — модели со зрением приходят с Hugging Face."))
                        .padding(.top, 30)
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
        .task(id: model.cards.count) { await model.resolveVisionVariants() }
    }

    private var intro: some View {
        let tint = Section.chat.palette[1]
        return ContentCard(padding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Label("Работа с картинками", systemImage: "photo.on.rectangle.angled")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(tint)
                Text("Этим моделям можно показать изображение: фото, скриншот, схему, график, отсканированный документ или рукописную заметку. Приложите картинку скрепкой в чате или просто перетащите её в окно.")
                Text("Что можно спросить: «что на фото?», «перепиши текст со скриншота», «что не так в этой ошибке?», «переведи меню», «объясни график», «сравни две картинки», «составь описание товара по фото».")
                    .foregroundStyle(.secondary)
                Text("Вместе с моделью скачивается проектор зрения — небольшой модуль, который переводит картинку на язык модели. Всё работает прямо на Mac: изображения никуда не отправляются.")
                    .foregroundStyle(.secondary)
            }
            .font(.system(size: 12))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.bottom, 4)
    }
}
