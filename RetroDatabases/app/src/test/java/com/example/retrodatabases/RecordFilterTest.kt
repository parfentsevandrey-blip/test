package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity
import com.example.retrodatabases.ui.filterRecords
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFilterTest {

    private val records = listOf(
        record(1, Kind.PRACTICE, "Структурное программирование", "Structured programming", 1972, favorite = true),
        record(2, Kind.LANGUAGE, "Язык C", "C", 1972),
        record(3, Kind.PRACTICE, "Граничные значения", "Boundary value analysis", 1979, rare = true),
    )

    @Test
    fun emptyQueryReturnsEverything() {
        assertEquals(listOf(1L, 2L, 3L), filterRecords(records, "", null, false, false).map { it.id })
    }

    @Test
    fun searchIsCaseInsensitiveForCyrillic() {
        assertEquals(listOf(1L), filterRecords(records, "СТРУКТУРНОЕ", null, false, false).map { it.id })
    }

    @Test
    fun searchMatchesOriginalTitleAndYear() {
        assertEquals(listOf(3L), filterRecords(records, "boundary", null, false, false).map { it.id })
        assertEquals(listOf(3L), filterRecords(records, "1979", null, false, false).map { it.id })
    }

    @Test
    fun kindFilterKeepsOnlyThatKind() {
        assertEquals(listOf(2L), filterRecords(records, "", Kind.LANGUAGE, false, false).map { it.id })
    }

    @Test
    fun favoritesAndRareFiltersCombine() {
        assertEquals(listOf(1L), filterRecords(records, "", null, true, false).map { it.id })
        assertEquals(listOf(3L), filterRecords(records, "", null, false, true).map { it.id })
        assertEquals(emptyList<Long>(), filterRecords(records, "", null, true, true).map { it.id })
    }

    private fun record(
        id: Long,
        kind: Kind,
        title: String,
        originalTitle: String,
        year: Int,
        favorite: Boolean = false,
        rare: Boolean = false,
    ) = RecordEntity(
        id = id,
        databaseId = 1,
        kind = kind,
        section = "Раздел",
        title = title,
        originalTitle = originalTitle,
        author = "Автор",
        year = year,
        summary = "Кратко",
        details = "Подробно",
        instructions = null,
        example = null,
        rare = rare,
        favorite = favorite,
    )
}
