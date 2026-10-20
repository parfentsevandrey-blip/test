package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 330 to 340: the days between the dedication of Constantinople and the death of Constantine, in one line of arithmetic. */
val db171Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the dedication of Constantinople to the death of Constantine",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 337,
        summary = "Constantinople was dedicated on 11 May 330 and Constantine died on 22 May 337, and a short program counts the days between the two dates.",
        details = "Both dates are the ones usually given, and the example uses Python's calendar with both dates in the same reckoning, so the count is exact for the dates as written. The span has two leap days, in February 332 and February 336, and no century year, so the calendar used here gives the same count as the Julian one.",
        instructions = """
            1. Save the example as constantine_days.py.
            2. Run it: python3 constantine_days.py
        """,
        example = """
            from datetime import date

            dedication = date(330, 5, 11)   # the dedication of Constantinople, as usually given
            death = date(337, 5, 22)        # the death of Constantine, as usually given
            print("days from the dedication of Constantinople to the death of Constantine:", (death - dedication).days)
        """,
    ),

)
