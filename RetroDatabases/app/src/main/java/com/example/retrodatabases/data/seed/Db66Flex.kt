package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1380 to 1390: days between the battles of Kulikovo, Aljubarrota, Sempach and Kosovo, and the death of Wat Tyler. */
val db66Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Kulikovo to Wat Tyler's death",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1381,
        summary = "The battle of Kulikovo was fought on 8 September 1380 and Wat Tyler was killed on 15 June 1381, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses Python dates in one reckoning, so the count is exact for the dates as written. The span has no leap day, since February 1381 is a common year.",
        instructions = """
            1. Save the example as kulikovo_tyler.py.
            2. Run it: python3 kulikovo_tyler.py
        """,
        example = """
            from datetime import date

            kulikovo = date(1380, 9, 8)   # battle of Kulikovo, as commonly given
            tyler = date(1381, 6, 15)     # death of Wat Tyler, as commonly given
            print("days from Kulikovo to Wat Tyler's death:", (tyler - kulikovo).days)
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Sempach to Kosovo",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1389,
        summary = "Sempach was fought on 9 July 1386 and Kosovo on 15 June 1389, and a short program counts the days between the two battles.",
        details = "Both dates are commonly given in the Julian reckoning of the period, and the example uses one calendar for both, so the count is exact for the dates as written. The span includes the leap day of February 1388.",
        instructions = """
            1. Save the example as sempach_kosovo.py.
            2. Run it: python3 sempach_kosovo.py
        """,
        example = """
            from datetime import date

            sempach = date(1386, 7, 9)   # battle of Sempach, as commonly given
            kosovo = date(1389, 6, 15)   # battle of Kosovo, as commonly given
            print("days from Sempach to Kosovo:", (kosovo - sempach).days)
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Aljubarrota to Sempach",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 1386,
        summary = "Aljubarrota was fought on 14 August 1385 and Sempach on 9 July 1386, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example counts them in one calendar, so the count is exact for the dates as written. The span has no leap day, since February 1386 is a common year.",
        instructions = """
            1. Save the example as aljubarrota_sempach.py.
            2. Run it: python3 aljubarrota_sempach.py
        """,
        example = """
            from datetime import date

            aljubarrota = date(1385, 8, 14)   # battle of Aljubarrota, as commonly given
            sempach = date(1386, 7, 9)        # battle of Sempach, as commonly given
            print("days from Aljubarrota to Sempach:", (sempach - aljubarrota).days)
        """,
    ),

)
