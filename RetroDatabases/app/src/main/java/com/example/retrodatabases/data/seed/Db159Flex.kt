package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 451 to 456: days from the opening of Chalcedon to Sidonius's recitation, and days from the murder of Aetius to the sack of Rome. */
val db159Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the opening of Chalcedon to Sidonius's recitation for Avitus",
        originalTitle = "Days between two fixed dates",
        author = "The council of Chalcedon and Sidonius Apollinaris",
        year = 456,
        summary = "The Council of Chalcedon opened on 8 October 451 and Sidonius recited his panegyric on 1 January 456, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. The calendar offset between the Julian and Gregorian reckonings does not change over these years, so the Julian count is the same as the count shown.",
        instructions = """
            1. Save the example as chalcedon_sidonius.py.
            2. Run it: python3 chalcedon_sidonius.py
        """,
        example = """
            from datetime import date

            council = date(451, 10, 8)
            recitation = date(456, 1, 1)
            print("days from the opening of Chalcedon to the recitation:", (recitation - council).days)
            print("whole years between the two dates:", recitation.year - council.year)
        """,
        rare = true,
    ),

    entry(
        databaseId = 159,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the murder of Aetius to the sack of Rome by the Vandals",
        originalTitle = "Days between two fixed dates",
        author = "Valentinian III and Gaiseric",
        year = 455,
        summary = "Aetius was killed on 21 September 454 and the Vandals sacked Rome on 2 June 455, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar, which gives the same count for these dates because no leap day falls between them. The span crosses the turn of the year, and the program prints the count so that it can be checked by hand.",
        instructions = """
            1. Save the example as aetius_sack.py.
            2. Run it: python3 aetius_sack.py
        """,
        example = """
            from datetime import date

            aetius = date(454, 9, 21)
            sack = date(455, 6, 2)
            print("days from the murder of Aetius to the sack of Rome:", (sack - aetius).days)
        """,
    ),

)
