import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            List(selection: Binding(get: { model.screen }, set: { if let s = $0 { model.screen = s } })) {
                Section("Каталог") {
                    row(.hot)
                    row(.popular)
                    row(.catalog, count: Catalog.all.count)
                }
                Section("Семейства") {
                    ForEach(Family.allCases) { f in row(.family(f), count: Catalog.all.filter { $0.family == f }.count) }
                }
                Section("На этом Mac") {
                    row(.machines, count: model.machines.count)
                    row(.downloads, count: model.jobs.filter { $0.state != .done }.count)
                }
            }
            .navigationSplitViewColumnWidth(230)
            .safeAreaInset(edge: .bottom) { macBadge }
        } detail: {
            NavigationStack(path: $model.path) {
                content
                    .navigationDestination(for: String.self) { id in
                        if let d = Catalog.distro(id) { DistroDetailView(distro: d) }
                    }
            }
            .id(model.screen)
            .background(AuroraBackground())
        }
        .searchable(text: $model.search, placement: .toolbar, prompt: "Поиск: Ubuntu, KDE, приватность…")
        .onChange(of: model.search) { _, text in
            if !text.isEmpty, model.screen == .hot || model.screen == .machines || model.screen == .downloads { model.screen = .catalog }
        }
        .sheet(item: $model.installTarget) { InstallSheet(distro: $0) }
        .onChange(of: model.openRequest) { _, id in
            guard let id else { return }
            openWindow(id: "machine", value: id)
            model.openRequest = nil
        }
        .alert("Vessel", isPresented: Binding(get: { model.alert != nil }, set: { if !$0 { model.alert = nil } })) {
            Button("OK") { model.alert = nil }
        } message: { Text(model.alert ?? "") }
    }

    @ViewBuilder
    private var content: some View {
        switch model.screen {
        case .hot: HotView()
        case .popular: PopularView()
        case .catalog: CatalogView()
        case .family(let f): FamilyView(family: f)
        case .machines: MachinesView()
        case .downloads: DownloadsView()
        }
    }

    private func row(_ screen: Screen, count: Int? = nil) -> some View {
        Label(screen.title, systemImage: screen.symbol)
            .badge(count ?? 0)
            .tag(screen)
    }

    private var macBadge: some View {
        HStack(spacing: 8) {
            Image(systemName: model.virtualizationSupported ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                .foregroundStyle(model.virtualizationSupported ? .green : .orange)
            VStack(alignment: .leading, spacing: 1) {
                Text(DemoUI.isOn ? "Apple M4 Pro" : MacInfo.chip).font(.system(size: 11, weight: .semibold)).lineLimit(1)
                Text("\(Format.plural(DemoUI.isOn ? 14 : MacInfo.cores, "ядро", "ядра", "ядер")) · \(DemoUI.isOn ? 48 : MacInfo.memoryGB) ГБ")
                    .font(.system(size: 10)).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(10)
        .glassEffect(.regular, in: .rect(cornerRadius: 14))
        .padding(10)
    }
}
