package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Example of 321: the Sundays of the Julian year of Constantine's Sunday law, counted day by day in code. */
val db172Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Sundays in the year of Constantine's Sunday law, counted day by day in code",
        originalTitle = "Julian year 321",
        author = "No single author",
        year = 321,
        summary = "In the Julian reckoning 321, the year of Constantine's Sunday law, a short program counts the days and the Sundays of the year.",
        details = "The year began on a Sunday and had 365 days, so the count comes to 53 Sundays. The example applies a weekday rule for the Julian calendar to every day, so the total can be checked by counting the weeks by hand.",
        instructions = """
            1. Save the example as sundays_321.py.
            2. Run it: python3 sundays_321.py
        """,
        example = """
            # Julian reckoning: a year is a leap year when it divides by four.
            # Zeller's congruence for the Julian calendar: 0 is Saturday, 1 Sunday, ... 6 Friday.
            NAMES = ["Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]

            def weekday(year, month, day):
                if month < 3:
                    month += 12
                    year -= 1
                k, j = year % 100, year // 100
                h = (day + (13 * (month + 1)) // 5 + k + k // 4 + 5 + 6 * j) % 7
                return NAMES[h]

            def month_lengths(year):
                leap = year % 4 == 0
                return [31, 29 if leap else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

            YEAR = 321
            days = 0
            sundays = 0
            for month, length in enumerate(month_lengths(YEAR), start=1):
                for day in range(1, length + 1):
                    days += 1
                    if weekday(YEAR, month, day) == "Sunday":
                        sundays += 1
            print("year", YEAR, "days:", days)
            print("1 January", YEAR, "was a", weekday(YEAR, 1, 1))
            print("Sundays:", sundays)
        """,
        rare = true,
    ),

)
