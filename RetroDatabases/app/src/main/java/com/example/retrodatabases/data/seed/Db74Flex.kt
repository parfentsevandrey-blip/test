package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1300 to 1310: days between the battle of Courtrai, the first Estates-General, the coronation of Clement V and the Templar arrests. */
val db74Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between Courtrai and the Templar arrests",
        originalTitle = "Days between two fixed dates",
        author = "The armies of Courtrai and the royal officers of France",
        year = 1307,
        summary = "The battle of Courtrai was fought on 11 July 1302 and the Templar arrests in France began on 13 October 1307, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated. Python's calendar is proleptic Gregorian, but across these dates its day count matches the Julian reckoning of the time, because the span contains no century year that changes the count, and it includes the leap day of February 1304.",
        instructions = """
            1. Save the example as courtrai_templar.py.
            2. Run it: python3 courtrai_templar.py
        """,
        example = """
            from datetime import date

            courtrai = date(1302, 7, 11)     # the battle of Courtrai, as commonly dated
            arrests = date(1307, 10, 13)     # the Templar arrests in France, as commonly dated
            print("days between Courtrai and the Templar arrests:", (arrests - courtrai).days)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between the first Estates-General and the coronation of Clement V",
        originalTitle = "Days between two fixed dates",
        author = "Philip IV and Clement V",
        year = 1305,
        summary = "The assembly of the three estates met on 10 April 1302 and Clement V was crowned at Lyon on 14 November 1305, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated. The span includes the leap day of February 1304 and no century year, so the count of 1314 days matches the historical reckoning, as in the Courtrai example.",
        instructions = """
            1. Save the example as estates_coronation.py.
            2. Run it: python3 estates_coronation.py
        """,
        example = """
            from datetime import date

            estates = date(1302, 4, 10)       # the first Estates-General, as commonly dated
            coronation = date(1305, 11, 14)   # Clement V crowned at Lyon, as commonly dated
            print("days between the Estates-General and the coronation of Clement V:", (coronation - estates).days)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between the coronation of Clement V and the Templar arrests",
        originalTitle = "Days between two fixed dates",
        author = "Clement V and Philip IV",
        year = 1307,
        summary = "Clement V was crowned at Lyon on 14 November 1305 and the Templar arrests in France began on 13 October 1307, and a short program counts the days between them.",
        details = "The span includes no leap day, since February 1306 and February 1307 are common years, so the count is 698 days. Both dates are given as commonly stated.",
        instructions = """
            1. Save the example as coronation_templar.py.
            2. Run it: python3 coronation_templar.py
        """,
        example = """
            from datetime import date

            coronation = date(1305, 11, 14)   # Clement V crowned at Lyon, as commonly dated
            arrests = date(1307, 10, 13)      # the Templar arrests in France, as commonly dated
            print("days between the coronation of Clement V and the Templar arrests:", (arrests - coronation).days)
        """,
    ),

)
