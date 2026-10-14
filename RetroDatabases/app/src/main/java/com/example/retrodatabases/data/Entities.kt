package com.example.retrodatabases.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Одна база данных внутри приложения, например «База 1». */
@Entity(tableName = "databases")
data class DatabaseEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String,
    /** Акцентный цвет базы в формате #RRGGBB. */
    val colorHex: String,
)

/** Запись каталога: язык, система, устройство, инструмент, практика, пояснение, проект и т. д. */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = DatabaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["databaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("databaseId")],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val databaseId: Long,
    /** Вид записи: раздел верхнего уровня каталога. */
    val kind: Kind,
    /** Подраздел внутри вида, например «Императивные и системные» или «Процессоры». */
    val section: String,
    val title: String,
    val originalTitle: String,
    val author: String,
    val year: Int,
    val summary: String,
    /** Подробности: для проектов и пояснений — как всё устроено и работает. */
    val details: String,
    /** Пошаговая инструкция: как запустить, как пользоваться. Null, если не нужна. */
    val instructions: String?,
    /** Фрагмент кода (или несколько файлов с заголовками «### имя») или null. */
    val example: String?,
    /** Редкая или малоизвестная запись; есть отдельный фильтр. */
    val rare: Boolean = false,
    val favorite: Boolean = false,
)

/** Строка экрана списка баз: сама база и число её записей. */
data class DatabaseSummary(
    val id: Long,
    val name: String,
    val description: String,
    val colorHex: String,
    val recordCount: Int,
)
