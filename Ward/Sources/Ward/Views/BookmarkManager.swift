import AppKit
import SwiftUI
import UniformTypeIdentifiers

/// Окно «Закладки» (⌥⌘B): папки слева, содержимое справа, поиск, перетаскивание, импорт и экспорт.
struct BookmarkManager: View {
    @Environment(AppModel.self) private var model
    @State private var place: BookmarkPlace? = .bar
    @State private var selection: Set<UUID> = []
    @State private var query = ""
    @State private var editing: BookmarkTarget?
    @State private var message: String?

    var body: some View {
        NavigationSplitView {
            List(selection: $place) {
                ForEach(model.bookmarks.folders.indices, id: \.self) { i in
                    let f = model.bookmarks.folders[i]
                    Label(f.title, systemImage: icon(f.place))
                        .padding(.leading, CGFloat(f.depth) * 14)
                        .tag(f.place)
                        .dropDestination(for: String.self) { values, _ in
                            var moved = false
                            for v in values {
                                if let id = UUID(uuidString: v), model.bookmarks.canMove(id, into: f.place) {
                                    model.moveBookmark(id, to: f.place)
                                    moved = true
                                }
                            }
                            return moved
                        }
                }
            }
            .navigationSplitViewColumnWidth(min: 200, ideal: 230)
        } detail: {
            detail
        }
        .searchable(text: $query, placement: .toolbar, prompt: "Поиск в закладках")
        .toolbar {
            ToolbarItemGroup {
                Button { newFolder() } label: { Label("Новая папка", systemImage: "folder.badge.plus") }
                    .help("Новая папка")
                Menu {
                    Button("Из файла HTML (Safari, Firefox, Chrome, Яндекс)…") { importFile(start: nil) }
                    Divider()
                    ForEach(BookmarkSources.browsers, id: \.name) { b in
                        Button("Из \(b.name)…") { importFile(start: b) }
                    }
                } label: {
                    Label("Импорт", systemImage: "square.and.arrow.down")
                }
                .help("Перенести закладки из другого браузера")
                Button { exportFile() } label: { Label("Экспорт", systemImage: "square.and.arrow.up") }
                    .help("Сохранить закладки в файл HTML — его понимает любой браузер")
            }
        }
        .navigationTitle("Закладки")
        .popover(item: $editing, arrowEdge: .top) { target in
            BookmarkEditor(id: target.id) { editing = nil }.environment(model)
        }
        .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK") { message = nil }
        }
    }

    // MARK: - Содержимое папки

    private var items: [Bookmark] {
        if !query.isEmpty { return model.bookmarks.search(query, limit: 500) }
        return model.bookmarks.items(in: place ?? .bar)
    }

    @ViewBuilder
    private var detail: some View {
        let list = items
        if list.isEmpty {
            ContentUnavailableView(query.isEmpty ? "Папка пуста" : "Ничего не найдено",
                                   systemImage: query.isEmpty ? "bookmark" : "magnifyingglass",
                                   description: Text(query.isEmpty ? "Перетащите сюда закладки или нажмите ☆ в адресной строке." : "Поиск идёт по названиям и адресам."))
        } else {
            List(selection: $selection) {
                ForEach(list) { b in
                    row(b)
                        .tag(b.id)
                        .draggable(b.id.uuidString)
                }
                .onMove { from, to in
                    guard query.isEmpty, let first = from.first else { return }
                    let current = model.bookmarks.items(in: place ?? .bar)
                    model.moveBookmark(current[first].id, to: place ?? .bar, at: to)
                }
            }
            .onDeleteCommand {
                for id in selection { model.removeBookmark(id) }
                selection = []
            }
            .contextMenu(forSelectionType: UUID.self) { ids in
                if ids.count == 1, let id = ids.first, let b = model.bookmarks.find(id) {
                    BookmarkContextMenu(bookmark: b, browser: model.activeBrowser) { editing = BookmarkTarget(id: id) }
                } else if ids.count > 1 {
                    Button("Открыть в новых вкладках (\(ids.count))") {
                        BookmarkOpener.openAll(ids.compactMap { model.bookmarks.find($0) }.flatMap(\.links), in: model.activeBrowser)
                    }
                    Button("Удалить (\(ids.count))", role: .destructive) {
                        for id in ids { model.removeBookmark(id) }
                        selection = []
                    }
                }
            } primaryAction: { ids in
                for id in ids { open(id) }
            }
        }
    }

    private func row(_ b: Bookmark) -> some View {
        HStack(spacing: 10) {
            if b.isFolder {
                Image(systemName: "folder.fill").foregroundStyle(.blue).frame(width: 22)
            } else {
                SiteTile(host: b.url?.host ?? "", size: 22)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(b.title).lineLimit(1)
                if let url = b.url {
                    Text(url.absoluteString).font(.caption).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
                } else {
                    let n = b.links.count
                    Text("\(n) \(Format.plural(n, "закладка", "закладки", "закладок"))").font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(b.added.formatted(date: .abbreviated, time: .omitted)).font(.caption).foregroundStyle(.tertiary)
        }
        .padding(.vertical, 3)
    }

    private func icon(_ place: BookmarkPlace) -> String {
        switch place {
        case .bar: "menubar.rectangle"
        case .other: "tray.full"
        case .folder: "folder"
        }
    }

    /// Двойной щелчок: папку — открыть в списке, закладку — во вкладке.
    private func open(_ id: UUID) {
        guard let b = model.bookmarks.find(id) else { return }
        if b.isFolder {
            query = ""
            place = .folder(id)
        } else {
            BookmarkOpener.openAll([b], in: model.activeBrowser)
        }
    }

    private func newFolder() {
        let f = model.addFolder("Новая папка", to: place ?? .bar)
        selection = [f.id]
        editing = BookmarkTarget(id: f.id)
    }

    // MARK: - Импорт и экспорт

    private func importFile(start: BookmarkSources.Browser?) {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if let start {
            panel.message = "Выберите файл «Bookmarks» в папке профиля \(start.name) (обычно «Default»)."
            panel.directoryURL = start.folder
            panel.showsHiddenFiles = true
        } else {
            panel.message = "Выберите файл закладок HTML (в браузере: «Экспорт закладок»)."
            panel.allowedContentTypes = [.html]
        }
        guard panel.runModal() == .OK, let url = panel.url else { return }
        guard let data = try? Data(contentsOf: url), let result = BookmarkImport.parse(data) else {
            message = "В этом файле не нашлось закладок"
            return
        }
        let added = model.importBookmarks(result, source: start?.name ?? url.deletingPathExtension().lastPathComponent)
        message = added == 0 ? "Все закладки из файла уже есть в Ward" : "Перенесено \(added) \(Format.plural(added, "закладка", "закладки", "закладок"))"
    }

    private func exportFile() {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "Закладки Ward.html"
        panel.allowedContentTypes = [.html]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try BookmarkImport.export(model.bookmarks).write(to: url, atomically: true, encoding: .utf8)
            message = "Закладки сохранены: \(url.lastPathComponent)"
        } catch {
            message = "Не удалось сохранить: \(error.localizedDescription)"
        }
    }
}

/// Где браузеры на Chromium хранят закладки. Ward в песочнице: файл открывается только через окно выбора,
/// поэтому окно сразу показывает нужную папку.
enum BookmarkSources {
    struct Browser {
        var name: String
        var path: String
        var folder: URL { BookmarkSources.home.appendingPathComponent(path, isDirectory: true) }
    }

    static let browsers: [Browser] = [
        Browser(name: "Google Chrome", path: "Library/Application Support/Google/Chrome/Default"),
        Browser(name: "Яндекс Браузера", path: "Library/Application Support/Yandex/YandexBrowser/Default"),
        Browser(name: "Brave", path: "Library/Application Support/BraveSoftware/Brave-Browser/Default"),
        Browser(name: "Microsoft Edge", path: "Library/Application Support/Microsoft Edge/Default"),
        Browser(name: "Arc", path: "Library/Application Support/Arc/User Data/Default"),
        Browser(name: "Vivaldi", path: "Library/Application Support/Vivaldi/Default"),
    ]

    /// Настоящая домашняя папка (в песочнице `homeDirectoryForCurrentUser` — папка контейнера).
    static var home: URL {
        if let pw = getpwuid(getuid()), let dir = pw.pointee.pw_dir { return URL(fileURLWithPath: String(cString: dir), isDirectory: true) }
        return FileManager.default.homeDirectoryForCurrentUser
    }
}
