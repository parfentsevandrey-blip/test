package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1200 to 1210: the days between the sack of Constantinople in 1204 and its recovery in 1261, counted by a Julian day number. */
val db84Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Days from the sack of Constantinople to its recovery in 1261",
        originalTitle = "Days between two dates",
        author = "The Fourth Crusade and Michael VIII Palaiologos",
        year = 1204,
        summary = "The Fourth Crusade sacked Constantinople in April 1204 and Michael VIII retook it on 25 July 1261, and a short program counts the days between the two dates.",
        details = "Both dates are given in the Julian calendar, which was in use at the time, and the program converts them with a Julian day number formula so that the count does not depend on the calendar of the computer. Accounts give 12 or 13 April for the breach of the walls, so the count uses 13 April, the date most often given.",
        instructions = """
            1. Save the example as constantinople_days.py.
            2. Run it: python3 constantinople_days.py
        """,
        example = """
            def julian_day_number(year, month, day):
                # January and February count as months 13 and 14 of the year before
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            sack = julian_day_number(1204, 4, 13)
            recovery = julian_day_number(1261, 7, 25)
            print("days from the sack to the recovery:", recovery - sack)
        """,
    ),

)
