package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1282 to 1288: the day counts between the Sicilian Vespers, the death of Llywelyn ap Gruffudd, the death of Alfonso X and the battle of Worringen. */
val db76Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Sicilian Vespers to the death of Llywelyn ap Gruffudd",
        originalTitle = "Days between two fixed dates",
        author = "No single author",
        year = 1282,
        summary = "The Sicilian Vespers began on 30 March 1282 and Llywelyn ap Gruffudd was killed on 11 December 1282, and a short program counts the 256 days between them.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar with fixed dates, so the count is exact for the dates as written. The span lies inside one calendar year, so no leap day falls in it.",
        instructions = """
            1. Save the example as vespers_llywelyn.py.
            2. Run it: python3 vespers_llywelyn.py
        """,
        example = """
            from datetime import date

            vespers = date(1282, 3, 30)       # the Sicilian Vespers, as commonly given
            llywelyn = date(1282, 12, 11)     # the death of Llywelyn ap Gruffudd, as commonly given
            print("days from the Vespers to Llywelyn's death:", (llywelyn - vespers).days)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Llywelyn to the death of Alfonso X",
        originalTitle = "Days between two fixed dates",
        author = "No single author",
        year = 1284,
        summary = "Llywelyn ap Gruffudd died on 11 December 1282 and Alfonso X of Castile on 4 April 1284, and a short program counts the 480 days between the two deaths.",
        details = "Both dates are given as commonly stated, and the span crosses February 1283, which has 28 days, and February 1284, which has 29, so the leap day is counted. The count is exact for the dates as written.",
        instructions = """
            1. Save the example as llywelyn_alfonso.py.
            2. Run it: python3 llywelyn_alfonso.py
        """,
        example = """
            from datetime import date

            llywelyn = date(1282, 12, 11)   # the death of Llywelyn ap Gruffudd, as commonly given
            alfonso = date(1284, 4, 4)      # the death of Alfonso X of Castile, as commonly given
            print("days from Llywelyn's death to Alfonso's:", (alfonso - llywelyn).days)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Alfonso X to the battle of Worringen",
        originalTitle = "Days between two fixed dates",
        author = "No single author",
        year = 1288,
        summary = "Alfonso X died on 4 April 1284 and the battle of Worringen was fought on 5 June 1288, and a short program counts the 1523 days between them.",
        details = "Both dates are given as commonly stated, and the span includes the leap day of February 1288, which the calendar module counts. The count is exact for the dates as written and says nothing about the battle itself.",
        instructions = """
            1. Save the example as alfonso_worringen.py.
            2. Run it: python3 alfonso_worringen.py
        """,
        example = """
            from datetime import date

            alfonso = date(1284, 4, 4)     # the death of Alfonso X, as commonly given
            worringen = date(1288, 6, 5)   # the battle of Worringen, as commonly given
            print("days from Alfonso's death to Worringen:", (worringen - alfonso).days)
        """,
    ),

)
