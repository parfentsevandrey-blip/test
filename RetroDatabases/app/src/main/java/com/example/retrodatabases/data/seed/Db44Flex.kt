package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1600 to 1610: five binary signs for Bacon's letters and the days of two fixed spans of time. */
val db44Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.FLEX,
        section = "Ciphers",
        title = "Five binary signs for twenty-four letters",
        originalTitle = "The Advancement of Learning",
        author = "Francis Bacon",
        year = 1605,
        summary = "Bacon's biliteral cipher needs five signs for each letter, because 2 to the fifth power, which is 32, is the first power of two that covers his 24 letters.",
        details = "Bacon described the cipher in his Advancement of Learning of 1605, and its alphabet had 24 letters, with I and J and U and V sharing signs. The example finds the smallest number of binary signs that gives every letter its own code.",
        instructions = """
            1. Save the example as bacon_bits.py.
            2. Run it: python3 bacon_bits.py
        """,
        example = """
            letters = 24
            bits = 1
            while 2 ** bits < letters:
                bits += 1
            print("binary signs needed:", bits, "codes available:", 2 ** bits)
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.FLEX,
        section = "Treaties",
        title = "Days in the Twelve Years' Truce of 1609",
        originalTitle = "Twaalfjarig Bestand",
        author = "Spain and the Dutch Republic",
        year = 1609,
        summary = "The Twelve Years' Truce between Spain and the Dutch Republic was agreed in 1609 for twelve years, and a short program counts its years and days.",
        details = "The truce was agreed at Antwerp on 9 April 1609, and it was meant to pause the war in the Low Countries for twelve years. The example counts the years and days to its end, 9 April 1621, with fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as truce_days.py.
            2. Run it: python3 truce_days.py
        """,
        example = """
            from datetime import date

            signed = date(1609, 4, 9)   # the truce was agreed on this day, as commonly given
            ends = date(1621, 4, 9)     # twelve years later
            print("years:", ends.year - signed.year)
            print("days:", (ends - signed).days)
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Bodleian reopening to the Bank's opening",
        originalTitle = "Bibliotheca Bodleiana",
        author = "Sir Thomas Bodley",
        year = 1609,
        summary = "Python's date arithmetic counts the days between two fixed dates, here the Bodleian reopening of November 1602 and the Bank of Amsterdam's opening in January 1609.",
        details = "The Bodleian reopened on 8 November 1602, and the Amsterdam bank's opening is generally given as 31 January 1609. The example counts the days between the two dates with a fixed calendar, so the result never changes.",
        instructions = """
            1. Save the example as bodley_days.py.
            2. Run it: python3 bodley_days.py
        """,
        example = """
            from datetime import date

            reopened = date(1602, 11, 8)       # the Bodleian reopening, as commonly given
            bank_opened = date(1609, 1, 31)    # the Bank of Amsterdam's opening, as commonly given
            days = (bank_opened - reopened).days
            print(f"days from the Bodleian reopening to the Bank of Amsterdam opening: {days}")
        """,
    ),

)
