package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1540 to 1545: days from the Jesuit bull to the Council of Trent, days in the 1540s and days from the New Laws to the Albertina. */
val db50Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Jesuit bull to the Council of Trent",
        originalTitle = "Regimini militantis Ecclesiae",
        author = "Pope Paul III",
        year = 1545,
        summary = "Python's date arithmetic counts the days from the Jesuit bull of 27 September 1540 to the first session of the Council of Trent on 13 December 1545.",
        details = "The bull is commonly dated 27 September 1540, and the council's first session is commonly given as 13 December 1545. The example counts the days between these fixed dates, so the result never changes.",
        instructions = """
            1. Save the example as bull_to_trent.py.
            2. Run it: python3 bull_to_trent.py
        """,
        example = """
            from datetime import date

            bull = date(1540, 9, 27)       # the Jesuit bull, as commonly dated
            council = date(1545, 12, 13)   # the first session of the Council of Trent, as commonly dated
            print("days from the Jesuit bull to the council:", (council - bull).days)
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.FLEX,
        section = "Decades",
        title = "Days in the 1540s, from the first day to the last",
        originalTitle = "Anno Domini",
        author = "Western calendar reckoning",
        year = 1540,
        summary = "Python's date arithmetic counts the days in the decade from 1 January 1540 to 31 December 1549, a span that holds three leap years.",
        details = "The decade has ten years, and three of them, 1540, 1544 and 1548, are leap years. The example uses fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as decade_days.py.
            2. Run it: python3 decade_days.py
        """,
        example = """
            from datetime import date

            first = date(1540, 1, 1)
            last = date(1549, 12, 31)
            print("days in the 1540s:", (last - first).days + 1)
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the New Laws to the Albertina",
        originalTitle = "Leyes Nuevas",
        author = "Emperor Charles V",
        year = 1542,
        summary = "Python's date arithmetic counts the days from the New Laws of 20 November 1542 to the Albertina's opening on 17 August 1544.",
        details = "The New Laws are commonly dated 20 November 1542, and the Albertina's opening is commonly given as 17 August 1544. The example counts the days between these fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as laws_to_albertina.py.
            2. Run it: python3 laws_to_albertina.py
        """,
        example = """
            from datetime import date

            laws = date(1542, 11, 20)      # the New Laws, as commonly dated
            opened = date(1544, 8, 17)     # the Albertina's opening, as commonly given
            print("days from the New Laws to the Albertina:", (opened - laws).days)
        """,
    ),

)
