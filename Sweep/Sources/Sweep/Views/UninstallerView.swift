import AppKit
import SwiftUI
import UniformTypeIdentifiers

enum AppFilter: String, CaseIterable, Identifiable {
    case all, unused, large, appStore, intel, leftovers

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "Все"
        case .unused: "Не используются"
        case .large: "Крупные"
        case .appStore: "App Store"
        case .intel: "Intel"
        case .leftovers: "Остатки"
        }
    }
}

/// «Программы»: удаление вместе со всеми следами, «сброс» и остатки уже удалённых программ.
struct UninstallerView: View {
    @Environment(AppModel.self) private var model
    @State private var search = ""
    @State private var filter: AppFilter = .all
    @State private var dropTargeted = false

    private var visible: [AppInfo] {
        model.apps.filter { app in
            guard search.isEmpty || app.bundle.name.localizedCaseInsensitiveContains(search) || app.bundle.bundleID.localizedCaseInsensitiveContains(search) else { return false }
            switch filter {
            case .all, .leftovers: return true
            case .unused: return app.unused
            case .large: return (app.size ?? 0) > 500_000_000
            case .appStore: return app.bundle.isAppStore
            case .intel: return app.bundle.isIntelOnly
            }
        }
        .sorted { filter == .large ? ($0.size ?? 0) > ($1.size ?? 0) : $0.bundle.name.localizedCaseInsensitiveCompare($1.bundle.name) == .orderedAscending }
    }

    var body: some View {
        VStack(spacing: 0) {
            ModuleHeader(title: "Программы", subtitle: "Удаление вместе с настройками, кэшами, контейнерами и службами автозапуска. Перетащите сюда программу из Finder.") {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Поиск", text: $search).textFieldStyle(.plain).frame(width: 160)
                }
                .padding(.horizontal, 12)
                .frame(height: 32)
                .glassEffect(.regular.interactive(), in: .capsule)
            }
            filters
            HStack(alignment: .top, spacing: 16) {
                appList.frame(width: 290)
                if filter == .leftovers {
                    OrphansPanel()
                } else {
                    AppDetailPanel()
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .dropDestination(for: URL.self) { urls, _ in
            guard let app = urls.first(where: { $0.pathExtension == "app" }) else { return false }
            filter = .all
            model.openApp(app)
            return true
        } isTargeted: { dropTargeted = $0 }
        .overlay {
            if dropTargeted {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(Module.uninstaller.tint, style: StrokeStyle(lineWidth: 2.5, dash: [10, 7]))
                    .overlay { Label("Отпустите, чтобы найти все следы программы", systemImage: "app.badge.checkmark")
                        .font(.system(size: 16, weight: .semibold)).padding(16).glassEffect(.regular, in: .capsule) }
                    .padding(12)
                    .allowsHitTesting(false)
            }
        }
        .onChange(of: filter) { if filter == .leftovers, model.orphans.isEmpty { model.scanOrphans() } }
    }

    private var filters: some View {
        GlassEffectContainer(spacing: 6) {
            HStack(spacing: 6) {
                ForEach(AppFilter.allCases) { f in
                    Button { withAnimation(.snappy) { filter = f } } label: {
                        Text(f.title).font(.system(size: 12, weight: .semibold)).padding(.horizontal, 12).padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                    .glassEffect(filter == f ? .regular.tint(Module.uninstaller.tint.opacity(0.5)).interactive() : .regular.interactive(), in: .capsule)
                }
                Spacer()
            }
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 12)
    }

    private var appList: some View {
        ContentCard {
            if model.appsLoading && model.apps.isEmpty {
                ProgressView("Ищу программы…").frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 2) {
                        ForEach(visible) { app in
                            AppRow(app: app, selected: model.selectedAppID == app.id) {
                                if filter == .leftovers { filter = .all }
                                model.selectApp(app.id)
                            }
                        }
                    }
                    .padding(6)
                }
            }
        }
    }
}

struct AppRow: View {
    let app: AppInfo
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                FileIcon(url: app.bundle.url, size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 5) {
                        Text(app.bundle.name).font(.system(size: 12.5, weight: .semibold)).lineLimit(1)
                        if app.bundle.isIntelOnly { Badge(text: "Intel", color: .orange) }
                        if app.unused && app.lastUsed != nil { Badge(text: "давно", color: .purple) }
                    }
                    Text(app.lastUsed.map { "Открывали \(Format.relative($0))" } ?? (app.bundle.version.map { "Версия \($0)" } ?? ""))
                        .font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 4)
                Text(app.size.map(Format.bytes) ?? "…").font(.system(size: 11.5, weight: .semibold)).monospacedDigit().foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background {
                if selected { RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Module.uninstaller.tint.opacity(0.18)) }
            }
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

struct Badge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text).font(.system(size: 9.5, weight: .bold)).foregroundStyle(color)
            .lineLimit(1).fixedSize()
            .padding(.horizontal, 5).padding(.vertical, 1)
            .background(Capsule().fill(color.opacity(0.14)))
    }
}

/// Подробности выбранной программы и найденные следы, сгруппированные по видам.
struct AppDetailPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ContentCard {
            if let app = model.selectedApp {
                VStack(spacing: 0) {
                    header(app)
                    Divider()
                    if model.leftoversLoading {
                        ProgressView("Ищу следы программы…").frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        leftoversList
                    }
                    Divider()
                    actions(app)
                }
            } else {
                ContentUnavailableView("Выберите программу", systemImage: "square.grid.2x2",
                                       description: Text("Или перетащите её сюда из Finder — найдём все её файлы."))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private func header(_ app: AppInfo) -> some View {
        HStack(spacing: 16) {
            FileIcon(url: app.bundle.url, size: 64)
                .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
            VStack(alignment: .leading, spacing: 5) {
                Text(app.bundle.name).font(.system(size: 20, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7)
                Text(app.bundle.bundleID).font(.system(size: 11, design: .monospaced)).foregroundStyle(.secondary).textSelection(.enabled)
                HStack(spacing: 6) {
                    if let v = app.bundle.version { Badge(text: "v\(v)", color: .secondary) }
                    if app.bundle.isAppStore { Badge(text: "App Store", color: .blue) }
                    if app.bundle.isIntelOnly { Badge(text: "Только Intel — через Rosetta", color: .orange) }
                    if app.bundle.isApple { Badge(text: "Apple", color: .gray) }
                    if let team = app.bundle.teamID { Badge(text: team, color: .secondary) }
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(app.size == nil ? "…" : Format.bytes((app.size ?? 0) + model.leftovers.reduce(0) { $0 + $1.size }))
                    .font(.system(size: 20, weight: .bold, design: .rounded)).monospacedDigit()
                Text(app.size == nil ? "считаю размер…" : "программа \(Format.bytes(app.size ?? 0)) + следы \(Format.bytes(model.leftovers.reduce(0) { $0 + $1.size }))")
                    .font(.system(size: 10.5)).foregroundStyle(.secondary)
                Text(app.lastUsed.map { "Открывали \(Format.relative($0))" } ?? "Нет данных о запусках")
                    .font(.system(size: 10.5)).foregroundStyle(.secondary)
            }
        }
        .padding(18)
    }

    private var leftoversList: some View {
        let groups = Dictionary(grouping: model.leftovers) { $0.group ?? "Другое" }
        let order = groups.keys.sorted { (groups[$0]?.reduce(0) { $0 + $1.size } ?? 0) > (groups[$1]?.reduce(0) { $0 + $1.size } ?? 0) }
        return ScrollView {
            if model.leftovers.isEmpty {
                ContentUnavailableView("Следов не найдено", systemImage: "checkmark.seal", description: Text("Программа хранит всё внутри себя."))
                    .padding(.top, 40)
            }
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                ForEach(order, id: \.self) { group in
                    Section {
                        ForEach(groups[group] ?? []) { item in
                            ItemRow(item: item, symbol: "folder.fill") { model.toggleLeftover(item) }
                            Divider().opacity(0.35).padding(.leading, 60)
                        }
                    } header: {
                        HStack {
                            Text(group).font(.system(size: 11.5, weight: .bold)).textCase(.uppercase).foregroundStyle(.secondary)
                            Spacer()
                            Text(Format.bytes(groups[group]?.reduce(0) { $0 + $1.size } ?? 0)).font(.system(size: 11)).monospacedDigit().foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(.bar)
                    }
                }
            }
        }
    }

    private func actions(_ app: AppInfo) -> some View {
        let running = model.isRunning(app.bundle)
        let selectedSize = model.leftovers.filter(\.selected).reduce(0) { $0 + $1.size }
        return HStack(spacing: 10) {
            if running {
                Label("Программа запущена", systemImage: "exclamationmark.circle.fill").font(.system(size: 12)).foregroundStyle(.orange)
                Button("Завершить") { model.quit(app.bundle) }.buttonStyle(.glass)
            } else {
                Text("Программа уйдёт в Корзину").font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Button { model.uninstall(keepApp: true) } label: { Label("Сбросить", systemImage: "arrow.counterclockwise") }
                .buttonStyle(.glass)
                .controlSize(.large)
                .disabled(selectedSize == 0 || running)
                .help("Удалить настройки и данные, оставив программу — как после первой установки")
            Button { model.uninstall() } label: {
                Label("Удалить \(Format.bytes((app.size ?? 0) + selectedSize))", systemImage: "trash.fill").padding(.horizontal, 6)
            }
            .buttonStyle(.glassProminent)
            .tint(.red)
            .controlSize(.large)
            .disabled(running || app.bundle.isSystem || app.bundle.bundleID == Bundle.main.bundleIdentifier)
        }
        .padding(14)
    }
}

/// Остатки программ, которых больше нет.
struct OrphansPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ContentCard {
            VStack(spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Остатки удалённых программ").font(.system(size: 17, weight: .bold))
                        Text(CategoryID.leftovers.explanation).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(4).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Spacer()
                }
                .padding(18)
                Divider()
                if model.orphansLoading {
                    ProgressView("Сверяю со списком программ…").frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if model.orphans.isEmpty {
                    ContentUnavailableView("Остатков нет", systemImage: "checkmark.seal").frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(model.orphans) { item in
                                ItemRow(item: item, symbol: "puzzlepiece.extension.fill") {
                                    if let i = model.orphans.firstIndex(where: { $0.id == item.id }) { model.orphans[i].selected.toggle() }
                                }
                                Divider().opacity(0.35).padding(.leading, 60)
                            }
                        }
                    }
                }
                Divider()
                HStack {
                    Text("Отмечено \(Format.bytes(model.orphans.filter(\.selected).reduce(0) { $0 + $1.size }))").monospacedDigit().foregroundStyle(.secondary)
                    Spacer()
                    Button { model.scanOrphans() } label: { Label("Обновить", systemImage: "arrow.clockwise") }.buttonStyle(.glass)
                    Button("Удалить отмеченное") { model.cleanOrphans() }
                        .buttonStyle(.glassProminent)
                        .tint(Module.uninstaller.tint)
                        .disabled(!model.orphans.contains(where: \.selected))
                }
                .padding(14)
            }
        }
    }
}
