import Foundation

/// Установленная модель: файлы GGUF в папке приложения и сведения о них.
nonisolated struct InstalledModel: Codable, Identifiable, Sendable, Hashable {
    var repo: String
    var name: String
    var lab: String
    var quant: String
    var files: [String]
    var size: Int64
    var params: Double
    var activeParams: Double?
    var architecture: String?
    var contextLength: Int?
    var installedAt: Date
    var lastUsed: Date?
    var verified: Bool
    /// Проектор зрения (mmproj) — с ним модель видит изображения.
    var projector: String?

    var id: String { repo + "#" + quant }
    var folderName: String { repo.replacingOccurrences(of: "/", with: "__") }
    var folder: URL { AppPaths.models.appendingPathComponent(folderName, isDirectory: true) }
    var primaryPath: String { folder.appendingPathComponent(files.first ?? "").path }
    var projectorPath: String? {
        guard let projector else { return nil }
        let path = folder.appendingPathComponent(projector).path
        return FileManager.default.fileExists(atPath: path) ? path : nil
    }
    var vision: Bool { projectorPath != nil }
    var exists: Bool { files.allSatisfy { FileManager.default.fileExists(atPath: folder.appendingPathComponent($0).path) } }

    /// Короткое имя для API (как в Ollama): «qwen3.5-9b:q4_k_m».
    var apiName: String {
        let base = name.lowercased().replacingOccurrences(of: " ", with: "-")
        return base + ":" + quant.lowercased()
    }

    var card: ModelCard {
        Curated.cards.first(where: { $0.id == repo }) ?? ModelCard(
            id: repo, name: name, lab: lab, params: params, activeParams: activeParams, architecture: architecture,
            contextLength: contextLength, summary: "", quality: CardBuilder.estimatedQuality(params: params, active: activeParams, created: nil))
    }
}

nonisolated enum Library {
    static var manifest: URL { AppPaths.models.appendingPathComponent("library.json") }

    static func load() -> [InstalledModel] {
        guard let data = try? Data(contentsOf: manifest),
              let list = try? JSONDecoder.iso.decode([InstalledModel].self, from: data) else { return [] }
        return list.filter(\.exists)
    }

    static func save(_ models: [InstalledModel]) {
        if let data = try? JSONEncoder.iso.encode(models) { try? data.write(to: manifest, options: .atomic) }
    }

    /// Удаляет файлы варианта; пустая папка модели уходит вместе с ними.
    static func remove(_ model: InstalledModel) throws {
        let fm = FileManager.default
        for f in model.files + (model.projector.map { [$0] } ?? []) { try? fm.removeItem(at: model.folder.appendingPathComponent(f)) }
        let rest = (try? fm.contentsOfDirectory(atPath: model.folder.path))?.filter { !$0.hasPrefix(".") } ?? []
        if rest.isEmpty { try? fm.removeItem(at: model.folder) }
    }

    /// Сколько места занимают модели.
    static func diskUsage(_ models: [InstalledModel]) -> Int64 { models.reduce(0) { $0 + $1.size } }
}

extension JSONEncoder {
    nonisolated static var iso: JSONEncoder {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }
}

extension JSONDecoder {
    nonisolated static var iso: JSONDecoder {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }
}
