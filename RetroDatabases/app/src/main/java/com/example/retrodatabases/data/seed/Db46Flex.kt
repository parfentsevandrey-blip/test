package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1580 to 1590: the days dropped in the 1582 calendar reform, a four-handed card deal and the binary digits for a word-sign system. */
val db46Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.FLEX,
        section = "Calendar",
        title = "Days dropped by the Gregorian reform of 1582",
        originalTitle = "Inter gravissimas",
        author = "Pope Gregory XIII",
        year = 1582,
        summary = "The reform of 1582 made Thursday 4 October follow as Friday 15 October, and a short program with fixed day formulas shows that ten days were dropped.",
        details = "The bull Inter gravissimas of 24 February 1582 set the reform in force in October 1582, when Julian Thursday 4 October became Gregorian Thursday 14 October. The example converts the dates to day numbers with fixed formulas, so the output never changes.",
        instructions = """
            1. Save the example as days_dropped.py.
            2. Run it: python3 days_dropped.py
        """,
        example = """
            def julian_day(year, month, day):
                # Day number of a date in the Julian calendar, from fixed formulas.
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            def gregorian_day(year, month, day):
                # The same formulas with the century rules added give the Gregorian day number.
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - y // 100 + y // 400 - 32045

            same_day = julian_day(1582, 10, 4)
            print("Julian 4 October 1582 is Gregorian 14 October 1582:", gregorian_day(1582, 10, 14) == same_day)
            print("Julian 4 October 1582 is Gregorian 15 October 1582:", gregorian_day(1582, 10, 15) == same_day)
            match = [day for day in range(1, 32) if gregorian_day(1582, 10, day) == same_day]
            print("days dropped:", match[0] - 4)
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.FLEX,
        section = "Cards",
        title = "Cards in a pack dealt to four players",
        originalTitle = "Cards in a pack dealt to four players",
        author = "Card players of the period",
        year = 1586,
        summary = "A pack of 52 cards dealt to four players gives each player 13 cards with none left over, and a short program prints the deal.",
        details = "The example divides a pack of 52 cards evenly among four players, which is how a four-handed deal works. It checks the remainder with integer arithmetic, so the output is exact.",
        instructions = """
            1. Save the example as card_deal.py.
            2. Run it: python3 card_deal.py
        """,
        example = """
            PACK = 52
            PLAYERS = 4

            print("cards dealt to each player:", PACK // PLAYERS)
            print("cards left over:", PACK % PLAYERS)
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.FLEX,
        section = "Shorthand",
        title = "Binary digits for 500 word-signs",
        originalTitle = "Characterie: An Arte of Short, Swift, and Secret Writing by Character",
        author = "Timothy Bright",
        year = 1588,
        summary = "A word-sign system of 500 signs needs nine binary digits for each sign, because 2 to the eighth power is 256 and 2 to the ninth is 512, and a short program finds that number.",
        details = "Bright's Characterie of 1588 gave each common word its own sign, and the example takes 500 signs as an illustrative total, not a count from the book. The program finds the smallest number of binary digits that gives each sign its own code.",
        instructions = """
            1. Save the example as word_signs.py.
            2. Run it: python3 word_signs.py
        """,
        example = """
            SIGNS = 500   # an illustrative total of word-signs

            bits = 1
            while 2 ** bits < SIGNS:
                bits += 1
            print("signs:", SIGNS, "binary digits:", bits, "codes:", 2 ** bits)
        """,
    ),

)
