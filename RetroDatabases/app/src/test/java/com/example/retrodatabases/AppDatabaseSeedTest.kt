package com.example.retrodatabases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.MIGRATION_1_2
import com.example.retrodatabases.data.MIGRATION_2_3
import com.example.retrodatabases.data.MIGRATION_3_4
import com.example.retrodatabases.data.seed.db1Records
import com.example.retrodatabases.data.seed.db2Records
import com.example.retrodatabases.data.seed.db3Records
import com.example.retrodatabases.data.seed.db4Records
import com.example.retrodatabases.data.seed.seedDatabases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real database: schema validation, the seed SQL in onCreate, the migrations and the favourite toggle. */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseSeedTest {

    @Test
    fun firstOpenSeedsEveryDatabaseWithItsRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.get(context).libraryDao()

        val databases = dao.observeDatabases().first()
        assertEquals(seedDatabases.size, databases.size)
        assertEquals(db1Records.size, databases.single { it.id == 1L }.recordCount)
        assertEquals(db2Records.size, databases.single { it.id == 2L }.recordCount)
        assertEquals(db3Records.size, databases.single { it.id == 3L }.recordCount)
        assertEquals(db4Records.size, databases.single { it.id == 4L }.recordCount)

        val records = dao.observeRecords(1L).first()
        assertEquals(db1Records.size, records.size)
        assertTrue(records.any { it.kind == Kind.PROJECT && it.instructions != null })
        assertTrue(records.any { it.rare })
    }

    @Test
    fun migrationRestoresDatabaseTwoAndKeepsDatabaseOne() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 1 has no database 2: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 2")
        sqlite.execSQL("DELETE FROM databases WHERE id = 2")
        MIGRATION_1_2.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db2Records.size, dao.observeRecords(2L).first().size)
        assertEquals(db1Records.size, dao.observeRecords(1L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThree() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 2 has no database 3: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 3")
        sqlite.execSQL("DELETE FROM databases WHERE id = 3")
        MIGRATION_2_3.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db3Records.size, dao.observeRecords(3L).first().size)
        assertEquals(db2Records.size, dao.observeRecords(2L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseFour() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 3 has no database 4: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 4")
        sqlite.execSQL("DELETE FROM databases WHERE id = 4")
        MIGRATION_3_4.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db4Records.size, dao.observeRecords(4L).first().size)
        assertEquals(db3Records.size, dao.observeRecords(3L).first().size)
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
