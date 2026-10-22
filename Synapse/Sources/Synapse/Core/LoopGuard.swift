import CLlama
import Foundation

/// Защита от зацикливания: замечает, что модель ходит по кругу («это V8… нет, я перепроверил… это V8…»).
///
/// Так бывает у локальных моделей, особенно сильно сжатых: в размышлениях они повторяют одну и ту же мысль
/// без конца. Если это случилось в размышлениях, движок закрывает блок размышлений сам и модель переходит
/// к ответу по тому, что уже поняла. Если по кругу пошёл сам ответ — генерация останавливается.
/// Тот же механизм ограничивает длину размышлений, если в Настройках задан предел.
nonisolated struct LoopGuard {
    enum Action: Equatable {
        case none
        /// Закрыть размышления: дописать `closing` (маркер конца размышлений для этого формата), пояснение — в `note`.
        case closeThinking(note: String, closing: String)
        /// Остановить ответ: модель повторяется.
        case stop
    }

    let enabled: Bool
    /// Предел размышлений в токенах; 0 — без предела.
    let budget: Int
    private(set) var inReasoning: Bool
    private var opener: String
    private var window = ""
    private var reasoning: [llama_token] = []
    private var answer: [llama_token] = []
    private var thinkingTokens = 0
    private var sinceCheck = 0
    private var closedOnce = false

    init(enabled: Bool, budget: Int, startsInReasoning: Bool, format: ChatFormat) {
        self.enabled = enabled
        self.budget = budget
        inReasoning = startsInReasoning
        switch format {
        case .gemma4: opener = "<|channel>thought"
        case .harmony: opener = "<|channel|>analysis<|message|>"
        default: opener = "<think>"
        }
    }

    /// Маркер, который закрывает размышления для того, как они открылись.
    static func closing(for opener: String) -> String {
        switch opener {
        case "<|channel>thought": "<channel|>"
        case "<|channel|>analysis<|message|>": "<|end|><|start|>assistant<|channel|>final<|message|>"
        default: "\n</think>\n\n"
        }
    }

    /// Очередной токен и его текст. Возвращает, что сделать.
    mutating func observe(_ token: llama_token, text: String) -> Action {
        if !text.isEmpty {
            window = String((window + text).suffix(48))
            if let o = ReplyParser.opens.first(where: { window.contains($0) }) {
                inReasoning = true
                opener = o
                window = ""
            } else if ReplyParser.closes.contains(where: { window.contains($0) }) {
                inReasoning = false
                window = ""
            }
        }
        if inReasoning {
            reasoning.append(token)
            thinkingTokens += 1
        } else {
            answer.append(token)
        }
        if reasoning.count > 1200 { reasoning.removeFirst(reasoning.count - 1200) }
        if answer.count > 1600 { answer.removeFirst(answer.count - 1600) }

        if inReasoning && budget > 0 && thinkingTokens >= budget {
            return close("Размышления остановлены: достигнут предел в \(budget) токенов. Отвечаю по тому, что уже понятно.")
        }
        guard enabled else { return .none }
        sinceCheck += 1
        guard sinceCheck >= 8 else { return .none }
        sinceCheck = 0
        if inReasoning {
            // В размышлениях повторы недопустимы: и точный круг, и одна и та же фраза снова и снова.
            if Self.periodic(reasoning, minPeriod: 4, maxPeriod: 240, times: 3)
                || Self.recurring(reasoning, gram: 10, times: 4, window: 600) {
                if closedOnce { return .stop }
                return close("Размышления остановлены: модель пошла по кругу. Отвечаю по тому, что уже понятно.")
            }
        } else if Self.periodic(answer, minPeriod: 6, maxPeriod: 300, times: 4) {
            // В ответе бывают законные повторы (таблицы, код), поэтому только точный круг из четырёх витков.
            return .stop
        }
        return .none
    }

    private mutating func close(_ note: String) -> Action {
        closedOnce = true
        inReasoning = false
        reasoning.removeAll()
        window = ""
        return .closeThinking(note: "\n\n— " + note, closing: Self.closing(for: opener))
    }

    /// Последние `period` токенов повторились `times` раз подряд — для какого-нибудь `period` из диапазона.
    static func periodic(_ t: [llama_token], minPeriod: Int, maxPeriod: Int, times: Int) -> Bool {
        let n = t.count
        guard times >= 2, n >= minPeriod * times else { return false }
        let top = min(maxPeriod, n / times)
        guard top >= minPeriod else { return false }
        for p in minPeriod...top {
            var same = true
            for k in 0..<((times - 1) * p) where t[n - 1 - k] != t[n - 1 - k - p] {
                same = false
                break
            }
            if same { return true }
        }
        return false
    }

    /// Одна и та же цепочка из `gram` токенов встречается `times` раз среди последних `window` токенов.
    static func recurring(_ t: [llama_token], gram: Int, times: Int, window: Int) -> Bool {
        let tail = Array(t.suffix(window))
        guard tail.count >= gram * times else { return false }
        var counts: [Int: Int] = [:]
        for i in 0...(tail.count - gram) {
            var h = Hasher()
            for j in i..<(i + gram) { h.combine(tail[j]) }
            let key = h.finalize()
            let c = (counts[key] ?? 0) + 1
            if c >= times { return true }
            counts[key] = c
        }
        return false
    }
}
