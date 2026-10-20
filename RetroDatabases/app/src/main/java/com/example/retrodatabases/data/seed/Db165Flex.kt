package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 397: the days from the battle of the Frigidus to the death of Martin of Tours, counted with two fixed dates. */
val db165Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of the Frigidus to the death of Martin of Tours",
        originalTitle = "Days from the battle of the Frigidus to the death of Martin of Tours",
        author = "Theodosius I and Martin of Tours",
        year = 397,
        summary = "The battle of the Frigidus was fought on 5 September 394 and Martin of Tours died on 8 November 397, and a short program counts the days between the two dates.",
        details = "Both dates are the usual ones, and the example uses 5 September for the battle, the day the fighting began, since some accounts give 6 September. The span includes the leap day of February 396 and contains no century year, so the count is the same in the Julian and the Gregorian calendars.",
        instructions = """
            1. Save the example as frigidus_martin.py.
            2. Run it: python3 frigidus_martin.py
        """,
        example = """
            from datetime import date

            frigidus = date(394, 9, 5)     # the battle of the Frigidus, as commonly given
            martin = date(397, 11, 8)      # the death of Martin of Tours, as commonly given
            print("days from the Frigidus to Martin's death:", (martin - frigidus).days)
        """,
    ),

)
