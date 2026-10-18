import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("customPlatformToolsPath") private var customPath = ""
    @State private var cacheSize: Int64 = 0

    var body: some View {
        Form {
            Section("Android Platform Tools") {
                LabeledContent("Используются") {
                    Text(model.tools.directory?.path ?? "—")
                        .textSelection(.enabled)
                        .foregroundStyle(.secondary)
                }
                if let version = model.tools.version {
                    LabeledContent("Версия", value: version)
                }
                TextField("Свой путь к папке с adb и fastboot", text: $customPath, prompt: Text("Автоматически"))
                HStack {
                    Button("Применить") { model.retryTools() }
                    Button("Скачать свежую версию") {
                        Task { try? await model.tools.prepare(forceDownload: true) }
                    }
                }
            }

            Section("Загрузки") {
                LabeledContent("Кэш прошивок", value: cacheSize.formattedBytes)
                HStack {
                    Button("Показать в Finder") {
                        NSWorkspace.shared.activateFileViewerSelecting([AppPaths.downloads])
                    }
                    Button("Очистить", role: .destructive) {
                        try? FileManager.default.removeItem(at: AppPaths.downloads)
                        updateCacheSize()
                    }
                    .disabled(model.installSession?.isActive == true)
                }
            }

            Section {
                Text("ROM Forge не связан с проектами прошивок и производителями устройств. Установка альтернативной системы стирает данные и может лишить гарантии — действуйте осознанно.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .frame(width: 520)
        .padding(.vertical, 8)
        .onAppear(perform: updateCacheSize)
    }

    private func updateCacheSize() {
        let url = AppPaths.downloads
        let enumerator = FileManager.default.enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey])
        var total: Int64 = 0
        while let file = enumerator?.nextObject() as? URL {
            total += Int64((try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        }
        cacheSize = total
    }
}
