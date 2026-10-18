import Foundation

/// Ищет сборки каждой прошивки из каталога для конкретного устройства.
enum ROMFinder {
    static func availability(of project: ROMProject, for specs: DeviceSpecs) async -> ROMAvailability {
        let codenames = specs.codenameCandidates.isEmpty ? [specs.codename.lowercased()] : specs.codenameCandidates
        do {
            switch project.source {
            case .lineageAPI:
                for codename in codenames {
                    let builds = try await lineageBuilds(codename: codename)
                    if !builds.isEmpty { return .available(builds) }
                }
                return .notFound

            case .otaJSON(let templates):
                for codename in codenames {
                    for template in templates {
                        let url = URL(string: template.replacingOccurrences(of: "{codename}", with: codename))!
                        let builds = try await otaBuilds(url: url, romID: project.id)
                        if !builds.isEmpty {
                            return .available(await attachingLineageRecovery(builds, codenames: codenames))
                        }
                    }
                }
                return .notFound

            case .grapheneOS:
                guard specs.vendorFamily == .google else { return .notFound }
                for codename in codenames {
                    if let build = try await grapheneBuild(codename: codename) { return .available([build]) }
                }
                return .notFound

            case .eos:
                for codename in codenames {
                    let builds = try await eosBuilds(codename: codename)
                    if !builds.isEmpty {
                        return .available(await attachingLineageRecovery(builds, codenames: codenames))
                    }
                }
                return .notFound

            case .directoryIndex(let template):
                for codename in codenames {
                    let url = URL(string: template.replacingOccurrences(of: "{codename}", with: codename))!
                    let builds = try await directoryBuilds(url: url, romID: project.id)
                    if !builds.isEmpty {
                        return .available(await attachingLineageRecovery(builds, codenames: codenames))
                    }
                }
                return .notFound

            case .manual(let guide):
                // Не показываем мёртвую ссылку: сначала проверяем, что страница
                // устройства существует. Если нет — честно «нет для устройства».
                for codename in codenames {
                    let url = URL(string: guide.replacingOccurrences(of: "{codename}", with: codename))!
                    if guide.contains("{codename}") {
                        if try await isReachable(url) { return .manual(url) }
                    } else {
                        // В ссылке нет кодового имени — проверить поддержку нельзя,
                        // просто ведём на общую страницу установки.
                        return .manual(url)
                    }
                }
                return .notFound
            }
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    // MARK: - LineageOS

    private struct LineageBuild: Decodable {
        struct File: Decodable {
            let filename: String
            let url: URL
            let sha256: String?
            let size: Int64?
        }
        let datetime: TimeInterval
        let files: [File]
        let type: String
        let version: String
    }

    private static let lineageRecoveryPartitions = ["boot", "recovery", "dtbo", "vendor_boot", "vendor_kernel_boot", "vbmeta"]

    static func lineageBuilds(codename: String) async throws -> [ROMBuild] {
        let url = URL(string: "https://download.lineageos.org/api/v2/devices/\(codename)/builds")!
        guard let data = try await fetch(url) else { return [] }
        let builds = try JSONDecoder().decode([LineageBuild].self, from: data)
        return builds.compactMap { build in
            guard let zip = build.files.first(where: { $0.filename.hasSuffix(".zip") }) else { return nil }
            let images = build.files
                .filter { lineageRecoveryPartitions.contains(($0.filename as NSString).deletingPathExtension) }
                .map { BuildFile(fileName: $0.filename, url: $0.url, size: $0.size, sha256: $0.sha256) }
            return ROMBuild(
                romID: "lineageos",
                version: "LineageOS \(build.version)",
                androidVersion: androidVersion(forLineage: build.version),
                date: Date(timeIntervalSince1970: build.datetime),
                channel: build.type.capitalized,
                kind: .sideloadZip,
                package: BuildFile(fileName: zip.filename, url: zip.url, size: zip.size, sha256: zip.sha256),
                recoveryImages: images,
                recoveryImagesSource: images.isEmpty ? nil : "LineageOS Recovery",
                notesURL: URL(string: "https://wiki.lineageos.org/devices/\(codename)/install")
            )
        }
    }

    private static func androidVersion(forLineage version: String) -> String? {
        let map = ["18.1": "11", "19.1": "12L", "20.0": "13", "21.0": "14", "22.0": "15", "22.1": "15",
                   "22.2": "15", "23.0": "16", "23.1": "16", "23.2": "16", "24.0": "17"]
        return map[version]
    }

    /// Для прошивок без своего recovery подставляем LineageOS Recovery того же устройства.
    private static func attachingLineageRecovery(_ builds: [ROMBuild], codenames: [String]) async -> [ROMBuild] {
        var lineage: ROMBuild?
        for codename in codenames {
            if let first = try? await lineageBuilds(codename: codename).first {
                lineage = first
                break
            }
        }
        guard let lineage, !lineage.recoveryImages.isEmpty else { return builds }
        return builds.map { build in
            var build = build
            if build.recoveryImages.isEmpty {
                build.recoveryImages = lineage.recoveryImages
                build.recoveryImagesSource = "LineageOS Recovery"
            }
            return build
        }
    }

    // MARK: - OTA JSON (LineageOS Updater format)

    private struct OTAResponse: Decodable {
        struct Entry: Decodable {
            let filename: String?
            let download: URL?
            let url: URL?
            let timestamp: TimeInterval?
            let datetime: TimeInterval?
            let sha256: String?
            let md5: String?
            let size: Int64?
            let version: String?
            let buildtype: String?
            let romtype: String?
            let maintainer: String?
            let forum: String?
        }
        let response: [Entry]
    }

    static func otaBuilds(url: URL, romID: String) async throws -> [ROMBuild] {
        guard let data = try await fetch(url) else { return [] }
        guard let response = try? JSONDecoder().decode(OTAResponse.self, from: data) else { return [] }
        return response.response.compactMap { entry in
            guard let link = entry.download ?? entry.url else { return nil }
            // У SourceForge ссылки вида …/file.zip/download.
            let fileName = entry.filename
                ?? (link.lastPathComponent == "download" ? link.deletingLastPathComponent() : link).lastPathComponent
            let timestamp = entry.timestamp ?? entry.datetime
            return ROMBuild(
                romID: romID,
                version: entry.version.map { "v\($0)" } ?? fileName,
                androidVersion: androidVersion(fromFileName: fileName),
                date: timestamp.map { Date(timeIntervalSince1970: $0) },
                channel: entry.buildtype ?? entry.romtype,
                kind: .sideloadZip,
                package: BuildFile(fileName: fileName, url: link, size: entry.size, sha256: entry.sha256, md5: entry.md5),
                notesURL: entry.forum.flatMap { $0.isEmpty ? nil : URL(string: $0) },
                maintainer: entry.maintainer
            )
        }
    }

    /// «crDroidAndroid-16.0-20260912-…» → «16».
    private static func androidVersion(fromFileName name: String) -> String? {
        for part in name.split(separator: "-") {
            if let value = Double(part), value >= 10, value < 30 {
                return String(Int(value))
            }
        }
        return nil
    }

    // MARK: - GrapheneOS

    static func grapheneBuild(codename: String) async throws -> ROMBuild? {
        let url = URL(string: "https://releases.grapheneos.org/\(codename)-stable")!
        guard let data = try await fetch(url) else { return nil }
        // Формат: «2026100600 1791330262 lynx stable»
        let parts = String(decoding: data, as: UTF8.self).split(separator: " ")
        guard parts.count >= 2, let timestamp = TimeInterval(parts[1].trimmingCharacters(in: .whitespacesAndNewlines))
        else { return nil }
        let version = String(parts[0])
        let fileName = "\(codename)-install-\(version).zip"
        return ROMBuild(
            romID: "grapheneos",
            version: "GrapheneOS \(version)",
            androidVersion: nil,
            date: Date(timeIntervalSince1970: timestamp),
            channel: "Stable",
            kind: .factoryImage,
            package: BuildFile(fileName: fileName, url: URL(string: "https://releases.grapheneos.org/\(fileName)")!),
            notesURL: URL(string: "https://grapheneos.org/releases#\(version)")
        )
    }

    // MARK: - /e/OS (images.ecloud.global)

    private struct EOSResponse: Decodable {
        struct Build: Decodable {
            struct File: Decodable {
                let ftype: String
                let filename: String
                let url: URL
                let size: Int64?
                let sha256: String?
            }
            let display_version: String?
            let version: String?
            let android_version: Int?
            let type: String?
            let datetime: TimeInterval?
            let files: [File]
        }
        let builds: [Build]
    }

    static func eosBuilds(codename: String) async throws -> [ROMBuild] {
        let url = URL(string: "https://images.ecloud.global/api/v2/devices/\(codename)/downloads")!
        guard let data = try await fetch(url, accept: "application/json") else { return [] }
        guard let response = try? JSONDecoder().decode(EOSResponse.self, from: data) else { return [] }
        return response.builds.compactMap { build in
            guard let ota = build.files.first(where: { $0.ftype.uppercased() == "OTA" }) else { return nil }
            return ROMBuild(
                romID: "eos",
                version: "/e/OS \(build.version ?? build.display_version ?? "")",
                androidVersion: build.android_version.map(String.init),
                date: build.datetime.map { Date(timeIntervalSince1970: $0) },
                channel: build.type?.capitalized,
                kind: .sideloadZip,
                package: BuildFile(fileName: ota.filename, url: ota.url, size: ota.size, sha256: ota.sha256)
            )
        }
    }

    // MARK: - Каталог-листинг с ZIP'ами (LineageOS for microG)

    static func directoryBuilds(url: URL, romID: String) async throws -> [ROMBuild] {
        guard let data = try await fetch(url) else { return [] }
        let html = String(decoding: data, as: UTF8.self)
        // Вытаскиваем href="...zip" из листинга каталога.
        var builds: [ROMBuild] = []
        var seen: Set<String> = []
        let pattern = #"href="([^"]+\.zip)""#
        let regex = try NSRegularExpression(pattern: pattern)
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range(at: 1), in: html) else { continue }
            let href = String(html[range])
            let fileURL = URL(string: href, relativeTo: url)?.absoluteURL ?? url
            let fileName = fileURL.lastPathComponent
            guard seen.insert(fileName).inserted else { continue }
            builds.append(ROMBuild(
                romID: romID,
                version: fileName.replacingOccurrences(of: ".zip", with: ""),
                androidVersion: androidVersion(fromFileName: fileName),
                date: date(fromFileName: fileName),
                channel: fileName.contains("microG") ? "microG" : nil,
                kind: .sideloadZip,
                package: BuildFile(fileName: fileName, url: fileURL)
            ))
        }
        // Новые сверху.
        return builds.sorted { ($0.date ?? .distantPast) > ($1.date ?? .distantPast) }
    }

    /// «lineage-23.2-20260901-microG-alioth.zip» → дата сборки.
    private static func date(fromFileName name: String) -> Date? {
        guard let match = name.range(of: #"20\d{6}"#, options: .regularExpression) else { return nil }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.date(from: String(name[match]))
    }

    // MARK: - Сеть

    /// Проверяет, существует ли страница устройства (без скачивания тела).
    static func isReachable(_ url: URL) async throws -> Bool {
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.httpMethod = "HEAD"
        request.setValue("ROMForge/1.0 (macOS)", forHTTPHeaderField: "User-Agent")
        guard let (_, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse else { return false }
        return (200..<400).contains(http.statusCode)
    }

    /// nil — если ресурса нет (404): для конкретного устройства это нормально.
    private static func fetch(_ url: URL, accept: String? = nil) async throws -> Data? {
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.setValue("ROMForge/1.0 (macOS)", forHTTPHeaderField: "User-Agent")
        if let accept { request.setValue(accept, forHTTPHeaderField: "Accept") }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { return nil }
        if http.statusCode == 404 { return nil }
        guard (200..<300).contains(http.statusCode) else { throw DownloadError.badStatus(http.statusCode) }
        return data
    }
}
