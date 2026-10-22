import SwiftUI

/// Настройки: что делает каждая защита — простыми словами.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = DemoUI.settingsTab

    var body: some View {
        TabView(selection: $tab) {
            ProtectionSettings().tabItem { Label("Защита", systemImage: "shield.lefthalf.filled") }.tag(0)
            ListsSettings().tabItem { Label("Списки", systemImage: "list.bullet.rectangle") }.tag(1)
            DataSettings().tabItem { Label("Данные", systemImage: "externaldrive") }.tag(2)
            SearchSettings().tabItem { Label("Поиск", systemImage: "magnifyingglass") }.tag(3)
            GeneralSettings().tabItem { Label("Основные", systemImage: "gearshape") }.tag(4)
        }
        .frame(width: 640, height: 620)
    }
}

/// Переключатель с пояснением под ним.
struct SettingToggle: View {
    let title: String
    let detail: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                Text(detail).font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .toggleStyle(.switch)
    }
}

struct ProtectionSettings: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                SettingToggle(title: "Защита от отпечатка браузера",
                              detail: "Сайты узнают вас без cookie — по тому, как ваш Mac рисует картинки, звук и текст. Ward добавляет в эти ответы незаметный шум: для каждого сайта свой и новый при каждом запуске. Связать ваши визиты на разных сайтах становится нельзя.",
                              isOn: $model.prefs.fingerprint)
                SettingToggle(title: "Очищать ссылки",
                              detail: "Убирает из адресов метки слежки (utm_source, fbclid, yclid и сотни других) и пропускает ссылки-переходники вроде google.com/url или vk.com/away.php: сайт, откуда вы пришли, не узнаёт, куда вы ушли.",
                              isOn: $model.prefs.cleanLinks)
                SettingToggle(title: "Только HTTPS",
                              detail: "Сначала пробует открыть сайт по защищённому соединению. Если сайт так не умеет, Ward предупредит, прежде чем открыть его без шифрования.",
                              isOn: $model.prefs.httpsOnly)
                SettingToggle(title: "Предупреждать о мошеннических сайтах",
                              detail: "Сверка с базой Safe Browsing, как в Safari: Mac сравнивает короткие отпечатки адресов, а запросы идут через серверы Apple, которые скрывают ваш IP. Плюс собственные списки Ward с вредоносными сайтами — они проверяются прямо на Mac.",
                              isOn: $model.prefs.fraudWarnings)
                SettingToggle(title: "Отключить WebRTC",
                              detail: "WebRTC нужен для видеозвонков в браузере, но может раскрыть ваш настоящий IP-адрес даже через VPN. Включите, если пользуетесь VPN и не звоните из браузера.",
                              isOn: $model.prefs.blockWebRTC)
            } header: {
                Text("Защиты")
            } footer: {
                Text("Реклама и трекеры блокируются всегда — списками фильтров во вкладке «Списки». На отдельном сайте защиту можно выключить через щит в адресной строке.")
            }
            if !model.prefs.unprotectedSites.isEmpty {
                Section("Сайты без защиты") {
                    ForEach(model.prefs.unprotectedSites.sorted(), id: \.self) { site in
                        HStack {
                            Text(site)
                            Spacer()
                            Button("Включить защиту") { model.setProtection(true, site: site) }.buttonStyle(.borderless)
                        }
                    }
                }
            }
            if !model.prefs.noScriptSites.isEmpty {
                Section("Сайты без JavaScript") {
                    ForEach(model.prefs.noScriptSites.sorted(), id: \.self) { site in
                        HStack {
                            Text(site)
                            Spacer()
                            Button("Включить JavaScript") { model.setJavaScript(true, site: site) }.buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .formStyle(.grouped)
    }
}

struct ListsSettings: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Form {
            Section {
                ForEach(FilterList.all) { list in
                    Toggle(isOn: Binding(get: { model.prefs.enabledLists.contains(list.id) }, set: { on in
                        if on { model.prefs.enabledLists.insert(list.id) } else { model.prefs.enabledLists.remove(list.id) }
                    })) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(list.title)
                                Text(list.kind.title)
                                    .font(.caption2.weight(.semibold))
                                    .padding(.horizontal, 6).padding(.vertical, 1)
                                    .background(kindColor(list.kind).opacity(0.16), in: Capsule())
                                    .foregroundStyle(kindColor(list.kind))
                            }
                            Text(list.summary).font(.callout).foregroundStyle(.secondary)
                            if let date = FilterStorage.updated(list) {
                                Text("Обновлён \(Format.relative(date))").font(.caption).foregroundStyle(.tertiary)
                            }
                        }
                    }
                    .toggleStyle(.switch)
                }
            } header: {
                Text("Списки фильтров")
            } footer: {
                Text("Списки ведут сообщества EasyList, uBlock Origin, AdGuard, abuse.ch и другие — их проверяют и обновляют каждый день. Ward переводит их в правила движка WebKit: блокировка происходит до запроса и не тормозит страницы.")
            }
            Section("Состояние") {
                LabeledContent("Правила") { Text(phaseText) }
                if let r = model.blocker.report {
                    LabeledContent("Работает правил") { Text(Format.count(r.rules)) }
                    LabeledContent("Разобрано строк") { Text(Format.count(r.lines)) }
                    LabeledContent("Не подходят для WebKit") { Text(Format.count(r.skipped + r.invalidSelectors + r.dropped)) }
                        .help("Правила со скриптами и особым синтаксисом uBlock Origin, которых нет в WebKit. В основном это точечные исправления для отдельных сайтов.")
                    LabeledContent("Сборка заняла") { Text(String(format: "%.1f с", r.seconds)) }
                }
                Toggle("Обновлять списки автоматически", isOn: Binding(get: { model.prefs.autoUpdateLists }, set: { model.prefs.autoUpdateLists = $0 }))
                HStack {
                    Button(model.listsUpdating ? "Обновляю…" : "Обновить сейчас") { Task { await model.updateLists() } }
                        .disabled(model.listsUpdating)
                    if let message = model.listsMessage { Text(message).font(.callout).foregroundStyle(.secondary) }
                }
            }
        }
        .formStyle(.grouped)
    }

    private var phaseText: String {
        switch model.blocker.phase {
        case .idle: "не собраны"
        case .preparing(let text): text
        case .ready: "готовы"
        case .failed(let text): text
        }
    }

    private func kindColor(_ kind: TrackerKind) -> Color {
        switch kind {
        case .ads: .blue
        case .tracking: .purple
        case .malware: .red
        case .annoyance: .gray
        }
    }
}

struct DataSettings: View {
    @Environment(AppModel.self) private var model
    @State private var records: Int?
    @State private var message: String?

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                SettingToggle(title: "Удалять данные сайтов при выходе",
                              detail: "Cookie, хранилище и кэш стираются, когда вы закрываете Ward, — следующий запуск начинается с чистого листа. Сайты из списка «Помнить вход» не трогаются.",
                              isOn: $model.prefs.clearOnQuit)
                SettingToggle(title: "Вести историю",
                              detail: "Только на этом Mac — для подсказок в адресной строке. Частные окна историю не ведут никогда.",
                              isOn: $model.prefs.keepHistory)
                SettingToggle(title: "Очищать историю при выходе", detail: "Подсказки будут только из избранного.", isOn: $model.prefs.clearHistoryOnQuit)
                    .disabled(!model.prefs.keepHistory)
            } header: {
                Text("Данные")
            }
            Section("Помнить вход") {
                if model.prefs.rememberedSites.isEmpty {
                    Text("Пока нет сайтов. Включите «Помнить вход» в щите на сайте, где не хотите каждый раз входить заново.")
                        .font(.callout).foregroundStyle(.secondary)
                }
                ForEach(model.prefs.rememberedSites.sorted(), id: \.self) { site in
                    HStack {
                        Text(site)
                        Spacer()
                        Button("Убрать") { model.setRemembered(false, site: site) }.buttonStyle(.borderless)
                    }
                }
            }
            Section("Стереть сейчас") {
                HStack {
                    Button("Удалить данные всех сайтов") {
                        Task {
                            let n = await model.clearSiteData(keeping: model.prefs.rememberedSites)
                            message = "Удалено: \(n) \(Format.plural(n, "сайт", "сайта", "сайтов"))"
                            records = await model.siteDataCount()
                        }
                    }
                    Button("Очистить историю") {
                        model.clearHistory()
                        message = "История очищена"
                    }
                    Button("Сбросить счётчики") { model.resetStats() }
                }
                if let records { Text("Сейчас данные хранят \(records) \(Format.plural(records, "сайт", "сайта", "сайтов"))").font(.callout).foregroundStyle(.secondary) }
                if let message { Text(message).font(.callout).foregroundStyle(.green) }
            }
        }
        .formStyle(.grouped)
        .task { records = await model.siteDataCount() }
    }
}

struct SearchSettings: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                Picker("Поисковик", selection: $model.prefs.search) {
                    ForEach(SearchEngine.allCases) { engine in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(engine.title)
                                if !engine.isPrivate {
                                    Text("собирает данные").font(.caption2).foregroundStyle(.orange)
                                }
                            }
                            Text(engine.note).font(.callout).foregroundStyle(.secondary)
                        }
                        .tag(engine)
                    }
                }
                .pickerStyle(.radioGroup)
            } header: {
                Text("Поиск из адресной строки")
            } footer: {
                Text("Подсказки при вводе берутся только из вашей истории и избранного: Ward не отправляет поисковику каждую набранную букву.")
            }
        }
        .formStyle(.grouped)
    }
}

struct GeneralSettings: View {
    @Environment(AppModel.self) private var model
    @State private var isDefault = false

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Браузер по умолчанию")
                        Text(isDefault ? "Ссылки из других приложений открываются в Ward." : "Ссылки из почты и мессенджеров будут открываться в Ward.")
                            .font(.callout).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if isDefault {
                        Label("Да", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                    } else {
                        Button("Сделать") {
                            model.makeDefaultBrowser()
                            Task {
                                try? await Task.sleep(for: .seconds(2))
                                isDefault = model.isDefaultBrowser
                            }
                        }
                    }
                }
                SettingToggle(title: "Открывать вкладки прошлого сеанса", detail: "Ward запомнит открытые вкладки и вернёт их при следующем запуске.", isOn: $model.prefs.restoreTabs)
                SettingToggle(title: "Не запускать видео со звуком само",
                              detail: "Видео и звук начинают играть только после вашего нажатия. Действует на новые вкладки.",
                              isOn: $model.prefs.blockAutoplay)
                SettingToggle(title: "Инструменты разработчика",
                              detail: "Пункт «Проверить элемент» в меню страницы — для веб-разработчиков.",
                              isOn: $model.prefs.developerTools)
            }
            Section("О Ward") {
                Text("Ward работает на WebKit — движке Safari, встроенном в macOS. Своё в нём — блокировщик, защита от отпечатка, очистка ссылок и интерфейс. Никакой телеметрии: Ward ничего не отправляет о вас — ни разработчикам, ни кому-либо ещё. В сеть он сам обращается только за свежими списками фильтров.")
                    .font(.callout).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .onAppear { isDefault = model.isDefaultBrowser }
    }
}
