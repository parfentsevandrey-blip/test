package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1120 to 1130: day counts between the White Ship, the Concordat of Worms, the Council of Troyes and the deaths of two popes. */
val db92Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Concordat of Worms to the Council of Troyes",
        originalTitle = "Days between two fixed dates",
        author = "No single author",
        year = 1122,
        summary = "The Concordat of Worms was sealed on 23 September 1122 and the Council of Troyes opened on 13 January 1129, and a short program counts the 2304 days between them.",
        details = "Both dates are as commonly given, and the Python calendar counts them as they stand. No century year lies between them, so the Python count matches the count in the Julian reckoning that the sources use, and the span crosses the leap days of February 1124 and February 1128.",
        instructions = """
            1. Save the example as worms_troyes.py.
            2. Run it: python3 worms_troyes.py
        """,
        example = """
            from datetime import date

            worms = date(1122, 9, 23)    # Concordat of Worms, as commonly given
            troyes = date(1129, 1, 13)   # opening of the Council of Troyes, as commonly given
            print("days from Worms to Troyes:", (troyes - worms).days)
        """,
    ),

    entry(
        databaseId = 92,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the White Ship to the Concordat of Worms",
        originalTitle = "Days between two fixed dates",
        author = "No single author",
        year = 1120,
        summary = "The White Ship sank on 25 November 1120 and the Concordat of Worms was sealed on 23 September 1122, and a short program counts the 667 days between the two.",
        details = "The sinking drowned William Adelin, the heir of Henry I of England, and the date is commonly given as 25 November 1120. The span has no leap day, since 1121 and 1122 are common years, and the count is the same in the Julian reckoning.",
        instructions = """
            1. Save the example as white_ship.py.
            2. Run it: python3 white_ship.py
        """,
        example = """
            from datetime import date

            ship = date(1120, 11, 25)      # the White Ship sinks, as commonly given
            concordat = date(1122, 9, 23)  # the Concordat of Worms, as commonly given
            print("days from the White Ship to the Concordat:", (concordat - ship).days)
        """,
    ),

    entry(
        databaseId = 92,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Callixtus II to the death of Honorius II",
        originalTitle = "Days between two fixed dates",
        author = "Popes Callixtus II and Honorius II",
        year = 1124,
        summary = "Callixtus II died on 13 December 1124 and Honorius II died on 13 February 1130, and a short program counts the 1888 days between the two deaths.",
        details = "Both dates are as commonly given. The span starts after the leap day of February 1124 and crosses the leap day of February 1128, so exactly one leap day falls inside it.",
        instructions = """
            1. Save the example as callixtus_honorius.py.
            2. Run it: python3 callixtus_honorius.py
        """,
        example = """
            from datetime import date

            callixtus = date(1124, 12, 13)   # death of Callixtus II, as commonly given
            honorius = date(1130, 2, 13)     # death of Honorius II, as commonly given
            print("days from Callixtus to Honorius:", (honorius - callixtus).days)
        """,
    ),

)
