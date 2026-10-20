package com.example.retrodatabases.assistant

/**
 * Splits text into lowercase word tokens. Letters and digits of every script are kept, so English and Russian
 * questions go through the same code. The letter with diaeresis is folded into the plain letter, so both spellings match.
 */
object Tokenizer {

    private val separator = Regex("[^\\p{L}\\p{Nd}]+")

    /** Every token of the text, function words included. Used to recognise what a question asks for. */
    fun tokens(text: String): List<String> =
        text.lowercase()
            .replace('ё', 'е')
            .split(separator)
            .filter { it.isNotEmpty() }

    /** The tokens that carry the subject of a question: function words and one-letter tokens are dropped. */
    fun contentTokens(text: String): List<String> =
        tokens(text).filter { it.length > 1 && it !in stopWords }

    /** Function words and request words in English and Russian. They are too frequent to help a search. */
    private val stopWords: Set<String> = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "could", "do", "does", "find", "for", "from", "give",
        "how", "i", "in", "is", "it", "list", "me", "my", "of", "on", "or", "search", "show", "tell", "the", "to",
        "what", "where", "which", "who", "with", "you", "your", "there",
        "и", "в", "во", "не", "на", "что", "как", "где", "какой", "какая", "какие", "каких", "мне", "мой", "для", "по",
        "от", "из", "со", "у", "же", "ли", "это", "все", "покажи", "скажи", "найди", "найти", "поиск", "про", "есть",
        "можно", "сколько", "к", "ко", "об",
    )
}
