package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1690 to 1700, one line of code each: the year 1694 in Roman numerals, the years from van der Heyden's 1690 book to Newsham's 1721 pump, and 1697 in hexadecimal. */
val db35Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.FLEX,
        section = "Dates",
        title = "The year 1694 in Roman numerals",
        originalTitle = "Dictionnaire de l'Academie francoise",
        author = "Academie francaise",
        year = 1694,
        summary = "The year of the French Academy's first dictionary, 1694, written in Roman numerals as MDCXCIV.",
        details = "Title pages of the period often gave dates in Roman numerals. The short function converts any year by taking the largest symbols first, and here it prints the Roman form of 1694.",
        instructions = """
            1. Save the example as roman.py.
            2. Run it: python3 roman.py
        """,
        example = """
            def roman(n):
                pairs = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
                out = ""
                for value, symbol in pairs:
                    while n >= value:
                        out += symbol
                        n -= value
                return out

            print(roman(1694))
        """,
    ),



    entry(
        databaseId = 35,
        kind = Kind.FLEX,
        section = "Fire engines",
        title = "Years from van der Heyden's 1690 book to Newsham's 1721 fire pump",
        originalTitle = "Beschryving der nieuwlyks uitgevonden en geoctrojeerde slang-brand-spuiten",
        author = "Jan van der Heyden",
        year = 1690,
        summary = "Van der Heyden's 1690 book on the hose engine came 31 years before Newsham's patented fire pump of 1721.",
        details = "Both are fire engines that pump water for fighting fires, and the gap in years is a simple difference. The one line prints the number of years between the two dates.",
        instructions = """
            1. Save the example as fire_gap.py.
            2. Run it: python3 fire_gap.py
        """,
        example = """
            print(f"years from 1690 to 1721: {1721 - 1690}")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.FLEX,
        section = "Dates",
        title = "The year of Bayle's dictionary in hexadecimal",
        originalTitle = "Dictionnaire historique et critique",
        author = "Pierre Bayle",
        year = 1697,
        summary = "The first edition of Bayle's dictionary, 1697, written in hexadecimal as 0x6a1.",
        details = "Hexadecimal uses sixteen digits, and the one-line program prints the year with the usual 0x prefix. Bayle's dictionary appeared in 1697, so the output is the hexadecimal form of that year.",
        instructions = """
            1. Save the example as hex_year.py.
            2. Run it: python3 hex_year.py
        """,
        example = """
            print(f"1697 in hexadecimal: {1697:#x}")
        """,
    ),

)
