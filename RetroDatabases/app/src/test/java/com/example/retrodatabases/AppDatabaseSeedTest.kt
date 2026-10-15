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
import com.example.retrodatabases.data.MIGRATION_19_20
import com.example.retrodatabases.data.MIGRATION_20_21
import com.example.retrodatabases.data.MIGRATION_21_22
import com.example.retrodatabases.data.MIGRATION_22_23
import com.example.retrodatabases.data.MIGRATION_23_24
import com.example.retrodatabases.data.MIGRATION_24_25
import com.example.retrodatabases.data.MIGRATION_25_26
import com.example.retrodatabases.data.MIGRATION_26_27
import com.example.retrodatabases.data.MIGRATION_27_28
import com.example.retrodatabases.data.MIGRATION_28_29
import com.example.retrodatabases.data.MIGRATION_29_30
import com.example.retrodatabases.data.MIGRATION_30_31
import com.example.retrodatabases.data.MIGRATION_31_32
import com.example.retrodatabases.data.MIGRATION_32_33
import com.example.retrodatabases.data.MIGRATION_33_34
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
        assertEquals(db20Records.size, databases.single { it.id == 20L }.recordCount)
        assertEquals(db21Records.size, databases.single { it.id == 21L }.recordCount)
        assertEquals(db22Records.size, databases.single { it.id == 22L }.recordCount)
        assertEquals(db23Records.size, databases.single { it.id == 23L }.recordCount)
        assertEquals(db24Records.size, databases.single { it.id == 24L }.recordCount)
        assertEquals(db25Records.size, databases.single { it.id == 25L }.recordCount)
        assertEquals(db26Records.size, databases.single { it.id == 26L }.recordCount)
        assertEquals(db27Records.size, databases.single { it.id == 27L }.recordCount)
        assertEquals(db28Records.size, databases.single { it.id == 28L }.recordCount)
        assertEquals(db29Records.size, databases.single { it.id == 29L }.recordCount)
        assertEquals(db30Records.size, databases.single { it.id == 30L }.recordCount)
        assertEquals(db31Records.size, databases.single { it.id == 31L }.recordCount)
        assertEquals(db32Records.size, databases.single { it.id == 32L }.recordCount)
        assertEquals(db33Records.size, databases.single { it.id == 33L }.recordCount)
        assertEquals(db34Records.size, databases.single { it.id == 34L }.recordCount)

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
    fun migrationRestoresDatabaseTwenty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 19 has no database 20: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 20")
        sqlite.execSQL("DELETE FROM databases WHERE id = 20")
        MIGRATION_19_20.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db20Records.size, dao.observeRecords(20L).first().size)
        assertEquals(db19Records.size, dao.observeRecords(19L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyOne() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 20 has no database 21: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 21")
        sqlite.execSQL("DELETE FROM databases WHERE id = 21")
        MIGRATION_20_21.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db21Records.size, dao.observeRecords(21L).first().size)
        assertEquals(db20Records.size, dao.observeRecords(20L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyTwo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 21 has no database 22: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 22")
        sqlite.execSQL("DELETE FROM databases WHERE id = 22")
        MIGRATION_21_22.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db22Records.size, dao.observeRecords(22L).first().size)
        assertEquals(db21Records.size, dao.observeRecords(21L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyThree() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 22 has no database 23: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 23")
        sqlite.execSQL("DELETE FROM databases WHERE id = 23")
        MIGRATION_22_23.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db23Records.size, dao.observeRecords(23L).first().size)
        assertEquals(db22Records.size, dao.observeRecords(22L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyFour() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 23 has no database 24: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 24")
        sqlite.execSQL("DELETE FROM databases WHERE id = 24")
        MIGRATION_23_24.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db24Records.size, dao.observeRecords(24L).first().size)
        assertEquals(db23Records.size, dao.observeRecords(23L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyFive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 24 has no database 25: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 25")
        sqlite.execSQL("DELETE FROM databases WHERE id = 25")
        MIGRATION_24_25.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db25Records.size, dao.observeRecords(25L).first().size)
        assertEquals(db24Records.size, dao.observeRecords(24L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentySix() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 25 has no database 26: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 26")
        sqlite.execSQL("DELETE FROM databases WHERE id = 26")
        MIGRATION_25_26.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db26Records.size, dao.observeRecords(26L).first().size)
        assertEquals(db25Records.size, dao.observeRecords(25L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentySeven() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 26 has no database 27: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 27")
        sqlite.execSQL("DELETE FROM databases WHERE id = 27")
        MIGRATION_26_27.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db27Records.size, dao.observeRecords(27L).first().size)
        assertEquals(db26Records.size, dao.observeRecords(26L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyEight() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 27 has no database 28: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 28")
        sqlite.execSQL("DELETE FROM databases WHERE id = 28")
        MIGRATION_27_28.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db28Records.size, dao.observeRecords(28L).first().size)
        assertEquals(db27Records.size, dao.observeRecords(27L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseTwentyNine() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 28 has no database 29: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 29")
        sqlite.execSQL("DELETE FROM databases WHERE id = 29")
        MIGRATION_28_29.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db29Records.size, dao.observeRecords(29L).first().size)
        assertEquals(db28Records.size, dao.observeRecords(28L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 29 has no database 30: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 30")
        sqlite.execSQL("DELETE FROM databases WHERE id = 30")
        MIGRATION_29_30.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db30Records.size, dao.observeRecords(30L).first().size)
        assertEquals(db29Records.size, dao.observeRecords(29L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirtyOne() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 30 has no database 31: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 31")
        sqlite.execSQL("DELETE FROM databases WHERE id = 31")
        MIGRATION_30_31.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db31Records.size, dao.observeRecords(31L).first().size)
        assertEquals(db30Records.size, dao.observeRecords(30L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirtyTwo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 31 has no database 32: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 32")
        sqlite.execSQL("DELETE FROM databases WHERE id = 32")
        MIGRATION_31_32.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db32Records.size, dao.observeRecords(32L).first().size)
        assertEquals(db31Records.size, dao.observeRecords(31L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirtyThree() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 32 has no database 33: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 33")
        sqlite.execSQL("DELETE FROM databases WHERE id = 33")
        MIGRATION_32_33.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db33Records.size, dao.observeRecords(33L).first().size)
        assertEquals(db32Records.size, dao.observeRecords(32L).first().size)
    }

    @Test
    fun migrationRestoresDatabaseThirtyFour() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.get(context)
        val dao = database.libraryDao()
        val sqlite = database.openHelper.writableDatabase

        // A file made at version 33 has no database 34: remove it, then run the migration.
        sqlite.execSQL("DELETE FROM records WHERE databaseId = 34")
        sqlite.execSQL("DELETE FROM databases WHERE id = 34")
        MIGRATION_33_34.migrate(sqlite)

        assertEquals(seedDatabases.size, dao.observeDatabases().first().size)
        assertEquals(db34Records.size, dao.observeRecords(34L).first().size)
        assertEquals(db33Records.size, dao.observeRecords(33L).first().size)
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
