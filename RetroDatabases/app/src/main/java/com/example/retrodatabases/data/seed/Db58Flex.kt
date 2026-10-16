package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1460 to 1470: days from the sack of Mainz to the Eltville printing, years from Balbus to the Catholicon, and days from the Eltville printing to 1470. */
val db58Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the sack of Mainz to the Eltville word list",
        originalTitle = "Sack of Mainz, 28 October 1462",
        author = "Adolf of Nassau and the Bechtermunze brothers",
        year = 1467,
        summary = "Python's date library counts the days from the sack of Mainz on 28 October 1462 to the Eltville printing of 4 November 1467.",
        details = "Adolf of Nassau took Mainz in 1462, and the Eltville brothers finished their word list in November 1467. Both dates are given as they are commonly cited, and no century year falls between them, so the count is the same in the Julian and Gregorian reckonings.",
        instructions = """
            1. Save the example as mainz_days.py.
            2. Run it: python3 mainz_days.py
        """,
        example = """
            from datetime import date

            sack = date(1462, 10, 28)     # the sack of Mainz, as commonly given
            eltville = date(1467, 11, 4)  # the Eltville first edition, as commonly given
            days = (eltville - sack).days
            print(f"days from 28 October 1462 to 4 November 1467: {days}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Balbus's Catholicon of 1286 to its Mainz edition",
        originalTitle = "Catholicon",
        author = "Johannes Balbus of Genoa",
        year = 1460,
        summary = "The Catholicon was finished at Genoa in 1286 and printed at Mainz in 1460, a gap of 174 years that a short program computes.",
        details = "The 1286 date is the one usually given for Balbus's text, and 1460 is the date of the Mainz edition. The program makes the subtraction explicit, so the reader can see how the gap is found.",
        instructions = """
            1. Save the example as catholicon_years.py.
            2. Run it: python3 catholicon_years.py
        """,
        example = """
            composed = 1286   # Balbus finished the Catholicon at Genoa, as commonly given
            printed = 1460    # the Mainz edition, as commonly given
            print(f"years from the Catholicon of {composed} to its Mainz printing of {printed}: {printed - composed}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Count of days from 4 November 1467 to the start of 1470",
        originalTitle = "Vocabularius ex quo",
        author = "Heinrich and Nikolaus Bechtermunze of Eltville",
        year = 1467,
        summary = "Python's date library counts the days from the Eltville printing of 4 November 1467 to New Year's Day 1470.",
        details = "The Eltville first edition is dated 4 November 1467, and New Year's Day 1470 opens the year of the Paris letters. The count includes the leap day of February 1468.",
        instructions = """
            1. Save the example as eltville_days.py.
            2. Run it: python3 eltville_days.py
        """,
        example = """
            from datetime import date

            eltville = date(1467, 11, 4)   # the Eltville first edition, as commonly given
            new_year = date(1470, 1, 1)
            print(f"days from 4 November 1467 to 1 January 1470: {(new_year - eltville).days}")
        """,
    ),

)
