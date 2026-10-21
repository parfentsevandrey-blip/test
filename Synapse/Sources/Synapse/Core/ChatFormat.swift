import Foundation

/// Реплика диалога в том виде, в каком её получает модель.
nonisolated struct ChatTurn: Sendable, Hashable, Codable {
    enum Role: String, Sendable, Codable { case system, user, assistant }
    var role: Role
    var content: String
}

/// Как превратить диалог в текст для конкретной модели.
///
/// Встроенный в llama.cpp `llama_chat_apply_template` распознаёт шаблоны эвристически и не исполняет Jinja.
/// Для самых популярных семейств формат собираем сами — так работают режим размышлений Qwen3+
/// и новый формат Gemma 4, которого нет среди встроенных.
nonisolated enum ChatFormat: Sendable, Equatable {
    /// ChatML с блоком `<think>`: Qwen3, Qwen3.5+, QwQ и производные.
    case chatmlThinking
    /// `<|turn>user … <turn|>` — Gemma 4.
    case gemma4
    /// Harmony — gpt-oss: размышления в канале analysis, ответ в канале final.
    case harmony
    /// Шаблон модели через llama_chat_apply_template.
    case builtin(String)
    /// Запасной вариант, когда шаблона нет или он не распознан.
    case chatml

    static func detect(template: String?) -> ChatFormat {
        guard let t = template, !t.isEmpty else { return .chatml }
        if t.contains("<|turn>") && t.contains("<turn|>") { return .gemma4 }
        if t.contains("<|start|>") && t.contains("<|channel|>") { return .harmony }
        if t.contains("<|im_start|>") && t.contains("<think>") && !t.contains("<|im_sep|>") { return .chatmlThinking }
        return .builtin(t)
    }

    /// Можно ли попросить модель отвечать сразу, без размышлений.
    var thinkingControl: Bool {
        switch self {
        case .chatmlThinking, .gemma4, .harmony: true
        default: false
        }
    }

    var title: String {
        switch self {
        case .chatmlThinking: "ChatML + размышления"
        case .gemma4: "Gemma 4"
        case .harmony: "Harmony (gpt-oss)"
        case .builtin: "Шаблон модели"
        case .chatml: "ChatML"
        }
    }

    /// Текст промпта и признак: начинается ли ответ модели внутри блока размышлений.
    func render(_ turns: [ChatTurn], thinking: Bool,
                builtin: (String, [ChatTurn]) -> String?) -> (text: String, startsInReasoning: Bool) {
        switch self {
        case .chatmlThinking:
            var s = ""
            for t in turns { s += "<|im_start|>\(t.role.rawValue)\n\(t.content)<|im_end|>\n" }
            s += "<|im_start|>assistant\n"
            s += thinking ? "<think>\n" : "<think>\n\n</think>\n\n"
            return (s, thinking)

        case .gemma4:
            var s = ""
            let system = turns.filter { $0.role == .system }.map(\.content).joined(separator: "\n\n")
            if thinking || !system.isEmpty {
                s += "<|turn>system\n"
                if thinking { s += "<|think|>\n" }
                s += system.trimmingCharacters(in: .whitespacesAndNewlines)
                s += "<turn|>\n"
            }
            for t in turns where t.role != .system {
                let role = t.role == .assistant ? "model" : "user"
                s += "<|turn>\(role)\n\(t.content.trimmingCharacters(in: .whitespacesAndNewlines))<turn|>\n"
            }
            s += "<|turn>model\n"
            if !thinking { s += "<|channel>thought\n<channel|>" }
            return (s, false)

        case .harmony:
            // Совсем отключить размышления gpt-oss нельзя — просим думать коротко.
            var turns = turns
            let effort = "Reasoning: \(thinking ? "medium" : "low")"
            if let i = turns.firstIndex(where: { $0.role == .system }) {
                turns[i].content = effort + "\n\n" + turns[i].content
            } else {
                turns.insert(ChatTurn(role: .system, content: effort), at: 0)
            }
            var s = ""
            for t in turns {
                s += "<|start|>\(t.role.rawValue)<|message|>\(t.content)"
                s += t.role == .assistant ? "<|return|>" : "<|end|>"
            }
            s += "<|start|>assistant"
            return (s, false)

        case .builtin(let template):
            if let s = builtin(template, turns) { return (s, false) }
            return ChatFormat.chatml.render(turns, thinking: thinking, builtin: builtin)

        case .chatml:
            var s = ""
            for t in turns { s += "<|im_start|>\(t.role.rawValue)\n\(t.content)<|im_end|>\n" }
            s += "<|im_start|>assistant\n"
            return (s, false)
        }
    }
}

/// Ответ модели, разобранный на размышления и сам ответ.
nonisolated struct ReplyParts: Equatable, Sendable {
    var reasoning = ""
    var answer = ""
    /// Модель всё ещё размышляет (блок не закрыт).
    var reasoningOpen = false
}

/// Разбор потока токенов: `<think>…</think>`, `<|channel>thought…<channel|>` (Gemma 4),
/// каналы Harmony (gpt-oss). Служебные маркеры убираются, недописанный маркер в конце
/// потока придерживается, чтобы не мигал в интерфейсе.
nonisolated enum ReplyParser {
    static let opens = ["<think>", "<|channel>thought", "<|channel|>analysis<|message|>"]
    static let closes = ["</think>", "<channel|>", "<|end|>"]
    static let dropped = ["<|start|>assistant", "<|channel|>final<|message|>", "<|channel|>commentary", "<|channel|>final",
                          "<|message|>", "<|return|>", "<|im_end|>", "<turn|>", "<end_of_turn>", "<|endoftext|>", "<eos>", "<|eot_id|>",
                          // Закрывающие маркеры вне размышлений — просто мусор (в размышлениях они срабатывают раньше).
                          "</think>", "<channel|>", "<|end|>"]

    static func parse(_ raw: String, startsInReasoning: Bool = false) -> ReplyParts {
        var parts = ReplyParts()
        var inReasoning = startsInReasoning
        var rest = Substring(raw)

        // Хвост, похожий на начало маркера, пока не показываем.
        let all = opens + closes + dropped
        if let lt = rest.lastIndex(of: "<") {
            let tail = rest[lt...]
            if tail.count < 40, all.contains(where: { $0.hasPrefix(tail) && $0 != String(tail) }) {
                rest = rest[..<lt]
            }
        }

        while !rest.isEmpty {
            let candidates = (inReasoning ? closes : opens).map { ($0, true) } + dropped.map { ($0, false) }
            var best: (range: Range<Substring.Index>, marker: String, switches: Bool)?
            for (marker, switches) in candidates {
                if let r = rest.range(of: marker), best == nil || r.lowerBound < best!.range.lowerBound {
                    best = (r, marker, switches)
                }
            }
            guard let hit = best else {
                append(rest, inReasoning, &parts)
                break
            }
            append(rest[..<hit.range.lowerBound], inReasoning, &parts)
            if hit.switches { inReasoning.toggle() }
            rest = rest[hit.range.upperBound...]
        }
        parts.reasoningOpen = inReasoning
        parts.reasoning = parts.reasoning.trimmingCharacters(in: .whitespacesAndNewlines)
        parts.answer = String(parts.answer.drop(while: { $0.isWhitespace || $0.isNewline }))
        return parts
    }

    private static func append(_ s: Substring, _ reasoning: Bool, _ parts: inout ReplyParts) {
        if reasoning { parts.reasoning += s } else { parts.answer += s }
    }
}

/// Байты токенов → текст. Токен может оборвать многобайтовый символ UTF-8 (кириллица, эмодзи),
/// поэтому незаконченный хвост ждёт следующего токена.
nonisolated struct UTF8Accumulator {
    private var pending: [UInt8] = []

    mutating func push(_ bytes: [UInt8]) -> String {
        pending += bytes
        var cut = pending.count
        var i = pending.count - 1
        var back = 0
        while i >= 0 && back < 4 {
            let b = pending[i]
            if b & 0xC0 == 0x80 { i -= 1; back += 1; continue }
            let need = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1
            if pending.count - i < need { cut = i }
            break
        }
        let out = String(decoding: pending[..<cut], as: UTF8.self)
        pending.removeFirst(cut)
        return out
    }

    mutating func flush() -> String {
        defer { pending.removeAll() }
        return String(decoding: pending, as: UTF8.self)
    }
}
