package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.db1Records
import com.example.retrodatabases.data.seed.seedDatabases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun databaseOneExistsAndAllRecordsBelongToIt() {
        val ids = seedDatabases.map { it.id }.toSet()
        assertTrue(1L in ids)
        assertTrue(db1Records.all { it.databaseId == 1L })
    }

    @Test
    fun titlesAreUniqueWithinDatabaseOne() {
        val titles = db1Records.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun yearsAreInTheRangeOfTheCatalogue() {
        assertTrue(db1Records.all { it.year in 1950..1990 })
    }

    @Test
    fun everyKindHasRecords() {
        assertEquals(Kind.entries.toSet(), db1Records.map { it.kind }.toSet())
    }

    @Test
    fun everyRecordHasSummaryAndDetails() {
        assertTrue(db1Records.all { it.summary.isNotBlank() && it.details.isNotBlank() })
    }

    @Test
    fun projectsHaveInstructionsAndCode() {
        val projects = db1Records.filter { it.kind == Kind.PROJECT }
        assertTrue(projects.isNotEmpty())
        assertTrue(projects.all { !it.instructions.isNullOrBlank() && !it.example.isNullOrBlank() })
    }

    @Test
    fun rareRecordsArePresent() {
        assertTrue(db1Records.count { it.rare } > 0)
    }
}
