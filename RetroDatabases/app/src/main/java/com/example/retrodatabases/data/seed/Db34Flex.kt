package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1700 to 1710: a sign count for roots, a recurrence for shuffles and an error term for 22/7. */
val db34Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.FLEX,
        section = "Calendars",
        title = "Is 1700 a leap year? One line of arithmetic",
        originalTitle = "Gregorian calendar",
        author = "Pope Gregory XIII",
        year = 1700,
        summary = "The Gregorian leap rule skips century years unless they are divisible by 400, so 1700 is not a leap year, and one line of code shows it.",
        details = "The rule was set by the Gregorian reform of 1582, and Sweden's calendar experiment of 1700 dropped the same leap day. The example applies the rule to 1700 and to 1704, and prints whether each is a leap year.",
        instructions = """
            1. Save the example as leap_year.py.
            2. Run it: python3 leap_year.py
        """,
        example = """
            def gregorian_leap(year):
                # Divisible by 4, except the century years, which must also be divisible by 400.
                return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)

            print("1700 is a leap year:", gregorian_leap(1700), "; 1704 is a leap year:", gregorian_leap(1704))
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.FLEX,
        section = "Arithmetic",
        title = "A rule-of-three trade problem in one line",
        originalTitle = "Arifmetika, sie est nauka chislitelnaya",
        author = "Leonty Magnitsky",
        year = 1703,
        summary = "If 3 measures cost 7 coins, the rule of three gives the cost of 12 measures, and one line of code computes it exactly.",
        details = "Problems of this kind were the stock of arithmetic books of the period, including Magnitsky's Arithmetic of 1703. The measures and prices in the example are invented, and the program applies the rule of three with an exact fraction.",
        instructions = """
            1. Save the example as rule_of_three.py.
            2. Run it: python3 rule_of_three.py
        """,
        example = """
            from fractions import Fraction as F
            print("12 measures cost", F(7, 3) * 12, "coins")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.FLEX,
        section = "Geometry",
        title = "Jones's 22/7 and how far it misses the circle ratio",
        originalTitle = "Synopsis Palmariorum Matheseos",
        author = "William Jones",
        year = 1706,
        summary = "The fraction 22/7 is a familiar rough value for the circle ratio, and one line of code prints how far it lies above the true value.",
        details = "Jones's symbol named the circle ratio, and fractions such as 22/7 were the usual rough values for it. The example prints the error of 22/7 against the value of pi from the standard math module, to six places.",
        instructions = """
            1. Save the example as twenty_two_sevenths.py.
            2. Run it: python3 twenty_two_sevenths.py
        """,
        example = """
            from math import pi
            print(f"22/7 minus pi: {22 / 7 - pi:.6f}")
        """,
    ),

)
