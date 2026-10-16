package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1410 to 1420: the days between Hus's burning at Constance and the election of Martin V. */
val db63Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Days from Hus's burning to Martin V's election",
        originalTitle = "Concilium Constantiense",
        author = "The Council of Constance",
        year = 1417,
        summary = "Hus was burned at Constance on 6 July 1415 and Martin V was elected there on 11 November 1417, and a short program counts the days between the two dates.",
        details = "Both dates are in the Julian calendar used across Europe at the time, and for 1415 to 1417 the Julian and Gregorian calendars agree on leap years, so the day count is the same in either calendar. The example uses Python's date library, which counts with the Gregorian calendar, and it prints the days and the weeks with the days left over.",
        instructions = """
            1. Save the example as hus_election_days.py.
            2. Run it: python3 hus_election_days.py
        """,
        example = """
            from datetime import date

            burned = date(1415, 7, 6)      # Hus's burning at Constance, in the Julian calendar
            elected = date(1417, 11, 11)   # the election of Martin V, in the Julian calendar
            days = (elected - burned).days
            weeks, rest = divmod(days, 7)
            print("days from Hus's burning to Martin V's election:", days)
            print(f"{weeks} weeks and {rest} days")
        """,
    ),

)
