import Foundation

/// Закладка или папка. У папки нет адреса, зато есть вложенные закладки.
nonisolated struct Bookmark: Codable, Sendable, Identifiable, Hashable {
    var id = UUID()
    var title: String
    var url: URL?
    var children: [Bookmark]?
    var added = Date()

    static func link(_ url: URL, title: String) -> Bookmark {
        Bookmark(title: title.isEmpty ? (url.host ?? url.absoluteString) : title, url: url)
    }

    static func folder(_ title: String, _ children: [Bookmark] = []) -> Bookmark {
        Bookmark(title: title, url: nil, children: children)
    }

    var isFolder: Bool { url == nil }

    /// Все ссылки внутри папки, на любой глубине.
    var links: [Bookmark] {
        if !isFolder { return [self] }
        return (children ?? []).flatMap(\.links)
    }
}

/// Куда положить закладку: в корень панели, в «Другие закладки» или в папку.
nonisolated enum BookmarkPlace: Hashable, Sendable {
    case bar, other
    case folder(UUID)
}

/// Все закладки: панель закладок под адресной строкой и «Другие закладки» — как в Chrome.
nonisolated struct BookmarkTree: Codable, Sendable, Equatable {
    var bar: [Bookmark] = []
    var other: [Bookmark] = []

    init(bar: [Bookmark] = [], other: [Bookmark] = []) {
        self.bar = bar
        self.other = other
    }

    static var defaults: BookmarkTree {
        BookmarkTree(bar: [
            .link(URL(string: "https://duckduckgo.com")!, title: "DuckDuckGo"),
            .link(URL(string: "https://ru.wikipedia.org")!, title: "Википедия"),
            .link(URL(string: "https://github.com")!, title: "GitHub"),
            .link(URL(string: "https://www.youtube.com")!, title: "YouTube"),
        ])
    }

    /// Избранное прошлых версий становится панелью закладок.
    init(favorites: [Favorite]) {
        bar = favorites.map { Bookmark(id: $0.id, title: $0.title, url: $0.url) }
    }

    // MARK: - Поиск

    var allLinks: [Bookmark] { (bar + other).flatMap(\.links) }

    func find(_ id: UUID) -> Bookmark? {
        func walk(_ list: [Bookmark]) -> Bookmark? {
            for b in list {
                if b.id == id { return b }
                if let c = b.children, let found = walk(c) { return found }
            }
            return nil
        }
        return walk(bar) ?? walk(other)
    }

    func bookmark(for url: URL) -> Bookmark? {
        let key = Self.key(url)
        return allLinks.first { $0.url.map(Self.key) == key }
    }

    func contains(_ url: URL) -> Bool { bookmark(for: url) != nil }

    /// Адреса сравниваются без завершающей «/»: example.com и example.com/ — одна закладка.
    static func key(_ url: URL) -> String {
        var s = url.absoluteString
        if s.hasSuffix("/") { s.removeLast() }
        return s
    }

    /// Где лежит закладка: место и позиция в нём.
    func location(of id: UUID) -> (place: BookmarkPlace, index: Int)? {
        func walk(_ list: [Bookmark], _ place: BookmarkPlace) -> (BookmarkPlace, Int)? {
            for (i, b) in list.enumerated() {
                if b.id == id { return (place, i) }
                if let c = b.children, let found = walk(c, .folder(b.id)) { return found }
            }
            return nil
        }
        return walk(bar, .bar) ?? walk(other, .other)
    }

    func items(in place: BookmarkPlace) -> [Bookmark] {
        switch place {
        case .bar: return bar
        case .other: return other
        case .folder(let id): return find(id)?.children ?? []
        }
    }

    func title(of place: BookmarkPlace) -> String {
        switch place {
        case .bar: return "Панель закладок"
        case .other: return "Другие закладки"
        case .folder(let id): return find(id)?.title ?? "Папка"
        }
    }

    /// Все папки с глубиной вложенности — для выбора «куда сохранить».
    var folders: [(place: BookmarkPlace, title: String, depth: Int)] {
        var out: [(place: BookmarkPlace, title: String, depth: Int)] = []
        func walk(_ list: [Bookmark], _ depth: Int) {
            for b in list where b.isFolder {
                out.append((.folder(b.id), b.title, depth))
                walk(b.children ?? [], depth + 1)
            }
        }
        out.append((.bar, title(of: .bar), 0))
        walk(bar, 1)
        out.append((.other, title(of: .other), 0))
        walk(other, 1)
        return out
    }

    func search(_ text: String, limit: Int = 50) -> [Bookmark] {
        let q = text.lowercased().trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return [] }
        return Array(allLinks.filter { $0.title.lowercased().contains(q) || ($0.url?.absoluteString.lowercased().contains(q) ?? false) }.prefix(limit))
    }

    // MARK: - Изменения

    /// Изменить список закладок в месте `place`. false — такого места нет.
    @discardableResult
    mutating func edit(_ place: BookmarkPlace, _ body: (inout [Bookmark]) -> Void) -> Bool {
        switch place {
        case .bar: body(&bar); return true
        case .other: body(&other); return true
        case .folder(let id):
            func walk(_ list: inout [Bookmark]) -> Bool {
                for i in list.indices {
                    if list[i].id == id {
                        var children = list[i].children ?? []
                        body(&children)
                        list[i].children = children
                        return true
                    }
                    if list[i].children != nil, walk(&list[i].children!) { return true }
                }
                return false
            }
            return walk(&bar) || walk(&other)
        }
    }

    mutating func insert(_ bookmark: Bookmark, into place: BookmarkPlace, at index: Int? = nil) {
        edit(place) { list in list.insert(bookmark, at: min(max(0, index ?? list.count), list.count)) }
    }

    @discardableResult
    mutating func remove(_ id: UUID) -> Bookmark? {
        guard let (place, index) = location(of: id) else { return nil }
        var removed: Bookmark?
        edit(place) { list in removed = list.remove(at: index) }
        return removed
    }

    mutating func update(_ id: UUID, _ change: (inout Bookmark) -> Void) {
        guard let (place, index) = location(of: id) else { return }
        edit(place) { list in change(&list[index]) }
    }

    /// Папку нельзя положить в саму себя или в свою же вложенную папку.
    func canMove(_ id: UUID, into place: BookmarkPlace) -> Bool {
        guard case .folder(let target) = place else { return true }
        if target == id { return false }
        guard let moving = find(id), moving.isFolder else { return true }
        func contains(_ list: [Bookmark]) -> Bool { list.contains { $0.id == target || contains($0.children ?? []) } }
        return !contains(moving.children ?? [])
    }

    /// Переместить закладку: `index` — позиция в новом месте до перемещения (как у drag and drop).
    mutating func move(_ id: UUID, to place: BookmarkPlace, at index: Int) {
        guard canMove(id, into: place), let (from, oldIndex) = location(of: id) else { return }
        var target = index
        if from == place && oldIndex < index { target -= 1 }
        guard let item = remove(id) else { return }
        insert(item, into: place, at: target)
    }

    /// Добавить импортированные закладки, пропуская адреса, которые уже есть.
    mutating func merge(_ imported: BookmarkImport.Result, source: String) -> Int {
        var known = Set(allLinks.compactMap { $0.url.map(Self.key) })
        func fresh(_ list: [Bookmark]) -> [Bookmark] {
            list.compactMap { b in
                if let url = b.url {
                    return known.insert(Self.key(url)).inserted ? Bookmark(title: b.title, url: url) : nil
                }
                let kids = fresh(b.children ?? [])
                return kids.isEmpty ? nil : Bookmark.folder(b.title, kids)
            }
        }
        let barItems = fresh(imported.bar)
        let otherItems = fresh(imported.other)
        bar.append(contentsOf: barItems)
        if !otherItems.isEmpty { other.append(.folder("Импорт: \(source)", otherItems)) }
        return (barItems + otherItems).reduce(0) { $0 + $1.links.count }
    }
}

/// Импорт закладок из других браузеров и экспорт в общий формат.
///
/// - HTML (формат Netscape) — «Экспорт закладок» есть в Safari, Chrome, Firefox, Яндекс.Браузере и Edge.
/// - JSON Chrome, Brave, Edge, Яндекс.Браузера, Arc и других на Chromium — файл `Bookmarks` в папке профиля.
nonisolated enum BookmarkImport {
    nonisolated struct Result: Sendable {
        var bar: [Bookmark] = []
        var other: [Bookmark] = []
        var count: Int { (bar + other).reduce(0) { $0 + $1.links.count } }
    }

    static func parse(_ data: Data) -> Result? {
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let roots = json["roots"] as? [String: Any] {
            return chromium(roots)
        }
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .windowsCP1251) else { return nil }
        let result = html(text)
        return result.count > 0 ? result : nil
    }

    // MARK: Chromium

    static func chromium(_ roots: [String: Any]) -> Result {
        func node(_ raw: Any) -> Bookmark? {
            guard let n = raw as? [String: Any] else { return nil }
            let name = n["name"] as? String ?? ""
            if n["type"] as? String == "url" {
                guard let s = n["url"] as? String, let url = URL(string: s), isWeb(url) else { return nil }
                return .link(url, title: name)
            }
            let children = (n["children"] as? [Any] ?? []).compactMap(node)
            return .folder(name.isEmpty ? "Папка" : name, children)
        }
        func children(_ key: String) -> [Bookmark] {
            ((roots[key] as? [String: Any])?["children"] as? [Any] ?? []).compactMap(node)
        }
        return Result(bar: children("bookmark_bar"), other: children("other") + children("synced"))
    }

    // MARK: HTML (Netscape)

    /// Разбор по тегам: `<H3>` — заголовок папки, следующий `<DL>` — её содержимое, `</DL>` — конец,
    /// `<A HREF>` — закладка. Папка с PERSONAL_TOOLBAR_FOLDER (Chrome, Firefox) или id="favorites_bar"
    /// (Safari) — это панель закладок.
    static func html(_ text: String) -> Result {
        guard let regex = try? NSRegularExpression(pattern: #"<h3([^>]*)>(.*?)</h3>|<a\s([^>]*)>(.*?)</a>|<dl[\s>]|</dl>"#,
                                                   options: [.caseInsensitive, .dotMatchesLineSeparators]) else { return Result() }
        let ns = text as NSString
        var stack: [Frame] = [Frame(title: "", toolbar: false)]
        var pending: Frame?
        var opened = false
        var bar: [Bookmark] = []
        func group(_ m: NSTextCheckingResult, _ i: Int) -> String {
            let r = m.range(at: i)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
        for m in regex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            let token = ns.substring(with: m.range).lowercased()
            if token.hasPrefix("<h3") {
                let attrs = group(m, 1).lowercased()
                pending = Frame(title: entities(stripTags(group(m, 2))), toolbar: attrs.contains("personal_toolbar_folder=\"true\"") || attrs.contains("favorites_bar"))
            } else if token.hasPrefix("<a") {
                guard let href = attribute("href", in: group(m, 3)), let url = URL(string: entities(href)), isWeb(url) else { continue }
                stack[stack.count - 1].items.append(.link(url, title: entities(stripTags(group(m, 4))).trimmingCharacters(in: .whitespacesAndNewlines)))
            } else if token.hasPrefix("<dl") {
                // Первый <DL> — корень файла; остальные открывают папку, заголовок которой только что прошёл.
                if !opened { opened = true; continue }
                stack.append(pending ?? Frame(title: "Папка", toolbar: false))
                pending = nil
            } else if token.hasPrefix("</dl") {
                guard stack.count > 1 else { continue }
                let frame = stack.removeLast()
                if frame.toolbar && stack.count == 1 {
                    bar.append(contentsOf: frame.items)
                } else {
                    stack[stack.count - 1].items.append(.folder(frame.title.isEmpty ? "Папка" : frame.title, frame.items))
                }
            }
        }
        while stack.count > 1 {
            let frame = stack.removeLast()
            stack[stack.count - 1].items.append(.folder(frame.title, frame.items))
        }
        return Result(bar: bar, other: stack[0].items)
    }

    nonisolated struct Frame {
        var title: String
        var toolbar: Bool
        var items: [Bookmark] = []
    }

    static func export(_ tree: BookmarkTree) -> String {
        func esc(_ s: String) -> String {
            s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
                .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
        }
        var out = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!-- Закладки Ward. Этот файл можно импортировать в любой браузер. -->
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>

        """
        func write(_ list: [Bookmark], _ indent: String) {
            for b in list {
                let date = Int(b.added.timeIntervalSince1970)
                if let url = b.url {
                    out += "\(indent)<DT><A HREF=\"\(esc(url.absoluteString))\" ADD_DATE=\"\(date)\">\(esc(b.title))</A>\n"
                } else {
                    out += "\(indent)<DT><H3 ADD_DATE=\"\(date)\">\(esc(b.title))</H3>\n\(indent)<DL><p>\n"
                    write(b.children ?? [], indent + "    ")
                    out += "\(indent)</DL><p>\n"
                }
            }
        }
        out += "    <DT><H3 PERSONAL_TOOLBAR_FOLDER=\"true\">Панель закладок</H3>\n    <DL><p>\n"
        write(tree.bar, "        ")
        out += "    </DL><p>\n"
        write(tree.other, "    ")
        out += "</DL><p>\n"
        return out
    }

    // MARK: Мелочи

    static func isWeb(_ url: URL) -> Bool {
        let s = url.scheme?.lowercased() ?? ""
        return s == "http" || s == "https"
    }

    static func attribute(_ name: String, in attrs: String) -> String? {
        guard let r = try? NSRegularExpression(pattern: name + #"\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))"#, options: .caseInsensitive) else { return nil }
        let ns = attrs as NSString
        guard let m = r.firstMatch(in: attrs, range: NSRange(location: 0, length: ns.length)) else { return nil }
        for i in 2...4 where m.range(at: i).location != NSNotFound { return ns.substring(with: m.range(at: i)) }
        return nil
    }

    static func stripTags(_ s: String) -> String {
        s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
    }

    static func entities(_ s: String) -> String {
        guard s.contains("&") else { return s }
        var out = s
        for (k, v) in [("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'"), ("&apos;", "'"), ("&nbsp;", " ")] {
            out = out.replacingOccurrences(of: k, with: v)
        }
        if let r = try? NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);") {
            let ns = out as NSString
            var result = ""
            var last = 0
            for m in r.matches(in: out, range: NSRange(location: 0, length: ns.length)) {
                result += ns.substring(with: NSRange(location: last, length: m.range.location - last))
                let hex = ns.substring(with: m.range(at: 1)) == "x"
                if let code = UInt32(ns.substring(with: m.range(at: 2)), radix: hex ? 16 : 10), let scalar = Unicode.Scalar(code) {
                    result.unicodeScalars.append(scalar)
                }
                last = m.range.location + m.range.length
            }
            result += ns.substring(from: last)
            out = result
        }
        return out.replacingOccurrences(of: "&amp;", with: "&")
    }
}
