package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1130 to 1137: days between the coronations and deaths of Roger II, Henry I, Stephen and Louis VI. */
val db91Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Roger II's coronation to the death of Henry I of England",
        originalTitle = "Days between two fixed dates",
        author = "Roger II of Sicily and Henry I of England",
        year = 1130,
        summary = "Roger II was crowned on Christmas Day 1130 and Henry I of England died on 1 December 1135, and a short program counts the 1802 days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in Python's calendar, whose day counts match the Julian count across these years. The span includes the leap day of February 1132 and no other.",
        instructions = """
            1. Save the example as coronation_death.py.
            2. Run it: python3 coronation_death.py
        """,
        example = """
            from datetime import date

            coronation = date(1130, 12, 25)   # Roger II crowned at Christmas, as commonly given
            henry_death = date(1135, 12, 1)   # death of Henry I, as commonly given
            print("days from Roger II's coronation to Henry I's death:", (henry_death - coronation).days)
        """,
    ),

    entry(
        databaseId = 91,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Henry I to the coronation of Stephen",
        originalTitle = "Days between two fixed dates",
        author = "Henry I and Stephen of Blois",
        year = 1135,
        summary = "Henry I of England died on 1 December 1135 and Stephen was crowned on 22 December 1135, and a short program counts the 21 days between the two dates.",
        details = "Both dates are given as commonly stated, and the program uses fixed dates so the count is the same on every run. The interval falls inside one year, so no leap day is involved, and it opens the civil strife of the period known as the Anarchy.",
        instructions = """
            1. Save the example as stephen_crowning.py.
            2. Run it: python3 stephen_crowning.py
        """,
        example = """
            from datetime import date

            henry_death = date(1135, 12, 1)         # death of Henry I, as commonly given
            stephen_crowning = date(1135, 12, 22)   # coronation of Stephen, as commonly given
            print("days from Henry I's death to Stephen's coronation:", (stephen_crowning - henry_death).days)
        """,
    ),

    entry(
        databaseId = 91,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Henry I to the death of Louis VI of France",
        originalTitle = "Days between two fixed dates",
        author = "Henry I of England and Louis VI of France",
        year = 1137,
        summary = "Henry I of England died on 1 December 1135 and Louis VI of France died on 1 August 1137, and a short program counts the 609 days between them.",
        details = "Both dates are given as commonly stated, and the example counts with fixed dates in Python's calendar, which gives the same day count as the Julian reckoning for these years. The span includes the leap day of February 1136.",
        instructions = """
            1. Save the example as two_deaths.py.
            2. Run it: python3 two_deaths.py
        """,
        example = """
            from datetime import date

            henry_death = date(1135, 12, 1)   # death of Henry I, as commonly given
            louis_death = date(1137, 8, 1)    # death of Louis VI, as commonly given
            print("days from Henry I's death to Louis VI's death:", (louis_death - henry_death).days)
        """,
    ),

)
