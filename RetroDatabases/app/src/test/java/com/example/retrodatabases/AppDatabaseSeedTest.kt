package com.example.retrodatabases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.MIGRATION_1_2
import com.example.retrodatabases.data.MIGRATION_2_3
import com.example.retrodatabases.data.MIGRATION_3_4
import com.example.retrodatabases.data.MIGRATION_4_5
import com.example.retrodatabases.data.MIGRATION_5_6
import com.example.retrodatabases.data.MIGRATION_6_7
import com.example.retrodatabases.data.MIGRATION_7_8
import com.example.retrodatabases.data.MIGRATION_8_9
import com.example.retrodatabases.data.MIGRATION_9_10
import com.example.retrodatabases.data.MIGRATION_10_11
import com.example.retrodatabases.data.MIGRATION_11_12
import com.example.retrodatabases.data.MIGRATION_12_13
import com.example.retrodatabases.data.MIGRATION_13_14
import com.example.retrodatabases.data.MIGRATION_14_15
import com.example.retrodatabases.data.MIGRATION_15_16
import com.example.retrodatabases.data.MIGRATION_16_17
import com.example.retrodatabases.data.MIGRATION_17_18
import com.example.retrodatabases.data.MIGRATION_18_19
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
        assertEquals(db5Records.size, databases.single { it.id == 5L }.recordCount)
        assertEquals(db6Records.size, databases.single { it.id == 6L }.recordCount)
        assertEquals(db7Records.size, databases.single { it.id == 7L }.recordCount)
        assertEquals(db8Records.size, databases.single { it.id == 8L }.recordCount)
        assertEquals(db9Records.size, databases.single { it.id == 9L }.recordCount)
        assertEquals(db10Records.size, databases.single { it.id == 10L }.recordCount)
        assertEquals(db11Records.size, databases.single { it.id == 11L }.recordCount)
        assertEquals(db12Records.size, databases.single { it.id == 12L }.recordCount)
        assertEquals(db13Records.size, databases.single { it.id == 13L }.recordCount)
        assertEquals(db14Records.size, databases.single { it.id == 14L }.recordCount)
        assertEquals(db15Records.size, databases.single { it.id == 15L }.recordCount)
        assertEquals(db16Records.size, databases.single { it.id == 16L }.recordCount)
        assertEquals(db17Records.size, databases.single { it.id == 17L }.recordCount)
        assertEquals(db18Records.size, databases.single { it.id == 18L }.recordCount)
        assertEquals(db19Records.size, databases.single { it.id == 19L }.recordCount)

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
    fun migrationRestoresDatabaseFive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 4 has no database 5: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 5")
        sqlite.execSQL("DELETE FROM databases WHERE id = 5")
        MIGRATION_4_5.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db5Records.size, dao.observeRecords(5L).first().size)
        assertEquals(db4Records.size, dao.observeRecords(4L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseSix() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 5 has no database 6: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 6")
        sqlite.execSQL("DELETE FROM databases WHERE id = 6")
        MIGRATION_5_6.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db6Records.size, dao.observeRecords(6L).first().size)
        assertEquals(db5Records.size, dao.observeRecords(5L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseSeven() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 6 has no database 7: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 7")
        sqlite.execSQL("DELETE FROM databases WHERE id = 7")
        MIGRATION_6_7.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db7Records.size, dao.observeRecords(7L).first().size)
        assertEquals(db6Records.size, dao.observeRecords(6L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseEight() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 7 has no database 8: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 8")
        sqlite.execSQL("DELETE FROM databases WHERE id = 8")
        MIGRATION_7_8.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db8Records.size, dao.observeRecords(8L).first().size)
        assertEquals(db7Records.size, dao.observeRecords(7L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseNine() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 8 has no database 9: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 9")
        sqlite.execSQL("DELETE FROM databases WHERE id = 9")
        MIGRATION_8_9.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db9Records.size, dao.observeRecords(9L).first().size)
        assertEquals(db8Records.size, dao.observeRecords(8L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 9 has no database 10: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 10")
        sqlite.execSQL("DELETE FROM databases WHERE id = 10")
        MIGRATION_9_10.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db10Records.size, dao.observeRecords(10L).first().size)
        assertEquals(db9Records.size, dao.observeRecords(9L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseEleven() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 10 has no database 11: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 11")
        sqlite.execSQL("DELETE FROM databases WHERE id = 11")
        MIGRATION_10_11.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db11Records.size, dao.observeRecords(11L).first().size)
        assertEquals(db10Records.size, dao.observeRecords(10L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwelve() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 11 has no database 12: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 12")
        sqlite.execSQL("DELETE FROM databases WHERE id = 12")
        MIGRATION_11_12.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db12Records.size, dao.observeRecords(12L).first().size)
        assertEquals(db11Records.size, dao.observeRecords(11L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 12 has no database 13: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 13")
        sqlite.execSQL("DELETE FROM databases WHERE id = 13")
        MIGRATION_12_13.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db13Records.size, dao.observeRecords(13L).first().size)
        assertEquals(db12Records.size, dao.observeRecords(12L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseFourteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 13 has no database 14: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 14")
        sqlite.execSQL("DELETE FROM databases WHERE id = 14")
        MIGRATION_13_14.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db14Records.size, dao.observeRecords(14L).first().size)
        assertEquals(db13Records.size, dao.observeRecords(13L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseFifteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 14 has no database 15: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 15")
        sqlite.execSQL("DELETE FROM databases WHERE id = 15")
        MIGRATION_14_15.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db15Records.size, dao.observeRecords(15L).first().size)
        assertEquals(db14Records.size, dao.observeRecords(14L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseSixteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 15 has no database 16: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 16")
        sqlite.execSQL("DELETE FROM databases WHERE id = 16")
        MIGRATION_15_16.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db16Records.size, dao.observeRecords(16L).first().size)
        assertEquals(db15Records.size, dao.observeRecords(15L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseSeventeen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 16 has no database 17: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 17")
        sqlite.execSQL("DELETE FROM databases WHERE id = 17")
        MIGRATION_16_17.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db17Records.size, dao.observeRecords(17L).first().size)
        assertEquals(db16Records.size, dao.observeRecords(16L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseEighteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 17 has no database 18: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 18")
        sqlite.execSQL("DELETE FROM databases WHERE id = 18")
        MIGRATION_17_18.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db18Records.size, dao.observeRecords(18L).first().size)
        assertEquals(db17Records.size, dao.observeRecords(17L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseNineteen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 18 has no database 19: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 19")
        sqlite.execSQL("DELETE FROM databases WHERE id = 19")
        MIGRATION_18_19.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db19Records.size, dao.observeRecords(19L).first().size)
        assertEquals(db18Records.size, dao.observeRecords(18L).first().size)
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
