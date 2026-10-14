package com.example.retrodatabases.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One database inside the app, for example "Database 1". */
@Entity(tableName = "databases")
data class DatabaseEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String,
    /** Accent colour of the database in #RRGGBB format. */
    val colorHex: String,
)

/** Catalogue record: a language, system, device, tool, practice, guide, project and so on. */
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
    /** Kind of record: the top-level section of the catalogue. */
    val kind: Kind,
    /** Subsection inside the kind, for example "Imperative and system" or "Processors". */
    val section: String,
    val title: String,
    val originalTitle: String,
    val author: String,
    val year: Int,
    val summary: String,
    /** Details: for projects and guides, how everything is built and works. */
    val details: String,
    /** Step-by-step instructions: how to run it and how to use it. Null if not needed. */
    val instructions: String?,
    /** Code fragment (or several files with "### name" headings), or null. */
    val example: String?,
    /** Rare or little-known record; has its own filter. */
    val rare: Boolean = false,
    val favorite: Boolean = false,
)

/** One row of the database list screen: the database itself and its number of records. */
data class DatabaseSummary(
    val id: Long,
    val name: String,
    val description: String,
    val colorHex: String,
    val recordCount: Int,
)
