package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1080 to 1090: the days between the Christmas council of the Domesday survey and the death of William I. */
val db96Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Christmas council at Gloucester in 1085 to the death of William I",
        originalTitle = "Days between two fixed dates",
        author = "William I of England",
        year = 1087,
        summary = "The Domesday survey was ordered at Christmas 1085 and William I died on 9 September 1087, and a short program counts the 623 days between the two dates.",
        details = "Both dates are given as commonly stated, and the span holds no 29 February and no century year, so the count is the same in the Julian reckoning of the period and in Python's calendar. The example tests date arithmetic rather than the survey or the king's death.",
        instructions = """
            1. Save the example as domesday_william.py.
            2. Run it: python3 domesday_william.py
        """,
        example = """
            from datetime import date

            gloucester = date(1085, 12, 25)     # the Christmas council at Gloucester, as commonly given
            william_death = date(1087, 9, 9)    # William I's death at Rouen, as commonly given
            print("days from the Christmas council to the death of William I:", (william_death - gloucester).days)
        """,
    ),

)
