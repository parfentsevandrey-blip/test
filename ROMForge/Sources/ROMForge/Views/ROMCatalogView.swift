import SwiftUI

struct ROMCatalogView: View {
    @Environment(AppModel.self) private var model
    var specs: DeviceSpecs

    enum Filter: String, CaseIterable, Identifiable {
        case all = "Все"
        case available = "Готовые сборки"
        case privacy = "Приватность"
        case features = "Функции"
        case linux = "Linux"
        var id: String { rawValue }
    }

    @State private var filter: Filter = .all
    @State private var search = ""
    @State private var selected: ROMProject?

    private let columns = [GridItem(.adaptive(minimum: 280), spacing: 16)]

    var body: some View {
        ZStack {
            AmbientBackground(colors: [.purple, .teal])
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Прошивки для \(specs.displayName)")
                            .font(.largeTitle.weight(.bold))
                        Text("Кодовое имя «\(specs.codename)». Приложение проверило официальные серверы проектов — готовые сборки ставятся в один клик.")
                            .foregroundStyle(.secondary)
                    }
                    Picker("Фильтр", selection: $filter) {
                        ForEach(Filter.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .frame(maxWidth: 560)

                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(projects) { project in
                            Button { selected = project } label: {
                                ROMCard(project: project, availability: model.availability(for: project))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    if projects.isEmpty {
                        ContentUnavailableView.search(text: search)
                    }
                }
                .padding(28)
            }
        }
        .navigationTitle("Прошивки")
        .searchable(text: $search, placement: .toolbar, prompt: "Поиск прошивки")
        .toolbar {
            ToolbarItem {
                Button {
                    Task { await model.findROMs(for: specs) }
                } label: {
                    Label("Проверить снова", systemImage: "arrow.clockwise")
                }
            }
        }
        .sheet(item: $selected) { project in
            ROMDetailView(project: project, specs: specs)
                .environment(model)
        }
    }

    private var projects: [ROMProject] {
        ROMCatalog.all
            .filter { project in
                switch filter {
                case .all: true
                case .available: model.availability(for: project).isAvailable
                case .privacy: project.tags.contains(.privacy) || project.tags.contains(.degoogled)
                case .features: project.tags.contains(.features) || project.tags.contains(.pixel)
                case .linux: project.tags.contains(.linux)
                }
            }
            .filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search)
                || $0.tagline.localizedCaseInsensitiveContains(search) }
            .sorted { rank(model.availability(for: $0)) < rank(model.availability(for: $1)) }
    }

    private func rank(_ availability: ROMAvailability) -> Int {
        switch availability {
        case .available: 0
        case .checking: 1
        case .manual: 2
        case .failed: 3
        case .notFound: 4
        }
    }
}

private struct ROMCard: View {
    var project: ROMProject
    var availability: ROMAvailability
    @State private var hovering = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                IconTile(symbol: project.symbol, colors: project.colors)
                Spacer()
                availability.badge
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(project.name).font(.title3.weight(.semibold))
                Text(project.tagline).font(.callout).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                ForEach(project.tags.prefix(2), id: \.self) { tag in
                    Badge(text: tag.rawValue, symbol: tag.symbol)
                }
                Spacer()
                if let build = availability.builds.first {
                    Text(build.androidVersion.map { "Android \($0)" } ?? build.version)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .topLeading)
        .glassCard(tint: availability.isAvailable ? project.colors.last : nil)
        .opacity(availability == .notFound ? 0.6 : 1)
        .scaleEffect(hovering ? 1.015 : 1)
        .animation(.spring(duration: 0.25), value: hovering)
        .onHover { hovering = $0 }
        .contentShape(RoundedRectangle(cornerRadius: 22))
    }
}
