package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 460 to 470: the years of Hydatius's chronicle from 379 to 468, counted in code and split into decades. */
val db158Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.EXAMPLE,
        section = "Decades",
        title = "The 90 years of Hydatius's chronicle, 379 to 468, counted in decades",
        originalTitle = "Chronicon",
        author = "Hydatius of Aquae Flaviae",
        year = 468,
        summary = "A short program counts the years of Hydatius's chronicle from 379 to 468 and splits them into nine blocks of ten years.",
        details = "The example counts the years from 379 to 468 inclusive, the span that some accounts give for the chronicle. If the chronicle is taken to run to 469, the count rises to 91 years, so the end year that is chosen changes the total.",
        instructions = """
            1. Save the example as decades.py.
            2. Run it: python3 decades.py
        """,
        example = """
            FIRST = 379   # Hydatius resumes Jerome's chronicle in 379
            LAST = 468    # the last year, as some accounts give it
            years = LAST - FIRST + 1
            print("years in the chronicle:", years)
            print("full decades:", years // 10)
            for start in range(FIRST, LAST + 1, 10):
                print("decade from", start, "to", start + 9)
        """,
        rare = true,
    ),

)
