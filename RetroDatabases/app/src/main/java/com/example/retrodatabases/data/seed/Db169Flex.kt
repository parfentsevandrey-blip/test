package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 351 to 353: the days between two battles of the civil war of Constantius II and Magnentius. */
val db169Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Mursa to the battle of Mons Seleucus",
        originalTitle = "Days between two battles",
        author = "Constantius II and Magnentius",
        year = 351,
        summary = "The battle of Mursa was fought on 28 September 351 and the battle of Mons Seleucus on 3 July 353, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, which gives the same count as the Julian reckoning for these years because no century year falls in the span. The count tests date arithmetic rather than the battles, and both battles were fought in the civil war between Constantius and Magnentius.",
        instructions = """
            1. Save the example as mursa_seleucus.py.
            2. Run it: python3 mursa_seleucus.py
        """,
        example = """
            from datetime import date

            mursa = date(351, 9, 28)         # the battle of Mursa, as commonly given
            mons_seleucus = date(353, 7, 3)  # the battle of Mons Seleucus, as commonly given
            print("days from Mursa to Mons Seleucus:", (mons_seleucus - mursa).days)
        """,
        rare = true,
    ),

)
