package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 810 to 820: the years of Theophanes's chronicle from 284 to 813, counted by century in code. */
val db123Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.EXAMPLE,
        section = "Greek",
        title = "The years of Theophanes's chronicle, from 284 to 813, counted by century",
        originalTitle = "Chronographia",
        author = "Theophanes the Confessor",
        year = 813,
        summary = "Theophanes's chronicle covers the years 284 to 813, and a short program counts how many of those years fall in each century, giving 530 in all.",
        details = "The first year is the accession of Diocletian in 284 and the last is 813, and the example cuts the years at the century boundaries, so the third century holds only its years from 284 onward. The counts are easy to check by hand, since the fourth to eighth centuries hold a hundred years each and the ninth holds the 13 years from 801 to 813.",
        instructions = """
            1. Save the example as chronicle_centuries.py.
            2. Run it: python3 chronicle_centuries.py
        """,
        example = """
            FIRST, LAST = 284, 813
            counts = {}
            for year in range(FIRST, LAST + 1):
                century = (year - 1) // 100 + 1
                counts[century] = counts.get(century, 0) + 1
            for century in sorted(counts):
                print("century", century, ":", counts[century], "years")
            print("total years:", sum(counts.values()))
        """,
    ),

)
