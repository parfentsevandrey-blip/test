package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 230 to 240: the days in one Sothic cycle of 1461 Egyptian years, anchored to Censorinus's book of 238. */
val db181Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.EXAMPLE,
        section = "Cycles",
        title = "The Sothic cycle of 1461 Egyptian years counted in days",
        originalTitle = "Sothic cycle",
        author = "Egyptian reckoners, discussed by Censorinus",
        year = 238,
        summary = "The 365-day Egyptian year slips against the solar year, so 1461 Egyptian years make one full Sothic cycle, and a short program counts the days in that cycle.",
        details = "The sources do not date the cycle to 238; that year is the year of Censorinus's book De die natali, which discusses the cycle, so the year is an anchor and not the date of the cycle. The sources disagree about the date of the last renewal, and this record uses no renewal date.",
        instructions = """
            1. Save the example as sothic.py.
            2. Run it: python3 sothic.py
        """,
        example = """
            EGYPTIAN_YEAR_DAYS = 365      # every Egyptian civil year has 365 days
            CYCLE_YEARS = 1461            # Egyptian years in one Sothic cycle
            JULIAN_YEARS = 1460           # the same span counted in Julian years
            JULIAN_YEAR_DAYS = 365.25     # average Julian year, as the Julian rule gives
            print("egyptian days in the cycle:", CYCLE_YEARS * EGYPTIAN_YEAR_DAYS)
            print("julian years in the cycle:", JULIAN_YEARS)
            print("julian days in the same span:", JULIAN_YEARS * JULIAN_YEAR_DAYS)
        """,
        rare = true,
    ),

)
