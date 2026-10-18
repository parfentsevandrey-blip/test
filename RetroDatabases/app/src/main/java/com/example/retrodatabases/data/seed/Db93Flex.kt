package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1115 to 1120: days between a marriage and a battle, between two battles, and between a death and a wreck. */
val db93Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Field of Blood to the battle of Bremule",
        originalTitle = "Days between two battles",
        author = "The commanders of both battles",
        year = 1119,
        summary = "The Field of Blood was fought on 28 June 1119 and the battle of Bremule on 20 August 1119, and a short program counts the 53 days between them.",
        details = "Both dates are given as commonly stated, and the program uses fixed dates so the count stays the same on every run. Both dates fall in one calendar year, so the count is the same in the Julian reckoning of the period.",
        instructions = """
            1. Save the example as field_bremule.py.
            2. Run it: python3 field_bremule.py
        """,
        example = """
            from datetime import date

            field = date(1119, 6, 28)     # the battle of the Field of Blood, as commonly given
            bremule = date(1119, 8, 20)   # the battle of Bremule, as commonly given
            print("days from the Field of Blood to Bremule:", (bremule - field).days)
        """,
    ),

    entry(
        databaseId = 93,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Henry V's marriage to the battle of Welfesholz",
        originalTitle = "Days between a marriage and a battle",
        author = "Henry V and his Saxon opponents",
        year = 1115,
        summary = "Henry V married Matilda on 7 January 1114 and the battle of Welfesholz was fought on 11 February 1115, and a short program counts the 400 days between them.",
        details = "Both dates are given as commonly stated, and the program uses fixed dates so the count does not change between runs. The span runs through 1114, which is not a leap year, so the count is 365 days plus the 35 days of January and February 1115.",
        instructions = """
            1. Save the example as marriage_welfesholz.py.
            2. Run it: python3 marriage_welfesholz.py
        """,
        example = """
            from datetime import date

            marriage = date(1114, 1, 7)     # Henry V's marriage to Matilda, as commonly given
            welfesholz = date(1115, 2, 11)  # the battle of Welfesholz, as commonly given
            print("days from the marriage to Welfesholz:", (welfesholz - marriage).days)
        """,
    ),

    entry(
        databaseId = 93,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Alexios I to the loss of the White Ship",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 1120,
        summary = "Alexios I died on 15 August 1118 and the White Ship was lost on 25 November 1120, and a short program counts the 833 days between the two dates.",
        details = "Both dates are given as commonly stated, and the count uses the calendar arithmetic of Python, which gives the same difference in the Julian reckoning for these years. The span includes the leap day of 29 February 1120.",
        instructions = """
            1. Save the example as alexios_ship.py.
            2. Run it: python3 alexios_ship.py
        """,
        example = """
            from datetime import date

            alexios = date(1118, 8, 15)      # death of Alexios I Komnenos, as commonly given
            white_ship = date(1120, 11, 25)  # loss of the White Ship, as commonly given
            print("days from Alexios I's death to the White Ship:", (white_ship - alexios).days)
        """,
    ),

)
