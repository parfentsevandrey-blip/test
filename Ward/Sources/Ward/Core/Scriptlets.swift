import Foundation

/// Скриптлеты uBlock Origin (`example.com##+js(set-constant, adBlockDetected, false)`) и процедурная косметика
/// (`example.com##div:has-text(Реклама):upward(2)`).
///
/// Блокировщик WebKit их не понимает: это маленькие программы, которые выполняются на странице. Ward
/// исполняет их своей библиотекой (Scripts.scriptletLibrary и Scripts.procedural) — так работает защита от
/// «отключите блокировщик», реклама, которую сайт вставляет скриптом, и встроенная реклама YouTube.
nonisolated enum ScriptletCatalog {
    /// Короткие имена → полные (как в библиотеке Ward).
    static let aliases: [String: String] = [
        "set": "set-constant", "set-constant": "set-constant",
        "aopr": "abort-on-property-read", "abort-on-property-read": "abort-on-property-read",
        "aopw": "abort-on-property-write", "abort-on-property-write": "abort-on-property-write",
        "acs": "abort-current-script", "abort-current-script": "abort-current-script",
        "acis": "abort-current-script", "abort-current-inline-script": "abort-current-script",
        "aost": "abort-on-stack-trace", "abort-on-stack-trace": "abort-on-stack-trace",
        "nostif": "no-setTimeout-if", "no-setTimeout-if": "no-setTimeout-if", "prevent-setTimeout": "no-setTimeout-if", "setTimeout-defuser": "no-setTimeout-if",
        "nosiif": "no-setInterval-if", "no-setInterval-if": "no-setInterval-if", "prevent-setInterval": "no-setInterval-if", "setInterval-defuser": "no-setInterval-if",
        "nano-stb": "nano-setTimeout-booster", "nano-setTimeout-booster": "nano-setTimeout-booster", "adjust-setTimeout": "nano-setTimeout-booster",
        "nano-sib": "nano-setInterval-booster", "nano-setInterval-booster": "nano-setInterval-booster", "adjust-setInterval": "nano-setInterval-booster",
        "aeld": "addEventListener-defuser", "addEventListener-defuser": "addEventListener-defuser", "prevent-addEventListener": "addEventListener-defuser",
        "nowoif": "no-window-open-if", "no-window-open-if": "no-window-open-if", "window.open-defuser": "no-window-open-if", "prevent-window-open": "no-window-open-if",
        "ra": "remove-attr", "remove-attr": "remove-attr",
        "rc": "remove-class", "remove-class": "remove-class",
        "rmnt": "remove-node-text", "remove-node-text": "remove-node-text",
        "rpnt": "replace-node-text", "replace-node-text": "replace-node-text", "trusted-rpnt": "replace-node-text", "trusted-replace-node-text": "replace-node-text",
        "noeval": "noeval", "silent-noeval": "noeval",
        "noeval-if": "noeval-if", "prevent-eval-if": "noeval-if",
        "nowebrtc": "nowebrtc",
        "nobab": "bab-defuser", "bab-defuser": "bab-defuser",
        "nofab": "fuckadblock-defuser", "fuckadblock.js-3.2.0": "fuckadblock-defuser",
        "no-xhr-if": "no-xhr-if", "prevent-xhr": "no-xhr-if", "trusted-prevent-xhr": "no-xhr-if",
        "no-fetch-if": "no-fetch-if", "prevent-fetch": "no-fetch-if", "trusted-prevent-fetch": "no-fetch-if",
        "set-local-storage-item": "set-local-storage-item", "trusted-set-local-storage-item": "set-local-storage-item",
        "set-session-storage-item": "set-session-storage-item", "trusted-set-session-storage-item": "set-session-storage-item",
        "set-cookie": "set-cookie", "trusted-set-cookie": "set-cookie",
        "remove-cookie": "remove-cookie", "cookie-remover": "remove-cookie",
        "href-sanitizer": "href-sanitizer",
        "json-prune": "json-prune",
        "json-prune-fetch-response": "json-prune-fetch-response",
        "json-prune-xhr-response": "json-prune-xhr-response",
        "trusted-replace-xhr-response": "replace-xhr-response",
        "trusted-rpfr": "replace-fetch-response", "trusted-replace-fetch-response": "replace-fetch-response",
        "refresh-defuser": "refresh-defuser", "prevent-refresh": "refresh-defuser",
        "norafif": "no-requestAnimationFrame-if", "no-requestAnimationFrame-if": "no-requestAnimationFrame-if",
        "popads-dummy": "popads-dummy", "popads.net": "popads-dummy",
        "disable-newtab-links": "disable-newtab-links",
        "set-attr": "set-attr", "trusted-set-attr": "set-attr",
        "call-nothrow": "call-nothrow",
        "trusted-set": "set-constant", "trusted-set-constant": "set-constant",
        "close-window": "close-window", "window-close-if": "close-window",
    ]

    /// Скриптлеты, которые разрешены только в доверенных списках (команды uBlock Origin): они могут
    /// подставлять любые значения и переписывать ответы сервера.
    static let trustedOnly: Set<String> = [
        "trusted-rpnt", "trusted-replace-node-text", "trusted-replace-xhr-response", "trusted-rpfr", "trusted-replace-fetch-response",
        "trusted-set-local-storage-item", "trusted-set-session-storage-item", "trusted-set-cookie", "trusted-set-attr",
        "trusted-set", "trusted-set-constant", "trusted-prevent-xhr", "trusted-prevent-fetch",
        "replace-node-text", "rpnt",
    ]

    /// Аргументы `+js(имя, a, b\, c, 'd')` → ["имя", "a", "b, c", "d"].
    static func arguments(_ body: String) -> [String] {
        var parts: [String] = []
        var current = ""
        var escaped = false
        for ch in body {
            if escaped {
                if ch != "," { current.append("\\") }
                current.append(ch)
                escaped = false
            } else if ch == "\\" {
                escaped = true
            } else if ch == "," {
                parts.append(current)
                current = ""
            } else {
                current.append(ch)
            }
        }
        if escaped { current.append("\\") }
        parts.append(current)
        return parts.map { raw in
            let t = raw.trimmingCharacters(in: .whitespaces)
            if t.count >= 2, let f = t.first, let l = t.last, (f == "'" && l == "'") || (f == "\"" && l == "\"") {
                return String(t.dropFirst().dropLast())
            }
            return t
        }
    }

    /// Вызов в виде JSON: `["set-constant","a.b","false"]`. nil — скриптлет Ward не умеет или он запрещён
    /// для недоверенного списка.
    static func call(_ body: String, trusted: Bool) -> String? {
        var args = arguments(body)
        guard let name = args.first, let canonical = aliases[name] else { return nil }
        if trustedOnly.contains(name) && !trusted { return nil }
        args[0] = canonical
        guard let data = try? JSONSerialization.data(withJSONObject: args) else { return nil }
        return String(decoding: data, as: UTF8.self)
    }

    /// Запись в индексе: «1» или «0» (доверенный ли список) и вызов.
    static func entry(_ call: String, trusted: Bool) -> String { (trusted ? "1" : "0") + call }

    /// Домены правила: «youtube.com», «~good.com», «youtube.*» (любая зона).
    static func domains(_ text: String) -> (positive: [String], negative: [String]) {
        var positive: [String] = []
        var negative: [String] = []
        for part in text.split(separator: ",") {
            var d = part.trimmingCharacters(in: .whitespaces).lowercased()
            let neg = d.hasPrefix("~")
            if neg { d.removeFirst() }
            guard !d.isEmpty, d.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || ".-*".contains($0)) }) else { continue }
            if neg { negative.append(d) } else { positive.append(d) }
        }
        return (positive, negative)
    }

    /// Ключи, по которым ищутся правила сайта: сам хост, его «родители» и те же имена с любой зоной.
    static func keys(for host: String) -> [String] {
        let h = host.lowercased()
        var keys = Hosts.chain(h)
        if keys.isEmpty { keys = [h] }
        let site = Hosts.site(h)
        let tld = String(site.split(separator: ".").dropFirst().joined(separator: "."))
        if !tld.isEmpty {
            for k in Hosts.chain(h) where k.hasSuffix("." + tld) {
                keys.append(String(k.dropLast(tld.count)) + "*")
            }
        }
        return keys
    }

    /// Операторы процедурной косметики: с ними селектор не понимает ни CSS, ни блокировщик WebKit.
    static let proceduralOperators = [
        ":has-text(", ":-abp-contains(", ":contains(", ":upward(", ":nth-ancestor(", ":matches-css(", ":matches-css-before(",
        ":matches-css-after(", ":min-text-length(", ":xpath(", ":remove(", ":style(", ":remove-attr(", ":remove-class(",
        ":watch-attr(", ":matches-attr(", ":matches-prop(", ":matches-path(", ":-abp-has(", ":others(", ":if(", ":if-not(",
        ":matches-media(",
    ]

    static func isProcedural(_ selector: String) -> Bool {
        proceduralOperators.contains { selector.contains($0) }
    }
}

extension FilterParser {
    /// Скриптлеты и процедурная косметика. true — строка разобрана (или осознанно пропущена).
    nonisolated static func parseSpecial(_ line: String, trusted: Bool, into set: inout FilterSet) -> Bool {
        // Скриптлеты и исключения для них.
        for (marker, exception) in [("#@#+js(", true), ("##+js(", false)] {
            guard let range = line.range(of: marker) else { continue }
            set.stats.cosmetic += 1
            let head = String(line[..<range.lowerBound])
            guard line.hasSuffix(")") else { set.stats.skipped += 1; return true }
            let body = String(line[range.upperBound..<line.index(before: line.endIndex)])
            let (positive, negative) = ScriptletCatalog.domains(head)
            if exception {
                // «#@#+js()» без аргументов — выключить все скриптлеты на этих сайтах.
                let key = body.trimmingCharacters(in: .whitespaces).isEmpty ? "" : (ScriptletCatalog.call(body, trusted: true) ?? "?")
                for d in positive { set.scriptletExceptions[d, default: []].insert(key) }
                return true
            }
            guard !positive.isEmpty, let call = ScriptletCatalog.call(body, trusted: trusted) else {
                set.stats.skipped += 1 // общие скриптлеты для всех сайтов и незнакомые Ward
                return true
            }
            for d in positive { set.scriptlets[d, default: []].append(ScriptletCatalog.entry(call, trusted: trusted)) }
            for d in negative { set.scriptletExceptions[d, default: []].insert(call) }
            return true
        }

        // Вставка стилей AdGuard: «example.com#$#.banner { display: none !important; }» → :style().
        if let range = line.range(of: "#$#"), !line.contains("#$?#") {
            set.stats.cosmetic += 1
            let head = String(line[..<range.lowerBound])
            let rule = line[range.upperBound...].trimmingCharacters(in: .whitespaces)
            guard let open = rule.firstIndex(of: "{"), rule.hasSuffix("}"), !head.isEmpty else { set.stats.skipped += 1; return true }
            let selector = rule[..<open].trimmingCharacters(in: .whitespaces)
            let css = rule[rule.index(after: open)..<rule.index(before: rule.endIndex)].trimmingCharacters(in: .whitespaces)
            guard !selector.isEmpty, !css.isEmpty, !css.contains("url(") else { set.stats.skipped += 1; return true }
            addProcedural(selector + ":style(" + css + ")", head: head, into: &set)
            return true
        }

        // Процедурная косметика: «#?#» или «##» с операторами uBlock.
        for marker in ["#?#", "##"] {
            guard let range = line.range(of: marker) else { continue }
            let head = String(line[..<range.lowerBound])
            if head.contains("/") || head.contains("$") || head.contains("^") || head.contains("|") || head.hasSuffix("#") || head.hasSuffix("@") { return false }
            let selector = String(line[range.upperBound...]).trimmingCharacters(in: .whitespaces)
            guard marker == "#?#" || ScriptletCatalog.isProcedural(selector) else { return false }
            set.stats.cosmetic += 1
            guard !head.isEmpty, !selector.isEmpty, !selector.hasPrefix("^") else { set.stats.skipped += 1; return true }
            addProcedural(selector, head: head, into: &set)
            return true
        }
        return false
    }

    nonisolated static func addProcedural(_ selector: String, head: String, into set: inout FilterSet) {
        let (positive, _) = ScriptletCatalog.domains(head)
        guard !positive.isEmpty else { set.stats.skipped += 1; return }
        for d in positive { set.procedural[d, default: []].append(selector) }
    }
}

extension ProtectionIndex {
    /// Скриптлеты для страницы этого хоста: записи «1[…]»/«0[…]» без повторов и без исключённых.
    func scriptletCalls(for host: String) -> [String] {
        let keys = ScriptletCatalog.keys(for: host)
        var exceptions: Set<String> = []
        for k in keys { exceptions.formUnion(scriptletExceptions[k] ?? []) }
        if exceptions.contains("") { return [] }
        var seen: Set<String> = []
        var result: [String] = []
        for k in keys {
            for entry in scriptlets[k] ?? [] where !exceptions.contains(String(entry.dropFirst())) && seen.insert(String(entry.dropFirst())).inserted {
                result.append(entry)
            }
        }
        return result
    }

    /// Процедурные селекторы для страницы этого хоста.
    func proceduralSelectors(for host: String) -> [String] {
        var seen: Set<String> = []
        var result: [String] = []
        for k in ScriptletCatalog.keys(for: host) {
            for s in procedural[k] ?? [] where seen.insert(s).inserted { result.append(s) }
        }
        return result
    }
}
