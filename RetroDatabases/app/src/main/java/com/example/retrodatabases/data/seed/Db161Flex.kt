package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 431 and 438: the days from Augustine's death to the Council of Ephesus, and the days from the Council of Ephesus to the Theodosian Code. */
val db161Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the opening of the Council of Ephesus to the Theodosian Code",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 438,
        summary = "The Council of Ephesus opened on 22 June 431 and the Theodosian Code was issued on 15 February 438, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated. Python counts days in the Gregorian calendar, but no century year lies between the two dates, so its count matches the Julian reckoning in which both dates were written. The count can be checked by hand from the two leap years 432 and 436 that fall between them.",
        instructions = """
            1. Save the example as ephesus_code.py.
            2. Run it: python3 ephesus_code.py
        """,
        example = """
            from datetime import date

            opening = date(431, 6, 22)   # opening of the Council of Ephesus, as commonly given
            code = date(438, 2, 15)      # the Theodosian Code issued, as commonly given
            print("days from the opening of Ephesus to the Code:", (code - opening).days)
        """,
    ),

    entry(
        databaseId = 161,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Augustine of Hippo to the Council of Ephesus",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 431,
        summary = "Augustine of Hippo died on 28 August 430 and the Council of Ephesus opened on 22 June 431, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and no century year lies between them. The count can be checked by hand: 125 days remain in 430 after 28 August, and 22 June is day 173 of 431, which makes 298.",
        instructions = """
            1. Save the example as augustine_ephesus.py.
            2. Run it: python3 augustine_ephesus.py
        """,
        example = """
            from datetime import date

            augustine = date(430, 8, 28)   # death of Augustine at Hippo, as commonly given
            opening = date(431, 6, 22)     # opening of the Council of Ephesus, as commonly given
            print("days from Augustine's death to Ephesus:", (opening - augustine).days)
        """,
    ),

)
