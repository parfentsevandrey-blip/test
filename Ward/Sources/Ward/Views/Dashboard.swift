import Charts
import SwiftUI

/// Панель безопасности: всё, что Ward сделал для вас, — в цифрах, графиках и живой ленте.
/// Цифры настоящие: счётчики копятся с первого запуска и хранятся только на этом Mac.
struct SecurityDashboard: View {
    @Environment(AppModel.self) private var model
    @State private var confirmReset = false
    @State private var report: DashboardReport?

    private let tiles = [GridItem(.adaptive(minimum: 190, maximum: 320), spacing: 14)]

    var body: some View {
        let stats = model.stats
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                DashboardHero(stats: stats)
                    .opensReport(.overview)
                LazyVGrid(columns: tiles, spacing: 14) {
                    MetricTile(report: .tracking, icon: "eye.slash.fill", tint: .purple, value: stats.byKind[TrackerKind.tracking.rawValue] ?? 0,
                               title: "трекеров слежки", caption: "Счётчики и аналитика не узнали, что вы делали")
                    MetricTile(report: .ads, icon: "rectangle.slash.fill", tint: .blue, value: stats.byKind[TrackerKind.ads.rawValue] ?? 0,
                               title: "рекламных запросов", caption: "Не загрузились — страницы легче и быстрее")
                    MetricTile(report: .fingerprint, icon: "touchid", tint: .pink, value: stats.fingerprintPages,
                               title: "попыток узнать вас", caption: "Страницы пытались снять отпечаток браузера — получили подделку")
                    MetricTile(report: .hidden, icon: "eye.trianglebadge.exclamationmark", tint: .cyan, value: stats.hiddenElements,
                               title: "элементов скрыто", caption: "Рекламные блоки, баннеры, кнопки «поделиться» и «подпишитесь»")
                    MetricTile(report: .danger, icon: "exclamationmark.octagon.fill", tint: .red, value: stats.dangerBlocked + stats.phishingBlocked + (stats.byKind[TrackerKind.malware.rawValue] ?? 0),
                               title: "опасных адресов", caption: "Вредоносные сайты и подделки под банки — до первого запроса")
                    MetricTile(report: .cloaked, icon: "theatermasks.fill", tint: .indigo, value: stats.cloaked,
                               title: "скрытых трекеров", caption: "Притворялись частью сайта (CNAME), но Ward их раскрыл")
                    MetricTile(report: .consent, icon: "hand.raised.fill", tint: .orange, value: stats.consentsRejected,
                               title: "отказов от cookie", caption: "Ward сам нажал «Отклонить» в окнах согласия")
                    MetricTile(report: .annoyance, icon: "rectangle.on.rectangle.slash.fill", tint: .mint, value: stats.annoyancesHidden,
                               title: "плашек убрано", caption: "«Подпишитесь», «Отключите блокировщик», рассылки — поверх статей")
                    MetricTile(report: .isolation, icon: "cube.transparent.fill", tint: .teal, value: model.isolationAttemptsTotal,
                               title: "отказов в изоляторе", caption: "Геопозиция, камера, буфер обмена, отпечаток — запертые сайты получили отказ или подделку")
                    MetricTile(report: .corporation, icon: "building.2.crop.circle.fill", tint: .orange, value: stats.corporationBlocks.values.reduce(0, +),
                               title: "попыток корпораций", caption: "Обратиться из своей банки к другой корпорации — запрос не ушёл")
                    MetricTile(report: .mask, icon: "theatermasks.fill", tint: .purple, value: stats.masksIssued,
                               title: "вымышленных личностей", caption: model.prefs.maskMode ? "«Маска»: каждый сайт видит отдельного человека" : "Включите «Маску» — и каждый сайт увидит своего человека")
                    MetricTile(report: .links, icon: "scissors", tint: .mint, value: stats.cleanedLinks,
                               title: "ссылок очищено", caption: "Метки utm, fbclid, gclid убраны до перехода")
                    MetricTile(report: .bounce, icon: "arrow.triangle.turn.up.right.diamond.fill", tint: .indigo, value: stats.bounces,
                               title: "отскоков через трекеры", caption: "Вас перебрасывали через сайт-прокладку — его данные удалены")
                    MetricTile(report: .lan, icon: "wifi.router.fill", tint: .red, value: stats.lanBlocked,
                               title: "попыток в домашнюю сеть", caption: "Сайты из интернета стучались к роутеру, принтеру, сервисам на Mac")
                    MetricTile(report: .traces, icon: "eye.trianglebadge.exclamationmark.fill", tint: .cyan, value: stats.referrersTrimmed + stats.cookiesCapped,
                               title: "следов урезано", caption: "Адрес, откуда пришли, — без страницы; «вечные» cookie — на неделю")
                    MetricTile(report: .storage, icon: "hourglass", tint: .brown, value: stats.storagePurged,
                               title: "просроченных хранилищ", caption: "Сайты, где вы неделю ничего не делали, забыли свои идентификаторы")
                    MetricTile(report: .hardening, icon: "cpu.fill", tint: .blue, value: stats.noJITPages,
                               title: "страниц без JIT", caption: Hardening.available ? "Незнакомые сайты и банки — без самого атакуемого места браузера" : "Нужна macOS 26.4 или новее")
                    MetricTile(report: .https, icon: "lock.fill", tint: .green, value: stats.upgraded,
                               title: "переходов на HTTPS", caption: "Ссылка вела на незащищённую версию сайта")
                    MetricTile(report: .cleanup, icon: "trash.fill", tint: .gray, value: stats.sitesCleaned,
                               title: "сайтов забыто", caption: "Их cookie и данные убраны после закрытия вкладки")
                    MetricTile(report: .sponsor, icon: "forward.end.fill", tint: .teal, value: Int(stats.sponsorSeconds / 60),
                               title: "минут без вставок", caption: "SponsorBlock пропустил рекламу внутри роликов")
                    MetricTile(report: .pages, icon: "doc.text.magnifyingglass", tint: .brown, value: stats.pages,
                               title: "страниц под защитой", caption: "Открыто с включённой защитой Ward")
                }
                DashboardCard(title: "Остановлено за 30 дней", icon: "chart.bar.xaxis") {
                    DailyChart(stats: stats)
                }
                HStack(alignment: .top, spacing: 14) {
                    DashboardCard(title: "Кто следил больше всех", icon: "building.2") {
                        CompanyBars(stats: stats)
                    }
                    .opensReport(.tracking)
                    DashboardCard(title: "Что остановлено", icon: "chart.pie") {
                        KindDonut(stats: stats)
                    }
                    .opensReport(.overview)
                    .frame(maxWidth: 340)
                }
                HStack(alignment: .top, spacing: 14) {
                    DashboardCard(title: "Как вас пытались узнать", icon: "touchid") {
                        FingerprintBreakdown(stats: stats)
                    }
                    .opensReport(.fingerprint)
                    DashboardCard(title: "Прямо сейчас", icon: "dot.radiowaves.left.and.right") {
                        LiveFeed(events: model.recent)
                    }
                }
                DashboardCard(title: "Правдоподобие вымышленного устройства", icon: "checkmark.seal.fill") {
                    CoherenceCard()
                }
                DashboardCard(title: "Изолятор", icon: "cube.transparent.fill") {
                    IsolationBreakdown(stats: stats)
                }
                .opensReport(.isolation)
                DashboardCard(title: "Журнал безопасности", icon: "list.bullet.rectangle.portrait") {
                    SecurityLogCard()
                }
                ProtectionLevel()
                HStack {
                    Label("Статистика хранится только на этом Mac и никуда не отправляется.", systemImage: "lock.laptopcomputer")
                        .font(.callout).foregroundStyle(.secondary)
                    Spacer()
                    Button("Сбросить статистику…") { confirmReset = true }
                }
            }
            .padding(26)
            .frame(maxWidth: 1180)
            .frame(maxWidth: .infinity)
        }
        .defaultScrollAnchor(DemoUI.dashboardBottom ? .bottom : .top)
        .background(DashboardBackground())
        .overlay {
            if let report {
                ReportOverlay(report: report) { withAnimation(.spring(duration: 0.35, bounce: 0.15)) { self.report = nil } }
                    .transition(.asymmetric(insertion: .scale(scale: 0.92).combined(with: .opacity), removal: .scale(scale: 0.96).combined(with: .opacity)))
            }
        }
        .environment(\.openReport) { r in withAnimation(.spring(duration: 0.4, bounce: 0.2)) { report = r } }
        .task {
            if let demo = DemoUI.dashboardReport { try? await Task.sleep(for: .seconds(2)); report = demo }
        }
        .navigationTitle("Безопасность")
        .confirmationDialog("Сбросить всю статистику?", isPresented: $confirmReset) {
            Button("Сбросить", role: .destructive) { model.resetStats() }
        } message: {
            Text("Счётчики начнутся с нуля. Защита от этого не изменится.")
        }
    }
}

// MARK: - Шапка

struct DashboardHero: View {
    let stats: Stats

    var body: some View {
        HStack(spacing: 28) {
            ShieldPulse(tint: .green)
                .frame(width: 190, height: 190)
            VStack(alignment: .leading, spacing: 8) {
                Text("Ward защищает вас с \(stats.since.formatted(.dateTime.day().month(.wide).year()))")
                    .font(.title3).foregroundStyle(.secondary)
                AnimatedCount(target: stats.total)
                    .font(.system(size: 64, weight: .bold, design: .rounded))
                    .foregroundStyle(LinearGradient(colors: [.green, .teal], startPoint: .leading, endPoint: .trailing))
                Text("трекеров, рекламы и опасных адресов не дошло до вас")
                    .font(.title3.weight(.medium))
                HStack(spacing: 16) {
                    HeroBadge(icon: "sun.max.fill", tint: .orange, text: "Сегодня: \(Format.count(stats.today))")
                    if let best = stats.days.max(by: { $0.value < $1.value }), best.value > 0 {
                        HeroBadge(icon: "flame.fill", tint: .red, text: "Рекорд за день: \(Format.count(best.value))")
                    }
                    if stats.pages > 0 {
                        let perPage = Double(stats.total) / Double(stats.pages)
                        HeroBadge(icon: "divide", tint: .purple, text: "В среднем \(String(format: "%.1f", perPage).replacingOccurrences(of: ".", with: ",")) на страницу")
                    }
                }
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .glassEffect(.regular.tint(.green.opacity(0.06)), in: RoundedRectangle(cornerRadius: 28))
    }
}

struct HeroBadge: View {
    let icon: String
    let tint: Color
    let text: String

    var body: some View {
        Label { Text(text).monospacedDigit() } icon: { Image(systemName: icon).foregroundStyle(tint) }
            .font(.callout.weight(.medium))
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .glassEffect(.regular, in: Capsule())
    }
}

/// Щит с расходящимися волнами и вращающимся сиянием.
struct ShieldPulse: View {
    let tint: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            ZStack {
                ForEach(0..<3, id: \.self) { i in
                    let phase = (t / 3.2 + Double(i) / 3).truncatingRemainder(dividingBy: 1)
                    Circle()
                        .stroke(tint.opacity(0.45 * (1 - phase)), lineWidth: 2)
                        .scaleEffect(0.5 + phase * 0.5)
                }
                Circle()
                    .fill(AngularGradient(colors: [tint, .teal, .mint, .cyan, tint], center: .center, angle: .degrees(t * 36)))
                    .frame(width: 112, height: 112)
                    .blur(radius: 20)
                    .opacity(0.55)
                Circle()
                    .fill(.ultraThinMaterial)
                    .frame(width: 104, height: 104)
                    .overlay(Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1))
                Image(systemName: "shield.lefthalf.filled")
                    .font(.system(size: 50, weight: .semibold))
                    .foregroundStyle(LinearGradient(colors: [tint, .teal], startPoint: .top, endPoint: .bottom))
                    .scaleEffect(1 + 0.03 * sin(t * 2))
            }
        }
    }
}

// MARK: - Плитки

struct MetricTile: View {
    let report: DashboardReport
    let icon: String
    let tint: Color
    let value: Int
    let title: String
    let caption: String
    @State private var hover = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(tint.gradient, in: RoundedRectangle(cornerRadius: 9))
                    .symbolEffect(.bounce, value: value)
                Spacer()
            }
            AnimatedCount(target: value)
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(value > 0 ? tint : .secondary)
            Text(title).font(.headline)
            Text(caption).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 170, alignment: .topLeading)
        .glassEffect(.regular.tint(tint.opacity(hover ? 0.12 : 0.04)), in: RoundedRectangle(cornerRadius: 20))
        .scaleEffect(hover ? 1.02 : 1)
        .animation(.snappy(duration: 0.2), value: hover)
        .onHover { hover = $0 }
        .opensReport(report)
    }
}

/// Число, которое «набегает» при появлении и плавно растёт, когда счётчик меняется.
struct AnimatedCount: View {
    let target: Int
    @State private var shown = 0.0

    var body: some View {
        CountingNumber(value: shown)
            .onAppear { withAnimation(.easeOut(duration: 1.4)) { shown = Double(target) } }
            .onChange(of: target) { withAnimation(.snappy(duration: 0.6)) { shown = Double(target) } }
    }
}

struct CountingNumber: View, Animatable {
    var value: Double
    nonisolated var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text(Format.count(Int(value.rounded()))).monospacedDigit()
    }
}

struct DashboardCard<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(title, systemImage: icon).font(.headline)
            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 22))
    }
}

// MARK: - Графики

struct DailyChart: View {
    let stats: Stats
    @State private var grow = 0.0

    var body: some View {
        let days = stats.lastDays(30)
        let top = max(1, days.map(\.count).max() ?? 1)
        Chart(days) { day in
            BarMark(x: .value("День", day.date, unit: .day), y: .value("Остановлено", Double(day.count) * grow))
                .foregroundStyle(Calendar.current.isDateInToday(day.date)
                                 ? AnyShapeStyle(LinearGradient(colors: [.green, .teal], startPoint: .top, endPoint: .bottom))
                                 : AnyShapeStyle(Color.teal.opacity(0.5).gradient))
                .cornerRadius(4)
        }
        .chartYScale(domain: 0...Double(top) * 1.1)
        .chartXAxis {
            AxisMarks(values: .stride(by: .day, count: 5)) { _ in
                AxisValueLabel(format: .dateTime.day().month(.abbreviated))
            }
        }
        .frame(height: 190)
        .onAppear { withAnimation(.spring(duration: 1.2, bounce: 0.25).delay(0.15)) { grow = 1 } }
        .overlay {
            if stats.total == 0 {
                Text("Откройте пару сайтов — график начнёт расти").foregroundStyle(.secondary)
            }
        }
    }
}

struct CompanyBars: View {
    let stats: Stats
    @State private var grow = 0.0

    var body: some View {
        let top = Array(stats.byCompany.sorted { $0.value > $1.value }.prefix(8))
        let most = Double(top.first?.value ?? 1)
        VStack(alignment: .leading, spacing: 9) {
            if top.isEmpty {
                Text("Пока никто — откройте любой новостной сайт").foregroundStyle(.secondary)
            }
            ForEach(top.indices, id: \.self) { i in
                let entry = top[i]
                HStack(spacing: 10) {
                    Text(entry.key).lineLimit(1).frame(width: 130, alignment: .leading)
                    Capsule()
                        .fill(LinearGradient(colors: [Self.palette[i % Self.palette.count], Self.palette[i % Self.palette.count].opacity(0.55)],
                                             startPoint: .leading, endPoint: .trailing))
                        .frame(height: 12)
                        .scaleEffect(x: max(0.02, Double(entry.value) / most * grow), anchor: .leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(Format.count(entry.value)).monospacedDigit().foregroundStyle(.secondary).frame(width: 64, alignment: .trailing)
                }
                .font(.callout)
            }
        }
        .onAppear { withAnimation(.spring(duration: 1.1, bounce: 0.2).delay(0.2)) { grow = 1 } }
    }

    static let palette: [Color] = [.purple, .blue, .pink, .orange, .teal, .indigo, .green, .red]
}

struct KindDonut: View {
    let stats: Stats
    @State private var shown = false

    var body: some View {
        let kinds = TrackerKind.allCases.filter { (stats.byKind[$0.rawValue] ?? 0) > 0 }
        VStack(spacing: 10) {
            Chart(kinds, id: \.self) { kind in
                SectorMark(angle: .value("Сколько", shown ? stats.byKind[kind.rawValue] ?? 0 : 0), innerRadius: .ratio(0.62), angularInset: 2)
                    .foregroundStyle(by: .value("Вид", kind.title))
                    .cornerRadius(5)
            }
            .chartForegroundStyleScale(domain: TrackerKind.allCases.map(\.title), range: [Color.blue, .purple, .red, .orange])
            .chartLegend(position: .bottom, spacing: 10)
            .chartBackground { _ in
                VStack(spacing: 0) {
                    AnimatedCount(target: stats.total).font(.title2.weight(.bold))
                    Text("всего").font(.caption).foregroundStyle(.secondary)
                }
                .offset(y: -12)
            }
            .frame(height: 220)
            .overlay { if kinds.isEmpty { Text("Пока пусто").foregroundStyle(.secondary) } }
        }
        .onAppear { withAnimation(.easeOut(duration: 1).delay(0.25)) { shown = true } }
    }
}

/// Что запертые в изоляторе сайты пытались о вас узнать.
struct IsolationBreakdown: View {
    @Environment(AppModel.self) private var model
    let stats: Stats

    var body: some View {
        let sites = Array(Set(model.prefs.isolatedSites).union(stats.isolation.keys)).sorted { total($0) != total($1) ? total($0) > total($1) : $0 < $1 }
        VStack(alignment: .leading, spacing: 12) {
            Text(sites.isEmpty
                 ? "В изоляторе пока никого. Банки, госуслуги, маркетплейсы и соцсети Ward предложит запереть сам: у такого сайта будет своя «банка» данных и вымышленное устройство, а геопозицию, камеру, микрофон, уведомления и буфер обмена он не получит. Запереть любой сайт можно в щите."
                 : "Запертые сайты живут в своих «банках»: вход сохраняется, но видят они вымышленное устройство, а на любые запросы доступа получают отказ. Вот что они пытались получить.")
                .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            ForEach(sites, id: \.self) { site in
                let tried = stats.isolation[site] ?? [:]
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        SiteTile(host: site, size: 18)
                        Text(Punycode.display(site).text).font(.callout.weight(.semibold)).lineLimit(1)
                        if let kind = Isolation.category(of: site) {
                            Text(kind).font(.caption).foregroundStyle(.secondary)
                        }
                        if !model.isIsolated(site) {
                            Text("выпущен").font(.caption).foregroundStyle(.orange)
                        }
                        Spacer()
                        Text("\(Format.count(total(site))) \(Format.plural(total(site), "попытка", "попытки", "попыток"))")
                            .monospacedDigit().foregroundStyle(.secondary).font(.callout)
                    }
                    if tried.isEmpty {
                        Text("Пока ничего лишнего не просил.").font(.caption).foregroundStyle(.tertiary).padding(.leading, 26)
                    } else {
                        FlowChips(items: Isolation.Attempt.allCases.compactMap { a in
                            tried[a.rawValue].map { (a, $0) }
                        })
                        .padding(.leading, 26)
                    }
                }
            }
        }
    }

    private func total(_ site: String) -> Int { (stats.isolation[site] ?? [:]).values.reduce(0, +) }
}

/// Плашки «что пытался получить ×N» в несколько строк.
private struct FlowChips: View {
    let items: [(Isolation.Attempt, Int)]

    var body: some View {
        let columns = [GridItem(.adaptive(minimum: 170), spacing: 6, alignment: .leading)]
        LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
            ForEach(items, id: \.0) { attempt, n in
                HStack(spacing: 5) {
                    Image(systemName: attempt.icon).foregroundStyle(.teal)
                    Text(attempt.title).lineLimit(1)
                    Text("×\(Format.count(n))").monospacedDigit().foregroundStyle(.secondary)
                }
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.teal.opacity(0.1), in: Capsule())
                .help("\(attempt.title): \(attempt.answer)")
            }
        }
    }
}

struct FingerprintBreakdown: View {
    let stats: Stats
    @State private var grow = 0.0

    var body: some View {
        let most = Double(max(1, stats.fingerprintTechniques.values.max() ?? 1))
        VStack(alignment: .leading, spacing: 12) {
            Text(stats.fingerprintPages == 0
                 ? "Пока ни одна страница не пыталась снять отпечаток. Когда попытается — получит подделку, своя для каждого сайта."
                 : "\(Format.count(stats.fingerprintPages)) \(Format.plural(stats.fingerprintPages, "страница пыталась", "страницы пытались", "страниц пытались")) узнать вас без cookie. Каждая получила подделку — свою для каждого сайта и новую при каждом запуске.")
                .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            ForEach(FingerprintTechnique.allCases, id: \.self) { technique in
                let n = stats.fingerprintTechniques[technique.rawValue] ?? 0
                HStack(spacing: 10) {
                    Image(systemName: technique.icon).foregroundStyle(.pink).frame(width: 22)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(technique.title).font(.callout.weight(.medium))
                            Spacer()
                            Text(Format.count(n)).monospacedDigit().foregroundStyle(.secondary)
                        }
                        Capsule().fill(Color.pink.opacity(0.15)).frame(height: 6)
                            .overlay(alignment: .leading) {
                                Capsule().fill(Color.pink.gradient)
                                    .scaleEffect(x: Double(n) / most * grow, anchor: .leading)
                            }
                        Text(technique.detail).font(.caption).foregroundStyle(.tertiary)
                    }
                }
            }
            let scripts = Array(stats.fingerprintScripts.sorted { $0.value > $1.value }.prefix(5))
            if !scripts.isEmpty {
                Divider()
                Text("Скрипты-сборщики").font(.subheadline.weight(.semibold))
                ForEach(scripts.indices, id: \.self) { i in
                    let script = scripts[i].key, n = scripts[i].value
                    HStack {
                        Image(systemName: "chevron.left.forwardslash.chevron.right").foregroundStyle(.pink).frame(width: 16)
                        Text(script).lineLimit(1).truncationMode(.middle)
                        Text(Companies.owner(of: script)).foregroundStyle(.secondary).lineLimit(1)
                        Spacer()
                        Text("\(n) \(Format.plural(n, "страница", "страницы", "страниц"))").monospacedDigit().foregroundStyle(.secondary)
                    }
                    .font(.callout)
                }
            }
            let sites = Array(stats.fingerprintSites.sorted { $0.value > $1.value }.prefix(5))
            if !sites.isEmpty {
                Divider()
                Text("Чаще всего пытались").font(.subheadline.weight(.semibold))
                ForEach(sites.indices, id: \.self) { i in
                    let site = sites[i].key, n = sites[i].value
                    HStack {
                        SiteTile(host: site, size: 16)
                        Text(Punycode.display(site).text).lineLimit(1)
                        Spacer()
                        Text("\(n) \(Format.plural(n, "раз", "раза", "раз"))").monospacedDigit().foregroundStyle(.secondary)
                    }
                    .font(.callout)
                }
            }
        }
        .onAppear { withAnimation(.spring(duration: 1, bounce: 0.2).delay(0.3)) { grow = 1 } }
    }
}

// MARK: - Лента

struct LiveFeed: View {
    let events: [SecurityEvent]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if events.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "dot.radiowaves.left.and.right").font(.largeTitle).foregroundStyle(.tertiary)
                        .symbolEffect(.variableColor.iterative, options: .repeating)
                    Text("Откройте любой сайт — здесь появится всё, что Ward остановит, в ту же секунду.")
                        .font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
            }
            ForEach(events.prefix(14)) { event in
                HStack(spacing: 10) {
                    Image(systemName: event.kind.icon)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 24, height: 24)
                        .background(event.kind.color.gradient, in: Circle())
                    VStack(alignment: .leading, spacing: 1) {
                        Text(event.title).font(.callout.weight(.medium)).lineLimit(1)
                        if !event.detail.isEmpty {
                            Text(event.detail).font(.caption).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
                        }
                    }
                    Spacer(minLength: 4)
                    Text(event.date, style: .relative).font(.caption2).foregroundStyle(.tertiary).monospacedDigit()
                }
                .padding(.vertical, 5)
                .opensReport(DashboardReport.for(event.kind))
                .transition(.asymmetric(insertion: .move(edge: .top).combined(with: .opacity), removal: .opacity))
            }
        }
        .animation(.spring(duration: 0.45, bounce: 0.25), value: events.first?.id)
        .frame(maxHeight: 520, alignment: .top)
        .clipped()
    }
}

// MARK: - Уровень защиты

/// Какие защиты включены — и кнопка, чтобы включить остальные прямо отсюда.
struct ProtectionLevel: View {
    @Environment(AppModel.self) private var model
    @State private var shown = 0.0

    struct Item: Identifiable {
        let id: String
        let title: String
        let detail: String
        let on: Bool
        let enable: () -> Void
    }

    private var items: [Item] {
        let p = model.prefs
        return [
            Item(id: "lists", title: "Блокировка рекламы и трекеров", detail: "\(p.enabledLists.count) списков фильтров", on: !p.enabledLists.isEmpty) {},
            Item(id: "fp", title: "Подделка отпечатка браузера", detail: "Холст, WebGL, звук, шрифты", on: p.fingerprint) { model.prefs.fingerprint = true },
            Item(id: "links", title: "Очистка ссылок", detail: "Метки слежки и переходники", on: p.cleanLinks) { model.prefs.cleanLinks = true },
            Item(id: "https", title: "Только HTTPS", detail: "Без незашифрованных страниц", on: p.httpsOnly) { model.prefs.httpsOnly = true },
            Item(id: "consent", title: "Отказ от cookie", detail: "Сам нажимает «Отклонить»", on: p.consentReject) { model.prefs.consentReject = true },
            Item(id: "isolation", title: "Изолятор для «пылесосов»", detail: "Банки, госуслуги, магазины — в своих банках", on: p.suggestIsolation || !p.isolatedSites.isEmpty) { model.prefs.suggestIsolation = true },
            Item(id: "annoyances", title: "Назойливые плашки", detail: "«Подпишитесь», рассылки, запрет прокрутки", on: p.blockAnnoyances) { model.prefs.blockAnnoyances = true },
            Item(id: "cname", title: "Скрытые трекеры", detail: "Проверка поддоменов (CNAME)", on: p.cnameDetection) { model.prefs.cnameDetection = true },
            Item(id: "phishing", title: "Подделки под сайты", detail: "Похожие имена банков и почты", on: p.phishingWarnings) { model.prefs.phishingWarnings = true },
            Item(id: "closed", title: "Уборка за закрытыми сайтами", detail: "Cookie живут, пока открыта вкладка", on: p.cleanClosedSites) { model.prefs.cleanClosedSites = true },
            Item(id: "quit", title: "Стирать данные при выходе", detail: "Кроме сайтов «Помнить вход»", on: p.clearOnQuit) { model.prefs.clearOnQuit = true },
            Item(id: "strict", title: "Строгий режим для незнакомых", detail: "Режим блокировки Apple", on: p.strictMode != .off) { model.prefs.strictMode = .unfamiliar },
            Item(id: "webrtc", title: "WebRTC выключен", detail: "Не раскроет IP в обход VPN", on: p.blockWebRTC) { model.prefs.blockWebRTC = true },
            Item(id: "history", title: "История под Touch ID", detail: "Никто не заглянет без вас", on: p.protectHistory) { model.prefs.protectHistory = true },
            Item(id: "gpc", title: "«Не продавать мои данные»", detail: "Сигнал GPC и урезанный Referer", on: p.gpc && p.trimReferrer) { model.prefs.gpc = true; model.prefs.trimReferrer = true },
            Item(id: "lan", title: "Домашняя сеть закрыта", detail: "Сайты не достучатся до роутера и Mac", on: p.blockLocalNetwork) { model.prefs.blockLocalNetwork = true },
            Item(id: "bounce", title: "Защита от «отскоков»", detail: "Данные сайтов-прокладок удаляются", on: p.bounceProtection) { model.prefs.bounceProtection = true },
            Item(id: "lifetime", title: "Срок жизни данных", detail: "Cookie скриптов и хранилище — неделя", on: p.cookieLifetimeDays > 0 && p.storageLifetimeDays > 0) { model.prefs.cookieLifetimeDays = 7; model.prefs.storageLifetimeDays = 7 },
            Item(id: "nojit", title: "Без JIT для незнакомых", detail: Hardening.available ? "Защита от эксплойтов (macOS 26.4)" : "Нужна macOS 26.4", on: p.noJIT) { model.prefs.noJIT = true },
        ]
    }

    var body: some View {
        let list = items
        let on = list.filter(\.on).count
        let level = Double(on) / Double(list.count)
        let tint: Color = level >= 0.85 ? .green : level >= 0.6 ? .teal : .orange
        HStack(alignment: .top, spacing: 26) {
            ZStack {
                Circle().stroke(tint.opacity(0.15), lineWidth: 14)
                Circle()
                    .trim(from: 0, to: level * shown)
                    .stroke(AngularGradient(colors: [tint.opacity(0.6), tint], center: .center), style: StrokeStyle(lineWidth: 14, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 0) {
                    Text("\(Int((level * 100 * shown).rounded()))%").font(.system(size: 34, weight: .bold, design: .rounded)).monospacedDigit()
                    Text("уровень защиты").font(.caption).foregroundStyle(.secondary)
                }
            }
            .frame(width: 150, height: 150)
            .onAppear { withAnimation(.easeOut(duration: 1.3).delay(0.2)) { shown = 1 } }
            VStack(alignment: .leading, spacing: 10) {
                Text(on == list.count ? "Включено всё — максимальная защита" : "Включено \(on) из \(list.count)")
                    .font(.title3.weight(.semibold))
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 250), spacing: 10, alignment: .leading)], alignment: .leading, spacing: 8) {
                    ForEach(list) { item in
                        HStack(spacing: 8) {
                            Image(systemName: item.on ? "checkmark.circle.fill" : "circle.dashed")
                                .foregroundStyle(item.on ? Color.green : Color.secondary)
                                .contentTransition(.symbolEffect(.replace))
                            VStack(alignment: .leading, spacing: 0) {
                                Text(item.title).font(.callout.weight(.medium))
                                Text(item.detail).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 4)
                            if !item.on {
                                Button("Включить") { withAnimation(.snappy) { item.enable() } }
                                    .controlSize(.small)
                                    .buttonStyle(.glassProminent)
                            }
                        }
                    }
                }
                Text("Некоторые защиты выключены по умолчанию: строгий режим отключает часть возможностей сайтов, а без WebRTC не работают видеозвонки в браузере.")
                    .font(.caption).foregroundStyle(.tertiary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(22)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 24))
    }
}

/// Мягкий живой фон панели.
struct DashboardBackground: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 15, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            MeshGradient(width: 3, height: 3, points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [Float(0.5 + 0.1 * sin(t / 7)), Float(0.45 + 0.08 * cos(t / 9))], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ], colors: [
                Color.green.opacity(0.14), .clear, Color.teal.opacity(0.10),
                .clear, Color.mint.opacity(0.08), .clear,
                Color.purple.opacity(0.07), .clear, Color.cyan.opacity(0.10),
            ])
        }
        .background(.background)
        .ignoresSafeArea()
    }
}

// MARK: - Правдоподобие

/// Проверка вымышленного устройства той же батареей, что у детекторов подделки.
struct CoherenceCard: View {
    @Environment(AppModel.self) private var model
    @State private var running = false

    var body: some View {
        let report = model.coherence
        HStack(alignment: .top, spacing: 24) {
            ZStack {
                let score = report?.score ?? 0
                let tint: Color = score >= 0.99 ? .green : score >= 0.8 ? .orange : .red
                Circle().stroke(tint.opacity(0.15), lineWidth: 12)
                Circle().trim(from: 0, to: score).stroke(tint.gradient, style: StrokeStyle(lineWidth: 12, lineCap: .round)).rotationEffect(.degrees(-90))
                    .animation(.easeOut(duration: 0.9), value: score)
                VStack(spacing: 0) {
                    if running && report == nil {
                        ProgressView().controlSize(.small)
                    } else {
                        Text("\(report?.passed ?? 0)/\(report?.checks.count ?? 0)").font(.system(size: 26, weight: .bold, design: .rounded)).monospacedDigit()
                        Text("проверок").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 120, height: 120)
            VStack(alignment: .leading, spacing: 8) {
                if let report {
                    Text(report.failed.isEmpty
                         ? "Детекторы подделки не видят: вымышленное устройство выглядит как обычный Safari на Mac — в окне, во фреймах и в фоновых потоках."
                         : "Заметно \(report.failed.count) \(Format.plural(report.failed.count, "несоответствие", "несоответствия", "несоответствий")) — так детекторы могут отличить подделку:")
                        .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    ForEach(report.failed) { check in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(check.title) · \(check.profile)").font(.callout.weight(.medium))
                                Text(check.explanation).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                                if !check.detail.isEmpty { Text(check.detail).font(.caption2.monospaced()).foregroundStyle(.tertiary).lineLimit(2) }
                            }
                        }
                    }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 230), spacing: 8, alignment: .leading)], alignment: .leading, spacing: 4) {
                        ForEach(Array(Set(report.checks.filter(\.ok).map(\.key))).sorted(), id: \.self) { id in
                            Label(Coherence.titles[id] ?? id, systemImage: "checkmark.circle.fill")
                                .font(.caption).foregroundStyle(.green).labelStyle(.titleAndIcon)
                        }
                    }
                } else {
                    Text("Проверяю, заметна ли подделка: окно, фрейм, фоновый поток, ошибки, тексты функций…")
                        .font(.callout).foregroundStyle(.secondary)
                }
                HStack {
                    Button(running ? "Проверяю…" : "Проверить ещё раз") { Task { await run() } }
                        .disabled(running)
                    if let report { Text("проверено \(Format.relative(report.date))").font(.caption).foregroundStyle(.tertiary) }
                }
            }
        }
        .task {
            if model.coherence.map({ Date().timeIntervalSince($0.date) > 600 }) ?? true { await run() }
        }
    }

    private func run() async {
        running = true
        model.coherence = await Coherence.run(model: model)
        running = false
    }
}
