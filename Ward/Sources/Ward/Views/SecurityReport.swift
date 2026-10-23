import AppKit
import SwiftUI

/// Отчёт по одной плитке панели безопасности: что произошло, кто это делал, зачем, какими способами
/// и как Ward это остановил. Открывается поверх панели, как Quick Look, закрывается пробелом, Esc или щелчком мимо.
enum DashboardReport: String, Identifiable, CaseIterable {
    case overview, tracking, ads, fingerprint, hidden, danger, cloaked, consent, isolation, corporation, mask, annoyance, links, https, cleanup, sponsor, pages

    var id: String { rawValue }

    static func `for`(_ kind: SecurityEvent.Kind) -> DashboardReport {
        switch kind {
        case .tracker: .tracking
        case .ad: .ads
        case .danger, .phishing: .danger
        case .cloaked: .cloaked
        case .fingerprint: .fingerprint
        case .hidden: .hidden
        case .link: .links
        case .https: .https
        case .consent: .consent
        case .sponsor: .sponsor
        case .cleanup: .cleanup
        case .annoyance: .annoyance
        case .isolation: .isolation
        case .corporation: .corporation
        case .mask: .mask
        }
    }

    /// Какие события журнала относятся к отчёту.
    var kinds: [SecurityEvent.Kind] {
        switch self {
        case .overview, .pages: SecurityEvent.Kind.allCases
        case .tracking: [.tracker]
        case .ads: [.ad]
        case .fingerprint: [.fingerprint]
        case .hidden: [.hidden]
        case .danger: [.danger, .phishing]
        case .cloaked: [.cloaked]
        case .consent: [.consent]
        case .isolation: [.isolation]
        case .corporation: [.corporation]
        case .mask: [.mask]
        case .annoyance: [.annoyance]
        case .links: [.link]
        case .https: [.https]
        case .cleanup: [.cleanup]
        case .sponsor: [.sponsor]
        }
    }

    var icon: String {
        switch self {
        case .overview: "shield.lefthalf.filled"
        case .tracking: "eye.slash.fill"
        case .ads: "rectangle.slash.fill"
        case .fingerprint: "touchid"
        case .hidden: "eye.trianglebadge.exclamationmark"
        case .danger: "exclamationmark.octagon.fill"
        case .cloaked: "theatermasks.fill"
        case .consent: "hand.raised.fill"
        case .isolation: "cube.transparent.fill"
        case .corporation: "building.2.crop.circle.fill"
        case .mask: "theatermasks.fill"
        case .annoyance: "rectangle.on.rectangle.slash.fill"
        case .links: "scissors"
        case .https: "lock.fill"
        case .cleanup: "trash.fill"
        case .sponsor: "forward.end.fill"
        case .pages: "doc.text.magnifyingglass"
        }
    }

    var tint: Color {
        switch self {
        case .overview: .green
        case .tracking: .purple
        case .ads: .blue
        case .fingerprint: .pink
        case .hidden: .cyan
        case .danger: .red
        case .cloaked: .indigo
        case .consent: .orange
        case .isolation: .teal
        case .corporation: .orange
        case .mask: .purple
        case .annoyance, .links: .mint
        case .https: .green
        case .cleanup: .gray
        case .sponsor: .teal
        case .pages: .brown
        }
    }

    var title: String {
        switch self {
        case .overview: "Всё, что не дошло до вас"
        case .tracking: "Трекеры слежки"
        case .ads: "Реклама"
        case .fingerprint: "Попытки узнать вас без cookie"
        case .hidden: "Скрытые элементы страниц"
        case .danger: "Опасные адреса и подделки"
        case .cloaked: "Скрытые трекеры (CNAME)"
        case .consent: "Окна согласия на cookie"
        case .isolation: "Изолятор"
        case .corporation: "Корпорации в своих банках"
        case .mask: "Маска"
        case .annoyance: "Плашки поверх статей"
        case .links: "Метки слежки в ссылках"
        case .https: "Переходы на HTTPS"
        case .cleanup: "Уборка за закрытыми сайтами"
        case .sponsor: "Вставки в роликах"
        case .pages: "Страницы под защитой"
        }
    }

    /// Что это и зачем это делают.
    var what: String {
        switch self {
        case .overview:
            "Сводка по всем видам защиты: сколько запросов слежки и рекламы не ушло из браузера, сколько опасных адресов остановлено и чем ещё занимался Ward."
        case .tracking:
            "Скрипты аналитики, пиксели и маяки, которые записывают, какие страницы вы открываете, что нажимаете и сколько читаете, — и связывают эти записи между сайтами."
        case .ads:
            "Рекламные сети и аукционы: на одну страницу приходятся десятки запросов к серверам, которые решают, какую рекламу вам показать, и заодно узнают, где вы были."
        case .fingerprint:
            "Отпечаток браузера — это способ узнать вас без cookie: страница рисует невидимую картинку, обрабатывает звук, спрашивает видеокарту и шрифты. У каждого Mac результат чуть свой, и он не стирается вместе с cookie."
        case .hidden:
            "Рекламные блоки, баннеры, кнопки «поделиться» соцсетей и врезки «подпишитесь», которые остались бы на странице пустыми рамками или отвлекали бы от текста."
        case .danger:
            "Адреса из списков вредоносных и мошеннических сайтов и сайты-подделки под банки и сервисы с похожими именами (лишняя буква, буквы другого алфавита)."
        case .cloaked:
            "Трекеры, которые прячутся под поддоменом самого сайта (metrics.site.ru): для браузера это «свой» адрес, а на деле в DNS он ведёт к серверу сторонней компании слежки."
        case .consent:
            "Окна «Мы используем cookie». Сайту нужен ваш ответ: многие считают, что без ответа вы согласны на слежку."
        case .isolation:
            "Сайты в изоляторе живут в своей «банке» с вымышленным устройством. Здесь — что они пытались получить сверх нужного для работы."
        case .corporation:
            "Google, Яндекс, VK, Microsoft, Meta, Amazon, Apple, ByteDance, X, OpenAI, Anthropic и Сбер живут каждая в своей банке: вход во все сервисы одной корпорации работает, но её cookie не видны ни другим корпорациям, ни обычным сайтам. Здесь — сколько раз страницы корпорации пытались обратиться к другой корпорации."
        case .mask:
            "«Маска»: каждый сайт видит отдельного вымышленного человека — своё хранилище, своё устройство, огрублённое время. По отпечатку, cookie и ритму печати ваши визиты на разные сайты не связать."
        case .annoyance:
            "Плашки и окна поверх статьи: «Подпишитесь», «Отключите блокировщик», формы рассылки, затемнение страницы и запрет прокрутки."
        case .links:
            "Метки в адресах ссылок (utm_source, fbclid, gclid, yclid…) и ссылки-переходники: по ним сайт, куда вы пришли, узнаёт, откуда и через какую рекламу."
        case .https:
            "Ссылки, которые вели на незашифрованную версию сайта. Без HTTPS провайдер и любая сеть по пути видят страницу и могут её подменить."
        case .cleanup:
            "Cookie и хранилище сайтов живут месяцами и позволяют узнать вас при следующем визите. Ward удаляет их через 15 секунд после закрытия последней вкладки сайта."
        case .sponsor:
            "Спонсорские вставки и призывы подписаться внутри роликов YouTube — по данным открытой базы SponsorBlock."
        case .pages:
            "Страницы, открытые с включённой защитой: на каждой работали списки фильтров, защита отпечатка и всё остальное."
        }
    }

    /// Зачем это делают.
    var why: String {
        switch self {
        case .overview: "Почти всё остановленное — это сбор данных о вас ради рекламы, продажи брокерам данных и скоринга."
        case .tracking: "Профиль интересов, доходов, здоровья и взглядов — для таргетированной рекламы, продажи брокерам данных, кредитного и страхового скоринга."
        case .ads: "Показать рекламу, измерить, сработала ли она, и пополнить историю ваших посещений у рекламной сети."
        case .fingerprint: "Узнавать вас там, где cookie уже нет: после очистки данных, в частном окне, на других сайтах той же сети."
        case .hidden: "Реклама и вовлечение: клики, подписки, рост аудитории соцсетей."
        case .danger: "Заразить Mac, украсть пароли, коды из СМС и данные карт."
        case .cloaked: "Обойти блокировщики и ограничения браузеров на сторонние cookie: «свой» поддомен получает полноценные cookie сайта."
        case .consent: "Получить юридическое «согласие» на передачу данных партнёрам — иногда сотням компаний."
        case .isolation: "Банкам, маркетплейсам и соцсетям нужен «портрет устройства» для антифрода и рекламы — и они берут всё, что браузер отдаёт."
        case .corporation: "Обмен данными между корпорациями: рекламные пиксели, «вход через…», аналитика и синхронизация cookie позволяют им сводить ваши профили."
        case .mask: "Брокеры данных и рекламные сети склеивают визиты на разные сайты по отпечатку, cookie, IP и поведению — и получают ваш полный портрет."
        case .annoyance: "Подписки, регистрации, рассылки — и отключение блокировщиков, чтобы снова показывать рекламу."
        case .links: "Атрибуция рекламы и связывание ваших переходов между сайтами."
        case .https: "Сайт или ссылка устарели — но незашифрованный трафик удобно читать и подменять."
        case .cleanup: "Чем дольше живут cookie, тем длиннее история ваших визитов у сайта и его партнёров."
        case .sponsor: "Реклама, встроенная прямо в видео, — её не видят обычные блокировщики."
        case .pages: "—"
        }
    }

    /// Какими способами.
    var methods: [String] {
        switch self {
        case .overview: ["Запросы к серверам слежки и рекламы", "Отпечаток браузера", "Опасные и поддельные сайты", "Окна согласия, плашки, метки в ссылках"]
        case .tracking: ["Скрипты аналитики (Google Analytics, Яндекс Метрика, Top100 и др.)", "Пиксели 1×1 и маяки sendBeacon", "Запись сеанса: движения мыши, нажатия, прокрутка", "Сторонние cookie и идентификаторы в адресах"]
        case .ads: ["Рекламные скрипты и iframe", "Аукционы в реальном времени (header bidding)", "Пиксели ретаргетинга", "Реклама, встроенная в сам сайт"]
        case .fingerprint: FingerprintTechnique.allCases.map { "\($0.title) — \($0.detail.lowercased())" }
        case .hidden: ["Рекламные блоки и пустые рамки", "Кнопки и виджеты соцсетей", "Врезки «подпишитесь» и «читайте также»"]
        case .danger: ["Домены из баз вредоносного (URLhaus, uBlock Origin Badware)", "Имена с подменёнными буквами (кириллица вместо латиницы, «rn» вместо «m»)", "Опечатки под оригинал и приставки вроде «-login», «-pay»"]
        case .cloaked: ["Поддомен сайта с CNAME-записью на сервер слежки", "Cookie первой стороны вместо сторонних"]
        case .consent: ["Окно согласия поверх сайта", "«Согласие по умолчанию», если не ответить"]
        case .isolation: Isolation.Attempt.allCases.map { "\($0.title) → \($0.answer)" }
        case .corporation: ["Пиксели и скрипты одной корпорации на страницах другой", "Синхронизация cookie между рекламными сетями", "Виджеты «войти через», «поделиться», встроенные видео"]
        case .mask: ["Общие cookie и хранилища между сайтами", "Отпечаток устройства", "Точное время событий (ритм печати)", "Общий IP-адрес (если прокси без отдельных цепочек)"]
        case .annoyance: ["Нижние и верхние плашки «Подпишитесь»", "Окна «Отключите блокировщик»", "Формы рассылки", "Затемнение страницы и запрет прокрутки"]
        case .links: ["utm_*, fbclid, gclid, yclid, _openstat и сотни других меток", "Переходники: google.com/url, vk.com/away.php и подобные"]
        case .https: ["Ссылки на http://", "Устаревшие закладки и редиректы"]
        case .cleanup: ["Cookie сайта и его партнёров", "localStorage, IndexedDB, кэш"]
        case .sponsor: ["Спонсорские вставки", "Самореклама и «подпишитесь на канал»"]
        case .pages: ["—"]
        }
    }

    /// Как Ward это нейтрализовал.
    var neutralized: [String] {
        switch self {
        case .overview: ["Запрос к серверу слежки не уходит из браузера — сервер о вас вообще не узнаёт", "Отпечаток подменяется шумом, свой для каждого сайта", "Опасные сайты останавливаются до первого запроса"]
        case .tracking: ["Запрос не уходит из браузера: правила EasyPrivacy, uBlock Origin и AdGuard сверяются до соединения", "Метки слежки вырезаются из ссылок до перехода", "Cookie сайтов удаляются после закрытия вкладки"]
        case .ads: ["Рекламные запросы не уходят из браузера — страницы легче и быстрее", "Пустые рекламные места скрываются", "Скриптлеты uBlock Origin отключают рекламу, встроенную в сам сайт"]
        case .fingerprint: ["В каждый ответ добавляется незаметный шум: для каждого сайта свой, новый при каждом запуске", "Отпечатки разных сайтов и разных сеансов не сходятся — связать их нельзя", "В изоляторе — вымышленное устройство, постоянное для сайта"]
        case .hidden: ["Косметические фильтры прячут элементы до отрисовки", "Процедурные правила (:has-text, :upward) находят рекламу по тексту"]
        case .danger: ["Адрес остановлен до первого запроса — сайт не успел ничего загрузить", "Экран-предупреждение объясняет, что это и куда идти дальше"]
        case .cloaked: ["Ward спросил у DNS настоящее имя поддомена и блокирует его дальше", "Cookie такого поддомена не уходят"]
        case .consent: ["Ward сам нажал «Отклонить всё» — сайт получил именно отказ"]
        case .corporation: ["Своя банка у каждой корпорации: её cookie не видны никому", "Запрос к другой корпорации из банки не уходит — правила WebKit останавливают его до соединения", "Шрифты и капча — исключение: без них не работают формы входа, а данных о вас в них нет"]
        case .mask: ["Своё хранилище у каждого сайта — общих cookie нет", "Своё вымышленное устройство у каждого сайта", "Время событий огрублено до 8 мс", "С Tor — свой выход в сеть у каждого сайта", "«Сменить маску» — и все сайты видят новых людей"]
        case .isolation: ["Отказ — так же, как если бы вы нажали «Не разрешать»", "Вместо вашего устройства — легенда: свой постоянный шум отпечатка, огрублённое время", "Своя «банка» данных: других сайтов сайт не видит"]
        case .annoyance: ["Плашка спрятана, затемнение снято, прокрутка возвращена", "Шапки с меню и окна, которые вы открыли сами, не трогаются"]
        case .links: ["Метки вырезаны, переходник пропущен — сайт-источник не узнал, куда вы ушли"]
        case .https: ["Сайт открыт по защищённому соединению вместо незашифрованного"]
        case .cleanup: ["Данные удалены через 15 секунд после закрытия последней вкладки сайта", "Сайты «Помнить вход» не трогаются"]
        case .sponsor: ["Отрезок пропущен; Ward спрашивает базу по 4 знакам хеша id ролика — сервер не знает, какой ролик вы смотрите"]
        case .pages: ["На каждой странице работали все включённые защиты"]
        }
    }

    func value(_ stats: Stats, model: AppModel) -> Int {
        switch self {
        case .overview: stats.total
        case .tracking: stats.byKind[TrackerKind.tracking.rawValue] ?? 0
        case .ads: stats.byKind[TrackerKind.ads.rawValue] ?? 0
        case .fingerprint: stats.fingerprintPages
        case .hidden: stats.hiddenElements
        case .danger: stats.dangerBlocked + stats.phishingBlocked + (stats.byKind[TrackerKind.malware.rawValue] ?? 0)
        case .cloaked: stats.cloaked
        case .consent: stats.consentsRejected
        case .isolation: model.isolationAttemptsTotal
        case .corporation: stats.corporationBlocks.values.reduce(0, +)
        case .mask: stats.masksIssued
        case .annoyance: stats.annoyancesHidden
        case .links: stats.cleanedLinks
        case .https: stats.upgraded
        case .cleanup: stats.sitesCleaned
        case .sponsor: Int(stats.sponsorSeconds / 60)
        case .pages: stats.pages
        }
    }

    /// «Кто»: из счётчиков, если они есть для этого вида.
    func who(_ stats: Stats) -> [(String, Int)]? {
        switch self {
        case .overview, .tracking, .ads: Array(stats.byCompany.sorted { $0.value > $1.value }.prefix(10))
        case .fingerprint: Array(stats.fingerprintSites.sorted { $0.value > $1.value }.prefix(10))
        case .isolation: Array(stats.isolation.mapValues { $0.values.reduce(0, +) }.sorted { $0.value > $1.value }.prefix(10))
        case .corporation: Array(stats.corporationBlocks.map { (Jars.corporation(id: $0.key)?.name ?? $0.key, $0.value) }.sorted { $0.1 > $1.1 }.prefix(12))
        default: nil
        }
    }
}

/// Открыть отчёт из любой плитки панели.
extension EnvironmentValues {
    @Entry var openReport: @MainActor @Sendable (DashboardReport) -> Void = { _ in }
}

/// Плитка, которая открывает отчёт: щелчок, курсор-рука, лёгкое нажатие.
struct ReportLink: ViewModifier {
    @Environment(\.openReport) private var openReport
    let report: DashboardReport

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture { openReport(report) }
            .onHover { inside in if inside { NSCursor.pointingHand.push() } else { NSCursor.pop() } }
            .help("Подробный отчёт")
            .accessibilityAddTraits(.isButton)
    }
}

extension View {
    func opensReport(_ report: DashboardReport) -> some View { modifier(ReportLink(report: report)) }
}

/// Окно отчёта поверх панели, в духе Quick Look.
struct ReportOverlay: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    let report: DashboardReport
    let close: () -> Void
    @State private var entries: [LogEntry] = []
    @State private var loaded = false
    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            Color.black.opacity(0.28)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: close)
            card
                .frame(maxWidth: 820)
                .padding(40)
        }
        .focusable()
        .focusEffectDisabled()
        .focused($focused)
        .onKeyPress(.space) { close(); return .handled }
        .onKeyPress(.escape) { close(); return .handled }
        .onAppear { focused = true }
        .task(id: report) {
            let kinds = Set(report.kinds.map(\.rawValue))
            let all = await Task.detached(priority: .userInitiated) { SecurityLog.shared.entries(days: 30, limit: 20000) }.value
            entries = all.filter { kinds.contains($0.kind) }
            loaded = true
        }
    }

    private var card: some View {
        let stats = model.stats
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                Image(systemName: report.icon)
                    .font(.system(size: 22, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(report.tint.gradient, in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 2) {
                    Text(report.title).font(.title2.weight(.semibold))
                    Text("\(Format.count(report.value(stats, model: model))) за всё время · \(Format.count(entries.count)) в журнале за 30 дней")
                        .font(.callout).foregroundStyle(.secondary)
                }
                Spacer()
                Button(action: close) {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).frame(width: 28, height: 28)
                }
                .buttonStyle(.borderless)
                .background(Circle().fill(Color.primary.opacity(0.08)))
                .keyboardShortcut(.cancelAction)
                .help("Закрыть (пробел, Esc)")
            }
            .padding(22)
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    section("Что произошло", icon: "info.circle") { Text(report.what) }
                    if report != .pages { section("Зачем", icon: "questionmark.circle") { Text(report.why) } }
                    whoSection(stats)
                    section("Какими способами", icon: "wrench.and.screwdriver") { bullets(report.methods, color: report.tint) }
                    section("Как Ward это нейтрализовал", icon: "checkmark.shield") { bullets(report.neutralized, color: .green) }
                    eventsSection
                }
                .padding(22)
            }
            .frame(maxHeight: 560)
        }
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 26))
        .shadow(color: .black.opacity(0.25), radius: 30, y: 12)
    }

    @ViewBuilder
    private func whoSection(_ stats: Stats) -> some View {
        let list = report.who(stats) ?? derivedWho
        if !list.isEmpty {
            section(report == .fingerprint || report == .isolation ? "Какие сайты" : "Кто", icon: "person.2") {
                let top = Double(max(1, list.map(\.1).max() ?? 1))
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(list, id: \.0) { name, n in
                        HStack(spacing: 10) {
                            Text(name).lineLimit(1).frame(width: 220, alignment: .leading)
                            Capsule().fill(report.tint.opacity(0.15)).frame(height: 7)
                                .overlay(alignment: .leading) {
                                    GeometryReader { g in Capsule().fill(report.tint.gradient).frame(width: g.size.width * Double(n) / top) }
                                }
                            Text(Format.count(n)).monospacedDigit().foregroundStyle(.secondary).frame(width: 54, alignment: .trailing)
                        }
                        .font(.callout)
                    }
                }
            }
        }
    }

    /// «Кто» по журналу: первое слово подробностей — обычно сайт или адрес.
    private var derivedWho: [(String, Int)] {
        var counts: [String: Int] = [:]
        for e in entries {
            let head = e.detail.components(separatedBy: " · ").first?.components(separatedBy: " на ").first?
                .trimmingCharacters(in: .whitespaces) ?? ""
            guard !head.isEmpty, report != .sponsor, report != .cleanup else { continue }
            counts[head, default: 0] += 1
        }
        return Array(counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }.prefix(10))
    }

    private var eventsSection: some View {
        section("Последние события", icon: "list.bullet.rectangle") {
            if !loaded {
                ProgressView().controlSize(.small)
            } else if entries.isEmpty {
                Text(model.prefs.securityLog ? "В журнале за 30 дней событий этого вида нет." : "Журнал выключен — включите его на панели безопасности, чтобы видеть здесь каждое событие.")
                    .foregroundStyle(.secondary)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(entries.prefix(40)) { e in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(e.date.formatted(date: .abbreviated, time: .shortened)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                                .frame(width: 130, alignment: .leading)
                            Text(e.title).lineLimit(1)
                            Text(e.detail).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
                        }
                        .font(.callout)
                    }
                    Button("Весь журнал…") { openWindow(id: "security-log") }
                        .buttonStyle(.link)
                        .padding(.top, 6)
                }
            }
        }
    }

    private func section<C: View>(_ title: String, icon: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon).font(.headline)
            content().font(.body).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func bullets(_ items: [String], color: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            ForEach(items, id: \.self) { item in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Circle().fill(color).frame(width: 6, height: 6)
                    Text(item)
                }
            }
        }
    }
}
