package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1150 to 1160: days between two dates of 1154, and years between the first Pipe Roll, the Roncaglia decrees and two books. */
val db89Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Roger II's death to Henry II's coronation",
        originalTitle = "Days between two dates",
        author = "Roger II and Henry II",
        year = 1154,
        summary = "Roger II of Sicily died on 26 February 1154 and Henry II was crowned on 19 December 1154, and a short program counts the days between the two dates.",
        details = "Both dates are given as usually stated, and the count uses the same calendar for both, so the result is exact for the dates as written. The year 1154 had no leap day, so the count is a plain difference of days within one year.",
        instructions = """
            1. Save the example as roger_henry.py.
            2. Run it: python3 roger_henry.py
        """,
        example = """
            from datetime import date

            roger = date(1154, 2, 26)       # death of Roger II, as usually given
            henry = date(1154, 12, 19)      # coronation of Henry II, as usually given
            print("days from Roger II's death to Henry II's coronation:", (henry - roger).days)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.FLEX,
        section = "Accounts",
        title = "Years from the first Pipe Roll to the Roncaglia decrees",
        originalTitle = "Years between two records",
        author = "The English Exchequer and Frederick Barbarossa",
        year = 1158,
        summary = "The first Pipe Roll is dated 1155 and the Roncaglia decrees 1158, and a short program counts the years between the two dates.",
        details = "Both dates are given as usually stated, and the count is a plain difference of years, so the result is exact for the years as written. The two records come from the same decade but from different realms, one English and one imperial.",
        instructions = """
            1. Save the example as pipe_roncaglia.py.
            2. Run it: python3 pipe_roncaglia.py
        """,
        example = """
            pipe_roll = 1155     # first Pipe Roll of the unbroken series, as usually given
            roncaglia = 1158     # Roncaglia decrees, as usually given
            print("years from the first Pipe Roll to the Roncaglia decrees:", roncaglia - pipe_roll)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.FLEX,
        section = "Scholarship",
        title = "Years from the Scivias to the Metalogicon",
        originalTitle = "Years between two books",
        author = "Hildegard of Bingen and John of Salisbury",
        year = 1159,
        summary = "Hildegard's Scivias was completed in 1151 and John of Salisbury's Metalogicon in 1159, and a short program counts the years between the two books.",
        details = "Both dates are the usual ones for the two books, and the count is a plain difference of years, so the result is exact for the years as written. The two books come from different writers, but both were finished in the middle of the twelfth century.",
        instructions = """
            1. Save the example as scivias_metalogicon.py.
            2. Run it: python3 scivias_metalogicon.py
        """,
        example = """
            scivias = 1151      # Scivias completed, as usually given
            metalogicon = 1159  # Metalogicon completed, as usually given
            print("years from the Scivias to the Metalogicon:", metalogicon - scivias)
        """,
    ),

)
