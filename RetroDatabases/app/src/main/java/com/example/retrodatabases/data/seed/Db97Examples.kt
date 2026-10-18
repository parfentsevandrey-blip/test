package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1077: the days of Henry IV's penance at Canossa, counted in two ways in one program. */
val db97Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.EXAMPLE,
        section = "Empire and papacy",
        title = "The days of Henry IV's penance at Canossa, counted with both ends included",
        originalTitle = "The days of Henry IV's penance at Canossa, counted with both ends included",
        author = "Henry IV and Gregory VII",
        year = 1077,
        summary = "A short program counts the days of Henry IV's penance at Canossa, where accounts give either three days or four.",
        details = "Counting from 25 January to 28 January 1077 with both ends included gives four days, and counting to 27 January gives three, so the two figures differ by one day. The program prints both counts.",
        instructions = """
            1. Save the example as canossa_days.py.
            2. Run it: python3 canossa_days.py
        """,
        example = """
            from datetime import date

            start = date(1077, 1, 25)       # first day of the penance, as commonly given (Julian)
            absolution = date(1077, 1, 28)  # absolution, as commonly given (Julian)
            third_day = date(1077, 1, 27)   # the third day of the penance
            # both dates lie in one month, so the day count is the same in either calendar
            print("days from 25 to 28 January 1077, both included:", (absolution - start).days + 1)
            print("days from 25 to 27 January 1077, both included:", (third_day - start).days + 1)
        """,
    ),

)
