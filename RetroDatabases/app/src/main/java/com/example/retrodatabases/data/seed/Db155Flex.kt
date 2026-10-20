package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 490 to 500: the days from the death of Odoacer to the end of Kali year 3600, counted with a Julian day number. */
val db155Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Odoacer's death to the end of Kali year 3600",
        originalTitle = "Days between two fixed dates",
        author = "Theoderic the Great and Aryabhata",
        year = 493,
        summary = "Odoacer's death is usually dated to 15 March 493 and the Kali year 3600 ended on 21 March 499, and a short program counts the days between the two dates with a Julian day number.",
        details = "Both dates are given as commonly stated in the Julian calendar, and the example uses a Julian day number, so the count does not depend on the Gregorian rule. The span includes the leap day of 29 February 496.",
        instructions = """
            1. Save the example as odoacer_kali.py.
            2. Run it: python3 odoacer_kali.py
        """,
        example = """
            def julian_day(year, month, day):
                # Julian calendar day number, for dates after the start of the calendar
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            start = julian_day(493, 3, 15)   # death of Odoacer, as commonly given
            end = julian_day(499, 3, 21)     # end of Kali year 3600, as commonly given
            print("days from Odoacer's death to the end of Kali year 3600:", end - start)
        """,
        rare = true,
    ),

)
