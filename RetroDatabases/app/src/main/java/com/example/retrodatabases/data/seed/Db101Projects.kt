package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1031 to 1039: the day of the year on which five rulers of the decade died, counted by a short program. */
val db101Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.PROJECT,
        section = "Calendar",
        title = "The day of the year on which five rulers of the 1030s died",
        originalTitle = "The day of the year on which five rulers of the 1030s died",
        author = "Robert II, Rudolf III, Romanos III, Stephen I and Conrad II",
        year = 1039,
        summary = "A short program counts the day of the year for five rulers' deaths between 1031 and 1039 and prints the days left in each year.",
        details = "Each date is the one usually given for the death of the ruler named. Accounts differ on the day of Romanos III's death: 11 April 1034 is the usual date, while the chronicle of John Skylitzes gives 15 April, and the program uses 11 April. The count starts at 1 January as day 1, and 1032 is the only leap year among the five dates, so its extra day is counted.",
        instructions = """
            1. Save the example as rulers_day_of_year.py.
            2. Run it: python3 rulers_day_of_year.py
        """,
        example = """
            DAYS_BEFORE = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]


            def is_leap(year):
                return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)


            def day_of_year(year, month, day):
                extra = 1 if month > 2 and is_leap(year) else 0
                return DAYS_BEFORE[month - 1] + extra + day


            deaths = [
                ("Robert II of France", 1031, 7, 20),
                ("Rudolf III of Burgundy", 1032, 9, 6),
                ("Romanos III", 1034, 4, 11),
                ("Stephen I of Hungary", 1038, 8, 15),
                ("Conrad II", 1039, 6, 4),
            ]

            leap_count = 0
            for name, year, month, day in deaths:
                length = 366 if is_leap(year) else 365
                n = day_of_year(year, month, day)
                leap_count += 1 if is_leap(year) else 0
                print(f"{name} ({year}): day {n} of the year, {length - n} days left")
            print("leap years among the items:", leap_count)
        """,
    ),

)
