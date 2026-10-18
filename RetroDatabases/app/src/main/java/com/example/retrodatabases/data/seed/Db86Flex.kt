package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1187 to 1189: the days between three pairs of dated events of the late 1180s. */
val db86Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Hattin to the surrender of Jerusalem",
        originalTitle = "Days between two events",
        author = "Saladin and the kingdom of Jerusalem",
        year = 1187,
        summary = "The battle of Hattin was fought on 4 July 1187 and Jerusalem surrendered on 2 October 1187, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar. Both fall in one year, so the count is the same whichever calendar reckoning the dates are read in.",
        instructions = """
            1. Save the example as hattin.py.
            2. Run it: python3 hattin.py
        """,
        example = """
            from datetime import date

            hattin = date(1187, 7, 4)       # battle of Hattin, as commonly given
            jerusalem = date(1187, 10, 2)   # surrender of Jerusalem, as commonly given
            print("days from Hattin to the surrender of Jerusalem:", (jerusalem - hattin).days)
        """,
    ),

    entry(
        databaseId = 86,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Henry II to the coronation of Richard I",
        originalTitle = "Days between two events",
        author = "The English court of Henry II and Richard I",
        year = 1189,
        summary = "Henry II died on 6 July 1189 and Richard I was crowned on 3 September 1189, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. Both fall in one year, so the count does not depend on the calendar reckoning used for the dates.",
        instructions = """
            1. Save the example as henry_richard.py.
            2. Run it: python3 henry_richard.py
        """,
        example = """
            from datetime import date

            henry = date(1189, 7, 6)     # death of Henry II, as commonly given
            richard = date(1189, 9, 3)   # coronation of Richard I, as commonly given
            print("days from the death of Henry II to the coronation of Richard I:", (richard - henry).days)
        """,
    ),

    entry(
        databaseId = 86,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the surrender of Jerusalem to the drowning of Barbarossa",
        originalTitle = "Days between two events",
        author = "Saladin and Frederick I Barbarossa",
        year = 1187,
        summary = "Jerusalem surrendered on 2 October 1187 and Frederick I Barbarossa drowned on 10 June 1190, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar with both in the same reckoning. The span crosses the leap day of February 1188 but no century year, so the count is the same in the Julian and Gregorian reckonings.",
        instructions = """
            1. Save the example as jerusalem_barbarossa.py.
            2. Run it: python3 jerusalem_barbarossa.py
        """,
        example = """
            from datetime import date

            jerusalem = date(1187, 10, 2)    # surrender of Jerusalem, as commonly given
            barbarossa = date(1190, 6, 10)   # drowning of Frederick I, as commonly given
            print("days from the surrender of Jerusalem to the drowning of Barbarossa:", (barbarossa - jerusalem).days)
        """,
    ),

)
