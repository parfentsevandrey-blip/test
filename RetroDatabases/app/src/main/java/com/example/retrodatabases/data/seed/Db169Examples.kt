package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 350: the parts of Cyril of Jerusalem's Catechetical Lectures, counted in code. */
val db169Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.EXAMPLE,
        section = "Lectures",
        title = "Cyril of Jerusalem's Catechetical Lectures, the 24 pieces of the course counted in code",
        originalTitle = "Katecheseis",
        author = "Cyril of Jerusalem",
        year = 350,
        summary = "Cyril of Jerusalem's course for candidates for baptism has an introduction, eighteen catecheses and five mystagogical lectures, and a short program adds the parts to a total of 24.",
        details = "Accounts date the Jerusalem course to Lent 347 or 348, and some to about 350, so this record uses 350, the rounded date, to keep the year in the decade. The count follows the usual division of the text, and the example prints each part and the total, so the sum can be checked by hand.",
        instructions = """
            1. Save the example as cyril_count.py.
            2. Run it: python3 cyril_count.py
        """,
        example = """
            PARTS = [("procatechesis", 1), ("catecheses", 18), ("mystagogical lectures", 5)]
            for name, count in PARTS:
                print(name, count)
            print("total lectures:", sum(count for name, count in PARTS))
        """,
        rare = true,
    ),

)
