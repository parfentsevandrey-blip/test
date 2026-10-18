package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1132: the Saros of 223 lunations, the eclipse cycle that Babylonian astronomers used, counted in days. */
val db91Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.EXAMPLE,
        section = "Eclipses",
        title = "The Saros of 223 lunations, counted in days from the mean month",
        originalTitle = "Saros",
        author = "Babylonian astronomers, as inherited by later astronomers",
        year = 1132,
        summary = "The Saros of 223 lunations was an eclipse cycle that Babylonian astronomers used, and a short program turns it into days with the mean length of a lunar month.",
        details = "After 223 lunations the Sun, Moon and nodes return nearly to the same relative positions, so eclipses tend to repeat in a similar pattern. The example multiplies 223 by a mean lunar month of 29.5306 days, which gives 6585.32 days, a product that can be checked by hand.",
        instructions = """
            1. Save the example as saros.py.
            2. Run it: python3 saros.py
        """,
        example = """
            MONTH_DAYS = 29.5306   # mean lunar month, rounded to four decimals
            LUNATIONS = 223        # the Saros cycle
            print(f"{LUNATIONS} lunations: {LUNATIONS * MONTH_DAYS:.2f} days")
        """,
    ),

)
