package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1346 to 1348: days between the battles of Crecy and Neville's Cross, the founding of Charles University and the Garter, and the Crecy-to-Garter span. */
val db70Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Crecy to the battle of Neville's Cross",
        originalTitle = "Days between two battles",
        author = "The commanders of the two battles",
        year = 1346,
        summary = "The battle of Crecy was fought on 26 August 1346 and the battle of Neville's Cross on 17 October 1346, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count is the same on every run. Both dates use one reckoning, so the count is exact for the dates as written.",
        instructions = """
            1. Save the example as crecy_nevilles.py.
            2. Run it: python3 crecy_nevilles.py
        """,
        example = """
            from datetime import date

            crecy = date(1346, 8, 26)               # battle of Crecy, as commonly given
            nevilles_cross = date(1346, 10, 17)     # battle of Neville's Cross, as commonly given
            print("days from Crecy to Neville's Cross:", (nevilles_cross - crecy).days)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the founding of Charles University to the Garter",
        originalTitle = "Days between two foundings",
        author = "Charles IV and Edward III",
        year = 1348,
        summary = "Charles University was founded on 7 April 1348 and the Order of the Garter on 23 April 1348, and a short program counts the days between the two foundings.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar with both dates in one reckoning, so the count is exact for the dates as written. The span is short and does not cross a leap day.",
        instructions = """
            1. Save the example as charles_garter.py.
            2. Run it: python3 charles_garter.py
        """,
        example = """
            from datetime import date

            charles = date(1348, 4, 7)    # founding of Charles University, as commonly given
            garter = date(1348, 4, 23)    # founding of the Order of the Garter, as commonly given
            print("days from Charles University to the Garter:", (garter - charles).days)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Crecy to the founding of the Garter",
        originalTitle = "Days between two fixed dates",
        author = "Edward III",
        year = 1348,
        summary = "The battle of Crecy was fought on 26 August 1346 and the Order of the Garter was founded on 23 April 1348, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the span crosses the leap day of February 1348, which the Python calendar counts correctly. The result can be checked by counting the days of the rest of 1346, all of 1347 and the first months of 1348.",
        instructions = """
            1. Save the example as crecy_garter.py.
            2. Run it: python3 crecy_garter.py
        """,
        example = """
            from datetime import date

            crecy = date(1346, 8, 26)     # battle of Crecy, as commonly given
            garter = date(1348, 4, 23)    # founding of the Order of the Garter, as commonly given
            print("days from Crecy to the Garter:", (garter - crecy).days)
        """,
    ),

)
