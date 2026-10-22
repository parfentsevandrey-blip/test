package com.example.retrodatabases

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The last economics database that the seed data holds; raised as each batch of databases is generated. */
private const val LAST_ECONOMICS_DATABASE = 231L

/**
 * The economics databases (205 to the last one in [LAST_ECONOMICS_DATABASE]): every economic model with its best and
 * worst practice, and the compromises drawn from them. Database 206 is the sphere database and is tested separately.
 * The generator checks the same rules; these tests check the seed data the app really ships.
 */
class EconomicsSeedTest {

    private fun recordsOf(databaseId: Long) = seedRecords.filter { it.databaseId == databaseId }

    @Test
    fun theModelsDatabaseAndEachPracticeHaveEveryModelAndOneCompromise() {
        for (id in (205L..LAST_ECONOMICS_DATABASE).filter { it != 206L }) {
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
        val economics = seedRecords.filter { it.databaseId in 205L..LAST_ECONOMICS_DATABASE }
        assertTrue(economics.all { "Source:" in it.details })
        val compromises = economics.filter { it.kind == Kind.COMPROMISE }
        assertTrue(compromises.all { "Keeps:" in it.details && "Blocks:" in it.details })
    }
}
