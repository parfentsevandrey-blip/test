import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = DemoUI.settingsTab

    private static let tabs: [(String, String)] = [
        ("Основное", "gearshape"), ("Маршруты", "arrow.triangle.branch"), ("Сеть", "network"), ("Помощник", "lock.shield"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                ForEach(Self.tabs.indices, id: \.self) { i in
                    Button { tab = i } label: {
                        VStack(spacing: 3) {
                            Image(systemName: Self.tabs[i].1).font(.system(size: 16, weight: .medium))
                            Text(Self.tabs[i].0).font(.system(size: 11, weight: .medium))
                        }
                        .frame(width: 104, height: 48)
                        .foregroundStyle(tab == i ? Color.accentColor : .secondary)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(tab == i ? Color.accentColor.opacity(0.14) : .clear))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(4)
            .glassEffect(.regular, in: .rect(cornerRadius: 16))
            .padding(.top, 12)
            Group {
                switch tab {
                case 0: general
                case 1: routing
                case 2: network
                default: helper
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(width: 620, height: 560)
    }

    // MARK: Основное

    private var general: some View {
        @Bindable var model = model
        return Form {
            Section("Подключение") {
                Toggle("Подключаться при запуске Veil", isOn: $model.saved.settings.connectOnLaunch)
                Toggle("Если сервер перестал отвечать — переключаться на самый быстрый", isOn: $model.saved.settings.autoFailover)
                Text("Veil проверяет связь через сервер каждые 20 секунд и после смены сети или сна Mac переподключается сам.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Программа") {
                Toggle("Запускать при входе в систему", isOn: Binding(get: { model.launchAtLogin }, set: { model.launchAtLogin = $0 }))
                Toggle("Показывать значок в Dock", isOn: Binding(get: { model.saved.settings.showInDock },
                                                                 set: { model.saved.settings.showInDock = $0; model.applyDockPolicy() }))
            }
            Section("Подписки") {
                Toggle("Обновлять подписки автоматически", isOn: $model.saved.settings.autoUpdateSubscriptions)
                LabeledContent("User-Agent") {
                    Picker("", selection: $model.saved.settings.userAgent) {
                        Text("Veil").tag("Veil/1.0 (macOS)")
                        Text("Happ").tag("Happ/3.6.0")
                        Text("v2rayN").tag("v2rayN/7.13")
                        Text("Streisand").tag("Streisand/1.6")
                    }
                    .labelsHidden()
                    .fixedSize()
                }
                Toggle("Передавать идентификатор устройства (HWID)", isOn: $model.saved.settings.sendHWID)
                Text("Некоторые провайдеры ограничивают число устройств по HWID. Veil передаёт случайный идентификатор, а не серийный номер Mac.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }

    // MARK: Маршруты

    private var routing: some View {
        @Bindable var model = model
        return Form {
            Section("Режим") {
                Picker("Что через VPN", selection: Binding(get: { model.saved.routing.mode }, set: { m in model.setRouting { $0.mode = m } })) {
                    ForEach(RouteMode.allCases.filter { $0 != .profile || model.saved.routing.profile != nil }) { Text($0.title).tag($0) }
                }
                Text(model.saved.routing.mode.subtitle).font(.system(size: 11)).foregroundStyle(.secondary)
                Toggle("Блокировать рекламу и трекеры", isOn: Binding(get: { model.saved.routing.blockAds }, set: { v in model.setRouting { $0.blockAds = v } }))
            }
            Section("Свои правила") {
                rules("Всегда напрямую", \.direct, "sber.ru\nmy-office.local\n10.0.0.0/8")
                rules("Всегда через VPN", \.proxy, "chatgpt.com\nspotify.com")
                rules("Блокировать", \.block, "ads.example.com")
                Text("Домены (вместе с поддоменами) и IP-адреса или подсети, по одному на строку. Можно geosite:… и geoip:….")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Базы сайтов") {
                LabeledContent("Списки runetfreedom") {
                    HStack {
                        if model.geoDownloading { ProgressView().controlSize(.small) }
                        Text(model.geoUpdated.map { "обновлены \(Format.relative($0))" } ?? "не скачаны").foregroundStyle(.secondary)
                        Button("Обновить") { model.updateGeo() }.disabled(model.geoDownloading)
                    }
                }
                Text("Заблокированные в России сайты и адреса (антифильтр, re:filter) и российские сервисы. Обновляются раз в неделю.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            if let profile = model.saved.routing.profile {
                Section("Профиль провайдера") {
                    LabeledContent("Название", value: profile.name)
                    LabeledContent("Остальное", value: profile.globalProxy ? "через VPN" : "напрямую")
                    LabeledContent("Правил", value: "\(ruleCount(profile))")
                    Button("Удалить профиль", role: .destructive) { model.setRouting { $0.profile = nil; if $0.mode == .profile { $0.mode = .smart } } }
                }
            }
        }
        .formStyle(.grouped)
    }

    private func ruleCount(_ p: HappRoutingProfile) -> Int {
        let lists: [[String]] = [p.directSites, p.directIP, p.proxySites, p.proxyIP, p.blockSites, p.blockIP]
        return lists.reduce(0) { $0 + $1.count }
    }

    private func rules(_ title: String, _ key: WritableKeyPath<RoutingSettings, [String]>, _ placeholder: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 12, weight: .semibold))
            RulesEditor(text: model.saved.routing[keyPath: key].joined(separator: "\n"), placeholder: placeholder) { value in
                let list = value.split(whereSeparator: \.isNewline).map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                if list != model.saved.routing[keyPath: key] { model.setRouting { $0[keyPath: key] = list } }
            }
        }
    }

    // MARK: Сеть

    private var network: some View {
        @Bindable var model = model
        return Form {
            Section("Локальные порты") {
                LabeledContent("SOCKS5") { TextField("", value: $model.saved.settings.socksPort, format: .number.grouping(.never)).frame(width: 80) }
                LabeledContent("HTTP") { TextField("", value: $model.saved.settings.httpPort, format: .number.grouping(.never)).frame(width: 80) }
                Toggle("Раздавать прокси устройствам в локальной сети", isOn: $model.saved.settings.lanShare)
                if model.saved.settings.lanShare {
                    Text("Телефон или телевизор в той же сети Wi-Fi может указать этот Mac как прокси: адрес Mac, порт \(model.saved.settings.httpPort) (HTTP) или \(model.saved.settings.socksPort) (SOCKS5).")
                        .font(.system(size: 11)).foregroundStyle(.secondary)
                }
            }
            Section("Обход блокировок по DPI") {
                Toggle("Фрагментировать первый пакет TLS", isOn: $model.saved.settings.fragment.enabled)
                if model.saved.settings.fragment.enabled {
                    LabeledContent("Длина кусков") { TextField("", text: $model.saved.settings.fragment.length).frame(width: 100) }
                    LabeledContent("Пауза, мс") { TextField("", text: $model.saved.settings.fragment.interval).frame(width: 100) }
                }
                Text("Помогает, если провайдер режет соединения по имени сайта в первом пакете. Без нужды не включайте — немного замедляет подключение.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("DNS") {
                LabeledContent("Через VPN") { TextField("", text: $model.saved.settings.remoteDNS).frame(width: 260) }
                LabeledContent("Для российских сайтов") { TextField("", text: $model.saved.settings.domesticDNS).frame(width: 260) }
                Text("DNS-запросы для зарубежных сайтов идут через сервер — провайдер их не видит и не подменяет.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Text("Изменения применяются при следующем подключении.").font(.system(size: 11)).foregroundStyle(.secondary)
        }
        .formStyle(.grouped)
    }

    // MARK: Помощник

    private var helper: some View {
        Form {
            Section("Помощник Veil") {
                LabeledContent("Состояние") {
                    Label(model.helperReady ? "установлен" : "не установлен",
                          systemImage: model.helperReady ? "checkmark.seal.fill" : "xmark.seal")
                        .foregroundStyle(model.helperReady ? .green : .secondary)
                }
                Text("Маленькая служба с правами администратора: включает системный прокси и режим TUN, а если Veil закроется аварийно — сама вернёт настройки сети, чтобы Mac не остался без интернета. Ставится один раз, пароль спрашивает macOS.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
                HStack {
                    Button(model.helperReady ? "Переустановить" : "Установить") { model.installHelper() }
                    if model.helperReady { Button("Удалить", role: .destructive) { model.uninstallHelper() } }
                }
            }
            Section("О программе") {
                LabeledContent("Veil", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")
                LabeledContent("Ядро Xray", value: model.coreVersion.isEmpty ? "—" : model.coreVersion)
                LabeledContent("Данные", value: AppPaths.support.path)
            }
        }
        .formStyle(.grouped)
    }
}

/// Многострочное поле, которое сохраняет, когда закончили печатать (а не на каждую букву).
struct RulesEditor: View {
    @State var text: String
    var placeholder: String
    var onCommit: (String) -> Void
    @FocusState private var focused: Bool

    var body: some View {
        TextEditor(text: $text)
            .font(.system(size: 11.5, design: .monospaced))
            .frame(height: 54)
            .focused($focused)
            .overlay(alignment: .topLeading) {
                if text.isEmpty { Text(placeholder).font(.system(size: 11.5, design: .monospaced)).foregroundStyle(.tertiary).padding(.leading, 5).allowsHitTesting(false) }
            }
            .onChange(of: focused) { _, isFocused in if !isFocused { onCommit(text) } }
            .onDisappear { onCommit(text) }
    }
}
