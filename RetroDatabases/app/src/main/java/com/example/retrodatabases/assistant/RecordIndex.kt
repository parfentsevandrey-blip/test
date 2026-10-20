package com.example.retrodatabases.assistant

import com.example.retrodatabases.data.RecordEntity
import kotlin.math.ln

/**
 * A compact BM25 index over the catalogue records. Each field adds a weight to the terms it contains: the title counts
 * most, then the original title, the section and the year, then the author, the kind, the summary and last the details.
 * The index is built once from the records and only read afterwards, so it can be shared.
 */
class RecordIndex(private val records: List<RecordEntity>) {

    /** One matching record: its score (higher is better) and its position in the records list. */
    data class Hit(val record: RecordEntity, val score: Double, val doc: Int)

    /** The records that contain one term, with the weight of the term in each: two parallel arrays, no boxing. */
    private class Postings {
        var docs = IntArray(4)
        var weights = FloatArray(4)
        var size = 0

        fun add(doc: Int, weight: Float) {
            if (size == docs.size) {
                docs = docs.copyOf(size * 2)
                weights = weights.copyOf(size * 2)
            }
            docs[size] = doc
            weights[size] = weight
            size++
        }
    }

    private val postings = HashMap<String, Postings>()
    private val lengths = FloatArray(records.size)
    private val averageLength: Float

    init {
        records.forEachIndexed { doc, record ->
            val weights = HashMap<String, Float>()
            var length = 0f
            for ((text, fieldWeight) in fieldsOf(record)) {
                for (token in Tokenizer.contentTokens(text)) {
                    weights[token] = (weights[token] ?: 0f) + fieldWeight
                    length += fieldWeight
                }
            }
            lengths[doc] = length
            for ((token, weight) in weights) {
                postings.getOrPut(token) { Postings() }.add(doc, weight)
            }
        }
        averageLength = if (records.isEmpty()) 1f else lengths.average().toFloat().coerceAtLeast(1f)
    }

    /** Number of distinct terms in the index. */
    val termCount: Int get() = postings.size

    /** Number of term and record pairs in the index: a measure of its size. */
    val postingCount: Int get() = postings.values.sumOf { it.size }

    /** The records that match the query, best first. Records that share no term with the query are left out. */
    fun search(query: String, limit: Int = 10): List<Hit> {
        val terms = Tokenizer.contentTokens(query).distinct()
        if (terms.isEmpty() || records.isEmpty()) return emptyList()
        val scores = HashMap<Int, Double>()
        for (term in terms) {
            val list = postings[term] ?: continue
            val idf = ln(1.0 + (records.size - list.size + 0.5) / (list.size + 0.5))
            for (i in 0 until list.size) {
                val doc = list.docs[i]
                val tf = list.weights[i].toDouble()
                val normalised = K1 * (1.0 - B + B * (lengths[doc] / averageLength))
                scores[doc] = (scores[doc] ?: 0.0) + idf * tf * (K1 + 1.0) / (tf + normalised)
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { records[it.key].title })
            .take(limit)
            .map { Hit(records[it.key], it.value, it.key) }
    }

    private fun fieldsOf(record: RecordEntity): List<Pair<String, Float>> = listOf(
        record.title to 3f,
        record.originalTitle to 2f,
        record.section to 2f,
        record.year.toString() to 2f,
        record.author to 1.5f,
        record.kind.label to 1f,
        record.summary to 1f,
        record.details to 0.5f,
    )

    private companion object {
        const val K1 = 1.2
        const val B = 0.75
    }
}
