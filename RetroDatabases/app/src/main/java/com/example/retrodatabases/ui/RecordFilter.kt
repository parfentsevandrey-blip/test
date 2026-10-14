package com.example.retrodatabases.ui

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Фильтрация записей на стороне приложения: вид, избранное, редкие и текстовый поиск.
 * Поиск без учёта регистра. SQLite LIKE не понимает регистр кириллицы, поэтому используем lowercase().
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
