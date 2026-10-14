package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Собирает запись базы 1. databaseId фиксирован, id назначит Room при вставке.
 * Примеры и инструкции выравниваются по левому краю через trimIndent().
 * В коде примеров маркер <TAB> заменяется на символ табуляции (нужен для Makefile).
 */
internal fun entry(
    kind: Kind,
    section: String,
    title: String,
    originalTitle: String,
    author: String,
    year: Int,
    summary: String,
    details: String,
    instructions: String? = null,
    example: String? = null,
    rare: Boolean = false,
): RecordEntity = RecordEntity(
    databaseId = 1,
    kind = kind,
    section = section,
    title = title,
    originalTitle = originalTitle,
    author = author,
    year = year,
    summary = summary,
    details = details,
    instructions = instructions?.trimIndent(),
    example = example?.trimIndent()?.replace("<TAB>", "\t"),
    rare = rare,
)
