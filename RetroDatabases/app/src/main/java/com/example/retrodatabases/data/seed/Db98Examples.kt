package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1066: two English battles of 1066 turned into weekdays of the Julian calendar. */
val db98Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The weekdays of Stamford Bridge and Hastings, counted in the Julian calendar",
        originalTitle = "Julian calendar",
        author = "No single author",
        year = 1066,
        summary = "A short program turns the Julian dates of Stamford Bridge and Hastings into weekdays, and the count gives Monday for 25 September 1066 and Saturday for 14 October 1066.",
        details = "England used the Julian calendar in 1066, so the program uses the Julian day-number formula rather than the Gregorian calendar of Python's datetime module. The two battles lie 19 days apart, and the result can be checked by counting back from the Saturday.",
        instructions = """
            1. Save the example as weekdays_1066.py.
            2. Run it: python3 weekdays_1066.py
        """,
        example = """
            def julian_day_number(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]

            for label, (year, month, day) in [("Stamford Bridge", (1066, 9, 25)), ("Hastings", (1066, 10, 14))]:
                jdn = julian_day_number(year, month, day)
                print(label, "fell on a", WEEKDAYS[(jdn + 1) % 7])
        """,
    ),

)
