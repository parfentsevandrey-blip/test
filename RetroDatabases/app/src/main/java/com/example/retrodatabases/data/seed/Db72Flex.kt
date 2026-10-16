package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1320 to 1330: days from Arbroath to Muhldorf, days from Muhldorf to Cassel and days from Dante's death to the Florentine gun order. */
val db72Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Declaration of Arbroath to the battle of Muhldorf",
        originalTitle = "Days between two dated events",
        author = "The parties to both events",
        year = 1322,
        summary = "The Declaration of Arbroath is dated 6 April 1320 and the battle of Muhldorf was fought on 28 September 1322, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses Python's date library on them, which gives the same count here because no century boundary lies between the two dates. The span includes no leap day, since 1320 falls before April and 1321 and 1322 are common years.",
        instructions = """
            1. Save the example as arbroath_muhldorf.py.
            2. Run it: python3 arbroath_muhldorf.py
        """,
        example = """
            from datetime import date

            arbroath = date(1320, 4, 6)     # the Declaration of Arbroath, as commonly given
            muhldorf = date(1322, 9, 28)    # the battle of Muhldorf, as commonly given
            print("days from the Declaration of Arbroath to the battle of Muhldorf:", (muhldorf - arbroath).days)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Muhldorf to the battle of Cassel",
        originalTitle = "Days between two battles",
        author = "The commanders of the two battles",
        year = 1328,
        summary = "The battle of Muhldorf was fought on 28 September 1322 and the battle of Cassel on 23 August 1328, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the span includes two leap days, February 1324 and February 1328. The example uses Python's date library, so the count stays the same on every run.",
        instructions = """
            1. Save the example as muhldorf_cassel.py.
            2. Run it: python3 muhldorf_cassel.py
        """,
        example = """
            from datetime import date

            muhldorf = date(1322, 9, 28)    # the battle of Muhldorf, as commonly given
            cassel = date(1328, 8, 23)      # the battle of Cassel, as commonly given
            print("days from the battle of Muhldorf to the battle of Cassel:", (cassel - muhldorf).days)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Dante to the Florentine gun order",
        originalTitle = "Days between a death and an order",
        author = "Dante Alighieri and the government of Florence",
        year = 1326,
        summary = "Dante died on 14 September 1321 and the Florentine gun order is dated 11 February 1326, and a short program counts the days between them.",
        details = "The later date is the one that some accounts give for the Florentine document, and accounts differ on it. The span includes the leap day of February 1324 and no other.",
        instructions = """
            1. Save the example as dante_gun.py.
            2. Run it: python3 dante_gun.py
        """,
        example = """
            from datetime import date

            dante_death = date(1321, 9, 14)   # Dante's death at Ravenna, as commonly given
            gun_order = date(1326, 2, 11)     # the Florentine order, as one account gives it
            print("days from the death of Dante to the Florentine gun order:", (gun_order - dante_death).days)
        """,
    ),

)
