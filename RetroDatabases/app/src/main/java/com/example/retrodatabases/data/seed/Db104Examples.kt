package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1000: the leap years of 1000 to 1010 counted under the Julian rule and under the Gregorian rule. */
val db104Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "Leap years of 1000 to 1010 under the Julian rule and under the Gregorian rule",
        originalTitle = "Leap years of 1000 to 1010 under the Julian rule and under the Gregorian rule",
        author = "Julius Caesar and Pope Gregory XIII",
        year = 1000,
        summary = "Year 1000 is a leap year under the Julian rule used in Europe but not under the Gregorian rule, so the two rules count 3 and 2 leap years from 1000 to 1010.",
        details = "The Julian rule makes every fourth year a leap year, while the Gregorian rule of 1582 leaves out century years that are not divisible by 400. The Gregorian rule is applied to 1000 only for comparison, since the Gregorian calendar was not adopted until 1582.",
        instructions = """
            1. Save the example as leap_rules.py.
            2. Run it: python3 leap_rules.py
        """,
        example = """
            def julian_leap(year):
                return year % 4 == 0


            def gregorian_leap(year):
                return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)


            years = range(1000, 1011)
            julian = [y for y in years if julian_leap(y)]
            gregorian = [y for y in years if gregorian_leap(y)]
            print("Julian leap years:", julian)
            print("Gregorian leap years:", gregorian)
            print("counts, Julian and Gregorian:", len(julian), len(gregorian))
            print("1000 is a leap year, Julian:", julian_leap(1000), "Gregorian:", gregorian_leap(1000))
        """,
    ),

)
