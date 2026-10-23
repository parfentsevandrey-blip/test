import AppKit
import SwiftUI

/// Страница приложения: обложка в его цвете, скриншоты, описание, возможности, что нового.
struct AppDetail: View {
    @Environment(Store.self) private var store
    let app: CatalogApp
    @State private var hasBackup = false
    @State private var confirmRemove = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 30) {
                hero
                if let shots = screenshots, !shots.isEmpty { gallery(shots) }
                HStack(alignment: .top, spacing: 28) {
                    VStack(alignment: .leading, spacing: 24) {
                        block("О приложении") {
                            Text(app.description).font(.system(size: 14)).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                        }
                        block("Возможности") {
                            VStack(alignment: .leading, spacing: 9) {
                                ForEach(app.features, id: \.self) { feature in
                                    Label {
                                        Text(feature).font(.system(size: 13.5)).fixedSize(horizontal: false, vertical: true)
                                    } icon: {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(app.color)
                                    }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    VStack(alignment: .leading, spacing: 24) {
                        if let changes = app.changes, !changes.isEmpty { whatsNew(changes) }
                        info
                    }
                    .frame(width: 300)
                }
            }
            .padding(28)
        }
        .scrollContentBackground(.hidden)
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Button {
                    store.selectedID = nil
                } label: {
                    Label("Назад", systemImage: "chevron.left")
                }
            }
        }
        .task(id: store.state(app)) { hasBackup = await store.hasBackup(app) }
        .confirmationDialog("Удалить \(app.name)?", isPresented: $confirmRemove) {
            Button("Удалить программу, данные оставить") { Task { await store.uninstall(app, removeData: false) } }
            Button("Удалить вместе с данными", role: .destructive) { Task { await store.uninstall(app, removeData: true) } }
        } message: {
            let data = Installer.dataLocations(app)
            Text(data.isEmpty ? "Программа уйдёт в Корзину." :
                    "Программа уйдёт в Корзину. Данные (\(data.count) \(Format.plural(data.count, "папка", "папки", "папок")) в ~/Library) можно оставить — тогда при повторной установке всё вернётся.")
        }
    }

    private var screenshots: [URL]? { DemoUI.isOn ? nil : app.screenshots }

    private var hero: some View {
        HStack(alignment: .center, spacing: 24) {
            AppIconView(app: app, size: 128)
            VStack(alignment: .leading, spacing: 8) {
                Text(app.category.uppercased())
                    .font(.system(size: 11, weight: .bold)).tracking(1.2)
                    .foregroundStyle(app.color)
                Text(app.name).font(.system(size: 40, weight: .bold, design: .rounded))
                Text(app.tagline).font(.title3).foregroundStyle(.secondary)
                HStack(spacing: 10) {
                    ActionButton(app: app, large: true)
                    menu
                }
                .padding(.top, 6)
                statusLine
            }
            Spacer(minLength: 0)
        }
        .padding(26)
        .background {
            RoundedRectangle(cornerRadius: 32, style: .continuous)
                .fill(LinearGradient(colors: [app.color.opacity(0.28), app.color.opacity(0.06)], startPoint: .topLeading, endPoint: .bottomTrailing))
        }
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
    }

    @ViewBuilder
    private var statusLine: some View {
        switch store.state(app) {
        case .installed(let v):
            Text("Установлена версия \(v.description) — последняя").font(.system(size: 12)).foregroundStyle(.secondary)
        case .update(let from, let to):
            Text("Установлена \(from.description), доступна \(to.description). Данные и настройки сохранятся.")
                .font(.system(size: 12)).foregroundStyle(.secondary)
        case .notInstalled:
            if let size = app.size {
                Text("\(Format.bytes(size)) · без предупреждений macOS, ставится в «Программы»").font(.system(size: 12)).foregroundStyle(.secondary)
            }
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private var menu: some View {
        let installed: Bool = {
            switch store.state(app) {
            case .installed, .update: true
            default: false
            }
        }()
        if installed && app.bundleID != Bundle.main.bundleIdentifier {
            Menu {
                Button("Показать в Finder") { store.reveal(app) }
                if hasBackup {
                    Button("Вернуть предыдущую версию") { Task { await store.rollback(app) } }
                }
                Divider()
                Button("Удалить…", role: .destructive) { confirmRemove = true }
            } label: {
                Image(systemName: "ellipsis")
            }
            .menuStyle(.button)
            .buttonStyle(.glass)
            .fixedSize()
        }
    }

    private func gallery(_ shots: [URL]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                ForEach(shots, id: \.self) { url in
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().aspectRatio(contentMode: .fit)
                        } else {
                            RoundedRectangle(cornerRadius: 18).fill(app.color.opacity(0.08)).aspectRatio(1.56, contentMode: .fit)
                        }
                    }
                    .frame(height: 300)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .shadow(color: .black.opacity(0.18), radius: 14, y: 6)
                }
            }
            .padding(.vertical, 8)
        }
    }

    private func whatsNew(_ changes: [String]) -> some View {
        block("Что нового") {
            VStack(alignment: .leading, spacing: 7) {
                if let v = app.latest {
                    Text("Версия \(v.description)\(app.date.map { " · " + $0.formatted(date: .abbreviated, time: .omitted) } ?? "")")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
                }
                ForEach(changes.prefix(6), id: \.self) { line in
                    Text("• " + line).font(.system(size: 12.5)).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var info: some View {
        block("Сведения") {
            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 7) {
                row("Версия", app.latest?.description ?? "—")
                row("Размер", Format.bytes(app.size))
                if let replaces = app.replaces { row("Вместо", replaces) }
                row("Система", "macOS \(app.minimumSystem ?? "26") и новее")
                row("Подпись", app.signer == nil ? "ad-hoc" : "Harbor Code Signing")
                row("Идентификатор", app.bundleID)
            }
            .font(.system(size: 12))
        }
    }

    private func row(_ title: String, _ value: String) -> some View {
        GridRow {
            Text(title).foregroundStyle(.secondary)
            Text(value).textSelection(.enabled).lineLimit(2)
        }
    }

    private func block<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.system(size: 17, weight: .bold, design: .rounded))
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
