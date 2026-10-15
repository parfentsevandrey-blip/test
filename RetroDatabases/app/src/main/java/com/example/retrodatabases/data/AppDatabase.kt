package com.example.retrodatabases.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords

@Database(
    entities = [DatabaseEntity::class, RecordEntity::class],
    version = 19,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        private const val FILE_NAME = "retro_databases.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        private fun buildDatabase(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, FILE_NAME)
                .addCallback(SeedCallback())
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                )
                .build()
    }
}

/**
 * Version 2 added database 2. Copies created at version 1 get it from this migration,
 * so their favourites and the records of database 1 stay as they are.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(2L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 3 added database 3. Copies at version 2 get it from this migration;
 * copies at version 1 run it right after MIGRATION_1_2.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(3L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 4 added database 4. Copies at version 3 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(4L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 5 added database 5. Copies at version 4 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(5L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 6 added database 6. Copies at version 5 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(6L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 7 added database 7. Copies at version 6 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(7L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 8 added database 8. Copies at version 7 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(8L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 9 added database 9. Copies at version 8 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(9L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 10 added database 10. Copies at version 9 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(10L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 11 added database 11. Copies at version 10 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(11L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 12 added database 12. Copies at version 11 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(12L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 13 added database 13. Copies at version 12 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(13L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 14 added database 14. Copies at version 13 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(14L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 15 added database 15. Copies at version 14 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(15L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 16 added database 16. Copies at version 15 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(16L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 17 added database 17. Copies at version 16 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(17L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 18 added database 18. Copies at version 17 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(18L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Version 19 added database 19. Copies at version 18 get it from this migration;
 * older copies run it after the earlier migrations.
 */
internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val added = setOf(19L)
        seedDatabases.filter { it.id in added }.forEach { insertDatabase(db, it) }
        seedRecords.filter { it.databaseId in added }.forEach { insertRecord(db, it) }
    }
}

/**
 * Fills the databases when the database file is first created.
 * Inserts run as SQL queries directly in onCreate, so there is no need to wait until the Room instance is ready.
 */
private class SeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        seedDatabases.forEach { insertDatabase(db, it) }
        seedRecords.forEach { insertRecord(db, it) }
    }
}

private fun insertDatabase(db: SupportSQLiteDatabase, database: DatabaseEntity) {
    db.execSQL(
        "INSERT INTO databases (id, name, description, colorHex) VALUES (?, ?, ?, ?)",
        arrayOf<Any?>(database.id, database.name, database.description, database.colorHex),
    )
}

private fun insertRecord(db: SupportSQLiteDatabase, record: RecordEntity) {
    db.execSQL(
        """
        INSERT INTO records (databaseId, kind, section, title, originalTitle, author, year,
                             summary, details, instructions, example, rare, favorite)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """.trimIndent(),
        arrayOf<Any?>(
            record.databaseId,
            record.kind.name,
            record.section,
            record.title,
            record.originalTitle,
            record.author,
            record.year.toLong(),
            record.summary,
            record.details,
            record.instructions,
            record.example,
            if (record.rare) 1L else 0L,
        ),
    )
}
