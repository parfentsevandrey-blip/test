package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1140 to 1150: the reign lengths of six rulers who held power in the decade, summed and ranked. */
val db90Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reign lengths of six rulers who held power in the 1140s, summed and ranked",
        originalTitle = "Reign lengths",
        author = "No single author",
        year = 1145,
        summary = "A short program lists six rulers who held power in the 1140s, works out the length of each reign from its start and end years, and ranks them from shortest to longest.",
        details = "The rulers are John II Komnenos, Manuel I Komnenos, Roger II of Sicily, Conrad III, Gaozong of Song and Louis VII, and each reign is counted in whole years from the usual start and end dates. The start and end years are the conventional ones, and some reigns began in a year that accounts give differently.",
        instructions = """
            1. Save the example as reigns.py.
            2. Run it: python3 reigns.py
        """,
        example = """
            RULERS = [
                ("John II Komnenos", 1118, 1143),
                ("Manuel I Komnenos", 1143, 1180),
                ("Roger II of Sicily", 1130, 1154),
                ("Conrad III", 1138, 1152),
                ("Gaozong of Song", 1127, 1162),
                ("Louis VII", 1137, 1180),
            ]
            lengths = sorted((end - start, name) for name, start, end in RULERS)
            print("rulers:", len(RULERS))
            print("total years:", sum(length for length, _ in lengths))
            print("longest:", lengths[-1][1], lengths[-1][0], "years")
            print("shortest:", lengths[0][1], lengths[0][0], "years")
        """,
    ),

)
