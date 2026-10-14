package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.db1Records
import com.example.retrodatabases.data.seed.db2Records
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun seededDatabasesHaveUniqueIdsAndTheirOwnRecords() {
        val ids = seedDatabases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(seedRecords.all { it.databaseId in ids })
        assertTrue(db1Records.all { it.databaseId == 1L })
        assertTrue(db2Records.all { it.databaseId == 2L })
    }

    @Test
    fun titlesAreUniqueWithinEachDatabase() {
        for (records in listOf(db1Records, db2Records)) {
            val titles = records.map { it.title }
            assertEquals(titles.size, titles.toSet().size)
        }
    }

    @Test
    fun yearsAreInTheRangeOfEachCatalogue() {
        assertTrue(db1Records.all { it.year in 1950..1990 })
        assertTrue(db2Records.all { it.year in 1990..1999 })
    }

    @Test
    fun everyKindHasRecordsInEachDatabase() {
        assertEquals(Kind.entries.toSet(), db1Records.map { it.kind }.toSet())
        assertEquals(Kind.entries.toSet(), db2Records.map { it.kind }.toSet())
    }

    @Test
    fun everyRecordHasSummaryAndDetails() {
        assertTrue(seedRecords.all { it.summary.isNotBlank() && it.details.isNotBlank() })
    }

    @Test
    fun projectsHaveInstructionsAndCode() {
        val projects = seedRecords.filter { it.kind == Kind.PROJECT }
        assertTrue(projects.isNotEmpty())
        assertTrue(projects.all { !it.instructions.isNullOrBlank() && !it.example.isNullOrBlank() })
    }

    @Test
    fun rareRecordsArePresentInEachDatabase() {
        assertTrue(db1Records.count { it.rare } > 0)
        assertTrue(db2Records.count { it.rare } > 0)
    }
}
