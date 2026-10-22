import CryptoKit
import Foundation

/// Список фильтров: откуда берётся, что блокирует, включён ли по умолчанию.
nonisolated struct FilterList: Identifiable, Hashable, Sendable {
    var id: String
    var title: String
    var summary: String
    var url: String
    var kind: TrackerKind
    var enabledByDefault: Bool

    /// Списки — те же, что качает scripts/fetch-lists.sh при сборке.
    static let all: [FilterList] = [
        FilterList(id: "easylist", title: "EasyList", summary: "Основной список рекламы: баннеры, всплывающие окна, рекламные сети.",
                   url: "https://easylist.to/easylist/easylist.txt", kind: .ads, enabledByDefault: true),
        FilterList(id: "easyprivacy", title: "EasyPrivacy", summary: "Трекеры и счётчики: аналитика, пиксели, сбор данных о посещениях.",
                   url: "https://easylist.to/easylist/easyprivacy.txt", kind: .tracking, enabledByDefault: true),
        FilterList(id: "ubo-filters", title: "uBlock Origin", summary: "Фильтры команды uBlock Origin: то, что пропускает EasyList.",
                   url: "https://ublockorigin.github.io/uAssets/filters/filters.txt", kind: .ads, enabledByDefault: true),
        FilterList(id: "ubo-privacy", title: "uBlock Origin — приватность", summary: "Дополнительная защита от слежки и метки в ссылках.",
                   url: "https://ublockorigin.github.io/uAssets/filters/privacy.txt", kind: .tracking, enabledByDefault: true),
        FilterList(id: "ubo-badware", title: "uBlock Origin — опасные сайты", summary: "Мошенничество, поддельные загрузки, вредоносные страницы.",
                   url: "https://ublockorigin.github.io/uAssets/filters/badware.txt", kind: .malware, enabledByDefault: true),
        FilterList(id: "urlhaus", title: "URLhaus", summary: "Адреса, с которых сейчас распространяют вредоносное ПО (abuse.ch).",
                   url: "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-ag-online.txt", kind: .malware, enabledByDefault: true),
        FilterList(id: "peterlowe", title: "Peter Lowe", summary: "Рекламные и следящие серверы — проверенный годами список.",
                   url: "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext", kind: .tracking, enabledByDefault: true),
        FilterList(id: "ruadlist", title: "RU AdList", summary: "Реклама и трекеры на русскоязычных сайтах.",
                   url: "https://easylist-downloads.adblockplus.org/advblock.txt", kind: .ads, enabledByDefault: true),
        FilterList(id: "cookies", title: "Окна про cookie", summary: "Скрывает баннеры согласия на cookie (EasyList Cookie).",
                   url: "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt", kind: .annoyance, enabledByDefault: true),
        FilterList(id: "ubo-unbreak", title: "Починка сайтов", summary: "Исключения uBlock Origin: чтобы блокировка не ломала нужное.",
                   url: "https://ublockorigin.github.io/uAssets/filters/unbreak.txt", kind: .ads, enabledByDefault: true),
        FilterList(id: "adguard-url", title: "Метки в ссылках", summary: "Параметры слежки в адресах (utm, fbclid и сотни других) — AdGuard.",
                   url: "https://filters.adtidy.org/extension/ublock/filters/17.txt", kind: .tracking, enabledByDefault: true),
        FilterList(id: "annoyances", title: "Помехи", summary: "Виджеты соцсетей, всплывающие подписки, чаты поддержки, «приложение лучше».",
                   url: "https://secure.fanboy.co.nz/fanboy-annoyance.txt", kind: .annoyance, enabledByDefault: false),
    ]

    static func named(_ id: String) -> FilterList? { all.first { $0.id == id } }
}

/// Тексты списков: скачанные (свежие) или из бандла (на момент сборки).
nonisolated enum FilterStorage {
    static var folder: URL {
        let dir = AppPaths.support.appendingPathComponent("Filters", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func file(_ list: FilterList) -> URL { folder.appendingPathComponent(list.id + ".txt") }

    static func text(_ list: FilterList) -> String? {
        if let data = try? Data(contentsOf: file(list)) { return String(decoding: data, as: UTF8.self) }
        if let url = Bundle.main.url(forResource: list.id, withExtension: "txt", subdirectory: "Filters"),
           let data = try? Data(contentsOf: url) {
            return String(decoding: data, as: UTF8.self)
        }
        return nil
    }

    /// Когда список обновлялся: файл скачанного или дата сборки для встроенного.
    static func updated(_ list: FilterList) -> Date? {
        if let date = (try? FileManager.default.attributesOfItem(atPath: file(list).path))?[.modificationDate] as? Date { return date }
        if let url = Bundle.main.url(forResource: "DATE", withExtension: nil, subdirectory: "Filters"),
           let text = try? String(contentsOf: url, encoding: .utf8) {
            return ISO8601DateFormatter().date(from: text.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return nil
    }

    /// Скачать свежую версию. Ответ проверяется: это должен быть список фильтров, а не страница ошибки.
    static func download(_ list: FilterList) async throws -> Bool {
        guard let url = URL(string: list.url) else { return false }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 60)
        request.setValue("Ward", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200, data.count > 1000 else { return false }
        let head = String(decoding: data.prefix(4096), as: UTF8.self)
        guard !head.lowercased().contains("<html") else { return false }
        let target = file(list)
        let old = try? Data(contentsOf: target)
        try data.write(to: target, options: .atomic)
        return old != data
    }
}

/// Итог разбора включённых списков: правила WebKit и данные для собственных защит.
nonisolated struct FilterBuild: Sendable {
    var set: FilterSet
    var chunks: [RuleChunk]
    /// Отпечаток содержимого: если не изменился, скомпилированные правила берутся из хранилища WebKit.
    var fingerprint: String
}

nonisolated enum FilterEngine {
    /// Разобрать включённые списки. `validSelectors` — селекторы, которые понял движок страниц
    /// (неверный селектор WebKit не принимает, и весь список перестаёт работать).
    static func build(lists: [FilterList], validSelectors: Set<String>? = nil, parsed: FilterSet? = nil) -> FilterBuild {
        let set = parsed ?? parse(lists: lists)
        let chunks = RuleCompiler.chunks(set, validSelectors: validSelectors)
        var hasher = SHA256()
        for chunk in chunks { hasher.update(data: Data(chunk.json.utf8)) }
        let digest = hasher.finalize().map { String(format: "%02x", $0) }.joined()
        return FilterBuild(set: set, chunks: chunks, fingerprint: String(digest.prefix(16)))
    }

    static func parse(lists: [FilterList]) -> FilterSet {
        let texts = lists.compactMap { list in FilterStorage.text(list).map { (list, $0) } }
        let badfilters = FilterParser.badfilters(texts.map(\.1))
        var set = FilterSet()
        for (list, text) in texts {
            FilterParser.parse(text, kind: list.kind, into: &set, badfilters: badfilters)
        }
        return set
    }
}
