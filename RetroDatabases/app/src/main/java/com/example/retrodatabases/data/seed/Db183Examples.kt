package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 210 to 220: the years of the Eastern Han from 25 to 220, counted in code. */
val db183Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.EXAMPLE,
        section = "Chronology",
        title = "The years of the Eastern Han, from 25 to 220, counted in code",
        originalTitle = "Dong Han",
        author = "Liu Xiu, Emperor Guangwu",
        year = 220,
        summary = "The Eastern Han ran from Liu Xiu's accession in 25 to the end of the dynasty in 220, and a short program counts the years in two ways.",
        details = "Counted inclusively the span is 196 years, and between the two dates it is 195 years. Accounts differ on the start, since some begin the restored Han in 23 with the Gengshi emperor, and the example uses 25, the year Liu Xiu took the throne.",
        instructions = """
            1. Save the example as eastern_han.py.
            2. Run it: python3 eastern_han.py
        """,
        example = """
            start = 25    # Liu Xiu takes the throne, as commonly given
            end = 220     # the Han ends with the transfer of power to Cao Pi, as commonly given
            years = list(range(start, end + 1))
            print("years counted from", start, "to", end, "inclusive:", len(years))
            print("years between the two dates:", end - start)
        """,
        rare = true,
    ),

)
