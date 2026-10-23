import AppKit
import JavaScriptCore
import WebKit

/// Самопроверки для CI: `Ward.app/Contents/MacOS/Ward --selftest-<режим>`.
///
/// - `logic`  — без сети и WebKit: разбор фильтров, регулярные выражения, очистка ссылок, адресная строка,
///              синтаксис скриптов защиты, разбор всех встроенных списков;
/// - `webkit` — настоящий WebKit: сборка всех правил, стенд на этом Mac (реклама, трекеры, косметика),
///              отпечаток браузера, очистка ссылок, опасные адреса, HTTPS, защита выключена на сайте.
enum SelfTest {
    /// Идёт самопроверка: настоящие данные пользователя не читаем и не пишем.
    static var isRunning: Bool { CommandLine.arguments.contains { $0.hasPrefix("--selftest-") } }

    static func runIfRequested() {
        guard let mode = CommandLine.arguments.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        switch mode {
        case "--selftest-logic":
            let ok = logic()
            print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
            Foundation.exit(ok ? 0 : 1)
        case "--selftest-webkit":
            runInApp { await webkit() }
        default:
            print("Неизвестный режим \(mode)")
            Foundation.exit(2)
        }
    }

    @discardableResult
    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    static func info(_ message: String) { print("· \(message)") }

    // MARK: - Логика

    static let sample = """
    [Adblock Plus 2.0]
    ! Title: проверка
    ||ads.example.com^
    ||tracker.example.net^$third-party
    ||cdn.example.org/ads/*$script,image
    /banner/*/img^
    @@||ads.example.com/allowed^
    @@||goodsite.com^$document
    @@||nocosmetic.org^$generichide
    ##.ad-box
    ###ad_banner
    example.com,shop.example.com##.promo
    ~safe.org##.sticky-ad
    example.com#@#.ad-box
    example.com##+js(set-constant, x, 1)
    example.com#?#div:has-text(Реклама)
    ||bad.example^$removeparam=utm_source
    $removeparam=gclid
    ||news.site^$removeparam=ref
    ||out.example.com/go$urlskip=?to
    ||scam.example^$all
    ||popup.example^$popup
    /regex[0-9]+/
    ||unicode.пример^
    ||lazy.example^$csp=script-src 'none'
    ||remove.me^
    ||remove.me^$badfilter
    example.com,~sub.example.com##+js(set, adsOn, false)
    example.com##+js(aopr, detectAdBlock)
    example.com##+js(trusted-set-cookie, a, b)
    example.com##+js(unknown-scriptlet, x)
    example.org#@#+js()
    example.org##+js(set, adsOn, false)
    youtube.*##+js(set, ytAds, false)
    example.com##.ad:has-text(Реклама)
    example.com#?#.badge:upward(2)
    example.com#$#.x { color: red !important; }
    """

    static func logic() -> Bool {
        var ok = true

        // Регулярные выражения
        let r1 = FilterParser.regex("||example.com^") ?? ""
        ok = check(matches(r1, "https://example.com/x") && matches(r1, "http://sub.example.com") && matches(r1, "https://example.com:8080/")
                   && !matches(r1, "https://notexample.com/") && !matches(r1, "https://example.com.evil.org/"), "||домен^ → \(r1)") && ok
        let r2 = FilterParser.regex("/banner/*/img^") ?? ""
        ok = check(matches(r2, "https://x.org/banner/12/img?x") && matches(r2, "https://x.org/banner/a/img") && !matches(r2, "https://x.org/banner/a/imgs"), "путь с * и ^ → \(r2)") && ok
        let r3 = FilterParser.regex("|https://x.com/a|") ?? ""
        ok = check(matches(r3, "https://x.com/a") && !matches(r3, "https://x.com/ab"), "якоря | → \(r3)") && ok
        ok = check(FilterParser.regex("||unicode.пример^") == nil, "не-ASCII не переводится") && ok

        // Разбор
        var set = FilterSet()
        FilterParser.parse(sample, kind: .ads, into: &set, badfilters: FilterParser.badfilters([sample]))
        ok = check(set.genericSelectors.contains(".ad-box") && set.genericSelectors.contains("#ad_banner"), "общая косметика") && ok
        ok = check(set.specificSelectors["example.com|shop.example.com"] == [".promo"], "косметика для сайтов") && ok
        ok = check(set.specificSelectors["!safe.org"] == [".sticky-ad"], "косметика «везде, кроме»") && ok
        ok = check(set.genericExceptions[".ad-box"] == ["example.com"], "исключение косметики") && ok
        ok = check(set.pageExceptions.contains("goodsite.com"), "исключение страницы $document") && ok
        ok = check(set.noGenericCosmetics.contains("nocosmetic.org"), "$generichide") && ok
        ok = check(set.removeParams.contains("gclid") && set.siteRemoveParams["news.site"] == ["ref"], "метки в ссылках: $removeparam") && ok
        ok = check(set.redirectors["out.example.com"]?.first == RedirectRule(pathPrefix: "/go", parameter: "to"), "переходники: $urlskip") && ok
        ok = check(set.danger.isEmpty, "опасные адреса только из списков вредоносного") && ok
        ok = check(set.trackers["ads.example.com"] == .ads && set.trackers["tracker.example.net"] == .ads, "домены трекеров") && ok
        ok = check(set.allowedHosts.isEmpty, "частичное исключение не разрешает домен целиком") && ok
        ok = check(set.partialTrackers["cdn.example.org"] == .ads && set.trackers["cdn.example.org"] == nil, "домен с блокировкой путей") && ok
        ok = check(!set.blocks.contains { $0.urlFilter.contains("remove") }, "$badfilter отменяет правило") && ok
        ok = check(!set.blocks.contains { $0.urlFilter.contains("lazy") }, "$csp пропускается") && ok
        ok = check(set.blocks.contains { $0.resourceTypes == ["popup"] }, "$popup") && ok
        ok = check(set.blocks.contains { $0.loadType == "third-party" }, "$third-party") && ok
        ok = check(set.blocks.contains { $0.resourceTypes == ["image", "script"] }, "$script,image") && ok

        // Скриптлеты и процедурная косметика
        let index0 = ProtectionIndex(set)
        let calls = index0.scriptletCalls(for: "www.example.com")
        ok = check(calls.contains(#"0["set-constant","adsOn","false"]"#) && calls.contains(#"0["abort-on-property-read","detectAdBlock"]"#) && calls.count == 3,
                   "скриптлеты для сайта: \(calls)") && ok
        ok = check(!index0.scriptletCalls(for: "sub.example.com").contains { $0.contains("adsOn") }, "скриптлет «везде, кроме» не действует на исключённом поддомене") && ok
        ok = check(index0.scriptletCalls(for: "example.org").isEmpty, "#@#+js() выключает скриптлеты сайта") && ok
        ok = check(index0.scriptletCalls(for: "www.youtube.de").contains { $0.contains("ytAds") }, "домен с любой зоной (youtube.*)") && ok
        let procedural = index0.proceduralSelectors(for: "example.com")
        ok = check(procedural.count == 4 && procedural.contains(".x:style(color: red !important;)"), "процедурная косметика и #$#: \(procedural)") && ok
        let trusted = ScriptletCatalog.call("trusted-set-cookie, a, b", trusted: true)
        ok = check(trusted != nil && ScriptletCatalog.call("trusted-set-cookie, a, b", trusted: false) == nil, "доверенные скриптлеты — только из списков uBlock Origin") && ok
        ok = check(ScriptletCatalog.arguments(#"rpnt, script, /a\,b/, 'x y'"#) == ["rpnt", "script", "/a,b/", "x y"], "разбор аргументов скриптлета") && ok

        var malware = FilterSet()
        FilterParser.parse("||scam.example^$all\n||phish.example/login^\n", kind: .malware, into: &malware, badfilters: [])
        ok = check(malware.danger["scam.example"] == [""] && malware.danger["phish.example"] == ["/login"], "опасные адреса") && ok
        let index = ProtectionIndex(malware)
        ok = check(index.isDangerous(URL(string: "http://www.scam.example/x")!) && index.isDangerous(URL(string: "https://phish.example/login/a")!)
                   && !index.isDangerous(URL(string: "https://phish.example/home")!), "проверка опасного адреса") && ok

        // Правила WebKit
        let chunks = RuleCompiler.chunks(set)
        let parsed = chunks.compactMap { try? JSONSerialization.jsonObject(with: Data($0.json.utf8)) as? [[String: Any]] }
        ok = check(parsed.count == chunks.count && chunks.contains { $0.name == "network-0" } && chunks.contains { $0.name == "cosmetic-generic" },
                   "части правил: \(chunks.map { "\($0.name)=\($0.count)" }.joined(separator: ", "))") && ok
        let net = parsed.first ?? []
        let lastIsException = (net.last?["action"] as? [String: Any])?["type"] as? String == "ignore-previous-rules"
        ok = check(lastIsException, "исключения — в конце части") && ok
        let generic = parsed[safe: chunks.firstIndex { $0.name == "cosmetic-generic" } ?? -1] ?? []
        let unless = generic.contains { (($0["trigger"] as? [String: Any])?["unless-domain"] as? [String]) == ["*example.com"] }
        ok = check(unless, "общий селектор с исключением — unless-domain") && ok

        // Ссылки
        let cleaner = LinkCleaner(set)
        let c1 = cleaner.clean(URL(string: "https://site.com/p?utm_source=x&id=5&fbclid=y&UTM_Medium=z")!)
        ok = check(c1?.url.absoluteString == "https://site.com/p?id=5" && c1?.removed.count == 3, "метки убраны: \(c1?.url.absoluteString ?? "-")") && ok
        let c2 = cleaner.clean(URL(string: "https://www.google.com/url?q=https://example.org/page?utm_campaign=a%26b%3D1&sa=D")!)
        ok = check(c2?.url.host == "example.org" && c2?.unwrapped == true && !(c2?.url.absoluteString.contains("utm") ?? true), "переходник Google: \(c2?.url.absoluteString ?? "-")") && ok
        let c3 = cleaner.clean(URL(string: "https://vk.com/away.php?to=https%3A%2F%2Fhabr.com%2Fru%2F&cc_key=")!)
        ok = check(c3?.url.absoluteString == "https://habr.com/ru/", "переходник ВКонтакте: \(c3?.url.absoluteString ?? "-")") && ok
        let c4 = cleaner.clean(URL(string: "https://out.example.com/go?to=https://dest.org/")!)
        ok = check(c4?.url.absoluteString == "https://dest.org/", "переходник из списка") && ok
        let c5 = cleaner.clean(URL(string: "https://www.youtube.com/watch?v=abc&si=XYZ")!)
        ok = check(c5?.url.absoluteString == "https://www.youtube.com/watch?v=abc", "метка YouTube si") && ok
        ok = check(cleaner.clean(URL(string: "https://example.org/search?q=si&page=2")!) == nil, "чистая ссылка не меняется") && ok
        ok = check(cleaner.clean(URL(string: "https://news.site/a?ref=1&b=2")!)?.url.query == "b=2", "метка одного сайта") && ok

        // Адресная строка
        ok = check(Omnibox.url(from: "example.com", search: .duckduckgo)?.absoluteString == "https://example.com", "адрес без схемы → https") && ok
        ok = check(Omnibox.url(from: "localhost:8080/x", search: .duckduckgo)?.absoluteString == "http://localhost:8080/x", "localhost → http") && ok
        ok = check(Omnibox.url(from: "192.168.1.1", search: .duckduckgo)?.absoluteString == "http://192.168.1.1", "локальный IP → http") && ok
        let q = Omnibox.url(from: "как защититься от слежки", search: .duckduckgo)
        ok = check(q?.host == "duckduckgo.com" && SearchEngine.duckduckgo.query(in: q!) == "как защититься от слежки", "запрос → DuckDuckGo") && ok
        ok = check(Omnibox.url(from: "swift", search: .yandex)?.host == "yandex.ru", "одно слово → поиск") && ok

        // Домены
        ok = check(Hosts.site("a.b.example.co.uk") == "example.co.uk" && Hosts.site("news.mail.ru") == "mail.ru" && Hosts.site("127.0.0.1") == "127.0.0.1", "регистрируемый домен") && ok
        ok = check(Hosts.isLocal("localhost") && Hosts.isLocal("192.168.0.5") && Hosts.isLocal("printer.local") && !Hosts.isLocal("8.8.8.8") && !Hosts.isLocal("example.com"), "локальные адреса") && ok
        var trackerIndex = ProtectionIndex()
        trackerIndex.trackers = ["google-analytics.com": .tracking]
        ok = check(trackerIndex.classify("www.google-analytics.com", page: "news.ru") == .tracking
                   && trackerIndex.classify("www.google-analytics.com", page: "google-analytics.com") == nil, "трекер — только сторонний") && ok
        ok = check(Companies.owner(of: "mc.yandex.ru") == "Яндекс" && Companies.owner(of: "stats.unknown.io") == "unknown.io", "владельцы доменов") && ok

        // Настройки старой версии читаются
        let old = try? JSONDecoder().decode(Prefs.self, from: Data(#"{"search":"google","fingerprint":false}"#.utf8))
        ok = check(old?.search == .google && old?.fingerprint == false && old?.cleanLinks == true && old?.enabledLists.contains("easylist") == true, "настройки прошлой версии") && ok

        var history = History()
        history.visit(URL(string: "https://github.com/apple/swift")!, title: "Swift")
        history.visit(URL(string: "https://news.ycombinator.com")!, title: "Hacker News")
        ok = check(history.suggestions("git").first?.url.host == "github.com" && history.suggestions("hacker").count == 1, "подсказки из истории") && ok

        // Фишинг
        ok = check(Punycode.display("xn--e1afmkfd.xn--p1ai").text == "пример.рф" && !Punycode.display("xn--e1afmkfd.xn--p1ai").suspicious, "punycode: пример.рф") && ok
        ok = check(Punycode.display("xn--80ak6aa92e.com").suspicious, "кириллица под видом латиницы («аррӏе.com») показывается как punycode") && ok
        ok = check(Lookalike.imitated(host: "sberbamk.ru") == "sberbank.ru" && Lookalike.imitated(host: "paypa1.com") == "paypal.com"
                   && Lookalike.imitated(host: "gosuslugi-pay.ru") == "gosuslugi.ru" && Lookalike.imitated(host: "paypal.com.secure-login.net") == "paypal.com",
                   "подделки под известные сайты") && ok
        let good = ["finance.yahoo.com", "booking.com", "cooking.com", "githubusercontent.com", "login.microsoftonline.com", "online.sberbank.ru", "lenta.ru", "mall.ru"]
        ok = check(good.allSatisfy { Lookalike.imitated(host: $0) == nil }, "настоящие сайты не считаются подделками") && ok

        // Настройки и сеанс прошлых версий
        let v1 = try? JSONDecoder().decode(Prefs.self, from: Data(#"{"enabledLists":["easylist","annoyances"]}"#.utf8))
        ok = check(v1?.enabledLists == ["easylist", "annoyances", "ubo-quick-fixes", "adguard-cname"], "новые списки включаются, выключенные остаются выключенными: \(v1?.enabledLists.sorted() ?? [])") && ok
        let s1 = try? JSONDecoder().decode(SavedSession.self, from: Data(#"{"windows":[["https://a.com","https://b.com"]]}"#.utf8))
        ok = check(s1?.windows.first?.count == 2 && s1?.windows.first?.first?.container == nil, "сеанс первой версии читается") && ok
        let st = try? JSONDecoder().decode(Stats.self, from: Data(#"{"total":5,"byKind":{},"byCompany":{},"days":{},"cleanedLinks":1,"upgraded":0,"since":0}"#.utf8))
        ok = check(st?.total == 5 && st?.consentsRejected == 0, "счётчики первой версии читаются") && ok
        var strictPrefs = Prefs()
        strictPrefs.strictMode = .unfamiliar
        strictPrefs.relaxedSites = ["bank.ru"]
        ok = check(strictPrefs.strict(site: "new.com", familiar: false) && !strictPrefs.strict(site: "old.com", familiar: true) && !strictPrefs.strict(site: "bank.ru", familiar: false),
                   "строгий режим для незнакомых сайтов") && ok
        ok = check(SponsorBlockClient.prefix("dQw4w9WgXcQ") == "5f6b", "SponsorBlock: в запросе только 4 знака хеша id ролика") && ok
        ok = bookmarks() && ok

        // Счётчики панели безопасности
        ok = check(st?.fingerprintPages == 0 && st?.hiddenElements == 0 && Prefs().showBookmarksBar, "новые счётчики и панель закладок у старых данных") && ok
        var fpStats = Stats()
        fpStats.recordFingerprint(site: "a.com", technique: "canvas", first: true)
        fpStats.recordFingerprint(site: "a.com", technique: "webgl", first: false)
        fpStats.recordFingerprint(site: "b.com", technique: "canvas", first: true)
        ok = check(fpStats.fingerprintPages == 2 && fpStats.fingerprintSites["a.com"] == 1 && fpStats.fingerprintTechniques["canvas"] == 2,
                   "отпечаток: страница считается один раз, способы — каждый") && ok
        fpStats.record(.ads, company: "X")
        let days = fpStats.lastDays(30)
        ok = check(days.count == 30 && days.last?.count == 1 && Calendar.current.isDateInToday(days.last!.date), "график за 30 дней кончается сегодняшним днём") && ok

        // Учёт скрытого: те же правила, что получает WebKit
        var cosmeticSet = FilterSet()
        for line in ["##.ad-box", "###banner", "##.sponsor", "example.com#@#.sponsor", "news.ru##.promo", "~safe.org##.sticky",
                     "shop.example.com##.deal", "@@||nogeneric.net^$generichide", "nogeneric.net##.local"] {
            FilterParser.parse(line, kind: .ads, into: &cosmeticSet, badfilters: [])
        }
        let cosmeticIndex = CosmeticIndex(cosmeticSet, valid: Set(RuleCompiler.selectors(cosmeticSet)).subtracting(["#banner"]))
        let onNews = Set(cosmeticIndex.selectors(for: "www.news.ru"))
        let onExample = Set(cosmeticIndex.selectors(for: "example.com"))
        ok = check(onNews.isSuperset(of: [".ad-box", ".sponsor", ".promo", ".sticky"]) && !onNews.contains("#banner"), "селекторы сайта: общие, свои, «везде, кроме»; неверные отброшены") && ok
        ok = check(!onExample.contains(".sponsor") && !onExample.contains(".deal") && cosmeticIndex.selectors(for: "shop.example.com").contains(".deal"), "исключения и поддомены") && ok
        ok = check(!cosmeticIndex.selectors(for: "nogeneric.net").contains(".ad-box") && cosmeticIndex.selectors(for: "nogeneric.net").contains(".local"), "$generichide") && ok
        ok = check(!cosmeticIndex.selectors(for: "safe.org").contains(".sticky"), "«везде, кроме» не действует на исключённом сайте") && ok

        // Банки: корпорации узнаются по всем своим доменам, чужие сайты — нет; маска даёт сайту свою личность.
        let corpCases: [(String, String?)] = [("www.youtube.com", "google"), ("mail.google.ru", "google"), ("i.ytimg.com", "google"),
                                              ("amazon.co.uk", "amazon"), ("music.yandex.ru", "yandex"), ("dzen.ru", "vk"),
                                              ("github.com", "microsoft"), ("claude.ai", "anthropic"), ("example.com", nil),
                                              ("notgoogle.com", nil), ("myapp.firebaseapp.com", nil), ("googlefan.ru", nil)]
        let corpWrong = corpCases.filter { Jars.corporation(of: $0.0)?.id != $0.1 }
        ok = check(corpWrong.isEmpty, "корпорации по доменам (\(corpWrong.map(\.0)))") && ok
        let rules = Jars.ruleList(blockingAllBut: "google")
        ok = check((try? JSONSerialization.jsonObject(with: Data(rules.utf8))) != nil && !rules.contains("youtube") && rules.contains("yandex"),
                   "правила банки Google: другие корпорации — в блоке, свои домены — нет") && ok
        let model0 = AppModel.shared
        let savedMask = model0.prefs.maskMode, savedCorp = model0.prefs.corporateJars
        model0.prefs.maskMode = true
        model0.prefs.corporateJars = true
        let mA = model0.jar(forHost: "news.example.com", privateWindow: false, ephemeral: false)
        let mB = model0.jar(forHost: "shop.example.org", privateWindow: false, ephemeral: false)
        let mG = model0.jar(forHost: "www.youtube.com", privateWindow: false, ephemeral: false)
        let mP = model0.jar(forHost: "news.example.com", privateWindow: true, ephemeral: false)
        ok = check(mA == "mask:example.com" && mB == "mask:example.org" && mG == "corp:google" && mP == nil,
                   "маска: свой сайт — своя банка, корпорация — в своей, частное окно — без банок (\(mA ?? "-"), \(mB ?? "-"), \(mG ?? "-"))") && ok
        let idBefore = model0.jarStoreID("mask:example.com")
        let legendBefore = model0.jarLegend("mask:example.com")
        model0.prefs.maskEpoch += 1
        ok = check(model0.jarStoreID("mask:example.com") != idBefore && model0.jarLegend("mask:example.com") != legendBefore
                   && model0.jarStoreID("corp:google") == model0.jarStoreID("corp:google"),
                   "смена маски: новое хранилище и новое устройство; корпоративная банка постоянна") && ok
        model0.prefs.maskEpoch -= 1
        model0.prefs.maskMode = savedMask
        model0.prefs.corporateJars = savedCorp

        // Скрипты: синтаксис в JavaScriptCore
        let js = JSContext()!
        let scriptlets = Scripts.scriptlets([#"0["set-constant","a","false"]"#, #"1["replace-fetch-response","x","y",""]"#]) ?? ""
        for (name, source) in [("отпечаток", Scripts.fingerprint(secret: "test", blockWebRTC: true, spoofTimezone: true, spoofLanguage: true, event: "wtest")),
                               ("сигналы отпечатка", Scripts.fingerprintRelay(token: "wtest")), ("учёт скрытого", Scripts.countHidden),
                               ("наблюдатель", Scripts.observer), ("проверка селекторов", Scripts.validateSelectors), ("отпечаток холста", MiniHTTP.canvasProbe),
                               ("скриптлеты", scriptlets), ("процедурная косметика", Scripts.procedural([".a:has-text(x)"]) ?? ""),
                               ("отказ от cookie", Scripts.consent), ("SponsorBlock", Scripts.sponsorBlock), ("плашки", Scripts.annoyances),
                               ("изолятор", Isolation.script(Isolation.legend(site: "example.com", secret: "t"), event: "wtest")),
                               ("сигналы изолятора", Isolation.relay(token: "wtest"))] {
            js.exception = nil
            js.setObject(source, forKeyedSubscript: "src" as NSString)
            js.evaluateScript("new Function('list', src)")
            ok = check(js.exception == nil, "скрипт «\(name)» без ошибок синтаксиса\(js.exception.map { ": \($0)" } ?? "")") && ok
        }

        // Встроенные списки
        let lists = FilterList.all.filter(\.enabledByDefault)
        let available = lists.filter { FilterStorage.text($0) != nil }
        if available.isEmpty {
            info("встроенных списков нет (сборка без сети?) — пропускаю")
        } else {
            let start = Date()
            let full = FilterEngine.parse(lists: available)
            let parseTime = Date().timeIntervalSince(start)
            let build = FilterEngine.build(lists: available, parsed: full)
            let rules = build.chunks.reduce(0) { $0 + $1.count }
            info("списков: \(available.count), строк: \(full.stats.lines), сеть: \(full.stats.network), косметика: \(full.stats.cosmetic), пропущено: \(full.stats.skipped)")
            info("правил WebKit: \(rules) в \(build.chunks.count) частях (\(build.chunks.map { "\($0.name)=\($0.count)" }.joined(separator: ", "))), разбор \(String(format: "%.1f", parseTime)) с")
            info("доменов трекеров: \(full.trackers.count), опасных адресов: \(full.danger.count), меток в ссылках: \(full.removeParams.count) + \(full.siteRemoveParams.count) сайтов, переходников: \(full.redirectors.count)")
            ok = check(available.count == lists.count, "все \(lists.count) списков в бандле") && ok
            ok = check(rules > 100_000, "больше 100 тысяч правил") && ok
            ok = check(build.chunks.allSatisfy { $0.count < 150_000 }, "каждая часть меньше предела WebKit") && ok
            ok = check(full.trackers["google-analytics.com"] != nil || full.trackers["googletagmanager.com"] != nil, "Google Analytics в трекерах") && ok
            ok = check(full.trackers["mc.yandex.ru"] != nil || full.trackers["yandex.ru"] != nil, "Яндекс Метрика в трекерах") && ok
            ok = check(full.genericSelectors.contains("#ad_banner"), "EasyList: ###ad_banner") && ok
            ok = check(full.danger.count > 1000, "опасные адреса из URLhaus") && ok
            let fullIndex = ProtectionIndex(full)
            let calls = full.scriptlets.values.reduce(0) { $0 + $1.count }
            let proc = full.procedural.values.reduce(0) { $0 + $1.count }
            info("скриптлетов: \(calls) на \(full.scriptlets.count) сайтах, процедурных правил: \(proc), для youtube.com: \(fullIndex.scriptletCalls(for: "www.youtube.com").count)")
            ok = check(calls > 3000 && proc > 1000, "скриптлеты и процедурная косметика из списков") && ok
            ok = check(fullIndex.scriptletCalls(for: "www.youtube.com").count >= 5, "правила против рекламы YouTube") && ok
            ok = check(full.trackers["omtrdc.net"] != nil || full.trackers["sc.omtrdc.net"] != nil, "настоящие домены CNAME-трекеров в списках") && ok
            let own = ["auction.unityads.unity3d.com", "an.facebook.com", "adserver.pandora.com", "analytics.twitter.com"]
            let missed = own.filter { fullIndex.classify($0, page: "example.com") == nil }
            ok = check(missed.isEmpty, "собственный список Ward: аудио-, мобильная реклама и пиксели соцсетей" + (missed.isEmpty ? "" : " (пропущены: \(missed.joined(separator: ", ")))")) && ok
        }
        return ok
    }

    // MARK: - Закладки

    static func bookmarks() -> Bool {
        var ok = true
        let a = URL(string: "https://a.com")!, b = URL(string: "https://b.com/x")!, c = URL(string: "https://c.org")!
        var tree = BookmarkTree(favorites: [Favorite(url: a, title: "A"), Favorite(url: b, title: "B")])
        ok = check(tree.bar.map(\.title) == ["A", "B"] && tree.contains(URL(string: "https://a.com/")!), "избранное прошлой версии — на панели закладок") && ok
        let folder = Bookmark.folder("Папка")
        tree.insert(folder, into: .bar, at: 1)
        let inner = Bookmark.folder("Внутри")
        tree.insert(inner, into: .folder(folder.id))
        let cb = Bookmark.link(c, title: "C")
        tree.insert(cb, into: .folder(inner.id))
        ok = check(tree.bar.map(\.title) == ["A", "Папка", "B"] && tree.find(cb.id)?.url == c && tree.location(of: cb.id)?.place == .folder(inner.id), "папки и вложенные папки") && ok
        ok = check(tree.folders.map { $0.title } == ["Панель закладок", "Папка", "Внутри", "Другие закладки"] && tree.folders[2].depth == 2, "список папок для выбора") && ok
        ok = check(!tree.canMove(folder.id, into: .folder(inner.id)) && !tree.canMove(folder.id, into: .folder(folder.id)) && tree.canMove(cb.id, into: .bar),
                   "папку нельзя положить в саму себя") && ok
        let bID = tree.bar[2].id
        tree.move(bID, to: .bar, at: 0)
        ok = check(tree.bar.map(\.title) == ["B", "A", "Папка"], "перестановка на панели") && ok
        tree.move(tree.bar[0].id, to: .bar, at: 3)
        ok = check(tree.bar.map(\.title) == ["A", "Папка", "B"], "перестановка в конец (индекс до перемещения)") && ok
        tree.move(cb.id, to: .other, at: 0)
        ok = check(tree.other.first?.id == cb.id && tree.find(inner.id)?.children?.isEmpty == true, "перенос в «Другие закладки»") && ok
        tree.update(cb.id) { $0.title = "Си" }
        _ = tree.remove(folder.id)
        ok = check(tree.find(cb.id)?.title == "Си" && tree.find(inner.id) == nil && tree.bar.count == 2, "переименование и удаление папки с содержимым") && ok
        ok = check(tree.search("си").count == 1 && tree.search("b.com").first?.url == b, "поиск по названию и адресу") && ok

        let html = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE><H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1" PERSONAL_TOOLBAR_FOLDER="true">Панель закладок</H3>
            <DL><p>
                <DT><A HREF="https://news.example/?a=1&amp;b=2" ADD_DATE="1">Новости &amp; погода</A>
                <DT><H3>Работа</H3>
                <DL><p>
                    <DT><A HREF="https://git.example/">Git &#8212; код</A>
                    <DT><A HREF="javascript:alert(1)">Букмарклет</A>
                </DL><p>
            </DL><p>
            <DT><H3>Рецепты</H3>
            <DL><p>
                <DT><A HREF='https://food.example/'>Еда</A>
            </DL><p>
            <DT><A HREF="https://a.com/">A ещё раз</A>
        </DL><p>
        """
        let imported = BookmarkImport.html(html)
        ok = check(imported.bar.count == 2 && imported.bar[0].url?.absoluteString == "https://news.example/?a=1&b=2" && imported.bar[0].title == "Новости & погода",
                   "HTML: панель закладок и сущности &amp;") && ok
        ok = check(imported.bar[1].isFolder && imported.bar[1].children?.count == 1 && imported.bar[1].children?.first?.title == "Git — код",
                   "HTML: вложенная папка, &#8212;, букмарклеты пропущены") && ok
        ok = check(imported.other.count == 2 && imported.other[0].title == "Рецепты" && imported.other[0].children?.first?.url?.host == "food.example", "HTML: папки вне панели") && ok
        let json = #"{"roots":{"bookmark_bar":{"children":[{"type":"url","name":"Хабр","url":"https://habr.com/"},{"type":"folder","name":"Dev","children":[{"type":"url","name":"Swift","url":"https://swift.org/"}]}]},"other":{"children":[{"type":"url","name":"Карты","url":"https://maps.example/"}]},"synced":{"children":[]}}}"#
        let chrome = BookmarkImport.parse(Data(json.utf8))
        ok = check(chrome?.bar.count == 2 && chrome?.bar[1].children?.first?.title == "Swift" && chrome?.other.first?.title == "Карты", "JSON Chrome / Яндекса / Brave") && ok
        var target = BookmarkTree(bar: [.link(a, title: "A")])
        let added = target.merge(imported, source: "Safari")
        ok = check(added == 3 && target.bar.count == 3 && target.other.first?.title == "Импорт: Safari" && target.other.first?.links.count == 1,
                   "импорт без повторов: \(added) новых") && ok
        let again = BookmarkImport.html(BookmarkImport.export(target))
        ok = check(again.bar.flatMap(\.links).count == target.bar.flatMap(\.links).count && again.other.flatMap(\.links).count == target.other.flatMap(\.links).count
                   && again.bar[1].title == "Новости & погода", "экспорт в HTML читается обратно") && ok
        let saved = try? JSONDecoder().decode(BookmarkTree.self, from: JSONEncoder().encode(target))
        ok = check(saved == target, "закладки сохраняются и читаются") && ok
        return ok
    }

    static func matches(_ pattern: String, _ text: String) -> Bool {
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return false }
        return re.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    // MARK: - WebKit

    nonisolated(unsafe) private static var appDelegate: TestAppDelegate?

    /// WebKit работает только в живом приложении: запускаем его без окон и выполняем проверку.
    static func runInApp(_ work: @escaping () async -> Bool) -> Never {
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        let delegate = TestAppDelegate(work: work)
        appDelegate = delegate
        app.delegate = delegate
        app.run()
        Foundation.exit(1)
    }

    final class TestAppDelegate: NSObject, NSApplicationDelegate {
        let work: () async -> Bool
        init(work: @escaping () async -> Bool) { self.work = work }

        func applicationDidFinishLaunching(_ notification: Notification) {
            Task {
                let ok = await work()
                print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
                Foundation.exit(ok ? 0 : 1)
            }
        }
    }

    static func webkit() async -> Bool {
        var ok = true
        let model = AppModel.shared
        let lists = model.prefs.enabledFilterLists

        // 1. Сборка всех правил.
        var start = Date()
        await model.blocker.build(lists: lists, force: true)
        let firstBuild = Date().timeIntervalSince(start)
        guard check(model.blocker.isReady, "правила собраны за \(String(format: "%.1f", firstBuild)) с"), let report = model.blocker.report else {
            print("  состояние: \(model.blocker.phase)")
            return false
        }
        info("правил: \(report.rules), частей: \(model.blocker.ruleLists.count), пропущено при разборе: \(report.skipped), неверных селекторов: \(report.invalidSelectors), отброшено WebKit: \(report.dropped)")
        ok = check(report.rules > 100_000, "работает больше 100 тысяч правил") && ok
        ok = check(report.dropped < 500, "WebKit отверг меньше 500 правил (\(report.dropped))") && ok

        start = Date()
        await model.blocker.build(lists: lists, force: false)
        let cached = Date().timeIntervalSince(start)
        ok = check(model.blocker.isReady && cached < 5, "повторный запуск берёт готовые правила за \(String(format: "%.2f", cached)) с") && ok

        // 2. Стенд.
        let server = MiniHTTP()
        do { try server.start() } catch { return check(false, "стенд не запустился: \(error)") }
        server.installTestSite()
        let p = server.port
        info("стенд: http://localhost:\(p)")

        let window = NSWindow(contentRect: NSRect(x: -4000, y: -4000, width: 1200, height: 800), styleMask: [.borderless], backing: .buffered, defer: false)
        window.orderFrontRegardless()
        let browser = BrowserState(isPrivate: false)
        model.register(browser)
        guard let tab = browser.selected else { return false }
        let host = NSView(frame: window.contentLayoutRect)
        attachHost = host
        window.contentView = host
        let web = tab.webView
        web.frame = host.bounds
        host.addSubview(web)

        // 3. Реклама, трекеры, косметика.
        guard await open(tab, "http://localhost:\(p)/page.html") else { return check(false, "стенд не открылся: \(tab.interstitial.map { "\($0)" } ?? "")") }
        try? await Task.sleep(for: .seconds(5))
        let page = await evaluate(tab, """
        return { banner: getComputedStyle(document.getElementById('ad_banner')).display,
                 content: getComputedStyle(document.getElementById('content')).display,
                 results: window.results };
        """) as? [String: Any] ?? [:]
        let results = page["results"] as? [String: String] ?? [:]
        info("результаты стенда: \(results.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: " "))")
        ok = check(page["banner"] as? String == "none", "###ad_banner скрыт (display: \(page["banner"] ?? "?"))") && ok
        ok = check(page["content"] as? String != "none", "нужный текст на месте") && ok
        ok = check(results["adimg"] == "error" || results["adimg2"] == "error", "рекламная картинка не загрузилась") && ok
        ok = check(results["okimg"] == "load" && results["okjs"] == "load", "обычные картинка и скрипт загрузились") && ok
        ok = check(!server.requested.contains { $0.contains("/adbanners/") || $0.contains("/ads/banners/") }, "до сервера рекламные запросы не дошли") && ok
        ok = check(results["gtag"] == "error", "Google Tag Manager заблокирован") && ok
        ok = check(results["metrika"] == "error", "Яндекс Метрика заблокирована") && ok
        ok = check(results["fb"] == "error", "пиксель Facebook заблокирован") && ok
        if results["cdn"] == "load" { check(true, "обычный CDN загрузился — сеть есть, блокируется только лишнее") }
        else { info("CDN не загрузился (\(results["cdn"] ?? "нет ответа")) — возможно, на раннере нет сети") }
        try? await Task.sleep(for: .seconds(1))
        let seen = tab.trackers.keys.sorted()
        info("щит: \(seen.joined(separator: ", "))")
        ok = check(seen.contains { $0.contains("googletagmanager") } && seen.contains { $0.contains("yandex") }, "щит показывает остановленные трекеры (\(seen.count))") && ok
        ok = check(seen.contains { $0.contains("facebook") }, "щит показывает и заблокированное по пути (пиксель Facebook)") && ok
        let hiddenBefore = model.stats.hiddenElements
        await tab.countHidden(host: "localhost")
        info("скрыто на стенде: \(tab.hiddenCount), селекторов для сайта: \(model.blocker.cosmetic.selectors(for: "localhost").count)")
        ok = check(tab.hiddenCount >= 1 && model.stats.hiddenElements >= hiddenBefore, "панель безопасности знает, сколько скрыто (\(tab.hiddenCount))") && ok

        // 4. Защита выключена на сайте — всё грузится.
        model.setProtection(false, site: "localhost")
        _ = await open(tab, "http://localhost:\(p)/page.html?off")
        try? await Task.sleep(for: .seconds(3))
        let off = await evaluate(tab, "return { banner: getComputedStyle(document.getElementById('ad_banner')).display, results: window.results };") as? [String: Any] ?? [:]
        let offResults = off["results"] as? [String: String] ?? [:]
        ok = check(off["banner"] as? String == "block" && offResults["adimg"] == "load", "защита выключена на сайте — реклама видна") && ok
        model.setProtection(true, site: "localhost")

        // 5. Отпечаток холста.
        func fingerprintIn(_ t: Tab, _ url: String) async -> [String: Any] {
            _ = await open(t, url)
            return await evaluate(t, MiniHTTP.canvasProbe) as? [String: Any] ?? [:]
        }
        func fingerprint(_ url: String) async -> [String: Any] { await fingerprintIn(tab, url) }
        let a1 = await fingerprint("http://localhost:\(p)/fp.html")
        let a2 = await fingerprint("http://localhost:\(p)/fp.html?again")
        let b1 = await fingerprint("http://127.0.0.1:\(p)/fp.html")
        info("отпечатки: localhost \(a1["hash"] ?? "-") / \(a2["hash"] ?? "-"), 127.0.0.1 \(b1["hash"] ?? "-")")
        info("строка браузера: \(a1["ua"] ?? "-")")
        ok = check(a1["hash"] != nil && (a1["hash"] as? String) == (a2["hash"] as? String), "отпечаток стабилен внутри сайта") && ok
        ok = check((a1["hash"] as? String) != (b1["hash"] as? String), "у разных сайтов отпечатки разные") && ok
        ok = check(a1["gpc"] as? Bool == true, "navigator.globalPrivacyControl = true") && ok
        ok = check(a1["native"] as? Bool == true, "подмена выглядит как встроенная функция") && ok
        ok = check((a1["cores"] as? Int) == 8, "число ядер — как у большинства Mac") && ok
        ok = check((a1["ua"] as? String)?.contains("Safari/605.1.15") == true, "строка браузера как у Safari") && ok
        model.prefs.fingerprint = false
        let n1 = await fingerprint("http://localhost:\(p)/fp.html?plain")
        let n2 = await fingerprint("http://127.0.0.1:\(p)/fp.html?plain")
        ok = check(n1["hash"] != nil && (n1["hash"] as? String) == (n2["hash"] as? String), "без защиты отпечаток одинаков на всех сайтах (значит, разницу даёт защита)") && ok
        model.prefs.fingerprint = true

        // 5б. Попытки снять отпечаток видны в щите и в панели безопасности.
        let fpBefore = model.stats.fingerprintPages
        _ = await open(tab, "http://localhost:\(p)/fp-all.html")
        try? await Task.sleep(for: .seconds(2))
        info("способы отпечатка на странице: \(tab.fingerprinting.sorted())")
        ok = check(tab.fingerprinting.isSuperset(of: ["canvas", "audio", "fonts"]), "замечены холст, звук и перебор шрифтов") && ok
        for (way, what) in [("webgl", "модель видеокарты"), ("voices", "голоса синтеза речи")] {
            if tab.fingerprinting.contains(way) { check(true, "замечен запрос: \(what)") } else { info("\(what): на раннере недоступно — пропускаю") }
        }
        ok = check(model.stats.fingerprintPages == fpBefore + 1, "страница посчитана в панели один раз") && ok
        _ = await open(tab, "http://localhost:\(p)/target.html?fp")
        try? await Task.sleep(for: .seconds(1))
        ok = check(tab.fingerprinting.isEmpty, "обычная страница не считается слежкой") && ok

        // 6. Очистка ссылок.
        _ = await open(tab, "http://localhost:\(p)/clean.html?utm_source=newsletter&id=5&fbclid=IwAR0abc")
        ok = check(tab.url?.query == "id=5" && tab.cleanedParams.contains("fbclid"), "метки убраны до загрузки: \(tab.url?.absoluteString ?? "-")") && ok
        ok = check(!server.requested.contains { $0.contains("fbclid") }, "сайт не получил fbclid") && ok
        _ = await open(tab, "https://www.google.com/url?q=http://localhost:\(p)/target.html&sa=D&ust=1")
        ok = check(tab.url?.path == "/target.html" && tab.unwrappedRedirect, "переходник Google пропущен: \(tab.url?.absoluteString ?? "-")") && ok

        // 7. Опасный адрес — предупреждение без единого запроса.
        if let bad = model.blocker.index.danger.first(where: { $0.value.contains("") && !Hosts.isLocal($0.key) })?.key {
            tab.load(URL(string: "http://\(bad)/")!)
            try? await Task.sleep(for: .seconds(2))
            var isDanger = false
            if case .danger = tab.interstitial { isDanger = true }
            ok = check(isDanger, "опасный адрес \(bad) — предупреждение") && ok
        }

        // 8. HTTPS (нужна сеть).
        _ = await open(tab, "http://example.com/", timeout: 25)
        if tab.url?.scheme == "https" { check(true, "http://example.com открыт по HTTPS") }
        else { info("HTTPS-проверка: \(tab.url?.absoluteString ?? "-") \(tab.interstitial.map { "\($0)" } ?? "")") }

        // 9. Страница рекламного сервера целиком.
        _ = await open(tab, "https://googletagmanager.com/", timeout: 20)
        if case .blocked = tab.interstitial { check(true, "адрес трекера целиком — экран «Ward остановил эту страницу»") }
        else { info("страница трекера: \(tab.interstitial.map { "\($0)" } ?? "открылась") \(tab.url?.absoluteString ?? "")") }

        // 10. Частное окно: cookie только в памяти окна, отпечаток со своим секретом.
        server.set("/cookie-private.html", html: "<!doctype html><title>cookie</title><script>document.cookie = 'ward_private=1; max-age=3600; path=/';</script>")
        let priv = BrowserState(isPrivate: true)
        model.register(priv)
        if let ptab = priv.selected {
            let pweb = ptab.webView
            pweb.frame = host.bounds
            host.addSubview(pweb)
            _ = await open(ptab, "http://localhost:\(p)/cookie-private.html")
            let pa = await fingerprintIn(ptab, "http://localhost:\(p)/fp.html?private")
            let normal = await fingerprintIn(tab, "http://localhost:\(p)/fp.html?normal")
            ok = check(pa["hash"] != nil && (pa["hash"] as? String) != (normal["hash"] as? String), "у частного окна свой отпечаток на том же сайте") && ok
            let privateCookies = await cookies(priv.dataStore)
            let normalCookies = await cookies(WKWebsiteDataStore.default())
            ok = check(privateCookies.contains("ward_private") && !normalCookies.contains("ward_private"), "cookie частного окна не попали в общее хранилище") && ok
            ok = check(!priv.dataStore.isPersistent, "хранилище частного окна — только в памяти") && ok
            priv.closedWindow()
        }

        // 11. Скриптлеты и процедурная косметика (правила для localhost подставляются, как будто они из списков).
        model.blocker.injectForTest { index in
            index.scriptlets["localhost"] = [
                #"0["set-constant","adsOn","false"]"#, #"0["json-prune","ads"]"#, #"0["abort-on-property-read","detectAdBlock"]"#,
            ]
            index.procedural["localhost"] = [".promo:has-text(Реклама)", ".badge:upward(2)"]
        }
        tab.protectionsChanged()
        _ = await open(tab, "http://localhost:\(p)/scriptlets.html?s")
        try? await Task.sleep(for: .seconds(1))
        let sc = await evaluate(tab, """
        const shown = (id) => getComputedStyle(document.getElementById(id)).display !== 'none';
        return { results: window.results, promo1: shown('promo1'), promo2: shown('promo2'),
                 wrap: getComputedStyle(document.querySelector('.wrap')).display };
        """) as? [String: Any] ?? [:]
        let scr = sc["results"] as? [String: String] ?? [:]
        info("скриптлеты: \(scr), процедурная косметика: \(sc)")
        ok = check(scr["adsOn"] == "false", "set-constant: сайт не смог включить рекламу") && ok
        ok = check(scr["pruned"] == "true", "json-prune: рекламные поля убраны из JSON") && ok
        ok = check(scr["aopr"] == "aborted", "abort-on-property-read: проверка блокировщика прервана") && ok
        ok = check(sc["promo1"] as? Bool == false && sc["promo2"] as? Bool == true, ":has-text скрывает только рекламный блок") && ok
        ok = check(sc["wrap"] as? String == "none", ":upward(2) скрывает обёртку") && ok
        ok = check(tab.scriptletCount == 3, "щит знает, сколько скриптлетов работает (\(tab.scriptletCount))") && ok
        model.blocker.injectForTest { index in
            index.scriptlets["localhost"] = nil
            index.procedural["localhost"] = nil
        }
        tab.protectionsChanged()

        // 12. Отказ от cookie: известное окно (OneTrust) и незнакомое — по тексту кнопки.
        for (page, expected) in [("consent.html", "OneTrust"), ("consent2.html", "по тексту кнопки")] {
            _ = await open(tab, "http://localhost:\(p)/\(page)")
            try? await Task.sleep(for: .seconds(2.5))
            let r = await evaluate(tab, "return window.results.consent || 'нет'") as? String
            let state = try? await tab.webViewIfLoaded?.callAsyncJavaScript("return JSON.stringify(window.__wardConsent || null)", arguments: [:], in: nil, contentWorld: .defaultClient)
            info("состояние автоотказа: \(state.map { "\($0 ?? "nil")" } ?? "ошибка")")
            ok = check(r == "rejected" && tab.consentRejected == expected, "cookie: нажато «Отклонить» (\(page): \(r ?? "-"), \(tab.consentRejected ?? "-"))") && ok
        }

        // 12б. Назойливые плашки: «Подпишитесь» снизу, затемнение, «отключите блокировщик», рассылка.
        _ = await open(tab, "http://localhost:\(p)/annoy.html")
        try? await Task.sleep(for: .seconds(4.5))
        let annoy = await evaluate(tab, """
        const shown = (id) => { const e = document.getElementById(id); return !!e && getComputedStyle(e).display !== 'none'; };
        return { sheet: shown('sheet'), fade: shown('fade'), adb: shown('adb'), news: shown('news'), bg: shown('bg'),
                 header: getComputedStyle(document.querySelector('header')).display !== 'none',
                 overflow: getComputedStyle(document.body).overflowY };
        """) as? [String: Any] ?? [:]
        let annoyState = try? await tab.webViewIfLoaded?.callAsyncJavaScript("return JSON.stringify(window.__wardAnnoyances || null)", arguments: [:], in: nil, contentWorld: .defaultClient)
        info("плашки: \(annoy), на вкладке убрано \(tab.annoyancesHidden), состояние скрипта: \(annoyState.map { "\($0 ?? "nil")" } ?? "ошибка"), защита: \(tab.protectedHere)")
        ok = check(annoy["sheet"] as? Bool == false && annoy["adb"] as? Bool == false && annoy["news"] as? Bool == false,
                   "плашки «Подпишитесь», «отключите блокировщик» и рассылка убраны") && ok
        ok = check(annoy["fade"] as? Bool == false && annoy["overflow"] as? String != "hidden", "затемнение снято, прокрутка возвращена") && ok
        ok = check(annoy["header"] as? Bool == true && annoy["bg"] as? Bool == true, "шапка сайта с меню и фон страницы не тронуты") && ok
        ok = check(tab.annoyancesHidden >= 3, "щит знает об убранных плашках (\(tab.annoyancesHidden))") && ok

        // 12в. Плашка Bloomberg: обёртка нулевой высоты, запертые html и body, «туман» из псевдоэлемента.
        _ = await open(tab, "http://localhost:\(p)/bbg.html")
        try? await Task.sleep(for: .seconds(5))
        let wall = await evaluate(tab, """
        const shown = (id) => { let e = document.getElementById(id); if (!e) return null; for (; e; e = e.parentElement) if (getComputedStyle(e).display === 'none') return false; return true; };
        const b = getComputedStyle(document.body), nav = getComputedStyle(document.querySelector('nav'), '::after');
        document.getElementById('open').click();
        return { wall: shown('wall'), nav: shown('open'), body: [b.overflowY, b.position, b.pointerEvents].join(','),
                 fog: nav.display !== 'none' && nav.content !== 'none', classes: document.documentElement.className + '|' + document.body.className };
        """) as? [String: Any] ?? [:]
        info("плашка Bloomberg: \(wall)")
        ok = check(wall["wall"] as? Bool == false && wall["nav"] as? Bool == true, "Bloomberg: «Subscribe now… Claim This Offer» убрана, статья на месте") && ok
        ok = check((wall["body"] as? String)?.hasPrefix("hidden") == false && (wall["body"] as? String)?.contains("none") == false && wall["fog"] as? Bool == false,
                   "Bloomberg: прокрутка и щелчки вернулись, серый «туман» над статьёй снят") && ok

        // 13. Подделка под известный сайт — предупреждение без единого запроса.
        tab.load(URL(string: "https://sberbamk.ru/login")!)
        try? await Task.sleep(for: .seconds(1.5))
        if case .lookalike(_, let original) = tab.interstitial { ok = check(original == "sberbank.ru", "sberbamk.ru — «похоже на подделку под sberbank.ru»") && ok }
        else { ok = check(false, "sberbamk.ru — предупреждение о подделке (\(String(describing: tab.interstitial)))") && ok }

        // 14. Сайт без HTTPS: сначала HTTPS, потом предупреждение; на странице без шифрования — о пароле.
        // ward.localtest.me — публичное имя, которое ведёт на 127.0.0.1: для Ward это «настоящий» сайт.
        let plain = URL(string: "http://ward.localtest.me:\(p)/password.html")!
        _ = await open(tab, plain.absoluteString, timeout: 20)
        if case .insecure(let from) = tab.interstitial {
            check(true, "HTTPS не получился — экран «сайт не поддерживает защищённое соединение»")
            tab.proceedInsecure(from)
            let before = tab.loadsFinished
            for _ in 0..<50 where tab.loadsFinished == before { try? await Task.sleep(for: .milliseconds(200)) }
            _ = await evaluate(tab, "document.getElementById('pw').focus(); return 1")
            try? await Task.sleep(for: .seconds(1))
            ok = check(tab.passwordWarning, "поле пароля на странице без шифрования — предупреждение") && ok
        } else {
            info("localtest.me недоступен или HTTPS ответил (\(String(describing: tab.interstitial))) — проверку пароля пропускаю")
        }

        // 15. Строгий режим (Lockdown Mode WebKit) — без WebGL и WebAssembly.
        let probe = "const g = document.createElement('canvas').getContext('webgl'); return { webgl: !!g, wasm: typeof WebAssembly !== 'undefined' };"
        _ = await open(tab, "http://localhost:\(p)/strict.html?normal")
        let normal = await evaluate(tab, probe) as? [String: Bool] ?? [:]
        model.setStrict(true, site: "localhost")
        _ = await open(tab, "http://localhost:\(p)/strict.html?strict")
        let strict = await evaluate(tab, probe) as? [String: Bool] ?? [:]
        info("обычный режим: \(normal), строгий: \(strict)")
        ok = check(tab.strictHere && strict != normal, "строгий режим меняет возможности страницы") && ok
        model.setStrict(nil, site: "localhost")

        // 16. Размеры элементов: шум в стотысячных долях пикселя, разный у разных сайтов.
        let rectProbe = "return document.getElementById('box').getBoundingClientRect().width"
        _ = await open(tab, "http://localhost:\(p)/rects.html")
        let wa = await evaluate(tab, rectProbe) as? Double ?? 0
        _ = await open(tab, "http://127.0.0.1:\(p)/rects.html")
        let wb = await evaluate(tab, rectProbe) as? Double ?? 0
        ok = check(wa != wb && abs(wa - wb) < 0.001 && abs(wa - 123.4) < 0.01, "getBoundingClientRect: \(wa) / \(wb)") && ok

        // 17. Контейнеры и одноразовые вкладки: cookie не переходят между хранилищами.
        let work = model.containers[1]
        let cTab = browser.newTab(url: nil, select: false, container: work)
        let cWeb = cTab.webView
        cWeb.frame = host.bounds
        host.addSubview(cWeb)
        _ = await open(cTab, "http://127.0.0.1:\(p)/cookie.html?container")
        let eTab = browser.newTab(url: nil, select: false, ephemeral: true)
        let eWeb = eTab.webView
        eWeb.frame = host.bounds
        host.addSubview(eWeb)
        let defaultBefore = await cookies(WKWebsiteDataStore.default())
        _ = await open(eTab, "http://127.0.0.1:\(p)/cookie.html?ephemeral")
        let inContainer = await cookies(model.store(for: work))
        let inEphemeral = await cookies(eTab.dataStore)
        let inDefault = await cookies(WKWebsiteDataStore.default())
        info("cookie: контейнер \(inContainer.sorted()), одноразовая \(inEphemeral.sorted()), общее до \(defaultBefore.sorted()) / после \(inDefault.sorted())")
        ok = check(inContainer.contains("ward_test") && inEphemeral.contains("ward_test") && inDefault == defaultBefore,
                   "cookie контейнера и одноразовой вкладки не попали в общее хранилище") && ok
        let otherContainer = await cookies(model.store(for: model.containers[2]))
        ok = check(!otherContainer.contains("ward_test"), "другой контейнер этих cookie не видит") && ok

        // 17б. Изолятор: своя банка данных, вымышленное устройство, отказ во всех разрешениях;
        // обычная вкладка, открывшая сайт из изолятора, отдаёт его в банку.
        let jar = "127.0.0.1"
        model.prefs.isolatedSites.insert(jar)
        let iTab = browser.newTab(url: nil, select: false, jar: jar)
        let iWeb = iTab.webView
        iWeb.frame = host.bounds
        host.addSubview(iWeb)
        let defaultBeforeJar = await cookies(WKWebsiteDataStore.default())
        _ = await open(iTab, "http://127.0.0.1:\(p)/iso.html")
        try? await Task.sleep(for: .seconds(1.5))
        let iso = await evaluate(iTab, "return window.results") as? [String: Any] ?? [:]
        let legend = Isolation.legend(site: jar, secret: model.prefs.isolationSecret)
        info("изолятор: \(iso), попытки \(iTab.isolationAttempts), устройство \(legend.tag)")
        ok = check(iso["geo"] as? Int == 1 && iso["perm"] as? String == "denied", "изолятор: геопозиция — «запрещено», и опрос разрешений отвечает так же") && ok
        ok = check((iso["gum"] as? String).map { $0 == "NotAllowedError" || $0 == "none" } == true && (iso["devices"] as? Int ?? -1) == 0,
                   "изолятор: камера и микрофон — отказ, список устройств пуст") && ok
        ok = check((iso["clip"] as? String).map { $0 != "read" } == true && (iso["notif"] as? String).map { $0 == "denied" || $0 == "none" } == true,
                   "изолятор: буфер обмена не читается, уведомления запрещены") && ok
        ok = check(iso["native"] as? Bool == true, "изолятор: подменённые функции выглядят встроенными") && ok
        ok = check(iTab.isolationAttempts.keys.count >= 3, "изолятор записал попытки (\(iTab.isolationAttempts))") && ok
        let jarCookies = await cookies(iTab.dataStore)
        let defaultAfterJar = await cookies(WKWebsiteDataStore.default())
        ok = check(jarCookies.contains("ward_iso") && !defaultAfterJar.contains("ward_iso") && defaultAfterJar == defaultBeforeJar,
                   "cookie изолятора живут только в его банке") && ok
        // Обычная вкладка открывает сайт из изолятора — страница переезжает в его банку на месте, «Назад» возвращает.
        _ = await open(tab, "http://localhost:\(p)/clean.html?before")
        let generation = tab.webViewGeneration
        tab.load(URL(string: "http://127.0.0.1:\(p)/clean.html?jar")!)
        // В приложении страницу после переезда ставит на место SwiftUI; здесь — сами.
        func reattach() {
            if let v = tab.webViewIfLoaded, v.superview !== host { v.frame = host.bounds; host.addSubview(v) }
        }
        for _ in 0..<40 where tab.webViewIfLoaded?.url?.host != "127.0.0.1" { reattach(); try? await Task.sleep(for: .milliseconds(200)) }
        ok = check(tab.jar == jar && tab.webViewGeneration > generation && tab.webViewIfLoaded?.url?.host == "127.0.0.1",
                   "сайт из изолятора, открытый в обычной вкладке, переехал в свою банку на месте (\(tab.jar ?? "без банки"))") && ok
        ok = check(tab.canGoBack, "после переезда «Назад» доступно") && ok
        tab.goBack()
        for _ in 0..<60 where tab.webViewIfLoaded?.url?.host != "localhost" { reattach(); try? await Task.sleep(for: .milliseconds(200)) }
        reattach()
        ok = check(tab.jar == nil && tab.webViewIfLoaded?.url?.host == "localhost", "«Назад» вернул страницу из банки обратно") && ok
        for t in browser.tabs where t.jar == jar { browser.close(t) }
        await model.setIsolated(false, site: jar)
        _ = await open(tab, "http://localhost:\(p)/clean.html")

        // 17в. Корпоративные банки: правила «никаких других корпораций» собираются WebKit.
        _ = model.corporationRules("google")
        for _ in 0..<50 where model.corporationRules("google") == nil { try? await Task.sleep(for: .milliseconds(200)) }
        ok = check(model.corporationRules("google") != nil, "правила корпоративной банки Google собраны WebKit") && ok

        // 18. Уборка за закрытым сайтом: вкладки 127.0.0.1 закрыты — его данные удаляются, открытый localhost остаётся.
        _ = await open(tab, "http://localhost:\(p)/cookie.html?keep")
        browser.close(cTab)
        browser.close(eTab)
        let removed = await model.cleanupClosedSites()
        let afterContainer = await cookies(model.store(for: work))
        let afterDefault = await cookies(WKWebsiteDataStore.default())
        let recordNames = await AppModel.records(WKWebsiteDataStore.default()).map(\.displayName)
        info("после уборки: контейнер \(afterContainer.sorted()), общее \(afterDefault.sorted()), записи общего: \(recordNames)")
        ok = check(!afterContainer.contains("ward_test") && afterDefault.contains("ward_test"),
                   "данные закрытого сайта удалены (\(removed) записей), открытого — на месте") && ok

        // 19. Прокси: окно идёт через SOCKS5, и имя сайта (DNS) разрешает прокси, а не Mac.
        let socks = MiniSOCKS(targetPort: p)
        if (try? socks.start()) != nil {
            model.prefs.proxyHost = "127.0.0.1"
            model.prefs.proxyPort = socks.port
            model.prefs.proxyMode = .all
            let proxied = "http://proxy-test.ward.invalid:\(p)/clean.html?proxy"
            _ = await open(tab, proxied, timeout: 20)
            if case .insecure(let from) = tab.interstitial {
                tab.proceedInsecure(from)
                let before = tab.loadsFinished
                for _ in 0..<50 where tab.loadsFinished == before { try? await Task.sleep(for: .milliseconds(200)) }
            }
            let title = await evaluate(tab, "return document.title") as? String
            info("через прокси: \(socks.requests.map { "\($0.name)\($0.isDomain ? " (имя)" : " (IP)")" })")
            ok = check(title == "Чистая ссылка", "страница несуществующего для DNS сайта открылась через прокси") && ok
            ok = check(socks.requests.contains { $0.isDomain && $0.name == "proxy-test.ward.invalid" }, "имя сайта ушло прокси — DNS не утекает") && ok
            let rtc = await evaluate(tab, "return typeof RTCPeerConnection") as? String
            ok = check(rtc == "undefined", "в окне через прокси WebRTC выключен сам — IP не утечёт в обход прокси (\(rtc ?? "-"))") && ok
            model.prefs.proxyMode = .off
            socks.stop()
        }

        // 20. Загрузка: в «Загрузки», с SHA-256.
        _ = await open(tab, "http://localhost:\(p)/file.bin", timeout: 10)
        for _ in 0..<50 where model.downloads.items.first?.sha256 == nil { try? await Task.sleep(for: .milliseconds(200)) }
        if let item = model.downloads.items.first {
            let expected = Downloads.sha256(of: { () -> URL in
                let u = FileManager.default.temporaryDirectory.appendingPathComponent("ward-expected.bin")
                try? MiniHTTP.downloadBody.write(to: u)
                return u
            }())
            ok = check(item.state == .done && item.sha256 == expected, "загрузка завершена, SHA-256 совпадает (\(item.file?.lastPathComponent ?? "-"))") && ok
            if let file = item.file { try? FileManager.default.removeItem(at: file) }
        } else {
            ok = check(false, "загрузка не началась") && ok
        }

        // 21. История на диске — только в зашифрованном виде.
        model.saveState()
        let sealed = Storage.url("history.json.sealed")
        let sealedData = (try? Data(contentsOf: sealed)) ?? Data()
        ok = check(!sealedData.isEmpty && !FileManager.default.fileExists(atPath: Storage.url("history.json").path)
                   && sealedData.range(of: Data("localhost".utf8)) == nil, "история зашифрована (\(sealedData.count) байт, открытого текста нет)") && ok

        // 22. Песочница macOS.
        let sandboxed = ProcessInfo.processInfo.environment["APP_SANDBOX_CONTAINER_ID"] != nil
        let realHome = getpwuid(getuid()).map { String(cString: $0.pointee.pw_dir) } ?? "/Users/Shared"
        let outside = URL(fileURLWithPath: realHome).appendingPathComponent("ward-escape-test-\(UUID().uuidString.prefix(6))")
        let escaped = (try? Data("x".utf8).write(to: outside)) != nil
        if escaped { try? FileManager.default.removeItem(at: outside) }
        ok = check(sandboxed && !escaped, "Ward работает в песочнице: писать вне своей папки нельзя") && ok

        // 23. Сеть (справочно): скрытый трекер через DNS, SponsorBlock, сертификат.
        if let target = await Task.detached(operation: { CNAMEGuard.canonicalName("metrics.24hourfitness.com") }).value {
            let kind = model.blocker.index.classify(target, page: "www.24hourfitness.com")
            check(kind != nil, "CNAME: metrics.24hourfitness.com → \(target) — \(kind.map { "\($0)" } ?? "не трекер")")
        } else { info("CNAME: имя не разрешилось (нет сети?)") }
        let segments = await SponsorBlockClient.shared.segments(videoID: "0e3GPea1Tyg", prefs: model.prefs)
        if segments.isEmpty { info("SponsorBlock: отрезков не пришло (нет сети или база недоступна)") }
        else { check(true, "SponsorBlock: \(segments.count) \(Format.plural(segments.count, "отрезок", "отрезка", "отрезков")) для ролика (\(segments.compactMap { $0["category"] as? String }.joined(separator: ", ")))") }
        _ = await open(tab, "https://example.com/", timeout: 20)
        if let cert = tab.certificate { check(!cert.issuer.isEmpty, "сертификат example.com: \(cert.issuer), до \(cert.notAfter.map { "\($0)" } ?? "?")") }
        else { info("сертификат: не получен") }

        // 24. Внешний тест блокировщика (справочно).
        if CommandLine.arguments.contains("--live") {
            _ = await open(tab, "https://d3ward.github.io/toolz/adblock.html", timeout: 30)
            try? await Task.sleep(for: .seconds(20))
            let text = await evaluate(tab, "return document.body ? document.body.innerText : ''") as? String ?? ""
            let lines = text.split(separator: "\n").filter { $0.contains("%") || $0.lowercased().contains("blocked") }.prefix(6)
            info("d3ward adblock test: \(lines.joined(separator: " | "))")
            _ = await open(tab, "https://abrahamjuliot.github.io/creepjs/", timeout: 30)
            try? await Task.sleep(for: .seconds(25))
            let creep = await evaluate(tab, "return document.body ? document.body.innerText.slice(0, 600) : ''") as? String ?? ""
            info("CreepJS: \(creep.replacingOccurrences(of: "\n", with: " | ").prefix(400))")
        }

        server.stop()
        return ok
    }

    /// Окно стенда: сюда ставится страница вкладки после переезда в другую банку (в приложении это делает SwiftUI).
    static weak var attachHost: NSView?

    static func reattach(_ tab: Tab) {
        guard let host = attachHost, let view = tab.webViewIfLoaded, view.superview == nil else { return }
        view.frame = host.bounds
        host.addSubview(view)
    }

    /// Открыть адрес и дождаться конца загрузки (или экрана-предупреждения).
    static func open(_ tab: Tab, _ address: String, timeout: Double = 20) async -> Bool {
        guard let url = URL(string: address) else { return false }
        let before = tab.loadsFinished
        tab.load(url)
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            reattach(tab)
            try? await Task.sleep(for: .milliseconds(200))
            reattach(tab)
            if tab.interstitial != nil { return false }
            if tab.loadsFinished > before, let view = tab.webViewIfLoaded, !view.isLoading { return true }
        }
        return false
    }

    /// Имена cookie в хранилище.
    static func cookies(_ store: WKWebsiteDataStore) async -> Set<String> {
        await withCheckedContinuation { (c: CheckedContinuation<Set<String>, Never>) in
            store.httpCookieStore.getAllCookies { list in c.resume(returning: Set(list.map(\.name))) }
        }
    }

    static func evaluate(_ tab: Tab, _ js: String) async -> Any? {
        guard let view = tab.webViewIfLoaded else { return nil }
        do { return try await view.callAsyncJavaScript(js, arguments: [:], in: nil, contentWorld: .page) } catch {
            print("  скрипт: \(error.localizedDescription)")
            return nil
        }
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
