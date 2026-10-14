package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.DatabaseEntity

/**
 * Catalogue of databases that are created on the first launch.
 * To add a database: append an entry here and create a file next to it with its records (databaseId = new id).
 */
val seedDatabases: List<DatabaseEntity> = listOf(
    DatabaseEntity(
        id = 1,
        name = "Database 1: 80s coding catalogue",
        description = "Languages, systems, devices, tools and practices of the 1980s, including rare and little-known items.",
        colorHex = "#4DD0E1",
    ),
)
