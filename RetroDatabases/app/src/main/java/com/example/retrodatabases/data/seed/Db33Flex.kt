package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1710 to 1720: Pascal's rule in Bernoulli's year, the days of Queen Anne's reign and Waldegrave's mixed strategy. */
val db33Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.FLEX,
        section = "Combinatorics",
        title = "Pascal's rule for combinations in a few lines",
        originalTitle = "Ars conjectandi",
        author = "Jacob Bernoulli",
        year = 1713,
        summary = "Pascal's rule builds the table of combinations row by row, and the program checks every entry of the first nine rows against the factorial formula.",
        details = "Bernoulli's Ars Conjectandi of 1713 uses combinations to count cases in games of chance. The rule behind the table, that each entry is the sum of the two above it, is much older than the book and goes back to Pascal's treatise of 1654.",
        instructions = """
            1. Save the example as pascal_rule.py.
            2. Run it: python3 pascal_rule.py
        """,
        example = """
            from math import comb, factorial

            rows = [[1]]
            for n in range(1, 9):
                previous = rows[-1]
                rows.append([1] + [previous[k - 1] + previous[k] for k in range(1, n)] + [1])

            ok = all(rows[n][k] == comb(n, k) == factorial(n) // (factorial(k) * factorial(n - k))
                     for n in range(9) for k in range(n + 1))
            print("row 6:", " ".join(str(x) for x in rows[6]))
            print("row 8:", " ".join(str(x) for x in rows[8]))
            print("every entry checks:", ok)
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days in Queen Anne's reign",
        originalTitle = "The reign of Queen Anne",
        author = "Queen Anne",
        year = 1714,
        summary = "Queen Anne's reign ran from 8 March 1702 to 1 August 1714 in the Julian calendar, and the program counts the days between them in a few lines.",
        details = "Anne succeeded in March 1702 and died in August 1714, and the Hanoverian succession followed her death. The example takes the two Julian dates as the Gregorian 19 March 1702 and 12 August 1714, which fall eleven days later on the calendar, and subtracts them.",
        instructions = """
            1. Save the example as anne_reign.py.
            2. Run it: python3 anne_reign.py
        """,
        example = """
            from datetime import date

            # The Julian dates 8 March 1702 and 1 August 1714, shifted eleven days to the Gregorian calendar.
            accession = date(1702, 3, 19)
            death = date(1714, 8, 12)
            print(f"days from the accession to the death: {(death - accession).days}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.FLEX,
        section = "Games",
        title = "Waldegrave's mixed strategy for a card game",
        originalTitle = "Le Her",
        author = "James Waldegrave",
        year = 1713,
        summary = "In a two-player card game with no fixed best move, a mixed strategy with set chances leaves the opponent nothing to gain, which the program checks for a 2 by 2 game.",
        details = "Waldegrave's letter of 1713, printed with Montmort's second edition, gave a mixed strategy for the card game le Her in which each player chooses at random with fixed chances. The example solves a 2 by 2 zero-sum game with the same idea, finds the chances and the value, and checks that the row player gets the value against both columns.",
        instructions = """
            1. Save the example as waldegrave.py.
            2. Run it: python3 waldegrave.py
        """,
        example = """
            from fractions import Fraction as F

            # Payoffs to the row player. There is no saddle point, so each player must mix the two choices.
            a, b, c, d = F(3), F(-1), F(-2), F(4)
            denominator = a + d - b - c
            value = (a * d - b * c) / denominator
            p = (d - c) / denominator    # chance the row player takes the first row
            q = (d - b) / denominator    # chance the column player takes the first column
            print("row player plays row 1 with chance", p)
            print("column player plays column 1 with chance", q)
            print("value of the game", value)
            print("row payoff against column 1:", a * p + c * (1 - p))
            print("row payoff against column 2:", b * p + d * (1 - p))
        """,
        rare = true,
    ),
)
