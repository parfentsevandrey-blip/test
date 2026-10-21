import SwiftUI

enum InspectorTab: String, CaseIterable, Identifiable {
    case overview, files, peers, trackers

    var id: String { rawValue }

    var title: String {
        switch self {
        case .overview: "Обзор"
        case .files: "Файлы"
        case .peers: "Пиры"
        case .trackers: "Трекеры"
        }
    }

    var symbol: String {
        switch self {
        case .overview: "sparkles"
        case .files: "doc.on.doc"
        case .peers: "person.2"
        case .trackers: "antenna.radiowaves.left.and.right"
        }
    }
}

/// Боковой стеклянный инспектор выбранной раздачи.
struct InspectorPanel: View {
    @Environment(AppModel.self) private var model
    let torrent: TorrentSnapshot

    @State private var tab: InspectorTab = .overview
    @Namespace private var tabs

    private var tint: Color { torrent.status.color }

    var body: some View {
        VStack(spacing: 14) {
            header
            tabBar
            ScrollView {
                Group {
                    switch tab {
                    case .overview: overview
                    case .files: FilesList(torrent: torrent)
                    case .peers: PeersList(torrent: torrent)
                    case .trackers: TrackersList(torrent: torrent)
                    }
                }
                .padding(.bottom, 10)
            }
            .scrollIndicators(.never)
            .scrollEdgeEffectStyle(.soft, for: .all)
        }
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 30))
    }

    private var header: some View {
        HStack(spacing: 12) {
            KindIcon(kind: ContentKind(torrent), size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(torrent.name)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .fixedSize(horizontal: false, vertical: true)
                Text("\(Format.bytes(torrent.totalSize)) · \(torrent.status.title)")
                    .font(.system(size: 11.5)).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button {
                withAnimation(.bouncy) { model.selectedID = nil }
            } label: {
                Image(systemName: "xmark").font(.system(size: 11, weight: .bold))
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
        }
    }

    private var tabBar: some View {
        HStack(spacing: 2) {
            ForEach(InspectorTab.allCases) { t in
                Button {
                    withAnimation(.snappy) { tab = t }
                } label: {
                    Label(t.title, systemImage: t.symbol)
                        .font(.system(size: 11.5, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .padding(.vertical, 6)
                        .frame(maxWidth: .infinity)
                        .background {
                            if tab == t {
                                Capsule().fill(.primary.opacity(0.12)).matchedGeometryEffect(id: "tab", in: tabs)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .labelStyle(.titleAndIcon)
        .padding(3)
        .background(Capsule().fill(.primary.opacity(0.05)))
    }

    // MARK: Обзор

    private var overview: some View {
        VStack(spacing: 14) {
            SwarmConstellation(snapshot: torrent, tint: tint)
                .frame(height: 340)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(LinearGradient(colors: [.white.opacity(0.35), .white.opacity(0.04), .white.opacity(0.12)],
                                                     startPoint: .top, endPoint: .bottom), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.25), radius: 18, y: 8)

            section("Скорость") {
                SpeedChart(samples: model.torrentHistory[torrent.id] ?? [])
                    .frame(height: 110)
                HStack {
                    Label(Format.rate(torrent.downRate), systemImage: "arrow.down").foregroundStyle(.blue)
                    Spacer()
                    Label(Format.rate(torrent.upRate), systemImage: "arrow.up").foregroundStyle(Color(red: 0.15, green: 0.75, blue: 0.55))
                }
                .font(.system(size: 12, weight: .semibold))
                .monospacedDigit()
            }

            if torrent.pieceCount > 0 {
                section("Куски · \(torrent.pieceCount) по \(Format.bytes(Int64(torrent.pieceLength)))") {
                    PieceMosaic(snapshot: torrent, tint: tint)
                        .frame(height: mosaicHeight)
                }
            }

            section("Сводка") {
                Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 7) {
                    row("Готово", "\(Format.percent(torrent.progress)) · \(Format.bytes(torrent.doneSize)) из \(Format.bytes(torrent.wantedSize))")
                    row("Скачано", Format.bytes(torrent.downloadedTotal))
                    row("Отдано", "\(Format.bytes(torrent.uploadedTotal)) · рейтинг \(String(format: "%.2f", torrent.ratio))")
                    row("Пиры", "\(torrent.peersConnected) подключено, из них сидов \(torrent.seedsConnected) · известно \(torrent.peersKnown)")
                    if let eta = Format.eta(torrent.eta) { row("Осталось", eta) }
                    row("Папка", torrent.savePath.path)
                    row("Добавлен", torrent.addedAt.formatted(date: .abbreviated, time: .shortened))
                    if let done = torrent.completedAt { row("Завершён", done.formatted(date: .abbreviated, time: .shortened)) }
                }
                .font(.system(size: 12))
            }

            section("Режим") {
                Toggle(isOn: Binding(get: { torrent.isSequential }, set: { model.setSequential(torrent, $0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Последовательная загрузка")
                        Text("Куски идут по порядку — видео можно начать смотреть раньше")
                            .font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
                .toggleStyle(.switch)
            }

            HStack(spacing: 8) {
                Button { model.toggle(torrent) } label: {
                    Label([.paused, .finished, .error].contains(torrent.status) ? "Продолжить" : "Пауза",
                          systemImage: [.paused, .finished, .error].contains(torrent.status) ? "play.fill" : "pause.fill")
                }
                Button { model.reveal(torrent) } label: { Label("В Finder", systemImage: "folder") }
                Button { model.copyMagnet(torrent) } label: { Label("Magnet", systemImage: "link") }
            }
            .buttonStyle(.glass)
            .controlSize(.regular)
        }
    }

    private var mosaicHeight: CGFloat {
        let columns = max(1, Int(330 / 8.5))
        let rows = min(18, Int(ceil(Double(torrent.pieceCount) / Double(columns))))
        return CGFloat(max(1, rows)) * 8.5
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(.secondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.primary.opacity(0.05)))
    }

    private func row(_ title: String, _ value: String) -> some View {
        GridRow {
            Text(title).foregroundStyle(.secondary)
            Text(value).textSelection(.enabled).lineLimit(2)
        }
    }
}

// MARK: - Файлы

struct FilesList: View {
    @Environment(AppModel.self) private var model
    let torrent: TorrentSnapshot

    var body: some View {
        VStack(spacing: 6) {
            if torrent.files.isEmpty {
                ContentUnavailableView("Список файлов появится после получения метаданных",
                                       systemImage: "doc.badge.clock")
                    .frame(height: 220)
            } else {
                HStack {
                    Text("\(torrent.files.count) файлов").foregroundStyle(.secondary)
                    Spacer()
                    Button("Все") { model.setPriority(torrent, files: torrent.files.map(\.id), .normal) }
                    Button("Ничего") { model.setPriority(torrent, files: torrent.files.map(\.id), .skip) }
                }
                .font(.system(size: 11.5, weight: .medium))
                .buttonStyle(.glass)
                .controlSize(.small)
                LazyVStack(spacing: 4) {
                    ForEach(torrent.files) { file in FileRow(torrent: torrent, file: file) }
                }
            }
        }
    }
}

private struct FileRow: View {
    @Environment(AppModel.self) private var model
    let torrent: TorrentSnapshot
    let file: FileSnapshot
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 10) {
            Toggle("", isOn: Binding(get: { file.priority != .skip },
                                     set: { model.setPriority(torrent, files: [file.id], $0 ? .normal : .skip) }))
                .toggleStyle(.checkbox)
                .labelsHidden()
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text((file.path as NSString).lastPathComponent).font(.system(size: 12, weight: .medium)).lineLimit(1)
                    if file.priority == .high {
                        Image(systemName: "flame.fill").font(.system(size: 9)).foregroundStyle(.orange)
                    }
                }
                LiquidProgress(progress: file.progress, tint: file.priority == .skip ? .gray : torrent.status.color, height: 4)
                Text("\(Format.bytes(file.size)) · \(Format.percent(file.progress))")
                    .font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
            }
            if file.progress >= 1 {
                Button { model.open(file: file) } label: { Image(systemName: "play.fill") }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .controlSize(.small)
                    .help("Открыть")
            }
            Menu {
                ForEach(FilePriority.allCases, id: \.self) { p in
                    Button(p.title) { model.setPriority(torrent, files: [file.id], p) }
                }
            } label: {
                Image(systemName: "ellipsis")
            }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
            .frame(width: 24)
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(hovering ? 0.07 : 0.03)))
        .onHover { hovering = $0 }
        .opacity(file.priority == .skip ? 0.55 : 1)
    }
}

// MARK: - Пиры

struct PeersList: View {
    let torrent: TorrentSnapshot

    var body: some View {
        if torrent.peers.isEmpty {
            ContentUnavailableView("Пока нет подключений", systemImage: "person.2.slash",
                                   description: Text("Ищу участников через трекеры, DHT и PEX"))
                .frame(height: 220)
        } else {
            LazyVStack(spacing: 4) {
                ForEach(torrent.peers) { peer in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(peer.isSeed ? Color(red: 0.2, green: 0.85, blue: 0.7) : Color(red: 0.6, green: 0.5, blue: 1.0))
                            .frame(width: 8, height: 8)
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(peer.address).font(.system(size: 11.5, weight: .medium, design: .monospaced)).lineLimit(1)
                                Spacer()
                                Text(peer.source.title)
                                    .font(.system(size: 9.5, weight: .bold))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 1)
                                    .background(Capsule().fill(.primary.opacity(0.08)))
                            }
                            Text(peer.client).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1)
                            HStack(spacing: 10) {
                                LiquidProgress(progress: peer.progress, tint: .secondary, height: 3)
                                Text("↓ \(Format.rate(peer.downRate))").foregroundStyle(peer.downRate > 0 ? Color.blue : .secondary)
                                Text("↑ \(Format.rate(peer.upRate))").foregroundStyle(peer.upRate > 0 ? Color(red: 0.15, green: 0.75, blue: 0.55) : .secondary)
                            }
                            .font(.system(size: 10.5, weight: .medium))
                            .monospacedDigit()
                        }
                    }
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(0.03)))
                }
            }
        }
    }
}

// MARK: - Трекеры

struct TrackersList: View {
    let torrent: TorrentSnapshot

    var body: some View {
        if torrent.trackers.isEmpty {
            ContentUnavailableView("Трекеров нет", systemImage: "antenna.radiowaves.left.and.right.slash",
                                   description: Text("Раздача работает через DHT и обмен пирами"))
                .frame(height: 220)
        } else {
            LazyVStack(spacing: 4) {
                ForEach(torrent.trackers) { tracker in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(tracker.url).font(.system(size: 11.5, weight: .medium)).lineLimit(1).truncationMode(.middle)
                        HStack(spacing: 10) {
                            Text(tracker.status).foregroundStyle(tracker.status == "Работает" ? Color.green : .secondary).lineLimit(1)
                            Spacer()
                            if let s = tracker.seeders { Text("сидов \(s)") }
                            if let l = tracker.leechers { Text("качают \(l)") }
                            Text("пиров \(tracker.lastPeers)")
                        }
                        .font(.system(size: 10.5))
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(0.03)))
                }
            }
        }
    }
}
