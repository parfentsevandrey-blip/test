package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1176 and 1177: the days between the murder of Becket, the battle of Legnano, Myriokephalon and Montgisard. */
val db87Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between Becket's murder and the Lombard victory at Legnano",
        originalTitle = "Days between two dated events",
        author = "Thomas Becket and the Lombard League",
        year = 1176,
        summary = "Becket was murdered on 29 December 1170 and the Lombard cities beat Frederick Barbarossa at Legnano on 29 May 1176, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the count is the same in the Julian reckoning of the time as in Python's calendar, because both dates fall under the same offset between the two calendars. The example tests date arithmetic, not the events themselves.",
        instructions = """
            1. Save the example as becket_legnano.py.
            2. Run it: python3 becket_legnano.py
        """,
        example = """
            from datetime import date

            becket = date(1170, 12, 29)   # the murder of Thomas Becket, as commonly given
            legnano = date(1176, 5, 29)   # the battle of Legnano, as commonly given
            print("days from Becket's murder to Legnano:", (legnano - becket).days)
        """,
    ),

    entry(
        databaseId = 87,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between Legnano and the Byzantine defeat at Myriokephalon",
        originalTitle = "Days between two dated events",
        author = "Frederick Barbarossa and Manuel I Komnenos",
        year = 1176,
        summary = "The battle of Legnano was fought on 29 May 1176 and the Byzantine defeat at Myriokephalon on 17 September 1176, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated and both fall in one year, so the count is a short one. The example tests date arithmetic, not the battles themselves.",
        instructions = """
            1. Save the example as legnano_myriokephalon.py.
            2. Run it: python3 legnano_myriokephalon.py
        """,
        example = """
            from datetime import date

            legnano = date(1176, 5, 29)           # the battle of Legnano, as commonly given
            myriokephalon = date(1176, 9, 17)     # the defeat at Myriokephalon, as commonly given
            print("days from Legnano to Myriokephalon:", (myriokephalon - legnano).days)
        """,
    ),

    entry(
        databaseId = 87,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days between Myriokephalon and the Christian victory at Montgisard",
        originalTitle = "Days between two dated events",
        author = "Baldwin IV of Jerusalem and Manuel I Komnenos",
        year = 1177,
        summary = "The Byzantine defeat at Myriokephalon was on 17 September 1176 and the Christian victory at Montgisard on 25 November 1177, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the span crosses one full year, so the count is a year and a short stretch. The example tests date arithmetic, not the battles themselves.",
        instructions = """
            1. Save the example as myriokephalon_montgisard.py.
            2. Run it: python3 myriokephalon_montgisard.py
        """,
        example = """
            from datetime import date

            myriokephalon = date(1176, 9, 17)   # the defeat at Myriokephalon, as commonly given
            montgisard = date(1177, 11, 25)     # the battle of Montgisard, as commonly given
            print("days from Myriokephalon to Montgisard:", (montgisard - myriokephalon).days)
        """,
    ),

)
