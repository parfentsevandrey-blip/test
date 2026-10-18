package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1160 to 1170: the day number of Becket's death in the Julian reckoning, converted to the Gregorian date. */
val db88Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "The Julian date of Becket's death, counted in the Gregorian calendar",
        originalTitle = "Julian day number",
        author = "The Julian and Gregorian calendars",
        year = 1170,
        summary = "Becket was murdered on 29 December 1170 by the Julian reckoning in use in England, and a short program turns that day into its Gregorian date, 5 January 1171.",
        details = "England followed the Julian calendar in the twelfth century, and the Gregorian calendar then ran seven days ahead of it. The example computes the day number of the Julian date and converts that number back to a Gregorian date, so the seven-day gap appears in the output.",
        instructions = """
            1. Save the example as becket_gregorian.py.
            2. Run it: python3 becket_gregorian.py
        """,
        example = """
            from datetime import date

            def julian_day_number(year, month, day):
                # day number of a date written in the Julian calendar
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            def gregorian_date(day_number):
                # the Gregorian calendar date with this day number
                return date.fromordinal(day_number - 1721425)

            number = julian_day_number(1170, 12, 29)
            same_day = gregorian_date(number)
            print("day number of 29 December 1170 (Julian):", number)
            print("the same day in the Gregorian calendar:", same_day.isoformat())
            print("days added by the calendar change:", (same_day - date(1170, 12, 29)).days)
        """,
    ),

)
