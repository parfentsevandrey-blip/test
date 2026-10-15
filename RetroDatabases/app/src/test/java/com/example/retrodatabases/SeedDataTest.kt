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
import com.example.retrodatabases.data.seed.db10Records
import com.example.retrodatabases.data.seed.db11Records
import com.example.retrodatabases.data.seed.db12Records
import com.example.retrodatabases.data.seed.db13Records
import com.example.retrodatabases.data.seed.db14Records
import com.example.retrodatabases.data.seed.db15Records
import com.example.retrodatabases.data.seed.db16Records
import com.example.retrodatabases.data.seed.db17Records
import com.example.retrodatabases.data.seed.db18Records
import com.example.retrodatabases.data.seed.db19Records
import com.example.retrodatabases.data.seed.db20Records
import com.example.retrodatabases.data.seed.db21Records
import com.example.retrodatabases.data.seed.db22Records
import com.example.retrodatabases.data.seed.db23Records
import com.example.retrodatabases.data.seed.db24Records
import com.example.retrodatabases.data.seed.db25Records
import com.example.retrodatabases.data.seed.db26Records
import com.example.retrodatabases.data.seed.db27Records
import com.example.retrodatabases.data.seed.db28Records
import com.example.retrodatabases.data.seed.db29Records
import com.example.retrodatabases.data.seed.db30Records
import com.example.retrodatabases.data.seed.db31Records
import com.example.retrodatabases.data.seed.db32Records
import com.example.retrodatabases.data.seed.db33Records
import com.example.retrodatabases.data.seed.db34Records
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    private val catalogues = listOf(
        db1Records, db2Records, db3Records, db4Records, db5Records, db6Records, db7Records, db8Records, db9Records,
        db10Records, db11Records, db12Records, db13Records, db14Records,
        db15Records, db16Records, db17Records, db18Records, db19Records,
        db20Records, db21Records, db22Records, db23Records, db24Records, db25Records, db26Records,
        db27Records, db28Records, db29Records, db30Records, db31Records, db32Records, db33Records, db34Records,
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
        assertTrue(db10Records.all { it.databaseId == 10L })
        assertTrue(db11Records.all { it.databaseId == 11L })
        assertTrue(db12Records.all { it.databaseId == 12L })
        assertTrue(db13Records.all { it.databaseId == 13L })
        assertTrue(db14Records.all { it.databaseId == 14L })
        assertTrue(db15Records.all { it.databaseId == 15L })
        assertTrue(db16Records.all { it.databaseId == 16L })
        assertTrue(db17Records.all { it.databaseId == 17L })
        assertTrue(db18Records.all { it.databaseId == 18L })
        assertTrue(db19Records.all { it.databaseId == 19L })
        assertTrue(db20Records.all { it.databaseId == 20L })
        assertTrue(db21Records.all { it.databaseId == 21L })
        assertTrue(db22Records.all { it.databaseId == 22L })
        assertTrue(db23Records.all { it.databaseId == 23L })
        assertTrue(db24Records.all { it.databaseId == 24L })
        assertTrue(db25Records.all { it.databaseId == 25L })
        assertTrue(db26Records.all { it.databaseId == 26L })
        assertTrue(db27Records.all { it.databaseId == 27L })
        assertTrue(db28Records.all { it.databaseId == 28L })
        assertTrue(db29Records.all { it.databaseId == 29L })
        assertTrue(db30Records.all { it.databaseId == 30L })
        assertTrue(db31Records.all { it.databaseId == 31L })
        assertTrue(db32Records.all { it.databaseId == 32L })
        assertTrue(db33Records.all { it.databaseId == 33L })
        assertTrue(db34Records.all { it.databaseId == 34L })
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
        assertTrue(db10Records.all { it.year in 1940..1950 })
        assertTrue(db11Records.all { it.year in 1930..1940 })
        assertTrue(db12Records.all { it.year in 1920..1930 })
        assertTrue(db13Records.all { it.year in 1910..1920 })
        assertTrue(db14Records.all { it.year in 1900..1910 })
        assertTrue(db15Records.all { it.year in 1890..1900 })
        assertTrue(db16Records.all { it.year in 1880..1890 })
        assertTrue(db17Records.all { it.year in 1870..1880 })
        assertTrue(db18Records.all { it.year in 1860..1870 })
        assertTrue(db19Records.all { it.year in 1850..1860 })
        assertTrue(db20Records.all { it.year in 1840..1850 })
        assertTrue(db21Records.all { it.year in 1830..1840 })
        assertTrue(db22Records.all { it.year in 1820..1830 })
        assertTrue(db23Records.all { it.year in 1810..1820 })
        assertTrue(db24Records.all { it.year in 1800..1810 })
        assertTrue(db25Records.all { it.year in 1790..1800 })
        assertTrue(db26Records.all { it.year in 1780..1790 })
        assertTrue(db27Records.all { it.year in 1770..1780 })
        assertTrue(db28Records.all { it.year in 1760..1770 })
        assertTrue(db29Records.all { it.year in 1750..1760 })
        assertTrue(db30Records.all { it.year in 1740..1750 })
        assertTrue(db31Records.all { it.year in 1730..1740 })
        assertTrue(db32Records.all { it.year in 1720..1730 })
        assertTrue(db33Records.all { it.year in 1710..1720 })
        assertTrue(db34Records.all { it.year in 1700..1710 })
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
