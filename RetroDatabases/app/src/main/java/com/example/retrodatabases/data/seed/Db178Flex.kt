package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 263 to 268: the years between Liu Hui's commentary and the Jin code. */
val db178Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Liu Hui's commentary of 263 to the Jin code of 268",
        originalTitle = "Years between two dates",
        author = "Liu Hui and the Jin court",
        year = 263,
        summary = "Liu Hui's commentary of 263 and the Jin code of 268 are five years apart, and a short program counts the years between them.",
        details = "Both years are the usual dates of the two works, and the count is the difference between the two years rather than a span of exact days. The example prints that difference, so the result can be checked by hand.",
        instructions = """
            1. Save the example as years_gap.py.
            2. Run it: python3 years_gap.py
        """,
        example = """
            COMMENTARY = 263   # Liu Hui's commentary, as commonly dated
            JIN_CODE = 268     # the Jin code, as commonly dated
            print("years from the commentary to the Jin code:", JIN_CODE - COMMENTARY)
        """,
    ),

)
