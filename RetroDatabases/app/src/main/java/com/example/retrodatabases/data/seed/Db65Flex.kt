package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1390 to 1400: days between the battle of Nicopolis, the deposition of Richard II, his successor's coronation and the Kalmar Union. */
val db65Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Nicopolis to the deposition of Richard II",
        originalTitle = "Days between two dated events",
        author = "The commanders at Nicopolis and the English Parliament",
        year = 1399,
        summary = "The battle of Nicopolis was fought on 25 September 1396 and Richard II was deposed on 29 September 1399, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses the standard date library with both dates in the same reckoning. The count is 1099 days, and it crosses no leap day, since none of the Februaries from 1397 to 1399 is a leap year.",
        instructions = """
            1. Save the example as nicopolis_deposition.py.
            2. Run it: python3 nicopolis_deposition.py
        """,
        example = """
            from datetime import date

            nicopolis = date(1396, 9, 25)
            deposition = date(1399, 9, 29)
            print("days from Nicopolis to the deposition:", (deposition - nicopolis).days)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the deposition of Richard II to the coronation of Henry IV",
        originalTitle = "Days between two dated events",
        author = "The English Parliament and Henry IV",
        year = 1399,
        summary = "Richard II was deposed on 29 September 1399 and Henry IV was crowned on 13 October 1399, and a short program counts the 14 days between them.",
        details = "Both dates are commonly given as stated, and the example counts them with the standard date library. The count stays the same on every run, and it tests the date arithmetic rather than the events.",
        instructions = """
            1. Save the example as deposition_coronation.py.
            2. Run it: python3 deposition_coronation.py
        """,
        example = """
            from datetime import date

            deposition = date(1399, 9, 29)
            coronation = date(1399, 10, 13)
            print("days from the deposition to the coronation:", (coronation - deposition).days)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Nicopolis to the Kalmar Union",
        originalTitle = "Days between two dated events",
        author = "The commanders at Nicopolis and the Kalmar delegates",
        year = 1397,
        summary = "The battle of Nicopolis of 25 September 1396 and the Kalmar Union of 17 June 1397 are 265 days apart, and a short program counts them.",
        details = "The Kalmar date is the June 1397 agreement as commonly given, and the example treats it as a fixed day. The count crosses the turn of the year, so the program is a useful check of the date arithmetic.",
        instructions = """
            1. Save the example as nicopolis_kalmar.py.
            2. Run it: python3 nicopolis_kalmar.py
        """,
        example = """
            from datetime import date

            nicopolis = date(1396, 9, 25)
            kalmar = date(1397, 6, 17)
            print("days from Nicopolis to the Kalmar Union:", (kalmar - nicopolis).days)
        """,
    ),

)
