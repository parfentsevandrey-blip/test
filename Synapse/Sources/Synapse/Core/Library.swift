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

/// Модели хранятся отдельно от программы — в ~/Library/Application Support/Synapse/Models. Обновление
/// Synapse (новая версия поверх старой) эту папку не трогает. Чтобы модели не «пропали» и тогда, когда
/// список повреждён или записан другой версией, список читается по одной записи, хранится в двух копиях,
/// а модели, которые лежат в папке, но не попали в список, находятся заново.
nonisolated enum Library {
    static var manifest: URL { AppPaths.models.appendingPathComponent("library.json") }
    static var backup: URL { AppPaths.models.appendingPathComponent("library.backup.json") }

    static func load() -> [InstalledModel] {
        var list = (decode(manifest) ?? decode(backup) ?? []).filter(\.exists)
        let found = recover(known: list)
        if !found.isEmpty {
            list += found
            save(list)
        }
        return list
    }

    static func save(_ models: [InstalledModel]) {
        guard let data = try? JSONEncoder.iso.encode(models) else { return }
        try? data.write(to: manifest, options: .atomic)
        try? data.write(to: backup, options: .atomic)
    }

    /// По одной записи: если одна повреждена или из другой версии, остальные не теряются.
    private static func decode(_ url: URL) -> [InstalledModel]? {
        guard let data = try? Data(contentsOf: url),
              let items = try? JSONDecoder.iso.decode([Lossy<InstalledModel>].self, from: data) else { return nil }
        return items.compactMap(\.value)
    }

    /// Модели, которые лежат в папке, но не записаны в список: восстанавливаем по именам файлов.
    static func recover(known: [InstalledModel]) -> [InstalledModel] {
        let fm = FileManager.default
        guard let folders = try? fm.contentsOfDirectory(at: AppPaths.models, includingPropertiesForKeys: [.isDirectoryKey, .creationDateKey]) else { return [] }
        var result: [InstalledModel] = []
        for folder in folders where (try? folder.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true {
            let names = (try? fm.contentsOfDirectory(atPath: folder.path)) ?? []
            // Недокачанные части (.part) не считаются: у них другое окончание.
            let files = names.filter { $0.lowercased().hasSuffix(".gguf") }.map { name in
                let size = (try? fm.attributesOfItem(atPath: folder.appendingPathComponent(name).path)[.size] as? NSNumber)?.int64Value ?? 0
                return HubFile(type: "file", path: name, size: size, lfs: nil)
            }
            let repo = folder.lastPathComponent.replacingOccurrences(of: "__", with: "/")
            let org = String(repo.split(separator: "/").first ?? "")
            let curated = Curated.cards.first { $0.id.caseInsensitiveCompare(repo) == .orderedSame }
            let created = (try? folder.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date()
            for v in ModelVariant.group(files) {
                let paths = v.files.map(\.path)
                if known.contains(where: { $0.folderName == folder.lastPathComponent && $0.files.first == paths.first }) { continue }
                let params = curated?.params ?? Double(v.weightsSize) * 8 / v.quant.bits / 1e9
                result.append(InstalledModel(
                    repo: curated?.id ?? repo,
                    name: curated?.name ?? CardBuilder.prettyName(String(repo.split(separator: "/").last ?? Substring(repo))),
                    lab: curated?.lab ?? CardBuilder.labs[org.lowercased()] ?? org,
                    quant: v.quant.name, files: paths, size: v.size, params: params,
                    activeParams: curated?.activeParams ?? CardBuilder.activeParams(in: repo),
                    architecture: curated?.architecture, contextLength: nil, installedAt: created, lastUsed: nil,
                    verified: false, projector: v.projector?.path))
            }
        }
        return result
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

/// Элемент списка, который не ломает весь список, если сам не читается.
nonisolated struct Lossy<T: Decodable>: Decodable {
    let value: T?
    init(from decoder: Decoder) throws { value = try? T(from: decoder) }
}
