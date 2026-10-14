package com.example.retrodatabases.ui

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Filters records in the app: kind, favorites, rare records and text search.
 * Search ignores case. SQLite LIKE folds case only for ASCII, so the text is lowercased in Kotlin instead.
 */
fun filterRecords(
    records: List<RecordEntity>,
    query: String,
    kind: Kind?,
    favoritesOnly: Boolean,
    rareOnly: Boolean,
): List<RecordEntity> {
    val needle = query.trim().lowercase()
    return records.filter { record ->
        (kind == null || record.kind == kind) &&
            (!favoritesOnly || record.favorite) &&
            (!rareOnly || record.rare) &&
            (needle.isEmpty() || record.searchableText().contains(needle))
    }
}

private fun RecordEntity.searchableText(): String =
    listOf(title, originalTitle, author, section, summary, year.toString())
        .joinToString(separator = " ")
        .lowercase()
