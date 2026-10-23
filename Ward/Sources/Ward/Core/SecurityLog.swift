import Foundation

/// Запись журнала безопасности: что произошло, где и чем закончилось.
nonisolated struct LogEntry: Codable, Sendable, Identifiable, Hashable {
    var id = UUID()
    var date = Date()
    /// Вид события (`SecurityEvent.Kind.rawValue`).
    var kind: String
    var title: String
    var detail: String
}

/// Журнал безопасности: каждое событие защиты — строка JSON в файле дня (`Журнал/2026-10-23.jsonl`).
/// Только на этом Mac; события частных окон и одноразовых вкладок попадают без имён сайтов, как и в ленту.
/// Пишется на своей очереди пачками раз в пару секунд — страницы от журнала не тормозят.
nonisolated final class SecurityLog: @unchecked Sendable {
    static let shared = SecurityLog()

    let directory: URL
    private let queue = DispatchQueue(label: "app.ward.security-log", qos: .utility)
    private var pending: [LogEntry] = []
    private var flushScheduled = false

    private init() {
        directory = AppPaths.support.appendingPathComponent("Журнал", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func append(_ entry: LogEntry) {
        queue.async {
            self.pending.append(entry)
            guard !self.flushScheduled else { return }
            self.flushScheduled = true
            self.queue.asyncAfter(deadline: .now() + 2) { self.flush() }
        }
    }

    /// Дописать накопленное сейчас (перед выходом и перед чтением журнала).
    func flushNow() { queue.sync { flush() } }

    private func flush() {
        flushScheduled = false
        guard !pending.isEmpty else { return }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        var byDay: [String: Data] = [:]
        for entry in pending {
            guard var line = try? encoder.encode(entry) else { continue }
            line.append(0x0A)
            byDay[Self.day(entry.date), default: Data()].append(line)
        }
        pending.removeAll()
        for (day, data) in byDay {
            let url = directory.appendingPathComponent(day + ".jsonl")
            if let handle = try? FileHandle(forWritingTo: url) {
                _ = try? handle.seekToEnd()
                try? handle.write(contentsOf: data)
                try? handle.close()
            } else {
                try? data.write(to: url, options: .atomic)
            }
        }
    }

    /// Записи за последние `days` дней, новые сверху.
    func entries(days: Int = 30, limit: Int = 20000) -> [LogEntry] {
        flushNow()
        return queue.sync {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            var result: [LogEntry] = []
            for url in files().suffix(days).reversed() {
                guard let text = try? String(contentsOf: url, encoding: .utf8) else { continue }
                var dayEntries: [LogEntry] = []
                for line in text.split(separator: "\n") {
                    if let e = try? decoder.decode(LogEntry.self, from: Data(line.utf8)) { dayEntries.append(e) }
                }
                result += dayEntries.reversed()
                if result.count >= limit { break }
            }
            return Array(result.prefix(limit))
        }
    }

    /// Сколько места занимает журнал и за сколько дней.
    func summary() -> (days: Int, bytes: Int64) {
        queue.sync {
            let list = files()
            let bytes = list.reduce(Int64(0)) { $0 + Int64((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
            return (list.count, bytes)
        }
    }

    /// Удалить файлы старше `days` дней.
    func prune(keepingDays days: Int) {
        queue.async {
            let list = self.files()
            guard list.count > days else { return }
            for url in list.prefix(list.count - days) { try? FileManager.default.removeItem(at: url) }
        }
    }

    func clear() {
        queue.sync {
            pending.removeAll()
            for url in files() { try? FileManager.default.removeItem(at: url) }
        }
    }

    /// Выгрузка: JSON Lines (как хранится) или CSV для таблиц.
    func export(to url: URL, csv: Bool, days: Int) throws {
        let list = entries(days: days, limit: .max).reversed()
        if csv {
            var text = "Время;Вид;Событие;Подробности\n"
            let format = ISO8601DateFormatter()
            for e in list {
                let cells = [format.string(from: e.date), e.kind, e.title, e.detail].map { "\"" + $0.replacingOccurrences(of: "\"", with: "\"\"") + "\"" }
                text += cells.joined(separator: ";") + "\n"
            }
            try Data(text.utf8).write(to: url, options: .atomic)
        } else {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            var data = Data()
            for e in list {
                data.append(try encoder.encode(e))
                data.append(0x0A)
            }
            try data.write(to: url, options: .atomic)
        }
    }

    private func files() -> [URL] {
        let list = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return list.filter { $0.pathExtension == "jsonl" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    static func day(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
}
