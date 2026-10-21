package com.example.retrodatabases.assistant

import com.example.retrodatabases.data.DatabaseEntity
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * The on-device helper for the catalogue. It decides what a question asks for (see [IntentClassifier]), reads the
 * database number, the years and the kind out of the words, looks the records up in a [RecordIndex] and writes a
 * short answer. Everything runs on the phone: no question leaves the device.
 */
class Assistant(
    private val databases: List<DatabaseEntity>,
    private val records: List<RecordEntity>,
    private val years: Map<Long, IntRange>,
    private val classifier: IntentClassifier = IntentClassifier(),
) {

    /** What was understood, the text to show, and the records to offer. */
    data class Answer(val intent: Intent, val text: String, val hits: List<RecordIndex.Hit>)

    private val index: RecordIndex by lazy { RecordIndex(records) }
    private val databaseById: Map<Long, DatabaseEntity> = databases.associateBy { it.id }
    private val countByDatabase: Map<Long, Int> = records.groupingBy { it.databaseId }.eachCount()
    private val countByKind: Map<Kind, Int> = records.groupingBy { it.kind }.eachCount()
    private val rareCount: Int = records.count { it.rare }

    /** Answers one question. The answer is in Russian when the question has Cyrillic letters, otherwise in English. */
    fun answer(question: String): Answer {
        val russian = question.any { it in 'Ѐ'..'ӿ' }
        val (guess, confidence) = classifier.classify(question)
        val intent = when {
            startsWithSearchVerb(question) -> Intent.FIND
            confidence < MIN_CONFIDENCE -> Intent.UNKNOWN
            else -> guess
        }
        val slots = Slots.parse(question, databaseById.keys)
        return when (intent) {
            Intent.FIND -> find(question, slots, russian, fallback = false)
            Intent.DATABASES -> listDatabases(russian)
            Intent.COVERAGE -> coverage(slots, russian)
            Intent.RUNNABLE -> runnable(question, slots, russian)
            Intent.APP_HELP -> Answer(Intent.APP_HELP, if (russian) HELP_RU else HELP_EN, emptyList())
            Intent.STATS -> stats(slots, russian)
            Intent.UNKNOWN -> find(question, slots, russian, fallback = true)
        }
    }

    /**
     * A question that opens with a search verb asks for records, even when it names a database or a year. The
     * exception is a question about how to search: one that contains "how" or "как" goes to the classifier.
     */
    private fun startsWithSearchVerb(question: String): Boolean {
        val tokens = Tokenizer.tokens(question)
        val first = tokens.firstOrNull() ?: return false
        return first in SEARCH_VERBS && tokens.none { it in HOW_WORDS }
    }

    private fun find(question: String, slots: Slots, russian: Boolean, fallback: Boolean): Answer {
        val hits = index.search(question, limit = SEARCH_POOL)
            .filter { slots.matches(it.record) }
            .take(MAX_HITS)
        if (hits.isEmpty()) {
            val text = when {
                fallback && russian -> NOT_UNDERSTOOD_RU
                fallback -> NOT_UNDERSTOOD_EN
                russian -> NOTHING_FOUND_RU
                else -> NOTHING_FOUND_EN
            }
            return Answer(if (fallback) Intent.UNKNOWN else Intent.FIND, text, emptyList())
        }
        return Answer(Intent.FIND, if (russian) BEST_MATCHES_RU else BEST_MATCHES_EN, hits)
    }

    private fun listDatabases(russian: Boolean): Answer {
        val shown = databases.sortedBy { it.id }.take(LIST_LIMIT)
        val lines = shown.joinToString("\n") { db ->
            "Database ${db.id}: ${yearsText(db.id, russian)}, ${countByDatabase[db.id] ?: 0} records"
        }
        val rest = databases.size - shown.size
        val first = databases.minOfOrNull { it.id } ?: 0L
        val last = databases.maxOfOrNull { it.id } ?: 0L
        val head = if (russian) {
            "Всего баз: ${databases.size}, номера с $first по $last."
        } else {
            "There are ${databases.size} databases, numbered $first to $last."
        }
        val tail = when {
            rest <= 0 -> ""
            russian -> "\nи ещё $rest. Все базы перечислены на первом экране."
            else -> "\nand $rest more. The Databases screen lists them all."
        }
        return Answer(Intent.DATABASES, "$head\n$lines$tail", emptyList())
    }

    private fun coverage(slots: Slots, russian: Boolean): Answer {
        slots.databaseId?.let { id ->
            val name = databaseById.getValue(id).name
            val text = if (russian) {
                "База $id ($name) охватывает ${yearsText(id, true)} и содержит ${countByDatabase[id] ?: 0} записей."
            } else {
                "Database $id ($name) covers ${yearsText(id, false)} and has ${countByDatabase[id] ?: 0} records."
            }
            return Answer(Intent.COVERAGE, text, emptyList())
        }
        val wanted = slots.years ?: return Answer(
            Intent.COVERAGE,
            if (russian) ASK_NUMBER_RU else ASK_NUMBER_EN,
            emptyList(),
        )
        val matching = databases
            .filter { db -> yearsOf(db.id).let { range -> range.first <= wanted.last && wanted.first <= range.last } }
            .map { it.id }
            .sorted()
        val text = when {
            matching.isEmpty() && russian -> "Ни одна база не охватывает эти годы."
            matching.isEmpty() -> "No database covers those years."
            russian -> "Эти годы охватывают базы: ${matching.joinToString(", ")}."
            else -> "Those years are covered by database ${matching.joinToString(", ")}."
        }
        return Answer(Intent.COVERAGE, text, emptyList())
    }

    private fun runnable(question: String, slots: Slots, russian: Boolean): Answer {
        val scores = index.search(question, limit = records.size).associate { it.doc to it.score }
        val pool = records.indices.filter { doc ->
            val record = records[doc]
            record.kind in RUNNABLE_KINDS && record.example != null && slots.matches(record)
        }
        if (pool.isEmpty()) {
            return Answer(Intent.RUNNABLE, if (russian) NO_RUNNABLE_RU else NO_RUNNABLE_EN, emptyList())
        }
        val hits = pool
            .sortedWith(compareByDescending<Int> { scores[it] ?: 0.0 }.thenBy { records[it].title })
            .take(MAX_HITS)
            .map { RecordIndex.Hit(records[it], scores[it] ?: 0.0, it) }
        val text = if (russian) {
            "Записей с кодом для запуска: ${pool.size}. Вот первые ${hits.size}."
        } else {
            "Runnable records: ${pool.size}. The first ${hits.size} are below."
        }
        return Answer(Intent.RUNNABLE, text, hits)
    }

    private fun stats(slots: Slots, russian: Boolean): Answer {
        val id = slots.databaseId
        if (id != null) {
            val rare = records.count { it.databaseId == id && it.rare }
            val text = if (russian) {
                "База $id: записей ${countByDatabase[id] ?: 0}, из них редких $rare."
            } else {
                "Database $id has ${countByDatabase[id] ?: 0} records, $rare of them rare."
            }
            return Answer(Intent.STATS, text, emptyList())
        }
        val byKind = Kind.entries.joinToString(", ") { "${kindLabel(it, russian)} ${countByKind[it] ?: 0}" }
        val text = if (russian) {
            "В каталоге ${records.size} записей в ${databases.size} базах, редких: $rareCount. По видам: $byKind."
        } else {
            "The catalogue has ${records.size} records in ${databases.size} databases; $rareCount are rare. By kind: $byKind."
        }
        return Answer(Intent.STATS, text, emptyList())
    }

    /** The years of a database: its declared range, or the years of its records when none is declared. */
    private fun yearsOf(id: Long): IntRange = years[id] ?: records
        .filter { it.databaseId == id }
        .map { it.year }
        .let { all -> if (all.isEmpty()) 0..0 else all.min()..all.max() }

    private fun yearsText(id: Long, russian: Boolean): String {
        val range = yearsOf(id)
        return if (russian) "годы ${range.first}–${range.last}" else "years ${range.first} to ${range.last}"
    }

    private fun kindLabel(kind: Kind, russian: Boolean): String = if (!russian) kind.label else when (kind) {
        Kind.LANGUAGE -> "Языки"
        Kind.SYSTEM -> "Системы"
        Kind.DEVICE -> "Устройства"
        Kind.TOOL -> "Инструменты"
        Kind.PRACTICE -> "Практики"
        Kind.GUIDE -> "Руководства"
        Kind.EXAMPLE -> "Примеры"
        Kind.PROJECT -> "Проекты"
        Kind.FLEX -> "Flex"
        Kind.CROSS -> "Cross-code"
        Kind.MODEL -> "Экономические модели"
        Kind.COMPROMISE -> "Компромиссы"
    }

    /** The database number, the years and the kind that a question names. Each one is optional. */
    private class Slots(val databaseId: Long?, val years: IntRange?, val kind: Kind?) {

        fun matches(record: RecordEntity): Boolean =
            (databaseId == null || record.databaseId == databaseId) &&
                (years == null || record.year in years) &&
                (kind == null || record.kind == kind)

        companion object {
            private val databaseNumber = Regex("(?:database|db|база|базы|базе|базу|бд)\\s*№?\\s*(\\d{1,3})")
            private val decade = Regex("\\b(\\d{3})0(?:s\\b|-\\p{L}+)")
            private val number = Regex("\\b(\\d{1,4})\\b")

            /** Kind words: each kind is recognised by the start of a word, so Russian endings still match. */
            private val kindStems: List<Pair<Kind, List<String>>> = listOf(
                Kind.LANGUAGE to listOf("language", "язык"),
                Kind.SYSTEM to listOf("system", "систем"),
                Kind.DEVICE to listOf("device", "устройств"),
                Kind.TOOL to listOf("tool", "инструмент"),
                Kind.PRACTICE to listOf("practice", "практик"),
                Kind.GUIDE to listOf("guide", "руковод", "инструкц"),
                Kind.EXAMPLE to listOf("example", "пример"),
                Kind.PROJECT to listOf("project", "проект"),
                Kind.FLEX to listOf("flex"),
                Kind.CROSS to listOf("cross"),
                Kind.MODEL to listOf("model", "модел", "econom", "экономи"),
                Kind.COMPROMISE to listOf("compromis", "компромисс"),
            )

            fun parse(question: String, databaseIds: Set<Long>): Slots {
                val text = question.lowercase().replace('ё', 'е')
                val named = databaseNumber.find(text)?.groupValues?.get(1)?.toLong()
                val databaseId = named?.takeIf { it in databaseIds }
                val years: IntRange? = decade.find(text)?.let { match ->
                    val start = match.groupValues[1].toInt() * 10
                    start..start + 9
                } ?: yearWords(text, named)
                val tokens = Tokenizer.tokens(question)
                val kind = kindStems.firstNotNullOfOrNull { (kind, stems) ->
                    kind.takeIf { tokens.any { token -> stems.any { token.startsWith(it) } } }
                }
                return Slots(databaseId, years, kind)
            }

            /**
             * A year is a number of three or four digits, or a shorter number after the word for year. Shorter numbers
             * on their own are usually something else (a chapter, a count) and are ignored.
             */
            private fun yearWords(text: String, named: Long?): IntRange? {
                val yearContext = text.contains("year") || text.contains("год")
                return number.findAll(text)
                    .map { it.groupValues[1] }
                    .filter { it.toLong() != named }
                    .filter { it.length >= 3 || yearContext }
                    .map { it.toInt() }
                    .firstOrNull { it in 0..2100 }
                    ?.let { it..it }
            }
        }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.4
        val SEARCH_VERBS = setOf("find", "search", "look", "найди", "найти", "поиск", "ищи", "ищу")
        val HOW_WORDS = setOf("how", "как")
        const val MAX_HITS = 8
        const val SEARCH_POOL = 50
        const val LIST_LIMIT = 12
        val RUNNABLE_KINDS = setOf(Kind.GUIDE, Kind.EXAMPLE, Kind.PROJECT, Kind.FLEX, Kind.CROSS)

        const val HELP_EN = "Pick a database on the first screen to open its catalogue. Inside, type words to search by " +
            "title, original title, author, year or section. The chips filter by kind, favorites and rare records. " +
            "Open a record to read its summary, how it works and its code; the star adds it to favorites. Ask me to " +
            "find a term, list the databases, say which database covers a year, show runnable examples, count the " +
            "records or explain the app."

        const val HELP_RU = "На первом экране выберите базу, чтобы открыть её каталог. Внутри вводите слова для поиска " +
            "по названию, оригинальному названию, автору, году или разделу. Фишки фильтруют по виду записи, " +
            "избранному и редким записям. Откройте запись, чтобы прочитать краткое описание, как она устроена и её " +
            "код; звёздочка добавляет запись в избранное. Можно спросить: найти запись, перечислить базы, какая база " +
            "покрывает год, показать примеры для запуска, посчитать записи или объяснить приложение."

        const val BEST_MATCHES_EN = "Best matches:"
        const val BEST_MATCHES_RU = "Лучшие совпадения:"
        const val NOTHING_FOUND_EN = "Nothing matched. Try a name, a year, a database number or a kind such as Devices."
        const val NOTHING_FOUND_RU = "Ничего не нашлось. Попробуйте имя, год, номер базы или вид записи, например «Устройства»."
        const val NOT_UNDERSTOOD_EN = "I could not tell what you need. Try a word to search, or ask how to use the app."
        const val NOT_UNDERSTOOD_RU = "Не понял, что нужно. Попробуйте слово для поиска или спросите, как пользоваться приложением."
        const val ASK_NUMBER_EN = "Name a database number or a year, for example: which database covers 1850?"
        const val ASK_NUMBER_RU = "Назовите номер базы или год, например: какая база покрывает 1850 год?"
        const val NO_RUNNABLE_EN = "No runnable records match. Try another database or other words."
        const val NO_RUNNABLE_RU = "Подходящих записей с кодом нет. Попробуйте другую базу или другие слова."
    }
}
