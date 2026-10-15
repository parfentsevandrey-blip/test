package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.db1Records
import com.example.retrodatabases.data.seed.db2Records
import com.example.retrodatabases.data.seed.db3Records
import com.example.retrodatabases.data.seed.db4Records
import com.example.retrodatabases.data.seed.db5Records
import com.example.retrodatabases.data.seed.db6Records
import com.example.retrodatabases.data.seed.db7Records
import com.example.retrodatabases.data.seed.db8Records
import com.example.retrodatabases.data.seed.db9Records
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    private val catalogues = listOf(
        db1Records, db2Records, db3Records, db4Records, db5Records, db6Records, db7Records, db8Records, db9Records,
    )

    @Test
    fun seededDatabasesHaveUniqueIdsAndTheirOwnRecords() {
        val ids = seedDatabases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(seedRecords.all { it.databaseId in ids })
        assertTrue(db1Records.all { it.databaseId == 1L })
        assertTrue(db2Records.all { it.databaseId == 2L })
        assertTrue(db3Records.all { it.databaseId == 3L })
        assertTrue(db4Records.all { it.databaseId == 4L })
        assertTrue(db5Records.all { it.databaseId == 5L })
        assertTrue(db6Records.all { it.databaseId == 6L })
        assertTrue(db7Records.all { it.databaseId == 7L })
        assertTrue(db8Records.all { it.databaseId == 8L })
        assertTrue(db9Records.all { it.databaseId == 9L })
    }

    @Test
    fun titlesAreUniqueWithinEachDatabase() {
        for (records in catalogues) {
            val titles = records.map { it.title }
            assertEquals(titles.size, titles.toSet().size)
        }
    }

    @Test
    fun yearsAreInTheRangeOfEachCatalogue() {
        assertTrue(db1Records.all { it.year in 1950..1990 })
        assertTrue(db2Records.all { it.year in 1990..1999 })
        assertTrue(db3Records.all { it.year in 2000..2010 })
        assertTrue(db4Records.all { it.year in 2010..2020 })
        assertTrue(db5Records.all { it.year in 2020..2026 })
        assertTrue(db6Records.all { it.year in 1950..1960 })
        assertTrue(db7Records.all { it.year in 1960..1970 })
        assertTrue(db8Records.all { it.year in 1970..1980 })
        assertTrue(db9Records.all { it.year in 2026..2036 })
    }

    @Test
    fun everyKindHasRecordsInEachDatabase() {
        for (records in catalogues) {
            assertEquals(Kind.entries.toSet(), records.map { it.kind }.toSet())
        }
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
        for (records in catalogues) {
            assertTrue(records.count { it.rare } > 0)
        }
    }

    @Test
    fun forecastEntriesSayTheyAreForecasts() {
        val forecastKinds = setOf(Kind.LANGUAGE, Kind.SYSTEM, Kind.DEVICE, Kind.TOOL, Kind.PRACTICE)
        val forecasts = db9Records.filter { it.kind in forecastKinds }
        assertTrue(forecasts.isNotEmpty())
        assertTrue(forecasts.all { it.details.contains("forecast", ignoreCase = true) })
    }
}
