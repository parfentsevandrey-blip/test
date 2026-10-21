import AppKit
import QuickLook
import QuickLookThumbnailing
import SwiftUI

enum ClutterTab: String, CaseIterable, Identifiable {
    case large, old, duplicates
    var id: String { rawValue }
    var title: String {
        switch self {
        case .large: "Крупные"
        case .old: "Давно не открывали"
        case .duplicates: "Дубликаты"
        }
    }
}

/// «Крупные файлы»: большие, забытые и одинаковые файлы в личных папках. Всё — только в Корзину.
struct ClutterView: View {
    @Environment(AppModel.self) private var model
    @State private var tab: ClutterTab = .large
    @State private var kind: FileKind?
    @State private var preview: URL?

    var body: some View {
        Group {
            switch model.clutterPhase {
            case .idle:
                ModuleHero(module: .clutter, headline: "Крупные и забытые файлы",
                           text: "Видео, образы дисков, архивы и дубликаты в ваших папках. ~/Library и медиатеки Фото и Музыки не трогаем; удаление — только в Корзину.",
                           button: "Найти") { model.scanClutter() }
            case .scanning(_, let label) where model.clutter.scanned == 0:
                ModuleHero(module: .clutter, headline: "Крупные и забытые файлы", text: "Дубликаты сравниваются по содержимому (SHA-256), а не по имени.",
                           button: "", busy: label) {}
            default:
                content
            }
        }
        .quickLookPreview($preview)
    }

    private var files: [FileEntry] {
        let list = tab == .large ? model.clutter.large : model.clutter.old
        return kind.map { k in list.filter { $0.kind == k } } ?? list
    }

    private var selectedSize: Int64 {
        let all = model.clutter.large + model.clutter.old + model.clutter.duplicates.flatMap(\.files)
        var seen = Set<String>()
        return all.filter { model.clutterSelection.contains($0.id) && seen.insert($0.id).inserted }.reduce(0) { $0 + $1.size }
    }

    private var content: some View {
        VStack(spacing: 0) {
            ModuleHeader(title: "Крупные файлы", subtitle: "Просмотрено \(model.clutter.scanned) файлов. Пробел или глаз — быстрый просмотр.") {
                Button { model.scanClutter() } label: { Label("Заново", systemImage: "arrow.clockwise") }
                    .buttonStyle(.glass)
                    .disabled(model.isBusy(model.clutterPhase))
            }
            HStack(spacing: 10) {
                Picker("", selection: $tab) {
                    ForEach(ClutterTab.allCases) { t in Text(tabTitle(t)).tag(t) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .frame(width: 460)
                if tab != .duplicates {
                    Menu {
                        Button("Все виды") { kind = nil }
                        ForEach(FileKind.allCases, id: \.self) { k in Button(k.title) { kind = k } }
                    } label: { Label(kind?.title ?? "Все виды", systemImage: kind?.symbol ?? "line.3.horizontal.decrease") }
                    .fixedSize()
                }
                Spacer()
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 12)
            ContentCard {
                if tab == .duplicates { duplicates } else { list }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 92)
        }
        .overlay(alignment: .bottom) {
            ActionBar(title: "Выбрано \(Format.bytes(selectedSize))", subtitle: "Файлы отправятся в Корзину — их можно вернуть",
                      action: "В Корзину", tint: Module.clutter.tint, disabled: selectedSize == 0) { model.cleanClutter() }
        }
    }

    private func tabTitle(_ t: ClutterTab) -> String {
        switch t {
        case .large: "Крупные · \(model.clutter.large.count)"
        case .old: "Старые · \(model.clutter.old.count)"
        case .duplicates: "Дубликаты · \(Format.bytes(model.clutter.duplicates.reduce(0) { $0 + $1.wasted }))"
        }
    }

    private var list: some View {
        Group {
            if files.isEmpty {
                ContentUnavailableView("Ничего не найдено", systemImage: "doc.text.magnifyingglass").frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(files) { file in
                            FileRow(file: file, selected: model.clutterSelection.contains(file.id), toggle: { model.toggleClutter(file) },
                                    preview: { preview = file.url })
                            Divider().opacity(0.35).padding(.leading, 70)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private var duplicates: some View {
        Group {
            if model.clutter.duplicates.isEmpty {
                ContentUnavailableView("Дубликатов нет", systemImage: "checkmark.seal", description: Text("Сравнивались файлы от 1 МБ по содержимому."))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ForEach(model.clutter.duplicates) { group in
                            VStack(alignment: .leading, spacing: 0) {
                                HStack {
                                    Text("\(group.files.count) одинаковых файла по \(Format.bytes(group.size))").font(.system(size: 12, weight: .semibold))
                                    Spacer()
                                    Text("лишнее: \(Format.bytes(group.wasted))").font(.system(size: 11.5)).foregroundStyle(.secondary).monospacedDigit()
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                let keeper = ClutterScanner.keeper(in: group, home: model.home)
                                ForEach(group.files) { file in
                                    FileRow(file: file, selected: model.clutterSelection.contains(file.id), original: file.id == keeper?.id,
                                            toggle: { model.toggleClutter(file) }, preview: { preview = file.url })
                                }
                            }
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.primary.opacity(0.03)))
                        }
                    }
                    .padding(12)
                }
            }
        }
    }
}

struct FileRow: View {
    @Environment(AppModel.self) private var model
    let file: FileEntry
    let selected: Bool
    var original = false
    let toggle: () -> Void
    let preview: () -> Void
    @State private var thumbnail: NSImage?

    var body: some View {
        HStack(spacing: 12) {
            Toggle("", isOn: Binding(get: { selected }, set: { _ in toggle() })).toggleStyle(.checkbox).labelsHidden()
            Group {
                if let thumbnail {
                    Image(nsImage: thumbnail).resizable().scaledToFill()
                } else {
                    FileIcon(url: file.url, symbol: file.kind.symbol, size: 40)
                }
            }
            .frame(width: 40, height: 40)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(file.url.lastPathComponent).font(.system(size: 13, weight: .medium)).lineLimit(1).truncationMode(.middle)
                    if original { Badge(text: "оригинал", color: .green) }
                }
                Text(Format.path(file.url.deletingLastPathComponent(), home: model.home))
                    .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
            }
            Spacer(minLength: 8)
            Text(file.lastUse.map { "открывали \(Format.relative($0))" } ?? "")
                .font(.system(size: 11)).foregroundStyle(.tertiary).lineLimit(1)
            Button(action: preview) { Image(systemName: "eye") }.buttonStyle(.borderless).help("Быстрый просмотр")
            Text(Format.bytes(file.size)).font(.system(size: 12.5, weight: .semibold)).monospacedDigit().frame(minWidth: 72, alignment: .trailing)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture(perform: toggle)
        .contextMenu {
            Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([file.url]) }
            Button("Быстрый просмотр", action: preview)
            Button("Никогда не трогать") { model.ignore(file.url) }
        }
        .task(id: file.id) {
            let request = QLThumbnailGenerator.Request(fileAt: file.url, size: CGSize(width: 80, height: 80), scale: 2, representationTypes: .thumbnail)
            if let rep = try? await QLThumbnailGenerator.shared.generateBestRepresentation(for: request) {
                thumbnail = rep.nsImage
            }
        }
    }
}
