package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1410 to 1420: the sun's daily motion along the zodiac, the rate an astronomical dial such as the Orloj must show. */
val db63Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.FLEX,
        section = "Sun and moon",
        title = "How far the sun moves along the zodiac in one day",
        originalTitle = "Orloj",
        author = "Mikulas of Kadan and Jan Sindel",
        year = 1410,
        summary = "The sun's place on the Orloj's astronomical dial moves about a degree a day, and a short program divides a full turn by the length of the year to find the daily motion.",
        details = "The Orloj's astronomical dial shows the sun's place among the signs of the zodiac, so its sun pointer covers the zodiac once in a year. The example divides 360 degrees by 365.2422 days, a modern value of the tropical year, and the result is a modern figure, not a value taken from the clock.",
        instructions = """
            1. Save the example as sun_daily.py.
            2. Run it: python3 sun_daily.py
        """,
        example = """
            TROPICAL_YEAR = 365.2422   # days in a year, a modern value

            per_day = 360 / TROPICAL_YEAR
            print(f"degrees per day: {per_day:.4f}")
            print(f"degrees in 100 days: {100 * per_day:.2f}")
        """,
    ),

)
