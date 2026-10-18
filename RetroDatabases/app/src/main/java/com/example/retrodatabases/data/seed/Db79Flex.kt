package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1250 to 1260: days between the deaths, the councils and the battles of the decade, counted from fixed dates. */
val db79Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Frederick II to the enthronement of Mongke",
        originalTitle = "Days between two dated events",
        author = "Frederick II and Mongke Khan",
        year = 1251,
        summary = "Frederick II died on 13 December 1250 and Mongke was enthroned on 1 July 1251, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar with both dates in the same reckoning, so the count is exact for the dates as written. The span crosses the turn of the year but contains no leap day.",
        instructions = """
            1. Save the example as frederick_mongke.py.
            2. Run it: python3 frederick_mongke.py
        """,
        example = """
            from datetime import date

            frederick = date(1250, 12, 13)   # death of Frederick II, as commonly given
            mongke = date(1251, 7, 1)        # enthronement of Mongke Khan, as commonly given
            print("days from Frederick II to Mongke:", (mongke - frederick).days)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Innocent IV to the battle of Ain Jalut",
        originalTitle = "Days between two dated events",
        author = "Innocent IV and the armies of Ain Jalut",
        year = 1260,
        summary = "Innocent IV died on 7 December 1254 and the battle of Ain Jalut was fought on 3 September 1260, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example counts with the Python calendar, so the result is exact for the dates as written. The span includes the leap days of 1256 and 1260.",
        instructions = """
            1. Save the example as innocent_ain_jalut.py.
            2. Run it: python3 innocent_ain_jalut.py
        """,
        example = """
            from datetime import date

            innocent = date(1254, 12, 7)     # death of Innocent IV, as commonly given
            ain_jalut = date(1260, 9, 3)     # battle of Ain Jalut, as commonly given
            print("days from Innocent IV to Ain Jalut:", (ain_jalut - innocent).days)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Provisions of Oxford to the death of Mongke",
        originalTitle = "Days between two dated events",
        author = "The barons of Oxford and Mongke Khan",
        year = 1259,
        summary = "The Provisions of Oxford were agreed on 11 June 1258 and Mongke died on 11 August 1259, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar, so the count is exact for the dates as written. The span contains no leap day, since 1259 is not a leap year.",
        instructions = """
            1. Save the example as oxford_mongke.py.
            2. Run it: python3 oxford_mongke.py
        """,
        example = """
            from datetime import date

            oxford = date(1258, 6, 11)     # Provisions of Oxford, as commonly given
            mongke = date(1259, 8, 11)     # death of Mongke Khan, as commonly given
            print("days from the Provisions to Mongke:", (mongke - oxford).days)
        """,
    ),

)
