package com.example.retrodatabases.assistant

import kotlin.math.exp
import kotlin.math.ln

/** What the user wants from the assistant. */
enum class Intent { FIND, DATABASES, COVERAGE, RUNNABLE, APP_HELP, STATS, UNKNOWN }

/**
 * A multinomial naive Bayes classifier over word tokens. It is trained on the example questions in [TrainingExamples]
 * when it is created. It keeps only word counts in memory, and needs no network and no model file.
 */
class IntentClassifier(examples: List<Pair<Intent, String>> = TrainingExamples.all) {

    private val logPrior = HashMap<Intent, Double>()
    private val logLikelihood = HashMap<Intent, Map<String, Double>>()
    private val logUnseen = HashMap<Intent, Double>()
    private val vocabulary: Set<String>

    init {
        val counts = HashMap<Intent, HashMap<String, Int>>()
        val documents = HashMap<Intent, Int>()
        for ((intent, text) in examples) {
            documents[intent] = (documents[intent] ?: 0) + 1
            val bag = counts.getOrPut(intent) { HashMap() }
            for (token in Tokenizer.tokens(text)) bag[token] = (bag[token] ?: 0) + 1
        }
        vocabulary = counts.values.flatMapTo(HashSet()) { it.keys }
        val total = examples.size.toDouble()
        for ((intent, count) in documents) {
            logPrior[intent] = ln(count / total)
            val bag = counts.getValue(intent)
            // Laplace smoothing: every word of the vocabulary gets one extra count for every intent.
            val denominator = bag.values.sum() + vocabulary.size.toDouble()
            logLikelihood[intent] = bag.mapValues { (_, c) -> ln((c + 1) / denominator) }
            logUnseen[intent] = ln(1.0 / denominator)
        }
    }

    /**
     * The most likely intent and its probability among the intents (0 to 1). Words that the training set never saw
     * are ignored; a question made only of such words gets [Intent.UNKNOWN] with probability 0.
     */
    fun classify(text: String): Pair<Intent, Double> {
        val known = Tokenizer.tokens(text).filter { it in vocabulary }
        if (known.isEmpty()) return Intent.UNKNOWN to 0.0
        val scores = logPrior.mapValues { (intent, prior) ->
            val likelihood = logLikelihood.getValue(intent)
            val unseen = logUnseen.getValue(intent)
            prior + known.sumOf { token -> likelihood[token] ?: unseen }
        }
        val best = scores.maxByOrNull { it.value } ?: return Intent.UNKNOWN to 0.0
        val top = best.value
        val sum = scores.values.sumOf { exp(it - top) }
        return best.key to 1.0 / sum
    }
}

/** Example questions for each intent, in English and in Russian. */
object TrainingExamples {

    private val byIntent: Map<Intent, List<String>> = mapOf(
        Intent.FIND to listOf(
            "find the record about Liu Hui",
            "search for the abacus",
            "where is the astrolabe described",
            "show records about ciphers",
            "I want something about printing presses",
            "look up Babbage",
            "is there anything on the telegraph",
            "найди запись про абак",
            "поиск про шифры",
            "что есть про телеграф",
            "покажи записи о печатном станке",
            "где описан астролябия",
        ),
        Intent.DATABASES to listOf(
            "list the databases",
            "which databases are there",
            "show all databases",
            "how many databases do you have",
            "какие есть базы данных",
            "покажи все базы",
            "сколько баз",
            "список баз данных",
        ),
        Intent.COVERAGE to listOf(
            "which database covers the year 1850",
            "what years does database 12 cover",
            "what is in database 5",
            "which database has the 1700s",
            "какая база покрывает 1850 год",
            "что в базе 12",
            "за какие годы база 5",
            "в какой базе есть 1600 год",
        ),
        Intent.RUNNABLE to listOf(
            "show me runnable examples",
            "which records have code I can run",
            "give me a program to try",
            "any examples with code",
            "покажи примеры кода",
            "какие записи можно запустить",
            "программы для запуска",
            "есть ли код, который можно проверить",
        ),
        Intent.APP_HELP to listOf(
            "how do I use the app",
            "how do I search",
            "what do the filters do",
            "how do I add a favorite",
            "what does rare mean",
            "help me navigate",
            "как пользоваться приложением",
            "как искать записи",
            "что значит редкая запись",
            "как добавить в избранное",
        ),
        Intent.STATS to listOf(
            "how many records are there",
            "how many rare records",
            "total number of records",
            "how many languages are in the catalogue",
            "сколько всего записей",
            "сколько редких записей",
            "сколько языков в каталоге",
        ),
    )

    /** Every example as a pair of intent and text. */
    val all: List<Pair<Intent, String>> = byIntent.flatMap { (intent, texts) -> texts.map { intent to it } }
}
