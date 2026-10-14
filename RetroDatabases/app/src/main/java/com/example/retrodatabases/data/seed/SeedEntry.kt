package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Builds a record of database 1. databaseId is fixed; Room assigns the id on insert.
 * Instructions and examples are left-aligned with trimIndent().
 * In example code the <TAB> marker is replaced with a tab character (needed for Makefiles).
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
