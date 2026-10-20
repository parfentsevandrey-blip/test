package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 258: the day of the year and the days left after Cyprian's execution on 14 September. */
val db179Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "The day of the year of Cyprian's martyrdom, counted in code",
        originalTitle = "Day of the year",
        author = "No single author",
        year = 258,
        summary = "Cyprian of Carthage is usually dated to his execution on 14 September 258, and a short program counts that day of the year and the days left after it.",
        details = "The Julian calendar makes 258 a common year of 365 days, since 258 is not divisible by four, and the program adds the month lengths up to September. The count gives 257 as the day of the year, counting 14 September itself, and it leaves 108 days to the end of the year.",
        instructions = """
            1. Save the example as cyprian_day.py.
            2. Run it: python3 cyprian_day.py
        """,
        example = """
            MONTH_DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
            YEAR = 258      # Julian calendar year: not a leap year, since 258 is not divisible by four
            MONTH = 9       # September
            DAY = 14        # the usual date of Cyprian's execution at Carthage

            month_days = list(MONTH_DAYS)
            if YEAR % 4 == 0:
                month_days[1] = 29
            year_length = sum(month_days)
            day_of_year = sum(month_days[:MONTH - 1]) + DAY
            print("day of the year:", day_of_year)
            print("days left in the year:", year_length - day_of_year)
            print("year length:", year_length)
        """,
        rare = true,
    ),

)
