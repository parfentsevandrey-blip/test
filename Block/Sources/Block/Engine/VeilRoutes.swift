import AppKit

/// Правила блокировки в Veil: ссылка veil://block-list. Veil принимает только имена доменов и хранит
/// список Block отдельно от ваших правил; пустой список убирает его целиком.
enum VeilRoutes {
    static var installed: Bool { Handlers.appURL(CatalogApp.veil.bundleID) != nil }

    static func url(domains: [String]) -> URL? {
        let list = Array(Set(domains.map { $0.lowercased() })).filter { !AppleNetwork.isUntouchable($0) }.sorted()
        guard let json = try? JSONEncoder().encode(list) else { return nil }
        let encoded = json.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        return URL(string: "veil://block-list?owner=block&domains=\(encoded)")
    }

    /// Домены групп: точные имена и суффиксы (Veil закрывает их вместе с поддоменами).
    static func domains(_ groups: [NetGroup]) -> [String] {
        groups.flatMap { $0.hosts + $0.suffixes }
    }

    @discardableResult
    static func send(_ groups: [NetGroup]) -> Bool {
        guard installed, let url = url(domains: domains(groups)) else { return false }
        let configuration = NSWorkspace.OpenConfiguration()
        configuration.activates = false
        NSWorkspace.shared.open(url, configuration: configuration)
        return true
    }
}
