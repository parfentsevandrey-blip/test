import AppKit
import SwiftUI

/// Меню в строке меню: скорость и активные раздачи — без открытия окна.
struct MenuBarView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    private var active: [TorrentSnapshot] {
        model.torrents.filter { $0.status == .downloading || $0.status == .metadata || $0.status == .checking }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(Format.rate(model.totalDown), systemImage: "arrow.down").foregroundStyle(.blue)
                Label(Format.rate(model.totalUp), systemImage: "arrow.up").foregroundStyle(Color(red: 0.15, green: 0.75, blue: 0.55))
                Spacer()
            }
            .font(.system(size: 13, weight: .semibold))
            .monospacedDigit()
            SpeedChart(samples: model.history, compact: true)
                .frame(height: 44)

            if active.isEmpty {
                Text(model.torrents.isEmpty ? "Нет раздач" : "Всё скачано")
                    .foregroundStyle(.secondary)
                    .font(.system(size: 12))
            } else {
                ForEach(active.prefix(6)) { t in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(t.name).lineLimit(1)
                            Spacer()
                            Text(Format.percent(t.progress)).monospacedDigit().foregroundStyle(.secondary)
                        }
                        .font(.system(size: 12))
                        LiquidProgress(progress: t.progress, tint: t.status.color, active: t.downRate > 0, height: 5)
                    }
                }
            }

            Divider()
            GlassEffectContainer(spacing: 6) {
                HStack(spacing: 6) {
                    Button("Открыть Swarm") {
                        openWindow(id: "main")
                        NSApp.activate()
                    }
                    Spacer()
                    Button { model.pauseAll() } label: { Image(systemName: "pause.fill") }.help("Пауза для всех")
                    Button { model.resumeAll() } label: { Image(systemName: "play.fill") }.help("Продолжить все")
                    Button { NSApp.terminate(nil) } label: { Image(systemName: "power") }.help("Выйти")
                }
                .buttonStyle(.glass)
            }
        }
        .padding(14)
        .frame(width: 320)
    }
}

/// Настройки: папка, сеть, ограничения.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var folder: URL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]
    @State private var port = 0

    var body: some View {
        @Bindable var model = model
        Form {
            Section("Загрузки") {
                LabeledContent("Папка") {
                    HStack {
                        Text(folder.path).lineLimit(1).truncationMode(.middle).foregroundStyle(.secondary)
                        Button("Изменить…") { chooseFolder() }
                    }
                }
                Button("Открывать magnet-ссылки в Swarm") { model.makeDefaultMagnetHandler() }
            }
            Section("Скорость") {
                limitRow("Загрузка", value: $model.settings.downloadLimit)
                limitRow("Отдача", value: $model.settings.uploadLimit)
                LabeledContent("Остановить раздачу при рейтинге") {
                    Picker("", selection: $model.settings.seedRatioLimit) {
                        Text("никогда").tag(0.0)
                        Text("1.0").tag(1.0)
                        Text("2.0").tag(2.0)
                        Text("5.0").tag(5.0)
                    }
                    .labelsHidden()
                    .fixedSize()
                }
            }
            Section("Сеть") {
                Stepper("Пиров на раздачу: \(model.settings.maxPeersPerTorrent)", value: $model.settings.maxPeersPerTorrent, in: 10...200, step: 10)
                Toggle("DHT — поиск пиров без трекеров", isOn: $model.settings.dhtEnabled)
                Toggle("Трекеры", isOn: $model.settings.trackersEnabled)
                LabeledContent("Входящий порт") {
                    Text("\(port) · TCP").monospacedDigit().foregroundStyle(.secondary)
                }
            }
            Section {
                Text("Swarm написан с нуля: собственная реализация BitTorrent, DHT, трекеров и magnet. Никакой телеметрии и сторонних библиотек.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .frame(width: 500)
        .fixedSize(horizontal: false, vertical: true)
        .task {
            folder = model.downloadFolder
            port = Int(await model.engine.port)
        }
    }

    private func limitRow(_ title: String, value: Binding<Int>) -> some View {
        LabeledContent(title) {
            Picker("", selection: value) {
                Text("без ограничения").tag(0)
                ForEach([256, 512, 1024, 2048, 5120, 10240, 25600], id: \.self) { kb in
                    Text(Format.rate(Double(kb * 1024))).tag(kb * 1024)
                }
            }
            .labelsHidden()
            .fixedSize()
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.canCreateDirectories = true
        panel.directoryURL = folder
        if panel.runModal() == .OK, let url = panel.url {
            folder = url
            model.downloadFolder = url
        }
    }
}
