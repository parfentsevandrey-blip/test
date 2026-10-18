package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1210 to 1220: days from Bouvines to Runnymede, from Runnymede to the Lateran council, and from the death of Innocent III to the Dominican bull. */
val db83Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Bouvines to the sealing of Magna Carta",
        originalTitle = "Days between two events of 1214 and 1215",
        author = "The commanders at Bouvines and the barons at Runnymede",
        year = 1215,
        summary = "The battle of Bouvines was fought on 27 July 1214 and Magna Carta was sealed on 15 June 1215, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count is the same on every run. The span crosses the turn of the year and contains no leap day.",
        instructions = """
            1. Save the example as bouvines_runnymede.py.
            2. Run it: python3 bouvines_runnymede.py
        """,
        example = """
            from datetime import date

            bouvines = date(1214, 7, 27)     # the battle of Bouvines, as commonly given
            runnymede = date(1215, 6, 15)    # the sealing of Magna Carta, as commonly given
            print("days from Bouvines to Runnymede:", (runnymede - bouvines).days)
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Magna Carta to the opening of the Fourth Lateran Council",
        originalTitle = "Days between two events of 1215",
        author = "King John and Pope Innocent III",
        year = 1215,
        summary = "Magna Carta was sealed on 15 June 1215 and the Fourth Lateran Council opened on 11 November 1215, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. Both dates fall in one year, so the count does not cross a leap day.",
        instructions = """
            1. Save the example as charter_council.py.
            2. Run it: python3 charter_council.py
        """,
        example = """
            from datetime import date

            charter = date(1215, 6, 15)     # Magna Carta, as commonly given
            council = date(1215, 11, 11)    # opening of the Fourth Lateran Council, as commonly given
            print("days from Magna Carta to the council:", (council - charter).days)
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Innocent III to the Dominican bull of 1216",
        originalTitle = "Days between two papal acts of 1216",
        author = "Pope Innocent III and Pope Honorius III",
        year = 1216,
        summary = "Innocent III died on 16 July 1216 and the bull confirming the Order of Preachers is dated 22 December 1216, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. The span lies inside one year and contains no February, so the leap day of 1216 does not enter the count.",
        instructions = """
            1. Save the example as innocent_bull.py.
            2. Run it: python3 innocent_bull.py
        """,
        example = """
            from datetime import date

            innocent = date(1216, 7, 16)    # death of Innocent III, as commonly given
            bull = date(1216, 12, 22)       # bull for the Order of Preachers, as commonly given
            print("days from Innocent III to the bull:", (bull - innocent).days)
        """,
    ),

)
