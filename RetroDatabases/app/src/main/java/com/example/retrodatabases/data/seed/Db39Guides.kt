package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1650 to 1660: a fair split of stakes, the value of a fair game, a cycloid's length, a semicubical parabola's length and Brouncker's fraction for pi. */
val db39Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How the problem of points divides the stakes",
        originalTitle = "Lettres de Pascal et de Fermat",
        author = "Blaise Pascal and Pierre de Fermat",
        year = 1654,
        summary = "A game stopped early is split fairly by counting the ways the rest of the game could go and weighing each player's chance of winning.",
        details = "Pascal and Fermat exchanged letters on the problem in 1654, and the usual example gives two players, one needing two more points and the other three, with a stake of 64 pistoles. The fair split is 44 for the first player and 20 for the second, and the example uses binomial sums to find that split for the stated stakes and for two more cases.",
        instructions = """
            1. Save the example as points_split.py.
            2. Run it: python3 points_split.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def split(a, b, stake):
                # a and b are the points each player still needs; the game is settled within a + b - 1 more rounds.
                n = a + b - 1
                first_wins = sum(comb(n, k) for k in range(a, n + 1))
                share = stake * Fraction(first_wins, 2 ** n)
                return share, stake - share

            for a, b, stake in ((2, 3, 64), (1, 2, 40), (3, 3, 32)):
                first, second = split(a, b, stake)
                print(f"needs {a} and {b}, stake {stake}: first {first}, second {second}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Huygens's rule values a fair game",
        originalTitle = "De ratiociniis in ludo aleae",
        author = "Christiaan Huygens",
        year = 1657,
        summary = "The value of a game with several chances is the average of its prizes, each weighted by the number of equal chances that bring it.",
        details = "Huygens's treatise on games of chance was printed at Leiden in 1657 as an appendix to van Schooten's collection of mathematical exercises. The example applies the rule to a game with two chances of 10 and three of 5, to three equal chances of 3, 6 and 9, and to one chance of 1 with three of 4, using exact fractions.",
        instructions = """
            1. Save the example as fair_game.py.
            2. Run it: python3 fair_game.py
        """,
        example = """
            from fractions import Fraction

            def value(chances):
                # Each entry is (number of equal chances, prize); the value is the weighted average of the prizes.
                total = sum(n for n, _ in chances)
                return Fraction(sum(n * prize for n, prize in chances), total)

            print("2 chances of 10 and 3 chances of 5:", value([(2, 10), (3, 5)]))
            print("3 equal chances of 3, 6 and 9:", value([(1, 3), (1, 6), (1, 9)]))
            print("1 chance of 1 and 3 chances of 4:", value([(1, 1), (3, 4)]))
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.GUIDE,
        section = "Curves",
        title = "How Wren's rectification gives the length of a cycloid arch",
        originalTitle = "Cycloid",
        author = "Christopher Wren",
        year = 1658,
        summary = "One arch of a cycloid is exactly eight times as long as the radius of the rolling circle that traces it.",
        details = "Wren's result of 1658 is one of the earliest exact lengths found for a curve. The example integrates one arch numerically with the midpoint rule and prints the length beside eight times the radius.",
        instructions = """
            1. Save the example as cycloid_arch.py.
            2. Run it: python3 cycloid_arch.py
        """,
        example = """
            import math

            def arch_length(radius, steps):
                # Midpoint rule for x = r(t - sin t), y = r(1 - cos t) over t from 0 to 2 pi.
                # The speed along the curve is r times the square root of (2 - 2 cos t), which equals 2 r sin(t/2).
                h = 2 * math.pi / steps
                total = 0.0
                for i in range(steps):
                    t = (i + 0.5) * h
                    speed = radius * math.sqrt(2 - 2 * math.cos(t))
                    total += speed * h
                return total

            for r in (1, 2, 5):
                length = arch_length(r, 2000)
                print(f"radius {r}: arch length {length:.4f}, eight times the radius {8 * r}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.GUIDE,
        section = "Curves",
        title = "How Neile measures a semicubical parabola",
        originalTitle = "Semicubical parabola",
        author = "William Neile",
        year = 1657,
        summary = "The arc of the semicubical parabola from its cusp to a given point has a length that one formula gives, the first algebraic curve besides the line and the circle to have its length found.",
        details = "Neile's result of 1657 is usually credited as the first rectification of an algebraic curve. The example checks the closed formula against numerical integration at three points.",
        instructions = """
            1. Save the example as neile_rectification.py.
            2. Run it: python3 neile_rectification.py
        """,
        example = """
            import math

            def closed_length(t):
                # The curve x = t^2, y = t^3 has speed t sqrt(4 + 9 t^2); the closed form is ((4 + 9 t^2)^(3/2) - 8) / 27.
                return ((4 + 9 * t * t) ** 1.5 - 8) / 27

            def numeric_length(t, steps):
                # Midpoint rule for the integral of s sqrt(4 + 9 s^2) from 0 to t.
                h = t / steps
                total = 0.0
                for i in range(steps):
                    s = (i + 0.5) * h
                    total += s * math.sqrt(4 + 9 * s * s) * h
                return total

            for t in (1.0, 2.0, 3.0):
                print(f"t = {t:.0f}: formula {closed_length(t):.4f}, numeric {numeric_length(t, 20000):.4f}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.GUIDE,
        section = "Series",
        title = "How Brouncker's fraction approaches four over pi",
        originalTitle = "Arithmetica infinitorum",
        author = "William Brouncker",
        year = 1656,
        summary = "Brouncker's continued fraction gives four over pi as one plus a nested fraction built from squares of odd numbers, and its cut-offs close in on the value while swinging above and below it.",
        details = "Brouncker's fraction was printed in Wallis's Arithmetica infinitorum of 1656. The example computes the first five cut-offs as exact fractions and prints their decimal values beside four over pi.",
        instructions = """
            1. Save the example as brouncker.py.
            2. Run it: python3 brouncker.py
        """,
        example = """
            import math
            from fractions import Fraction

            def brouncker(depth):
                # Nested form: 1 + 1^2 / (2 + 3^2 / (2 + 5^2 / (2 + ...))), cut off after the given depth.
                tail = Fraction(2)
                for k in range(depth - 1, 0, -1):
                    tail = 2 + Fraction((2 * k + 1) ** 2) / tail
                return 1 + 1 / tail

            for depth in range(1, 6):
                value = brouncker(depth)
                print(f"depth {depth}: {value} = {float(value):.6f}")
            print(f"four over pi: {4 / math.pi:.6f}")
        """,
    ),

)
