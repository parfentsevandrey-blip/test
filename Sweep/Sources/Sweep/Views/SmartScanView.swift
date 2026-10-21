import AppKit
import SwiftUI

/// «Умная проверка»: одна кнопка — и всё безопасное найдено. Подробности — в плитках категорий.
struct SmartScanView: View {
    @Environment(AppModel.self) private var model
    @State private var since = Date()
    @State private var reviewing: CategoryID?

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                LiquidOrb(phase: model.smartPhase, categories: model.smartCategories, palette: Module.smart.palette, since: since)
                    .frame(maxWidth: 560, maxHeight: 420)
                orbLabel
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 14)

            caption
                .padding(.top, 4)

            if case .results = model.smartPhase {
                results
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            } else {
                Spacer(minLength: 0)
            }
            controls
                .padding(.bottom, 26)
        }
        .onChange(of: phaseKey) { since = Date() }
        .sheet(item: $reviewing) { id in
            CategoryReviewSheet(id: id, smart: true)
        }
    }

    private var phaseKey: Int {
        switch model.smartPhase {
        case .idle: 0
        case .scanning: 1
        case .results: 2
        case .cleaning: 3
        case .done: 4
        }
    }

    @ViewBuilder
    private var orbLabel: some View {
        switch model.smartPhase {
        case .idle:
            VStack(spacing: 4) {
                Image(systemName: "sparkles").font(.system(size: 40, weight: .semibold))
                Text(Format.bytes(model.disk.availableImportant)).font(.system(size: 22, weight: .bold, design: .rounded)).monospacedDigit()
                Text("свободно").font(.system(size: 12, weight: .medium)).opacity(0.7)
            }
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(0.4), radius: 6)
        case .scanning, .results, .cleaning:
            VStack(spacing: 2) {
                Text(Format.bytes(model.smartSelectedSize))
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .monospacedDigit()
                    .contentTransition(.numericText(value: Double(model.smartSelectedSize)))
                Text(model.isBusy(model.smartPhase) ? "найдено" : "можно освободить").font(.system(size: 13, weight: .semibold)).opacity(0.8)
            }
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(0.5), radius: 6)
            .animation(.smooth, value: model.smartSelectedSize)
        case .done(let freed):
            VStack(spacing: 2) {
                Image(systemName: "checkmark").font(.system(size: 34, weight: .heavy))
                Text(Format.bytes(freed)).font(.system(size: 36, weight: .heavy, design: .rounded)).monospacedDigit()
                Text("освобождено").font(.system(size: 13, weight: .semibold)).opacity(0.8)
            }
            .foregroundStyle(.white)
            .shadow(color: .black.opacity(0.5), radius: 6)
            .transition(.scale.combined(with: .opacity))
        }
    }

    @ViewBuilder
    private var caption: some View {
        VStack(spacing: 6) {
            switch model.smartPhase {
            case .idle:
                Text("Проверим Mac на мусор").font(.system(size: 26, weight: .bold))
                Text("Кэши, журналы, остатки удалённых программ, мусор разработчика и Корзина. Только то, что безопасно удалять — остальное покажем на проверку.")
            case .scanning(_, let label):
                Text("Ищу: \(label)").font(.system(size: 20, weight: .semibold)).contentTransition(.opacity)
                Text("Ничего не удаляется, пока вы не нажмёте «Очистить».")
            case .results:
                Text(model.smartSelectedSize > 0 ? "Готово к очистке" : "Mac в порядке").font(.system(size: 22, weight: .bold))
                Text("Отмечено только безопасное. Нажмите на плитку, чтобы посмотреть, что внутри.")
            case .cleaning:
                Text("Очищаю…").font(.system(size: 22, weight: .bold))
                Text("Программы пересоздадут свои кэши, когда понадобится.")
            case .done:
                Text("Готово!").font(.system(size: 26, weight: .bold))
                Text("Всё удалённое записано в «Журнал». То, что ушло в Корзину, можно вернуть.")
            }
        }
        .font(.system(size: 13))
        .foregroundStyle(.secondary)
        .multilineTextAlignment(.center)
        .frame(maxWidth: 560)
    }

    private var results: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 210, maximum: 280), spacing: 12)], spacing: 12) {
                ForEach(model.smartCategories) { category in
                    CategoryTile(category: category) { reviewing = category.id }
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .frame(maxHeight: 280)
    }

    @ViewBuilder
    private var controls: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                switch model.smartPhase {
                case .idle:
                    bigButton("Сканировать", "sparkle.magnifyingglass") { model.runSmartScan() }
                case .scanning:
                    bigButton("Сканирую…", "hourglass", disabled: true) {}
                case .results:
                    Button { model.resetSmart() } label: { Label("Заново", systemImage: "arrow.counterclockwise").padding(.horizontal, 6) }
                        .buttonStyle(.glass)
                        .controlSize(.extraLarge)
                    bigButton("Очистить \(Format.bytes(model.smartSelectedSize))", "sparkles", disabled: model.smartSelectedSize == 0) {
                        model.cleanSmart()
                    }
                case .cleaning:
                    bigButton("Очищаю…", "hourglass", disabled: true) {}
                case .done:
                    bigButton("Готово", "checkmark") { model.resetSmart() }
                }
            }
        }
        .animation(.bouncy, value: phaseKey)
    }

    private func bigButton(_ title: String, _ symbol: String, disabled: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.system(size: 16, weight: .semibold))
                .padding(.horizontal, 22)
                .padding(.vertical, 6)
                .contentTransition(.numericText())
        }
        .buttonStyle(.glassProminent)
        .tint(Module.smart.tint)
        .controlSize(.extraLarge)
        .disabled(disabled)
    }
}

/// Плитка категории с размером и флажком «очистить всё в категории».
struct CategoryTile: View {
    @Environment(AppModel.self) private var model
    let category: JunkCategory
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            HStack(spacing: 12) {
                Image(systemName: category.id.symbol)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(category.id.color.gradient))
                    .shadow(color: category.id.color.opacity(0.5), radius: 6, y: 2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.id.title).font(.system(size: 12.5, weight: .semibold)).lineLimit(1)
                    if let notice = category.notice, category.items.isEmpty {
                        Text(notice).font(.system(size: 11)).foregroundStyle(.orange).lineLimit(2)
                    } else {
                        Text("\(Format.bytes(category.selectedSize)) из \(Format.bytes(category.totalSize))")
                            .font(.system(size: 11.5)).foregroundStyle(.secondary).monospacedDigit()
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .bold)).foregroundStyle(.tertiary)
            }
            .padding(12)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.background.opacity(0.6)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(category.id.color.opacity(0.25)))
    }
}

/// Лист с содержимым категории — посмотреть и снять/поставить флажки.
struct CategoryReviewSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let id: CategoryID
    var smart = false

    private var category: JunkCategory? {
        smart ? model.smartCategories.first { $0.id == id } : model.categories[id]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: id.symbol)
                    .font(.system(size: 20, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(id.color.gradient))
                VStack(alignment: .leading, spacing: 2) {
                    Text(id.title).font(.system(size: 17, weight: .bold))
                    Text(id.explanation).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(3)
                }
            }
            .padding(20)
            Divider()
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(category?.items ?? []) { item in
                        ItemRow(item: item, symbol: id.symbol) { model.toggle(item, in: id, smart: smart) }
                        Divider().opacity(0.4).padding(.leading, 56)
                    }
                }
            }
            .frame(minHeight: 320)
            Divider()
            HStack {
                Button("Отметить безопасное") { model.setAll(id, true, smart: smart) }
                Button("Снять всё") { model.setAll(id, false, smart: smart) }
                Spacer()
                Text("Отмечено \(Format.bytes(category?.selectedSize ?? 0))").monospacedDigit().foregroundStyle(.secondary)
                Button("Готово") { dismiss() }.keyboardShortcut(.defaultAction).buttonStyle(.glassProminent)
            }
            .buttonStyle(.glass)
            .padding(16)
        }
        .frame(width: 760, height: 560)
    }
}

extension CategoryID: Identifiable {
    var id: String { rawValue }
}
