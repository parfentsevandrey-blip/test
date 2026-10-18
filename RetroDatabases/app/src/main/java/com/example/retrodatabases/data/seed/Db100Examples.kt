package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1046: the enthronement of Clement II on 25 December 1046, dated in the Julian and Gregorian reckonings. */
val db100Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The enthronement of Clement II on 25 December 1046, dated in two calendars",
        originalTitle = "The enthronement of Clement II on 25 December 1046, dated in two calendars",
        author = "Clement II and the Roman church",
        year = 1046,
        summary = "A short program gives the weekday of Clement II's enthronement, 25 December 1046 in the Julian reckoning, and converts the date into the Gregorian count, which is 31 December 1046.",
        details = "The Julian calendar was in use in the West in 1046, and the Gregorian count runs six days ahead of it that year, so one day carries two dates. The program finds the weekday by the Julian leap rule and by Python's Gregorian calendar, and both give a Thursday.",
        instructions = """
            1. Save the example as clement_calendars.py.
            2. Run it: python3 clement_calendars.py
        """,
        example = """
            from datetime import date

            NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]

            def julian_weekday(year, month, day):
                # 1 January of year 1 (Julian) was a Saturday; leap years are the years divisible by 4
                days_before_year = (year - 1) * 365 + (year - 1) // 4
                month_lengths = [31, 29 if year % 4 == 0 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
                days = days_before_year + sum(month_lengths[:month - 1]) + (day - 1)
                return NAMES[(6 + days) % 7]

            year, month, day = 1046, 12, 25
            offset = year // 100 - year // 400 - 2   # days the Gregorian count runs ahead of the Julian one
            gregorian = date(year, month, day + offset)
            print("Julian", day, "December", year, "is a", julian_weekday(year, month, day))
            print("the same day in the Gregorian count is", gregorian.day, "December", gregorian.year)
            print("Gregorian weekday check:", NAMES[(gregorian.weekday() + 1) % 7])
            print("days ahead in", year, "is", offset)
        """,
    ),

)
