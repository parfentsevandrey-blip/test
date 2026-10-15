package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.DatabaseEntity
import com.example.retrodatabases.data.RecordEntity

/**
 * Catalogue of databases that are created on the first launch.
 * To add a database: append an entry here, create its record files (databaseId = the new id)
 * and add their records to [seedRecords]. Installs made before that also need a migration in AppDatabase.
 */
val seedDatabases: List<DatabaseEntity> = listOf(
    DatabaseEntity(
        id = 1,
        name = "Database 1: 80s coding catalogue",
        description = "Languages, systems, devices, tools and practices of the 1980s, including rare and little-known items.",
        colorHex = "#4DD0E1",
    ),
    DatabaseEntity(
        id = 2,
        name = "Database 2: 90s coding catalogue",
        description = "Languages, systems, devices, tools and practices of the 1990s, including rare and little-known items.",
        colorHex = "#BA68C8",
    ),
    DatabaseEntity(
        id = 3,
        name = "Database 3: 2000s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 2000 to 2010, including rare and little-known items.",
        colorHex = "#FF8A65",
    ),
    DatabaseEntity(
        id = 4,
        name = "Database 4: 2010s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 2010 to 2020, including rare and little-known items.",
        colorHex = "#AED581",
    ),
    DatabaseEntity(
        id = 5,
        name = "Database 5: 2020s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 2020 to 2026, including rare and little-known items.",
        colorHex = "#7986CB",
    ),
    DatabaseEntity(
        id = 6,
        name = "Database 6: 1950s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1950 to 1960, including rare and little-known items.",
        colorHex = "#FFD54F",
    ),
)

/** Records of every seeded database. They are inserted together with the databases. */
val seedRecords: List<RecordEntity> = db1Records + db2Records + db3Records + db4Records + db5Records + db6Records
