package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 650 to 660: the years from the Hakuchi era of 650 to Japan's first water clock of 660. */
val db139Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Hakuchi era of 650 to Japan's first water clock of 660",
        originalTitle = "Years between two Japanese records",
        author = "Japanese court",
        year = 650,
        summary = "The Hakuchi era of 650 and Japan's first water clock of 660 are ten years apart, and a short program prints the gap.",
        details = "The two items are linked only by the dates commonly given for them, so the span compares those years and does not show any cause. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as hakuchi_clock.py.
            2. Run it: python3 hakuchi_clock.py
        """,
        example = """
            HAKUCHI = 650        # the Hakuchi era name, as commonly dated
            WATER_CLOCK = 660    # the first water clock, as the Nihon Shoki dates it
            print("years from the Hakuchi era of 650 to the first water clock of 660:", WATER_CLOCK - HAKUCHI)
        """,
    ),

)
