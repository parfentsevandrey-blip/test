package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.db9Records
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import com.example.retrodatabases.data.seed.seedYears
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the seed data of every database in one pass. The lists of databases, records and years are generated
 * from tools/databases.json, so these checks cover a new database without any change here.
 */
class SeedDataTest {

    private val recordsByDatabase = seedRecords.groupBy { it.databaseId }

    @Test
    fun seededDatabasesHaveUniqueIdsAndTheirOwnRecords() {
        val ids = seedDatabases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(seedRecords.all { it.databaseId in ids })
        assertEquals(ids.toSet(), recordsByDatabase.keys)
        assertEquals(ids.toSet(), seedYears.keys)
    }

    @Test
    fun titlesAreUniqueWithinEachDatabase() {
        for (records in recordsByDatabase.values) {
            val titles = records.map { it.title }
            assertEquals(titles.size, titles.toSet().size)
        }
    }

    @Test
    fun yearsAreInTheRangeOfEachCatalogue() {
        for ((id, years) in seedYears) {
            assertTrue("database $id", recordsByDatabase.getValue(id).all { it.year in years })
        }
    }

    @Test
    fun everyKindHasRecordsInEachDatabase() {
        val codingKinds = Kind.entries.toSet() - setOf(Kind.MODEL, Kind.COMPROMISE)
        for ((databaseId, records) in recordsByDatabase) {
            val kinds = records.map { it.kind }.toSet()
            if (databaseId >= 205L) {
                assertTrue("database $databaseId has a compromise", Kind.COMPROMISE in kinds)
            } else {
                assertEquals(codingKinds, kinds)
            }
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
        // The economics databases (205 and up) hold no rare records: rarity belongs to the coding catalogue.
        for ((databaseId, records) in recordsByDatabase) {
            if (databaseId < 205L) {
                assertTrue("database $databaseId has a rare record", records.count { it.rare } > 0)
            }
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
