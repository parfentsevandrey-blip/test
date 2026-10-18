package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 1054: one line of date arithmetic from the Great Schism to the battle of Hastings. */
val db99Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Great Schism to the battle of Hastings",
        originalTitle = "Days between two fixed dates",
        author = "Cardinal Humbert and William of Normandy",
        year = 1054,
        summary = "The bull of 16 July 1054 and the battle of Hastings on 14 October 1066 are 4473 days apart, and a short program counts the days between the two fixed dates.",
        details = "Both dates are as commonly stated in the Julian reckoning of the time. The Python calendar is Gregorian, but both dates fall under the same offset because 1100 lies after them, and the span holds three leap days, in 1056, 1060 and 1064.",
        instructions = """
            1. Save the example as schism_hastings.py.
            2. Run it: python3 schism_hastings.py
        """,
        example = """
            from datetime import date

            schism = date(1054, 7, 16)     # the bull on the altar of Hagia Sophia, as commonly given
            hastings = date(1066, 10, 14)  # the battle of Hastings, as commonly given
            print("days from the Great Schism to Hastings:", (hastings - schism).days)
        """,
    ),

)
