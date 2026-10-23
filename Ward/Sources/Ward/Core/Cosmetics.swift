import Foundation

/// Правила скрытия по сайтам — чтобы посчитать, сколько рекламных блоков, баннеров и кнопок
/// WebKit скрыл на странице. Скрывает сам движок (правила блокировщика), здесь только учёт.
nonisolated struct CosmeticIndex: Codable, Sendable {
    nonisolated struct Except: Codable, Sendable {
        var domains: [String]
        var selectors: [String]
    }

    /// Общие селекторы, действующие везде.
    var generic: [String] = []
    /// Общий селектор → сайты, где он не действует.
    var genericExcept: [String: [String]] = [:]
    /// Сайт → свои селекторы.
    var specific: [String: [String]] = [:]
    /// «Везде, кроме этих сайтов».
    var everywhereBut: [Except] = []
    var noGeneric: Set<String> = []
    var noSpecific: Set<String> = []

    init() {}

    init(_ set: FilterSet, valid: Set<String>) {
        var seen = Set<String>()
        for selector in set.genericSelectors where valid.contains(selector) && seen.insert(selector).inserted {
            if let domains = set.genericExceptions[selector] {
                if !domains.isEmpty { genericExcept[selector] = domains.sorted() }
            } else {
                generic.append(selector)
            }
        }
        for (key, list) in set.specificSelectors {
            let selectors = Array(Set(list.filter { valid.contains($0) })).sorted()
            guard !selectors.isEmpty else { continue }
            if key.hasPrefix("!") {
                everywhereBut.append(Except(domains: key.dropFirst().split(separator: "|").map(String.init), selectors: selectors))
            } else {
                for domain in key.split(separator: "|") { specific[String(domain), default: []].append(contentsOf: selectors) }
            }
        }
        noGeneric = set.noGenericCosmetics.union(set.pageExceptions)
        noSpecific = set.noSpecificCosmetics.union(set.pageExceptions)
    }

    /// Все селекторы, которые WebKit применяет на этом хосте (так же, как `if-domain`/`unless-domain` «*домен»).
    func selectors(for host: String) -> [String] {
        let chain = Set(Hosts.chain(host) + [host.lowercased()])
        var out: [String] = []
        if chain.isDisjoint(with: noGeneric) {
            out.append(contentsOf: generic)
            for (selector, domains) in genericExcept where chain.isDisjoint(with: domains) { out.append(selector) }
        }
        if chain.isDisjoint(with: noSpecific) {
            for c in chain { out.append(contentsOf: specific[c] ?? []) }
            for e in everywhereBut where chain.isDisjoint(with: e.domains) { out.append(contentsOf: e.selectors) }
        }
        return out
    }

    var isEmpty: Bool { generic.isEmpty && specific.isEmpty }
}

extension Scripts {
    /// Сколько элементов страницы попало под правила скрытия. Вложенные считаются один раз:
    /// рекламный блок с картинкой внутри — один скрытый блок, а не два.
    nonisolated static let countHidden = """
    const found = new Set();
    const take = (sel) => { document.querySelectorAll(sel).forEach((e) => found.add(e)); };
    for (let i = 0; i < list.length; i += 250) {
      const part = list.slice(i, i + 250);
      try { take(part.join(',')); } catch (e) { for (const s of part) { try { take(s); } catch (_) {} } }
    }
    let n = 0;
    for (const el of found) {
      let p = el.parentElement, inside = false;
      while (p) { if (found.has(p)) { inside = true; break; } p = p.parentElement; }
      if (!inside) n++;
    }
    return n;
    """

    /// Мост для сигналов защиты от отпечатка и следов: они работают в мире страницы и не могут писать Ward
    /// напрямую (иначе писать смогла бы и страница). Сигнал — событие на document со случайным для запуска
    /// именем; страница его не знает, а слушатель живёт в отдельном мире Ward. В `detail` — чей скрипт
    /// (для отпечатка) или что именно (адрес в домашней сети, имя cookie).
    nonisolated static func fingerprintRelay(token: String) -> String {
        """
        (() => {
          const post = (body) => { try { window.webkit.messageHandlers.wardEvent.postMessage(body); } catch (e) {} };
          const detail = (e) => (typeof e.detail === 'string' ? e.detail : '').slice(0, 200);
          for (const kind of [\(FingerprintTechnique.allCases.map { "'\($0.rawValue)'" }.joined(separator: ", "))]) {
            document.addEventListener('\(token)-' + kind, (e) => post({ type: 'fingerprint', technique: kind, script: detail(e) }), true);
          }
          for (const kind of [\(TraceSignal.allCases.map { "'\($0.rawValue)'" }.joined(separator: ", "))]) {
            document.addEventListener('\(token)-' + kind, (e) => post({ type: 'trace', what: kind, detail: detail(e) }), true);
          }
        })();
        """
    }
}

/// Следы, которые урезает скрипт приватности страницы.
nonisolated enum TraceSignal: String, CaseIterable, Sendable {
    /// Адрес, с которого вы пришли, отдан странице только как сайт.
    case referrer
    /// Cookie из скрипта страницы — с урезанным сроком жизни.
    case cookie
    /// Страница пыталась обратиться к устройству в вашей домашней сети.
    case lan
}

/// Способы снять отпечаток браузера, которые Ward замечает (и подменяет).
nonisolated enum FingerprintTechnique: String, CaseIterable, Sendable {
    case canvas, webgl, audio, fonts, voices, devices, storage, permissions

    /// Вес способа для детектора: холст, звук и перебор шрифтов почти всегда означают сбор отпечатка,
    /// список устройств и квота хранилища — бывают и по делу.
    var weight: Int {
        switch self {
        case .canvas, .audio, .fonts: 3
        case .webgl, .permissions: 2
        case .voices, .devices, .storage: 1
        }
    }

    /// С какого веса страница считается сборщиком отпечатка.
    static let threshold = 6

    var title: String {
        switch self {
        case .canvas: "Снимок холста"
        case .webgl: "Модель видеокарты"
        case .audio: "Звуковой отпечаток"
        case .fonts: "Перебор шрифтов"
        case .voices: "Голоса синтеза речи"
        case .devices: "Список камер и микрофонов"
        case .storage: "Размер диска"
        case .permissions: "Перебор разрешений"
        }
    }

    var detail: String {
        switch self {
        case .canvas: "Невидимая картинка: у каждого Mac она чуть своя"
        case .webgl: "Название GPU через WebGL"
        case .audio: "Обработка звука без звука"
        case .fonts: "Ширина текста в десятках шрифтов"
        case .voices: "Набор установленных голосов"
        case .devices: "Сколько и каких устройств подключено"
        case .storage: "Квота хранилища выдаёт объём диска"
        case .permissions: "Что вы разрешали сайтам: геопозиция, уведомления…"
        }
    }

    var icon: String {
        switch self {
        case .canvas: "paintbrush.pointed"
        case .webgl: "cpu"
        case .audio: "waveform"
        case .fonts: "textformat"
        case .voices: "person.wave.2"
        case .devices: "web.camera"
        case .storage: "internaldrive"
        case .permissions: "checklist"
        }
    }
}
