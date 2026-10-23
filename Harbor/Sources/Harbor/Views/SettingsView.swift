import AppKit
import SwiftUI

struct SettingsView: View {
    @Environment(Store.self) private var store
    @State private var cleared: Int?

    var body: some View {
        @Bindable var store = store
        Form {
            Section("Обновления") {
                Toggle("Обновлять автоматически", isOn: $store.autoUpdate)
                Text("Незапущенные программы обновляются сразу, запущенные — когда вы их закроете. Данные и настройки не трогаются.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
                Toggle("Запускать Harbor при входе", isOn: Binding(get: { store.launchAtLogin }, set: { store.launchAtLogin = $0 }))
                LabeledContent("Последняя проверка") {
                    Text(store.lastCheck?.formatted(date: .omitted, time: .shortened) ?? "ещё не было").foregroundStyle(.secondary)
                }
            }
            Section("Установка") {
                LabeledContent("Папка") { Text(Locations.installDirectory.path).foregroundStyle(.secondary) }
                LabeledContent("Резервные копии") {
                    Button("Показать") { NSWorkspace.shared.open(Locations.backups) }
                }
                HStack {
                    Button("Открыть «Управление приложениями»") { Store.openAppManagementSettings() }
                    Spacer()
                }
                HStack {
                    Button("Снять метку карантина с установленных") { cleared = store.clearQuarantine() }
                    if let cleared { Text(cleared == 0 ? "меток нет" : "снято: \(cleared)").foregroundStyle(.secondary) }
                }
            }
            Section {
                Text("Harbor скачивает сборки, опубликованные CI из открытого исходного кода, проверяет SHA-256 и подпись и только потом ставит. Никакой телеметрии.")
                    .font(.system(size: 11.5)).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .frame(width: 500)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Мини-панель в строке меню.
struct MenuBarView: View {
    @Environment(Store.self) private var store
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "sailboat.fill").foregroundStyle(.teal)
                Text("Harbor").font(.headline)
                Spacer()
                if store.checking { ProgressView().controlSize(.small) }
            }
            if store.updates.isEmpty {
                Text("Все приложения обновлены").font(.system(size: 12)).foregroundStyle(.secondary)
            } else {
                ForEach(store.updates) { app in
                    HStack(spacing: 8) {
                        AppIconView(app: app, size: 26)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(app.name).font(.system(size: 12.5, weight: .semibold))
                            if case .update(let from, let to) = store.state(app) {
                                Text("\(from.marketing) → \(to.description)").font(.system(size: 10.5)).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        ActionButton(app: app)
                    }
                }
            }
            Divider()
            HStack {
                Button("Открыть Harbor") {
                    openWindow(id: "main")
                    NSApp.activate()
                }
                Spacer()
                Button("Проверить") { Task { await store.check() } }
            }
            .buttonStyle(.glass)
        }
        .padding(14)
        .frame(width: 320)
    }
}
