import AppKit
import SwiftUI

/// «Очистка»: все категории мусора с подробным разбором и выбором каждой находки.
struct CleanupView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            switch model.cleanupPhase {
            case .idle:
                ModuleHero(module: .cleanup, headline: "Системный мусор",
                           text: "Кэши, журналы, остатки программ, мусор разработчика, прошивки и резервные копии устройств. Каждая находка — с объяснением, что это и почему её можно удалить.",
                           button: "Найти мусор") { model.scanCleanup() }
            case .scanning(let p, let label) where model.categories.isEmpty:
                ModuleHero(module: .cleanup, headline: "Системный мусор", text: "Ничего не удаляется без вашего подтверждения.",
                           button: "", busy: "\(label) · \(Int(p * 100))%") {}
            default:
                content
            }
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            ModuleHeader(title: "Очистка", subtitle: "Отмечено только то, что программы восстановят сами. Остальное — на ваше усмотрение.") {
                scanButton
            }
            HStack(alignment: .top, spacing: 16) {
                categoryList.frame(width: 250)
                detail
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 92)
        }
        .overlay(alignment: .bottom) {
            ActionBar(title: "Отмечено \(Format.bytes(model.cleanupSelectedSize))",
                      subtitle: "Кэши удаляются сразу, остальное — в Корзину",
                      action: "Очистить", tint: Module.cleanup.tint, disabled: model.cleanupSelectedSize == 0) {
                model.cleanCategories(model.cleanupCategories, title: "Очистка")
            }
        }
    }

    @ViewBuilder
    private var scanButton: some View {
        if case .scanning(let p, _) = model.cleanupPhase {
            HStack(spacing: 8) {
                ProgressView(value: p).frame(width: 120)
                Text("\(Int(p * 100))%").monospacedDigit().foregroundStyle(.secondary)
            }
        } else {
            Button { model.scanCleanup() } label: { Label("Пересканировать", systemImage: "arrow.clockwise") }
                .buttonStyle(.glass)
        }
    }

    private var categoryList: some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(model.cleanupCategories, id: \.self) { id in
                    if let category = model.categories[id], !category.items.isEmpty || category.notice != nil {
                        CategoryListRow(category: category, selected: model.selectedCategory == id) {
                            withAnimation(.snappy) { model.selectedCategory = id }
                        }
                    }
                }
            }
            .padding(6)
        }
        .scrollIndicators(.never)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.background.opacity(0.5)))
    }

    private var detail: some View {
        let id = model.selectedCategory
        let category = model.categories[id]
        return ContentCard {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: id.symbol)
                        .font(.system(size: 22, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 48, height: 48)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(id.color.gradient))
                        .shadow(color: id.color.opacity(0.5), radius: 8, y: 3)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(id.title).font(.system(size: 18, weight: .bold))
                        Text(id.explanation).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(4).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 6) {
                        Text(Format.bytes(category?.selectedSize ?? 0)).font(.system(size: 18, weight: .bold, design: .rounded)).monospacedDigit()
                        HStack(spacing: 6) {
                            Button("Все") { model.setAll(id, true) }
                            Button("Ничего") { model.setAll(id, false) }
                        }
                        .buttonStyle(.glass)
                        .controlSize(.small)
                    }
                }
                .padding(18)
                if let notice = category?.notice {
                    NoticeBanner(text: notice, showsAccessButton: notice.contains("полный доступ") || notice.contains("полного доступа"))
                        .padding(.horizontal, 18)
                        .padding(.bottom, 10)
                }
                Divider()
                if let items = category?.items, !items.isEmpty {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(items) { item in
                                ItemRow(item: item, symbol: id.symbol) { model.toggle(item, in: id) }
                                Divider().opacity(0.35).padding(.leading, 60)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                } else {
                    ContentUnavailableView("Здесь чисто", systemImage: "checkmark.seal", description: Text("В этой категории ничего не найдено."))
                        .frame(maxHeight: .infinity)
                }
            }
        }
    }
}

struct CategoryListRow: View {
    let category: JunkCategory
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: category.id.symbol)
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(category.id.color.gradient))
                VStack(alignment: .leading, spacing: 1) {
                    Text(category.id.title).font(.system(size: 12.5, weight: .semibold)).lineLimit(1)
                    Text(category.items.isEmpty ? (category.notice ?? "Пусто") : "\(category.items.count) · отмечено \(Format.bytes(category.selectedSize))")
                        .font(.system(size: 10.5)).foregroundStyle(category.items.isEmpty && category.notice != nil ? .orange : .secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                Text(Format.bytes(category.totalSize)).font(.system(size: 11.5, weight: .semibold)).monospacedDigit().foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background {
                if selected {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(category.id.color.opacity(0.18))
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}

/// Плашка с предупреждением (например, о полном доступе к диску).
struct NoticeBanner: View {
    let text: String
    var showsAccessButton = false

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.shield.fill").foregroundStyle(.orange)
            Text(text).font(.system(size: 12))
            Spacer()
            if showsAccessButton {
                Button("Открыть настройки") { Permissions.openFullDiskAccessSettings() }
                    .buttonStyle(.glass)
                    .controlSize(.small)
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.orange.opacity(0.1)))
    }
}

/// «Приватность»: следы в браузерах и списки недавних файлов — всё только по выбору.
struct PrivacyView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if model.categories[.privacy] == nil {
                ModuleHero(module: .privacy, headline: "Следы активности",
                           text: "История и cookie браузеров, списки недавних документов. Пароли, закладки, автозаполнение и расширения не трогаем никогда.",
                           button: "Найти следы", busy: model.isBusy(model.privacyPhase) ? "Смотрю браузеры…" : nil) { model.scanPrivacy() }
            } else {
                content
            }
        }
    }

    private var content: some View {
        let items = model.categories[.privacy]?.items ?? []
        return VStack(spacing: 0) {
            ModuleHeader(title: "Приватность", subtitle: "Ничего не отмечено заранее. Перед очисткой закройте браузеры.") {
                Button { model.scanPrivacy() } label: { Label("Обновить", systemImage: "arrow.clockwise") }.buttonStyle(.glass)
            }
            ContentCard {
                if items.isEmpty {
                    ContentUnavailableView("Следов не найдено", systemImage: "hand.raised", description: Text("Поддерживаются Safari, Chrome, Яндекс Браузер, Edge, Brave, Arc, Opera, Vivaldi и Firefox."))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(items) { item in
                                ItemRow(item: item, symbol: "hand.raised.fill") { model.toggle(item, in: .privacy) }
                                Divider().opacity(0.35).padding(.leading, 60)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 92)
        }
        .overlay(alignment: .bottom) {
            let size = model.categories[.privacy]?.selectedSize ?? 0
            let count = model.categories[.privacy]?.selectedCount ?? 0
            ActionBar(title: count == 0 ? "Ничего не выбрано" : "Выбрано: \(count)", subtitle: size > 0 ? Format.bytes(size) : nil,
                      action: "Стереть", tint: Module.privacy.tint, disabled: count == 0) {
                model.cleanCategories([.privacy], title: "Приватность")
            }
        }
    }
}
