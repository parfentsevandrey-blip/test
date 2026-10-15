package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1630 to 1640: a monochord's fifth and fourth, a day count to a letter of 1640 and the gap between Kepler's death and his Somnium. */
val db41Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.FLEX,
        section = "Music",
        title = "Mersenne's monochord: the fifth and the fourth from one string",
        originalTitle = "Monochord ratios for the fifth and fourth",
        author = "Marin Mersenne",
        year = 1636,
        summary = "Mersenne's Harmonie universelle of 1636 treats the consonances as ratios of string lengths, and a monochord string of 360 parts gives a fifth at 240 and a fourth at 270.",
        details = "The fifth is the ratio of two to three and the fourth the ratio of three to four, so the bridge stands at two thirds and three quarters of the string. The one line computes both positions in whole parts, which is the monochord arithmetic that the book sets out.",
        instructions = """
            1. Save the example as monochord.py.
            2. Run it: python3 monochord.py
        """,
        example = """
            string = 360    # the monochord string, in equal parts
            print("fifth at", string * 2 // 3, "; fourth at", string * 3 // 4)
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "Days from New Year to Fermat's Christmas letter of 1640",
        originalTitle = "Letter to Mersenne of 25 December 1640",
        author = "Pierre de Fermat",
        year = 1640,
        summary = "Fermat's letter to Mersenne of 25 December 1640 is where he stated that primes of the form 4n+1 are sums of two squares, and one line of code counts the days from New Year to that letter.",
        details = "Counting from 1 January, which is day 0, the letter falls on day 359 of the leap year 1640. The one line does the count with Python dates, and the number theory of the letter is not computed here.",
        instructions = """
            1. Save the example as fermat_letter.py.
            2. Run it: python3 fermat_letter.py
        """,
        example = """
            from datetime import date

            letter = date(1640, 12, 25)    # the day of the letter to Mersenne
            start = date(1640, 1, 1)       # New Year, counted as day 0
            print(f"days from 1 January to 25 December 1640: {(letter - start).days}")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.FLEX,
        section = "Astronomy",
        title = "Kepler's Somnium printed four years after its author's death",
        originalTitle = "Somnium, seu opus posthumum de astronomia lunari",
        author = "Johannes Kepler",
        year = 1634,
        summary = "Kepler's Somnium appeared in 1634, four years after its author died in 1630, and one line of code counts the gap between the two dates.",
        details = "The Somnium was printed after Kepler's death by his son, and it is a story of a journey to the Moon with notes on the lunar sky. The one line only subtracts the two years, and the astronomy of the book is not computed here.",
        instructions = """
            1. Save the example as somnium_gap.py.
            2. Run it: python3 somnium_gap.py
        """,
        example = """
            kepler_death = 1630
            somnium_printed = 1634
            print(f"the Somnium appeared {somnium_printed - kepler_death} years after Kepler's death")
        """,
    ),

)
