package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1290 to 1300: the day of the year for four dated events in the Julian calendar, the Julian day number of 1300 and Saka years turned into Common Era years. */
val db75Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "The day of the year for four dates from 1291 to 1300 in the Julian calendar",
        originalTitle = "Day of the year",
        author = "The Julian calendar's leap-year rule",
        year = 1291,
        summary = "A short program counts the day of the year for four dates from 1291 to 1300 in the Julian calendar, where every year divisible by four is a leap year.",
        details = "The dates are the fall of Acre in 1291, the Ordinances of Justice in 1293, the Liber Sextus in 1298 and the first Holy Year bull in 1300, all given in the Julian reckoning. The program counts the days in each year, and 1300 is a leap year in this calendar.",
        instructions = """
            1. Save the example as day_of_year.py.
            2. Run it: python3 day_of_year.py
        """,
        example = """
            def is_leap(year):
                return year % 4 == 0

            def day_of_year(year, month, day):
                feb = 29 if is_leap(year) else 28
                lengths = [31, feb, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
                total = 366 if is_leap(year) else 365
                return sum(lengths[:month - 1]) + day, total

            dates = [("18 May 1291", 1291, 5, 18), ("18 January 1293", 1293, 1, 18),
                     ("3 March 1298", 1298, 3, 3), ("22 February 1300", 1300, 2, 22)]
            for label, year, month, day in dates:
                position, total = day_of_year(year, month, day)
                print(label + ": day", position, "of", total)
        """,
    ),

    entry(
        databaseId = 75,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "The Julian day number of New Year's Day 1300",
        originalTitle = "Julian day number",
        author = "Joseph Scaliger and later astronomers",
        year = 1300,
        summary = "A short program turns the Julian calendar date of 1 January 1300 into a Julian day number, counting from 1 January of year 1 in the same calendar.",
        details = "The count starts from day 1721424 of the Julian day number scale, which is 1 January of year 1 in the Julian calendar, and the program adds the days of the years and the leap years before 1300. The answer is a plain whole number that can be checked by hand from the years and the leap years.",
        instructions = """
            1. Save the example as jdn_1300.py.
            2. Run it: python3 jdn_1300.py
        """,
        example = """
            year = 1300
            years_before = year - 1
            leap_years = years_before // 4
            days_since_start = years_before * 365 + leap_years
            print("days since 1 January 1 in the Julian calendar:", days_since_start)
            print("Julian day number of 1 January 1300:", 1721424 + days_since_start)
        """,
    ),

    entry(
        databaseId = 75,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "Saka years of Indian dates turned into Common Era years",
        originalTitle = "Saka era",
        author = "Indian astronomers of the Saka era",
        year = 1290,
        summary = "The Saka era begins 78 years after the start of the Common Era, so a short program turns three Saka years of the 1290s into Common Era years by adding 78.",
        details = "The Saka reckoning was used in Marathi and other Indian texts, and the Jnaneshvari of 1290 gives its date in Saka years. Months and the exact start of each Saka year are left out, so the result is for the year number alone.",
        instructions = """
            1. Save the example as saka.py.
            2. Run it: python3 saka.py
        """,
        example = """
            for saka_year in [1212, 1213, 1214]:
                print(saka_year, "Saka is", saka_year + 78, "CE")
        """,
    ),

)
