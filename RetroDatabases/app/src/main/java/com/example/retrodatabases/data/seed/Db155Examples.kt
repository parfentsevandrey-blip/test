package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 490 to 500: the four parts of the Aryabhatiya and their verses, added up by a short program. */
val db155Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.EXAMPLE,
        section = "Verses",
        title = "The verses of the Aryabhatiya counted by its four parts",
        originalTitle = "Aryabhatiya",
        author = "Aryabhata",
        year = 499,
        summary = "The Aryabhatiya is set out in four parts of 13, 33, 25 and 50 verses, and a short program adds them to 121 and separates out the 108 verses of the three later parts.",
        details = "The first part, the Gitika, has 13 verses, and the three later parts hold 108 verses between them, so the total is 121. Most accounts give this count, which follows the usual division into four parts.",
        instructions = """
            1. Save the example as aryabhatiya_verses.py.
            2. Run it: python3 aryabhatiya_verses.py
        """,
        example = """
            parts = [("Gitika", 13), ("Ganita", 33), ("Kalakriya", 25), ("Gola", 50)]
            for name, verses in parts:
                print(name, verses)
            total = sum(verses for name, verses in parts)
            print("verses in the four parts:", total)
            print("verses in the three later parts:", total - 13)
        """,
        rare = true,
    ),

)
