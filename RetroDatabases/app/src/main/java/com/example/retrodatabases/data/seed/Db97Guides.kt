package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1079: the 33-year cycle of the Jalali calendar, written out as a short program. */
val db97Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How the 33-year cycle of the Jalali calendar places its leap years",
        originalTitle = "How the 33-year cycle of the Jalali calendar places its leap years",
        author = "Malik-Shah's astronomers",
        year = 1079,
        summary = "A program lists the leap years of one 33-year cycle of the Jalali calendar and counts how many fall in two cycles.",
        details = "In each cycle of 33 years the leap years are years 4, 8, 12, 16, 20, 24, 28 and 33, which gives eight leap days in 33 years and a mean year of 365.2424 days. The program prints those years, the count per cycle, the count in the first 66 years and the mean year.",
        instructions = """
            1. Save the example as jalali_cycle.py.
            2. Run it: python3 jalali_cycle.py
        """,
        example = """
            leap = {4, 8, 12, 16, 20, 24, 28, 33}   # leap years of one 33-year cycle
            cycle_leaps = [y for y in range(1, 34) if y in leap]
            print("leap years in one cycle:", " ".join(str(y) for y in cycle_leaps))
            print("leap years per cycle:", len(cycle_leaps))
            print("leap years in years 1 to 66:", sum(1 for y in range(1, 67) if (y - 1) % 33 + 1 in leap))
            print("mean year in days: %.4f" % (365 + len(cycle_leaps) / 33))
        """,
    ),

)
