package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1190 to 1199: the years from Maimonides's Guide to its Hebrew version, and the days between three deaths of rulers of the decade. */
val db85Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Maimonides's Guide to its Hebrew translation",
        originalTitle = "Years between two dated books",
        author = "Moses Maimonides and Samuel ibn Tibbon",
        year = 1190,
        summary = "Maimonides's Guide for the Perplexed was completed in 1190 and Samuel ibn Tibbon's Hebrew version followed in 1204, and a one-line program counts the years between the two.",
        details = "Both dates are the usual ones for the Arabic text and its translation, and the answer is 14 years. The example is one line of subtraction, so it can be checked by hand.",
        instructions = """
            1. Save the example as guide_years.py.
            2. Run it: python3 guide_years.py
        """,
        example = """
            print("years from the Guide to the Hebrew translation:", 1204 - 1190)
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Saladin's death to the death of Henry VI",
        originalTitle = "Days between two deaths",
        author = "Saladin and Henry VI",
        year = 1197,
        summary = "Saladin died on 4 March 1193 and Henry VI died on 28 September 1197, and a short program counts the days between the two deaths.",
        details = "Both dates are given as commonly stated, and the span includes the leap day of February 1196. The program uses Python's calendar, which gives the same count as the Julian reckoning for these years.",
        instructions = """
            1. Save the example as saladin_henry.py.
            2. Run it: python3 saladin_henry.py
        """,
        example = """
            from datetime import date

            saladin = date(1193, 3, 4)
            henry_vi = date(1197, 9, 28)
            print("days from Saladin's death to Henry VI's death:", (henry_vi - saladin).days)
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Henry VI to the death of Richard I",
        originalTitle = "Days between two deaths",
        author = "Henry VI and Richard I",
        year = 1199,
        summary = "Henry VI died on 28 September 1197 and Richard I died on 6 April 1199, and a short program counts the days between the two deaths.",
        details = "The span runs through 1198 and early 1199, both common years, so it holds no leap day. The count is the same in the Julian and Gregorian calendars for these years.",
        instructions = """
            1. Save the example as henry_richard.py.
            2. Run it: python3 henry_richard.py
        """,
        example = """
            from datetime import date

            henry_vi = date(1197, 9, 28)
            richard = date(1199, 4, 6)
            print("days from Henry VI's death to Richard I's death:", (richard - henry_vi).days)
        """,
    ),

)
