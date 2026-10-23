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
