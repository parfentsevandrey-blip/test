package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 375: the days from Valentinian I's death to the battle of Adrianople, computed in one line of arithmetic. */
val db167Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Valentinian I's death to the battle of Adrianople",
        originalTitle = "Days between two dated events",
        author = "Valentinian I and the Gothic war",
        year = 375,
        summary = "Valentinian I died on 17 November 375 and the battle of Adrianople was fought on 9 August 378, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses Python's calendar for both. No century year falls between the two dates, so the Julian reckoning of the time and the calendar used by the program give the same count, which spans the leap day of February 376.",
        instructions = """
            1. Save the example as valentinian_adrianople.py.
            2. Run it: python3 valentinian_adrianople.py
        """,
        example = """
            from datetime import date

            valentinian = date(375, 11, 17)   # Valentinian I's death at Brigetio, as commonly given
            adrianople = date(378, 8, 9)      # the battle of Adrianople, as commonly given
            print("days from Valentinian's death to Adrianople:", (adrianople - valentinian).days)
        """,
    ),

)
