package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1010 to 1017: years between the Shahnameh, the Russian law code, the Leonese charter and later dated events. */
val db103Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Ferdowsi's Shahnameh of 1010 to his death in 1020 or 1025",
        originalTitle = "Years between two dated events",
        author = "Ferdowsi of Tus",
        year = 1010,
        summary = "Ferdowsi completed the Shahnameh in 1010 and died in 1020 or 1025, and a short program counts the 10 or the 15 years between the two dates.",
        details = "Accounts of the poet give both death dates, so the program prints both counts. The arithmetic checks the years only and says nothing about the poet's life.",
        instructions = """
            1. Save the example as ferdowsi_years.py.
            2. Run it: python3 ferdowsi_years.py
        """,
        example = """
            completed = 1010                 # the Shahnameh, as commonly dated
            for death in (1020, 1025):       # the two death years given in accounts
                print("years from 1010 to", death, ":", death - completed)
        """,
    ),

    entry(
        databaseId = 103,
        kind = Kind.FLEX,
        section = "Law",
        title = "Years from the short Pravda of Yaroslav to the expanded code of his sons in 1072",
        originalTitle = "Years between two dated events",
        author = "Yaroslav the Wise",
        year = 1017,
        summary = "The short Pravda of Yaroslav is dated to about 1017 in some accounts and the expanded code of his sons to 1072, and a short program counts the 55 years between them.",
        details = "Both dates are approximate, so the count measures the gap between two usual dates and not the length of a single event. The arithmetic checks the years only.",
        instructions = """
            1. Save the example as pravda_years.py.
            2. Run it: python3 pravda_years.py
        """,
        example = """
            yaroslav = 1017                  # the short Pravda, in the usual dating
            sons = 1072                      # the expanded code of Yaroslav's sons
            print("years from the short Pravda to the expanded code:", sons - yaroslav)
        """,
    ),

    entry(
        databaseId = 103,
        kind = Kind.FLEX,
        section = "Leon",
        title = "Years from the Fuero de Leon of 1017 to the Cortes of Leon of 1188",
        originalTitle = "Years between two dated events",
        author = "Alfonso V and Alfonso IX of Leon",
        year = 1017,
        summary = "The Fuero de Leon of 1017 and the Cortes of Leon of 1188 are 171 years apart, and a short program counts the gap between the two dates.",
        details = "The 1188 assembly is often named the earliest of its kind, a claim that historians dispute, so the count is only a gap in dates and not a judgement on that claim. The arithmetic checks the years only.",
        instructions = """
            1. Save the example as fuero_cortes.py.
            2. Run it: python3 fuero_cortes.py
        """,
        example = """
            fuero = 1017                     # the Fuero de Leon, as commonly dated
            cortes = 1188                    # the Cortes of Leon, as commonly dated
            print("years from the Fuero to the Cortes:", cortes - fuero)
        """,
    ),

)
