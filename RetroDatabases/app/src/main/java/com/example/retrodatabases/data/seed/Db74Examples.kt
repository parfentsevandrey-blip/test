package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1300 to 1310: years counted from a studium's bull, a clock's core and first record, a religious order's years, a spectacle sermon, the chapters of a Latin treatise and Stirling's days. */
val db74Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Universities",
        title = "Years between Perugia's first bull and its later privileges",
        originalTitle = "Privileges of the studium",
        author = "Popes and emperors of the 1300s",
        year = 1308,
        summary = "The privileges that followed Perugia's bull of 1308 came in 1318, 1321 and 1355, and a short program counts the years between the bull and each.",
        details = "The 1318 bull extended degree-giving rights to civil and canon law, the 1321 grant covered medicine and the arts, and the 1355 imperial document confirmed the foundation. Each count is in whole years from the bull's usual date of 1308.",
        instructions = """
            1. Save the example as perugia_privileges.py.
            2. Run it: python3 perugia_privileges.py
        """,
        example = """
            STUDIUM_BULL = 1308   # Perugia made a studium generale in 1308
            later = [
                ("1318 bull on civil and canon law", 1318),
                ("1321 grant for medicine and arts", 1321),
                ("1355 imperial document", 1355),
            ]
            for label, year in later:
                print(f"{label}: {year - STUDIUM_BULL} years after the bull")
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Clocks",
        title = "Span from the Beauvais mechanism's core to its first written trace",
        originalTitle = "Canon Musique clock years",
        author = "The Beauvais chapter",
        year = 1300,
        summary = "The core workings of the Canon Musique clock are dated to the early 1300s and its first written trace to 1387, and a short program counts the years between them.",
        details = "The core uses 1300 as a round date for the early 1300s, so the first count is approximate. The later writings of 1540 are included to show how long the written record of the clock runs.",
        instructions = """
            1. Save the example as beauvais_years.py.
            2. Run it: python3 beauvais_years.py
        """,
        example = """
            CORE_YEAR = 1300       # the cathedral dates the core workings to the early 1300s
            FIRST_RECORD = 1387    # repairs handled by Pierre de Saint-Omer
            LATER_WRITING = 1540   # later writings that mention the clock
            print("years from the core to the first record:", FIRST_RECORD - CORE_YEAR)
            print("years from the first record to the later writing:", LATER_WRITING - FIRST_RECORD)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Prussia",
        title = "Years from the founding of the Teutonic Order to its move to Marienburg",
        originalTitle = "Years of the Order",
        author = "The Teutonic Order",
        year = 1309,
        summary = "The Teutonic Order dates its founding to about 1190, and a short program counts the years to its move to Marienburg in 1309 and on to the battle of Grunwald in 1410.",
        details = "The founding date is approximate, since the Order's early years are recorded unevenly. The counts are in whole years, and they show how long the Order had been in existence before it settled in Prussia.",
        instructions = """
            1. Save the example as teutonic_years.py.
            2. Run it: python3 teutonic_years.py
        """,
        example = """
            ORDER_FOUNDED = 1190         # founding of the Teutonic Order, about 1190
            MOVE_TO_MARIENBURG = 1309    # the seat moved to Marienburg in 1309
            GRUNWALD = 1410              # battle of Grunwald
            print("years from the founding to the move:", MOVE_TO_MARIENBURG - ORDER_FOUNDED)
            print("years from the move to Grunwald:", GRUNWALD - MOVE_TO_MARIENBURG)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Glass",
        title = "The start of spectacle making, worked out from not yet twenty years",
        originalTitle = "Not yet twenty years",
        author = "Giordano da Pisa",
        year = 1306,
        summary = "If the craft of spectacles was not yet twenty years old in Giordano da Pisa's sermon of 1306, then it began after 1286, and a short program prints that year.",
        details = "The sermon's wording comes down through a later record, so the twenty years is a rough figure rather than an exact count. The sermon is dated 1305 in Florentine reckoning, which is 1306 in modern reckoning.",
        instructions = """
            1. Save the example as spectacles_year.py.
            2. Run it: python3 spectacles_year.py
        """,
        example = """
            SERMON_YEAR = 1306    # the sermon, 1305 in Florentine reckoning and 1306 in modern reckoning
            NOT_YET_YEARS = 20    # the craft is not yet twenty years old
            print("the craft began after:", SERMON_YEAR - NOT_YET_YEARS)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Latin",
        title = "The chapter totals of De vulgari eloquentia, by its two books",
        originalTitle = "De vulgari eloquentia",
        author = "Dante Alighieri",
        year = 1305,
        summary = "Dante's De vulgari eloquentia has nineteen chapters in its first book and fourteen in its unfinished second book, and a short program adds them up.",
        details = "The counts follow the usual chapter division of the treatise, and the second book stops before the planned later books begin. The example prints each book and the sum of the two.",
        instructions = """
            1. Save the example as chapters.py.
            2. Run it: python3 chapters.py
        """,
        example = """
            BOOK_ONE = 19   # chapters in the first book
            BOOK_TWO = 14   # chapters in the second book, which breaks off
            print("chapters in book I:", BOOK_ONE)
            print("chapters in book II:", BOOK_TWO)
            print("chapters in both books:", BOOK_ONE + BOOK_TWO)
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.EXAMPLE,
        section = "Siege",
        title = "Stirling's resistance in 1304, counted as a span of days",
        originalTitle = "Days of the Stirling siege",
        author = "The chroniclers of the siege",
        year = 1304,
        summary = "The resistance at Stirling Castle lasted from early April to late July 1304, and a short program counts the days in that bracket.",
        details = "The bracket is taken from the sources, which describe the siege as lasting roughly four months, so the count is for the bracket and not for exact days of the siege. The count from 1 April to 31 July 1304 is 121 days.",
        instructions = """
            1. Save the example as stirling_days.py.
            2. Run it: python3 stirling_days.py
        """,
        example = """
            from datetime import date

            start = date(1304, 4, 1)   # early April 1304, the start of the bracket
            end = date(1304, 7, 31)    # late July 1304, the end of the bracket
            print("days from 1 April to 31 July 1304:", (end - start).days)
        """,
    ),

)
