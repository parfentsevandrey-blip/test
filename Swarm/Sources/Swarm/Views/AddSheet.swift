import AppKit
import SwiftUI

/// Подтверждение добавления: что скачиваем, какие файлы и куда.
struct AddTorrentSheet: View {
    @Environment(AppModel.self) private var model
    let pending: PendingAdd

    @State private var folder: URL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]
    @State private var selected: Set<Int> = []
    @State private var sequential = false
    @State private var startNow = true

    private var meta: Metainfo? {
        if case .torrent(let m) = pending.source { return m }
        return nil
    }

    private var selectedSize: Int64 {
        guard let meta else { return 0 }
        return selected.reduce(0) { $0 + meta.files[$1].length }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                Image(systemName: meta == nil ? "link.circle.fill" : "doc.zipper")
                    .font(.system(size: 34, weight: .medium))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 3) {
                    Text(pending.name).font(.system(size: 16, weight: .semibold)).lineLimit(2)
                    if let meta {
                        Text("\(Format.bytes(meta.totalLength)) · \(meta.files.count) файлов · \(meta.pieceCount) кусков")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("Описание раздачи будет получено от пиров через DHT — файлы можно выбрать после этого")
                            .foregroundStyle(.secondary)
                    }
                }
                .font(.system(size: 12))
            }

            if let meta, meta.files.count > 1 {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Файлы").font(.system(size: 12, weight: .semibold))
                        Spacer()
                        Text("выбрано \(Format.bytes(selectedSize))").foregroundStyle(.secondary).monospacedDigit()
                        Button("Все") { selected = Set(meta.files.indices) }
                        Button("Ничего") { selected = [] }
                    }
                    .font(.system(size: 11.5))
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 2) {
                            ForEach(Array(meta.files.enumerated()), id: \.offset) { index, file in
                                if !file.path.contains(".pad") {
                                    Toggle(isOn: Binding(get: { selected.contains(index) },
                                                         set: { if $0 { selected.insert(index) } else { selected.remove(index) } })) {
                                        HStack {
                                            Text(file.path.dropFirst().joined(separator: "/")).lineLimit(1).truncationMode(.middle)
                                            Spacer()
                                            Text(Format.bytes(file.length)).foregroundStyle(.secondary).monospacedDigit()
                                        }
                                        .font(.system(size: 12))
                                    }
                                    .toggleStyle(.checkbox)
                                    .padding(.vertical, 3)
                                }
                            }
                        }
                        .padding(10)
                    }
                    .frame(maxHeight: 220)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.primary.opacity(0.05)))
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label(folder.path, systemImage: "folder.fill")
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Spacer()
                    Button("Изменить…") { chooseFolder() }
                        .buttonStyle(.glass)
                }
                Toggle("Начать загрузку сразу", isOn: $startNow)
                Toggle("Последовательно — чтобы смотреть видео во время загрузки", isOn: $sequential)
            }
            .font(.system(size: 12.5))
            .toggleStyle(.switch)

            HStack {
                Spacer()
                Button("Отмена") { model.pendingAdd = nil }
                    .keyboardShortcut(.cancelAction)
                    .buttonStyle(.glass)
                Button("Добавить") {
                    model.downloadFolder = folder
                    let priorities = meta.map { m in m.files.indices.map { selected.contains($0) ? FilePriority.normal : .skip } }
                    model.confirm(pending, folder: folder, priorities: priorities, sequential: sequential, startNow: startNow)
                }
                .keyboardShortcut(.defaultAction)
                .buttonStyle(.glassProminent)
                .disabled(meta != nil && selected.isEmpty)
            }
            .controlSize(.large)
        }
        .padding(24)
        .frame(width: 580)
        .onAppear {
            folder = model.downloadFolder
            if let meta { selected = Set(meta.files.indices) }
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.canCreateDirectories = true
        panel.directoryURL = folder
        panel.prompt = "Выбрать"
        if panel.runModal() == .OK, let url = panel.url { folder = url }
    }
}
