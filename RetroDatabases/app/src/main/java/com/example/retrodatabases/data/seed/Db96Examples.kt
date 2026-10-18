package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1080 to 1090: the Julian reckoning of the 1080s turned into Gregorian dates, with the six-day gap worked out by the century rule. */
val db96Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Christmas 1085 in the Julian reckoning, turned into Gregorian dates by a six-day gap",
        originalTitle = "Julian calendar",
        author = "Julius Caesar and Gregory XIII",
        year = 1085,
        summary = "A short program turns Christmas 1085 in the Julian reckoning into its Gregorian date, after the six-day gap that the century rule gives for the 1080s.",
        details = "The gap comes from the century rule: the year divided by 100, minus the year divided by 400, minus two, which gives six for 1085 and seven from 1100 on. Python's calendar counts days in the Gregorian way, so the program adds the gap to the Julian date to find the same day.",
        instructions = """
            1. Save the example as julian_christmas.py.
            2. Run it: python3 julian_christmas.py
        """,
        example = """
            from datetime import date, timedelta

            year = 1085
            gap = year // 100 - year // 400 - 2          # days by which the Gregorian count runs ahead
            julian = date(year, 12, 25)                   # Christmas 1085, as the Julian reckoning names it
            gregorian = julian + timedelta(days=gap)      # the same day in Python's calendar
            print("gap in days:", gap)
            print("Christmas 1085 in Gregorian dates:", gregorian, gregorian.strftime("%A"))
        """,
    ),

)
