package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 70 to 80: the days from Vespasian's death to the eruption of Vesuvius, both dates in 79. */
val db197Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Vespasian to the eruption of Vesuvius",
        originalTitle = "Days between two fixed dates",
        author = "Vespasian and Pliny the Younger",
        year = 79,
        summary = "Vespasian died on 23 June 79 and Pliny the Younger dates the eruption of Vesuvius to 24 August 79, and a short program counts the 62 days between the two dates.",
        details = "Sources give 23 June for Vespasian's death, although some give 24 June, and the example uses 23 June; with 24 June the count would be 61 days. Pliny the Younger gives 24 August for the eruption, and both dates fall in 79, which is not a leap year in either the Julian or the Gregorian reckoning, so the count is the same in both.",
        rare = true,
        instructions = """
            1. Save the example as vespasian_vesuvius.py.
            2. Run it: python3 vespasian_vesuvius.py
        """,
        example = """
            from datetime import date

            vespasian = date(79, 6, 23)   # Vespasian's death, as commonly given
            vesuvius = date(79, 8, 24)    # the eruption, as Pliny the Younger gives it
            print("days from Vespasian's death to the eruption:", (vesuvius - vespasian).days)
        """,
    ),

)
