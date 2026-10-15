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
    DatabaseEntity(
        id = 7,
        name = "Database 7: 1960s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1960 to 1970, including rare and little-known items.",
        colorHex = "#F06292",
    ),
    DatabaseEntity(
        id = 8,
        name = "Database 8: 1970s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1970 to 1980, including rare and little-known items.",
        colorHex = "#A1887F",
    ),
    DatabaseEntity(
        id = 9,
        name = "Database 9: 2026 to 2036 forecast",
        description = "Forecast of languages, systems, devices, tools and practices of 2026 to 2036, with the long shots marked rare.",
        colorHex = "#90A4AE",
    ),
    DatabaseEntity(
        id = 10,
        name = "Database 10: 1940s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1940 to 1950, including rare and little-known items.",
        colorHex = "#4DB6AC",
    ),
    DatabaseEntity(
        id = 11,
        name = "Database 11: 1930s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1930 to 1940, including rare and little-known items.",
        colorHex = "#E57373",
    ),
    DatabaseEntity(
        id = 12,
        name = "Database 12: 1920s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1920 to 1930, including rare and little-known items.",
        colorHex = "#DCE775",
    ),
    DatabaseEntity(
        id = 13,
        name = "Database 13: 1910s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1910 to 1920, including rare and little-known items.",
        colorHex = "#9FA8DA",
    ),
    DatabaseEntity(
        id = 14,
        name = "Database 14: 1900s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1900 to 1910, including rare and little-known items.",
        colorHex = "#FFAB91",
    ),
    DatabaseEntity(
        id = 15,
        name = "Database 15: 1890s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1890 to 1900, including rare and little-known items.",
        colorHex = "#90CAF9",
    ),
    DatabaseEntity(
        id = 16,
        name = "Database 16: 1880s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1880 to 1890, including rare and little-known items.",
        colorHex = "#CE93D8",
    ),
    DatabaseEntity(
        id = 17,
        name = "Database 17: 1870s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1870 to 1880, including rare and little-known items.",
        colorHex = "#FFCC80",
    ),
    DatabaseEntity(
        id = 18,
        name = "Database 18: 1860s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1860 to 1870, including rare and little-known items.",
        colorHex = "#E6EE9C",
    ),
    DatabaseEntity(
        id = 19,
        name = "Database 19: 1850s coding catalogue",
        description = "Languages, systems, devices, tools and practices of 1850 to 1860, including rare and little-known items.",
        colorHex = "#EF9A9A",
    ),
)

/** Records of every seeded database. They are inserted together with the databases. */
val seedRecords: List<RecordEntity> =
    db1Records + db2Records + db3Records + db4Records + db5Records + db6Records + db7Records + db8Records + db9Records +
        db10Records + db11Records + db12Records + db13Records + db14Records + db15Records + db16Records + db17Records +
        db18Records + db19Records
