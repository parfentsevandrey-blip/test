package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1290 to 1300: years of Marco Polo's journey, days from the fall of Acre to the first Holy Year bull, and days from a pope's death to the next election. */
val db75Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Marco Polo's departure to his return",
        originalTitle = "Years of a journey",
        author = "Marco Polo",
        year = 1295,
        summary = "Marco Polo left Venice in 1271 and came home in 1295, and a short program counts the years between the two dates.",
        details = "Both dates are the traditional ones, and the count is exact for the dates as given. The span covers his travels out and back, including his years in China.",
        instructions = """
            1. Save the example as polo_years.py.
            2. Run it: python3 polo_years.py
        """,
        example = """
            departure = 1271    # traditional date of leaving Venice
            return_year = 1295  # traditional date of his homecoming
            print("years from departure to return:", return_year - departure)
        """,
    ),

    entry(
        databaseId = 75,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the fall of Acre to the first Holy Year bull",
        originalTitle = "Days between two dates",
        author = "The garrison of Acre and Boniface VIII",
        year = 1291,
        summary = "Acre fell on 18 May 1291 and the bull Antiquorum habet is dated 22 February 1300, and a short program counts the days between the two dates in the Julian calendar.",
        details = "Both dates are given as commonly stated, and the program uses the Julian day count, so the answer is exact for the dates as written. The span includes the leap days of February 1292 and February 1296.",
        instructions = """
            1. Save the example as acre_holy_year.py.
            2. Run it: python3 acre_holy_year.py
        """,
        example = """
            def julian_day(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            print("days from the fall of Acre to the Holy Year bull:", julian_day(1300, 2, 22) - julian_day(1291, 5, 18))
        """,
    ),

    entry(
        databaseId = 75,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Nicholas IV to the election of Celestine V",
        originalTitle = "Days between two papal dates",
        author = "The cardinals of 1292 to 1294",
        year = 1292,
        summary = "Pope Nicholas IV died on 4 April 1292 and Celestine V was elected on 5 July 1294, and a short program counts the days between the two dates in the Julian calendar.",
        details = "The interval was a vacancy of more than two years, in which the cardinals chose no pope. Both dates are given as commonly stated, and the count uses the same Julian day count as the Acre example.",
        instructions = """
            1. Save the example as papal_gap.py.
            2. Run it: python3 papal_gap.py
        """,
        example = """
            def julian_day(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            print("days from the death of Nicholas IV to the election of Celestine V:", julian_day(1294, 7, 5) - julian_day(1292, 4, 4))
        """,
    ),

)
