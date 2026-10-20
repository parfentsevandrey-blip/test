package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 400 to 410: the days from the battle of Pollentia to the crossing of the Rhine. */
val db164Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Pollentia to the Rhine crossing",
        originalTitle = "Days between two fixed dates",
        author = "The armies of the two events",
        year = 406,
        summary = "The battle of Pollentia was fought on 6 April 402 and the Rhine was crossed on 31 December 406, and a short program counts the days between the two dates.",
        details = "Both dates are the usual ones, and Pollentia is said to have been fought on Easter Sunday of 402. The example reads the dates in the Python calendar, which gives the same span as the Julian calendar here because the two calendars differ by the same number of days at both dates, and the count crosses one leap day, in February 404.",
        instructions = """
            1. Save the example as pollentia_rhine.py.
            2. Run it: python3 pollentia_rhine.py
        """,
        example = """
            from datetime import date

            pollentia = date(402, 4, 6)    # the battle of Pollentia, as commonly given
            rhine = date(406, 12, 31)      # the Rhine crossing, as commonly given
            print("days from Pollentia to the Rhine crossing:", (rhine - pollentia).days)
        """,
    ),

)
