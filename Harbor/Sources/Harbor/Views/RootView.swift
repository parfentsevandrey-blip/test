import AppKit
import SwiftUI

struct RootView: View {
    @Environment(Store.self) private var store

    var body: some View {
        @Bindable var store = store
        ZStack(alignment: .bottom) {
            HarborBackground(tint: store.selected?.color ?? Color(red: 0.1, green: 0.52, blue: 0.86))
            if store.onboarded {
                NavigationSplitView {
                    Sidebar()
                        .navigationSplitViewColumnWidth(min: 200, ideal: 220, max: 260)
                } detail: {
                    Group {
                        if let app = store.selected {
                            AppDetail(app: app)
                                .id(app.id)
                        } else {
                            Shelf()
                        }
                    }
                    .transition(.opacity)
                }
            } else {
                OnboardingView()
                    .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
            if let toast = store.toast {
                Toast(text: toast).padding(.bottom, 24)
            }
        }
        .animation(.smooth, value: store.onboarded)
        .animation(.smooth, value: store.selectedID)
        .alert(store.alert?.message ?? "", isPresented: Binding(get: { store.alert != nil }, set: { if !$0 { store.alert = nil } })) {
            if store.alert?.recovery == .appManagement {
                Button("Открыть настройки") { Store.openAppManagementSettings() }
            }
            Button("Понятно", role: .cancel) {}
        }
    }
}

struct Sidebar: View {
    @Environment(Store.self) private var store

    var body: some View {
        @Bindable var store = store
        List(selection: Binding<ShelfSection?>(get: { store.section }, set: { store.section = $0 ?? .discover; store.selectedID = nil })) {
            ForEach(ShelfSection.allCases) { section in
                Label(section.title, systemImage: section.symbol)
                    .badge(section == .updates ? store.updates.count : 0)
                    .tag(section)
            }
            if !store.installedApps.isEmpty {
                Section("Установлено") {
                    ForEach(store.installedApps) { app in
                        Button {
                            store.selectedID = app.id
                        } label: {
                            HStack(spacing: 8) {
                                AppIconView(app: app, size: 22)
                                Text(app.name)
                                Spacer()
                                if case .update = store.state(app) {
                                    Circle().fill(app.color).frame(width: 7, height: 7)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                if store.checking {
                    Label("Проверяю обновления…", systemImage: "arrow.triangle.2.circlepath")
                } else if let error = store.loadError {
                    Label(error, systemImage: "wifi.exclamationmark").foregroundStyle(.orange)
                } else if let date = store.lastCheck {
                    Label("Проверено \(date.formatted(.relative(presentation: .named)))", systemImage: "checkmark.circle")
                }
                Label(store.autoUpdate ? "Автообновление включено" : "Автообновление выключено",
                      systemImage: store.autoUpdate ? "bolt.badge.checkmark" : "bolt.slash")
            }
            .font(.system(size: 11))
            .foregroundStyle(.secondary)
            .lineLimit(2)
            .padding(12)
        }
    }
}

/// Список приложений выбранного раздела.
struct Shelf: View {
    @Environment(Store.self) private var store
    private let columns = [GridItem(.adaptive(minimum: 300, maximum: 420), spacing: 18)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                let apps = list
                if apps.isEmpty {
                    empty
                } else {
                    LazyVGrid(columns: columns, spacing: 18) {
                        ForEach(apps) { app in AppCard(app: app) }
                    }
                }
            }
            .padding(28)
        }
        .scrollContentBackground(.hidden)
    }

    private var list: [CatalogApp] {
        switch store.section {
        case .discover: store.apps.filter { $0.bundleID != Bundle.main.bundleIdentifier }
        case .updates: store.updates
        case .installed: store.installedApps
        }
    }

    @ViewBuilder
    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 6) {
                Text(store.section == .discover ? "Гавань" : store.section.title)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                Text(subtitle).font(.title3).foregroundStyle(.secondary)
            }
            Spacer()
            if store.section == .updates, store.updates.count > 1 {
                Button("Обновить всё") { Task { await store.updateAll() } }
                    .buttonStyle(.glassProminent)
            }
            Button {
                Task { await store.check() }
            } label: {
                Image(systemName: "arrow.clockwise")
            }
            .buttonStyle(.glass)
            .disabled(store.checking)
            .help("Проверить обновления (⌘R)")
        }
    }

    private var subtitle: String {
        switch store.section {
        case .discover: "Свободные программы для Mac: без подписок, рекламы и слежки. Всё ставится и обновляется само."
        case .updates: store.updates.isEmpty ? "Всё обновлено." : "Новые версии сохранят ваши данные и настройки."
        case .installed: "То, что уже стоит на этом Mac."
        }
    }

    @ViewBuilder
    private var empty: some View {
        if store.catalog == nil {
            if let error = store.loadError {
                ContentUnavailableView("Каталог недоступен", systemImage: "wifi.exclamationmark", description: Text(error))
            } else {
                ProgressView("Загружаю каталог…").frame(maxWidth: .infinity, minHeight: 300)
            }
        } else if store.section == .updates {
            ContentUnavailableView("Обновлений нет", systemImage: "checkmark.seal", description: Text("Все установленные приложения последней версии."))
        } else {
            ContentUnavailableView("Пока ничего не установлено", systemImage: "square.dashed", description: Text("Выберите приложение во «Всех приложениях»."))
        }
    }
}

struct AppCard: View {
    @Environment(Store.self) private var store
    let app: CatalogApp
    @State private var hover = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 14) {
                AppIconView(app: app, size: 64)
                VStack(alignment: .leading, spacing: 4) {
                    Text(app.name).font(.system(size: 19, weight: .bold, design: .rounded))
                    Text(app.tagline).font(.system(size: 13)).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            Text(app.summary)
                .font(.system(size: 12.5))
                .foregroundStyle(.secondary)
                .lineLimit(3, reservesSpace: true)
            HStack {
                if let replaces = app.replaces {
                    Text("вместо \(replaces)").font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1)
                }
                Spacer()
                ActionButton(app: app)
            }
        }
        .padding(18)
        .background {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(LinearGradient(colors: [app.color.opacity(hover ? 0.16 : 0.10), .clear], startPoint: .topLeading, endPoint: .bottomTrailing))
        }
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .scaleEffect(hover ? 1.012 : 1)
        .animation(.snappy(duration: 0.2), value: hover)
        .onHover { hover = $0 }
        .contentShape(RoundedRectangle(cornerRadius: 24))
        .onTapGesture { store.selectedID = app.id }
    }
}
