package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Builds one catalogue record. databaseId says which database the record belongs to and defaults to database 1.
 * Room assigns the record id on insert.
 * Instructions and examples are left-aligned with trimIndent().
 * Two markers are replaced inside instructions and examples: <TAB> becomes a tab character (needed for Makefiles),
 * and <DOLLAR> becomes a dollar sign (needed for Perl, PHP and shell code, which would otherwise start a template
 * inside a raw string).
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
    databaseId: Long = 1,
): RecordEntity = RecordEntity(
    databaseId = databaseId,
    kind = kind,
    section = section,
    title = title,
    originalTitle = originalTitle,
    author = author,
    year = year,
    summary = summary,
    details = details,
    instructions = instructions?.trimIndent()?.replaceMarkers(),
    example = example?.trimIndent()?.replaceMarkers(),
    rare = rare,
)

private fun String.replaceMarkers(): String =
    replace("<TAB>", "\t").replace("<DOLLAR>", "\$")
