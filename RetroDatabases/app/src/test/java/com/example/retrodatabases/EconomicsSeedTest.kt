package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The economics databases (205 to 212): every economic model with its best and worst practice, and the compromises
 * drawn from them. The generator checks the same rules; these tests check the seed data the app really ships.
 */
class EconomicsSeedTest {

    private fun recordsOf(databaseId: Long) = seedRecords.filter { it.databaseId == databaseId }

    @Test
    fun theModelsDatabaseAndEachPracticeHaveEveryModelAndOneCompromise() {
        for (id in listOf(205L, 207L, 208L, 209L, 210L, 211L, 212L)) {
            val records = recordsOf(id)
            assertEquals("models in database $id", 36, records.count { it.kind == Kind.MODEL })
            assertEquals("compromises in database $id", 1, records.count { it.kind == Kind.COMPROMISE })
        }
    }

    @Test
    fun theSpheresDatabaseHasOneCompromisePerSphereAndOneForAll() {
        val records = recordsOf(206L)
        assertEquals(0, records.count { it.kind == Kind.MODEL })
        assertEquals(17, records.count { it.kind == Kind.COMPROMISE })
    }

    @Test
    fun everyEconomicsRecordNamesItsSourceAndEveryCompromiseSaysWhatItKeepsAndBlocks() {
        val economics = seedRecords.filter { it.databaseId in 205L..212L }
        assertTrue(economics.all { "Source:" in it.details })
        val compromises = economics.filter { it.kind == Kind.COMPROMISE }
        assertTrue(compromises.all { "Keeps:" in it.details && "Blocks:" in it.details })
    }
}
