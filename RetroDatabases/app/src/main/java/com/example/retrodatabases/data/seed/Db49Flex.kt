package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1550 to 1560: the days from a royal charter to 1560, the bits for a chapter's verses and the leap years of the decade. */
val db49Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Stationers' charter to 1 January 1560",
        originalTitle = "Royal charter of the Stationers' Company",
        author = "The Crown, under Queen Mary I",
        year = 1557,
        summary = "The Stationers' charter of 4 May 1557 is followed by 972 days before the year 1560 begins, which a short program counts with fixed dates.",
        details = "The first royal grant of incorporation to the London Stationers' Company is dated 4 May 1557, and the example counts the days from that date to 1 January 1560. The dates are fixed in the code, so the count never changes.",
        instructions = """
            1. Save the example as charter_days.py.
            2. Run it: python3 charter_days.py
        """,
        example = """
            from datetime import date

            charter = date(1557, 5, 4)    # the first royal grant of incorporation, as commonly dated
            new_year = date(1560, 1, 1)
            print("days from the charter to 1 January 1560:", (new_year - charter).days)
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.FLEX,
        section = "Citation",
        title = "Bits needed to number the verses of one chapter",
        originalTitle = "Novum Testamentum graece",
        author = "Robert Estienne",
        year = 1551,
        summary = "A chapter of 31 verses needs five binary digits to number every verse, because two to the fifth power, 32, is the first power of two that covers them.",
        details = "Estienne's numbered verses of 1551 gave each verse a number inside its chapter. The example finds the fewest binary digits that give every verse of an illustrative chapter of 31 verses its own number.",
        instructions = """
            1. Save the example as verse_bits.py.
            2. Run it: python3 verse_bits.py
        """,
        example = """
            verses = 31   # an illustrative chapter length
            bits = 1
            while 2 ** bits < verses:
                bits += 1
            print("binary digits needed for verse numbers up to", verses, ":", bits)
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.FLEX,
        section = "Calendar",
        title = "Days in the decade from 1 January 1551 to 1 January 1560",
        originalTitle = "Julian calendar",
        author = "Julius Caesar and Sosigenes",
        year = 1551,
        summary = "The nine years from 1 January 1551 to 1 January 1560 hold 3287 days, two of them leap days, which a short program counts with fixed dates.",
        details = "The Julian calendar, reformed by Julius Caesar with the help of Sosigenes, gave a leap day every fourth year, and the leap years inside this span are 1552 and 1556. The example counts the days with fixed calendar dates, so the result never changes.",
        instructions = """
            1. Save the example as decade_days.py.
            2. Run it: python3 decade_days.py
        """,
        example = """
            from datetime import date

            start = date(1551, 1, 1)
            end = date(1560, 1, 1)
            print("days from 1 January 1551 to 1 January 1560:", (end - start).days)
        """,
    ),

)
