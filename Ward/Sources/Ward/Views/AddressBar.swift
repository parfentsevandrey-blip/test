import SwiftUI

/// Состояние адресной строки окна: что введено и что подсказать.
@Observable
final class AddressState {
    var editing = false
    var text = ""
    var suggestions: [Suggestion] = []
    var highlighted = 0
    var focusPage = 0

    struct Suggestion: Identifiable, Hashable {
        enum Kind { case search, address, history, favorite }
        var id: String { kind == .search ? "search:" + input : input }
        var kind: Kind
        var title: String
        var subtitle: String
        var input: String
    }

    func update(_ model: AppModel) {
        let q = text.trimmingCharacters(in: .whitespaces)
        guard editing, !q.isEmpty else { suggestions = []; return }
        var list: [Suggestion] = []
        if let url = Omnibox.address(q) {
            list.append(Suggestion(kind: .address, title: url.absoluteString, subtitle: "Открыть адрес", input: url.absoluteString))
        } else {
            list.append(Suggestion(kind: .search, title: q, subtitle: "Искать в \(model.prefs.search.title)", input: q))
        }
        let lower = q.lowercased()
        for b in model.bookmarks.search(q, limit: 4) {
            guard let url = b.url, !list.contains(where: { $0.input == url.absoluteString }) else { continue }
            list.append(Suggestion(kind: .favorite, title: b.title, subtitle: url.siteHost, input: url.absoluteString))
        }
        for e in model.history.suggestions(q) where !list.contains(where: { $0.input == e.url.absoluteString }) {
            list.append(Suggestion(kind: .history, title: e.title.isEmpty ? e.url.siteHost : e.title, subtitle: e.url.absoluteString, input: e.url.absoluteString))
        }
        suggestions = Array(list.prefix(8))
        highlighted = min(highlighted, max(0, suggestions.count - 1))
    }
}

struct AddressBar: View {
    @Environment(AppModel.self) private var model
    let tab: Tab
    let browser: BrowserState
    @Bindable var address: AddressState
    let go: (String) -> Void
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 7) {
            securityIcon
            TextField("Поиск или адрес сайта", text: $address.text)
                .textFieldStyle(.plain)
                .focused($focused)
                .onSubmit(submit)
                .onKeyPress(.downArrow) { move(1) }
                .onKeyPress(.upArrow) { move(-1) }
                .onKeyPress(.escape) {
                    focused = false
                    return .handled
                }
            if let url = tab.url, !focused {
                BookmarkStar(url: url, title: tab.title)
            }
            Button { tab.isLoading ? tab.stop() : tab.reload() } label: {
                Image(systemName: tab.isLoading ? "xmark" : "arrow.clockwise")
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .help(tab.isLoading ? "Остановить" : "Обновить (⌘R)")
            .disabled(tab.isStartPage)
        }
        .padding(.horizontal, 12)
        .frame(height: 30)
        .background(.fill.tertiary, in: Capsule())
        .overlay(Capsule().strokeBorder(focused ? Color.accentColor.opacity(0.6) : .clear, lineWidth: 1.5))
        .onChange(of: focused) {
            if focused {
                address.editing = true
            } else {
                // Подсказки исчезают с задержкой: щелчок по подсказке успевает сработать.
                Task {
                    try? await Task.sleep(for: .milliseconds(250))
                    if !focused { address.editing = false }
                }
            }
            address.text = focused ? (tab.url?.absoluteString ?? "") : display
            address.highlighted = 0
            address.update(model)
        }
        .onChange(of: address.text) { if focused { address.highlighted = 0; address.update(model) } }
        .onChange(of: tab.url) { if !focused { address.text = display } }
        .onChange(of: tab.focusRequest) { focused = true }
        .onChange(of: address.focusPage) { focused = false }
        .onAppear {
            address.text = display
            if tab.isStartPage { focused = true }
        }
    }

    /// Без фокуса — только сайт (или запрос, если это страница поиска): так легче заметить подделку адреса.
    private var display: String {
        guard let url = tab.url else { return "" }
        if let q = model.prefs.search.query(in: url) { return q }
        // Международные домены — буквами, если это обычное имя; punycode, если буквы алфавитов смешаны.
        if url.scheme == "http" || url.scheme == "https" { return Punycode.display(url.siteHost).text }
        return url.absoluteString
    }

    private func submit() {
        let list = address.suggestions
        if address.highlighted > 0, address.highlighted < list.count {
            go(list[address.highlighted].input)
        } else {
            go(address.text)
        }
        focused = false
    }

    private func move(_ delta: Int) -> KeyPress.Result {
        guard !address.suggestions.isEmpty else { return .ignored }
        address.highlighted = (address.highlighted + delta + address.suggestions.count) % address.suggestions.count
        return .handled
    }

    @ViewBuilder
    private var securityIcon: some View {
        if focused || tab.url == nil {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
        } else if tab.interstitial != nil {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
        } else if Punycode.display(tab.host).suspicious {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red)
                .help("В адресе смешаны буквы разных алфавитов — так подделывают имена известных сайтов")
        } else if tab.strictHere {
            Image(systemName: "lock.shield.fill").foregroundStyle(.blue)
                .help("Строгий режим: соединение зашифровано, у страницы урезаны опасные возможности")
        } else if tab.url?.scheme == "https" && tab.hasOnlySecureContent {
            Image(systemName: "lock.fill").foregroundStyle(.secondary)
                .help("Соединение зашифровано")
        } else if tab.url?.scheme == "http" {
            Image(systemName: "lock.open.fill").foregroundStyle(.orange)
                .help("Соединение не зашифровано: всё, что вы вводите, видно по пути")
        } else {
            Image(systemName: "globe").foregroundStyle(.secondary)
        }
    }
}

/// Звезда в адресной строке: добавить страницу в закладки и сразу поправить название и папку.
struct BookmarkStar: View {
    @Environment(AppModel.self) private var model
    let url: URL
    let title: String
    @State private var editing: BookmarkTarget?

    var body: some View {
        let saved = model.bookmark(for: url)
        Button {
            model.bookmarkPage(url, title: title)
        } label: {
            Image(systemName: saved != nil ? "star.fill" : "star")
                .foregroundStyle(saved != nil ? Color.yellow : Color.secondary)
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.borderless)
        .help(saved != nil ? "Изменить закладку (⌘D)" : "Добавить в закладки (⌘D)")
        .onChange(of: model.bookmarkEditRequest?.count) {
            // Редактор открывается у той звезды, чья страница только что попала в закладки.
            if let id = model.bookmarkEditRequest?.id, model.bookmark(for: url)?.id == id { editing = BookmarkTarget(id: id) }
        }
        .popover(item: $editing, arrowEdge: .bottom) { target in
            BookmarkEditor(id: target.id) { editing = nil }
                .environment(model)
        }
    }
}

/// Какую закладку редактировать (для `popover(item:)`).
struct BookmarkTarget: Identifiable {
    let id: UUID
}

/// Подсказки под адресной строкой.
struct SuggestionList: View {
    @Bindable var address: AddressState
    let pick: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(Array(address.suggestions.enumerated()), id: \.element.id) { i, s in
                Button { pick(s.input) } label: {
                    HStack(spacing: 10) {
                        Image(systemName: icon(s.kind))
                            .frame(width: 18)
                            .foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(s.title).lineLimit(1)
                            Text(s.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                    .background(i == address.highlighted ? Color.accentColor.opacity(0.18) : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .frame(width: 620)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.18), radius: 18, y: 8)
    }

    private func icon(_ kind: AddressState.Suggestion.Kind) -> String {
        switch kind {
        case .search: "magnifyingglass"
        case .address: "globe"
        case .history: "clock"
        case .favorite: "bookmark"
        }
    }
}
