import Foundation

/// Сетевое правило в терминах блокировщика WebKit: регулярное выражение для адреса и условия.
nonisolated struct NetworkRule: Hashable, Sendable {
    var urlFilter: String
    var caseSensitive = false
    /// Типы ресурсов WebKit; пусто — все.
    var resourceTypes: [String] = []
    var loadType: String?
    var loadContext: String?
    /// Домены страницы, на которой действует правило («*» в начале — с поддоменами).
    var ifDomains: [String] = []
    var unlessDomains: [String] = []

    func json(_ action: String) -> [String: Any] {
        var trigger: [String: Any] = ["url-filter": urlFilter]
        if caseSensitive { trigger["url-filter-is-case-sensitive"] = true }
        if !resourceTypes.isEmpty { trigger["resource-type"] = resourceTypes }
        if let loadType { trigger["load-type"] = [loadType] }
        if let loadContext { trigger["load-context"] = [loadContext] }
        // WebKit не разрешает if-domain и unless-domain в одном правиле.
        if !ifDomains.isEmpty { trigger["if-domain"] = ifDomains } else if !unlessDomains.isEmpty { trigger["unless-domain"] = unlessDomains }
        return ["trigger": trigger, "action": ["type": action]]
    }
}

/// Куда относится домен из списка: реклама, трекер, вредоносное — для отчёта «кто следил».
nonisolated enum TrackerKind: String, Sendable, Codable, CaseIterable {
    case ads, tracking, malware, annoyance

    var title: String {
        switch self {
        case .ads: "Реклама"
        case .tracking: "Слежка"
        case .malware: "Опасное"
        case .annoyance: "Помехи"
        }
    }
}

/// Всё, что извлечено из списков фильтров: правила для WebKit и данные для собственных защит Ward.
nonisolated struct FilterSet: Sendable {
    var blocks: [NetworkRule] = []
    var exceptions: [NetworkRule] = []
    /// Страницы, где блокировка выключена целиком (`@@||site^$document`).
    var pageExceptions: Set<String> = []
    var genericSelectors: [String] = []
    /// Исключения общих селекторов: селектор → домены (пусто — убрать везде).
    var genericExceptions: [String: Set<String>] = [:]
    /// Селекторы для конкретных сайтов: «домены через |» → селекторы.
    var specificSelectors: [String: [String]] = [:]
    /// Сайты без общих (ghide), без частных (shide) или без всех (ehide) косметических правил.
    var noGenericCosmetics: Set<String> = []
    var noSpecificCosmetics: Set<String> = []
    /// Домены рекламы и трекеров — для подсчёта «кто пытался следить».
    var trackers: [String: TrackerKind] = [:]
    /// Домены, которые списки разрешают целиком (`@@||host^`): их не считаем трекерами.
    var allowedHosts: Set<String> = []
    /// Опасные адреса: домен → пути (пусто — весь домен). Для предупреждения до открытия страницы.
    var danger: [String: [String]] = [:]
    /// Метки слежки в ссылках: везде и на отдельных сайтах.
    var removeParams: Set<String> = []
    var siteRemoveParams: [String: Set<String>] = [:]
    /// Ссылки-переходники: домен → [(начало пути, параметр с настоящим адресом)].
    var redirectors: [String: [RedirectRule]] = [:]
    var stats = Stats()

    nonisolated struct Stats: Sendable, Codable {
        var lines = 0
        var network = 0
        var cosmetic = 0
        var skipped = 0
    }
}

nonisolated struct RedirectRule: Hashable, Sendable, Codable {
    var pathPrefix: String
    var parameter: String
}

/// Разбор списков в синтаксисе Adblock Plus / uBlock Origin / AdGuard.
///
/// Блокировщик WebKit проще uBlock: нет скриптлетов, перенаправлений на заглушки, процедурной косметики
/// и регулярных выражений с альтернативой. Такие правила пропускаются (они в меньшинстве — в основном
/// точечные исправления для отдельных сайтов). Остальное переводится один к одному.
nonisolated enum FilterParser {
    /// Опции, при которых правило не переводится в WebKit (его смысл не передать — лучше пропустить,
    /// чем блокировать лишнее).
    static let unsupportedOptions: Set<String> = [
        "csp", "rewrite", "replace", "header", "permissions", "urltransform", "uritransform", "method",
        "denyallow", "redirect-rule", "cname", "ipaddress", "inline-script", "inline-font", "jsonprune",
        "xmlprune", "responseheader", "requestheader", "hls", "network", "app", "cookie", "stealth",
        "removeheader", "strict1p", "strict3p",
    ]
    /// Опции исключений, которые для WebKit ничего не меняют.
    static let ignoredOptions: Set<String> = ["extension", "content", "jsinject", "urlblock", "genericblock", "important", "reason"]
    static let unsupportedMarkers = ["#?#", "#$#", "#%#", "#@?#", "#@$#", "#@%#", "$$", "$@$", "##+js", "#@#+js"]

    static let typeMap: [String: [String]] = [
        "script": ["script"], "image": ["image"], "stylesheet": ["style-sheet"], "css": ["style-sheet"],
        "font": ["font"], "media": ["media"], "xmlhttprequest": ["raw"], "xhr": ["raw"], "websocket": ["raw"],
        "other": ["raw"], "object": ["raw"], "object-subrequest": ["raw"], "ping": ["ping"], "beacon": ["ping"],
        "popup": ["popup"], "subdocument": ["document"], "frame": ["document"], "document": ["document"], "doc": ["document"],
    ]
    static let allTypes = ["script", "image", "style-sheet", "font", "media", "raw", "ping", "document", "svg-document"]

    static func parse(_ text: String, kind: TrackerKind, into target: inout FilterSet, badfilters: Set<String>) {
        // enumerateLines принимает сбегающее замыкание — inout в него не передать, работаем с копией.
        var set = target
        target = FilterSet() // чтобы большие массивы не копировались при изменении
        defer { target = set }
        text.enumerateLines { raw, _ in
            set.stats.lines += 1
            let line = raw.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("!"), !line.hasPrefix("["), !line.hasPrefix("# "), line.count < 4096 else { return }
            if badfilters.contains(line) { set.stats.skipped += 1; return }
            // Процедурная косметика, вставка стилей и скриптов, HTML-фильтры AdGuard — не для WebKit.
            if unsupportedMarkers.contains(where: { line.contains($0) }) {
                set.stats.cosmetic += 1
                set.stats.skipped += 1
                return
            }
            if parseCosmetic(line, into: &set) { return }
            parseNetwork(line, kind: kind, into: &set)
        }
    }

    /// Строки с `$badfilter` отменяют такое же правило из любого списка.
    static func badfilters(_ texts: [String]) -> Set<String> {
        var result: Set<String> = []
        for text in texts {
            text.enumerateLines { raw, _ in
                let line = raw.trimmingCharacters(in: .whitespaces)
                guard line.contains("badfilter"), let dollar = line.lastIndex(of: "$") else { return }
                let options = line[line.index(after: dollar)...].split(separator: ",").filter { $0 != "badfilter" }
                result.insert(String(line[..<dollar]) + (options.isEmpty ? "" : "$" + options.joined(separator: ",")))
            }
        }
        return result
    }

    // MARK: - Косметика

    /// `domains##selector`, `domains#@#selector`. Возвращает true, если строка косметическая.
    static func parseCosmetic(_ line: String, into set: inout FilterSet) -> Bool {
        for marker in ["#@#", "##"] {
            guard let range = line.range(of: marker) else { continue }
            let head = line[..<range.lowerBound]
            // Сетевые правила тоже бывают с «#» в адресе: косметика — только если слева домены.
            if head.contains("/") || head.contains("$") || head.contains("^") || head.contains("|") { return false }
            let selector = String(line[range.upperBound...]).trimmingCharacters(in: .whitespaces)
            set.stats.cosmetic += 1
            // Скриптлеты (##+js), процедурная косметика (#?#), вставка стилей (#$#) — не для WebKit.
            if selector.isEmpty || selector.hasPrefix("+js") || selector.hasPrefix("^") || head.hasSuffix("#") || head.hasSuffix("?") || head.hasSuffix("$")
                || selector.contains(":style(") || selector.contains(":remove(") {
                set.stats.skipped += 1
                return true
            }
            let (positive, negative) = domains(String(head), separator: ",")
            if positive == nil || negative == nil { set.stats.skipped += 1; return true }
            if marker == "#@#" {
                if positive!.isEmpty { set.genericExceptions[selector] = [] }
                else { set.genericExceptions[selector, default: []].formUnion(positive!) }
                return true
            }
            if positive!.isEmpty && negative!.isEmpty {
                set.genericSelectors.append(selector)
            } else if positive!.isEmpty {
                // Везде, кроме перечисленных: это частное правило с исключениями.
                set.specificSelectors["!" + negative!.sorted().joined(separator: "|"), default: []].append(selector)
            } else {
                set.specificSelectors[positive!.sorted().joined(separator: "|"), default: []].append(selector)
            }
            return true
        }
        return false
    }

    // MARK: - Сеть

    static func parseNetwork(_ line: String, kind: TrackerKind, into set: inout FilterSet) {
        var body = Substring(line)
        let exception = body.hasPrefix("@@")
        if exception { body = body.dropFirst(2) }
        var options: [Substring] = []
        // Опции — после последнего «$», если это не часть регулярного выражения.
        if let dollar = body.lastIndex(of: "$"), !(body.hasPrefix("/") && body[body.index(after: dollar)...].contains("/")) {
            options = body[body.index(after: dollar)...].split(separator: ",")
            body = body[..<dollar]
        }
        set.stats.network += 1
        let pattern = String(body)
        if pattern.hasPrefix("/") && pattern.hasSuffix("/") && pattern.count > 2 { set.stats.skipped += 1; return }

        var rule = NetworkRule(urlFilter: "")
        var include: Set<String> = []
        var exclude: Set<String> = []
        var pageLevel = false
        var cosmeticOff: (generic: Bool, specific: Bool) = (false, false)
        var removeParam: String?
        var urlskip: String?
        var toDomains: [String] = []

        for raw in options {
            let option = raw.lowercased()
            let negated = option.hasPrefix("~")
            let name = String(negated ? option.dropFirst() : Substring(option))
            let key = String(name.split(separator: "=", maxSplits: 1).first ?? "")
            let value = name.contains("=") ? String(raw.split(separator: "=", maxSplits: 1).last ?? "") : ""
            switch key {
            case "third-party", "3p": rule.loadType = negated ? "first-party" : "third-party"
            case "first-party", "1p": rule.loadType = negated ? "third-party" : "first-party"
            case "domain", "from":
                let (positive, negative) = domains(value, separator: "|")
                guard let positive, let negative else { set.stats.skipped += 1; return }
                rule.ifDomains = positive.map { "*" + $0 }
                rule.unlessDomains = negative.map { "*" + $0 }
            case "to":
                let (positive, _) = domains(value, separator: "|")
                guard let positive, !positive.isEmpty else { set.stats.skipped += 1; return }
                toDomains = positive
            case "match-case": rule.caseSensitive = true
            case "all": break // все типы, включая страницу целиком
            case "redirect", "empty", "mp4": break // блокировка без заглушки
            case "removeparam", "queryprune":
                removeParam = value
            case "urlskip":
                urlskip = value
            case "elemhide", "ehide": cosmeticOff = (true, true)
            case "generichide", "ghide": cosmeticOff.generic = true
            case "specifichide", "shide": cosmeticOff.specific = true
            case "popunder": include.insert("popup")
            case "badfilter": return // само правило-отмена ничего не блокирует
            default:
                if ignoredOptions.contains(key) {
                    continue
                } else if let types = typeMap[key] {
                    if (key == "document" || key == "doc") && exception { pageLevel = true }
                    if key == "subdocument" || key == "frame" { rule.loadContext = negated ? nil : "child-frame" }
                    if negated { exclude.formUnion(types) } else { include.formUnion(types) }
                } else if unsupportedOptions.contains(key) {
                    set.stats.skipped += 1
                    return
                } else {
                    set.stats.skipped += 1
                    return
                }
            }
        }

        // Метки слежки в ссылках — в собственный очиститель ссылок.
        if let removeParam {
            if !exception, !removeParam.isEmpty, !removeParam.hasPrefix("/"), !removeParam.hasPrefix("~"),
               removeParam.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || "_-.".contains($0)) }) {
                if let host = plainHost(pattern), rule.ifDomains.isEmpty {
                    set.siteRemoveParams[host, default: []].insert(removeParam)
                } else if pattern.isEmpty || pattern == "*" {
                    if rule.ifDomains.isEmpty { set.removeParams.insert(removeParam) }
                    else { for d in rule.ifDomains { set.siteRemoveParams[String(d.dropFirst()), default: []].insert(removeParam) } }
                }
            }
            return
        }
        // Переходники: «||host/path$urlskip=?param» — настоящий адрес в параметре.
        if let urlskip {
            if !exception, urlskip.hasPrefix("?"), let (host, path) = hostAndPath(pattern) {
                set.redirectors[host, default: []].append(RedirectRule(pathPrefix: path, parameter: String(urlskip.dropFirst())))
            }
            return
        }

        // Косметические исключения для страницы.
        if exception, cosmeticOff.generic || cosmeticOff.specific {
            if let host = plainHost(pattern) {
                if cosmeticOff.generic { set.noGenericCosmetics.insert(host) }
                if cosmeticOff.specific { set.noSpecificCosmetics.insert(host) }
            }
            if include.isEmpty && !pageLevel { return }
        }
        if exception && pageLevel {
            if let host = plainHost(pattern) { set.pageExceptions.insert(host) }
            return
        }

        // Типы ресурсов.
        if !include.isEmpty || !exclude.isEmpty {
            var types = include.isEmpty ? Set(allTypes) : include
            types.subtract(exclude)
            if types.isEmpty { set.stats.skipped += 1; return }
            rule.resourceTypes = types.sorted()
        }

        // Опасные адреса (вредоносное ПО, фишинг) — ещё и в собственное предупреждение до загрузки страницы.
        if !exception, kind == .malware, rule.ifDomains.isEmpty, let (host, path) = hostAndPath(pattern) {
            set.danger[host, default: []].append(path)
        }

        // «$to=» без адреса: правило для каждого домена назначения.
        let patterns = toDomains.isEmpty ? [pattern] : toDomains.map { "||\($0)^" }
        for p in patterns {
            guard let regex = regex(p) else { set.stats.skipped += 1; continue }
            if regex == ".*" && rule.ifDomains.isEmpty && rule.unlessDomains.isEmpty && rule.resourceTypes.isEmpty && rule.loadType == nil {
                set.stats.skipped += 1 // «блокировать всё» — явно не то, что имели в виду
                continue
            }
            var r = rule
            r.urlFilter = regex
            if exception {
                set.exceptions.append(r)
                if let host = plainHost(p), r.ifDomains.isEmpty, r.resourceTypes.isEmpty { set.allowedHosts.insert(host) }
            } else {
                set.blocks.append(r)
                if let host = plainHost(p), r.ifDomains.isEmpty, r.loadType != "first-party" {
                    set.trackers[host] = set.trackers[host] ?? kind
                }
            }
        }
    }

    // MARK: - Помощники

    /// Домены через разделитель: положительные и с «~». nil — запись, которую WebKit не поймёт
    /// (не ASCII, «*.» в конце для всех зон, регулярное выражение).
    static func domains(_ text: String, separator: Character) -> (positive: [String]?, negative: [String]?) {
        var positive: [String] = []
        var negative: [String] = []
        for part in text.split(separator: separator) {
            var d = part.trimmingCharacters(in: .whitespaces).lowercased()
            let neg = d.hasPrefix("~")
            if neg { d.removeFirst() }
            if d.hasPrefix("*.") { d.removeFirst(2) }
            if d.isEmpty { continue }
            guard d.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "-") }), !d.hasSuffix("."), d.contains(".") || d == "localhost" else {
                // «google.*» и подобные: пропускаем запись; если это было единственное условие — правило тоже.
                if neg { continue }
                return (nil, nil)
            }
            if neg { negative.append(d) } else { positive.append(d) }
        }
        return (positive, negative)
    }

    /// `||host^` → host.
    static func plainHost(_ pattern: String) -> String? {
        guard pattern.hasPrefix("||") else { return nil }
        var p = pattern.dropFirst(2)
        if p.hasSuffix("^") { p = p.dropLast() } else if p.hasSuffix("^|") { p = p.dropLast(2) }
        let host = p.lowercased()
        guard !host.isEmpty, host.contains("."), host.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "-") }) else { return nil }
        return host
    }

    /// `||host/path^` → (host, "/path"); путь без «*» и «^».
    static func hostAndPath(_ pattern: String) -> (String, String)? {
        guard pattern.hasPrefix("||") else { return nil }
        var p = String(pattern.dropFirst(2))
        while p.hasSuffix("^") || p.hasSuffix("|") || p.hasSuffix("*") { p.removeLast() }
        let slash = p.firstIndex(of: "/")
        let host = String(slash.map { p[..<$0] } ?? Substring(p)).lowercased()
        let path = slash.map { String(p[$0...]) } ?? ""
        guard !host.isEmpty, host.contains("."), host.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "-") }),
              !path.contains("*"), !path.contains("^") else { return nil }
        return (host, path)
    }

    /// Шаблон Adblock → регулярное выражение блокировщика WebKit.
    ///
    /// WebKit понимает подмножество регулярных выражений: «.», «*», «+», «?», наборы символов, группы, «^» и «$».
    /// Альтернативы «|» нет, поэтому разделитель «^» в конце становится «([/:?=&].*)?$» (разделитель или конец).
    static func regex(_ raw: String) -> String? {
        var p = Substring(raw)
        var prefix = ""
        var anchoredEnd = false
        if p.hasPrefix("||") {
            p = p.dropFirst(2)
            prefix = "^[a-z][a-z0-9.+-]*://([a-z0-9.-]+\\.)?"
        } else if p.hasPrefix("|") {
            p = p.dropFirst()
            prefix = "^"
        }
        if p.hasSuffix("|") {
            p = p.dropLast()
            anchoredEnd = true
        }
        if prefix == "^" { while p.hasPrefix("*") { p = p.dropFirst(); prefix = "" } }
        if prefix.isEmpty { while p.hasPrefix("*") { p = p.dropFirst() } }
        if !anchoredEnd { while p.hasSuffix("*") { p = p.dropLast() } }
        if p.isEmpty { return prefix.isEmpty ? ".*" : nil }

        var out = prefix
        let chars = Array(p)
        for (i, ch) in chars.enumerated() {
            guard ch.isASCII, let ascii = ch.asciiValue, ascii >= 0x20, ascii < 0x7F else { return nil }
            switch ch {
            case "*": out += ".*"
            case "^": out += i == chars.count - 1 && !anchoredEnd ? "([/:?=&].*)?$" : "[/:?=&]"
            case ".", "+", "?", "$", "{", "}", "(", ")", "[", "]", "\\", "|": out += "\\" + String(ch)
            default: out.append(ch)
            }
        }
        if anchoredEnd { out += "$" }
        return out.count <= 2000 ? out : nil
    }
}
