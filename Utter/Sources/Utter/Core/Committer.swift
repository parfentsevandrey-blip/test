import Foundation

/// Утверждение слов в потоке (LocalAgreement-2, Macháček et al., «Turning Whisper into Real-Time
/// Transcription System», 2023): фраза распознаётся заново каждые полсекунды, и слово печатается,
/// только когда две гипотезы подряд с ним согласны. Напечатанное больше не меняется — в поле не бывает
/// «прыгающего» текста и исправлений стиранием.
nonisolated struct Committer {
    /// Утверждённые (напечатанные) слова текущей фразы.
    private(set) var committed: [String] = []
    private var previous: [String]?
    /// Первые слова фразы придерживаем, пока их не станет больше `holdBack`: так голосовая команда
    /// («новая строка») не успеет напечататься как текст.
    var holdBack = 2

    static func words(_ text: String) -> [String] {
        text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
    }

    var committedText: String { committed.joined(separator: " ") }

    mutating func reset() {
        committed = []
        previous = nil
    }

    /// Новая гипотеза всей фразы → слова, которые можно печатать.
    mutating func update(_ hypothesis: String) -> [String] {
        let words = Self.words(hypothesis)
        defer { previous = words }
        guard words.count > committed.count, Array(words.prefix(committed.count)) == committed, let prev = previous else { return [] }
        let agreed = zip(prev, words).prefix(while: { $0 == $1 }).count
        // Последнее слово гипотезы может быть оборвано на полуслове — его не трогаем.
        let target = min(agreed, words.count - 1)
        if committed.isEmpty && target <= holdBack { return [] }
        guard target > committed.count else { return [] }
        let new = Array(words[committed.count..<target])
        committed += new
        return new
    }

    /// Окончательный текст фразы → оставшиеся слова. Если окончательный вариант разошёлся с уже
    /// напечатанным (редко), напечатанное остаётся, добавляется только хвост после него.
    mutating func finalize(_ final: String) -> [String] {
        let words = Self.words(final)
        defer { reset() }
        return words.count > committed.count ? Array(words[committed.count...]) : []
    }
}

/// Голосовые команды — фраза целиком.
nonisolated enum VoiceCommand: Equatable {
    case newLine
    case newParagraph
    case deleteLast

    static func match(_ text: String) -> VoiceCommand? {
        let normalized = text.lowercased()
            .unicodeScalars.filter { CharacterSet.letters.contains($0) || $0 == " " }
            .map(String.init).joined()
            .split(separator: " ").joined(separator: " ")
        switch normalized {
        case "новая строка", "с новой строки", "перенос строки": return .newLine
        case "новый абзац", "абзац", "с нового абзаца": return .newParagraph
        case "удали это", "удали последнее", "стереть это", "сотри это", "отмена": return .deleteLast
        default: return nil
        }
    }
}

/// Склейка фраз в связный текст: пробелы между фразами, регистр после паузы посреди предложения,
/// учёт того, что уже напечатано в этом сеансе.
nonisolated struct TextJoiner {
    /// Всё напечатанное в сеансе.
    private(set) var typed = ""
    /// Сколько символов напечатала каждая фраза — для команды «удали это».
    private var utterances: [Int] = []
    private var current = 0
    private var utteranceOpen = false

    var lastCharacter: Character? { typed.last }

    /// Слова текущей фразы → текст для печати.
    mutating func append(_ words: [String]) -> String {
        guard !words.isEmpty else { return "" }
        var text = ""
        if !utteranceOpen {
            utteranceOpen = true
            current = 0
            if let last = typed.last, !last.isWhitespace { text += " " }
        } else {
            text += " "
        }
        var words = words
        if current == 0, let last = typed.last, !".!?…\n".contains(last) {
            // Пауза посреди предложения: модель начнёт фразу с заглавной — возвращаем строчную.
            words[0] = Self.decapitalize(words[0])
        }
        text += words.joined(separator: " ")
        typed += text
        current += text.count
        return text
    }

    mutating func closeUtterance() {
        if utteranceOpen && current > 0 { utterances.append(current) }
        utteranceOpen = false
        current = 0
    }

    /// Команда → что напечатать и сколько стереть.
    mutating func apply(_ command: VoiceCommand) -> (text: String, backspaces: Int) {
        closeUtterance()
        switch command {
        case .newLine:
            typed += "\n"
            utterances.append(1)
            return ("\n", 0)
        case .newParagraph:
            typed += "\n\n"
            utterances.append(2)
            return ("\n\n", 0)
        case .deleteLast:
            guard let n = utterances.popLast() else { return ("", 0) }
            typed.removeLast(min(n, typed.count))
            return ("", n)
        }
    }

    static func decapitalize(_ word: String) -> String {
        guard let first = word.first, first.isUppercase else { return word }
        let rest = word.dropFirst()
        // Аббревиатуры и имена-аббревиатуры («МГУ», «iOS») не трогаем.
        if let second = rest.first, second.isUppercase { return word }
        return first.lowercased() + rest
    }
}
