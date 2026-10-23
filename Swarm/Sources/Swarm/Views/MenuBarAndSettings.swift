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

/// Настройки: основные, сеть, приватность.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = DemoUI.settingsTab ?? 0

    var body: some View {
        TabView(selection: $tab) {
            GeneralSettings().tabItem { Label("Основные", systemImage: "gearshape") }.tag(0)
            NetworkSettings().tabItem { Label("Сеть", systemImage: "network") }.tag(1)
            PrivacySettings().tabItem { Label("Приватность", systemImage: "lock.shield") }.tag(2)
        }
        .frame(width: 540)
    }
}

private struct GeneralSettings: View {
    @Environment(AppModel.self) private var model
    @State private var folder: URL = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]

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
                Toggle("Веб-сиды — докачивать с HTTP-серверов из торрента", isOn: $model.settings.webSeedsEnabled)
            }
            Section {
                Text("Swarm написан с нуля: собственная реализация BitTorrent v1 и v2, uTP, DHT, шифрования, трекеров и magnet. Никакой телеметрии и сторонних библиотек.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .fixedSize(horizontal: false, vertical: true)
        .task { folder = model.downloadFolder }
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

/// Состояние сети раз в секунду.
private struct NetworkStatusPoller: ViewModifier {
    @Environment(AppModel.self) private var model
    @Binding var status: NetworkStatus

    func body(content: Content) -> some View {
        content.task {
            if DemoUI.isOn { status = DemoUI.network(); return }
            while !Task.isCancelled {
                status = await model.engine.networkStatus()
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }
}

private struct NetworkSettings: View {
    @Environment(AppModel.self) private var model
    @State private var status = NetworkStatus()

    var body: some View {
        @Bindable var model = model
        Form {
            Section("Подключения") {
                Stepper("Пиров на раздачу: \(model.settings.maxPeersPerTorrent)", value: $model.settings.maxPeersPerTorrent, in: 10...200, step: 10)
                Toggle("uTP — соединения поверх UDP, бережнее к каналу", isOn: $model.settings.utpEnabled)
                LabeledContent("Входящий порт") {
                    Text("\(status.port) · TCP\(status.udpOpen ? " и UDP" : "")").monospacedDigit().foregroundStyle(.secondary)
                }
                Toggle("Открывать порт на роутере (UPnP, NAT-PMP, PCP)", isOn: $model.settings.portMapping)
                if model.settings.portMapping {
                    LabeledContent("Роутер") {
                        Text(mappingText).foregroundStyle(status.mapping.externalPort != nil ? Color.green : .secondary)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            Section("Поиск пиров") {
                Toggle("Трекеры", isOn: $model.settings.trackersEnabled)
                Toggle("DHT — распределённая сеть без трекеров", isOn: $model.settings.dhtEnabled)
                if model.settings.dhtEnabled {
                    LabeledContent("Узел DHT") {
                        Text("знает \(status.dhtNodes) \(Format.plural(status.dhtNodes, "узел", "узла", "узлов")) · ответил на \(status.dhtQueries) · хранит \(status.dhtStoredPeers) анонсов")
                            .monospacedDigit().foregroundStyle(.secondary)
                    }
                }
                Toggle("Локальная сеть (LSD) — пиры в той же сети", isOn: $model.settings.lsdEnabled)
                    .disabled(model.settings.anonymous)
                if model.settings.anonymous {
                    Text("В анонимном режиме анонсы в локальную сеть выключены.").font(.system(size: 11)).foregroundStyle(.secondary)
                }
            }
        }
        .formStyle(.grouped)
        .fixedSize(horizontal: false, vertical: true)
        .modifier(NetworkStatusPoller(status: $status))
    }

    private var mappingText: String {
        let m = status.mapping
        guard let port = m.externalPort else { return m.message }
        return "\(m.method ?? "") · внешний порт \(port)" + (m.externalIP.map { " · \($0)" } ?? "")
    }
}

private struct PrivacySettings: View {
    @Environment(AppModel.self) private var model
    @State private var status = NetworkStatus()
    @State private var source = ""

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                Picker("Шифрование соединений", selection: $model.settings.encryption) {
                    ForEach(EncryptionMode.allCases) { Text($0.title).tag($0) }
                }
                Text(encryptionHint).font(.system(size: 11)).foregroundStyle(.secondary)
            } header: {
                Text("Шифрование")
            }
            Section("Анонимный режим") {
                Toggle("Не называть программу", isOn: $model.settings.anonymous)
                Text("Случайный идентификатор вместо «Swarm», без версии в рукопожатии, нейтральный User-Agent для трекеров и веб-сидов, без анонсов в локальную сеть. Ваш IP-адрес это не скрывает — для этого нужен VPN.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Чёрный список адресов") {
                Toggle("Не соединяться с адресами из списка", isOn: $model.settings.blocklistEnabled)
                if model.settings.blocklistEnabled {
                    HStack {
                        TextField("Файл или адрес списка (.p2p, .dat, .gz)", text: $source)
                            .textFieldStyle(.roundedBorder)
                            .onSubmit { model.settings.blocklistSource = source }
                        Button("Файл…") { chooseFile() }
                    }
                    HStack {
                        Text(blocklistText).font(.system(size: 11)).foregroundStyle(status.blocklistMessage == nil ? Color.secondary : Color.orange)
                        Spacer()
                        Button("Обновить") {
                            model.settings.blocklistSource = source
                            Task { await model.engine.reloadBlocklist() }
                        }
                        .disabled(source.isEmpty)
                    }
                }
            }
        }
        .formStyle(.grouped)
        .fixedSize(horizontal: false, vertical: true)
        .modifier(NetworkStatusPoller(status: $status))
        .task { source = model.settings.blocklistSource }
    }

    private var encryptionHint: String {
        switch model.settings.encryption {
        case .prefer: "Соединения шифруются (MSE/RC4), чтобы провайдер не опознал торрент по содержимому; с клиентами без шифрования — открытое соединение."
        case .require: "Только зашифрованные соединения. Пиров станет немного меньше: часть клиентов шифрование не поддерживает."
        case .off: "Исходящие соединения без шифрования; входящие зашифрованные всё равно принимаются."
        }
    }

    private var blocklistText: String {
        if let message = status.blocklistMessage { return message }
        guard status.blocklistRanges > 0 else { return "Список не загружен" }
        return "\(status.blocklistRanges) \(Format.plural(status.blocklistRanges, "диапазон", "диапазона", "диапазонов")) · отклонено соединений: \(status.blocklistBlocked)"
    }

    private func chooseFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        if panel.runModal() == .OK, let url = panel.url {
            source = url.path
            model.settings.blocklistSource = url.path
        }
    }
}

