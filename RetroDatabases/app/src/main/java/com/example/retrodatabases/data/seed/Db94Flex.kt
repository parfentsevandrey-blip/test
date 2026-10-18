package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1100 to 1109: days between three pairs of dated events in England, the Holy Land and Canterbury. */
val db94Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Godfrey's death to Baldwin I's coronation at Bethlehem",
        originalTitle = "Days between two fixed dates",
        author = "Godfrey of Bouillon and Baldwin I",
        year = 1100,
        summary = "Godfrey died on 18 July 1100 and Baldwin I was crowned on Christmas Day 1100, and a short program counts the 160 days between the two dates.",
        details = "Both dates are given as commonly stated, and the span holds no 29 February, so the count is the same in the Julian reckoning of the period and in Python's calendar. The example tests date arithmetic rather than the events.",
        instructions = """
            1. Save the example as godfrey_baldwin.py.
            2. Run it: python3 godfrey_baldwin.py
        """,
        example = """
            from datetime import date

            godfrey = date(1100, 7, 18)      # Godfrey's death, as commonly given
            baldwin = date(1100, 12, 25)     # Baldwin I's coronation at Bethlehem, as commonly given
            print("days from Godfrey's death to Baldwin's coronation:", (baldwin - godfrey).days)
        """,
    ),

    entry(
        databaseId = 94,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Henry I's coronation to the battle of Tinchebrai",
        originalTitle = "Days between two fixed dates",
        author = "Henry I and Robert Curthose",
        year = 1106,
        summary = "Henry I was crowned on 5 August 1100 and won the battle of Tinchebrai on 28 September 1106, and a short program counts the 2245 days between the two dates.",
        details = "Both dates are given as commonly stated, and the span holds one 29 February, in 1104, which the Python calendar counts in the same way as the Julian reckoning. The count tests date arithmetic rather than the battle.",
        instructions = """
            1. Save the example as coronation_tinchebrai.py.
            2. Run it: python3 coronation_tinchebrai.py
        """,
        example = """
            from datetime import date

            coronation = date(1100, 8, 5)     # Henry I's coronation, as commonly given
            tinchebrai = date(1106, 9, 28)    # the battle of Tinchebrai, as commonly given
            print("days from Henry I's coronation to Tinchebrai:", (tinchebrai - coronation).days)
        """,
    ),

    entry(
        databaseId = 94,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Baldwin I's coronation to the death of Anselm",
        originalTitle = "Days between two fixed dates",
        author = "Baldwin I and Anselm of Canterbury",
        year = 1109,
        summary = "Baldwin I was crowned on 25 December 1100 and Anselm died on 21 April 1109, and a short program counts the 3039 days between the two dates.",
        details = "Both dates are given as commonly stated, and the span holds two leap days, in 1104 and 1108, which both the Python calendar and the Julian reckoning count. The example checks the arithmetic of the dates and says nothing about the two men.",
        instructions = """
            1. Save the example as baldwin_anselm.py.
            2. Run it: python3 baldwin_anselm.py
        """,
        example = """
            from datetime import date

            baldwin = date(1100, 12, 25)    # Baldwin I's coronation, as commonly given
            anselm = date(1109, 4, 21)      # Anselm's death at Canterbury, as commonly given
            print("days from Baldwin's coronation to Anselm's death:", (anselm - baldwin).days)
        """,
    ),

)
