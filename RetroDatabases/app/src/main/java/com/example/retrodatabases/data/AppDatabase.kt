package com.example.retrodatabases.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.retrodatabases.data.seed.db1Records
import com.example.retrodatabases.data.seed.seedDatabases

@Database(
    entities = [DatabaseEntity::class, RecordEntity::class],
    version = 1,
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
                .build()
    }
}

/**
 * Заполняет базы при первом создании файла БД.
 * Вставка идёт SQL-запросами прямо в onCreate, поэтому не нужно ждать готовности экземпляра Room.
 * Базы из seed-списков добавляются только при создании файла: в уже установленных копиях
 * новая база появится после увеличения версии БД и миграции.
 */
private class SeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        seedDatabases.forEach { database ->
            db.execSQL(
                "INSERT INTO databases (id, name, description, colorHex) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(database.id, database.name, database.description, database.colorHex),
            )
        }

        db1Records.forEach { record ->
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
    }
}
