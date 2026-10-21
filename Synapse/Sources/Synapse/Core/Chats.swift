import Foundation

nonisolated struct ChatMessage: Codable, Identifiable, Sendable, Hashable {
    enum Role: String, Codable, Sendable { case user, assistant }

    var id = UUID()
    var role: Role
    var text: String
    var reasoning: String?
    var date = Date()
    var stats: GenerationStats?
    var model: String?
    var error: String?
    /// Сколько секунд модель размышляла.
    var thinkingSeconds: Double?
}

nonisolated struct Conversation: Codable, Identifiable, Sendable, Hashable {
    var id = UUID()
    var title = "Новый чат"
    var created = Date()
    var updated = Date()
    var messages: [ChatMessage] = []
    var modelID: String?
    var systemPrompt = Conversation.defaultSystemPrompt

    static let defaultSystemPrompt = "Ты — дружелюбный и точный помощник. Отвечай на языке пользователя, по делу, с примерами, когда они помогают."

    /// Реплики для модели: системная + история (в ответах ассистента — без размышлений).
    var turns: [ChatTurn] {
        var t: [ChatTurn] = []
        if !systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { t.append(ChatTurn(role: .system, content: systemPrompt)) }
        for m in messages where m.error == nil && !(m.role == .assistant && m.text.isEmpty) {
            t.append(ChatTurn(role: m.role == .user ? .user : .assistant, content: m.text))
        }
        return t
    }
}

nonisolated enum ChatArchive {
    static func load() -> [Conversation] {
        let fm = FileManager.default
        let files = (try? fm.contentsOfDirectory(at: AppPaths.chats, includingPropertiesForKeys: nil)) ?? []
        return files.filter { $0.pathExtension == "json" }
            .compactMap { try? JSONDecoder.iso.decode(Conversation.self, from: Data(contentsOf: $0)) }
            .sorted { $0.updated > $1.updated }
    }

    static func save(_ c: Conversation) {
        if let data = try? JSONEncoder.iso.encode(c) {
            try? data.write(to: AppPaths.chats.appendingPathComponent(c.id.uuidString + ".json"), options: .atomic)
        }
    }

    static func delete(_ c: Conversation) {
        try? FileManager.default.removeItem(at: AppPaths.chats.appendingPathComponent(c.id.uuidString + ".json"))
    }

    /// Заголовок из первого вопроса.
    static func title(from text: String) -> String {
        let line = text.split(whereSeparator: \.isNewline).first.map(String.init) ?? text
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        return trimmed.count > 48 ? String(trimmed.prefix(46)) + "…" : trimmed
    }
}
