import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct MainView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings

    var body: some View {
        @Bindable var model = model
        HStack(spacing: 0) {
            ConnectionPanel()
                .frame(width: 360)
            Divider().opacity(0.3)
            ServerList()
                .frame(minWidth: 420, maxWidth: .infinity)
        }
        .background(Aura(status: model.status))
        .overlay(alignment: .bottom) {
            if let toast = model.toast {
                Toast(text: toast).padding(.bottom, 18)
            }
        }
        .animation(.smooth, value: model.toast)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button { Task { await model.pingAll() } } label: {
                    if model.pingingAll { ProgressView().controlSize(.small) } else { Label("Проверить задержку", systemImage: "speedometer") }
                }
                .help("Проверить задержку всех серверов")
                .disabled(model.servers.isEmpty)
                Button { model.showAdd = true } label: { Label("Добавить", systemImage: "plus") }
                    .help("Добавить подписку или сервер")
                Button { openWindow(id: "log") } label: { Label("Журнал", systemImage: "text.alignleft") }
                    .help("Журнал подключения")
            }
        }
        .sheet(isPresented: $model.showAdd) { AddSheet() }
        .sheet(item: $model.shareServer) { ShareSheet(server: $0) }
        .frame(minWidth: 860, minHeight: 600)
        .onChange(of: model.settingsRequest) { openSettings() }
        .onChange(of: model.logRequest) { openWindow(id: "log") }
        .onDrop(of: [.fileURL, .image, .plainText], isTargeted: nil) { providers in
            AddSheet.handleDrop(providers, model: model)
            return true
        }
    }
}

/// Добавление: вставить текст, отсканировать QR-код с экрана или из картинки.
struct AddSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var busy = false
    @State private var result: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Добавить подписку или серверы").font(.system(size: 18, weight: .bold))
            Text("Ссылка подписки (https://…, happ://add/…), ссылки серверов (vless://, vmess://, trojan://, ss://, socks://, hy2://) — по одной на строку, профиль маршрутизации happ://routing/… или JSON-конфигурация Xray.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            TextEditor(text: $text)
                .font(.system(size: 12, design: .monospaced))
                .scrollContentBackground(.hidden)
                .padding(8)
                .frame(height: 150)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(0.05)))
            HStack(spacing: 8) {
                Button { text = NSPasteboard.general.string(forType: .string) ?? text } label: { Label("Из буфера", systemImage: "doc.on.clipboard") }
                Button { scanScreen() } label: { Label("QR-код на экране", systemImage: "qrcode.viewfinder") }
                Button { pickImage() } label: { Label("QR из картинки", systemImage: "photo") }
            }
            .buttonStyle(.glass)
            if let result {
                Text(result).font(.system(size: 12)).foregroundStyle(.secondary).textSelection(.enabled)
            }
            HStack {
                Spacer()
                Button("Отмена") { dismiss() }.keyboardShortcut(.cancelAction)
                Button {
                    busy = true
                    Task {
                        let r = await model.importText(text)
                        busy = false
                        model.flash(r)
                        if !r.hasPrefix("Не ") { dismiss() } else { result = r }
                    }
                } label: {
                    if busy { ProgressView().controlSize(.small) } else { Text("Добавить") }
                }
                .buttonStyle(.glassProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
            }
        }
        .padding(22)
        .frame(width: 560)
        .onAppear {
            // Если в буфере уже ссылка — подставляем её сразу.
            let clip = NSPasteboard.general.string(forType: .string) ?? ""
            if clip.contains("://") && text.isEmpty { text = clip }
        }
    }

    private func scanScreen() {
        Task {
            let codes = await QR.scanScreen()
            if codes.isEmpty {
                result = "QR-кодов на экране не нашлось. Если macOS спросит — разрешите Veil «Запись экрана»."
            } else {
                text = codes.joined(separator: "\n")
                result = "Найдено QR-кодов: \(codes.count)"
            }
        }
    }

    private func pickImage() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.image]
        panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url, let image = NSImage(contentsOf: url) else { return }
        let codes = QR.read(image)
        if codes.isEmpty { result = "На картинке нет QR-кода" } else { text = codes.joined(separator: "\n") }
    }

    /// Перетащили в окно картинку с QR, файл со ссылками или текст.
    static func handleDrop(_ providers: [NSItemProvider], model: AppModel) {
        for provider in providers {
            if provider.canLoadObject(ofClass: NSImage.self) {
                _ = provider.loadObject(ofClass: NSImage.self) { image, _ in
                    let codes = (image as? NSImage).map { QR.read($0) } ?? []
                    importDropped(codes.joined(separator: "\n"), empty: "На картинке нет QR-кода")
                }
            } else if provider.canLoadObject(ofClass: String.self) {
                _ = provider.loadObject(ofClass: String.self) { text, _ in
                    importDropped(text ?? "", empty: "Пустой текст")
                }
            }
        }
    }

    nonisolated static func importDropped(_ text: String, empty: String) {
        Task { @MainActor in
            let model = AppModel.shared
            if text.isEmpty {
                model.flash(empty)
                return
            }
            let message = await model.importText(text)
            model.flash(message)
        }
    }
}

/// QR-код и ссылка сервера — чтобы перенести на телефон.
struct ShareSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var server: Server

    var body: some View {
        let link = Links.link(for: server)
        VStack(spacing: 14) {
            Text("\(server.flag) \(server.title)").font(.system(size: 16, weight: .bold))
            if let image = QR.image(link) {
                Image(nsImage: image)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 260, height: 260)
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 18).fill(.white))
            }
            Text("Отсканируйте камерой телефона в Happ, Streisand или v2rayNG.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary)
            HStack {
                Button("Скопировать ссылку") { model.copy(link) }.buttonStyle(.glass)
                Button("Готово") { dismiss() }.buttonStyle(.glassProminent).keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(width: 360)
    }
}

/// Журнал ядра и приложения.
struct LogView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let _ = model.logVersion
        let entries = LogBook.shared.all
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 3) {
                        ForEach(entries) { e in
                            HStack(alignment: .top, spacing: 8) {
                                Text(e.date.formatted(date: .omitted, time: .standard)).foregroundStyle(.tertiary)
                                Text(e.text).foregroundStyle(e.level == .error ? .red : e.level == .warning ? .orange : .primary)
                                    .textSelection(.enabled)
                            }
                            .font(.system(size: 11.5, design: .monospaced))
                            .id(e.id)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: entries.last?.id) { _, id in if let id { proxy.scrollTo(id, anchor: .bottom) } }
            }
            Divider()
            HStack {
                Text("Ядро Xray \(model.coreVersion)").font(.system(size: 11)).foregroundStyle(.secondary)
                Spacer()
                Button("Скопировать всё") { model.copy(entries.map { "\($0.date.formatted(date: .omitted, time: .standard)) \($0.text)" }.joined(separator: "\n")) }
                Button("Очистить") { LogBook.shared.clear(); model.logVersion += 1 }
            }
            .padding(10)
        }
        .frame(minWidth: 620, minHeight: 360)
    }
}

/// Меню в строке меню: состояние, кнопка, быстрые серверы.
struct MenuBarContent: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Button(model.status.isOn ? "Отключить" : "Подключить") { model.toggle() }
            .keyboardShortcut("k")
        if let s = model.selected {
            Text("\(s.flag) \(s.title) · \(model.status.title)")
        }
        if model.status.isOn {
            Text("↓ \(Format.speed(model.speedDown))   ↑ \(Format.speed(model.speedUp))")
        }
        Divider()
        let quick = model.servers.filter { $0.issue == nil }
            .sorted { (model.ping($0.id)?.value ?? Int.max) < (model.ping($1.id)?.value ?? Int.max) }.prefix(8)
        if !quick.isEmpty {
            Menu("Сервер") {
                ForEach(Array(quick)) { s in
                    Button("\(s.flag) \(s.title)\(model.ping(s.id)?.value.map { " — \($0) мс" } ?? "")") { model.switchTo(s) }
                }
            }
        }
        Menu("Режим") {
            ForEach(ConnectionMode.allCases) { mode in
                Button((model.saved.mode == mode ? "✓ " : "") + mode.title) { model.setMode(mode) }
            }
        }
        Menu("Маршрутизация") {
            ForEach(RouteMode.allCases.filter { $0 != .profile || model.saved.routing.profile != nil }) { mode in
                Button((model.saved.routing.mode == mode ? "✓ " : "") + mode.title) { model.setRouting { $0.mode = mode } }
            }
        }
        Divider()
        Button("Открыть Veil") {
            NSApp.activate()
            openWindow(id: "main")
        }
        Button("Выйти") { NSApp.terminate(nil) }.keyboardShortcut("q")
    }
}
