package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1375 to 1378: days from the death of Edward III to the coronation of Richard II, from the death of Gregory XI to the election of Urban VI, and from Petrarch's death to Boccaccio's. */
val db67Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Edward III to the coronation of Richard II",
        originalTitle = "Days between two dates",
        author = "The English court",
        year = 1377,
        summary = "Edward III died on 21 June 1377 and Richard II was crowned on 16 July 1377, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar with both dates, so the count is exact for the dates as written. The span lies within one summer and crosses no leap day.",
        instructions = """
            1. Save the example as edward_richard.py.
            2. Run it: python3 edward_richard.py
        """,
        example = """
            from datetime import date

            edward_death = date(1377, 6, 21)         # death of Edward III, as commonly given
            richard_crowned = date(1377, 7, 16)      # coronation of Richard II, as commonly given
            print("days from Edward III's death to Richard II's coronation:", (richard_crowned - edward_death).days)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.FLEX,
        section = "Papacy",
        title = "Days from the death of Gregory XI to the election of Urban VI",
        originalTitle = "Days between two dates",
        author = "The cardinals at Rome and Avignon",
        year = 1378,
        summary = "Gregory XI died on 27 March 1378 and Urban VI was elected on 8 April 1378, and a short program counts the days between the two dates.",
        details = "The dates are the ones commonly given, and the example uses fixed dates in the Python calendar so the count stays the same on every run. The count spans the weeks that opened the Western Schism, and it says nothing about the election itself.",
        instructions = """
            1. Save the example as gregory_urban.py.
            2. Run it: python3 gregory_urban.py
        """,
        example = """
            from datetime import date

            gregory_death = date(1378, 3, 27)        # death of Gregory XI, as commonly given
            urban_election = date(1378, 4, 8)        # election of Urban VI, as commonly given
            print("days from Gregory XI's death to Urban VI's election:", (urban_election - gregory_death).days)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.FLEX,
        section = "Deaths",
        title = "Days from the death of Petrarch to the death of Boccaccio",
        originalTitle = "Days between two deaths",
        author = "Petrarch and Boccaccio",
        year = 1375,
        summary = "Petrarch died on 18 July 1374 and Boccaccio on 21 December 1375, and a short program counts the 521 days between the two dates.",
        details = "Both dates are the ones commonly given, though some accounts give 19 July for Petrarch's death. The span runs through 1375, which is not a leap year, so the count has no leap day in it.",
        instructions = """
            1. Save the example as petrarch_boccaccio.py.
            2. Run it: python3 petrarch_boccaccio.py
        """,
        example = """
            from datetime import date

            petrarch = date(1374, 7, 18)             # Petrarch's death, as commonly given
            boccaccio = date(1375, 12, 21)           # Boccaccio's death, as commonly given
            print("days from Petrarch's death to Boccaccio's death:", (boccaccio - petrarch).days)
        """,
    ),

)
