import Foundation

nonisolated struct SubtitleCue: Codable, Sendable, Hashable {
    var start: Double
    var end: Double
    var text: String
}

nonisolated struct SubtitleTrackData: Codable, Sendable, Hashable {
    var name: String
    var language: String?
    var cues: [SubtitleCue]

    /// Активные реплики на момент `time` (бинарный поиск + короткий откат назад для перекрытий).
    func text(at time: Double) -> String? {
        guard !cues.isEmpty else { return nil }
        var lo = 0, hi = cues.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if cues[mid].start <= time { lo = mid + 1 } else { hi = mid }
        }
        var lines: [String] = []
        var i = lo - 1
        var steps = 0
        while i >= 0, steps < 8 {
            let cue = cues[i]
            if cue.start <= time, time < cue.end { lines.insert(cue.text, at: 0) }
            i -= 1
            steps += 1
        }
        return lines.isEmpty ? nil : lines.joined(separator: "\n")
    }
}

/// Разбор внешних субтитров: SRT, WebVTT, ASS/SSA. Кодировку определяем сами —
/// русские .srt часто лежат в Windows-1251.
nonisolated enum SubtitleParser {
    static let fileExtensions: Set<String> = ["srt", "vtt", "webvtt", "ass", "ssa"]

    static func load(_ url: URL) throws -> SubtitleTrackData {
        let data = try Data(contentsOf: url)
        let text = decode(data)
        let ext = url.pathExtension.lowercased()
        let cues: [SubtitleCue]
        if ext == "ass" || ext == "ssa" || text.contains("[Events]") {
            cues = parseASS(text)
        } else {
            cues = parseTimed(text)
        }
        let base = url.deletingPathExtension()
        // movie.ru.srt → язык «ru»
        let langCode = base.pathExtension.count == 2 || base.pathExtension.count == 3 ? base.pathExtension : nil
        let name = langCode.flatMap { Locale.current.localizedString(forLanguageCode: $0)?.capitalized }
            ?? url.lastPathComponent
        return SubtitleTrackData(name: name, language: langCode, cues: cues)
    }

    static func decode(_ data: Data) -> String {
        if data.starts(with: [0xEF, 0xBB, 0xBF]) { return String(decoding: data.dropFirst(3), as: UTF8.self) }
        if data.starts(with: [0xFF, 0xFE]) || data.starts(with: [0xFE, 0xFF]) {
            return String(data: data, encoding: .utf16) ?? ""
        }
        if let s = String(data: data, encoding: .utf8) { return s }
        if let s = String(data: data, encoding: .windowsCP1251) { return s }
        return String(data: data, encoding: .isoLatin1) ?? ""
    }

    /// SRT и WebVTT: блоки, разделённые пустой строкой, со строкой «начало --> конец».
    static func parseTimed(_ raw: String) -> [SubtitleCue] {
        let text = raw.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        var cues: [SubtitleCue] = []
        for block in text.components(separatedBy: "\n\n") {
            let lines = block.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
            guard let arrowIndex = lines.firstIndex(where: { $0.contains("-->") }) else { continue }
            let parts = lines[arrowIndex].components(separatedBy: "-->")
            guard parts.count == 2,
                  let start = timestamp(parts[0]),
                  let end = timestamp(parts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: " ").first ?? "")
            else { continue }
            let body = lines[(arrowIndex + 1)...].joined(separator: "\n")
            let clean = stripTags(body).trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty { cues.append(SubtitleCue(start: start, end: max(end, start + 0.1), text: clean)) }
        }
        return cues.sorted { $0.start < $1.start }
    }

    static func parseASS(_ raw: String) -> [SubtitleCue] {
        var cues: [SubtitleCue] = []
        var textIndex = 9
        var startIndex = 1
        var endIndex = 2
        for line in raw.components(separatedBy: .newlines) {
            if line.hasPrefix("Format:"), cues.isEmpty {
                let fields = line.dropFirst(7).split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
                if let t = fields.firstIndex(of: "text") { textIndex = t }
                if let s = fields.firstIndex(of: "start") { startIndex = s }
                if let e = fields.firstIndex(of: "end") { endIndex = e }
                continue
            }
            guard line.hasPrefix("Dialogue:") else { continue }
            let fields = line.dropFirst(9).split(separator: ",", maxSplits: textIndex, omittingEmptySubsequences: false)
            guard fields.count > textIndex,
                  let start = timestamp(String(fields[startIndex])),
                  let end = timestamp(String(fields[endIndex])) else { continue }
            let text = cleanASS(String(fields[textIndex]))
            if !text.isEmpty { cues.append(SubtitleCue(start: start, end: end, text: text)) }
        }
        return cues.sorted { $0.start < $1.start }
    }

    /// Текст реплики ASS: убираем {\теги} и переводим \N в перенос строки.
    static func cleanASS(_ s: String) -> String {
        var out = ""
        var depth = 0
        for ch in s {
            if ch == "{" { depth += 1; continue }
            if ch == "}" { depth = max(0, depth - 1); continue }
            if depth == 0 { out.append(ch) }
        }
        return out.replacingOccurrences(of: "\\N", with: "\n")
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\h", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func stripTags(_ s: String) -> String {
        var out = ""
        var inTag = false
        for ch in s {
            if ch == "<" { inTag = true; continue }
            if ch == ">" && inTag { inTag = false; continue }
            if !inTag { out.append(ch) }
        }
        return cleanASS(out)
    }

    /// «01:02:03,456», «01:02:03.456», «02:03.456», «0:01:02.34» (ASS).
    static func timestamp(_ raw: String) -> Double? {
        let s = raw.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        let parts = s.split(separator: ":")
        guard (2...3).contains(parts.count) else { return nil }
        var total = 0.0
        for part in parts {
            guard let v = Double(part) else { return nil }
            total = total * 60 + v
        }
        return total
    }

    /// Субтитры, лежащие рядом с видео: movie.srt, movie.ru.srt, movie.eng.ass…
    static func sidecars(for video: URL) -> [URL] {
        let dir = video.deletingLastPathComponent()
        let stem = video.deletingPathExtension().lastPathComponent.lowercased()
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return files.filter { url in
            let name = url.lastPathComponent.lowercased()
            return fileExtensions.contains(url.pathExtension.lowercased()) && name.hasPrefix(stem)
        }
        .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
    }
}
