import SwiftUI

enum LibraryFilter: String, CaseIterable, Identifiable {
    case all, unwatched, favorites

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "Все"
        case .unwatched: "Не досмотрены"
        case .favorites: "Избранное"
        }
    }
}

enum LibrarySort: String, CaseIterable, Identifiable {
    case recent, added, title, duration

    var id: String { rawValue }

    var title: String {
        switch self {
        case .recent: "Недавние"
        case .added: "Дата добавления"
        case .title: "Название"
        case .duration: "Длительность"
        }
    }
}

struct LibraryView: View {
    @Environment(AppModel.self) private var model
    @State private var search = ""
    @State private var filter: LibraryFilter = .all
    @State private var sort: LibrarySort = .recent
    @Namespace private var glass

    private var library: MediaLibrary { model.library }

    private var visibleItems: [MediaItem] {
        var items: [MediaItem]
        switch sort {
        case .recent: items = library.recent
        case .added: items = library.items.sorted { $0.addedAt > $1.addedAt }
        case .title: items = library.items.sorted { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
        case .duration: items = library.items.sorted { $0.duration > $1.duration }
        }
        switch filter {
        case .all: break
        case .unwatched: items = items.filter { !$0.isWatched }
        case .favorites: items = items.filter(\.isFavorite)
        }
        if !search.isEmpty {
            items = items.filter { $0.title.localizedCaseInsensitiveContains(search) }
        }
        return items
    }

    var body: some View {
        ZStack(alignment: .top) {
            LibraryBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 34) {
                    hero
                    if library.items.isEmpty {
                        EmptyLibraryView()
                    } else {
                        if model.engine.hasMedia, let current = model.engine.current {
                            NowPlayingCard(item: current)
                        }
                        let continuing = library.continueWatching.filter { $0.id != model.engine.current?.id }
                        if search.isEmpty, filter == .all, !continuing.isEmpty {
                            section("Продолжить просмотр", symbol: "play.circle") {
                                ScrollView(.horizontal) {
                                    LazyHStack(spacing: 22) {
                                        ForEach(continuing) { item in
                                            MediaCard(item: item, list: continuing, style: .wide)
                                                .frame(width: 380)
                                        }
                                    }
                                    .padding(.vertical, 16)
                                    .padding(.horizontal, 4)
                                }
                                .scrollIndicators(.never)
                                .scrollClipDisabled()
                            }
                        }
                        let items = visibleItems
                        section(search.isEmpty ? "Медиатека" : "Найдено: \(items.count)", symbol: "square.grid.2x2") {
                            if items.isEmpty {
                                ContentUnavailableView.search(text: search)
                                    .frame(maxWidth: .infinity)
                            } else {
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 250, maximum: 380), spacing: 24)], spacing: 30) {
                                    ForEach(items) { item in
                                        MediaCard(item: item, list: items, style: .grid)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 36)
                .padding(.top, 88)
                .padding(.bottom, 48)
            }
            .scrollEdgeEffectStyle(.soft, for: .top)

            header
        }
        .ignoresSafeArea()
    }

    // MARK: Шапка

    private var header: some View {
        HStack(spacing: 10) {
            Spacer(minLength: 84)
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    HStack(spacing: 7) {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField("Поиск", text: $search)
                            .textFieldStyle(.plain)
                            .frame(width: 170)
                        if !search.isEmpty {
                            Button { search = "" } label: { Image(systemName: "xmark.circle.fill") }
                                .buttonStyle(.plain)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .glassEffect(.regular.interactive(), in: .capsule)

                    HStack(spacing: 2) {
                        ForEach(LibraryFilter.allCases) { f in
                            Button {
                                withAnimation(.snappy) { filter = f }
                            } label: {
                                Text(f.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .padding(.horizontal, 11)
                                    .padding(.vertical, 6)
                                    .background {
                                        if filter == f {
                                            Capsule().fill(.white.opacity(0.18)).matchedGeometryEffect(id: "filter", in: glass)
                                        }
                                    }
                                    .contentShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(3)
                    .glassEffect(.regular, in: .capsule)

                    Menu {
                        Picker("Сортировка", selection: $sort) {
                            ForEach(LibrarySort.allCases) { Text($0.title).tag($0) }
                        }
                        .pickerStyle(.inline)
                        Divider()
                        Button("Убрать отсутствующие файлы") { library.removeMissing() }
                        Button("Сбросить историю просмотра") { library.clearHistory() }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                    }
                    .menuIndicator(.hidden)
                    .menuStyle(.button)
                    .buttonStyle(.glass)
                    .controlSize(.large)
                    .help("Сортировка")

                    Button { model.presentOpenPanel(folders: true) } label: {
                        Image(systemName: "folder.badge.plus")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.large)
                    .help("Открыть папку (⇧⌘O)")

                    Button { model.presentOpenPanel() } label: {
                        Label("Открыть", systemImage: "play.rectangle.fill")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .help("Открыть файл (⌘O)")
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 6) {
            ShimmerTitle(text: "Lumen")
            Text(summary)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.secondary)
                .contentTransition(.numericText())
        }
    }

    private var summary: String {
        let count = library.items.count
        guard count > 0 else { return "Плеер, который выглядит так же хорошо, как ваши фильмы" }
        return "\(count) видео · \(TimeFormat.duration(library.totalDuration)) просмотра"
    }

    private func section<Content: View>(_ title: String, symbol: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(title, systemImage: symbol)
                .font(.system(size: 19, weight: .bold, design: .rounded))
            content()
        }
    }
}

/// Большая карточка «Сейчас играет» — возвращает в плеер с того же места.
private struct NowPlayingCard: View {
    @Environment(AppModel.self) private var model
    let item: MediaItem

    var body: some View {
        Button { model.resumeCurrent() } label: {
            HStack(spacing: 18) {
                ZStack {
                    if let image = model.library.thumbnails[item.id] {
                        Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Rectangle().fill(.white.opacity(0.08))
                    }
                }
                .frame(width: 168, height: 94)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 6) {
                    Label("Сейчас", systemImage: "waveform")
                        .symbolEffect(.variableColor.iterative, isActive: true)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.tint)
                    Text(item.title)
                        .font(.system(size: 17, weight: .semibold))
                        .lineLimit(1)
                    Text("\(TimeFormat.string(model.engine.currentTime)) из \(TimeFormat.string(model.engine.duration))")
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
                Spacer()
                Image(systemName: "play.fill")
                    .font(.system(size: 20, weight: .bold))
                    .frame(width: 54, height: 54)
                    .glassEffect(.regular.tint(.accentColor.opacity(0.5)).interactive(), in: .circle)
            }
            .padding(14)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle())
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 26))
    }
}

private struct EmptyLibraryView: View {
    @Environment(AppModel.self) private var model
    @State private var phase: CGFloat = 0

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "play.rectangle.on.rectangle.fill")
                .font(.system(size: 64, weight: .medium))
                .symbolRenderingMode(.palette)
                .foregroundStyle(.white, .tint)
                .symbolEffect(.breathe.pulse.byLayer, options: .repeat(.continuous))
            VStack(spacing: 8) {
                Text("Перетащите сюда видео")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                Text("MP4 · MOV · MKV · WebM · H.264 · HEVC · AV1 · VP9 · HDR10 · Dolby Vision · AC-3 · E-AC-3")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            HStack(spacing: 12) {
                Button { model.presentOpenPanel(folders: true) } label: {
                    Label("Открыть папку", systemImage: "folder")
                }
                .buttonStyle(.glass)
                Button { model.presentOpenPanel() } label: {
                    Label("Открыть файл", systemImage: "play.rectangle.fill")
                }
                .buttonStyle(.glassProminent)
            }
            .controlSize(.extraLarge)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 70)
        .glassEffect(.regular, in: .rect(cornerRadius: 40))
        .overlay {
            RoundedRectangle(cornerRadius: 40, style: .continuous)
                .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [9, 9], dashPhase: phase))
                .foregroundStyle(.white.opacity(0.22))
        }
        .onAppear {
            withAnimation(.linear(duration: 3).repeatForever(autoreverses: false)) { phase = -72 }
        }
    }
}
