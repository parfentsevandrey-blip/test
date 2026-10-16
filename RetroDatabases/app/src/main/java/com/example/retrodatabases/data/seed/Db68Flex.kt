package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1364 to 1367: the days between the death of Rudolf IV and Najera, between Auray and the Vienna charter, and between the Krakow charter and the Pecs bull. */
val db68Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Rudolf IV to the battle of Najera",
        originalTitle = "Days between two dates",
        author = "Duke Rudolf IV of Austria and the commanders at Najera",
        year = 1367,
        summary = "Duke Rudolf IV died on 27 July 1365 and the battle of Najera was fought on 3 April 1367, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example counts days in the calendar used by Python, so the count is exact for the dates as written. No leap day falls in the span, so the count is 615 days.",
        instructions = """
            1. Save the example as najera.py.
            2. Run it: python3 najera.py
        """,
        example = """
            from datetime import date
            rudolf = date(1365, 7, 27)   # death of Rudolf IV, as commonly given
            najera = date(1367, 4, 3)    # battle of Najera, as commonly given
            print("days from the death of Rudolf IV to the battle of Najera:", (najera - rudolf).days)
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Auray to the Vienna charter",
        originalTitle = "Days between two dates",
        author = "The commanders at Auray and Duke Rudolf IV",
        year = 1365,
        summary = "The battle of Auray was fought on 29 September 1364 and the Vienna charter is dated 12 March 1365, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated. The span holds no leap day, since the leap day of 1364 falls before September, so the count of 164 days is exact for the dates as written.",
        instructions = """
            1. Save the example as auray.py.
            2. Run it: python3 auray.py
        """,
        example = """
            from datetime import date
            auray = date(1364, 9, 29)    # battle of Auray, as commonly given
            vienna = date(1365, 3, 12)   # Vienna charter, as commonly given
            print("days from Auray to the Vienna charter:", (vienna - auray).days)
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Krakow charter to the Pecs bull",
        originalTitle = "Days between two dates",
        author = "King Casimir III and Pope Urban V",
        year = 1367,
        summary = "The Krakow charter is dated 12 May 1364 and the Pecs bull 1 September 1367, and a short program counts the days between the two documents.",
        details = "Both dates are given as commonly stated. The span crosses the years 1365 and 1366, which have no leap day, and it ends before the leap year of 1368, so the total is 1207 days.",
        instructions = """
            1. Save the example as krakow.py.
            2. Run it: python3 krakow.py
        """,
        example = """
            from datetime import date
            krakow = date(1364, 5, 12)   # Krakow charter, as commonly given
            pecs = date(1367, 9, 1)      # Pecs bull, as commonly given
            print("days from the Krakow charter to the Pecs bull:", (pecs - krakow).days)
        """,
    ),

)
