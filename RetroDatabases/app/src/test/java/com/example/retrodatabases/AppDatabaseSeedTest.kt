package com.example.retrodatabases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.seed.DATABASE_VERSION
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import com.example.retrodatabases.data.seedMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the real database: schema validation, the seed SQL in onCreate, the migrations and the favourite toggle.
 * The checks walk the catalogue, so a new database needs no test code of its own.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseSeedTest {

    @Test
    fun firstOpenSeedsEveryDatabaseWithItsRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.get(context).libraryDao()

        val databases = dao.observeDatabases().first()
        assertEquals(seedDatabases.size, databases.size)
        for (database in seedDatabases) {
            val expected = seedRecords.count { it.databaseId == database.id }
            assertEquals(expected, databases.single { it.id == database.id }.recordCount)
        }
    }

    @Test
    fun migrationsCoverEveryVersionUpToTheCurrentOne() {
        assertEquals((2..DATABASE_VERSION).toList(), seedMigrations.map { it.endVersion })
    }

    @Test
    fun migrationRestoresEachDatabaseAndKeepsTheRest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        for (migration in seedMigrations) {
            val id = migration.endVersion.toLong()
            // A file made one version earlier has no database `id`: remove it, then run the migration.
            sqlite.execSQL("DELETE FROM records WHERE databaseId = $id")
            sqlite.execSQL("DELETE FROM databases WHERE id = $id")
            migration.migrate(sqlite)

            assertEquals(seedRecords.count { it.databaseId == id }, dao.observeRecords(id).first().size)
        }
        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(seedRecords.count { it.databaseId == 1L }, dao.observeRecords(1L).first().size)
    }

    @Test
    fun toggleFavoriteStoresTheChange() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.get(context).libraryDao()

        val record = dao.observeRecords(1L).first().first()
        assertEquals(false, record.favorite)

        dao.toggleFavorite(record.id)
        val updated = dao.observeRecord(record.id).first()
        assertNotNull(updated)
        assertEquals(true, updated!!.favorite)
    }
}
