package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1490 to 1500: fifteen woodcuts for twenty-two chapters, the days of a sea passage and the years of a clock tower's building. */
val db55Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.FLEX,
        section = "Woodcuts",
        title = "Fifteen woodcuts for the twenty-two chapters of Revelation",
        originalTitle = "Apocalipsis cum figuris",
        author = "Albrecht Duerer",
        year = 1498,
        summary = "Duerer's series of 1498 gave the Book of Revelation fifteen woodcut pictures, and the example sets that count against the book's twenty-two chapters.",
        details = "The series was printed at Nuremberg in 1498, in Latin and in German, and the count of pictures is the one commonly given. The example reduces the ratio to lowest terms and counts the chapters that have no picture, so the output is fixed by the arithmetic.",
        instructions = """
            1. Save the example as apocalypse_fraction.py.
            2. Run it: python3 apocalypse_fraction.py
        """,
        example = """
            from fractions import Fraction

            PICTURES = 15    # full-page woodcuts in the series, as commonly given
            CHAPTERS = 22    # chapters of the Book of Revelation
            share = Fraction(PICTURES, CHAPTERS)
            print(f"pictures per chapter: {share}")
            print(f"chapters without a picture: {CHAPTERS - PICTURES}")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.FLEX,
        section = "Voyages",
        title = "Days from Vasco da Gama's sailing to Calicut",
        originalTitle = "Voyage to India",
        author = "Vasco da Gama and his fleet",
        year = 1497,
        summary = "Vasco da Gama's fleet sailed from Lisbon in July 1497 and reached Calicut in May 1498, a passage of 316 days on the dates commonly given.",
        details = "The dates are those commonly given for the voyage, and the example counts only the days between the two dates on the calendar. Accounts differ on some of the dates of the early stops, so the record names the departure and the arrival only.",
        instructions = """
            1. Save the example as vasco_days.py.
            2. Run it: python3 vasco_days.py
        """,
        example = """
            from datetime import date

            sailed = date(1497, 7, 8)      # departure from Lisbon, as commonly given
            reached = date(1498, 5, 20)    # arrival at Calicut, as commonly given
            print("days from Lisbon to Calicut:", (reached - sailed).days)
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.FLEX,
        section = "Clocks",
        title = "Years from the first build date of Venice's clock to its unveiling",
        originalTitle = "Torre dell'Orologio",
        author = "Zuan Paolo and Zuan Carlo Rainieri",
        year = 1499,
        summary = "The clock tower of Venice was unveiled on 1 February 1499, and an account that gives 1496 as its first build date counts three years to the unveiling.",
        details = "Accounts differ on the first build date, and the commission itself is dated to 1493 or 1495 in some sources, so the example uses 1496 and the official unveiling of 1499 only. The output is a difference of two years and is fixed by those dates.",
        instructions = """
            1. Save the example as clock_years.py.
            2. Run it: python3 clock_years.py
        """,
        example = """
            first_build = 1496   # first build date in one account, as commonly given
            unveiled = 1499      # official unveiling on 1 February 1499
            print("years from the first build date to the unveiling:", unveiled - first_build)
        """,
    ),

)
