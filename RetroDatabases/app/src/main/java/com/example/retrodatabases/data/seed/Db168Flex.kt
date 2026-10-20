package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 360 to 370: the days from the death of Julian in 363 to the elevation of Valens in 364. */
val db168Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Julian to the elevation of Valens",
        originalTitle = "Days between two dates",
        author = "Julian and Valens",
        year = 364,
        summary = "Julian died on 26 June 363 and Valens was elevated on 28 March 364, and a short program counts the 276 days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses Python's date arithmetic, which counts the leap day of February 364. The count is exact for the dates as written and depends on no battle or ceremony.",
        instructions = """
            1. Save the example as julian_valens.py.
            2. Run it: python3 julian_valens.py
        """,
        example = """
            from datetime import date

            julian_death = date(363, 6, 26)   # death of Julian, 26 June 363, as commonly given
            valens = date(364, 3, 28)         # Valens elevated as co-emperor, 28 March 364
            print("days from Julian's death to Valens's elevation:", (valens - julian_death).days)
        """,
    ),

)
