import SwiftUI
import UniformTypeIdentifiers

/// Главное окно: сияющий фон, стеклянная шапка с полем magnet, список раздач и инспектор справа.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @State private var dropTargeted = false
    @FocusState private var magnetFocused: Bool
    @Namespace private var glass

    var body: some View {
        @Bindable var model = model
        ZStack {
            AuroraBackground(activity: model.activity)
            HStack(spacing: 0) {
                VStack(spacing: 0) {
                    header
                    filters
                    content
                }
                .frame(maxWidth: .infinity)
                .overlay(alignment: .bottom) {
                    if !model.torrents.isEmpty {
                        ControlIsland()
                            .padding(.bottom, 18)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                if let selected = model.selected {
                    InspectorPanel(torrent: selected)
                        .frame(width: 400)
                        .padding([.trailing, .bottom, .top], 12)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                        .id(selected.id)
                }
            }
            .animation(.smooth(duration: 0.35), value: model.selectedID)
            if dropTargeted { dropOverlay }
        }
        .overlay(alignment: .bottom) { toast }
        .frame(minWidth: 820, minHeight: 540)
        .dropDestination(for: URL.self) { urls, _ in
            model.open(urls)
            return !urls.isEmpty
        } isTargeted: { t in
            withAnimation(.smooth(duration: 0.2)) { dropTargeted = t }
        }
        .sheet(item: $model.pendingAdd) { pending in
            AddTorrentSheet(pending: pending)
        }
        .onKeyPress(.escape) {
            guard model.selectedID != nil else { return .ignored }
            model.selectedID = nil
            return .handled
        }
        .onKeyPress(.space) {
            guard !magnetFocused, let t = model.selected else { return .ignored }
            model.toggle(t)
            return .handled
        }
    }

    // MARK: - Шапка

    private var header: some View {
        @Bindable var model = model
        return HStack(spacing: 0) {
            Spacer(minLength: 84) // место для «светофора»; справа столько же — шапка ровно по центру
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "link")
                            .foregroundStyle(.secondary)
                            .symbolEffect(.bounce, value: magnetFocused)
                        TextField("Вставьте magnet-ссылку", text: $model.magnetText)
                            .textFieldStyle(.plain)
                            .focused($magnetFocused)
                            .onSubmit { if !model.magnetText.isEmpty { model.addMagnet(model.magnetText) } }
                        if !model.magnetText.isEmpty {
                            Button { model.addMagnet(model.magnetText) } label: {
                                Image(systemName: "arrow.down.circle.fill").font(.system(size: 17))
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(.tint)
                            .transition(.scale.combined(with: .opacity))
                        }
                    }
                    .font(.system(size: 13))
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .glassEffect(.regular.interactive(), in: .capsule)
                    .glassEffectID("magnet", in: glass)
                    .frame(minWidth: 220, idealWidth: 340, maxWidth: 380)

                    Button { model.presentOpenPanel() } label: {
                        Label("Открыть", systemImage: "doc.badge.plus")
                    }
                    .help("Открыть .torrent (⌘O)")
                    Button { model.presentCreatePanel() } label: {
                        Label("Создать", systemImage: "square.and.arrow.up.on.square")
                    }
                    .help("Создать торрент из файла или папки (⌘N)")

                    speeds
                }
                .buttonStyle(.glass)
                .controlSize(.large)
                .labelStyle(.titleAndIcon)
            }
            .fixedSize(horizontal: true, vertical: false)
            Spacer(minLength: 84)
        }
        .padding(.top, 10)
        .padding(.bottom, 8)
        .animation(.smooth(duration: 0.2), value: model.magnetText.isEmpty)
    }

    private var speeds: some View {
        HStack(spacing: 10) {
            VStack(alignment: .trailing, spacing: 1) {
                Label(Format.rate(model.totalDown), systemImage: "arrow.down")
                    .foregroundStyle(model.totalDown > 0 ? Color.blue : .secondary)
                Label(Format.rate(model.totalUp), systemImage: "arrow.up")
                    .foregroundStyle(model.totalUp > 0 ? Color(red: 0.15, green: 0.75, blue: 0.55) : .secondary)
            }
            .font(.system(size: 11, weight: .semibold))
            .monospacedDigit()
            .contentTransition(.numericText())
            .labelStyle(.titleAndIcon)
            .lineLimit(1)
            .fixedSize()
            .frame(minWidth: 74, alignment: .trailing)
            SpeedChart(samples: model.history, compact: true)
                .frame(width: 96, height: 26)
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .glassEffect(.regular, in: .capsule)
        .glassEffectID("speeds", in: glass)
    }

    // MARK: - Фильтры и список

    private var filters: some View {
        @Bindable var model = model
        return HStack(spacing: 8) {
            Spacer(minLength: 0)
            GlassEffectContainer(spacing: 6) {
                HStack(spacing: 6) {
                    ForEach(TorrentFilter.allCases) { f in
                        FilterChip(title: f.title, count: f == .all ? 0 : model.count(f), selected: model.filter == f) {
                            withAnimation(.bouncy) { model.filter = f }
                        }
                    }
                }
            }
            if model.torrents.count > 3 {
                HStack(spacing: 6) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Поиск", text: $model.search).textFieldStyle(.plain).frame(width: 140)
                }
                .font(.system(size: 12.5))
                .padding(.horizontal, 12)
                .frame(height: 30)
                .glassEffect(.regular.interactive(), in: .capsule)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 6)
    }

    @ViewBuilder
    private var content: some View {
        if model.torrents.isEmpty {
            emptyState
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(model.visible) { t in
                        TorrentCard(torrent: t, selected: t.id == model.selectedID)
                            .transition(.asymmetric(insertion: .scale(scale: 0.96).combined(with: .opacity),
                                                    removal: .opacity))
                    }
                }
                .frame(maxWidth: 920)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 120) // под плавающим островом управления
                .frame(maxWidth: .infinity)
                .animation(.smooth, value: model.visible.map(\.id))
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .overlay {
                if model.visible.isEmpty {
                    ContentUnavailableView.search(text: model.search)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            Image(systemName: "point.3.filled.connected.trianglepath.dotted")
                .font(.system(size: 64, weight: .thin))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.tint)
                .symbolEffect(.breathe, options: .repeating)
            VStack(spacing: 6) {
                Text("Перетащите сюда .torrent или magnet-ссылку")
                    .font(.system(size: 20, weight: .semibold))
                Text("Swarm сам найдёт пиров через трекеры и DHT. Без рекламы, телеметрии и сторонних библиотек.")
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button { model.presentOpenPanel() } label: { Label("Открыть .torrent", systemImage: "doc.badge.plus") }
                        .buttonStyle(.glassProminent)
                    Button { magnetFocused = true } label: { Label("Вставить magnet", systemImage: "link") }
                        .buttonStyle(.glass)
                    Button { model.presentCreatePanel() } label: { Label("Создать раздачу", systemImage: "square.and.arrow.up.on.square") }
                        .buttonStyle(.glass)
                }
                .controlSize(.extraLarge)
            }
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var dropOverlay: some View {
        RoundedRectangle(cornerRadius: 28, style: .continuous)
            .strokeBorder(Color.accentColor, style: StrokeStyle(lineWidth: 2.5, dash: [10, 7]))
            .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color.accentColor.opacity(0.08)))
            .overlay {
                Label("Отпустите, чтобы добавить", systemImage: "arrow.down.doc.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .padding(.horizontal, 22)
                    .padding(.vertical, 14)
                    .glassEffect(.regular.tint(.accentColor.opacity(0.3)), in: .capsule)
            }
            .padding(14)
            .allowsHitTesting(false)
            .transition(.opacity.combined(with: .scale(scale: 1.02)))
    }

    @ViewBuilder
    private var toast: some View {
        if let text = model.toast {
            Text(text)
                .font(.system(size: 12.5, weight: .medium))
                .lineLimit(2)
                .padding(.horizontal, 18)
                .padding(.vertical, 11)
                .glassEffect(.regular, in: .capsule)
                .padding(.bottom, model.torrents.isEmpty ? 22 : 118)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
