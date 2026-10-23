import Foundation

/// Готовый к компиляции список правил WebKit.
nonisolated struct RuleChunk: Sendable {
    var name: String
    var json: String
    var count: Int
}

/// Из разобранных фильтров — списки правил блокировщика WebKit (JSON).
///
/// Исключения в WebKit действуют только внутри своего списка («ignore-previous-rules» отменяет предыдущие
/// правила этого же списка), поэтому сетевые правила делятся на части, а все исключения дописываются
/// в конец каждой. Предел WebKit — 150 000 правил в списке; берём с запасом.
nonisolated enum RuleCompiler {
    static let chunkLimit = 90_000
    /// Сколько селекторов в одном правиле скрытия: одно неверное правило не должно ломать сотни других.
    static let selectorsPerRule = 60

    static func chunks(_ set: FilterSet, validSelectors: Set<String>? = nil) -> [RuleChunk] {
        var result: [RuleChunk] = []

        // Сеть: блокировки без повторов, затем исключения.
        var seen = Set<NetworkRule>()
        let blocks = set.blocks.filter { seen.insert($0).inserted }
        var seenExceptions = Set<NetworkRule>()
        var exceptions = set.exceptions.filter { seenExceptions.insert($0).inserted }.map { $0.json("ignore-previous-rules") }
        for host in set.pageExceptions.sorted() {
            exceptions.append(["trigger": ["url-filter": ".*", "if-domain": ["*" + host]], "action": ["type": "ignore-previous-rules"]])
        }
        let parts = max(1, Int((Double(blocks.count) / Double(chunkLimit)).rounded(.up)))
        let size = Int((Double(blocks.count) / Double(parts)).rounded(.up))
        for i in 0..<parts {
            let slice = blocks[min(blocks.count, i * size)..<min(blocks.count, (i + 1) * size)]
            var rules: [[String: Any]] = slice.map { $0.json("block") }
            if i == 0 { rules.append(contentsOf: builtinRules) }
            rules.append(contentsOf: exceptions)
            result.append(RuleChunk(name: "network-\(i)", json: encode(rules), count: rules.count))
        }

        // Косметика: общие селекторы группами, частные — по наборам доменов.
        func valid(_ s: String) -> Bool { validSelectors.map { $0.contains(s) } ?? true }
        var generic: [[String: Any]] = []
        var genericOnly: [String] = []
        var seenSelectors = Set<String>()
        for selector in set.genericSelectors where seenSelectors.insert(selector).inserted && valid(selector) {
            if let domains = set.genericExceptions[selector] {
                if domains.isEmpty { continue }
                // Скрыть везде, кроме этих сайтов — отдельным правилом.
                generic.append(hide(selector, unless: domains.sorted().map { "*" + $0 }))
            } else {
                genericOnly.append(selector)
            }
        }
        for start in stride(from: 0, to: genericOnly.count, by: selectorsPerRule) {
            generic.append(hide(genericOnly[start..<min(genericOnly.count, start + selectorsPerRule)].joined(separator: ", ")))
        }
        for host in set.noGenericCosmetics.sorted() {
            generic.append(["trigger": ["url-filter": ".*", "if-domain": ["*" + host]], "action": ["type": "ignore-previous-rules"]])
        }
        for host in set.pageExceptions.sorted() {
            generic.append(["trigger": ["url-filter": ".*", "if-domain": ["*" + host]], "action": ["type": "ignore-previous-rules"]])
        }
        if !generic.isEmpty { result.append(RuleChunk(name: "cosmetic-generic", json: encode(generic), count: generic.count)) }

        var specific: [[String: Any]] = []
        for key in set.specificSelectors.keys.sorted() {
            let selectors = Array(Set(set.specificSelectors[key] ?? [])).sorted().filter(valid)
            guard !selectors.isEmpty else { continue }
            let negative = key.hasPrefix("!")
            let domains = (negative ? String(key.dropFirst()) : key).split(separator: "|").map { "*" + $0 }
            for start in stride(from: 0, to: selectors.count, by: selectorsPerRule) {
                let group = selectors[start..<min(selectors.count, start + selectorsPerRule)].joined(separator: ", ")
                specific.append(negative ? hide(group, unless: domains) : hide(group, if: domains))
            }
        }
        for host in set.noSpecificCosmetics.union(set.pageExceptions).sorted() {
            specific.append(["trigger": ["url-filter": ".*", "if-domain": ["*" + host]], "action": ["type": "ignore-previous-rules"]])
        }
        // Частные правила тоже могут перевалить за предел — делим.
        for (i, start) in stride(from: 0, to: specific.count, by: chunkLimit).enumerated() {
            let part = Array(specific[start..<min(specific.count, start + chunkLimit)])
            result.append(RuleChunk(name: "cosmetic-specific-\(i)", json: encode(part), count: part.count))
        }
        return result
    }

    /// Собственные правила Ward поверх списков.
    static var builtinRules: [[String: Any]] {
        [
            // «Аудит ссылок» (атрибут ping): браузер сообщает сайту о каждом переходе — не нужно.
            ["trigger": ["url-filter": ".*", "resource-type": ["ping"]], "action": ["type": "block"]],
        ] + clickIDPixels
    }

    /// Пиксели и маяки к чужим серверам с меткой клика в адресе (gclid, fbclid…): так рекламная сеть узнаёт,
    /// что вы пришли по её объявлению. Метку из адреса подзапроса WebKit вырезать не даёт — запрос не уходит.
    static var clickIDPixels: [[String: Any]] {
        ["gclid", "fbclid", "msclkid", "yclid", "dclid", "ttclid", "twclid", "li_fat_id", "_hsenc", "mc_eid", "rb_clickid", "irclickid"].map { name -> [String: Any] in
            ["trigger": ["url-filter": "[?&]\(name)=", "load-type": ["third-party"], "resource-type": ["image", "ping", "raw"]], "action": ["type": "block"]]
        }
    }

    static func hide(_ selector: String, if domains: [String] = [], unless: [String] = []) -> [String: Any] {
        var trigger: [String: Any] = ["url-filter": ".*"]
        if !domains.isEmpty { trigger["if-domain"] = domains } else if !unless.isEmpty { trigger["unless-domain"] = unless }
        return ["trigger": trigger, "action": ["type": "css-display-none", "selector": selector]]
    }

    static func encode(_ rules: [[String: Any]]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: rules, options: []) else { return "[]" }
        return String(decoding: data, as: UTF8.self)
    }

    /// Все селекторы, которые пойдут в правила, — для проверки движком страниц.
    static func selectors(_ set: FilterSet) -> [String] {
        var all = Set(set.genericSelectors)
        for list in set.specificSelectors.values { all.formUnion(list) }
        return all.sorted()
    }
}
