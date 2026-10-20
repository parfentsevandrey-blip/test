package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 510 to 520: the thirty years of Clovis's reign, from 481 to his death in 511, counted in code. */
val db153Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.EXAMPLE,
        section = "Frankish",
        title = "The 30 years of Clovis's reign, from 481 to 511, counted in code",
        originalTitle = "Chlodovechus",
        author = "Chroniclers of the Franks",
        year = 511,
        summary = "Clovis ruled the Franks from the usual start of 481 to his death in 511, a reign of 30 years that a one-line program counts.",
        details = "The usual dates are 481 for the start and 511 for the death, and the count follows them, so the answer is the span of the reign and not a count of years of active rule. The program only subtracts the two dates, which a reader can check against any list of Frankish kings.",
        instructions = """
            1. Save the example as clovis_reign.py.
            2. Run it: python3 clovis_reign.py
        """,
        example = """
            START = 481  # the usual year Clovis became king of the Franks
            END = 511    # the year of his death
            print("years of Clovis's reign:", END - START)
        """,
    ),

)
