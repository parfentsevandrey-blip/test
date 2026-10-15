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
    version = 5,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
