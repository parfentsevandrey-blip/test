import AppKit
import SwiftUI
import UniformTypeIdentifiers

/// Кнопка загрузок в правом верхнем углу: всегда на месте, кольцо показывает ход активных загрузок.
struct DownloadsButton: View {
    @Environment(AppModel.self) private var model
    @State private var shown = false

    var body: some View {
        let downloads = model.downloads
        Button { shown.toggle() } label: {
            ZStack {
                Image(systemName: "arrow.down.circle")
                    .symbolEffect(.bounce, value: downloads.pulse)
                if downloads.active > 0 {
                    Circle()
                        .trim(from: 0, to: max(0.04, downloads.activeProgress))
                        .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .frame(width: 19, height: 19)
                        .animation(.easeOut(duration: 0.3), value: downloads.activeProgress)
                }
            }
            .overlay(alignment: .topTrailing) {
                if downloads.active > 1 {
                    Text("\(downloads.active)")
                        .font(.system(size: 9, weight: .bold).monospacedDigit())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 3.5)
                        .background(Color.accentColor, in: Capsule())
                        .offset(x: 6, y: -5)
                }
            }
        }
        .help(downloads.active > 0 ? "Загрузки: идёт \(downloads.active) — \(Int((downloads.activeProgress * 100).rounded())) %" : "Загрузки (⌥⌘L)")
        .popover(isPresented: $shown, arrowEdge: .bottom) {
            DownloadsPanel { shown = false }.environment(model)
        }
    }
}

/// Короткий список под кнопкой: последние загрузки и переход ко всем.
struct DownloadsPanel: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    var close: () -> Void = {}

    var body: some View {
        let items = model.downloads.items
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Загрузки").font(.headline)
                Spacer()
                Button("Очистить") { model.downloads.clearFinished() }
                    .buttonStyle(.borderless).font(.callout)
                    .disabled(!items.contains { $0.state != .running })
            }
            if items.isEmpty {
                VStack(spacing: 6) {
                    Image(systemName: "arrow.down.circle").font(.system(size: 30)).foregroundStyle(.tertiary)
                    Text("Пока ничего не скачано").foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 22)
            } else {
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(items.prefix(8)) { item in
                            DownloadRow(item: item, compact: true)
                        }
                    }
                }
                .frame(maxHeight: 360)
            }
            Divider()
            HStack {
                Button {
                    model.downloads.openFolder()
                    close()
                } label: { Label("Папка «Загрузки»", systemImage: "folder") }
                Spacer()
                Button {
                    openWindow(id: "downloads")
                    close()
                } label: { Label(items.count > 8 ? "Все загрузки (\(items.count))" : "Все загрузки", systemImage: "list.bullet") }
                .help("Окно загрузок (⌥⌘L)")
            }
            .buttonStyle(.borderless)
            .font(.callout)
        }
        .padding(16)
        .frame(width: 400)
    }
}

/// Строка загрузки: значок файла, имя, ход или итог, действия.
struct DownloadRow: View {
    @Environment(AppModel.self) private var model
    let item: Downloads.Item
    var compact = false
    @State private var hover = false

    var body: some View {
        HStack(spacing: 10) {
            FileIcon(item: item, size: compact ? 30 : 36)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name)
                    .lineLimit(1).truncationMode(.middle)
                    .foregroundStyle(item.state == .done && !item.fileExists ? .secondary : .primary)
                    .strikethrough(item.state == .done && !item.fileExists)
                switch item.state {
                case .running:
                    ProgressView(value: item.total > 0 ? item.progress : nil).controlSize(.small)
                    Text(runningStatus).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                default:
                    Text(status).font(.caption).foregroundStyle(statusColor).lineLimit(2)
                }
                if !compact, let source = item.source {
                    Text(source.siteHost).font(.caption2).foregroundStyle(.tertiary).lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            actions
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(hover ? Color.primary.opacity(0.05) : .clear, in: RoundedRectangle(cornerRadius: 8))
        .contentShape(Rectangle())
        .onHover { hover = $0 }
        .onTapGesture(count: 2) { if item.state == .done { model.downloads.open(item) } }
        // Готовый файл можно вытащить мышкой в Finder, письмо или другое приложение.
        .onDrag {
            guard item.state == .done, let file = item.file, item.fileExists else { return NSItemProvider() }
            return NSItemProvider(contentsOf: file) ?? NSItemProvider()
        }
        .contextMenu { menu }
    }

    @ViewBuilder
    private var actions: some View {
        HStack(spacing: 4) {
            switch item.state {
            case .running:
                Button { model.downloads.cancel(item) } label: { Image(systemName: "pause.circle.fill") }
                    .help("Остановить — потом можно продолжить с того же места")
                Button { model.downloads.remove(item) } label: { Image(systemName: "xmark.circle.fill") }
                    .help("Отменить и убрать из списка")
            case .done:
                if item.fileExists {
                    Button { model.downloads.reveal(item) } label: { Image(systemName: "magnifyingglass.circle.fill") }
                        .help("Показать в Finder")
                }
            case .failed, .cancelled:
                if item.canRetry {
                    Button { model.downloads.retry(item) } label: { Image(systemName: "arrow.clockwise.circle.fill") }
                        .help(item.resumeData != nil ? "Продолжить" : "Скачать заново")
                }
            }
        }
        .font(.title3)
        .foregroundStyle(.secondary)
        .buttonStyle(.borderless)
    }

    @ViewBuilder
    private var menu: some View {
        if item.state == .done, item.fileExists {
            Button("Открыть") { model.downloads.open(item) }
            Button("Показать в Finder") { model.downloads.reveal(item) }
            Divider()
        }
        if item.canRetry { Button(item.resumeData != nil ? "Продолжить" : "Скачать заново") { model.downloads.retry(item) } }
        if item.source != nil { Button("Скопировать ссылку") { model.downloads.copyLink(item) } }
        if let hash = item.sha256 {
            Button("Скопировать SHA-256") {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(hash, forType: .string)
            }
        }
        Divider()
        Button("Убрать из списка") { model.downloads.remove(item) }
        if item.state == .done, item.fileExists {
            Button("Переместить в Корзину", role: .destructive) { model.downloads.trash(item) }
        }
    }

    private var runningStatus: String {
        var parts = [item.total > 0 ? "\(Format.bytes(item.received)) из \(Format.bytes(item.total))" : Format.bytes(item.received)]
        if item.speed > 1 { parts.append("\(Format.bytes(Int64(item.speed)))/с") }
        if let left = item.remaining { parts.append("осталось " + Self.duration(left)) }
        return parts.joined(separator: " · ")
    }

    private var status: String {
        switch item.state {
        case .done:
            guard item.fileExists else { return "Файл удалён или перемещён" }
            var s = "Готово · \(Format.bytes(max(item.total, item.received)))"
            if !compact, let hash = item.sha256 { s += " · SHA-256 \(hash.prefix(12))…" }
            return s
        case .failed(let message): return message
        case .cancelled: return item.resumeData != nil ? "Остановлено на \(Format.bytes(item.received))" : "Отменено"
        case .running: return ""
        }
    }

    private var statusColor: Color {
        if case .failed = item.state { return .red }
        return .secondary
    }

    static func duration(_ t: TimeInterval) -> String {
        let s = Int(t.rounded())
        if s < 60 { return "\(max(1, s)) с" }
        if s < 3600 { return "\(s / 60) мин" }
        return "\(s / 3600) ч \(s % 3600 / 60) мин"
    }
}

/// Значок файла: настоящий из Finder, если файл на месте, иначе — по типу.
struct FileIcon: View {
    let item: Downloads.Item
    let size: CGFloat

    var body: some View {
        Image(nsImage: icon)
            .resizable()
            .frame(width: size, height: size)
            .opacity(item.state == .done || item.state == .running ? 1 : 0.5)
            .overlay(alignment: .bottomTrailing) {
                if case .failed = item.state {
                    Image(systemName: "exclamationmark.circle.fill").font(.caption).foregroundStyle(.white, .red)
                }
            }
    }

    private var icon: NSImage {
        if item.state == .done, let file = item.file, item.fileExists { return NSWorkspace.shared.icon(forFile: file.path) }
        let ext = (item.name as NSString).pathExtension
        return NSWorkspace.shared.icon(for: UTType(filenameExtension: ext) ?? .data)
    }
}

/// Окно «Загрузки» (⌥⌘L): весь список с поиском, отбором и группами по дням.
struct DownloadsWindow: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var filter = Filter.all

    enum Filter: String, CaseIterable, Identifiable {
        case all = "Все", running = "Идут", done = "Готово", problems = "Не скачались"
        var id: String { rawValue }
    }

    var body: some View {
        let groups = grouped
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Picker("", selection: $filter) {
                    ForEach(Filter.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .fixedSize()
                Spacer()
                Text(summary).font(.callout).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            Divider()
            if groups.isEmpty {
                ContentUnavailableView {
                    Label(model.downloads.items.isEmpty ? "Загрузок пока нет" : "Ничего не найдено", systemImage: "arrow.down.circle")
                } description: {
                    Text(model.downloads.items.isEmpty
                         ? "Всё, что вы скачаете, появится здесь. Файлы сохраняются в папку «Загрузки» и помечаются как скачанные из интернета — macOS проверит их перед первым открытием."
                         : "Попробуйте другой запрос или отбор.")
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(groups) { group in
                        Section(group.title) {
                            ForEach(group.items) { item in
                                DownloadRow(item: item)
                                    .listRowSeparator(.hidden)
                            }
                        }
                    }
                }
                .listStyle(.inset)
            }
        }
        .searchable(text: $query, placement: .toolbar, prompt: "Имя файла или сайт")
        .toolbar {
            ToolbarItemGroup {
                Button { model.downloads.openFolder() } label: { Label("Папка «Загрузки»", systemImage: "folder") }
                    .help("Открыть папку «Загрузки» в Finder")
                Button { model.downloads.clearFinished() } label: { Label("Очистить список", systemImage: "trash") }
                    .help("Убрать из списка всё, что уже не качается (файлы останутся на месте)")
                    .disabled(!model.downloads.items.contains { $0.state != .running })
            }
        }
        .navigationTitle("Загрузки")
        .frame(minWidth: 560, minHeight: 360)
    }

    private var filtered: [Downloads.Item] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return model.downloads.items.filter { item in
            switch filter {
            case .all: break
            case .running: guard item.state == .running else { return false }
            case .done: guard item.state == .done else { return false }
            case .problems: guard item.state != .done, item.state != .running else { return false }
            }
            guard !q.isEmpty else { return true }
            return item.name.lowercased().contains(q) || (item.source?.absoluteString.lowercased().contains(q) ?? false)
        }
    }

    struct DayGroup: Identifiable {
        let title: String
        var items: [Downloads.Item]
        var id: String { title }
    }

    private var grouped: [DayGroup] {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.setLocalizedDateFormatFromTemplate("d MMMM yyyy")
        var result: [DayGroup] = []
        for item in filtered {
            let title = calendar.isDateInToday(item.started) ? "Сегодня"
                : calendar.isDateInYesterday(item.started) ? "Вчера"
                : formatter.string(from: item.started)
            if let last = result.indices.last, result[last].title == title {
                result[last].items.append(item)
            } else {
                result.append(DayGroup(title: title, items: [item]))
            }
        }
        return result
    }

    private var summary: String {
        let items = model.downloads.items
        let done = items.filter { $0.state == .done }
        let size = done.reduce(Int64(0)) { $0 + max($1.total, $1.received) }
        let n = done.count
        guard n > 0 else { return "" }
        return "\(n) \(Format.plural(n, "файл", "файла", "файлов")) · \(Format.bytes(size))"
    }
}
