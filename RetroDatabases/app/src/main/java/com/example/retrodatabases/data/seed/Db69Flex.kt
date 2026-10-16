package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1350 to 1360: the days between three pairs of dated events of the Hundred Years' War and the Jacquerie. */
val db69Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Poitiers to the Jacquerie",
        originalTitle = "Days from the battle of Poitiers to the Jacquerie",
        author = "The commanders and rebels of the two events",
        year = 1358,
        summary = "The battle of Poitiers was fought on 19 September 1356 and the Jacquerie rising began on 28 May 1358, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated. No century boundary lies between them, so the count is the same in the Julian reckoning of the chronicles and in Python's calendar, and the example tests date arithmetic rather than the events themselves.",
        instructions = """
            1. Save the example as poitiers_jacquerie.py.
            2. Run it: python3 poitiers_jacquerie.py
        """,
        example = """
            from datetime import date

            poitiers = date(1356, 9, 19)    # battle of Poitiers, as commonly given
            jacquerie = date(1358, 5, 28)   # start of the Jacquerie, as commonly given
            print("days from Poitiers to the Jacquerie:", (jacquerie - poitiers).days)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Jacquerie to the treaty of Bretigny",
        originalTitle = "Days from the Jacquerie to the treaty of Bretigny",
        author = "The rebels and the negotiators of the two events",
        year = 1360,
        summary = "The Jacquerie rising began on 28 May 1358 and the treaty of Bretigny was made on 8 May 1360, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated. The span includes the leap day of February 1360, which a careless count would miss, and the example prints the total so it can be followed by hand.",
        instructions = """
            1. Save the example as jacquerie_bretigny.py.
            2. Run it: python3 jacquerie_bretigny.py
        """,
        example = """
            from datetime import date

            jacquerie = date(1358, 5, 28)   # start of the Jacquerie, as commonly given
            bretigny = date(1360, 5, 8)     # treaty of Bretigny, as commonly given
            print("days from the Jacquerie to Bretigny:", (bretigny - jacquerie).days)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Poitiers to the treaty of Bretigny",
        originalTitle = "Days from the battle of Poitiers to the treaty of Bretigny",
        author = "The commanders and negotiators of the two events",
        year = 1360,
        summary = "The battle of Poitiers was fought on 19 September 1356 and the treaty of Bretigny was made on 8 May 1360, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated. The span crosses the leap day of February 1360, and the program prints the count so that it can be checked by adding whole years and then the months.",
        instructions = """
            1. Save the example as poitiers_bretigny.py.
            2. Run it: python3 poitiers_bretigny.py
        """,
        example = """
            from datetime import date

            poitiers = date(1356, 9, 19)   # battle of Poitiers, as commonly given
            bretigny = date(1360, 5, 8)    # treaty of Bretigny, as commonly given
            print("days from Poitiers to Bretigny:", (bretigny - poitiers).days)
        """,
    ),

)
