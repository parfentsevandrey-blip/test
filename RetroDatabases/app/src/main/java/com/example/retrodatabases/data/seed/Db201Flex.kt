package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 30 to 40: one line of arithmetic between Velleius's history of 30 and the start of the Aqua Claudia in 38. */
val db201Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Velleius's history of 30 to the start of the Aqua Claudia in 38",
        originalTitle = "Years between two dates",
        author = "No single author",
        year = 38,
        summary = "Velleius's history is dated to 30 and the Aqua Claudia was begun in 38, and a short program counts the years between them.",
        details = "Both years are the usual dates, and the example takes the difference as a plain count of years with no calendar detail. Some older texts give 36 as the start of the Aqua Claudia, and the count would change with that reading.",
        instructions = """
            1. Save the example as years_between.py.
            2. Run it: python3 years_between.py
        """,
        example = """
            HISTORY_YEAR = 30         # Velleius's history, usual year
            AQUA_CLAUDIA_YEAR = 38    # the Aqua Claudia begun, usual year
            print("years from 30 to 38:", AQUA_CLAUDIA_YEAR - HISTORY_YEAR)
        """,
    ),

)
