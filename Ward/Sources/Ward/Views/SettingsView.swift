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
            SitesSettings().tabItem { Label("Сайты", systemImage: "globe") }.tag(3)
            NetworkSettings().tabItem { Label("Сеть", systemImage: "network.badge.shield.half.filled") }.tag(4)
            SearchSettings().tabItem { Label("Поиск", systemImage: "magnifyingglass") }.tag(5)
            GeneralSettings().tabItem { Label("Основные", systemImage: "gearshape") }.tag(6)
        }
        .frame(width: 680, height: 660)
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

    struct SponsorCategory: Identifiable {
        let id: String
        let title: String
    }

    static let sponsorCategories: [SponsorCategory] = [
        .init(id: "sponsor", title: "Спонсорские вставки"), .init(id: "selfpromo", title: "Самореклама автора, мерч"),
        .init(id: "interaction", title: "Просьбы подписаться и поставить лайк"), .init(id: "exclusive_access", title: "Реклама доступа к ролику"),
        .init(id: "intro", title: "Заставка в начале"), .init(id: "outro", title: "Титры и концовка"),
        .init(id: "preview", title: "Анонс «в этом выпуске»"), .init(id: "filler", title: "Отступления не по теме"),
        .init(id: "music_offtopic", title: "Немузыкальная часть в клипах"),
    ]

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                SettingToggle(title: "Защита от отпечатка браузера",
                              detail: "Сайты узнают вас без cookie — по тому, как ваш Mac рисует картинки, звук и текст, по размерам элементов и голосам синтеза речи. Ward добавляет в эти ответы незаметный шум: для каждого сайта свой и новый при каждом запуске.",
                              isOn: $model.prefs.fingerprint)
                SettingToggle(title: "Часовой пояс — UTC",
                              detail: "Сайты не узнают ваш часовой пояс, а с ним — страну. Время на сайтах будет показываться по Гринвичу.",
                              isOn: $model.prefs.spoofTimezone)
                    .disabled(!model.prefs.fingerprint)
                SettingToggle(title: "Язык для сайтов — английский",
                              detail: "Русский язык сильно сужает круг, в котором вас ищут. Сайты будут открываться на английском; пункты системных меню Ward тоже станут английскими. Вступает в силу после перезапуска.",
                              isOn: $model.prefs.spoofLanguage)
                SettingToggle(title: "Очищать ссылки",
                              detail: "Убирает из адресов метки слежки (utm_source, fbclid, yclid и сотни других) и пропускает ссылки-переходники вроде google.com/url или vk.com/away.php.",
                              isOn: $model.prefs.cleanLinks)
                SettingToggle(title: "Отклонять cookie за вас",
                              detail: "В окнах согласия (OneTrust, Cookiebot, Didomi, Google и других) Ward сам нажимает «Отклонить всё». Сайт получает именно отказ, а не «согласие по умолчанию».",
                              isOn: $model.prefs.consentReject)
                SettingToggle(title: "Убирать назойливые плашки",
                              detail: "Окна и полосы поверх статьи — «Подпишитесь», «Отключите блокировщик», рассылки, «Скачайте приложение» — и затемнение страницы под ними. Ward прячет их и возвращает прокрутку. Окна, которые вы открыли сами, и меню сайтов не трогаются.",
                              isOn: $model.prefs.blockAnnoyances)
                SettingToggle(title: "Находить скрытые трекеры",
                              detail: "Некоторые счётчики прячутся под поддоменом самого сайта (metrics.site.ru). Ward узнаёт у DNS настоящее имя такого поддомена и блокирует его, если это трекер.",
                              isOn: $model.prefs.cnameDetection)
            } header: {
                Text("Слежка")
            } footer: {
                Text("Реклама и трекеры блокируются всегда — списками фильтров во вкладке «Списки», вместе со скриптлетами uBlock Origin.")
            }
            Section("Безопасность") {
                SettingToggle(title: "Только HTTPS",
                              detail: "Сначала пробует открыть сайт по защищённому соединению. Если сайт так не умеет, Ward предупредит. На странице без шифрования предупредит и о поле пароля.",
                              isOn: $model.prefs.httpsOnly)
                SettingToggle(title: "Предупреждать о подделках",
                              detail: "Адреса, похожие на банки и сервисы (sberbamk.ru, paypa1.com, gosuslugi-pay.ru), и домены с подменёнными буквами другого алфавита. Сайты, где вы уже бывали, не проверяются.",
                              isOn: $model.prefs.phishingWarnings)
                SettingToggle(title: "Предупреждать о мошеннических сайтах",
                              detail: "Сверка с базой Safe Browsing, как в Safari: Mac сравнивает короткие отпечатки адресов, а запросы идут через серверы Apple, которые скрывают ваш IP. Плюс собственные списки Ward с вредоносными сайтами — они проверяются прямо на Mac.",
                              isOn: $model.prefs.fraudWarnings)
                Picker(selection: $model.prefs.strictMode) {
                    Text("Выключен").tag(StrictMode.off)
                    Text("На незнакомых сайтах").tag(StrictMode.unfamiliar)
                    Text("На всех сайтах").tag(StrictMode.all)
                } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Строгий режим")
                        Text("Режим блокировки Apple (Lockdown Mode) для страниц: без JIT-компиляции JavaScript, WebGL, сложных шрифтов и ещё десятка возможностей, через которые атакуют браузеры. Сайты работают медленнее, некоторые выглядят проще. На отдельном сайте включается в щите.")
                            .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    }
                }
                SettingToggle(title: "Отключить WebRTC",
                              detail: "WebRTC нужен для видеозвонков в браузере, но может раскрыть ваш настоящий IP-адрес даже через VPN. Включите, если пользуетесь VPN и не звоните из браузера.",
                              isOn: $model.prefs.blockWebRTC)
            }
            Section {
                SettingToggle(title: "Пропускать вставки в роликах",
                              detail: "Ward узнаёт отрезки ролика по первым четырём знакам хеша его id — сервер не знает, какой ролик вы смотрите. Ничего не отправляется обратно.",
                              isOn: $model.prefs.sponsorBlock)
                ForEach(Self.sponsorCategories) { category in
                    Toggle(category.title, isOn: Binding(get: { model.prefs.sponsorCategories.contains(category.id) }, set: { on in
                        if on { model.prefs.sponsorCategories.insert(category.id) } else { model.prefs.sponsorCategories.remove(category.id) }
                    }))
                    .disabled(!model.prefs.sponsorBlock)
                    .padding(.leading, 12)
                }
            } header: {
                Text("YouTube: SponsorBlock")
            } footer: {
                Text("Отрезки размечают зрители в открытой базе SponsorBlock (sponsor.ajay.app, данные — CC BY-NC-SA 4.0). При пропуске появляется подсказка с кнопкой «Отменить».")
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
    @State private var confirmWipe = false
    @State private var newContainer = ""

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                SettingToggle(title: "Убирать данные закрытых сайтов",
                              detail: "Через 15 секунд после того, как закрыта последняя вкладка сайта, его cookie и хранилище удаляются — вместе с cookie трекеров, которые он успел поставить. Сайты из «Помнить вход» остаются.",
                              isOn: $model.prefs.cleanClosedSites)
                SettingToggle(title: "Удалять данные сайтов при выходе",
                              detail: "Cookie, хранилище и кэш стираются, когда вы закрываете Ward, — следующий запуск начинается с чистого листа. Сайты из «Помнить вход» не трогаются.",
                              isOn: $model.prefs.clearOnQuit)
                SettingToggle(title: "Вести историю",
                              detail: "Только на этом Mac, в зашифрованном виде (ключ — в Связке ключей). Для подсказок в адресной строке и окна «История». Частные окна и одноразовые вкладки историю не ведут.",
                              isOn: $model.prefs.keepHistory)
                SettingToggle(title: "Открывать историю по Touch ID", detail: "Окно «История» (⌘Y) попросит Touch ID или пароль Mac.", isOn: $model.prefs.protectHistory)
                    .disabled(!model.prefs.keepHistory)
                SettingToggle(title: "Очищать историю при выходе", detail: "Подсказки будут только из избранного.", isOn: $model.prefs.clearHistoryOnQuit)
                    .disabled(!model.prefs.keepHistory)
            } header: {
                Text("Данные")
            }
            Section {
                ForEach(model.containers) { c in
                    HStack {
                        Circle().fill(ContainerPalette.color(c.color)).frame(width: 10, height: 10)
                        Text(c.name)
                        Spacer()
                        let sites = model.prefs.siteContainers.filter { $0.value == c.id }.keys.sorted()
                        if !sites.isEmpty { Text(sites.prefix(3).joined(separator: ", ")).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                        Button("Удалить") { Task { await model.deleteContainer(c) } }.buttonStyle(.borderless)
                            .help("Удалить контейнер вместе со всеми его cookie и данными")
                    }
                }
                HStack {
                    TextField("Новый контейнер", text: $newContainer)
                    Button("Добавить") {
                        let name = newContainer.trimmingCharacters(in: .whitespaces)
                        if !name.isEmpty { model.addContainer(name: name) }
                        newContainer = ""
                    }
                    .disabled(newContainer.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } header: {
                Text("Контейнеры")
            } footer: {
                Text("У каждого контейнера свои cookie и данные: сайты из «Работы» не видят, что вы делали в «Покупках». Вкладку в контейнере открывает меню у кнопки «+»; сайт можно закрепить за контейнером в щите.")
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
                            message = "Удалено: \(n) \(Format.plural(n, "запись", "записи", "записей"))"
                            records = await model.siteDataCount()
                        }
                    }
                    Button("Очистить историю") {
                        model.clearHistory()
                        message = "История очищена"
                    }
                    Button("Сбросить счётчики") { model.resetStats() }
                }
                Button("Стереть всё…", role: .destructive) { confirmWipe = true }
                    .help("Закрыть все окна и удалить данные всех сайтов во всех контейнерах, историю и вкладки (⇧⌘⌫)")
                if let records { Text("Сейчас данные хранят \(records) \(Format.plural(records, "сайт", "сайта", "сайтов"))").font(.callout).foregroundStyle(.secondary) }
                if let message { Text(message).font(.callout).foregroundStyle(.green) }
            }
        }
        .formStyle(.grouped)
        .task { records = await model.siteDataCount() }
        .confirmationDialog("Стереть всё?", isPresented: $confirmWipe) {
            Button("Стереть всё", role: .destructive) { Task { await model.wipeEverything() } }
        } message: {
            Text("Закроются все окна, удалятся cookie и данные всех сайтов (и запомненных тоже), история и вкладки прошлого сеанса. Настройки и избранное останутся.")
        }
    }
}

/// Сайты с особыми настройками: защита, JavaScript, строгий режим, камера и микрофон, контейнер.
struct SitesSettings: View {
    @Environment(AppModel.self) private var model

    private var sites: [String] {
        let p = model.prefs
        var all = p.unprotectedSites.union(p.noScriptSites).union(p.strictSites).union(p.relaxedSites)
        all.formUnion(p.mediaDecisions.keys)
        all.formUnion(p.siteContainers.keys)
        all.formUnion(p.trustedLookalikes)
        return all.sorted()
    }

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                SettingToggle(title: "Запрещать камеру и микрофон без вопроса",
                              detail: "Сайты не смогут даже спросить. Разрешения, которые вы уже запомнили, продолжат действовать.",
                              isOn: $model.prefs.denyMediaByDefault)
            } header: {
                Text("Разрешения")
            } footer: {
                Text("Геолокация, уведомления и доступ к датчикам в Ward выключены всегда: WebKit в этом браузере их сайтам не даёт.")
            }
            Section("Сайты с особыми настройками") {
                if sites.isEmpty {
                    Text("Таких сайтов пока нет. Настройки отдельного сайта — в щите в адресной строке.").font(.callout).foregroundStyle(.secondary)
                }
                ForEach(sites, id: \.self) { site in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            SiteTile(host: site, size: 18)
                            Text(site).font(.headline)
                            Spacer()
                            Button("Сбросить") { reset(site) }.buttonStyle(.borderless)
                        }
                        HStack(spacing: 8) {
                            if model.prefs.unprotectedSites.contains(site) { chip("Защита выключена", .orange) }
                            if model.prefs.noScriptSites.contains(site) { chip("Без JavaScript", .gray) }
                            if model.prefs.strictSites.contains(site) { chip("Строгий режим", .blue) }
                            if model.prefs.relaxedSites.contains(site) { chip("Без строгого режима", .gray) }
                            if let media = model.prefs.mediaDecisions[site] { chip(media ? "Камера и микрофон: да" : "Камера и микрофон: нет", media ? .green : .red) }
                            if let c = model.container(forSite: site) { chip("Контейнер «\(c.name)»", ContainerPalette.color(c.color)) }
                            if model.prefs.trustedLookalikes.contains(site) { chip("Не подделка", .gray) }
                        }
                    }
                }
            }
        }
        .formStyle(.grouped)
    }

    private func chip(_ text: String, _ color: Color) -> some View {
        Text(text).font(.caption.weight(.medium)).padding(.horizontal, 8).padding(.vertical, 3)
            .background(color.opacity(0.15), in: Capsule()).foregroundStyle(color)
    }

    private func reset(_ site: String) {
        model.prefs.unprotectedSites.remove(site)
        model.prefs.noScriptSites.remove(site)
        model.prefs.strictSites.remove(site)
        model.prefs.relaxedSites.remove(site)
        model.prefs.mediaDecisions[site] = nil
        model.prefs.siteContainers[site] = nil
        model.prefs.trustedLookalikes.remove(site)
    }
}

/// Прокси для окон: например, через Veil.
struct NetworkSettings: View {
    @Environment(AppModel.self) private var model
    @State private var status: String?
    @State private var checking = false

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                Picker("Через прокси идут", selection: $model.prefs.proxyMode) {
                    Text("Никакие окна").tag(ProxyMode.off)
                    Text("Все окна").tag(ProxyMode.all)
                    Text("Только частные окна").tag(ProxyMode.privateOnly)
                }
                HStack {
                    TextField("Адрес", text: $model.prefs.proxyHost).frame(maxWidth: 200)
                    TextField("Порт", value: $model.prefs.proxyPort, format: .number.grouping(.never)).frame(maxWidth: 90)
                    Button(checking ? "Проверяю…" : "Проверить") {
                        checking = true
                        Task {
                            let ok = await NetworkPrivacy.check(host: model.prefs.proxyHost, port: model.prefs.proxyPort)
                            status = ok ? "Прокси отвечает" : "Прокси не отвечает — запустите Veil или другой прокси"
                            checking = false
                        }
                    }
                    .disabled(checking)
                }
                if let status { Text(status).font(.callout).foregroundStyle(status.hasPrefix("Прокси отвечает") ? .green : .orange) }
            } header: {
                Text("Прокси SOCKS5")
            } footer: {
                Text("Страницы, их запросы и поиск адресов сайтов (DNS) идут через прокси: сайты и провайдер не видят, куда вы ходите. Если прокси выключен, окна не откроют сайты напрямую — Ward не уходит в обход. Veil по умолчанию слушает 127.0.0.1:10808.")
            }
        }
        .formStyle(.grouped)
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
