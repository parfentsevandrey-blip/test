package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1400 to 1410: days between two pairs of fixed dates, and the eleven years of the decade added up. */
val db64Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Chaucer's death to the death of Timur",
        originalTitle = "Days between two fixed dates",
        author = "Geoffrey Chaucer and Timur",
        year = 1400,
        summary = "Chaucer died on 25 October 1400 and Timur died on 18 February 1405, and a short program counts the days between the two fixed dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar with both dates in the same reckoning, so the count is exact for the dates as written. The span crosses the leap day of February 1404.",
        instructions = """
            1. Save the example as chaucer_timur.py.
            2. Run it: python3 chaucer_timur.py
        """,
        example = """
            from datetime import date

            chaucer = date(1400, 10, 25)   # Chaucer's death, as commonly given
            timur = date(1405, 2, 18)      # Timur's death, as commonly given
            print("days from Chaucer's death to Timur's death:", (timur - chaucer).days)
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Ankara to the battle of Grunwald",
        originalTitle = "Days between two battles",
        author = "The commanders of the two battles",
        year = 1402,
        summary = "The battle of Ankara was fought on 28 July 1402 and the battle of Grunwald on 15 July 1410, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated. The example uses fixed dates in the Python calendar, so the count stays the same on every run, and it tests date arithmetic rather than the battles themselves.",
        instructions = """
            1. Save the example as ankara_grunwald.py.
            2. Run it: python3 ankara_grunwald.py
        """,
        example = """
            from datetime import date

            ankara = date(1402, 7, 28)     # the battle of Ankara, as commonly given
            grunwald = date(1410, 7, 15)   # the battle of Grunwald, as commonly given
            print("days from Ankara to Grunwald:", (grunwald - ankara).days)
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.FLEX,
        section = "Sums",
        title = "The years 1400 to 1410 added up",
        originalTitle = "Sum of years",
        author = "No single author",
        year = 1410,
        summary = "The eleven years from 1400 to 1410 add up to 15455, and a short program counts them and adds them.",
        details = "The years form an arithmetic series, so the total is the number of years times the average of the first and last, which is 11 times 1405. The example prints the count and the total, so both can be checked by hand.",
        instructions = """
            1. Save the example as years_sum.py.
            2. Run it: python3 years_sum.py
        """,
        example = """
            years = list(range(1400, 1411))
            print("years:", len(years))
            print("sum of the years:", sum(years))
        """,
    ),

)
