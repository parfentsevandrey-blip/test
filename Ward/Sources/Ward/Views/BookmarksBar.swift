import AppKit
import SwiftUI

/// Панель закладок под адресной строкой — как в Chrome: закладки и папки в один ряд, что не влезло — в «»».
/// Закладки перетаскиваются (в том числе в папки), ссылку со страницы можно бросить прямо на панель.
struct BookmarksBar: View {
    @Environment(AppModel.self) private var model
    let browser: BrowserState
    @State private var widths: [UUID: CGFloat] = [:]
    @State private var available: CGFloat = 0
    @State private var dropBefore: UUID?
    @State private var newFolder: BookmarkTarget?

    var body: some View {
        let items = model.bookmarks.bar
        let visible = fitting(items)
        HStack(spacing: 2) {
            ForEach(items.prefix(visible)) { b in
                BookmarkBarItem(bookmark: b, browser: browser)
                    .overlay(alignment: .leading) {
                        if dropBefore == b.id {
                            Capsule().fill(Color.accentColor).frame(width: 2, height: 18).offset(x: -2)
                        }
                    }
                    .dropDestination(for: String.self) { values, _ in
                        drop(values, before: b)
                    } isTargeted: { on in
                        if on { dropBefore = b.id } else if dropBefore == b.id { dropBefore = nil }
                    }
            }
            if items.isEmpty {
                Text("Нажмите ☆ в адресной строке или перетащите сюда ссылку")
                    .font(.caption).foregroundStyle(.tertiary).padding(.leading, 6)
            }
            Spacer(minLength: 0)
            if visible < items.count {
                Menu {
                    BookmarkMenuContent(items: Array(items.dropFirst(visible)), browser: browser)
                } label: {
                    Image(systemName: "chevron.right.2")
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .help("Остальные закладки панели")
            }
            if !model.bookmarks.other.isEmpty {
                Divider().frame(height: 16)
                Menu {
                    BookmarkMenuContent(items: model.bookmarks.other, browser: browser)
                } label: {
                    Label("Другие закладки", systemImage: "folder")
                        .labelStyle(.titleAndIcon)
                        .font(.system(size: 12))
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
            }
        }
        .padding(.horizontal, 8)
        .frame(height: 30)
        .frame(maxWidth: .infinity)
        .background(alignment: .leading) {
            // Невидимая копия всех закладок — чтобы знать ширину каждой и сколько влезает.
            HStack(spacing: 2) {
                ForEach(items) { b in
                    BookmarkBarLabel(bookmark: b)
                        .fixedSize()
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { widths[b.id] = $0 }
                }
            }
            .hidden()
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { available = $0 }
        .contentShape(Rectangle())
        .dropDestination(for: String.self) { values, _ in
            drop(values, before: nil)
        }
        .contextMenu {
            if let tab = browser.selected, let url = tab.url {
                Button("Добавить эту страницу") { model.addBookmark(url, title: tab.title) }
            }
            Button("Новая папка…") { newFolder = BookmarkTarget(id: model.addFolder("Новая папка").id) }
            Divider()
            Button("Управление закладками") { model.openWindow?("bookmarks") }
            Button("Скрыть панель закладок") { model.prefs.showBookmarksBar = false }
        }
        .popover(item: $newFolder, arrowEdge: .bottom) { target in
            BookmarkEditor(id: target.id) { newFolder = nil }.environment(model)
        }
    }

    /// Сколько закладок помещается: остальное уходит в «»».
    private func fitting(_ items: [Bookmark]) -> Int {
        guard available > 0 else { return items.count }
        let reserved: CGFloat = 16 + 34 + (model.bookmarks.other.isEmpty ? 0 : 150)
        var used: CGFloat = 0
        for (i, b) in items.enumerated() {
            used += (widths[b.id] ?? 120) + 2
            if used > available - reserved {
                // Последняя закладка влезает и без кнопки «»» — тогда показываем всё.
                if i == items.count - 1 && used <= available - reserved + 34 { return items.count }
                return i
            }
        }
        return items.count
    }

    /// Бросили на панель: свою закладку — переставить, ссылку со страницы — добавить.
    private func drop(_ values: [String], before target: Bookmark?) -> Bool {
        dropBefore = nil
        let index = target.flatMap { t in model.bookmarks.bar.firstIndex { $0.id == t.id } } ?? model.bookmarks.bar.count
        var handled = false
        for value in values {
            if let id = UUID(uuidString: value), model.bookmarks.find(id) != nil {
                if target?.isFolder == true, let folder = target, folder.id != id {
                    model.moveBookmark(id, to: .folder(folder.id))
                } else {
                    model.moveBookmark(id, to: .bar, at: index)
                }
                handled = true
            } else if let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)), BookmarkImport.isWeb(url) {
                model.addBookmark(url, title: url.siteHost, to: .bar, at: index)
                handled = true
            }
        }
        return handled
    }
}

/// Значок и название закладки на панели.
struct BookmarkBarLabel: View {
    let bookmark: Bookmark

    var body: some View {
        HStack(spacing: 5) {
            if bookmark.isFolder {
                Image(systemName: "folder.fill").foregroundStyle(.secondary).font(.system(size: 12))
            } else {
                SiteTile(host: bookmark.url?.host ?? "", size: 14)
            }
            Text(bookmark.title.isEmpty ? (bookmark.url?.siteHost ?? "") : bookmark.title)
                .font(.system(size: 12))
                .lineLimit(1)
                .frame(maxWidth: 150, alignment: .leading)
                .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 4)
    }
}

/// Одна закладка или папка на панели.
struct BookmarkBarItem: View {
    @Environment(AppModel.self) private var model
    let bookmark: Bookmark
    let browser: BrowserState
    @State private var hover = false
    @State private var editing: BookmarkTarget?

    var body: some View {
        Group {
            if bookmark.isFolder {
                Menu {
                    BookmarkMenuContent(items: bookmark.children ?? [], browser: browser)
                    if (bookmark.children ?? []).isEmpty { Text("Папка пуста") }
                } label: {
                    BookmarkBarLabel(bookmark: bookmark)
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
            } else {
                Button { BookmarkOpener.open(bookmark, in: browser, newTab: NSEvent.modifierFlags.contains(.command)) } label: {
                    BookmarkBarLabel(bookmark: bookmark)
                }
                .buttonStyle(.plain)
                .help("\(bookmark.title)\n\(bookmark.url?.absoluteString ?? "")")
            }
        }
        .background(hover ? Color.primary.opacity(0.08) : .clear, in: Capsule())
        .onHover { hover = $0 }
        .draggable(bookmark.id.uuidString) {
            BookmarkBarLabel(bookmark: bookmark).background(.regularMaterial, in: Capsule())
        }
        .contextMenu { BookmarkContextMenu(bookmark: bookmark, browser: browser) { editing = BookmarkTarget(id: bookmark.id) } }
        .popover(item: $editing, arrowEdge: .bottom) { target in
            BookmarkEditor(id: target.id) { editing = nil }.environment(model)
        }
    }
}

/// Пункты меню для списка закладок; папки — вложенными меню.
struct BookmarkMenuContent: View {
    let items: [Bookmark]
    let browser: BrowserState?

    var body: some View {
        ForEach(items) { b in
            if b.isFolder {
                Menu {
                    // Вложенная папка — тот же список, только глубже.
                    AnyView(BookmarkMenuContent(items: b.children ?? [], browser: browser))
                    let links = b.links
                    if links.count > 1 {
                        Divider()
                        Button("Открыть все (\(links.count))") { BookmarkOpener.openAll(links, in: browser) }
                    }
                } label: {
                    Label(b.title, systemImage: "folder")
                }
            } else {
                Button {
                    if let browser { BookmarkOpener.open(b, in: browser, newTab: NSEvent.modifierFlags.contains(.command)) }
                    else { BookmarkOpener.openAll([b], in: nil) }
                } label: {
                    Label(b.title.isEmpty ? (b.url?.siteHost ?? "") : b.title, systemImage: "globe")
                }
            }
        }
    }
}

/// Контекстное меню закладки: как в Chrome.
struct BookmarkContextMenu: View {
    @Environment(AppModel.self) private var model
    let bookmark: Bookmark
    let browser: BrowserState?
    let edit: () -> Void

    var body: some View {
        if bookmark.isFolder {
            let links = bookmark.links
            Button("Открыть все (\(links.count))") { BookmarkOpener.openAll(links, in: browser) }.disabled(links.isEmpty)
            Button("Переименовать…", action: edit)
            Divider()
            Button("Удалить папку", role: .destructive) { model.removeBookmark(bookmark.id) }
        } else {
            Button("Открыть") { if let browser { BookmarkOpener.open(bookmark, in: browser, newTab: false) } }
            Button("Открыть в новой вкладке") { BookmarkOpener.openAll([bookmark], in: browser) }
            if browser?.isPrivate == false {
                Button("Открыть в одноразовой вкладке") {
                    if let url = bookmark.url { _ = browser?.newTab(url: url, ephemeral: true) }
                }
            }
            Divider()
            Button("Изменить…", action: edit)
            Button("Скопировать адрес") {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(bookmark.url?.absoluteString ?? "", forType: .string)
            }
            Divider()
            Button("Удалить", role: .destructive) { model.removeBookmark(bookmark.id) }
        }
    }
}

enum BookmarkOpener {
    /// Обычный щелчок — в текущей вкладке, ⌘-щелчок — в новой.
    static func open(_ bookmark: Bookmark, in browser: BrowserState, newTab: Bool) {
        guard let url = bookmark.url else { return }
        if newTab || browser.selected == nil {
            _ = browser.newTab(url: url, select: !newTab)
        } else {
            browser.selected?.load(url)
        }
    }

    static func openAll(_ links: [Bookmark], in browser: BrowserState?) {
        let urls = links.compactMap(\.url)
        if let browser {
            for (i, url) in urls.enumerated() { _ = browser.newTab(url: url, select: i == 0) }
            browser.window?.makeKeyAndOrderFront(nil)
        } else {
            for url in urls { AppModel.shared.open(url) }
        }
    }
}

/// Редактор закладки или папки: название, адрес, папка. Открывается по звезде, ⌘D и «Изменить…».
struct BookmarkEditor: View {
    @Environment(AppModel.self) private var model
    let id: UUID
    var done: () -> Void
    @State private var title = ""
    @State private var address = ""
    @State private var place: BookmarkPlace = .bar
    @State private var loaded = false
    @FocusState private var titleFocused: Bool

    var body: some View {
        let bookmark = model.bookmarks.find(id)
        let isFolder = bookmark?.isFolder ?? false
        let folders = model.bookmarks.folders.filter { model.bookmarks.canMove(id, into: $0.place) }
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: isFolder ? "folder.fill" : "bookmark.fill")
                    .font(.title2)
                    .foregroundStyle(isFolder ? Color.blue.gradient : Color.yellow.gradient)
                Text(isFolder ? "Папка" : "Закладка").font(.headline)
            }
            TextField("Название", text: $title)
                .textFieldStyle(.roundedBorder)
                .focused($titleFocused)
            if !isFolder {
                TextField("Адрес", text: $address)
                    .textFieldStyle(.roundedBorder)
            }
            Picker("Где", selection: $place) {
                ForEach(folders.indices, id: \.self) { i in
                    let f = folders[i]
                    Text(String(repeating: "   ", count: f.depth) + f.title).tag(f.place)
                }
            }
            HStack {
                Button("Удалить", role: .destructive) {
                    model.removeBookmark(id)
                    done()
                }
                Spacer()
                Button("Готово") {
                    save()
                    done()
                }
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(16)
        .frame(width: 340)
        .onAppear {
            guard !loaded, let bookmark else { return }
            loaded = true
            title = bookmark.title
            address = bookmark.url?.absoluteString ?? ""
            place = model.bookmarks.location(of: id)?.place ?? .bar
            titleFocused = true
        }
    }

    private func save() {
        guard let bookmark = model.bookmarks.find(id) else { return }
        var url = bookmark.url
        if !bookmark.isFolder {
            let text = address.trimmingCharacters(in: .whitespaces)
            if let typed = URL(string: text), BookmarkImport.isWeb(typed) { url = typed }
            else if let typed = URL(string: "https://" + text), typed.host?.contains(".") == true { url = typed }
        }
        model.updateBookmark(id, title: title.trimmingCharacters(in: .whitespaces).isEmpty ? (url?.siteHost ?? "Папка") : title, url: url)
        if model.bookmarks.location(of: id)?.place != place { model.moveBookmark(id, to: place) }
    }
}
